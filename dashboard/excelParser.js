const XLSX = require('xlsx');
const path = require('path');

function parseExcelTestData(filePath) {
  try {
    const workbook = XLSX.readFile(filePath);
    const result = {
      calling: [],
      sms: [],
      dataUsage: []
    };

    // Parse Calling Sheet
    if (workbook.SheetNames.includes('Calling')) {
      const sheet = workbook.Sheets['Calling'];
      const data = XLSX.utils.sheet_to_json(sheet);
      result.calling = data.map(row => ({
        name: row['Name'] || row['Test Name'],
        aPartyNumber: row['A Party Number'],
        bPartyNumber: row['B Party Number'],
        cPartyNumber: row['C Party Number'] || null,
        preferredNetwork: row['Preferred Network'] || '4G',
        volteSupported: row['VoLTE Supported'] === true || row['VoLTE Supported'] === 'TRUE',
        duration: parseInt(row['Duration (s)']) || 15,
        attempts: parseInt(row['Attempts']) || 1,
        callType: row['Call Type'] || 'VOICE',
        direction: row['Direction'] || 'OUTGOING'
      }));
    }

    // Parse SMS Sheet
    if (workbook.SheetNames.includes('SMS')) {
      const sheet = workbook.Sheets['SMS'];
      const data = XLSX.utils.sheet_to_json(sheet);
      result.sms = data.map(row => ({
        testType: row['Test Type']?.trim(),
        groupName: row['Group Name']?.trim() || null,
        aPartyNumber: row['A Party Number'],
        bPartyNumber: row['B Party Number'] || null,
        messageType: row['Message type']?.trim() || 'Text Message',
        message: row['Message']?.trim(),
        direction: row['Direction']?.trim() || 'OUTGOING',
        smsCount: parseInt(row['SMS Count']) || 1,
        expectedMembers: parseInt(row['Expected Members']) || 0
      }));
    }

    // Parse Data Usage Sheet
    if (workbook.SheetNames.includes('DataUsage')) {
      const sheet = workbook.Sheets['DataUsage'];
      const data = XLSX.utils.sheet_to_json(sheet);
      result.dataUsage = data.map(row => ({
        scenario: row['Test Scenario'],
        targetDataGB: parseFloat(row['Target Data (GB)']),
        durationMin: parseInt(row['Duration (min)']),
        appsToUse: row['Apps to Use']?.split(',').map(s => s.trim()),
        validationCriteria: row['Validation Criteria']
      }));
    }

    return result;
  } catch (error) {
    console.error('❌ Error parsing Excel:', error);
    throw error;
  }
}

module.exports = { parseExcelTestData };