package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RecommendationService {
  private final JdbcClient jdbc;
  private final CatalogRepository catalog;
  private final DeckService decks;
  private final DeckValidator validator;
  private final HaikuExplainer haiku;
  private final JsonMapper json;
  private final String rankingSql;

  public RecommendationService(
      JdbcClient jdbc,
      CatalogRepository catalog,
      DeckService decks,
      DeckValidator validator,
      HaikuExplainer haiku,
      JsonMapper json)
      throws IOException {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.decks = decks;
    this.validator = validator;
    this.haiku = haiku;
    this.json = json;
    rankingSql =
        new ClassPathResource("sql/recommendations.sql").getContentAsString(StandardCharsets.UTF_8);
  }

  public JdbcClient.StatementSpec rankedQuery(DeckView deck) {
    var targets = validator.targets();
    return jdbc.sql(rankingSql)
        .param("revision_id", decks.revisionId(deck.id(), deck.revision()))
        .param("commander_id", deck.commander().oracleId())
        .param("identity", CatalogRepository.array(deck.commander().colorIdentity()))
        .param("theme", deck.theme())
        .param("land_target", targets.get("land"))
        .param("ramp_target", targets.get("ramp"))
        .param("draw_target", targets.get("draw"))
        .param("interaction_target", targets.get("interaction"));
  }

  @Transactional
  public RecommendationRun generate(UUID deckId) {
    UUID active = catalog.lockActive();
    if (active == null) throw ApiException.bad("Import a card catalog first");
    DeckView deck = decks.get(deckId);
    Analysis analysis = validator.validate(deck, active, catalog.updatedAt());
    if (!analysis.recommendationEligible())
      throw ApiException.bad("Resolve deck construction errors before requesting recommendations");
    List<Candidate> candidates =
        rankedQuery(deck)
            .query((r, n) -> new Candidate(r.getObject("oracle_id", UUID.class), r.getInt("score")))
            .list();
    Map<UUID, Card> found = catalog.cards(candidates.stream().map(Candidate::oracleId).toList());
    List<Suggestion> suggestions = new ArrayList<>();
    for (Candidate candidate : candidates) {
      Card add = found.get(candidate.oracleId()), cut = null;
      if (deck.totalCards() == 100) {
        cut =
            deck.entries().stream()
                .map(DeckEntry::card)
                .filter(c -> !c.oracleId().equals(add.oracleId()))
                .filter(
                    c ->
                        !c.land() || analysis.roles().get("land") > validator.targets().get("land"))
                .filter(c -> protectsCoverage(c, add, analysis))
                .filter(c -> candidate.score() > cutValue(c, deck, analysis))
                .sorted(
                    Comparator.<Card>comparingInt(c -> cutValue(c, deck, analysis))
                        .thenComparing(Card::manaValue, Comparator.reverseOrder())
                        .thenComparing(Card::name))
                .findFirst()
                .orElse(null);
        if (cut == null) continue;
      }
      DeckView simulated = simulate(deck, add, cut);
      if (!validator.validate(simulated, active, catalog.updatedAt()).recommendationEligible())
        continue;
      List<String> reasons = new ArrayList<>();
      for (String role : validator.targets().keySet())
        if (add.tags().contains(role) && analysis.roles().get(role) < validator.targets().get(role))
          reasons.add("fills the " + role + " gap");
      if (!deck.theme().equals("general") && add.tags().contains(deck.theme()))
        reasons.add("matches the " + deck.theme() + " theme");
      if (reasons.isEmpty()) reasons.add("offers a lower-cost option for the mana curve");
      String rationale = String.join("; ", reasons) + ".";
      rationale = Character.toUpperCase(rationale.charAt(0)) + rationale.substring(1);
      suggestions.add(
          new Suggestion(
              "s" + (suggestions.size() + 1), add, cut, rationale, null, candidate.score()));
      if (suggestions.size() == 5) break;
    }
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO"
                + " recommendation_run(id,deck_id,revision_number,catalog_import_id,suggestions)"
                + " VALUES(:id,:deck,:revision,:catalog,CAST(:suggestions AS jsonb))")
        .param("id", id)
        .param("deck", deckId)
        .param("revision", deck.revision())
        .param("catalog", active)
        .param("suggestions", json.writeValueAsString(suggestions))
        .update();
    return get(id);
  }

  private boolean protectsCoverage(Card cut, Card add, Analysis analysis) {
    for (String role : validator.targets().keySet())
      if (cut.tags().contains(role)
          && !add.tags().contains(role)
          && analysis.roles().get(role) <= validator.targets().get(role)) return false;
    return true;
  }

  private int cutValue(Card c, DeckView deck, Analysis analysis) {
    int value = c.manaValue().doubleValue() <= 3 ? 2 : 0;
    if (!deck.theme().equals("general") && c.tags().contains(deck.theme())) value += 4;
    for (String role : validator.targets().keySet())
      if (c.tags().contains(role) && analysis.roles().get(role) < validator.targets().get(role))
        value += 6;
    return value;
  }

  public DeckView simulate(DeckView deck, Card add, Card cut) {
    Map<UUID, DeckEntry> entries = new LinkedHashMap<>();
    for (DeckEntry e : deck.entries()) entries.put(e.card().oracleId(), e);
    if (cut != null) {
      DeckEntry existing = entries.get(cut.oracleId());
      if (existing == null) throw ApiException.bad("Suggested cut is absent from this deck");
      if (existing.quantity() == 1) entries.remove(cut.oracleId());
      else entries.put(cut.oracleId(), new DeckEntry(cut, existing.quantity() - 1));
    }
    DeckEntry existing = entries.get(add.oracleId());
    entries.put(add.oracleId(), new DeckEntry(add, existing == null ? 1 : existing.quantity() + 1));
    return new DeckView(
        deck.id(),
        deck.name(),
        deck.revision(),
        deck.commander(),
        deck.theme(),
        deck.note(),
        List.copyOf(entries.values()),
        deck.createdAt());
  }

  @Transactional
  public DeckView apply(UUID runId, ApplyRequest input) {
    RecommendationRun run = get(runId);
    UUID currentCatalog = catalog.lockActive();
    int current =
        jdbc.sql("SELECT current_revision FROM deck WHERE id=:id FOR UPDATE")
            .param("id", run.deckId())
            .query(Integer.class)
            .single();
    if (current != input.expectedRevision()
        || current != run.revision()
        || !Objects.equals(currentCatalog, run.catalogImportId()))
      throw ApiException.conflict("The deck or catalog changed. Generate fresh recommendations.");
    Suggestion s =
        run.suggestions().stream()
            .filter(x -> x.id().equals(input.suggestionId()))
            .findFirst()
            .orElseThrow(() -> ApiException.bad("Unknown suggestion"));
    DeckView before = decks.get(run.deckId()), after = simulate(before, s.add(), s.cut());
    Analysis validation = validator.validate(after, currentCatalog, catalog.updatedAt());
    if (!validation.recommendationEligible())
      throw ApiException.bad("This suggestion no longer passes construction checks");
    return decks.save(
        run.deckId(),
        new SaveRevision(
            current,
            before.commander().oracleId(),
            before.theme(),
            "Applied suggestion: "
                + s.add().name()
                + (s.cut() == null ? "" : " for " + s.cut().name()),
            after.entries().stream()
                .map(e -> new EntryInput(e.card().oracleId(), e.quantity()))
                .toList()));
  }

  public RecommendationRun explain(UUID id) {
    RecommendationRun run = get(id);
    if (!Objects.equals(catalog.activeId(), run.catalogImportId())
        || decks.get(run.deckId()).revision() != run.revision())
      throw ApiException.conflict(
          "The deck or catalog changed. Generate fresh recommendations before requesting"
              + " explanations.");
    if (run.suggestions().isEmpty()) return run;
    int claimed =
        jdbc.sql(
                "UPDATE recommendation_run SET explanation_status='pending' WHERE id=:id AND"
                    + " explanation_status='rules'")
            .param("id", id)
            .update();
    if (claimed == 0) return get(id);
    HaikuExplainer.Result result;
    try {
      result = haiku.explain(decks.get(run.deckId(), run.revision()), run);
    } catch (Exception e) {
      result =
          new HaikuExplainer.Result(
              Map.of(),
              "fallback",
              "Explanation unavailable; rule explanations retained",
              null,
              Map.of());
    }
    List<Suggestion> annotated = new ArrayList<>();
    for (Suggestion s : run.suggestions())
      annotated.add(
          new Suggestion(
              s.id(),
              s.add(),
              s.cut(),
              s.ruleExplanation(),
              result.explanations().get(s.id()),
              s.score()));
    jdbc.sql(
            "UPDATE recommendation_run SET suggestions=CAST(:suggestions AS"
                + " jsonb),explanation_status=:status,explanation_detail=:detail,model=:model,usage=CAST(:usage"
                + " AS jsonb) WHERE id=:id")
        .param("id", id)
        .param("suggestions", json.writeValueAsString(annotated))
        .param("status", result.status())
        .param("detail", result.detail())
        .param("model", result.model(), java.sql.Types.VARCHAR)
        .param("usage", json.writeValueAsString(result.usage()))
        .update();
    return get(id);
  }

  @SuppressWarnings("unchecked")
  public RecommendationRun get(UUID id) {
    return jdbc.sql("SELECT * FROM recommendation_run WHERE id=:id")
        .param("id", id)
        .query(
            (r, n) ->
                new RecommendationRun(
                    r.getObject("id", UUID.class),
                    r.getObject("deck_id", UUID.class),
                    r.getInt("revision_number"),
                    r.getObject("catalog_import_id", UUID.class),
                    List.of(json.readValue(r.getString("suggestions"), Suggestion[].class)),
                    r.getString("explanation_status"),
                    r.getString("explanation_detail"),
                    r.getString("model"),
                    r.getString("usage") == null
                        ? Map.of()
                        : json.readValue(r.getString("usage"), Map.class)))
        .optional()
        .orElseThrow(() -> ApiException.missing("Recommendation run not found"));
  }
}
