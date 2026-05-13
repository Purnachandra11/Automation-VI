package com.telecom.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ✅ FIXED DEVICE MANAGER
 * Fixed auto-answer availability detection
 */
public class DeviceManager {
    private static Map<String, String> deviceMap = new HashMap<>();
    private static Map<String, ImprovedADBAutoAnswer> autoAnswerServices = new HashMap<>();
    private static Set<String> autoAnswerNumbers = new HashSet<>();  // ✅ CHANGED: Only store phone numbers
    
    /**
     * Initialize devices with parameters
     */
    public static void initializeDevices(String aPartyDeviceId, String aPartyNumber, 
            String bPartyDeviceId, String bPartyNumber) {
		deviceMap.clear();
		autoAnswerNumbers.clear();
		stopAllAutoAnswerServices();
		
		System.out.println("\n" + "=".repeat(80));
		System.out.println("📱 INITIALIZING DEVICE MANAGER");
		System.out.println("=".repeat(80));
		
		// Configure A-party
		if (aPartyDeviceId != null && aPartyNumber != null) {
		deviceMap.put(aPartyNumber, aPartyDeviceId);
		autoAnswerNumbers.add(aPartyNumber);  // ✅ CRITICAL FIX: Add A-Party too!
		System.out.println("✅ A-Party: " + aPartyNumber + " -> " + aPartyDeviceId);
		System.out.println("🤖 Auto-Answer Enabled for A-Party: " + aPartyNumber);
		}
		
		// Configure B-party
		if (bPartyDeviceId != null && bPartyNumber != null) {
		deviceMap.put(bPartyNumber, bPartyDeviceId);
		autoAnswerNumbers.add(bPartyNumber);  // ✅ Already correct
		System.out.println("✅ B-Party: " + bPartyNumber + " -> " + bPartyDeviceId);
		System.out.println("🤖 Auto-Answer Enabled for B-Party: " + bPartyNumber);
		}
		
		System.out.println("\n📋 Device Map: " + deviceMap);
		System.out.println("🤖 Auto-Answer Numbers: " + autoAnswerNumbers);
		System.out.println("=".repeat(80) + "\n");
		
		// Verify connections
		verifyDeviceConnections();
		}
    
    /**
     * ✅ FIXED: Auto-initialize from system properties
     */
    public static void initializeDevices() {
        String aPartyDevice = System.getProperty("aPartyDevice");
        String aPartyNumber = System.getProperty("aPartyNumber");
        String bPartyDevice = System.getProperty("bPartyDevice");
        String bPartyNumber = System.getProperty("bPartyNumber");
        
        // ✅ CHANGED: Check all required properties
        if (aPartyDevice == null || aPartyNumber == null) {
            System.out.println("⚠️ A-Party properties missing:");
            System.out.println("   -DaPartyDevice=" + aPartyDevice);
            System.out.println("   -DaPartyNumber=" + aPartyNumber);
        }
        
        if (bPartyDevice == null || bPartyNumber == null) {
            System.out.println("⚠️ B-Party properties missing:");
            System.out.println("   -DbPartyDevice=" + bPartyDevice);
            System.out.println("   -DbPartyNumber=" + bPartyNumber);
        }
        
        // ✅ CHANGED: Initialize even with partial data
        if (aPartyDevice != null && aPartyNumber != null) {
            if (bPartyDevice != null && bPartyNumber != null) {
                initializeDevices(aPartyDevice, aPartyNumber, bPartyDevice, bPartyNumber);
            } else {
                // Initialize with A-Party only
                deviceMap.put(aPartyNumber, aPartyDevice);
                System.out.println("✅ A-Party configured: " + aPartyNumber + " -> " + aPartyDevice);
                System.out.println("⚠️ B-Party not configured - auto-answer will not be available");
            }
        } else {
            System.out.println("❌ Cannot initialize - A-Party properties are required");
        }
    }
    
    /**
     * ✅ SETUP AUTO-ANSWER
     */
    public static boolean setupAutoAnswer(String bPartyNumber, String expectedCaller) {
        try {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("🤖 SETTING UP AUTO-ANSWER");
            System.out.println("=".repeat(80));
            System.out.println("📞 B-Party Number: " + bPartyNumber);
            System.out.println("👤 Expected Caller: " + expectedCaller);
            
            // ✅ ADDED: Debug info
            System.out.println("\n🔍 Debug Info:");
            System.out.println("   Device Map: " + deviceMap);
            System.out.println("   Auto-Answer Numbers: " + autoAnswerNumbers);
            System.out.println("   Is Available: " + isAutoAnswerAvailable(bPartyNumber));
            
            // Check if auto-answer is available
            if (!isAutoAnswerAvailable(bPartyNumber)) {
                System.out.println("\n❌ Auto-answer not available for: " + bPartyNumber);
                System.out.println("   Reason: Number not in auto-answer list");
                System.out.println("\n💡 Troubleshooting:");
                System.out.println("   1. Check -DbPartyNumber matches the target: " + bPartyNumber);
                System.out.println("   2. Verify initializeDevices() was called");
                System.out.println("   3. Current auto-answer numbers: " + autoAnswerNumbers);
                System.out.println("=".repeat(80) + "\n");
                return false;
            }
            
            // Get device ID
            String deviceId = getDeviceIdForNumber(bPartyNumber);
            if (deviceId == null) {
                System.out.println("\n❌ Device ID not found for: " + bPartyNumber);
                System.out.println("   Device map: " + deviceMap);
                System.out.println("=".repeat(80) + "\n");
                return false;
            }
            
            System.out.println("✅ Found device ID: " + deviceId);
            
            // Verify device is connected
            System.out.println("🔌 Checking device connection...");
            if (!isDeviceConnected(deviceId)) {
                System.out.println("❌ Device not connected: " + deviceId);
                System.out.println("\n💡 Check connection:");
                System.out.println("   1. Run: adb devices");
                System.out.println("   2. Verify device ID matches exactly");
                System.out.println("   3. For WiFi: IP:PORT format (e.g., 100.99.81.3:39461)");
                
                // ✅ ADDED: Show connected devices
                List<String> connected = ADBHelper.getConnectedDevices();
                System.out.println("\n📱 Currently connected devices:");
                if (connected.isEmpty()) {
                    System.out.println("   (none)");
                } else {
                    for (String dev : connected) {
                        System.out.println("   • " + dev);
                    }
                }
                
                System.out.println("=".repeat(80) + "\n");
                return false;
            }
            
            System.out.println("✅ Device is connected: " + deviceId);
            
            // Stop existing service if any
            if (autoAnswerServices.containsKey(bPartyNumber)) {
                System.out.println("🔄 Stopping existing auto-answer service...");
                stopAutoAnswer(bPartyNumber);
            }
            
            // Create and start new auto-answer service
            System.out.println("🚀 Starting new auto-answer service...");
            ImprovedADBAutoAnswer autoAnswer = new ImprovedADBAutoAnswer(deviceId, expectedCaller);
            autoAnswer.start();
            autoAnswerServices.put(bPartyNumber, autoAnswer);
            
            // Wait for service to stabilize
            Thread.sleep(2000);
            
            // Verify service is running
            if (autoAnswer.isRunning()) {
                System.out.println("\n✅✅✅ AUTO-ANSWER SERVICE STARTED SUCCESSFULLY ✅✅✅");
                System.out.println("   Device: " + deviceId);
                System.out.println("   Expected Caller: " + expectedCaller);
                System.out.println("   Monitoring: Active");
                System.out.println("=".repeat(80) + "\n");
                return true;
            } else {
                System.out.println("\n❌ Auto-answer service failed to start");
                System.out.println("   Check the service logs for details");
                System.out.println("=".repeat(80) + "\n");
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ Auto-answer setup error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("=".repeat(80) + "\n");
            return false;
        }
    }
    
    /**
     * ✅ STOP AUTO-ANSWER
     */
    public static void stopAutoAnswer(String bPartyNumber) {
        try {
            ImprovedADBAutoAnswer autoAnswer = autoAnswerServices.get(bPartyNumber);
            if (autoAnswer != null) {
                autoAnswer.stop();
                autoAnswerServices.remove(bPartyNumber);
                System.out.println("✅ Auto-answer stopped for: " + bPartyNumber);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Auto-answer stop error: " + e.getMessage());
        }
    }
    
    /**
     * ✅ STOP ALL AUTO-ANSWER SERVICES
     */
    public static void stopAllAutoAnswerServices() {
        if (autoAnswerServices.isEmpty()) {
            return;
        }
        
        System.out.println("\n🛑 Stopping all auto-answer services...");
        for (String number : new HashSet<>(autoAnswerServices.keySet())) {
            stopAutoAnswer(number);
        }
        autoAnswerServices.clear();
        System.out.println("✅ All auto-answer services stopped\n");
    }
    
    /**
     * ✅ FIXED: Check if auto-answer is available for a number
     */
    public static boolean isAutoAnswerAvailable(String number) {
        // Simply check if the number is in our auto-answer list
        return autoAnswerNumbers.contains(number);
    }
    
    /**
     * Get device ID for phone number
     */
    public static String getDeviceIdForNumber(String number) {
        return deviceMap.get(number);
    }
    
    /**
     * ✅ IMPROVED: Check if device is connected via ADB
     */
    public static boolean isDeviceConnected(String deviceId) {
        try {
            List<String> devices = ADBHelper.getConnectedDevices();
            
            // Exact match
            if (devices.contains(deviceId)) {
                return true;
            }
            
            // For wireless devices, check IP prefix match
            if (deviceId.contains(":")) {
                String ipPrefix = deviceId.substring(0, deviceId.lastIndexOf(":"));
                for (String device : devices) {
                    if (device.startsWith(ipPrefix)) {
                        System.out.println("✅ Found wireless device match: " + device + " for " + deviceId);
                        return true;
                    }
                }
            }
            
            // Check if deviceId is partial match
            for (String device : devices) {
                if (device.contains(deviceId) || deviceId.contains(device)) {
                    System.out.println("✅ Found partial device match: " + device + " for " + deviceId);
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            System.out.println("⚠️ Error checking device connection: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verify device connections
     */
    private static void verifyDeviceConnections() {
        System.out.println("🔌 Verifying device connections...");
        boolean allConnected = true;
        
        for (Map.Entry<String, String> entry : deviceMap.entrySet()) {
            String number = entry.getKey();
            String deviceId = entry.getValue();
            boolean connected = isDeviceConnected(deviceId);
            
            if (connected) {
                String model = ADBHelper.getDeviceModel(deviceId);
                System.out.println("   ✅ " + number + " (" + model + ") - Connected");
            } else {
                System.out.println("   ❌ " + number + " (" + deviceId + ") - NOT Connected");
                allConnected = false;
            }
        }
        
        if (!allConnected) {
            System.out.println("\n⚠️ WARNING: Not all devices are connected!");
            System.out.println("💡 Run 'adb devices' to check connections");
            
            // Show what's actually connected
            List<String> connected = ADBHelper.getConnectedDevices();
            System.out.println("\n📱 Actually connected devices:");
            if (connected.isEmpty()) {
                System.out.println("   (none)");
            } else {
                for (String dev : connected) {
                    System.out.println("   • " + dev);
                }
            }
        }
        System.out.println();
    }
    
    /**
     * Print device status
     */
    public static void printDeviceStatus() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📱 DEVICE STATUS");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📋 Configured Devices:");
        if (deviceMap.isEmpty()) {
            System.out.println("   ⚠️ No devices configured!");
            System.out.println("   💡 Call DeviceManager.initializeDevices() first");
        } else {
            for (Map.Entry<String, String> entry : deviceMap.entrySet()) {
                String number = entry.getKey();
                String deviceId = entry.getValue();
                boolean connected = isDeviceConnected(deviceId);
                String model = connected ? ADBHelper.getDeviceModel(deviceId) : "Unknown";
                String status = connected ? "✅ Connected" : "❌ Disconnected";
                
                System.out.println("   " + number + " -> " + deviceId);
                System.out.println("      Model: " + model + " | Status: " + status);
            }
        }
        
        System.out.println("\n🔌 Currently Connected (from adb devices):");
        List<String> connectedDevices = ADBHelper.getConnectedDevices();
        if (connectedDevices.isEmpty()) {
            System.out.println("   ⚠️ No devices found!");
        } else {
            for (String deviceId : connectedDevices) {
                String model = ADBHelper.getDeviceModel(deviceId);
                System.out.println("   • " + deviceId + " (" + model + ")");
            }
        }
        
        System.out.println("\n🤖 Auto-Answer Configuration:");
        System.out.println("   Enabled Numbers: " + autoAnswerNumbers);
        
        System.out.println("\n🔄 Auto-Answer Services:");
        if (autoAnswerServices.isEmpty()) {
            System.out.println("   No active services");
        } else {
            for (Map.Entry<String, ImprovedADBAutoAnswer> entry : autoAnswerServices.entrySet()) {
                ImprovedADBAutoAnswer service = entry.getValue();
                String status = service.isRunning() ? "✅ Running" : "❌ Stopped";
                System.out.println("   " + entry.getKey() + " -> " + status + 
                                 " (Calls: " + service.getCallsAnswered() + ")");
            }
        }
        
        System.out.println("=".repeat(80) + "\n");
    }
    
    // Getters
    public static Set<String> getConnectedPhoneNumbers() {
        return new HashSet<>(autoAnswerNumbers);
    }
    
    public static Set<String> getManualNumbers() {
        return new HashSet<>();
    }
    
    public static String getAPartyDeviceId() {
        String aPartyDevice = System.getProperty("aPartyDevice");
        if (aPartyDevice != null) {
            return aPartyDevice;
        }
        return deviceMap.values().stream().findFirst().orElse(null);
    }
}