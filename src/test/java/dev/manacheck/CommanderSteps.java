package dev.manacheck;

import static dev.manacheck.Domain.*;
import static org.assertj.core.api.Assertions.*;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;

/** Scenario-scoped glue; no external imports, recommendations, images, or model calls. */
public class CommanderSteps extends PostgresTestBase {
  private final DataSource dataSource;
  private final Map<Integer, DeckView> snapshots = new LinkedHashMap<>();
  private DeckView current;
  private List<EntryInput> mainDeck;
  private Analysis analysis;
  private ApiException rejectedSave;
  private Throwable rejectedMutation;
  private List<RevisionSummary> historyBeforeRejection;

  public CommanderSteps(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Before
  public void resetScenario() throws SQLException {
    // Fail before truncation if a future configuration accidentally points outside Testcontainers.
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
      assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(17);
    }
    // Jupiter's @BeforeEach is not invoked by Cucumber. Call the existing reset explicitly.
    resetOwnedTestDatabase();
  }

  @Given("the fixture catalog contains no saved decks")
  public void freshCatalog() {
    assertThat(decks.list()).isEmpty();
    assertThat(jdbc.sql("SELECT count(*) FROM deck_revision").query(Long.class).single()).isZero();
    assertThat(jdbc.sql("SELECT count(*) FROM deck_entry").query(Long.class).single()).isZero();
    assertThat(catalog.status().activeCards()).isEqualTo(Fixture.CARDS.size());
    assertThat(catalog.importRun(catalog.activeId()).status()).isEqualTo("SUCCEEDED");
  }

  @Given("my commander is {string}")
  public void commander(String name) {
    remember(
        decks.create(new CreateDeck("Acceptance deck", Fixture.card(name).oracleId(), "general")));
  }

  @Given("my main deck contains:")
  public void mainDeck(DataTable table) {
    mainDeck = inputs(table);
  }

  @When("I check the construction rules")
  public void checkConstruction() {
    save(current.commander().name(), current.theme(), "Construction check", mainDeck);
    analysis = decks.analysis(current.id());
  }

  @Then("the analysis counts {int} cards including the commander")
  public void total(int expected) {
    assertThat(analysis.totalCards()).isEqualTo(expected);
    assertThat(decks.get(current.id()).totalCards()).isEqualTo(expected);
  }

  @Then("deck completion is {word}")
  public void completion(String expected) {
    assertThat(analysis.complete()).isEqualTo(booleanValue(expected));
  }

  @Then("recommendation eligibility is {word}")
  public void eligibility(String expected) {
    assertThat(analysis.recommendationEligible()).isEqualTo(booleanValue(expected));
  }

  @Then("the construction error codes are {string}")
  public void errors(String expected) {
    List<String> codes = expected.equals("none") ? List.of() : List.of(expected.split(",\\s*"));
    assertThat(analysis.errors())
        .extracting(Finding::code)
        .containsExactlyInAnyOrderElementsOf(codes);
  }

  @Then("{string} is identified by the {string} finding")
  public void offendingCard(String card, String code) {
    assertThat(analysis.errors())
        .anySatisfy(
            finding -> {
              assertThat(finding.code()).isEqualTo(code);
              assertThat(finding.oracleId()).isEqualTo(Fixture.card(card).oracleId());
              assertThat(finding.message()).contains(card);
            });
  }

  @Given("I save a revision with theme {string}, note {string} and cards:")
  public void saveRevision(String theme, String note, DataTable table) {
    save(current.commander().name(), theme, note, inputs(table));
  }

  @When("I save a new revision with commander {string}, theme {string}, note {string} and cards:")
  public void saveWithCommander(String commander, String theme, String note, DataTable table) {
    save(commander, theme, note, inputs(table));
  }

  @Then("the current revision is {int}")
  public void currentRevision(int expected) {
    assertThat(decks.get(current.id())).isEqualTo(snapshots.get(expected));
    assertThat(decks.get(current.id()).revision()).isEqualTo(expected);
    assertThat(decks.revisions(current.id()))
        .extracting(RevisionSummary::revision)
        .containsExactlyElementsOf(
            snapshots.keySet().stream().sorted(Comparator.reverseOrder()).toList());
  }

  @Then("revision {int} keeps its original snapshot")
  public void unchangedSnapshot(int number) {
    assertThat(snapshots).containsKey(number);
    // Record equality checks entries, quantities, commander, theme, note, and creation time.
    assertThat(decks.get(current.id(), number)).isEqualTo(snapshots.get(number));
  }

  @Then("revision {int} contains exactly:")
  public void revisionCards(int number, DataTable table) {
    Map<UUID, Integer> expected =
        inputs(table).stream()
            .collect(Collectors.toMap(EntryInput::oracleId, EntryInput::quantity));
    Map<UUID, Integer> actual =
        decks.get(current.id(), number).entries().stream()
            .collect(Collectors.toMap(e -> e.card().oracleId(), DeckEntry::quantity));
    assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
  }

  @Then("revision {int} has commander {string}, theme {string} and note {string}")
  public void metadata(int number, String commander, String theme, String note) {
    DeckView saved = decks.get(current.id(), number);
    assertThat(saved.commander().oracleId()).isEqualTo(Fixture.card(commander).oracleId());
    assertThat(saved.theme()).isEqualTo(theme);
    assertThat(saved.note()).isEqualTo(note);
  }

  @Then("comparing revision {int} to revision {int} shows exactly:")
  public void differences(int from, int to, DataTable table) {
    List<DiffEntry> expected =
        table.asMaps().stream()
            .map(
                row ->
                    new DiffEntry(
                        Fixture.card(row.get("card")).oracleId(),
                        row.get("card"),
                        Integer.parseInt(row.get("before")),
                        Integer.parseInt(row.get("after")),
                        Integer.parseInt(row.get("delta"))))
            .toList();
    assertThat(decks.diff(current.id(), from, to)).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Then("comparing revision {int} to itself shows no differences")
  public void sameRevision(int number) {
    assertThat(decks.diff(current.id(), number, number)).isEmpty();
  }

  @When("I try to save expecting revision {int} with cards:")
  public void staleSave(int expectedRevision, DataTable table) {
    assertThat(expectedRevision).isLessThan(current.revision());
    historyBeforeRejection = decks.revisions(current.id());
    rejectedSave =
        catchThrowableOfType(
            ApiException.class,
            () ->
                decks.save(
                    current.id(),
                    new SaveRevision(
                        expectedRevision,
                        current.commander().oracleId(),
                        "general",
                        "Stale edit must never be saved",
                        inputs(table))));
  }

  @Then("the save is rejected with status {int} and code {string}")
  public void conflict(int status, String code) {
    assertThat(rejectedSave).isNotNull();
    assertThat(rejectedSave.status()).isEqualTo(status);
    assertThat(rejectedSave.code()).isEqualTo(code);
    assertThat(rejectedSave.getMessage()).contains("Reload before saving");
  }

  @Then("the revision history is unchanged by the rejected save")
  public void unchangedHistory() {
    assertThat(decks.revisions(current.id())).isEqualTo(historyBeforeRejection);
    for (int number : snapshots.keySet()) unchangedSnapshot(number);
  }

  @When("I try to {string} in published revision {int}")
  public void mutateSnapshot(String change, int number) {
    String sql =
        switch (change) {
          case "rewrite its note" -> "UPDATE deck_revision SET note='Rewritten' WHERE id=:revision";
          case "delete the snapshot" -> "DELETE FROM deck_revision WHERE id=:revision";
          case "change a card quantity" ->
              "UPDATE deck_entry SET quantity=11 WHERE revision_id=:revision AND oracle_id=:card";
          case "remove a card" ->
              "DELETE FROM deck_entry WHERE revision_id=:revision AND oracle_id=:card";
          case "add a card" ->
              "INSERT INTO deck_entry(revision_id,oracle_id,quantity) VALUES(:revision,:card,1)";
          default -> throw new IllegalArgumentException("Unknown snapshot change: " + change);
        };
    historyBeforeRejection = decks.revisions(current.id());
    var statement = jdbc.sql(sql).param("revision", decks.revisionId(current.id(), number));
    if (sql.contains(":card"))
      statement.param(
          "card", Fixture.card(change.equals("add a card") ? "Cultivate" : "Forest").oracleId());
    rejectedMutation = catchThrowable(statement::update);
  }

  @Then("PostgreSQL rejects the change because the snapshot is immutable")
  public void immutable() {
    assertThat(rejectedMutation)
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    unchangedHistory();
  }

  private void save(String commander, String theme, String note, List<EntryInput> entries) {
    remember(
        decks.save(
            current.id(),
            new SaveRevision(
                current.revision(), Fixture.card(commander).oracleId(), theme, note, entries)));
  }

  private void remember(DeckView saved) {
    current = saved;
    snapshots.put(saved.revision(), saved);
  }

  private static List<EntryInput> inputs(DataTable table) {
    return table.asMaps().stream()
        .map(
            row ->
                new EntryInput(
                    Fixture.card(row.get("card")).oracleId(),
                    Integer.parseInt(row.get("quantity"))))
        .toList();
  }

  private static boolean booleanValue(String value) {
    assertThat(value).isIn("true", "false");
    return Boolean.parseBoolean(value);
  }
}
