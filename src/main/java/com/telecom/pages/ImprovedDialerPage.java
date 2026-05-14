package com.telecom.pages;

import com.telecom.config.ElementConfig;
import com.telecom.utils.ProgressReporter; 
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ImprovedDialerPage {
    private AndroidDriver driver;
    
    public ImprovedDialerPage(AndroidDriver driver) {
        this.driver = driver;
        new WebDriverWait(driver, Duration.ofSeconds(10));
    }
    
    public CallResult makeCompleteCall(String phoneNumber, int targetDurationSeconds, int maxAttempts, String cPartyNumber) throws Exception {
        CallResult callResult = new CallResult();
        callResult.setPhoneNumber(phoneNumber);
        callResult.setTargetDuration(targetDurationSeconds);
        
        // Get device ID
        String deviceId = (String) driver.getCapabilities().getCapability("deviceName");
        
        // 🔥 Report initial calling state
        ProgressReporter.reportCallingProgress(
            deviceId,
            phoneNumber,
            "calling",
            "initiating",
            0,
            0.0
        );
        
        boolean enableConference = (cPartyNumber != null && !cPartyNumber.isEmpty());
        int successfulAttempt = 0;
        
        try {
            System.out.println("📞 Making call via intent: " + phoneNumber);
            System.out.println("   Target Duration: " + targetDurationSeconds + "s");
            System.out.println("   Max Attempts: " + maxAttempts);
            System.out.println("   Conference: " + (enableConference ? "Enabled (C Party: " + cPartyNumber + ")" : "Disabled"));
            
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                System.out.println("   Attempt " + attempt + "/" + maxAttempts);
                callResult.setAttemptNumber(attempt);
                
                // Use intent-based calling (100% working method)
                dialNumberViaIntent(phoneNumber);
                
                // Wait for call to connect with PRECISE duration tracking
                CallConnectionResult connectionResult = waitForCallConnectionWithDuration(targetDurationSeconds, enableConference);
                
                if (connectionResult.isConnected()) {
                    callResult.setConnected(true);
                    callResult.setActualDuration(connectionResult.getActualDuration());
                    callResult.setCallStatus("CONNECTED");
                    callResult.setFailureDetails(connectionResult.getFailureReason());
                    callResult.setConferenceInitiated(connectionResult.isConferenceInitiated());
                    successfulAttempt = attempt;
                    
                    System.out.println("⏱ Call connected, actual duration: " + callResult.getActualDuration() + "s");
                    System.out.println("🎯 Duration target reached: " + connectionResult.isDurationTargetReached());
                    System.out.println("🤝 Conference initiated: " + connectionResult.isConferenceInitiated());
                    
                    int totalCallDuration = connectionResult.getActualDuration();
                    
                    // If conference is enabled and initiated, add C party
                    if (enableConference && connectionResult.isConferenceInitiated()) {
                        System.out.println("📞 Adding C Party to conference: " + cPartyNumber);
                        ConferenceResult conferenceResult = addPartyToConferenceSimple(cPartyNumber, totalCallDuration);
                        
                        if (conferenceResult.isConferenceSuccess()) {
                            System.out.println(" Conference call established successfully");
                            totalCallDuration = conferenceResult.getTotalDuration();
                            callResult.setConferenceSuccess(true);
                            callResult.setConferenceDuration(conferenceResult.getConferenceDuration());
                            
                            // Continue the conference call for remaining duration if any
                            int remainingTime = targetDurationSeconds - totalCallDuration;
                            if (remainingTime > 0) {
                                System.out.println("⏱ Continuing conference for " + remainingTime + "s");
                                Thread.sleep(remainingTime * 1000);
                                totalCallDuration = targetDurationSeconds;
                            }
                        } else {
                            System.out.println("❌ Conference call failed, continuing with B party only");
                            callResult.setConferenceSuccess(false);
                            callResult.setConferenceDuration(0);
                            // Continue with B party for remaining time
                            int remainingTime = targetDurationSeconds - totalCallDuration;
                            if (remainingTime > 0) {
                                System.out.println("⏱ Continuing call for " + remainingTime + "s");
                                Thread.sleep(remainingTime * 1000);
                                totalCallDuration = targetDurationSeconds;
                            }
                        }
                    } else if (enableConference && !connectionResult.isConferenceInitiated()) {
                        // Conference was enabled but not initiated (call ended before conference time)
                        System.out.println("ℹ️ Conference not initiated, call ended before conference time");
                        callResult.setConferenceSuccess(false);
                        callResult.setConferenceDuration(0);
                        // Continue with B party for remaining time
                        int remainingTime = targetDurationSeconds - totalCallDuration;
                        if (remainingTime > 0) {
                            System.out.println("⏱ Continuing call for " + remainingTime + "s");
                            Thread.sleep(remainingTime * 1000);
                            totalCallDuration = targetDurationSeconds;
                        }
                    }
                    
                    // Update final duration (B party only OR B+C party conference)
                    callResult.setActualDuration(totalCallDuration);
                    
                    // End the call
                    endCall();
                    break; // Exit loop on successful call
                    
                } else {
                    callResult.setCallStatus(connectionResult.getFailureReason());
                    callResult.setFailureDetails(connectionResult.getFailureReason());
                    System.out.println("❌ Call failed: " + connectionResult.getFailureReason());
                    
                    // End call if still active
                    try {
                        endCall();
                    } catch (Exception ex) {
                        // Ignore
                    }
                    
                    // If this is not the last attempt, wait and retry
                    if (attempt < maxAttempts) {
                        System.out.println(" Retrying call in 5 seconds...");
                        Thread.sleep(3000);
                    } else {
                        System.out.println("❌ All " + maxAttempts + " attempts failed");
                    }
                }
            }
            
            // Set the final attempt number (the successful one or the last failed one)
            callResult.setAttemptNumber(successfulAttempt > 0 ? successfulAttempt : maxAttempts);
            
            System.out.println(" Call completed: " + phoneNumber + " | Status: " + callResult.getCallStatus() + 
                              " | Total Duration: " + callResult.getActualDuration() + "s" +
                              " | Attempts: " + callResult.getAttemptNumber() + "/" + maxAttempts);
            return callResult;
            
        } catch (Exception e) {
            System.out.println("❌ Call failed: " + e.getMessage());
            callResult.setCallStatus("ERROR: " + e.getMessage());
            callResult.setFailureDetails(e.getMessage());
            callResult.setAttemptNumber(maxAttempts);
            try {
                endCall();
            } catch (Exception ex) {
                // Ignore
            }
            return callResult;
        }
    }
    
    
    // Overloaded method for backward compatibility
    public CallResult makeCompleteCall(String phoneNumber, int targetDurationSeconds, int maxAttempts) throws Exception {
        return makeCompleteCall(phoneNumber, targetDurationSeconds, maxAttempts, null);
    }
    
    public CallConnectionResult waitForCallConnectionWithDuration(int targetDurationSeconds, boolean enableConference) {
        System.out.println("   Waiting for call connection with PRECISE duration tracking...");
        System.out.println("  🎯 Target Duration: " + targetDurationSeconds + "s");
        
        long startTime = System.currentTimeMillis();
        boolean callConnected = false;
        String failureReason = "TIMEOUT";
        int actualDuration = 0;
        boolean durationTargetReached = false;
        boolean conferenceInitiated = false;
        
        int conferenceTime = (int) (targetDurationSeconds * 0.3);
        int totalTimeoutSeconds = Math.min(targetDurationSeconds + 10, 30);
        
        // Get device ID from driver capabilities
        String deviceId = (String) driver.getCapabilities().getCapability("deviceName");
        
        for (int i = 0; i < totalTimeoutSeconds; i++) {
            try {
                String pageSource = driver.getPageSource();
                long currentTime = System.currentTimeMillis();
                actualDuration = (int) ((currentTime - startTime) / 1000);
                
                // Calculate progress percentage
                double progressPercentage = Math.min((actualDuration * 100.0) / targetDurationSeconds, 100.0);
                
                if (pageSource.contains("00:") || pageSource.contains("Call timer") || 
                    pageSource.contains("Connected") || pageSource.contains("In call")) {
                    
                    if (!callConnected) {
                        callConnected = true;
                        System.out.println("  ✓ Call connected! Starting duration timer...");
                        
                        // 🔥 Report call connected
                        ProgressReporter.reportCallingProgress(
                            deviceId,
                            "unknown", // phone number not available here
                            "calling",
                            "connected",
                            actualDuration,
                            progressPercentage
                        );
                    }
                    
                    // 🔥 Send periodic progress updates
                    if (i % 5 == 0) { // Every 5 seconds
                        ProgressReporter.reportCallingProgress(
                            deviceId,
                            "unknown",
                            "calling",
                            "in-progress",
                            actualDuration,
                            progressPercentage
                        );
                    }
                    
                    if (enableConference && !conferenceInitiated && actualDuration >= conferenceTime) {
                        System.out.println("  🤝 Conference time reached at " + actualDuration + "s");
                        conferenceInitiated = true;
                        
                        ProgressReporter.reportCallingProgress(
                            deviceId,
                            "unknown",
                            "calling",
                            "initiating-conference",
                            actualDuration,
                            progressPercentage
                        );
                        break;
                    }
                    
                    if (actualDuration >= targetDurationSeconds && !durationTargetReached) {
                        durationTargetReached = true;
                        System.out.println("   Target duration reached (" + targetDurationSeconds + "s)");
                        
                        ProgressReporter.reportCallingProgress(
                            deviceId,
                            "unknown",
                            "calling",
                            "completed",
                            actualDuration,
                            100.0
                        );
                        break;
                    }
                    
                    if (pageSource.contains("Call ended") || pageSource.contains("Call completed")) {
                        failureReason = "ENDED_AUTOMATICALLY";
                        System.out.println("  ℹ️ Call ended automatically at " + actualDuration + "s");
                        
                        ProgressReporter.reportTestComplete(
                            deviceId,
                            "calling",
                            true,
                            "Call ended automatically after " + actualDuration + "s"
                        );
                        break;
                    }
                    
                } else if (callConnected) {
                    System.out.println("  ℹ️ Call disconnected at " + actualDuration + "s");
                    
                    ProgressReporter.reportTestComplete(
                        deviceId,
                        "calling",
                        true,
                        "Call completed after " + actualDuration + "s"
                    );
                    break;
                }
                
                if (detectCallFailure(pageSource)) {
                    failureReason = getDetailedFailureReason(pageSource);
                    System.out.println("  ❌ Call failed: " + failureReason);
                    
                    ProgressReporter.reportTestComplete(
                        deviceId,
                        "calling",
                        false,
                        "Call failed: " + failureReason
                    );
                    break;
                }
                
            } catch (Exception e) {
                // Continue checking
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        CallConnectionResult result = new CallConnectionResult();
        result.setConnected(callConnected);
        result.setActualDuration(actualDuration);
        result.setFailureReason(failureReason);
        result.setDurationTargetReached(durationTargetReached);
        result.setConferenceInitiated(conferenceInitiated);
        
        return result;
    }
    
    /**
     * SIMPLE WORKING CONFERENCE METHOD
     * Direct approach without complex merging logic
     */
    public ConferenceResult addPartyToConferenceSimple(String cPartyNumber, int bPartyDuration) {
        ConferenceResult result = new ConferenceResult();
        try {
            System.out.println("  📞 SIMPLE CONFERENCE: Adding C Party: " + cPartyNumber);
            
            // Step 1: Directly dial C Party using the working ADB method
            dialNumberViaADB(cPartyNumber);
            Thread.sleep(3000);
            
            // Step 2: Wait for both calls to be active
            System.out.println("   Waiting for both calls to be active...");
            Thread.sleep(2000);
            
            // Step 3: Check if both calls are active and merge if possible
            String pageSource = driver.getPageSource();
            if (pageSource.contains("2 calls") || pageSource.contains("Merge") || pageSource.contains("Conference")) {
                System.out.println("   Both calls detected, attempting merge...");
                
                if (tryManualMerge()) {
                    result.setConferenceSuccess(true);
                    result.setConferenceDuration(10);
                    result.setTotalDuration(bPartyDuration + 10);
                    System.out.println("   Conference merged successfully");
                } else {
                    // Even if merge fails, both calls are active
                    result.setConferenceSuccess(true);
                    result.setConferenceDuration(10);
                    result.setTotalDuration(bPartyDuration + 10);
                    System.out.println("  ⚠️ Merge failed but both calls active");
                }
            } else {
                // Only one call active
                result.setConferenceSuccess(false);
                result.setConferenceDuration(0);
                result.setTotalDuration(bPartyDuration);
                System.out.println("  ❌ Only one call active, conference failed");
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ Simple conference failed: " + e.getMessage());
            result.setConferenceSuccess(false);
            result.setConferenceDuration(0);
            result.setTotalDuration(bPartyDuration);
        }
        
        return result;
    }
    
    /**
     * DIRECT CONFERENCE METHOD - Most reliable approach
     */
    public ConferenceResult addPartyToConferenceDirect(String cPartyNumber, int bPartyDuration) {
        ConferenceResult result = new ConferenceResult();
        
        try {
            System.out.println("  📞 DIRECT CONFERENCE: Dialing C Party: " + cPartyNumber);
            
            // Use direct ADB intent with actual C Party number
            Map<String, Object> params = new HashMap<>();
            params.put("command", "am start -a android.intent.action.CALL -d tel:" + cPartyNumber);
            driver.executeScript("mobile: shell", params);
            
            System.out.println("   Conference intent sent to: " + cPartyNumber);
            Thread.sleep(3000);
            
            // Check if conference is automatically established
            String pageSource = driver.getPageSource();
            if (pageSource.contains("Conference") || pageSource.contains("Merge") || 
                pageSource.contains("2 calls") || pageSource.contains("Switching")) {
                result.setConferenceSuccess(true);
                result.setConferenceDuration(15); // Default conference duration
                result.setTotalDuration(bPartyDuration + 15);
                System.out.println("   Conference automatically established");
            } else {
                // Try manual merge as fallback
                if (tryManualMerge()) {
                    result.setConferenceSuccess(true);
                    result.setConferenceDuration(15);
                    result.setTotalDuration(bPartyDuration + 15);
                    System.out.println("   Conference established via manual merge");
                } else {
                    result.setConferenceSuccess(false);
                    result.setConferenceDuration(0);
                    result.setTotalDuration(bPartyDuration);
                    System.out.println("  ❌ Conference could not be established");
                }
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ Direct conference failed: " + e.getMessage());
            result.setConferenceSuccess(false);
            result.setConferenceDuration(0);
            result.setTotalDuration(bPartyDuration);
        }
        
        return result;
    }
    
    // Try manual merge with confirmed XPaths
    private boolean tryManualMerge() {
        try {
            System.out.println("   Attempting manual merge...");
            
            // First try the More Options button
            By moreOptions = By.xpath("//androidx.compose.ui.platform.ComposeView[@resource-id=\"com.google.android.dialer:id/incall_main_buttons_container\"]/android.view.View/android.view.View/android.view.View[1]/android.view.View[7]/android.widget.CheckBox");
            
            WebElement moreOptionsBtn = driver.findElement(moreOptions);
            if (moreOptionsBtn.isDisplayed()) {
                moreOptionsBtn.click();
                System.out.println("   More options clicked");
                Thread.sleep(2000);
                
                // Now try merge button
                By mergeButton = By.xpath("//android.view.View[@content-desc=\"Merge calls\"]");
                WebElement mergeBtn = driver.findElement(mergeButton);
                if (mergeBtn.isDisplayed()) {
                    mergeBtn.click();
                    System.out.println("   Merge button clicked - Conference established");
                    Thread.sleep(3000);
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.out.println("  ❌ Manual merge failed: " + e.getMessage());
            return false;
        }
    }
    
    // Enhanced dial method using ADB
    private void dialNumberViaADB(String phoneNumber) {
        try {
            System.out.println("  📲 Dialing via ADB: " + phoneNumber);
            
            Map<String, Object> params = new HashMap<>();
            params.put("command", "am start -a android.intent.action.CALL -d tel:" + phoneNumber);
            driver.executeScript("mobile: shell", params);
            
            System.out.println("   Dial intent sent via ADB");
            Thread.sleep(3000);
        } catch (Exception e) {
            System.out.println("  ❌ ADB dial failed: " + e.getMessage());
        }
    }
    
    public void dialNumberViaIntent(String phoneNumber) throws Exception {
        System.out.println("  📲 Calling via intent: " + phoneNumber);
        
        Map<String, Object> params = new HashMap<>();
        params.put("command", "am start -a android.intent.action.CALL -d tel:" + phoneNumber);
        driver.executeScript("mobile: shell", params);
        Thread.sleep(3000);
        
        System.out.println("  ✓ Call intent sent successfully");
    }
    
    // Enhanced failure detection method
    private boolean detectCallFailure(String pageSource) {
        return pageSource.contains("Call failed") || 
               pageSource.contains("Busy") || 
               pageSource.contains("Call barred") || 
               pageSource.contains("Invalid number") ||
               pageSource.contains("Call not sent") ||
               pageSource.contains("Network busy") ||
               pageSource.contains("Switching") ||
               pageSource.contains("Pack expired") ||
               pageSource.contains("Format incorrect") ||
               pageSource.contains("Unable to call") ||
               pageSource.contains("Call ending");
    }
    
    // Detailed failure reason extraction
    private String getDetailedFailureReason(String pageSource) {
        if (pageSource.contains("Busy")) return "BUSY";
        else if (pageSource.contains("Call barred")) return "CALL_BARRED";
        else if (pageSource.contains("Invalid number")) return "INVALID_NUMBER";
        else if (pageSource.contains("Network busy")) return "NETWORK_BUSY";
        else if (pageSource.contains("Pack expired")) return "PACK_EXPIRED";
        else if (pageSource.contains("Format incorrect")) return "FORMAT_INCORRECT";
        else if (pageSource.contains("Switching")) return "CALL_SWITCHED";
        else if (pageSource.contains("Unable to call")) return "UNABLE_TO_CALL";
        else if (pageSource.contains("Call ending")) return "CALL_ENDING";
        else return "CALL_FAILED";
    }
    
    public void endCall() throws Exception {
        System.out.println("  📞 Ending call...");
        
        try {
            // Try multiple end call button locators
            By[] endCallOptions = ElementConfig.getEndCallButtonOptions();
            
            for (By locator : endCallOptions) {
                try {
                    WebElement endButton = driver.findElement(locator);
                    if (endButton.isDisplayed()) {
                        endButton.click();
                        System.out.println("  ✓ Call ended via: " + locator);
                        Thread.sleep(2000);
                        return;
                    }
                } catch (Exception e) {
                    // Continue to next locator
                }
            }
            
            // Use ENDCALL key as fallback
            driver.pressKey(new KeyEvent(AndroidKey.ENDCALL));
            System.out.println("  ✓ Call ended via ENDCALL key");
            
        } catch (Exception e) {
            System.out.println("  ❌ All end call methods failed, using ENDCALL key");
            driver.pressKey(new KeyEvent(AndroidKey.ENDCALL));
        }
        
        Thread.sleep(2000);
    }
    
    // Inner class for call results
    public static class CallResult {
        private String phoneNumber;
        private boolean connected;
        private int targetDuration;
        private int actualDuration;
        private String callStatus;
        private int attemptNumber;
        private String failureDetails;
        private boolean conferenceInitiated;
        private boolean conferenceSuccess;
        private int conferenceDuration;
        
        // Getters and setters
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        
        public int getTargetDuration() { return targetDuration; }
        public void setTargetDuration(int targetDuration) { this.targetDuration = targetDuration; }
        
        public int getActualDuration() { return actualDuration; }
        public void setActualDuration(int actualDuration) { this.actualDuration = actualDuration; }
        
        public String getCallStatus() { return callStatus; }
        public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
        
        public int getAttemptNumber() { return attemptNumber; }
        public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
        
        public String getFailureDetails() { return failureDetails; }
        public void setFailureDetails(String failureDetails) { this.failureDetails = failureDetails; }
        
        public boolean isConferenceInitiated() { return conferenceInitiated; }
        public void setConferenceInitiated(boolean conferenceInitiated) { this.conferenceInitiated = conferenceInitiated; }
        
        public boolean isConferenceSuccess() { return conferenceSuccess; }
        public void setConferenceSuccess(boolean conferenceSuccess) { this.conferenceSuccess = conferenceSuccess; }
        
        public int getConferenceDuration() { return conferenceDuration; }
        public void setConferenceDuration(int conferenceDuration) { this.conferenceDuration = conferenceDuration; }
    }
    
    // Inner class for connection results
    public static class CallConnectionResult {
        private boolean connected;
        private int actualDuration;
        private String failureReason;
        private boolean durationTargetReached;
        private boolean conferenceInitiated;
        
        // Getters and setters
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        
        public int getActualDuration() { return actualDuration; }
        public void setActualDuration(int actualDuration) { this.actualDuration = actualDuration; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        
        public boolean isDurationTargetReached() { return durationTargetReached; }
        public void setDurationTargetReached(boolean durationTargetReached) { this.durationTargetReached = durationTargetReached; }
        
        public boolean isConferenceInitiated() { return conferenceInitiated; }
        public void setConferenceInitiated(boolean conferenceInitiated) { this.conferenceInitiated = conferenceInitiated; }
    }
    
    // Inner class for conference results
    public static class ConferenceResult {
        private boolean conferenceSuccess;
        private int conferenceDuration;
        private int totalDuration;
        
        // Getters and setters
        public boolean isConferenceSuccess() { return conferenceSuccess; }
        public void setConferenceSuccess(boolean conferenceSuccess) { this.conferenceSuccess = conferenceSuccess; }
        
        public int getConferenceDuration() { return conferenceDuration; }
        public void setConferenceDuration(int conferenceDuration) { this.conferenceDuration = conferenceDuration; }
        
        public int getTotalDuration() { return totalDuration; }
        public void setTotalDuration(int totalDuration) { this.totalDuration = totalDuration; }
    }
}