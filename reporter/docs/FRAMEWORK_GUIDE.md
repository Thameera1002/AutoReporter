# AutoReporter – Framework Guide

This guide explains every file in the `reporter` project: what it is for, what it contains, and how it connects to the rest.
For Allure setup, tags, listeners and report history, see [ALLURE_GUIDE.md](ALLURE_GUIDE.md).

---

## 1. Overview

AutoReporter is a **Selenium + TestNG** UI test automation framework built on the **Page Object Model (POM)**, with **Allure** for reporting and **WebDriverManager** for browser drivers.
The demo tests run against the public practice site <https://www.saucedemo.com>.

| Tool | Role |
|---|---|
| Selenium WebDriver | Drives the real browser (open pages, click, type, read text). |
| TestNG | Test runner: finds `@Test` methods, runs setup/teardown, groups tests into suites, retries failures. |
| WebDriverManager | Downloads the browser driver (chromedriver, geckodriver, msedgedriver) that matches the installed browser. |
| Allure | Builds an interactive HTML report with steps, screenshots, history, trends and retries. |
| AspectJ | Weaves Allure's `@Step` annotations into page methods at run time, so each call becomes a report step. |
| Maven | Downloads libraries, compiles the code, runs the tests and builds the report. |

### How a test run flows

```
mvn clean test
  └─ Surefire plugin starts TestNG with testng.xml (and the AspectJ agent)
       ├─ AllureHistoryListener.onExecutionStart()  → prepare allure-results + history
       ├─ RetryTransformer                          → attach RetryAnalyzer to every @Test
       └─ for each @Test:
            BaseTest.setUp()        → WebDriverManager opens the browser
            Test method             → uses page objects (PG_Login, PG_Dashboard)
                                       each @Step method is recorded by Allure
            BaseTest.tearDown()     → screenshot on failure, browser closed
            RetryAnalyzer           → re-runs the test if it failed (up to retry.count)
  └─ Allure TestNG listener has written JSON results to allure-results/

allure generate --clean allure-results -o allure-report   → HTML report (with history)
allure open allure-report                                 → view in browser
```

---

## 2. Project structure

```
reporter/
├── pom.xml                         Maven build: libraries + plugins
├── testng.xml                      Default suite: the real tests (login, dashboard)
├── testng-demo.xml                 Demo suite: intentional failed/broken/skipped/flaky tests
├── run-tests.sh                    One command: run tests → build report → open it
├── docs/                           These guides
├── src/main/java/com/ucsc/automation/
│   ├── pages/                      Page objects (what's on each page, what you can do there)
│   │   ├── BasePage.java
│   │   ├── PG_Login.java
│   │   └── PG_Dashboard.java
│   └── utils/                      Framework helpers
│       ├── PropertyHandler.java
│       ├── WebDriverManager.java
│       └── Reporter.java
├── src/main/resources/
│   └── config.properties           Site URL, credentials, browser, timeouts, retry count
├── src/test/java/com/ucsc/automation/
│   ├── BaseTest.java               Browser setup/teardown for every test
│   ├── listeners/                  TestNG listeners
│   │   ├── AllureHistoryListener.java
│   │   ├── RetryAnalyzer.java
│   │   └── RetryTransformer.java
│   ├── TS_Common/TS_Common.java         Login tests
│   ├── TS_Dashboard/TS_Dashboard.java   Dashboard tests
│   ├── TS_ReportDemo/TS_ReportDemo.java Intentional failed / broken / skipped tests
│   └── TS_Flaky/TS_Flaky.java           Intentional flaky tests
├── src/test/resources/
│   ├── allure.properties           Where Allure writes results (IDE runs)
│   └── META-INF/services/org.testng.ITestNGListener   Auto-registers our listeners
│
│   (generated – not source)
├── allure-results/                 Raw JSON results of the latest run
├── allure-report/                  Generated HTML report (also holds the history)
├── .allure/                        Run counter (build-number) + Allure CLI downloaded by the Maven plugin
└── target/                         Compiled classes, Surefire reports (wiped by mvn clean)
```

**Naming convention**
- `PG_` = **P**a**g**e object (one class per web page).
- `TS_` = **T**est **S**uite (one class per feature area).
- `TC_nnn_` = **T**est **C**ase, numbered by suite (0xx login, 1xx dashboard, 2xx status demo, 3xx flaky).

**Why `pages` and `utils` are in `src/main`, but tests in `src/test`:** the framework code (pages, helpers) is reusable and could be packaged as a library; the tests are consumers of it. Maven compiles `src/main` first, then `src/test` against it.

---

## 3. pom.xml – Maven build

### 3.1 Properties

| Property | Value | Purpose |
|---|---|---|
| `maven.compiler.source/target` | 17 | Java language level. |
| `project.build.sourceEncoding` | UTF-8 | Avoids platform-dependent encoding warnings. |
| `selenium.version`, `testng.version`, … | – | All versions in one place so upgrades are a one-line change. |
| `suite` | `testng.xml` | Default suite file. Run another with `mvn test -Dsuite=testng-demo.xml`. |
| `allure.results.dir` | `${project.basedir}/allure-results` | Where raw results are written. **Outside `target/`** so `mvn clean` keeps history. |
| `allure.report.dir` | `${project.basedir}/allure-report` | Where the HTML report is written. |

### 3.2 Dependencies (libraries)

| Dependency | Why it is needed |
|---|---|
| `selenium-java` | WebDriver API and the Chrome/Firefox/Edge drivers' Java bindings. |
| `testng` | Test annotations (`@Test`, `@BeforeMethod`…), assertions, suites, listeners, retry. |
| `allure-testng` | Allure's TestNG integration. It ships a TestNG listener (`AllureTestNg`) that **registers itself automatically** and writes one JSON file per test into `allure-results`. Also provides the annotations `@Step`, `@Epic`, `@Severity`, etc. |
| `webdrivermanager` | Resolves and downloads the right driver binary for the installed browser version. |
| `aspectjweaver` | Required by Allure so that `@Step` / `@Attachment` annotated methods are intercepted and recorded. Loaded as a Java agent (see Surefire). |
| `slf4j-simple` | A logging backend. Selenium and WebDriverManager log through SLF4J; without a backend you only get a "no logger" warning. |

Selenium, TestNG and Allure use the default (`compile`) scope because the page objects and utilities in `src/main` use them too.

### 3.3 Plugins

**maven-compiler-plugin** – compiles the code with `<release>17</release>` (guarantees only Java 17 APIs are used, even when built with a newer JDK).

**maven-surefire-plugin** – runs the tests during `mvn test`.

| Setting | What it does |
|---|---|
| `suiteXmlFiles` → `${suite}` | Runs TestNG with `testng.xml` (or the file passed in `-Dsuite`). |
| `argLine` → `-javaagent:…aspectjweaver.jar` | Starts the test JVM with AspectJ attached, so Allure `@Step` methods are recorded. Without it, steps silently disappear from the report. |
| `systemPropertyVariables` → `allure.results.directory` | Tells the Allure listener where to write results. |
| `systemPropertyVariables` → `allure.report.directory` | Read by our `AllureHistoryListener` to find the previous report's history. |
| *(test failures)* | By default a failed test makes the build fail (exit code ≠ 0). Add `-Dmaven.test.failure.ignore=true` to finish the build anyway with failures recorded. Jenkins uses this so the reports are still published and the build is marked UNSTABLE. |

**allure-maven** – adds the Maven goals `allure:report` and `allure:serve`.

| Setting | What it does |
|---|---|
| `reportVersion` | Which Allure command-line version the plugin downloads (into `.allure/`). |
| `resultsDirectory` | Read results from `allure-results`. |
| `reportDirectory` | Write the report to `allure-report`. |

> The plugin overwrites our run labels (`executor.json`) with a generic "Maven" one, so the recommended way to build the report is the Allure CLI (`allure generate`) – see the Allure guide.

---

## 4. Configuration files

### 4.1 `src/main/resources/config.properties`

All test settings in one place. **Any key can be overridden from the command line** with `-Dkey=value` (system properties win over the file), e.g. `mvn test -Dbrowser=firefox -Dheadless=true -Dretry.count=0`.

| Key | Example | Meaning |
|---|---|---|
| `base.url` | `https://www.saucedemo.com/` | Address of the application under test. |
| `username` / `password` | `standard_user` / `secret_sauce` | Valid demo login. |
| `invalid.username` | `locked_out_user` | A demo user the site refuses ("locked out"). |
| `browser` | `chrome` | `chrome`, `firefox` or `edge`. |
| `headless` | `false` | `true` runs the browser without a window (faster, needed on CI servers). |
| `implicit.wait` | `0` | Selenium implicit wait in seconds. Kept at 0 because we use explicit waits (mixing both causes unpredictable delays). |
| `explicit.wait` | `10` | Max seconds `BasePage` waits for an element to become visible/clickable. |
| `page.load.timeout` | `30` | Max seconds for a page to load. |
| `retry.count` | `2` | How many times a failed/broken test is re-run. `0` disables retries. |

### 4.2 `testng.xml` and `testng-demo.xml`

The suite definitions used by Maven. Which one runs is chosen with `-Dsuite` (default `testng.xml`).

| File | Suite name | `<test>` groups | Use |
|---|---|---|---|
| `testng.xml` | AutoReporter Suite | *Login Tests* (`TS_Common`), *Dashboard Tests* (`TS_Dashboard`) | Normal runs, locally and on Jenkins. Should be all green. |
| `testng-demo.xml` | AutoReporter Demo Suite | *Report Status Demo* (`TS_ReportDemo`), *Flaky Test Demo* (`TS_Flaky`) | Showing report features. Always has failures on purpose. |

- `<parameter name="browser" value="chrome"/>` – passed into `BaseTest.setUp(browser)`. It takes priority over `browser` in `config.properties` when running via the suite file.
- Each `<test>` block groups classes and appears as a separate group in the report.
- No `<listeners>` are declared: all listeners are registered automatically (see 4.4), so they also work when a single class is run from the IDE.

### 4.3 `src/test/resources/allure.properties`

```properties
allure.results.directory=allure-results
```

Allure reads this file from the classpath. It matters for **IDE runs** (VS Code / IntelliJ "Run test"), where Surefire's system properties are not applied. The path is relative to the working directory (the `reporter` folder), so IDE and Maven runs write to the same place. When Maven sets the system property, it takes priority.

### 4.4 `src/test/resources/META-INF/services/org.testng.ITestNGListener`

```
com.ucsc.automation.listeners.AllureHistoryListener
com.ucsc.automation.listeners.RetryTransformer
```

This is Java's **ServiceLoader** mechanism. TestNG scans the classpath for this file and registers every class listed in it as a listener – for every run, however it is started (Maven, IDE, `testng.xml` or a single class). Allure's own `AllureTestNg` listener is registered the same way from inside the `allure-testng` jar.

---

## 5. Framework classes (`src/main/java`)

### 5.1 `utils/PropertyHandler`

Reads `config.properties` once and hands out values.

- **Static block** – loads the file from the classpath when the class is first used. Throws a clear error if the file is missing.
- `get(key)` – returns the value; checks `System.getProperty(key)` first so `-Dkey=value` overrides the file. Throws `IllegalArgumentException` if the key is missing (fail fast instead of a `NullPointerException` later).
- `get(key, default)`, `getInt(key, default)`, `getBoolean(key, default)` – typed versions with a fallback.
- `final` class with a private constructor: it is a pure utility, never instantiated.

### 5.2 `utils/WebDriverManager`

Creates, stores and closes the browser.

- **`ThreadLocal<WebDriver>`** – every thread gets its own browser. This keeps tests independent and makes the framework ready for parallel runs (`parallel="methods"` in `testng.xml`).
- `initDriver(browser)`:
  1. Calls Boni Garcia's `io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup()` (or firefox/edge) to download the matching driver. The **full package name** is used because our class has the same simple name.
  2. Builds browser options: `--headless=new` when `headless=true`; for Chrome also `--disable-notifications`, `--remote-allow-origins=*` and **`--guest`** (guest profile stops Chrome's "password found in a data breach" popup that blocks saucedemo after login). In headless mode Chrome and Edge also get `--no-sandbox` and `--disable-dev-shm-usage` (`SERVER_ARGS`), which keep Chrome from crashing on Linux servers such as Jenkins.
  3. Sets implicit wait and page-load timeout from the config, maximises the window when not headless.
  4. Stores the driver in the `ThreadLocal`.
- `getDriver()` – returns the current thread's browser; throws a clear error if `initDriver()` was not called.
- `quitDriver()` – closes the browser and clears the `ThreadLocal` (prevents leaks between tests).

### 5.3 `utils/Reporter`

A small wrapper around Allure so tests don't depend on Allure's API directly.

- `log(message)` – prints to the console **and** adds a step to the Allure report (`Allure.step`).
- `attachText(name, content)` – attaches plain text (e.g. an error message) to the current test.
- `attachScreenshot(name)` – takes a PNG screenshot of the browser and attaches it. Errors are caught so a screenshot problem never fails a test.

> Not to be confused with `org.testng.Reporter` (TestNG's own class, used in `TS_Flaky`).

### 5.4 `pages/BasePage`

Parent of all page objects. Holds the shared driver and a `WebDriverWait`.

- Constructor – takes the current thread's driver from `WebDriverManager` and creates a wait of `explicit.wait` seconds.
- `waitForVisible`, `waitForClickable`, `waitForUrlContains` – explicit waits using `ExpectedConditions`.
- `click`, `type`, `getText` – every action waits first, so pages never need `Thread.sleep`.
- `isDisplayed` – returns `false` instead of throwing when the element does not appear.
- `getTitle`, `getCurrentUrl` – general page info.
- `abstract` – you never create a `BasePage` directly.

### 5.5 `pages/PG_Login`

The saucedemo login page.

- **Locators** (private `By` fields): username box `#user-name`, password `#password`, login button `#login-button`, error `h3[data-test='error']`, logo `.login_logo`.
- `open()` – navigates to `base.url` and waits for the username field.
- `enterUsername`, `enterPassword`, `clickLogin` – single actions.
- `loginAs(user, pass)` – full login; **returns `PG_Dashboard`** because a successful login lands there.
- `loginExpectingError(user, pass)` – login that should fail; returns `PG_Login` (we stay on the page).
- `isLoaded()`, `getErrorMessage()` – used for assertions.
- Methods return page objects, which allows **fluent chaining**: `new PG_Login().open().loginAs(u, p)`.
- Every action is annotated with **`@Step`**, so it appears as a step in the report (e.g. *"Enter username: standard_user"*).

### 5.6 `pages/PG_Dashboard`

The "Products" page shown after login.

- Locators: page title `span.title`, product cards `.inventory_item`, burger menu `#react-burger-menu-btn`, logout link `#logout_sidebar_link`.
- `isLoaded()` – waits for the URL to contain `inventory.html` and the title to be visible.
- `getPageHeader()` – returns the title text ("Products").
- `getProductCount()` – number of product cards (6 on the demo site).
- `logout()` – opens the menu, clicks Logout, returns `PG_Login`.

---

## 6. Test classes (`src/test/java`)

### 6.1 `BaseTest`

Every test class extends it.

- `@BeforeMethod setUp(@Optional browser)` – runs **before each test**: opens a fresh browser (from the `testng.xml` parameter, or `config.properties` if absent) and logs it.
- `@AfterMethod tearDown(ITestResult)` – runs **after each test**: if the test failed, attaches a screenshot to Allure, then closes the browser.
- `alwaysRun = true` – setup/teardown also run when tests are filtered by groups.
- A fresh browser per test means no test depends on another's leftovers.

### 6.2 `TS_Common` – login tests

| Test | Checks |
|---|---|
| `TC_001_validLogin` | Valid user reaches the dashboard and header is "Products"; attaches a screenshot. |
| `TC_002_lockedOutUserLogin` | Locked-out user gets an error containing "locked out"; attaches the message. |
| `TC_003_invalidPasswordLogin` | Wrong password gives "do not match" error. |

### 6.3 `TS_Dashboard` – dashboard tests

| Test | Checks |
|---|---|
| `TC_101_productsAreListed` | Dashboard shows 6 products. |
| `TC_102_logout` | Logout returns to the login page. |

### 6.4 `TS_ReportDemo` – intentional non-passing tests (demo only)

Shows how Allure displays each status.

| Test | Allure status | How |
|---|---|---|
| `TC_201_wrongHeaderText` | **Failed** | Asserts header is "Dashboard" (it's "Products") → `AssertionError`. |
| `TC_202_wrongProductCount` | **Failed** | Asserts 10 products (there are 6). |
| `TC_203_missingElement` | **Broken** | Clicks a non-existent element → `NoSuchElementException`. |
| `TC_204_missingTestData` | **Broken** | Reads an undefined config key → `IllegalArgumentException`. |
| `TC_205_featureNotReady` | **Skipped** | Throws `SkipException`. |
| `TC_206_dependsOnFailedTest` | **Skipped** | `dependsOnMethods = TC_201`, which fails, so TestNG skips it. |

**Failed vs Broken:** an `AssertionError` (the app behaved wrongly) is *failed*; any other exception (the test itself could not complete) is *broken*.

### 6.5 `TS_Flaky` – intentional flaky tests (demo only)

Simulates intermittent problems to demonstrate retries.

- A static `Map<method name, counter>` counts how many times each test has started in this run; `currentAttempt()` returns 0 for the first attempt, 1 for the first retry, etc.

| Test | Behaviour | Expected (retry.count = 2) |
|---|---|---|
| `TC_301_passesOnFirstRetry` | Assertion fails on attempt 0, passes on attempt 1. | Passed after 1 retry. |
| `TC_302_passesOnSecondRetry` | `TimeoutException` on attempts 0 and 1. | Passed after 2 retries (broken if retry.count < 2). |
| `TC_303_randomFailure` | Fails 50 % of the time at random. | Usually passes within 3 attempts; varies between runs. |

All three carry Allure's `@Flaky` annotation.

---

## 7. Listeners (`src/test/java/.../listeners`)

A **TestNG listener** is a class TestNG calls at certain moments (run start, test start, test failure…). Ours are registered through `META-INF/services` (section 4.4).

### 7.1 `AllureHistoryListener` (implements `IExecutionListener`)

`onExecutionStart()` runs **once, before any test**:

1. **Deletes `allure-results/`** so the report shows only this run (otherwise results pile up and old runs get mixed in).
2. **Copies `allure-report/history/` → `allure-results/history/`.** This is how Allure history works: the generator reads the previous history from the results folder and adds the new run to it.
3. **Increments the run counter** in `.allure/build-number`.
4. **Writes `executor.json`** – run name "Run #N (date time)", build order N, report name. Allure uses it to label runs on the Trend chart and in the Executors widget.
5. **Writes `environment.properties`** – browser, headless, base URL, OS, Java version. Shown in the report's *Environment* widget.

Folders come from the system properties `allure.results.directory` / `allure.report.directory`, defaulting to `allure-results` / `allure-report`.

### 7.2 `RetryAnalyzer` (implements `IRetryAnalyzer`)

TestNG calls `retry(result)` every time a test fails.

- Returns `true` (run it again) while `attempt < retry.count`; then `false` (accept the failure).
- Prints `[RETRY] <test> failed (<exception>). Retry n of N` to the console.
- TestNG creates one analyzer instance per test method, so each test has its own counter.
- Skipped tests (`SkipException`) are not retried – only failures.

### 7.3 `RetryTransformer` (implements `IAnnotationTransformer`)

TestNG calls `transform(...)` for every `@Test` **before the run starts**. It sets `retryAnalyzer = RetryAnalyzer.class` on every test that doesn't define its own. Result: the whole project gets retries without adding `retryAnalyzer = …` to each `@Test`.

---

## 8. `run-tests.sh`

```bash
./run-tests.sh [maven options]     # e.g. ./run-tests.sh -Dheadless=true
```

1. `mvn clean test "$@"` – runs the suite (extra options are passed through).
2. `allure generate --clean allure-results -o allure-report` – builds the HTML report **with history**, even if tests failed.
3. `allure open allure-report` – starts a local web server and opens the report.
4. Exits with Maven's exit code, so a CI job still sees a failure.

---

## 9. Common commands

| Goal | Command (run in `reporter/`) |
|---|---|
| Run the real tests | `mvn clean test` |
| Run the demo tests | `mvn clean test -Dsuite=testng-demo.xml` |
| Don't fail the build on test failures | `mvn clean test -Dmaven.test.failure.ignore=true` |
| Headless / other browser | `mvn clean test -Dheadless=true -Dbrowser=firefox` |
| Disable retries | `mvn clean test -Dretry.count=0` |
| One class only | `mvn clean test -Dtest=TS_Common` |
| Run + report + open | `./run-tests.sh` |

## 10. Extending the framework

- **New page:** create `PG_Xxx extends BasePage`, add private `By` locators and `@Step` action methods that return the next page object.
- **New tests:** create `TS_Xxx extends BaseTest` in its own package, add `@Test` methods with Allure tags, and add the class to `testng.xml` (or `testng-demo.xml` for demo tests).
- **New setting:** add it to `config.properties` and read it with `PropertyHandler.get("key")`.
