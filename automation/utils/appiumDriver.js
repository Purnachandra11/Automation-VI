const wdio = require("webdriverio");

// Assign a different system port for each device
function getSystemPort(deviceId) {
    const hash = deviceId.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0);
    return 8200 + (hash % 90); // generates port between 8200–8250
}

async function createDriver(deviceId) {
    const opts = {
        path: "/",
        port: 4723, // Appium server port
        capabilities: {
            platformName: "Android",
            "appium:automationName": "UiAutomator2",

            // REQUIRED for multi-device setup:
            "appium:udid": deviceId, 
            "appium:systemPort": getSystemPort(deviceId),

            "appium:deviceName": deviceId,
            "appium:noReset": true,
            "appium:newCommandTimeout": 300
        }
    };

    return await wdio.remote(opts);
}

module.exports = { createDriver };
