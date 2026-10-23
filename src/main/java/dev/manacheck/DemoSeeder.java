package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DemoSeeder implements ApplicationRunner {
  private final boolean enabled;
  private final CatalogImporter importer;
  private final CatalogRepository catalog;
  private final DeckService decks;
  private final JsonMapper json;
  private final JdbcClient jdbc;

  public DemoSeeder(
      @Value("${manacheck.demo:true}") boolean enabled,
      CatalogImporter importer,
      CatalogRepository catalog,
      DeckService decks,
      JsonMapper json,
      JdbcClient jdbc) {
    this.enabled = enabled;
    this.importer = importer;
    this.catalog = catalog;
    this.decks = decks;
    this.json = json;
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    jdbc.sql(
            "UPDATE catalog_import SET status='FAILED',error='Process stopped before import"
                + " completed',completed_at=now() WHERE status='RUNNING'")
        .update();
    jdbc.sql(
            "DELETE FROM catalog_stage WHERE import_id IN (SELECT id FROM catalog_import WHERE"
                + " status='FAILED')")
        .update();
    jdbc.sql(
            "UPDATE recommendation_run SET"
                + " explanation_status='fallback',explanation_detail='Process stopped before"
                + " explanation completed; rule explanations retained' WHERE"
                + " explanation_status='pending'")
        .update();
    if (!enabled) return;
    boolean fresh = catalog.activeId() == null;
    if (fresh) {
      var manifest = json.readTree(new ClassPathResource("demo/manifest.json").getInputStream());
      UUID id =
          importer.importStream(
              "offline-demo",
              Instant.parse(manifest.path("source_updated_at").asText()),
              new ClassPathResource("demo/oracle-cards.jsonl.gz").getInputStream(),
              true);
      if (!catalog.importRun(id).status().equals("SUCCEEDED"))
        throw new IllegalStateException("Demo import failed: " + catalog.importRun(id).error());
    }
    if (fresh && decks.list().isEmpty()) {
      Card meren = catalog.byName("Meren of Clan Nel Toth").orElseThrow();
      DeckView deck =
          decks.create(new CreateDeck("The graveyard shift", meren.oracleId(), "graveyard"));
      Map<String, Integer> entries = new LinkedHashMap<>();
      entries.put("Forest", 18);
      entries.put("Swamp", 18);
      for (String name :
          List.of(
              "Sol Ring",
              "Arcane Signet",
              "Sakura-Tribe Elder",
              "Elvish Mystic",
              "Llanowar Elves",
              "Satyr Wayfinder",
              "Eternal Witness",
              "Reclamation Sage",
              "Victimize",
              "Beast Within",
              "Putrefy",
              "Grisly Salvage",
              "Cultivate",
              "Sign in Blood",
              "Read the Bones",
              "Wayfarer's Bauble",
              "Spore Frog",
              "Viscera Seer",
              "Zulaport Cutthroat",
              "Blood Artist")) entries.put(name, 1);
      decks.save(
          deck.id(),
          new SaveRevision(
              deck.revision(),
              meren.oracleId(),
              "graveyard",
              "A starter list to grow from. Add cards and review role coverage.",
              inputs(entries)));
      Card ezuri = catalog.byName("Ezuri, Renegade Leader").orElseThrow();
      DeckView elves =
          decks.create(new CreateDeck("A little more green", ezuri.oracleId(), "general"));
      decks.save(
          elves.id(),
          new SaveRevision(
              elves.revision(),
              ezuri.oracleId(),
              "general",
              "An unfinished Elf deck. Try importing a list.",
              inputs(
                  Map.of(
                      "Forest",
                      32,
                      "Llanowar Elves",
                      1,
                      "Elvish Mystic",
                      1,
                      "Fyndhorn Elves",
                      1,
                      "Elvish Visionary",
                      1,
                      "Sol Ring",
                      1,
                      "Cultivate",
                      1))));
    }
  }

  private List<EntryInput> inputs(Map<String, Integer> entries) {
    return entries.entrySet().stream()
        .map(e -> new EntryInput(catalog.byName(e.getKey()).orElseThrow().oracleId(), e.getValue()))
        .toList();
  }
}
