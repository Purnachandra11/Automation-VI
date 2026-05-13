package com.telecom.driver;

import com.telecom.config.ConfigReader;
import com.telecom.utils.ADBHelper;
import com.telecom.utils.VPNManager;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class DriverManager {
    private static AppiumDriverLocalService service;
    private static ThreadLocal<AndroidDriver> driver = new ThreadLocal<>();
    private static final String DEFAULT_APPIUM_URL = "http://127.0.0.1:4723";
    
//    private static final Set<Integer> usedPorts = new HashSet<>();

 // Replace the port management section (lines 23-113) with this improved version:

    private static final int MIN_PORT = 8200;
    private static final int MAX_PORT = 8899; 
    private static final AtomicInteger portCounter = new AtomicInteger(MIN_PORT);
    private static final ConcurrentHashMap<String, Integer> devicePortMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> portDeviceMap = new ConcurrentHashMap<>(); // Track port->device mapping

    public static synchronized int getAvailableSystemPort(String deviceId) {
        System.out.println("🔍 Looking for available port for device: " + deviceId);
        
        // First, check if device already has a port assigned
        if (devicePortMap.containsKey(deviceId)) {
            int existingPort = devicePortMap.get(deviceId);
            if (!isPortInUse(existingPort) && deviceId.equals(portDeviceMap.get(existingPort))) {
                System.out.println("🔁 Reusing existing port " + existingPort + " for device " + deviceId);
                return existingPort;
            } else {
                // Port is busy or assigned to different device, clean up
                System.out.println("🧹 Cleaning up stale port " + existingPort + " for device " + deviceId);
                devicePortMap.remove(deviceId);
                portDeviceMap.remove(existingPort);
            }
        }
        
        // Try sequential ports first (more predictable)
        int basePort = portCounter.getAndUpdate(prev -> {
            int next = prev + 2;  // Increment by smaller amount
            return (next > MAX_PORT) ? MIN_PORT : next;
        });
        
        // Try up to 100 ports
        for (int i = 0; i < 100; i++) {
            int tryPort = basePort + i;
            if (tryPort > MAX_PORT) {
                tryPort = MIN_PORT + (tryPort - MAX_PORT - 1);
            }
            
            // Skip if port is already assigned to a device
            if (portDeviceMap.containsKey(tryPort)) {
                continue;
            }
            
            // Check if port is actually free on system
            if (!isPortInUse(tryPort)) {
                devicePortMap.put(deviceId, tryPort);
                portDeviceMap.put(tryPort, deviceId);
                System.out.println("✅ Assigned port " + tryPort + " to device " + deviceId);
                return tryPort;
            } else {
                System.out.println("⏭️ Port " + tryPort + " is in use, skipping...");
            }
        }
        
        // Fallback: random port
        return getRandomAvailablePort(deviceId);
    }

    private static int getRandomAvailablePort(String deviceId) {
        System.out.println("🎲 Trying random port assignment for device " + deviceId);
        Random random = new Random();
        
        for (int attempt = 0; attempt < 50; attempt++) {
            int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT + 1);
            
            // Skip if port is already assigned
            if (portDeviceMap.containsKey(port)) {
                continue;
            }
            
            if (!isPortInUse(port)) {
                devicePortMap.put(deviceId, port);
                portDeviceMap.put(port, deviceId);
                System.out.println("✅ Assigned random port " + port + " to device " + deviceId);
                return port;
            }
        }
        
        // Last resort: force a port (might fail but worth trying)
        for (int port = MIN_PORT; port <= MAX_PORT; port++) {
            if (!portDeviceMap.containsKey(port) && !isPortInUse(port)) {
                devicePortMap.put(deviceId, port);
                portDeviceMap.put(port, deviceId);
                System.out.println("🆘 Force assigned port " + port + " to device " + deviceId);
                return port;
            }
        }
        
        // Complete last resort
        int defaultPort = 8392;
        System.out.println("⚠️ Using default port " + defaultPort + " for device " + deviceId + " (ALL PORTS BUSY!)");
        return defaultPort;
    }

    private static boolean isPortInUse(int port) {
        // Add a small delay to avoid rapid connection attempts
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000); // 1 second timeout
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 1000);
            return true; // Port is in use
        } catch (Exception e) {
            return false; // Port is available or connection failed
        }
    }

    public static synchronized void releasePortForDevice(String deviceId) {
        Integer port = devicePortMap.remove(deviceId);
        if (port != null) {
            portDeviceMap.remove(port);
            System.out.println("♻️ Released port " + port + " for device " + deviceId);
            
            // Optional: Force kill any process on that port
            try {
                killProcessOnPort(port);
            } catch (Exception e) {
                // Ignore, just cleanup attempt
            }
        } else {
            System.out.println("ℹ️ No port to release for device " + deviceId);
        }
    }

    @SuppressWarnings("deprecation")
	private static void killProcessOnPort(int port) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Run netstat command
                Process process = Runtime.getRuntime().exec(
                    new String[]{"cmd.exe", "/c", "netstat -ano | findstr :" + port}
                );
                
                // Read output
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
                );
                
                // Also read error stream to avoid hanging
                Thread errorReader = new Thread(() -> {
                    try (java.io.BufferedReader error = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {
                        while (error.readLine() != null) {
                            // Just consume errors
                        }
                    } catch (Exception ignored) {}
                });
                errorReader.start();
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("LISTENING") || line.contains("TCP")) {
                        // Parse PID - more robust parsing
                        String[] parts = line.trim().split("\\s+");
                        String pid = null;
                        for (int i = parts.length - 1; i >= 0; i--) {
                            if (parts[i].matches("\\d+")) {
                                pid = parts[i];
                                break;
                            }
                        }
                        
                        if (pid != null && !pid.equals("0")) {
                            // Kill the process
                            Runtime.getRuntime().exec("taskkill /F /PID " + pid);
                            System.out.println("🗡️ Killed process " + pid + " on port " + port);
                        }
                    }
                }
                
                reader.close();
                errorReader.join();
                process.waitFor();
            }
        } catch (Exception e) {
            // Silent fail - cleanup attempt only
        }
    }
    
    
    public static void startAppiumService() {
        try {
            // Try to use existing server first
            if (isAppiumServerRunning()) {
                System.out.println("✅ Using existing Appium server");
                return;
            }
            
            // Start new server
            System.out.println("🚀 Starting Appium server...");
            AppiumServiceBuilder builder = new AppiumServiceBuilder()
                .withIPAddress("127.0.0.1")
                .usingPort(4723)
                .withArgument(GeneralServerFlag.BASEPATH, "/")
                .withArgument(GeneralServerFlag.SESSION_OVERRIDE)
                .withArgument(GeneralServerFlag.LOG_LEVEL, "info")
                .withArgument(GeneralServerFlag.RELAXED_SECURITY)
                .withArgument(GeneralServerFlag.ALLOW_INSECURE, "adb_shell")
                .withTimeout(Duration.ofSeconds(300));
            
            service = AppiumDriverLocalService.buildService(builder);
            service.start();
            
            System.out.println("✅ Appium server started on: " + service.getUrl());
            
        } catch (Exception e) {
            System.out.println("❌ Appium server start failed: " + e.getMessage());
            throw new RuntimeException("Appium service failed to start", e);
        }
    }
    
    private static boolean isAppiumServerRunning() {
        try {
            URI uri = URI.create(DEFAULT_APPIUM_URL + "/status");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.connect();
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static AndroidDriver initializeDriver(String deviceId, String platformVersion) throws Exception {
        System.out.println("🚀 Initializing driver for: " + deviceId);
        
        // Ensure device is connected
        if (!ADBHelper.isDeviceConnected(deviceId)) {
            System.out.println("⚠ Device not connected, attempting to reconnect...");
            if (!ADBHelper.connectDevice(deviceId)) {
                throw new RuntimeException("Cannot connect to device: " + deviceId);
            }
        }
        int systemPort = getAvailableSystemPort(deviceId);
        UiAutomator2Options options = new UiAutomator2Options()
            .setUdid(deviceId)
            .setPlatformName("Android")
            .setPlatformVersion(platformVersion)
            .setAutomationName("UiAutomator2")
            .setAppPackage(ConfigReader.getAppPackage())
            .setAppActivity(ConfigReader.getAppActivity())
            .setNoReset(true)
            .setFullReset(false)
            .setAutoGrantPermissions(true)
            .setNewCommandTimeout(Duration.ofSeconds(300))
            .setDeviceName(ADBHelper.getDeviceModel(deviceId))
            .setSystemPort(systemPort); 
        
        // Appium capabilities
//        options.setCapability("appium:ignoreHiddenApiPolicyError", true);
//        options.setCapability("appium:disableWindowAnimation", true);
//        options.setCapability("appium:skipDeviceInitialization", true);
//        options.setCapability("appium:skipServerInstallation", true);
//        options.setCapability("appium:enforceAppInstall", false);
//        options.setCapability("appium:allowInsecure", "adb_shell");
//        options.setCapability("appium:relaxedSecurityEnabled", true);
//        options.setCapability("appium:ensureWebviewsHavePages", true);
//        options.setCapability("appium:nativeWebScreenshot", true);
//        options.setCapability("appium:connectHardwareKeyboard", true);
        options.setCapability("appium:ignoreHiddenApiPolicyError", true);
        options.setCapability("appium:disableWindowAnimation", true);

        // CRITICAL FIXES
        options.setCapability("appium:adbExecTimeout", 120000);
        options.setCapability("appium:skipDeviceInitialization", false);

        options.setCapability("appium:skipServerInstallation", true);
        options.setCapability("appium:enforceAppInstall", false);
        options.setCapability("appium:allowInsecure", "adb_shell");
        options.setCapability("appium:relaxedSecurityEnabled", true);
        options.setCapability("appium:ensureWebviewsHavePages", true);
        options.setCapability("appium:nativeWebScreenshot", true);
        options.setCapability("appium:connectHardwareKeyboard", true);

        
        try {
            AndroidDriver androidDriver;
            
            if (service != null && service.isRunning()) {
                // Use programmatically started server
                androidDriver = new AndroidDriver(service.getUrl(), options);
            } else {
                // Use external server
                URI appiumUri = URI.create(DEFAULT_APPIUM_URL);
                androidDriver = new AndroidDriver(appiumUri.toURL(), options);
            }
            
            driver.set(androidDriver);
            
            System.out.println("✅ Driver initialized successfully");
            System.out.println("   📱 Device: " + ADBHelper.getDeviceModel(deviceId));
            System.out.println("   🤖 Android: " + platformVersion);
            System.out.println("   🔗 Connection: " + ADBHelper.getConnectionType(deviceId));
            
            return androidDriver;
            
        } catch (Exception e) {
            System.out.println("❌ Driver initialization failed: " + e.getMessage());
            System.out.println("💡 Make sure Appium server is running");
            throw e;
        }
    }
    
    public static AndroidDriver initializeDriverForMessaging(String deviceId, String platformVersion) throws Exception {
        System.out.println("🚀 Initializing MESSAGING driver for: " + deviceId);
        
        // Ensure device is connected
        if (!ADBHelper.isDeviceConnected(deviceId)) {
            System.out.println("⚠ Device not connected, attempting to reconnect...");
            if (!ADBHelper.connectDevice(deviceId)) {
                throw new RuntimeException("Cannot connect to device: " + deviceId);
            }
        }
        
        // Add a delay for wireless connections
        if (ADBHelper.getConnectionType(deviceId).equals("WIRELESS")) {
            System.out.println("📡 Wireless connection detected, adding delay...");
            Thread.sleep(5000); // Wait 5 seconds for wireless stability
        }
        
        int systemPort = getAvailableSystemPort(deviceId);
        UiAutomator2Options options = new UiAutomator2Options()
            .setUdid(deviceId)
            .setPlatformName("Android")
            .setPlatformVersion(platformVersion)
            .setAutomationName("UiAutomator2")
            .setAppPackage(ConfigReader.getMessageAppPackage())
            .setAppActivity(ConfigReader.getMessageAppActivity())
            .setNoReset(true)
            .setFullReset(false)
            .setAutoGrantPermissions(true)
            .setNewCommandTimeout(Duration.ofSeconds(300))
            .setDeviceName(ADBHelper.getDeviceModel(deviceId))
            .setSystemPort(systemPort); 
        
        // Appium capabilities - UPDATED BASED ON RECOMMENDATIONS
        options.setCapability("appium:ignoreHiddenApiPolicyError", true);
        options.setCapability("appium:disableWindowAnimation", true);
        options.setCapability("appium:skipDeviceInitialization", false);  // ✅ MUST be false
        options.setCapability("appium:skipServerInstallation", false);    // ✅ MUST be false for recovery
        options.setCapability("appium:enforceAppInstall", false);
        options.setCapability("appium:allowInsecure", "adb_shell");
        options.setCapability("appium:relaxedSecurityEnabled", true);
        options.setCapability("appium:ensureWebviewsHavePages", true);
        options.setCapability("appium:nativeWebScreenshot", true);
        options.setCapability("appium:connectHardwareKeyboard", true);
        
        // CRITICAL TIMEOUT SETTINGS - UPDATED
        options.setCapability("appium:adbExecTimeout", 120000);
        options.setCapability("appium:androidInstallTimeout", 120000);
        options.setCapability("appium:uiautomator2ServerLaunchTimeout", 90000);
        options.setCapability("appium:uiautomator2ServerInstallTimeout", 90000);
        options.setCapability("appium:newCommandTimeout", 3000);
        
        // Additional wireless-specific capabilities
        options.setCapability("appium:remoteAdbHost", "127.0.0.1");
        options.setCapability("appium:remoteAdbPort", 5037);
        
        // Add noReset and fullReset capabilities explicitly (though already set via setter methods)
        options.setCapability("appium:noReset", true);
        options.setCapability("appium:fullReset", false);
        
        try {
            AndroidDriver androidDriver;
            
            if (service != null && service.isRunning()) {
                androidDriver = new AndroidDriver(service.getUrl(), options);
            } else {
                URI appiumUri = URI.create(DEFAULT_APPIUM_URL);
                androidDriver = new AndroidDriver(appiumUri.toURL(), options);
            }
            
            driver.set(androidDriver);
            
            System.out.println("✅ Messaging Driver initialized successfully");
            System.out.println("   📱 Device: " + ADBHelper.getDeviceModel(deviceId));
            System.out.println("   🤖 Android: " + platformVersion);
            System.out.println("   📱 Messaging App: " + ConfigReader.getMessageAppPackage());
            System.out.println("   🔗 Connection: " + ADBHelper.getConnectionType(deviceId));
            
            return androidDriver;
            
        } catch (Exception e) {
            System.out.println("❌ Messaging Driver initialization failed: " + e.getMessage());
            throw e;
        }
    }
    
    public static AndroidDriver initializeDriverForDataUsage(String deviceId, String platformVersion) throws Exception {
        System.out.println("📶 Initializing DATA USAGE driver for: " + deviceId);

        if (!ADBHelper.isDeviceConnected(deviceId)) {
            if (!ADBHelper.connectDevice(deviceId)) {
                throw new RuntimeException("Cannot connect to device: " + deviceId);
            }
        }
        
        int systemPort = getAvailableSystemPort(deviceId);
        int mjpegPort = systemPort + 2000;

        UiAutomator2Options options = new UiAutomator2Options()
        	    .setUdid(deviceId)
        	    .setPlatformName("Android")
        	    .setPlatformVersion(platformVersion)
        	    .setAutomationName("UiAutomator2")
//        	    .setAppPackage(ConfigReader.getDataUsagePackage())
//        	    .setAppActivity(ConfigReader.getDataUsageActivity())
        	    .setNoReset(true)
        	    .setSystemPort(systemPort);

        	options.setCapability("appium:mjpegServerPort", mjpegPort);


        AndroidDriver androidDriver = new AndroidDriver(
                (service != null && service.isRunning()) ? service.getUrl() : URI.create(DEFAULT_APPIUM_URL).toURL(),
                options
        );

        driver.set(androidDriver);

        System.out.println("📶 Data Usage Driver initialized successfully");
        return androidDriver;
    }

    
    public static AndroidDriver initializeDriverWithVPN(String deviceId, String platformVersion) throws Exception {
        System.out.println("🔗 Establishing VPN connection before driver init...");
        
        // Connect to VPN first for wireless
        if (ConfigReader.isVPNEnabled() && ADBHelper.getConnectionType(deviceId).equals("WIRELESS")) {
            VPNManager.connectToFreeVPN(deviceId);
//            Thread.sleep(5000);
        }
        
        // Then initialize driver
        return initializeDriver(deviceId, platformVersion);
    }
    
    public static AndroidDriver getDriver() {
        return driver.get();
    }
    
    public static void quitDriver() {
        AndroidDriver currentDriver = null;
        String deviceId = null;
        
        try {
            currentDriver = driver.get();
            if (currentDriver != null) {
                // Get deviceId before quitting
                try {
                    Object udid = currentDriver.getCapabilities().getCapability("udid");
                    if (udid != null) {
                        deviceId = udid.toString();
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Could not extract device ID: " + e.getMessage());
                }
                
                // Try to quit gracefully
                currentDriver.quit();
                System.out.println("✅ Driver quit successfully");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Driver quit had issues: " + e.getMessage());
            if (currentDriver != null) {
                try {
                    // Force cleanup
                    currentDriver.quit();
                } catch (Exception e2) {
                    System.out.println("⚠️ Force quit also failed: " + e2.getMessage());
                }
            }
        } finally {
            driver.remove();
            
            // Always release port if we have deviceId
            if (deviceId != null) {
                releasePortForDevice(deviceId);
            } else {
                System.out.println("⚠️ Could not release port - deviceId unknown");
            }
        }
    }
    
    public static AndroidDriver initializeDriverWithRetry(String deviceId, String platformVersion, int maxRetries) throws Exception {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("🔄 Driver initialization attempt " + attempt + "/" + maxRetries);
                return initializeDriver(deviceId, platformVersion);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    throw e;
                }
                
                if (e.getMessage().contains("port") && e.getMessage().contains("busy")) {
                    System.out.println("⚠️ Port conflict detected, performing cleanup...");
                    releasePortForDevice(deviceId);
                    Thread.sleep(2000); // Wait before retry
                }
            }
        }
        throw new RuntimeException("Failed to initialize driver after " + maxRetries + " attempts");
    }
    
    public static void stopAppiumService() {
        try {
            if (service != null && service.isRunning()) {
                service.stop();
                System.out.println("✅ Appium service stopped");
            }
        } catch (Exception e) {
            System.out.println("⚠ Appium service stop had issues: " + e.getMessage());
        }
    }
    
    public static boolean isServiceRunning() {
        return service != null && service.isRunning();
    }
    
    public static synchronized void emergencyPortCleanup() {
        System.out.println("🚨 Performing emergency port cleanup...");
        devicePortMap.clear();
        portDeviceMap.clear();
        System.out.println("✅ Emergency port cleanup completed");
    }
}