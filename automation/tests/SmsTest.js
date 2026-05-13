const { sendMultipleSmsUniversal } = require("../utils/sms");
const { createDriver } = require("../utils/appiumDriver");
const {killUiAutomatorProcesses}=require("../utils/killsession")

async function runSmsTest(deviceId, number, message, count) {
    const start = Date.now();

    // Appium not required, but we start it if you want to keep consistency
    await killUiAutomatorProcesses(deviceId);
    const driver = await createDriver(deviceId);

    await sendMultipleSmsUniversal(deviceId, number, message, count);

    const totalSec = (Date.now() - start) / 1000;

    await driver.deleteSession();

    return {
        target: number,
        message,
        count,
        totalSec,
        result: "PASS"
    };
}

module.exports = { runSmsTest };
