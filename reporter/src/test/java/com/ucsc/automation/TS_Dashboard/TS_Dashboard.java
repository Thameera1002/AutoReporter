package com.ucsc.automation.TS_Dashboard;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ucsc.automation.BaseTest;
import com.ucsc.automation.pages.PG_Dashboard;
import com.ucsc.automation.pages.PG_Login;
import com.ucsc.automation.utils.PropertyHandler;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;

@Epic("Demo Site")
@Feature("Dashboard")
public class TS_Dashboard extends BaseTest {

    private PG_Dashboard login() {
        return new PG_Login()
                .open()
                .loginAs(PropertyHandler.get("username"), PropertyHandler.get("password"));
    }

    @Test(description = "Dashboard lists products")
    @Severity(SeverityLevel.NORMAL)
    public void TC_101_productsAreListed() {
        PG_Dashboard dashboard = login();
        Assert.assertTrue(dashboard.isLoaded(), "Dashboard did not load");
        Assert.assertEquals(dashboard.getProductCount(), 6, "Unexpected product count");
    }

    @Test(description = "User can log out from the dashboard")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_102_logout() {
        PG_Login loginPage = login().logout();
        Assert.assertTrue(loginPage.isLoaded(), "Login page not shown after logout");
    }
}
