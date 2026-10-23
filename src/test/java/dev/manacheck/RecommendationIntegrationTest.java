package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RecommendationIntegrationTest extends PostgresTestBase {
  record Scenario(String id, String commander, String theme, boolean complete) {
    @Override
    public String toString() {
      return id + " " + commander + " " + theme + " " + (complete ? "swap" : "addition");
    }
  }

  static Stream<Scenario> scenarios() throws IOException {
    return Arrays.stream(
        Fixture.JSON.readValue(
            RecommendationIntegrationTest.class.getResourceAsStream(
                "/recommendation-scenarios.json"),
            Scenario[].class));
  }

  private DeckView prepare(Scenario s) {
    Card commander = Fixture.card(s.commander());
    String land =
        Map.of("W", "Plains", "U", "Island", "B", "Swamp", "R", "Mountain", "G", "Forest")
            .get(commander.colorIdentity().getFirst());
    DeckView d = decks.create(new CreateDeck(s.id(), commander.oracleId(), s.theme()));
    List<EntryInput> entries = new ArrayList<>();
    entries.add(new EntryInput(Fixture.card(land).oracleId(), s.complete() ? 90 : 30));
    List<String> artifacts =
        s.complete()
            ? List.of(
                "Sol Ring",
                "Arcane Signet",
                "Wayfarer's Bauble",
                "Thought Vessel",
                "Skullclamp",
                "Mind Stone",
                "Fellwar Stone",
                "Worn Powerstone",
                "Command Tower")
            : List.of("Sol Ring");
    for (String name : artifacts) entries.add(new EntryInput(Fixture.card(name).oracleId(), 1));
    return decks.save(
        d.id(),
        new SaveRevision(
            d.revision(),
            commander.oracleId(),
            s.theme(),
            "Fixed recommendation scenario",
            entries));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  void twentyFixedScenariosPreserveConstructionRules(Scenario s) {
    DeckView d = prepare(s);
    RecommendationRun run = recommendations.generate(d.id());
    assertThat(run.suggestions()).hasSizeBetween(1, 5);
    for (Suggestion suggestion : run.suggestions()) {
      assertThat(suggestion.cut() != null).isEqualTo(s.complete());
      DeckView after = recommendations.simulate(d, suggestion.add(), suggestion.cut());
      Analysis check = validator.validate(after, catalog.activeId(), catalog.updatedAt());
      assertThat(check.recommendationEligible())
          .as(s.id() + " " + suggestion.add().name())
          .isTrue();
      assertThat(after.totalCards()).isEqualTo(s.complete() ? 100 : d.totalCards() + 1);
    }
    RecommendationRun again = recommendations.generate(d.id());
    assertThat(again.suggestions().stream().map(x -> x.add().oracleId()).toList())
        .isEqualTo(run.suggestions().stream().map(x -> x.add().oracleId()).toList());
  }

  @Test
  void applyingSuggestionCreatesOneRevisionAndRejectsReuse() {
    DeckView d = prepare(new Scenario("apply", "Meren of Clan Nel Toth", "graveyard", true));
    RecommendationRun run = recommendations.generate(d.id());
    DeckView after =
        recommendations.apply(
            run.id(), new ApplyRequest(run.suggestions().getFirst().id(), d.revision()));
    assertThat(after.revision()).isEqualTo(d.revision() + 1);
    assertThat(after.totalCards()).isEqualTo(100);
    assertThat(decks.get(d.id(), d.revision()).entries()).isEqualTo(d.entries());
    assertThatThrownBy(
            () ->
                recommendations.apply(
                    run.id(),
                    new ApplyRequest(run.suggestions().getFirst().id(), after.revision())))
        .isInstanceOf(ApiException.class)
        .extracting("status")
        .isEqualTo(409);
  }

  @Test
  void revisedCatalogInvalidatesOldRecommendations() {
    DeckView d = prepare(new Scenario("catalog", "Meren of Clan Nel Toth", "graveyard", false));
    RecommendationRun run = recommendations.generate(d.id());
    String changed =
        new String(Fixture.jsonl(), StandardCharsets.UTF_8).replace("Sol Ring", "Sol Ring updated");
    UUID update =
        importer.importStream(
            "changed",
            Instant.now(),
            new ByteArrayInputStream(changed.getBytes(StandardCharsets.UTF_8)),
            false);
    assertThat(catalog.importRun(update).status()).isEqualTo("SUCCEEDED");
    assertThatThrownBy(
            () ->
                recommendations.apply(
                    run.id(), new ApplyRequest(run.suggestions().getFirst().id(), d.revision())))
        .isInstanceOf(ApiException.class)
        .extracting("status")
        .isEqualTo(409);
  }

  @Test
  void illegalDeckCannotReceiveSuggestions() {
    DeckView d = prepare(new Scenario("invalid", "Ezuri, Renegade Leader", "general", false));
    decks.save(
        d.id(),
        new SaveRevision(
            d.revision(),
            d.commander().oracleId(),
            d.theme(),
            "Off color",
            List.of(new EntryInput(Fixture.card("Lightning Bolt").oracleId(), 1))));
    assertThatThrownBy(() -> recommendations.generate(d.id()))
        .isInstanceOf(ApiException.class)
        .extracting("status")
        .isEqualTo(422);
  }

  @Test
  void noUsefulCandidatesProducesAnEmptySuccessfulRun() {
    String subset =
        Fixture.sourceLine("Forest") + "\n" + Fixture.sourceLine("Ezuri, Renegade Leader");
    importer.importStream(
        "minimal",
        Instant.now(),
        new ByteArrayInputStream(subset.getBytes(StandardCharsets.UTF_8)),
        false);
    DeckView d =
        decks.create(
            new CreateDeck(
                "No candidate", Fixture.card("Ezuri, Renegade Leader").oracleId(), "general"));
    d =
        decks.save(
            d.id(),
            new SaveRevision(
                d.revision(),
                d.commander().oracleId(),
                d.theme(),
                "",
                List.of(new EntryInput(Fixture.card("Forest").oracleId(), 99))));
    assertThat(recommendations.generate(d.id()).suggestions()).isEmpty();
  }

  @Test
  void missingApiKeyFallsBackOnceAndRetainsRules() {
    DeckView d = prepare(new Scenario("fallback", "Meren of Clan Nel Toth", "graveyard", false));
    RecommendationRun run = recommendations.generate(d.id());
    RecommendationRun first = recommendations.explain(run.id()),
        second = recommendations.explain(run.id());
    assertThat(first.explanationStatus()).isEqualTo("fallback");
    assertThat(second).isEqualTo(first);
    assertThat(first.suggestions())
        .allMatch(s -> s.aiExplanation() == null && !s.ruleExplanation().isBlank());
  }
}
