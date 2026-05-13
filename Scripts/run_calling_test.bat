@echo off
echo ============================================
echo 📱 Telecom Test Automation Runner
echo ============================================
echo.

REM Get current ADB devices
echo 🔍 Checking connected devices...
adb devices
echo.

REM Set default values
set DEFAULT_A_PARTY=LFMVIBEMW8HUR4XK
set DEFAULT_A_NUMBER=8696904544
set DEFAULT_B_NUMBER=9773328866

REM Get B-party device ID (wireless device)
echo 📶 Looking for wireless/B-party device...
for /f "tokens=1,2" %%a in ('adb devices ^| findstr "connected device"') do (
    if not "%%a"=="%DEFAULT_A_PARTY%" (
        if "%%a"=="List" goto skip
        if "%%a"=="" goto skip
        set B_PARTY_DEVICE=%%a
        echo ✅ Found wireless device: %%a
    )
)

:skip
if "%B_PARTY_DEVICE%"=="" (
    echo ⚠️ No wireless device found. Please connect B-party device.
    pause
    exit /b 1
)

echo.
echo 📋 TEST CONFIGURATION:
echo    A-Party: %DEFAULT_A_PARTY% (%DEFAULT_A_NUMBER%)
echo    B-Party: %B_PARTY_DEVICE% (%DEFAULT_B_NUMBER%)
echo.

REM Run Maven test with dynamic parameters
echo 🚀 Starting Calling Tests...
mvn test -Dtest=CallingTest ^
    -DaPartyDevice=%DEFAULT_A_PARTY% ^
    -DaPartyNumber=%DEFAULT_A_NUMBER% ^
    -DbPartyDevice=%B_PARTY_DEVICE% ^
    -DbPartyNumber=%DEFAULT_B_NUMBER%

echo.
echo 📊 Test execution complete!
pause