package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class WorkflowIntegrationTest extends PostgresTestBase {
  @Test
  void oracleIdentitiesSurviveDigitalPreferredPrintings() throws IOException {
    DeckView existing = create();
    String extra;
    try (InputStream input = getClass().getResourceAsStream("/catalog-digital-preferred.jsonl")) {
      extra = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
    }
    String snapshot = new String(Fixture.jsonl(), StandardCharsets.UTF_8) + "\n" + extra;
    byte[] bytes = snapshot.getBytes(StandardCharsets.UTF_8);
    UUID imported =
        importer.importStream(
            "digital-preferred-regression", Instant.now(), new ByteArrayInputStream(bytes), false);
    assertThat(catalog.importRun(imported).status()).isEqualTo("SUCCEEDED");
    Card badlands = catalog.byName("Badlands").orElseThrow();
    assertThat(badlands.oracleId())
        .isEqualTo(UUID.fromString("13ff3222-91cb-4796-a34e-899ed817694c"));
    assertThat(badlands.paper()).isTrue();
    Card lotus = catalog.byName("Black Lotus").orElseThrow();
    assertThat(lotus.paper()).isTrue();
    assertThat(lotus.commanderLegality()).isEqualTo("banned");
    assertThat(catalog.byName("Oracle of the Alpha")).isEmpty();
    assertThat(catalog.status().activeCards()).isEqualTo(117);
    assertThat(catalog.importRun(imported).skippedCount()).isEqualTo(2);
    assertThat(decks.get(existing.id()).revision()).isEqualTo(existing.revision());
    assertThat(decks.get(existing.id(), 1).entries()).isEmpty();
    UUID repeated =
        importer.importStream(
            "digital-preferred-repeat", Instant.now(), new ByteArrayInputStream(bytes), false);
    assertThat(catalog.importRun(repeated).status()).isEqualTo("UNCHANGED");
    assertThat(catalog.status().activeCards()).isEqualTo(117);
  }

  @Test
  void activeSourceSurvivesTheRecentHistoryLimit() {
    UUID active = catalog.activeId();
    for (int i = 0; i < 13; i++)
      importer.importStream(
          "failed-refresh",
          Instant.now(),
          new ByteArrayInputStream("{invalid".getBytes(StandardCharsets.UTF_8)),
          false);
    CatalogStatus status = catalog.status();
    assertThat(status.recentImports()).noneMatch(r -> r.id().equals(active));
    assertThat(status.activeSource()).isEqualTo("test-fixture");
    assertThat(status.activeImportId()).isEqualTo(active);
  }

  @LocalServerPort int port;

  private DeckView create() {
    return decks.create(
        new CreateDeck(
            "Regression deck", Fixture.card("Meren of Clan Nel Toth").oracleId(), "graveyard"));
  }

  private SaveRevision revision(DeckView d, List<EntryInput> entries) {
    return new SaveRevision(
        d.revision(), d.commander().oracleId(), d.theme(), "Test revision", entries);
  }

  @Test
  void savesAtomicSnapshotsAndDiffIncludesCommander() {
    DeckView first = create();
    DeckView next =
        decks.save(
            first.id(),
            revision(
                first,
                List.of(
                    new EntryInput(Fixture.card("Forest").oracleId(), 18),
                    new EntryInput(Fixture.card("Sol Ring").oracleId(), 1))));
    assertThat(decks.get(first.id(), 1).entries()).isEmpty();
    assertThat(decks.diff(first.id(), 1, 2))
        .extracting(DiffEntry::name)
        .containsExactly("Forest", "Sol Ring");
    assertThat(next.totalCards()).isEqualTo(20);
    assertThat(decks.list())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.commanderImageUri())
                  .isEqualTo(next.commander().faces().getFirst().imageUri());
              assertThat(summary.totalCards()).isEqualTo(20);
            });
    assertThatThrownBy(() -> decks.save(first.id(), revision(first, List.of())))
        .isInstanceOf(ApiException.class)
        .extracting("status")
        .isEqualTo(409);
  }

  @Test
  void changingCommanderAppearsInDiff() {
    DeckView d = create();
    decks.save(
        d.id(),
        new SaveRevision(
            d.revision(),
            Fixture.card("Ezuri, Renegade Leader").oracleId(),
            "general",
            "New leader",
            List.of()));
    assertThat(decks.diff(d.id(), 1, 2))
        .extracting(DiffEntry::delta)
        .containsExactlyInAnyOrder(-1, 1);
  }

  @Test
  void unknownCardsAndBadQuantitiesNeverCreatePartialRevisions() {
    DeckView d = create();
    assertThatThrownBy(
            () -> decks.save(d.id(), revision(d, List.of(new EntryInput(UUID.randomUUID(), 1)))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                decks.save(
                    d.id(),
                    revision(d, List.of(new EntryInput(Fixture.card("Forest").oracleId(), 0)))))
        .isInstanceOf(ApiException.class);
    assertThat(decks.get(d.id()).revision()).isEqualTo(1);
    assertThat(decks.revisions(d.id())).hasSize(1);
  }

  @Test
  void competingSavesHaveOneWinner() throws Exception {
    DeckView d = create();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<Integer> edit =
          () -> {
            start.await();
            try {
              decks.save(
                  d.id(),
                  revision(d, List.of(new EntryInput(Fixture.card("Forest").oracleId(), 1))));
              return 201;
            } catch (ApiException e) {
              return e.status();
            }
          };
      Future<Integer> a = pool.submit(edit), b = pool.submit(edit);
      start.countDown();
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
    }
    assertThat(decks.get(d.id()).revision()).isEqualTo(2);
  }

  @Test
  void databaseProtectsForeignKeysQuantitiesAndHistory() {
    DeckView d = create();
    UUID revision = decks.revisionId(d.id(), 1);
    UUID assembling = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO deck_revision(id,deck_id,revision_number,commander_id)"
                + " VALUES(:rid,:deck,2,:commander)")
        .param("rid", assembling)
        .param("deck", d.id())
        .param("commander", d.commander().oracleId())
        .update();
    assertThatThrownBy(
            () ->
                jdbc.sql("INSERT INTO deck_entry VALUES(:revision,:card,0)")
                    .param("revision", assembling)
                    .param("card", Fixture.card("Forest").oracleId())
                    .update())
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql("INSERT INTO deck_entry VALUES(:revision,:card,1)")
                    .param("revision", assembling)
                    .param("card", UUID.randomUUID())
                    .update())
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql("UPDATE deck_revision SET note='rewrite' WHERE id=:id")
                    .param("id", revision)
                    .update())
        .hasMessageContaining("immutable");
    assertThatThrownBy(
            () ->
                jdbc.sql("INSERT INTO deck_entry VALUES(:revision,:card,1)")
                    .param("revision", revision)
                    .param("card", Fixture.card("Forest").oracleId())
                    .update())
        .hasMessageContaining("immutable");
  }

  @Test
  void importPreviewResolvesNamesAndAggregatesQuantities() {
    DeckView d = create();
    ImportPreview p =
        decks.preview(
            new PreviewRequest(
                "1 Meren of Clan Nel Toth\n9 Forest\n9 forest\n1 Sol Ring",
                d.commander().oracleId()));
    assertThat(p.errors()).isEmpty();
    assertThat(p.entries()).hasSize(2);
    assertThat(p.entries()).anyMatch(e -> e.quantity() == 18);
    assertThat(
            decks
                .preview(
                    new PreviewRequest(
                        "1 A hallucinated card\n2 Sol Ring", d.commander().oracleId()))
                .errors())
        .hasSize(1);
    assertThat(
            decks
                .preview(
                    new PreviewRequest(
                        "1 Meren of Clan Nel Toth\n1 Meren of Clan Nel Toth",
                        d.commander().oracleId()))
                .errors())
        .hasSize(1);
    assertThat(decks.get(d.id()).entries()).isEmpty();
  }

  @Test
  void identicalImportsPreserveCatalogIdentity() {
    UUID active = catalog.activeId();
    UUID second =
        importer.importStream(
            "repeat", Instant.now(), new ByteArrayInputStream(Fixture.archive()), true);
    assertThat(catalog.importRun(second).status()).isEqualTo("UNCHANGED");
    assertThat(catalog.activeId()).isEqualTo(active);
    assertThat(catalog.status().activeCards()).isEqualTo(115);
    assertThat(catalog.importRun(second).skippedCount())
        .isEqualTo(1); // An identically named Llanowar Elves token is excluded.
  }

  @Test
  void malformedAndTruncatedImportsPreserveGoodData() {
    UUID active = catalog.activeId();
    for (byte[] bytes :
        List.of(
            "{broken".getBytes(StandardCharsets.UTF_8),
            (Fixture.sourceLine("Forest") + "\n" + Fixture.sourceLine("Forest"))
                .getBytes(StandardCharsets.UTF_8))) {
      UUID id = importer.importStream("bad", Instant.now(), new ByteArrayInputStream(bytes), false);
      assertThat(catalog.importRun(id).status()).isEqualTo("FAILED");
      assertThat(catalog.activeId()).isEqualTo(active);
    }
    UUID truncated =
        importer.importStream(
            "truncated",
            Instant.now(),
            new ByteArrayInputStream(Arrays.copyOf(Fixture.archive(), 100)),
            true);
    assertThat(catalog.importRun(truncated).status()).isEqualTo("FAILED");
    assertThat(catalog.activeId()).isEqualTo(active);
  }

  @Test
  void connectorAcceptsExtraFieldsAndRejectsMissingFields() {
    var valid =
        (tools.jackson.databind.node.ObjectNode)
            Fixture.JSON.readTree(Fixture.sourceLine("Forest"));
    valid.put("extra_optional_field", true);
    UUID good =
        importer.importStream(
            "schema-extra",
            Instant.now(),
            new ByteArrayInputStream(valid.toString().getBytes(StandardCharsets.UTF_8)),
            false);
    assertThat(catalog.importRun(good).status()).isEqualTo("SUCCEEDED");
    UUID active = catalog.activeId();
    valid.remove("oracle_id");
    UUID bad =
        importer.importStream(
            "schema-missing",
            Instant.now(),
            new ByteArrayInputStream(valid.toString().getBytes(StandardCharsets.UTF_8)),
            false);
    assertThat(catalog.importRun(bad).status()).isEqualTo("FAILED");
    assertThat(catalog.activeId()).isEqualTo(active);
    valid.remove("games");
    UUID missingGames =
        importer.importStream(
            "schema-missing-games",
            Instant.now(),
            new ByteArrayInputStream(valid.toString().getBytes(StandardCharsets.UTF_8)),
            false);
    assertThat(catalog.importRun(missingGames).status()).isEqualTo("FAILED");
  }

  @Test
  void retiredCardsRemainAvailableInHistory() {
    DeckView d = create();
    decks.save(
        d.id(), revision(d, List.of(new EntryInput(Fixture.card("Sol Ring").oracleId(), 1))));
    UUID id =
        importer.importStream(
            "smaller-snapshot",
            Instant.now(),
            new ByteArrayInputStream(
                (Fixture.sourceLine("Forest") + "\n" + Fixture.sourceLine("Meren of Clan Nel Toth"))
                    .getBytes(StandardCharsets.UTF_8)),
            false);
    assertThat(catalog.importRun(id).status()).isEqualTo("SUCCEEDED");
    assertThat(decks.get(d.id(), 2).entries().getFirst().card().active()).isFalse();
    assertThat(decks.analysis(d.id()).errors()).extracting(Finding::code).contains("INACTIVE");
  }

  @Test
  void browserOriginCannotTriggerWritesOrSpend() throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/decks"))
                    .header("Origin", "https://outside.example")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void apiWorkflowReturnsStructuredErrorsAndActualData() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    var search =
        client.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/cards?q=Sol%20Ring"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(search.statusCode()).isEqualTo(200);
    assertThat(Fixture.JSON.readTree(search.body()).path("cards").size()).isEqualTo(1);
    var invalid =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/decks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(invalid.statusCode()).isEqualTo(422);
    assertThat(Fixture.JSON.readTree(invalid.body()).path("code").asText())
        .isEqualTo("INVALID_INPUT");
    var unknownField =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/decks"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        Fixture.JSON.writeValueAsString(
                            Map.of(
                                "name",
                                "Unknown field",
                                "commanderOracleId",
                                Fixture.card("Meren of Clan Nel Toth").oracleId(),
                                "theme",
                                "general",
                                "companions",
                                List.of()))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(unknownField.statusCode()).isEqualTo(400);
  }
}
