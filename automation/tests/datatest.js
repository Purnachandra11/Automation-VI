// const { runDataUsageTest } = require("../utils/data");
const { getConnectedDevice } = require("../utils/device");
const { startDataUsageTest } =require( "../utils/data.js");




 async function runDataUsageTest(deviceId,app,target,sec,appmode,res) {
    
    const result = await startDataUsageTest(
        deviceId,          
        "com.google.android.youtube", 
        target,                   
        sec,
        appmode,
        res                    
    );
    console.log("📊 Data Usage Result:", result);
    return result;


}


module.exports = { runDataUsageTest };
