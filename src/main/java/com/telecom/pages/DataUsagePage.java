package com.telecom.pages;

import com.telecom.utils.ADBHelper;
import io.appium.java_client.android.AndroidDriver;
import java.util.HashMap;
import java.util.Map;
import com.telecom.utils.ProgressReporter;  

/**
 * Measures data accurately from /proc/net/dev
 */
public class DataUsagePage {
    @SuppressWarnings("unused")
    private AndroidDriver driver;
    
    public DataUsagePage(AndroidDriver driver) {
        this.driver = driver;
    }
    
    public Map<String, Object> executeDataUsageScenario(String scenario, double targetGB, 
                                                        int durationMin, String apps, String deviceId) {
        Map<String, Object> results = new HashMap<>();
        boolean wifiWasDisabled = false;
        
        try {
            System.out.println("🌐 Executing: " + scenario);
            System.out.println("   Target: " + targetGB + " GB in " + durationMin + " minutes");
            
            String downloadUrl = getDownloadUrlForTarget(targetGB);
            int targetMB = (int)(targetGB * 1024);
            
            // Disable WiFi
            // wifiWasDisabled = disableWiFi(deviceId);
            
            // Execute test
            results = executeTestFlow(deviceId, downloadUrl, targetMB, durationMin, targetGB);
            
            return results;
            
        } catch (Exception e) {
            System.out.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
            
            results.put("success", false);
            results.put("consumedBytes", 0L);
            results.put("consumedMB", 0.0);
            results.put("consumedGB", 0.0);
            results.put("status", "FAILED");
            return results;
            
        } finally {
            if (wifiWasDisabled) {
                enableWiFi(deviceId);
            }
        }
    }
    
    /**
     * Debug: Show all network interfaces on device
     */
    private void debugShowAllInterfaces(String deviceId) {
        try {
            System.out.println("\n🔍 DEBUG: All network interfaces on device:");
            System.out.println("─".repeat(80));
            
            String command = "adb -s " + deviceId + " shell cat /proc/net/dev";
            String output = ADBHelper.executeCommand(command);
            
            if (output == null || output.isEmpty()) {
                System.out.println("   ⚠️  Could not read /proc/net/dev");
                return;
            }
            
            for (String line : output.split("\n")) {
                if (!line.contains(":")) continue;
                
                String iface = line.split(":")[0].trim();
                
                try {
                    String data = line.substring(line.indexOf(":") + 1).trim();
                    String[] parts = data.split("\\s+");
                    
                    if (parts.length >= 9) {
                        long rx = Long.parseLong(parts[0]);
                        long tx = Long.parseLong(parts[8]);
                        long total = rx + tx;
                        
                        System.out.println(String.format("   %-20s RX: %-12s TX: %-12s Total: %s",
                            iface, formatBytes(rx), formatBytes(tx), formatBytes(total)));
                    }
                } catch (Exception e) {
                    // Skip invalid lines
                }
            }
            System.out.println("─".repeat(80));
            
        } catch (Exception e) {
            System.out.println("   ⚠️  Debug failed: " + e.getMessage());
        }
    }
    
    /**
     *  ULTRA-SIMPLE: Just download via Chrome and measure
     */
    private Map<String, Object> executeTestFlow(String deviceId, String downloadUrl, 
                                                int targetMB, int durationMin, double targetGB) {
        Map<String, Object> results = new HashMap<>();
        String filename = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1);
        
        try {
            // STEP 1: Get initial data usage
            System.out.println("\n📊 STEP 1: Getting initial data usage");
            
            // Debug: Show all interfaces first
            debugShowAllInterfaces(deviceId);
            
            String activeInterface = getActiveCcmniInterface(deviceId);
            long initialRx = getRxBytes(deviceId, activeInterface);
            long initialTx = getTxBytes(deviceId, activeInterface);
            
            System.out.println("   Initial RX: " + formatBytes(initialRx));
            System.out.println("   Initial TX: " + formatBytes(initialTx));
            
            // STEP 2: Clean up old downloads
            System.out.println("\n🗑️  STEP 2: Cleaning old downloads");
            ADBHelper.executeCommand("adb -s " + deviceId + " shell rm -f /storage/emulated/0/Download/" + filename);
            ADBHelper.executeCommand("adb -s " + deviceId + " shell am force-stop com.android.chrome");
            Thread.sleep(1000);
            
            // STEP 3: Start download
            System.out.println("\n📥 STEP 3: Starting download");
            System.out.println("   URL: " + downloadUrl);
            System.out.println("   Duration: " + durationMin + " minutes");
            
            String downloadIntent = "adb -s " + deviceId + 
                " shell am start -a android.intent.action.VIEW -d \"" + downloadUrl + "\"";
            ADBHelper.executeCommand(downloadIntent);
            Thread.sleep(3000); // Let download start
            
            // STEP 4: Monitor for specified duration
            System.out.println("\n⏱️  STEP 4: Monitoring for " + durationMin + " minutes");
            monitorProgress(deviceId, activeInterface, initialRx, durationMin);
            
            // STEP 5: Stop download
            System.out.println("\n⏹️  STEP 5: Stopping download");
            ADBHelper.executeCommand("adb -s " + deviceId + " shell am force-stop com.android.chrome");
            Thread.sleep(2000);
            
            // STEP 6: Clean up file
            System.out.println("\n🗑️  STEP 6: Cleaning up");
            ADBHelper.executeCommand("adb -s " + deviceId + " shell rm -f /storage/emulated/0/Download/" + filename);
            
            // STEP 7: Get final data usage
            System.out.println("\n📊 STEP 7: Getting final data usage");
            Thread.sleep(2000); // Let stats settle
            
            long finalRx = getRxBytes(deviceId, activeInterface);
            long finalTx = getTxBytes(deviceId, activeInterface);
            
            System.out.println("   Final RX: " + formatBytes(finalRx));
            System.out.println("   Final TX: " + formatBytes(finalTx));
            
            // Calculate consumption
            long consumedRx = finalRx - initialRx;
            long consumedTx = finalTx - initialTx;
            long totalConsumed = consumedRx + consumedTx;
            
            double consumedMB = totalConsumed / (1024.0 * 1024.0);
            double consumedGB = consumedMB / 1024.0;
            double achievementPercent = (consumedMB / targetMB) * 100.0;
            
            String status = totalConsumed >= (1024 * 1024) ? "SUCCESS" : "FAILED";
            
            // Print summary
            System.out.println("\n" + "=".repeat(80));
            System.out.println("📊 FINAL REPORT");
            System.out.println("=".repeat(80));
            System.out.println("   Downloaded:    " + formatBytes(consumedRx));
            System.out.println("   Uploaded:      " + formatBytes(consumedTx));
            System.out.println("   Total:         " + formatBytes(totalConsumed));
            System.out.println("   Target:        " + targetMB + " MB");
            System.out.println("   Achievement:   " + String.format("%.1f%%", achievementPercent));
            System.out.println("   Status:        " + (status.equals("SUCCESS") ? " SUCCESS" : "❌ FAILED"));
            System.out.println("=".repeat(80));
            
            // Return results
            results.put("success", totalConsumed > 0);
            results.put("consumedBytes", totalConsumed);
            results.put("consumedMB", consumedMB);
            results.put("consumedGB", consumedGB);
            results.put("consumedRxMB", consumedRx / (1024.0 * 1024.0));
            results.put("consumedTxMB", consumedTx / (1024.0 * 1024.0));
            results.put("initialRxBytes", initialRx);
            results.put("initialTxBytes", initialTx);
            results.put("finalRxBytes", finalRx);
            results.put("finalTxBytes", finalTx);
            results.put("targetMB", targetMB);
            results.put("targetGB", targetGB);
            results.put("achievementPercent", achievementPercent);
            results.put("targetAchieved", achievementPercent >= 5.0);
            results.put("status", status);
            
            return results;
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
            
            results.put("success", false);
            results.put("consumedBytes", 0L);
            results.put("consumedMB", 0.0);
            results.put("consumedGB", 0.0);
            results.put("status", "FAILED");
            return results;
        }
    }
    
 // Add this method to your existing DataUsagePage.java
 // Replace the existing monitorProgress method with this enhanced version

 private void monitorProgress(String deviceId, String activeInterface, long startRx, int durationMin) {
     try {
         int totalSec = durationMin * 60;
         int checkInterval = 10; // Check every 10 seconds
         int checks = totalSec / checkInterval;
         
         for (int i = 1; i <= checks; i++) {
             Thread.sleep(checkInterval * 1000L);
             
             long currentRx = getRxBytes(deviceId, activeInterface);
             long downloaded = currentRx - startRx;
             double downloadedMB = downloaded / (1024.0 * 1024.0);
             
             int elapsed = i * checkInterval;
             double progress = (elapsed * 100.0) / totalSec;
             
             // 🔥 NEW: Send progress to WebSocket server
             ProgressReporter.reportDataProgress(
                 deviceId, 
                 "data", 
                 elapsed, 
                 totalSec, 
                 downloadedMB, 
                 activeInterface
             );
             
             // Console output (your existing format)
             System.out.println(String.format(
                 "   [%s] %d/%d sec | Downloaded: %s | %.0f%%",
                 createProgressBar(progress, 20),
                 elapsed,
                 totalSec,
                 formatBytes(downloaded),
                 progress
             ));
         }
         
         // Report completion
         ProgressReporter.reportTestComplete(
             deviceId, 
             "data", 
             true, 
             "Data usage test completed successfully"
         );
         
     } catch (Exception e) {
         System.out.println("   ⚠️ Monitoring error: " + e.getMessage());
         ProgressReporter.reportTestComplete(
             deviceId, 
             "data", 
             false, 
             "Monitoring failed: " + e.getMessage()
         );
     }
 }
    /**
     * Get active mobile data interface - works on ALL Android devices
     */
    private String getActiveCcmniInterface(String deviceId) {
        try {
            // Get all network interfaces
            String command = "adb -s " + deviceId + " shell cat /proc/net/dev";
            String output = ADBHelper.executeCommand(command);
            
            if (output == null || output.trim().isEmpty()) {
                System.out.println("   ⚠️  Could not read /proc/net/dev");
                return "rmnet_data0"; // Common fallback
            }
            
            System.out.println("\n📡 Detecting mobile data interface...");
            
            // Common mobile data interface patterns (in priority order)
            String[] patterns = {
                "rmnet_data",  // Qualcomm (most common)
                "rmnet",       // Qualcomm variant
                "ccmni",       // MediaTek
                "seth_",       // Samsung Exynos
                "v4-rmnet",    // Some Qualcomm devices
                "clat4"        // IPv4 over IPv6
            };
            
            String activeInterface = null;
            long maxBytes = 0;
            
            // Find interfaces matching patterns with actual traffic
            for (String line : output.split("\n")) {
                if (!line.contains(":")) continue;
                
                String iface = line.split(":")[0].trim();
                
                // Check if interface matches any pattern
                boolean matches = false;
                for (String pattern : patterns) {
                    if (iface.startsWith(pattern)) {
                        matches = true;
                        break;
                    }
                }
                
                if (!matches) continue;
                
                // Parse RX+TX bytes
                try {
                    String data = line.substring(line.indexOf(":") + 1).trim();
                    String[] parts = data.split("\\s+");
                    
                    if (parts.length >= 9) {
                        long rxBytes = Long.parseLong(parts[0]);
                        long txBytes = Long.parseLong(parts[8]);
                        long totalBytes = rxBytes + txBytes;
                        
                        System.out.println("   Found: " + iface + " - " + formatBytes(totalBytes) + 
                                         " (RX: " + formatBytes(rxBytes) + ", TX: " + formatBytes(txBytes) + ")");
                        
                        // Select interface with most traffic
                        if (totalBytes > maxBytes) {
                            maxBytes = totalBytes;
                            activeInterface = iface;
                        }
                    }
                } catch (Exception e) {
                    // Skip invalid lines
                }
            }
            
            // If no interface found, try to find ANY interface with traffic
            if (activeInterface == null) {
                System.out.println("   ⚠️  No standard mobile interface found, checking all interfaces...");
                
                for (String line : output.split("\n")) {
                    if (!line.contains(":")) continue;
                    
                    String iface = line.split(":")[0].trim();
                    
                    // Skip loopback and wifi
                    if (iface.equals("lo") || iface.startsWith("wlan") || iface.startsWith("wifi")) {
                        continue;
                    }
                    
                    try {
                        String data = line.substring(line.indexOf(":") + 1).trim();
                        String[] parts = data.split("\\s+");
                        
                        if (parts.length >= 9) {
                            long rxBytes = Long.parseLong(parts[0]);
                            long txBytes = Long.parseLong(parts[8]);
                            long totalBytes = rxBytes + txBytes;
                            
                            if (totalBytes > 1024 * 1024) { // At least 1MB traffic
                                System.out.println("   Found active: " + iface + " - " + formatBytes(totalBytes));
                                
                                if (totalBytes > maxBytes) {
                                    maxBytes = totalBytes;
                                    activeInterface = iface;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip
                    }
                }
            }
            
            if (activeInterface != null) {
                System.out.println("    Selected interface: " + activeInterface + " (" + formatBytes(maxBytes) + ")");
                return activeInterface;
            }
            
            // Last resort fallback
            System.out.println("   ⚠️  No active interface found, using rmnet_data0");
            return "rmnet_data0";
            
        } catch (Exception e) {
            System.out.println("   ⚠️  Interface detection failed: " + e.getMessage());
            return "rmnet_data0";
        }
    }
    
    /**
     * Get RX bytes for interface
     */
    private long getRxBytes(String deviceId, String interfaceName) {
        try {
            // Read entire /proc/net/dev and parse locally
            String command = "adb -s " + deviceId + " shell cat /proc/net/dev";
            String output = ADBHelper.executeCommand(command);
            
            if (output == null || output.isEmpty()) return 0;
            
            // Find the interface line
            for (String line : output.split("\n")) {
                if (line.trim().startsWith(interfaceName + ":")) {
                    String data = line.substring(line.indexOf(":") + 1).trim();
                    String[] fields = data.split("\\s+");
                    return fields.length >= 1 ? Long.parseLong(fields[0]) : 0;
                }
            }
            
            return 0;
            
        } catch (Exception e) {
            System.out.println("   ⚠️  Error reading RX bytes: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get TX bytes for interface
     */
    private long getTxBytes(String deviceId, String interfaceName) {
        try {
            // Read entire /proc/net/dev and parse locally
            String command = "adb -s " + deviceId + " shell cat /proc/net/dev";
            String output = ADBHelper.executeCommand(command);
            
            if (output == null || output.isEmpty()) return 0;
            
            // Find the interface line
            for (String line : output.split("\n")) {
                if (line.trim().startsWith(interfaceName + ":")) {
                    String data = line.substring(line.indexOf(":") + 1).trim();
                    String[] fields = data.split("\\s+");
                    return fields.length >= 9 ? Long.parseLong(fields[8]) : 0;
                }
            }
            
            return 0;
            
        } catch (Exception e) {
            System.out.println("   ⚠️  Error reading TX bytes: " + e.getMessage());
            return 0;
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private String getDownloadUrlForTarget(double targetGB) {
        if (targetGB >= 10) return "http://speedtest.tele2.net/10GB.zip";
        if (targetGB >= 5) return "http://speedtest.tele2.net/5GB.zip";
        if (targetGB >= 1) return "http://speedtest.tele2.net/1GB.zip";
        if (targetGB >= 0.5) return "http://speedtest.tele2.net/500MB.zip";
        if (targetGB >= 0.1) return "http://speedtest.tele2.net/100MB.zip";
        return "http://speedtest.tele2.net/10MB.zip";
    }
    
    // private boolean disableWiFi(String deviceId) {
    //     try {
    //         System.out.println("\n📶 Disabling WiFi...");
    //         ADBHelper.executeCommand("adb -s " + deviceId + " shell svc wifi disable");
    //         Thread.sleep(3000);
            
    //         String dataState = ADBHelper.executeCommand("adb -s " + deviceId + 
    //             " shell settings get global mobile_data");
    //         if (dataState.trim().equals("0")) {
    //             ADBHelper.executeCommand("adb -s " + deviceId + " shell svc data enable");
    //             Thread.sleep(2000);
    //         }
            
    //         System.out.println("    WiFi disabled");
    //         return true;
    //     } catch (Exception e) {
    //         return false;
    //     }
    // }
    
    private void enableWiFi(String deviceId) {
        try {
            System.out.println("\n📶 Re-enabling WiFi...");
            ADBHelper.executeCommand("adb -s " + deviceId + " shell svc wifi enable");
            Thread.sleep(3000);
            System.out.println("    WiFi enabled");
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private String createProgressBar(double progress, int length) {
        int filled = Math.min((int)((progress / 100.0) * length), length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }
}