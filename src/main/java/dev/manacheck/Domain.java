package dev.manacheck;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class Domain {
  private Domain() {}

  public static final Set<String> THEMES = Set.of("general", "tokens", "counters", "graveyard");

  public record Face(String name, String typeLine, String oracleText, String imageUri) {}

  public record Card(
      UUID oracleId,
      String name,
      BigDecimal manaValue,
      String typeLine,
      String oracleText,
      List<String> colorIdentity,
      List<String> basicLandTypes,
      List<String> keywords,
      String commanderLegality,
      boolean paper,
      boolean active,
      boolean commanderEligible,
      String unsupportedReason,
      Integer copyLimit,
      String scryfallUri,
      List<Face> faces,
      Set<String> tags) {
    public boolean land() {
      return tags.contains("land");
    }
  }

  public record EntryInput(UUID oracleId, int quantity) {}

  public record DeckEntry(Card card, int quantity) {}

  public record CreateDeck(String name, UUID commanderOracleId, String theme) {}

  public record SaveRevision(
      int expectedRevision,
      UUID commanderOracleId,
      String theme,
      String note,
      List<EntryInput> entries) {}

  public record DeckView(
      UUID id,
      String name,
      int revision,
      Card commander,
      String theme,
      String note,
      List<DeckEntry> entries,
      Instant createdAt) {
    public int totalCards() {
      return 1 + entries.stream().mapToInt(DeckEntry::quantity).sum();
    }
  }

  public record DeckSummary(
      UUID id,
      String name,
      String commanderName,
      String commanderImageUri,
      String theme,
      int revision,
      int totalCards,
      Instant updatedAt) {}

  public record Finding(String code, String message, UUID oracleId) {}

  public record Analysis(
      int totalCards,
      boolean complete,
      boolean recommendationEligible,
      List<Finding> errors,
      List<Finding> warnings,
      Map<String, Integer> roles,
      Map<String, Integer> targets,
      Map<String, Integer> manaCurve,
      Map<String, Integer> colors,
      UUID catalogImportId,
      Instant catalogUpdatedAt) {}

  public record PreviewRequest(String text, UUID commanderOracleId) {}

  public record PreviewLine(int line, String input, Card card, int quantity) {}

  public record ImportPreview(
      List<EntryInput> entries, List<PreviewLine> lines, List<String> errors) {}

  public record DiffEntry(
      UUID oracleId, String name, int beforeQuantity, int afterQuantity, int delta) {}

  public record RevisionSummary(int revision, String theme, String note, Instant createdAt) {}

  public record Suggestion(
      String id, Card add, Card cut, String ruleExplanation, String aiExplanation, int score) {}

  public record RecommendationRun(
      UUID id,
      UUID deckId,
      int revision,
      UUID catalogImportId,
      List<Suggestion> suggestions,
      String explanationStatus,
      String explanationDetail,
      String model,
      Map<String, Object> usage) {}

  public record ApplyRequest(String suggestionId, int expectedRevision) {}

  public record CatalogImport(
      UUID id,
      String source,
      Instant sourceUpdatedAt,
      String status,
      int recordCount,
      int skippedCount,
      String error,
      Instant startedAt,
      Instant completedAt) {}

  public record CatalogStatus(
      UUID activeImportId,
      String activeSource,
      Instant sourceUpdatedAt,
      long activeCards,
      List<CatalogImport> recentImports) {}

  public record Candidate(UUID oracleId, int score) {}
}
