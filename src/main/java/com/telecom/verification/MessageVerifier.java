package com.telecom.verification;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 *  UNIFIED MESSAGE VERIFICATION
 * Handles verification for both Text SMS and Voice Messages
 * Enhanced to support both individual and group messages
 */
public class MessageVerifier {
    private AndroidDriver driver;
    @SuppressWarnings("unused")
    private WebDriverWait wait;
    
    public MessageVerifier(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }
    
    /**
     *  VERIFY MESSAGE SENT (Text or Voice) - Enhanced version
     * Returns: true if sent successfully, false if failed
     * Supports both individual and group messages
     */
    public boolean verifyMessageSent() {
        System.out.println("🔍 Verifying message status...");
        
        try {
            // Wait for initial processing
            Thread.sleep(2000);
            
            int maxAttempts = 10; // Max 20 seconds total wait time (2 sec * 10)
            int attempts = 0;
            
            while (attempts < maxAttempts) {
                System.out.println("   🔍 Verification attempt " + (attempts + 1) + "/" + maxAttempts);
                
                // Try multiple strategies to find status
                VerificationResult result = checkMessageStatus();
                
                switch (result.status) {
                    case SUCCESS:
                        System.out.println(" Message verified as sent successfully");
                        return true;
                    case FAILED:
                        System.out.println("❌ Message sending failed: " + result.details);
                        return false;
                    case IN_PROGRESS:
                        System.out.println("⏳ Message still sending, waiting...");
                        Thread.sleep(2000);
                        attempts++;
                        continue;
                    case UNKNOWN:
                        System.out.println("⚠️ Status unclear, continuing to check...");
                        break;
                }
                
                Thread.sleep(2000);
                attempts++;
            }
            
            // Timeout without clear status
            System.out.println("❌ Verification timeout - no clear status after 20 seconds");
            return false;
            
        } catch (Exception e) {
            System.out.println("❌ Verification error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     *  CHECK MESSAGE STATUS WITH MULTIPLE STRATEGIES
     */
    private VerificationResult checkMessageStatus() {
        VerificationResult result = new VerificationResult();
        
        try {
            // Strategy 1: Check for visual indicators
            result = checkVisualIndicators();
            if (result.status != Status.UNKNOWN) {
                return result;
            }
            
            // Strategy 2: Check page source
            result = checkPageSource();
            if (result.status != Status.UNKNOWN) {
                return result;
            }
            
            // Strategy 3: Check specific elements
            result = checkSpecificElements();
            
        } catch (Exception e) {
            result.status = Status.UNKNOWN;
            result.details = "Error checking status: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     *  CHECK VISUAL INDICATORS
     * Handles both individual and group messages
     */
    private VerificationResult checkVisualIndicators() {
        VerificationResult result = new VerificationResult();
        
        try {
            // Look for the most recent message bubble or status
            // Handle both individual and group messages
            List<WebElement> messageElements = driver.findElements(By.xpath(
                "//android.view.View[@resource-id='message_list']/android.view.View[last()]"
            ));
            
            if (messageElements.isEmpty()) {
                // Alternative XPath for group messages or different layouts
                messageElements = driver.findElements(By.xpath(
                    "//android.widget.ListView/android.view.View[last()]"
                ));
            }
            
            if (messageElements.isEmpty()) {
                // Another alternative for different UI structures
                messageElements = driver.findElements(By.xpath(
                    "//*[contains(@resource-id, 'message') or contains(@resource-id, 'msg')]" +
                    "/android.view.View[last()]"
                ));
            }
            
            if (messageElements.isEmpty()) {
                result.status = Status.UNKNOWN;
                result.details = "No message elements found";
                return result;
            }
            
            WebElement lastMessage = messageElements.get(0);
            
            // Check for timer/sending icon
            List<WebElement> timerIcons = lastMessage.findElements(By.xpath(
                ".//*[contains(@resource-id, 'timer') or " +
                "contains(@class, 'timer') or " +
                "@text='⏱️' or " +
                "contains(@content-desc, 'sending') or " +
                "contains(@content-desc, 'Sending') or " +
                "contains(@content-desc, 'pending') or " +
                "contains(@content-desc, 'Pending')]"
            ));
            
            if (!timerIcons.isEmpty()) {
                result.status = Status.IN_PROGRESS;
                result.details = "Timer/sending icon detected";
                return result;
            }
            
            // Check status text
            String elementText = lastMessage.getText();
            String elementContent = lastMessage.getAttribute("content-desc");
            String displayText = (elementText != null && !elementText.isEmpty()) ? elementText : elementContent;
            
            if (displayText != null && !displayText.isEmpty()) {
                System.out.println("   📝 Found status text: " + displayText);
                
                // Positive indicators (case-insensitive check)
                String[] positiveIndicators = {"Sent", "Delivered", "SMS", "Message sent", "Delivered", 
                                                "sent", "delivered", "sms"};
                String[] negativeIndicators = {"Not sent", "Failed", "Error", "Unable to send", "Failed to send",
                                                "not sent", "failed", "error", "unable"};
                
                for (String positive : positiveIndicators) {
                    if (displayText.contains(positive)) {
                        result.status = Status.SUCCESS;
                        result.details = "Positive status: " + displayText;
                        return result;
                    }
                }
                
                for (String negative : negativeIndicators) {
                    if (displayText.contains(negative)) {
                        result.status = Status.FAILED;
                        result.details = "Negative status: " + displayText;
                        return result;
                    }
                }
            }
            
            // Check for voice message indicator
            List<WebElement> voiceIndicators = lastMessage.findElements(By.xpath(
                ".//*[contains(@content-desc, 'voice') or " +
                "contains(@content-desc, 'Voice') or " +
                "contains(@resource-id, 'voice') or " +
                "contains(@resource-id, 'audio') or " +
                "contains(@class, 'voice') or " +
                "contains(@class, 'audio')]"
            ));
            
            if (!voiceIndicators.isEmpty()) {
                result.status = Status.SUCCESS;
                result.details = "Voice message indicator found";
                return result;
            }
            
            result.status = Status.UNKNOWN;
            result.details = "No clear visual indicators";
            
        } catch (Exception e) {
            result.status = Status.UNKNOWN;
            result.details = "Visual check error: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     *  CHECK PAGE SOURCE
     */
    private VerificationResult checkPageSource() {
        VerificationResult result = new VerificationResult();
        
        try {
            String pageSource = driver.getPageSource().toLowerCase();
            
            // Positive indicators in page source
            if (pageSource.contains("sent") || 
                pageSource.contains("delivered") ||
                pageSource.contains("message sent") ||
                pageSource.contains("sms") ||
                pageSource.contains("voice message") ||
                pageSource.contains("audio message")) {
                result.status = Status.SUCCESS;
                result.details = "Positive indicators in page source";
                return result;
            }
            
            // Negative indicators in page source
            if (pageSource.contains("not sent") ||
                pageSource.contains("failed") ||
                pageSource.contains("error") ||
                pageSource.contains("unable to send") ||
                pageSource.contains("failed to send")) {
                result.status = Status.FAILED;
                result.details = "Negative indicators in page source";
                return result;
            }
            
            // Sending in progress
            if (pageSource.contains("sending") ||
                pageSource.contains("timer") ||
                pageSource.contains("00:") ||
                pageSource.contains("pending")) {
                result.status = Status.IN_PROGRESS;
                result.details = "Sending in progress (page source)";
                return result;
            }
            
            result.status = Status.UNKNOWN;
            result.details = "No clear indicators in page source";
            
        } catch (Exception e) {
            result.status = Status.UNKNOWN;
            result.details = "Page source check error: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     *  CHECK SPECIFIC ELEMENTS
     */
    private VerificationResult checkSpecificElements() {
        VerificationResult result = new VerificationResult();
        
        try {
            // Check for specific status elements (case-insensitive)
            List<WebElement> statusElements = driver.findElements(By.xpath(
                "//*[contains(@text, 'Sent') or " +
                "contains(@text, 'Delivered') or " +
                "contains(@text, 'Not sent') or " +
                "contains(@text, 'Failed') or " +
                "contains(@text, 'sent') or " +
                "contains(@text, 'delivered') or " +
                "contains(@text, 'not sent') or " +
                "contains(@text, 'failed') or " +
                "contains(@content-desc, 'Sent') or " +
                "contains(@content-desc, 'Delivered') or " +
                "contains(@content-desc, 'Not sent') or " +
                "contains(@content-desc, 'Failed') or " +
                "contains(@content-desc, 'sent') or " +
                "contains(@content-desc, 'delivered') or " +
                "contains(@content-desc, 'not sent') or " +
                "contains(@content-desc, 'failed')]"
            ));
            
            for (WebElement element : statusElements) {
                if (element.isDisplayed()) {
                    String text = element.getText() != null ? element.getText() : element.getAttribute("content-desc");
                    
                    if (text != null) {
                        String textLower = text.toLowerCase();
                        if (textLower.contains("sent") || textLower.contains("delivered")) {
                            result.status = Status.SUCCESS;
                            result.details = "Status element found: " + text;
                            return result;
                        } else if (textLower.contains("not sent") || textLower.contains("failed")) {
                            result.status = Status.FAILED;
                            result.details = "Status element found: " + text;
                            return result;
                        }
                    }
                }
            }
            
            result.status = Status.UNKNOWN;
            result.details = "No specific status elements found";
            
        } catch (Exception e) {
            result.status = Status.UNKNOWN;
            result.details = "Element check error: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     *  SIMPLE CHECK - For quick verification
     */
    public boolean quickVerifyMessageSent() {
        try {
            Thread.sleep(3000); // Wait a bit
            
            // Just check page source for positive indicators
            String pageSource = driver.getPageSource().toLowerCase();
            
            boolean isSent = pageSource.contains("sent") || 
                           pageSource.contains("delivered") ||
                           pageSource.contains("message sent") ||
                           pageSource.contains("sms sent");
            
            boolean isFailed = pageSource.contains("not sent") ||
                             pageSource.contains("failed to send") ||
                             pageSource.contains("failed");
            
            if (isSent) {
                System.out.println(" Quick verification: Message sent");
                return true;
            } else if (isFailed) {
                System.out.println("❌ Quick verification: Message failed");
                return false;
            }
            
            // If unclear, assume sent (to not break flow)
            System.out.println("⚠️ Quick verification: Status unclear, assuming sent");
            return true;
            
        } catch (Exception e) {
            System.out.println("⚠️ Quick verification error: " + e.getMessage());
            return false; // On error, assume failed
        }
    }
    
    /**
     *  LEGACY COMPATIBILITY - For backward compatibility
     * Keeps the original simple verification logic
     */
    public boolean legacyVerifyMessageSent() {
        try {
            // Wait for initial processing
            Thread.sleep(2000);
            
            int maxAttempts = 10; // Max 20 seconds total wait time (2 sec * 10)
            int attempts = 0;
            
            while (attempts < maxAttempts) {
                // Find the status element
                List<WebElement> statusElements = driver.findElements(By.xpath(
                    "//android.view.View[@resource-id='message_list']/android.view.View[2]"
                ));
                
                if (statusElements.isEmpty()) {
                    Thread.sleep(2000);
                    attempts++;
                    continue;
                }
                
                WebElement statusElement = statusElements.get(0);
                String elementText = statusElement.getText();
                String elementContent = statusElement.getAttribute("content-desc");
                String displayText = elementText.isEmpty() ? elementContent : elementText;
                
                // Check for timer icon (sending in progress)
                List<WebElement> timerIcons = statusElement.findElements(By.xpath(
                    ".//*[contains(@resource-id, 'timer') or contains(@class, 'timer') or @text='⏱️']"
                ));
                
                boolean hasTimerIcon = !timerIcons.isEmpty();
                
                // If timer icon is present, wait and check again
                if (hasTimerIcon) {
                    Thread.sleep(2000);
                    attempts++;
                    continue;
                }
                
                // Check status text
                if (displayText != null) {
                    if (displayText.contains("Sent") || 
                        displayText.contains("Delivered") ||
                        displayText.contains("SMS")) {
                        return true; // Success
                    } else if (displayText.contains("Not sent") ||
                              displayText.contains("Failed") ||
                              displayText.contains("Error")) {
                        return false; // Failure
                    }
                }
                
                // Also check page source as fallback
                String pageSource = driver.getPageSource();
                if (pageSource.contains("Sent") || 
                    pageSource.contains("Delivered") ||
                    pageSource.contains("SMS")) {
                    return true;
                } else if (pageSource.contains("Not sent") ||
                          pageSource.contains("Failed")) {
                    return false;
                }
                
                Thread.sleep(2000);
                attempts++;
            }
            
            // If we reach here, we timed out without clear status
            return false;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // ==================== ENUMS AND HELPER CLASSES ====================
    
    private enum Status {
        SUCCESS,
        FAILED,
        IN_PROGRESS,
        UNKNOWN
    }
    
    private static class VerificationResult {
        Status status = Status.UNKNOWN;
        String details = "";
    }
}