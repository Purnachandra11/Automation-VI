package com.telecom.core;

import com.telecom.utils.ScreenshotUtils;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class BasePage {
    
    protected AppiumDriver driver;
    protected WebDriverWait wait;
    protected ScreenshotUtils screenshotUtils;
    
    // ✅ Accept ScreenshotUtils from test class
    public BasePage(AppiumDriver driver, ScreenshotUtils screenshotUtils) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.screenshotUtils = screenshotUtils;
        PageFactory.initElements(new AppiumFieldDecorator(driver, Duration.ofSeconds(10)), this);
    }
    
    
    
    protected void click(WebElement element, String screenshotName) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element));
            element.click();
            System.out.println("  ✓ Clicked element");
            
            Thread.sleep(1500); // Wait for UI update
            
            if (screenshotName != null && !screenshotName.isEmpty()) {
                screenshotUtils.captureScreenshot(screenshotName);
            }
            
        } catch (Exception e) {
            System.err.println("  ✗ Error clicking element: " + e.getMessage());
            throw new RuntimeException("Failed to click element", e);
        }
    }
    
    protected void clickWithoutScreenshot(WebElement element) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element));
            element.click();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.err.println("Error clicking element: " + e.getMessage());
        }
    }
    
    protected boolean isDisplayed(WebElement element) {
        try {
            return element != null && element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
    
    protected String getText(WebElement element) {
        try {
            wait.until(ExpectedConditions.visibilityOf(element));
            return element.getText();
        } catch (Exception e) {
            return null;
        }
    }
    
    protected void waitForVisibility(WebElement element, int timeoutSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.visibilityOf(element));
        } catch (Exception e) {
            System.err.println("Element not visible within timeout");
        }
    }
    
    public void captureScreenshot(String stepName) {
        // Add null check and pass to ScreenshotUtils
        if (stepName != null && !stepName.trim().isEmpty()) {
            screenshotUtils.captureScreenshot(stepName);
        }
    }
}    