package com.telecom.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ADBLauncher {
    
    /**
     * Launch SIM Toolkit using ADB monkey command
     * Works for: adb shell monkey -p com.android.stk -c android.intent.category.LAUNCHER 1
     */
    public static boolean launchSIMToolkit() {
        return executeADBCommand("shell monkey -p com.android.stk -c android.intent.category.LAUNCHER 1");
    }
    
    /**
     * Launch SIM Toolkit on specific device
     */
    public static boolean launchSIMToolkit(String deviceId) {
        return executeADBCommand("-s " + deviceId + " shell monkey -p com.android.stk -c android.intent.category.LAUNCHER 1");
    }
    
    /**
     * Alternative method using activity manager
     */
    public static boolean launchSIMToolkitViaActivity(String deviceId) {
        return executeADBCommand("-s " + deviceId + " shell am start -n com.android.stk/.StkLauncherActivity");
    }
    
    /**
     * Generic ADB command execution
     */
    private static boolean executeADBCommand(String command) {
        try {
            String fullCommand = "adb " + command;
            System.out.println("  🔧 Executing: " + fullCommand);
            
            @SuppressWarnings("deprecation")
			Process process = Runtime.getRuntime().exec(fullCommand);
            
            // Read output
            BufferedReader successReader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
            );
            
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            String line;
            while ((line = successReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                System.out.println("  ✅ ADB command executed successfully");
                if (!output.toString().isEmpty()) {
                    System.out.println("  Output: " + output.toString().trim());
                }
                return true;
            } else {
                System.err.println("  ❌ ADB command failed with exit code: " + exitCode);
                if (!error.toString().isEmpty()) {
                    System.err.println("  Error: " + error.toString().trim());
                }
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("  ❌ Error executing ADB command: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if device is connected
     */
    public static boolean isDeviceConnected(String deviceId) {
        try {
            @SuppressWarnings("deprecation")
			Process process = Runtime.getRuntime().exec("adb devices");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(deviceId) && line.contains("device")) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Error checking device connection: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get first connected device ID
     */
    public static String getFirstDeviceId() {
        try {
            @SuppressWarnings("deprecation")
			Process process = Runtime.getRuntime().exec("adb devices");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("\tdevice")) {
                    return line.split("\t")[0];
                }
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Error getting device ID: " + e.getMessage());
            return null;
        }
    }
}