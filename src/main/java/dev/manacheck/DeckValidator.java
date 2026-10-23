package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeckValidator {
  private static final Map<String, String> LAND_COLORS =
      Map.of("Plains", "W", "Island", "U", "Swamp", "B", "Mountain", "R", "Forest", "G");
  private final Map<String, Integer> targets;

  public DeckValidator(
      @Value("${manacheck.targets.land:36}") int land,
      @Value("${manacheck.targets.ramp:10}") int ramp,
      @Value("${manacheck.targets.draw:10}") int draw,
      @Value("${manacheck.targets.interaction:10}") int interaction) {
    targets = new LinkedHashMap<>();
    targets.put("land", land);
    targets.put("ramp", ramp);
    targets.put("draw", draw);
    targets.put("interaction", interaction);
  }

  public Map<String, Integer> targets() {
    return Collections.unmodifiableMap(targets);
  }

  public Analysis validate(DeckView deck, UUID importId, Instant updatedAt) {
    List<Finding> errors = new ArrayList<>(), warnings = new ArrayList<>();
    Card commander = deck.commander();
    if (!commander.commanderEligible())
      errors.add(
          new Finding("COMMANDER", "Selected card cannot be a commander", commander.oracleId()));
    checkCard(commander, commander, warnings, errors);
    Map<UUID, Integer> copies = new HashMap<>();
    copies.put(commander.oracleId(), 1);
    Map<String, Integer> roles = new LinkedHashMap<>(),
        curve = new TreeMap<>(),
        colors = new LinkedHashMap<>();
    targets.keySet().forEach(r -> roles.put(r, 0));
    for (String c : List.of("W", "U", "B", "R", "G", "C")) colors.put(c, 0);
    int total = 1;
    for (DeckEntry entry : deck.entries()) {
      Card card = entry.card();
      int quantity = entry.quantity();
      total += quantity;
      if (quantity <= 0)
        errors.add(new Finding("QUANTITY", "Card quantities must be positive", card.oracleId()));
      copies.merge(card.oracleId(), quantity, Integer::sum);
      checkCard(card, commander, warnings, errors);
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
    for (DeckEntry e : deck.entries())
      if (e.card().copyLimit() != null && copies.get(e.card().oracleId()) > e.card().copyLimit())
        errors.add(
            new Finding(
                "COPY_LIMIT",
                e.card().name() + " exceeds its copy limit of " + e.card().copyLimit(),
                e.card().oracleId()));
    if (total != 100)
      errors.add(
          new Finding(
              "CARD_COUNT",
              "Deck contains " + total + " cards; a complete Commander deck contains 100",
              null));
    for (var role : targets.entrySet())
      if (roles.get(role.getKey()) < role.getValue())
        warnings.add(
            new Finding(
                "ROLE_GAP",
                role.getKey()
                    + ": "
                    + roles.get(role.getKey())
                    + " of heuristic target "
                    + role.getValue(),
                null));
    if (updatedAt != null && updatedAt.isBefore(Instant.now().minusSeconds(7 * 86400)))
      warnings.add(
          new Finding(
              "CATALOG_AGE",
              "Catalog snapshot is more than seven days old; refresh before relying on legality",
              null));
    boolean eligible = total <= 100 && errors.stream().allMatch(e -> e.code().equals("CARD_COUNT"));
    return new Analysis(
        total,
        errors.isEmpty(),
        eligible,
        List.copyOf(errors),
        List.copyOf(warnings),
        roles,
        targets(),
        curve,
        colors,
        importId,
        updatedAt);
  }

  private void checkCard(Card card, Card commander, List<Finding> warnings, List<Finding> errors) {
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
}
