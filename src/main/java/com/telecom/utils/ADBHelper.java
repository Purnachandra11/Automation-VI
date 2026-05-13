package com.telecom.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ADBHelper {
    
    public static String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            reader.close();
            
            if (exitCode != 0) {
                System.out.println("⚠️ Command exited with code: " + exitCode + " - " + command);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Command execution failed: " + e.getMessage() + " - " + command);
        }
        return output.toString();
    }
    
    public static boolean connectDevice(String deviceIdentifier) {
        System.out.println("🔗 Connecting to device: " + deviceIdentifier);
        
        try {
            if (deviceIdentifier.contains(".")) {
                return setupWirelessConnection(deviceIdentifier);
            } else {
                executeCommand("adb devices");
                return true;
            }
        } catch (Exception e) {
            System.out.println("❌ Device connection failed: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean setupWirelessConnection(String deviceIdentifier) {
        try {
            System.out.println("📶 Setting up wireless connection...");
            
            if (deviceIdentifier.contains(":")) {
                String result = executeCommand("adb connect " + deviceIdentifier);
                boolean connected = result.contains("connected") || result.contains("already");
                if (connected) {
                    System.out.println("✅ Wireless connection established: " + deviceIdentifier);
                } else {
                    System.out.println("❌ Wireless connection failed: " + result);
                }
                return connected;
            }
            return false;
            
        } catch (Exception e) {
            System.out.println("❌ Wireless setup failed: " + e.getMessage());
            return false;
        }
    }
    
    public static List<String> getConnectedDevices() {
        List<String> devices = new ArrayList<>();
        try {
            String result = executeCommand("adb devices");
            String[] lines = result.split("\n");
            
            for (String line : lines) {
                if (line.contains("\tdevice") || line.contains("\tconnected")) {
                    String deviceId = line.split("\t")[0];
                    if (!deviceId.equals("List") && !deviceId.isEmpty()) {
                        devices.add(deviceId);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error getting devices: " + e.getMessage());
        }
        return devices;
    }
    
    public static String getDeviceModel(String deviceId) {
        try {
            return executeCommand("adb -s " + deviceId + " shell getprop ro.product.model").trim();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    public static String getAndroidVersion(String deviceId) {
        try {
            return executeCommand("adb -s " + deviceId + " shell getprop ro.build.version.release").trim();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    public static void grantPermissions(String deviceId) {
        try {
            String[] permissions = {
                "android.permission.CALL_PHONE",
                "android.permission.READ_PHONE_STATE", 
                "android.permission.SEND_SMS",
                "android.permission.READ_SMS",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
            };
            
            for (String permission : permissions) {
                executeCommand("adb -s " + deviceId + " shell pm grant com.android.dialer " + permission);
                executeCommand("adb -s " + deviceId + " shell pm grant com.google.android.apps.messaging " + permission);
                Thread.sleep(100); // Small delay between commands
            }
            System.out.println("✅ Permissions granted successfully");
        } catch (Exception e) {
            System.out.println("⚠️ Some permissions couldn't be granted: " + e.getMessage());
        }
    }
    
    public static boolean isDeviceConnected(String deviceId) {
        List<String> devices = getConnectedDevices();
        return devices.contains(deviceId);
    }
    
    public static void restartADBServer() {
        try {
            executeCommand("adb kill-server");
            Thread.sleep(2000);
            executeCommand("adb start-server");
            Thread.sleep(3000);
            System.out.println("✅ ADB server restarted");
        } catch (Exception e) {
            System.out.println("❌ ADB restart failed: " + e.getMessage());
        }
    }
    
    public static String getConnectionType(String deviceId) {
        return deviceId.contains(".") ? "WIRELESS" : "USB";
    }
 // Add these methods to ADBHelper.java

    /**
     * ✅ NEW: Install auto-answer APK on device
     */
    public static boolean installAutoAnswerAPK(String deviceId, String apkPath) {
        try {
            System.out.println("📦 Installing auto-answer APK on device: " + deviceId);
            
            String installCmd = "adb -s " + deviceId + " install -r " + apkPath;
            String result = executeCommand(installCmd);
            
            boolean success = result.contains("Success") || result.contains("already installed");
            System.out.println("✅ Auto-answer APK installed: " + success);
            return success;
            
        } catch (Exception e) {
            System.out.println("❌ Auto-answer APK installation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ NEW: Start auto-answer service on device
     */
    public static boolean startAutoAnswerService(String deviceId, String expectedCaller) {
        try {
            System.out.println("🤖 Starting auto-answer service on device: " + deviceId);
            
            // Set expected caller number
            String setCallerCmd = "adb -s " + deviceId + " shell am broadcast -a com.telecom.SET_EXPECTED_CALLER --es caller \"" + expectedCaller + "\"";
            executeCommand(setCallerCmd);
            
            // Start the auto-answer service
            String startServiceCmd = "adb -s " + deviceId + " shell am start-service -n com.telecom/.utils.AutoAnswerService";
            String result = executeCommand(startServiceCmd);
            
            boolean success = result.contains("Starting") || result.contains("Success") || result.contains("Service started");
            System.out.println("✅ Auto-answer service started: " + success);
            return success;
            
        } catch (Exception e) {
            System.out.println("❌ Auto-answer service start failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ NEW: Stop auto-answer service on device
     */
    public static boolean stopAutoAnswerService(String deviceId) {
        try {
            System.out.println("🛑 Stopping auto-answer service on device: " + deviceId);
            
            String stopCmd = "adb -s " + deviceId + " shell am stop-service com.telecom/.utils.AutoAnswerService";
            String result = executeCommand(stopCmd);
            
            boolean success = result.contains("Stopping") || result.contains("Success") || result.isEmpty();
            System.out.println("✅ Auto-answer service stopped: " + success);
            return success;
            
        } catch (Exception e) {
            System.out.println("⚠️ Auto-answer service stop failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * ✅ IMPROVED: Get accurate mobile data usage with better methods
     */
    public static Map<String, String> getDetailedMobileDataUsage(String deviceId) {
        Map<String, String> dataUsage = new HashMap<>();
        
        try {
            System.out.println("📊 Getting accurate mobile data usage for device: " + deviceId);
            
            // Method 1: Use dumpsys netstats with proper mobile interface filtering
            String netstatsData = getEnhancedNetstatsData(deviceId);
            if (!netstatsData.equals("Unknown") && !netstatsData.equals("0.00 MB")) {
                dataUsage.put("mobile_data_used", netstatsData);
                dataUsage.put("source", "netstats");
                System.out.println("✅ Got data usage from netstats: " + netstatsData);
                return dataUsage;
            }
            
            // Method 2: Use dumpsys connectivity service
            String connectivityData = getConnectivityServiceData(deviceId);
            if (!connectivityData.equals("Unknown") && !connectivityData.equals("0.00 MB")) {
                dataUsage.put("mobile_data_used", connectivityData);
                dataUsage.put("source", "connectivity");
                System.out.println("✅ Got data usage from connectivity: " + connectivityData);
                return dataUsage;
            }
            
            // Method 3: Fallback to package-specific data usage
            String packageData = getPackageDataUsage(deviceId);
            if (!packageData.equals("Unknown")) {
                dataUsage.put("mobile_data_used", packageData);
                dataUsage.put("source", "package");
                System.out.println("✅ Got data usage from package stats: " + packageData);
                return dataUsage;
            }
            
            dataUsage.put("mobile_data_used", "Unknown");
            dataUsage.put("source", "fallback");
            
        } catch (Exception e) {
            System.out.println("❌ Error getting mobile data usage: " + e.getMessage());
            dataUsage.put("mobile_data_used", "Unknown");
            dataUsage.put("source", "error");
        }
        
        return dataUsage;
    }
    public static Map<String, String> getCommandLineDeviceIds() {
        Map<String, String> devices = new HashMap<>();
        
        devices.put("aPartyDevice", System.getProperty("aPartyDevice", "LFMVIBEMW8HUR4XK"));
        devices.put("aPartyNumber", System.getProperty("aPartyNumber", "8696904544"));
        devices.put("bPartyDevice", System.getProperty("bPartyDevice"));
        devices.put("bPartyNumber", System.getProperty("bPartyNumber", "9773328866"));
        
        return devices;
    }
    
    /**
     * ✅ NEW: Find device by characteristics (useful for auto-detection)
     */
    public static String findDeviceByModelPattern(String pattern) {
        List<String> devices = getConnectedDevices();
        
        for (String deviceId : devices) {
            String model = getDeviceModel(deviceId);
            if (model.toLowerCase().contains(pattern.toLowerCase())) {
                return deviceId;
            }
        }
        return null;
    }
    /**
     * ✅ IMPROVED: Get netstats data with mobile-only filtering
     */
    private static String getEnhancedNetstatsData(String deviceId) {
        try {
            // First try: grep for mobile interfaces only
            String netstatsCommand = "adb -s " + deviceId + " shell dumpsys netstats | grep -E \"(MOBILE|rmnet|iface=.*mobile)\"";
            String netstatsOutput = executeCommand(netstatsCommand);
            
            if (netstatsOutput != null && !netstatsOutput.isEmpty()) {
                long totalBytes = 0;
                
                // Parse rxBytes and txBytes from mobile interfaces only
                Pattern rxPattern = Pattern.compile("rxBytes=(\\d+)");
                Pattern txPattern = Pattern.compile("txBytes=(\\d+)");
                Pattern rbPattern = Pattern.compile("rbBytes=(\\d+)");
                
                Matcher rxMatcher = rxPattern.matcher(netstatsOutput);
                Matcher txMatcher = txPattern.matcher(netstatsOutput);
                Matcher rbMatcher = rbPattern.matcher(netstatsOutput);
                
                while (rxMatcher.find()) {
                    totalBytes += Long.parseLong(rxMatcher.group(1));
                }
                while (txMatcher.find()) {
                    totalBytes += Long.parseLong(txMatcher.group(1));
                }
                while (rbMatcher.find()) {
                    totalBytes += Long.parseLong(rbMatcher.group(1));
                }
                
                if (totalBytes > 0) {
                    return formatBytes(totalBytes);
                }
            }
            
            // Second try: Full netstats detail parsing if first method fails
            String detailCommand = "adb -s " + deviceId + " shell dumpsys netstats detail";
            String detailOutput = executeCommand(detailCommand);
            
            if (detailOutput != null && !detailOutput.isEmpty()) {
                long totalBytes = 0;
                
                // Look for mobile interfaces and their data usage
                String[] lines = detailOutput.split("\n");
                boolean inMobileSection = false;
                
                for (String line : lines) {
                    line = line.trim();
                    
                    // Look for mobile network sections
                    if (line.contains("MOBILE") || line.contains("mobile") || 
                        line.contains("iface=rmnet") || line.contains("NetworkStats:")) {
                        inMobileSection = true;
                    }
                    
                    if (inMobileSection) {
                        // Extract rxBytes and txBytes
                        Pattern rxPattern = Pattern.compile("rxBytes=(\\d+)");
                        Pattern txPattern = Pattern.compile("txBytes=(\\d+)");
                        
                        Matcher rxMatcher = rxPattern.matcher(line);
                        Matcher txMatcher = txPattern.matcher(line);
                        
                        if (rxMatcher.find()) {
                            totalBytes += Long.parseLong(rxMatcher.group(1));
                        }
                        if (txMatcher.find()) {
                            totalBytes += Long.parseLong(txMatcher.group(1));
                        }
                    }
                    
                    // Reset section flag when we move to next section
                    if (line.contains("---") || line.contains("=========")) {
                        inMobileSection = false;
                    }
                }
                
                if (totalBytes > 0) {
                    return formatBytes(totalBytes);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Enhanced netstats parsing failed: " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * ✅ NEW: Get data from connectivity service
     */
    private static String getConnectivityServiceData(String deviceId) {
        try {
            String command = "adb -s " + deviceId + " shell dumpsys connectivity | grep -A 10 -B 10 \"Mobile\"";
            String output = executeCommand(command);
            
            if (output != null && output.contains("Mobile")) {
                long totalBytes = 0;
                
                // Look for byte counts in mobile data sections
                Pattern pattern = Pattern.compile("(\\d+)\\s*bytes");
                Matcher matcher = pattern.matcher(output);
                
                while (matcher.find()) {
                    totalBytes += Long.parseLong(matcher.group(1));
                }
                
                if (totalBytes > 0) {
                    return formatBytes(totalBytes);
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return "Unknown";
    }

    /**
     * ✅ NEW: Get package-specific data usage for browsers
     */
    private static String getPackageDataUsage(String deviceId) {
        try {
            // Get data usage for common browser packages
            String[] browserPackages = {
                "com.android.chrome",
                "com.chrome.beta", 
                "org.mozilla.firefox",
                "com.sec.android.app.sbrowser"
            };
            
            long totalBytes = 0;
            
            for (String pkg : browserPackages) {
                String command = "adb -s " + deviceId + " shell dumpsys package " + pkg + " | grep -E \"(dataSize|mobileData)\"";
                String output = executeCommand(command);
                
                if (output != null) {
                    Pattern pattern = Pattern.compile("(\\d+)");
                    Matcher matcher = pattern.matcher(output);
                    
                    while (matcher.find()) {
                        totalBytes += Long.parseLong(matcher.group(1));
                    }
                }
            }
            
            if (totalBytes > 0) {
                return formatBytes(totalBytes);
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return "Unknown";
    }
    
    /**
     * ✅ NEW: Reset data usage statistics before test
     */
    public static boolean resetDataUsageStats(String deviceId) {
        try {
            System.out.println("🔄 Resetting data usage statistics...");
            
            // Try multiple methods to reset stats
            String[] resetCommands = {
                "adb -s " + deviceId + " shell ip -s link",
                "adb -s " + deviceId + " shell tc qdisc del dev rmnet0 root",
                "adb -s " + deviceId + " shell ndc bandwidth clear",
                "adb -s " + deviceId + " shell dumpsys netstats --reset"
            };
            
            for (String command : resetCommands) {
                try {
                    executeCommand(command);
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Ignore individual command failures
                }
            }
            
            System.out.println("✅ Data usage statistics reset attempted");
            return true;
            
        } catch (Exception e) {
            System.out.println("⚠️ Data usage reset failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * ✅ Format bytes to human readable format
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * ✅ NEW: Get battery level for device
     */
    public static String getBatteryLevel(String deviceId) {
        try {
            String batteryCommand = "adb -s " + deviceId + " shell dumpsys battery | grep level";
            String output = executeCommand(batteryCommand);
            
            if (output.contains("level:")) {
                String level = output.split(":")[1].trim();
                return level + "%";
            }
            
            // Alternative method
            String altCommand = "adb -s " + deviceId + " shell cat /sys/class/power_supply/battery/capacity";
            String altOutput = executeCommand(altCommand).trim();
            
            if (!altOutput.isEmpty()) {
                return altOutput + "%";
            }
            
            return "Unknown";
            
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * ✅ ENHANCED: Get network type with more details
     */
    public static String getNetworkType(String deviceId) {
        try {
            // Multiple methods to get network type
            String method1 = executeCommand("adb -s " + deviceId + " shell getprop gsm.network.type").trim();
            String method2 = executeCommand("adb -s " + deviceId + " shell dumpsys telephony.registry | grep mDataConnectionTechnology").trim();
            String method3 = executeCommand("adb -s " + deviceId + " shell dumpsys telephony.registry | grep mServiceState").trim();
            
            if (method1.contains("NR") || method2.contains("NR") || method3.contains("NR")) return "5G";
            if (method1.contains("LTE") || method2.contains("LTE") || method3.contains("LTE")) return "4G";
            if (method1.contains("HSPA") || method2.contains("HSPA") || method3.contains("HSPA")) return "3G";
            if (method1.contains("EDGE") || method2.contains("EDGE") || method3.contains("EDGE")) return "2G";
            if (method1.contains("GPRS") || method2.contains("GPRS") || method3.contains("GPRS")) return "2G";
            
            return method1.isEmpty() ? "UNKNOWN" : method1;
            
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * ✅ NEW: Get carrier information
     */
    public static String getCarrierName(String deviceId) {
        try {
            String carrier = executeCommand("adb -s " + deviceId + " shell getprop gsm.operator.alpha").trim();
            return carrier.isEmpty() ? "UNKNOWN" : carrier;
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * ✅ NEW: Check if mobile data is enabled
     */
    public static boolean isMobileDataEnabled(String deviceId) {
        try {
            String result = executeCommand("adb -s " + deviceId + " shell settings get global mobile_data").trim();
            return "1".equals(result);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * ✅ NEW: Reset data usage statistics
     */
    public static boolean resetDataUsage(String deviceId) {
        try {
            String result = executeCommand("adb -s " + deviceId + " shell pm clear com.android.providers.telephony");
            System.out.println("📊 Data usage statistics reset: " + result);
            return true;
        } catch (Exception e) {
            System.out.println("⚠️ Could not reset data usage: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * ✅ NEW: Get current data state
     */
    public static String getDataState(String deviceId) {
        try {
            String state = executeCommand("adb -s " + deviceId + " shell dumpsys telephony.registry | grep mDataConnectionState").trim();
            if (state.contains("=")) {
                return state.split("=")[1].trim();
            }
            return "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * ✅ NEW: Force mobile data connection
     */
    public static boolean forceMobileDataConnection(String deviceId) {
        try {
            // Enable mobile data
            executeCommand("adb -s " + deviceId + " shell svc data enable");
            Thread.sleep(2000);
            
            // Check if mobile data is enabled
            return isMobileDataEnabled(deviceId);
        } catch (Exception e) {
            System.out.println("⚠️ Could not force mobile data connection: " + e.getMessage());
            return false;
        }
    }
    /**
     * ✅ NEW: Check if wget/curl are available on device
     */
    public static boolean isDownloadToolAvailable(String deviceId, String tool) {
        try {
            String checkCommand = "adb -s " + deviceId + " shell \"which " + tool + " || echo 'not_found'\"";
            String result = executeCommand(checkCommand).trim();
            return !result.equals("not_found") && !result.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ✅ NEW: Install download tools if missing
     */
    public static boolean installDownloadTools(String deviceId) {
        try {
            System.out.println("🔧 Installing download tools...");
            
            // Try to install wget/curl via busybox or package manager
            String[] installCommands = {
                "adb -s " + deviceId + " shell \"pm install -r /system/app/Busybox/Busybox.apk\"",
                "adb -s " + deviceId + " shell \"pkg install wget -y\"",
                "adb -s " + deviceId + " shell \"pkg install curl -y\""
            };
            
            for (String cmd : installCommands) {
                try {
                    executeCommand(cmd);
                    Thread.sleep(1000);
                } catch (Exception e) {
                    // Continue trying other methods
                }
            }
            
            // Check what's available
            boolean wgetAvailable = isDownloadToolAvailable(deviceId, "wget");
            boolean curlAvailable = isDownloadToolAvailable(deviceId, "curl");
            
            System.out.println("📋 Tools available - wget: " + wgetAvailable + ", curl: " + curlAvailable);
            return wgetAvailable || curlAvailable;
            
        } catch (Exception e) {
            System.out.println("⚠️ Tool installation failed: " + e.getMessage());
            return false;
        }
    }
}