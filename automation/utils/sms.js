const util = require("util");
const exec = util.promisify(require("child_process").exec);

async function sendSmsUniversal(deviceId, phoneNumber, message) {
    console.log(`📩 Sending SMS to ${phoneNumber}`);

    const cmd = `adb -s ${deviceId} shell service call isms 7 i32 0 s16 "com.android.mms.service" s16 "${phoneNumber}" s16 "null" s16 "${message}"`;

    try {
        await exec(cmd);
        console.log("✓ SMS sent via universal ADB method");
    } catch (e) {
        console.error("❌ SMS sending failed:", e.message);
    }
}

async function sendMultipleSmsUniversal(deviceId, phoneNumber, message, count) {
    for (let i = 0; i < count; i++) {
        await sendSmsUniversal(deviceId, phoneNumber, message);
        await new Promise(r => setTimeout(r, 500));
    }
}

module.exports = { sendSmsUniversal, sendMultipleSmsUniversal };
