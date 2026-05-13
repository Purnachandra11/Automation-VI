const express = require('express');
const { exec } = require('child_process');
const path = require('path');

const nodemailer = require('nodemailer');
const multer = require('multer');
const crypto = require('crypto');
const app = express();
const fs = require("fs");
const PORT = 5174;
const SERVER_IP = '188.208.141.113';
const { spawn } = require('child_process');
// require('./config.js')
const WebSocket = require('ws');
const http = require('http');
// Create HTTP server
const server = http.createServer(app);
const { TestOrchestrator } = require('./testOrchestrator');
const { getSimNumberViaUSSD } = require('./ussdHandler');


// Create WebSocket server
const wss = new WebSocket.Server({ server });

// Store connected clients
const clients = new Set();
const phoneDeviceMap = new Map();

const cors = require('cors');
app.use(cors());
app.use(express.json());
app.use(express.static(__dirname));

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    // Save to src/test/resources/ directory
    const resourcesPath = path.join(__dirname, '..', 'src', 'test', 'resources');

    // Ensure directory exists
    if (!fs.existsSync(resourcesPath)) {
      fs.mkdirSync(resourcesPath, { recursive: true });
    }

    cb(null, resourcesPath);
  },
  filename: function (req, file, cb) {
    // Keep original filename (will overwrite if exists)
    cb(null, "contacts.xlsx");
  }
});

const upload = multer({
  storage: storage,
  fileFilter: function (req, file, cb) {
    const name = (file.originalname || '').toLowerCase();
    const allowed = name.endsWith('.xlsx') || name.endsWith('.xls');
    if (allowed) return cb(null, true);
    cb(new Error('Only Excel files (.xlsx, .xls) are allowed!'), false);
  },
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB limit
  }
});

// Return JSON on upload errors
app.use((err, req, res, next) => {
  if (err && err.message && err.message.includes('Only Excel files')) {
    return res.status(400).json({ success: false, message: err.message });
  }
  if (err) {
    return res.status(500).json({ success: false, message: err.message || 'Internal Server Error' });
  }
  next();
});

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'login.html'));
});

app.get('/dashboard', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.get('/device-progress.html', (req, res) => {
  res.sendFile(path.join(__dirname, 'device-progress.html'));
});

function runCommand(cmd) {
  return new Promise((resolve) => {
    exec(cmd, (err, stdout, stderr) => {
      resolve({
        error: err || null,
        stdout,
        stderr,
        code: err?.code ?? 0
      });
    });
  });
}



const FILES_DIR = "../test-output/comprehensive-reports";

// AND filters by today's date
app.post("/api/search-files", (req, res) => {
  const { name } = req.body;

  if (!name) {
    return res.status(400).json({ message: "Query parameter is required" });
  }

  fs.readdir(FILES_DIR, (err, files) => {
    if (err) {
      console.error("Error reading directory:", err);
      return res.status(500).json({ error: "Unable to read directory" });
    }

    // Normalize the search name - handle both formats
    const searchName = name.trim();
    const searchPatterns = [];

    // If it's a 91-number, also search without 91 prefix
    if (searchName.startsWith('91') && searchName.length > 2) {
      searchPatterns.push(searchName); // Original with 91
      searchPatterns.push(searchName.substring(2)); // Without 91
    }
    // If it's a normal number (without 91), also search with 91 prefix
    else if (!searchName.startsWith('91') && searchName.length >= 10) {
      searchPatterns.push(searchName); // Original without 91
      searchPatterns.push('91' + searchName); // With 91 prefix
    }
    else {
      searchPatterns.push(searchName); // Just use as-is
    }

    console.log(`Searching for patterns: ${searchPatterns.join(', ')}`);

    // Get today's date in YYYY-MM-DD format
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0]; // Format: YYYY-MM-DD

    // Also check for other date formats that might appear in filenames
    const todayFormats = [
      todayStr, // YYYY-MM-DD
      todayStr.replace(/-/g, ''), // YYYYMMDD
      `${today.getDate().toString().padStart(2, '0')}-${(today.getMonth() + 1).toString().padStart(2, '0')}-${today.getFullYear()}`, // DD-MM-YYYY
      `${(today.getMonth() + 1).toString().padStart(2, '0')}-${today.getDate().toString().padStart(2, '0')}-${today.getFullYear()}`, // MM-DD-YYYY
    ];

    console.log(`Today's date filters: ${todayFormats.join(', ')}`);

    // Filter files that match any of the search patterns AND today's date
    const matchedFiles = files.filter(file => {
      const fileNameLower = file.toLowerCase();

      // First check if file matches search patterns
      const matchesSearch = searchPatterns.some(pattern =>
        fileNameLower.includes(pattern.toLowerCase())
      );

      // Then check if file contains today's date
      const matchesDate = todayFormats.some(dateFormat =>
        fileNameLower.includes(dateFormat)
      );

      return matchesSearch && matchesDate;
    });

    // If no files found with today's date, fall back to all matching files
    let finalFiles = matchedFiles;
    if (matchedFiles.length === 0) {
      console.log(`No files found for today's date, showing all matching files`);
      finalFiles = files.filter(file => {
        const fileNameLower = file.toLowerCase();
        return searchPatterns.some(pattern =>
          fileNameLower.includes(pattern.toLowerCase())
        );
      });
    }

    // Get file stats for more accurate date filtering
    const filesWithStats = [];
    finalFiles.forEach(file => {
      try {
        const filePath = path.join(FILES_DIR, file);
        const stats = fs.statSync(filePath);
        const fileDate = new Date(stats.mtime); // Use modification time

        // Check if file was created/modified today
        const isToday = fileDate.toDateString() === today.toDateString();

        filesWithStats.push({
          file,
          path: filePath,
          created: fileDate,
          isToday: isToday
        });
      } catch (error) {
        console.error(`Error getting stats for ${file}:`, error);
        filesWithStats.push({
          file,
          path: path.join(FILES_DIR, file),
          created: null,
          isToday: false
        });
      }
    });

    // Sort by creation date (newest first)
    filesWithStats.sort((a, b) => {
      if (!a.created) return 1;
      if (!b.created) return -1;
      return b.created - a.created;
    });

    console.log(`Found ${filesWithStats.length} files for search "${name}"`);

    res.json({
      success: true,
      count: filesWithStats.length,
      files: filesWithStats,
      today: todayStr,
      searchPatterns: searchPatterns,
      message: matchedFiles.length === finalFiles.length ?
        "Showing today's reports only" :
        "No reports found for today, showing all available reports"
    });
  });
});

// Enhanced download file endpoint
app.post("/api/download-file", (req, res) => {
  try {
    const fileName = req.body.name;

    if (!fileName) {
      return res.status(400).json({ error: "File name is required" });
    }

    const filePath = path.join(FILES_DIR, fileName);

    // Check if file exists
    if (!fs.existsSync(filePath)) {
      // Try to find the file with different naming patterns
      const normalizedName = fileName.trim();
      let actualFileName = null;

      // Read directory to find matching file
      const files = fs.readdirSync(FILES_DIR);

      // Try different naming patterns
      if (normalizedName.startsWith('91') && normalizedName.length > 2) {
        // Try without 91 prefix
        const without91 = normalizedName.substring(2);
        actualFileName = files.find(file =>
          file.toLowerCase().includes(without91.toLowerCase())
        );
      } else if (!normalizedName.startsWith('91') && normalizedName.length >= 10) {
        // Try with 91 prefix
        const with91 = '91' + normalizedName;
        actualFileName = files.find(file =>
          file.toLowerCase().includes(with91.toLowerCase())
        );
      }

      // If still not found, try direct case-insensitive match
      if (!actualFileName) {
        actualFileName = files.find(file =>
          file.toLowerCase().includes(normalizedName.toLowerCase())
        );
      }

      if (actualFileName) {
        const actualFilePath = path.join(FILES_DIR, actualFileName);
        console.log(`Found file: ${actualFileName} for request: ${fileName}`);
        return res.download(actualFilePath, actualFileName, (err) => {
          if (err) {
            console.error("Download error:", err);
            res.status(500).json({ error: "Error downloading file" });
          }
        });
      }

      return res.status(404).json({
        success: false,
        error: "File not found",
        message: `File "${fileName}" not found in ${FILES_DIR}`
      });
    }

    // File exists, send it
    console.log(`Downloading file: ${filePath}`);
    res.download(filePath, fileName, (err) => {
      if (err) {
        console.error("Download error:", err);
        res.status(500).json({
          success: false,
          error: "Error downloading file"
        });
      }
    });

  } catch (error) {
    console.error("Error:", error);
    return res.status(500).json({
      success: false,
      error: "Internal Server Error",
      message: error.message
    });
  }
});

// Add a helper endpoint to list all files (for debugging)
app.get("/api/list-all-files", (req, res) => {
  try {
    fs.readdir(FILES_DIR, (err, files) => {
      if (err) {
        console.error("Error reading directory:", err);
        return res.status(500).json({
          success: false,
          error: "Unable to read directory"
        });
      }

      // Get today's date
      const today = new Date();
      const todayStr = today.toDateString();

      // Get file stats and categorize
      const filesWithStats = [];
      const todayFiles = [];
      const olderFiles = [];

      files.forEach(file => {
        try {
          const filePath = path.join(FILES_DIR, file);
          const stats = fs.statSync(filePath);
          const fileDate = new Date(stats.mtime);
          const isToday = fileDate.toDateString() === todayStr;

          const fileInfo = {
            file,
            path: filePath,
            created: fileDate,
            isToday: isToday
          };

          filesWithStats.push(fileInfo);

          if (isToday) {
            todayFiles.push(fileInfo);
          } else {
            olderFiles.push(fileInfo);
          }

        } catch (error) {
          console.error(`Error getting stats for ${file}:`, error);
        }
      });

      // Sort by date (newest first)
      filesWithStats.sort((a, b) => {
        if (!a.created) return 1;
        if (!b.created) return -1;
        return b.created - a.created;
      });

      // Group files by number patterns
      const groupedFiles = {};

      files.forEach(file => {
        // Extract numbers from filename (both 91 and non-91)
        const matches = file.match(/(91\d{10}|\d{10})/g);
        if (matches && matches.length > 0) {
          const key = matches[0]; // Use first matched number as key
          if (!groupedFiles[key]) {
            groupedFiles[key] = [];
          }
          groupedFiles[key].push(file);
        }
      });

      res.json({
        success: true,
        total: files.length,
        today: todayFiles.length,
        files: filesWithStats,
        todayFiles: todayFiles,
        olderFiles: olderFiles,
        groupedByNumber: groupedFiles,
        dateFilter: todayStr
      });
    });
  } catch (error) {
    console.error("Error:", error);
    res.status(500).json({
      success: false,
      error: "Internal Server Error"
    });
  }
});

// Modify the test execution endpoint to pass deviceId to parseAndBroadcastLogs:
app.post("/api/test-command", upload.single('file'), async (req, res) => {
  try {
    let cmd = '';
    const deviceId = req.body.deviceId;
    const aPartyNumber = req.body.phone;
    console.log("nainji");
    let raw = fs.readFileSync("../automation/controller/device-sim-map.json", "utf8");
    raw = JSON.parse(raw)
    console.log(raw);
    let aplha = ['b', 'c', 'd']
    let deviceCommand = `-DaPartyDevice=${deviceId} -DaPartyNumber=${aPartyNumber}`
    let temp = raw.filter((ele) => { return ele.id != deviceId }).map((e, index) => { return ` -D${aplha[index]}PartyDevice=${e.id} -D${aplha[index]}PartyNumber=${e.sim}` })

    deviceCommand += temp;



    switch (req.body.testType) {
      case 'sms':
        cmd = "cd .. && mvn clean test -Dtest=SMSTest " + deviceCommand
        break;

      case 'calling':
        cmd = "cd .. && mvn clean test -Dtest=CallingTest " + deviceCommand

        break;

      case 'simToolkit':
        cmd = `cd .. && mvn clean test -Dtest=SIMToolkitCaptureTest -Dudid=${req.body.deviceId} -DaPartyNumber=${req.body.phone} `;
        break;


      case 'calling-sms':
        cmd = `cd .. && mvn clean test -Dtest=${"CallingTest,SMSTest"} -DdeviceId=${req.body.deviceId} -DaPartyNumber=${req.body.phone}`
        break;
      case 'incomingsms':
        cmd = `cd .. && mvn clean test -Dtest=${"CallingTest,SMSTest"} -DdeviceId=${req.body.deviceId} -DaPartyNumber=${req.body.phone}`
        break;

      case 'data':
        cmd = "cd .. && mvn clean test -Dtest=DataUsageTest -DaPartyDevice=" + req.body.deviceId + " -DaPartyNumber=" + req.body.phone
        break;

      case 'latch':
        cmd = "cd .. && mvn clean test -Dtest=SIMAutoLatchTestSuite -DaPartyDevice=" + req.body.deviceId + " -DaPartyNumber=" + req.body.phone
        break;

      case 'all':
        cmd = `cd .. && mvn clean test -Dtest=${"CallingTest,SMSTest,DataUsageTest"} -DdeviceId=${req.body.deviceId} -DaPartyNumber=${req.body.phone}`
        break;

      case 'calling-data':
        cmd = `cd .. && mvn clean test -Dtest=${"CallingTest,DataUsageTest"} -DdeviceId=${req.body.deviceId} -DaPartyNumber=${req.body.phone}`
        break;

      case 'sms-data':
        cmd = `cd .. && mvn clean test -Dtest=${"SMSTest,DataUsageTest"} -DdeviceId=${req.body.deviceId} -DaPartyNumber=${req.body.phone}`
        break;


      default:
        break;
    }
    // mvn test -Dtest=SMSTest -DdeviceId=LFMVIBEMW8HUR4XK
    console.log(cmd)
    if (cmd == '') {
      return;
    }
    const result = await runCommand(
      cmd
    );

    console.log("Exit Code:", result.code);
    console.log("STDOUT:", result.stdout);
    console.log("STDERR:", result.stderr);
    console.log("Error:", result.error);

    // If exit code is 0 => success
    if (result.code === 0) {
      res.status(200).json({
        status: "success",
        exitCode: result.code,
        stdout: result.stdout.split('Appium service stopped')[1],
        stderr: result.stderr,
      });
    } else {


      res.status(500).json({
        status: "failed",
        exitCode: result.code,
        stdout: result.stdout,
        stderr: result.stderr,
        error: result.error,
      });
    }
    // if (callingFilePath) {
    //       fs.unlink(callingFilePath, (err) => {
    //         if (err) {
    //           console.error("File delete error:", err);
    //         } else {
    //           console.log("File deleted:");
    //         }
    //       });
    //     }
    //     if (smsFilePath) {
    //       fs.unlink(smsFilePath, (err) => {
    //         if (err) {
    //           console.error("File delete error:", err);
    //         } else {
    //           console.log("File deleted:");
    //         }
    //       });
    //     }

  } catch (err) {
    console.error("Error:", err);

    return res.status(500).json({
      status: "error",
      message: "Something went wrong while running the command",
      error: err.message,
    });
  }
});


app.post('/api/auth/login', (req, res) => {
  const { email, password } = req.body;
  res.json({ success: true, email: email });
  return;
  if (!email || !password) {
    return res.status(400).json({ success: false, message: 'Email and password are required' });
  }

  const user = allowedUsers.find(
    (u) => u.email.toLowerCase() === email.toLowerCase() && u.password === password
  );

  if (!user) {
    return res.status(401).json({ success: false, message: 'Invalid credentials' });
  }

  const token = createSession(user.email);
  res.json({ success: true, token, email: user.email });
});

app.post('/api/auth/logout', (req, res) => {
  const { token } = req.body;
  if (token) {
    activeSessions.delete(token);
  }
  res.json({ success: true });
});

app.get('/api/auth/validate', (req, res) => {
  const token = req.query.token || req.headers['x-session-token'];
  const session = validateSession(token);
  if (!session) {
    return res.status(401).json({ success: false, message: 'Session expired. Please login again.' });
  }
  res.json({ success: true, email: session.email });
});

let adbStatus = 'stopped';
let appiumStatus = 'stopped';
let connectedDevices = new Map();

// ========== Simple Auth ==========

const allowedUsers = [
  { email: 'nainji@gmail.com', password: 'Password@123' },
  { email: 'Chandra@gmail.com', password: 'Password@123' },
  { email: 'kalidindi.chandra@qdegrees.org', password: 'Password@123' }
];

const activeSessions = new Map();
const SESSION_TIMEOUT = 1000 * 60 * 60 * 8; // 8 hours

function createSession(email) {
  const token = crypto.randomBytes(32).toString('hex');
  activeSessions.set(token, { email, createdAt: Date.now() });
  return token;
}

function validateSession(token) {
  if (!token) return null;
  const session = activeSessions.get(token);
  if (!session) return null;

  if (Date.now() - session.createdAt > SESSION_TIMEOUT) {
    activeSessions.delete(token);
    return null;
  }
  return session;
}

// ========== Helper Functions ==========

// Helper function to execute commands with promise
function executeCommand(command) {
  return new Promise((resolve, reject) => {
    exec(command, (error, stdout, stderr) => {
      resolve({
        success: !error,
        output: stdout || stderr,
        error: error
      });
    });
  });
}

// Improved pairing function using spawn
function pairDevice(ip, port, code) {
  return new Promise((resolve) => {
    const pairProcess = spawn('adb', ['pair', `${ip}:${port}`], {
      stdio: ['pipe', 'pipe', 'pipe']
    });

    let output = '';

    pairProcess.stdout.on('data', (data) => {
      output += data.toString();
      console.log('Pairing stdout:', data.toString());

      // If we see the pairing prompt, send the code
      if (data.toString().includes('Enter pairing code:')) {
        pairProcess.stdin.write(code + '\n');
      }
    });

    pairProcess.stderr.on('data', (data) => {
      output += data.toString();
      console.log('Pairing stderr:', data.toString());
    });

    pairProcess.on('close', (code) => {
      console.log('Pairing process exited with code:', code);
      console.log('Final pairing output:', output);

      const success = output.includes('Successfully paired');
      resolve(success);
    });

    pairProcess.on('error', (error) => {
      console.error('Pairing process error:', error);
      resolve(false);
    });

    // Timeout after 10 seconds
    setTimeout(() => {
      pairProcess.kill();
      console.log('Pairing timeout');
      resolve(false);
    }, 10000);
  });
}

function extractReportPath(output) {
  const match = output.match(/Report generated: (.+\.zip)/);
  return match ? match[1] : '';
}

function parseTestCount(output) {
  const match = output.match(/Tests run: (\d+)/);
  return match ? parseInt(match[1]) : 0;
}

function parsePassedCount(output) {
  const match = output.match(/Tests run: (\d+), Failures: (\d+)/);
  if (match) {
    return parseInt(match[1]) - parseInt(match[2]);
  }
  return 0;
}

function getTestSuite(testType) {
  const mapping = {
    'calling': 'testng-calling.xml',
    'sms': 'testng-sms.xml',
    'data': 'testng-data-usage.xml',
    'calling-sms': 'testng-both-calling-and-sms.xml',
    'comprehensive': 'testng-all.xml',
    'multi-device': 'testng-all.xml' // Multi-device uses comprehensive test suite
  };
  return mapping[testType] || 'testng-all.xml';
}

function getTestClass(testType) {
  const mapping = {
    'calling': 'CallingTest',
    'sms': 'SMSTest',
    'data': 'DataUsageTest',
    'calling-sms': 'CallingTest,SMSTest',
    'comprehensive': 'ComprehensiveTelecomTest',
    'multi-device': 'ComprehensiveTelecomTest' // Multi-device uses comprehensive test class
  };
  return mapping[testType] || 'ComprehensiveTelecomTest';
}

function checkNetworkMatch(current, target) {
  current = current.toUpperCase();
  target = target.toUpperCase();

  if (target === '5G') return current.includes('NR') || current.includes('5G');
  if (target === '4G') return current.includes('LTE') || current.includes('4G');
  if (target === '3G') return current.includes('HSPA') || current.includes('UMTS') || current.includes('3G');
  if (target === '2G') return current.includes('EDGE') || current.includes('GPRS') || current.includes('2G');

  return true;
}

// ========== ADB/Appium Control ==========

app.post('/api/adb/start', (req, res) => {
  exec('adb start-server', (error) => {
    if (error) {
      return res.json({ success: false, message: error.message });
    }
    adbStatus = 'running';
    res.json({ success: true, message: 'ADB server started' });
  });
});

app.post('/api/adb/stop', (req, res) => {
  exec('adb kill-server', (error) => {
    if (error) {
      return res.json({ success: false, message: error.message });
    }
    adbStatus = 'stopped';
    res.json({ success: true, message: 'ADB server stopped' });
  });
});

// ========== FIXED Appium Functions ==========

app.post('/api/appium/start', (req, res) => {
  console.log('🚀 Starting Appium server...');

  // First, stop any existing Appium server using your batch file logic
  exec('netstat -ano | findstr :4723', (error, stdout) => {
    if (stdout && stdout.includes('LISTENING')) {
      console.log('🛑 Stopping existing Appium server...');

      // Kill processes using port 4723 (your batch file logic)
      const lines = stdout.split('\n');
      lines.forEach(line => {
        const match = line.match(/:4723.*LISTENING\s+(\d+)/);
        if (match && match[1]) {
          const pid = match[1];
          exec(`taskkill /F /PID ${pid}`, (killError) => {
            console.log(killError ? `Failed to kill ${pid}` : `Killed process ${pid}`);
          });
        }
      });

      // Wait a moment for processes to be killed
      setTimeout(() => startFreshAppium(res), 2000);
    } else {
      startFreshAppium(res);
    }
  });
});

function startFreshAppium(res) {
  // Use your working batch file command: start Appium in new command window that stays open
  exec('start cmd /k "appium --address 127.0.0.1 --port 4723 --allow-insecure uiautomator2:adb_shell"', (error) => {
    if (error) {
      console.error('❌ Appium start error:', error);
      appiumStatus = 'stopped';
      return res.json({
        success: false,
        message: `Failed to start Appium: ${error.message}`
      });
    }

    console.log('⏳ Waiting for Appium to start...');

    // Check if Appium started successfully
    const checkAppium = (attempt = 1) => {
      setTimeout(() => {
        exec('netstat -ano | findstr :4723', (checkError, checkStdout) => {
          if (checkStdout && checkStdout.includes('LISTENING')) {
            console.log('✅ Appium server started successfully');
            appiumStatus = 'running';
            res.json({
              success: true,
              message: 'Appium server started successfully'
            });
          } else if (attempt < 5) {
            // Try again
            console.log(`🔄 Appium check attempt ${attempt} failed, retrying...`);
            checkAppium(attempt + 1);
          } else {
            // Final attempt failed
            console.error('❌ Appium failed to start after multiple attempts');
            appiumStatus = 'stopped';
            res.json({
              success: false,
              message: 'Appium failed to start - port 4723 not listening after 10 seconds'
            });
          }
        });
      }, attempt * 2000); // 2s, 4s, 6s, 8s, 10s
    };

    checkAppium(1);
  });
}

app.post('/api/appium/stop', (req, res) => {
  console.log('🛑 Stopping Appium server...');

  let killedProcesses = 0;

  // Kill processes using port 4723
  exec('netstat -ano | findstr :4723', (error, stdout) => {
    if (stdout) {
      const lines = stdout.split('\n');
      lines.forEach(line => {
        const match = line.match(/:4723.*LISTENING\s+(\d+)/);
        if (match && match[1]) {
          const pid = match[1];
          exec(`taskkill /F /PID ${pid}`, (killError) => {
            if (!killError) killedProcesses++;
          });
        }
      });
    }

    // Also kill any node processes with Appium
    exec('taskkill /F /IM node.exe /FI "WINDOWTITLE eq appium*"', (error2) => {
      appiumStatus = 'stopped';
      console.log(`✅ Appium server stopped. Killed ${killedProcesses} process(es)`);
      res.json({
        success: true,
        message: `Appium server stopped. Killed ${killedProcesses} process(es)`
      });
    });
  });
});

// ========== Improved Server Status Check ==========

app.get('/api/servers/status', (req, res) => {
  // Check ADB status
  exec('adb devices', (adbError, adbStdout) => {
    const adbRunning = !adbError && adbStdout && adbStdout.includes('device');

    // Check Appium status by port
    exec('netstat -ano | findstr :4723', (appiumError, appiumStdout) => {
      const appiumRunning = appiumStdout && appiumStdout.includes('LISTENING');

      // Update global status
      adbStatus = adbRunning ? 'running' : 'stopped';
      appiumStatus = appiumRunning ? 'running' : 'stopped';

      res.json({
        success: true,
        adbStatus: adbStatus,
        appiumStatus: appiumStatus
      });
    });
  });
});

// ========== Device Connection ==========

app.post('/api/device/connect-usb', (req, res) => {
  exec('adb devices', (error, stdout) => {
    if (error) {
      return res.json({ success: false, message: error.message });
    }

    const lines = stdout.split('\n');
    const deviceLine = lines.find(line => line.includes('\tdevice'));

    if (deviceLine) {
      const deviceId = deviceLine.split('\t')[0];

      exec(`adb -s "${deviceId}" shell getprop ro.product.model`, async (err, model) => {
        exec(`adb -s "${deviceId}" shell getprop ro.build.version.release`, async (err2, version) => {
          const device = {
            id: deviceId,
            model: model ? model.trim() : 'Unknown Model',
            androidVersion: version ? version.trim() : 'Unknown Version',
            connectionType: 'USB'
          };

          try {
            const { phoneNumber, balance, validityDate, validityIsFuture } = await getSimNumberViaUSSD(deviceId, null);
            if (phoneNumber) {
              device.phoneNumber = phoneNumber;
              device.balance = balance || null;
              device.validityDate = validityDate || null;
              device.validityIsFuture = validityIsFuture === true;
              phoneDeviceMap.set(phoneNumber, deviceId);
            }
          } catch (_) { }

          connectedDevices.set(deviceId, device);

          res.json({
            success: true,
            device,
            message: 'USB device connected successfully'
          });
        });
      });
    } else {
      res.json({ success: false, message: 'No USB device found' });
    }
  });
});

// Wireless connection with VPN support for NEW devices
app.post('/api/device/connect-wireless', async (req, res) => {
  const { ip, port, code } = req.body;

  try {
    // Step 1: Restart ADB server
    await executeCommand('adb kill-server');
    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait for server to stop
    await executeCommand('adb start-server');
    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait for server to start

    // Step 2: Pair with device using spawn for better input handling
    const pairSuccess = await pairDevice(ip, port, code);
    if (!pairSuccess) {
      return res.json({ success: false, message: 'Pairing failed. Please check the pairing code.' });
    }

    // Step 3: Connect to device
    const connectResult = await executeCommand(`adb connect ${ip}:${port}`);
    if (!connectResult.success || !connectResult.output.includes('connected')) {
      return res.json({ success: false, message: 'Paired Successfully, Need connected the Device!!' });
    }

    // Step 4: Get device info
    const deviceId = `${ip}:${port}`;
    const modelResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.product.model`);
    const versionResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.build.version.release`);

    const device = {
      id: deviceId,
      model: modelResult.success ? modelResult.output.trim() : 'Unknown Model',
      androidVersion: versionResult.success ? versionResult.output.trim() : 'Unknown Version',
      connectionType: 'WIRELESS_NEW'
    };
    try {
      const { phoneNumber, balance, validityDate, validityIsFuture } = await getSimNumberViaUSSD(deviceId, null);
      if (phoneNumber) {
        device.phoneNumber = phoneNumber;
        device.balance = balance || null;
        device.validityDate = validityDate || null;
        device.validityIsFuture = validityIsFuture === true;
        phoneDeviceMap.set(phoneNumber, deviceId);
      }
    } catch (_) { }

    connectedDevices.set(deviceId, device);

    res.json({
      success: true,
      device,
      message: 'New wireless device paired and connected successfully'
    });

  } catch (error) {
    console.error('New wireless connection error:', error);
    res.json({
      success: false,
      message: `Connection error: ${error.message}`
    });
  }
});

// Connect to existing paired wireless device
app.post('/api/device/connect-existing-wireless', async (req, res) => {
  const { ip, port } = req.body;
  console.log(`Connecting existing wireless device: ${ip}:${port}`);

  try {
    // Step 1: Connect directly (no pairing needed for existing devices)
    const connectResult = await executeCommand(`adb connect ${ip}:${port}`);

    if (!connectResult.success || !connectResult.output.includes('connected')) {
      console.log('Connection failed:', connectResult.output);
      return res.json({
        success: false,
        message: 'Connection failed. Device may need to be paired again.'
      });
    }

    console.log('Connection successful:', connectResult.output);

    // Step 2: Get device information
    const deviceId = `${ip}:${port}`;

    const modelResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.product.model`);
    const versionResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.build.version.release`);

    const device = {
      id: deviceId,
      model: modelResult.success ? modelResult.output.trim() : 'Unknown Model',
      androidVersion: versionResult.success ? versionResult.output.trim() : 'Unknown Version',
      connectionType: 'WIRELESS_EXISTING'
    };

    connectedDevices.set(deviceId, device);

    res.json({
      success: true,
      device,
      message: 'Existing wireless device connected successfully'
    });

  } catch (error) {
    console.error('Existing wireless connection error:', error);
    res.json({
      success: false,
      message: `Connection error: ${error.message}`
    });
  }
});

// Get list of connected devices (including wireless/VPN devices) - FIXED VERSION
app.get('/api/device/list', async (req, res) => {
  try {
    const result = await executeCommand('adb devices');
    const devices = [];

    console.log('Raw ADB output:', result.output);

    const lines = result.output.split('\n');

    for (const line of lines) {

      const cleaned = line.trim();  // <-- VERY IMPORTANT

      const match = cleaned.match(/^([^\s]+)\s+(device|connected|unauthorized|offline)$/i);

      if (match) {
        const deviceId = match[1];

        // Read device info
        const modelResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.product.model`);
        const versionResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.build.version.release`);

        devices.push({
          id: deviceId,
          model: modelResult.output.trim(),
          androidVersion: versionResult.output.trim(),
          connectionType: deviceId.includes(':') ? 'WIRELESS' : 'USB'
        });
      }
    }

    console.log('Final devices list:', devices);
    res.json({ success: true, devices });

  } catch (error) {
    console.error('Error in device list endpoint:', error);
    res.json({
      success: false,
      message: `Failed to get devices: ${error.message}`,
      devices: []
    });
  }
});


// Connect using already-connected device ID - FIXED VERSION
app.post('/api/device/select-device', async (req, res) => {
  const { deviceId } = req.body;

  console.log('Selecting device:', deviceId);

  try {
    // Verify device is still connected with improved parsing
    const devicesResult = await executeCommand('adb devices');
    let deviceFound = false;

    const lines = devicesResult.output.split('\n');
    for (const line of lines) {
      const match = line.match(/^([^\s\t]+)[\s\t]+device$/);
      if (match && match[1].trim() === deviceId) {
        deviceFound = true;
        break;
      }
    }

    if (!deviceFound) {
      return res.json({
        success: false,
        message: 'Device no longer connected or not found'
      });
    }

    // Get device info with proper error handling
    let model = 'Unknown Model';
    let version = 'Unknown Version';

    try {
      const modelResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.product.model`);
      if (modelResult.success) {
        model = modelResult.output.trim();
      }
    } catch (modelError) {
      console.error('Error getting model:', modelError);
    }

    try {
      const versionResult = await executeCommand(`adb -s "${deviceId}" shell getprop ro.build.version.release`);
      if (versionResult.success) {
        version = versionResult.output.trim();
      }
    } catch (versionError) {
      console.error('Error getting version:', versionError);
    }

    // Determine connection type
    let connectionType = 'USB';
    if (deviceId.includes('._adb-tls-connect._tcp')) {
      connectionType = 'WIRELESS_VPN';
    } else if (deviceId.includes(':')) {
      connectionType = 'WIRELESS';
    }

    const device = {
      id: deviceId,
      model: model,
      androidVersion: version,
      connectionType: connectionType
    };

    connectedDevices.set(deviceId, device);

    console.log('Device selected successfully:', device);
    res.json({
      success: true,
      device,
      message: 'Device selected successfully'
    });

  } catch (error) {
    console.error('Error selecting device:', error);
    res.json({
      success: false,
      message: `Error selecting device: ${error.message}`
    });
  }
});

// ========== Network Configuration ==========

app.post('/api/network/configure', (req, res) => {
  const { deviceId, networkType, volteStatus } = req.body;

  console.log(`Configuring network: ${networkType}, VoLTE: ${volteStatus}`);

  const testClass = 'NetworkConfigTest';
  const command = `mvn test "-Dtest=${testClass}" "-DdeviceId=${deviceId}" "-DnetworkType=${networkType}" "-DvolteStatus=${volteStatus}"`;

  exec(command, { cwd: path.join(__dirname, '..') }, (error) => {
    if (error) {
      return res.json({ success: false, message: error.message });
    }
    res.json({ success: true, message: 'Network configured successfully' });
  });
});

app.post('/api/network/validate', (req, res) => {
  const { deviceId, networkType } = req.body;

  exec(`adb -s "${deviceId}" shell getprop gsm.network.type`, (error, stdout) => {
    const currentNetwork = stdout ? stdout.trim() : 'unknown';
    const available = checkNetworkMatch(currentNetwork, networkType);

    res.json({
      success: true,
      available,
      currentNetwork,
      message: available ? `${networkType} available` : `${networkType} not available at your location`
    });
  });
});

// Get current network status
app.post('/api/network/status', (req, res) => {
  const { deviceId } = req.body;

  exec(`adb -s "${deviceId}" shell getprop gsm.network.type`, (error, networkStdout) => {
    const networkType = networkStdout ? networkStdout.trim().toLowerCase() : 'unknown';

    // Get VoLTE status (simplified - you might need a different approach)
    exec(`adb -s "${deviceId}" shell dumpsys telephony.registry | grep mImsState`, (volteError, volteStdout) => {
      let volteStatus = 'disabled';
      if (volteStdout && volteStdout.includes('REGISTERED')) {
        volteStatus = 'enabled';
      }

      res.json({
        success: true,
        networkType: networkType,
        volteStatus: volteStatus
      });
    });
  });
});

// ========== File Upload ==========

// Single file upload endpoint
app.post('/api/files/upload', upload.single('file'), (req, res) => {
  if (!req.file) {
    return res.json({ success: false, message: 'No file uploaded' });
  }

  console.log(`File uploaded: ${req.file.originalname} to ${req.file.path}`);

  res.json({
    success: true,
    message: 'File uploaded successfully',
    filename: req.file.originalname,
    path: req.file.path
  });
});

// Multiple files upload endpoint
app.post('/api/files/upload-multiple', upload.array('files', 10), (req, res) => {
  if (!req.files || req.files.length === 0) {
    return res.json({ success: false, message: 'No files uploaded' });
  }

  const uploadedFiles = req.files.map(file => ({
    filename: file.originalname,
    path: file.path
  }));

  console.log(`Uploaded ${uploadedFiles.length} file(s):`, uploadedFiles.map(f => f.filename).join(', '));

  res.json({
    success: true,
    message: `${uploadedFiles.length} file(s) uploaded successfully`,
    files: uploadedFiles
  });
});

// ========== Test Execution ==========

app.post('/api/tests/run', (req, res) => {
  const { deviceId, testType, excelFiles } = req.body;

  console.log(`Running ${testType} tests on device ${deviceId}`);
  console.log(`Using files:`, excelFiles);

  const testClass = getTestClass(testType);
  const suiteFile = getTestSuite(testType);
  const command = `mvn test "-DsuiteXmlFile=testng-xml/${suiteFile}" "-DdeviceId=${deviceId}"`;

  exec(command, { cwd: path.join(__dirname, '..') }, (error, stdout, stderr) => {
    if (error) {
      return res.json({
        success: false,
        message: error.message,
        totalTests: 0,
        passed: 0,
        failed: 0
      });
    }

    const totalTests = parseTestCount(stdout);
    const passed = parsePassedCount(stdout);
    const failed = totalTests - passed;

    res.json({
      success: true,
      message: 'Tests completed successfully',
      totalTests,
      passed,
      failed
    });
  });
});

// Test Stop endpoint
app.post('/api/tests/stop', (req, res) => {
  exec('taskkill /F /IM java.exe', (error) => {
    res.json({
      success: !error,
      message: error ? 'Failed to stop tests' : 'Tests stopped'
    });
  });
});

// ========== Multi-Device Testing ==========

app.post('/api/tests/multi-device/run', (req, res) => {
  const { devices, testType, excelFile } = req.body;

  console.log(`Running ${testType} tests on ${devices.length} devices`);

  const promises = devices.map(device => {
    return new Promise((resolve) => {
      const suiteFile = getTestSuite(testType);
      const command = `mvn test "-DsuiteXmlFile=testng-xml/${suiteFile}" "-DdeviceId=${device.id}"`;

      exec(command, { cwd: path.join(__dirname, '..') }, (error, stdout) => {
        resolve({
          deviceId: device.id,
          success: !error,
          message: error ? error.message : 'Tests completed'
        });
      });
    });
  });

  Promise.all(promises).then(results => {
    res.json({ success: true, message: 'Multi-device tests started', results });
  });
});

// ========== Final Report ==========

app.post('/api/reports/final/generate', (req, res) => {
  const { aPartyNumber } = req.body;

  console.log(`Generating final report for: ${aPartyNumber}`);

  const command = `mvn exec:java -Dexec.mainClass="com.telecom.utils.FinalReportZipGenerator" -Dexec.args="${aPartyNumber}"`;

  exec(command, { cwd: path.join(__dirname, '..') }, (error, stdout) => {
    if (error) {
      return res.json({ success: false, message: error.message });
    }

    const reportPath = extractReportPath(stdout);
    res.json({ success: true, reportPath });
  });
});

// Download report
app.get('/api/reports/final/download', (req, res) => {
  const reportPath = path.join(__dirname, '..', 'test-output', 'final-reports');

  fs.readdir(reportPath, (err, files) => {
    if (err) {
      return res.status(404).send('No reports found');
    }

    const latestZip = files.filter(f => f.endsWith('.zip')).sort().pop();

    if (latestZip) {
      res.download(path.join(reportPath, latestZip));
    } else {
      res.status(404).send('No report found');
    }
  });
});

// In server.js, update the existing endpoint:
// ================= SAMPLE FILE DOWNLOAD =================
app.get('/api/download-sample-file', (req, res) => {
  try {
    const filePath = path.join(
      __dirname,
      '..',
      'test-output',
      'Sample-file',
      'contacts.xlsx'
    );

    if (!fs.existsSync(filePath)) {
      console.error('Sample file not found:', filePath);
      return res.status(404).json({
        success: false,
        message: 'Sample Excel file not found on server'
      });
    }

    res.setHeader(
      'Content-Disposition',
      'attachment; filename="contacts.xlsx"'
    );
    res.setHeader(
      'Content-Type',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    );

    return res.download(filePath, 'contacts.xlsx');
  } catch (err) {
    console.error('Sample file download error:', err);
    res.status(500).json({
      success: false,
      message: 'Unable to download sample file'
    });
  }
});


// Email report
app.post('/api/reports/final/email', async (req, res) => {
  const { email } = req.body;

  console.log(`Sending report to: ${email}`);

  try {
    const reportPath = path.join(__dirname, '..', 'test-output', 'final-reports');
    const files = fs.readdirSync(reportPath);
    const latestZip = files.filter(f => f.endsWith('.zip')).sort().pop();

    if (!latestZip) {
      return res.json({ success: false, message: 'No report found' });
    }

    // Configure email transporter
    const transporter = nodemailer.createTransport({
      host: 'smtp-mail.outlook.com',
      port: 587,
      secure: false,
      auth: {
        user: process.env.EMAIL_USER || 'your-email@outlook.com',
        pass: process.env.EMAIL_PASS || 'your-password'
      }
    });

    const mailOptions = {
      from: process.env.EMAIL_USER || 'your-email@outlook.com',
      to: email,
      subject: 'Telecom Automation Test Report',
      text: 'Please find attached the comprehensive test report.',
      html: '<h2>Telecom Automation Test Report</h2><p>Please find attached the comprehensive test report.</p>',
      attachments: [
        {
          filename: latestZip,
          path: path.join(reportPath, latestZip)
        }
      ]
    };

    await transporter.sendMail(mailOptions);
    res.json({ success: true, message: 'Email sent successfully' });

  } catch (error) {
    console.error('Email error:', error);
    res.json({ success: false, message: error.message });
  }
});

app.get('/api/reports/latest', (req, res) => {
  const reportPath = path.join(__dirname, '..', 'test-output', 'comprehensive-reports');

  fs.readdir(reportPath, (err, files) => {
    if (err) {
      return res.status(404).send('No reports found');
    }

    const latestHtml = files.filter(f => f.endsWith('.html')).sort().pop();

    if (latestHtml) {
      res.sendFile(path.join(reportPath, latestHtml));
    } else {
      res.status(404).send('No report found');
    }
  });
});

// ========== Debug Endpoint ==========

// Debug endpoint to check raw ADB output
app.get('/api/debug/adb-devices', async (req, res) => {
  try {
    const result = await executeCommand('adb devices');
    const lines = result.output.split('\n');

    const parsedDevices = [];
    lines.forEach((line, index) => {
      parsedDevices.push({
        lineNumber: index,
        rawLine: line,
        hasDevice: line.includes('device'),
        matchResult: line.match(/^([^\s\t]+)[\s\t]+device$/)
      });
    });

    res.json({
      success: true,
      rawOutput: result.output,
      parsedLines: parsedDevices,
      fullCommand: 'adb devices'
    });
  } catch (error) {
    res.json({
      success: false,
      error: error.message
    });
  }
});
// WebSocket connection handling
wss.on('connection', (ws) => {
  console.log('✅ New WebSocket client connected');
  clients.add(ws);

  ws.on('close', () => {
    console.log('❌ WebSocket client disconnected');
    clients.delete(ws);
  });

  ws.on('error', (error) => {
    console.error('WebSocket error:', error);
    clients.delete(ws);
  });
});

// Broadcast function to send updates to all connected clients
function broadcastProgress(progressData) {
  const message = JSON.stringify(progressData);
  clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message);
    }
  });
}

// Modified progress update endpoint
app.post('/api/progress/update', (req, res) => {
  try {
    const progressData = req.body;

    // Broadcast to all WebSocket clients
    broadcastProgress(progressData);

    // Also log to console for backend visibility
    console.log('📊 Progress Updatessssssss:', progressData);

    res.json({ success: true, message: 'Progress broadcast successful' });
  } catch (error) {
    console.error('❌ Progress update failed:', error);
    res.status(500).json({ success: false, message: error.message });
  }
});
// Add this function to server.js (after line 342 or before the parseAndBroadcastLogs function)
function broadcastToDevice(deviceId, progressData) {
  const message = JSON.stringify({ deviceId, ...progressData });
  clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message);
    }
  });
  console.log(`📡 Broadcast to device ${deviceId}:`, progressData.type || 'update');
}

// Modify the parseAndBroadcastLogs function to include device ID:
function parseAndBroadcastLogs(stdout, deviceId) {
  const lines = stdout.split('\n');

  lines.forEach(line => {
    if (line.trim()) {
      let logType = 'info';
      if (line.includes('✅') || line.includes('SUCCESS')) logType = 'success';
      else if (line.includes('❌') || line.includes('ERROR') || line.includes('FAILED')) logType = 'error';
      else if (line.includes('⚠️') || line.includes('WARNING')) logType = 'warning';

      // Check for progress data in [INFO] WS_PROGRESS format
      if (line.includes('[INFO] WS_PROGRESS:')) {
        try {
          const jsonStr = line.substring(line.indexOf('{'));
          const progressData = JSON.parse(jsonStr);
          broadcastToDevice(deviceId, progressData);
        } catch (e) {
          broadcastToDevice(deviceId, {
            type: 'log',
            message: line,
            logType: logType,
            timestamp: Date.now()
          });
        }
      } else {
        broadcastToDevice(deviceId, {
          type: 'log',
          message: line,
          logType: logType,
          timestamp: Date.now()
        });
      }
    }
  });
}

// Modified test execution with real-time streaming
app.post("/api/test-command", upload.single('file'), async (req, res) => {
  try {
    let cmd = '';
    const deviceId = req.body.deviceId;
    const aPartyNumber = req.body.phone;


    switch (req.body.testType) {
      case 'sms':
        cmd = `cd .. && mvn clean test -Dtest=SMSTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      case 'calling':
        cmd = `cd .. && mvn clean test -Dtest=CallingTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      case 'calling-sms':
        cmd = `cd .. && mvn clean test -Dtest=CallingTest,SMSTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      case 'data':
        cmd = `cd .. && mvn clean test -Dtest=DataUsageTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      case 'all':
        cmd = `cd .. && mvn clean test -Dtest=CallingTest,SMSTest,DataUsageTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      case 'calling-data':
        cmd = `cd .. && mvn clean test -Dtest=CallingTest,DataUsageTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      case 'sms-data':
        cmd = `cd .. && mvn clean test -Dtest=SMSTest,DataUsageTest -DdeviceId=${deviceId} -DaPartyNumber=${aPartyNumber}`;
        break;
      default:
        return res.status(400).json({ success: false, message: 'Invalid test type' });
    }

    console.log('🚀 Executing command:', cmd);

    // Use spawn for real-time output streaming
    const { spawn } = require('child_process');
    const testProcess = spawn('cmd', ['/c', cmd], {
      cwd: path.join(__dirname, '..')
    });

    let stdout = '';
    let stderr = '';

    // Stream stdout in real-time
    testProcess.stdout.on('data', (data) => {
      const output = data.toString();
      stdout += output;
      parseAndBroadcastLogs(output, deviceId);
    });

    // Stream stderr in real-time
    testProcess.stderr.on('data', (data) => {
      const output = data.toString();
      stderr += output;
      parseAndBroadcastLogs(output, deviceId);
    });

    // Handle process completion
    testProcess.on('close', (code) => {
      console.log(`✅ Test process exited with code ${code}`);

      // Send completion notification
      broadcastProgress(deviceId, {
        type: 'complete',
        exitCode: code,
        success: code === 0,
        message: code === 0 ? 'Tests completed successfully' : 'Tests failed',
        timestamp: Date.now()
      });

      // Send response
      if (code === 0) {
        res.status(200).json({
          status: "success",
          exitCode: code,
          stdout: stdout,
          stderr: stderr
        });
      } else {
        res.status(500).json({
          status: "failed",
          exitCode: code,
          stdout: stdout,
          stderr: stderr
        });
      }
    });

    testProcess.on('error', (error) => {
      console.error('❌ Test process error:', error);
      broadcastProgress({
        type: 'error',
        message: `Process error: ${error.message}`,
        timestamp: Date.now()
      });

      res.status(500).json({
        status: "error",
        message: error.message
      });
    });

  } catch (err) {
    console.error("❌ Error:", err);
    res.status(500).json({
      status: "error",
      message: err.message
    });
  }
});
// ========== Error Handling Middleware ==========

app.use((err, req, res, next) => {
  console.error('Server error:', err);
  res.status(500).json({
    success: false,
    message: 'Internal server error'
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: 'Endpoint not found'
  });
});

// Start Server
// Add this for better CORS handling
app.use(cors({
  origin: [
    'http://localhost:5174',      // Local development
    'http://188.208.141.113:5174', // Public network access
    'http://localhost:5173'       // Other possible local ports
  ],
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

// Handle preflight requests
app.options('*', cors());

// If you want the server to be accessible from both
server.listen(PORT, '0.0.0.0', () => {  // Listen on all interfaces
  console.log(`✅ Backend server running on port ${PORT}`);
  console.log(`🔌 WebSocket server ready on ws://localhost:${PORT} and ws://188.208.141.113:${PORT}`);
  console.log(`📊 API endpoints ready`);
  console.log(`📱 Dashboard: http://localhost:${PORT}/`);
});

