package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class HaikuExplainerTest {
  HttpServer server;
  String endpoint;
  AtomicInteger calls = new AtomicInteger();
  String response;
  int status = 200;
  long delay;
  final DeckView deck = Fixture.deck("Meren of Clan Nel Toth");
  final RecommendationRun run =
      new RecommendationRun(
          UUID.randomUUID(),
          deck.id(),
          1,
          UUID.randomUUID(),
          List.of(
              new Suggestion(
                  "s1",
                  Fixture.card("Solemn Simulacrum"),
                  null,
                  "Fills draw and ramp gaps",
                  null,
                  12)),
          "rules",
          null,
          null,
          Map.of());

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext(
        "/messages",
        exchange -> {
          calls.incrementAndGet();
          var input = Fixture.JSON.readTree(exchange.getRequestBody());
          assertThat(input.path("thinking").path("type").asText()).isEqualTo("disabled");
          assertThat(input.path("max_tokens").asInt()).isEqualTo(1200);
          assertThat(input.has("temperature")).isFalse();
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          byte[] body = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          try {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
          } finally {
            exchange.close();
          }
        });
    server.start();
    endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/messages";
    response =
        provider(
            "end_turn",
            Map.of(
                "explanations",
                List.of(
                    Map.of(
                        "suggestionId",
                        "s1",
                        "explanation",
                        "Solemn Simulacrum adds a land and can draw a card when it dies."))));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  HaikuExplainer client(String key, int timeout) {
    return new HaikuExplainer(key, "claude-haiku-5-5", endpoint, timeout, Fixture.JSON);
  }

  String provider(String stop, Object output) {
    return Fixture.JSON.writeValueAsString(
        Map.of(
            "stop_reason",
            stop,
            "content",
            List.of(Map.of("type", "text", "text", Fixture.JSON.writeValueAsString(output))),
            "usage",
            Map.of("input_tokens", 100, "output_tokens", 20)));
  }

  @Test
  void validStructuredExplanationAndUsageAreAccepted() {
    var result = client("test-key", 2).explain(deck, run);
    assertThat(result.status()).isEqualTo("haiku");
    assertThat(result.explanations()).containsKey("s1");
    assertThat(result.usage()).containsEntry("input_tokens", 100);
    assertThat(calls).hasValue(1);
  }

  @Test
  void unknownIdsAreRejectedAndUsageIsRetained() {
    response =
        provider(
            "end_turn",
            Map.of(
                "explanations",
                List.of(Map.of("suggestionId", "invented", "explanation", "Bad ID"))));
    var result = client("test-key", 2).explain(deck, run);
    assertThat(result.status()).isEqualTo("fallback");
    assertThat(result.explanations()).isEmpty();
    assertThat(result.usage()).containsEntry("output_tokens", 20);
  }

  @Test
  void missingExplanationsAreRejected() {
    response = provider("end_turn", Map.of("explanations", List.of()));
    assertThat(client("test-key", 2).explain(deck, run).status()).isEqualTo("fallback");
  }

  @Test
  void malformedOutputIsRejected() {
    response = "{broken";
    assertThat(client("test-key", 2).explain(deck, run).status()).isEqualTo("fallback");
  }

  @Test
  void refusalPreservesRules() {
    response = provider("refusal", Map.of());
    assertThat(client("test-key", 2).explain(deck, run).status()).isEqualTo("fallback");
  }

  @Test
  void timeoutDoesNotRetry() {
    delay = 1500;
    assertThat(client("test-key", 1).explain(deck, run).detail()).contains("timed out");
    assertThat(calls).hasValue(1);
  }

  @Test
  void missingCredentialsNeverCallProvider() {
    assertThat(client("", 2).explain(deck, run).status()).isEqualTo("fallback");
    assertThat(calls).hasValue(0);
  }

  @Test
  void providerFailureDoesNotRetry() {
    status = 429;
    assertThat(client("test-key", 2).explain(deck, run).status()).isEqualTo("fallback");
    assertThat(calls).hasValue(1);
  }
}
