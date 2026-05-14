package com.telecom.core;

import com.telecom.config.ConfigReader;
import com.telecom.driver.DriverManager;
import com.telecom.pages.ImprovedDialerPage;
import com.telecom.pages.WorkingVideoCallDialer;
import com.telecom.utils.*;
import io.appium.java_client.android.AndroidDriver;
import java.text.SimpleDateFormat;
import java.util.*;
import com.telecom.utils.ProgressReporter;

public class CompleteCallingTestExecutor {
    
    private AndroidDriver driver;
    @SuppressWarnings("unused")
    private ImprovedDialerPage dialerPage;
    @SuppressWarnings("unused")
    private WorkingVideoCallDialer videoDialerPage;
    private String aPartyDeviceId;
    private String bPartyDeviceId;
    private String aPartyNumber;
    private String bPartyNumber;
    private List<Map<String, Object>> testResults = new ArrayList<>();
    
    //  NEW: Cache for previous test's POST-CALL USSD result
    private Map<String, Map<String, Object>> lastPostCallUSSDCache = new HashMap<>();
    
    // USSD Configuration
    private static final String USSD_CODE = "*199#";
    private static final long USSD_WAIT_BEFORE_CALL = 5000; 
    private static final long USSD_WAIT_AFTER_CALL = 8000; 
    private static final int MAX_USSD_RETRIES = 2;
    public CompleteCallingTestExecutor(AndroidDriver driver, String aPartyDeviceId) {
    	  this.driver = driver;
          this.aPartyDeviceId = aPartyDeviceId;
          this.dialerPage = new ImprovedDialerPage(driver);
          this.videoDialerPage = new WorkingVideoCallDialer(driver);
          
          initializeDeviceInfo();
      }
    
    /**
     *  Initialize device and numbers from system properties
     */
    private void initializeDeviceInfo() {
        this.aPartyNumber = System.getProperty("aPartyNumber", ConfigReader.getDialingNumber());
        this.bPartyDeviceId = System.getProperty("bPartyDevice");
        this.bPartyNumber = System.getProperty("bPartyNumber");
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📱 CALLING TEST EXECUTOR INITIALIZED (OPTIMIZED USSD)");
        System.out.println("=".repeat(100));
        System.out.println("A-Party Device: " + aPartyDeviceId);
        System.out.println("A-Party Number: " + aPartyNumber);
        System.out.println("B-Party Device: " + bPartyDeviceId);
        System.out.println("B-Party Number: " + bPartyNumber);
        System.out.println("=".repeat(100) + "\n");
        
        if (bPartyDeviceId != null && bPartyNumber != null) {
            System.out.println("🔧 Initializing DeviceManager with devices...");
            DeviceManager.initializeDevices(
                aPartyDeviceId, aPartyNumber, bPartyDeviceId, bPartyNumber
            );
            DeviceManager.printDeviceStatus();
        } else {
            System.out.println("⚠️ B-Party information not provided - auto-answer will not be available");
        }
    }
    
    /**
     *  MAIN EXECUTION METHOD
     */
    public List<Map<String, Object>> executeAllCallingTests(String excelFilePath) {
        testResults.clear();
        lastPostCallUSSDCache.clear(); // Clear cache at start
        
        try {
            List<Map<String, Object>> testCases = EnhancedExcelReader.readCallingTestData(excelFilePath);
            
            if (testCases.isEmpty()) {
                System.out.println("❌ No calling test data found");
                return testResults;
            }
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("🚀 STARTING CALLING TEST EXECUTION");
            System.out.println("📊 Total Tests: " + testCases.size());
            System.out.println("=".repeat(100) + "\n");
            
            //  Initialize progress reporter for test suite
            ProgressReporter.initializeTestSuite(aPartyDeviceId, testCases.size());
            
            // Check VoLTE status before starting
            System.out.println("📡 Checking VoLTE Status for Both Parties...");
            Map<String, Map<String, String>> volteStatuses = 
                ImprovedVoLTEManager.getVoLTEStatusBothParties(aPartyDeviceId, bPartyDeviceId);
            
         //  Report progress for VoLTE check
            ProgressReporter.reportCallingProgress(
                aPartyDeviceId, 
                aPartyNumber, 
                "VoLTE Status Check", 
                "IN_PROGRESS", 
                0, 
                0.0
            );
            
            Map<String, String> aPartyVolte = volteStatuses.getOrDefault("aParty", new HashMap<>());
            Map<String, String> bPartyVolte = volteStatuses.getOrDefault("bParty", new HashMap<>());
            
            // Execute each test
            for (int i = 0; i < testCases.size(); i++) {
                Map<String, Object> testCase = testCases.get(i);
                
                System.out.println("\n" + "=".repeat(100));
                System.out.println("📞 TEST " + (i + 1) + "/" + testCases.size() + ": " + testCase.get("name"));
                System.out.println("Network: " + testCase.get("preferredNetwork") + 
                                 " | Type: " + testCase.get("callType") + 
                                 " | Direction: " + testCase.get("direction"));
                System.out.println("=".repeat(100));
                
             //  Report test start progress
                double testProgress = (i * 100.0) / testCases.size();
                ProgressReporter.reportCallingProgress(
                    aPartyDeviceId,
                    aPartyNumber,
                    "Starting Test " + (i + 1) + "/" + testCases.size(),
                    "STARTING",
                    0,
                    testProgress
                );
                
                Map<String, Object> result = executeSingleCallingTest(
                    testCase, aPartyVolte, bPartyVolte
                );
                testResults.add(result);
                
                Thread.sleep(3000);
                
             //  Report test completion progress
                testProgress = ((i + 1) * 100.0) / testCases.size();
                String status = result.getOrDefault("finalStatus", "UNKNOWN").toString();
                ProgressReporter.reportCallingProgress(
                    aPartyDeviceId,
                    aPartyNumber,
                    "Completed Test " + (i + 1) + "/" + testCases.size(),
                    status,
                    0,
                    testProgress
                );
            }
            
            generateReports();
            printSummary();
            
            //  Report overall completion
            ProgressReporter.reportTestComplete(
                aPartyDeviceId,
                "calling",
                true,
                "Completed " + testCases.size() + " calling tests successfully"
            );
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
        
            //  Report error completion
            ProgressReporter.reportTestComplete(
                aPartyDeviceId,
                "calling",
                false,
                "Test execution failed: " + e.getMessage()
            );
        
        }
        
        return testResults;
    }
    
    /**
     *  OPTIMIZED: Execute single calling test with USSD caching
     */
    private Map<String, Object> executeSingleCallingTest(
            Map<String, Object> testCase,
            Map<String, String> aPartyVolte,
            Map<String, String> bPartyVolte) {
        
        Map<String, Object> result = new HashMap<>();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        
        try {
            result.putAll(testCase);
            result.put("testTimestamp", timestamp);
            
            // Check if Excel numbers have device mappings
            // Get test parameters
            String callType = (String) testCase.get("callType");
           
            String excelAParty = (String) testCase.get("aPartyNumber");
            String excelBParty = (String) testCase.get("bPartyNumber");
            String cParty = (String) testCase.get("cPartyNumber");
            int duration = (Integer) testCase.get("duration");
            int attempts = (Integer) testCase.get("attempts");
            String preferredNetwork = (String) testCase.get("preferredNetwork");
            boolean isIncoming = (Boolean) testCase.get("isIncoming");
            @SuppressWarnings("unused")
			boolean isConference = (Boolean) testCase.get("isConference");
            
            //  Report test details
            ProgressReporter.reportCallingProgress(
                aPartyDeviceId,
                aPartyNumber,
                "Preparing " + callType + " call",
                "PREPARING",
                duration,
                0.0
            );
            
         //  STEP 1: Determine caller and receiver based on direction WITH DEVICE MAPPING VALIDATION
            String direction = (String) testCase.getOrDefault("direction", "OUTGOING");
            String callerDeviceId, callerNumber, receiverDeviceId, receiverNumber;

            //  BUILD DEVICE MAPPING FROM SYSTEM PROPERTIES (similar to SMS executor)
            Map<String, String> numberToDeviceMap = new HashMap<>();

            // Add A-Party mapping
            String aPartyDevice = aPartyDeviceId;
            String aPartyNumber = this.aPartyNumber;
            if (aPartyNumber != null && aPartyDevice != null) {
                numberToDeviceMap.put(cleanNumber(aPartyNumber), aPartyDevice);
                System.out.println("   📱 A-Party mapped: " + aPartyNumber + " -> " + aPartyDevice);
            }

            // Add B-Party mapping if provided in system properties
            String bPartyDevice = this.bPartyDeviceId;
            String bPartyNumber = this.bPartyNumber;
            if (bPartyNumber != null && bPartyDevice != null) {
                numberToDeviceMap.put(cleanNumber(bPartyNumber), bPartyDevice);
                System.out.println("   📱 B-Party mapped: " + bPartyNumber + " -> " + bPartyDevice);
            }

            System.out.println("\n📊 EXCEL NUMBERS DEVICE MAPPING CHECK:");
            System.out.println("   Excel A-Party: " + excelAParty);
            System.out.println("   Excel B-Party: " + excelBParty);

            if ("INCOMING".equals(direction)) {
                // INCOMING: B-Party calls A-Party
                callerNumber = excelBParty;
                receiverNumber = excelAParty;
                
                // Get device IDs from mapping
                callerDeviceId = numberToDeviceMap.get(cleanNumber(callerNumber));
                receiverDeviceId = numberToDeviceMap.get(cleanNumber(receiverNumber));
                
                System.out.println("\n📥 INCOMING call mode");
                System.out.println("   Caller (B-Party): " + callerNumber + 
                                  " -> Device: " + (callerDeviceId != null ? callerDeviceId : "NOT MAPPED"));
                System.out.println("   Receiver (A-Party): " + receiverNumber + 
                                  " -> Device: " + (receiverDeviceId != null ? receiverDeviceId : "NOT MAPPED"));
                
                //  CRITICAL: For incoming, verify A-Party device exists
                if (receiverDeviceId == null) {
                    System.out.println("\n❌ INCOMING TEST ERROR: A-Party number from Excel (" + receiverNumber + 
                                      ") has no device mapping!");
                    result.put("finalStatus", "SKIPPED");
                    result.put("callStatus", "A_PARTY_NO_DEVICE");
                    result.put("comments", "A-Party number " + receiverNumber + " has no device mapping. " +
                               "Please set -DaPartyNumber=" + receiverNumber + " and ensure device is connected.");
                    return result;
                }
                
            } else {
                // OUTGOING: A-Party calls B-Party
                callerNumber = excelAParty;
                receiverNumber = excelBParty;
                
                // Get device IDs from mapping
                callerDeviceId = numberToDeviceMap.get(cleanNumber(callerNumber));
                receiverDeviceId = numberToDeviceMap.get(cleanNumber(receiverNumber));
                
                System.out.println("\n📤 OUTGOING call mode");
                System.out.println("   Caller (A-Party): " + callerNumber + 
                                  " -> Device: " + (callerDeviceId != null ? callerDeviceId : "NOT MAPPED"));
                System.out.println("   Receiver (B-Party): " + receiverNumber + 
                                  " -> Device: " + (receiverDeviceId != null ? receiverDeviceId : "NOT MAPPED"));
                
                //  CRITICAL: For outgoing, verify A-Party device exists
                if (callerDeviceId == null) {
                    System.out.println("\n❌ OUTGOING TEST ERROR: A-Party number from Excel (" + callerNumber + 
                                      ") has no device mapping!");
                    result.put("finalStatus", "SKIPPED");
                    result.put("callStatus", "A_PARTY_NO_DEVICE");
                    result.put("comments", "A-Party number " + callerNumber + " has no device mapping. " +
                               "Please set -DaPartyNumber=" + callerNumber + " and ensure device is connected.");
                    return result;
                }
            }

            //  Additional verification: Check if numbers match system properties
            if ("OUTGOING".equals(direction)) {
                String cleanExcelAParty = cleanNumber(excelAParty);
                String cleanSystemAParty = cleanNumber(this.aPartyNumber);
                
                if (!cleanExcelAParty.equals(cleanSystemAParty)) {
                    System.out.println("\n⚠️ WARNING: A-Party number mismatch!");
                    System.out.println("   Excel A-Party: " + cleanExcelAParty);
                    System.out.println("   System A-Party: " + cleanSystemAParty);
                    System.out.println("   Continuing with Excel A-Party number");
                    
                    // Update caller number to use Excel value
                    callerNumber = excelAParty;
                    result.put("aPartyNumberMismatch", true);
                    result.put("excelAParty", cleanExcelAParty);
                    result.put("systemAParty", cleanSystemAParty);
                }
            }

            // Store numbers in result
            result.put("callerNumber", callerNumber);
            result.put("receiverNumber", receiverNumber);
            
            
         //  STEP 2: Enhanced device validation (matches SMS executor)
            DeviceValidationResult validation = validateDeviceConnectivityEnhanced(
                callerDeviceId, receiverDeviceId, isIncoming, callerNumber, direction, receiverNumber
            );

            if (!validation.isValid) {
                System.out.println("\n⚠️ SKIPPING TEST: " + validation.reason);
                
                ProgressReporter.reportCallingProgress(
                    aPartyDeviceId,
                    callerNumber,
                    "TEST_SKIPPED",
                    "Skipped: " + validation.reason,
                    0,
                    0.0
                );
                
                result.put("finalStatus", "SKIPPED");
                result.put("callStatus", "SKIPPED");
                result.put("comments", validation.reason);
                
                // Clear cache for disconnected device
                if (validation.reason.contains("disconnected") && callerDeviceId != null) {
                    lastPostCallUSSDCache.remove(callerDeviceId);
                }
                
                return result;
            }
            
            storeVolteStatus(result, aPartyVolte, bPartyVolte);
            
         //  STEP 3: OPTIMIZED PRE-CALL USSD CHECK (with cache reuse) - UPDATED FOR DUAL CHECK
            System.out.println("\n💰 PRE-CALL BALANCE CHECK...");

            Map<String, Object> beforeUSSD = null;
            Map<String, Object> receiverBeforeUSSD = null;

            if ("INCOMING".equals(direction)) {
                //  INCOMING: Check both B-Party (caller) and A-Party (receiver) balances
                
                // 1. Check B-Party (caller) balance
                System.out.println("   📱 Checking B-Party (Caller) balance...");
                ProgressReporter.reportCallingProgress(
                    callerDeviceId,
                    callerNumber,
                    "Checking Caller Pre-Call Balance",
                    "USSD_CHECK",
                    0,
                    10.0
                );
                
                beforeUSSD = getOrPerformPreCallUSSD(callerDeviceId, callerNumber);
                
                ProgressReporter.reportCallingProgress(
                    callerDeviceId,
                    callerNumber,
                    beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false) ? 
                        "Caller Balance Check Complete" : "Caller Balance Check Failed",
                    beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false) ? 
                        "USSD_SUCCESS" : "USSD_FAILED",
                    0,
                    15.0
                );
                
                // 2. Check A-Party (receiver) balance
                System.out.println("   📱 Checking A-Party (Receiver) balance...");
                ProgressReporter.reportCallingProgress(
                    receiverDeviceId,
                    receiverNumber,
                    "Checking Receiver Pre-Call Balance",
                    "USSD_CHECK",
                    0,
                    17.0
                );
                
                receiverBeforeUSSD = getOrPerformPreCallUSSD(receiverDeviceId, receiverNumber);
                
                ProgressReporter.reportCallingProgress(
                    receiverDeviceId,
                    receiverNumber,
                    receiverBeforeUSSD != null && (Boolean) receiverBeforeUSSD.getOrDefault("success", false) ? 
                        "Receiver Balance Check Complete" : "Receiver Balance Check Failed",
                    receiverBeforeUSSD != null && (Boolean) receiverBeforeUSSD.getOrDefault("success", false) ? 
                        "USSD_SUCCESS" : "USSD_FAILED",
                    0,
                    20.0
                );
                
                // Store B-Party (caller) results
                if (beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false)) {
                    result.put("bPartyBeforeBalance", beforeUSSD.get("balance"));
                    result.put("bPartyBeforeValidity", beforeUSSD.get("validity"));
                    result.put("callerMSISDN", beforeUSSD.get("phoneNumber"));
                    
                    if (beforeUSSD.containsKey("cachedFromPreviousTest")) {
                        result.put("callerUSSDSource", "CACHED");
                        System.out.println("   ♻️ Caller balance reused from previous test");
                    } else {
                        result.put("callerUSSDSource", "NEW_CHECK");
                    }
                    
                    System.out.println("    Caller Before Balance: ₹" + beforeUSSD.get("balance"));
                    if (beforeUSSD.get("validity") != null) {
                        System.out.println("   📅 Caller Validity: " + beforeUSSD.get("validity"));
                    }
                } else {
                    handleUSSDCheckFailure(beforeUSSD, callerDeviceId, result, "caller", direction);
                    if ("SKIPPED".equals(result.get("finalStatus"))) {
                        return result;
                    }
                    System.out.println("   ℹ️ Continuing without caller balance info...");
                    result.put("bPartyBeforeBalance", "N/A");
                    result.put("ussdCheckFailed", true);
                }
                
                // Store A-Party (receiver) results
                if (receiverBeforeUSSD != null && (Boolean) receiverBeforeUSSD.getOrDefault("success", false)) {
                    result.put("aPartyBeforeBalance", receiverBeforeUSSD.get("balance"));
                    result.put("aPartyBeforeValidity", receiverBeforeUSSD.get("validity"));
                    result.put("receiverMSISDN", receiverBeforeUSSD.get("phoneNumber"));
                    
                    if (receiverBeforeUSSD.containsKey("cachedFromPreviousTest")) {
                        result.put("receiverUSSDSource", "CACHED");
                        System.out.println("   ♻️ Receiver balance reused from previous test");
                    } else {
                        result.put("receiverUSSDSource", "NEW_CHECK");
                    }
                    
                    System.out.println("    Receiver Before Balance: ₹" + receiverBeforeUSSD.get("balance"));
                    if (receiverBeforeUSSD.get("validity") != null) {
                        System.out.println("   📅 Receiver Validity: " + receiverBeforeUSSD.get("validity"));
                    }
                } else {
                    handleUSSDCheckFailure(receiverBeforeUSSD, receiverDeviceId, result, "receiver", direction);
                    System.out.println("   ℹ️ Continuing without receiver balance info...");
                    result.put("aPartyBeforeBalance", "N/A");
                }
                
            } else {
                //  OUTGOING: Only check A-Party (caller) balance
                ProgressReporter.reportCallingProgress(
                    callerDeviceId,
                    callerNumber,
                    "Checking Pre-Call Balance",
                    "USSD_CHECK",
                    0,
                    10.0
                );
                
                beforeUSSD = getOrPerformPreCallUSSD(callerDeviceId, callerNumber);
                
                ProgressReporter.reportCallingProgress(
                    callerDeviceId,
                    callerNumber,
                    beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false) ? 
                        "Balance Check Complete" : "Balance Check Failed",
                    beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false) ? 
                        "USSD_SUCCESS" : "USSD_FAILED",
                    0,
                    20.0
                );
                
                if (beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false)) {
                    result.put("beforeBalance", beforeUSSD.get("balance"));
                    result.put("beforeValidity", beforeUSSD.get("validity"));
                    result.put("callerMSISDN", beforeUSSD.get("phoneNumber"));
                    
                    if (beforeUSSD.containsKey("cachedFromPreviousTest")) {
                        result.put("preCallUSSDSource", "CACHED");
                        System.out.println("   ♻️ Reused from previous test");
                    } else {
                        result.put("preCallUSSDSource", "NEW_CHECK");
                    }
                    
                    System.out.println("    Before Balance: ₹" + beforeUSSD.get("balance"));
                    if (beforeUSSD.get("validity") != null) {
                        System.out.println("   📅 Validity: " + beforeUSSD.get("validity"));
                    }
                } else {
                    handleUSSDCheckFailure(beforeUSSD, callerDeviceId, result, "caller", direction);
                    if ("SKIPPED".equals(result.get("finalStatus"))) {
                        return result;
                    }
                    System.out.println("   ℹ️ Continuing test without balance info...");
                    result.put("beforeBalance", "N/A");
                    result.put("ussdCheckFailed", true);
                }
            }

            Thread.sleep(USSD_WAIT_BEFORE_CALL);

            //  STEP 4: Set network type on caller device
            System.out.println("📡 Setting network on CALLER device: " + preferredNetwork);
            setNetworkType(callerDeviceId, preferredNetwork);
            Thread.sleep(5000);
            
            //  STEP 5: Enhanced call handling determination
            String callHandling = determineCallHandling(receiverDeviceId, receiverNumber, direction);
            result.put("callHandling", callHandling);
            
//            if (callHandling.equals("AUTO_ANSWER")) {
//                System.out.println("🤖 Setting up auto-answer on RECEIVER device: " + receiverDeviceId);
//                System.out.println("   Receiver will auto-answer calls from: " + callerNumber);
//                
//                ProgressReporter.reportCallingProgress(
//                    receiverDeviceId,
//                    receiverNumber,
//                    "Setting up auto-answer",
//                    "AUTO_ANSWER_SETUP",
//                    0,
//                    25.0
//                );
//                
//                DeviceManager.setupAutoAnswer(receiverNumber, callerNumber);
//                Thread.sleep(5000);
//                
//                result.put("autoAnswerDevice", receiverDeviceId);
//                result.put("autoAnswerExpectedCaller", callerNumber);
         // In CompleteCallingTestExecutor.executeSingleCallingTest()
            if (callHandling.equals("AUTO_ANSWER")) {
                System.out.println("🤖 Setting up auto-answer on RECEIVER device: " + receiverDeviceId);
                System.out.println("   Receiver will auto-answer calls from: " + callerNumber);
                
                DeviceManager.setupAutoAnswer(receiverNumber, callerNumber);
                
                // 🔥 CRITICAL: Wait for auto-answer service to fully initialize
                System.out.println("   ⏳ Waiting 3 seconds for auto-answer service to stabilize...");
                Thread.sleep(3000);
                
                result.put("autoAnswerDevice", receiverDeviceId);
                result.put("autoAnswerExpectedCaller", callerNumber);
                
            } else if (callHandling.equals("RECEIVER_UNAVAILABLE")) {
                System.out.println("❌ INCOMING call cannot proceed - receiver device not available");
                result.put("finalStatus", "SKIPPED");
                result.put("callStatus", "RECEIVER_UNAVAILABLE");
                result.put("comments", "Receiver device not available for incoming call");
                return result;
                
            } else {
                System.out.println("👤 Manual answer expected");
                result.put("autoAnswerStatus", "DISABLED");
                
                if ("INCOMING".equals(direction)) {
                    result.put("callHandlingNote", "Manual answer on A-Party device");
                } else {
                    result.put("bPartyNetworkType", "OFFLINE");
                    result.put("bPartyVolteEnabled", "false");
                    result.put("bPartyVolteStatus", "OFFLINE");
                }
            }
            
            //  STEP 6: Execute call based on type
            System.out.println("📞 Executing " + callType + " call from CALLER device: " + callerDeviceId);
            
            switch (callType) {
                case "VIDEO":
                    executeVideoCall(callerDeviceId, receiverNumber, duration, attempts, callHandling, result);
                    break;
                case "VOLTE":
                    executeVolteCall(callerDeviceId, receiverNumber, duration, attempts, callHandling, result);
                    break;
                case "CONFERENCE":
                    executeConferenceCall(callerDeviceId, receiverNumber, cParty, duration, attempts, callHandling, result);
                    break;
                case "VOICE":
                default:
                    executeVoiceCall(callerDeviceId, receiverNumber, duration, attempts, callHandling, result);
                    break;
            }
            
            if (callHandling.equals("AUTO_ANSWER")) {
                System.out.println("🛑 Stopping auto-answer on receiver device: " + receiverDeviceId);
                DeviceManager.stopAutoAnswer(receiverNumber);
                result.put("autoAnswerStopped", true);
            }
            
         //  STEP 7: POST-CALL USSD BALANCE CHECK (for both parties in incoming case)
            if (callerDeviceId != null) {
                System.out.println("\n💰 POST-CALL BALANCE CHECK...");
                System.out.println("   ⏳ Waiting " + (USSD_WAIT_AFTER_CALL/1000) + " seconds for balance update...");
                Thread.sleep(USSD_WAIT_AFTER_CALL);
                
                if ("INCOMING".equals(direction)) {
                    // For incoming calls, check both parties
                    
                    // // 1. Check B-Party (caller) after balance
                    // System.out.println("   📱 Checking B-Party (Caller) after balance...");
                    // ProgressReporter.reportCallingProgress(
                    //     callerDeviceId,
                    //     callerNumber,
                    //     "Checking Caller Post-Call Balance",
                    //     "USSD_CHECK",
                    //     0,
                    //     85.0
                    // );
                    
                    // Map<String, Object> afterUSSD = performPostCallUSSDCheck(callerDeviceId, callerNumber);
                    
                    // 2. Check A-Party (receiver) after balance
                    System.out.println("   📱 Checking A-Party (Receiver) after balance...");
                    ProgressReporter.reportCallingProgress(
                        receiverDeviceId,
                        receiverNumber,
                        "Checking Receiver Post-Call Balance",
                        "USSD_CHECK",
                        0,
                        87.0
                    );
                    
                    Map<String, Object> receiverAfterUSSD = performPostCallUSSDCheck(receiverDeviceId, receiverNumber);
                    
                    // Store B-Party (caller) after balance
                    // if (afterUSSD != null && (Boolean) afterUSSD.getOrDefault("success", false)) {
                    //     result.put("bPartyAfterBalance", afterUSSD.get("balance"));
                    //     result.put("bPartyAfterValidity", afterUSSD.get("validity"));
                    //     cachePostCallUSSDForNextTest(callerDeviceId, afterUSSD);
                        
                    //     System.out.println("    Caller After Balance: ₹" + afterUSSD.get("balance"));
                    // } else {
                    //     System.out.println("   ⚠️ Caller after-balance USSD check failed");
                    //     result.put("bPartyAfterBalance", "N/A");
                    //     lastPostCallUSSDCache.remove(callerDeviceId);
                    // }
                    
                    // Store A-Party (receiver) after balance
                    if (receiverAfterUSSD != null && (Boolean) receiverAfterUSSD.getOrDefault("success", false)) {
                        result.put("aPartyAfterBalance", receiverAfterUSSD.get("balance"));
                        result.put("aPartyAfterValidity", receiverAfterUSSD.get("validity"));
                        cachePostCallUSSDForNextTest(receiverDeviceId, receiverAfterUSSD);
                        
                        System.out.println("    Receiver After Balance: ₹" + receiverAfterUSSD.get("balance"));
                    } else {
                        System.out.println("   ⚠️ Receiver after-balance USSD check failed");
                        result.put("aPartyAfterBalance", "N/A");
                        lastPostCallUSSDCache.remove(receiverDeviceId);
                    }
                    
                    // Calculate deductions for both parties
                    try {
                        // B-Party deduction (caller)
                        if (result.containsKey("bPartyBeforeBalance") && !"N/A".equals(result.get("bPartyBeforeBalance")) &&
                            result.containsKey("bPartyAfterBalance") && !"N/A".equals(result.get("bPartyAfterBalance"))) {
                            
                            Double beforeBal = parseBalance(result.get("bPartyBeforeBalance"));
                            Double afterBal = parseBalance(result.get("bPartyAfterBalance"));
                            
                            if (beforeBal != null && afterBal != null) {
                                double deduction = beforeBal - afterBal;
                                result.put("bPartyBalanceDeduction", deduction);
                                result.put("callCost", deduction); // Set as call cost
                                System.out.println("   💸 Caller Balance Deduction: ₹" + String.format("%.2f", deduction));
                            }
                        }
                        
                        // A-Party deduction (receiver - should be zero for incoming)
                        if (result.containsKey("aPartyBeforeBalance") && !"N/A".equals(result.get("aPartyBeforeBalance")) &&
                            result.containsKey("aPartyAfterBalance") && !"N/A".equals(result.get("aPartyAfterBalance"))) {
                            
                            Double beforeBal = parseBalance(result.get("aPartyBeforeBalance"));
                            Double afterBal = parseBalance(result.get("aPartyAfterBalance"));
                            
                            if (beforeBal != null && afterBal != null) {
                                double deduction = beforeBal - afterBal;
                                result.put("aPartyBalanceDeduction", deduction);
                                if (Math.abs(deduction) > 0.01) { // Small threshold
                                    System.out.println("   ⚠️ Receiver Balance Changed: ₹" + String.format("%.2f", deduction));
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("   ⚠️ Could not calculate balance deduction: " + e.getMessage());
                    }
                    
                } else {
                    // OUTGOING: Only check caller (A-Party) balance
                    Map<String, Object> afterUSSD = performPostCallUSSDCheck(callerDeviceId, callerNumber);
                    
                    if (afterUSSD != null && (Boolean) afterUSSD.getOrDefault("success", false)) {
                        result.put("afterBalance", afterUSSD.get("balance"));
                        result.put("afterValidity", afterUSSD.get("validity"));
                        cachePostCallUSSDForNextTest(callerDeviceId, afterUSSD);
                        
                        System.out.println("    After Balance: ₹" + afterUSSD.get("balance"));
                        
                        // Calculate deduction
                        if (beforeUSSD != null && (Boolean) beforeUSSD.getOrDefault("success", false)) {
                            try {
                                Double beforeBal = parseBalance(beforeUSSD.get("balance"));
                                Double afterBal = parseBalance(afterUSSD.get("balance"));
                                
                                if (beforeBal != null && afterBal != null) {
                                    double deduction = beforeBal - afterBal;
                                    result.put("balanceDeduction", deduction);
                                    result.put("callCost", deduction);
                                    System.out.println("   💸 Balance Deduction: ₹" + String.format("%.2f", deduction));
                                }
                            } catch (Exception e) {
                                System.out.println("   ⚠️ Could not calculate balance deduction: " + e.getMessage());
                            }
                        }
                    } else {
                        System.out.println("   ⚠️ After-balance USSD check failed (non-critical)");
                        result.put("afterBalance", "N/A");
                        lastPostCallUSSDCache.remove(callerDeviceId);
                    }
                }
            }
            
            //  STEP 8: Build comprehensive comments
            result.put("comments", buildCallingComments(result));
            
            String status = (String) result.get("finalStatus");
            String emoji = "SUCCESS".equals(status) ? "" : "❌";
            System.out.println(emoji + " Test Status: " + status);
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
            result.put("finalStatus", "ERROR");
            result.put("comments", "Error: " + e.getMessage());
            
            try {
                DeviceManager.stopAllAutoAnswerServices();
            } catch (Exception ex) {
                // Ignore cleanup errors
            }
        }
        
        return result;
    }
    
    /**
     *  ENHANCED DEVICE VALIDATION - With direction-specific requirements
     * MATCHES SMS EXECUTOR LOGIC EXACTLY
     */
    private DeviceValidationResult validateDeviceConnectivityEnhanced(
            String callerDevice, String receiverDevice, boolean isIncoming, 
            String callerNumber, String direction, String receiverNumber) {
        
        System.out.println("\n🔌 VALIDATING DEVICE CONNECTIVITY...");
        System.out.println("   Direction: " + direction);
        System.out.println("   isIncoming: " + isIncoming);
        
        // SCENARIO A: OUTGOING TEST
        if ("OUTGOING".equals(direction)) {
            System.out.println("   📤 OUTGOING TEST VALIDATION");
            
            //  Validate CALLER device (A-Party device)
            if (callerDevice == null) {
                String reason = "Caller device not mapped for number: " + callerNumber;
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            if (!ADBHelper.isDeviceConnected(callerDevice)) {
                String reason = "Caller device is disconnected: " + callerDevice + 
                               ". Please reconnect and retry this test.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            System.out.println("    Caller device connected: " + callerDevice);
            
            //  Validate RECEIVER device (optional for outgoing)
            if (receiverDevice != null) {
                if (!ADBHelper.isDeviceConnected(receiverDevice)) {
                    System.out.println("   ⚠️ Receiver device not connected: " + receiverDevice);
                    System.out.println("   ℹ️ For outgoing tests, receiver may be external - continuing");
                } else {
                    System.out.println("    Receiver device connected: " + receiverDevice);
                }
            }
            
            return new DeviceValidationResult(true, "Outgoing test validation passed");
        }
        
        // SCENARIO B: INCOMING TEST ⭐ CRITICAL FIX
        else if ("INCOMING".equals(direction)) {
            System.out.println("   📥 INCOMING TEST VALIDATION");
            
            //  Validate CALLER device (B-Party device)
            if (callerDevice == null) {
                String reason = "Caller (B-Party) device not mapped for number: " + callerNumber;
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            if (!ADBHelper.isDeviceConnected(callerDevice)) {
                String reason = "Caller (B-Party) device is disconnected: " + callerDevice + 
                               ". Please reconnect and retry this test.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            System.out.println("    Caller (B-Party) device connected: " + callerDevice);
            
            //   **CRITICAL FIX: Validate RECEIVER device (A-Party device)**
            // For INCOMING tests, A-Party is the RECEIVER and MUST be available
            if (receiverDevice == null) {
                String reason = "Receiver (A-Party) device not mapped. For incoming tests, " +
                               "A-Party device must be available to receive calls.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            if (!ADBHelper.isDeviceConnected(receiverDevice)) {
                String reason = "Receiver (A-Party) device is disconnected: " + receiverDevice + 
                               ". For incoming tests, A-Party device MUST be connected.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            System.out.println("    Receiver (A-Party) device connected: " + receiverDevice);
            
            return new DeviceValidationResult(true, "Incoming test validation passed");
        }
        
        // Unknown direction
        String reason = "Unknown test direction: " + direction;
        System.out.println("   ❌ " + reason);
        return new DeviceValidationResult(false, reason);
    }

    // Inner class for validation result (add at class level)
    private static class DeviceValidationResult {
        boolean isValid;
        String reason;
        
        DeviceValidationResult(boolean isValid, String reason) {
            this.isValid = isValid;
            this.reason = reason;
        }
    }
    
    /**
     *  NEW: Cache POST-CALL USSD result for next test
     */
    private void cachePostCallUSSDForNextTest(String deviceId, Map<String, Object> postCallUSSD) {
        if (postCallUSSD != null && (Boolean) postCallUSSD.getOrDefault("success", false)) {
            // Store the POST-CALL result with timestamp
            Map<String, Object> cacheEntry = new HashMap<>(postCallUSSD);
            cacheEntry.put("cachedTimestamp", System.currentTimeMillis());
            
            lastPostCallUSSDCache.put(deviceId, cacheEntry);
            
            System.out.println("   💾 Cached balance for next test:");
            System.out.println("      Device: " + deviceId);
            System.out.println("      Balance: ₹" + cacheEntry.get("balance"));
            System.out.println("      Phone: " + cacheEntry.get("phoneNumber"));
        }
    }
    
    /**
     *  NEW: Get PRE-CALL USSD (reuse POST-CALL from previous test if available)
     */
    private Map<String, Object> getOrPerformPreCallUSSD(String deviceId, String phoneNumber) {
        try {
            // Check if we have cached POST-CALL USSD from previous test for this device
            if (lastPostCallUSSDCache.containsKey(deviceId)) {
                Map<String, Object> cachedUSSD = lastPostCallUSSDCache.get(deviceId);
                
                System.out.println("   ♻️ REUSING POST-CALL balance from previous test");
                System.out.println("      Device: " + deviceId);
                System.out.println("      Cached Balance: ₹" + cachedUSSD.get("balance"));
                
                // Verify phone number matches
                String cachedNumber = (String) cachedUSSD.get("phoneNumber");
                if (cachedNumber != null && phoneNumber != null) {
                    String cleanExpected = cleanNumber(phoneNumber);
                    String cleanCached = cleanNumber(cachedNumber);
                    
                    if (cleanExpected.equals(cleanCached)) {
                        System.out.println("       Phone number verified: " + cleanCached);
                        
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
                System.out.println("   ❌ Device disconnected during pre-call USSD check");
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
                        System.out.println("    Phone number verified: " + cleanDetected);
                    }
                }
            }
            
            return ussdResult;
            
        } catch (Exception e) {
            System.out.println("   ❌ Pre-call USSD check error: " + e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
    
 /**
  *  ENHANCED: Perform post-call USSD check with retry and driver reset
  */
 private Map<String, Object> performPostCallUSSDCheck(String deviceId, String phoneNumber) {
     try {
         System.out.println("   📞 Checking balance after call...");
         Map<String, Object> ussdResult = performUSSDCheckWithRetry(deviceId, USSD_CODE, "AFTER", phoneNumber);
         return ussdResult;
     } catch (Exception e) {
         System.out.println("   ❌ Post-call USSD check error: " + e.getMessage());
         Map<String, Object> errorResult = new HashMap<>();
         errorResult.put("success", false);
         errorResult.put("error", e.getMessage());
         return errorResult;
     }
 }

 /**
  *  Perform USSD check with retry and driver reset
  */
 private Map<String, Object> performUSSDCheckWithRetry(
         String deviceId, String ussdCode, String checkType, String expectedNumber) {
     
     System.out.println("\n💰 " + checkType + " BALANCE CHECK...");
     
     Map<String, Object> ussdResult = null;
     int attempt = 0;
     
     while (attempt < MAX_USSD_RETRIES) {
         attempt++;
         
         try {
             System.out.println("    Attempt " + attempt + "/" + MAX_USSD_RETRIES);
             
             if (!ADBHelper.isDeviceConnected(deviceId)) {
                 System.out.println("   ❌ Device disconnected: " + deviceId);
                 Map<String, Object> errorResult = new HashMap<>();
                 errorResult.put("success", false);
                 errorResult.put("error", "Device disconnected");
                 errorResult.put("deviceDisconnected", true);
                 return errorResult;
             }
             
             closeDialerAppCompletely(deviceId);
             Thread.sleep(1000);
             returnToHomeScreen(deviceId);
             Thread.sleep(1000);
             
             ussdResult = USSDService.checkBalanceAndValidity(deviceId, ussdCode);
             
             System.out.println("    Resetting driver after USSD operation...");
             try {
                 resetDriverAfterUSSD(deviceId);
             } catch (Exception resetEx) {
                 System.out.println("   ⚠️ Driver reset error (continuing): " + resetEx.getMessage());
             }
             
             prepareDialerAppAfterUSSD(deviceId);
             
             if (ussdResult != null && (Boolean) ussdResult.getOrDefault("success", false)) {
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
                 
                 System.out.println("    USSD SUCCESS");
                 System.out.println("      Phone: " + phoneNumber);
                 System.out.println("      Balance: " + ussdResult.get("balance"));
                 
                 if (ussdResult.get("validity") != null) {
                     System.out.println("      Validity: " + ussdResult.get("validity"));
                 }
                 
                 return ussdResult;
             }
             
             System.out.println("   ❌ USSD API returned error");
             if (ussdResult != null && ussdResult.containsKey("error")) {
                 System.out.println("      Error: " + ussdResult.get("error"));
             }
             
             if (attempt < MAX_USSD_RETRIES) {
                 System.out.println("   ⏳ Waiting 3s before...");
                 Thread.sleep(3000);
             }
             
         } catch (Exception e) {
             System.out.println("   ❌ USSD attempt failed: " + e.getMessage());
             
             try {
                 resetDriverAfterUSSD(deviceId);
             } catch (Exception resetEx) {
                 // Ignore
             }
             
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
     
     Map<String, Object> failureResult = new HashMap<>();
     failureResult.put("success", false);
     failureResult.put("error", "USSD check failed after " + MAX_USSD_RETRIES + " attempts");
     
     if (!ADBHelper.isDeviceConnected(deviceId)) {
         failureResult.put("deviceDisconnected", true);
     }
     
     return failureResult;
 }

 /**
  *  NEW: Reset Driver After USSD Operation (same as SMS)
  */
 private void resetDriverAfterUSSD(String deviceId) {
     try {
         System.out.println("   🔧 Resetting Appium session after USSD...");
         
         // Step 1: Force stop dialer app via ADB (always works)
         try {
             String[] cmd = {"adb", "-s", deviceId, "shell", "am", "force-stop", 
                            "com.google.android.dialer"};
             Process process = Runtime.getRuntime().exec(cmd);
             process.waitFor();
             System.out.println("    Dialer app force stopped");
             Thread.sleep(2000);
         } catch (Exception e) {
             System.out.println("   ⚠️ Force stop failed: " + e.getMessage());
         }
         
         // Step 2: Terminate via driver (if still alive)
         try {
             driver.terminateApp("com.google.android.dialer");
             Thread.sleep(1000);
         } catch (Exception e) {
             // Expected to fail if instrumentation is dead
         }
         
         // Step 3: Check if driver is still responsive
         boolean driverDead = false;
         try {
             driver.getPageSource();
         } catch (Exception e) {
             System.out.println("   ⚠️ Driver is unresponsive, recreating session...");
             driverDead = true;
         }
         
         // Step 4: If driver is dead, recreate entire session
         if (driverDead) {
             System.out.println("    Recreating Appium session...");
             String platformVersion = ADBHelper.getAndroidVersion(deviceId).split("\\.")[0];
             
             // Quit old driver
             try {
                 driver.quit();
             } catch (Exception e) {
                 // Ignore
             }
             
             Thread.sleep(2000);
             
             // Create new driver
             this.driver = com.telecom.driver.DriverManager.initializeDriver(
                 deviceId, platformVersion
             );
             this.dialerPage = new ImprovedDialerPage(driver);
             this.videoDialerPage = new WorkingVideoCallDialer(driver);
             
             System.out.println("    New Appium session created");
         } else {
             // Driver is alive, just restart the app
             System.out.println("    Restarting dialer app...");
             driver.activateApp("com.google.android.dialer");
             Thread.sleep(3000);
             System.out.println("    Dialer app restarted");
         }
         
     } catch (Exception e) {
         System.out.println("   ❌ Driver reset failed: " + e.getMessage());
         e.printStackTrace();
     }
 }
    
 /**
  *  ENHANCED: Close dialer app completely (improved version)
  */
 private void closeDialerAppCompletely(String deviceId) {
     try {
         // Method 1: Terminate via driver
         try {
             driver.terminateApp("com.google.android.dialer");
             Thread.sleep(1000);
         } catch (Exception e) {
             // Ignore - may fail after USSD
         }
         
         // Method 2: Force stop via ADB (always works)
         try {
             String[] cmd = {"adb", "-s", deviceId, "shell", "am", "force-stop", 
                 "com.google.android.dialer"};
             Process process = Runtime.getRuntime().exec(cmd);
             int exitCode = process.waitFor();
             if (exitCode == 0) {
                 System.out.println("    Dialer app force stopped via ADB");
             }
         } catch (Exception e) {
             System.out.println("   ⚠️ Force stop failed: " + e.getMessage());
         }
         
     } catch (Exception e) {
         System.out.println("   ⚠️ Close dialer app error: " + e.getMessage());
     }
 }

 /**
  *  ENHANCED: Return to home screen (improved version)
  */
 private void returnToHomeScreen(String deviceId) {
     try {
         // Press home key multiple times
         for (int i = 0; i < 2; i++) {
             String[] cmd = {"adb", "-s", deviceId, "shell", "input", "keyevent", "KEYCODE_HOME"};
             Process process = Runtime.getRuntime().exec(cmd);
             process.waitFor();
             Thread.sleep(300);
         }
         System.out.println("   🏠 Returned to home screen");
     } catch (Exception e) {
         System.out.println("   ⚠️ Home screen error: " + e.getMessage());
     }
 }

 /**
  *  ENHANCED: Prepare dialer app after USSD operation (improved version)
  */
 private void prepareDialerAppAfterUSSD(String deviceId) {
     try {
         System.out.println("   📱 Preparing dialer app after USSD...");
         
         // Wait for any popups to clear
         Thread.sleep(2000);
         
         // Launch dialer app
         driver.activateApp("com.google.android.dialer");
         Thread.sleep(3000);
         
         System.out.println("    Dialer app ready");
         
     } catch (Exception e) {
         System.out.println("   ⚠️ Dialer app preparation warning: " + e.getMessage());
     }
 }

 /**
  *  NEW: Parse balance - Handle different formats (same as SMS)
  */
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

 /**
  *  KEEP EXISTING: Clean phone number method (already exists)
  */
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
     *  Execute VOICE call with USSD tracking
     */
    private void executeVoiceCall(String callerDeviceId, String receiverNumber, int duration, 
            int attempts, String callHandling, Map<String, Object> result) {
        try {
            System.out.println("☎️ Initiating VOICE call from device: " + callerDeviceId);
            
         //  Report call initiation
            ProgressReporter.reportCallingProgress(
                callerDeviceId,
                receiverNumber,
                "Dialing " + receiverNumber,
                "DIALING",
                duration,
                30.0
            );
            
            AndroidDriver callerDriver = getDriverForDevice(callerDeviceId);
            ImprovedDialerPage callerDialerPage = new ImprovedDialerPage(callerDriver);
            
            for (int attempt = 1; attempt <= attempts; attempt++) {
                System.out.println("  Attempt " + attempt + "/" + attempts);
                
             //  Report attempt progress
                ProgressReporter.reportCallingProgress(
                    callerDeviceId,
                    receiverNumber,
                    "Attempt " + attempt + "/" + attempts,
                    "ATTEMPT_" + attempt,
                    duration,
                    40.0 + (10.0 * (attempt - 1))
                );
                
                
                CallMetrics metrics = new CallMetrics();
                long callStartTime = System.currentTimeMillis();
                
                // Dial the number from CALLER device
                callerDialerPage.dialNumberViaIntent(receiverNumber);
                System.out.println("  📱 Dialing initiated from " + callerDeviceId + " at: " + callStartTime);
                
             //  Report ringing progress
                ProgressReporter.reportCallingProgress(
                    callerDeviceId,
                    receiverNumber,
                    "Call Ringing",
                    "RINGING",
                    duration,
                    50.0
                );
                
                //  FIXED: Wait for call connection and track ring time
                boolean connected = waitForCallConnectionWithRingTime(callerDriver, metrics, duration);
                
                if (connected) {
                	//  Report connection success
                    ProgressReporter.reportCallingProgress(
                        callerDeviceId,
                        receiverNumber,
                        "Call Connected",
                        "CONNECTED",
                        duration,
                        60.0
                    );
                	
                    long connectionTime = System.currentTimeMillis();
                    metrics.ringTimeMs = connectionTime - callStartTime;
                    metrics.ringTimeSeconds = metrics.ringTimeMs / 1000.0;
                    
                    System.out.println("   Call connected after " + 
                        String.format("%.2f", metrics.ringTimeSeconds) + "s ring time");
                    
                    //  CRITICAL FIX: Store ring time in result
                    result.put("ringTimeMs", metrics.ringTimeMs);
                    result.put("ringTimeSeconds", metrics.ringTimeSeconds);
                    result.put("ringTime", (int) Math.round(metrics.ringTimeSeconds)); // Integer for Excel
                    
                    //  Track actual call duration from 00:01 onwards
                    int actualDuration = trackCallDuration(callerDriver, duration, callerDeviceId, receiverNumber);
                    metrics.actualDurationSeconds = actualDuration;
                    
                    
                    result.put("callStatus", "CONNECTED");
                    result.put("actualDuration", actualDuration);
                    result.put("targetDuration", duration);
                    result.put("attemptNumber", attempt);
                    result.put("finalStatus", "SUCCESS");
                    
                    //  CRITICAL FIX: Store auto-answer status correctly
                    if (callHandling.equals("AUTO_ANSWER")) {
                        result.put("autoAnswerEnabled", true);  // Boolean for logic
                        result.put("autoAnswerStatus", "YES"); // String for display
                    } else {
                        result.put("autoAnswerEnabled", false);
                        result.put("autoAnswerStatus", "NO - MANUAL");
                    }
                    
                    // End call
                    callerDialerPage.endCall();
                    System.out.println("   Voice call successful on attempt " + attempt);
                    break;
                    
                } else {
                    result.put("callStatus", "NO_ANSWER");
                    result.put("finalStatus", "FAILED");
                    result.put("attemptNumber", attempt);
                    result.put("ringTime", 0);
                    result.put("autoAnswerEnabled", false);
                    result.put("autoAnswerStatus", "N/A");
                    
                    callerDialerPage.endCall();
                    
                    if (attempt < attempts) {
                        System.out.println("  ⏳ Waiting before...");
                        Thread.sleep(5000);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("  ❌ Voice call error: " + e.getMessage());
            result.put("finalStatus", "ERROR");
            result.put("comments", "Error: " + e.getMessage());
            result.put("ringTime", 0);
            result.put("autoAnswerEnabled", false);
            result.put("autoAnswerStatus", "ERROR");
        }
    }
    
    /**
     *  NEW: Get driver for specific device (handles device switching for INCOMING calls)
     */
    private AndroidDriver getDriverForDevice(String deviceId) {
        // If the caller device is the same as current driver's device, use existing driver
        String currentDeviceId = (String) driver.getCapabilities().getCapability("udid");
        
        if (currentDeviceId != null && currentDeviceId.equals(deviceId)) {
            return driver;
        }
        
        // For INCOMING calls where caller is B-Party, we need B-Party's driver
        // This requires initializing a new driver session for B-Party
        try {
            System.out.println("   Switching to device: " + deviceId);
            
            String platformVersion = ADBHelper.getAndroidVersion(deviceId).split("\\.")[0];
            AndroidDriver newDriver = DriverManager.initializeDriver(deviceId, platformVersion);
            
            return newDriver;
            
        } catch (Exception e) {
            System.out.println("  ⚠️ Could not switch to device " + deviceId + ", using current driver");
            return driver;
        }
    }
    
    /**
     *  NEW: Wait for call connection and measure ring time (with specific driver)
     */
    private boolean waitForCallConnectionWithRingTime(AndroidDriver driverToUse, 
                                                       CallMetrics metrics, int maxWaitSeconds) {
        System.out.println("  ⏳ Waiting for call connection (ring time tracking)...");
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < maxWaitSeconds + 10; i++) {
            try {
                String pageSource = driverToUse.getPageSource();
                
                // Check for connection indicators
                if (pageSource.contains("00:") || 
                    pageSource.contains("Call timer") || 
                    pageSource.contains("Connected") || 
                    pageSource.contains("In call")) {
                    
                    long connectedTime = System.currentTimeMillis();
                    metrics.ringTimeMs = connectedTime - startTime;
                    metrics.ringTimeSeconds = metrics.ringTimeMs / 1000.0;
                    
                    System.out.println("   Call connected!");
                    System.out.println("  ⏱️ Ring time: " + 
                        String.format("%.2f", metrics.ringTimeSeconds) + "s (" + 
                        metrics.ringTimeMs + "ms)");
                    return true;
                }
                
                // Check for failure
                if (detectCallFailure(pageSource)) {
                    System.out.println("  ❌ Call failed: " + getDetailedFailureReason(pageSource));
                    return false;
                }
                
                Thread.sleep(1000);
                
            } catch (Exception e) {
                // Continue checking
            }
        }
        
        System.out.println("  ⏰ Call connection timeout");
        return false;
    }
    
    /**
     *  ENHANCED: Track actual call duration with 1-second updates
     */
    private int trackCallDuration(AndroidDriver driverToUse, int targetDuration, 
                                  String deviceId, String receiverNumber) throws InterruptedException {
        System.out.println("  ⏱️ ENHANCED: Tracking call duration with 1-second precision...");
        
        long durationStartTime = System.currentTimeMillis();
        int actualDuration = 0;
        boolean durationStarted = false;
        
        // Wait for 00:01 to appear (call timer starts) - with multiple attempts
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                String pageSource = driverToUse.getPageSource();
                
                // Multiple timer detection patterns
                if (pageSource.contains("00:01") || 
                    pageSource.contains("0:01") ||
                    pageSource.contains("00:02") ||
                    pageSource.contains("0:02") ||
                    pageSource.contains("Connected") ||
                    pageSource.contains("In call")) {
                    
                    durationStartTime = System.currentTimeMillis();
                    durationStarted = true;
                    System.out.println("   Duration tracking started (attempt " + attempt + ")");
                    
                    ProgressReporter.reportCallingProgress(
                        deviceId,
                        receiverNumber,
                        "Call Connected - Starting Duration",
                        "IN_CALL",
                        targetDuration,
                        70.0
                    );
                    break;
                }
                
                if (attempt < 5) {
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                // Continue
            }
        }
        
        if (!durationStarted) {
            System.out.println("  ⚠️ Could not detect timer, using fallback method");
            durationStartTime = System.currentTimeMillis();
            durationStarted = true;
        }
        
        // 🔥 CONTINUOUS 1-SECOND TRACKING
        while (actualDuration < targetDuration) {
            try {
                long elapsed = System.currentTimeMillis() - durationStartTime;
                actualDuration = (int) (elapsed / 1000);
                
                // Calculate progress (70% to 100% range)
                double progress = 70.0 + (30.0 * actualDuration / targetDuration);
                
                // 🔥 EVERY SECOND UPDATE
                System.out.println("  ⏱️ [" + String.format("%02d", actualDuration) + "s/" + targetDuration + "s]");
                
                ProgressReporter.reportCallingProgress(
                    deviceId,
                    receiverNumber,
                    "Call In Progress (" + actualDuration + "s/" + targetDuration + "s)",
                    "IN_CALL",
                    targetDuration,
                    progress
                );
                
                // Check if call is still active
                String pageSource = driverToUse.getPageSource();
                if (pageSource.contains("Call ended") || 
                    pageSource.contains("Call completed") ||
                    !(pageSource.contains("00:") || pageSource.contains("In call") || pageSource.contains("Connected"))) {
                    
                    System.out.println("  ⚠️ Call ended prematurely at " + actualDuration + "s");
                    
                    ProgressReporter.reportCallingProgress(
                        deviceId,
                        receiverNumber,
                        "Call Ended Prematurely",
                        "ENDED_EARLY",
                        targetDuration,
                        100.0
                    );
                    return actualDuration;
                }
                
                // 🔥 PRECISE 1-SECOND WAIT
                long nextSecond = durationStartTime + ((actualDuration + 1) * 1000);
                long sleepTime = nextSecond - System.currentTimeMillis();
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
                
            } catch (Exception e) {
                System.out.println("  ⚠️ Duration tracking error: " + e.getMessage());
                
                // Fallback: sleep 1 second and continue
                try {
                    Thread.sleep(1000);
                    actualDuration++;
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        
        System.out.println("   Target duration reached: " + actualDuration + "s");
        
        // Final progress update
        ProgressReporter.reportCallingProgress(
            deviceId,
            receiverNumber,
            "Call Completed Successfully",
            "COMPLETED",
            targetDuration,
            100.0
        );
        
        return actualDuration;
    }
    
    
    /**
     *  Execute VIDEO call (with caller device)
     */
    private void executeVideoCall(String callerDeviceId, String receiverNumber, int duration, 
            int attempts, String callHandling, Map<String, Object> result) {
		try {
		System.out.println("📹 Initiating VIDEO call from device: " + callerDeviceId);
		
		//  Report video call initiation
			ProgressReporter.reportCallingProgress(
				callerDeviceId,
				receiverNumber,
				"Initiating Video Call",
				"DIALING",
				duration,
				30.0
		);
		
		AndroidDriver callerDriver = getDriverForDevice(callerDeviceId);
		WorkingVideoCallDialer callerVideoDialer = new WorkingVideoCallDialer(callerDriver);
		
		for (int attempt = 1; attempt <= attempts; attempt++) {
		System.out.println("  Attempt " + attempt + "/" + attempts);
		
		//  Report attempt progress
			ProgressReporter.reportCallingProgress(
				callerDeviceId,
				receiverNumber,
				"Attempt " + attempt + "/" + attempts,
				"ATTEMPT_" + attempt,
				duration,
				40.0 + (10.0 * (attempt - 1))
		);
		
		// Make the video call - it already tracks ring time
		WorkingVideoCallDialer.VideoCallResult videoResult = 
		callerVideoDialer.makeVideoCall(receiverNumber, duration, 1);
		
		//  CRITICAL FIX: Extract ring time from VideoCallResult
		int videoRingTime = videoResult.getRingTime();  // Get ring time from dialer
		
		// Store ring time in main result
		result.put("ringTime", videoRingTime);
		result.put("ringTimeSeconds", videoRingTime);
		result.put("ringTimeMs", videoRingTime * 1000L);
		
		System.out.println("  📊 Video Ring Time: " + videoRingTime + "s");
		
		// Store other results
		result.put("callStatus", videoResult.getCallStatus());
		result.put("actualDuration", videoResult.getActualDuration());
		result.put("attemptNumber", attempt);
		result.put("videoQuality", videoResult.getVideoQuality());
		result.put("finalStatus", videoResult.isConnected() ? "SUCCESS" : "FAILED");
		
		if (videoResult.isConnected()) {
		//  Report video call connection status
			ProgressReporter.reportCallingProgress(
				 callerDeviceId,
				 receiverNumber,
				 "Video Call Connected",
				 "CONNECTED",
				 duration,
				 60.0
		);
		
		// Report progress during video call
		for (int sec = 1; sec <= videoResult.getActualDuration(); sec++) {
		 double progress = 60.0 + (40.0 * sec / duration);
		 if (sec % 5 == 0 || sec == duration) {
		     ProgressReporter.reportCallingProgress(
		         callerDeviceId,
		         receiverNumber,
		         "Video Call In Progress (" + sec + "s/" + duration + "s)",
		         "IN_CALL",
		         duration,
		         progress
		     );
		 }
		 Thread.sleep(1000);
		}
		
		ProgressReporter.reportCallingProgress(
		 callerDeviceId,
		 receiverNumber,
		 "Video Call Completed",
		 "COMPLETED",
		 duration,
		 100.0
		);
	}
		
		//  CRITICAL FIX: Store auto-answer status correctly
		if (callHandling.equals("AUTO_ANSWER")) {
		result.put("autoAnswerEnabled", true);
		result.put("autoAnswerStatus", "YES");
		} else {
		result.put("autoAnswerEnabled", false);
		result.put("autoAnswerStatus", "NO - MANUAL");
		}
		
		if (videoResult.isConnected()) {
		System.out.println("   Video call successful on attempt " + attempt);
		break;
		} else {
		if (attempt < attempts) {
		 System.out.println("  ⏳ Waiting before...");
		 Thread.sleep(5000);
		}
	}
}
		} catch (Exception e) {
		System.out.println("  ❌ Video call error: " + e.getMessage());
		result.put("finalStatus", "ERROR");
		result.put("comments", "Error: " + e.getMessage());
		result.put("ringTime", 0);
		result.put("autoAnswerEnabled", false);
		result.put("autoAnswerStatus", "ERROR");
		}
    }
    
    /**
     *  Execute VoLTE specific call (with caller device)
     */
    private void executeVolteCall(String callerDeviceId, String receiverNumber, int duration, 
                                   int attempts, String callHandling, Map<String, Object> result) {
        try {
            System.out.println("📞 Initiating VoLTE call from device: " + callerDeviceId);
            
            boolean aPartyVolteActive = "true".equals(result.get("aPartyVolteEnabled"));
            
            if (!aPartyVolteActive) {
                System.out.println("  ⚠️ VoLTE not active on A-Party, will fallback to CS");
                result.put("volteStatus", "FALLBACK_TO_CS");
            }
            
            executeVoiceCall(callerDeviceId, receiverNumber, duration, attempts, callHandling, result);
            
            if ("SUCCESS".equals(result.get("finalStatus"))) {
                result.put("volteCallStatus", aPartyVolteActive ? "USED_VOLTE" : "FALLBACK_TO_CS");
            }
        } catch (Exception e) {
            System.out.println("  ❌ VoLTE call error: " + e.getMessage());
            result.put("finalStatus", "ERROR");
        }
    }
    
    /**
     *  Execute CONFERENCE call (3-way) (with caller device)
     */
    private void executeConferenceCall(String callerDeviceId, String receiverNumber, String cParty, 
                                        int duration, int attempts, String callHandling, 
                                        Map<String, Object> result) {
        try {
            System.out.println("👥 Initiating CONFERENCE call from device: " + callerDeviceId);
            System.out.println("  B-Party: " + receiverNumber);
            System.out.println("  C-Party: " + cParty);
            
            AndroidDriver callerDriver = getDriverForDevice(callerDeviceId);
            ImprovedDialerPage callerDialerPage = new ImprovedDialerPage(callerDriver);
            
            // First call to B-Party
            int bPartyDuration = (int) (duration * 0.3);
            executeVoiceCall(callerDeviceId, receiverNumber, bPartyDuration, 1, callHandling, result);
            
            if ("SUCCESS".equals(result.get("finalStatus"))) {
                System.out.println("   B-Party connected, adding C-Party...");
                
                ImprovedDialerPage.ConferenceResult confResult = 
                    callerDialerPage.addPartyToConferenceSimple(cParty, bPartyDuration);
                
                if (confResult.isConferenceSuccess()) {
                    System.out.println("   Conference established");
                    result.put("conferenceStatus", "SUCCESS");
                    result.put("conferenceMembers", 2);
                    result.put("conferenceDuration", confResult.getConferenceDuration());
                    
                    int remainingTime = duration - bPartyDuration;
                    if (remainingTime > 0) {
                        System.out.println("  ⏳ Conference duration: " + remainingTime + "s");
                        Thread.sleep(remainingTime * 1000);
                    }
                    
                    result.put("actualDuration", duration);
                } else {
                    System.out.println("  ❌ Conference setup failed");
                    result.put("conferenceStatus", "FAILED");
                    result.put("finalStatus", "PARTIAL_SUCCESS");
                }
            }
        } catch (Exception e) {
            System.out.println("  ❌ Conference call error: " + e.getMessage());
            result.put("finalStatus", "ERROR");
        }
    }
    
    /**
     *  ENHANCED: Determine call handling based on direction
     */
    /**
     *  SIMPLIFIED: Determine call handling based on direction and device availability
     */
    private String determineCallHandling(String receiverDeviceId, String receiverNumber, String direction) {
        // Device validation already handled in validateDeviceConnectivityEnhanced
        // This method now only determines if auto-answer should be used
        
        if (receiverDeviceId == null || receiverDeviceId.isEmpty()) {
            System.out.println("  ℹ️ Receiver device not configured - manual answer expected");
            return "MANUAL_EXPECTED";
        }
        
        List<String> connectedDevices = ADBHelper.getConnectedDevices();
        boolean receiverConnected = connectedDevices.contains(receiverDeviceId);
        
        if (receiverConnected) {
            // Auto-answer is available if device is connected
            System.out.println("   Receiver device connected - auto-answer available");
            return "AUTO_ANSWER";
        } else {
            // Device validation should have already caught this for INCOMING tests
            // For OUTGOING, manual answer is expected
            System.out.println("  ℹ️ Manual answer expected");
            return "MANUAL_EXPECTED";
        }
    }
    
    /**
     *  Set network type
     */
    private void setNetworkType(String deviceId, String networkType) {
        try {
            if ("AUTO".equals(networkType)) {
                System.out.println("   Network set to AUTO");
                return;
            }
            
            NetworkManager.setNetworkType(deviceId, networkType);
            System.out.println("   Network set to: " + networkType);
        } catch (Exception e) {
            System.out.println("  ⚠️ Network change failed: " + e.getMessage());
        }
    }
    
    /**
     *  Store VoLTE status in result
     */
    private void storeVolteStatus(Map<String, Object> result, 
                                   Map<String, String> aPartyVolte,
                                   Map<String, String> bPartyVolte) {
        // A-Party VoLTE
        result.put("aPartyVolteStatus", aPartyVolte.getOrDefault("volteStatus", "UNKNOWN"));
        result.put("aPartyVolteEnabled", aPartyVolte.getOrDefault("volteEnabled", "false"));
        result.put("aPartyImsRegistration", aPartyVolte.getOrDefault("imsRegistration", "UNKNOWN"));
        result.put("aPartyNetworkType", aPartyVolte.getOrDefault("networkType", "UNKNOWN"));
        result.put("aPartyRATType", detectRAT(aPartyVolte.getOrDefault("networkType", "UNKNOWN")));
        
        // B-Party VoLTE
        if (!bPartyVolte.isEmpty()) {
            result.put("bPartyVolteStatus", bPartyVolte.getOrDefault("volteStatus", "UNKNOWN"));
            result.put("bPartyVolteEnabled", bPartyVolte.getOrDefault("volteEnabled", "false"));
            result.put("bPartyImsRegistration", bPartyVolte.getOrDefault("imsRegistration", "UNKNOWN"));
            result.put("bPartyNetworkType", bPartyVolte.getOrDefault("networkType", "UNKNOWN"));
            result.put("bPartyRATType", detectRAT(bPartyVolte.getOrDefault("networkType", "UNKNOWN")));
        }
    }
    
    /**
     *  Detect RAT type from network type
     */
    private String detectRAT(String networkType) {
        if (networkType == null || networkType.isEmpty()) return "UNKNOWN";
        
        String upper = networkType.toUpperCase();
        if (upper.contains("NR") || upper.contains("5G")) return "5G";
        if (upper.contains("LTE") || upper.contains("4G")) return "4G";
        if (upper.contains("HSPA") || upper.contains("UMTS") || upper.contains("WCDMA")) return "3G";
        if (upper.contains("EDGE") || upper.contains("GPRS") || upper.contains("GSM")) return "2G";
        
        return "UNKNOWN";
    }
    
    /**
     *  Detect call failure
     */
    private boolean detectCallFailure(String pageSource) {
        return pageSource.contains("Call failed") || 
               pageSource.contains("Busy") || 
               pageSource.contains("Call barred") || 
               pageSource.contains("Invalid number") ||
               pageSource.contains("Call not sent") ||
               pageSource.contains("Network busy") ||
               pageSource.contains("Switching") ||
               pageSource.contains("Pack expired") ||
               pageSource.contains("Format incorrect") ||
               pageSource.contains("Unable to call") ||
               pageSource.contains("Call ending");
    }
    
    /**
     *  Get detailed failure reason
     */
    private String getDetailedFailureReason(String pageSource) {
        if (pageSource.contains("Busy")) return "BUSY";
        else if (pageSource.contains("Call barred")) return "CALL_BARRED";
        else if (pageSource.contains("Invalid number")) return "INVALID_NUMBER";
        else if (pageSource.contains("Network busy")) return "NETWORK_BUSY";
        else if (pageSource.contains("Pack expired")) return "PACK_EXPIRED";
        else if (pageSource.contains("Format incorrect")) return "FORMAT_INCORRECT";
        else if (pageSource.contains("Switching")) return "CALL_SWITCHED";
        else if (pageSource.contains("Unable to call")) return "UNABLE_TO_CALL";
        else if (pageSource.contains("Call ending")) return "CALL_ENDING";
        else return "CALL_FAILED";
    }
    
    
    private String buildCallingComments(Map<String, Object> result) {
        StringBuilder comments = new StringBuilder();
        
        String direction = (String) result.getOrDefault("direction", "OUTGOING");
        String callStatus = (String) result.getOrDefault("callStatus", "UNKNOWN");
        int actualDuration = (Integer) result.getOrDefault("actualDuration", 0);
        int ringTime = (Integer) result.getOrDefault("ringTime", 0);
        
        comments.append(direction).append(" Call: ").append(callStatus);
        
        // Add ring time
        if (ringTime > 0) {
            comments.append(" | Ring: ").append(ringTime).append("s");
        }
        
        comments.append(" | Duration: ").append(actualDuration).append("s");
        
        //  ENHANCED: Add balance information for BOTH parties in incoming calls
        comments.append(" | ");
        
        if ("INCOMING".equals(direction)) {
            // For incoming calls, show both B-Party (caller) and A-Party (receiver) info
            
            // B-Party (Caller) info
            if (result.containsKey("bPartyBeforeBalance") && !"N/A".equals(result.get("bPartyBeforeBalance"))) {
                comments.append("Caller(").append(result.get("callerNumber")).append("): ");
                comments.append("Before=₹").append(result.get("bPartyBeforeBalance"));
                
                if (result.containsKey("bPartyAfterBalance") && !"N/A".equals(result.get("bPartyAfterBalance"))) {
                    comments.append(", After=₹").append(result.get("bPartyAfterBalance"));
                    
                    if (result.containsKey("bPartyBalanceDeduction")) {
                        Object deduction = result.get("bPartyBalanceDeduction");
                        if (deduction instanceof Number) {
                            comments.append(", Deduction=₹").append(String.format("%.2f", ((Number)deduction).doubleValue()));
                        }
                    }
                }
                comments.append(" | ");
            }
            
            // A-Party (Receiver) info
            if (result.containsKey("aPartyBeforeBalance") && !"N/A".equals(result.get("aPartyBeforeBalance"))) {
                comments.append("Receiver(").append(result.get("receiverNumber")).append("): ");
                comments.append("Before=₹").append(result.get("aPartyBeforeBalance"));
                
                if (result.containsKey("aPartyAfterBalance") && !"N/A".equals(result.get("aPartyAfterBalance"))) {
                    comments.append(", After=₹").append(result.get("aPartyAfterBalance"));
                    
                    if (result.containsKey("aPartyBalanceDeduction")) {
                        Object deduction = result.get("aPartyBalanceDeduction");
                        if (deduction instanceof Number) {
                            double dedValue = ((Number)deduction).doubleValue();
                            if (Math.abs(dedValue) > 0.01) {
                                comments.append(", Deduction=₹").append(String.format("%.2f", dedValue));
                            }
                        }
                    }
                }
            }
            
        } else {
            // For outgoing calls, show A-Party (caller) info only
            if (result.containsKey("beforeBalance") && !"N/A".equals(result.get("beforeBalance"))) {
                comments.append("Caller(").append(result.get("callerNumber")).append("): ");
                comments.append("Before=₹").append(result.get("beforeBalance"));
                
                if (result.containsKey("afterBalance") && !"N/A".equals(result.get("afterBalance"))) {
                    comments.append(", After=₹").append(result.get("afterBalance"));
                    
                    if (result.containsKey("balanceDeduction")) {
                        Object deduction = result.get("balanceDeduction");
                        if (deduction instanceof Number) {
                            comments.append(", Deduction=₹").append(String.format("%.2f", ((Number)deduction).doubleValue()));
                        }
                    }
                }
            }
        }
        
        // Auto-answer status
        if (result.containsKey("autoAnswerEnabled") && (Boolean) result.get("autoAnswerEnabled")) {
            comments.append(" | Auto-Answer: ");
        } else {
            comments.append(" | Manual Answer");
        }
        
        return comments.toString();
    }

    
    /**
     *  Generate reports
     */
    private void generateReports() {
        try {
            String excelReport = ReportGenerator.generateCallingExcelReport(testResults);
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println("📊 REPORTS GENERATED");
            System.out.println("Excel: " + excelReport);
            System.out.println("=".repeat(100));
        } catch (Exception e) {
            System.out.println("⚠️ Report generation failed: " + e.getMessage());
        }
    }
    
    /**
     *  Print execution summary - FIXED ClassCastException
     */
    private void printSummary() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("📈 TEST SUMMARY");
        System.out.println("=".repeat(100));
        
        long success = testResults.stream()
            .filter(r -> "SUCCESS".equals(r.get("finalStatus")))
            .count();
        long failed = testResults.size() - success;
        
        System.out.println("Total Tests: " + testResults.size());
        System.out.println(" Passed: " + success);
        System.out.println("❌ Failed: " + failed);
        
        if (testResults.size() > 0) {
            double rate = (success * 100.0) / testResults.size();
            System.out.println("📈 Success Rate: " + String.format("%.1f%%", rate));
        }
        
        // Print detailed breakdown
        long autoAnswered = testResults.stream()
            .filter(r -> "YES".equals(r.get("autoAnswerStatus")))
            .count();
        
        long manualAnswered = testResults.stream()
            .filter(r -> r.get("autoAnswerStatus") != null && 
                        r.get("autoAnswerStatus").toString().contains("MANUAL"))
            .count();
        
        System.out.println("\n📞 Call Handling:");
        System.out.println("   Auto-Answered: " + autoAnswered);
        System.out.println("   Manual Answered: " + manualAnswered);
        
        //  FIX: Calculate average ring time - handle both Integer and Double
        double avgRingTime = testResults.stream()
            .filter(r -> r.containsKey("ringTimeSeconds"))
            .mapToDouble(r -> {
                Object ringTime = r.get("ringTimeSeconds");
                if (ringTime instanceof Integer) {
                    return ((Integer) ringTime).doubleValue();
                } else if (ringTime instanceof Double) {
                    return (Double) ringTime;
                } else if (ringTime instanceof Number) {
                    return ((Number) ringTime).doubleValue();
                } else {
                    return 0.0;
                }
            })
            .average()
            .orElse(0.0);
        
        if (avgRingTime > 0) {
            System.out.println("\n⏱️ Average Ring Time: " + String.format("%.2f", avgRingTime) + "s");
        }
        
        System.out.println("=".repeat(100) + "\n");
    }
    
    /**
     *  Helper method to handle USSD check failures
     */
    private void handleUSSDCheckFailure(Map<String, Object> ussdResult, String deviceId, 
                                        Map<String, Object> result, String partyType, 
                                        String direction) {
        
        if (ussdResult != null && ussdResult.containsKey("deviceDisconnected") 
            && (Boolean) ussdResult.get("deviceDisconnected")) {
            
            System.out.println("\n❌ SKIPPING TEST: " + partyType + " device disconnected during USSD check");
            result.put("finalStatus", "SKIPPED");
            result.put("callStatus", "DEVICE_DISCONNECTED");
            
            if ("INCOMING".equals(direction)) {
                result.put("comments", partyType + " device " + deviceId + " is disconnected. " +
                           "For incoming tests, both devices must be connected.");
            } else {
                result.put("comments", partyType + " device " + deviceId + " is disconnected. " +
                           "Please reconnect and retry this test.");
            }
            
            // Clear cache for this device
            lastPostCallUSSDCache.remove(deviceId);
        } else {
            System.out.println("   ⚠️ " + partyType + " USSD check failed after retries");
        }
    }
    
    /**
     *  Inner class for call metrics
     */
    private static class CallMetrics {
        long ringTimeMs = 0;
        double ringTimeSeconds = 0.0;
        @SuppressWarnings("unused")
		int actualDurationSeconds = 0;
    }
}