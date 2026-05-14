package com.telecom.tests;

import com.telecom.config.ConfigReader;
import com.telecom.core.DataUsageTestExecutor;
import com.telecom.driver.DriverManager;
import io.appium.java_client.android.AndroidDriver;
import org.testng.annotations.*;

import java.util.List;
import java.util.Map;

public class DataUsageTest {
    private AndroidDriver driver;
    private String deviceId;
    
    @BeforeClass
    public void setUp() throws Exception {
        System.out.println("🌐 Setting up Data Usage Test Environment...");
        
        DriverManager.startAppiumService();
        
        deviceId = System.getProperty("deviceId");
        if (deviceId == null || deviceId.isEmpty()) {
            List<String> devices = com.telecom.utils.ADBHelper.getConnectedDevices();
            if (devices.isEmpty()) {
                throw new Exception("❌ No device connected");
            }
            deviceId = devices.get(0);
        }
        
        String androidVersion = com.telecom.utils.ADBHelper.getAndroidVersion(deviceId);
        driver = DriverManager.initializeDriver(deviceId, androidVersion.split("\\.")[0]);
        
        System.out.println(" Data Usage Test Environment Ready");
    }
    
    @Test
    public void executeDataUsageTests() {
        try {
            System.out.println("\n🌐 EXECUTING DATA USAGE TESTS");
            System.out.println("=".repeat(80));
            
            String excelPath = ConfigReader.getExcelFilePath();
            
            DataUsageTestExecutor executor = new DataUsageTestExecutor(driver, deviceId);
            List<Map<String, Object>> results = executor.executeDataUsageTests(excelPath);
            
            System.out.println("\n Data Usage Tests Completed");
            System.out.println("   Total Tests: " + results.size());
            
        } catch (Exception e) {
            System.out.println("❌ Data usage test execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @AfterClass
    public void tearDown() {
        if (driver != null) {
            DriverManager.quitDriver();
        }
        // Don't stop external Appium server
        System.out.println("ℹ️ External Appium server remains running");
    }
}
