const { exec } = require("child_process");
const util = require("util");
const execPromise = util.promisify(exec);

async function getSimNumberViaUSSD(deviceId, res) {
  if (res) {
    function send(data) {
      res.write(`data: ${JSON.stringify(data)}\n\n`);
    }
  }

  console.log("📞 Dialing USSD to get SIM number...");
  await execPromise(`adb -s ${deviceId} shell am start -a android.intent.action.CALL -d tel:*199%23`);
  if (res) send({ status: "Dialing USSD", progress: 10 });

  console.log("⏳ Waiting for USSD popup...");
  const timeout = Date.now() + 15000;
  let popupText = "";
  while (Date.now() < timeout) {
    try {
      await execPromise(`adb -s ${deviceId} shell uiautomator dump /sdcard/ussd_dump.xml`);
      const { stdout } = await execPromise(`adb -s ${deviceId} shell cat /sdcard/ussd_dump.xml`);
      const textNodes = Array.from(stdout.matchAll(/text=\"([^\"]+)\"/g)).map(m => m[1]);
      if (textNodes && textNodes.length) {
        const joined = textNodes.join("\n");
        if (!joined.toLowerCase().includes("running") && !joined.toLowerCase().includes("loading")) {
          popupText = joined;
          break;
        }
      }
    } catch (_) {}
    await new Promise(r => setTimeout(r, 400));
  }

  console.log("📱 USSD Response:", popupText);

  // Extract phone number and balance
  let phoneNumber = null;
  let balance = null;
  let validityDate = null;
  let validityIsFuture = null;

  const lines = popupText.split("\n").map(l => l.trim()).filter(Boolean);
  for (const line of lines) {
    if (!phoneNumber) {
      const m = line.match(/msisdn\s*[:]?\s*(\d{10,})/i) || line.match(/mobile\s*number\s*[:]?\s*(\d{10,})/i);
      if (m) phoneNumber = m[1];
    }
    if (!balance) {
      const b1 = line.match(/main\s*bal(?:ance)?\s*[:]?\s*(?:rs\.?|₹)?\s*([\d\.]+)/i);
      const b2 = line.match(/balance\s*[:]?\s*(?:rs\.?|₹)?\s*([\d\.]+)/i);
      if (b1) balance = b1[1];
      else if (b2) balance = b2[1];
    }
    if (!validityDate) {
      const v1 = line.match(/ul\s*vldty\s*[:]?\s*([\w\-\/ ]+)/i);
      const v2 = line.match(/validity\s*[:]?\s*([\w\-\/ ]+)/i);
      const raw = v1 ? v1[1] : (v2 ? v2[1] : null);
      if (raw) {
        const parsed = parseValidity(raw);
        if (parsed) validityDate = parsed.toISOString();
      }
    }
  }

  if (validityDate) {
    const dt = new Date(validityDate);
    const today = new Date();
    dt.setHours(0,0,0,0);
    today.setHours(0,0,0,0);
    validityIsFuture = dt >= today;
  }

  console.log(" Phone Number:", phoneNumber);
  console.log("💰 Balance:", balance);

  if (res) send({ 
    status: `Device Ready`, 
    balance, 
    validityDate, 
    validityIsFuture,
    progress: 30 
  });

  await closeUssdWithButton(deviceId);
  await clearResidualUssdPopups(deviceId);

  return { phoneNumber, balance, validityDate, validityIsFuture };
}

// async function closeUssdWithBackKey(deviceId) {
//   try {
//     await execPromise(`adb -s ${deviceId} shell input keyevent 4`);
//     await new Promise(r => setTimeout(r, 300));
//     await execPromise(`adb -s ${deviceId} shell input keyevent 4`);
//     return true;
//   } catch (_) {
//     return false;
//   }
// }
// ---------------------------------------------------------------
// CLOSE POP-UP
// ---------------------------------------------------------------
async function closeUssdWithButton(deviceId) {
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
            const btn = await deviceId.$(xpath);
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
async function clearResidualUssdPopups(deviceId) {
    const timeout = Date.now() + 15000;

    while (Date.now() < timeout) {
        try {
            const msg = await deviceId.$('//*[@resource-id="android:id/message"]');
            if (await msg.isDisplayed()) {
                console.log("Closing secondary USSD...");
                await closeUssdWithBackKey(deviceId);
                await new Promise(r => setTimeout(r, 500));
                continue;
            }
        } catch (_) { }

        await new Promise(r => setTimeout(r, 300));
    }
}
// async function clearResidualUssdPopups(deviceId) {
//   const timeout = Date.now() + 15000;
//   while (Date.now() < timeout) {
//     try {
//       await execPromise(`adb -s ${deviceId} shell uiautomator dump /sdcard/ussd_dump.xml`);
//       const { stdout } = await execPromise(`adb -s ${deviceId} shell cat /sdcard/ussd_dump.xml`);
//       const hasMessage = /resource-id=\"android:id\/message\"/.test(stdout);
//       if (hasMessage) {
//         console.log("Closing secondary USSD...");
//         await closeUssdWithBackKey(deviceId);
//         await new Promise(r => setTimeout(r, 500));
//         continue;
//       }
//     } catch (_) {}
//     await new Promise(r => setTimeout(r, 300));
//   }
// }

function parseValidity(text) {
  const t = text.trim();
  const m1 = t.match(/(\d{2})[\-\/](\d{2})[\-\/](\d{4})/);
  if (m1) {
    const d = `${m1[3]}-${m1[2]}-${m1[1]}`;
    const dt = new Date(d);
    if (!isNaN(dt.getTime())) return dt;
  }
  const m2 = t.match(/(\d{2})[\-\/](\d{2})[\-\/](\d{2})/);
  if (m2) {
    const yy = parseInt(m2[3], 10);
    const year = yy + (yy >= 70 ? 1900 : 2000);
    const d = `${year}-${m2[2]}-${m2[1]}`;
    const dt = new Date(d);
    if (!isNaN(dt.getTime())) return dt;
  }
  const m3 = t.match(/(\d{2})\s+([A-Za-z]{3,})\s+(\d{4})/);
  if (m3) {
    const month = mapMonth(m3[2]);
    if (month) {
      const d = `${m3[3]}-${month}-${m3[1]}`;
      const dt = new Date(d);
      if (!isNaN(dt.getTime())) return dt;
    }
  }
  return null;
}

function mapMonth(m) {
  const s = m.toLowerCase();
  const map = { jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06', jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12' };
  const k = s.slice(0,3);
  return map[k];
}

module.exports = { getSimNumberViaUSSD };
