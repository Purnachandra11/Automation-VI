const Excel = require("exceljs");

async function generateReport(data, name) {
    const workbook = new Excel.Workbook();
    const sheet = workbook.addWorksheet("Call Test");

    sheet.columns = [
        { header: "Test Scenario", key: "name" },
        { header: "A Party", key: "device" },
        { header: "B Party", key: "number" },
        { header: "Target Duration", key: "targetDuration" },
        { header: "Type", key: "type" },
        { header: "Actual Duration (sec)", key: "connected" },
        { header: "Previous Balance", key: "startingbalance" },
        { header: "Deduction", key: "deducted" },
        { header: "New Balance", key: "afterbalance" },
        { header: "Attempts", key: "attempt" },
        { header: "Final Status", key: "result" },
        { header: "Reason", key: "reason" },
        { header: "Time Stamp", key: "time" }
    ];

    // Style header row
    const headerRow = sheet.getRow(1);

    headerRow.eachCell((cell) => {
        cell.fill = {
            type: "pattern",
            pattern: "solid",
            fgColor: { argb: "FF4B0082" } // Indigo
        };

        cell.font = {
            bold: true,
            color: { argb: "FFFFFFFF" },  // White text
            size: 12
        };

        cell.alignment = { horizontal: "center", vertical: "middle" };
    });

    // Add data rows
    data.forEach((row) => {
        sheet.addRow(row);
    });

    // Auto-fit column widths
    sheet.columns.forEach((column) => {
        let maxLength = 10; // minimum width
        column.eachCell({ includeEmpty: true }, (cell) => {
            const cellValue = cell.value ? cell.value.toString() : "";
            maxLength = Math.max(maxLength, cellValue.length);
        });
        column.width = maxLength + 2; // add padding
    });

    await workbook.xlsx.writeFile(`../test-output/reports/CallingReport_${name}.xlsx`);
}

async function generateSMSReport(data, name) {
    const workbook = new Excel.Workbook();
    const sheet = workbook.addWorksheet("SMS Test");

    sheet.columns = [
        { header: "Test Scenario", key: "name" },
        { header: "A Party", key: "device" },
        { header: "B Party", key: "number" },
        { header: "Count", key: "count" },
        { header: "message", key: "message" },
        { header: "Previous Balance", key: "startingbalance" },
        
        { header: "New Balance", key: "afterbalance" },
        { header: "Deduction", key: "deducted" },
        { header: "Final Status", key: "result" },
        { header: "Reason", key: "reason" },
        { header: "Time Stamp", key: "time" }
    ];

    // Style header row
    const headerRow = sheet.getRow(1);

    headerRow.eachCell((cell) => {
        cell.fill = {
            type: "pattern",
            pattern: "solid",
            fgColor: { argb: "FF4B0082" } // Indigo
        };

        cell.font = {
            bold: true,
            color: { argb: "FFFFFFFF" },  // White text
            size: 12
        };

        cell.alignment = { horizontal: "center", vertical: "middle" };
    });

    // Add data rows
    data.forEach((row) => {
        sheet.addRow(row);
    });

    // Auto-fit column widths
    sheet.columns.forEach((column) => {
        let maxLength = 10; // minimum width
        column.eachCell({ includeEmpty: true }, (cell) => {
            const cellValue = cell.value ? cell.value.toString() : "";
            maxLength = Math.max(maxLength, cellValue.length);
        });
        column.width = maxLength + 2; 
    });

    await workbook.xlsx.writeFile(`../test-output/reports/SMSReport_${name}.xlsx`);
}


async function generateDataReport(data, name) {
    const workbook = new Excel.Workbook();
    const sheet = workbook.addWorksheet("Data Test");
    console.log(data)

    sheet.columns = [
        { header: "Test Scenario", key: "name" },
        { header: "A Party", key: "device" },
        { header: "Target Data(MB)", key: "target" },
        { header: "Duration (min)", key: "duration" },
        { header: "Consumed Data", key: "consumed" },
         { header: "APN ", key: "apn" },
         { header: "APN Name ", key: "apnName" },
        { header: "Target Achieved", key: "achieved" },
        // { header: "Previous Balance", key: "startingbalance" },
        // { header: "New Balance", key: "afterbalance" },
        { header: "Final Status", key: "result" },
        { header: "Reason", key: "reason" },
        { header: "Time Stamp", key: "time" }
    ];

    // Style header row
    const headerRow = sheet.getRow(1);

    headerRow.eachCell((cell) => {
        cell.fill = {
            type: "pattern",
            pattern: "solid",
            fgColor: { argb: "FF4B0082" } // Indigo
        };

        cell.font = {
            bold: true,
            color: { argb: "FFFFFFFF" },  // White text
            size: 12
        };

        cell.alignment = { horizontal: "center", vertical: "middle" };
    });

    // Add data rows
    data.forEach((row) => {
        sheet.addRow(row);
    });

    // Auto-fit column widths
    sheet.columns.forEach((column) => {
        let maxLength = 10; // minimum width
        column.eachCell({ includeEmpty: true }, (cell) => {
            const cellValue = cell.value ? cell.value.toString() : "";
            maxLength = Math.max(maxLength, cellValue.length);
        });
        column.width = maxLength + 2; 
    });

    await workbook.xlsx.writeFile(`../test-output/reports/DataUsageReport_${name}.xlsx`);
}



module.exports = { generateReport,generateSMSReport,generateDataReport };
