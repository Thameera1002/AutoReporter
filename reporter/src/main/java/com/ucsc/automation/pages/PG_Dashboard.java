package com.ucsc.automation.pages;

import org.openqa.selenium.By;

import io.qameta.allure.Step;

/**
 * Landing page after a successful login (the "Products" inventory page on saucedemo.com).
 */
public class PG_Dashboard extends BasePage {

    private final By lblPageTitle = By.cssSelector("span.title");
    private final By lstInventoryItems = By.cssSelector(".inventory_item");
    private final By btnBurgerMenu = By.id("react-burger-menu-btn");
    private final By lnkLogout = By.id("logout_sidebar_link");

    public boolean isLoaded() {
        try {
            waitForUrlContains("inventory.html");
            return isDisplayed(lblPageTitle);
        } catch (Exception e) {
            return false;
        }
    }

    public String getPageHeader() {
        return getText(lblPageTitle);
    }

    public int getProductCount() {
        waitForVisible(lstInventoryItems);
        return driver.findElements(lstInventoryItems).size();
    }

    @Step("Log out")
    public PG_Login logout() {
        click(btnBurgerMenu);
        click(lnkLogout);
        return new PG_Login();
    }
}
