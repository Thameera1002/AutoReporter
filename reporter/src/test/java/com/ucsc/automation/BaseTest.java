package com.ucsc.automation;

import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import com.ucsc.automation.utils.PropertyHandler;
import com.ucsc.automation.utils.Reporter;
import com.ucsc.automation.utils.WebDriverManager;

/**
 * Opens a fresh browser before each test and closes it afterwards.
 * A screenshot is attached to the Allure report when a test fails.
 */
public abstract class BaseTest {

    @Parameters("browser")
    @BeforeMethod(alwaysRun = true)
    public void setUp(@Optional String browser) {
        String selected = browser != null ? browser : PropertyHandler.get("browser", "chrome");
        WebDriverManager.initDriver(selected);
        Reporter.log("Browser started: " + selected);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        if (result.getStatus() == ITestResult.FAILURE) {
            Reporter.attachScreenshot("Failure - " + result.getName());
        }
        WebDriverManager.quitDriver();
    }
}
