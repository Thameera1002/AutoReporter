package com.ucsc.automation.TS_ReportDemo;

import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import com.ucsc.automation.BaseTest;
import com.ucsc.automation.pages.PG_Dashboard;
import com.ucsc.automation.pages.PG_Login;
import com.ucsc.automation.utils.PropertyHandler;
import com.ucsc.automation.utils.Reporter;
import com.ucsc.automation.utils.WebDriverManager;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;

/**
 * INTENTIONALLY non-passing tests, used to demonstrate how Allure shows each status:
 *   FAILED  - an assertion did not hold (AssertionError)
 *   BROKEN  - the test crashed with any other exception (e.g. element not found)
 *   SKIPPED - SkipException, or a dependency that did not pass
 * Remove this class from testng.xml when you no longer need the demo.
 */
@Epic("Demo Site")
@Feature("Report Status Demo")
public class TS_ReportDemo extends BaseTest {

    private PG_Dashboard login() {
        return new PG_Login()
                .open()
                .loginAs(PropertyHandler.get("username"), PropertyHandler.get("password"));
    }

    // ---------------- FAILED ----------------

    @Test(description = "[FAIL] Dashboard header is 'Dashboard'")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Expected to FAIL: the real header text is 'Products'.")
    public void TC_201_wrongHeaderText() {
        PG_Dashboard dashboard = login();
        Assert.assertEquals(dashboard.getPageHeader(), "Dashboard", "Dashboard header text mismatch");
    }

    @Test(description = "[FAIL] Dashboard lists 10 products")
    @Severity(SeverityLevel.NORMAL)
    @Description("Expected to FAIL: the demo site lists 6 products.")
    public void TC_202_wrongProductCount() {
        PG_Dashboard dashboard = login();
        Assert.assertEquals(dashboard.getProductCount(), 10, "Unexpected product count");
    }

    // ---------------- BROKEN ----------------

    @Test(description = "[BROKEN] Click a button that does not exist")
    @Severity(SeverityLevel.NORMAL)
    @Description("Expected to be BROKEN: NoSuchElementException is not an assertion failure.")
    public void TC_203_missingElement() {
        login();
        Reporter.log("Looking for a 'checkout-now' button that is not on the page");
        WebDriverManager.getDriver().findElement(By.id("checkout-now")).click();
    }

    @Test(description = "[BROKEN] Read a config key that is not defined")
    @Severity(SeverityLevel.MINOR)
    @Description("Expected to be BROKEN: missing test data throws IllegalArgumentException.")
    public void TC_204_missingTestData() {
        String coupon = PropertyHandler.get("coupon.code");
        Reporter.log("Coupon: " + coupon);
    }

    // ---------------- SKIPPED ----------------

    @Test(description = "[SKIP] Checkout flow (feature not ready)")
    @Severity(SeverityLevel.NORMAL)
    @Description("Expected to be SKIPPED via SkipException.")
    public void TC_205_featureNotReady() {
        throw new SkipException("Checkout feature is not implemented yet - skipping");
    }

    @Test(description = "[SKIP] Runs only if TC_201 passes",
            dependsOnMethods = "TC_201_wrongHeaderText")
    @Severity(SeverityLevel.MINOR)
    @Description("Expected to be SKIPPED: depends on TC_201, which fails.")
    public void TC_206_dependsOnFailedTest() {
        Assert.assertTrue(login().isLoaded());
    }
}
