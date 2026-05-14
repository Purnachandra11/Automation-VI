@echo off
title Telecom Automation Framework
echo ===============================================
echo    TELECOM AUTOMATION FRAMEWORK - EXECUTOR
echo ===============================================

:: Set colors
set GREEN=[92m
set YELLOW=[93m
set RED=[91m
set RESET=[0m

:: Check Java version
echo Checking Java version...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo %RED%Java is not installed or not in PATH%RESET%
    pause
    exit /b 1
)

:: Check Maven
echo Checking Maven...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo %RED%Maven is not installed or not in PATH%RESET%
    pause
    exit /b 1
)

:: Check ADB
echo Checking ADB...
adb devices >nul 2>&1
if %errorlevel% neq 0 (
    echo %YELLOW%ADB not found. Please ensure Android SDK is installed%RESET%
)

:: Set parameters
set DEVICE_ID=%1
set TEST_SUITE=testng-comprehensive.xml

if "%DEVICE_ID%"=="" (
    echo %YELLOW%No device ID provided. Will use connected devices.%RESET%
    set DEVICE_PARAM=
) else (
    echo %GREEN%Using device: %DEVICE_ID%%RESET%
    set DEVICE_PARAM=-Ddevice.id=%DEVICE_ID%
)

if "%2" neq "" (
    set TEST_SUITE=%2
)

:: Clean and create directories
echo.
echo %YELLOW%Cleaning previous test outputs...%RESET%
if exist test-output rmdir /s /q test-output
mkdir test-output\comprehensive-reports
mkdir test-output\screenshots
mkdir test-output\extent-reports

:: Generate sample data if requested
if "%3"=="--generate-sample" (
    echo %GREEN%Generating sample test data...%RESET%
    mvn exec:java -Dexec.mainClass="com.telecom.Main" -Dexec.args="--generate-sample"
)

:: Run tests
echo.
echo %GREEN%Starting Telecom Automation Tests...%RESET%
echo ===============================================

mvn clean test %DEVICE_PARAM% -DsuiteXmlFile=testng-xml/%TEST_SUITE%

:: Check test results
if %errorlevel% equ 0 (
    echo.
    echo %GREEN%===============================================%RESET%
    echo %GREEN% Tests completed successfully!%RESET%
    echo %GREEN%===============================================%RESET%
) else (
    echo.
    echo %RED%===============================================%RESET%
    echo %RED%❌ Tests completed with failures%RESET%
    echo %RED%===============================================%RESET%
)

:: Open reports directory if exists
if exist test-output\comprehensive-reports (
    echo.
    echo %YELLOW%Reports are available in: test-output\comprehensive-reports\%RESET%
    echo %YELLOW%Screenshots are in: test-output\screenshots\%RESET%
)

pause