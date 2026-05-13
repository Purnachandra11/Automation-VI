const { createDriver } = require("../utils/appiumDriver");
const { sendSmsUI } = require("../utils/smsui");

async function runSmsUiTest(deviceId, number, message,count,res) {
    const driver = await createDriver(deviceId);

    const success = await sendSmsUI(driver, number, message,count,res);

    await driver.deleteSession();

    return { success };
}

module.exports = { runSmsUiTest };
