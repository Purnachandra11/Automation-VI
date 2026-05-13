package com.telecom.config;

import java.io.FileInputStream;
import java.util.Properties;

public class ConfigReader {
    private static Properties properties;
    
    static {
        try {
            String configPath = "src/test/resources/config.properties";
            properties = new Properties();
            properties.load(new FileInputStream(configPath));
        } catch (Exception e) {
            System.out.println("Error loading config: " + e.getMessage());
            properties = new Properties();
        }
    }
    
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public static String getAppPackage() {
        return getProperty("appPackage", "com.google.android.dialer");
    }
    
    public static String getAppActivity() {
        return getProperty("appActivity", "com.google.android.dialer.extensions.GoogleDialtactsActivity");
    }
    
    public static String getMessageAppPackage() {
        return getProperty("messageAppPackage", "com.google.android.apps.messaging");
    }
    
    public static String getMessageAppActivity() {
        return getProperty("messageAppActivity", "com.google.android.apps.messaging.ui.ConversationListActivity");
    }
    
    public static int getCallDuration() {
        return Integer.parseInt(getProperty("call.duration", "7"));
    }
    
    public static int getSMSWaitTime() {
        return Integer.parseInt(getProperty("sms.wait.time", "5"));
    }
    
    public static boolean isVPNEnabled() {
        return Boolean.parseBoolean(getProperty("vpn.enabled", "false"));
    }
    
    public static String getDialingNumber() {
        // First try to get from command line property, then fall back to config file
        String dialingNumber = System.getProperty("aPartyNumber");
        if (dialingNumber != null && !dialingNumber.trim().isEmpty()) {
            return dialingNumber.trim();
        }
        // Fall back to config file if system property is not set
        return getProperty("dialing.number", "");
    }
    
    public static String getEmailSubject() {
        String subject = getProperty("email.subject", "UAT Automation Test Report");
        return subject.replace("{dialing.number}", getDialingNumber());
    }
    
    public static String getSMSMessageTemplate() {
        return getProperty("smsMessageTemplate", "Hello {name}, this is an automated test message from Telecom Automation Framework.");
    }
    
    public static boolean isEmailEnabled() {
        return Boolean.parseBoolean(getProperty("email.enabled", "false"));
    }
    
    public static String getExcelFilePath() {
        return getProperty("excelFilePath", "src/test/resources/contacts.xlsx");
    }
    
    public static int getMaxCallAttempts() {
        return Integer.parseInt(getProperty("max.call.attempts", "3"));
    }
    
    public static boolean isVolteEnabled() {
        return Boolean.parseBoolean(getProperty("volte.enabled", "false"));
    }
    
    public static String getCPartyNumber() {
        return getProperty("cparty.number", "");
    }
}