package com.ucsc.automation.TS_Common;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ucsc.automation.BaseTest;
import com.ucsc.automation.pages.PG_Dashboard;
import com.ucsc.automation.pages.PG_Login;
import com.ucsc.automation.utils.PropertyHandler;
import com.ucsc.automation.utils.Reporter;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;

@Epic("Demo Site")
@Feature("Login")
public class TS_Common extends BaseTest {

    @Test(description = "Valid user can log in")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Log in with valid credentials and verify the dashboard (Products page) loads.")
    public void TC_001_validLogin() {
        PG_Dashboard dashboard = new PG_Login()
                .open()
                .loginAs(PropertyHandler.get("username"), PropertyHandler.get("password"));

        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load after login");
        Assert.assertEquals(dashboard.getPageHeader(), "Products");
        Reporter.attachScreenshot("Dashboard after login");
    }

    @Test(description = "Locked-out user sees an error")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Log in with a locked-out user and verify the error message.")
    public void TC_002_lockedOutUserLogin() {
        PG_Login login = new PG_Login()
                .open()
                .loginExpectingError(PropertyHandler.get("invalid.username"), PropertyHandler.get("password"));

        String error = login.getErrorMessage();
        Reporter.attachText("Error message", error);
        Assert.assertTrue(error.contains("locked out"), "Unexpected error message: " + error);
    }

    @Test(description = "Wrong password is rejected")
    @Severity(SeverityLevel.NORMAL)
    public void TC_003_invalidPasswordLogin() {
        PG_Login login = new PG_Login()
                .open()
                .loginExpectingError(PropertyHandler.get("username"), "wrong_password");

        Assert.assertTrue(login.getErrorMessage().contains("do not match"),
                "Expected credentials mismatch error");
    }
}
