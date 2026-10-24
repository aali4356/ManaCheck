package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class DeckValidatorTest {
  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
  private static final UUID IMPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DeckValidator validator = new DeckValidator(36, 10, 10, 10, CLOCK);

  @ParameterizedTest
  @CsvSource({
    "1, 0, false, true, CARD_COUNT",
    "99, 98, false, true, CARD_COUNT",
    "100, 99, true, true, none",
    "101, 100, false, false, CARD_COUNT"
  })
  void commanderCountsTowardCompletionAndOnlyUndersizeMayRemainEligible(
      int total, int mainCount, boolean complete, boolean eligible, String error) {
    DeckView deck =
        mainCount == 0
            ? deck(commander("G"))
            : deck(commander("G"), new DeckEntry(basicLand(), mainCount));
    Analysis analysis = analyze(deck);
    assertThat(analysis.totalCards()).isEqualTo(total);
    assertThat(analysis.complete()).isEqualTo(complete);
    assertThat(analysis.recommendationEligible()).isEqualTo(eligible);
    assertErrors(analysis, error);
  }

  @Test
  void offColorSpellBlocksRecommendationsEvenForAnUndersizedDraft() {
    TestCard spell = new TestCard("Blue spell");
    spell.colors = List.of("U");
    Analysis analysis = analyze(deck(commander("G"), new DeckEntry(spell.build(), 1)));
    assertThat(analysis.totalCards()).isEqualTo(2);
    assertThat(analysis.complete()).isFalse();
    assertThat(analysis.recommendationEligible()).isFalse();
    assertThat(analysis.errors())
        .extracting(Finding::code, Finding::oracleId)
        .containsExactlyInAnyOrder(tuple("COLOR_IDENTITY", spell.id), tuple("CARD_COUNT", null));
  }

  @Test
  void multifaceCardRequiresItsCombinedIdentityEvenWhenTheFrontFaceFits() {
    TestCard spell = new TestCard("White front // Red back");
    spell.colors = List.of("W", "R");
    spell.faces =
        List.of(
            new Face("White front", "Creature", "White rules", ""),
            new Face("Red back", "Creature", "Red rules", ""));
    DeckEntry entry = new DeckEntry(spell.build(), 1);
    Analysis rejected = analyze(deck(commander("W"), new DeckEntry(basicLand(), 98), entry));
    assertErrors(rejected, "COLOR_IDENTITY");
    assertThat(rejected.errors().getFirst().oracleId()).isEqualTo(spell.id);
    assertThat(rejected.recommendationEligible()).isFalse();
    Analysis accepted = analyze(deck(commander("W", "R"), new DeckEntry(basicLand(), 98), entry));
    assertThat(accepted.errors()).isEmpty();
    assertThat(accepted.complete()).isTrue();
    assertThat(accepted.recommendationEligible()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"Plains, W", "Island, U", "Swamp, B", "Mountain, R", "Forest, G"})
  void intrinsicLandTypeNeedsItsColorEvenWithEmptyCardIdentity(String type, String color) {
    TestCard land = new TestCard("Intrinsic " + type);
    land.type = "Land — " + type;
    land.basicTypes = List.of(type);
    land.tags = Set.of("land");
    DeckEntry entry = new DeckEntry(land.build(), 1);
    Analysis rejected = analyze(deck(commander(), new DeckEntry(basicLand(), 98), entry));
    assertErrors(rejected, "LAND_TYPE");
    assertThat(rejected.errors().getFirst().oracleId()).isEqualTo(land.id);
    assertThat(rejected.recommendationEligible()).isFalse();
    Analysis accepted = analyze(deck(commander(color), new DeckEntry(basicLand(), 98), entry));
    assertThat(accepted.errors()).isEmpty();
    assertThat(accepted.complete()).isTrue();
  }

  static Stream<Arguments> dualLandIdentities() {
    return Stream.of(
        Arguments.of(List.of("G", "U"), 0),
        Arguments.of(List.of("G"), 1),
        Arguments.of(List.of("U"), 1),
        Arguments.of(List.of(), 2));
  }

  @ParameterizedTest
  @MethodSource("dualLandIdentities")
  void everyTypeOfADualLandMustFitTheCommander(List<String> identity, int offColorTypes) {
    TestCard dual = new TestCard("Forest Island");
    dual.type = "Land — Forest Island";
    dual.basicTypes = List.of("Forest", "Island");
    dual.tags = Set.of("land");
    Analysis analysis =
        analyze(
            deck(
                commander(identity.toArray(String[]::new)),
                new DeckEntry(basicLand(), 98),
                new DeckEntry(dual.build(), 1)));
    assertThat(analysis.errors()).hasSize(offColorTypes);
    assertThat(analysis.errors())
        .allSatisfy(
            finding -> {
              assertThat(finding.code()).isEqualTo("LAND_TYPE");
              assertThat(finding.oracleId()).isEqualTo(dual.id);
            });
    assertThat(analysis.complete()).isEqualTo(offColorTypes == 0);
    assertThat(analysis.recommendationEligible()).isEqualTo(offColorTypes == 0);
  }

  @Test
  void singletonAllowanceUsesOracleIdentityAcrossSeparateCardObjectsAndRows() {
    TestCard first = new TestCard("First printing");
    TestCard second = new TestCard("Another printing");
    second.id = first.id;
    Analysis legal =
        analyze(
            deck(commander("G"), new DeckEntry(basicLand(), 98), new DeckEntry(first.build(), 1)));
    assertThat(legal.errors()).isEmpty();
    assertThat(legal.complete()).isTrue();
    Analysis duplicate =
        analyze(
            deck(
                commander("G"),
                new DeckEntry(basicLand(), 97),
                new DeckEntry(first.build(), 1),
                new DeckEntry(second.build(), 1)));
    assertErrors(duplicate, "COPY_LIMIT");
    assertThat(duplicate.errors().getFirst().oracleId()).isEqualTo(first.id);
    assertThat(duplicate.recommendationEligible()).isFalse();
  }

  @Test
  void commanderUsesItsAllowanceAndLegalityIsReportedOnceWhenRepeatedInMainDeck() {
    TestCard commander = new TestCard("Banned commander");
    commander.commander = true;
    commander.legality = "banned";
    Card card = commander.build();
    Analysis analysis =
        analyze(
            deck(
                card,
                new DeckEntry(basicLand(), 97),
                new DeckEntry(card, 1),
                new DeckEntry(card, 1)));
    assertThat(analysis.totalCards()).isEqualTo(100);
    assertThat(analysis.errors())
        .extracting(Finding::code, Finding::oracleId)
        .containsExactlyInAnyOrder(
            tuple("LEGALITY", commander.id), tuple("COPY_LIMIT", commander.id));
    assertThat(analysis.complete()).isFalse();
    assertThat(analysis.recommendationEligible()).isFalse();
  }

  @Test
  void splitRowsDoNotRepeatCardConstructionFindings() {
    TestCard invalid = new TestCard("Retired off-color card");
    invalid.active = false;
    invalid.paper = false;
    invalid.legality = "banned";
    invalid.colors = List.of("U");
    invalid.unsupported = "Unrecognized construction exception";
    Card card = invalid.build();
    Analysis analysis =
        analyze(
            deck(
                commander("G"),
                new DeckEntry(basicLand(), 97),
                new DeckEntry(card, 1),
                new DeckEntry(card, 1)));
    assertThat(analysis.errors())
        .extracting(Finding::code, Finding::oracleId)
        .containsExactlyInAnyOrder(
            tuple("INACTIVE", invalid.id),
            tuple("LEGALITY", invalid.id),
            tuple("COLOR_IDENTITY", invalid.id),
            tuple("UNSUPPORTED", invalid.id),
            tuple("COPY_LIMIT", invalid.id));
    assertThat(analysis.recommendationEligible()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void basicsAndExplicitUnlimitedExceptionsCanFillTheMainDeck(boolean basic) {
    TestCard unlimited = new TestCard("Any-number creature");
    unlimited.copyLimit = null;
    Card card = basic ? basicLand() : unlimited.build();
    Analysis analysis =
        analyze(deck(commander("G"), new DeckEntry(card, 40), new DeckEntry(card, 59)));
    assertThat(analysis.totalCards()).isEqualTo(100);
    assertThat(analysis.errors()).isEmpty();
    assertThat(analysis.complete()).isTrue();
    assertThat(analysis.recommendationEligible()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"5, 90, true, none", "6, 89, false, COPY_LIMIT"})
  void numberedExceptionAllowsExactlyNineCopiesAcrossSplitRows(
      int secondQuantity, int lands, boolean eligible, String error) {
    TestCard bounded = new TestCard("Nine-copy creature");
    bounded.copyLimit = 9;
    Card card = bounded.build();
    Analysis analysis =
        analyze(
            deck(
                commander("G"),
                new DeckEntry(basicLand(), lands),
                new DeckEntry(card, 4),
                new DeckEntry(card, secondQuantity)));
    assertThat(analysis.totalCards()).isEqualTo(100);
    assertErrors(analysis, error);
    assertThat(analysis.complete()).isEqualTo(eligible);
    assertThat(analysis.recommendationEligible()).isEqualTo(eligible);
  }

  @ParameterizedTest
  @CsvSource({"2, 97, true, none", "3, 96, false, COPY_LIMIT"})
  void commanderConsumesOneCopyOfAnExplicitBoundedAllowance(
      int mainCopies, int lands, boolean eligible, String error) {
    TestCard bounded = new TestCard("Three-copy commander");
    bounded.commander = true;
    bounded.copyLimit = 3;
    Card card = bounded.build();
    Analysis analysis =
        analyze(deck(card, new DeckEntry(basicLand(), lands), new DeckEntry(card, mainCopies)));
    assertThat(analysis.totalCards()).isEqualTo(100);
    assertErrors(analysis, error);
    assertThat(analysis.complete()).isEqualTo(eligible);
    assertThat(analysis.recommendationEligible()).isEqualTo(eligible);
  }

  @ParameterizedTest
  @CsvSource({
    "true, ineligible, COMMANDER",
    "true, unsupported, UNSUPPORTED",
    "false, unsupported, UNSUPPORTED",
    "true, banned, LEGALITY",
    "false, banned, LEGALITY",
    "true, not-legal, LEGALITY",
    "false, not-legal, LEGALITY",
    "true, non-paper, LEGALITY",
    "false, non-paper, LEGALITY",
    "true, inactive, INACTIVE",
    "false, inactive, INACTIVE"
  })
  void constructionFailuresBlockCompleteDecksForCommanderAndEntries(
      boolean asCommander, String status, String error) {
    TestCard invalid = new TestCard("Invalid selection");
    invalid.commander = asCommander;
    switch (status) {
      case "ineligible" -> invalid.commander = false;
      case "unsupported" -> invalid.unsupported = "Selectable identity requires manual review";
      case "banned" -> invalid.legality = "banned";
      case "not-legal" -> invalid.legality = "not_legal";
      case "non-paper" -> invalid.paper = false;
      case "inactive" -> invalid.active = false;
      default -> throw new IllegalArgumentException(status);
    }
    Analysis analysis =
        asCommander
            ? analyze(deck(invalid.build(), new DeckEntry(basicLand(), 99)))
            : analyze(
                deck(
                    commander("G"),
                    new DeckEntry(basicLand(), 98),
                    new DeckEntry(invalid.build(), 1)));
    assertThat(analysis.totalCards()).isEqualTo(100);
    assertErrors(analysis, error);
    assertThat(analysis.errors().getFirst().oracleId()).isEqualTo(invalid.id);
    if (status.equals("unsupported"))
      assertThat(analysis.errors().getFirst().message())
          .contains("Selectable identity requires manual review");
    assertThat(analysis.complete()).isFalse();
    assertThat(analysis.recommendationEligible()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"0, 100, none", "-2, 98, CARD_COUNT"})
  void nonpositiveRowsCannotCancelCopiesOrSubtractMainDeckCoverage(
      int quantity, int total, String countError) {
    TestCard spell = new TestCard("Overlapping spell");
    spell.colors = List.of("G");
    spell.tags = Set.of("ramp", "draw", "interaction");
    Card card = spell.build();
    Analysis analysis =
        analyze(
            deck(
                commander("G"),
                new DeckEntry(basicLand(), 97),
                new DeckEntry(card, 2),
                new DeckEntry(card, quantity)));
    assertThat(analysis.totalCards()).isEqualTo(total);
    assertThat(analysis.errors())
        .extracting(Finding::code)
        .containsExactlyInAnyOrderElementsOf(
            countError.equals("none")
                ? List.of("COPY_LIMIT", "QUANTITY")
                : List.of("COPY_LIMIT", "QUANTITY", "CARD_COUNT"));
    assertThat(analysis.roles())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("land", 97, "ramp", 2, "draw", 2, "interaction", 2));
    assertThat(analysis.manaCurve()).containsExactlyInAnyOrderEntriesOf(Map.of("2", 2));
    assertThat(analysis.colors())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("W", 0, "U", 0, "B", 0, "R", 0, "G", 2, "C", 97));
    assertThat(analysis.complete()).isFalse();
    assertThat(analysis.recommendationEligible()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void nonpositiveRowsContributeNoRoleCurveOrColorBuckets(int quantity) {
    TestCard spell = new TestCard("Uncountable spell");
    spell.mana = "6";
    spell.colors = List.of("G");
    spell.tags = Set.of("ramp", "draw", "interaction");
    Analysis analysis = analyze(deck(commander("G"), new DeckEntry(spell.build(), quantity)));
    assertThat(analysis.errors())
        .extracting(Finding::code)
        .containsExactlyInAnyOrder("QUANTITY", "CARD_COUNT");
    assertThat(analysis.roles().values()).containsOnly(0);
    assertThat(analysis.manaCurve()).isEmpty();
    assertThat(analysis.colors().values()).containsOnly(0);
    assertThat(analysis.recommendationEligible()).isFalse();
  }

  @Test
  void overlappingRolesCurveAndColorsAreWeightedForMainDeckEntriesOnly() {
    TestCard commander = new TestCard("Role-rich commander");
    commander.commander = true;
    commander.mana = "9";
    commander.colors = List.of("G", "B");
    commander.tags = Set.of("land", "ramp", "draw", "interaction");
    TestCard forest = new TestCard("Basic Forest");
    forest.type = "Basic Land — Forest";
    forest.basicTypes = List.of("Forest");
    forest.colors = List.of("G");
    forest.mana = "0";
    forest.copyLimit = null;
    forest.tags = Set.of("land");
    TestCard overlap = new TestCard("Overlapping creature");
    overlap.mana = "2.75";
    overlap.colors = List.of("G", "B");
    overlap.copyLimit = null;
    overlap.tags = Set.of("ramp", "draw", "interaction");
    TestCard costly = new TestCard("Expensive interaction");
    costly.mana = "7.9";
    costly.colors = List.of("B");
    costly.copyLimit = null;
    costly.tags = Set.of("interaction");
    TestCard colorless = new TestCard("Large colorless creature");
    colorless.mana = "9";
    Card spell = overlap.build();
    Analysis analysis =
        new DeckValidator(92, 4, 5, 6, CLOCK)
            .validate(
                deck(
                    commander.build(),
                    new DeckEntry(basicLand(), 89),
                    new DeckEntry(forest.build(), 3),
                    new DeckEntry(spell, 1),
                    new DeckEntry(spell, 3),
                    new DeckEntry(costly.build(), 2),
                    new DeckEntry(colorless.build(), 1)),
                IMPORT_ID,
                NOW);
    assertThat(analysis.totalCards()).isEqualTo(100);
    assertThat(analysis.errors()).isEmpty();
    assertThat(analysis.complete()).isTrue();
    assertThat(analysis.recommendationEligible()).isTrue();
    assertThat(analysis.roles())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("land", 92, "ramp", 4, "draw", 4, "interaction", 6));
    assertThat(analysis.manaCurve()).containsExactlyInAnyOrderEntriesOf(Map.of("2", 4, "7+", 3));
    assertThat(analysis.colors())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("W", 0, "U", 0, "B", 6, "R", 0, "G", 7, "C", 90));
    assertThat(analysis.warnings())
        .containsExactly(new Finding("ROLE_GAP", "draw: 4 of heuristic target 5", null));
  }

  @ParameterizedTest
  @CsvSource({
    "2026-10-16T11:59:59.999999999Z, true",
    "2026-10-16T12:00:00Z, false",
    "2026-10-16T12:00:00.000000001Z, false",
    "2026-10-24T12:00:00Z, false"
  })
  void catalogAgeWarnsOnlyBeyondSevenDaysAndPreservesSourceProvenance(
      Instant updatedAt, boolean stale) {
    Analysis analysis =
        new DeckValidator(0, 0, 0, 0, CLOCK)
            .validate(deck(commander("G"), new DeckEntry(basicLand(), 99)), IMPORT_ID, updatedAt);
    assertThat(analysis.catalogImportId()).isEqualTo(IMPORT_ID);
    assertThat(analysis.catalogUpdatedAt()).isEqualTo(updatedAt);
    assertThat(analysis.warnings())
        .extracting(Finding::code)
        .containsExactlyElementsOf(stale ? List.of("CATALOG_AGE") : List.of());
    assertThat(analysis.errors()).isEmpty();
    assertThat(analysis.complete()).isTrue();
    assertThat(analysis.recommendationEligible()).isTrue();
  }

  @Test
  void absentCatalogProvenanceRemainsAbsentWithoutInventingAnAge() {
    Analysis analysis = validator.validate(deck(commander("G")), null, null);
    assertThat(analysis.catalogImportId()).isNull();
    assertThat(analysis.catalogUpdatedAt()).isNull();
    assertThat(analysis.warnings()).extracting(Finding::code).doesNotContain("CATALOG_AGE");
    assertErrors(analysis, "CARD_COUNT");
    assertThat(analysis.recommendationEligible()).isTrue();
  }

  @Test
  void returnedFindingsAndAllMapsRejectCallerMutation() {
    DeckView deck = deck(commander("G"), new DeckEntry(new TestCard("Spell").build(), 1));
    Analysis analysis = analyze(deck);
    for (Map<String, Integer> map :
        List.of(
            validator.targets(),
            analysis.targets(),
            analysis.roles(),
            analysis.manaCurve(),
            analysis.colors())) {
      assertThatThrownBy(() -> map.put("caller", 999))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> map.entrySet().iterator().next().setValue(999))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> map.keySet().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
    assertThatThrownBy(() -> analysis.errors().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> analysis.warnings().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(analyze(deck)).isEqualTo(analysis);
    assertThat(validator.targets())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("land", 36, "ramp", 10, "draw", 10, "interaction", 10));
  }

  @Test
  void springUsesThePublicConstructorAndConfiguredTargets() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test-targets",
                  Map.of(
                      "manacheck.targets.land",
                      35,
                      "manacheck.targets.ramp",
                      8,
                      "manacheck.targets.draw",
                      9,
                      "manacheck.targets.interaction",
                      11)));
      context.register(DeckValidator.class);
      context.refresh();
      DeckValidator springValidator = context.getBean(DeckValidator.class);
      assertThat(springValidator.targets())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of("land", 35, "ramp", 8, "draw", 9, "interaction", 11));
      assertThat(
              springValidator
                  .validate(deck(commander("G"), new DeckEntry(basicLand(), 99)), IMPORT_ID, null)
                  .complete())
          .isTrue();
    }
  }

  // Retain classifier regressions until the integrator intentionally moves them.
  @Test
  void gristIsEligibleThroughItsDeckConstructionAbility() {
    Card grist = Fixture.card("Grist, the Hunger Tide");
    assertThat(grist.typeLine()).contains("Planeswalker").doesNotContain("Creature");
    assertThat(grist.commanderEligible()).isTrue();
    assertThat(analyze(deck(grist)).errors()).extracting(Finding::code).doesNotContain("COMMANDER");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Legendary Creature — Bear",
        "Legendary Artifact — Vehicle",
        "Legendary Artifact — Spacecraft",
        "Artifact"
      })
  void currentCommanderTypeRulesAreCovered(String type) {
    var source =
        (tools.jackson.databind.node.ObjectNode)
            Fixture.JSON.readTree(Fixture.sourceLine("Sol Ring"));
    source.put("type_line", type);
    source.put("power", "3");
    source.put("toughness", "3");
    assertThat(new CardClassifier().classify(source).commanderEligible())
        .isEqualTo(type.startsWith("Legendary"));
  }

  @Test
  void spacecraftUsesTheFrontFacePowerBox() {
    var source =
        (tools.jackson.databind.node.ObjectNode)
            Fixture.JSON.readTree(Fixture.sourceLine("Sol Ring"));
    source.put("type_line", "Legendary Artifact — Spacecraft // Land");
    var face = source.putArray("card_faces").addObject();
    face.put("name", "Test spacecraft");
    face.put("type_line", "Legendary Artifact — Spacecraft");
    face.put("power", "3");
    face.put("toughness", "3");
    assertThat(new CardClassifier().classify(source).commanderEligible()).isTrue();
  }

  @Test
  void classifierIgnoresExtraFieldsAndRejectsMissingRequiredData() {
    CardClassifier classifier = new CardClassifier();
    var node = Fixture.JSON.readTree(Fixture.sourceLine("Forest")).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) node).put("future_optional_field", "safe");
    assertThat(classifier.classify(node).name()).isEqualTo("Forest");
    ((tools.jackson.databind.node.ObjectNode) node).remove("oracle_id");
    assertThatThrownBy(() -> classifier.classify(node)).isInstanceOf(ApiException.class);
  }

  private Analysis analyze(DeckView deck) {
    return validator.validate(deck, IMPORT_ID, NOW);
  }

  private static void assertErrors(Analysis analysis, String error) {
    assertThat(analysis.errors())
        .extracting(Finding::code)
        .containsExactlyElementsOf(error.equals("none") ? List.of() : List.of(error));
  }

  private static DeckView deck(Card commander, DeckEntry... entries) {
    return new DeckView(
        IMPORT_ID, "Synthetic deck", 1, commander, "general", "", List.of(entries), NOW);
  }

  private static Card commander(String... colors) {
    TestCard commander = new TestCard("Synthetic commander");
    commander.type = "Legendary Creature";
    commander.commander = true;
    commander.colors = List.of(colors);
    return commander.build();
  }

  private static Card basicLand() {
    TestCard land = new TestCard("Colorless basic land");
    land.type = "Basic Land";
    land.mana = "0";
    land.copyLimit = null;
    land.tags = Set.of("land");
    return land.build();
  }

  // Catalog facts are stated directly; validator expectations do not depend on the classifier.
  private static final class TestCard {
    UUID id = UUID.randomUUID();
    final String name;
    String mana = "2";
    String type = "Artifact";
    List<String> colors = List.of();
    List<String> basicTypes = List.of();
    List<Face> faces = List.of();
    String legality = "legal";
    boolean paper = true;
    boolean active = true;
    boolean commander;
    String unsupported;
    Integer copyLimit = 1;
    Set<String> tags = Set.of();

    TestCard(String name) {
      this.name = name;
    }

    Card build() {
      return new Card(
          id,
          name,
          new BigDecimal(mana),
          type,
          "",
          colors,
          basicTypes,
          List.of(),
          legality,
          paper,
          active,
          commander,
          unsupported,
          copyLimit,
          "",
          faces,
          tags);
    }
  }
}
