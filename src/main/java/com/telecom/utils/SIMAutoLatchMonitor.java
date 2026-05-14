package com.telecom.utils;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public class SIMAutoLatchMonitor {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int POLL_INTERVAL_MS = 2000;
    private static final int FLIGHT_MODE_DELAY_MS = 5000; // Increased from 3000
    
    private String deviceId;
    private int timeoutSeconds;
    
    public SIMAutoLatchMonitor(String deviceId) {
        this.deviceId = deviceId;
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }
    
    public SIMAutoLatchMonitor(String deviceId, int timeoutSeconds) {
        this.deviceId = deviceId;
        this.timeoutSeconds = timeoutSeconds;
    }
    
    /**
     *  SET PREFERRED NETWORK TYPE - FIXED FOR DUAL SIM
     */
    public boolean setPreferredNetworkType(String networkType) {
        try {
            int mode = getNetworkModeValue(networkType);
            
            // Try both SIM slots for dual SIM devices
            String[] commands = {
                String.format("adb -s %s shell settings put global preferred_network_mode %d", deviceId, mode),
                String.format("adb -s %s shell settings put global preferred_network_mode1 %d", deviceId, mode),
                String.format("adb -s %s shell cmd phone set-preferred-network-type-for-slot -s 0 %d", deviceId, mode)
            };
            
            boolean success = false;
            for (String command : commands) {
                try {
                    String result = ADBHelper.executeCommand(command);
                    if (!result.contains("Error") && !result.contains("Failed")) {
                        success = true;
                    }
                } catch (Exception e) {
                    // Continue trying other commands
                }
            }
            
            Thread.sleep(3000);
            System.out.println(" Preferred network set to: " + networkType);
            return success;
            
        } catch (Exception e) {
            System.out.println("❌ Error setting preferred network: " + e.getMessage());
            return false;
        }
    }
    
    private int getNetworkModeValue(String networkType) {
        switch (networkType.toUpperCase()) {
            case "2G": return 1;
            case "3G": return 3;
            case "4G": 
            case "LTE": return 11;
            case "5G": return 33;
            case "AUTO": return 33;
            default: return 33;
        }
    }
    
    /**
     *  MAIN AUTO-LATCH TEST EXECUTION - COMPLETELY FIXED
     */
    public AutoLatchResult executeAutoLatchTest() {
        AutoLatchResult result = new AutoLatchResult();
        result.setDeviceId(deviceId);
        result.setTestStartTime(Instant.now());
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📡 SIM AUTO-LATCH TEST - Device: " + deviceId);
        System.out.println("=".repeat(100));
        
        try {
            // ========== STEP 1: DEVICE & SIM VERIFICATION ==========
            System.out.println("\n1️⃣ DEVICE & SIM VERIFICATION");
            System.out.println("   " + "-".repeat(80));
            
            if (!verifyDeviceAndSIM()) {
                result.setSuccess(false);
                result.setErrorMessage("Device or SIM not ready");
                return result;
            }
            
            // ========== STEP 2: CAPTURE INITIAL STATE ==========
            System.out.println("\n2️⃣ INITIAL NETWORK STATE");
            System.out.println("   " + "-".repeat(80));
            
            Map<String, String> initialState = captureNetworkStateRobust();
            result.setInitialNetworkState(initialState.get("state"));
            result.setInitialRAT(initialState.get("rat"));
            result.setInitialIMSRegistered(isIMSRegisteredRobust());
            
            System.out.println("   📶 State: " + initialState.get("state"));
            System.out.println("   📡 RAT: " + initialState.get("rat"));
            System.out.println("   📞 IMS: " + (result.isInitialIMSRegistered() ? "" : "❌"));
            
            // If not registered, test is invalid
            if ("OUT_OF_SERVICE".equals(initialState.get("state")) || 
                "UNKNOWN".equals(initialState.get("state"))) {
                result.setSuccess(false);
                result.setErrorMessage("Device not registered initially - cannot test auto-latch");
                return result;
            }
            
            // ========== STEP 3: ENABLE FLIGHT MODE ==========
            System.out.println("\n3️⃣ ENABLING FLIGHT MODE");
            System.out.println("   " + "-".repeat(80));
            
            boolean flightEnabled = enableFlightModeRobust();
            if (!flightEnabled) {
                result.setSuccess(false);
                result.setErrorMessage("Failed to enable flight mode");
                return result;
            }
            
            // Verify flight mode is ON
            Thread.sleep(FLIGHT_MODE_DELAY_MS);
            String flightState = captureNetworkStateRobust().get("state");
            System.out.println("   ✈️ Flight Mode State: " + flightState);
            
            // ========== STEP 4: DISABLE FLIGHT MODE & START TIMER ==========
            System.out.println("\n4️⃣ DISABLING FLIGHT MODE & MONITORING");
            System.out.println("   " + "-".repeat(80));
            
            Instant startTime = Instant.now();
            boolean flightDisabled = disableFlightModeRobust();
            
            if (!flightDisabled) {
                result.setSuccess(false);
                result.setErrorMessage("Failed to disable flight mode");
                return result;
            }
            
            // ========== STEP 5: MONITOR REGISTRATION ==========
            System.out.println("\n5️⃣ MONITORING NETWORK REGISTRATION");
            System.out.println("   " + "-".repeat(80));
            
            NetworkRegistrationEvent registrationEvent = monitorNetworkRegistrationRobust(startTime);
            result.setRegistrationEvent(registrationEvent);
            
            // ========== STEP 6: FINALIZE RESULTS ==========
            result.setTestEndTime(Instant.now());
            
            if (registrationEvent.isRegistered()) {
                long autoLatchTimeMs = java.time.Duration.between(startTime, registrationEvent.getRegistrationTime()).toMillis();
                
                result.setSuccess(true);
                result.setAutoLatchTimeMs(autoLatchTimeMs);
                result.setAutoLatchTimeSeconds(autoLatchTimeMs / 1000.0);
                result.setFinalNetworkState(registrationEvent.getFinalNetworkState());
                result.setFinalRAT(registrationEvent.getFinalRAT());
                result.setFinalIMSRegistered(isIMSRegisteredRobust());
                
                // Determine result classification
                if (autoLatchTimeMs <= 30000) {
                    result.setTestResult("PASS");
                } else if (autoLatchTimeMs <= 60000) {
                    result.setTestResult("MARGINAL");
                } else {
                    result.setTestResult("SLOW");
                }
                
                System.out.println("\n   " + "=".repeat(80));
                System.out.println("    NETWORK REGISTERED!");
                System.out.println("   ⏱️  Auto-latch: " + autoLatchTimeMs + " ms (" + 
                                 String.format("%.2f", autoLatchTimeMs/1000.0) + "s)");
                System.out.println("   📶 Final State: " + result.getFinalNetworkState());
                System.out.println("   📡 Final RAT: " + result.getFinalRAT());
                System.out.println("   📞 IMS: " + (result.isFinalIMSRegistered() ? "" : "❌"));
                System.out.println("   📊 Result: " + result.getTestResult());
                
            } else {
                result.setSuccess(false);
                result.setTestResult("FAIL");
                result.setErrorMessage("Network registration timeout after " + timeoutSeconds + "s");
                
                // Still capture final state
                Map<String, String> finalState = captureNetworkStateRobust();
                result.setFinalNetworkState(finalState.get("state"));
                result.setFinalRAT(finalState.get("rat"));
                result.setFinalIMSRegistered(false);
                
                System.out.println("\n   " + "=".repeat(80));
                System.out.println("   ❌ REGISTRATION TIMEOUT");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Auto-latch test error: " + e.getMessage());
            result.setSuccess(false);
            result.setTestResult("ERROR");
            result.setErrorMessage(e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=".repeat(100) + "\n");
        return result;
    }
    
    /**
     *  VERIFY DEVICE AND SIM ARE READY
     */
    private boolean verifyDeviceAndSIM() {
        try {
            String serialNo = executeADBCommand("shell getprop ro.serialno").trim();
            String simState = executeADBCommand("shell getprop gsm.sim.state").trim();
            String operator = executeADBCommand("shell getprop gsm.operator.alpha").trim();
            
            System.out.println("   📱 Device: " + serialNo);
            System.out.println("   📞 SIM: " + simState);
            System.out.println("   🏢 Operator: " + operator);
            
            // Check if at least one SIM is loaded
            if (simState.contains("LOADED") || simState.contains("READY")) {
                System.out.println("    SIM verified");
                return true;
            } else {
                System.out.println("   ❌ No active SIM found");
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Verification failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     *  ROBUST NETWORK STATE CAPTURE - MULTIPLE METHODS
     */
    private Map<String, String> captureNetworkStateRobust() {
        Map<String, String> state = new HashMap<>();
        state.put("state", "UNKNOWN");
        state.put("rat", "UNKNOWN");
        
        try {
            // Method 1: Try telephony.registry (requires permissions)
            String serviceState = executeADBCommand("shell dumpsys telephony.registry | grep -i servicestate");
            if (serviceState != null && !serviceState.isEmpty()) {
                String parsedState = parseServiceState(serviceState);
                if (!"UNKNOWN".equals(parsedState)) {
                    state.put("state", parsedState);
                }
            }
            
            // Method 2: Try gsm properties
            if ("UNKNOWN".equals(state.get("state"))) {
                String gsmState = executeADBCommand("shell getprop gsm.operator.alpha").trim();
                if (!gsmState.isEmpty() && !gsmState.equals("null")) {
                    state.put("state", "IN_SERVICE");
                } else {
                    state.put("state", "OUT_OF_SERVICE");
                }
            }
            
            // Get RAT
            String networkType = executeADBCommand("shell getprop gsm.network.type").trim();
            if (!networkType.isEmpty() && !networkType.equals("Unknown")) {
                state.put("rat", parseNetworkType(networkType));
            }
            
            // Method 3: Try network info command
            if ("UNKNOWN".equals(state.get("rat"))) {
                String netInfo = executeADBCommand("shell dumpsys connectivity | grep -i 'mobile'");
                if (netInfo.contains("LTE")) state.put("rat", "LTE");
                else if (netInfo.contains("HSPA")) state.put("rat", "HSPA");
                else if (netInfo.contains("UMTS")) state.put("rat", "UMTS");
                else if (netInfo.contains("EDGE")) state.put("rat", "EDGE");
            }
            
        } catch (Exception e) {
            System.out.println("   ⚠️ State capture error: " + e.getMessage());
        }
        
        return state;
    }
    
    /**
     *  ROBUST IMS REGISTRATION CHECK
     */
    private boolean isIMSRegisteredRobust() {
        try {
            // Method 1: Try dumpsys ims
            String imsData = executeADBCommand("shell dumpsys ims");
            if (imsData != null && imsData.toLowerCase().contains("registered")) {
                return true;
            }
            
            // Method 2: Try IMS settings
            String imsSetting = executeADBCommand("shell settings get global volte_vt_enabled").trim();
            if ("1".equals(imsSetting)) {
                return true;
            }
            
            // Method 3: Check VoLTE property
            String volteState = executeADBCommand("shell getprop persist.vendor.radio.volte_state").trim();
            if ("1".equals(volteState)) {
                return true;
            }
            
        } catch (Exception e) {
            // IMS check failed, assume not registered
        }
        
        return false;
    }
    
    /**
     *  ROBUST FLIGHT MODE ENABLE - MULTIPLE METHODS
     */
    private boolean enableFlightModeRobust() {
        System.out.println("   Method 1: Using ADB settings...");
        
        try {
            // Primary method: Direct ADB command
            executeADBCommand("shell settings put global airplane_mode_on 1");
            executeADBCommand("shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
            Thread.sleep(2000);
            
            // Verify
            String airplaneState = executeADBCommand("shell settings get global airplane_mode_on").trim();
            if ("1".equals(airplaneState)) {
                System.out.println("    Flight mode enabled via ADB");
                return true;
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ ADB method failed: " + e.getMessage());
        }
        
        // Fallback: Try UI automation
        System.out.println("   Method 2: Using UI automation...");
        try {
            executeADBCommand("shell input keyevent KEYCODE_POWER");
            Thread.sleep(500);
            executeADBCommand("shell input keyevent KEYCODE_POWER");
            Thread.sleep(1000);
            
            // Swipe down for quick settings
            executeADBCommand("shell input swipe 500 0 500 500");
            Thread.sleep(1000);
            
            // Tap airplane mode (approximate coordinates)
            executeADBCommand("shell input tap 100 200");
            Thread.sleep(2000);
            
            System.out.println("   ⚠️ UI automation attempted");
            return true;
            
        } catch (Exception e) {
            System.out.println("   ❌ UI automation failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     *  ROBUST FLIGHT MODE DISABLE - MULTIPLE METHODS
     */
    private boolean disableFlightModeRobust() {
        System.out.println("   Method 1: Using ADB settings...");
        
        try {
            executeADBCommand("shell settings put global airplane_mode_on 0");
            executeADBCommand("shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
            Thread.sleep(2000);
            
            String airplaneState = executeADBCommand("shell settings get global airplane_mode_on").trim();
            if ("0".equals(airplaneState)) {
                System.out.println("    Flight mode disabled via ADB");
                return true;
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ ADB method failed: " + e.getMessage());
        }
        
        // Fallback: UI automation
        System.out.println("   Method 2: Using UI automation...");
        try {
            executeADBCommand("shell input swipe 500 0 500 500");
            Thread.sleep(1000);
            executeADBCommand("shell input tap 100 200");
            Thread.sleep(2000);
            
            System.out.println("   ⚠️ UI automation attempted");
            return true;
            
        } catch (Exception e) {
            System.out.println("   ❌ UI automation failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     *  ROBUST NETWORK MONITORING
     */
    private NetworkRegistrationEvent monitorNetworkRegistrationRobust(Instant startTime) {
        NetworkRegistrationEvent event = new NetworkRegistrationEvent();
        String previousState = "OUT_OF_SERVICE";
        String previousRAT = "NONE";
        List<NetworkTransition> transitions = new ArrayList<>();
        
        System.out.println("   Timeout: " + timeoutSeconds + " seconds");
        
        for (int elapsed = 0; elapsed < timeoutSeconds; elapsed += 2) {
            try {
                Map<String, String> currentState = captureNetworkStateRobust();
                String state = currentState.get("state");
                String rat = currentState.get("rat");
                
                // Record transition
                if (!state.equals(previousState) || !rat.equals(previousRAT)) {
                    NetworkTransition transition = new NetworkTransition(
                        elapsed, previousState, previousRAT, state, rat
                    );
                    transitions.add(transition);
                    
                    String icon = getStateIcon(state);
                    System.out.printf("   [%3ds] %s %-20s | %-10s | %s\n", 
                        elapsed, icon, state, rat, getTimestamp());
                    
                    previousState = state;
                    previousRAT = rat;
                }
                
                // Check if registered
                if (isNetworkRegistered(state)) {
                    event.setRegistered(true);
                    event.setRegistrationTime(Instant.now());
                    event.setFinalNetworkState(state);
                    event.setFinalRAT(rat);
                    event.setTransitions(transitions);
                    return event;
                }
                
                Thread.sleep(POLL_INTERVAL_MS);
                
            } catch (Exception e) {
                System.out.println("   ⚠️ Monitoring error: " + e.getMessage());
            }
        }
        
        // Timeout
        event.setRegistered(false);
        event.setTransitions(transitions);
        event.setFinalNetworkState(previousState);
        event.setFinalRAT(previousRAT);
        return event;
    }
    
    // ========== HELPER METHODS ==========
    
    private String parseServiceState(String serviceState) {
        if (serviceState.contains("IN_SERVICE") || serviceState.contains("HOME")) {
            return "IN_SERVICE";
        } else if (serviceState.contains("OUT_OF_SERVICE")) {
            return "OUT_OF_SERVICE";
        } else if (serviceState.contains("EMERGENCY")) {
            return "EMERGENCY_ONLY";
        }
        return "UNKNOWN";
    }
    
    private String parseNetworkType(String networkType) {
        networkType = networkType.toUpperCase();
        if (networkType.contains("NR") || networkType.contains("5G")) return "5G_NR";
        if (networkType.contains("LTE") || networkType.contains("4G")) return "LTE";
        if (networkType.contains("HSPA")) return "HSPA";
        if (networkType.contains("UMTS") || networkType.contains("3G")) return "UMTS";
        if (networkType.contains("EDGE")) return "EDGE";
        if (networkType.contains("GPRS")) return "GPRS";
        return networkType;
    }
    
    private String getStateIcon(String state) {
        if (state.contains("SERVICE") || state.contains("HOME")) return "🟢";
        if (state.contains("SEARCHING")) return "🔍";
        if (state.contains("OUT") || state.contains("OFF")) return "🔴";
        if (state.contains("EMERGENCY")) return "🚨";
        return "📶";
    }
    
    private boolean isNetworkRegistered(String state) {
        return state != null && 
               (state.contains("SERVICE") || state.contains("HOME"));
    }
    
    private String getTimestamp() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
    
    private String executeADBCommand(String command) {
        return ADBHelper.executeCommand("adb -s " + deviceId + " " + command);
    }
}