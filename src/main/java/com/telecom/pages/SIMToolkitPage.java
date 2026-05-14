package com.telecom.pages;

import com.telecom.config.SIMToolkitConfig;
import com.telecom.utils.DeviceUtils;
import com.telecom.utils.ScreenshotUtils;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import com.telecom.utils.ProgressReporter;

public class SIMToolkitPage {
    
    protected AppiumDriver driver;
    protected WebDriverWait wait;
    private String deviceId; 
    protected ScreenshotUtils screenshotUtils;
    private DeviceUtils deviceUtils;
    
    @AndroidFindBy(xpath = "//*[contains(@text, 'SIM') or contains(@text, 'Menu') or contains(@text, 'Vi')]")
    private List<WebElement> simMenuOptions;
    
    @AndroidFindBy(xpath = "//*[contains(@text, 'Vi') or contains(@text, 'Vodafone')]")
    private List<WebElement> viMenuElements;
    
    @AndroidFindBy(xpath = "//*[contains(@text, 'Vi Menu') or contains(@text, 'Vodafone Menu')]")
    private WebElement viMenuHeader;
    
    @AndroidFindBy(xpath = "//*[@text='FLASH!' or contains(@text,'FLASH')]")
    private WebElement flashOption;

    
    @AndroidFindBy(xpath = "//*[@text='Roaming' or contains(@text, 'Roaming')]")
    private WebElement roamingOption;
    
    @AndroidFindBy(xpath = "//*[@text='Vodafone IN' or contains(@text, 'Vodafone IN')]")
    private WebElement vodafoneInOption;
    
    @AndroidFindBy(xpath = "//*[@text='International' or contains(@text, 'International')]")
    private WebElement internationalOption;
    
    @AndroidFindBy(id = "android:id/button1")
    private WebElement okButton;
    
    @AndroidFindBy(id = "android:id/button2")
    private WebElement cancelButton;
    
    //  CRITICAL: Accept ScreenshotUtils from test class
    public SIMToolkitPage(AppiumDriver driver, ScreenshotUtils screenshotUtils, String deviceId) {
        this.driver = driver;
        this.deviceId = deviceId; 
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.screenshotUtils = screenshotUtils;
        this.deviceUtils = new DeviceUtils(driver);
        PageFactory.initElements(new AppiumFieldDecorator(driver, Duration.ofSeconds(10)), this);
    }
    
    public SIMToolkitConfig.SIMType detectAndHandleSIMScenario() {
        System.out.println("┌─ Step 2: Detect & Handle SIM Scenario");
        reportProgress("STARTED", "Starting SIM Toolkit detection", 10); // Changed from 0 to 10
        
        SIMToolkitConfig.SIMType simType = deviceUtils.detectSIMType();
        System.out.println("  Detected: " + simType.getDescription());
        reportProgress("SIM_DETECTED", "SIM Type: " + simType.getDescription(), 20);
        
        switch (simType) {
            case SINGLE_SIM:
            	 reportProgress("HANDLING_SINGLE_SIM", "Handling single SIM scenario", 30);
                handleSingleSIM();
                break;
            case DUAL_SIM_MIXED:
            	reportProgress("HANDLING_DUAL_SIM_MIXED", "Handling dual SIM mixed scenario", 30);
                handleDualSIMMixed();
                break;
            case DUAL_SIM_VI:
            	reportProgress("HANDLING_DUAL_SIM_VI", "Handling dual SIM Vi scenario", 30);
                handleDualSIMVi();
                break;
        }
        
        System.out.println("└─  SIM scenario handled\n");
        reportProgress("COMPLETED", "SIM scenario handled successfully", 40); 
        return simType;
    }
    
    private void handleSingleSIM() {
        System.out.println("  → Scenario A: Single SIM Device");
        reportProgress("SINGLE_SIM", "Processing single SIM device", 50);
        captureScreenshot("Vi Menu Home");
        reportProgress("SCREENSHOT_CAPTURED", "Screenshot captured for single SIM", 60);
    }

    private void handleDualSIMMixed() {
        System.out.println("  → Scenario B: Dual SIM (Vi + Other)");
        reportProgress("DUAL_SIM_MIXED", "Processing dual SIM mixed", 50);
        
        captureScreenshot("SIM Selection Screen");
        reportProgress("SCREENSHOT_1", "SIM selection screen captured", 60);
        
        selectViMenu();
        reportProgress("VI_MENU_SELECTED", "Vi menu selected", 70);
        
        captureScreenshot("Vi Menu Home");
        reportProgress("SCREENSHOT_2", "Vi Menu home captured", 80);
    }
    private void handleDualSIMVi() {
        System.out.println("  → Scenario C: Dual SIM (Both Vi)");
        reportProgress("DUAL_SIM_VI", "Processing dual SIM Vi", 50);
        
        captureScreenshot("SIM Selection Screen");
        reportProgress("SCREENSHOT_1", "SIM selection screen captured", 60);
        
        if (!simMenuOptions.isEmpty()) {
            clickWithoutScreenshot(simMenuOptions.get(0));
            reportProgress("MENU_CLICKED", "Clicked on SIM menu option", 70);
        }
        
        captureScreenshot("Vi Menu Home");
        reportProgress("SCREENSHOT_2", "Vi Menu home captured", 80);
    }
    
    private void selectViMenu() {
        for (WebElement menuOption : simMenuOptions) {
            String text = getText(menuOption);
            if (text != null && (text.contains("Vi") || text.contains("Vodafone"))) {
                clickWithoutScreenshot(menuOption);
                break;
            }
        }
    }
    
    public void navigateToFlashOption() {
        System.out.println("┌─ Step 3: Flash Option");
        reportProgress("FLASH_OPTION", "Navigating to Flash option", 45);
        
        try {
            if (isDisplayed(flashOption)) {
            	  reportProgress("FLASH_FOUND", "Flash option found", 50);
                click(flashOption, "Flash Option");
                deviceUtils.navigateBack();
                System.out.println("└─  Flash option captured\n");
                reportProgress("FLASH_COMPLETED", "Flash option tested successfully", 55);
            } else {
                captureScreenshot("Flash Option Not Found");
                System.out.println("└─ ⚠ Flash option not found\n");
                reportProgress("FLASH_NOT_FOUND", "Flash option not found", 55);
            }
        } catch (Exception e) {
            System.err.println("└─ ❌ Error: " + e.getMessage());
            reportProgress("FLASH_ERROR", "Error: " + e.getMessage(), 0);
        }
    }
    
    public void navigateToRoamingOption() {
        System.out.println("┌─ Step 4: Roaming Option");
        reportProgress("ROAMING_OPTION", "Navigating to Roaming option", 60);
        
        try {
            if (isDisplayed(roamingOption)) {
            	 reportProgress("ROAMING_FOUND", "Roaming option found", 65);
                click(roamingOption, "Roaming Menu");
                System.out.println("└─  Roaming menu captured\n");
                reportProgress("ROAMING_ENTERED", "Entered Roaming menu", 70);
            } else {
                System.out.println("└─ ⚠ Roaming option not found\n");
                reportProgress("ROAMING_NOT_FOUND", "Roaming option not found", 70);
            }
        } catch (Exception e) {
            System.err.println("└─ ❌ Error: " + e.getMessage());
            reportProgress("ROAMING_ERROR", "Error: " + e.getMessage(), 0);
        }
    }
    
    public void validateRoamingSubMenus() {
        System.out.println("┌─ Step 5-6: Roaming Sub-Menus");
        reportProgress("ROAMING_SUBMENUS", "Validating roaming sub-menus", 75);
        
        validateVodafoneIN();
        
        // Re-open Roaming menu before validating International option
        reOpenRoamingMenu();
        
        validateInternational();
        
        System.out.println("└─  Sub-menus validated\n");
        reportProgress("SUB_MENUS_COMPLETED", "Roaming sub-menus validated", 85); 
    }

    private void reOpenRoamingMenu() {
        System.out.println("  → Re-opening Roaming Menu");
        reportProgress("REOPEN_ROAMING", "Re-opening Roaming menu for International option", 72);
        
        try {
            if (isDisplayed(roamingOption)) {
                reportProgress("ROAMING_FOUND", "Roaming option found", 45);
                click(roamingOption, "Roaming Menu");
                System.out.println("└─  Roaming menu captured\n");
                reportProgress("ROAMING_ENTERED", "Entered Roaming menu", 50);
            } else {
                System.out.println("└─ ⚠ Roaming option not found\n");
                reportProgress("ROAMING_NOT_FOUND", "Roaming option not found", 50);
            }
        } catch (Exception e) {
            System.err.println("└─ ❌ Error: " + e.getMessage());
            reportProgress("ROAMING_ERROR", "Error: " + e.getMessage(), 0);
        }
    }

    private void validateVodafoneIN() {
        System.out.println("  → Vodafone IN Option");
        reportProgress("VODAFONE_IN", "Validating Vodafone IN option", 76); 
        
        try {
            if (isDisplayed(vodafoneInOption)) {
                reportProgress("VODAFONE_IN_FOUND", "Vodafone IN option found", 78);
                click(vodafoneInOption, "Vodafone IN");
                handlePopup("vodafone_in", true);
                reportProgress("VODAFONE_IN_TESTED", "Vodafone IN option tested", 80);
            } else {
                System.out.println("    ⚠ Vodafone IN not found");
                reportProgress("VODAFONE_IN_NOT_FOUND", "Vodafone IN option not found", 80);
            }
        } catch (Exception e) {
            System.err.println("    ✗ Error: " + e.getMessage());
            reportProgress("VODAFONE_IN_ERROR", "Error: " + e.getMessage(), 0);
        }
    }

    private void validateInternational() {
        System.out.println("  → International Option");
        reportProgress("INTERNATIONAL", "Validating International option", 82);
        
        try {
            // Ensure we're in the Roaming menu before looking for International option
            if (isDisplayed(internationalOption)) {
                reportProgress("INTERNATIONAL_FOUND", "International option found", 84);
                click(internationalOption, "International");
                handlePopup("international", true);
                reportProgress("INTERNATIONAL_TESTED", "International option tested", 86);
            } else {
                System.out.println("    ⚠ International not found");
                reportProgress("INTERNATIONAL_NOT_FOUND", "International option not found", 86);
            }
        } catch (Exception e) {
            System.err.println("    ✗ Error: " + e.getMessage());
            reportProgress("INTERNATIONAL_ERROR", "Error: " + e.getMessage(), 0);
        }
    }
    
    private void handlePopup(String popupName, boolean clickOK) {
        try {
            Thread.sleep(2000);
            
            if (clickOK && isDisplayed(okButton)) {
                clickWithoutScreenshot(okButton);
                Thread.sleep(1500);
                System.out.println("    ✓ Clicked OK");
            } else if (!clickOK && isDisplayed(cancelButton)) {
                clickWithoutScreenshot(cancelButton);
                System.out.println("    ✓ Clicked Cancel");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("    ✗ Popup handling error: " + e.getMessage());
        }
    }
    
    public boolean verifyViBranding() {
        try {
            reportProgress("BRANDING_CHECK", "Verifying Vi branding", 90);
            
            for (WebElement viElement : viMenuElements) {
                if (isDisplayed(viElement)) {
                    String text = getText(viElement);
                    if (text != null) {
                        for (String branding : SIMToolkitConfig.VI_BRANDING_TEXTS) {
                            if (text.contains(branding)) {
                                System.out.println(" Vi branding verified: " + text);
                                reportProgress("BRANDING_VERIFIED", "Vi branding verified: " + text, 95);
                                return true;
                            }
                        }
                    }
                }
            }
            
            reportProgress("BRANDING_NOT_FOUND", "Vi branding not found", 95);
            return false;
            
        } catch (Exception e) {
            reportProgress("BRANDING_ERROR", "Error verifying branding: " + e.getMessage(), 0);
            return false;
        }
    }
    
    public void completeSIMToolkitTest() {
        System.out.println("┌─ SIM Toolkit Test Complete");
        reportProgress("TEST_COMPLETE", "SIM Toolkit test completed successfully", 100);
        System.out.println("└─  All SIM Toolkit steps completed\n");
    }
    
    // Helper methods
    protected void click(WebElement element, String screenshotName) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element));
            element.click();
            System.out.println("  ✓ Clicked element");
            
            Thread.sleep(1500);
            
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
    
    public void captureScreenshot(String stepName) {
        if (stepName != null && !stepName.trim().isEmpty()) {
            screenshotUtils.captureScreenshot(stepName);
        }
    }
    /**
     * Helper method to report SIM Toolkit progress
     */
    private void reportProgress(String action, String status, double percentage) {
        if (deviceId != null) {
            try {
                // 🔥 FIXED: Use reportCallingProgress instead of reportSMSProgress
                // Since SIM Toolkit is part of calling/network testing
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    "SIM_Toolkit",  // phoneNumber parameter - using identifier
                    action,         // action
                    status,         // status
                    0,              // duration (not applicable for SIM Toolkit)
                    percentage      // percentage
                );
            } catch (Exception e) {
                // Silently fail - don't disrupt test execution
                System.err.println("SIM Toolkit progress report failed: " + e.getMessage());
            }
        }
    }
    
}