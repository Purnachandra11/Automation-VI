@echo off
echo Setting up Telecom Automation Framework...
echo.

:: Create directory structure
mkdir src\main\java\com\telecom
mkdir src\test\resources
mkdir test-output\comprehensive-reports
mkdir test-output\screenshots
mkdir test-output\extent-reports
mkdir testng-xml

echo ✅ Directory structure created

:: Generate sample config if not exists
if not exist src\test\resources\config.properties (
    echo # Telecom Automation Configuration > src\test\resources\config.properties
    echo appPackage=com.google.android.dialer >> src\test\resources\config.properties
    echo appActivity=com.google.android.dialer.extensions.GoogleDialtactsActivity >> src\test\resources\config.properties
    echo dialing.number=+919876543210 >> src\test\resources\config.properties
    echo excelFilePath=src/test/resources/contacts.xlsx >> src\test\resources\config.properties
    echo ✅ Sample config.properties created
)

echo.
echo 🎉 Framework setup completed!
echo.
echo Next steps:
echo 1. Update src\test\resources\config.properties with your settings
echo 2. Run: mvn clean install
echo 3. Add test numbers to contacts.xlsx
echo 4. Run: run_tests.bat