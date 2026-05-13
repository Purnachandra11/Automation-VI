const { getSimNumberViaUSSD } = require('./ussdHandler');
const { parseExcelTestData } = require('./excelParser');
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

class TestOrchestrator {
  constructor(deviceId, excelFilePath, wsClients, phoneDeviceMap) {
    this.deviceId = deviceId;
    this.excelFilePath = excelFilePath;
    this.wsClients = wsClients;
    this.testData = null;
    this.devicePhoneNumber = null;
    this.phoneToDeviceId = phoneDeviceMap || {};
  }

  broadcast(data) {
    const message = JSON.stringify({ deviceId: this.deviceId, ...data });
    this.wsClients.forEach(client => {
      if (client.readyState === 1) { // WebSocket.OPEN
        client.send(message);
      }
    });
  }

  async discoverConnectedDeviceNumbers() {
    try {
      const { stdout } = await execPromise('adb devices');
      const lines = stdout.split('\n');
      const deviceIds = lines
        .map(l => l.trim())
        .filter(l => l && l.endsWith('device'))
        .map(l => l.split('\t')[0]);

      for (const devId of deviceIds) {
        try {
          const { phoneNumber } = await getSimNumberViaUSSD(devId, null);
          if (phoneNumber) {
            this.phoneToDeviceId[phoneNumber] = devId;
            this.broadcast({
              type: 'log',
              message: `🔗 Mapped ${phoneNumber} → ${devId}`,
              logType: 'info'
            });
          }
        } catch (e) {
          this.broadcast({
            type: 'log',
            message: `⚠️ USSD mapping failed for ${devId}: ${e.message}`,
            logType: 'warning'
          });
        }
      }
    } catch (err) {
      this.broadcast({
        type: 'log',
        message: `❌ Failed to list devices: ${err.message}`,
        logType: 'error'
      });
    }
  }

  async initialize() {
    try {
      this.broadcast({
        type: 'log',
        message: '🚀 Initializing test environment...',
        logType: 'info'
      });

      // Step 1: Get device phone number via USSD
      this.broadcast({
        type: 'progress',
        progress: { percentage: 10, action: 'Getting device info', status: 'Running' }
      });

      const ussdResult = await getSimNumberViaUSSD(this.deviceId, null);
      this.devicePhoneNumber = ussdResult.phoneNumber;

      if (!this.devicePhoneNumber) {
        throw new Error('Failed to retrieve phone number from device');
      }

      this.broadcast({
        type: 'log',
        message: `✅ Device phone number: ${this.devicePhoneNumber}`,
        logType: 'success'
      });

      // Verification after Excel upload
      this.broadcast({
        type: 'progress',
        progress: { percentage: 15, action: 'Verifying numbers', status: 'Excel' }
      });

      // Step 2: Parse Excel file
      this.broadcast({
        type: 'progress',
        progress: { percentage: 20, action: 'Reading test data', status: 'Parsing Excel' }
      });

      this.testData = parseExcelTestData(this.excelFilePath);

      this.broadcast({
        type: 'log',
        message: `✅ Test data loaded: ${this.testData.calling.length} calling tests, ${this.testData.sms.length} SMS tests`,
        logType: 'success'
      });

      // Verify A-Party and B-Party numbers
      let aMismatch = 0;
      const bSet = new Set();
      for (const t of this.testData.calling) {
        if (t.aPartyNumber && t.aPartyNumber !== this.devicePhoneNumber) aMismatch++;
        if (t.bPartyNumber) bSet.add(t.bPartyNumber);
      }
      for (const t of this.testData.sms) {
        if (t.aPartyNumber && t.aPartyNumber !== this.devicePhoneNumber) aMismatch++;
        if (t.bPartyNumber) bSet.add(t.bPartyNumber);
      }
      const missingB = Array.from(bSet).filter(n => !this.phoneToDeviceId[n]);
      this.broadcast({
        type: 'log',
        message: `🔎 Verification: A mismatches=${aMismatch}, B missing devices=${missingB.length}`,
        logType: missingB.length ? 'warning' : 'info'
      });
      if (missingB.length) {
        this.broadcast({
          type: 'log',
          message: `❗ Missing B-Party device mapping for: ${missingB.join(', ')}`,
          logType: 'warning'
        });
      }

      return true;
    } catch (error) {
      this.broadcast({
        type: 'log',
        message: `❌ Initialization failed: ${error.message}`,
        logType: 'error'
      });
      throw error;
    }
  }

  async runSIMAutoLatchTests() {
    const aPartyDevice = this.deviceId;
    const aPartyNumber = this.devicePhoneNumber;
    // Try to find matching B-Party from Excel by number mapping
    const allNumbers = new Set([
      ...this.testData.calling.map(t => t.bPartyNumber).filter(Boolean),
      ...this.testData.sms.map(t => t.bPartyNumber).filter(Boolean)
    ]);
    let bPartyNumber = null;
    let bPartyDevice = null;
    for (const num of allNumbers) {
      if (this.phoneToDeviceId[num]) {
        bPartyNumber = num;
        bPartyDevice = this.phoneToDeviceId[num];
        break;
      }
    }

    const cmd = `cd .. && mvn clean test -Dtest=SIMAutoLatchTestSuite ` +
      `-DaPartyDevice=${aPartyDevice} -DaPartyNumber=${aPartyNumber} ` +
      `${bPartyDevice ? `-DbPartyDevice=${bPartyDevice} -DbPartyNumber=${bPartyNumber}` : ``}`;

    try {
      await execPromise(cmd);
      this.broadcast({
        type: 'log',
        message: `✅ SIM auto-latch suite completed`,
        logType: 'success'
      });
    } catch (error) {
      this.broadcast({
        type: 'log',
        message: `❌ SIM auto-latch failed: ${error.message}`,
        logType: 'error'
      });
    }
  }

  async runTests(testType) {
    try {
      await this.initialize();

      switch (testType) {
        case 'calling':
          await this.runCallingTests();
          break;
        case 'sms':
          await this.runSMSTests();
          break;
        case 'data':
          await this.runDataTests();
          break;
        case 'sim-auto-latch':
          await this.runSIMAutoLatchTests();
          break;
        case 'calling-sms':
          await this.runCallingTests();
          await this.runSMSTests();
          break;
        case 'all':
          await this.runCallingTests();
          await this.runSMSTests();
          await this.runDataTests();
          break;
        default:
          throw new Error('Invalid test type');
      }

      this.broadcast({
        type: 'complete',
        success: true,
        message: 'All tests completed successfully'
      });

      return true;
    } catch (error) {
      this.broadcast({
        type: 'complete',
        success: false,
        message: error.message
      });
      throw error;
    }
  }

  async runCallingTests() {
    const tests = this.testData.calling;
    const total = tests.length;

    for (let i = 0; i < total; i++) {
      const test = tests[i];
      const progress = Math.round(((i + 1) / total) * 100);

      this.broadcast({
        type: 'progress',
        testType: 'calling',
        progress: {
          percentage: progress,
          action: 'CALLING',
          status: `Test ${i + 1}/${total}`,
          number: test.bPartyNumber,
          duration: test.duration
        }
      });

      const aPartyDevice = this.deviceId;
      const aPartyNumber = this.devicePhoneNumber;
      const bPartyNumber = test.bPartyNumber;
      const bPartyDevice = this.phoneToDeviceId[bPartyNumber] || '';

      // Build Maven command with both parties
      const cmd = `cd .. && mvn clean test -Dtest=CallingTest ` +
        `-DaPartyDevice=${aPartyDevice} -DaPartyNumber=${aPartyNumber} ` +
        `${bPartyDevice ? `-DbPartyDevice=${bPartyDevice} -DbPartyNumber=${bPartyNumber} ` : ''}` +
        `-DcallDuration=${test.duration} ` +
        `-DnetworkType=${test.preferredNetwork} ` +
        `-DvolteEnabled=${test.volteSupported}`;

      try {
        const { stdout, stderr } = await execPromise(cmd);
        this.broadcast({
          type: 'log',
          message: `✅ Calling test ${i + 1} completed: ${test.bPartyNumber}`,
          logType: 'success'
        });
      } catch (error) {
        this.broadcast({
          type: 'log',
          message: `❌ Calling test ${i + 1} failed: ${error.message}`,
          logType: 'error'
        });
      }
    }
  }

  async runSMSTests() {
    const tests = this.testData.sms;
    const total = tests.length;

    for (let i = 0; i < total; i++) {
      const test = tests[i];
      const progress = Math.round(((i + 1) / total) * 100);

      this.broadcast({
        type: 'progress',
        testType: 'sms',
        progress: {
          percentage: progress,
          action: 'SENDING SMS',
          status: `Test ${i + 1}/${total}`,
          number: test.bPartyNumber || test.groupName
        }
      });

      const aPartyDevice = this.deviceId;
      const aPartyNumber = this.devicePhoneNumber;
      const bPartyNumber = test.bPartyNumber;
      const bPartyDevice = bPartyNumber ? (this.phoneToDeviceId[bPartyNumber] || '') : '';

      let cmd = `cd .. && mvn clean test -Dtest=SMSTest ` +
        `-DaPartyDevice=${aPartyDevice} -DaPartyNumber=${aPartyNumber} ` +
        `${bPartyDevice ? `-DbPartyDevice=${bPartyDevice} -DbPartyNumber=${bPartyNumber} ` : ''}`;

      if (test.testType === 'Individual') {
        cmd += ` -DbPartyNumber=${test.bPartyNumber} -DmessageText="${test.message}"`;
      } else {
        cmd += ` -DgroupName="${test.groupName}" -DmessageText="${test.message}"`;
      }

      try {
        const { stdout, stderr } = await execPromise(cmd);
        this.broadcast({
          type: 'log',
          message: `✅ SMS test ${i + 1} completed`,
          logType: 'success'
        });
      } catch (error) {
        this.broadcast({
          type: 'log',
          message: `❌ SMS test ${i + 1} failed: ${error.message}`,
          logType: 'error'
        });
      }
    }
  }

  async runDataTests() {
    const tests = this.testData.dataUsage;
    const total = tests.length;

    for (let i = 0; i < total; i++) {
      const test = tests[i];
      const progress = Math.round(((i + 1) / total) * 100);

      this.broadcast({
        type: 'progress',
        testType: 'data',
        progress: {
          percentage: progress,
          action: 'DATA USAGE',
          status: test.scenario,
          downloadedMB: 0,
          elapsedSec: 0,
          totalSec: test.durationMin * 60
        }
      });

      const cmd = `cd .. && mvn clean test -Dtest=DataUsageTest ` +
        `-DdeviceId=${this.deviceId} ` +
        `-DaPartyNumber=${this.devicePhoneNumber} ` +
        `-DtargetDataGB=${test.targetDataGB} ` +
        `-DdurationMin=${test.durationMin}`;

      try {
        const { stdout, stderr } = await execPromise(cmd);
        this.broadcast({
          type: 'log',
          message: `✅ Data test ${i + 1} completed`,
          logType: 'success'
        });
      } catch (error) {
        this.broadcast({
          type: 'log',
          message: `❌ Data test ${i + 1} failed: ${error.message}`,
          logType: 'error'
        });
      }
    }
  }
}

module.exports = { TestOrchestrator };
