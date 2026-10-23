package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CatalogImporter {
  private final JdbcClient jdbc;
  private final JdbcTemplate batch;
  private final TransactionTemplate tx;
  private final CatalogRepository catalog;
  private final CardClassifier classifier;
  private final JsonMapper json;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final AtomicBoolean liveRunning = new AtomicBoolean();

  public CatalogImporter(
      JdbcClient jdbc,
      JdbcTemplate batch,
      TransactionTemplate tx,
      CatalogRepository catalog,
      CardClassifier classifier,
      JsonMapper json) {
    this.jdbc = jdbc;
    this.batch = batch;
    this.tx = tx;
    this.catalog = catalog;
    this.classifier = classifier;
    this.json = json;
  }

  public UUID startScryfall() {
    if (!liveRunning.compareAndSet(false, true))
      throw ApiException.conflict("A Scryfall import is already running");
    UUID id = create("scryfall-oracle", Instant.now());
    Thread.startVirtualThread(
        () -> {
          try {
            HttpResponse<String> metadata =
                http.send(
                    request(URI.create("https://api.scryfall.com/bulk-data")),
                    HttpResponse.BodyHandlers.ofString());
            if (metadata.statusCode() != 200)
              throw new IOException("Scryfall metadata returned HTTP " + metadata.statusCode());
            JsonNode chosen = null;
            for (JsonNode n : json.readTree(metadata.body()).path("data"))
              if (n.path("type").asText().equals("oracle_cards")) chosen = n;
            if (chosen == null || !chosen.hasNonNull("jsonl_download_uri"))
              throw new IOException("Oracle JSONL export is unavailable");
            URI download = URI.create(chosen.path("jsonl_download_uri").asText());
            if (!"https".equals(download.getScheme())
                || !"data.scryfall.io".equals(download.getHost()))
              throw new IOException("Unexpected Scryfall download host");
            Instant sourceDate = Instant.parse(chosen.path("updated_at").asText());
            jdbc.sql("UPDATE catalog_import SET source_updated_at=:date WHERE id=:id")
                .param("date", sourceDate.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
            HttpResponse<InputStream> response =
                http.send(request(download), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
              if (response.statusCode() != 200)
                throw new IOException("Scryfall download returned HTTP " + response.statusCode());
              process(id, input, true);
            }
          } catch (Exception e) {
            fail(id, e);
          } finally {
            liveRunning.set(false);
          }
        });
    return id;
  }

  private HttpRequest request(URI uri) {
    return HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMinutes(3))
        .header("User-Agent", "ManaCheck/0.1 (local Commander portfolio project)")
        .header("Accept", "application/json, application/gzip")
        .GET()
        .build();
  }

  public UUID importStream(String source, Instant updatedAt, InputStream input, boolean gzip) {
    UUID id = create(source, updatedAt);
    try {
      process(id, input, gzip);
    } catch (Exception e) {
      fail(id, e);
    }
    return id;
  }

  private UUID create(String source, Instant updatedAt) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO catalog_import(id,source,source_updated_at,status)"
                + " VALUES(:id,:source,:date,'RUNNING')")
        .param("id", id)
        .param("source", source)
        .param("date", updatedAt.atOffset(ZoneOffset.UTC))
        .update();
    return id;
  }

  private synchronized void process(UUID id, InputStream input, boolean gzip)
      throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    int count = 0, skipped = 0, lineNumber = 0;
    List<Card> pending = new ArrayList<>(500);
    InputStream decompressed = gzip ? new GZIPInputStream(input) : input;
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new DigestInputStream(decompressed, digest), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) continue;
        JsonNode node;
        try {
          node = json.readTree(line);
        } catch (Exception e) {
          throw new IOException("Invalid JSON at line " + lineNumber);
        }
        if (!node.isObject()) throw new IOException("Expected a card object at line " + lineNumber);
        if (classifier.excluded(node)) {
          skipped++;
          continue;
        }
        try {
          pending.add(classifier.classify(node));
        } catch (Exception e) {
          throw new IOException("Line " + lineNumber + ": " + e.getMessage());
        }
        count++;
        if (pending.size() == 500) {
          stage(id, pending);
          pending.clear();
        }
      }
    }
    if (!pending.isEmpty()) stage(id, pending);
    if (count == 0) throw new IOException("Empty import; the current catalog was preserved");
    String hash =
        HexFormat.of().formatHex(digest.digest())
            + ":"
            + CardClassifier.CLASSIFIER_VERSION
            + ":"
            + CardClassifier.RULES_VERSION;
    final int records = count, excluded = skipped;
    tx.executeWithoutResult(status -> promote(id, hash, records, excluded));
  }

  private void stage(UUID id, List<Card> cards) {
    batch.batchUpdate(
        "INSERT INTO catalog_stage(import_id,oracle_id,payload) VALUES(?,?,CAST(? AS jsonb))",
        cards,
        500,
        (ps, c) -> {
          ps.setObject(1, id);
          ps.setObject(2, c.oracleId());
          ps.setString(3, json.writeValueAsString(c));
        });
  }

  private void promote(UUID id, String hash, int count, int skipped) {
    jdbc.sql("SELECT singleton FROM catalog_state WHERE singleton FOR UPDATE")
        .query(Boolean.class)
        .single();
    String activeHash =
        jdbc.sql(
                "SELECT content_hash FROM catalog_import WHERE id=(SELECT active_import_id FROM"
                    + " catalog_state WHERE singleton)")
            .query(String.class)
            .optional()
            .orElse("");
    if (hash.equals(activeHash)) {
      finish(id, "UNCHANGED", hash, count, skipped);
      jdbc.sql("DELETE FROM catalog_stage WHERE import_id=:id").param("id", id).update();
      return;
    }
    Instant current = catalog.updatedAt(), incoming = catalog.importRun(id).sourceUpdatedAt();
    if (current != null && incoming.isBefore(current))
      throw ApiException.conflict("Older snapshot rejected; the current catalog was preserved");
    jdbc.sql(
            """
            INSERT INTO card(oracle_id,name,mana_value,type_line,oracle_text,color_identity,basic_land_types,keywords,
              commander_legality,paper,active,commander_eligible,unsupported_reason,copy_limit,scryfall_uri,import_id)
            SELECT oracle_id,payload->>'name',(payload->>'manaValue')::numeric,payload->>'typeLine',payload->>'oracleText',
              ARRAY(SELECT jsonb_array_elements_text(payload->'colorIdentity')),
              ARRAY(SELECT jsonb_array_elements_text(payload->'basicLandTypes')),
              ARRAY(SELECT jsonb_array_elements_text(payload->'keywords')),
              payload->>'commanderLegality',(payload->>'paper')::boolean,true,(payload->>'commanderEligible')::boolean,
              payload->>'unsupportedReason',(payload->>'copyLimit')::integer,payload->>'scryfallUri',:id
            FROM catalog_stage WHERE import_id=:id
            ON CONFLICT(oracle_id) DO UPDATE SET name=EXCLUDED.name,mana_value=EXCLUDED.mana_value,type_line=EXCLUDED.type_line,
              oracle_text=EXCLUDED.oracle_text,color_identity=EXCLUDED.color_identity,basic_land_types=EXCLUDED.basic_land_types,
              keywords=EXCLUDED.keywords,commander_legality=EXCLUDED.commander_legality,paper=EXCLUDED.paper,active=true,
              commander_eligible=EXCLUDED.commander_eligible,unsupported_reason=EXCLUDED.unsupported_reason,
              copy_limit=EXCLUDED.copy_limit,scryfall_uri=EXCLUDED.scryfall_uri,import_id=EXCLUDED.import_id
            """)
        .param("id", id)
        .update();
    jdbc.sql(
            "DELETE FROM card_face WHERE oracle_id IN (SELECT oracle_id FROM catalog_stage WHERE"
                + " import_id=:id)")
        .param("id", id)
        .update();
    jdbc.sql(
            """
            INSERT INTO card_face SELECT s.oracle_id,(f.ordinality-1)::integer,f.value->>'name',f.value->>'typeLine',f.value->>'oracleText',f.value->>'imageUri'
            FROM catalog_stage s CROSS JOIN LATERAL jsonb_array_elements(s.payload->'faces') WITH ORDINALITY f(value,ordinality)
            WHERE s.import_id=:id
            """)
        .param("id", id)
        .update();
    jdbc.sql(
            "DELETE FROM card_tag WHERE oracle_id IN (SELECT oracle_id FROM catalog_stage WHERE"
                + " import_id=:id)")
        .param("id", id)
        .update();
    jdbc.sql(
            "INSERT INTO card_tag SELECT s.oracle_id,t.tag,:version FROM catalog_stage s CROSS JOIN"
                + " LATERAL jsonb_array_elements_text(s.payload->'tags') t(tag) WHERE"
                + " s.import_id=:id")
        .param("id", id)
        .param("version", CardClassifier.RULES_VERSION)
        .update();
    jdbc.sql(
            "UPDATE card SET active=false WHERE NOT EXISTS(SELECT 1 FROM catalog_stage s WHERE"
                + " s.import_id=:id AND s.oracle_id=card.oracle_id)")
        .param("id", id)
        .update();
    finish(id, "SUCCEEDED", hash, count, skipped);
    jdbc.sql("UPDATE catalog_state SET active_import_id=:id WHERE singleton")
        .param("id", id)
        .update();
    jdbc.sql("DELETE FROM catalog_stage WHERE import_id=:id").param("id", id).update();
  }

  private void finish(UUID id, String status, String hash, int count, int skipped) {
    jdbc.sql(
            "UPDATE catalog_import SET"
                + " status=:status,content_hash=:hash,record_count=:count,skipped_count=:skipped,completed_at=now()"
                + " WHERE id=:id")
        .param("id", id)
        .param("status", status)
        .param("hash", hash)
        .param("count", count)
        .param("skipped", skipped)
        .update();
  }

  private void fail(UUID id, Exception error) {
    String message =
        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    if (error instanceof org.springframework.dao.DataIntegrityViolationException)
      message = "Duplicate or invalid Oracle identity in staged import";
    jdbc.sql(
            "UPDATE catalog_import SET status='FAILED',error=:error,completed_at=now() WHERE"
                + " id=:id")
        .param("id", id)
        .param("error", message.substring(0, Math.min(message.length(), 400)))
        .update();
    jdbc.sql("DELETE FROM catalog_stage WHERE import_id=:id").param("id", id).update();
  }
}
