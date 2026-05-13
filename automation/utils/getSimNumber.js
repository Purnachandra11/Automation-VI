const { createDriver } = require("./appiumDriver");
const { exec } = require("child_process");
async function getSimNumberViaUSSD(deviceId, res) {
    if(res){function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }}
    console.log("Dialing USSD...");

    const driver = await createDriver(deviceId);

    await exec(`adb -s ${deviceId} shell am start -a android.intent.action.CALL -d tel:*199%23`);
   if(res) send({ status: "Dialing Ussd" });
    console.log("Waiting for USSD popup...");

    const timeout = Date.now() + 15000;
    let popupText = "";

    const ussdXPaths = [
        '//*[@resource-id="com.android.phone:id/message"]',   // Samsung
        '//*[@resource-id="android:id/message"]',             // Motorola / Pixel
        '//android.widget.TextView[contains(@text,"MSISDN")]',
        '//android.widget.TextView[contains(@text,"Balance")]',
        '//android.widget.TextView'                           // fallback
    ];

    while (Date.now() < timeout) {
        for (let xp of ussdXPaths) {
            try {
                const el = await driver.$(xp);
                if (await el.isDisplayed()) {
                    popupText = await el.getText();
                    if (popupText.toLowerCase().includes('running') || popupText.toLowerCase().includes('loading')) {
                        popupText = "";
                    } else {

                        break;
                    }
                }
            } catch (_) { }
        }

        if (popupText) break;
        await driver.pause(400);
    }

    console.log("POPUPOP TEXT >>>>>>>>>================================================================================================================================>>>>>>>>>>>>>>>>>>>");
    console.log(popupText);

    // Extract fields safely
    let sim = popupText
        .split("\n")
        .find(ele => ele.toLowerCase().includes("msisdn"))
        ?.split(":")[1]
        ?.trim() || null;

    let balance = popupText
        .split("\n")
        .find(ele => ele.toLowerCase().includes("balance"))
        ?.split(":")[1]
        ?.trim() || null;

    console.log("SIM:", sim);
    console.log("BALANCE:", balance);
    if(res)send({ status: `Testing Sim BALANCE:${balance}` });

    await closeUssdWithButton(driver);
    await clearResidualUssdPopups(driver);

    return { sim, balance };
}


// ---------------------------------------------------------------
// CLOSE POP-UP
// ---------------------------------------------------------------
async function closeUssdWithButton(driver) {
    const xpaths = [
        '//android.widget.Button[@text="OK"]',
        '//android.widget.Button[@text="Dismiss"]',
        '//android.widget.Button[@text="CANCEL"]',
        '//android.widget.Button[@resource-id="android:id/button1"]',
        '//android.widget.Button[@resource-id="android:id/button2"]',
        '//android.widget.Button' // fallback
    ];

    for (let xpath of xpaths) {
        try {
            const btn = await driver.$(xpath);
            if (await btn.isDisplayed()) {
                await btn.click();
                return true;
            }
        } catch (_) { }
    }

    return false;
}


// ---------------------------------------------------------------
// CLEAR ANY REMAINING POPUPS
// ---------------------------------------------------------------
async function clearResidualUssdPopups(driver) {
    const timeout = Date.now() + 15000;

    while (Date.now() < timeout) {
        try {
            const msg = await driver.$('//*[@resource-id="android:id/message"]');
            if (await msg.isDisplayed()) {
                console.log("Closing secondary USSD...");
                await closeUssdWithButton(driver);
                await driver.pause(500);
                continue;
            }
        } catch (_) { }

        await driver.pause(300);
    }
}

module.exports = { getSimNumberViaUSSD };
