package dev.manacheck;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "manacheck.demo=false",
      "manacheck.anthropic.api-key=",
      "logging.level.root=WARN",
      "debug=false"
    })
abstract class PostgresTestBase {
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  static {
    POSTGRES.start();
    Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcClient jdbc;
  @Autowired CatalogImporter importer;
  @Autowired CatalogRepository catalog;
  @Autowired DeckService decks;
  @Autowired RecommendationService recommendations;
  @Autowired DeckValidator validator;

  @BeforeEach
  void resetOwnedTestDatabase() {
    jdbc.sql(
            "TRUNCATE"
                + " recommendation_run,deck_entry,deck_revision,deck,card_tag,card_face,card,catalog_stage,catalog_state,catalog_import"
                + " CASCADE")
        .update();
    jdbc.sql("INSERT INTO catalog_state(singleton) VALUES(true)").update();
    var id =
        importer.importStream(
            "test-fixture",
            Instant.parse("2026-10-21T21:02:01.935Z"),
            new ByteArrayInputStream(Fixture.archive()),
            true);
    if (!catalog.importRun(id).status().equals("SUCCEEDED"))
      throw new IllegalStateException(catalog.importRun(id).error());
  }
}
