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
                    + " as another creature you control or a creature card in your graveyard, draw"
                    + " a card."))
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
        "If you would draw a card except the first one you draw in each of your draw steps, skip"
            + " that draw instead."
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
    assertThat(tags("Sorcery", "Ability (Reminder (nested reminder), do nothing.) Draw two cards."))
        .containsExactly("draw");
  }

  @Test
  void llanowarVisionaryRetainsRampAndDrawOverlap() {
    assertThat(
            tags("Creature — Elf Druid", "When this creature enters, draw a card.\n{T}: Add {G}."))
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

  @Test
  void lazotepQuarryRecursionIsNotOpponentInteraction() {
    assertThat(
            tags(
                "Land — Desert",
                "{T}: Add {C}.\n"
                    + "{T}, Sacrifice a creature: Add one mana of any color.\n"
                    + "{X}{2}, {T}, Sacrifice a Desert: Exile target creature card with mana value"
                    + " X from your graveyard. Create a token that's a copy of it, except it's a"
                    + " 4/4 black Zombie. Activate only as a sorcery."))
        .containsExactlyInAnyOrder("land", "tokens", "graveyard");
  }

  @Test
  void staffOfCompleationKeepsItsOtherRolesWithoutSelfRemovalInteraction() {
    assertThat(
            tags(
                "Artifact",
                "{T}, Pay 1 life: Destroy target permanent you own.\n"
                    + "{T}, Pay 2 life: Add one mana of any color.\n"
                    + "{T}, Pay 3 life: Proliferate.\n{T}, Pay 4 life: Draw a card.\n"
                    + "{5}: Untap this artifact."))
        .containsExactlyInAnyOrder("ramp", "draw", "counters");
  }

  @Test
  void allyEncampmentSelfBounceDoesNotSupplyInteraction() {
    assertThat(
            tags(
                "Land",
                "{T}: Add {C}.\n{T}: Add one mana of any color. Spend this mana only to cast an"
                    + " Ally spell.\n{1}, {T}, Sacrifice this land: Return target Ally you control"
                    + " to its owner's hand."))
        .containsExactly("land");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Exile target creature card from your graveyard.",
        "Destroy target creature you control.",
        "Destroy all creatures you control.",
        "Counter target spell you control.",
        "Return target creature you own to its owner's hand."
      })
  void effectsRestrictedToYourOwnResourcesDoNotSupplyInteraction(String oracle) {
    assertThat(tags("Instant", oracle)).doesNotContain("interaction");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Destroy target permanent.",
        "Destroy all creatures.",
        "Destroy all creatures you don't control.",
        "Exile target creature you don't control.",
        "Exile target card from a graveyard.",
        "Counter target spell.",
        "Return target creature to its owner's hand.",
        "Exile target creature card from your graveyard.\nDestroy target artifact.",
        "Return target creature you control to its owner's hand. Exile target creature.",
        "Destroy target artifact you control and target artifact you don't control."
      })
  void opponentAndUnrestrictedEffectsStillSupplyInteraction(String oracle) {
    assertThat(tags("Instant", oracle)).contains("interaction");
  }

  @Test
  void selectableIdentityFromTheCatalogStillRequiresReview() {
    assertThat(Fixture.card("The Prismatic Piper").unsupportedReason())
        .contains("Selectable color identity");
  }

  @Test
  void arcaneSignetManaWrittenInWordsSuppliesRamp() {
    assertThat(
            tags("Artifact", "{T}: Add one mana of any color in your commander's color identity."))
        .containsExactly("ramp");
  }

  @Test
  void multipleManaWrittenInWordsSuppliesRamp() {
    assertThat(tags("Artifact", "{T}: Add two mana of any one color.")).containsExactly("ramp");
  }

  @Test
  void addingACounterDoesNotSupplyManaRamp() {
    assertThat(tags("Artifact", "Add one +1/+1 counter to target creature."))
        .containsExactly("counters");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{1}, {T}, Sacrifice this artifact: Add one mana of any color.\n"
            + "When this artifact is put into a graveyard from the battlefield, draw a card.",
        "{1}, {T}: Add one mana of any color.",
        "{T}, {1}: Add one mana of any color.",
        "Enchant land\nWhen this Aura enters, draw a card.\n"
            + "Enchanted land has \"{T}: Add one mana of any color.\""
      })
  void manaFilteringAndGrantingALandFixingDoNotEstablishWordManaRamp(String oracle) {
    assertThat(tags("Artifact", oracle)).doesNotContain("ramp");
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
