package com.telecom.core;

import com.telecom.config.ConfigReader;
import com.telecom.pages.DataUsagePage;
import com.telecom.utils.*;
import io.appium.java_client.android.AndroidDriver;

import java.text.SimpleDateFormat;
import java.util.*;
import com.telecom.utils.ProgressReporter;

public class DataUsageTestExecutor {
    @SuppressWarnings("unused")
    private AndroidDriver driver;
    private String deviceId;
    private DataUsagePage dataUsagePage;
    private String deviceNumber;
    
    // ✅ NEW: Cache for USSD results (same as calling test)
    private Map<String, Map<String, Object>> lastPostDataUSSDCache = new HashMap<>();
    
    // USSD Configuration (same as calling test)
    private static final String USSD_CODE = "*199#";
    private static final long USSD_WAIT_BEFORE_DATA = 5000; 
    private static final long USSD_WAIT_AFTER_DATA = 8000; 
    private static final int MAX_USSD_RETRIES = 2;
    
    public DataUsageTestExecutor(AndroidDriver driver, String deviceId) {
        this.driver = driver;
        this.deviceId = deviceId;
        this.dataUsagePage = new DataUsagePage(driver);
        
        // ✅ Get device number from system properties
        this.deviceNumber = System.getProperty("DaPartyNumber");
        if (this.deviceNumber == null || this.deviceNumber.isEmpty()) {
            this.deviceNumber = ConfigReader.getDialingNumber();
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🌐 DATA USAGE TEST EXECUTOR INITIALIZED (WITH USSD)");
        System.out.println("=".repeat(80));
        System.out.println("Device ID: " + deviceId);
        System.out.println("Device Number: " + deviceNumber);
        System.out.println("=".repeat(80) + "\n");
    }
    
    public List<Map<String, Object>> executeDataUsageTests(String excelFilePath) {
        List<Map<String, Object>> testResults = new ArrayList<>();
        
        try {
            List<Map<String, Object>> dataTests = EnhancedExcelReader.readDataUsageTestData(excelFilePath, "DataUsage");
            
            if (dataTests.isEmpty()) {
                System.out.println("❌ No data usage test data found in Excel");
                return testResults;
            }
            
            System.out.println("🌐 Starting Data Usage Tests for " + dataTests.size() + " scenarios");
            
            // ✅ NEW: Initialize progress reporter for test suite
            ProgressReporter.initializeTestSuite(deviceId, dataTests.size());
            
            int testNumber = 1;
            for (Map<String, Object> dataTest : dataTests) {
                String scenario = (String) dataTest.get("scenario");
                
                System.out.println("\n" + "=".repeat(80));
                System.out.println("🌐 Test " + testNumber + "/" + dataTests.size() + ": " + scenario);
                System.out.println("=".repeat(80));
                
                // ✅ NEW: Report test start
                ProgressReporter.reportCallingProgress(
                    deviceId, 
                    deviceNumber, 
                    "START_DATA_TEST", 
                    "Running: " + scenario,
                    0,
                    (testNumber - 1) * 100.0 / dataTests.size()
                );
                
                Map<String, Object> result = executeSingleDataTest(dataTest);
                testResults.add(result);
                
                // ✅ NEW: Report test completion
                String status = (String) result.getOrDefault("finalStatus", "UNKNOWN");
                boolean success = "SUCCESS".equals(status) || "PARTIAL".equals(status);
                ProgressReporter.reportTestComplete(
                    deviceId,
                    "data",
                    success,
                    "Data test " + testNumber + " completed: " + scenario + " - Status: " + status
                );
                
                testNumber++;
                Thread.sleep(3000);
            }
            
            generateDataUsageReport(testResults);
            
        } catch (Exception e) {
            System.out.println("❌ Data usage test execution failed: " + e.getMessage());
            e.printStackTrace();
            
            // ✅ NEW: Report failure
            ProgressReporter.reportTestComplete(
                deviceId,
                "data",
                false,
                "Test suite failed: " + e.getMessage()
            );
        }
        
        return testResults;
    }
    
    
    private Map<String, Object> executeSingleDataTest(Map<String, Object> dataTest) {
        Map<String, Object> result = new HashMap<>();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        
        // ✅ NEW: Variable to track phone mismatch for report
        StringBuilder phoneMismatchWarning = new StringBuilder();
        
        try {
            result.putAll(dataTest);
            result.put("testTimestamp", timestamp);
            
            // ✅ Get APN details before starting
            Map<String, String> apnInfo = NetworkMonitor.getAPNInfo(deviceId);
            result.put("apnName", apnInfo.get("apnName"));
            result.put("apnType", apnInfo.get("apnType"));
            result.put("apnProtocol", apnInfo.get("apnProtocol"));
            
            Map<String, String> networkInfo = NetworkMonitor.getNetworkInfo(deviceId);
            result.put("networkType", networkInfo.get("networkType"));
            result.put("carrier", networkInfo.get("operator"));
            
            String scenario = getStringValue(dataTest.get("scenario"));
            double targetDataGB = getDoubleValue(dataTest.get("targetData"));
            int durationMinutes = getIntValue(dataTest.get("duration"));
            String appsToUse = getStringValue(dataTest.get("appsToUse"));
            
            System.out.println("🌐 Data Usage Test: " + scenario);
            System.out.println("   Target: " + targetDataGB + " GB, Duration: " + durationMinutes + " min");
            System.out.println("   Apps: " + appsToUse);
            System.out.println("   APN: " + apnInfo.get("apnName") + " (" + apnInfo.get("apn") + ")");
            System.out.println("   Network: " + networkInfo.get("networkType") + " | " + networkInfo.get("operator"));
            
         // ✅ NEW: Report test details
            ProgressReporter.reportCallingProgress(
                deviceId,
                deviceNumber,
                "DATA_TEST_CONFIG",
                "Configuring: " + scenario,
                0,
                10.0
            );
            
            // ✅ PRE-DATA USAGE BALANCE CHECK (using cached USSD)
            System.out.println("\n💰 PRE-DATA BALANCE CHECK...");
            
         // ✅ NEW: Report USSD check start
            ProgressReporter.reportCallingProgress(
                deviceId,
                deviceNumber,
                "BALANCE_CHECK",
                "Checking pre-data balance...",
                0,
                20.0
            );
            Map<String, Object> beforeUSSD = getOrPerformPreDataUSSD(deviceId, deviceNumber);
            
         // ✅ NEW: Report USSD check completion
            if (beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false)) {
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    deviceNumber,
                    "BALANCE_CHECK",
                    "Pre-balance: ₹" + beforeUSSD.get("balance"),
                    0,
                    30.0
                );
            }
            
            
            String detectedPhoneNumber = null;
            String expectedPhoneNumber = cleanNumber(deviceNumber);
            
            if (beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false)) {
                result.put("beforeBalance", beforeUSSD.get("balance"));
                result.put("beforeValidity", beforeUSSD.get("validity"));
                result.put("senderMSISDN", beforeUSSD.get("phoneNumber"));
                
                // ✅ Capture phone number for mismatch check
                detectedPhoneNumber = cleanNumber((String) beforeUSSD.get("phoneNumber"));
                result.put("ussdApartyNumber", detectedPhoneNumber);
                
                // Check for phone number mismatch
                if (detectedPhoneNumber != null && expectedPhoneNumber != null && 
                    !detectedPhoneNumber.equals(expectedPhoneNumber)) {
                    String mismatchMsg = "WARNING: Phone number mismatch! Expected: " + expectedPhoneNumber + 
                                        " Detected: " + detectedPhoneNumber;
                    phoneMismatchWarning.append(mismatchMsg);
                    System.out.println("   ⚠️ " + mismatchMsg);
                }
                
                // Mark if it was reused from cache
                if (beforeUSSD.containsKey("cachedFromPreviousTest")) {
                    result.put("preDataUSSDSource", "CACHED");
                    System.out.println("   ♻️ Reused from previous test");
                } else {
                    result.put("preDataUSSDSource", "NEW_CHECK");
                }
                
                System.out.println("   ✅ Before Balance: ₹" + beforeUSSD.get("balance"));
                if (beforeUSSD.get("validity") != null) {
                    System.out.println("   📅 Validity: " + beforeUSSD.get("validity"));
                }
            } else {
                System.out.println("   ⚠️ USSD check failed after retries");
                result.put("beforeBalance", "N/A");
                result.put("ussdCheckFailed", true);
                result.put("ussdApartyNumber", "N/A");
            }
            
            Thread.sleep(USSD_WAIT_BEFORE_DATA);
            
            // ✅ Execute data usage scenario and get ACCURATE results from /proc/net/dev
            System.out.println("\n📡 Starting data consumption...");
            
            // ✅ NEW: Report data consumption start
            ProgressReporter.reportCallingProgress(
                deviceId,
                deviceNumber,
                "DATA_CONSUMPTION",
                "Starting data consumption...",
                0,
                40.0
            );
            
            Map<String, Object> testResults = dataUsagePage.executeDataUsageScenario(
                scenario, targetDataGB, durationMinutes, appsToUse, deviceId
            );
            
            // Extract accurate consumption data from /proc/net/dev (ccmni1)
            boolean trafficGenerated = (Boolean) testResults.getOrDefault("success", false);
            long consumedBytes = (Long) testResults.getOrDefault("consumedBytes", 0L);
            double consumedMB = (Double) testResults.getOrDefault("consumedMB", 0.0);
            double consumedGB = (Double) testResults.getOrDefault("consumedGB", 0.0);
            double consumedRxMB = (Double) testResults.getOrDefault("consumedRxMB", 0.0);
            double consumedTxMB = (Double) testResults.getOrDefault("consumedTxMB", 0.0);
            long initialRxBytes = (Long) testResults.getOrDefault("initialRxBytes", 0L);
            long initialTxBytes = (Long) testResults.getOrDefault("initialTxBytes", 0L);
            long finalRxBytes = (Long) testResults.getOrDefault("finalRxBytes", 0L);
            long finalTxBytes = (Long) testResults.getOrDefault("finalTxBytes", 0L);
            boolean targetAchieved = (Boolean) testResults.getOrDefault("targetAchieved", false);
            double achievementPercent = (Double) testResults.getOrDefault("achievementPercent", 0.0);
            String status = (String) testResults.getOrDefault("status", "FAILED");
            
            if (!trafficGenerated) {
                System.out.println("⚠️ Traffic generation had issues, but measurement completed");
            }
            
            System.out.println("📊 Consumed: " + String.format("%.4f GB (%.2f MB)", consumedGB, consumedMB));
            
            if (consumedBytes > 0) {
                System.out.println("✅ Data consumption detected: " + consumedBytes + " bytes");
            }
            
            // Populate result with ACCURATE data from /proc/net/dev
            result.put("initialData", String.format("%.2f MB", 
                (initialRxBytes + initialTxBytes) / (1024.0 * 1024.0)));
            
            result.put("finalData", String.format("%.2f MB", 
                (finalRxBytes + finalTxBytes) / (1024.0 * 1024.0)));
            
            result.put("consumedData", String.format("%.2f MB (%.4f GB)", consumedMB, consumedGB));
            
            result.put("targetAchieved", targetAchieved);
            result.put("finalStatus", status);
            result.put("dataSource", "ccmni1 (/proc/net/dev)");
            result.put("trafficGenerated", trafficGenerated ? "YES" : "NO");
            
            // ✅ POST-DATA USAGE BALANCE CHECK
            String afterBalance = "N/A";
            if (deviceId != null) {
                System.out.println("\n💰 POST-DATA BALANCE CHECK...");
                System.out.println("   ⏳ Waiting " + (USSD_WAIT_AFTER_DATA/1000) + " seconds for balance update...");
                
             // ✅ NEW: Report waiting for balance update
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    deviceNumber,
                    "BALANCE_UPDATE",
                    "Waiting for balance update...",
                    0,
                    90.0
                );
                
                Thread.sleep(USSD_WAIT_AFTER_DATA);
                
                // ✅ NEW: Report post-balance check
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    deviceNumber,
                    "POST_BALANCE_CHECK",
                    "Checking post-data balance...",
                    0,
                    95.0
                );
                
                Map<String, Object> afterUSSD = performPostDataUSSDCheck(deviceId, deviceNumber);
                
                if (afterUSSD != null && (Boolean) afterUSSD.getOrDefault("success", false)) {
                    afterBalance = afterUSSD.get("balance").toString();
                    result.put("afterBalance", afterBalance);
                    result.put("afterValidity", afterUSSD.get("validity"));
                    
                    System.out.println("   ✅ After Balance: ₹" + afterBalance);
                    
                    // ✅ CACHE THIS RESULT FOR NEXT TEST
                    cachePostDataUSSDForNextTest(deviceId, afterUSSD);
                    
                    // Calculate balance deduction
                    if (beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false)) {
                        try {
                            Double beforeBal = parseBalance(beforeUSSD.get("balance"));
                            Double afterBal = parseBalance(afterBalance);
                            
                            if (beforeBal != null && afterBal != null) {
                                double deduction = beforeBal - afterBal;
                                result.put("balanceDeduction", deduction);
                                System.out.println("   💸 Balance Deduction: ₹" + String.format("%.2f", deduction));
                            }
                        } catch (Exception e) {
                            System.out.println("   ⚠️ Could not calculate balance deduction: " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("   ⚠️ After-balance USSD check failed (non-critical)");
                    // Clear cache on failure
                    lastPostDataUSSDCache.remove(deviceId);
                }
            }
            
            // ✅ Build detailed comments with phone mismatch warning at the beginning
            StringBuilder commentsBuilder = new StringBuilder();
            
            // Add phone mismatch warning if present
            if (phoneMismatchWarning.length() > 0) {
                commentsBuilder.append(phoneMismatchWarning.toString()).append(" | ");
            }
            
            // Add data consumption details
            commentsBuilder.append(String.format(
                "RX: %.2f MB, TX: %.2f MB, Total: %.2f MB (%.1f%% of target %d MB) | Source: ccmni1",
                consumedRxMB,
                consumedTxMB,
                consumedMB,
                achievementPercent,
                (int)(targetDataGB * 1024)
            ));
            
         // ✅ ADD APN DETAILS TO COMMENTS - This is the key change
            if (apnInfo != null && apnInfo.containsKey("apnDetails")) {
                commentsBuilder.append(" | ").append(apnInfo.get("apnDetails"));
            } else if (apnInfo != null) {
                // Fallback to individual APN fields
                commentsBuilder.append(String.format(" | APN: %s (%s)", 
                    apnInfo.get("apnName"), 
                    apnInfo.get("apn")));
            }
         // Add network info to comments
            if (networkInfo != null && !networkInfo.isEmpty()) {
                commentsBuilder.append(String.format(" | Network: %s (%s)", 
                    networkInfo.get("networkType"), 
                    networkInfo.get("operator")));
            }
            
            String comments = commentsBuilder.toString();
            result.put("comments", comments);
            
            // Additional metrics for reporting
            result.put("consumedBytes", consumedBytes);
            result.put("consumedMB", consumedMB);
            result.put("consumedGB", consumedGB);
            result.put("achievementPercent", achievementPercent);
            
            // ✅ Ensure all required columns are present for the report
            result.put("apartyNumber", expectedPhoneNumber); 
            result.put("targetData", targetDataGB);
            result.put("duration", durationMinutes);
            result.put("apps", appsToUse);
            result.put("apn", apnInfo.get("apnName") + " (" + apnInfo.get("apn") + ")");
            result.put("networkType", networkInfo.get("networkType"));
            result.put("carrier", networkInfo.get("operator"));  
            result.put("timestamp", timestamp);
            
         // ✅ NEW: Report test completion
            ProgressReporter.reportCallingProgress(
                deviceId,
                deviceNumber,
                "TEST_COMPLETE",
                "Test completed: " + status,
                0,
                100.0
            );
            
            System.out.println("✅ Data usage test completed - Status: " + status);
            
        } catch (Exception e) {
            System.out.println("❌ Data usage test failed: " + e.getMessage());
            e.printStackTrace();
            
         // ✅ NEW: Report failure
            ProgressReporter.reportTestComplete(
                deviceId,
                "data",
                false,
                "Test failed: " + e.getMessage()
            );
            
            result.put("finalStatus", "ERROR");
            result.put("comments", "Error: " + e.getMessage());
            result.put("consumedData", "0.00 MB (0.0000 GB)");
            result.put("targetAchieved", false);
            result.put("initialData", "0.00 MB");
            result.put("finalData", "0.00 MB");
            result.put("dataSource", "error");
            result.put("achievementPercent", 0.0);
            result.put("beforeBalance", "N/A");
            result.put("afterBalance", "N/A");
            result.put("balanceDeduction", 0.0);
            result.put("ussdApartyNumber", "N/A");
            result.put("apartyNumber", cleanNumber(deviceNumber));
            result.put("targetData", getDoubleValue(dataTest.get("targetData")));
            result.put("duration", getIntValue(dataTest.get("duration")));
            result.put("apps", getStringValue(dataTest.get("appsToUse")));
            result.put("timestamp", timestamp);
        }
        
        return result;
    }
    
    // ✅ NEW: Cache POST-DATA USSD result for next test
    private void cachePostDataUSSDForNextTest(String deviceId, Map<String, Object> postDataUSSD) {
        if (postDataUSSD != null && (Boolean) postDataUSSD.getOrDefault("success", false)) {
            Map<String, Object> cacheEntry = new HashMap<>(postDataUSSD);
            cacheEntry.put("cachedTimestamp", System.currentTimeMillis());
            
            lastPostDataUSSDCache.put(deviceId, cacheEntry);
            
            System.out.println("   💾 Cached balance for next test:");
            System.out.println("      Device: " + deviceId);
            System.out.println("      Balance: ₹" + cacheEntry.get("balance"));
            System.out.println("      Phone: " + cacheEntry.get("phoneNumber"));
        }
    }
    
    // ✅ NEW: Get PRE-DATA USSD (reuse POST-DATA from previous test if available)
    private Map<String, Object> getOrPerformPreDataUSSD(String deviceId, String phoneNumber) {
        try {
            // Check if we have cached POST-DATA USSD from previous test for this device
            if (lastPostDataUSSDCache.containsKey(deviceId)) {
                Map<String, Object> cachedUSSD = lastPostDataUSSDCache.get(deviceId);
                
                System.out.println("   ♻️ REUSING POST-DATA balance from previous test");
                System.out.println("      Device: " + deviceId);
                System.out.println("      Cached Balance: ₹" + cachedUSSD.get("balance"));
                
                // Verify phone number matches
                String cachedNumber = (String) cachedUSSD.get("phoneNumber");
                if (cachedNumber != null && phoneNumber != null) {
                    String cleanExpected = cleanNumber(phoneNumber);
                    String cleanCached = cleanNumber(cachedNumber);
                    
                    if (cleanExpected.equals(cleanCached)) {
                        System.out.println("      ✅ Phone number verified: " + cleanCached);
                        
                        // Mark this as cached and return
                        cachedUSSD.put("cachedFromPreviousTest", true);
                        return cachedUSSD;
                    } else {
                        System.out.println("      ⚠️ Phone number mismatch - performing fresh check");
                        System.out.println("         Expected: " + cleanExpected);
                        System.out.println("         Cached: " + cleanCached);
                    }
                }
            }
            
            // No cache available or phone mismatch - perform fresh USSD check
            System.out.println("   📞 Performing fresh balance check...");
            Map<String, Object> ussdResult = performUSSDCheckWithRetry(deviceId, USSD_CODE, "BEFORE", phoneNumber);
            
            if (ussdResult != null && ussdResult.containsKey("deviceDisconnected") 
                && (Boolean) ussdResult.get("deviceDisconnected")) {
                System.out.println("   ❌ Device disconnected during pre-data USSD check");
                return ussdResult;
            }
            
            if (ussdResult != null && (Boolean) ussdResult.getOrDefault("success", false)) {
                String detectedNumber = (String) ussdResult.get("phoneNumber");
                if (detectedNumber != null && phoneNumber != null) {
                    String cleanExpected = cleanNumber(phoneNumber);
                    String cleanDetected = cleanNumber(detectedNumber);
                    
                    if (!cleanExpected.equals(cleanDetected)) {
                        System.out.println("   ⚠️ WARNING: Phone number mismatch!");
                        System.out.println("      Expected: " + cleanExpected);
                        System.out.println("      Detected: " + cleanDetected);
                    } else {
                        System.out.println("   ✅ Phone number verified: " + cleanDetected);
                    }
                }
            }
            
            return ussdResult;
            
        } catch (Exception e) {
            System.out.println("   ❌ Pre-data USSD check error: " + e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
    
    // ✅ NEW: Perform post-data USSD check
    private Map<String, Object> performPostDataUSSDCheck(String deviceId, String phoneNumber) {
        try {
            System.out.println("   📞 Checking balance after data usage...");
            Map<String, Object> ussdResult = performUSSDCheckWithRetry(deviceId, USSD_CODE, "AFTER", phoneNumber);
            return ussdResult;
        } catch (Exception e) {
            System.out.println("   ❌ Post-data USSD check error: " + e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
    
    // ✅ REUSED FROM CALLING TEST: Perform USSD check with retry
    private Map<String, Object> performUSSDCheckWithRetry(
            String deviceId, String ussdCode, String checkType, String expectedNumber) {
        
        System.out.println("\n💰 " + checkType + " BALANCE CHECK (with retry)...");
        
     // ✅ NEW: Report USSD check start
        ProgressReporter.reportCallingProgress(
            deviceId,
            expectedNumber,
            "USSD_CHECK",
            "Attempting USSD check: " + checkType,
            0,
            0.0
        );
        
        Map<String, Object> ussdResult = null;
        int attempt = 0;
        
        while (attempt < MAX_USSD_RETRIES) {
            attempt++;
            
            try {
                System.out.println("   🔄 Attempt " + attempt + "/" + MAX_USSD_RETRIES);
                
                // ✅ NEW: Report retry attempt
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    expectedNumber,
                    "USSD_CHECK_RETRY",
                    "Attempt " + attempt + "/" + MAX_USSD_RETRIES,
                    attempt,
                    (attempt * 100.0 / MAX_USSD_RETRIES) * 50.0
                );
                
                if (!ADBHelper.isDeviceConnected(deviceId)) {
                    System.out.println("   ❌ Device disconnected: " + deviceId);
                    
                    // ✅ NEW: Report device disconnected
                    ProgressReporter.reportCallingProgress(
                        deviceId,
                        expectedNumber,
                        "DEVICE_DISCONNECTED",
                        "Device disconnected during USSD check",
                        0,
                        0.0
                    );
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "Device disconnected");
                    errorResult.put("deviceDisconnected", true);
                    return errorResult;
                }
                
                ussdResult = USSDService.checkBalanceAndValidity(deviceId, ussdCode);
                
                if (ussdResult != null && (Boolean) ussdResult.getOrDefault("success", false)) {
                	 // ✅ NEW: Report successful USSD
                    ProgressReporter.reportCallingProgress(
                        deviceId,
                        expectedNumber,
                        "USSD_SUCCESS",
                        "Balance check successful",
                        0,
                        100.0
                    );
                    Object balanceObj = ussdResult.get("balance");
                    Object phoneObj = ussdResult.get("phoneNumber");
                    Object simObj = ussdResult.get("sim");
                    
                    if (balanceObj != null) {
                        String balanceStr = balanceObj.toString();
                        if (balanceStr.startsWith("Rs ")) {
                            balanceStr = balanceStr.substring(3).trim();
                            ussdResult.put("balance", balanceStr);
                        }
                        
                        try {
                            Double numericBalance = parseBalance(balanceStr);
                            ussdResult.put("balanceNumeric", numericBalance);
                        } catch (Exception e) {
                            System.out.println("   ⚠️ Could not parse balance: " + balanceStr);
                        }
                    }
                    
                    String phoneNumber = phoneObj != null ? phoneObj.toString() : 
                                        (simObj != null ? simObj.toString() : null);
                    
                    if (phoneNumber != null) {
                        ussdResult.put("phoneNumber", phoneNumber);
                    }
                    
                    System.out.println("   ✅ USSD SUCCESS");
                    System.out.println("      Phone: " + phoneNumber);
                    System.out.println("      Balance: " + ussdResult.get("balance"));
                    
                    if (ussdResult.get("validity") != null) {
                        System.out.println("      Validity: " + ussdResult.get("validity"));
                    }
                    
                    return ussdResult;
                }
                
                System.out.println("   ❌ USSD API returned error");
                if (attempt < MAX_USSD_RETRIES) {
                    System.out.println("   ⏳ Waiting 3s before retry...");
                    Thread.sleep(3000);
                }
                
            } catch (Exception e) {
            	
            	// ✅ NEW: Report USSD error
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    expectedNumber,
                    "USSD_ERROR",
                    "USSD attempt " + attempt + " failed: " + e.getMessage(),
                    attempt,
                    0.0
                );
                System.out.println("   ❌ USSD attempt failed: " + e.getMessage());
                
                if (attempt < MAX_USSD_RETRIES) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        System.out.println("   ⚠️ USSD check failed after " + MAX_USSD_RETRIES + " attempts");
        
        // ✅ NEW: Report USSD failure after retries
        ProgressReporter.reportCallingProgress(
            deviceId,
            expectedNumber,
            "USSD_FAILED",
            "USSD check failed after " + MAX_USSD_RETRIES + " attempts",
            MAX_USSD_RETRIES,
            0.0
        );
        
        Map<String, Object> failureResult = new HashMap<>();
        failureResult.put("success", false);
        failureResult.put("error", "USSD check failed after " + MAX_USSD_RETRIES + " attempts");
        
        if (!ADBHelper.isDeviceConnected(deviceId)) {
            failureResult.put("deviceDisconnected", true);
        }
        
        return failureResult;
    }
    
    // ✅ REUSED FROM CALLING TEST: Parse balance
    private Double parseBalance(Object balanceObj) {
        if (balanceObj == null) return null;
        
        try {
            String balanceStr = balanceObj.toString();
            
            // Remove common prefixes/suffixes
            balanceStr = balanceStr.replace("Rs", "")
                                  .replace("₹", "")
                                  .replace("INR", "")
                                  .trim();
            
            return Double.parseDouble(balanceStr);
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Could not parse balance: " + balanceObj);
            return null;
        }
    }
    
    // ✅ REUSED FROM CALLING TEST: Clean phone number
    private String cleanNumber(String number) {
        if (number == null) return "";
        
        // Remove all non-digit characters
        String cleaned = number.replaceAll("[^0-9]", "");
        
        // Extract last 10 digits (Indian mobile number format)
        if (cleaned.length() >= 10) {
            return cleaned.substring(cleaned.length() - 10);
        }
        
        return cleaned;
    }
    
    /**
     * ✅ Generate data usage reports with ACCURATE data
     */
    private void generateDataUsageReport(List<Map<String, Object>> results) {
        try {
            System.out.println("\n📈 Generating Data Usage Reports...");
            
            // Calculate summary statistics using ACCURATE data
            int totalTests = results.size();
            int successCount = 0;
            int partialCount = 0;
            int failedCount = 0;
            double totalConsumedGB = 0;
            double totalTargetGB = 0;
            
            for (Map<String, Object> result : results) {
                String status = getStringValue(result.get("finalStatus"));
                if ("SUCCESS".equals(status)) successCount++;
                else if ("PARTIAL".equals(status)) partialCount++;
                else failedCount++;
                
                // Get consumed data directly from the accurate measurement
                if (result.containsKey("consumedGB")) {
                    totalConsumedGB += (Double) result.get("consumedGB");
                } else {
                    // Fallback parsing if needed
                    String consumedStr = getStringValue(result.get("consumedData"));
                    if (consumedStr.contains("GB")) {
                        // Extract GB value from format "XX.XX MB (0.XXXX GB)"
                        int gbStart = consumedStr.indexOf("(");
                        int gbEnd = consumedStr.indexOf("GB");
                        if (gbStart > 0 && gbEnd > gbStart) {
                            String gbValue = consumedStr.substring(gbStart + 1, gbEnd).trim();
                            totalConsumedGB += Double.parseDouble(gbValue);
                        }
                    }
                }
                
                // Get target data
                totalTargetGB += getDoubleValue(result.get("targetData"));
            }
            
            // Calculate overall achievement
            double overallAchievement = totalTargetGB > 0 ? (totalConsumedGB / totalTargetGB * 100.0) : 0.0;
            
            // Print summary
            System.out.println("📊 DATA USAGE TEST SUMMARY");
            System.out.println("=".repeat(50));
            System.out.println("   Total Tests: " + totalTests);
            System.out.println("   ✅ Success: " + successCount);
            System.out.println("   ⚠️ Partial: " + partialCount);
            System.out.println("   ❌ Failed: " + failedCount);
            System.out.println("   📱 Total Consumed: " + String.format("%.4f GB (%.2f MB)", 
                totalConsumedGB, totalConsumedGB * 1024));
            System.out.println("   🎯 Total Target: " + String.format("%.2f GB", totalTargetGB));
            System.out.println("   📈 Success Rate: " + String.format("%.1f%%", (successCount * 100.0 / totalTests)));
            System.out.println("   🏆 Achievement: " + String.format("%.1f%%", overallAchievement));
            System.out.println("=".repeat(50));
            
            // Generate detailed reports
            String excelReport = ReportGenerator.generateDataUsageExcelReport(results);
            String htmlReport = ReportGenerator.generateDataUsageHTMLReport(results);
            
            System.out.println("\n✅ Data Usage Reports Generated:");
            System.out.println("   📄 Excel: " + excelReport);
            System.out.println("   🌐 HTML: " + htmlReport);
            
        } catch (Exception e) {
            System.out.println("❌ Report generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String getStringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
    
    private int getIntValue(Object value) {
        if (value == null) return 0;
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private double getDoubleValue(Object value) {
        if (value == null) return 0.0;
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}