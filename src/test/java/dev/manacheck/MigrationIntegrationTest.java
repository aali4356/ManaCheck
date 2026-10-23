package dev.manacheck;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigrationIntegrationTest extends PostgresTestBase {
  @Test
  void additiveUpgradePreservesPopulatedRevision() throws Exception {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var config =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    config.target("1").load().migrate();
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement s = connection.createStatement()) {
      s.execute("SET search_path TO " + schema);
      UUID imported = UUID.randomUUID(),
          card = UUID.randomUUID(),
          deck = UUID.randomUUID(),
          revision = UUID.randomUUID();
      s.execute(
          "INSERT INTO catalog_import(id,source,source_updated_at,status) VALUES('"
              + imported
              + "','migration-fixture',now(),'SUCCEEDED')");
      s.execute(
          "INSERT INTO"
              + " card(oracle_id,name,mana_value,type_line,oracle_text,color_identity,basic_land_types,keywords,commander_legality,paper,commander_eligible,import_id)"
              + " VALUES('"
              + card
              + "','Old card',1,'Legendary Creature','','{}','{}','{}','legal',true,true,'"
              + imported
              + "')");
      s.execute(
          "INSERT INTO deck(id,name,current_revision) VALUES('" + deck + "','Existing deck',1)");
      s.execute(
          "INSERT INTO deck_revision(id,deck_id,revision_number,commander_id,note) VALUES('"
              + revision
              + "','"
              + deck
              + "',1,'"
              + card
              + "','Original note')");
      s.execute("INSERT INTO deck_entry VALUES('" + revision + "','" + card + "',7)");
      Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      try (ResultSet result =
          s.executeQuery(
              "SELECT r.theme,r.note,e.quantity FROM deck_revision r JOIN deck_entry e ON"
                  + " e.revision_id=r.id")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("general");
        assertThat(result.getString(2)).isEqualTo("Original note");
        assertThat(result.getInt(3)).isEqualTo(7);
      }
      s.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }
}
