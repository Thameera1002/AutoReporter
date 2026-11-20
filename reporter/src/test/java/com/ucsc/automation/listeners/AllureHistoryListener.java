package com.ucsc.automation.listeners;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.testng.IExecutionListener;

import com.ucsc.automation.utils.PropertyHandler;

/**
 * Keeps Allure execution history between runs.
 *
 * Before the tests start it:
 *  1. clears old raw results (so each report shows only the latest run),
 *  2. copies allure-report/history into allure-results/history (this powers the
 *     Trend, Retries and History tabs),
 *  3. writes executor.json (run number / name) and environment.properties.
 *
 * Registered via META-INF/services, so it runs for Maven and IDE runs alike.
 */
public class AllureHistoryListener implements IExecutionListener {

    private static final Path RESULTS_DIR =
            Paths.get(System.getProperty("allure.results.directory", "allure-results"));
    private static final Path REPORT_DIR =
            Paths.get(System.getProperty("allure.report.directory", "allure-report"));
    private static final Path BUILD_NUMBER_FILE = Paths.get(".allure", "build-number");

    @Override
    public void onExecutionStart() {
        try {
            deleteDirectory(RESULTS_DIR);
            Files.createDirectories(RESULTS_DIR);
            copyHistory();
            int buildNumber = nextBuildNumber();
            writeExecutor(buildNumber);
            writeEnvironment();
            System.out.println("[ALLURE] Run #" + buildNumber + " - results: " + RESULTS_DIR.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("[ALLURE] Could not prepare history: " + e.getMessage());
        }
    }

    private void copyHistory() throws IOException {
        Path source = REPORT_DIR.resolve("history");
        if (!Files.isDirectory(source)) {
            System.out.println("[ALLURE] No previous history found (first run).");
            return;
        }
        Path target = RESULTS_DIR.resolve("history");
        Files.createDirectories(target);
        try (Stream<Path> files = Files.list(source)) {
            files.forEach(file -> {
                try {
                    Files.copy(file, target.resolve(file.getFileName()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private int nextBuildNumber() throws IOException {
        int number = 0;
        if (Files.exists(BUILD_NUMBER_FILE)) {
            try {
                number = Integer.parseInt(Files.readString(BUILD_NUMBER_FILE).trim());
            } catch (NumberFormatException ignored) {
                number = 0;
            }
        }
        number++;
        Files.createDirectories(BUILD_NUMBER_FILE.getParent());
        Files.writeString(BUILD_NUMBER_FILE, String.valueOf(number));
        return number;
    }

    private void writeExecutor(int buildNumber) throws IOException {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String json = """
                {
                  "name": "Local Machine",
                  "type": "local",
                  "buildOrder": %d,
                  "buildName": "Run #%d (%s)",
                  "reportName": "AutoReporter Test Report"
                }
                """.formatted(buildNumber, buildNumber, time);
        Files.writeString(RESULTS_DIR.resolve("executor.json"), json, StandardCharsets.UTF_8);
    }

    private void writeEnvironment() throws IOException {
        Properties env = new Properties();
        env.setProperty("Browser", PropertyHandler.get("browser", "chrome"));
        env.setProperty("Headless", PropertyHandler.get("headless", "false"));
        env.setProperty("Base.URL", PropertyHandler.get("base.url", "-"));
        env.setProperty("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        env.setProperty("Java", System.getProperty("java.version"));
        try (OutputStream out = Files.newOutputStream(RESULTS_DIR.resolve("environment.properties"))) {
            env.store(out, "Allure environment");
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
