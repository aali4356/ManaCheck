package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final CatalogRepository catalog;
  private final CatalogImporter importer;
  private final DeckService decks;
  private final RecommendationService recommendations;

  public ApiController(
      CatalogRepository catalog,
      CatalogImporter importer,
      DeckService decks,
      RecommendationService recommendations) {
    this.catalog = catalog;
    this.importer = importer;
    this.decks = decks;
    this.recommendations = recommendations;
  }

  @GetMapping("/cards")
  public Map<String, Object> cards(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "false") boolean commanders,
      @RequestParam(required = false) UUID commanderId,
      @RequestParam(defaultValue = "0") int offset) {
    if (q.length() > 120 || offset < 0 || offset > 100_000)
      throw ApiException.bad("Invalid search query or offset");
    return Map.of("cards", catalog.search(q, commanders, commanderId, offset), "offset", offset);
  }

  @GetMapping("/catalog/imports")
  public CatalogStatus catalog() {
    return catalog.status();
  }

  @PostMapping("/catalog/imports")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, UUID> importCatalog() {
    return Map.of("id", importer.startScryfall());
  }

  @GetMapping("/catalog/imports/{id}")
  public CatalogImport importStatus(@PathVariable UUID id) {
    return catalog.importRun(id);
  }

  @GetMapping("/decks")
  public List<DeckSummary> decks() {
    return decks.list();
  }

  @PostMapping("/decks")
  @ResponseStatus(HttpStatus.CREATED)
  public DeckView create(@RequestBody CreateDeck input) {
    return decks.create(input);
  }

  @GetMapping("/decks/{id}")
  public DeckView deck(@PathVariable UUID id) {
    return decks.get(id);
  }

  @GetMapping("/decks/{id}/revisions")
  public List<RevisionSummary> revisions(@PathVariable UUID id) {
    return decks.revisions(id);
  }

  @GetMapping("/decks/{id}/revisions/diff")
  public List<DiffEntry> diff(@PathVariable UUID id, @RequestParam int from, @RequestParam int to) {
    return decks.diff(id, from, to);
  }

  @GetMapping("/decks/{id}/revisions/{number}")
  public DeckView revision(@PathVariable UUID id, @PathVariable int number) {
    return decks.get(id, number);
  }

  @PostMapping("/decks/{id}/revisions")
  @ResponseStatus(HttpStatus.CREATED)
  public DeckView save(@PathVariable UUID id, @RequestBody SaveRevision input) {
    return decks.save(id, input);
  }

  @PostMapping("/decks/{id}/imports/preview")
  public ImportPreview preview(@PathVariable UUID id, @RequestBody PreviewRequest input) {
    decks.get(id);
    return decks.preview(input);
  }

  @GetMapping("/decks/{id}/analysis")
  public Analysis analysis(@PathVariable UUID id) {
    return decks.analysis(id);
  }

  @PostMapping("/decks/{id}/recommendations")
  @ResponseStatus(HttpStatus.CREATED)
  public RecommendationRun recommend(@PathVariable UUID id) {
    return recommendations.generate(id);
  }

  @GetMapping("/recommendations/{id}")
  public RecommendationRun recommendation(@PathVariable UUID id) {
    return recommendations.get(id);
  }

  @PostMapping("/recommendations/{id}/explanations")
  public RecommendationRun explain(@PathVariable UUID id) {
    return recommendations.explain(id);
  }

  @PostMapping("/recommendations/{id}/apply")
  @ResponseStatus(HttpStatus.CREATED)
  public DeckView apply(@PathVariable UUID id, @RequestBody ApplyRequest input) {
    return recommendations.apply(id, input);
  }
}
