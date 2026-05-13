package com.telecom.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ IMPROVED VOLTE MANAGER
 * - Enable/Disable VoLTE properly
 * - Accurate detection
 * - Proper IMS registration
 */
public class ImprovedVoLTEManager {
    
    /**
     * ✅ ENABLE VOLTE ON DEVICE
     */
    public static boolean enableVoLTE(String deviceId) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📶 ENABLING VOLTE");
        System.out.println("=".repeat(80));
        System.out.println("📱 Device: " + deviceId);
        
        try {
            // Method 1: Enable VoLTE via settings database
            String enableCmd1 = "adb -s " + deviceId + " shell settings put global volte_enable 1";
            ADBHelper.executeCommand(enableCmd1);
            System.out.println("✓ Global VoLTE setting enabled");
            
            // Method 2: Enable enhanced 4G LTE mode
            String enableCmd2 = "adb -s " + deviceId + " shell settings put global enhanced_4g_mode_enabled 1";
            ADBHelper.executeCommand(enableCmd2);
            System.out.println("✓ Enhanced 4G mode enabled");
            
            // Method 3: MTK specific settings
            String enableCmd3 = "adb -s " + deviceId + " shell setprop persist.vendor.mtk.volte.enable 1";
            ADBHelper.executeCommand(enableCmd3);
            System.out.println("✓ MTK VoLTE property set");
            
            // Method 4: Enable IMS registration
            String enableCmd4 = "adb -s " + deviceId + " shell setprop persist.vendor.radio.volte_state 1";
            ADBHelper.executeCommand(enableCmd4);
            System.out.println("✓ IMS registration state set");
            
            // Method 5: Enable hardware VoLTE flag
            String enableCmd5 = "adb -s " + deviceId + " shell setprop persist.radio.hvolte.enable 1";
            ADBHelper.executeCommand(enableCmd5);
            System.out.println("✓ Hardware VoLTE enabled");
            
            // Wait for settings to apply
            Thread.sleep(3000);
            
            // Verify VoLTE is enabled
            boolean enabled = isVoLTEEnabled(deviceId);
            
            if (enabled) {
                System.out.println("✅ VOLTE ENABLED SUCCESSFULLY");
            } else {
                System.out.println("⚠️ VoLTE enabled but not yet registered");
                System.out.println("💡 IMS may take 10-30 seconds to register");
            }
            
            System.out.println("=".repeat(80) + "\n");
            return true;
            
        } catch (Exception e) {
            System.out.println("❌ VoLTE enable error: " + e.getMessage());
            System.out.println("=".repeat(80) + "\n");
            return false;
        }
    }
    
    /**
     * ✅ DISABLE VOLTE ON DEVICE
     */
    public static boolean disableVoLTE(String deviceId) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📴 DISABLING VOLTE");
        System.out.println("=".repeat(80));
        System.out.println("📱 Device: " + deviceId);
        
        try {
            // Method 1: Disable VoLTE via settings database
            String disableCmd1 = "adb -s " + deviceId + " shell settings put global volte_enable 0";
            ADBHelper.executeCommand(disableCmd1);
            System.out.println("✓ Global VoLTE setting disabled");
            
            // Method 2: Disable enhanced 4G LTE mode
            String disableCmd2 = "adb -s " + deviceId + " shell settings put global enhanced_4g_mode_enabled 0";
            ADBHelper.executeCommand(disableCmd2);
            System.out.println("✓ Enhanced 4G mode disabled");
            
            // Method 3: MTK specific settings
            String disableCmd3 = "adb -s " + deviceId + " shell setprop persist.vendor.mtk.volte.enable 0";
            ADBHelper.executeCommand(disableCmd3);
            System.out.println("✓ MTK VoLTE property cleared");
            
            // Method 4: Disable IMS registration
            String disableCmd4 = "adb -s " + deviceId + " shell setprop persist.vendor.radio.volte_state 0";
            ADBHelper.executeCommand(disableCmd4);
            System.out.println("✓ IMS registration state cleared");
            
            // Method 5: Disable hardware VoLTE flag
            String disableCmd5 = "adb -s " + deviceId + " shell setprop persist.radio.hvolte.enable 0";
            ADBHelper.executeCommand(disableCmd5);
            System.out.println("✓ Hardware VoLTE disabled");
            
            // Wait for settings to apply
            Thread.sleep(3000);
            
            // Verify VoLTE is disabled
            boolean enabled = isVoLTEEnabled(deviceId);
            
            if (!enabled) {
                System.out.println("✅ VOLTE DISABLED SUCCESSFULLY");
            } else {
                System.out.println("⚠️ VoLTE still showing as enabled");
                System.out.println("💡 IMS may take time to deregister");
            }
            
            System.out.println("=".repeat(80) + "\n");
            return true;
            
        } catch (Exception e) {
            System.out.println("❌ VoLTE disable error: " + e.getMessage());
            System.out.println("=".repeat(80) + "\n");
            return false;
        }
    }
    
    /**
     * ✅ CHECK IF VOLTE IS ENABLED
     */
    public static boolean isVoLTEEnabled(String deviceId) {
        try {
            // Check multiple indicators
            String imsData = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell \"dumpsys telephony.registry | grep -E 'IMS.*state: CONNECTED'\""
            );
            
            String settingsCheck = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell settings get global enhanced_4g_mode_enabled"
            ).trim();
            
            String volteCheck = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell getprop persist.vendor.mtk.volte.enable"
            ).trim();
            
            boolean imsConnected = imsData.contains("state: CONNECTED") && imsData.contains("IMS");
            boolean settingsEnabled = "1".equals(settingsCheck);
            boolean propertyEnabled = "1".equals(volteCheck);
            
            return imsConnected || (settingsEnabled && propertyEnabled);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * ✅ GET DETAILED VOLTE STATUS
     */
    public static Map<String, String> getDetailedVoLTEStatus(String deviceId) {
        Map<String, String> status = new HashMap<>();
        
        try {
            System.out.println("📶 Checking VoLTE status for: " + deviceId);
            
            // Get IMS data
            String imsData = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell \"dumpsys telephony.registry | grep -E 'IMS|state: CONNECTED' | grep -i ims\""
            );
            
            // Get settings
            String volteEnabled = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell settings get global enhanced_4g_mode_enabled"
            ).trim();
            
            String networkType = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell getprop gsm.network.type"
            ).trim();
            
            // Parse IMS status
            boolean imsConnected = imsData.contains("state: CONNECTED") && imsData.contains("IMS");
            boolean settingsEnabled = "1".equals(volteEnabled);
            
            status.put("volteEnabled", String.valueOf(imsConnected || settingsEnabled));
            status.put("volteStatus", (imsConnected || settingsEnabled) ? "ENABLED" : "DISABLED");
            status.put("imsRegistration", imsConnected ? "REGISTERED" : "NOT_REGISTERED");
            status.put("networkType", networkType.isEmpty() ? "UNKNOWN" : networkType);
            status.put("callCapability", imsConnected ? "VOLTE_READY" : "LEGACY_VOICE");
            
            if (imsConnected) {
                System.out.println("✅ VoLTE: ACTIVE (IMS registered)");
            } else {
                System.out.println("❌ VoLTE: INACTIVE");
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ Error checking VoLTE: " + e.getMessage());
            status.put("volteEnabled", "false");
            status.put("volteStatus", "UNKNOWN");
        }
        
        return status;
    }
    
    /**
     * ✅ WAIT FOR IMS REGISTRATION
     */
    public static boolean waitForIMSRegistration(String deviceId, int timeoutSeconds) {
        System.out.println("⏳ Waiting for IMS registration (max " + timeoutSeconds + "s)...");
        
        for (int i = 0; i < timeoutSeconds; i++) {
            try {
                if (isVoLTEEnabled(deviceId)) {
                    System.out.println("✅ IMS registered after " + i + " seconds");
                    return true;
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                return false;
            }
        }
        
        System.out.println("⏱️ IMS registration timeout after " + timeoutSeconds + "s");
        return false;
    }
    
    /**
     * ✅ GET VOLTE STATUS FOR BOTH PARTIES
     */
    public static Map<String, Map<String, String>> getVoLTEStatusBothParties(
            String aPartyDeviceId, String bPartyDeviceId) {
        
        Map<String, Map<String, String>> bothStatuses = new HashMap<>();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 CHECKING VOLTE STATUS - BOTH PARTIES");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📱 A-Party: " + aPartyDeviceId);
        Map<String, String> aPartyStatus = getDetailedVoLTEStatus(aPartyDeviceId);
        bothStatuses.put("aParty", aPartyStatus);
        
        if (bPartyDeviceId != null && !bPartyDeviceId.isEmpty()) {
            System.out.println("\n📱 B-Party: " + bPartyDeviceId);
            Map<String, String> bPartyStatus = getDetailedVoLTEStatus(bPartyDeviceId);
            bothStatuses.put("bParty", bPartyStatus);
        } else {
            System.out.println("\n⚠️ B-Party device ID not available");
        }
        
        System.out.println("=".repeat(80) + "\n");
        
        return bothStatuses;
    }
    
    /**
     * ✅ PRINT VOLTE DIAGNOSTICS
     */
    public static void printVoLTEDiagnostics(String deviceId) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📶 VOLTE DIAGNOSTIC REPORT - " + deviceId);
        System.out.println("=".repeat(80));
        
        Map<String, String> status = getDetailedVoLTEStatus(deviceId);
        
        System.out.println("VoLTE Status: " + status.get("volteStatus"));
        System.out.println("IMS Registration: " + status.get("imsRegistration"));
        System.out.println("Network Type: " + status.get("networkType"));
        System.out.println("Call Capability: " + status.get("callCapability"));
        
        System.out.println("=".repeat(80) + "\n");
    }
}