const express=require('express');
const router=express.Router()
const { exec } = require('child_process');
const fs = require("fs");
const path = require("path");

const {getSimNumberViaUSSD} =require('../utils/getSimNumber')
const SIM_MAP_FILE = path.join(__dirname, "device-sim-map.json");





router.post('/device/connect-usb', async (req, res) => {
    exec('adb devices', async (error, stdout) => {
        if (error) {
            return res.json({ success: false, message: error.message });
        }

        const lines = stdout.split('\n');
        const deviceLine = lines.filter(line => line.includes('\tdevice'));

        if (!deviceLine || deviceLine.length === 0) {
            return res.json({ success: false, message: 'No USB device found' });
        }

        const deviceIds = deviceLine.map(line => line.split('\t')[0]);

        try {
            // Run USSD query in parallel
            const simResults = await Promise.all(
                deviceIds.map(id => getSimNumberViaUSSD(id))
            );

            // Final merged array
            let final = deviceIds.map((id, index) => ({
                id,
                sim: simResults[index]?.sim || null
            }));

            // --------------------------
            // WRITE TO JSON FILE (SAVE DEVICE → SIM MAP)
            // --------------------------

            let existingData = {};
 console.log(fs.existsSync(SIM_MAP_FILE));
            

            final.forEach(item => {
                if (item.sim) {
                    existingData[item.sim] = item.id;
                }
            });

           fs.writeFileSync(SIM_MAP_FILE, JSON.stringify(final, null, 2), "utf8");

            // --------------------------

            return res.json({
                success: true,
                devices: final,
                message: 'USB devices connected successfully'
            });

        } catch (err) {
            return res.json({
                success: false,
                message: 'Error while fetching SIM via USSD',
                error: err.message
            });
        }
    });
});



module.exports=router
