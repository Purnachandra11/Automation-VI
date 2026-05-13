package com.telecom.utils;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ProgressReporter - Sends real-time progress updates to Node.js server
 * This class integrates with your existing test classes to send WebSocket updates
 */
public class ProgressReporter {
    private static final String PROGRESS_ENDPOINT = "http://localhost:5174/api/progress/update";
    private static final Gson gson = new Gson();
    
    // Track completion status per device and test type
    private static final Map<String, AtomicInteger> completedTests = new ConcurrentHashMap<>();
    private static final Map<String, Integer> totalTests = new ConcurrentHashMap<>();
    
    /**
     * Report Data Usage Test Progress
     */
    public static void reportDataProgress(String deviceId, String testType, 
                                         int elapsedSec, int totalSec, 
                                         double downloadedMB, String interfaceName) {
        try {
            double progress = (elapsedSec * 100.0) / totalSec;
            
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("deviceId", deviceId);
            progressData.put("testType", "data");
            progressData.put("timestamp", System.currentTimeMillis());
            progressData.put("completed", false);
            
            Map<String, Object> details = new HashMap<>();
            details.put("elapsedSec", elapsedSec);
            details.put("totalSec", totalSec);
            details.put("downloadedMB", downloadedMB);
            details.put("progress", progress);
            details.put("interface", interfaceName);
            details.put("progressBar", createProgressBar(progress, 20));
            
            // Add overall test suite progress if total tests is known
            String testSuiteKey = deviceId + "-suite";
            if (totalTests.containsKey(testSuiteKey)) {
                int completed = completedTests.containsKey(testSuiteKey) ? completedTests.get(testSuiteKey).get() : 0;
                int total = totalTests.get(testSuiteKey);
                double suiteProgress = (completed * 100.0) / total;
                details.put("suiteProgress", suiteProgress);
                details.put("completedTests", completed);
                details.put("totalTests", total);
            }
            
            progressData.put("progress", details);
            
            sendProgress(progressData);
            
            // Also print to console for backend visibility
            System.out.println(String.format(
                "[INFO] WS_PROGRESS:%s",
                gson.toJson(progressData)
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to report data progress: " + e.getMessage());
        }
    }
    
    /**
     * Report Calling Test Progress
     */
    public static void reportCallingProgress(String deviceId, String phoneNumber, 
                                            String action, String status, 
                                            int duration, double percentage) {
        try {
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("deviceId", deviceId);
            progressData.put("testType", "calling");
            progressData.put("timestamp", System.currentTimeMillis());
            progressData.put("completed", false);
            
            Map<String, Object> details = new HashMap<>();
            details.put("action", action);
            details.put("status", status);
            details.put("number", phoneNumber);
            details.put("duration", duration);
            details.put("percentage", percentage);
            
            // Add overall test suite progress if total tests is known
            String testSuiteKey = deviceId + "-suite";
            if (totalTests.containsKey(testSuiteKey)) {
                int completed = completedTests.containsKey(testSuiteKey) ? completedTests.get(testSuiteKey).get() : 0;
                int total = totalTests.get(testSuiteKey);
                double suiteProgress = (completed * 100.0) / total;
                details.put("suiteProgress", suiteProgress);
                details.put("completedTests", completed);
                details.put("totalTests", total);
            }
            
            progressData.put("progress", details);
            
            sendProgress(progressData);
            
            System.out.println(String.format(
                "[INFO] WS_PROGRESS:%s",
                gson.toJson(progressData)
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to report calling progress: " + e.getMessage());
        }
    }
    
    /**
     * Report SMS Test Progress
     */
    public static void reportSMSProgress(String deviceId, String phoneNumber, 
                                        String action, String status, 
                                        double percentage) {
        try {
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("deviceId", deviceId);
            progressData.put("testType", "sms");
            progressData.put("timestamp", System.currentTimeMillis());
            progressData.put("completed", false);
            
            Map<String, Object> details = new HashMap<>();
            details.put("action", action);
            details.put("status", status);
            details.put("number", phoneNumber);
            details.put("percentage", percentage);
            
            // Add overall test suite progress if total tests is known
            String testSuiteKey = deviceId + "-suite";
            if (totalTests.containsKey(testSuiteKey)) {
                int completed = completedTests.containsKey(testSuiteKey) ? completedTests.get(testSuiteKey).get() : 0;
                int total = totalTests.get(testSuiteKey);
                double suiteProgress = (completed * 100.0) / total;
                details.put("suiteProgress", suiteProgress);
                details.put("completedTests", completed);
                details.put("totalTests", total);
            }
            
            progressData.put("progress", details);
            
            sendProgress(progressData);
            
            System.out.println(String.format(
                "[INFO] WS_PROGRESS:%s",
                gson.toJson(progressData)
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to report SMS progress: " + e.getMessage());
        }
    }
    
    /**
     * Initialize test suite - call this before starting test execution
     */
    public static void initializeTestSuite(String deviceId, int totalTestCount) {
        String key = deviceId + "-suite";
        totalTests.put(key, totalTestCount);
        completedTests.put(key, new AtomicInteger(0));
        System.out.println(String.format(
            "[INFO] Initialized test suite for device %s with %d total tests",
            deviceId, totalTestCount
        ));
    }
    
    /**
     * Report Test Completion
     */
    public static void reportTestComplete(String deviceId, String testType, 
                                         boolean success, String message) {
        try {
            // Increment completed tests counter
            String suiteKey = deviceId + "-suite";
            if (completedTests.containsKey(suiteKey)) {
                int completed = completedTests.get(suiteKey).incrementAndGet();
                int total = totalTests.get(suiteKey);
                double suiteProgress = (completed * 100.0) / total;
                
                System.out.println(String.format(
                    "[INFO] Test completed: %s/%s (%.1f%%) for device %s",
                    completed, total, suiteProgress, deviceId
                ));
            }
            
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("deviceId", deviceId);
            progressData.put("testType", testType);
            progressData.put("timestamp", System.currentTimeMillis());
            progressData.put("completed", true);
            progressData.put("success", success);
            progressData.put("message", message);
            
            // Add suite progress information
            if (completedTests.containsKey(suiteKey)) {
                int completed = completedTests.get(suiteKey).get();
                int total = totalTests.get(suiteKey);
                double suiteProgress = (completed * 100.0) / total;
                
                Map<String, Object> details = new HashMap<>();
                details.put("suiteProgress", suiteProgress);
                details.put("completedTests", completed);
                details.put("totalTests", total);
                details.put("suiteProgressBar", createProgressBar(suiteProgress, 20));
                
                progressData.put("suite", details);
            }
            
            sendProgress(progressData);
            
            System.out.println(String.format(
                "[INFO] WS_PROGRESS:%s",
                gson.toJson(progressData)
            ));
            
        } catch (Exception e) {
            System.err.println("Failed to report completion: " + e.getMessage());
        }
    }
    
    /**
     * Reset test suite for a device
     */
    public static void resetTestSuite(String deviceId) {
        String key = deviceId + "-suite";
        completedTests.remove(key);
        totalTests.remove(key);
        System.out.println(String.format(
            "[INFO] Reset test suite for device %s",
            deviceId
        ));
    }
    
    /**
     * Send progress update to Node.js server
     */
    private static void sendProgress(Map<String, Object> progressData) {
        try {
            String jsonPayload = gson.toJson(progressData);
            
            Request.post(PROGRESS_ENDPOINT)
                .bodyString(jsonPayload, ContentType.APPLICATION_JSON)
                .execute()
                .returnContent()
                .asString();
                
        } catch (Exception e) {
            // Silently fail - don't disrupt test execution
            System.err.println("Progress update failed: " + e.getMessage());
        }
    }
    
    /**
     * Create visual progress bar
     */
    private static String createProgressBar(double progress, int length) {
        int filled = Math.min((int)((progress / 100.0) * length), length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }
}