package com.telecom.utils;

/**
 * Automated detection of call scenarios
 */
public class CallScenarioDetector {
    
    public static class CallScenario {
        private String scenarioName;
        private String status;
        private String comment;
        private int scenarioNumber;
        
        public CallScenario(int number, String name, String status, String comment) {
            this.scenarioNumber = number;
            this.scenarioName = name;
            this.status = status;
            this.comment = comment;
        }
        
        // Getters
        public String getScenarioName() { return scenarioName; }
        public String getStatus() { return status; }
        public String getComment() { return comment; }
        public int getScenarioNumber() { return scenarioNumber; }
    }
    
    /**
     * Main method to detect call scenario from page source
     */
    public static CallScenario detectCallScenario(String pageSource, boolean callConnected, 
                                                   int actualDuration, int targetDuration) {
        
        // Scenario 1: Call Ringing
        if (containsIgnoreCase(pageSource, "ringing", "ring") && callConnected) {
            return new CallScenario(1, "Call Ringing", "CONNECTED", 
                String.format("Call: Connected (%ds) | Ringing detected", actualDuration));
        }
        
        // Scenario 2: Busy Tone
        if (containsIgnoreCase(pageSource, "busy")) {
            return new CallScenario(2, "Busy Tone", "CONNECTED (BUSY)", 
                String.format("Call: Busy tone detected (%ds)", actualDuration));
        }
        
        // Scenario 3: Call Connected (default successful scenario)
        if (callConnected && actualDuration >= targetDuration * 0.9) {
            return new CallScenario(3, "Call Connected", "CONNECTED", 
                String.format("Call: Connected (%ds)", actualDuration));
        }
        
        // Scenario 4: Call Dropped
        if (callConnected && actualDuration < targetDuration && 
            containsIgnoreCase(pageSource, "drop", "disconnect")) {
            return new CallScenario(4, "Call Dropped", "CONNECTED (DROPPED)", 
                String.format("Call dropped after %ds", actualDuration));
        }
        
        // Scenario 5: Invalid Number
        if (containsIgnoreCase(pageSource, "invalid number", "invalid_number", 
                              "format incorrect")) {
            return new CallScenario(5, "Invalid Number", "FAILED", 
                "Invalid number format detected");
        }
        
        // Scenario 6: Network Error
        if (containsIgnoreCase(pageSource, "network") && 
            containsIgnoreCase(pageSource, "error", "busy", "unavailable")) {
            return new CallScenario(6, "Network Error", "FAILED", 
                "No network signal detected");
        }
        
        // Scenario 7: Not Reachable
        if (containsIgnoreCase(pageSource, "not reachable", "out of coverage", 
                              "unreachable", "switched off")) {
            return new CallScenario(7, "Not Reachable", "FAILED", 
                "Call failed - B party not reachable");
        }
        
        // Scenario 8: A-Party Barred
        if (containsIgnoreCase(pageSource, "outgoing") && 
            containsIgnoreCase(pageSource, "barred", "restricted")) {
            return new CallScenario(8, "A-Party Barred", "FAILED", 
                "Call failed - Outgoing barred");
        }
        
        // Scenario 9: B-Party Barred
        if (containsIgnoreCase(pageSource, "incoming") && 
            containsIgnoreCase(pageSource, "barred", "restricted")) {
            return new CallScenario(9, "B-Party Barred", "REJECTED", 
                "Call rejected - Incoming barred");
        }
        
        // Scenario 10: Wrong SIM
        if (containsIgnoreCase(pageSource, "wrong sim", "wrong_sim", 
                              "sim error", "no sim")) {
            return new CallScenario(10, "Wrong SIM", "FAILED", 
                "Call failed due to wrong SIM slot");
        }
        
        // Scenario 11: Call Quality (connected with good quality)
        if (callConnected && actualDuration >= targetDuration * 0.8) {
            return new CallScenario(11, "Call Quality", "CONNECTED", 
                String.format("Call: Connected (%ds) | Audio quality OK", actualDuration));
        }
        
        // Scenario 12: CRBT Detected
        if (containsIgnoreCase(pageSource, "crbt", "caller tune", "ring back tone") && 
            callConnected) {
            return new CallScenario(12, "CRBT Detected", "CONNECTED", 
                String.format("Call: Connected with CRBT detected (%ds)", actualDuration));
        }
        
        // Default: Generic failure
        return new CallScenario(0, "Unknown", "FAILED", 
            "Call scenario could not be determined");
    } 
    
    /**
     * Helper method for case-insensitive multi-keyword search
     */
    private static boolean containsIgnoreCase(String source, String... keywords) {
        if (source == null) return false;
        String lowerSource = source.toLowerCase();
        for (String keyword : keywords) {
            if (lowerSource.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get scenario emoji for reporting
     */
    public static String getScenarioEmoji(int scenarioNumber) {
        switch (scenarioNumber) {
            case 1: return ""; // Ringing
            case 2: return "📞"; // Busy
            case 3: return ""; // Connected
            case 4: return "⚠️"; // Dropped
            case 5: return "❌"; // Invalid
            case 6: return "📡"; // Network Error
            case 7: return "📵"; // Not Reachable
            case 8: return "🚫"; // A-Party Barred
            case 9: return "🚫"; // B-Party Barred
            case 10: return "📱"; // Wrong SIM
            case 11: return "📊"; // Call Quality
            case 12: return "🎵"; // CRBT
            default: return "❓";
        }
    }
}
