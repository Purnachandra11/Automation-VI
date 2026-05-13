const { createDriver } = require("../utils/appiumDriver");
const { pickCall } = require("../utils/pickCall");
const { endCall, startCall, waitForCallConnected, waitForCallTimer, waitForTimerByText } = require("../utils/call");
async function runCallTest(deviceId, targetNumber, durationSec = 15, receiveId, res) {
    function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }
    let driver
    let driver1
    if (deviceId) driver = await createDriver(deviceId);
    if (receiveId) driver1 = await createDriver(receiveId);

    console.log("📞 Starting call...");
    send({ status: `Dialing Call To ${targetNumber}` });
    const dialingStart = Date.now();
    if (deviceId) { await startCall(deviceId, targetNumber); }

    if (receiveId) {
        send({ status: `Automatic receiving the call on  ${targetNumber}` });
        await pickCall(driver1, receiveId)

    }

    const connected = await waitForTimerByText(driver);
    if(connected){send({ status: `Call Connected ` })}else{send({ status: "Call Not Connected Moving On"});};
    const callConnectedAt = connected ? Date.now() : dialingStart;

    const ringingTime = (callConnectedAt - dialingStart) / 1000;
    console.log("⏱ Ringing time:", ringingTime, durationSec);

    // ACTIVE CALL
    const activeStart = Date.now();

    await driver.pause(durationSec * 1000);
    send({ status: `Ending  Connected Call ` });
    await endCall(driver);

    const activeTime = (Date.now() - activeStart) / 1000;

    await driver.deleteSession();

    return { ringingTime, activeTime };
}



module.exports = { runCallTest };
