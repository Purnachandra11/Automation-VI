package com.telecom.tests;

import com.telecom.config.ConfigReader;
import com.telecom.core.CompleteSMSTestExecutor;
import com.telecom.driver.DriverManager;
import com.telecom.utils.ADBHelper;
import com.telecom.utils.DeviceManager;
import com.telecom.utils.ProgressReporter;
import io.appium.java_client.android.AndroidDriver;
import org.testng.annotations.*;
import org.testng.asserts.SoftAssert;

import java.util.List;
import java.util.Map;

/**
 *  ENHANCED SMS & VOICE MESSAGE TEST CLASS
 * Supports: OUTGOING/INCOMING, Individual/Group, Text/Voice
 */
public class SMSTest {
    
    private AndroidDriver driver;
    private CompleteSMSTestExecutor smsExecutor;
    private String aPartyDeviceId;
    private String aPartyNumber;
    private String bPartyDeviceId;
    private String bPartyNumber;
    private SoftAssert softAssert;
    
    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("💬 SMS & VOICE MESSAGE TEST SETUP");
        System.out.println("=".repeat(100));
        
        softAssert = new SoftAssert();
        
        // STEP 1: Get device parameters
        aPartyDeviceId = System.getProperty("aPartyDevice");
        if (aPartyDeviceId == null || aPartyDeviceId.isEmpty()) {
            aPartyDeviceId = System.getProperty("deviceId");
        }
        aPartyNumber = System.getProperty("aPartyNumber");
        bPartyDeviceId = System.getProperty("bPartyDevice");
        bPartyNumber = System.getProperty("bPartyNumber");
        
        // Auto-detect A-Party if not provided
        if (aPartyDeviceId == null || aPartyDeviceId.isEmpty()) {
            List<String> devices = ADBHelper.getConnectedDevices();
            if (devices.isEmpty()) {
                throw new Exception("❌ No device connected. Please connect at least one device.");
            }
            aPartyDeviceId = devices.get(0);
            System.out.println("🔍 Auto-detected A-Party device: " + aPartyDeviceId);
        }
        
        // STEP 2: Validate required parameters
        if (aPartyNumber == null || aPartyNumber.isEmpty()) {
            throw new Exception("❌ A-Party number is required. Use -DaPartyNumber=XXXXXXXXXX");
        }
        
        // Get Android version
        String platformVersion = System.getProperty("platformVersion");
        if (platformVersion == null || platformVersion.isEmpty()) {
            platformVersion = ADBHelper.getAndroidVersion(aPartyDeviceId).split("\\.")[0];
        }
        
        // STEP 3: Print configuration
        System.out.println("\n📱 DEVICE CONFIGURATION:");
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ A-Party (Primary):                                              │");
        System.out.println("│   Device ID: " + String.format("%-48s", aPartyDeviceId) + "│");
        System.out.println("│   Number:    " + String.format("%-48s", aPartyNumber) + "│");
        System.out.println("│   Model:     " + String.format("%-48s", ADBHelper.getDeviceModel(aPartyDeviceId)) + "│");
        System.out.println("│   Android:   " + String.format("%-48s", platformVersion) + "│");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        
        if (bPartyDeviceId != null && bPartyNumber != null) {
            System.out.println("│ B-Party (Secondary):                                            │");
            System.out.println("│   Device ID: " + String.format("%-48s", bPartyDeviceId) + "│");
            System.out.println("│   Number:    " + String.format("%-48s", bPartyNumber) + "│");
            System.out.println("│   Model:     " + String.format("%-48s", ADBHelper.getDeviceModel(bPartyDeviceId)) + "│");
            System.out.println("│   Status:     Full bidirectional SMS support                 │");
        } else {
            System.out.println("│ B-Party (Secondary): ⚠️  NOT CONFIGURED                         │");
            System.out.println("│   Status:    ⚠️  INCOMING SMS tests will be SKIPPED            │");
        }
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // STEP 4: Report initialization
        ProgressReporter.reportSMSProgress(
            aPartyDeviceId,
            "",
            "INITIALIZING",
            "Setting up SMS test environment",
            0.0
        );
        
        // STEP 5: Initialize DeviceManager
        System.out.println("\n🔧 INITIALIZING DEVICE MANAGER...");
        if (aPartyDeviceId != null && aPartyNumber != null) {
            if (bPartyDeviceId != null && bPartyNumber != null) {
                DeviceManager.initializeDevices(
                    aPartyDeviceId, aPartyNumber,
                    bPartyDeviceId, bPartyNumber
                );
                System.out.println(" Both devices configured - Full bidirectional SMS support enabled");
            } else {
                // Initialize with A-Party only
                System.out.println("⚠️  Only A-Party configured");
                System.out.println("   📤 OUTGOING tests:  Supported");
                System.out.println("   📥 INCOMING tests: ⚠️  Will be SKIPPED");
                
                // Still initialize DeviceManager with A-Party
                DeviceManager.initializeDevices(
                    aPartyDeviceId, aPartyNumber,
                    null, null
                );
            }
        }
        
        DeviceManager.printDeviceStatus();
        
        // STEP 6: Start Appium
        System.out.println("\n🔧 STARTING APPIUM SERVICE...");
        DriverManager.startAppiumService();
        System.out.println(" Appium service started");
        
        // STEP 7: Initialize driver
        System.out.println("\n💬 INITIALIZING MESSAGING DRIVER FOR A-PARTY...");
        driver = DriverManager.initializeDriverForMessaging(aPartyDeviceId, platformVersion);
        
        if (driver == null) {
            throw new Exception("❌ Failed to initialize driver for A-Party");
        }
        
        System.out.println(" Driver initialized successfully");
        
        // STEP 8: Initialize SMS executor
        System.out.println("\n🔧 INITIALIZING SMS TEST EXECUTOR...");
        smsExecutor = new CompleteSMSTestExecutor(driver, aPartyDeviceId);
        System.out.println(" SMS Test Executor ready");
        
        // STEP 9: Report setup complete
        ProgressReporter.reportSMSProgress(
            aPartyDeviceId,
            "",
            "READY",
            "SMS test environment ready",
            5.0
        );
        
        System.out.println("\n SMS TEST SETUP COMPLETED SUCCESSFULLY ");
        System.out.println("=".repeat(100) + "\n");
    }
    
    @Test(description = "Execute all SMS and Voice tests from Excel", priority = 1)
    public void testAllSMSAndVoiceScenarios() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🚀 EXECUTING ALL SMS & VOICE MESSAGE TESTS");
        System.out.println("=".repeat(100));
        
        try {
            String excelFilePath = ConfigReader.getExcelFilePath();
            System.out.println("📄 Excel File: " + excelFilePath);
            
            // Report: Loading tests
            ProgressReporter.reportSMSProgress(
                aPartyDeviceId,
                "",
                "LOADING",
                "Reading SMS test data from Excel",
                10.0
            );
            
            // Execute all tests
            List<Map<String, Object>> results = smsExecutor.executeAllSMSTests(excelFilePath);
            
            // Print comprehensive summary
            printTestSummary(results);
            
            // Validate results
            validateTestResults(results);
            
            // Report completion
            long totalTests = results.size();
            long successfulTests = results.stream()
                .filter(r -> "SUCCESS".equals(r.get("finalStatus")))
                .count();
            
            double successRate = totalTests > 0 ? (successfulTests * 100.0 / totalTests) : 0;
            
            ProgressReporter.reportTestComplete(
                aPartyDeviceId,
                "sms",
                successfulTests == totalTests,
                String.format("Completed %d tests with %.1f%% success rate", 
                    totalTests, successRate)
            );
            
            // Assert soft assertions
            softAssert.assertAll();
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
            
            ProgressReporter.reportTestComplete(
                aPartyDeviceId,
                "sms",
                false,
                "Test execution failed: " + e.getMessage()
            );
            
            throw new AssertionError("SMS tests failed: " + e.getMessage(), e);
        }
    }
    
    /**
     *  PRINT COMPREHENSIVE TEST SUMMARY
     */
    private void printTestSummary(List<Map<String, Object>> results) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📈 TEST EXECUTION SUMMARY");
        System.out.println("=".repeat(100));
        
        if (results.isEmpty()) {
            System.out.println("⚠️  No tests were executed");
            return;
        }
        
        int total = results.size();
        
        // Status breakdown
        long passed = results.stream()
            .filter(r -> "SUCCESS".equals(r.get("finalStatus")))
            .count();
        
        long partial = results.stream()
            .filter(r -> "PARTIAL_SUCCESS".equals(r.get("finalStatus")))
            .count();
        
        long failed = results.stream()
            .filter(r -> "FAILED".equals(r.get("finalStatus")))
            .count();
        
        long error = results.stream()
            .filter(r -> "ERROR".equals(r.get("finalStatus")))
            .count();
        
        long skipped = results.stream()
            .filter(r -> "SKIPPED".equals(r.get("finalStatus")))
            .count();
        
        // Overall statistics
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ OVERALL TEST STATISTICS                                         │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ Total Tests:          " + String.format("%-41d", total) + "│");
        System.out.println("│  Passed:            " + String.format("%-41d", passed) + "│");
        System.out.println("│ ⚠️  Partial:           " + String.format("%-41d", partial) + "│");
        System.out.println("│ ❌ Failed:            " + String.format("%-41d", failed) + "│");
        System.out.println("│ 🚨 Error:             " + String.format("%-41d", error) + "│");
        System.out.println("│ ⭕ Skipped:           " + String.format("%-41d", skipped) + "│");
        
        if (total > 0) {
            double successRate = (passed * 100.0) / total;
            System.out.println("│ Success Rate:         " + String.format("%-38.1f%%", successRate) + "│");
        }
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // Message delivery statistics
        int totalSMS = results.stream()
            .mapToInt(r -> (Integer) r.getOrDefault("totalSMS", 0))
            .sum();
        
        int successfulSMS = results.stream()
            .mapToInt(r -> (Integer) r.getOrDefault("successfulSMS", 0))
            .sum();
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ MESSAGE DELIVERY STATISTICS                                     │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ Total Messages Sent:  " + String.format("%-41d", totalSMS) + "│");
        System.out.println("│  Delivered:         " + String.format("%-41d", successfulSMS) + "│");
        System.out.println("│ ❌ Failed:            " + String.format("%-41d", (totalSMS - successfulSMS)) + "│");
        
        if (totalSMS > 0) {
            double deliveryRate = (successfulSMS * 100.0) / totalSMS;
            System.out.println("│ Delivery Rate:        " + String.format("%-38.1f%%", deliveryRate) + "│");
        }
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // Direction breakdown
        long outgoing = results.stream()
            .filter(r -> "OUTGOING".equals(r.get("direction")))
            .count();
        
        long incoming = results.stream()
            .filter(r -> "INCOMING".equals(r.get("direction")))
            .count();
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ DIRECTION BREAKDOWN                                             │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 📤 OUTGOING:          " + String.format("%-41d", outgoing) + "│");
        System.out.println("│ 📥 INCOMING:          " + String.format("%-41d", incoming) + "│");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // Type breakdown
        long individual = results.stream()
            .filter(r -> (Boolean) r.getOrDefault("isIndividual", false))
            .count();
        
        long group = results.stream()
            .filter(r -> (Boolean) r.getOrDefault("isGroup", false))
            .count();
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ MESSAGE TYPE BREAKDOWN                                          │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 📱 Individual:        " + String.format("%-41d", individual) + "│");
        System.out.println("│ 👥 Group:             " + String.format("%-41d", group) + "│");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // Message format breakdown
        long textMessages = results.stream()
            .filter(r -> "text".equalsIgnoreCase((String) r.get("messageType")))
            .count();
        
        long voiceMessages = results.stream()
            .filter(r -> "voice".equalsIgnoreCase((String) r.get("messageType")))
            .count();
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ MESSAGE FORMAT BREAKDOWN                                        │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 💬 Text Messages:     " + String.format("%-41d", textMessages) + "│");
        System.out.println("│ 🎤 Voice Messages:    " + String.format("%-41d", voiceMessages) + "│");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // Delivery time analysis (for individual messages with timestamps)
        List<Long> deliveryTimes = results.stream()
            .filter(r -> r.containsKey("deliveryTimeMs"))
            .map(r -> ((Number) r.get("deliveryTimeMs")).longValue())
            .filter(t -> t > 0)
            .sorted()
            .toList();
        
        if (!deliveryTimes.isEmpty()) {
            long minTime = deliveryTimes.get(0);
            long maxTime = deliveryTimes.get(deliveryTimes.size() - 1);
            double avgTime = deliveryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            
            long withinSLA = deliveryTimes.stream().filter(t -> t <= 60000).count();
            long exceedsSLA = deliveryTimes.size() - withinSLA;
            
            System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
            System.out.println("│ DELIVERY TIME ANALYSIS                                          │");
            System.out.println("├─────────────────────────────────────────────────────────────────┤");
            System.out.println("│ Measured Deliveries:  " + String.format("%-41d", deliveryTimes.size()) + "│");
            System.out.println("│ ⏱️  Min Time:          " + String.format("%-35s", formatTime(minTime)) + "│");
            System.out.println("│ ⏱️  Max Time:          " + String.format("%-35s", formatTime(maxTime)) + "│");
            System.out.println("│ ⏱️  Avg Time:          " + String.format("%-35s", formatTime((long) avgTime)) + "│");
            System.out.println("│  Within SLA (≤60s): " + String.format("%-41d", withinSLA) + "│");
            System.out.println("│ ❌ Exceeds SLA (>60s):" + String.format("%-41d", exceedsSLA) + "│");
            System.out.println("└─────────────────────────────────────────────────────────────────┘");
        }
        
        // Balance deduction summary
        double totalDeduction = results.stream()
            .filter(r -> r.containsKey("balanceDeduction"))
            .mapToDouble(r -> ((Number) r.get("balanceDeduction")).doubleValue())
            .sum();
        
        if (totalDeduction > 0) {
            System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
            System.out.println("│ BALANCE DEDUCTION SUMMARY                                       │");
            System.out.println("├─────────────────────────────────────────────────────────────────┤");
            System.out.println("│ Total Deduction:      ₹" + String.format("%-38.2f", totalDeduction) + "│");
            System.out.println("│ Avg Per Message:      ₹" + String.format("%-38.2f", totalDeduction / successfulSMS) + "│");
            System.out.println("└─────────────────────────────────────────────────────────────────┘");
        }
        
        System.out.println("\n" + "=".repeat(100));
    }
    
    /**
     *  FORMAT TIME for display
     */
    private String formatTime(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + " ms";
        } else {
            double seconds = milliseconds / 1000.0;
            return String.format("%.2f s (%d ms)", seconds, milliseconds);
        }
    }
    
    /**
     *  VALIDATE TEST RESULTS
     */
    private void validateTestResults(List<Map<String, Object>> results) {
        System.out.println("\n🔍 VALIDATING TEST RESULTS...");
        
        for (Map<String, Object> result : results) {
            String testName = (String) result.get("name");
            String finalStatus = (String) result.get("finalStatus");
            
            if ("FAILED".equals(finalStatus) || "ERROR".equals(finalStatus)) {
                String comments = (String) result.getOrDefault("comments", "No details");
                softAssert.fail("Test failed: " + testName + " - " + comments);
            }
        }
        
        System.out.println(" Validation complete");
    }
    
    @AfterClass(alwaysRun = true)
    public void tearDown() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🧹 CLEANUP & TEARDOWN");
        System.out.println("=".repeat(100));
        
        try {
            if (smsExecutor != null) {
                smsExecutor.cleanup();
                System.out.println(" SMS Executor cleanup completed");
            }
        } catch (Exception e) {
            System.out.println("⚠️  SMS Executor cleanup issue: " + e.getMessage());
        }
        
        try {
            if (driver != null) {
                DriverManager.quitDriver();
                System.out.println(" Main driver quit successfully");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Main driver quit issue: " + e.getMessage());
        }
        
        try {
            DriverManager.stopAppiumService();
            System.out.println(" Appium service stopped");
        } catch (Exception e) {
            System.out.println("⚠️  Appium service stop issue: " + e.getMessage());
        }
        
        System.out.println("=".repeat(100));
        System.out.println(" TEARDOWN COMPLETE");
    }
}