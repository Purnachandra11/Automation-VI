package com.telecom.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * VPN Manager for handling VPN connections on Android devices
 * Supports VPN status checking and basic VPN management
 */
public class VPNManager {
    
    /**
     * Connect to a free VPN service
     * @param deviceId The device ID to connect VPN on
     * @return true if VPN connection initiated successfully
     */
    public static boolean connectToFreeVPN(String deviceId) {
        System.out.println("🔐 Connecting to Free VPN on device: " + deviceId);
        
        try {
            // Check if VPN app is installed
            String checkInstalled = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell pm list packages | grep vpn"
            );
            
            if (checkInstalled.isEmpty()) {
                System.out.println("⚠️ No VPN app found on device");
                System.out.println("💡 Continuing without VPN...");
                return false;
            }
            
            // Try to start VPN connection via intent
            String vpnIntent = String.format(
                "adb -s %s shell am start -a android.net.vpn.SETTINGS",
                deviceId
            );
            
            ADBHelper.executeCommand(vpnIntent);
            System.out.println(" VPN settings opened");
            
            Thread.sleep(3000);
            
            // Note: Actual VPN connection requires manual setup
            // This just opens VPN settings
            System.out.println("ℹ️ VPN must be connected manually if not already active");
            
            return true;
            
        } catch (Exception e) {
            System.out.println("❌ VPN connection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if VPN is currently connected
     * @param deviceId The device ID to check
     * @return true if VPN is active
     */
    public static boolean isVPNConnected(String deviceId) {
        try {
            String vpnStatus = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys connectivity | grep -i vpn"
            );
            
            boolean connected = vpnStatus.contains("CONNECTED") || 
                              vpnStatus.contains("VPN established");
            
            if (connected) {
                System.out.println(" VPN is active on device: " + deviceId);
            } else {
                System.out.println("❌ VPN is not active on device: " + deviceId);
            }
            
            return connected;
            
        } catch (Exception e) {
            System.out.println("⚠️ Could not check VPN status: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disconnect VPN
     * @param deviceId The device ID to disconnect VPN from
     * @return true if VPN disconnection successful
     */
    public static boolean disconnectVPN(String deviceId) {
        System.out.println("🔓 Disconnecting VPN on device: " + deviceId);
        
        try {
            // Force stop VPN-related processes
            ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell am force-stop com.android.vpndialogs"
            );
            
            System.out.println(" VPN disconnected");
            return true;
            
        } catch (Exception e) {
            System.out.println("❌ VPN disconnection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get VPN connection details
     * @param deviceId The device ID
     * @return Map with VPN details
     */
    public static Map<String, String> getVPNDetails(String deviceId) {
        Map<String, String> vpnDetails = new HashMap<>();
        
        try {
            String vpnInfo = ADBHelper.executeCommand(
                "adb -s " + deviceId + " shell dumpsys connectivity | grep -A10 -i vpn"
            );
            
            vpnDetails.put("connected", isVPNConnected(deviceId) ? "true" : "false");
            vpnDetails.put("rawInfo", vpnInfo);
            
            // Try to extract VPN interface
            if (vpnInfo.contains("tun")) {
                vpnDetails.put("interface", "tun0");
            } else if (vpnInfo.contains("ppp")) {
                vpnDetails.put("interface", "ppp0");
            } else {
                vpnDetails.put("interface", "NONE");
            }
            
        } catch (Exception e) {
            vpnDetails.put("error", e.getMessage());
        }
        
        return vpnDetails;
    }
    
    /**
     * Enable VPN programmatically (requires root or VPN app)
     * @param deviceId The device ID
     * @param vpnConfig VPN configuration details
     * @return true if VPN setup successful
     */
    public static boolean enableVPN(String deviceId, Map<String, String> vpnConfig) {
        System.out.println("🔐 Enabling VPN with custom config...");
        
        try {
            String vpnName = vpnConfig.getOrDefault("name", "TestVPN");
            String vpnServer = vpnConfig.getOrDefault("server", "");
            
            if (vpnServer.isEmpty()) {
                System.out.println("❌ VPN server not specified");
                return false;
            }
            
            System.out.println("📡 VPN Name: " + vpnName);
            System.out.println("🌐 VPN Server: " + vpnServer);
            
            // Note: Actual VPN setup requires VPN app or root access
            // This is a placeholder for custom VPN logic
            System.out.println("⚠️ Custom VPN setup requires manual configuration");
            
            return false;
            
        } catch (Exception e) {
            System.out.println("❌ VPN enable failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for VPN connection to establish
     * @param deviceId The device ID
     * @param timeoutSeconds Maximum time to wait
     * @return true if VPN connected within timeout
     */
    public static boolean waitForVPNConnection(String deviceId, int timeoutSeconds) {
        System.out.println("⏳ Waiting for VPN connection (max " + timeoutSeconds + "s)...");
        
        for (int i = 0; i < timeoutSeconds; i++) {
            if (isVPNConnected(deviceId)) {
                System.out.println(" VPN connected after " + i + "s");
                return true;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        System.out.println("⏱️ VPN connection timeout after " + timeoutSeconds + "s");
        return false;
    }
    
    /**
     * Print VPN diagnostics
     * @param deviceId The device ID
     */
    public static void printVPNDiagnostics(String deviceId) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔐 VPN DIAGNOSTICS - Device: " + deviceId);
        System.out.println("=".repeat(80));
        
        boolean connected = isVPNConnected(deviceId);
        System.out.println("Connection Status: " + (connected ? " CONNECTED" : "❌ DISCONNECTED"));
        
        Map<String, String> details = getVPNDetails(deviceId);
        System.out.println("VPN Interface: " + details.getOrDefault("interface", "NONE"));
        
        System.out.println("=".repeat(80) + "\n");
    }
}