package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CatalogRepository {
  private final JdbcClient jdbc;
  private final JsonMapper json;
  static final String CARD_SELECT =
      """
      SELECT c.*, ARRAY(SELECT tag FROM card_tag t WHERE t.oracle_id=c.oracle_id ORDER BY tag) AS tags,
        COALESCE((SELECT jsonb_agg(jsonb_build_object('name',f.name,'typeLine',f.type_line,
            'oracleText',f.oracle_text,'imageUri',f.image_uri) ORDER BY f.face_number)
            FROM card_face f WHERE f.oracle_id=c.oracle_id),'[]'::jsonb)::text AS faces_json
      FROM card c
      """;

  public CatalogRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public Optional<Card> find(UUID id) {
    return jdbc.sql(CARD_SELECT + " WHERE c.oracle_id=:id")
        .param("id", id)
        .query(mapper())
        .optional();
  }

  public Card require(UUID id) {
    if (id == null) throw ApiException.bad("A commander or card identity is required");
    return find(id).orElseThrow(() -> ApiException.missing("Card not found: " + id));
  }

  public Map<UUID, Card> cards(Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<UUID, Card> result = new HashMap<>();
    for (Card c :
        jdbc.sql(CARD_SELECT + " WHERE c.oracle_id IN (:ids)")
            .param("ids", ids)
            .query(mapper())
            .list()) result.put(c.oracleId(), c);
    return result;
  }

  public Optional<Card> byName(String name) {
    return jdbc.sql(
            CARD_SELECT
                + " WHERE lower(c.name)=lower(:name) AND c.active ORDER BY c.oracle_id LIMIT 1")
        .param("name", name)
        .query(mapper())
        .optional();
  }

  public List<Card> search(String query, boolean commanders, UUID commanderId, int offset) {
    String identity = commanderId == null ? null : array(require(commanderId).colorIdentity());
    return jdbc.sql(
            CARD_SELECT
                + """
                WHERE c.active AND c.paper AND c.commander_legality='legal'
                  AND (:query='' OR position(:query IN lower(c.name))>0)
                  AND (NOT :commanders OR c.commander_eligible)
                  AND (:identity IS NULL OR c.color_identity <@ CAST(:identity AS text[]))
                ORDER BY (lower(c.name)=:query) DESC, lower(c.name), c.oracle_id LIMIT 25 OFFSET :offset
                """)
        .param("query", query.toLowerCase(Locale.ROOT))
        .param("commanders", commanders)
        .param("identity", identity, Types.VARCHAR)
        .param("offset", Math.max(0, offset))
        .query(mapper())
        .list();
  }

  public UUID activeId() {
    return jdbc.sql("SELECT active_import_id FROM catalog_state WHERE singleton")
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  public UUID lockActive() {
    return jdbc.sql("SELECT active_import_id FROM catalog_state WHERE singleton FOR SHARE")
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  public Instant updatedAt() {
    return jdbc.sql(
            "SELECT i.source_updated_at FROM catalog_import i JOIN catalog_state s ON"
                + " i.id=s.active_import_id WHERE s.singleton")
        .query((r, n) -> instant(r, "source_updated_at"))
        .optional()
        .orElse(null);
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public CatalogStatus status() {
    UUID active = activeId();
    return new CatalogStatus(
        active,
        active == null ? null : importRun(active).source(),
        updatedAt(),
        jdbc.sql("SELECT count(*) FROM card WHERE active").query(Long.class).single(),
        imports());
  }

  public List<CatalogImport> imports() {
    return jdbc.sql("SELECT * FROM catalog_import ORDER BY started_at DESC LIMIT 12")
        .query(importMapper())
        .list();
  }

  public CatalogImport importRun(UUID id) {
    return jdbc.sql("SELECT * FROM catalog_import WHERE id=:id")
        .param("id", id)
        .query(importMapper())
        .optional()
        .orElseThrow(() -> ApiException.missing("Import not found"));
  }

  public void write(Card c, UUID importId) {
    jdbc.sql(
            """
            INSERT INTO card(oracle_id,name,mana_value,type_line,oracle_text,color_identity,basic_land_types,keywords,
              commander_legality,paper,active,commander_eligible,unsupported_reason,copy_limit,scryfall_uri,import_id)
            VALUES(:id,:name,:mv,:type,:text,CAST(:colors AS text[]),CAST(:lands AS text[]),CAST(:keywords AS text[]),
              :legality,:paper,true,:eligible,:unsupported,:copies,:uri,:import)
            ON CONFLICT(oracle_id) DO UPDATE SET name=EXCLUDED.name,mana_value=EXCLUDED.mana_value,
              type_line=EXCLUDED.type_line,oracle_text=EXCLUDED.oracle_text,color_identity=EXCLUDED.color_identity,
              basic_land_types=EXCLUDED.basic_land_types,keywords=EXCLUDED.keywords,commander_legality=EXCLUDED.commander_legality,
              paper=EXCLUDED.paper,active=true,commander_eligible=EXCLUDED.commander_eligible,
              unsupported_reason=EXCLUDED.unsupported_reason,copy_limit=EXCLUDED.copy_limit,scryfall_uri=EXCLUDED.scryfall_uri,import_id=EXCLUDED.import_id
            """)
        .param("id", c.oracleId())
        .param("name", c.name())
        .param("mv", c.manaValue())
        .param("type", c.typeLine())
        .param("text", c.oracleText())
        .param("colors", array(c.colorIdentity()))
        .param("lands", array(c.basicLandTypes()))
        .param("keywords", array(c.keywords()))
        .param("legality", c.commanderLegality())
        .param("paper", c.paper())
        .param("eligible", c.commanderEligible())
        .param("unsupported", c.unsupportedReason(), Types.VARCHAR)
        .param("copies", c.copyLimit(), Types.INTEGER)
        .param("uri", c.scryfallUri())
        .param("import", importId)
        .update();
    jdbc.sql("DELETE FROM card_face WHERE oracle_id=:id").param("id", c.oracleId()).update();
    for (int i = 0; i < c.faces().size(); i++) {
      Face f = c.faces().get(i);
      jdbc.sql("INSERT INTO card_face VALUES(:id,:n,:name,:type,:text,:image)")
          .param("id", c.oracleId())
          .param("n", i)
          .param("name", f.name())
          .param("type", f.typeLine())
          .param("text", f.oracleText())
          .param("image", f.imageUri())
          .update();
    }
    jdbc.sql("DELETE FROM card_tag WHERE oracle_id=:id").param("id", c.oracleId()).update();
    for (String tag : c.tags())
      jdbc.sql("INSERT INTO card_tag VALUES(:id,:tag,:version)")
          .param("id", c.oracleId())
          .param("tag", tag)
          .param("version", CardClassifier.RULES_VERSION)
          .update();
  }

  private RowMapper<Card> mapper() {
    return (rs, n) -> {
      List<Face> faces = new ArrayList<>();
      for (var f : json.readTree(rs.getString("faces_json")))
        faces.add(
            new Face(
                f.path("name").asText(),
                f.path("typeLine").asText(),
                f.path("oracleText").asText(),
                f.path("imageUri").asText("")));
      return new Card(
          rs.getObject("oracle_id", UUID.class),
          rs.getString("name"),
          rs.getBigDecimal("mana_value"),
          rs.getString("type_line"),
          rs.getString("oracle_text"),
          strings(rs, "color_identity"),
          strings(rs, "basic_land_types"),
          strings(rs, "keywords"),
          rs.getString("commander_legality"),
          rs.getBoolean("paper"),
          rs.getBoolean("active"),
          rs.getBoolean("commander_eligible"),
          rs.getString("unsupported_reason"),
          (Integer) rs.getObject("copy_limit"),
          rs.getString("scryfall_uri"),
          faces,
          new TreeSet<>(strings(rs, "tags")));
    };
  }

  private RowMapper<CatalogImport> importMapper() {
    return (r, n) ->
        new CatalogImport(
            r.getObject("id", UUID.class),
            r.getString("source"),
            instant(r, "source_updated_at"),
            r.getString("status"),
            r.getInt("record_count"),
            r.getInt("skipped_count"),
            r.getString("error"),
            instant(r, "started_at"),
            instant(r, "completed_at"));
  }

  public static Instant instant(ResultSet r, String column) throws SQLException {
    Timestamp t = r.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  private static List<String> strings(ResultSet r, String column) throws SQLException {
    return Arrays.asList((String[]) r.getArray(column).getArray());
  }

  public static String array(Collection<String> values) {
    return "{"
        + String.join(
            ",",
            values.stream()
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .toList())
        + "}";
  }
}
