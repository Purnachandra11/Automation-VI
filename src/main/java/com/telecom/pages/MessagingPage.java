package com.telecom.pages;

import io.appium.java_client.android.AndroidDriver;
import java.util.Map;

/**
 * ✅ MESSAGING PAGE WRAPPER
 * Delegates to ImprovedMessagingPage with voice message support
 */
public class MessagingPage {
    private ImprovedMessagingPage improvedMessaging;
    
    // Constructor with just driver
    public MessagingPage(AndroidDriver driver) {
        String deviceId = getDeviceIdFromDriver(driver);
        this.improvedMessaging = new ImprovedMessagingPage(driver, deviceId);
    }
    
    // Constructor with explicit deviceId
    public MessagingPage(AndroidDriver driver, String deviceId) {
        this.improvedMessaging = new ImprovedMessagingPage(driver, deviceId);
    }
    
    /**
     * Extract deviceId from driver capabilities
     */
    private String getDeviceIdFromDriver(AndroidDriver driver) {
        try {
            // Try to get UDID from capabilities
            Object udid = driver.getCapabilities().getCapability("udid");
            if (udid != null) {
                return udid.toString();
            }
            
            // Try deviceName
            Object deviceName = driver.getCapabilities().getCapability("deviceName");
            if (deviceName != null) {
                return deviceName.toString();
            }
            
            // Default fallback
            return "unknown-device";
            
        } catch (Exception e) {
            return "unknown-device";
        }
    }
    
    /**
     * Send individual text SMS
     */
    public boolean sendIndividualSMS(String phoneNumber, String message) {
        return improvedMessaging.sendIndividualSMS(phoneNumber, message);
    }
    
    /**
     * ✅ Send individual voice message
     */
    public boolean sendIndividualVoiceMessage(String phoneNumber) {
        return improvedMessaging.sendIndividualVoiceMessageFixed(phoneNumber);
    }
    
    /**
     * Send group text SMS
     */
    public Map<String, Object> sendGroupSMS(String groupName, String message) {
        return improvedMessaging.sendGroupSMS(groupName, message);
    }
    
    /**
     * ✅ Send group voice message
     */
    public Map<String, Object> sendGroupVoiceMessage(String groupName) {
        return improvedMessaging.sendGroupVoiceMessage(groupName);
    }
    
    /**
     * Backward compatibility - send SMS
     */
    public boolean sendSMS(String phoneNumber, String message) {
        return improvedMessaging.sendSMS(phoneNumber, message);
    }
}