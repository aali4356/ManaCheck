package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeckValidator {
  private static final Map<String, String> LAND_COLORS =
      Map.of("Plains", "W", "Island", "U", "Swamp", "B", "Mountain", "R", "Forest", "G");
  private final Map<String, Integer> targets;
  private final Clock clock;

  @Autowired
  public DeckValidator(
      @Value("${manacheck.targets.land:36}") int land,
      @Value("${manacheck.targets.ramp:10}") int ramp,
      @Value("${manacheck.targets.draw:10}") int draw,
      @Value("${manacheck.targets.interaction:10}") int interaction) {
    this(land, ramp, draw, interaction, Clock.systemUTC());
  }

  DeckValidator(int land, int ramp, int draw, int interaction, Clock clock) {
    Map<String, Integer> configured = new LinkedHashMap<>();
    configured.put("land", land);
    configured.put("ramp", ramp);
    configured.put("draw", draw);
    configured.put("interaction", interaction);
    targets = freeze(configured);
    this.clock = clock;
  }

  public Map<String, Integer> targets() {
    return targets;
  }

  public Analysis validate(DeckView deck, UUID importId, Instant updatedAt) {
    List<Finding> errors = new ArrayList<>(), warnings = new ArrayList<>();
    Card commander = deck.commander();
    Map<UUID, Card> cards = new LinkedHashMap<>();
    cards.put(commander.oracleId(), commander);
    for (DeckEntry entry : deck.entries()) cards.putIfAbsent(entry.card().oracleId(), entry.card());

    checkConstruction(commander, cards.values(), errors);
    checkCopyAllowances(deck, cards.values(), errors);
    Composition composition = mainDeckComposition(deck, errors);
    addDeckFindings(composition, updatedAt, errors, warnings);
    boolean eligible =
        errors.isEmpty()
            || (composition.total() < 100
                && errors.stream().allMatch(e -> e.code().equals("CARD_COUNT")));
    return new Analysis(
        composition.total(),
        errors.isEmpty(),
        eligible,
        List.copyOf(errors),
        List.copyOf(warnings),
        freeze(composition.roles()),
        targets(),
        freeze(composition.curve()),
        freeze(composition.colors()),
        importId,
        updatedAt);
  }

  private void checkConstruction(Card commander, Collection<Card> cards, List<Finding> errors) {
    if (!commander.commanderEligible())
      errors.add(
          new Finding("COMMANDER", "Selected card cannot be a commander", commander.oracleId()));
    for (Card card : cards) checkCard(card, commander, errors);
  }

  private void checkCopyAllowances(DeckView deck, Collection<Card> cards, List<Finding> errors) {
    Map<UUID, Integer> copies = new HashMap<>();
    copies.put(deck.commander().oracleId(), 1);
    for (DeckEntry entry : deck.entries())
      if (entry.quantity() > 0)
        copies.merge(entry.card().oracleId(), entry.quantity(), Integer::sum);
    for (Card card : cards)
      if (card.copyLimit() != null && copies.getOrDefault(card.oracleId(), 0) > card.copyLimit())
        errors.add(
            new Finding(
                "COPY_LIMIT",
                card.name() + " exceeds its copy limit of " + card.copyLimit(),
                card.oracleId()));
  }

  private Composition mainDeckComposition(DeckView deck, List<Finding> errors) {
    Map<String, Integer> roles = new LinkedHashMap<>(),
        curve = new TreeMap<>(),
        colors = new LinkedHashMap<>();
    targets.keySet().forEach(r -> roles.put(r, 0));
    for (String c : List.of("W", "U", "B", "R", "G", "C")) colors.put(c, 0);
    int total = 1;
    for (DeckEntry entry : deck.entries()) {
      Card card = entry.card();
      int quantity = entry.quantity();
      // Preserve DeckView's reported count even for invalid drafts.
      total += quantity;
      if (quantity <= 0) {
        errors.add(new Finding("QUANTITY", "Card quantities must be positive", card.oracleId()));
        continue;
      }
      for (String role : roles.keySet())
        if (card.tags().contains(role)) roles.merge(role, quantity, Integer::sum);
      if (!card.land())
        curve.merge(
            card.manaValue().intValue() >= 7 ? "7+" : String.valueOf(card.manaValue().intValue()),
            quantity,
            Integer::sum);
      if (card.colorIdentity().isEmpty()) colors.merge("C", quantity, Integer::sum);
      else for (String c : card.colorIdentity()) colors.merge(c, quantity, Integer::sum);
    }
    return new Composition(total, roles, curve, colors);
  }

  private void addDeckFindings(
      Composition composition, Instant updatedAt, List<Finding> errors, List<Finding> warnings) {
    if (composition.total() != 100)
      errors.add(
          new Finding(
              "CARD_COUNT",
              "Deck contains "
                  + composition.total()
                  + " cards; a complete Commander deck contains 100",
              null));
    for (var role : targets.entrySet())
      if (composition.roles().get(role.getKey()) < role.getValue())
        warnings.add(
            new Finding(
                "ROLE_GAP",
                role.getKey()
                    + ": "
                    + composition.roles().get(role.getKey())
                    + " of heuristic target "
                    + role.getValue(),
                null));
    if (updatedAt != null && updatedAt.isBefore(clock.instant().minus(Duration.ofDays(7))))
      warnings.add(
          new Finding(
              "CATALOG_AGE",
              "Catalog snapshot is more than seven days old; refresh before relying on legality",
              null));
  }

  private void checkCard(Card card, Card commander, List<Finding> errors) {
    if (!card.active())
      errors.add(
          new Finding(
              "INACTIVE", card.name() + " is absent from the current catalog", card.oracleId()));
    if (!card.paper() || !"legal".equals(card.commanderLegality()))
      errors.add(
          new Finding(
              "LEGALITY",
              card.name() + " is not Commander-legal in this snapshot",
              card.oracleId()));
    if (!commander.colorIdentity().containsAll(card.colorIdentity()))
      errors.add(
          new Finding(
              "COLOR_IDENTITY",
              card.name() + " is outside the commander's color identity",
              card.oracleId()));
    for (String type : card.basicLandTypes())
      if (!commander.colorIdentity().contains(LAND_COLORS.get(type)))
        errors.add(
            new Finding(
                "LAND_TYPE", card.name() + " has an off-color basic land type", card.oracleId()));
    if (card.unsupportedReason() != null)
      errors.add(
          new Finding(
              "UNSUPPORTED", card.name() + ": " + card.unsupportedReason(), card.oracleId()));
  }

  private static Map<String, Integer> freeze(Map<String, Integer> values) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  private record Composition(
      int total,
      Map<String, Integer> roles,
      Map<String, Integer> curve,
      Map<String, Integer> colors) {}
}
