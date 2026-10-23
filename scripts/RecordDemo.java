import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;

/**
 * Records actual browser states at one frame per second; no provider calls or fabricated screens.
 */
public class RecordDemo {
  private final WebDriver browser;
  private final WebDriverWait wait;
  private final Path frames;
  private final int secondsPerStep;
  private int frame;

  RecordDemo(int duration) throws Exception {
    ChromeOptions options = new ChromeOptions();
    options.addArguments(
        "--headless=new", "--window-size=1440,1080", "--no-sandbox", "--disable-dev-shm-usage");
    if (Files.exists(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")))
      options.setBinary("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
    browser = new ChromeDriver(options);
    wait = new WebDriverWait(browser, Duration.ofSeconds(15));
    frames = Path.of(".local/demo-frames-" + System.currentTimeMillis());
    Files.createDirectories(frames);
    secondsPerStep = Math.max(1, duration / 15);
  }

  void ready() {
    wait.until(d -> "true".equals(d.findElement(By.tagName("body")).getAttribute("data-ready")));
  }

  WebElement find(String selector) {
    return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector)));
  }

  void click(String selector) {
    WebElement target =
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
    ((JavascriptExecutor) browser)
        .executeScript("arguments[0].scrollIntoView({block:'center',behavior:'instant'});", target);
    wait.until(
        d ->
            (Boolean)
                ((JavascriptExecutor) d)
                    .executeScript(
                        "const r=arguments[0].getBoundingClientRect();const"
                            + " h=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return"
                            + " !!h && (h===arguments[0]||arguments[0].contains(h));",
                        target));
    target.click();
  }

  void show(String selector) {
    ((JavascriptExecutor) browser)
        .executeScript(
            "arguments[0].scrollIntoView({block:'center',behavior:'instant'});", find(selector));
  }

  void hold(String title, String detail) throws Exception {
    ((JavascriptExecutor) browser)
        .executeScript(
            "let n=document.getElementById('recording-caption');if(!n){n=document.createElement('aside');n.id='recording-caption';n.className='recording-caption';document.body.append(n)}n.replaceChildren();const"
                + " h=document.createElement('h3');h.textContent=arguments[0];const"
                + " p=document.createElement('p');p.textContent=arguments[1];n.append(h,p);",
            title,
            detail);
    System.out.println("Recording: " + title);
    for (int second = 0; second < secondsPerStep; second++) {
      long start = System.nanoTime();
      Files.write(
          frames.resolve(String.format("%06d.png", frame++)),
          ((TakesScreenshot) browser).getScreenshotAs(OutputType.BYTES));
      Thread.sleep(Math.max(1, 1000 - (System.nanoTime() - start) / 1_000_000));
    }
  }

  void record(Path output) throws Exception {
    try {
      browser.get("http://localhost:8080/");
      ready();
      hold(
          "ManaCheck / a Commander workspace",
          "A Java and PostgreSQL project: saved decks, immutable revisions, and explainable card"
              + " suggestions.");
      show("#decks-heading");
      hold(
          "A library with a history",
          "Every deck keeps its own revision journal. The full card catalog lives in PostgreSQL.");
      show("#new-deck");
      find("#new-name").sendKeys("Graveyard field notes");
      find("#commander-search").sendKeys("Meren");
      click("#commander-results [data-action='select-commander']");
      new Select(find("#new-theme")).selectByValue("graveyard");
      hold(
          "Start with a commander",
          "Pick a leader and a theme. The source Oracle ID keeps reprints tied to the same gameplay"
              + " card.");
      click("#create-deck");
      wait.until(ExpectedConditions.urlMatches(".*/decks/[a-f0-9-]+$"));
      ready();
      String deckUrl = browser.getCurrentUrl();
      hold(
          "One commander, a working list",
          "Incomplete decks stay saveable. Construction findings distinguish a draft from a"
              + " complete deck.");
      click(".import-panel summary");
      find("#import-text").sendKeys("18 Forest\n18 Swamp\n1 Sol Ring\n1 Cultivate");
      click("#preview-import");
      wait.until(
          ExpectedConditions.textToBePresentInElementLocated(
              By.id("import-preview"), "4 lines resolved"));
      hold(
          "Preview before replacing the list",
          "Names resolve against the local catalog. Unknown names and bad quantities need"
              + " correction before use.");
      click("#use-import");
      show("#decklist-heading");
      hold(
          "The working list is still a draft",
          "The imported entries are staged in the editor. Existing revisions stay unchanged until a"
              + " new save.");
      find("#revision-note").sendKeys("A foundation of lands, ramp, and a little direction");
      click("#save-deck");
      wait.until(ExpectedConditions.textToBe(By.id("revision-label"), "Revision 2"));
      hold(
          "Save an immutable snapshot",
          "A transaction checks the expected revision, writes the entries, and advances the deck"
              + " pointer.");
      show("#analysis-heading");
      hold(
          "Strict checks and flexible targets",
          "Card count, identity, copy limits, and legality are rules. Role coverage and mana curve"
              + " are composition heuristics.");
      click("#generate-suggestions");
      find(".suggestion-card");
      show("#suggestions-heading");
      hold(
          "SQL finds the candidates",
          "Bound SQL filters eligible cards and ranks missing roles, theme matches, and mana"
              + " efficiency. Java simulates each change.");
      click("#explain-ai");
      wait.until(
          d ->
              !d.findElement(By.id("explanation-status"))
                  .getText()
                  .contains("no model request made"));
      hold(
          "AI is optional",
          "This recording uses the offline fallback. Real provider responses must match known"
              + " suggestion IDs; strategic claims still need review.");
      click("[data-apply]");
      wait.until(ExpectedConditions.textToBe(By.id("revision-label"), "Revision 3"));
      show(".editor-heading");
      hold(
          "One reviewed change, a new revision",
          "Applying one suggestion repeats validation and seals a new snapshot. The previous run"
              + " becomes stale.");
      click("a[href$='/compare']");
      ready();
      show(".compare-panel");
      hold(
          "See the exact change",
          "A FULL OUTER JOIN shows cards on either side, including quantity changes and commander"
              + " replacements.");
      new Select(find("#compare-from")).selectByValue("1");
      wait.until(
          ExpectedConditions.textToBePresentInElementLocated(By.id("diff-entries"), "Forest"));
      hold(
          "Keep the whole story",
          "Compare the empty first revision with the imported and improved list. Notes preserve the"
              + " reason for a change.");
      browser.get(deckUrl);
      ready();
      hold(
          "Back to the deck",
          "The saved list and analysis survive navigation. A new catalog or deck revision"
              + " invalidates old recommendations.");
      browser.get("http://localhost:8080/");
      ready();
      hold(
          "Built to be inspected",
          "The repository includes PostgreSQL tests, twenty fixed recommendation scenarios, API"
              + " checks, browser journeys, and measured SQL plans.");
    } finally {
      browser.quit();
    }
    Files.createDirectories(output.toAbsolutePath().getParent());
    int result =
        new ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-framerate",
                "1",
                "-i",
                frames.resolve("%06d.png").toString(),
                "-vf",
                "fps=5",
                "-c:v",
                "libvpx-vp9",
                "-deadline",
                "realtime",
                "-cpu-used",
                "6",
                "-crf",
                "35",
                "-b:v",
                "0",
                "-pix_fmt",
                "yuv420p",
                output.toString())
            .inheritIO()
            .start()
            .waitFor();
    if (result != 0) throw new IllegalStateException("Video encoding failed: " + result);
    System.out.println("Recorded " + frame + " seconds to " + output);
  }

  public static void main(String[] args) throws Exception {
    int duration = args.length > 0 ? Integer.parseInt(args[0]) : 300;
    if (duration < 15 || duration > 600)
      throw new IllegalArgumentException("Choose 15–600 seconds");
    Path output = Path.of(args.length > 1 ? args[1] : "docs/evidence/manacheck-demo.webm");
    if (Files.exists(output))
      throw new IllegalArgumentException("Output already exists; choose a new path");
    new RecordDemo(duration).record(output);
  }
}
