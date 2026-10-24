package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class HaikuExplainerTest {
  HttpServer server;
  String endpoint;
  AtomicInteger calls = new AtomicInteger();
  AtomicReference<JsonNode> sent = new AtomicReference<>();
  ExecutorService executor;
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
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/messages",
        exchange -> {
          calls.incrementAndGet();
          var input = Fixture.JSON.readTree(exchange.getRequestBody());
          sent.set(input);
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
    executor.close();
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "{}",
        "{\"explanations\":null}",
        "{\"explanations\":\"wrong type\"}",
        "{\"explanations\":[\"not an object\"]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\"}]}",
        "{\"explanations\":[{\"explanation\":\"Missing ID\"}]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":42}]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":true}]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":null}]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":\"  \"}]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":\"Valid\"}],\"extra\":true}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":\"Valid\",\"extra\":true}]}",
        "{\"explanations\":[{\"suggestionId\":\"s1\",\"explanation\":\"First\"},{\"suggestionId\":\"s1\",\"explanation\":\"Duplicate\"}]}"
      })
  void invalidResponseShapeRetainsRulesAndUsageWithoutRetry(String output) {
    response = provider("end_turn", Fixture.JSON.readTree(output));
    var result = client("test-key", 2).explain(deck, run);
    assertThat(result.status()).isEqualTo("fallback");
    assertThat(result.explanations()).isEmpty();
    assertThat(result.usage()).containsEntry("output_tokens", 20);
    assertThat(calls).hasValue(1);
  }

  @Test
  void oversizedExplanationRetainsRules() {
    response =
        provider(
            "end_turn",
            Map.of(
                "explanations",
                List.of(Map.of("suggestionId", "s1", "explanation", "x".repeat(801)))));
    assertThat(client("test-key", 2).explain(deck, run).status()).isEqualTo("fallback");
    assertThat(calls).hasValue(1);
  }

  @Test
  void requestPreservesSeparateOracleAbilitiesAndActualCutFacts() {
    var source =
        (tools.jackson.databind.node.ObjectNode)
            Fixture.JSON.readTree(Fixture.sourceLine("Sol Ring"));
    source.put("name", "Synthetic ability boundaries");
    source.put(
        "oracle_text",
        "{2}, {T}: Create a Food token.\n" + "{3}, {T}: Draw a card. Activate only as a sorcery.");
    Card add = new CardClassifier().classify(source);
    Card cut = Fixture.card("Arcane Signet");
    var swap =
        new RecommendationRun(
            run.id(),
            deck.id(),
            1,
            run.catalogImportId(),
            List.of(new Suggestion("s1", add, cut, "Fills the draw gap", null, 8)),
            "rules",
            null,
            null,
            Map.of());
    assertThat(client("test-key", 2).explain(deck, swap).status()).isEqualTo("haiku");
    JsonNode request = sent.get();
    JsonNode prompt =
        Fixture.JSON.readTree(request.path("messages").get(0).path("content").asText());
    JsonNode change = prompt.path("changes").get(0);
    assertThat(change.path("suggestionId").asText()).isEqualTo("s1");
    assertThat(change.path("add").path("oracleText").asText()).isEqualTo(add.oracleText());
    assertThat(change.path("add").path("oracleLines").valueStream().map(JsonNode::asText).toList())
        .containsExactly(
            "{2}, {T}: Create a Food token.", "{3}, {T}: Draw a card. Activate only as a sorcery.");
    assertThat(change.path("cut").path("oracleText").asText()).isEqualTo(cut.oracleText());
    assertThat(change.path("cut").path("name").asText()).isEqualTo("Arcane Signet");
    assertThat(request.path("thinking").path("type").asText()).isEqualTo("disabled");
    assertThat(request.path("max_tokens").intValue()).isEqualTo(1200);
    assertThat(
            request
                .path("output_config")
                .path("format")
                .path("schema")
                .path("additionalProperties")
                .booleanValue())
        .isFalse();
    assertThat(calls).hasValue(1);
  }
}
