package com.ucsc.automation.pages;

import org.openqa.selenium.By;

import com.ucsc.automation.utils.PropertyHandler;

import io.qameta.allure.Step;

/**
 * Login page of the demo site https://www.saucedemo.com
 */
public class PG_Login extends BasePage {

    private final By txtUsername = By.id("user-name");
    private final By txtPassword = By.id("password");
    private final By btnLogin = By.id("login-button");
    private final By lblError = By.cssSelector("h3[data-test='error']");
    private final By imgLoginLogo = By.className("login_logo");

    @Step("Open login page")
    public PG_Login open() {
        driver.get(PropertyHandler.get("base.url"));
        waitForVisible(txtUsername);
        return this;
    }

    @Step("Enter username: {username}")
    public PG_Login enterUsername(String username) {
        type(txtUsername, username);
        return this;
    }

    @Step("Enter password")
    public PG_Login enterPassword(String password) {
        type(txtPassword, password);
        return this;
    }

    @Step("Click Login button")
    public void clickLogin() {
        click(btnLogin);
    }

    @Step("Log in as {username}")
    public PG_Dashboard loginAs(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
        return new PG_Dashboard();
    }

    @Step("Log in with an invalid user: {username}")
    public PG_Login loginExpectingError(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
        return this;
    }

    public boolean isLoaded() {
        return isDisplayed(imgLoginLogo) && isDisplayed(btnLogin);
    }

    public String getErrorMessage() {
        return getText(lblError);
    }
}
