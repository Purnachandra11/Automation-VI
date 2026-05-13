const express = require("express");
const { getConnectedDevice } = require("./utils/device");
const { toggleVoLTE } = require("./utils/toggleVolte");
const fs = require("fs");
const { getSimNumberViaUSSD } = require("./utils/getSimNumber");
const { runCallTest } = require("./tests/calltest");
const { runSmsTest } = require("./tests/SmsTest");
const { runSmsUiTest } = require("./tests/smsuitest");
const { generateReport, generateSMSReport,generateDataReport } = require("./utils/excel");
const multer = require("multer");
const XLSX = require("xlsx");
const { runDataUsageTest } = require("./tests/datatest");
const { performIncomingCallTest } = require("./utils/pickCall")

const upload = multer({ dest: "uploads/" });

const app = express();
const cors = require("cors");
app.use(cors());

app.use(express.json());
app.use('/api', require('./controller/configurationRouter'))

app.get("/device", (req, res) => {
    getConnectedDevice((err, device) => {
        if (err) return res.status(500).send("Error detecting device");
        if (!device) return res.send("No device detected");
        res.send({ device });
    });
});

app.post("/getBalance",async(req,res)=>{
    let obj = await getSimNumberViaUSSD(req.body.deviceId);

    res.json({status:1,...obj})
})

app.get("/start-test", async (req, res) => {
    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("Connection", "keep-alive");

    function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }

    send({ status: "Reading Test Cases" });
    let deviceId
    let receiveId
    const workbook = XLSX.readFile("../src/test/resources/contacts.xlsx");
    const sheet = workbook.Sheets['Calling'];
    const rows = XLSX.utils.sheet_to_json(sheet);

    console.log(rows)

    send({ status: "Starting Tests" });
    const results = [];
    let data = []
    let phone
    let cumbalance = ''
    let raw = fs.readFileSync("./controller/device-sim-map.json", "utf8");
    raw = JSON.parse(raw)
    let initRow = 0
    // Loop each test row sequentially
    for (let i = 0; i < rows.length; i++) {
        let fail = ""
        const { testName, targetNumber, duration, type, number } = {
            testName: rows[i]['Name'],
            targetNumber: rows[i]['B Party Number'],
            duration: rows[i]['Actual Call Duration (s)'],
            type: rows[i]['Type'],
            number: rows[i]['A Party Number'],
        };
        send({ status: `Running test ${i + 1}/${rows.length}-${testName}` });

        console.log(number, i)
        console.log(raw)
        let result = {};
        let sim;

        let aftersim;
        let afterbalance;
        let mainDevice = raw.filter((ele) => ele.sim == number)
        if (mainDevice.length) {
            deviceId = mainDevice[0].id
        } else {
            fail = "Either 'A Party number' is wrong or device not connected"
            send({ error: fail });
            deviceId = undefined
            if (i == initRow) {
                initRow++
            }
            // res.json({status:0,message:"Either A party number is wrong or device not connected"})
        }
        console.log(fail)

        if (fail == '') {
            let secondDevice = raw.filter((ele) => ele.sim == targetNumber)
            if (secondDevice.length) {
                receiveId = secondDevice[0].id
            } else {
                receiveId = undefined
            }


            console.log(i, "0987678987")

            if (i == initRow) {
                send({ status: "Getting Initial Balance" });
                let obj = await getSimNumberViaUSSD(deviceId, res);
                sim = obj.sim;
                phone = obj.sim
                cumbalance = obj.balance
            }
            console.log(sim, cumbalance, receiveId, type)
            console.log("==================================================")
            send({ status: "running-call", test: testName });
            result = await runCallTest(type == 'incoming' ? receiveId : deviceId, type == 'incoming' ? phone : targetNumber, duration, type == 'incoming' ? deviceId : receiveId, res);
            send({ status: "Getting After call Balance" });
            let obj1 = await getSimNumberViaUSSD(deviceId, res);
            sim = obj1.sim;
            afterbalance = obj1.balance

            results.push({ testName, ...result });
        }
        data.push({
            name: testName,
            startingbalance: cumbalance,
            afterbalance: afterbalance,
            deducted:diffMoney(cumbalance,afterbalance),
            attempt: 1,
            time: new Date(),
            device: fail == '' ? phone : number,
            number: targetNumber,
            targetDuration: duration,
            type: type,
            connected: result.activeTime,
            result: result.activeTime >= duration ? "Success" : "Fail",
            reason: fail == "" ? '-' : fail
        })
        cumbalance = afterbalance
    }
    send({ status: "Tests Done , Generating Report" });
    let b = Date.now()
    await generateReport(data, b.toString());

    send({ status: "All Tests Completed,Reports are available Below", data: { name: b.toString() } });

    res.write(`event: end\ndata: ${JSON.stringify({
        finalData: b.toString(),
    })}\n\n`);
    res.end();
});


app.get("/start-sms-test", async (req, res) => {

    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("Connection", "keep-alive");

    function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }

    send({ status: "Reading Test Cases" });
    let deviceId
    let receiveId
    const workbook = XLSX.readFile("../src/test/resources/contacts.xlsx");
    const sheet = workbook.Sheets['SMS'];
    const rows = XLSX.utils.sheet_to_json(sheet);

    console.log(rows)

    send({ status: "Starting Tests" });
    const results = [];
    let data = []
    let phone
    let cumbalance = ''
    let raw = fs.readFileSync("./controller/device-sim-map.json", "utf8");
    raw = JSON.parse(raw)
    let initRow = 0

    for (let i = 0; i < rows.length; i++) {
        let fail = ""
        const { testName, targetNumber, message, receiveId, count,number } = {
            testName: rows[i]['Test Type'],
            targetNumber: rows[i]['Recipient'],
            message: rows[i]['Message'],
            receiveId: rows[i]['receiveId'],
            count: rows[i]['SMS Count'],
            number: rows[i]['A Party Number'],
        };
        console.log(number, i)
        console.log(raw)
        let result = {};
        let sim;

        let aftersim;
        let afterbalance;
        send({ status: "Linking the 'A Party Number' Device" });
        let mainDevice = raw.filter((ele) => ele.sim == number)
        if (mainDevice.length) {
            deviceId = mainDevice[0].id
        } else {
            fail = "Either 'A Party number' is wrong or device not connected"
            send({ error: fail });
            deviceId = undefined
            if (i == initRow) {
                initRow++
            }
            // res.json({status:0,message:"Either A party number is wrong or device not connected"})
        }
        console.log(fail)


        let type = 'outgoing'
if(fail==''){
    send({ status: "Reading Initial Balance" });
        if (i == initRow) {
            let obj = await getSimNumberViaUSSD(deviceId,res);
            sim = obj.sim;
            phone = obj.sim
            cumbalance = obj.balance
        }
        console.log(sim, cumbalance, receiveId, type)
        console.log("==================================================")
        send({ status: `Sending the message to ${targetNumber}` });
        // const result = await runCallTest(type=='incoming'?receiveId:deviceId,type=='incoming'?phone: targetNumber, duration,type=='incoming'?deviceId:receiveId);
        result = await runSmsUiTest(deviceId, targetNumber, message, count,res);
        send({ status: "Fetching Balance Again" });
        let obj1 = await getSimNumberViaUSSD(deviceId,res);
        sim = obj1.sim;
        afterbalance = obj1.balance

        results.push({ testName, ...result });
    }
        data.push({
            name: testName,
            startingbalance: cumbalance,
            afterbalance: afterbalance,
            deducted:diffMoney(cumbalance,afterbalance),
            count: count,
            time: new Date(),
            message: message,
            device: fail==''?phone:number,
            number: targetNumber,
            result: "Success",
            reason:fail==""?'-':fail
        })
        cumbalance = afterbalance
    }
      send({ status: "Tests Done , Generating Report" });
    let b = Date.now()

    await generateSMSReport(data, b.toString());

    send({ status: "All Tests Completed,Reports are available Below", data: { name: b.toString() } });

    res.write(`event: end\ndata: ${JSON.stringify({
        finalData: b.toString(),
    })}\n\n`);
    res.end();


});


app.get("/start-data-test", async (req, res) => {
    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("Connection", "keep-alive");

    function send(data) {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
    }

    send({ status: "Reading Test Cases" });
    let deviceId
    const workbook = XLSX.readFile("../src/test/resources/contacts.xlsx");
    const sheet = workbook.Sheets['DataUsage'];
    const rows = XLSX.utils.sheet_to_json(sheet);

    console.log(rows)
     send({ status: "Starting Tests" });
    const results = [];
    let data = []
    let phone
    let cumbalance = ''
    let raw = fs.readFileSync("./controller/device-sim-map.json", "utf8");
    raw = JSON.parse(raw)
    let initRow = 0

    for (let i = 0; i < rows.length; i++) {
        let fail = ""
        const { testName, targetNumber, duration,number,appmode } = {
            testName: rows[i]['Test Scenario'],
            targetNumber: rows[i]['Target Data (MB)'],
            duration: rows[i]['Duration (sec)'],
            number: rows[i]['A Party Number'],
            appmode:rows[i]['Apps to Use']
        };
        console.log(number, i)
        console.log(raw)
        let result = {};
        let sim;

        let aftersim;
        let afterbalance;
        send({ status: "Linking the 'A Party Number' Device" });
        let mainDevice = raw.filter((ele) => ele.sim == number)
        if (mainDevice.length) {
            deviceId = mainDevice[0].id
        } else {
            fail = "Either 'A Party number' is wrong or device not connected"
            send({ error: fail });
            deviceId = undefined
            if (i == initRow) {
                initRow++
            }
            // res.json({status:0,message:"Either A party number is wrong or device not connected"})
        }
        console.log(fail)


        let type = 'outgoing'
if(fail==''){
    // send({ status: "Reading Initial Balance" });
    //     if (i == initRow) {
    //         let obj = await getSimNumberViaUSSD(deviceId,res);
    //         sim = obj.sim;
    //         phone = obj.sim
    //         cumbalance = obj.balance
    //     }
        // console.log(sim, cumbalance, receiveId, type)
        console.log("==================================================")
        send({ status: `Starting data test for  ${targetNumber} Mb` });
         result = await runDataUsageTest(deviceId, app, targetNumber, duration,appmode,res);
        console.log(result);
        send({ status: "Fetching Balance Again" });
        // let obj1 = await getSimNumberViaUSSD(deviceId,res);
        // sim = obj1.sim;
        // afterbalance = obj1.balance

        results.push({ testName, ...result });
    }
    // sheet.columns = [
    //     { header: "Test Scenario", key: "name" },
    //     { header: "A Party", key: "device" },
    //     { header: "Target Data(MB)", key: "target" },
    //     { header: "Duration (sec)", key: "duration" },
    //     { header: "Consumed Data", key: "consumed" },
    //     { header: "Target Achieved", key: "achieved" },
    //     { header: "Previous Balance", key: "startingbalance" },
    //     { header: "New Balance", key: "afterbalance" },
    //     { header: "Final Status", key: "result" },
    //     { header: "Reason", key: "reason" },
    //     { header: "Time Stamp", key: "time" }
    // ];

        data.push({
            name: testName,
            startingbalance: cumbalance,
            afterbalance: afterbalance,
            deducted:diffMoney(cumbalance,afterbalance),
            duration:duration,
            time: new Date(),
            consumed:result.usedMB,
            device: number,
            app:appmode,
            apn:result.apn,
            apnName:result.name,
            achieved:targetNumber<=result.usedMB,
            target: targetNumber,
            result: result.targetReached?"Success":result.usedMB>0?"Data Working but target not achieved":"Fail",
            reason:fail==""?'-':fail
        })
        cumbalance = afterbalance
    }
      send({ status: "Tests Done , Generating Report" });
    let b = Date.now()

    await generateDataReport(data, b.toString());

    send({ status: "All Tests Completed,Reports are available Below", data: { name: b.toString() } });

    res.write(`event: end\ndata: ${JSON.stringify({
        finalData: b.toString(),
    })}\n\n`);
    res.end();


});

app.post("/startPickupTest", async (req, res) => {
    const deviceId = req.body.deviceId
    const phone = req.body.phone
    const workbook = XLSX.readFile("../src/test/resources/contacts.xlsx");
    const sheet = workbook.Sheets['DataUsage'];
    const rows = XLSX.utils.sheet_to_json(sheet);
    const result = await performIncomingCallTest(deviceId);
    console.log(result);
    res.send({
        status: "Data Usage Test Completed",
        result
    });
})

function parseMoney(value) {
    if (!value) return 0;

    // Remove currency symbols, commas, spaces
    const cleaned = value
        .replace(/[^0-9.-]/g, "") // keep digits, dot, minus
        .trim();

    return parseFloat(cleaned) || 0;
}

function diffMoney(a, b) {
    const numA = parseMoney(a);
    const numB = parseMoney(b);

    return numA - numB;
}

// toggleVoLTE('100.75.151.109:46527',true)

app.listen(5175, "0.0.0.0", () => {
  console.log("Server running on port 5175 (public)");
});