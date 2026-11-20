package com.ucsc.automation.TS_Flaky;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.openqa.selenium.TimeoutException;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import com.ucsc.automation.BaseTest;
import com.ucsc.automation.pages.PG_Dashboard;
import com.ucsc.automation.pages.PG_Login;
import com.ucsc.automation.utils.PropertyHandler;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Flaky;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;

/**
 * INTENTIONALLY flaky tests, to demonstrate RetryAnalyzer and Allure's Retries / Flaky marks.
 * Each test simulates an intermittent problem on its first attempt(s), then behaves normally.
 * Remove this class from testng.xml when you no longer need the demo.
 */
@Epic("Demo Site")
@Feature("Flaky Test Demo")
public class TS_Flaky extends BaseTest {

    private PG_Dashboard login() {
        return new PG_Login()
                .open()
                .loginAs(PropertyHandler.get("username"), PropertyHandler.get("password"));
    }

    /** How many times each test method has started in this run. */
    private static final Map<String, AtomicInteger> RUNS = new ConcurrentHashMap<>();

    /** 0 on the first attempt, 1 on the first retry, and so on. */
    private int currentAttempt() {
        String method = Reporter.getCurrentTestResult().getMethod().getMethodName();
        return RUNS.computeIfAbsent(method, k -> new AtomicInteger()).getAndIncrement();
    }

    @Flaky
    @Test(description = "[FLAKY] Fails once, passes on retry 1")
    @Severity(SeverityLevel.NORMAL)
    @Description("Simulates an assertion that fails on the first attempt only. Expected: PASSED after 1 retry.")
    public void TC_301_passesOnFirstRetry() {
        PG_Dashboard dashboard = login();
        String expected = currentAttempt() == 0 ? "Loading..." : "Products";
        Assert.assertEquals(dashboard.getPageHeader(), expected, "Header not ready yet");
    }

    @Flaky
    @Test(description = "[FLAKY] Times out twice, passes on retry 2")
    @Severity(SeverityLevel.NORMAL)
    @Description("Simulates a slow page (timeout) on the first two attempts. Expected: PASSED after 2 retries "
            + "(needs retry.count >= 2).")
    public void TC_302_passesOnSecondRetry() {
        PG_Dashboard dashboard = login();
        if (currentAttempt() < 2) {
            throw new TimeoutException("Simulated slow network: product list did not load in time");
        }
        Assert.assertEquals(dashboard.getProductCount(), 6);
    }

    @Flaky
    @Test(description = "[FLAKY] Random 50% failure")
    @Severity(SeverityLevel.MINOR)
    @Description("Fails at random half of the time. Result changes between runs: watch its History tab.")
    public void TC_303_randomFailure() {
        PG_Dashboard dashboard = login();
        boolean glitch = ThreadLocalRandom.current().nextBoolean();
        Assert.assertFalse(glitch, "Random glitch happened");
        Assert.assertTrue(dashboard.isLoaded());
    }
}
