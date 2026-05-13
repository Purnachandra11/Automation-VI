package com.telecom.tests;

import com.telecom.config.ConfigReader;
import com.telecom.core.CompleteCallingTestExecutor;
import com.telecom.core.CompleteSMSTestExecutor;
import com.telecom.driver.DriverManager;
import com.telecom.utils.ADBHelper;
import io.appium.java_client.android.AndroidDriver;
import org.testng.annotations.*;
import java.util.List;
import java.util.Map;


public class CallingTest {
    
    private AndroidDriver driver;
    private CompleteCallingTestExecutor callingExecutor;
    private String aPartyDeviceId;
    
    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🚀 CALLING TEST SETUP");
        System.out.println("=".repeat(100));
        
        // Get device parameters
        aPartyDeviceId = System.getProperty("aPartyDevice");
        if (aPartyDeviceId == null || aPartyDeviceId.isEmpty()) {
            // Auto-detect
            List<String> devices = ADBHelper.getConnectedDevices();
            if (devices.isEmpty()) {
                throw new Exception("❌ No device connected");
            }
            aPartyDeviceId = devices.get(0);
        }
        
        String platformVersion = System.getProperty("platformVersion");
        if (platformVersion == null || platformVersion.isEmpty()) {
            platformVersion = ADBHelper.getAndroidVersion(aPartyDeviceId).split("\\.")[0];
        }
        
        System.out.println("📱 Configuration:");
        System.out.println("   A-Party Device: " + aPartyDeviceId);
        System.out.println("   Android Version: " + platformVersion);
        
        // ✅ CRITICAL FIX: Initialize DeviceManager BEFORE anything else
        System.out.println("\n🔧 Initializing DeviceManager...");
        com.telecom.utils.DeviceManager.initializeDevices();
        
        // Verify initialization
        com.telecom.utils.DeviceManager.printDeviceStatus();
        
        // Start Appium
        System.out.println("\n🔧 Starting services...");
        DriverManager.startAppiumService();
        
        // Initialize driver
        System.out.println("📲 Initializing driver...");
        driver = DriverManager.initializeDriver(aPartyDeviceId, platformVersion);
        
        // Initialize executor
        callingExecutor = new CompleteCallingTestExecutor(driver, aPartyDeviceId);
        
        System.out.println("✅ Setup completed successfully\n");
    }
    
    @Test(description = "Execute all calling tests from Excel")
    public void testAllCallingScenarios() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📞 EXECUTING ALL CALLING TESTS");
        System.out.println("=".repeat(100));
        
        try {
            String excelFilePath = ConfigReader.getExcelFilePath();
            System.out.println("📄 Excel File: " + excelFilePath);
            
            // Execute all tests
            List<Map<String, Object>> results = callingExecutor.executeAllCallingTests(excelFilePath);
            
            // Print final report
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📈 CALLING TESTS COMPLETED");
            System.out.println("Total Tests: " + results.size());
            
            long passed = results.stream()
                .filter(r -> "SUCCESS".equals(r.get("finalStatus")))
                .count();
            
            System.out.println("✅ Passed: " + passed);
            System.out.println("❌ Failed: " + (results.size() - passed));
            
            if (results.size() > 0) {
                System.out.println("📊 Success Rate: " + 
                    String.format("%.1f%%", (passed * 100.0 / results.size())));
            }
            
            System.out.println("=".repeat(100));
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
            throw new AssertionError("Calling tests failed: " + e.getMessage());
        }
    }
    
    @Test(description = "Test individual calling scenarios")
    public void testVoiceCall() {
        System.out.println("\nℹ️ Individual scenario tests would use specific data from Excel");
    }
    
    @Test(description = "Test video calling")
    public void testVideoCall() {
        System.out.println("\nℹ️ Video test data would come from Excel");
    }
    
    @Test(description = "Test conference calling")
    public void testConferenceCall() {
        System.out.println("\nℹ️ Conference test data would come from Excel");
    }
    
    @AfterClass(alwaysRun = true)
    public void tearDown() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🧹 CLEANUP");
        System.out.println("=".repeat(100));
        
        try {
            if (driver != null) {
                DriverManager.quitDriver();
                System.out.println("✅ Driver quit successfully");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Driver quit had issues: " + e.getMessage());
        }
        
        System.out.println("=".repeat(100));
    }
}

@SuppressWarnings("all")
class UpdatedSMSTest {
    
    private AndroidDriver driver;
    private CompleteSMSTestExecutor smsExecutor;
    private String deviceId;
    
    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("💬 SMS TEST SETUP");
        System.out.println("=".repeat(100));
        
        // Get device
        deviceId = System.getProperty("deviceId");
        if (deviceId == null || deviceId.isEmpty()) {
            List<String> devices = ADBHelper.getConnectedDevices();
            if (devices.isEmpty()) {
                throw new Exception("❌ No device connected");
            }
            deviceId = devices.get(0);
        }
        
        String androidVersion = ADBHelper.getAndroidVersion(deviceId).split("\\.")[0];
        
        System.out.println("📱 Configuration:");
        System.out.println("   Device: " + deviceId);
        System.out.println("   Android: " + androidVersion);
        
        // Start services
        System.out.println("\n🔧 Starting services...");
        DriverManager.startAppiumService();
        
        // Initialize messaging driver
        System.out.println("💬 Initializing messaging driver...");
        driver = DriverManager.initializeDriverForMessaging(deviceId, androidVersion);
        
        // Initialize executor
        smsExecutor = new CompleteSMSTestExecutor(driver, deviceId);
        
        System.out.println("✅ Setup completed successfully\n");
    }
    
    @Test(description = "Execute all SMS tests from Excel")
    public void testAllSMSScenarios() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📱 EXECUTING ALL SMS TESTS");
        System.out.println("=".repeat(100));
        
        try {
            String excelFilePath = ConfigReader.getExcelFilePath();
            System.out.println("📄 Excel File: " + excelFilePath);
            
            // Execute all tests
            List<Map<String, Object>> results = smsExecutor.executeAllSMSTests(excelFilePath);
            
            // Print final report
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📈 SMS TESTS COMPLETED");
            System.out.println("Total Tests: " + results.size());
            
            long passed = results.stream()
                .filter(r -> "SUCCESS".equals(r.get("finalStatus")) || 
                           "PARTIAL_SUCCESS".equals(r.get("finalStatus")))
                .count();
            
            System.out.println("✅ Passed: " + passed);
            System.out.println("❌ Failed: " + (results.size() - passed));
            
            // SMS delivery statistics
            int totalSMS = results.stream()
                .mapToInt(r -> (Integer) r.getOrDefault("totalSMS", 0))
                .sum();
            
            int successfulSMS = results.stream()
                .mapToInt(r -> (Integer) r.getOrDefault("successfulSMS", 0))
                .sum();
            
            System.out.println("\n📊 SMS Delivery:");
            System.out.println("   Total SMS: " + totalSMS);
            System.out.println("   Successful: " + successfulSMS);
            System.out.println("   Failed: " + (totalSMS - successfulSMS));
            
            if (totalSMS > 0) {
                System.out.println("   Success Rate: " + 
                    String.format("%.1f%%", (successfulSMS * 100.0 / totalSMS)));
            }
            
            System.out.println("=".repeat(100));
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
            throw new AssertionError("SMS tests failed: " + e.getMessage());
        }
    }
    
    @Test(description = "Test individual SMS")
    public void testIndividualSMS() {
        System.out.println("\nℹ️ Individual SMS test data would come from Excel");
    }
    
    @Test(description = "Test group SMS")
    public void testGroupSMS() {
        System.out.println("\nℹ️ Group SMS test data would come from Excel");
    }
    
    @AfterClass(alwaysRun = true)
    public void tearDown() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🧹 CLEANUP");
        System.out.println("=".repeat(100));
        
        try {
            if (driver != null) {
                driver.quit();
                System.out.println("✅ Driver quit successfully");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Driver quit had issues: " + e.getMessage());
        }
        
        System.out.println("=".repeat(100));
    }
}