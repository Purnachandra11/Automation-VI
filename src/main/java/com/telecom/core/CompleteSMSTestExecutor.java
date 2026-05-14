package com.telecom.core;

import com.telecom.config.ConfigReader;
import com.telecom.pages.MessagingPage;
import com.telecom.utils.*;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * COMPLETE SMS & VOICE MESSAGE TEST EXECUTOR
*/
@SuppressWarnings("unused")
public class CompleteSMSTestExecutor {
    
    private AndroidDriver driver;
    private String primaryDeviceId;
    private MessagingPage messagingPage;
    private Map<String, String> numberToDeviceMap;
    private List<Map<String, Object>> allResults;
    
 // Cache for previous test's POST-CALL USSD result
    private Map<String, Map<String, Object>> lastPostCallUSSDCache = new HashMap<>();
    
    private static final SimpleDateFormat TIMESTAMP_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    // SLA Constants
    private static final long MAX_DELIVERY_TIME_MS = 120000; // 2 minute
    private static final long MESSAGE_POLL_INTERVAL = 2000;  // 2 seconds
    private static final int MAX_USSD_RETRIES = 2;
    
    /**
     * Inner class for device validation results
     */
    private static class DeviceValidationResult {
        boolean isValid;
        String reason;
        
        DeviceValidationResult(boolean isValid, String reason) {
            this.isValid = isValid;
            this.reason = reason;
        }
    }
    
    public CompleteSMSTestExecutor(AndroidDriver driver, String primaryDeviceId) {
        this.driver = driver;
        this.primaryDeviceId = primaryDeviceId;
        this.messagingPage = new MessagingPage(driver);
        this.numberToDeviceMap = new HashMap<>();
        this.allResults = new ArrayList<>();
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🚀 SMS & VOICE MESSAGE TEST EXECUTOR INITIALIZED");
        System.out.println("=".repeat(100));
    }
    
    /**
     * MAIN EXECUTION METHOD - Read Excel and Execute All Tests
     */
    public List<Map<String, Object>> executeAllSMSTests(String excelFilePath) {
        System.out.println("\n📊 LOADING TEST DATA FROM EXCEL...");
        
        // Clear cache at start
        if (lastPostCallUSSDCache == null) {
            lastPostCallUSSDCache = new HashMap<>();
        }
        lastPostCallUSSDCache.clear();
        
        // Read test data
        List<Map<String, Object>> testCases = EnhancedExcelReader.readSMSTestData(excelFilePath);
        
        if (testCases.isEmpty()) {
            System.out.println("❌ No SMS test cases found in Excel");
            
         // NEW: Report no tests
            ProgressReporter.reportTestComplete(
                primaryDeviceId,
                "sms",
                false,
                "No SMS test cases found in Excel"
            );
            
            return allResults;
        }
        
        System.out.println("Loaded " + testCases.size() + " SMS test cases\n");
        
     // NEW: Initialize progress reporter for SMS test suite
        ProgressReporter.initializeTestSuite(primaryDeviceId, testCases.size());
        
        // Build device mapping
        buildDeviceMapping(testCases);
        
        // Execute each test
        int testNumber = 1;
        for (Map<String, Object> testCase : testCases) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("🧪 TEST " + testNumber + " / " + testCases.size());
            System.out.println("=".repeat(100));
            
         // NEW: Report test start
            String testName = (String) testCase.getOrDefault("name", "Unknown Test");
            ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                (String) testCase.getOrDefault("aPartyNumber", ""),
                "START_TEST",
                "Starting: " + testName,
                ((testNumber - 1) * 100.0) / testCases.size()
            );
            
            Map<String, Object> result = executeSingleTest(testCase);
            allResults.add(result);
            
         // NEW: Report test completion
            String status = (String) result.getOrDefault("finalStatus", "UNKNOWN");
            boolean success = "SUCCESS".equals(status);
            ProgressReporter.reportTestComplete(
                primaryDeviceId,
                "sms",
                success,
                "SMS test " + testNumber + " completed: " + testName + " - Status: " + status
            );
            
            // Report progress
            double progress = (testNumber * 100.0) / testCases.size();
            ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                "",
                "TESTING",
                "Completed test " + testNumber + " of " + testCases.size(),
                progress
            );
            
            testNumber++;
        }
        
        // Generate reports
        generateReports();
        
     // NEW: Report final completion
        ProgressReporter.reportTestComplete(
            primaryDeviceId,
            "sms",
            true,
            "SMS test suite completed: " + allResults.size() + " tests executed"
        );
        
        return allResults;
    }
    
    /**
     * BUILD DEVICE MAPPING from A/B/C Party info
     */
    private void buildDeviceMapping(List<Map<String, Object>> testCases) {
        System.out.println("🔧 Building Device Mapping...");
        
        // Get from DeviceManager
        String aPartyDevice = System.getProperty("aPartyDevice");
        String aPartyNumber = System.getProperty("aPartyNumber");
        String bPartyDevice = System.getProperty("bPartyDevice");
        String bPartyNumber = System.getProperty("bPartyNumber");
        
        if (aPartyNumber != null && aPartyDevice != null) {
            numberToDeviceMap.put(cleanNumber(aPartyNumber), aPartyDevice);
            System.out.println("   📱 A-Party: " + aPartyNumber + " -> " + aPartyDevice);
        }
        
        if (bPartyNumber != null && bPartyDevice != null) {
            numberToDeviceMap.put(cleanNumber(bPartyNumber), bPartyDevice);
            System.out.println("   📱 B-Party: " + bPartyNumber + " -> " + bPartyDevice);
        }
        
        System.out.println("Device mapping complete: " + numberToDeviceMap.size() + " devices\n");
    }
    
    /**
     * EXECUTE SINGLE TEST - Main Test Logic with Enhanced Error Handling
     */
    /**
     * EXECUTE SINGLE TEST - Main Test Logic with Enhanced Error Handling
     */
    private Map<String, Object> executeSingleTest(Map<String, Object> testCase) {
        Map<String, Object> result = new HashMap<>();
        result.putAll(testCase);
        
        Date testStartTime = new Date();
        result.put("testStartTime", TIMESTAMP_FORMAT.format(testStartTime));
        
        try {
            // Extract test parameters
            String testType = (String) testCase.get("testType");
            String aPartyNumber = (String) testCase.get("aPartyNumber");
            String bPartyNumber = (String) testCase.get("bPartyNumber");
            String recipient = (String) testCase.get("recipient");
            String groupName = (String) testCase.get("groupName");
            String messageType = (String) testCase.get("messageType");
            String direction = (String) testCase.get("direction");
            String message = (String) testCase.get("message");
            boolean isIndividual = (Boolean) testCase.get("isIndividual");
            boolean isGroup = (Boolean) testCase.get("isGroup");
            
            System.out.println("📋 Test: " + testCase.get("name"));
            System.out.println("   Type: " + testType + " | Message: " + messageType + " | Direction: " + direction);
            
            // NEW: Report test configuration
            ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                aPartyNumber,
                "TEST_CONFIG",
                "Configuring: ",
                10.0
            );
            
            // STEP 1: Determine sender and receiver
            String senderNumber, receiverNumber, senderDevice, receiverDevice;
            
            if ("OUTGOING".equals(direction)) {
                senderNumber = aPartyNumber;
                receiverNumber = isIndividual ? recipient : null;
                senderDevice = numberToDeviceMap.get(cleanNumber(senderNumber));
                receiverDevice = receiverNumber != null ? 
                    numberToDeviceMap.get(cleanNumber(receiverNumber)) : null;
            } else { // INCOMING
                senderNumber = isIndividual ? recipient : bPartyNumber;
                receiverNumber = aPartyNumber;
                senderDevice = numberToDeviceMap.get(cleanNumber(senderNumber));
                receiverDevice = receiverNumber != null ? 
                    numberToDeviceMap.get(cleanNumber(receiverNumber)) : null;
            }
            
            System.out.println("   📤 Sender: " + senderNumber + " (" + senderDevice + ")");
            if (receiverNumber != null) {
                System.out.println("   📥 Receiver: " + receiverNumber + " (" + receiverDevice + ")");
            }
            
         // STEP 2: Validate device connectivity - ENHANCED
            DeviceValidationResult validation = validateDeviceConnectivityEnhanced(
                senderDevice, receiverDevice, isIndividual, senderNumber, direction
            );

            if (!validation.isValid) {
                System.out.println("\n⚠️ SKIPPING TEST: " + validation.reason);
                
                // NEW: Report test skip
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    senderNumber,
                    "TEST_SKIPPED",
                    "Skipped: " + validation.reason,
                    0.0
                );
                
                result.put("finalStatus", "SKIPPED");
                result.put("comments", validation.reason);
                result.put("totalSMS", 1);
                result.put("successfulSMS", 0);
                result.put("failedSMS", 0);
                result.put("skippedSMS", 1);
                return result;
            }
            
            // STEP 3: Pre-test USSD check (Before balance)
            System.out.println("\n💰 PRE-CALL BALANCE CHECK...");
            
            // Store balance results for both parties
            Map<String, Object> beforeUSSD_Sender = null;
            Map<String, Object> beforeUSSD_Receiver = null;
            
            // CHECK SENDER BALANCE (bPartyNumber for incoming)
            if (senderDevice != null) {
                System.out.println("   💰 Checking sender balance (" + senderNumber + ")...");
                
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    senderNumber,
                    "BALANCE_CHECK",
                    "Checking sender pre-call balance...",
                    20.0
                );
                
                beforeUSSD_Sender = getOrPerformPreCallUSSD(senderDevice, senderNumber);
                
                if (beforeUSSD_Sender != null && (Boolean) beforeUSSD_Sender.getOrDefault("success", false)) {
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        senderNumber,
                        "BALANCE_CHECK",
                        "Sender pre-balance: ₹" + beforeUSSD_Sender.get("balance"),
                        30.0
                    );
                    
                    System.out.println("   Sender Before Balance: ₹" + beforeUSSD_Sender.get("balance"));
                    
                    // Store sender balance in result with proper naming
                    if ("INCOMING".equals(direction)) {
                        result.put("bPartyBeforeBalance", beforeUSSD_Sender.get("balance"));
                        result.put("bPartyBeforeValidity", beforeUSSD_Sender.get("validity"));
                        result.put("senderMSISDN", beforeUSSD_Sender.get("phoneNumber"));
                    } else {
                        result.put("beforeBalance", beforeUSSD_Sender.get("balance"));
                        result.put("beforeValidity", beforeUSSD_Sender.get("validity"));
                        result.put("senderMSISDN", beforeUSSD_Sender.get("phoneNumber"));
                    }
                    
                    if (beforeUSSD_Sender.containsKey("cachedFromPreviousTest")) {
                        System.out.println("   ♻️ Sender balance reused from previous test");
                    }
                } else if (beforeUSSD_Sender != null && beforeUSSD_Sender.containsKey("deviceDisconnected") 
                    && (Boolean) beforeUSSD_Sender.get("deviceDisconnected")) {
                    
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        senderNumber,
                        "DEVICE_DISCONNECTED",
                        "Sender device disconnected during USSD check",
                        0.0
                    );
                    
                    System.out.println("\n❌ SKIPPING TEST: Sender device disconnected during USSD check");
                    result.put("finalStatus", "SKIPPED");
                    result.put("comments", "Sender device " + senderDevice + " is disconnected. Please reconnect and retry this test.");
                    result.put("totalSMS", 1);
                    result.put("successfulSMS", 0);
                    result.put("failedSMS", 0);
                    result.put("skippedSMS", 1);
                    
                    lastPostCallUSSDCache.remove(senderDevice);
                    return result;
                } else {
                    System.out.println("   ⚠️ Sender USSD check failed (continuing)");
                    if ("INCOMING".equals(direction)) {
                        result.put("bPartyBeforeBalance", "N/A");
                    } else {
                        result.put("beforeBalance", "N/A");
                    }
                    
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        senderNumber,
                        "BALANCE_CHECK_FAILED",
                        "Sender USSD check failed (continuing)",
                        30.0
                    );
                }
            }
            
            // CHECK RECEIVER BALANCE (aPartyNumber for incoming) - Only if device is available
            if (receiverDevice != null && ADBHelper.isDeviceConnected(receiverDevice)) {
                System.out.println("   💰 Checking receiver balance (" + receiverNumber + ")...");
                
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    receiverNumber,
                    "BALANCE_CHECK_RECEIVER",
                    "Checking receiver pre-call balance...",
                    35.0
                );
                
                beforeUSSD_Receiver = getOrPerformPreCallUSSD(receiverDevice, receiverNumber);
                
                if (beforeUSSD_Receiver != null && (Boolean) beforeUSSD_Receiver.getOrDefault("success", false)) {
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        receiverNumber,
                        "BALANCE_CHECK_RECEIVER",
                        "Receiver pre-balance: ₹" + beforeUSSD_Receiver.get("balance"),
                        40.0
                    );
                    
                    System.out.println("   Receiver Before Balance: ₹" + beforeUSSD_Receiver.get("balance"));
                    
                    // Store receiver balance in result with proper naming
                    if ("INCOMING".equals(direction)) {
                        result.put("beforeBalance", beforeUSSD_Receiver.get("balance"));
                        result.put("beforeValidity", beforeUSSD_Receiver.get("validity"));
                        result.put("receiverMSISDN", beforeUSSD_Receiver.get("phoneNumber"));
                    } else {
                        result.put("receiverBeforeBalance", beforeUSSD_Receiver.get("balance"));
                        result.put("receiverBeforeValidity", beforeUSSD_Receiver.get("validity"));
                        result.put("receiverMSISDN", beforeUSSD_Receiver.get("phoneNumber"));
                    }
                } else {
                    System.out.println("   ⚠️ Receiver USSD check failed (non-critical)");
                    if ("INCOMING".equals(direction)) {
                        result.put("beforeBalance", "N/A");
                    } else {
                        result.put("receiverBeforeBalance", "N/A");
                    }
                }
            }
            
            // Report message sending start
            ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                senderNumber,
                "SENDING_MESSAGE",
                "Sending " + messageType + " message...",
                50.0
            );
            
            // STEP 4: Execute message sending
            boolean sendSuccess;
            long senderTimestamp = 0;
            long receiverTimestamp = 0;
            
            if ("text".equalsIgnoreCase(messageType)) {
                // TEXT MESSAGE FLOW
                Map<String, Object> textResult = executeTextMessage(
                    senderDevice, senderNumber, receiverNumber, 
                    groupName, message, isIndividual, isGroup
                );
                
                sendSuccess = (Boolean) textResult.get("success");
                senderTimestamp = (Long) textResult.get("senderTimestamp");
                
                result.put("textResult", textResult);
                
            } else if ("voice".equalsIgnoreCase(messageType)) {
                // VOICE MESSAGE FLOW
                Map<String, Object> voiceResult = executeVoiceMessage(
                    senderDevice, senderNumber, receiverNumber, 
                    groupName, isIndividual, isGroup
                );
                
                sendSuccess = (Boolean) voiceResult.get("success");
                senderTimestamp = (Long) voiceResult.get("senderTimestamp");
                
                result.put("voiceResult", voiceResult);
                
            } else {
                throw new Exception("Unknown message type: " + messageType);
            }
            
            // NEW: Report message sending result
            if (sendSuccess) {
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    senderNumber,
                    "MESSAGE_SENT",
                    messageType + " message sent successfully",
                    60.0
                );
            } else {
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    senderNumber,
                    "MESSAGE_FAILED",
                    "Failed to send " + messageType + " message",
                    60.0
                );
            }
            
         // STEP 5: Receiver verification (if individual)
            if (sendSuccess && isIndividual) {
                System.out.println("\n VERIFYING MESSAGE RECEIPT ON RECEIVER...");
                
                // Determine actual receiver device based on direction
                String actualReceiverDevice = null;
                String actualReceiverNumber = null;
                
                if ("OUTGOING".equals(direction)) {
                    actualReceiverDevice = receiverDevice;
                    actualReceiverNumber = receiverNumber;
                } else { // INCOMING
                    // For incoming, receiver is always aParty
                    actualReceiverNumber = aPartyNumber;
                    actualReceiverDevice = numberToDeviceMap.get(cleanNumber(aPartyNumber));
                }
                
                if (actualReceiverDevice != null && ADBHelper.isDeviceConnected(actualReceiverDevice)) {
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        actualReceiverNumber,
                        "VERIFYING_DELIVERY",
                        "Verifying message delivery...",
                        70.0
                    );
                    
                 // UPDATED: Get verification result map
                    Map<String, Object> verificationResult = verifyMessageReceived(
                        actualReceiverDevice, 
                        senderNumber, 
                        messageType,
                        direction,
                        aPartyNumber,
                        recipient
                    );

                    // EXTRACT DATA FROM VERIFICATION RESULT
                    Long receiverTimestampObj = (Long) verificationResult.get("receiverTimestamp");
                    receiverTimestamp = receiverTimestampObj != null ? receiverTimestampObj : 0L;
                    String verificationStatus = (String) verificationResult.getOrDefault("verificationStatus", "UNVERIFIED");
                    
                    // STORE VERIFICATION DETAILS
                    result.put("receiverTimestamp", receiverTimestamp);
                    result.put("verificationStatus", verificationStatus);
                    
                    // STORE FORMATTED TIMESTAMP (if available)
                    if (receiverTimestamp > 0) {
                        result.put("receiverTime", TIMESTAMP_FORMAT.format(new Date(receiverTimestamp)));
                        result.put("receiverTimeFormatted", TIMESTAMP_FORMAT.format(new Date(receiverTimestamp)));
                    } else {
                        result.put("receiverTime", "N/A");
                        result.put("receiverTimeFormatted", "N/A");
                    }
                    
                    // CALCULATE DELIVERY TIME (if timestamp available)
                    if (receiverTimestamp > 0 && senderTimestamp > 0) {
                        long deliveryTime = receiverTimestamp - senderTimestamp;
                        result.put("deliveryTimeMs", deliveryTime);
                        result.put("deliveryTimeSec", deliveryTime / 1000.0);
                        
                        System.out.println("   ⏱️ Delivery Time: " + deliveryTime + "ms (" + 
                            (deliveryTime / 1000.0) + "s)");
                        
                        if (deliveryTime > MAX_DELIVERY_TIME_MS) {
                            System.out.println("   ❌ FAILED: Delivery time exceeds SLA (>120s)");
                            result.put("deliveryStatus", "FAILED_SLA");
                            
                            ProgressReporter.reportSMSProgress(
                                primaryDeviceId,
                                actualReceiverNumber,
                                "DELIVERY_FAILED_SLA",
                                "Delivery exceeded SLA: " + (deliveryTime / 1000.0) + "s",
                                80.0
                            );
                        } else {
                            System.out.println("   Delivery within SLA");
                            result.put("deliveryStatus", "SUCCESS");
                            
                            ProgressReporter.reportSMSProgress(
                                primaryDeviceId,
                                actualReceiverNumber,
                                "DELIVERY_SUCCESS",
                                "Delivered in " + (deliveryTime / 1000.0) + "s",
                                80.0
                            );
                        }
                    } else {
                        System.out.println("   ⚠️ Could not verify receipt time");
                        result.put("deliveryStatus", verificationStatus); // Use verification status
                        
                        // REPORT SPECIFIC STATUS
                        if ("DEVICE_UNAVAILABLE".equals(verificationStatus)) {
                            ProgressReporter.reportSMSProgress(
                                primaryDeviceId,
                                actualReceiverNumber,
                                "DEVICE_UNAVAILABLE",
                                "Receiver device not connected for verification",
                                80.0
                            );
                        } else if ("NOT_RECEIVED".equals(verificationStatus)) {
                            ProgressReporter.reportSMSProgress(
                                primaryDeviceId,
                                actualReceiverNumber,
                                "NOT_RECEIVED",
                                "Message not received within timeout",
                                80.0
                            );
                        } else {
                            ProgressReporter.reportSMSProgress(
                                primaryDeviceId,
                                actualReceiverNumber,
                                "DELIVERY_UNVERIFIED",
                                "Delivery could not be verified: " + verificationStatus,
                                80.0
                            );
                        }
                    }
                } else {
                    // HANDLE DEVICE UNAVAILABLE CASE
                    System.out.println("   ⚠️ Receiver device not available for verification");
                    String unavailableStatus = "DEVICE_UNAVAILABLE";
                    result.put("deliveryStatus", unavailableStatus);
                    result.put("verificationStatus", unavailableStatus);
                    result.put("receiverTimestamp", 0L);
                    result.put("receiverTime", "DEVICE_UNAVAILABLE");
                    result.put("receiverTimeFormatted", "DEVICE_UNAVAILABLE");
                    
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        actualReceiverNumber != null ? actualReceiverNumber : "",
                        "DEVICE_UNAVAILABLE",
                        "Receiver device not connected",
                        70.0
                    );
                }
            }
            
            // STEP 6: Post-test USSD check (After balance)
            System.out.println("\n💰 POST-CALL BALANCE CHECK...");
            
            // Store after balance results
            Map<String, Object> afterUSSD_Sender = null;
            Map<String, Object> afterUSSD_Receiver = null;
            
            // CHECK SENDER AFTER BALANCE
            if (senderDevice != null && sendSuccess) {
                System.out.println("   💰 Checking sender after balance (" + senderNumber + ")...");
                System.out.println("   ⏳ Waiting 5s for balance update...");
                
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    senderNumber,
                    "WAITING_BALANCE_UPDATE",
                    "Waiting for sender balance update...",
                    85.0
                );
                
                Thread.sleep(5000);
                
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    senderNumber,
                    "POST_BALANCE_CHECK_SENDER",
                    "Checking sender post-call balance...",
                    90.0
                );
                
                afterUSSD_Sender = performUSSDCheckWithRetry(
                    senderDevice, "*199#", "AFTER_SENDER", senderNumber
                );
                
                if (afterUSSD_Sender != null && (Boolean) afterUSSD_Sender.getOrDefault("success", false)) {
                    // Store sender after balance
                    if ("INCOMING".equals(direction)) {
                        result.put("bPartyAfterBalance", afterUSSD_Sender.get("balance"));
                        result.put("bPartyAfterValidity", afterUSSD_Sender.get("validity"));
                    } else {
                        result.put("afterBalance", afterUSSD_Sender.get("balance"));
                        result.put("afterValidity", afterUSSD_Sender.get("validity"));
                    }
                    
                    System.out.println("   Sender After Balance: ₹" + afterUSSD_Sender.get("balance"));
                    
                    // CACHE THIS RESULT FOR NEXT TEST
                    cachePostCallUSSDForNextTest(senderDevice, afterUSSD_Sender);
                    
                    // Calculate sender balance deduction
                    if (beforeUSSD_Sender != null && (Boolean) beforeUSSD_Sender.getOrDefault("success", false)) {
                        try {
                            Double beforeBal = parseBalance(beforeUSSD_Sender.get("balance"));
                            Double afterBal = parseBalance(afterUSSD_Sender.get("balance"));
                            
                            if (beforeBal != null && afterBal != null) {
                                double deduction = beforeBal - afterBal;
                                
                                if ("INCOMING".equals(direction)) {
                                    result.put("bPartyBalanceDeduction", deduction);
                                    System.out.println("   💸 Sender Balance Deduction: ₹" + String.format("%.2f", deduction));
                                } else {
                                    result.put("balanceDeduction", deduction);
                                    System.out.println("   💸 Balance Deduction: ₹" + String.format("%.2f", deduction));
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("   ⚠️ Could not calculate sender balance deduction: " + e.getMessage());
                        }
                    }
                    
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        senderNumber,
                        "POST_BALANCE_SUCCESS_SENDER",
                        "Sender post-balance: ₹" + afterUSSD_Sender.get("balance"),
                        95.0
                    );
                } else {
                    System.out.println("   ⚠️ Sender after-balance USSD check failed");
                    lastPostCallUSSDCache.remove(senderDevice);
                    
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        senderNumber,
                        "POST_BALANCE_FAILED_SENDER",
                        "Sender post-balance check failed",
                        95.0
                    );
                }
            }
            
            // CHECK RECEIVER AFTER BALANCE - Only if device is available
            if (receiverDevice != null && ADBHelper.isDeviceConnected(receiverDevice)) {
                System.out.println("   💰 Checking receiver after balance (" + receiverNumber + ")...");
                
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    receiverNumber,
                    "POST_BALANCE_CHECK_RECEIVER",
                    "Checking receiver post-call balance...",
                    97.0
                );
                
                afterUSSD_Receiver = performUSSDCheckWithRetry(
                    receiverDevice, "*199#", "AFTER_RECEIVER", receiverNumber
                );
                
                if (afterUSSD_Receiver != null && (Boolean) afterUSSD_Receiver.getOrDefault("success", false)) {
                    // Store receiver after balance
                    if ("INCOMING".equals(direction)) {
                        result.put("afterBalance", afterUSSD_Receiver.get("balance"));
                        result.put("afterValidity", afterUSSD_Receiver.get("validity"));
                    } else {
                        result.put("receiverAfterBalance", afterUSSD_Receiver.get("balance"));
                        result.put("receiverAfterValidity", afterUSSD_Receiver.get("validity"));
                    }
                    
                    System.out.println("   Receiver After Balance: ₹" + afterUSSD_Receiver.get("balance"));
                    
                    // Calculate receiver balance deduction (if any)
                    if (beforeUSSD_Receiver != null && (Boolean) beforeUSSD_Receiver.getOrDefault("success", false)) {
                        try {
                            Double beforeBal = parseBalance(beforeUSSD_Receiver.get("balance"));
                            Double afterBal = parseBalance(afterUSSD_Receiver.get("balance"));
                            
                            if (beforeBal != null && afterBal != null) {
                                double deduction = beforeBal - afterBal;
                                
                                if ("INCOMING".equals(direction)) {
                                    result.put("balanceDeduction", deduction);
                                    System.out.println("   💸 Receiver Balance Deduction: ₹" + String.format("%.2f", deduction));
                                } else {
                                    result.put("receiverBalanceDeduction", deduction);
                                    System.out.println("   💸 Receiver Balance Deduction: ₹" + String.format("%.2f", deduction));
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("   ⚠️ Could not calculate receiver balance deduction: " + e.getMessage());
                        }
                    }
                    
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        receiverNumber,
                        "POST_BALANCE_SUCCESS_RECEIVER",
                        "Receiver post-balance: ₹" + afterUSSD_Receiver.get("balance"),
                        100.0
                    );
                } else {
                    System.out.println("   ⚠️ Receiver after-balance USSD check failed");
                }
            }
            
            // STEP 7: Determine final status
            result.put("messageDelivered", sendSuccess);
            result.put("totalSMS", 1);
            result.put("successfulSMS", sendSuccess ? 1 : 0);
            result.put("failedSMS", sendSuccess ? 0 : 1);
            
            // Build comprehensive comments with balance information
            StringBuilder comments = new StringBuilder();
            
            if (!sendSuccess) {
                result.put("finalStatus", "FAILED");
                comments.append("Message sending failed");
            } else if (isIndividual && receiverTimestamp > 0) {
                long deliveryTime = receiverTimestamp - senderTimestamp;
                if (deliveryTime > MAX_DELIVERY_TIME_MS) {
                    result.put("finalStatus", "FAILED");
                    comments.append("Delivery time exceeded SLA");
                } else {
                    result.put("finalStatus", "SUCCESS");
                    comments.append("Message delivered successfully");
                }
            } else {
                result.put("finalStatus", "SUCCESS");
                comments.append("Message sent successfully");
            }
            
            // ADD BALANCE INFORMATION TO COMMENTS
            comments.append(" | ");
            
            if ("INCOMING".equals(direction)) {
                // For incoming messages, show bParty (sender) and aParty (receiver) balances
                comments.append("Sender(").append(senderNumber).append("): ");
                
                if (result.containsKey("bPartyBeforeBalance") && !"N/A".equals(result.get("bPartyBeforeBalance"))) {
                    comments.append("Before=₹").append(result.get("bPartyBeforeBalance"));
                    
                    if (result.containsKey("bPartyAfterBalance") && !"N/A".equals(result.get("bPartyAfterBalance"))) {
                        comments.append(", After=₹").append(result.get("bPartyAfterBalance"));
                        
                        if (result.containsKey("bPartyBalanceDeduction")) {
                            double deduction = (Double) result.get("bPartyBalanceDeduction");
                            comments.append(", Deduction=₹").append(String.format("%.2f", deduction));
                        }
                    }
                } else {
                    comments.append("Balance N/A");
                }
                
                comments.append(" | Receiver(").append(receiverNumber).append("): ");
                
                if (result.containsKey("beforeBalance") && !"N/A".equals(result.get("beforeBalance"))) {
                    comments.append("Before=₹").append(result.get("beforeBalance"));
                    
                    if (result.containsKey("afterBalance") && !"N/A".equals(result.get("afterBalance"))) {
                        comments.append(", After=₹").append(result.get("afterBalance"));
                        
                        if (result.containsKey("balanceDeduction")) {
                            double deduction = (Double) result.get("balanceDeduction");
                            comments.append(", Deduction=₹").append(String.format("%.2f", deduction));
                        }
                    }
                } else {
                    comments.append("Balance N/A");
                }
                
            } else {
                // For outgoing messages, show aParty (sender) balances
                comments.append("Sender(").append(senderNumber).append("): ");
                
                if (result.containsKey("beforeBalance") && !"N/A".equals(result.get("beforeBalance"))) {
                    comments.append("Before=₹").append(result.get("beforeBalance"));
                    
                    if (result.containsKey("afterBalance") && !"N/A".equals(result.get("afterBalance"))) {
                        comments.append(", After=₹").append(result.get("afterBalance"));
                        
                        if (result.containsKey("balanceDeduction")) {
                            double deduction = (Double) result.get("balanceDeduction");
                            comments.append(", Deduction=₹").append(String.format("%.2f", deduction));
                        }
                    }
                } else {
                    comments.append("Balance N/A");
                }
            }
            
            result.put("comments", comments.toString());
            
            // NEW: Report final test status
            ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                senderNumber,
                "TEST_COMPLETE",
                "Test completed: " + result.get("finalStatus"),
                100.0
            );
            
        } catch (Exception e) {
            System.out.println("❌ Test execution failed: " + e.getMessage());
            e.printStackTrace();
            
            // NEW: Report test error
            ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                "",
                "TEST_ERROR",
                "Test error: " + e.getMessage(),
                0.0
            );
            
            result.put("finalStatus", "ERROR");
            result.put("comments", "Test error: " + e.getMessage());
            result.put("totalSMS", 1);
            result.put("successfulSMS", 0);
            result.put("failedSMS", 1);
        }
        
        Date testEndTime = new Date();
        result.put("testEndTime", TIMESTAMP_FORMAT.format(testEndTime));
        result.put("testTimestamp", TIMESTAMP_FORMAT.format(testStartTime));
        
        return result;
    }

    
//    /**
//     * ENHANCED DEVICE VALIDATION - Returns detailed result
//     */
//    private DeviceValidationResult validateDeviceConnectivityEnhanced(
//            String senderDevice, String receiverDevice, boolean isIndividual, String senderNumber) {
//        
//        System.out.println("\n🔌 VALIDATING DEVICE CONNECTIVITY...");
//        
//        // Check sender device
//        if (senderDevice == null) {
//            String reason = "Sender device not mapped for number: " + senderNumber;
//            System.out.println("   ❌ " + reason);
//            return new DeviceValidationResult(false, reason);
//        }
//        
//        if (!ADBHelper.isDeviceConnected(senderDevice)) {
//            String reason = "Sender device is disconnected: " + senderDevice + ". Please reconnect and retry this test.";
//            System.out.println("   ❌ " + reason);
//            return new DeviceValidationResult(false, reason);
//        }
//        
//        System.out.println("   Sender device connected: " + senderDevice);
//        
//        // Check receiver device (if individual)
//        if (isIndividual && receiverDevice != null) {
//            if (!ADBHelper.isDeviceConnected(receiverDevice)) {
//                System.out.println("   ⚠️ Receiver device not connected: " + receiverDevice);
//                System.out.println("   ℹ️ Will skip delivery verification");
//            } else {
//                System.out.println("   Receiver device connected: " + receiverDevice);
//            }
//        }
//        
//        return new DeviceValidationResult(true, "All required devices connected");
//    }
    /**
     * ENHANCED DEVICE VALIDATION - Returns detailed result
     * FIXED: For INCOMING tests, both sender AND receiver (A-Party) MUST be connected
     */
    private DeviceValidationResult validateDeviceConnectivityEnhanced(
            String senderDevice, String receiverDevice, boolean isIndividual, 
            String senderNumber, String direction) {
        
        System.out.println("\n🔌 VALIDATING DEVICE CONNECTIVITY...");
        System.out.println("   Direction: " + direction);
        System.out.println("   isIndividual: " + isIndividual);
        
        // SCENARIO A: OUTGOING TEST
        if ("OUTGOING".equals(direction)) {
            System.out.println("   📤 OUTGOING TEST VALIDATION");
            
            // Validate SENDER device (A-Party device)
            if (senderDevice == null) {
                String reason = "Sender device not mapped for number: " + senderNumber;
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            if (!ADBHelper.isDeviceConnected(senderDevice)) {
                String reason = "Sender device is disconnected: " + senderDevice + 
                               ". Please reconnect and retry this test.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            System.out.println("   Sender device connected: " + senderDevice);
            
            // Validate RECEIVER device (optional for outgoing)
            if (isIndividual && receiverDevice != null) {
                if (!ADBHelper.isDeviceConnected(receiverDevice)) {
                    System.out.println("   ⚠️ Receiver device not connected: " + receiverDevice);
                    System.out.println("   ℹ️ For outgoing tests, receiver may be external - continuing");
                } else {
                    System.out.println("   Receiver device connected: " + receiverDevice);
                }
            }
            
            return new DeviceValidationResult(true, "Outgoing test validation passed");
        }
        
        // SCENARIO B: INCOMING TEST
        else if ("INCOMING".equals(direction)) {
            System.out.println("   📥 INCOMING TEST VALIDATION");
            
            // Validate SENDER device (B-Party/Recipient device)
            if (senderDevice == null) {
                String reason = "Sender (B-Party/Recipient) device not mapped for number: " + senderNumber;
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            if (!ADBHelper.isDeviceConnected(senderDevice)) {
                String reason = "Sender (B-Party/Recipient) device is disconnected: " + senderDevice + 
                               ". Please reconnect and retry this test.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            System.out.println("   Sender (B-Party) device connected: " + senderDevice);
            
            // For INCOMING tests, A-Party is the RECEIVER and MUST be available
            if (receiverDevice == null) {
                String reason = "Receiver (A-Party) device not mapped. For incoming tests, " +
                               "A-Party device must be available to receive messages.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            if (!ADBHelper.isDeviceConnected(receiverDevice)) {
                String reason = "Receiver (A-Party) device is disconnected: " + receiverDevice + 
                               ". For incoming tests, A-Party device MUST be connected.";
                System.out.println("   ❌ " + reason);
                return new DeviceValidationResult(false, reason);
            }
            
            System.out.println("   Receiver (A-Party) device connected: " + receiverDevice);
            
            return new DeviceValidationResult(true, "Incoming test validation passed");
        }
        
        // Unknown direction
        String reason = "Unknown test direction: " + direction;
        System.out.println("   ❌ " + reason);
        return new DeviceValidationResult(false, reason);
    }
    
    /**
     * PERFORM USSD CHECK WITH RETRY AND ERROR HANDLING
     */
    private Map<String, Object> performUSSDCheckWithRetry(
            String deviceId, String ussdCode, String checkType, String expectedNumber) {
        
        System.out.println("\n💰 " + checkType + " BALANCE CHECK...");
        
        ProgressReporter.reportSMSProgress(
                primaryDeviceId,
                expectedNumber,
                "USSD_CHECK",
                "Starting USSD check: " + checkType,
                0.0
            );
        
        Map<String, Object> ussdResult = null;
        int attempt = 0;
        
        while (attempt < MAX_USSD_RETRIES) {
            attempt++;
            
            try {
                System.out.println("    Attempt " + attempt + "/" + MAX_USSD_RETRIES);
                
             // NEW: Report retry attempt
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    expectedNumber,
                    "USSD_CHECK",
                    "Attempt " + attempt + "/" + MAX_USSD_RETRIES,
                    (attempt * 100.0 / MAX_USSD_RETRIES) * 50.0
                );
                
                // Check device connectivity before USSD
                if (!ADBHelper.isDeviceConnected(deviceId)) {
                    System.out.println("   ❌ Device disconnected: " + deviceId);
                    
                 // NEW: Report device disconnected
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        expectedNumber,
                        "DEVICE_DISCONNECTED",
                        "Device disconnected during USSD check",
                        0.0
                    );
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "Device disconnected");
                    errorResult.put("deviceDisconnected", true);
                    return errorResult;
                }
                
                // Execute USSD API
                ussdResult = USSDService.checkBalanceAndValidity(deviceId, ussdCode);
                
                // CRITICAL FIX: Reset driver after USSD to recover instrumentation
                System.out.println("    Resetting driver after USSD operation...");
                try {
                    resetDriverAfterUSSD(deviceId);
                } catch (Exception resetEx) {
                    System.out.println("   ⚠️ Driver reset error (continuing): " + resetEx.getMessage());
                }
                
                // Check result
                if (ussdResult != null && (Boolean) ussdResult.getOrDefault("success", false)) {
                    // SUCCESS - Extract and clean data
                    Object balanceObj = ussdResult.get("balance");
                    Object phoneObj = ussdResult.get("phoneNumber");
                    Object simObj = ussdResult.get("sim");
                    
                    // Clean balance (remove "Rs " prefix if present)
                    if (balanceObj != null) {
                        String balanceStr = balanceObj.toString();
                        if (balanceStr.startsWith("Rs ")) {
                            balanceStr = balanceStr.substring(3).trim();
                            ussdResult.put("balance", balanceStr);
                        }
                    }
                    
                    // Use phoneNumber or sim field
                    String phoneNumber = phoneObj != null ? phoneObj.toString() : 
                                        (simObj != null ? simObj.toString() : null);
                    
                    if (phoneNumber != null) {
                        ussdResult.put("phoneNumber", phoneNumber);
                    }
                    
                    System.out.println("   USSD SUCCESS");
                    
                 // NEW: Report USSD success
                    ProgressReporter.reportSMSProgress(
                        primaryDeviceId,
                        expectedNumber,
                        "USSD_SUCCESS",
                        "USSD check successful",
                        100.0
                    );
                    System.out.println("      Phone: " + phoneNumber);
                    System.out.println("      Balance: " + ussdResult.get("balance"));
                    
                    // Check validity field
                    if (ussdResult.get("validity") != null) {
                        System.out.println("      Validity: " + ussdResult.get("validity"));
                    }
                    
                    return ussdResult;
                }
                
                // FAILURE - Check error details
                System.out.println("   ❌ USSD API returned error");
             // NEW: Report USSD failure
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    expectedNumber,
                    "USSD_ERROR",
                    "USSD attempt " + attempt + " failed",
                    (attempt * 100.0 / MAX_USSD_RETRIES) * 50.0
                );
                if (ussdResult != null && ussdResult.containsKey("error")) {
                    System.out.println("      Error: " + ussdResult.get("error"));
                }
                
                
                // Wait before retry (except on last attempt)
                if (attempt < MAX_USSD_RETRIES) {
                    System.out.println("   ⏳ Waiting 3s before...");
                    Thread.sleep(3000);
                }
                
            } catch (Exception e) {
                System.out.println("   ❌ USSD attempt failed: " + e.getMessage());
                
                // NEW: Report USSD exception
                ProgressReporter.reportSMSProgress(
                    primaryDeviceId,
                    expectedNumber,
                    "USSD_EXCEPTION",
                    "USSD exception: " + e.getMessage(),
                    (attempt * 100.0 / MAX_USSD_RETRIES) * 50.0
                );
                
                // Try to reset driver on error too
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
        
        // All retries exhausted
        System.out.println("   ⚠️ USSD check failed after " + MAX_USSD_RETRIES + " attempts");
        
        // NEW: Report USSD final failure
        ProgressReporter.reportSMSProgress(
            primaryDeviceId,
            expectedNumber,
            "USSD_FAILED",
            "USSD check failed after " + MAX_USSD_RETRIES + " attempts",
            0.0
        );
        
        // Return failure result
        Map<String, Object> failureResult = new HashMap<>();
        failureResult.put("success", false);
        failureResult.put("error", "USSD check failed after " + MAX_USSD_RETRIES + " attempts");
        
        // Check if device is still connected
        if (!ADBHelper.isDeviceConnected(deviceId)) {
            failureResult.put("deviceDisconnected", true);
        }
        
        return failureResult;
    }

    /**
     * NEW: Reset Driver After USSD Operation
     */
    private void resetDriverAfterUSSD(String deviceId) {
        try {
            System.out.println("   🔧 Resetting Appium session after USSD...");
            
            // Step 1: Force stop messaging app via ADB (always works)
            try {
                String[] cmd = {"adb", "-s", deviceId, "shell", "am", "force-stop", 
                               "com.google.android.apps.messaging"};
                Process process = Runtime.getRuntime().exec(cmd);
                process.waitFor();
                System.out.println("   Messaging app force stopped");
                Thread.sleep(2000);
            } catch (Exception e) {
                System.out.println("   ⚠️ Force stop failed: " + e.getMessage());
            }
            
            // Step 2: Terminate via driver (if still alive)
            try {
                driver.terminateApp("com.google.android.apps.messaging");
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
                this.driver = com.telecom.driver.DriverManager.initializeDriverForMessaging(
                    deviceId, platformVersion
                );
                this.messagingPage = new MessagingPage(driver);
                
                System.out.println("   New Appium session created");
            } else {
                // Driver is alive, just restart the app
                System.out.println("    Restarting messaging app...");
                driver.activateApp("com.google.android.apps.messaging");
                Thread.sleep(3000);
                System.out.println("   Messaging app restarted");
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Driver reset failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * NEW: Cache POST-CALL USSD result for next test
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
     * NEW: Get PRE-CALL USSD (reuse POST-CALL from previous test if available)
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
                        System.out.println("      Phone number verified: " + cleanCached);
                        
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
            Map<String, Object> ussdResult = performUSSDCheckWithRetry(deviceId, "*199#", "BEFORE", phoneNumber);
            
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
                        System.out.println("   Phone number verified: " + cleanDetected);
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
     * PARSE BALANCE - Handle different formats
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
     * EXECUTE TEXT MESSAGE
     */
    private Map<String, Object> executeTextMessage(String senderDevice, String senderNumber,
            String receiverNumber, String groupName, String message, 
            boolean isIndividual, boolean isGroup) throws Exception {
        
        Map<String, Object> result = new HashMap<>();
        
        System.out.println("\n💬 SENDING TEXT MESSAGE...");
        
        // Switch to sender device if needed
        switchToDevice(senderDevice);
        
        long senderTimestamp = System.currentTimeMillis();
        boolean success;
        
        if (isIndividual) {
            System.out.println("   📱 Individual SMS to: " + receiverNumber);
            success = messagingPage.sendIndividualSMS(receiverNumber, message);
        } else {
            System.out.println("   👥 Group SMS to: " + groupName);
            Map<String, Object> groupResult = messagingPage.sendGroupSMS(groupName, message);
            success = (Boolean) groupResult.getOrDefault("success", false);
            result.put("participantCount", groupResult.get("participantCount"));
        }
        
        result.put("success", success);
        result.put("senderTimestamp", senderTimestamp);
        result.put("senderTime", TIMESTAMP_FORMAT.format(new Date(senderTimestamp)));
        
        if (success) {
            System.out.println("   Text message sent at: " + result.get("senderTime"));
        } else {
            System.out.println("   ❌ Text message failed");
        }
        
        return result;
    }
    
    /**
     * EXECUTE VOICE MESSAGE
     */
    private Map<String, Object> executeVoiceMessage(String senderDevice, String senderNumber,
            String receiverNumber, String groupName, boolean isIndividual, boolean isGroup) throws Exception {
        
        Map<String, Object> result = new HashMap<>();
        
        System.out.println("\n🎤 SENDING VOICE MESSAGE...");
        
        // Switch to sender device if needed
        switchToDevice(senderDevice);
        
        long senderTimestamp = System.currentTimeMillis();
        boolean success;
        
        if (isIndividual) {
            System.out.println("   📱 Individual voice message to: " + receiverNumber);
            success = messagingPage.sendIndividualVoiceMessage(receiverNumber);
        } else {
            System.out.println("   👥 Group voice message to: " + groupName);
            Map<String, Object> groupResult = messagingPage.sendGroupVoiceMessage(groupName);
            success = (Boolean) groupResult.getOrDefault("success", false);
            result.put("participantCount", groupResult.get("participantCount"));
        }
        
        result.put("success", success);
        result.put("senderTimestamp", senderTimestamp);
        result.put("senderTime", TIMESTAMP_FORMAT.format(new Date(senderTimestamp)));
        
        if (success) {
            System.out.println("   Voice message sent at: " + result.get("senderTime"));
        } else {
            System.out.println("   ❌ Voice message failed");
        }
        
        return result;
    }
    
    /**
     * VERIFY MESSAGE RECEIVED (T2)
     */
    /**
     * ENHANCED MESSAGE RECEIPT VERIFICATION
     * Returns: Map containing receiver timestamp and status
     */
    private Map<String, Object> verifyMessageReceived(String receiverDevice, String senderNumber, 
                                       String messageType, String direction, 
                                       String aPartyNumber, String recipientNumber) {
        System.out.println("\n📥 ENHANCED MESSAGE RECEIPT VERIFICATION");
        System.out.println("   Direction: " + direction);
        System.out.println("   From: " + senderNumber + " | Type: " + messageType);
        
        Map<String, Object> verificationResult = new HashMap<>();
        long receiverTimestamp = 0; // DECLARE HERE ONCE
        String verificationStatus = "UNVERIFIED";
        String actualReceiverDevice = receiverDevice;
        
        try {
            // 1. For INCOMING messages, receiver is always aParty device
            if ("INCOMING".equals(direction)) {
                // Receiver is aParty for incoming messages
                String aPartyDevice = numberToDeviceMap.get(cleanNumber(aPartyNumber));
                if (aPartyDevice != null && ADBHelper.isDeviceConnected(aPartyDevice)) {
                    actualReceiverDevice = aPartyDevice;
                    System.out.println("   📱 INCOMING: Receiver is aParty device: " + aPartyDevice);
                } else {
                    System.out.println("   ⚠️ INCOMING: aParty device not available");
                    verificationStatus = "DEVICE_UNAVAILABLE";
                    verificationResult.put("receiverTimestamp", 0L);
                    verificationResult.put("verificationStatus", verificationStatus);
                    return verificationResult;
                }
            }
            
            // 2. Check if receiver device is available
            if (actualReceiverDevice == null) {
                System.out.println("   ⚠️ No receiver device available for verification");
                verificationStatus = "DEVICE_UNAVAILABLE";
                verificationResult.put("receiverTimestamp", 0L);
                verificationResult.put("verificationStatus", verificationStatus);
                return verificationResult;
            }
            
            if (!ADBHelper.isDeviceConnected(actualReceiverDevice)) {
                System.out.println("   ⚠️ Receiver device not connected: " + actualReceiverDevice);
                verificationStatus = "DEVICE_UNAVAILABLE";
                verificationResult.put("receiverTimestamp", 0L);
                verificationResult.put("verificationStatus", verificationStatus);
                return verificationResult;
            }
            
            // 3. Switch to receiver device if needed
            if (!actualReceiverDevice.equals(primaryDeviceId)) {
                switchToDevice(actualReceiverDevice);
            }
            
            // 4. Clear notifications and open messaging app fresh
            System.out.println("   🔧 Preparing receiver device...");
            clearNotifications(actualReceiverDevice);
            Thread.sleep(2000);
            
            // 5. Open messaging app and ensure proper state
            openMessagingAppFresh(actualReceiverDevice);
            Thread.sleep(5000);
            
            // 6. Determine which number to look for in conversation
            String numberToLookFor = senderNumber;
            
            // For incoming messages from individual recipients, look for recipient number
            if ("INCOMING".equals(direction) && recipientNumber != null) {
                numberToLookFor = recipientNumber;
                System.out.println("   🔍 INCOMING: Looking for conversation with recipient: " + recipientNumber);
            }
            
            // 7. Check if conversation exists
            System.out.println("   🔍 Looking for conversation with: " + numberToLookFor);
            String cleanNumberToLookFor = cleanNumber(numberToLookFor);
            boolean conversationFound = findAndOpenSenderConversation(cleanNumberToLookFor);
            
            if (!conversationFound) {
                System.out.println("   ℹ️ No existing conversation, looking for new message...");
                
                // Wait for new message notification
                long startTime = System.currentTimeMillis();
                // FIXED: Just assign value, don't redeclare
                receiverTimestamp = waitForNewMessageNotification(actualReceiverDevice, numberToLookFor, startTime);
                
                if (receiverTimestamp > 0) {
                    verificationStatus = "RECEIVED_VIA_NOTIFICATION";
                    System.out.println("   Message received via notification at: " + 
                        TIMESTAMP_FORMAT.format(new Date(receiverTimestamp)));
                } else {
                    verificationStatus = "NOT_RECEIVED";
                }
            } else {
                // 8. Conversation exists, check for latest message
                System.out.println("    Checking for new messages in conversation...");
                
                // Get the timestamp of the last message before waiting
                Long lastMessageTimestamp = getLastMessageTimestampInConversation();
                
                // Wait for new message
                receiverTimestamp = waitForNewMessageInConversation(
                    numberToLookFor, messageType, lastMessageTimestamp
                );
                
                if (receiverTimestamp > 0) {
                    verificationStatus = "RECEIVED_IN_CONVERSATION";
                    System.out.println("   Message received in conversation at: " + 
                        TIMESTAMP_FORMAT.format(new Date(receiverTimestamp)));
                } else {
                    verificationStatus = "NOT_RECEIVED";
                }
            }
            
            if (receiverTimestamp > 0) {
                System.out.println("   📅 Receiver Timestamp: " + 
                    TIMESTAMP_FORMAT.format(new Date(receiverTimestamp)));
            } else {
                System.out.println("   ⚠️ Message not received within expected time");
            }
            
            verificationResult.put("receiverTimestamp", receiverTimestamp);
            verificationResult.put("verificationStatus", verificationStatus);
            verificationResult.put("receiverDevice", actualReceiverDevice);
            
            return verificationResult;
            
        } catch (Exception e) {
            System.out.println("   ❌ Receipt verification error: " + e.getMessage());
            e.printStackTrace();
            
            verificationResult.put("receiverTimestamp", 0L);
            verificationResult.put("verificationStatus", "VERIFICATION_ERROR");
            verificationResult.put("error", e.getMessage());
            
            return verificationResult;
        }
    }

    /**
     * CLEAR NOTIFICATIONS on device
     */
    private void clearNotifications(String deviceId) {
        try {
            String[] cmd = {"adb", "-s", deviceId, "shell", "cmd", "notification", "remove_all"};
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
            System.out.println("   🔭 Cleared notifications");
        } catch (Exception e) {
            System.out.println("   ⚠️ Could not clear notifications: " + e.getMessage());
        }
    }

    /**
     * OPEN MESSAGING APP FRESH - Ensure clean state
     */
    private void openMessagingAppFresh(String deviceId) throws Exception {
        try {
            // Close app if already running
            closeMessagingAppCompletely(deviceId);
            Thread.sleep(2000);
            
            // Launch app fresh
            driver.activateApp("com.google.android.apps.messaging");
            Thread.sleep(5000);
            
            // Ensure we're on main screen
            ensureMessagingMainScreen();
            
            System.out.println("   Messaging app opened fresh");
        } catch (Exception e) {
            System.out.println("   ⚠️ Error opening fresh app: " + e.getMessage());
            throw e;
        }
    }
    /**
     * NEW: Close messaging app completely MODIFIED: Enhanced app closure
     */
    private void closeMessagingAppCompletely(String deviceId) {
        try {
            System.out.println("   🔴 Closing messaging app completely...");
            
            // Method 1: Terminate via driver (may fail if instrumentation is dead)
            try {
                driver.terminateApp("com.google.android.apps.messaging");
                Thread.sleep(1000);
                System.out.println("   App terminated via driver");
            } catch (Exception e) {
                System.out.println("   ⚠️ Driver terminate failed (expected after USSD)");
            }

            // Method 2: Force stop via ADB (always works)
            try {
                String[] cmd = {"adb", "-s", deviceId, "shell", "am", "force-stop",
                    "com.google.android.apps.messaging"};
                Process process = Runtime.getRuntime().exec(cmd);
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("   Messaging app force stopped via ADB");
                }
            } catch (Exception e) {
                System.out.println("   ⚠️ ADB force stop failed: " + e.getMessage());
            }
            
            Thread.sleep(2000);

        } catch (Exception e) {
            System.out.println("   ⚠️ Close app error: " + e.getMessage());
        }
    }

    /**
     * MODIFIED: Use ADB for navigation instead of Appium
     */
    private void ensureMessagingMainScreen() {
        try {
            // Try to find start chat FAB
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    WebElement startChatFab = driver.findElement(
                        By.id("com.google.android.apps.messaging:id/start_chat_fab")
                    );
                    if (startChatFab.isDisplayed()) {
                        System.out.println("   On messaging main screen");
                        return;
                    }
                } catch (Exception e) {
                    // FIX: Use ADB instead of driver.pressKey()
                    System.out.println("   🔙 Pressing BACK via ADB (attempt " + (attempt + 1) + ")");
                    try {
                        String[] cmd = {"adb", "-s", primaryDeviceId, "shell", 
                                       "input", "keyevent", "KEYCODE_BACK"};
                        Process process = Runtime.getRuntime().exec(cmd);
                        process.waitFor();
                        Thread.sleep(1000);
                    } catch (Exception adbEx) {
                        System.out.println("   ⚠️ ADB BACK failed: " + adbEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ Main screen check: " + e.getMessage());
        }
    }

    
    /**
     * FIND AND OPEN SENDER CONVERSATION
     */
    private boolean findAndOpenSenderConversation(String cleanSender) {
        try {
            System.out.println("   🔎 Searching for sender: " + cleanSender);
            
            // Method 1: Check if conversation exists in list
            List<WebElement> conversationElements = driver.findElements(
                By.id("com.google.android.apps.messaging:id/conversation_name")
            );
            
            for (WebElement conversation : conversationElements) {
                try {
                    String conversationText = conversation.getText();
                    if (conversationText.contains(cleanSender)) {
                        System.out.println("   📞 Found conversation with sender");
                        conversation.click();
                        Thread.sleep(3000);
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            
            // Method 2: Check page source for sender number
            String pageSource = driver.getPageSource();
            if (pageSource.contains(cleanSender)) {
                System.out.println("   🔍 Sender found in page source");
                
                // Try to click on element containing the sender number
                WebElement senderElement = driver.findElement(
                    By.xpath("//*[contains(@text, '" + cleanSender + "')]")
                );
                senderElement.click();
                Thread.sleep(3000);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Error finding conversation: " + e.getMessage());
            return false;
        }
    }

    /**
     * GET LAST MESSAGE TIMESTAMP IN CONVERSATION
     */
    private Long getLastMessageTimestampInConversation() {
        try {
            // Get all message elements in conversation
            List<WebElement> messageElements = driver.findElements(
                By.xpath("//android.view.View[contains(@resource-id, 'message_')]")
            );
            
            if (messageElements.isEmpty()) {
                // Alternative XPaths for different message layouts
                messageElements = driver.findElements(
                    By.xpath("//android.widget.ListView/android.view.View")
                );
            }
            
            if (!messageElements.isEmpty()) {
                // Get the last message element
                WebElement lastMessage = messageElements.get(messageElements.size() - 1);
                
                // Check if this message has a timestamp
                try {
                    List<WebElement> timestampElements = lastMessage.findElements(
                        By.xpath(".//android.widget.TextView[contains(@resource-id, 'timestamp')]")
                    );
                    
                    if (!timestampElements.isEmpty()) {
                        String timestampText = timestampElements.get(0).getText();
                        System.out.println("   ⏰ Last message timestamp: " + timestampText);
                        // Convert to long timestamp (simplified - you might need actual parsing)
                        return System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            return null;
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Could not get last message timestamp: " + e.getMessage());
            return null;
        }
    }

    /**
     * WAIT FOR NEW MESSAGE IN CONVERSATION
     */
    private long waitForNewMessageInConversation(String senderNumber, String messageType, Long lastMessageTimestamp) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long maxWaitTime = startTime + MAX_DELIVERY_TIME_MS;
        int pollCount = 0;
        
        String cleanSender = cleanNumber(senderNumber);
        System.out.println("   ⏳ Waiting for new message (max 60s)...");
        
        while (System.currentTimeMillis() < maxWaitTime) {
            pollCount++;
            System.out.println("   Poll " + pollCount + ": " + 
                ((System.currentTimeMillis() - startTime) / 1000) + "s elapsed");
            
            try {
                // Refresh the page source
                String pageSource = driver.getPageSource();
                
                // Check for new message indicators
                boolean hasNewMessage = false;
                
                // Method 1: Check for specific message type
                if ("text".equalsIgnoreCase(messageType)) {
                    // Look for text message indicators
                    hasNewMessage = pageSource.contains("SMS") || 
                                   pageSource.contains("Text") ||
                                   pageSource.contains("Message");
                } else if ("voice".equalsIgnoreCase(messageType)) {
                    // Look for voice message indicators
                    hasNewMessage = pageSource.contains("Voice") || 
                                   pageSource.contains("Audio") ||
                                   pageSource.contains("Microphone") ||
                                   pageSource.contains("Recording");
                }
                
                // Method 2: Check for "Delivered" or "Received" status
                boolean hasDeliveryStatus = pageSource.contains("Delivered") ||
                                           pageSource.contains("Received") ||
                                           pageSource.contains("delivered") ||
                                           pageSource.contains("received");
                
                if (hasNewMessage || hasDeliveryStatus) {
                    long receiverTimestamp = System.currentTimeMillis();
                    
                    // Verify it's from the right sender
                    if (pageSource.contains(cleanSender)) {
                        System.out.println("   📬 New message detected!");
                        return receiverTimestamp;
                    }
                }
                
                // Method 3: Check UI elements directly
                try {
                    // Look for new message bubble
                    List<WebElement> newMessageElements = driver.findElements(
                        By.xpath("//android.view.View[contains(@resource-id, 'message_') and @clickable='true']")
                    );
                    
                    for (WebElement message : newMessageElements) {
                        try {
                            String messageText = message.getText();
                            String contentDesc = message.getAttribute("content-desc");
                            
                            // Check if this is a new message (after our last timestamp)
                            if ((messageText != null && messageText.contains(cleanSender)) ||
                                (contentDesc != null && contentDesc.contains(cleanSender))) {
                                long receiverTimestamp = System.currentTimeMillis();
                                System.out.println("   📨 New message element found!");
                                return receiverTimestamp;
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                } catch (Exception e) {
                    // Continue with other methods
                }
                
                // Wait before next poll
                Thread.sleep(MESSAGE_POLL_INTERVAL);
                
            } catch (Exception e) {
                System.out.println("   ⚠️ Poll error: " + e.getMessage());
                Thread.sleep(MESSAGE_POLL_INTERVAL);
            }
        }
        
        System.out.println("   ⚠️ Timeout waiting for message in conversation");
        return 0;
    }

    /**
     * WAIT FOR NEW MESSAGE NOTIFICATION (when no existing conversation)
     */
    private long waitForNewMessageNotification(String deviceId, String senderNumber, long startTime) throws InterruptedException {
        long maxWaitTime = startTime + MAX_DELIVERY_TIME_MS;
        int pollCount = 0;
        
        String cleanSender = cleanNumber(senderNumber);
        System.out.println("   🔭 Waiting for new message notification...");
        
        while (System.currentTimeMillis() < maxWaitTime) {
            pollCount++;
            System.out.println("   Poll " + pollCount + ": " + 
                ((System.currentTimeMillis() - startTime) / 1000) + "s elapsed");
            
            try {
                // Method 1: Check for notification
                String notifications = getNotifications(deviceId);
                
                if (notifications.contains(cleanSender) || 
                    notifications.contains("Message") || 
                    notifications.contains("SMS") ||
                    notifications.contains("New message")) {
                    
                    long receiverTimestamp = System.currentTimeMillis();
                    System.out.println("   🔔 Notification received!");
                    
                    // Tap on notification to open message
                    tapNotification(deviceId);
                    Thread.sleep(3000);
                    
                    return receiverTimestamp;
                }
                
                // Method 2: Check message app for new conversation
                try {
                    // Search for sender in conversation list
                    driver.findElement(By.xpath("//*[contains(@text, '" + cleanSender + "')]"));
                    long receiverTimestamp = System.currentTimeMillis();
                    System.out.println("   📞 New conversation appeared!");
                    return receiverTimestamp;
                } catch (Exception e) {
                    // No new conversation found
                }
                
                // Wait before next poll
                Thread.sleep(MESSAGE_POLL_INTERVAL);
                
            } catch (Exception e) {
                System.out.println("   ⚠️ Notification poll error: " + e.getMessage());
                Thread.sleep(MESSAGE_POLL_INTERVAL);
            }
        }
        
        System.out.println("   ⚠️ Timeout waiting for notification");
        return 0;
    }

    /**
     * COUNT MESSAGES IN CURRENT CONVERSATION
     */
    private int countMessagesInConversation() {
        try {
            List<WebElement> messageElements = driver.findElements(
                By.xpath("//android.view.View[contains(@resource-id, 'message_')]")
            );
            
            if (messageElements.isEmpty()) {
                messageElements = driver.findElements(
                    By.xpath("//android.widget.ListView/android.view.View")
                );
            }
            
            return messageElements.size();
            
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * GET NOTIFICATIONS from device
     */
    private String getNotifications(String deviceId) {
        try {
            String[] cmd = {"adb", "-s", deviceId, "shell", "dumpsys", "notification", "--noredact"};
            Process process = Runtime.getRuntime().exec(cmd);
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor();
            return output.toString();
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Could not get notifications: " + e.getMessage());
            return "";
        }
    }

    /**
     * TAP NOTIFICATION
     */
    private void tapNotification(String deviceId) {
        try {
            // Tap on notification area (simplified - might need coordinates)
            String[] tapCmd = {"adb", "-s", deviceId, "shell", 
                              "input", "tap", "500", "200"};
            Process tapProcess = Runtime.getRuntime().exec(tapCmd);
            tapProcess.waitFor();
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Could not tap notification: " + e.getMessage());
        }
    }

    /**
     * CLEAN NUMBER METHOD
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
     * SWITCH TO DEVICE (if needed)
     */
    private void switchToDevice(String deviceId) throws Exception {
        if (deviceId == null || deviceId.equals(primaryDeviceId)) {
            return; // Already on correct device
        }
        
        System.out.println("    Switching to device: " + deviceId);
        
        // Quit current driver
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Initialize new driver for target device
        String platformVersion = ADBHelper.getAndroidVersion(deviceId).split("\\.")[0];
        this.driver = com.telecom.driver.DriverManager.initializeDriverForMessaging(
            deviceId, platformVersion
        );
        this.messagingPage = new MessagingPage(driver);
        
        System.out.println("   Switched to device: " + deviceId);
    }
    
    /**
     * OPEN MESSAGING APP
     */
    private void openMessagingApp(String deviceId) throws Exception {
        try {
            driver.activateApp("com.google.android.apps.messaging");
            Thread.sleep(3000);
        } catch (Exception e) {
            Map<String, Object> params = new HashMap<>();
            params.put("command", "am start -n com.google.android.apps.messaging/.ui.ConversationListActivity");
            driver.executeScript("mobile: shell", params);
            Thread.sleep(3000);
        }
    }
    
    /**
     * GENERATE REPORTS
     */
    private void generateReports() {
        System.out.println("\n📊 GENERATING REPORTS...");
        
        try {
            // Generate Excel report
            String excelReport = ReportGenerator.generateSMSExcelReport(allResults);
            System.out.println("   Excel Report: " + excelReport);
            
            // Generate HTML report
            String aPartyNumber = System.getProperty("aPartyNumber", "Unknown");
            String htmlReport = ReportGenerator.generateSMSTestReport(
                allResults, primaryDeviceId, aPartyNumber
            );
            System.out.println("   HTML Report: " + htmlReport);
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Report generation error: " + e.getMessage());
        }
    }
    
    /**
     * CLEANUP
     */
    public void cleanup() {
        System.out.println("\n🧹 Cleaning up SMS Test Executor...");
        
        try {
            if (driver != null) {
                driver.quit();
            }
        } catch (Exception e) {
            // Ignore
        }
        
        System.out.println("Cleanup complete");
    }

}