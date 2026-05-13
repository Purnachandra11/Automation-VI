module.exports = {
    // Google Messages App
    MESSAGING_PACKAGE: "com.google.android.apps.messaging",
    MESSAGING_ACTIVITY: "com.google.android.apps.messaging.ui.ConversationListActivity",

    START_CHAT_FAB: 'id=com.google.android.apps.messaging:id/start_chat_fab',
    MESSAGE_INPUT: 'id=com.google.android.apps.messaging:id/compose_message_text',

    // PRIMARY SEND BUTTON — Your working Java XPath
    SEND_BUTTON_WORKING_XPATH:
        '//android.view.View[@resource-id="Compose:Draft:Send"]/android.widget.Button',

    // Fallbacks
    SEND_BUTTON_CONTENT_DESC:
        '//android.widget.ImageButton[@content-desc="Send SMS"]',

    SEND_BUTTON_ID:
        'id=com.google.android.apps.messaging:id/send_message_button_icon',

    SEND_BUTTON_GENERIC:
        '//*[contains(@text, "Send") or contains(@content-desc, "Send")]',

    // Return all send button options
    getSendButtonOptions() {
        return [
            this.SEND_BUTTON_WORKING_XPATH,
            this.SEND_BUTTON_CONTENT_DESC,
            this.SEND_BUTTON_ID,
            this.SEND_BUTTON_GENERIC
        ];
    }
};
