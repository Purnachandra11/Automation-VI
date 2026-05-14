package com.telecom.tests;

import com.telecom.core.SIMAutoLatchTestExecutor;
import com.telecom.utils.ADBHelper;
import org.testng.annotations.*;
import java.util.List;
import java.util.Map;

public class SIMAutoLatchTestSuite {
    
    private String aPartyDeviceId;
    private String bPartyDeviceId;
    private String aPartyNumber;
    private String bPartyNumber;
    
    @BeforeClass(alwaysRun = true)
    public void setup() throws Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📡 SIM AUTO-LATCH TEST SUITE SETUP");
        System.out.println("=".repeat(100));
        
        // Get device parameters
        aPartyDeviceId = System.getProperty("aPartyDevice");
        bPartyDeviceId = System.getProperty("bPartyDevice");
        aPartyNumber = System.getProperty("aPartyNumber", "8696904544");
        bPartyNumber = System.getProperty("bPartyNumber", "9773328866");
        
        // Auto-detect devices if not provided
        if (aPartyDeviceId == null || aPartyDeviceId.isEmpty()) {
            List<String> devices = ADBHelper.getConnectedDevices();
            if (devices.isEmpty()) {
                throw new Exception("❌ No device connected");
            }
            aPartyDeviceId = devices.get(0);
            if (devices.size() > 1) {
                bPartyDeviceId = devices.get(1);
            }
        }
        
        System.out.println("📱 Configuration:");
        System.out.println("   A-Party Device: " + aPartyDeviceId);
        System.out.println("   A-Party Number: " + aPartyNumber);
        System.out.println("   B-Party Device: " + bPartyDeviceId);
        System.out.println("   B-Party Number: " + bPartyNumber);
        
        System.out.println(" Setup completed successfully\n");
    }
    
    @Test(description = "Execute all SIM auto-latch tests from Excel")
    public void executeSIMAutoLatchTests() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📡 EXECUTING SIM AUTO-LATCH TESTS");
        System.out.println("=".repeat(100));
        
        try {
            // Use hardcoded path instead of ConfigReader
            String excelFilePath = "src/test/resources/contacts.xlsx";
            System.out.println("📄 Excel File: " + excelFilePath);
            
            // Initialize executor with both phone numbers
            SIMAutoLatchTestExecutor executor = new SIMAutoLatchTestExecutor(
                aPartyDeviceId, 
                bPartyDeviceId,
                aPartyNumber,
                bPartyNumber
            );
            
            // Execute all tests
            List<Map<String, Object>> results = executor.executeAllSIMAutoLatchTests(excelFilePath);
            
            // Print summary
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📈 SIM AUTO-LATCH TESTS COMPLETED");
            System.out.println("Total Tests: " + results.size());
            
            long passed = results.stream()
                .filter(r -> "PASS".equals(r.get("testResult")))
                .count();
            
            long marginal = results.stream()
                .filter(r -> "MARGINAL".equals(r.get("testResult")))
                .count();
            
            long slow = results.stream()
                .filter(r -> "SLOW".equals(r.get("testResult")))
                .count();
            
            long failed = results.stream()
                .filter(r -> "FAIL".equals(r.get("testResult")) || "ERROR".equals(r.get("testResult")))
                .count();
            
            System.out.println(" PASS (< 30s): " + passed);
            System.out.println("⚠️  MARGINAL (30-60s): " + marginal);
            System.out.println("🐌 SLOW (> 60s): " + slow);
            System.out.println("❌ FAILED: " + failed);
            
            if (results.size() > 0) {
                double successRate = ((passed + marginal) * 100.0) / results.size();
                System.out.println("\n📊 Overall Success Rate: " + String.format("%.1f%%", successRate));
            }
            
            // Display sample output format
            System.out.println("\n📋 EXPECTED OUTPUT FORMAT:");
            System.out.println("=".repeat(120));
            System.out.println("Device ID\tParty Number\tTimeout(s)\tInitial Network\tFinal Network\tAuto-Latch Time(ms)\tAuto-Latch Time(s)\tTest Result\tTransitions\tIMS Registered\tisRoaming\tComments");
            System.out.println("-".repeat(120));
            
            // Display first few results as example
            int count = Math.min(results.size(), 3);
            for (int i = 0; i < count; i++) {
                Map<String, Object> result = results.get(i);
                System.out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                    result.getOrDefault("deviceId", ""),
                    result.getOrDefault("partyNumber", ""),
                    result.getOrDefault("timeoutSeconds", ""),
                    result.getOrDefault("initialNetworkState", ""),
                    result.getOrDefault("finalNetworkState", ""),
                    result.getOrDefault("autoLatchTimeMs", ""),
                    result.getOrDefault("autoLatchTimeSeconds", ""),
                    result.getOrDefault("testResult", ""),
                    result.getOrDefault("transitions", ""),
                    result.getOrDefault("finalIMSRegistered", ""),
                    "TRUE", // Placeholder for roaming status
                    result.getOrDefault("comments", "")
                );
            }
            
            if (results.size() > 3) {
                System.out.println("... (" + (results.size() - 3) + " more results)");
            }
            
            System.out.println("=".repeat(100));
            
        } catch (Exception e) {
            System.out.println("❌ SIM auto-latch test execution failed: " + e.getMessage());
            e.printStackTrace();
            throw new AssertionError("SIM auto-latch tests failed: " + e.getMessage());
        }
    }
    
    @AfterClass(alwaysRun = true)
    public void tearDown() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🧹 CLEANUP");
        System.out.println("=".repeat(100));
        System.out.println(" Test execution completed");
    }
}