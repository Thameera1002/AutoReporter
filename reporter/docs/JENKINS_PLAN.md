# AutoReporter – Jenkins CI Plan

Plan for running the AutoReporter Selenium tests on a Jenkins server and getting the Allure reports (with history) back from it.

Related guides: [FRAMEWORK_GUIDE.md](FRAMEWORK_GUIDE.md) · [ALLURE_GUIDE.md](ALLURE_GUIDE.md)

> In this document, `<JENKINS_URL>` stands for the Jenkins server address (e.g. `http://<server-ip>:8080`).
> Keep server addresses and passwords out of the repository: store passwords in **Jenkins → Credentials**, never in code or docs.

---

## 1. How it will work

```
GitHub (push / schedule) ──▶ Jenkins pipeline (Jenkinsfile in the repo)
   1. Checkout     git clone AutoReporter
   2. Test         cd reporter && mvn clean test -Dheadless=true
   3. Report       Allure Jenkins plugin → report + history trend on the build page
   4. Publish      JUnit results, report archived as a downloadable artifact, optional email
```

The **Allure Jenkins plugin** handles history on Jenkins. It copies the previous build's history into the new results, keeps the trend across builds and labels runs with Jenkins build numbers. It replaces what `AllureHistoryListener` does locally. The listener keeps working, so local runs are not affected.

---

## 2. Phase 1 – Prepare the Jenkins server (once)

| # | Task | Details |
|---|---|---|
| 1 | Check the server OS | Most likely Linux. Tests must run **headless**, because the server has no screen for a browser window. |
| 2 | Install **Java 17+** and **Maven** | **Manage Jenkins → Tools**: add a JDK named `jdk17` and a Maven installation named `maven3` (or install both on the machine). |
| 3 | Install **Google Chrome** on the server | WebDriverManager downloads the *driver*, not the *browser*. Debian/Ubuntu: `sudo apt install google-chrome-stable`. |
| 4 | Install Jenkins plugins | **Allure Jenkins Plugin**, **Pipeline**, **Git**, **JUnit**, **Timestamper** (time on each console line). Optional: **Email Extension**. |
| 5 | Configure the Allure command line | **Manage Jenkins → Tools → Allure Commandline**: add version `2.34.0`, "Install automatically", name it `allure`. |
| 6 | GitHub access | Public repo: nothing needed. Private repo: add a GitHub personal access token as a Jenkins credential. |
| 7 | Outbound internet access | The build needs Maven Central (libraries), the chromedriver downloads and `saucedemo.com`. |

---

## 3. Phase 2 – Changes in the repository ✅ Done

| Change | File |
|---|---|
| Jenkins pipeline | `Jenkinsfile` (repo root) |
| Chrome/Edge flags for Linux servers | `reporter/src/main/java/com/ucsc/automation/utils/WebDriverManager.java` |
| Real tests and demo tests in separate suites | `reporter/testng.xml`, `reporter/testng-demo.xml` |
| `-Dmaven.test.failure.ignore=true` now works | `reporter/pom.xml` (removed the fixed `testFailureIgnore=false`) |

### 3.1 `Jenkinsfile`

Jenkins reads this file from the repo on every build.

**Build parameters** (shown under **Build with Parameters**; the first build runs with the defaults):

| Parameter | Choices / default | Meaning |
|---|---|---|
| `SUITE` | `testng.xml` / `testng-demo.xml` | Real tests, or the intentional failed/broken/skipped/flaky demo tests. |
| `BROWSER` | `chrome` / `firefox` | Browser to run on (must be installed on the server). |
| `RETRY_COUNT` | `2` | Retries for failed tests; overrides `retry.count` in `config.properties`. |

**What each part does:**

| Part | Purpose |
|---|---|
| `tools { jdk 'jdk17'; maven 'maven3' }` | Uses the JDK and Maven configured in Phase 1. The names must match. |
| `triggers { githubPush(); cron('H 2 * * *') }` | `githubPush()` runs a build on every push (via the GitHub webhook). `cron` runs nightly around 02:00; `H` spreads jobs so they don't all start at once. Triggers in the Jenkinsfile **replace** the ones ticked in the job's Configure page on every build, so keep them here. |
| `options` | `timestamps()` adds a time to each console line, `buildDiscarder` keeps the last 30 builds, `timeout` stops a build stuck for more than 30 minutes. |
| `stage('Checkout')` | Gets the code from GitHub. |
| `stage('Test')` | In `reporter/`, runs `mvn -B clean test` with the chosen suite, `-Dheadless=true`, browser and retry count. |
| `-Dmaven.test.failure.ignore=true` | Failed tests don't stop the pipeline. The JUnit step then marks the build **UNSTABLE** (yellow) instead of FAILED, and the report steps still run. |
| `post { always { … } }` | Publishes the reports whether the tests passed or failed. |
| `junit …surefire-reports/*.xml` | Jenkins's own **Test Result** page and trend. |
| `allure results: [[path: 'reporter/allure-results']]` | Builds the Allure report (into the workspace's `allure-report/`) and keeps history between builds. |
| `archiveArtifacts 'allure-report/**'` | Keeps a copy of the report with each build, downloadable as a zip. Built into Jenkins, no extra plugin. |

### 3.2 Chrome settings for a Linux server

In headless mode, `WebDriverManager` now adds these flags to Chrome and Edge:

| Flag | Why |
|---|---|
| `--no-sandbox` | Chrome can't start its sandbox when it runs as the root user or inside Docker, and exits at startup. |
| `--disable-dev-shm-usage` | Servers often have a very small `/dev/shm`, which makes Chrome crash during tests. |

Local runs with a browser window are unchanged.

### 3.3 Real tests and demo tests

| Suite file | Contents | Use |
|---|---|---|
| `testng.xml` (default) | `TS_Common`, `TS_Dashboard` | Normal runs, locally and on Jenkins. Should be all green. |
| `testng-demo.xml` | `TS_ReportDemo`, `TS_Flaky` | Showing report features. Always has failures on purpose, so the build is UNSTABLE. |

Locally: `mvn clean test` runs the real tests; `mvn clean test -Dsuite=testng-demo.xml` runs the demo.

### 3.4 Checked locally

The exact Maven command from the `Jenkinsfile` was run on a Mac, headless:

| Suite | Result | Maven exit code |
|---|---|---|
| `testng.xml` | 5 tests, all passed | 0 |
| `testng-demo.xml` | Failures recorded as expected | 0 (build continues, so reports get published) |

The Jenkins steps themselves (`junit`, `allure`, `archiveArtifacts`) can only be checked on the server, in Phase 3.

---

## 4. Phase 3 – Create the Jenkins job

1. **New Item → Pipeline**, name it `AutoReporter`.
2. **Pipeline → Definition: Pipeline script from SCM**
   - SCM: Git
   - Repository URL: `https://github.com/Thameera1002/AutoReporter.git`
   - Credentials: only if the repo is private
   - Branch: `main`
   - Script path: `Jenkinsfile`
3. Choose when it runs:
   - **On every push (GitHub webhook):** in the GitHub repo, **Settings → Webhooks → Add webhook**, Payload URL `<JENKINS_URL>/github-webhook/`, content type `application/json`. The job's trigger comes from `githubPush()` in the Jenkinsfile and is active after the first build, so builds before that must be started with **Build Now**.
   - **On a schedule:** the `cron` line in the Jenkinsfile.
   - **Manually:** **Build with Parameters**.
4. Run the job twice and check that the Allure trend on the job page shows two builds.

---

## 5. Phase 4 – Getting the reports back

| Where | What you get |
|---|---|
| Build page → **Allure Report** | Full interactive report for that build (steps, screenshots, retries). |
| Job page → **Allure Report trend** chart | Pass/fail history across builds, kept by the plugin. |
| Build page → **Test Result** | TestNG results through the JUnit step, with Jenkins's own trend. |
| Build page → **Build Artifacts → (all files in zip)** | Downloadable copy of the report. To view: unzip, then `allure open allure-report`. |
| Email (optional, Email Extension plugin) | Build status, pass/fail counts and a link to the report after each build. |

---

## 6. Things to watch out for

| Issue | Cause / fix |
|---|---|
| `chrome not found` / `session not created` | Chrome not installed on the server (Phase 1, step 3). |
| Chrome crashes: `DevToolsActivePort file doesn't exist` | The tests must run headless, where `--no-sandbox --disable-dev-shm-usage` are added (section 3.2). Check `-Dheadless=true` is in the Maven command. |
| `Invalid parameter "zipFile", did you mean "label"?` | An older Jenkinsfile used the `zip` step, which needs the Pipeline Utility Steps plugin. Fixed: the Jenkinsfile now uses the built-in `archiveArtifacts`. |
| `Invalid option type "timestamps"` | Install the **Timestamper** plugin (Phase 1, step 4). |
| `No such DSL method 'allure'` | Install the **Allure Jenkins Plugin** and set up **Allure Commandline** under Tools. |
| Test Result page shows more skipped tests than Allure | Retried attempts are counted as skipped in the JUnit XML. Allure counts each test once, so use Allure's numbers. |
| `No such tool 'jdk17'` / `'maven3'` | Tool names in the Jenkinsfile don't match **Manage Jenkins → Tools**. |
| Allure report empty | Results path in the Jenkinsfile must be `reporter/allure-results`. |
| Allure trend missing | Trend appears from the second build onwards; check the Allure plugin is used, not a manual `allure generate`. |
| Build always UNSTABLE | `SUITE=testng-demo.xml` was chosen; its tests fail on purpose (section 3.3). |
| Webhook doesn't trigger builds | Jenkins must be reachable from the internet on its port; check **Recent Deliveries** in the GitHub webhook settings. |
| Slow builds | Retries on always-failing tests; lower `RETRY_COUNT` or use `SUITE=testng.xml`. |

---

