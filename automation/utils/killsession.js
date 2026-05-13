const { execSync } = require("child_process");

function killUiAutomatorProcesses(deviceId) {
    const cmds = [
        `adb -s ${deviceId} shell pkill -f uiautomator`,
        `adb -s ${deviceId} shell pkill -f io.appium.uiautomator2.server`,
        `adb -s ${deviceId} shell pkill -f io.appium.uiautomator2`
    ];

    for (const cmd of cmds) {
        try { execSync(cmd); } catch (_) {}
    }
}

module.exports={killUiAutomatorProcesses}
