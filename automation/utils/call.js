const { exec } = require("child_process");

function startCall(deviceId, number) {
    return new Promise((resolve, reject) => {
        exec(`adb -s ${deviceId} shell am start -a android.intent.action.CALL -d tel:${number}`,
            (err) => err ? reject(err) : resolve()
        );
    });
}
function ussdCall(deviceId, number) {
    return new Promise((resolve, reject) => {
        exec(`adb -s ${deviceId} shell am start -a android.intent.action.CALL -d tel:*199%23`,
            (err) => err ? reject(err) : resolve()
        );
    });
}

// async function endCall(deviceId) {
//     try {
//         // Method 1 (basic)
//         await exec(`adb -s ${deviceId} shell input keyevent KEYCODE_ENDCALL`);

//         // Method 2 (alternate) - some devices use KEYCODE 6
//         await exec(`adb -s ${deviceId} shell input keyevent 6`);

//         // Method 3 (strongest) - ends call via Telecom service
//         await exec(`adb -s ${deviceId} shell service call telecom 28`);

//         // Method 4 (optional) - force kill dialer app
//         await exec(`adb -s ${deviceId} shell pkill com.android.dialer`);

//     } catch (err) {
//         console.log("Error ending call:", err);
//     }
// }


const execPromise = (cmd) => new Promise((resolve, reject) =>
    exec(cmd, (err, stdout) => err ? reject(err) : resolve(stdout))
);

async function waitForCallConnected(deviceId, timeoutMs = 15000) {
    console.log("⏳ Waiting for call connection...");

    const start = Date.now();

    while (Date.now() - start < timeoutMs) {
        try {
            const out = await execPromise(
                `adb -s ${deviceId} shell dumpsys telephony.registry | findstr mCallState`
            );

            // Extract numbers
            console.log("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

            console.log(out)
            console.log("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
            const match = out.match(/mCallState=(\d+)/);
            if (match) {
                const state = parseInt(match[1]);
                console.log(`📞 Call CONNECTED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>+${state}`);
                if (state === 2) {   // 2 = OFFHOOK (connected)
                    // return true;
                }
            }
        } catch (err) {
            // ignore & retry
        }

        await new Promise(res => setTimeout(res, 500));
    }

    console.log("⚠️ Call never reached OFFHOOK state");
    return false;
}

function isValidCallTimer(text) {
    // accept mm:ss format
    const m = text.match(/^(\d{1,2}):(\d{2})$/);
    if (!m) return false;

    const minutes = parseInt(m[1]);
    const seconds = parseInt(m[2]);

    // Reject system clock patterns (07–23)
    if (minutes >= 7 && minutes <= 23) return false;

    // Valid call timers: 00:xx to 06:xx
    if (minutes == 0 && seconds <= 59) return true;

    return false;
}

async function waitForTimerByText(driver, timeout = 15000) {
    console.log("⌛ Searching call timer with safe filtering...");

    const start = Date.now();

    while (Date.now() - start < timeout) {
        let xml = "";

        try {
            xml = await driver.getPageSource();
        } catch {}

        // capture all mm:ss patterns
        const matches = xml.match(/\b\d{1,2}:\d{2}\b/g);

        if (matches) {
            for (const t of matches) {
                if (isValidCallTimer(t)) {
                    console.log("📞 Call CONNECTED — Timer =", t);
                    return true;
                }
            }
        }

        // await driver.pause(300);
    }

    console.log("⚠ No timer detected — fallback...");
    return false;
}


const CALL_TIMER_IDS = [
    "com.android.incallui:id/chronometer",
    "com.google.android.dialer:id/contactgrid_bottom_timer",
    "com.google.android.dialer:id/call_state_label",
    "com.samsung.android.incallui:id/chronometer",
    "com.miui.incallui:id/miui_call_time",
    "com.android.server.telecom:id/primaryCallInfoDuration"
];
async function waitForCallTimer(driver, timeout = 15000) {
    console.log("⌛ Waiting for call timer...>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

    const start = Date.now();

    while (Date.now() - start < timeout) {
        for (let id of CALL_TIMER_IDS) {
            try {
                const el = await driver.$(`id:${id}`);
                if (await el.isExisting()) {
                    console.log("📞 Call CONNECTED (timer detected)");
                    return true;
                }
            } catch {
                // Ignore and keep checking
            }
        }

        await driver.pause(300);
    }

    console.log("⚠ Timer not detected, fallback to telephony");
    return false;
}


async function endCall(driver) {
    console.log("poiuyuiopiuytyuiouytrftyuiuytgftyuiuytrtyuiuytrtyuytrt");
    const selectors = [
        '~End call',
        '//android.widget.ImageButton[@content-desc="End call"]',
        '//android.widget.Button[@content-desc="End call"]',
        'id=com.android.dialer:id/end_call_button',
    ];

    for (const selector of selectors) {
        try {
            const btn = await driver.$(selector);
            if (await btn.isDisplayed()) {
                await btn.click();
                console.log("✓ Ended via UI selector:", selector);
                return;
            }
        } catch (e) {}
    }

    console.log("⚠ Using fallback pressKeyCode");
    try {
        await driver.pressKeyCode(6);
        return;
    } catch {}

    console.log("❌ All failed, forcing terminateApp…");
    await driver.terminateApp("com.android.dialer");
}


function getCallState(deviceId) {
    return new Promise((resolve) => {
        exec(`adb -s ${deviceId} shell dumpsys telephony.registry | grep mCallState`,
            (err, stdout) => {
                if (err) return resolve(-1);
                const match = stdout.match(/mCallState=(\d)/);
                resolve(match ? parseInt(match[1]) : -1);
            }
        );
    });
}

module.exports = { startCall, ussdCall,endCall,waitForTimerByText, getCallState,waitForCallConnected ,waitForCallTimer};
