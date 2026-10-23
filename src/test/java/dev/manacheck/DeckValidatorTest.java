package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeckValidatorTest {
  @Test
  void gristIsEligibleThroughItsDeckConstructionAbility() {
    Card grist = Fixture.card("Grist, the Hunger Tide");
    assertThat(grist.typeLine()).contains("Planeswalker").doesNotContain("Creature");
    assertThat(grist.commanderEligible()).isTrue();
    assertThat(analyze(Fixture.deck("Grist, the Hunger Tide")).errors())
        .extracting(Finding::code)
        .doesNotContain("COMMANDER");
  }

  private final DeckValidator validator = new DeckValidator(36, 10, 10, 10);

  private Analysis analyze(DeckView d) {
    return validator.validate(d, UUID.randomUUID(), Instant.now());
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

  @ParameterizedTest
  @ValueSource(ints = {99, 100, 101})
  void countBoundaries(int total) {
    Analysis a =
        analyze(Fixture.deck("Ezuri, Renegade Leader", Fixture.entry("Forest", total - 1)));
    assertThat(a.totalCards()).isEqualTo(total);
    assertThat(a.complete()).isEqualTo(total == 100);
    assertThat(a.recommendationEligible()).isEqualTo(total <= 100);
  }

  @Test
  void duplicateNonbasicCardsAreRejectedAcrossOneOracleIdentity() {
    assertThat(
            analyze(Fixture.deck("Ezuri, Renegade Leader", Fixture.entry("Sol Ring", 2))).errors())
        .extracting(Finding::code)
        .contains("COPY_LIMIT");
  }

  @Test
  void commanderCannotAlsoOccupyAMainDeckSlot() {
    assertThat(
            analyze(
                    Fixture.deck(
                        "Ezuri, Renegade Leader", Fixture.entry("Ezuri, Renegade Leader", 1)))
                .errors())
        .extracting(Finding::code)
        .contains("COPY_LIMIT");
  }

  @Test
  void backFaceContributesToColorIdentity() {
    Analysis a =
        analyze(
            Fixture.deck(
                "Losheel, Clockwork Scholar",
                Fixture.entry("Archangel Avacyn // Avacyn, the Purifier", 1)));
    assertThat(a.errors()).extracting(Finding::code).contains("COLOR_IDENTITY");
  }

  @Test
  void landTypesAreCheckedEvenWhenTheColorIdentityIsEmpty() {
    Card c = Fixture.card("Swamp");
    Card modified =
        new Card(
            c.oracleId(),
            c.name(),
            c.manaValue(),
            c.typeLine(),
            c.oracleText(),
            List.of(),
            c.basicLandTypes(),
            c.keywords(),
            c.commanderLegality(),
            true,
            true,
            false,
            null,
            null,
            c.scryfallUri(),
            c.faces(),
            c.tags());
    assertThat(analyze(Fixture.deck("Ezuri, Renegade Leader", new DeckEntry(modified, 1))).errors())
        .extracting(Finding::code)
        .contains("LAND_TYPE");
  }

  @Test
  void unlimitedAndNumberedCopyExceptionsAreHonored() {
    assertThat(
            analyze(Fixture.deck("Meren of Clan Nel Toth", Fixture.entry("Relentless Rats", 20)))
                .errors())
        .extracting(Finding::code)
        .doesNotContain("COPY_LIMIT");
    assertThat(analyze(Fixture.deck("Meren of Clan Nel Toth", Fixture.entry("Nazgûl", 9))).errors())
        .extracting(Finding::code)
        .doesNotContain("COPY_LIMIT");
    assertThat(
            analyze(Fixture.deck("Meren of Clan Nel Toth", Fixture.entry("Nazgûl", 10))).errors())
        .extracting(Finding::code)
        .contains("COPY_LIMIT");
  }

  @Test
  void selectableCommanderIdentitiesRequireReview() {
    assertThat(analyze(Fixture.deck("The Prismatic Piper")).errors())
        .extracting(Finding::code)
        .contains("UNSUPPORTED");
  }

  @Test
  void nonCommanderSelectionIsRejected() {
    assertThat(analyze(Fixture.deck("Sol Ring")).errors())
        .extracting(Finding::code)
        .contains("COMMANDER");
  }

  @Test
  void roleOverlapIsVisibleAndQuantitiesAreWeighted() {
    Analysis a =
        analyze(
            Fixture.deck(
                "Meren of Clan Nel Toth",
                Fixture.entry("Forest", 18),
                Fixture.entry("Swamp", 18),
                Fixture.entry("Solemn Simulacrum", 1)));
    assertThat(a.roles())
        .containsEntry("land", 36)
        .containsEntry("ramp", 1)
        .containsEntry("draw", 1);
    assertThat(a.manaCurve()).containsEntry("4", 1);
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
}
