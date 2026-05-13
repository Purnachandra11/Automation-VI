package com.telecom.config;

import org.openqa.selenium.By;

public class ElementConfig {
    
    // GOOGLE DIALER
    public static final String DIALER_PACKAGE = "com.google.android.dialer";
    public static final String DIALER_ACTIVITY = "com.google.android.dialer.extensions.GoogleDialtactsActivity";
    
    // Digit Buttons - Using By.id directly
    public static final By DIGIT_ZERO = By.id("com.google.android.dialer:id/zero");
    public static final By DIGIT_ONE = By.id("com.google.android.dialer:id/one");
    public static final By DIGIT_TWO = By.id("com.google.android.dialer:id/two");
    public static final By DIGIT_THREE = By.id("com.google.android.dialer:id/three");
    public static final By DIGIT_FOUR = By.id("com.google.android.dialer:id/four");
    public static final By DIGIT_FIVE = By.id("com.google.android.dialer:id/five");
    public static final By DIGIT_SIX = By.id("com.google.android.dialer:id/six");
    public static final By DIGIT_SEVEN = By.id("com.google.android.dialer:id/seven");
    public static final By DIGIT_EIGHT = By.id("com.google.android.dialer:id/eight");
    public static final By DIGIT_NINE = By.id("com.google.android.dialer:id/nine");
    
    public static final By CALL_BUTTON = By.id("com.google.android.dialer:id/dialpad_voice_call_button");
    public static final By END_CALL_BUTTON = By.id("com.google.android.dialer:id/incall_end_call");
    
    // CONFERENCE CALL ELEMENTS - UPDATED WITH ACTUAL XPATHS
    public static final By ADD_CALL_BUTTON = By.xpath("//android.view.View[@content-desc=\"Add call\"]");
    public static final By MORE_OPTIONS_BUTTON = By.xpath("//androidx.compose.ui.platform.ComposeView[@resource-id=\"com.google.android.dialer:id/incall_main_buttons_container\"]/android.view.View/android.view.View/android.view.View[1]/android.view.View[7]/android.widget.CheckBox");
    public static final By MERGE_CALLS_BUTTON = By.xpath("//android.view.View[@content-desc=\"Merge calls\"]");
    
    // GOOGLE MESSAGES
    public static final String MESSAGING_PACKAGE = "com.google.android.apps.messaging";
    public static final String MESSAGING_ACTIVITY = "com.google.android.apps.messaging.ui.ConversationListActivity";
    
    public static final By START_CHAT_FAB = By.id("com.google.android.apps.messaging:id/start_chat_fab");
    public static final By MESSAGE_INPUT = By.id("com.google.android.apps.messaging:id/compose_message_text");
    
    // SEND BUTTON - Using your proven working XPath as primary
    public static final By SEND_BUTTON_WORKING = By.xpath("//android.view.View[@resource-id=\"Compose:Draft:Send\"]/android.widget.Button");
    public static final By SEND_BUTTON_WORKING_01 = By.xpath("//android.widget.ImageView[@content-desc=\"Send SMS\"]");
  
    
    // Fallback send button locators (only if primary fails)
    public static final By SEND_BUTTON_CONTENT_DESC = By.xpath("//android.widget.ImageButton[@content-desc='Send SMS']");
    public static final By SEND_BUTTON_ID = By.id("com.google.android.apps.messaging:id/send_message_button_icon");
    public static final By SEND_BUTTON_GENERIC = By.xpath("//*[contains(@text, 'Send') or contains(@content-desc, 'Send')]");
    
    // ANDROID SETTINGS
    public static final String SETTINGS_PACKAGE = "com.android.settings";
    
    // Network & Internet Settings
    public static final By NETWORK_SETTINGS_TEXT = By.xpath("//android.widget.TextView[@text='Network & internet']");
    public static final By NETWORK_SETTINGS_CONTAINS_TEXT = By.xpath("//*[contains(@text, 'Network') or contains(@text, 'network')]");
    public static final By NETWORK_SETTINGS_ID = By.id("com.android.settings:id/title");
    
    // VPN Settings
    public static final By VPN_SETTINGS_TEXT = By.xpath("//android.widget.TextView[@text='VPN']");
    public static final By VPN_SETTINGS_CONTAINS_TEXT = By.xpath("//*[contains(@text, 'VPN') or contains(@text, 'vpn')]");
    public static final By VPN_SETTINGS_ID = By.id("com.android.settings:id/title");
    
    // SIM Settings
    public static final By SIM_SETTINGS_TEXT = By.xpath("//android.widget.TextView[@text='SIM cards']");
    public static final By SIM_SETTINGS_CONTAINS_TEXT = By.xpath("//*[contains(@text, 'SIM') or contains(@text, 'sim')]");
    
    // Mobile Network Settings
    public static final By MOBILE_NETWORK_TEXT = By.xpath("//android.widget.TextView[@text='Mobile network']");
    public static final By MOBILE_NETWORK_CONTAINS_TEXT = By.xpath("//*[contains(@text, 'Mobile') or contains(@text, 'mobile')]");
    
    // Helper method to get digit button By locator
    public static By getDigitButton(char digit) {
        switch (digit) {
            case '0': return DIGIT_ZERO;
            case '1': return DIGIT_ONE;
            case '2': return DIGIT_TWO;
            case '3': return DIGIT_THREE;
            case '4': return DIGIT_FOUR;
            case '5': return DIGIT_FIVE;
            case '6': return DIGIT_SIX;
            case '7': return DIGIT_SEVEN;
            case '8': return DIGIT_EIGHT;
            case '9': return DIGIT_NINE;
            default: throw new IllegalArgumentException("Invalid digit: " + digit);
        }
    }
    
    // Helper method to get all conference button options with your actual XPaths first
    public static By[] getAddCallButtonOptions() {
        return new By[] {
            ADD_CALL_BUTTON,                    // First priority - your actual XPath
            By.xpath("//*[contains(@content-desc, 'Add call')]"), // Second priority
            By.xpath("//*[contains(@text, 'Add call')]"),         // Third priority
            By.id("com.google.android.dialer:id/add_call_button") // Fourth priority
        };
    }
    
    public static By[] getMoreOptionsButtonOptions() {
        return new By[] {
            MORE_OPTIONS_BUTTON,                // First priority - your actual XPath
            By.xpath("//android.widget.ImageButton[@content-desc='More options']"), // Second priority
            By.xpath("//*[contains(@content-desc, 'More')]")      // Third priority
        };
    }
    
    public static By[] getMergeCallsButtonOptions() {
        return new By[] {
            MERGE_CALLS_BUTTON,                 // First priority - your actual XPath
            By.xpath("//android.widget.TextView[@text='Merge calls']"), // Second priority
            By.xpath("//*[contains(@text, 'Merge')]"),             // Third priority
            By.xpath("//*[contains(@content-desc, 'Merge')]")      // Fourth priority
        };
    }
    
    // Helper method to get end call button options
    public static By[] getEndCallButtonOptions() {
        return new By[] {
            END_CALL_BUTTON,                    // First priority - ID
            By.xpath("//android.widget.Button[contains(@text, 'End')]"),
            By.xpath("//*[contains(@content-desc, 'End call')]"),
            By.xpath("//*[contains(@content-desc, 'End')]"),
            By.xpath("//android.widget.ImageButton[contains(@resource-id, 'end_call')]")
        };
    }
    
    // Helper method to get all send button options with your working method first
    public static By[] getSendButtonOptions() {
        return new By[] {
            SEND_BUTTON_WORKING,       // First priority - your proven working XPath
            SEND_BUTTON_WORKING_01,
            SEND_BUTTON_CONTENT_DESC,  // Second priority - content description
            SEND_BUTTON_ID,            // Third priority - ID
            SEND_BUTTON_GENERIC        // Fourth priority - generic
        };
    }
    
    // Helper method for network settings with priority
    public static By[] getNetworkSettingsOptions() {
        return new By[] {
            NETWORK_SETTINGS_TEXT,
            NETWORK_SETTINGS_ID,
            NETWORK_SETTINGS_CONTAINS_TEXT
        };
    }
    
    // Helper method for VPN settings with priority
    public static By[] getVpnSettingsOptions() {
        return new By[] {
            VPN_SETTINGS_TEXT,
            VPN_SETTINGS_ID,
            VPN_SETTINGS_CONTAINS_TEXT
        };
    }
    
    // Helper method for SIM settings
    public static By[] getSimSettingsOptions() {
        return new By[] {
            SIM_SETTINGS_TEXT,
            SIM_SETTINGS_CONTAINS_TEXT
        };
    }
    
    // Helper method for mobile network settings
    public static By[] getMobileNetworkOptions() {
        return new By[] {
            MOBILE_NETWORK_TEXT,
            MOBILE_NETWORK_CONTAINS_TEXT
        };
    }
}