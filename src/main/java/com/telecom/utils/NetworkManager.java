package com.telecom.utils;

public class NetworkManager {
    
    /**
     *  POINT 1: Dynamic Network Type Handling with proper radio deactivation
     */
    public static boolean setNetworkType(String deviceId, String targetNetworkType) {
        System.out.println("🔡 Changing network type to: " + targetNetworkType);
        
        try {
            String currentNetwork = getCurrentNetworkType(deviceId);
            System.out.println("   Current Network: " + currentNetwork);
            
            // Step 1: Check if downgrading from 5G to 4G - deactivate 5G radio
            if (currentNetwork.equals("5G") && targetNetworkType.equals("4G")) {
                System.out.println("   ⚠️ Downgrading from 5G to 4G - Deactivating 5G radio...");
                deactivate5GRadio(deviceId);
                Thread.sleep(3000);
            }
            
            // Step 2: Check for 3G availability when switching to 3G
            if (targetNetworkType.equals("3G")) {
                System.out.println("   🔍 Checking 3G availability...");
                boolean is3GAvailable = check3GAvailability(deviceId);
                
                if (!is3GAvailable) {
                    System.out.println("   ❌ 3G signal not available - Test will be skipped");
                    return false;
                }
                System.out.println("    3G signal available - Proceeding with network change");
            }
            
            // Step 3: Set the network type
            int networkMode = getNetworkModeValue(targetNetworkType);
            String command = "adb -s " + deviceId + " shell settings put global preferred_network_mode " + networkMode;
            ADBHelper.executeCommand(command);
            
            // Additional commands for MTK devices
            String mtkCommand = "adb -s " + deviceId + " shell setprop persist.vendor.radio.rat_on combine";
            ADBHelper.executeCommand(mtkCommand);
            
            Thread.sleep(5000); // Wait for network switch
            
            // Step 4: Verify network change
            String newNetwork = getCurrentNetworkType(deviceId);
            boolean success = verifyNetworkSwitch(targetNetworkType, newNetwork);
            
            if (success) {
                System.out.println("    Network type changed successfully to: " + newNetwork);
            } else {
                System.out.println("   ⚠️ Network change initiated, verification pending");
            }
            
            return success;
            
        } catch (Exception e) {
            System.out.println("   ❌ Network type change error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deactivate 5G radio when downgrading
     */
    private static void deactivate5GRadio(String deviceId) {
        try {
            // Method 1: Disable NR mode
            String disableNR = "adb -s " + deviceId + " shell setprop persist.vendor.radio.5g_mode_pref 0";
            ADBHelper.executeCommand(disableNR);
            
            // Method 2: Force LTE only mode temporarily
            String forceLTE = "adb -s " + deviceId + " shell settings put global preferred_network_mode 11";
            ADBHelper.executeCommand(forceLTE);
            
            System.out.println("    5G radio deactivated");
            
        } catch (Exception e) {
            System.out.println("   ⚠️ 5G deactivation warning: " + e.getMessage());
        }
    }
    
    /**
     * Check if 3G signal is available
     */
    private static boolean check3GAvailability(String deviceId) {
        try {
            // Temporarily switch to 3G mode to check availability
            String check3G = "adb -s " + deviceId + " shell settings put global preferred_network_mode 2";
            ADBHelper.executeCommand(check3G);
            
            Thread.sleep(5000); // Wait for network registration
            
            // Check signal strength
            String signalInfo = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys telephony.registry | grep mSignalStrength"
            );
            
            // Check if registered on 3G network
            String networkInfo = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys telephony.registry | grep mDataConnectionState"
            );
            
            boolean is3GRegistered = signalInfo.contains("gsm") && 
                                    !signalInfo.contains("99") && // 99 means no signal
                                    networkInfo.contains("2"); // 2 means connected
            
            return is3GRegistered;
            
        } catch (Exception e) {
            System.out.println("   ⚠️ 3G availability check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get current network type
     */
    public static String getCurrentNetworkType(String deviceId) {
        try {
            // Method 1: Check radio technology
            String radioTech = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell getprop gsm.network.type"
            ).trim();
            
            // Method 2: Check telephony registry
            String telephonyInfo = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys telephony.registry"
            );
            
            // Determine network type
            if (radioTech.contains("NR") || telephonyInfo.contains("NR_NSA") || 
                telephonyInfo.contains("NR_SA")) {
                return "5G";
            } else if (radioTech.contains("LTE") || telephonyInfo.contains("LTE")) {
                return "4G";
            } else if (radioTech.contains("HSPA") || radioTech.contains("UMTS") || 
                      radioTech.contains("WCDMA")) {
                return "3G";
            } else if (radioTech.contains("EDGE") || radioTech.contains("GPRS") || 
                      radioTech.contains("GSM")) {
                return "2G";
            }
            
            return "UNKNOWN";
            
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * Get network mode value for ADB command
     */
    private static int getNetworkModeValue(String networkType) {
        switch (networkType.toUpperCase()) {
            case "5G":
            case "AUTO":
                return 33; // NR/LTE/WCDMA/GSM auto
            case "4G":
            case "LTE":
                return 11; // LTE only
            case "3G":
            case "WCDMA":
                return 2; // WCDMA only
            case "2G":
            case "GSM":
                return 1; // GSM only
            default:
                return 33; // Default to auto
        }
    }
    
    /**
     * Verify network switch was successful
     */
    private static boolean verifyNetworkSwitch(String target, String current) {
        if (target.equals("AUTO") || target.equals("5G")) {
            return current.equals("5G") || current.equals("4G");
        }
        return target.equals(current);
    }
    
    /**
     *  POINT 2: Get highest available network for data usage
     */
    public static String getHighestAvailableNetwork(String deviceId) {
        System.out.println("🔍 Detecting highest available network for data usage...");
        
        try {
            // Check for 5G availability
            if (check5GAvailability(deviceId)) {
                System.out.println("    5G available - Using 5G for data usage");
                return "5G";
            }
            
            // Check for 4G availability
            if (check4GAvailability(deviceId)) {
                System.out.println("    4G available - Using 4G for data usage");
                return "4G";
            }
            
            // Fallback to 3G
            System.out.println("   ⚠️ Using 3G for data usage (5G/4G not available)");
            return "3G";
            
        } catch (Exception e) {
            System.out.println("   ❌ Network detection error: " + e.getMessage());
            return "4G"; // Default to 4G
        }
    }
    
    private static boolean check5GAvailability(String deviceId) {
        try {
            String networkInfo = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys telephony.registry"
            );
            return networkInfo.contains("NR") || networkInfo.contains("5G");
        } catch (Exception e) {
            return false;
        }
    }
    
    private static boolean check4GAvailability(String deviceId) {
        try {
            String networkInfo = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell getprop gsm.network.type"
            );
            return networkInfo.contains("LTE") || networkInfo.contains("4G");
        } catch (Exception e) {
            return false;
        }
    }
}
