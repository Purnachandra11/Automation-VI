package com.telecom.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ImprovedADBAutoAnswer {
    private String deviceId;
    private String expectedCaller;
    private boolean running = false;
    private Thread monitorThread;
    private int callsAnswered = 0;
    private volatile boolean callDetected = false;
    
    public ImprovedADBAutoAnswer(String deviceId, String expectedCaller) {
        this.deviceId = deviceId;
        this.expectedCaller = expectedCaller;
    }
    
    public void start() {
        running = true;
        callsAnswered = 0;
        callDetected = false;
        System.out.println("🤖 Starting AGGRESSIVE ADB Auto-Answer for device: " + deviceId);
        System.out.println("📞 Expected caller: " + expectedCaller);
        System.out.println("⚡ Using MULTI-METHOD monitoring (50ms polling)");
        
        monitorThread = new Thread(() -> {
            System.out.println("👀 Monitor thread started with AGGRESSIVE polling...");
            while (running) {
                try {
                    monitorAndAnswerCallsAggressive();
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    System.out.println("🛑 Monitor thread interrupted");
                    break;
                } catch (Exception e) {
                }
            }
            System.out.println("👋 Monitor thread stopped");
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        System.out.println("✅ Auto-answer service is now AGGRESSIVELY monitoring");
    }
    
    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
            try {
                monitorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("🛑 Auto-Answer service stopped for device: " + deviceId);
        System.out.println("📊 Total calls answered: " + callsAnswered);
    }
    
    /**
     * ⚡ AGGRESSIVE: Use multiple detection methods simultaneously
     */
    private void monitorAndAnswerCallsAggressive() throws InterruptedException {
        if (callDetected) {
            return;
        }
        
        try {
            // METHOD 1: Check telephony state (most reliable)
            String callState = executeADBCommandQuick("dumpsys telephony.registry | grep mCallState");
            
            // METHOD 2: Check audio state (backup detection)
            String audioState = executeADBCommandQuick("dumpsys audio | grep 'mRingerMode\\|mMode'");
            
            // METHOD 3: Check notification (another backup)
            String notifications = executeADBCommandQuick("dumpsys notification | grep 'Notification(.*call'");
            
            // Detect ringing call using ANY method
            boolean isRinging = callState.contains("mCallState=2") || 
                               callState.contains("mCallState: 2") ||
                               audioState.contains("RINGER_MODE_NORMAL") ||
                               notifications.contains("call");
            
            if (isRinging && !callDetected) {
                callDetected = true;
                
                System.out.println("\n" + "=".repeat(70));
                System.out.println("📞 🔔 INCOMING CALL DETECTED ON " + deviceId);
                System.out.println("=".repeat(70));
                
                // Get incoming number
                String incomingNumber = getIncomingNumber();
                System.out.println("📱 Incoming from: " + incomingNumber);
                System.out.println("👤 Expected caller: " + expectedCaller);
                
                // ⚡ IMMEDIATE MULTI-METHOD ANSWER
                boolean answered = answerCallAggressively();
                
                if (answered) {
                    callsAnswered++;
                    System.out.println("✅ ✅ ✅ CALL ANSWERED SUCCESSFULLY!");
                    System.out.println("📊 Total calls answered: " + callsAnswered);
                    System.out.println("=".repeat(70) + "\n");
                    
                    // Wait to avoid re-triggering
                    Thread.sleep(15000);
                    callDetected = false;
                } else {
                    System.out.println("❌ All answer attempts failed");
                    System.out.println("=".repeat(70) + "\n");
                    Thread.sleep(3000);
                    callDetected = false;
                }
            }
            
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            // Continue monitoring
        }
    }
    
    /**
     * ⚡ AGGRESSIVE: Try ALL answer methods in parallel
     */
//    private boolean answerCallAggressively() {
//        System.out.println("⚡ EXECUTING AGGRESSIVE ANSWER STRATEGY...");
//        
//        // Try multiple methods rapidly in sequence
//        String[] answerMethods = {
//            "input keyevent KEYCODE_CALL",      // Method 1: Standard call key
//        };
//        
//        for (int attempt = 1; attempt <= 1; attempt++) {
//            System.out.println("\n🔄 ANSWER ROUND " + attempt + "/1:");
//            
//            for (int i = 0; i < answerMethods.length; i++) {
//                try {
//                    System.out.println("   " + (i + 1) + ". Trying: " + answerMethods[i]);
//                    
//                    // Execute answer command
//                    ProcessBuilder pb = new ProcessBuilder("adb", "-s", deviceId, "shell", "input keyevent KEYCODE_CALL");
//                    pb.redirectErrorStream(true);
//                    Process process = pb.start();
//                    
//                    boolean finished = process.waitFor(2, TimeUnit.SECONDS);
//                    if (finished && process.exitValue() == 0) {
//                        System.out.println("   ✅ Command executed successfully");
//                        
//                        // Quick check
//                        Thread.sleep(500);
//                        if (verifyCallAnswered()) {
//                            System.out.println("   ✅✅ CALL ANSWERED WITH METHOD " + (i + 1) + "!");
//                            return true;
//                        }
//                    }
//                    
//                    // Small delay between methods
//                    Thread.sleep(300);
//                    
//                } catch (Exception e) {
//                    System.out.println("   ⚠️ Method " + (i + 1) + " failed: " + e.getMessage());
//                }
//            }
//            
//            // Check after each round
//            try {
//                Thread.sleep(1000);
//                if (verifyCallAnswered()) {
//                    System.out.println("✅ Call answered after round " + attempt);
//                    return true;
//                }
//            } catch (Exception e) {
//                // Continue
//            }
//        }
//        
//        return false;
//    }
    private boolean answerCallAggressively() {
        System.out.println("⚡ EXECUTING AGGRESSIVE ANSWER STRATEGY...");

        try {
            System.out.println("   📞 Trying: KEYCODE_CALL");

            ProcessBuilder pb = new ProcessBuilder(
                    "adb", "-s", deviceId, "shell", "input keyevent KEYCODE_CALL"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
                System.out.println("   ✅ Command executed");

                Thread.sleep(500);
                if (verifyCallAnswered()) {
                    System.out.println("   ✅✅ CALL ANSWERED!");
                    return true;
                }
            }

        } catch (Exception e) {
            System.out.println("   ⚠️ Failed: " + e.getMessage());
        }

        return false;
    }

    /**
     * Get incoming phone number
     */
    private String getIncomingNumber() {
        try {
            String result = executeADBCommandQuick("dumpsys telephony.registry | grep mCallIncomingNumber");
            if (result.contains("=")) {
                String number = result.split("=")[1].trim();
                number = number.replaceAll("[^0-9+]", "");
                return number.isEmpty() ? "Unknown" : number;
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }
    /**
     * Verify call answered - check multiple indicators
     */
    private boolean verifyCallAnswered() {
        try {
            // Check 1: Telephony state - OFFHOOK means call is active
            String callState = executeADBCommandQuick("dumpsys telephony.registry | grep mCallState");
            
            // More flexible pattern matching
            boolean isOffhook = callState.contains("mCallState=1") || 
                               callState.contains("mCallState: 1") ||
                               callState.contains("OFFHOOK") ||
                               callState.matches(".*mCallState.*1.*");
            
            if (isOffhook) {
                System.out.println("   ✅ Verified via call state (OFFHOOK)");
                return true;
            }
            
            // Check 2: Audio mode - IN_CALL or IN_COMMUNICATION
            String audioState = executeADBCommandQuick("dumpsys audio | grep -E 'mMode|audio_mode'");
            boolean isInCallAudio = audioState.contains("mMode=2") || 
                                   audioState.contains("mMode=3") || 
                                   audioState.contains("mMode: 2") || 
                                   audioState.contains("mMode: 3") ||
                                   audioState.contains("AUDIO_MODE_IN_CALL") ||
                                   audioState.contains("AUDIO_MODE_IN_COMMUNICATION");
            
            if (isInCallAudio) {
                System.out.println("   ✅ Verified via audio mode (IN_CALL)");
                return true;
            }
            
            // Check 3: Call status via telecom service (most reliable)
            String telecomState = executeADBCommandQuick("dumpsys telecom | grep -E 'Call.*state'");
            if (telecomState.contains("ACTIVE") || telecomState.contains("CONNECTED")) {
                System.out.println("   ✅ Verified via telecom (ACTIVE/CONNECTED)");
                return true;
            }
            
            // Check 4: Phone app state
            String phoneState = executeADBCommandQuick("dumpsys phone | grep -i state");
            if (phoneState.contains("OFFHOOK")) {
                System.out.println("   ✅ Verified via phone state (OFFHOOK)");
                return true;
            }
            
            System.out.println("   ⚠️ Call state not confirmed");
            System.out.println("   Call state: " + callState);
            System.out.println("   Audio state: " + audioState.substring(0, Math.min(100, audioState.length())));
            return false;
            
        } catch (Exception e) {
            System.out.println("   ⚠️ Verification error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * ⚡ QUICK: Fast ADB command execution
     */
    private String executeADBCommandQuick(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("adb", "-s", deviceId, "shell", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            process.waitFor(1, TimeUnit.SECONDS);
            reader.close();
            
            return output.toString().trim();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    public int getCallsAnswered() {
        return callsAnswered;
    }
    
    public boolean isRunning() {
        return running && monitorThread != null && monitorThread.isAlive();
    }
}