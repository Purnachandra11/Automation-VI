const { createDriver } = require("./appiumDriver");
const { exec } = require("child_process");
const { endCall,startCall,waitForCallConnected,waitForCallTimer,waitForTimerByText } = require("./call");


async function openNotifications(deviceId) {
    exec(`adb -s ${deviceId} shell cmd statusbar expand-notifications`);
}

async function closeNotifications(driver) {
    // Swipe UP from bottom-middle
    await driver.touchPerform([
        { action: "press", options: { x: 540, y: 1700 } },  // bottom center
        { action: "wait", options: { ms: 150 } },
        { action: "moveTo", options: { x: 540, y: 200 } },  // swipe upward
        { action: "release" }
    ]);

    console.log("📵 Notification panel closed (swipe)");
}

async function answerIncomingCall(driver) {
    console.log("🔍 Scanning for ANY readable 'Answer' button text ...");

    const answerKeywords = [
        "answer",
        "accept",
        "receive",
        "attend",
        "ans",          // sometimes truncated
        "pickup",
        "take call",
        "ᴀɴsᴡᴇʀ",       // stylized
        "उत्तर",         // hindi
        "स्वीकारें",      // hindi accept
        "जवाब",         // answer in hindi
    ];

    // scan all possible text-carrying elements inside the notification panel
    const elements = await driver.$$('//android.widget.Button | //android.widget.TextView | //android.widget.ImageButton');

    for (let el of elements) {
        try {
            const text = (await el.getText() || "").toLowerCase();

            if (!text) continue;

            for (let keyword of answerKeywords) {
                if (text.includes(keyword.toLowerCase())) {
                    console.log(`📞 Found ANSWER button by text match → "${text}"`);
                    await el.click();
                    return true;
                }
            }
        } catch (_) {}
    }

    console.log("❌ No 'Answer' text found in notification elements");
    return false;
}




async function pickCall(driver, deviceId) {

    console.log("Expanding notification shade...");
    await openNotifications(deviceId);

    await driver.pause(5000);

    console.log("Looking for Answer button in notification...");
    const ans = await answerIncomingCall(driver);

    return ans;

    
}


async function performIncomingCallTest(deviceId,durationSec=10){
    const driver = await createDriver(deviceId);
const dialingStart = Date.now();
let ans=await pickCall(driver,deviceId);
console.log(ans,"=========================================================")
if(ans){
    // const connected = await waitForTimerByText(driver);
    const callConnectedAt =  Date.now();
     await driver.pause(durationSec * 1000);

    await endCall(driver);
    return {dialingStart,callConnectedAt}
}
}



module.exports={performIncomingCallTest,pickCall}