package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class CardClassifier {
  public static final String RULES_VERSION = "roles-v4";
  public static final String CLASSIFIER_VERSION = "catalog-v3";
  // MH2 release notes: Grist is a creature during deck construction, despite its printed type.
  // https://magic.wizards.com/en/news/feature/modern-horizons-2-release-notes-2021-06-04
  private static final Set<UUID> COMMANDER_OVERRIDES =
      Set.of(UUID.fromString("0efb0d7e-dea0-4817-a243-15066e9ef333"));
  private static final Map<String, Integer> NUMBERS =
      Map.ofEntries(
          Map.entry("one", 1),
          Map.entry("two", 2),
          Map.entry("three", 3),
          Map.entry("four", 4),
          Map.entry("five", 5),
          Map.entry("six", 6),
          Map.entry("seven", 7),
          Map.entry("eight", 8),
          Map.entry("nine", 9),
          Map.entry("ten", 10));
  private static final Pattern LIMITED =
      Pattern.compile("a deck can have up to (\\w+) cards named", Pattern.CASE_INSENSITIVE);
  private static final Pattern DRAW =
      Pattern.compile(
          "\\bdraws?\\s+(?:a|one|two|three|four|five|six|seven|x|that many|[0-9]+)\\s+cards?\\b");
  private static final Pattern DRAW_CONDITION = Pattern.compile("\\b(?:when|whenever|if|would)\\b");
  private static final Pattern TOKEN = Pattern.compile("\\btokens?\\b");
  private static final Pattern MANA_IN_WORDS =
      Pattern.compile("\\badd (?:one|two|three|four|five|six|[0-9]+) mana\\b");
  private static final Pattern TAP_COST = Pattern.compile("^\\{[tq]\\}");
  private static final Pattern COST_SYMBOL = Pattern.compile("\\{([^}]+)\\}");
  private static final Pattern INTERACTION =
      Pattern.compile(
          "\\b(?:destroy (?:target|all)|exile target|counter target)\\b[^.;\\r\\n]*"
              + "|\\breturn target\\b[^.;\\r\\n]*owner.s hand");
  private static final Pattern SELF_TARGET =
      Pattern.compile("\\b(?:you (?:own|control)|your graveyard)\\b");
  private static final Pattern OPPONENT_TARGET =
      Pattern.compile("\\b(?:opponent|you don't (?:own|control))\\b");
  private static final Set<String> EXCLUDED_LAYOUTS =
      Set.of(
          "token",
          "double_faced_token",
          "emblem",
          "art_series",
          "reversible_card",
          "scheme",
          "plane",
          "vanguard");

  public boolean excluded(JsonNode node) {
    if (!node.hasNonNull("games") || !node.path("games").isArray()) return false;
    return EXCLUDED_LAYOUTS.contains(node.path("layout").asText()) || !paperIdentity(node);
  }

  private boolean paperIdentity(JsonNode node) {
    // Oracle legality describes the card identity; games describes the representative printing.
    // Oracle bulk can select an MTGO printing for paper cards such as Badlands and Black Lotus.
    return strings(node.path("games")).contains("paper")
        || Set.of("legal", "banned").contains(node.path("legalities").path("commander").asText());
  }

  public Card classify(JsonNode node) {
    for (String key :
        List.of("oracle_id", "name", "cmc", "type_line", "color_identity", "legalities", "games"))
      if (!node.hasNonNull(key)) throw ApiException.bad("Card is missing required field: " + key);
    if (!node.path("name").isString()
        || node.path("name").asText().isBlank()
        || !node.path("type_line").isString()
        || !node.path("cmc").isNumber()
        || !node.path("color_identity").isArray()
        || !node.path("games").isArray()
        || !node.path("legalities").path("commander").isString())
      throw ApiException.bad("Card has an invalid required field type");
    UUID id;
    try {
      id = UUID.fromString(node.path("oracle_id").asText());
    } catch (IllegalArgumentException e) {
      throw ApiException.bad("Invalid Oracle identity");
    }
    String type = node.path("type_line").asText();
    List<Face> faces = new ArrayList<>();
    if (node.path("card_faces").isArray()) {
      for (JsonNode f : node.path("card_faces"))
        faces.add(
            new Face(
                f.path("name").asText(),
                f.path("type_line").asText(type),
                f.path("oracle_text").asText(""),
                image(f)));
    } else
      faces.add(
          new Face(
              node.path("name").asText(), type, node.path("oracle_text").asText(""), image(node)));
    String oracle = node.path("oracle_text").asText("");
    if (oracle.isBlank()) oracle = String.join("\n", faces.stream().map(Face::oracleText).toList());
    String text = oracle.toLowerCase(Locale.ROOT);
    String frontType = faces.getFirst().typeLine();
    JsonNode front = node.path("card_faces").isArray() ? node.path("card_faces").get(0) : node;
    String frontText = faces.getFirst().oracleText().toLowerCase(Locale.ROOT);
    boolean eligible =
        (frontType.contains("Legendary")
                && (frontType.contains("Creature")
                    || frontType.contains("Vehicle")
                    || (frontType.contains("Spacecraft")
                        && front.hasNonNull("power")
                        && front.hasNonNull("toughness"))))
            || frontText.contains("can be your commander")
            || COMMANDER_OVERRIDES.contains(id);
    String unsupported = null;
    if (text.contains("choose a color") && text.contains("before the game begins"))
      unsupported = "Selectable color identity requires manual review";
    Integer limit = type.contains("Basic") && type.contains("Land") ? null : 1;
    if (text.contains("a deck can have any number of cards named")) limit = null;
    else {
      var m = LIMITED.matcher(text);
      if (m.find()) {
        String value = m.group(1);
        limit = NUMBERS.get(value);
        if (limit == null)
          try {
            limit = Integer.parseInt(value);
          } catch (NumberFormatException ignored) {
          }
        if (limit == null || limit < 1) {
          limit = 1;
          unsupported = "Unrecognized deck copy-limit exception";
        }
      } else if (text.contains("a deck can have"))
        unsupported = "Unrecognized deck construction exception";
    }
    List<String> basicTypes = new ArrayList<>();
    if (type.contains("Land"))
      for (String land : List.of("Plains", "Island", "Swamp", "Mountain", "Forest"))
        if (Pattern.compile("\\b" + land + "\\b").matcher(type).find()) basicTypes.add(land);
    List<String> identity = strings(node.path("color_identity"));
    if (!Set.of("W", "U", "B", "R", "G").containsAll(identity))
      throw ApiException.bad("Invalid color identity");
    BigDecimal mana = node.path("cmc").decimalValue();
    if (mana.signum() < 0) throw ApiException.bad("Negative mana value");
    return new Card(
        id,
        node.path("name").asText(),
        mana,
        type,
        oracle,
        identity,
        basicTypes,
        strings(node.path("keywords")),
        node.path("legalities").path("commander").asText(),
        paperIdentity(node),
        true,
        eligible,
        unsupported,
        limit,
        node.path("scryfall_uri").asText(""),
        faces,
        tags(type, text));
  }

  public Set<String> tags(String type, String text) {
    Set<String> tags = new TreeSet<>();
    if (type.contains("Land")) tags.add("land");
    if (!type.contains("Land")
        && (text.contains("add {")
            || hasUnpaidWordManaAbility(text)
            || text.matches("(?s).*search your library.*land.*onto the battlefield.*")))
      tags.add("ramp");
    if (hasDrawInstruction(text)) tags.add("draw");
    if (hasInteraction(text)) tags.add("interaction");
    // Standalone token mentions retain the theme without matching "nontoken".
    if (TOKEN.matcher(text).find()) tags.add("tokens");
    if (text.contains("+1/+1 counter") || text.contains("proliferate")) tags.add("counters");
    if (text.contains("graveyard")) tags.add("graveyard");
    return tags;
  }

  private boolean hasUnpaidWordManaAbility(String text) {
    // Recognize direct tap-for-mana wording. A paid mana filter or a granted land
    // ability does not establish acceleration. More complex mana engines remain heuristic.
    for (String line : withoutReminderText(text).split("\\R")) {
      int colon = line.indexOf(':');
      if (colon < 0) continue;
      String cost = line.substring(0, colon).strip();
      if (!TAP_COST.matcher(cost).find()
          || !MANA_IN_WORDS.matcher(line.substring(colon + 1)).find()) continue;
      var symbols = COST_SYMBOL.matcher(cost);
      boolean paysMana = false;
      while (symbols.find()) if (!Set.of("t", "q").contains(symbols.group(1))) paysMana = true;
      if (!paysMana) return true;
    }
    return false;
  }

  private boolean hasInteraction(String text) {
    // Bounded target-clause heuristic. Self-only recursion, bounce and removal do not
    // establish opposing interaction. A separate unrestricted/opponent effect still counts.
    var effects = INTERACTION.matcher(withoutReminderText(text));
    while (effects.find()) {
      String effect = effects.group();
      if (!SELF_TARGET.matcher(effect).find() || OPPONENT_TARGET.matcher(effect).find())
        return true;
    }
    return false;
  }

  private boolean hasDrawInstruction(String text) {
    // Bounded Oracle wording heuristic: ignore reminders and draw conditions, then look for
    // an instruction. Commas separate "Whenever you draw ..." / "If you would draw ..."
    // from their effects, while "If you do, draw a card" retains its actual instruction.
    // This does not interpret arbitrary Oracle grammar or keyword-defined draw effects.
    for (String clause : withoutReminderText(text).split("[.,;:\\r\\n]")) {
      var draw = DRAW.matcher(clause);
      while (draw.find())
        if (!DRAW_CONDITION.matcher(clause.substring(0, draw.start())).find()) return true;
    }
    return false;
  }

  private String withoutReminderText(String text) {
    StringBuilder rules = new StringBuilder();
    int depth = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(') {
        if (depth == 0) rules.append(' ');
        depth++;
      } else if (c == ')' && depth > 0) depth--;
      else if (depth == 0) rules.append(c);
    }
    return rules.toString();
  }

  private String image(JsonNode n) {
    return n.path("image_uris").path("normal").asText("");
  }

  public static List<String> strings(JsonNode node) {
    List<String> list = new ArrayList<>();
    if (node.isArray()) node.forEach(n -> list.add(n.asText()));
    return List.copyOf(list);
  }
}
