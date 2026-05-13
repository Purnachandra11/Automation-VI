const util = require("util");
const exec = util.promisify(require("child_process").exec);

// MOBILE interfaces found on almost every device
const MOBILE_IFACES = [
    "rmnet_data0", "rmnet_data1", "rmnet_ipa0", "rmnet0", "ccmni0", "ccmni1", "vzw_rmnet0"
];

async function getMobileBytes(deviceId) {
    try {
        const { stdout } = await exec(
            `adb -s ${deviceId} shell cat /proc/net/dev`
        );

        let total = 0;

        const lines = stdout.split("\n");

        for (let line of lines) {
            for (let iface of MOBILE_IFACES) {
                if (line.includes(iface)) {
                    const parts = line.split(/\s+/);

                    const rx = parseInt(parts[1] || 0);
                    const tx = parseInt(parts[9] || 0);

                    total += rx + tx;
                }
            }
        }

        return total;

    } catch (err) {
        console.log("❌ Failed reading /proc/net/dev:", err.message);
        return 0;
    }
}

async function openApp(deviceId, packageName) {
    console.log(`📲 Opening app: ${packageName}`);
    await exec(`adb -s ${deviceId} shell monkey -p ${packageName} -c android.intent.category.LAUNCHER 1`);
    await new Promise(res => setTimeout(res, 5000));

    console.log("▶️ Auto-playing YouTube...");
    await exec(`adb -s ${deviceId} shell input keyevent 66`);
}

async function getAPN(deviceId) {
    try {
        const { stdout } = await exec(
            `adb -s ${deviceId} shell content query --uri content://telephony/carriers/preferapn`
        );
     console.log(stdout)
     console.log("=============================")
        if (!stdout || stdout.trim() === "") return "Unknown";

        const apnMatch = stdout.match(/apn=(.*?)[,\n]/);
         const apnName= stdout.match(/name=(.*?)[,\n]/);
        return {apn:apnMatch ? apnMatch[1] : "Unknown",name:apnName?apnName[1]:"Unknown"};

    } catch (e) {
        return "Unknown";
    }
}

async function openChromeAndDownload(deviceId, url) {
    console.log(`🌐 Opening Chrome with URL: ${url}`);

    await exec(
        `adb -s ${deviceId} shell am start -n com.android.chrome/com.google.android.apps.chrome.Main -a android.intent.action.VIEW -d "${url}"`
    );

    await new Promise(res => setTimeout(res, 5000));
}

async function startDataUsageTest(deviceId, packageName, targetMb, durationSec,appmode,res) {
    console.log(`🚀 Starting Data Usage Test`);
    console.log(deviceId)
     function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }

    send({ status: "Checking and Enabling Mobile Data" });

    // await exec(`adb -s ${deviceId} shell svc wifi disable`);
    await exec(`adb -s ${deviceId} shell svc data enable`);

    console.log("🌐 Forcing mobile data routing...");
    await exec(`adb -s ${deviceId} shell settings put global mobile_data 1`);

    send({ status: "Fetching APN" });
    let r= await getAPN(deviceId)
    console.log(r)  
    if(appmode.toLowerCase()=='stream'){
        send({ status: "Opening Youtube" });
        await openApp(deviceId, packageName);
    }else{
        send({ status: "Downloading File" });

        await openChromeAndDownload(deviceId,`http://speedtest.tele2.net/${targetMb}MB.zip`)
    }

    const startBytes = await getMobileBytes(deviceId);
    console.log(`📡 Start bytes: ${startBytes}`);
    
    const targetBytes = targetMb * 1024 * 1024;
    const stopTime = Date.now() + durationSec * 1000;

    while (Date.now() < stopTime) {
        const nowBytes = await getMobileBytes(deviceId);
        console.log(nowBytes)
        
        const used = nowBytes - startBytes;

        console.log(`📶 Used: ${(used / (1024 * 1024)).toFixed(2)} MB`);
           send({ status: `📶 Used: ${(used / (1024 * 1024)).toFixed(2)} MB`});

        if (used >= targetBytes) {
            
            console.log("🎯 Target reached!");
            return {
                success: true,
                usedMB: (used / (1024 * 1024)).toFixed(2),
                targetReached: true,
                apn:r.apn,
                name:r.name
            };
        }

        await new Promise(res => setTimeout(res, 2000));
    }

    const final = await getMobileBytes(deviceId);
    const used = final - startBytes;

    return {
        success: true,
        usedMB: (used / (1024 * 1024)).toFixed(2),
        targetReached: used >= targetBytes,
        apn:r
    };
}

module.exports = { startDataUsageTest };
