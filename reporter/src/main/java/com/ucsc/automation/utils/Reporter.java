package com.ucsc.automation.utils;

import java.io.ByteArrayInputStream;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import io.qameta.allure.Allure;

/**
 * Thin wrapper around Allure for logging steps and attaching evidence.
 */
public final class Reporter {

    private Reporter() {
    }

    /** Adds a passed step to the Allure report and prints it to the console. */
    public static void log(String message) {
        System.out.println("[INFO] " + message);
        Allure.step(message);
    }

    /** Attaches plain text to the current test in the Allure report. */
    public static void attachText(String name, String content) {
        Allure.addAttachment(name, "text/plain", content);
    }

    /** Captures a screenshot of the current browser and attaches it to the Allure report. */
    public static void attachScreenshot(String name) {
        try {
            WebDriver driver = WebDriverManager.getDriver();
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(png), "png");
        } catch (Exception e) {
            System.out.println("[WARN] Could not capture screenshot: " + e.getMessage());
        }
    }
}
