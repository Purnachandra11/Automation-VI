package com.telecom.utils;

import com.telecom.config.SIMToolkitConfig;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.telecom.utils.ADBLauncher;

import java.time.Duration;
import java.util.List;

@SuppressWarnings("unused")
public class DeviceUtils {
    
    private AppiumDriver driver;
    private WebDriverWait wait;
    
    public DeviceUtils(AppiumDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }
    /**
     * Launch SIM Toolkit with multiple fallback methods
     * @throws InterruptedException 
     */
    public boolean launchSIMToolkit(String deviceId) throws InterruptedException {
        System.out.println("\n🚀 Launching SIM Toolkit...");
        
        // Method 1: Try ADB monkey command with device ID
        System.out.println("  Method 1: ADB monkey command (with device ID)");
        if (ADBLauncher.launchSIMToolkit(deviceId)) {
            Thread.sleep(5000);
            if (isSIMToolkitVisible()) {
                System.out.println("  ✅ SIM Toolkit launched successfully");
                return true;
            }
        }
        
        // Method 2: Try ADB monkey command without device ID
        System.out.println("  Method 2: ADB monkey command (without device ID)");
        if (ADBLauncher.launchSIMToolkit()) {
            Thread.sleep(5000);
            if (isSIMToolkitVisible()) {
                System.out.println("  ✅ SIM Toolkit launched successfully");
                return true;
            }
        }
        
        // Method 3: Try ADB activity launch
        System.out.println("  Method 3: ADB activity launch");
        if (ADBLauncher.launchSIMToolkitViaActivity(deviceId)) {
            Thread.sleep(5000);
            if (isSIMToolkitVisible()) {
                System.out.println("  ✅ SIM Toolkit launched successfully");
                return true;
            }
        }
        
        // Method 4: Try Appium activity launch
        System.out.println("  Method 4: Appium activity launch");
        try {
            launchApp(
                SIMToolkitConfig.SIM_TOOLKIT_PACKAGE,
                SIMToolkitConfig.SIM_TOOLKIT_ACTIVITY
            );
            Thread.sleep(5000);
            if (isSIMToolkitVisible()) {
                System.out.println("  ✅ SIM Toolkit launched successfully");
                return true;
            }
        } catch (Exception e) {
            System.err.println("  ❌ Appium launch failed: " + e.getMessage());
        }
        
        System.err.println("  ❌ All launch methods failed!");
        return false;
    }
    
    /**
     * Check if SIM Toolkit is visible on screen
     */
    private boolean isSIMToolkitVisible() {
        try {
            // Check for various SIM Toolkit indicators
            String[] indicators = {
                "SIM Toolkit", "STK", "SIM Menu", "SIM", 
                "Vi", "Vodafone", "Menu", "USSD", "Vodafone Services"
            };
            
            for (String indicator : indicators) {
                if (isElementPresent(indicator)) {
                    System.out.println("  Found indicator: " + indicator);
                    return true;
                }
            }
            
            // Also check page source
            String pageSource = driver.getPageSource().toLowerCase();
            if (pageSource.contains("sim") || 
                pageSource.contains("stk") || 
                pageSource.contains("vodafone") ||
                pageSource.contains("vi")) {
                System.out.println("  SIM Toolkit content found in page source");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    public SIMToolkitConfig.SIMType detectSIMType() {
        try {
            Thread.sleep(3000); // Wait for screen to load
            
            // Get page source for analysis
            String pageSource = driver.getPageSource();
            
            // Look for SIM selection indicators
            List<WebElement> simOptions = driver.findElements(By.xpath(
                "//*[contains(@text, 'SIM') or contains(@text, 'Menu') or contains(@text, 'Vi')]"
            ));
            
            // Count Vi-specific elements
            int viCount = 0;
            for (WebElement element : simOptions) {
                try {
                    String text = element.getText();
                    if (text != null && (text.toLowerCase().contains("vi") || 
                        text.toLowerCase().contains("vodafone"))) {
                        viCount++;
                    }
                } catch (Exception e) {
                    // Skip if can't get text
                }
            }
            
            System.out.println("  Detection Analysis:");
            System.out.println("    Total menu options found: " + simOptions.size());
            System.out.println("    Vi-branded options: " + viCount);
            
            // Decision logic
            if (simOptions.size() <= 1) {
                return SIMToolkitConfig.SIMType.SINGLE_SIM;
            } else if (viCount >= 2) {
                return SIMToolkitConfig.SIMType.DUAL_SIM_VI;
            } else if (viCount >= 1) {
                return SIMToolkitConfig.SIMType.DUAL_SIM_MIXED;
            } else {
                return SIMToolkitConfig.SIMType.SINGLE_SIM;
            }
            
        } catch (Exception e) {
            System.err.println("Error detecting SIM type: " + e.getMessage());
            return SIMToolkitConfig.SIMType.SINGLE_SIM;
        }
    }
    
    public WebElement findElementWithText(String text, boolean partial) {
        try {
            String xpath = partial ? 
                String.format("//*[contains(@text, '%s')]", text) :
                String.format("//*[@text='%s']", text);
            
            return wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<WebElement> findElementsWithText(String text, boolean partial) {
        try {
            String xpath = partial ? 
                String.format("//*[contains(@text, '%s')]", text) :
                String.format("//*[@text='%s']", text);
            
            return driver.findElements(By.xpath(xpath));
        } catch (Exception e) {
            return null;
        }
    }
    
    public void navigateBack() {
        try {
            driver.navigate().back();
            Thread.sleep(1500);
            System.out.println("  ✓ Navigated back");
        } catch (Exception e) {
            System.err.println("Error navigating back: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("deprecation")
	public void launchApp(String appPackage, String appActivity) {
        try {
            if (driver instanceof AndroidDriver) {
                AndroidDriver androidDriver = (AndroidDriver) driver;
                androidDriver.startActivity(new io.appium.java_client.android.Activity(
                    appPackage, appActivity
                ));
                Thread.sleep(3000);
                System.out.println("  ✓ App launched: " + appPackage);
            }
        } catch (Exception e) {
            System.err.println("Error launching app: " + e.getMessage());
            throw new RuntimeException("Failed to launch app", e);
        }
    }
    
    public boolean isElementPresent(String text) {
        try {
            WebElement element = findElementWithText(text, true);
            return element != null && element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * Close current application by sending BACK key events via ADB
     */
    public void closeAppUsingBackKey(String deviceId) {
        try {
            System.out.println("\n🛑 Closing app using BACK key events...");

            ProcessBuilder builder;

            if (deviceId != null && !deviceId.isEmpty()) {
                builder = new ProcessBuilder(
                    "adb", "-s", deviceId,
                    "shell", "input", "keyevent", "KEYCODE_BACK"
                );
            } else {
                builder = new ProcessBuilder(
                    "adb",
                    "shell", "input", "keyevent", "KEYCODE_BACK"
                );
            }

            // Press BACK multiple times to ensure app exits
            for (int i = 0; i < 4; i++) {
                Process process = builder.start();
                process.waitFor();
                Thread.sleep(1000);
            }

            System.out.println("  ✅ App closed using BACK key");

        } catch (Exception e) {
            System.err.println("Error closing app using BACK key: " + e.getMessage());
        }
    }

}
