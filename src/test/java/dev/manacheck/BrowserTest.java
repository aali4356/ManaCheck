package dev.manacheck;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.boot.test.web.server.LocalServerPort;

@Tag("browser")
class BrowserTest extends PostgresTestBase {
  @LocalServerPort int port;
  WebDriver browser;
  WebDriverWait wait;

  String base() {
    return "http://localhost:" + port;
  }

  @BeforeEach
  void open() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments(
        "--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1080");
    String binary = System.getenv("CHROME_BIN");
    if (binary != null) options.setBinary(binary);
    else if (Files.exists(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")))
      options.setBinary("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
    options.setExperimentalOption(
        "prefs", Map.of("profile.managed_default_content_settings.images", 2));
    browser = new ChromeDriver(options);
    wait = new WebDriverWait(browser, Duration.ofSeconds(12));
  }

  @AfterEach
  void close(TestInfo info) {
    if (browser != null) {
      try {
        screenshot("final-" + info.getTestMethod().orElseThrow().getName());
      } catch (Exception ignored) {
      }
      browser.quit();
    }
  }

  WebElement visible(String css) {
    return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
  }

  void click(String css) {
    WebElement target = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(css)));
    ((JavascriptExecutor) browser)
        .executeScript(
            "arguments[0].scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});",
            target);
    wait.until(
        d ->
            (Boolean)
                ((JavascriptExecutor) d)
                    .executeScript(
                        "const r=arguments[0].getBoundingClientRect();const"
                            + " hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return"
                            + " !!hit && (hit===arguments[0] || arguments[0].contains(hit));",
                        target));
    target.click();
  }

  void ready() {
    wait.until(d -> "true".equals(d.findElement(By.tagName("body")).getAttribute("data-ready")));
  }

  void screenshot(String name) throws Exception {
    Path folder = Path.of("target/browser");
    Files.createDirectories(folder);
    Files.write(
        folder.resolve(name + ".png"),
        ((TakesScreenshot) browser).getScreenshotAs(OutputType.BYTES));
  }

  @Test
  void createImportSuggestApplyAndCompare() throws Exception {
    browser.get(base() + "/");
    ready();
    screenshot("library");
    visible("#new-name").sendKeys("Browser regression deck");
    visible("#commander-search").sendKeys("Meren");
    click("#commander-results [data-action='select-commander']");
    new Select(visible("#new-theme")).selectByValue("graveyard");
    click("#create-deck");
    wait.until(ExpectedConditions.urlMatches(".*/decks/[a-f0-9-]+$"));
    ready();
    wait.until(ExpectedConditions.textToBe(By.id("save-state"), "All changes saved"));
    click(".import-panel summary");
    visible("#import-text").sendKeys("18 Forest\n18 Swamp\n1 Sol Ring\n1 Cultivate");
    click("#preview-import");
    wait.until(
        ExpectedConditions.textToBePresentInElementLocated(
            By.id("import-preview"), "4 lines resolved"));
    click("#use-import");
    visible("#revision-note").sendKeys("Imported a starting list");
    click("#save-deck");
    wait.until(ExpectedConditions.textToBe(By.id("revision-label"), "Revision 2"));
    click("#generate-suggestions");
    visible(".suggestion-card");
    screenshot("editor");
    click("#explain-ai");
    wait.until(
        ExpectedConditions.textToBePresentInElementLocated(
            By.id("explanation-status"), "Fallback"));
    click("[data-apply]");
    wait.until(ExpectedConditions.textToBe(By.id("revision-label"), "Revision 3"));
    assertThat(visible("#deck-total").getText()).isEqualTo("40 / 100");
    click("a[href$='/compare']");
    ready();
    visible("#diff-entries tr");
    screenshot("compare");
    assertThat(visible("#diff-entries").getText()).contains("+1");
    assertThat(browser.manage().logs().get("browser").getAll())
        .noneMatch(e -> e.getLevel().intValue() >= 1000 && !e.getMessage().contains("favicon"));
  }

  @Test
  void librarySearchSortAndEmptyState() {
    decks.create(
        new Domain.CreateDeck(
            "Zebra graveyard", Fixture.card("Meren of Clan Nel Toth").oracleId(), "graveyard"));
    var alpha =
        decks.create(
            new Domain.CreateDeck(
                "Alpha elves", Fixture.card("Ezuri, Renegade Leader").oracleId(), "general"));
    decks.save(
        alpha.id(),
        new Domain.SaveRevision(
            alpha.revision(),
            alpha.commander().oracleId(),
            "general",
            "Lands",
            List.of(new Domain.EntryInput(Fixture.card("Forest").oracleId(), 24))));
    browser.get(base() + "/");
    ready();
    new Select(visible("#deck-sort")).selectByValue("name");
    assertThat(visible("#deck-list .deck-name").getText()).isEqualTo("Alpha elves");
    new Select(visible("#deck-sort")).selectByValue("cards");
    assertThat(visible("#deck-list .deck-name").getText()).isEqualTo("Alpha elves");
    visible("#deck-filter").sendKeys("meren");
    assertThat(browser.findElements(By.cssSelector("#deck-list li:not([hidden])"))).hasSize(1);
    assertThat(visible("#deck-list li:not([hidden]) .deck-name").getText())
        .isEqualTo("Zebra graveyard");
    visible("#deck-filter").clear();
    visible("#deck-filter").sendKeys("no matching deck");
    assertThat(visible("#deck-empty").getText()).contains("No matching decks");
    visible("#deck-filter").clear();
    visible("#deck-filter").sendKeys(Keys.SPACE);
    assertThat(browser.findElements(By.cssSelector("#deck-list li:not([hidden])"))).hasSize(2);
  }

  @Test
  void mobileLayoutAndInvalidImportStayUsable() throws Exception {
    browser.manage().window().setSize(new Dimension(390, 844));
    browser.get(base() + "/");
    ready();
    assertThat(visible("h1").getText()).isEqualTo("Your decks");
    Number overflow =
        (Number)
            ((JavascriptExecutor) browser)
                .executeScript("return document.documentElement.scrollWidth - window.innerWidth");
    assertThat(overflow.intValue()).isLessThanOrEqualTo(1);
    var deck =
        decks.create(
            new Domain.CreateDeck(
                "Mobile review", Fixture.card("Ezuri, Renegade Leader").oracleId(), "general"));
    browser.get(base() + "/decks/" + deck.id());
    ready();
    wait.until(ExpectedConditions.textToBe(By.id("save-state"), "All changes saved"));
    click(".import-panel summary");
    visible("#import-text").sendKeys("1 Invented card name");
    click("#preview-import");
    wait.until(
        ExpectedConditions.textToBePresentInElementLocated(
            By.id("import-preview"), "unknown card"));
    assertThat(browser.findElement(By.id("use-import")).isDisplayed()).isFalse();
    assertThat(decks.get(deck.id()).revision()).isEqualTo(1);
    screenshot("mobile-editor");
    Number editorOverflow =
        (Number)
            ((JavascriptExecutor) browser)
                .executeScript("return document.documentElement.scrollWidth - window.innerWidth");
    assertThat(editorOverflow.intValue()).isLessThanOrEqualTo(1);
  }
}
