package com.telecom.config;

/**
 *  USSD CONFIGURATION
 */
public class USSDConfig {
    
    // USSD Codes
    public static final String BALANCE_CHECK_CODE = "*199#";
//    public static final String DATA_BALANCE_CODE = "*121#";
//    public static final String VALIDITY_CODE = "*199*2#";
//    
    // Timeouts (in milliseconds)
    public static final long USSD_DIAL_TIMEOUT = 5000;
    public static final long USSD_RESPONSE_TIMEOUT = 15000;
    public static final long USSD_COMPLETION_TIMEOUT = 20000;
    public static final long BALANCE_UPDATE_WAIT = 5000;
    
    // Completion Indicators
    public static final String[] COMPLETION_INDICATORS = {
        "USSD Check Completed",
        "Check Completed",
        "Balance:",
        "MSISDN:",
        "Validity:",
        "Main Balance"
    };
    
    // Error Indicators
    public static final String[] ERROR_INDICATORS = {
        "Error",
        "Failed",
        "Not supported",
        "Service not available",
        "Unable to process",
        "Invalid"
    };
    
    // App packages to force stop
    public static final String[] USSD_APP_PACKAGES = {
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.incallui"
    };
}