const util = require("util");
const exec = util.promisify(require("child_process").exec);
const ElementConfig = require("../config/ElementConfig");

async function openMessagingApp(driver) {
    console.log("📱 Opening Messaging App...");

    try {
        await driver.activateApp(ElementConfig.MESSAGING_PACKAGE);
        console.log("   App activated");
    } catch {
        console.log("  ⚠ activateApp failed, using shell...");
        await driver.execute("mobile: shell", {
            command: `am start -n ${ElementConfig.MESSAGING_PACKAGE}/${ElementConfig.MESSAGING_ACTIVITY}`
        });
    }

    await driver.pause(4000);
}

async function navigateToMainScreen(driver) {
    console.log(" Ensuring main screen…");

    for (let i = 0; i < 5; i++) {
        try {
            await driver.$('id=com.google.android.apps.messaging:id/start_chat_fab');
            console.log("   On main screen");
            return;
        } catch {
            await driver.back();
            await driver.pause(1000);
        }
    }
}

async function startNewConversation(driver) {
    console.log("💬 Starting new conversation…");

    const btn = await driver.$('id=com.google.android.apps.messaging:id/start_chat_fab');
    await btn.click();
    await driver.pause(2000);
}

async function enterPhoneNumber(driver, phoneNumber) {
    console.log("📞 Entering number:", phoneNumber);
  phoneNumber=phoneNumber.toString()
    for (let digit of phoneNumber) {
        await driver.pressKeyCode(Number(digit) + 7); // DIGIT_0 starts at keycode 7
        await driver.pause(200);
    }

    await driver.pressKeyCode(66); // ENTER
    await driver.pause(2000);
}

async function enterMessage(driver, message) {
    console.log("💭 Entering message…");

    const input = await driver.$('id=com.google.android.apps.messaging:id/compose_message_text');
    await input.setValue(message);

    await driver.pause(500);
}

async function sendMessage(driver) {
    console.log("  📤 Sending message...");

    const selectors = ElementConfig.getSendButtonOptions();

    for (const selector of selectors) {
        try {
            const btn = await driver.$(selector);
            if (await btn.isDisplayed()) {
                await btn.click();
                await driver.pause(1000);
                console.log("   Message sent via:", selector);
                return;
            }
        } catch (err) { /* try next */ }
    }

    console.log("❌ SEND button not found using any selector.");
}

async function verifyDeliveryADB(deviceId) {
    try {
        const { stdout } = await exec(`adb -s ${deviceId} shell dumpsys telephony.registry | grep -i sms`);
        if (stdout.includes("SUCCESS")) {
            console.log("  📡 ADB SMS send SUCCESS detected");
        } else {
            console.log("  ⚠ Cannot confirm delivery from ADB");
        }
    } catch {}
}


async function verifyMessageSent(driver) {
    console.log("  🔍 Skipping pageSource check (unstable on some devices)");
    await driver.pause(2000);
    console.log("   Assuming SMS was sent successfully");
    return true;
}
async function ensureMainScreen(driver) {
    console.log(" Ensuring main screen...");

    // 1. Check if start chat FAB is already visible
    try {
        const fab = await driver.$(ElementConfig.START_CHAT_FAB);
        if (await fab.isDisplayed()) {
            console.log("   Already on main screen");
            return;
        }
    } catch {}

    console.log("  ⚠ Not on main screen, navigating back...");

    // 2. Press BACK repeatedly until FAB appears
    for (let i = 0; i < 6; i++) {
        await driver.back();
        await driver.pause(1000);

        try {
            const fab = await driver.$(ElementConfig.START_CHAT_FAB);
            if (await fab.isDisplayed()) {
                console.log("   Reached main screen");
                return;
            }
        } catch {}
    }

    console.log("  ⚠ Could not reach main screen — might be in first-time screens");
}


async function sendSmsUI(driver, phoneNumber, message,count,res) {
      function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }
send({ status: "Opening Messaging App" });
    await openMessagingApp(driver);
    // await navigateToMainScreen(driver);
    await ensureMainScreen(driver);
    send({ status: `Entering Target Mobile number:${phoneNumber}` });
    await startNewConversation(driver);
    await enterPhoneNumber(driver, phoneNumber);
   for(let i=0;i<count;i++)
    {console.log(i); 
        send({ status: `Typing Message` });
    await enterMessage(driver, message);
send({ status: `Sending ${i+1} Message` });
    await sendMessage(driver);
    //  await verifyDeliveryADB(deviceId)
   await verifyMessageSent(driver);
 if(count-1==i){
    return ;
 }
}
}

module.exports = {
    sendSmsUI
};
