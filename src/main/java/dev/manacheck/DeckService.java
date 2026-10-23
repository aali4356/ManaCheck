package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckService {
  private final JdbcClient jdbc;
  private final CatalogRepository cards;
  private final DeckValidator validator;

  public DeckService(JdbcClient jdbc, CatalogRepository cards, DeckValidator validator) {
    this.jdbc = jdbc;
    this.cards = cards;
    this.validator = validator;
  }

  @Transactional
  public DeckView create(CreateDeck input) {
    if (input.name() == null || input.name().isBlank() || input.name().length() > 120)
      throw ApiException.bad("Deck name must contain 1–120 characters");
    cards.require(input.commanderOracleId());
    UUID id = UUID.randomUUID();
    jdbc.sql("INSERT INTO deck(id,name) VALUES(:id,:name)")
        .param("id", id)
        .param("name", input.name().trim())
        .update();
    return save(
        id,
        new SaveRevision(0, input.commanderOracleId(), input.theme(), "Created deck", List.of()));
  }

  @Transactional
  public DeckView save(UUID id, SaveRevision input) {
    UUID activeCatalog = cards.lockActive();
    Integer current =
        jdbc.sql("SELECT current_revision FROM deck WHERE id=:id FOR UPDATE")
            .param("id", id)
            .query(Integer.class)
            .optional()
            .orElseThrow(() -> ApiException.missing("Deck not found"));
    if (current != input.expectedRevision())
      throw ApiException.conflict("This deck has changed. Reload before saving.");
    String theme = input.theme() == null ? "general" : input.theme();
    if (!THEMES.contains(theme))
      throw ApiException.bad("Choose general, tokens, counters, or graveyard");
    if (input.note() != null && input.note().length() > 1500)
      throw ApiException.bad("Revision note exceeds 1,500 characters");
    cards.require(input.commanderOracleId());
    if (input.entries() == null || input.entries().size() > 500)
      throw ApiException.bad("Supply up to 500 deck entries");
    Map<UUID, Integer> entries = new LinkedHashMap<>();
    for (EntryInput entry : input.entries()) {
      if (entry == null
          || entry.oracleId() == null
          || entry.quantity() < 1
          || entry.quantity() > 1000)
        throw ApiException.bad("Each entry requires a card and quantity between 1 and 1,000");
      int quantity = entries.merge(entry.oracleId(), entry.quantity(), Integer::sum);
      if (quantity > 1000) throw ApiException.bad("Combined quantity exceeds 1,000");
    }
    if (cards.cards(entries.keySet()).size() != entries.size())
      throw ApiException.bad("One or more card identities are unknown");
    UUID revisionId = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO deck_revision(id,deck_id,revision_number,commander_id,catalog_import_id,note,theme)
            VALUES(:rid,:id,:number,:commander,:catalog,:note,:theme)
            """)
        .param("rid", revisionId)
        .param("id", id)
        .param("number", current + 1)
        .param("commander", input.commanderOracleId())
        .param("catalog", activeCatalog, Types.OTHER)
        .param("note", input.note() == null ? "" : input.note())
        .param("theme", theme)
        .update();
    for (var entry : entries.entrySet())
      jdbc.sql("INSERT INTO deck_entry VALUES(:revision,:card,:quantity)")
          .param("revision", revisionId)
          .param("card", entry.getKey())
          .param("quantity", entry.getValue())
          .update();
    jdbc.sql("UPDATE deck SET current_revision=:number,updated_at=now() WHERE id=:id")
        .param("number", current + 1)
        .param("id", id)
        .update();
    return get(id, current + 1);
  }

  public List<DeckSummary> list() {
    return jdbc.sql(
            """
            SELECT d.id,d.name,c.name AS commander_name,f.image_uri AS commander_image_uri,
              r.theme,d.current_revision,d.updated_at,
              1+COALESCE((SELECT SUM(e.quantity) FROM deck_entry e WHERE e.revision_id=r.id),0) AS total
            FROM deck d JOIN deck_revision r ON r.deck_id=d.id AND r.revision_number=d.current_revision
            JOIN card c ON c.oracle_id=r.commander_id
            LEFT JOIN card_face f ON f.oracle_id=c.oracle_id AND f.face_number=0
            ORDER BY d.updated_at DESC,d.id
            """)
        .query(
            (r, n) ->
                new DeckSummary(
                    r.getObject("id", UUID.class),
                    r.getString("name"),
                    r.getString("commander_name"),
                    r.getString("commander_image_uri"),
                    r.getString("theme"),
                    r.getInt("current_revision"),
                    r.getInt("total"),
                    CatalogRepository.instant(r, "updated_at")))
        .list();
  }

  public DeckView get(UUID id) {
    return get(id, null);
  }

  public DeckView get(UUID id, Integer number) {
    record Metadata(
        String name,
        int revision,
        UUID commander,
        String theme,
        String note,
        Instant createdAt,
        UUID revisionId) {}
    Metadata m =
        jdbc.sql(
                """
                SELECT d.name,r.* FROM deck d JOIN deck_revision r ON r.deck_id=d.id
                WHERE d.id=:id AND r.revision_number=COALESCE(:number,d.current_revision)
                """)
            .param("id", id)
            .param("number", number, Types.INTEGER)
            .query(
                (r, n) ->
                    new Metadata(
                        r.getString("name"),
                        r.getInt("revision_number"),
                        r.getObject("commander_id", UUID.class),
                        r.getString("theme"),
                        r.getString("note"),
                        CatalogRepository.instant(r, "created_at"),
                        r.getObject("id", UUID.class)))
            .optional()
            .orElseThrow(() -> ApiException.missing("Deck revision not found"));
    List<EntryInput> raw =
        jdbc.sql(
                "SELECT oracle_id,quantity FROM deck_entry WHERE revision_id=:id ORDER BY"
                    + " oracle_id")
            .param("id", m.revisionId())
            .query(
                (r, n) ->
                    new EntryInput(r.getObject("oracle_id", UUID.class), r.getInt("quantity")))
            .list();
    Set<UUID> ids = new HashSet<>(raw.stream().map(EntryInput::oracleId).toList());
    ids.add(m.commander());
    Map<UUID, Card> found = cards.cards(ids);
    List<DeckEntry> entries =
        raw.stream()
            .map(e -> new DeckEntry(found.get(e.oracleId()), e.quantity()))
            .sorted(Comparator.comparing(e -> e.card().name()))
            .toList();
    return new DeckView(
        id,
        m.name(),
        m.revision(),
        found.get(m.commander()),
        m.theme(),
        m.note(),
        entries,
        m.createdAt());
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Analysis analysis(UUID id) {
    return validator.validate(get(id), cards.activeId(), cards.updatedAt());
  }

  public List<RevisionSummary> revisions(UUID id) {
    get(id);
    return jdbc.sql(
            "SELECT revision_number,theme,note,created_at FROM deck_revision WHERE deck_id=:id"
                + " ORDER BY revision_number DESC")
        .param("id", id)
        .query(
            (r, n) ->
                new RevisionSummary(
                    r.getInt("revision_number"),
                    r.getString("theme"),
                    r.getString("note"),
                    CatalogRepository.instant(r, "created_at")))
        .list();
  }

  public List<DiffEntry> diff(UUID id, int from, int to) {
    get(id, from);
    get(id, to);
    return jdbc.sql(
            """
            WITH old_cards AS (
              SELECT oracle_id,SUM(quantity)::integer AS quantity FROM (
                SELECT e.oracle_id,e.quantity FROM deck_entry e JOIN deck_revision r ON r.id=e.revision_id WHERE r.deck_id=:id AND r.revision_number=:from
                UNION ALL SELECT commander_id,1 FROM deck_revision WHERE deck_id=:id AND revision_number=:from
              ) x GROUP BY oracle_id
            ), new_cards AS (
              SELECT oracle_id,SUM(quantity)::integer AS quantity FROM (
                SELECT e.oracle_id,e.quantity FROM deck_entry e JOIN deck_revision r ON r.id=e.revision_id WHERE r.deck_id=:id AND r.revision_number=:to
                UNION ALL SELECT commander_id,1 FROM deck_revision WHERE deck_id=:id AND revision_number=:to
              ) x GROUP BY oracle_id
            )
            SELECT c.oracle_id,c.name,COALESCE(o.quantity,0) AS before_quantity,COALESCE(n.quantity,0) AS after_quantity,
              COALESCE(n.quantity,0)-COALESCE(o.quantity,0) AS delta
            FROM old_cards o FULL OUTER JOIN new_cards n USING(oracle_id)
            JOIN card c ON c.oracle_id=COALESCE(o.oracle_id,n.oracle_id)
            WHERE COALESCE(o.quantity,0)<>COALESCE(n.quantity,0) ORDER BY c.name
            """)
        .param("id", id)
        .param("from", from)
        .param("to", to)
        .query(
            (r, n) ->
                new DiffEntry(
                    r.getObject("oracle_id", UUID.class),
                    r.getString("name"),
                    r.getInt("before_quantity"),
                    r.getInt("after_quantity"),
                    r.getInt("delta")))
        .list();
  }

  public UUID revisionId(UUID id, int number) {
    return jdbc.sql("SELECT id FROM deck_revision WHERE deck_id=:id AND revision_number=:n")
        .param("id", id)
        .param("n", number)
        .query(UUID.class)
        .single();
  }

  public ImportPreview preview(PreviewRequest input) {
    if (input.text() == null || input.text().length() > 100_000)
      throw ApiException.bad("Paste a decklist of up to 100,000 characters");
    cards.require(input.commanderOracleId());
    List<String> errors = new ArrayList<>();
    List<PreviewLine> lines = new ArrayList<>();
    Map<UUID, Integer> entries = new LinkedHashMap<>();
    String[] raw = input.text().split("\\R");
    if (raw.length > 500) throw ApiException.bad("Paste up to 500 lines");
    Pattern format = Pattern.compile("^(\\d+)\\s+(.+?)\\s*$");
    int commanderCopies = 0;
    for (int i = 0; i < raw.length; i++) {
      if (raw[i].isBlank()) continue;
      var match = format.matcher(raw[i].trim());
      if (!match.matches()) {
        errors.add("Line " + (i + 1) + ": use quantity card name");
        continue;
      }
      int quantity;
      try {
        quantity = Integer.parseInt(match.group(1));
      } catch (NumberFormatException e) {
        quantity = 0;
      }
      if (quantity < 1 || quantity > 1000) {
        errors.add("Line " + (i + 1) + ": quantity must be between 1 and 1,000");
        continue;
      }
      Optional<Card> card = cards.byName(match.group(2));
      if (card.isEmpty()) {
        errors.add("Line " + (i + 1) + ": unknown card “" + match.group(2) + "”");
        continue;
      }
      Card c = card.get();
      lines.add(new PreviewLine(i + 1, raw[i], c, quantity));
      if (c.oracleId().equals(input.commanderOracleId())) {
        commanderCopies += quantity;
        if (commanderCopies != 1)
          errors.add("Line " + (i + 1) + ": combined commander quantity must be one");
      } else {
        int combined = entries.merge(c.oracleId(), quantity, Integer::sum);
        if (combined > 1000) errors.add("Line " + (i + 1) + ": combined quantity exceeds 1,000");
      }
    }
    return new ImportPreview(
        entries.entrySet().stream().map(e -> new EntryInput(e.getKey(), e.getValue())).toList(),
        lines,
        List.copyOf(errors));
  }
}
