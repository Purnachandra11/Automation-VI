const { exec } = require("child_process");

function getConnectedDevice(callback) {
    exec("adb devices", (err, stdout) => {
        if (err) return callback(err);

        const lines = stdout.split("\n").slice(1);
        console.log(lines)
        const device = lines.find(line => line.includes("device"));

        if (!device) return callback(null, null);

        const deviceId = device.split("\t")[0];
        callback(null, deviceId);
    });
}

module.exports = { getConnectedDevice };
