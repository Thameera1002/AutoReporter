package com.ucsc.automation.utils;

import java.time.Duration;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

/**
 * Creates and holds one WebDriver per thread (safe for parallel TestNG runs).
 * Driver binaries are resolved with Boni Garcia's WebDriverManager (referenced by
 * its fully-qualified name because this class shares the simple name).
 */
public final class WebDriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private WebDriverManager() {
    }

    public static WebDriver getDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("WebDriver not initialised. Call initDriver() first.");
        }
        return driver;
    }

    public static WebDriver initDriver() {
        return initDriver(PropertyHandler.get("browser", "chrome"));
    }

    public static WebDriver initDriver(String browser) {
        boolean headless = PropertyHandler.getBoolean("headless", false);
        WebDriver driver;

        switch (browser.toLowerCase()) {
            case "firefox" -> {
                io.github.bonigarcia.wdm.WebDriverManager.firefoxdriver().setup();
                FirefoxOptions options = new FirefoxOptions();
                if (headless) {
                    options.addArguments("-headless");
                }
                driver = new FirefoxDriver(options);
            }
            case "edge" -> {
                io.github.bonigarcia.wdm.WebDriverManager.edgedriver().setup();
                EdgeOptions options = new EdgeOptions();
                if (headless) {
                    options.addArguments("--headless=new");
                }
                driver = new EdgeDriver(options);
            }
            case "chrome" -> {
                io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--remote-allow-origins=*", "--disable-notifications");
                // Stop Chrome's password-leak popup from blocking the demo site after login
                options.addArguments("--guest");
                if (headless) {
                    options.addArguments("--headless=new", "--window-size=1920,1080");
                }
                driver = new ChromeDriver(options);
            }
            default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
        }

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(PropertyHandler.getInt("implicit.wait", 0)));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PropertyHandler.getInt("page.load.timeout", 30)));
        if (!headless) {
            driver.manage().window().maximize();
        }

        DRIVER.set(driver);
        return driver;
    }

    public static void quitDriver() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                DRIVER.remove();
            }
        }
    }
}
