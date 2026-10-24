package dev.manacheck;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ApiContractTest extends PostgresTestBase {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Value("${manacheck.anthropic.model}") String configuredModel;

  @Test
  void cardSearchReturnsTypedCardsAndHonorsFiltersAndOffset() {
    JsonNode search =
        contract(request().queryParam("q", "Sol Ring").get("/api/cards"), 200, "card-search");
    assertThat(search.path("offset").intValue()).isZero();
    assertThat(search.path("cards").size()).isEqualTo(1);
    JsonNode card = search.path("cards").get(0);
    assertThat(card.path("oracleId").asText()).isEqualTo(oracleId("Sol Ring"));
    assertThat(card.path("manaValue").decimalValue()).isEqualByComparingTo("1");
    assertThat(card.path("faces").get(0).path("name").asText()).isEqualTo("Sol Ring");

    JsonNode commander =
        contract(
            request().queryParam("q", "Meren").queryParam("commanders", true).get("/api/cards"),
            200,
            "card-search");
    assertThat(commander.path("cards").size()).isEqualTo(1);
    assertThat(commander.path("cards").get(0).path("commanderEligible").booleanValue()).isTrue();
    JsonNode offColor =
        contract(
            request()
                .queryParam("q", "Swamp")
                .queryParam("commanderId", oracleId("Ezuri, Renegade Leader"))
                .get("/api/cards"),
            200,
            "card-search");
    assertThat(offColor.path("cards").size()).isZero();
    JsonNode page =
        contract(
            request().queryParam("q", "Sol Ring").queryParam("offset", 1).get("/api/cards"),
            200,
            "card-search");
    assertThat(page.path("offset").intValue()).isEqualTo(1);
    assertThat(page.path("cards").size()).isZero();
  }

  @Test
  void createdDecksAreRetrievableAndListedWithCurrentSummaryFields() {
    assertThat(get("/api/decks", "deck-list").size()).isZero();
    JsonNode first = create("Contract graveyard");
    assertThat(first.path("revision").intValue()).isEqualTo(1);
    assertThat(first.path("name").asText()).isEqualTo("Contract graveyard");
    assertThat(first.path("commander").path("oracleId").asText())
        .isEqualTo(oracleId("Meren of Clan Nel Toth"));
    assertThat(first.path("theme").asText()).isEqualTo("graveyard");
    assertThat(first.path("note").asText()).isEqualTo("Created deck");
    assertThat(first.path("entries").size()).isZero();
    assertThat(get(deckPath(first), "deck")).isEqualTo(first);

    JsonNode second = create("Contract second deck");
    JsonNode summaries = get("/api/decks", "deck-list");
    assertThat(summaries.size()).isEqualTo(2);
    assertThat(summaries.valueStream().map(n -> n.path("id").asText()).toList())
        .containsExactlyInAnyOrder(first.path("id").asText(), second.path("id").asText());
    for (JsonNode summary : summaries) {
      assertThat(summary.path("commanderName").asText()).isEqualTo("Meren of Clan Nel Toth");
      assertThat(summary.path("commanderImageUri"))
          .isEqualTo(first.path("commander").path("faces").get(0).path("imageUri"));
      assertThat(summary.path("revision").intValue()).isEqualTo(1);
      assertThat(summary.path("totalCards").intValue()).isEqualTo(1);
    }
  }

  @Test
  void savingReplacementSnapshotsRetainsEveryHistoricalFieldAndListsRevisions() {
    JsonNode first = create("Contract history");
    JsonNode second = save(first, "Meren of Clan Nel Toth", "graveyard", "First list",
        Map.of("Forest", 18, "Sol Ring", 1));
    JsonNode third = save(second, "Ezuri, Renegade Leader", "tokens", "New leader and list",
        Map.of("Forest", 20, "Command Tower", 1));
    assertThat(second.path("revision").intValue()).isEqualTo(2);
    assertThat(third.path("revision").intValue()).isEqualTo(3);
    assertThat(get(deckPath(first) + "/revisions/1", "deck")).isEqualTo(first);
    assertThat(get(deckPath(first) + "/revisions/2", "deck")).isEqualTo(second);
    assertThat(get(deckPath(first), "deck")).isEqualTo(third);
    assertThat(second.path("entries").size()).isEqualTo(2);
    assertThat(second.path("entries").get(0).path("quantity").intValue()).isEqualTo(18);
    assertThat(second.path("entries").get(1).path("card").path("name").asText())
        .isEqualTo("Sol Ring");

    JsonNode history = get(deckPath(first) + "/revisions", "revisions");
    assertThat(history.size()).isEqualTo(3);
    for (JsonNode snapshot : List.of(first, second, third)) {
      JsonNode summary = history.valueStream()
          .filter(n -> n.path("revision").equals(snapshot.path("revision")))
          .findFirst().orElseThrow();
      for (String field : List.of("theme", "note", "createdAt"))
        assertThat(summary.path(field)).as("revision %s %s", snapshot.path("revision"), field)
            .isEqualTo(snapshot.path(field));
    }
    JsonNode summaries = get("/api/decks", "deck-list");
    assertThat(summaries.size()).isEqualTo(1);
    assertThat(summaries.get(0).path("revision").intValue()).isEqualTo(3);
    assertThat(summaries.get(0).path("totalCards").intValue()).isEqualTo(22);
    assertThat(summaries.get(0).path("theme").asText()).isEqualTo("tokens");
  }

  @Test
  void differencesIncludeMembershipQuantitiesAndCommanderAndStaleSaveIsAtomic() {
    JsonNode first = create("Contract differences");
    JsonNode second = save(first, "Meren of Clan Nel Toth", "graveyard", "Before",
        Map.of("Forest", 18, "Sol Ring", 1));
    JsonNode third = save(second, "Ezuri, Renegade Leader", "general", "After",
        Map.of("Forest", 19, "Command Tower", 1));
    String path = deckPath(first);
    JsonNode diff = get(path + "/revisions/diff?from=2&to=3", "diff");
    assertThat(diff.size()).isEqualTo(5);
    assertDifference(diff, "Forest", 18, 19);
    assertDifference(diff, "Sol Ring", 1, 0);
    assertDifference(diff, "Command Tower", 0, 1);
    assertDifference(diff, "Meren of Clan Nel Toth", 1, 0);
    assertDifference(diff, "Ezuri, Renegade Leader", 0, 1);
    JsonNode reverse = get(path + "/revisions/diff?from=3&to=2", "diff");
    assertThat(reverse.size()).isEqualTo(5);
    for (JsonNode change : diff)
      assertDifference(reverse, change.path("name").asText(),
          change.path("afterQuantity").intValue(), change.path("beforeQuantity").intValue());
    assertThat(get(path + "/revisions/diff?from=3&to=3", "diff").size()).isZero();

    JsonNode problem = contract(post(path + "/revisions",
        revisionBody(second, "Meren of Clan Nel Toth", "graveyard", "Stale write",
            Map.of("Swamp", 1))), 409, "problem");
    assertThat(problem.path("type").asText("about:blank")).isEqualTo("about:blank");
    assertThat(problem.path("title").asText()).isEqualTo("Conflict");
    assertThat(problem.path("status").intValue()).isEqualTo(409);
    assertThat(problem.path("code").asText()).isEqualTo("STALE_REVISION");
    assertThat(problem.path("detail").asText()).isEqualTo("This deck has changed. Reload before saving.");
    assertThat(problem.path("instance").asText()).isEqualTo(path + "/revisions");
    assertThat(get(path, "deck")).isEqualTo(third);
    assertThat(get(path + "/revisions/2", "deck")).isEqualTo(second);
    assertThat(get(path + "/revisions", "revisions").size()).isEqualTo(3);
  }

  @Test
  void analysisReportsCountsFindingsRoleMapsAndFixtureCatalogProvenance() {
    JsonNode deck = save(create("Contract analysis"), "Meren of Clan Nel Toth", "graveyard",
        "Roles and curve", Map.of("Forest", 18, "Sol Ring", 1, "Read the Bones", 1));
    JsonNode analysis = get(deckPath(deck) + "/analysis", "analysis");
    assertThat(analysis.path("totalCards").intValue()).isEqualTo(21);
    assertThat(analysis.path("complete").booleanValue()).isFalse();
    assertThat(analysis.path("recommendationEligible").booleanValue()).isTrue();
    assertThat(analysis.path("errors").size()).isEqualTo(1);
    assertThat(analysis.path("errors").get(0).path("code").asText()).isEqualTo("CARD_COUNT");
    assertThat(analysis.path("errors").get(0).path("oracleId").isNull()).isTrue();
    assertThat(analysis.path("roles"))
        .isEqualTo(JSON.valueToTree(Map.of("land", 18, "ramp", 1, "draw", 1, "interaction", 0)));
    assertThat(analysis.path("targets"))
        .isEqualTo(JSON.valueToTree(Map.of("land", 36, "ramp", 10, "draw", 10, "interaction", 10)));
    assertThat(analysis.path("manaCurve")).isEqualTo(JSON.valueToTree(Map.of("1", 1, "3", 1)));
    assertThat(analysis.path("colors").path("G").intValue()).isEqualTo(18);
    assertThat(analysis.path("colors").path("B").intValue()).isEqualTo(1);
    assertThat(analysis.path("colors").path("C").intValue()).isEqualTo(1);
    assertThat(analysis.path("warnings").valueStream().map(n -> n.path("code").asText()).toList())
        .contains("ROLE_GAP");
    // These are fixture identities, not service-generated analysis expectations.
    assertThat(analysis.path("catalogImportId").asText()).isEqualTo(catalog.activeId().toString());
    assertThat(analysis.path("catalogUpdatedAt").asText()).isEqualTo("2026-10-21T21:02:01.935Z");
  }

  @Test
  void recommendationsApplyAValidatedSwapAndInvalidateTheOriginalRun() {
    JsonNode deck = save(create("Contract swap"), "Meren of Clan Nel Toth", "graveyard",
        "Complete land-heavy fixture", Map.of("Forest", 50, "Swamp", 49));
    assertThat(get(deckPath(deck) + "/analysis", "analysis").path("complete").booleanValue()).isTrue();
    JsonNode run = contract(post(deckPath(deck) + "/recommendations", Map.of()), 201, "recommendation");
    assertThat(run.path("deckId")).isEqualTo(deck.path("id"));
    assertThat(run.path("revision")).isEqualTo(deck.path("revision"));
    assertThat(run.path("catalogImportId").asText()).isEqualTo(catalog.activeId().toString());
    assertThat(run.path("explanationStatus").asText()).isEqualTo("rules");
    assertThat(run.path("suggestions").size()).isBetween(1, 5);
    assertThat(get(runPath(run), "recommendation")).isEqualTo(run);
    JsonNode suggestion = run.path("suggestions").get(0);
    assertThat(suggestion.path("cut").isNull()).isFalse();
    assertThat(suggestion.path("ruleExplanation").asText()).isNotBlank();
    JsonNode applied = contract(post(runPath(run) + "/apply",
        Map.of("suggestionId", suggestion.path("id").asText(), "expectedRevision", 2)), 201, "deck");
    assertThat(applied.path("revision").intValue()).isEqualTo(3);
    JsonNode diff = get(deckPath(deck) + "/revisions/diff?from=2&to=3", "diff");
    assertThat(diff.size()).isEqualTo(2);
    assertDifference(diff, suggestion.path("add").path("name").asText(), 0, 1);
    String cut = suggestion.path("cut").path("name").asText();
    int beforeQuantity = cut.equals("Forest") ? 50 : 49;
    assertDifference(diff, cut, beforeQuantity, beforeQuantity - 1);
    JsonNode analysis = get(deckPath(deck) + "/analysis", "analysis");
    assertThat(analysis.path("totalCards").intValue()).isEqualTo(100);
    assertThat(analysis.path("complete").booleanValue()).isTrue();
    assertThat(analysis.path("errors").size()).isZero();
    assertThat(get(deckPath(deck) + "/revisions/2", "deck")).isEqualTo(deck);
    assertThat(get(deckPath(deck), "deck")).isEqualTo(applied);
    JsonNode stale = contract(post(runPath(run) + "/apply",
        Map.of("suggestionId", suggestion.path("id").asText(), "expectedRevision", 3)), 409, "problem");
    assertThat(stale.path("code").asText()).isEqualTo("STALE_REVISION");
    assertThat(get(deckPath(deck), "deck")).isEqualTo(applied);
  }

  @Test
  void noKeyExplanationFallbackRetainsTheOriginalSuggestionsAndOutcome() {
    JsonNode deck = create("Contract fallback");
    JsonNode run = contract(post(deckPath(deck) + "/recommendations", Map.of()), 201, "recommendation");
    assertThat(run.path("suggestions").size()).isBetween(1, 5);
    // An incomplete deck produces additions; the complete-deck contract covers non-null cuts.
    for (JsonNode suggestion : run.path("suggestions"))
      assertThat(suggestion.path("cut").isNull()).isTrue();
    JsonNode fallback = contract(post(runPath(run) + "/explanations", Map.of()), 200, "recommendation");
    assertThat(fallback.path("explanationStatus").asText()).isEqualTo("fallback");
    assertThat(fallback.path("explanationDetail").asText())
        .isEqualTo("No API key configured; rule explanations retained");
    assertThat(fallback.path("model").asText()).isEqualTo(configuredModel);
    assertThat(fallback.path("usage").isObject()).isTrue();
    assertThat(fallback.path("usage").size()).isZero();
    for (String field : List.of("id", "deckId", "revision", "catalogImportId", "suggestions"))
      assertThat(fallback.path(field)).as("retained %s", field).isEqualTo(run.path(field));
    for (JsonNode suggestion : fallback.path("suggestions")) {
      assertThat(suggestion.path("aiExplanation").isNull()).isTrue();
      assertThat(suggestion.path("ruleExplanation").asText()).isNotBlank();
    }
    assertThat(get(runPath(run), "recommendation")).isEqualTo(fallback);
    assertThat(contract(post(runPath(run) + "/explanations", Map.of()), 200, "recommendation"))
        .isEqualTo(fallback);
    assertThat(get(deckPath(deck), "deck")).isEqualTo(deck);
  }

  @Test
  void schemaRegressionRejectsMissingFieldsWrongTypesAndUnexpectedNestedFields() {
    JsonNode valid = save(create("Contract schema regression"), "Meren of Clan Nel Toth",
        "graveyard", "Schema baseline", Map.of("Forest", 1));
    assertThat(matchesJsonSchemaInClasspath("schemas/deck.json").matches(valid.toString())).isTrue();
    ObjectNode missing = (ObjectNode) valid.deepCopy();
    ((ObjectNode) missing.path("commander")).remove("name");
    assertRejected(missing, "missing required nested card name");
    ObjectNode wrongType = (ObjectNode) valid.deepCopy();
    ((ObjectNode) wrongType.path("entries").get(0)).put("quantity", "1");
    assertRejected(wrongType, "string in place of integer quantity");
    ObjectNode wrongCardType = (ObjectNode) valid.deepCopy();
    ((ObjectNode) wrongCardType.path("commander")).put("manaValue", false);
    assertRejected(wrongCardType, "boolean in place of numeric mana value");
    ObjectNode extra = (ObjectNode) valid.deepCopy();
    ((ObjectNode) extra.path("commander").path("faces").get(0)).put("unexpected", true);
    assertRejected(extra, "unexpected nested face property");
    ObjectNode extraRoot = (ObjectNode) valid.deepCopy();
    extraRoot.put("unexpected", true);
    assertRejected(extraRoot, "unexpected top-level property");
  }

  private void assertRejected(JsonNode invalid, String reason) {
    assertThat(matchesJsonSchemaInClasspath("schemas/deck.json").matches(invalid.toString()))
        .as(reason).isFalse();
  }

  private RequestSpecification request() {
    return given().baseUri("http://127.0.0.1").port(port).accept(ContentType.JSON);
  }

  private Response post(String path, Object body) {
    // Explicit Jackson 3 serialization avoids REST Assured's automatic mapper selection.
    return request().contentType(ContentType.JSON).body(JSON.writeValueAsString(body)).post(path);
  }

  private JsonNode contract(Response response, int status, String schema) {
    String mediaType = schema.equals("problem") ? "application/problem+json" : "application/json";
    String body = response.then().statusCode(status).contentType(mediaType)
        .body(matchesJsonSchemaInClasspath("schemas/" + schema + ".json")).extract().asString();
    return JSON.readTree(body);
  }

  private JsonNode get(String path, String schema) {
    return contract(request().get(path), 200, schema);
  }

  private JsonNode create(String name) {
    return contract(post("/api/decks", Map.of("name", name,
        "commanderOracleId", oracleId("Meren of Clan Nel Toth"), "theme", "graveyard")), 201, "deck");
  }

  private JsonNode save(JsonNode previous, String commander, String theme, String note,
      Map<String, Integer> entries) {
    return contract(post(deckPath(previous) + "/revisions",
        revisionBody(previous, commander, theme, note, entries)), 201, "deck");
  }

  private Map<String, Object> revisionBody(JsonNode previous, String commander, String theme,
      String note, Map<String, Integer> entries) {
    return Map.of("expectedRevision", previous.path("revision").intValue(),
        "commanderOracleId", oracleId(commander), "theme", theme, "note", note,
        "entries", entries.entrySet().stream()
            .map(e -> Map.of("oracleId", oracleId(e.getKey()), "quantity", e.getValue())).toList());
  }

  private String oracleId(String name) {
    return Fixture.card(name).oracleId().toString();
  }

  private String deckPath(JsonNode deck) {
    return "/api/decks/" + deck.path("id").asText();
  }

  private String runPath(JsonNode run) {
    return "/api/recommendations/" + run.path("id").asText();
  }

  private void assertDifference(JsonNode diff, String name, int before, int after) {
    List<JsonNode> matches = diff.valueStream()
        .filter(n -> n.path("name").asText().equals(name)).toList();
    assertThat(matches).as("difference for %s", name).hasSize(1);
    JsonNode entry = matches.getFirst();
    assertThat(entry.path("oracleId").asText()).isEqualTo(oracleId(name));
    assertThat(entry.path("beforeQuantity").intValue()).isEqualTo(before);
    assertThat(entry.path("afterQuantity").intValue()).isEqualTo(after);
    assertThat(entry.path("delta").intValue()).isEqualTo(after - before);
  }
}
