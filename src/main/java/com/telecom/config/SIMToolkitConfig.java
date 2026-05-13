package com.telecom.config;

import java.util.Arrays;
import java.util.List;

public class SIMToolkitConfig {
    
    // App Configuration
    public static final String SIM_TOOLKIT_PACKAGE = "com.android.stk";
    public static final String SIM_TOOLKIT_ACTIVITY = ".StkLauncherActivity";
    
    // Timeouts (in seconds)
    public static final int DEFAULT_TIMEOUT = 30;
    public static final int SHORT_TIMEOUT = 5;
    public static final int LONG_TIMEOUT = 60;
    
    // Screenshot Configuration
    public static final String SCREENSHOT_DIR = "test-output/screenshots/";
    public static final String REPORT_DIR = "test-output/comprehensive-reports/";
    
    // Expected Texts for Validation
    public static final List<String> VI_BRANDING_TEXTS = Arrays.asList(
        "Vi", "Vodafone Idea", "Vodafone IN", "VI"
    );
    
    public static final List<String> SIM_TOOLKIT_TEXTS = Arrays.asList(
        "SIM Toolkit", "STK", "SIM Menu", "USSD"
    );
    
    // Menu Options
    public static final String FLASH_OPTION = "FLASH!";
    public static final String ROAMING_OPTION = "Roaming";
    public static final String VODAFONE_IN_OPTION = "Vodafone IN";
    public static final String INTERNATIONAL_OPTION = "International";
    
    // Button Texts
    public static final String OK_BUTTON = "OK";
    public static final String CANCEL_BUTTON = "Cancel";
    public static final String SELECT_BUTTON = "Select";
    
    // SIM Types
    public enum SIMType {
        SINGLE_SIM("Single SIM Device"),
        DUAL_SIM_MIXED("Dual SIM (Vi + Other)"),
        DUAL_SIM_VI("Dual SIM (Both Vi)");
        
        private final String description;
        
        SIMType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Android Key Codes
    public static final int BACK_BUTTON_KEYCODE = 4;
    public static final int HOME_BUTTON_KEYCODE = 3;
    public static final int MENU_BUTTON_KEYCODE = 82;
}
