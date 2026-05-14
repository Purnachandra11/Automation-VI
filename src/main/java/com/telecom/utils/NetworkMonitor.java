package com.telecom.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class NetworkMonitor {
    
    public static Map<String, String> getNetworkInfo(String deviceId) {
        Map<String, String> networkInfo = new HashMap<>();
        
        try {
            String networkType = getCurrentNetworkType(deviceId);
            networkInfo.put("networkType", networkType);
            
            String operator = ADBHelper.executeCommand("adb -s " + deviceId + " shell getprop gsm.operator.alpha").trim();
            networkInfo.put("operator", operator.isEmpty() ? "Unknown" : operator);
            
            // Get VoLTE status
            Map<String, String> volteInfo = getVolteStatus(deviceId);
            networkInfo.putAll(volteInfo);
            
            System.out.println("📶 Network Info: " + networkType + " | " + operator);
            
        } catch (Exception e) {
            System.out.println("⚠ Network monitoring error: " + e.getMessage());
            networkInfo.put("networkType", "UNKNOWN");
            networkInfo.put("operator", "Unknown");
        }
        
        return networkInfo;
    }
    
    public static String getCurrentNetworkType(String deviceId) {
        try {
            // Method 1: Check gsm.network.type
            String networkType = ADBHelper.executeCommand("adb -s " + deviceId + " shell getprop gsm.network.type").trim();
            
            if (networkType.contains("NR") || networkType.contains("5G")) return "5G";
            if (networkType.contains("LTE") || networkType.contains("4G")) return "4G";
            if (networkType.contains("HSPA") || networkType.contains("3G")) return "3G";
            if (networkType.contains("EDGE") || networkType.contains("2G")) return "2G";
            
            // Method 2: Check telephony.registry
            String telephonyInfo = ADBHelper.executeCommand("adb -s " + deviceId + " shell dumpsys telephony.registry");
            if (telephonyInfo.contains("NR_NSA") || telephonyInfo.contains("NR")) return "5G";
            if (telephonyInfo.contains("LTE")) return "4G";
            if (telephonyInfo.contains("HSPA") || telephonyInfo.contains("UMTS")) return "3G";
            if (telephonyInfo.contains("EDGE") || telephonyInfo.contains("GPRS")) return "2G";
            
            return networkType.isEmpty() ? "UNKNOWN" : networkType;
            
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    public static Map<String, String> getVolteStatus(String deviceId) {
        Map<String, String> volteInfo = new HashMap<>();
        
        try {
            String volteStatus = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell \"echo '===== VoLTE Status =====; " +
                "echo MTK Vendor VoLTE: $(getprop persist.vendor.mtk.volte.enable); " +
                "echo VoLTE Service State: $(getprop persist.vendor.radio.volte_state); " +
                "echo Hardware VoLTE Flag: $(getprop persist.radio.hvolte.enable); " +
                "echo SIM Indicators: $(getprop vendor.ril.mtk_hvolte_indicator)'\""
            );
            
            boolean volteEnabled = volteStatus.contains("MTK Vendor VoLTE: 1") && 
                                 volteStatus.contains("VoLTE Service State: 1");
            
            volteInfo.put("volteEnabled", String.valueOf(volteEnabled));
            volteInfo.put("volteDetails", volteStatus);
            
        } catch (Exception e) {
            volteInfo.put("volteEnabled", "false");
            volteInfo.put("volteDetails", "Error retrieving VoLTE status");
        }
        
        return volteInfo;
    }
    
    public static boolean waitForStableNetwork(String deviceId, int timeoutSeconds) {
        System.out.println("📶 Checking network type...");
        
        try {
            Map<String, String> networkInfo = getNetworkInfo(deviceId);
            String networkType = networkInfo.get("networkType");
            
            System.out.println(" Network Type: " + networkType);
            return true;
            
        } catch (Exception e) {
            System.out.println("⚠ Network check failed: " + e.getMessage());
            return false;
        }
    }

 // Replace the getAPNInfo method with this updated version
    public static Map<String, String> getAPNInfo(String deviceId) {
        Map<String, String> apnInfo = new HashMap<>();
        StringBuilder apnComment = new StringBuilder();
        
        try {
            // Build the exact ADB command for Vodafone/IDEA APNs
            String command = "adb -s " + deviceId + " shell \"content query --uri content://telephony/carriers --projection name,apn,type,current --where \\\"current=1 AND type LIKE '%default%' AND (name LIKE '%Vodafone%' OR name LIKE '%IDEA%')\\\"\"";
            
            System.out.println("📱 Executing APN query: " + command);
            
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            process.waitFor();
            String result = output.toString();
            
            if (!result.isEmpty() && result.contains("Row:")) {
                // Parse the output
                String rowData = result.substring(result.indexOf("Row:"));
                
                // Extract fields from the exact format
                String name = extractAPNValue(rowData, "name=");
                String apn = extractAPNValue(rowData, "apn=");
                String type = extractAPNValue(rowData, "type=");
                String current = extractAPNValue(rowData, "current=");
                
                // Build APN details string for comment section
                apnComment.append("APN Details: Name=").append(name)
                         .append(", APN=").append(apn)
                         .append(", Type=").append(type)
                         .append(", Current=").append(current);
                
                // Store individual values
                apnInfo.put("apnName", name);
                apnInfo.put("apn", apn);
                apnInfo.put("apnType", type);
                apnInfo.put("current", current);
                apnInfo.put("apnDetails", apnComment.toString());  // Add the combined APN details
                
                System.out.println(" APN Info Retrieved: " + apnComment.toString());
                
            } else {
                // Fallback to preferapn if no Vodafone/IDEA APNs found
                System.out.println("⚠️ No Vodafone/IDEA APN found, trying preferapn as fallback...");
                return getAPNInfoFallback(deviceId);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error getting APN info: " + e.getMessage());
            apnInfo.put("error", e.getMessage());
            apnInfo.put("apnDetails", "Error retrieving APN info");
        }
        
        return apnInfo;
    }

    // Add this new fallback method (place it after getAPNInfo method)
    private static Map<String, String> getAPNInfoFallback(String deviceId) {
        Map<String, String> apnInfo = new HashMap<>();
        StringBuilder apnComment = new StringBuilder();
        
        try {
            String[] cmd = {
                "adb", "-s", deviceId, "shell",
                "content", "query", "--uri", "content://telephony/carriers/preferapn"
            };
            
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            process.waitFor();
            String result = output.toString();
            
            if (!result.isEmpty() && result.contains("Row:")) {
                String rowData = result.substring(result.indexOf("Row:"));
                
                String apnName = extractValue(rowData, "name=");
                String apn = extractValue(rowData, "apn=");
                String apnType = extractValue(rowData, "type=");
                String current = "1"; // preferapn means current
                
                apnComment.append("APN Details: Name=").append(apnName)
                         .append(", APN=").append(apn)
                         .append(", Type=").append(apnType)
                         .append(", Current=").append(current)
                         .append(" (Fallback from preferapn)");
                
                apnInfo.put("apnName", apnName);
                apnInfo.put("apn", apn);
                apnInfo.put("apnType", apnType);
                apnInfo.put("current", current);
                apnInfo.put("apnDetails", apnComment.toString());
                
                System.out.println(" APN Info (Fallback): " + apnComment.toString());
            } else {
                apnInfo.put("apnDetails", "APN: NOT_FOUND");
                System.out.println("⚠️ No APN configuration found");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error in APN fallback: " + e.getMessage());
            apnInfo.put("apnDetails", "Error retrieving APN info");
        }
        
        return apnInfo;
    }

    // Add this new extractor method (place it near other helper methods)
    private static String extractAPNValue(String rowData, String key) {
        try {
            int startIndex = rowData.indexOf(key);
            if (startIndex != -1) {
                startIndex += key.length();
                int endIndex = rowData.indexOf(",", startIndex);
                if (endIndex == -1) endIndex = rowData.length();
                return rowData.substring(startIndex, endIndex).trim();
            }
        } catch (Exception e) {
            // Ignore and return unknown
        }
        return "UNKNOWN";
    }

    // Keep the original extractValue method as is (no changes needed to this)
    private static String extractValue(String rowData, String key) {
        try {
            int startIndex = rowData.indexOf(key);
            if (startIndex != -1) {
                startIndex += key.length();
                int endIndex = rowData.indexOf(",", startIndex);
                if (endIndex == -1) endIndex = rowData.length();
                return rowData.substring(startIndex, endIndex).trim();
            }
        } catch (Exception e) {
            // Ignore and return unknown
        }
        return "UNKNOWN";
    }
}