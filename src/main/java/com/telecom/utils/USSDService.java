package com.telecom.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class USSDService {
    
    private static final int CONNECT_TIMEOUT = 150000; // 150 seconds
    private static final int READ_TIMEOUT = 150000;    // 150 seconds
    private static final String API_URL = "http://localhost:5175/getBalance";
    
    /**
     *  CHECK BALANCE AND VALIDITY - Enhanced Version
     */  
    public static Map<String, Object> checkBalanceAndValidity(String deviceId, String ussdCode) {
        Map<String, Object> result = new HashMap<>();
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        
        try {
            System.out.println("   🌐 Calling USSD API for device: " + deviceId);
            
            // 1. Create connection
            @SuppressWarnings("deprecation")
            URL url = new URL(API_URL);
            conn = (HttpURLConnection) url.openConnection();
            
            // 2. Configure request
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            
            // 3. Send request body
            String requestBody = "{\"deviceId\":\"" + deviceId + "\"}";
            conn.getOutputStream().write(requestBody.getBytes("UTF-8"));
            conn.getOutputStream().flush();
            
            // 4. Read response
            int responseCode = conn.getResponseCode();
            System.out.println("   📡 API Response Code: " + responseCode);
            
            if (responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String responseStr = response.toString();
            System.out.println("   📄 API Response: " + responseStr);
            
            // 5. Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            result = mapper.readValue(responseStr, new TypeReference<Map<String, Object>>() {});
            
            // 6. Validate and clean response
            if (result.containsKey("status")) {
                Integer status = (Integer) result.get("status");
                
                if (status == 1) {
                    // Success
                    result.put("success", true);
                    
                    // Clean balance (remove "Rs " prefix if present)
                    Object balanceObj = result.get("balance");
                    if (balanceObj != null) {
                        String balanceStr = balanceObj.toString();
                        if (balanceStr.startsWith("Rs ")) {
                            balanceStr = balanceStr.substring(3).trim();
                            result.put("balance", balanceStr);
                        }
                    }
                    
                    // Ensure phoneNumber is set (use sim field as fallback)
                    if (!result.containsKey("phoneNumber") && result.containsKey("sim")) {
                        result.put("phoneNumber", result.get("sim"));
                    }
                    
                    // Validity may be null - that's OK
                    if (!result.containsKey("validity")) {
                        result.put("validity", null);
                    }
                    
                    System.out.println("    USSD API Success");
                    
                } else {
                    // API returned status != 1
                    result.put("success", false);
                    String errorMsg = (String) result.getOrDefault("error", "API returned status: " + status);
                    result.put("error", errorMsg);
                    System.out.println("   ❌ USSD API Failed: " + errorMsg);
                }
            } else {
                // No status field in response
                result.put("success", false);
                result.put("error", "Invalid API response: missing status field");
                System.out.println("   ❌ Invalid API response");
            }
            
            return result;
            
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("   ❌ USSD API Timeout: " + e.getMessage());
            result.put("success", false);
            result.put("error", "API timeout: " + e.getMessage());
            return result;
            
        } catch (java.net.ConnectException e) {
            System.out.println("   ❌ USSD API Connection Failed: " + e.getMessage());
            result.put("success", false);
            result.put("error", "Cannot connect to API: " + e.getMessage());
            return result;
            
        } catch (Exception e) {
            System.out.println("   ❌ USSD API Error: " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("error", "API error: " + e.getMessage());
            return result;
            
        } finally {
            // 7. Cleanup resources
            try {
                if (reader != null) {
                    reader.close();
                }
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     *  CHECK BALANCE ONLY - Quick check without full details
     */
    public static Double getQuickBalance(String deviceId) {
        try {
            Map<String, Object> result = checkBalanceAndValidity(deviceId, "*199#");
            
            if ((Boolean) result.getOrDefault("success", false)) {
                Object balanceObj = result.get("balance");
                if (balanceObj != null) {
                    String balanceStr = balanceObj.toString()
                        .replace("Rs", "")
                        .replace("₹", "")
                        .replace("INR", "")
                        .trim();
                    return Double.parseDouble(balanceStr);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Quick balance check failed: " + e.getMessage());
        }
        return null;
    }
    
    /**
     *  VERIFY PHONE NUMBER MATCH
     */
    public static boolean verifyPhoneNumber(String deviceId, String expectedNumber) {
        try {
            Map<String, Object> ussdResult = checkBalanceAndValidity(deviceId, "*199#");
            
            if ((Boolean) ussdResult.getOrDefault("success", false)) {
                String actualNumber = (String) ussdResult.get("phoneNumber");
                
                if (actualNumber != null) {
                    String normalizedExpected = normalizePhoneNumber(expectedNumber);
                    String normalizedActual = normalizePhoneNumber(actualNumber);
                    
                    boolean match = normalizedExpected.equals(normalizedActual);
                    
                    System.out.println("📱 Phone Number Verification:");
                    System.out.println("   Expected: " + expectedNumber + " → " + normalizedExpected);
                    System.out.println("   Actual: " + actualNumber + " → " + normalizedActual);
                    System.out.println("   Match: " + (match ? "" : "❌"));
                    
                    return match;
                }
            }
            
        } catch (Exception e) {
            System.out.println("❌ Phone number verification failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     *  NORMALIZE PHONE NUMBER - Handle different formats
     */
    private static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        
        // Remove all non-digit characters
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        
        // Handle different formats:
        // 91xxxxxxxxxx (12 digits) -> xxxxxxxxxx (10 digits)
        // 0xxxxxxxxxx (11 digits) -> xxxxxxxxxx (10 digits)
        if (digits.length() > 10) {
            if (digits.startsWith("91") && digits.length() == 12) {
                return digits.substring(2); // Remove country code
            } else if (digits.startsWith("0") && digits.length() == 11) {
                return digits.substring(1); // Remove leading 0
            }
        }
        
        // Return last 10 digits
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
    
    /**
     *  TEST CONNECTION - Verify API is reachable
     */
    public static boolean testAPIConnection() {
        try {
            System.out.println("🔍 Testing USSD API connection...");
            
            @SuppressWarnings("deprecation")
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            boolean reachable = (responseCode > 0);
            System.out.println(reachable ? " API is reachable" : "❌ API is not reachable");
            
            return reachable;
            
        } catch (Exception e) {
            System.out.println("❌ API connection test failed: " + e.getMessage());
            return false;
        }
    }
}