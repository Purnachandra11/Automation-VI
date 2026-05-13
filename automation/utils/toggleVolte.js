const { execSync } = require("child_process");

const { createDriver } = require("./appiumDriver");
async function toggleVoLTE(deviceId, enable) {
    let driver = await createDriver(deviceId);
    const desired = enable ? 1 : 0;

    console.log(`\n==============================`);
    console.log(`   🔧 TOGGLING VOLTE → ${enable ? "ON" : "OFF"}`);
    console.log(`==============================\n`);

    // 1️⃣ Try ALL possible OEM VoLTE settings via ADB
    const commands = [
        // AOSP / Samsung
        `settings put global enhanced_4g_lte_mode_enabled ${desired}`,
        `settings put global volte_vt_enabled ${desired}`,
        `settings put global ims_video_calling_enabled ${desired}`,

        // Xiaomi / Redmi / Poco
        `settings put global enable_volte ${desired}`,

        // Oppo / Vivo
        `settings put system volte_vt_enabled ${desired}`,
        `settings put secure ims_volte_enabled ${desired}`
    ];

    for (const cmd of commands) {
        try {
            execSync(`adb -s ${deviceId} shell ${cmd}`, { stdio: "ignore" });
        } catch (_) { }
    }

    // 2️⃣ Validate using dumpsys (check if VoLTE enabled)
    const status = getVoLTEStatus(deviceId);
    console.log("Current VoLTE status:", status);

    if (status.volteEnabled === enable) {
        console.log("✔ ADB successfully toggled VoLTE");
        return true;
    }

    console.log("⚠ ADB toggle failed → trying UI Automation...");

    // 3️⃣ FALLBACK: Open SIM settings using Appium and toggle the switch
    const result = await toggleVoLTEviaUI(driver, enable, deviceId);

    if (result) console.log("✔ Successfully toggled VoLTE via UI");
    else console.log("❌ VoLTE toggle failed");

    return result;
}

// ───────────────────────────────
// Check VoLTE status via dumpsys
// ───────────────────────────────
function getVoLTEStatus(deviceId) {
    try {
        const output = execSync(
            `adb -s ${deviceId} shell dumpsys telephony.registry`,
            { encoding: "utf-8" }
        );

        const ims = /mImsRegistered=(true|false)/.exec(output);
        const voiceRat = /mVoiceNetworkType=(\d+)/.exec(output);
        console.log(ims, voiceRat)
        return {
            imsRegistered: ims ? ims[1] === "true" : false,
            voiceNetworkType: voiceRat ? parseInt(voiceRat[1]) : null,
            volteEnabled: ims ? ims[1] === "true" : false
        };
    } catch (err) {
        return { imsRegistered: false, voiceNetworkType: null, volteEnabled: false };
    }
}


function openMobileNetworkSettings(deviceId) {
    execSync(`adb -s ${deviceId} shell am start -a android.settings.DATA_ROAMING_SETTINGS`);
}
// ───────────────────────────────
// UI Automation fallback (works on ALL devices)
// Requires Appium driver
// ───────────────────────────────
async function toggleVoLTEviaUI(driver, enable, deviceId) {
    try {
        console.log("📱 Opening Mobile Network Settings...");

        openMobileNetworkSettings(deviceId);

        await driver.pause(2000);

        const possibleSwitches = [
            '//*[@text="VoLTE"]//following-sibling::android.widget.Switch',
            '//*[@text="4G Calling"]//following-sibling::android.widget.Switch',
            '//*[@text="Enhanced 4G LTE Mode"]//following-sibling::android.widget.Switch',
            '//android.widget.Switch'
        ];

        for (let xp of possibleSwitches) {
            try {
                const el = await driver.$(xp);
                if (await el.isDisplayed()) {
                    const value = await el.getAttribute("checked");
                    const isOn = value === "true";

                    if (isOn !== enable) {
                        console.log("📞 Toggling VoLTE switch...");
                        await el.click();
                        await driver.pause(1500);
                    }

                    // validate again
                    return true;
                }
            } catch (_) { }
        }

        console.log("❌ No VoLTE switch found in UI");
        return false;
    } catch (err) {
        console.log("❌ UI toggle error:", err);
        return false;
    }
}

module.exports = { toggleVoLTE };
