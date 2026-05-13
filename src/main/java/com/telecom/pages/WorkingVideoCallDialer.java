package com.telecom.pages;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.telecom.utils.ProgressReporter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Working Video Call Dialer using Correct Android Intent
 * Uses: android.telecom.extra.START_CALL_WITH_VIDEO_STATE 3
 */
public class WorkingVideoCallDialer {
    private AndroidDriver driver;
    
    public WorkingVideoCallDialer(AndroidDriver driver) {
        this.driver = driver;
    }
    
    public VideoCallResult makeVideoCall(String phoneNumber, int targetDurationSeconds, int maxAttempts) {
        VideoCallResult result = new VideoCallResult();
        result.setPhoneNumber(phoneNumber);
        result.setTargetDuration(targetDurationSeconds);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📹 INITIATING VIDEO CALL (00:01 Detection Method)");
        System.out.println("=".repeat(80));
        System.out.println("📱 Number: " + phoneNumber);
        System.out.println("🎯 Target: " + targetDurationSeconds + "s | ⏰ Ring timeout: 30s");
        System.out.println("🔄 Max Attempts: " + maxAttempts);
        
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                System.out.println("\n🔄 Attempt " + attempt + "/" + maxAttempts);
                result.setAttemptNumber(attempt);
                
                // Dial the video call
                boolean callInitiated = dialVideoCallCorrectIntent(phoneNumber);
                
                if (!callInitiated) {
                    System.out.println("❌ Failed to initiate video call");
                    result.setCallStatus("FAILED_TO_INITIATE");
                    continue;
                }
                
                System.out.println("✅ Video call dialed, waiting for 00:01...");
                
                // Wait for connection (00:01 appears)
                VideoConnectionResult connection = waitForVideoCallConnection(targetDurationSeconds);
                
                if (connection.isConnected()) {
                    result.setConnected(true);
                    result.setActualDuration(connection.getActualDuration());
                    result.setCallStatus("CONNECTED");
                    result.setVideoQuality(connection.getVideoQuality());
                    result.setRingTime(connection.getRingTimeSeconds()); // Store ring time
                    
                    System.out.println("\n🎉 VIDEO CALL SUCCESS!");
                    System.out.println("   ⏱️ Ring Time: " + connection.getRingTimeSeconds() + "s");
                    System.out.println("   ⏱️ Call Duration: " + connection.getActualDuration() + "s");
                    System.out.println("   📹 Quality: " + connection.getVideoQuality());
                    
                    // End the call
                    endVideoCall();
                    break;
                    
                } else {
                    result.setCallStatus(connection.getFailureReason());
                    result.setRingTime(connection.getRingTimeSeconds());
                    System.out.println("❌ Video call failed: " + connection.getFailureReason());
                    System.out.println("   ⏱️ Ringed for: " + connection.getRingTimeSeconds() + "s");
                    
                    // Try to end call
                    try {
                        endVideoCall();
                    } catch (Exception e) {
                        // Ignore
                    }
                    
                    if (attempt < maxAttempts) {
                        System.out.println("🔄 Retrying in 5 seconds...");
                        Thread.sleep(5000);
                    }
                }
            }
            
            System.out.println("=".repeat(80));
            
        } catch (Exception e) {
            System.out.println("❌ Video call error: " + e.getMessage());
            result.setCallStatus("FAILED");
            result.setFailureReason(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * ✅ CORRECT VIDEO CALL INTENT
     * This is the WORKING method from your ADB command
     */
    private boolean dialVideoCallCorrectIntent(String phoneNumber) {
        try {
            System.out.println("📹 Dialing video call with CORRECT intent...");
            
            // ✅ This is the WORKING format:
            // am start -a android.intent.action.CALL -d tel:8696904544 
            // --ei android.telecom.extra.START_CALL_WITH_VIDEO_STATE 3
            
            Map<String, Object> params = new HashMap<>();
            String videoCallCommand = String.format(
                "am start -a android.intent.action.CALL -d tel:%s --ei android.telecom.extra.START_CALL_WITH_VIDEO_STATE 3",
                phoneNumber
            );
            
            params.put("command", videoCallCommand);
            Object result = driver.executeScript("mobile: shell", params);
            
            System.out.println("✅ Video call intent executed");
            System.out.println("   Command: " + videoCallCommand);
            if (result != null) {
                System.out.println("   Result: " + result.toString());
            }
            
            Thread.sleep(2000);
            
            // Verify call was initiated by checking page source
            String pageSource = driver.getPageSource();
            boolean callInitiated = pageSource.contains("video") || 
                                   pageSource.contains("Video") || 
                                   pageSource.contains("Camera") || 
                                   pageSource.contains("Calling") ||
                                   pageSource.contains("Dialing");
            
            if (callInitiated) {
                System.out.println("✅ Video call UI detected - call initiated");
                return true;
            } else {
                System.out.println("⚠️ No video call UI detected - checking alternative indicators");
                
                // Sometimes the call starts without obvious UI changes
                // Check for any call-related UI
                boolean hasCallUI = pageSource.contains("00:") || 
                                   pageSource.contains("End call") ||
                                   pageSource.contains("Mute") ||
                                   pageSource.contains("Speaker");
                
                if (hasCallUI) {
                    System.out.println("✅ Call UI detected - proceeding");
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.out.println("❌ Video call intent failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * ✅ ENHANCED ULTRA: Wait for video call connection - MULTIPLE TIMER DETECTION METHODS
     */
    private VideoConnectionResult waitForVideoCallConnection(int targetDurationSeconds) {
        System.out.println("⏳ ULTRA ENHANCED: Waiting for video call connection...");
        
        VideoConnectionResult result = new VideoConnectionResult();
        long ringStartTime = System.currentTimeMillis();
        boolean connected = false;
        String failureReason = "TIMEOUT";
        int actualDuration = 0;
        String videoQuality = "STANDARD";
        
        int ringTimeoutSeconds = 30;
        int detectionAttempts = 0;
        
        // 🔥 PHASE 1: AGGRESSIVE CONNECTION DETECTION
        for (int i = 0; i < ringTimeoutSeconds; i++) {
            try {
                detectionAttempts++;
                String pageSource = driver.getPageSource();
                String pageSourceLower = pageSource.toLowerCase();
                
                // 🔥 METHOD 1: Timer detection (00:01, 00:02, etc.)
                boolean timerDetected = false;
                String timer = getCallTimerEnhanced(pageSource);
                if (timer != null) {
                    System.out.println("✅ TIMER DETECTED: " + timer);
                    timerDetected = true;
                }
                
                // 🔥 METHOD 2: Call state indicators
                boolean callStateDetected = pageSource.contains("Connected") || 
                                           pageSource.contains("In call") ||
                                           pageSource.contains("Call timer") ||
                                           pageSource.contains("Duration") ||
                                           pageSourceLower.contains("video") ||
                                           pageSourceLower.contains("camera");
                
                // 🔥 METHOD 3: UI element detection
                boolean uiElementsDetected = false;
                try {
                    // Check for video call specific UI elements
                    List<WebElement> videoElements = driver.findElements(By.xpath("//*[contains(@text, 'Video') or contains(@text, 'video')]"));
                    List<WebElement> callElements = driver.findElements(By.xpath("//*[contains(@text, 'Call') or contains(@content-desc, 'Call')]"));
                    uiElementsDetected = !videoElements.isEmpty() || !callElements.isEmpty();
                } catch (Exception e) {
                    // Continue
                }
                
                // 🔥 CONNECTION LOGIC: Any detection method counts!
                if (timerDetected || callStateDetected || uiElementsDetected) {
                    long connectionTime = System.currentTimeMillis();
                    result.setRingTimeMs(connectionTime - ringStartTime);
                    result.setRingTimeSeconds(i + 1);
                    
                    connected = true;
                    System.out.println("🎉 VIDEO CALL CONNECTED!");
                    System.out.println("   ⏱️ Ring Time: " + result.getRingTimeSeconds() + "s");
                    System.out.println("   📊 Detection Method: " + 
                        (timerDetected ? "Timer" : callStateDetected ? "Call State" : "UI Elements"));
                    
                    // Try to detect video quality
                    videoQuality = detectVideoQuality(pageSource);
                    System.out.println("📹 Video Quality: " + videoQuality);
                    
                    break;
                }
                
                // 🔥 METHOD 4: Check for call failure
                if (detectVideoCallFailureImmediate(pageSource)) {
                    failureReason = detectVideoCallFailure(pageSource);
                    System.out.println("❌ Video call failed: " + failureReason);
                    
                    result.setRingTimeMs(System.currentTimeMillis() - ringStartTime);
                    result.setRingTimeSeconds(i + 1);
                    return result;
                }
                
                // Progress logging every 2 seconds (more frequent)
                if ((i + 1) % 2 == 0) {
                    System.out.println("   🔔 Ringing... " + (i + 1) + "s | Detection attempts: " + detectionAttempts);
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
        
        if (!connected) {
            System.out.println("⏰ Ring timeout - No connection detected after " + ringTimeoutSeconds + "s");
            result.setRingTimeMs(System.currentTimeMillis() - ringStartTime);
            result.setRingTimeSeconds(ringTimeoutSeconds);
            result.setFailureReason("NO_ANSWER");
            return result;
        }
        
        // 🔥 PHASE 2: CONTINUOUS DURATION TRACKING (1-second updates)
        System.out.println("⏱️ Tracking video call duration with 1-second updates...");
        long durationStartTime = System.currentTimeMillis();
        
        for (int sec = 1; sec <= targetDurationSeconds; sec++) {
            try {
                long currentTime = System.currentTimeMillis();
                @SuppressWarnings("unused")
				int elapsedSec = (int) ((currentTime - durationStartTime) / 1000);
                
                // Progress calculation
                double progressPercentage = Math.min((sec * 100.0) / targetDurationSeconds, 100.0);
                
                // 🔥 EVERY SECOND UPDATE
                System.out.println("   ⏱️ [" + String.format("%02d", sec) + "s/" + targetDurationSeconds + "s] Progress: " + 
                                 String.format("%.1f", progressPercentage) + "%");
                
                // Send progress update via ProgressReporter
                try {
                    String deviceId = (String) driver.getCapabilities().getCapability("udid");
                    String phoneNumber = "unknown"; // You might want to pass this
                    
                    ProgressReporter.reportCallingProgress(
                        deviceId,
                        phoneNumber,
                        "Video Call In Progress (" + sec + "s/" + targetDurationSeconds + "s)",
                        "IN_CALL",
                        targetDurationSeconds,
                        progressPercentage
                    );
                } catch (Exception e) {
                    // Ignore progress reporting errors
                }
                
                // Check if call is still active
                String pageSource = driver.getPageSource();
                if (pageSource.contains("Call ended") || 
                    pageSource.contains("Call completed") ||
                    pageSource.contains("Video call ended") ||
                    pageSource.contains("Disconnected")) {
                    
                    System.out.println("ℹ️ Video call ended at " + sec + "s");
                    actualDuration = sec;
                    break;
                }
                
                Thread.sleep(1000);
                actualDuration = sec;
                
            } catch (Exception e) {
                System.out.println("⚠️ Duration tracking interrupted: " + e.getMessage());
                break;
            }
        }
        
        System.out.println("✅ Target duration reached: " + actualDuration + "s");
        
        result.setConnected(connected);
        result.setActualDuration(actualDuration);
        result.setVideoQuality(videoQuality);
        
        return result;
    }

    /**
     * 🔥 ENHANCED: Get call timer with multiple pattern detection
     */
    private String getCallTimerEnhanced(String pageSource) {
        // Pattern 1: 00:01, 00:15, 01:23 format
        Pattern pattern1 = Pattern.compile("(\\d{1,2}:\\d{2})");
        
        // Pattern 2: 0:01, 0:15 format
        Pattern pattern2 = Pattern.compile("(\\d:\\d{2})");
        
        // Pattern 3: Timer with words like "00 minutes 01 seconds"
        Pattern pattern3 = Pattern.compile("(\\d+)\\s*(min|minutes|mins)\\s*(\\d+)\\s*(sec|seconds|s)");
        
        Matcher matcher;
        
        // Try pattern 1
        matcher = pattern1.matcher(pageSource);
        while (matcher.find()) {
            String time = matcher.group();
            if (isValidTimer(time)) {
                return time;
            }
        }
        
        // Try pattern 2
        matcher = pattern2.matcher(pageSource);
        while (matcher.find()) {
            String time = matcher.group();
            if (isValidTimer("0" + time)) { // Add leading zero
                return time;
            }
        }
        
        // Try pattern 3
        matcher = pattern3.matcher(pageSource.toLowerCase());
        if (matcher.find()) {
            String minutes = matcher.group(1);
            String seconds = matcher.group(3);
            return String.format("%02d:%02d", Integer.parseInt(minutes), Integer.parseInt(seconds));
        }
        
        return null;
    }

    private boolean isValidTimer(String time) {
        try {
            String[] parts = time.split(":");
            if (parts.length != 2) return false;
            
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            
            // Valid timer if seconds < 60 and minutes reasonable
            return seconds < 60 && minutes < 60;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ✅ Detect immediate call failure (before connection)
     */
    private boolean detectVideoCallFailureImmediate(String pageSource) {
        return pageSource.contains("Call failed") || 
               pageSource.contains("Busy") || 
               pageSource.contains("Call ended") ||
               pageSource.contains("Declined") ||
               pageSource.contains("Unavailable") ||
               pageSource.contains("Not answering") ||
               pageSource.contains("Switched off") ||
               pageSource.contains("Network issue") ||
               pageSource.contains("Couldn't connect");
    }
    
    /**
     * Detect video quality from page source
     */
    private String detectVideoQuality(String pageSource) {
        if (pageSource.contains("HD") || 
            pageSource.contains("720p") || 
            pageSource.contains("1080p") ||
            pageSource.contains("High quality")) {
            return "HD";
        } else if (pageSource.contains("SD") || 
                   pageSource.contains("480p") ||
                   pageSource.contains("Low quality")) {
            return "SD";
        } else {
            return "STANDARD";
        }
    }
    
    /**
     * Detect video call failure reason
     */
    private String detectVideoCallFailure(String pageSource) {
        if (pageSource.contains("declined")) return "DECLINED";
        if (pageSource.contains("busy")) return "BUSY";
        if (pageSource.contains("unavailable")) return "UNAVAILABLE";
        if (pageSource.contains("network")) return "NETWORK_ERROR";
        if (pageSource.contains("not supported")) return "NOT_SUPPORTED";
        if (pageSource.contains("Camera not available")) return "CAMERA_UNAVAILABLE";
        if (pageSource.contains("Camera permission")) return "CAMERA_PERMISSION_DENIED";
        return "FAILED";
    }
    
    /**
     * ✅ End video call
     */
    private void endVideoCall() throws Exception {
        System.out.println("📹 Ending video call...");
        
        try {
            // Try to find end call button first
            By[] endCallButtons = {
                By.id("com.android.dialer:id/incall_end_call"),
                By.id("com.android.dialer:id/endButton"),
                By.xpath("//android.widget.Button[@content-desc='End call']"),
                By.xpath("//android.widget.Button[contains(@text, 'End')]")
            };
            
            for (By locator : endCallButtons) {
                try {
                    WebElement endButton = driver.findElement(locator);
                    if (endButton.isDisplayed()) {
                        endButton.click();
                        System.out.println("✅ Video call ended via button");
                        Thread.sleep(2000);
                        return;
                    }
                } catch (Exception e) {
                    // Try next
                }
            }
            
            // Fallback: Use ENDCALL keyevent
            System.out.println("⚠️ Button not found, using ENDCALL keyevent");
            Map<String, Object> params = new HashMap<>();
            params.put("command", "input keyevent KEYCODE_ENDCALL");
            driver.executeScript("mobile: shell", params);
            System.out.println("✅ Video call ended via keyevent");
            
        } catch (Exception e) {
            System.out.println("⚠️ End call had issues: " + e.getMessage());
        }
        
        Thread.sleep(2000);
    }
    
    
    
    // ==================== RESULT CLASSES ====================
    
    public static class VideoCallResult {
        private String phoneNumber;
        private boolean connected;
        private int targetDuration;
        private int actualDuration;
        private String callStatus;
        private int attemptNumber;
        private String failureReason;
        private String videoQuality;
        private int ringTime;  
        
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
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        
        public String getVideoQuality() { return videoQuality; }
        public void setVideoQuality(String videoQuality) { this.videoQuality = videoQuality; }
        
        public int getRingTime() { return ringTime; }
        public void setRingTime(int ringTime) { this.ringTime = ringTime; }
        
     // Ensure these getters exist:
        public long getRingTimeMs() { 
            return ringTime * 1000L; // Convert seconds to milliseconds
        }
        
        public double getRingTimeSecondsDouble() { 
            return (double) ringTime; 
        }
    }
    
    public static class VideoConnectionResult {
        private boolean connected;
        private int actualDuration;
        private String failureReason;
        private String videoQuality;
        private long ringTimeMs;        // ADD THIS
        private int ringTimeSeconds;    // ADD THIS
        
        // Getters and setters
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        
        public int getActualDuration() { return actualDuration; }
        public void setActualDuration(int actualDuration) { this.actualDuration = actualDuration; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        
        public String getVideoQuality() { return videoQuality; }
        public void setVideoQuality(String videoQuality) { this.videoQuality = videoQuality; }
        
        // ADD THESE GETTERS AND SETTERS
        public long getRingTimeMs() { return ringTimeMs; }
        public void setRingTimeMs(long ringTimeMs) { this.ringTimeMs = ringTimeMs; }
        
        public int getRingTimeSeconds() { return ringTimeSeconds; }
        public void setRingTimeSeconds(int ringTimeSeconds) { this.ringTimeSeconds = ringTimeSeconds; }
    }
}