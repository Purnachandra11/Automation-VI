package com.telecom.utils;

import java.time.Instant;

/**
 *  Result class for auto-latch test
 */
public class AutoLatchResult {
    private String deviceId;
    private Instant testStartTime;
    private Instant testEndTime;
    private boolean success;
    private String testResult;
    private long autoLatchTimeMs;
    private double autoLatchTimeSeconds;
    private String initialNetworkState;
    private String initialRAT;
    private boolean initialIMSRegistered;
    private String finalNetworkState;
    private String finalRAT;
    private boolean finalIMSRegistered;
    private NetworkRegistrationEvent registrationEvent;
    private String errorMessage;
    
    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public Instant getTestStartTime() { return testStartTime; }
    public void setTestStartTime(Instant testStartTime) { this.testStartTime = testStartTime; }
    
    public Instant getTestEndTime() { return testEndTime; }
    public void setTestEndTime(Instant testEndTime) { this.testEndTime = testEndTime; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getTestResult() { return testResult; }
    public void setTestResult(String testResult) { this.testResult = testResult; }
    
    public long getAutoLatchTimeMs() { return autoLatchTimeMs; }
    public void setAutoLatchTimeMs(long autoLatchTimeMs) { this.autoLatchTimeMs = autoLatchTimeMs; }
    
    public double getAutoLatchTimeSeconds() { return autoLatchTimeSeconds; }
    public void setAutoLatchTimeSeconds(double autoLatchTimeSeconds) { this.autoLatchTimeSeconds = autoLatchTimeSeconds; }
    
    public String getInitialNetworkState() { return initialNetworkState; }
    public void setInitialNetworkState(String initialNetworkState) { this.initialNetworkState = initialNetworkState; }
    
    public String getInitialRAT() { return initialRAT; }
    public void setInitialRAT(String initialRAT) { this.initialRAT = initialRAT; }
    
    public boolean isInitialIMSRegistered() { return initialIMSRegistered; }
    public void setInitialIMSRegistered(boolean initialIMSRegistered) { this.initialIMSRegistered = initialIMSRegistered; }
    
    public String getFinalNetworkState() { return finalNetworkState; }
    public void setFinalNetworkState(String finalNetworkState) { this.finalNetworkState = finalNetworkState; }
    
    public String getFinalRAT() { return finalRAT; }
    public void setFinalRAT(String finalRAT) { this.finalRAT = finalRAT; }
    
    public boolean isFinalIMSRegistered() { return finalIMSRegistered; }
    public void setFinalIMSRegistered(boolean finalIMSRegistered) { this.finalIMSRegistered = finalIMSRegistered; }
    
    public NetworkRegistrationEvent getRegistrationEvent() { return registrationEvent; }
    public void setRegistrationEvent(NetworkRegistrationEvent registrationEvent) { this.registrationEvent = registrationEvent; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
 // Add this to AutoLatchResult.java after other fields:
    private String finalNetworkType;

    // Add these getter and setter:
    public String getFinalNetworkType() { return finalNetworkType; }
    public void setFinalNetworkType(String finalNetworkType) { this.finalNetworkType = finalNetworkType; }
}