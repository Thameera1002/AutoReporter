# AutoReporter – Allure Reporting Guide

This guide covers, step by step, how Allure is set up in this project, what each Allure annotation does, how Allure works together with the TestNG listeners, and how to open the report with full execution history.
For a file-by-file description of the whole framework, see [FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md).

---

## 1. How Allure works – the big picture

Allure has **two separate parts**:

| Part | When it runs | What it produces |
|---|---|---|
| **Allure adapter** (`allure-testng` library) | *During* the test run, inside the test JVM | Raw JSON files in `allure-results/` – one per test, plus attachments (screenshots, text). |
| **Allure report generator** (Allure command line, `allure`) | *After* the run | A static HTML website in `allure-report/` built from those JSON files. |

```
 Test run (mvn test)                              After the run
┌──────────────────────────────────┐            ┌────────────────────────────────────┐
│ TestNG ─▶ AllureTestNg listener  │  writes    │ allure generate                    │
│           @Step / @Epic / ...    │ ─────────▶ │   allure-results/  ──▶ allure-report/│
│           Reporter.attach...     │  JSON+PNG  │   (+ history/)        (+ history/) │
└──────────────────────────────────┘            └────────────────────────────────────┘
                                                 allure open allure-report  → browser
```

The raw results alone are not a report. You always need a **generate** (or **serve**) step to see it.

---

## 2. Step-by-step: configuring Allure in this project

These are the steps that were done to set up Allure. Follow the same steps to add Allure to another TestNG project.

### Step 1 – Install the Allure command line (once per machine)

Needed to generate and open reports.

```bash
# macOS
brew install allure
# Windows (Scoop)
scoop install allure
# Check
allure --version
```

Allure needs Java on the PATH. (The Maven plugin can also download its own copy into `.allure/` – see Step 5 – but the system `allure` command is what this project's script uses.)

### Step 2 – Add the Allure TestNG adapter to `pom.xml`

```xml
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-testng</artifactId>
    <version>${allure.version}</version>   <!-- 2.34.0 -->
</dependency>
```

This jar contains:
- the `AllureTestNg` listener, which **registers itself** with TestNG through `META-INF/services` (no `<listeners>` entry in `testng.xml` is needed);
- the annotations (`@Step`, `@Epic`, `@Feature`, `@Severity`, …) and the `Allure` API (`Allure.step`, `Allure.addAttachment`).

### Step 3 – Add AspectJ and attach it to the test JVM

`@Step` and `@Attachment` work by intercepting method calls. That interception is done by **AspectJ**, which must be loaded as a Java agent when the tests start.

```xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>${aspectj.version}</version>
</dependency>
```

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            -javaagent:"${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar"
        </argLine>
        ...
```

> Without the agent the tests still pass, but every `@Step` is missing from the report.
> `Allure.step("text")` (used by our `Reporter.log`) works even without AspectJ, because it is a direct API call.

### Step 4 – Choose where results are written

By default Allure writes to `allure-results` in the working directory, and Maven projects usually put it under `target/`. **`mvn clean` deletes `target/`**, which would also delete the history. So this project keeps both folders in the project root:

`pom.xml`:
```xml
<properties>
    <allure.results.dir>${project.basedir}/allure-results</allure.results.dir>
    <allure.report.dir>${project.basedir}/allure-report</allure.report.dir>
</properties>
...
<systemPropertyVariables>
    <allure.results.directory>${allure.results.dir}</allure.results.directory>  <!-- read by Allure -->
    <allure.report.directory>${allure.report.dir}</allure.report.directory>     <!-- read by our history listener -->
</systemPropertyVariables>
```

`src/test/resources/allure.properties` (used by IDE runs, where Surefire's settings don't apply):
```properties
allure.results.directory=allure-results
```

### Step 5 – Add the Allure Maven plugin (optional)

```xml
<plugin>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-maven</artifactId>
    <version>${allure.maven.version}</version>
    <configuration>
        <reportVersion>${allure.version}</reportVersion>
        <resultsDirectory>${allure.results.dir}</resultsDirectory>
        <reportDirectory>${allure.report.dir}</reportDirectory>
    </configuration>
</plugin>
```

It adds `mvn allure:report` and `mvn allure:serve` and downloads its own Allure command line into `.allure/allure-<version>/`. It is useful on machines without the `allure` command, but it replaces our run labels (see section 6.4).

### Step 6 – Register the project's own listeners

`src/test/resources/META-INF/services/org.testng.ITestNGListener`:
```
com.ucsc.automation.listeners.AllureHistoryListener
com.ucsc.automation.listeners.RetryTransformer
```

- `AllureHistoryListener` – keeps history, writes run labels and environment info (section 5.2).
- `RetryTransformer` – attaches `RetryAnalyzer` to all tests so retries show in Allure (section 5.3).

### Step 7 – Add Allure annotations and attachments to the code

- Page methods get `@Step` (section 3.2).
- Test classes/methods get `@Epic`, `@Feature`, `@Severity`, `@Description`, `@Flaky` (section 3).
- `BaseTest.tearDown` attaches a screenshot when a test fails (section 4).

### Step 8 – Run, generate, open

Run these **from the `reporter` folder** (see [section 6.0](#60-first-be-in-the-reporter-folder)):

```bash
cd reporter
mvn clean test
allure generate --clean allure-results -o allure-report
allure open allure-report
```
or simply `./run-tests.sh`. Details in sections 6 and 7.

---

## 3. Allure annotations (tags) used in this project

### 3.1 Summary

| Annotation | Placed on | Where it appears in the report | Example in project |
|---|---|---|---|
| `@Epic` | Class / method | **Behaviors** tab – top level | `@Epic("Demo Site")` on all test classes |
| `@Feature` | Class / method | **Behaviors** tab – second level | `@Feature("Login")`, `@Feature("Dashboard")`, `@Feature("Flaky Test Demo")` |
| `@Severity` | Method | Severity label on the test, **Graphs** tab severity chart, filter in test list | `@Severity(SeverityLevel.BLOCKER)` on valid login |
| `@Description` | Method | **Description** section at the top of a test's page | "Log in with valid credentials and verify the dashboard loads." |
| `@Test(description = …)` | Method (TestNG) | The **test name** shown everywhere in Allure (instead of the Java method name) | "Valid user can log in" |
| `@Step` | Any method (page objects) | Each call becomes a numbered **step** in the test's Execution section, with timing and status | `@Step("Enter username: {username}")` |
| `@Flaky` | Class / method | Flaky (💣) icon on the test, flaky filter in the test list. This is the only way a test gets the flaky mark; retries alone don't add it. | All `TS_Flaky` tests |

### 3.2 `@Step` in detail

```java
@Step("Enter username: {username}")
public PG_Login enterUsername(String username) { ... }
```

- `{username}` is replaced with the argument value → *"Enter username: standard_user"*.
- Steps nest: `loginAs()` is a step that contains *Enter username*, *Enter password* and *Click Login button* as sub-steps.
- If an exception is thrown inside a step, that step is marked failed/broken. This shows exactly **where** the test went wrong.
- Leave secrets out of step names: `enterPassword` uses the fixed text "Enter password" so the password isn't printed in the report.

### 3.3 Severity levels

`BLOCKER` > `CRITICAL` > `NORMAL` > `MINOR` > `TRIVIAL`. They are used to sort and filter, and to judge how serious a failure is. In this project, valid login is `BLOCKER` (nothing else works without it) and logout and the locked-out user test are `CRITICAL`.

### 3.4 Programmatic API (through `utils/Reporter`)

| Call | Result in report |
|---|---|
| `Reporter.log("Browser started: chrome")` → `Allure.step(...)` | A passed step with that text. |
| `Reporter.attachText("Error message", text)` → `Allure.addAttachment(..., "text/plain", ...)` | A text attachment you can open in the test. |
| `Reporter.attachScreenshot("Dashboard after login")` → `Allure.addAttachment(..., "image/png", ...)` | An inline screenshot. |

### 3.5 Tags you can add later

| Annotation | Use |
|---|---|
| `@Story("…")` | Third level in Behaviors (Epic → Feature → Story). |
| `@Owner("name")` | Who owns the test. |
| `@Link`, `@Issue("BUG-12")`, `@TmsLink("TC-5")` | Links to Jira or a test management tool (base URLs set in `allure.properties`, e.g. `allure.link.issue.pattern=https://jira.example.com/browse/{}`). |
| `@Attachment` | Make a method's return value an attachment. |

---

## 4. Test statuses in Allure

| Status | Colour | Cause | Demo test |
|---|---|---|---|
| **Passed** | Green | Test finished without error. | `TS_Common`, `TS_Dashboard` |
| **Failed** | Red | An **assertion** failed (`AssertionError`) – usually a product bug. | `TC_201`, `TC_202` |
| **Broken** | Yellow | Any **other exception** (element not found, timeout, bad data) – usually a test or environment problem. | `TC_203`, `TC_204` |
| **Skipped** | Grey | `SkipException`, or a `dependsOnMethods` test didn't pass. | `TC_205`, `TC_206` |

The **Categories** tab groups failures: *Product defects* (failed) and *Test defects* (broken). Custom categories can be defined in a `categories.json` file placed in `allure-results`.

For every failed or broken test, `BaseTest.tearDown` attaches a **screenshot** taken at the moment of failure, under *Tear down* in the test's page.

---

## 5. How Allure works with the listeners

### 5.1 Order of events in one run

```
mvn clean test
│
├─ TestNG starts
│   ├─ ServiceLoader registers: AllureTestNg, AllureHistoryListener, RetryTransformer
│   ├─ RetryTransformer.transform()          (once per @Test)  → retryAnalyzer = RetryAnalyzer
│   └─ AllureHistoryListener.onExecutionStart()  (once)
│         1. delete allure-results/
│         2. copy allure-report/history/* → allure-results/history/
│         3. .allure/build-number  N → N+1
│         4. write allure-results/executor.json        ("Run #N (date)")
│         5. write allure-results/environment.properties
│
├─ For each test:
│   ├─ AllureTestNg: test started
│   ├─ BaseTest.setUp()          → browser opens       (Allure: "Set up" section)
│   ├─ test method               → @Step calls recorded (Allure: "Execution" section)
│   ├─ BaseTest.tearDown()       → screenshot if failed, browser closed (Allure: "Tear down")
│   ├─ AllureTestNg: writes <uuid>-result.json (+ attachments)
│   └─ if it failed: RetryAnalyzer.retry() → true → TestNG marks this attempt "retried"
│                                                 and runs the test again
└─ TestNG finishes
```

### 5.2 `AllureHistoryListener` – why it exists

Allure history works like this:

1. When Allure **generates** a report, it reads `allure-results/history/` (the previous runs) and writes an **updated** `allure-report/history/` that includes the current run.
2. For the next report to know about earlier runs, that `history/` folder must be copied back into the **new** `allure-results/` **before** generating.

Allure does not do step 2 itself. The listener does it automatically at the start of every run, for both Maven and IDE runs. It also:
- clears old results, so a report shows only the latest run;
- writes `executor.json`, so the Trend chart shows *"Run #6 (2026-11-20 23:15)"* instead of unnamed points;
- writes `environment.properties`, filling the *Environment* widget (browser, headless, URL, OS, Java).

### 5.3 `RetryTransformer` + `RetryAnalyzer` – how retries reach the report

1. `RetryTransformer` gives every test the `RetryAnalyzer`.
2. When a test fails, TestNG asks `RetryAnalyzer.retry()`. While attempts < `retry.count` (from `config.properties`), it answers `true`.
3. TestNG marks the failed attempt as **retried/skipped** and runs the test again with a fresh browser (`setUp` and `tearDown` run again).
4. Allure writes **one result file per attempt**. All attempts of a test share the same **historyId** (based on class, method and parameters).
5. When generating, Allure groups results with the same historyId: the **last attempt** is the test's status, and the earlier ones appear in the test's **Retries** tab with their own errors and screenshots.
6. A test that has retries gets a **retries icon** in the test list. Allure does **not** mark it flaky on its own: only tests annotated with `@Flaky` get the flaky mark and filter (checked with Allure 2.34). Add `@Flaky` to tests you know are unstable.

Change the count in `config.properties` (`retry.count=2`) or per run (`-Dretry.count=0`).

> **Different counts in Maven and Allure:** Surefire's console counts every attempt (e.g. *Tests run: 25, Skipped: 13*), because retried attempts are counted as skipped. Allure counts each **test once** (e.g. *14 total*). The Allure numbers are the real result.

---

## 6. Opening the report after execution

### 6.0 First: be in the `reporter` folder

> ⚠️ **Run every Allure command from the `reporter` folder, not from `AutoReporter`.**
> `allure generate` looks for `allure-results` in the folder your terminal is in. The results are in `AutoReporter/reporter/allure-results`, one level below `AutoReporter`.

**What goes wrong in the wrong folder.** Running the commands from `AutoReporter`:

```
/Users/.../UCSC/AutoReporter % allure generate --clean allure-results -o allure-report
allure-results does not exist
Report successfully generated to allure-report
```

Allure doesn't stop when it can't find the results. It prints a warning and still builds an **empty** report, in a new folder `AutoReporter/allure-report`. `allure open allure-report` then opens that report, which has no tests in it.

**How to fix it:**

1. Check where you are. The prompt shows the current folder, or run `pwd`:
   ```bash
   pwd
   # wrong:  /Users/.../UCSC/AutoReporter
   # right:  /Users/.../UCSC/AutoReporter/reporter
   ```
2. Move into `reporter`:
   ```bash
   cd reporter
   ```
3. Check that results exist. This should list `.json` files:
   ```bash
   ls allure-results
   ```
4. Generate and open:
   ```bash
   allure generate --clean allure-results -o allure-report
   allure open allure-report
   ```
5. Delete the empty report the wrong folder produced. It holds no data or history:
   ```bash
   rm -rf ../allure-report
   ```

**Alternative: stay in `AutoReporter` and give the paths:**
```bash
allure generate --clean reporter/allure-results -o reporter/allure-report
allure open reporter/allure-report
```

**Or use the script, which works from any folder** because it moves into `reporter` itself:
```bash
./reporter/run-tests.sh     # from AutoReporter
./run-tests.sh              # from reporter
```

All commands below assume you are in `reporter`.

### 6.1 Recommended: one command

```bash
./run-tests.sh                       # run → generate with history → open
./run-tests.sh -Dheadless=true       # Maven options are passed through
```

### 6.2 Manually, with the Allure CLI (keeps history and run labels)

```bash
mvn clean test
allure generate --clean allure-results -o allure-report
allure open allure-report
```

- `generate` builds the HTML into `allure-report/`. `--clean` empties the folder first; the history is safe because the listener already copied it into `allure-results/history`.
- `open` starts a small local web server and opens the browser. Stop it with `Ctrl+C`.

### 6.3 Quick look without saving: `allure serve`

```bash
allure serve allure-results
```

Generates into a **temporary** folder and opens it. It shows the history it is given, but the current run is **not** saved into `allure-report/`, so it won't be in the next report's history.

### 6.4 With the Maven plugin

```bash
mvn allure:report     # → allure-report/   (history kept, but run label becomes "Maven")
mvn allure:serve      # temporary, like allure serve
```

### 6.5 After running tests from the IDE

The listener runs for IDE runs too, so only the report step is needed:
```bash
allure generate --clean allure-results -o allure-report && allure open allure-report
```

### 6.6 Why not double-click `index.html`?

Browsers block a page opened from a local file (`file://`) from loading its JSON data, so the report stays on "Loading…". Always use `allure open` (or any web server) to view it.

---

## 7. Getting the report with execution history

### 7.1 What history gives you

| Where | What you see |
|---|---|
| **Overview → Trend** chart | Passed/failed/broken/skipped counts for each run, labelled *Run #N*. |
| **Graphs** tab | Duration and retries trends. |
| Test page → **History** tab | That test's status in recent runs, plus a success rate. |
| Test page → **Retries** tab | All attempts in *this* run. |
| Status-change icons | "New failed", "New passed", etc., computed against the previous run. |

### 7.2 Rules for history to keep working

1. **Always generate into the same folder:** `allure-report/`. The history lives in `allure-report/history/`.
2. **Generate after every run you want in the history.** If you run tests twice without generating, the first run is lost from the history.
3. **Don't delete `allure-report/`** (or `.allure/build-number`, or the run numbering restarts at 1). `mvn clean` is safe because neither is in `target/`.
4. Use `allure generate` (not `serve`) to add a run to the history.
5. A test's history is matched by **historyId**. Renaming a test method or class starts a new history for it.
6. Allure keeps only a limited number of recent runs (about 20 by default); older ones drop off.

### 7.3 Verify history is working

```bash
./run-tests.sh -Dheadless=true     # run 1
./run-tests.sh -Dheadless=true     # run 2
```
Open *Overview*: the Trend chart should show two points, *Run #N-1* and *Run #N*. Open any test → *History*: it lists the earlier result.

### 7.4 Starting history from scratch

```bash
rm -rf allure-report allure-results .allure/build-number
```

### 7.5 On a CI server (e.g. Jenkins / GitHub Actions)

CI machines start empty, so `allure-report/history` must be **kept between builds**: save it as a build artifact or cache, and restore it into `allure-report/history/` before `mvn test`. The listener will then copy it into the results as usual. (The Jenkins Allure plugin does this automatically.)

---

## 8. Troubleshooting

| Problem | Cause / fix |
|---|---|
| `allure-results does not exist`, then the report opens with no data | The command was run from the wrong folder (e.g. `AutoReporter`). `cd reporter` and generate again; delete the empty `AutoReporter/allure-report`. See [section 6.0](#60-first-be-in-the-reporter-folder). |
| No steps in the report | AspectJ agent not loaded. Run through Maven, or add the same `-javaagent:` to the IDE's run configuration VM options. |
| Report shows "Loading…" forever | Opened `index.html` directly. Use `allure open allure-report`. |
| Trend shows only one run | Report generated to a different folder, `allure serve` was used, or `allure-report/` was deleted. |
| Run labels show "Maven" | Report built with `mvn allure:report`. Use `allure generate`. |
| Old tests appear in the report | Results not cleared. Check that `AllureHistoryListener` is listed in `META-INF/services/org.testng.ITestNGListener`. |
| `allure: command not found` | Install the CLI (Step 1), or use `mvn allure:report` / `mvn allure:serve`. |
| Retries not happening | `retry.count=0`, or `RetryTransformer` not registered. Skipped tests are never retried. |
