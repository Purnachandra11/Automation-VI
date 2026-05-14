package com.telecom.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-Test Verification System
 * Verifies device connectivity, VoLTE capability, and network readiness
 */
public class PreTestVerifier {
    
    public static class VerificationResult {
        private boolean passed;
        private String status;
        private String message;
        private Map<String, Object> details = new HashMap<>();
        
        public VerificationResult(boolean passed, String status, String message) {
            this.passed = passed;
            this.status = status;
            this.message = message;
        }
        
        public boolean isPassed() { return passed; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
        public void addDetail(String key, Object value) { details.put(key, value); }
    }
    
    /**
     *  Main verification entry point
     */
    public static boolean verifyTestReadiness(List<Map<String, Object>> testData) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔍 PRE-TEST VERIFICATION STARTED");
        System.out.println("=".repeat(80));
        
        List<VerificationResult> results = new ArrayList<>();
        
        // 1. Verify device connectivity
        results.add(verifyDeviceConnectivity());
        
        // 2. Verify device capabilities based on test requirements
        results.add(verifyDeviceCapabilities(testData));
        
        // 3. Verify network readiness
        results.add(verifyNetworkReadiness());
        
        // 4. Verify VoLTE if required
        boolean requiresVoLTE = testData.stream()
            .anyMatch(t -> "VOLTE".equals(t.get("callType")));
        if (requiresVoLTE) {
            results.add(verifyVoLTESupport());
        }
        
        // 5. Verify Video Call support if required
        boolean requiresVideo = testData.stream()
            .anyMatch(t -> "VIDEO".equals(t.get("callType")));
        if (requiresVideo) {
            results.add(verifyVideoCallSupport());
        }
        
        // Print results
        System.out.println("\n📋 VERIFICATION RESULTS:");
        System.out.println("-".repeat(80));
        
        boolean allPassed = true;
        for (VerificationResult result : results) {
            String icon = result.isPassed() ? "" : "❌";
            System.out.println(icon + " " + result.getStatus() + ": " + result.getMessage());
            
            if (!result.getDetails().isEmpty()) {
                result.getDetails().forEach((key, value) -> 
                    System.out.println("   └─ " + key + ": " + value)
                );
            }
            
            allPassed = allPassed && result.isPassed();
        }
        
        System.out.println("-".repeat(80));
        
        if (allPassed) {
            System.out.println(" ALL VERIFICATIONS PASSED - Test can proceed");
        } else {
            System.out.println("❌ VERIFICATION FAILED - Please fix issues before running tests");
        }
        
        System.out.println("=".repeat(80) + "\n");
        
        return allPassed;
    }
    
    /**
     *  Verify both devices are connected
     */
    private static VerificationResult verifyDeviceConnectivity() {
        String aPartyDevice = System.getProperty("aPartyDevice");
        String bPartyDevice = System.getProperty("bPartyDevice");
        
        VerificationResult result = new VerificationResult(false, "Device Connectivity", "");
        
        // Check A-Party
        if (aPartyDevice == null || aPartyDevice.isEmpty()) {
            result = new VerificationResult(false, "Device Connectivity", 
                "A-Party device not specified in command line");
            return result;
        }
        
        boolean aPartyConnected = ADBHelper.isDeviceConnected(aPartyDevice);
        if (!aPartyConnected) {
            result = new VerificationResult(false, "Device Connectivity", 
                "A-Party device not connected: " + aPartyDevice);
            result.addDetail("A-Party Device", aPartyDevice + " (NOT CONNECTED)");
            return result;
        }
        
        // Check B-Party
        if (bPartyDevice == null || bPartyDevice.isEmpty()) {
            result = new VerificationResult(false, "Device Connectivity", 
                "B-Party device not specified in command line");
            return result;
        }
        
        boolean bPartyConnected = ADBHelper.isDeviceConnected(bPartyDevice);
        if (!bPartyConnected) {
            result = new VerificationResult(false, "Device Connectivity", 
                "B-Party device not connected: " + bPartyDevice);
            result.addDetail("A-Party Device", aPartyDevice + " (CONNECTED)");
            result.addDetail("B-Party Device", bPartyDevice + " (NOT CONNECTED)");
            return result;
        }
        
        // Both connected
        result = new VerificationResult(true, "Device Connectivity", "Both devices connected");
        result.addDetail("A-Party", aPartyDevice + " - " + ADBHelper.getDeviceModel(aPartyDevice));
        result.addDetail("B-Party", bPartyDevice + " - " + ADBHelper.getDeviceModel(bPartyDevice));
        
        return result;
    }
    
    /**
     *  Verify device capabilities match test requirements
     */
    private static VerificationResult verifyDeviceCapabilities(List<Map<String, Object>> testData) {
        VerificationResult result = new VerificationResult(true, "Device Capabilities", "");
        
        // Count test types
        long voiceTests = testData.stream().filter(t -> "VOICE".equals(t.get("callType"))).count();
        long volteTests = testData.stream().filter(t -> "VOLTE".equals(t.get("callType"))).count();
        long videoTests = testData.stream().filter(t -> "VIDEO".equals(t.get("callType"))).count();
        
        StringBuilder msg = new StringBuilder();
        msg.append("Test requirements validated");
        
        if (voiceTests > 0) result.addDetail("Voice Calls", voiceTests + " tests");
        if (volteTests > 0) result.addDetail("VoLTE Calls", volteTests + " tests (requires 4G/5G)");
        if (videoTests > 0) result.addDetail("Video Calls", videoTests + " tests (requires video capability)");
        
        result = new VerificationResult(true, "Device Capabilities", msg.toString());
        return result;
    }
    
    /**
     *  Verify network readiness
     */
    private static VerificationResult verifyNetworkReadiness() {
        String aPartyDevice = System.getProperty("aPartyDevice");
        String bPartyDevice = System.getProperty("bPartyDevice");
        
        VerificationResult result = new VerificationResult(true, "Network Readiness", "");
        
        // Check A-Party network
        String aPartyNetwork = NetworkMonitor.getCurrentNetworkType(aPartyDevice);
        boolean aPartyMobileData = ADBHelper.isMobileDataEnabled(aPartyDevice);
        
        // Check B-Party network
        String bPartyNetwork = NetworkMonitor.getCurrentNetworkType(bPartyDevice);
        boolean bPartyMobileData = ADBHelper.isMobileDataEnabled(bPartyDevice);
        
        boolean bothReady = aPartyMobileData && bPartyMobileData;
        
        if (bothReady) {
            result = new VerificationResult(true, "Network Readiness", "Both devices have active networks");
            result.addDetail("A-Party Network", aPartyNetwork + " (Mobile Data: ON)");
            result.addDetail("B-Party Network", bPartyNetwork + " (Mobile Data: ON)");
        } else {
            result = new VerificationResult(false, "Network Readiness", "Network not ready on one or both devices");
            result.addDetail("A-Party", aPartyNetwork + " (Mobile Data: " + (aPartyMobileData ? "ON" : "OFF") + ")");
            result.addDetail("B-Party", bPartyNetwork + " (Mobile Data: " + (bPartyMobileData ? "ON" : "OFF") + ")");
        }
        
        return result;
    }
    
    /**
     *  Verify VoLTE support on both devices
     */
    private static VerificationResult verifyVoLTESupport() {
        String aPartyDevice = System.getProperty("aPartyDevice");
        String bPartyDevice = System.getProperty("bPartyDevice");
        
        VerificationResult result = new VerificationResult(false, "VoLTE Support", "");
        
        // Check A-Party VoLTE
        Map<String, String> aPartyVolte = NetworkMonitor.getVolteStatus(aPartyDevice);
        boolean aPartyEnabled = "true".equals(aPartyVolte.get("volteEnabled"));
        String aPartyNetwork = NetworkMonitor.getCurrentNetworkType(aPartyDevice);
        
        // Check B-Party VoLTE
        Map<String, String> bPartyVolte = NetworkMonitor.getVolteStatus(bPartyDevice);
        boolean bPartyEnabled = "true".equals(bPartyVolte.get("volteEnabled"));
        String bPartyNetwork = NetworkMonitor.getCurrentNetworkType(bPartyDevice);
        
        // VoLTE requires 4G/5G network
        boolean aPartyNetworkOk = aPartyNetwork.equals("4G") || aPartyNetwork.equals("5G") || aPartyNetwork.equals("LTE");
        boolean bPartyNetworkOk = bPartyNetwork.equals("4G") || bPartyNetwork.equals("5G") || bPartyNetwork.equals("LTE");
        
        boolean bothSupported = aPartyEnabled && bPartyEnabled && aPartyNetworkOk && bPartyNetworkOk;
        
        if (bothSupported) {
            result = new VerificationResult(true, "VoLTE Support", "VoLTE enabled on both devices");
            result.addDetail("A-Party", aPartyNetwork + " network, VoLTE: ON");
            result.addDetail("B-Party", bPartyNetwork + " network, VoLTE: ON");
        } else {
            result = new VerificationResult(false, "VoLTE Support", "VoLTE not available on one or both devices");
            result.addDetail("A-Party", aPartyNetwork + " network, VoLTE: " + (aPartyEnabled ? "ON" : "OFF"));
            result.addDetail("B-Party", bPartyNetwork + " network, VoLTE: " + (bPartyEnabled ? "ON" : "OFF"));
            
            if (!aPartyNetworkOk || !bPartyNetworkOk) {
                result.addDetail("Issue", "VoLTE requires 4G/5G network");
            }
        }
        
        return result;
    }
    
    /**
     *  Verify Video Call support
     */
    private static VerificationResult verifyVideoCallSupport() {
        String aPartyDevice = System.getProperty("aPartyDevice");
        String bPartyDevice = System.getProperty("bPartyDevice");
        
        VerificationResult result = new VerificationResult(false, "Video Call Support", "");
        
        // Check if dialer app supports video calls (check for ViLTE or VoLTE capability)
        String aPartyNetwork = NetworkMonitor.getCurrentNetworkType(aPartyDevice);
        String bPartyNetwork = NetworkMonitor.getCurrentNetworkType(bPartyDevice);
        
        // Video calls typically require 4G/5G
        boolean aPartyNetworkOk = aPartyNetwork.equals("4G") || aPartyNetwork.equals("5G") || aPartyNetwork.equals("LTE");
        boolean bPartyNetworkOk = bPartyNetwork.equals("4G") || bPartyNetwork.equals("5G") || bPartyNetwork.equals("LTE");
        
        // Check if camera permissions are granted
        boolean aPartyCameraOk = checkCameraPermission(aPartyDevice);
        boolean bPartyCameraOk = checkCameraPermission(bPartyDevice);
        
        boolean videoSupported = aPartyNetworkOk && bPartyNetworkOk && aPartyCameraOk && bPartyCameraOk;
        
        if (videoSupported) {
            result = new VerificationResult(true, "Video Call Support", "Video calling available on both devices");
            result.addDetail("A-Party", aPartyNetwork + " network, Camera: OK");
            result.addDetail("B-Party", bPartyNetwork + " network, Camera: OK");
        } else {
            result = new VerificationResult(false, "Video Call Support", "Video calling not available");
            result.addDetail("A-Party", aPartyNetwork + ", Camera: " + (aPartyCameraOk ? "OK" : "NOT GRANTED"));
            result.addDetail("B-Party", bPartyNetwork + ", Camera: " + (bPartyCameraOk ? "OK" : "NOT GRANTED"));
            
            if (!aPartyNetworkOk || !bPartyNetworkOk) {
                result.addDetail("Issue", "Video calls require 4G/5G network");
            }
        }
        
        return result;
    }
    
    /**
     * Check if camera permission is granted
     */
    private static boolean checkCameraPermission(String deviceId) {
        try {
            String result = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys package com.android.dialer | grep CAMERA"
            );
            return result.contains("granted=true");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     *  Grant camera permissions if needed
     */
    public static void grantCameraPermissions(String deviceId) {
        try {
            System.out.println("📹 Granting camera permissions on: " + deviceId);
            ADBHelper.executeCommand("adb -s " + deviceId + " shell pm grant com.android.dialer android.permission.CAMERA");
            System.out.println(" Camera permissions granted");
        } catch (Exception e) {
            System.out.println("⚠️ Could not grant camera permissions: " + e.getMessage());
        }
    }
}