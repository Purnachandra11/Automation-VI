package com.telecom.tests;

import java.util.Map;

import com.telecom.utils.USSDService;

public class Main {
    public static void main(String[] args) {
        String deviceId = "ZA222QJ657";
        
        System.out.println("🔍 Testing USSD on device: " + deviceId);
        System.out.println("======================================");
        
        
        // Full USSD Service
        System.out.println("Full USSD Service");
        System.out.println("----------------------------");
        Map<String, Object> detailedResult = USSDService.checkBalanceAndValidity(deviceId, "*199#");
        
        if ((Boolean) detailedResult.getOrDefault("success", false)) {
            System.out.println(" SUCCESS!");
            System.out.println("Phone: " + detailedResult.get("phoneNumber"));
            System.out.println("Balance: ₹" + detailedResult.get("balance"));
            System.out.println("Numeric Balance: " + detailedResult.get("balanceNumeric"));
            
            if (detailedResult.get("validity") != null) {
                System.out.println("Validity: " + detailedResult.get("validity"));
            }
        } else {
            System.out.println("❌ FAILED: " + detailedResult.get("error"));
        }
        
        System.out.println("\n🏁 All tests completed!");
    }
}