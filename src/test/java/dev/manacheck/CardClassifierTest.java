package dev.manacheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class CardClassifierTest {
  private final CardClassifier classifier = new CardClassifier();
  private final JsonMapper json = JsonMapper.builder().build();

  // Named examples quote the pinned Oracle text in the repository's demo source and
  // docs/HAIKU-REVIEW.md. All other source fields are synthetic; no catalog is imported.
  @Test
  void guardianProjectDrawsFromNontokenCreaturesWithoutATokensTheme() {
    assertThat(
            tags(
                "Enchantment",
                "Whenever a nontoken creature you control enters, if it doesn't have the same name"
                    + " as another creature you control or a creature card in your graveyard, draw a"
                    + " card."))
        .containsExactlyInAnyOrder("draw", "graveyard");
  }

  @Test
  void signInBloodHasAnActualThirdPersonDrawInstruction() {
    assertThat(tags("Sorcery", "Target player draws two cards and loses 2 life."))
        .containsExactly("draw");
  }

  @Test
  void lifeFromTheLoamRecursLandsWithoutDrawing() {
    assertThat(
            tags(
                "Sorcery",
                """
                Return up to three target land cards from your graveyard to your hand.
                Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)
                """))
        .containsExactly("graveyard");
  }

  @Test
  void generousGiftPreservesBothInteractionAndActualTokenCreation() {
    assertThat(
            tags(
                "Instant",
                "Destroy target permanent. Its controller creates a 3/3 green Elephant creature"
                    + " token."))
        .containsExactlyInAnyOrder("interaction", "tokens");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Create two 1/1 white Soldier creature tokens.",
        "Whenever a nontoken creature you control dies, create a Treasure token."
      })
  void trueTokenMentionsRemainTagged(String oracle) {
    assertThat(tags("Enchantment", oracle)).containsExactly("tokens");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Whenever a nontoken creature you control dies, you gain 1 life.",
        "Nontoken creatures you control get +1/+1."
      })
  void nontokenMentionsAloneDoNotCreateATokensTheme(String oracle) {
    assertThat(tags("Enchantment", oracle)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "When this creature enters, you may discard a card. If you do, draw a card.",
        "If you do, you may draw a card.",
        "At the beginning of your upkeep, if you control a creature, draw a card.",
        "You may draw a card. If you do, discard a card.",
        "Whenever a creature you control dies, you gain 1 life and draw a card."
      })
  void conditionalActualDrawInstructionsRemainTagged(String oracle) {
    assertThat(tags("Enchantment", oracle)).containsExactly("draw");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Whenever you draw a card, you gain 1 life.",
        "Whenever an opponent draws two cards, that player loses 2 life.",
        "When you draw a card, you may gain 1 life.",
        "If you would draw a card, exile the top card of your library instead.",
        "If a player would draw two cards, that player mills two cards instead.",
        "If you would draw a card except the first one you draw in each of your draw steps, skip that draw instead."
      })
  void drawConditionsAndReplacementsWithoutActualDrawInstructionsAreNotDraw(String oracle) {
    assertThat(tags("Enchantment", oracle)).doesNotContain("draw");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Whenever you draw a card, you gain 1 life.\n{2}: Draw a card.",
        "If you would draw a card, exile the top card of your library instead.\n{T}: Draw a card.",
        "If you would draw a card, draw two cards instead.",
        "Whenever you draw a card, you may draw a card."
      })
  void anActualDrawStillCountsAlongsideDrawConditions(String oracle) {
    assertThat(tags("Enchantment", oracle)).containsExactly("draw");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Dredge 3 (If you would draw a card (including the first one), mill three cards instead.)",
        "Ability (Reminder (nested reminder), draw a card.)",
        "Ability (Reminder (draw a card), then discard a card.)"
      })
  void nestedReminderTextDoesNotSupplyADrawInstruction(String oracle) {
    assertThat(tags("Sorcery", oracle)).doesNotContain("draw");
  }

  @Test
  void realDrawAfterNestedReminderTextStillCounts() {
    assertThat(
            tags(
                "Sorcery", "Ability (Reminder (nested reminder), do nothing.) Draw two cards."))
        .containsExactly("draw");
  }

  @Test
  void llanowarVisionaryRetainsRampAndDrawOverlap() {
    assertThat(
            tags(
                "Creature — Elf Druid", "When this creature enters, draw a card.\n{T}: Add {G}."))
        .containsExactlyInAnyOrder("draw", "ramp");
  }

  @Test
  void inspiringCallRetainsCountersAndDrawOverlap() {
    assertThat(
            tags(
                "Instant",
                "Draw a card for each creature you control with a +1/+1 counter on it. Those"
                    + " creatures gain indestructible until end of turn. (Damage and effects that"
                    + " say \"destroy\" don't destroy them.)"))
        .containsExactlyInAnyOrder("counters", "draw");
  }

  @Test
  void basicLandRetainsLandWithoutBecomingRamp() {
    assertThat(tags("Basic Land — Forest", "({T}: Add {G}.)")).containsExactly("land");
  }

  @Test
  void libraryLandSearchRetainsRamp() {
    assertThat(
            tags(
                "Sorcery",
                "Search your library for a basic land card, put that card onto the battlefield"
                    + " tapped, then shuffle."))
        .containsExactly("ramp");
  }

  @Test
  void proliferateRetainsCounters() {
    assertThat(tags("Instant", "Proliferate.")).containsExactly("counters");
  }

  private Set<String> tags(String type, String oracle) {
    var source = json.createObjectNode();
    source.put("oracle_id", "00000000-0000-0000-0000-000000000001");
    source.put("name", "Synthetic classifier example");
    source.put("cmc", 2);
    source.put("type_line", type);
    source.put("oracle_text", oracle);
    source.putArray("color_identity");
    source.putArray("games").add("paper");
    source.putObject("legalities").put("commander", "legal");
    return classifier.classify(source).tags();
  }
}
