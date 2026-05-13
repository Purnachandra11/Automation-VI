package com.telecom.utils;

import com.telecom.config.SIMToolkitConfig;
import io.appium.java_client.AppiumDriver;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class ScreenshotUtils {

    private AppiumDriver driver;
    private volatile int screenshotCounter = 0;
    private final List<String> capturedScreenshots = Collections.synchronizedList(new ArrayList<>());
    private final List<String> screenshotDescriptions = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> screenshotTimestamps = Collections.synchronizedList(new ArrayList<>());
    private Date testStartTime;
    private Date testEndTime;
    
    // Constants
    private static final String REPORT_DIR = "test-output/comprehensive-reports/";
    private static final Pattern TIMESTAMP_PATTERN = 
        Pattern.compile("_(\\d{8})_(\\d{6})\\.png$");
    
    // Thread-safe date formatters
    private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_FORMATTER = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMdd_HHmmss"));
    private static final ThreadLocal<SimpleDateFormat> DISPLAY_FORMATTER = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    private static final ThreadLocal<SimpleDateFormat> FILE_DATE_FORMATTER = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMdd"));

    public ScreenshotUtils(AppiumDriver driver) {
        this.driver = driver;
        createScreenshotDir();
        loadExistingScreenshots();
    }
    
    /**
     * Set test start time - call this at the beginning of test
     */
    public void setTestStartTime() {
        this.testStartTime = new Date();
        System.out.println("⏰ Test start time set: " + testStartTime);
    }
    
    /**
     * Set test end time - call this at the end of test
     */
    public void setTestEndTime() {
        this.testEndTime = new Date();
        System.out.println("⏰ Test end time set: " + testEndTime);
    }
    
    public Date getTestStartTime() {
        return testStartTime;
    }
    
    public Date getTestEndTime() {
        return testEndTime;
    }

    /**
     * Create screenshot directory if it doesn't exist
     */
    private void createScreenshotDir() {
        File dir = new File(SIMToolkitConfig.SCREENSHOT_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("📁 Created screenshot directory: " + SIMToolkitConfig.SCREENSHOT_DIR);
            } else {
                System.err.println("❌ Failed to create screenshot directory: " + SIMToolkitConfig.SCREENSHOT_DIR);
            }
        }
    }

    /**
     * ✅ Load existing screenshots from disk into memory
     * FIXED: Added null check for directory
     */
    private void loadExistingScreenshots() {
        File dir = new File(SIMToolkitConfig.SCREENSHOT_DIR);
        
        // FIX: Check if directory exists before listing files
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("📁 Screenshot directory not found or is not a directory: " + SIMToolkitConfig.SCREENSHOT_DIR);
            return;
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        
        if (files != null && files.length > 0) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            
            for (File file : files) {
                capturedScreenshots.add(file.getName());
                String desc = file.getName()
                    .replaceAll("^\\d+_", "")
                    .replaceAll("_\\d{8}_\\d{6}\\.png$", "")
                    .replace("_", " ");
                screenshotDescriptions.add(desc);
                
                // Extract and store timestamp
                Long timestamp = extractTimestampFromFilename(file.getName());
                screenshotTimestamps.add(timestamp);
            }
            
            screenshotCounter = files.length;
            System.out.println("📂 Loaded " + files.length + " existing screenshots from disk");
        } else {
            System.out.println("📂 No existing screenshots found in directory: " + SIMToolkitConfig.SCREENSHOT_DIR);
        }
    }
    
    /**
     * Extract timestamp from filename (e.g., 001_launch_20241220_143022.png)
     */
    private Long extractTimestampFromFilename(String filename) {
        try {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(filename);
            if (matcher.find()) {
                String dateStr = matcher.group(1); // yyyyMMdd
                String timeStr = matcher.group(2); // HHmmss
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                Date date = sdf.parse(dateStr + "_" + timeStr);
                return date.getTime();
            }
        } catch (Exception e) {
            System.err.println("❌ Error parsing timestamp from filename: " + filename + " - " + e.getMessage());
        }
        return 0L;
    }

    /**
     * Capture screenshot with step name
     * FIXED: Added null check for driver and proper file path construction
     */
    public String captureScreenshot(String stepName) {
        if (driver == null) {
            System.err.println("❌ Driver is null, cannot capture screenshot for: " + stepName);
            return null;
        }
        
        try {
            screenshotCounter++;
            
            String timestamp = TIMESTAMP_FORMATTER.get().format(new Date());
            long currentTime = System.currentTimeMillis();
            
            // FIX: Sanitize step name to remove invalid filename characters
            String sanitizedStepName = stepName.toLowerCase()
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_]", "");
            
            String fileName = String.format(
                    "%03d_%s_%s.png",
                    screenshotCounter,
                    sanitizedStepName,
                    timestamp
            );

            // FIX: Ensure directory exists before saving
            File screenshotDir = new File(SIMToolkitConfig.SCREENSHOT_DIR);
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            
            // FIX: Use proper file path construction
            File dest = new File(screenshotDir, fileName);
            
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(src, dest);

            capturedScreenshots.add(fileName);
            screenshotDescriptions.add(stepName);
            screenshotTimestamps.add(currentTime);

            System.out.println("📸 Screenshot saved: " + dest.getAbsolutePath());
            return dest.getAbsolutePath();

        } catch (Exception e) {
            System.err.println("❌ Screenshot failed for " + stepName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get screenshots captured during the test period
     */
    private List<ScreenshotInfo> getScreenshotsInTestPeriod() {
        List<ScreenshotInfo> testScreenshots = new ArrayList<>();
        
        if (testStartTime == null || testEndTime == null) {
            System.out.println("⚠️ Test start/end time not set. Showing all screenshots.");
            // Return all if times not set
            synchronized (capturedScreenshots) {
                for (int i = 0; i < capturedScreenshots.size(); i++) {
                    testScreenshots.add(new ScreenshotInfo(
                        capturedScreenshots.get(i),
                        screenshotDescriptions.get(i),
                        screenshotTimestamps.get(i)
                    ));
                }
            }
            return testScreenshots;
        }
        
        long startTime = testStartTime.getTime();
        long endTime = testEndTime.getTime();
        
        System.out.println("🔍 Filtering screenshots from " + testStartTime + " to " + testEndTime);
        
        synchronized (capturedScreenshots) {
            for (int i = 0; i < capturedScreenshots.size(); i++) {
                long screenshotTime = screenshotTimestamps.get(i);
                
                // Check if screenshot was taken during test period
                // Adding 2 second buffer for timing differences
                if (screenshotTime >= (startTime - 2000) && screenshotTime <= (endTime + 2000)) {
                    testScreenshots.add(new ScreenshotInfo(
                        capturedScreenshots.get(i),
                        screenshotDescriptions.get(i),
                        screenshotTimestamps.get(i)
                    ));
                }
            }
        }
        
        System.out.println("📊 Found " + testScreenshots.size() + " screenshots in test period");
        return testScreenshots;
    }
    
    /**
     * Helper class to store screenshot information
     */
    private static class ScreenshotInfo {
        String filename;
        String description;
        long timestamp;
        
        ScreenshotInfo(String filename, String description, long timestamp) {
            this.filename = filename;
            this.description = description;
            this.timestamp = timestamp;
        }
        
        String getFormattedTime() {
            return DISPLAY_FORMATTER.get().format(new Date(timestamp));
        }
        
        /**
         * Get relative path for HTML report
         * FIXED: Proper path calculation
         */
        String getRelativeImagePath() {
            try {
                File screenshotFile = new File(SIMToolkitConfig.SCREENSHOT_DIR, filename);
                File reportDir = new File(REPORT_DIR);
                
                if (!reportDir.exists()) {
                    return "file:///" + screenshotFile.getAbsolutePath().replace("\\", "/");
                }
                
                // Calculate relative path
                String screenshotPath = screenshotFile.getCanonicalPath();
                String reportPath = reportDir.getCanonicalPath();
                
                if (screenshotPath.startsWith(reportPath)) {
                    return screenshotPath.substring(reportPath.length()).replace("\\", "/");
                }
                
                // Fallback: Use relative path if in same parent directory
                return "../" + new File(SIMToolkitConfig.SCREENSHOT_DIR).getName() + "/" + filename;
            } catch (Exception e) {
                System.err.println("❌ Error calculating relative path for: " + filename);
                return filename; // Fallback
            }
        }
    }

    /**
     * Generate HTML screenshot report
     * FIXED: Report directory constant and image path calculation
     */
    public void generateScreenshotReport() {
        try {
            // FIX: Use constant for report directory
            File reportDir = new File(REPORT_DIR);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
                System.out.println("📁 Created report directory: " + REPORT_DIR);
            }

            // Get the device number like other reports do
            String dialingNumber = System.getProperty("aPartyNumber");

            String timestamp = TIMESTAMP_FORMATTER.get().format(new Date());
            // FIX: Use proper file path construction
            String reportPath = new File(reportDir, 
                "Screenshot_Report_" + dialingNumber + "_" + timestamp + ".html").getAbsolutePath();
            
            // Get screenshots captured during test period
            List<ScreenshotInfo> testScreenshots = getScreenshotsInTestPeriod();

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Vi SIM Toolkit Screenshot Report - ").append(dialingNumber).append("</title>")
                .append("<style>")
                .append("body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f7fa; padding: 20px; margin: 0; }")
                .append("h1 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }")
                .append(".info-box { background: white; padding: 20px; border-radius: 8px; margin: 15px 0; ")
                .append("box-shadow: 0 2px 4px rgba(0,0,0,0.1); }")
                .append(".info-box h2 { color: #2980b9; margin-top: 0; }")
                .append(".info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); ")
                .append("gap: 15px; margin-top: 15px; }")
                .append(".info-item { background: #ecf0f1; padding: 15px; border-radius: 6px; }")
                .append(".info-item strong { display: block; color: #34495e; margin-bottom: 5px; }")
                .append(".screenshot-box { background: white; padding: 20px; margin: 20px 0; ")
                .append("border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }")
                .append(".screenshot-box h3 { color: #2980b9; margin-top: 0; border-bottom: 2px solid #ecf0f1; padding-bottom: 8px; }")
                .append(".screenshot-meta { color: #7f8c8d; font-size: 14px; margin: 8px 0; }")
                .append(".screenshot-box img { max-width: 400px; height: auto; border: 2px solid #bdc3c7; ")
                .append("border-radius: 4px; margin-top: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.15); ")
                .append("transition: transform 0.2s; }")
                .append(".screenshot-box img:hover { transform: scale(1.02); }")
                .append(".screenshot-box code { background: #ecf0f1; padding: 4px 8px; border-radius: 4px; ")
                .append("color: #e74c3c; font-size: 12px; }")
                .append(".footer { text-align: center; color: #7f8c8d; margin-top: 30px; padding: 20px; ")
                .append("border-top: 1px solid #ecf0f1; }")
                .append(".no-screenshots { text-align: center; padding: 40px; color: #7f8c8d; ")
                .append("font-style: italic; background: #ecf0f1; border-radius: 8px; margin: 20px 0; }")
                .append(".image-container { position: relative; }")
                .append(".image-not-found { color: #e74c3c; background: #ffeaea; padding: 10px; ")
                .append("border-radius: 4px; margin-top: 10px; }")
                .append("</style></head><body>");

            html.append("<h1>📱 Vi SIM Toolkit Screenshot Report - ").append(dialingNumber).append("</h1>");
            
            // Test Information Section
            html.append("<div class='info-box'>")
                .append("<h2>Test Information</h2>")
                .append("<div class='info-grid'>");
            
            // Add device number to report
            html.append("<div class='info-item'>")
                .append("<strong>Device Number</strong>")
                .append("<span>").append(dialingNumber.equals("unknown") ? "Not Specified" : dialingNumber).append("</span>")
                .append("</div>");
            
            if (testStartTime != null) {
                html.append("<div class='info-item'>")
                    .append("<strong>Test Start Time</strong>")
                    .append("<span>").append(DISPLAY_FORMATTER.get().format(testStartTime)).append("</span>")
                    .append("</div>");
            }
            
            if (testEndTime != null) {
                html.append("<div class='info-item'>")
                    .append("<strong>Test End Time</strong>")
                    .append("<span>").append(DISPLAY_FORMATTER.get().format(testEndTime)).append("</span>")
                    .append("</div>");
            }
            
            html.append("<div class='info-item'>")
                .append("<strong>📸 Total Screenshots in Test Period</strong>")
                .append("<span>").append(testScreenshots.size()).append("</span>")
                .append("</div>")
//                .append("<div class='info-item'>")
//                .append("<strong>📂 Screenshot Directory</strong>")
//                .append("<span><code>").append(SIMToolkitConfig.SCREENSHOT_DIR).append("</code></span>")
//                .append("</div>")
//                .append("<div class='info-item'>")
//                .append("<strong>📊 Report Location</strong>")
//                .append("<span><code>").append(reportPath).append("</code></span>")
//                .append("</div>")
                .append("</div></div>");
            
            // Screenshots Section
            html.append("<div class='info-box'>")
                .append("<h2>Screenshots Captured During Test</h2>");
            
            if (testScreenshots.isEmpty()) {
                html.append("<div class='no-screenshots'>")
                    .append("<p>⚠️ No screenshots found for the specified test period</p>")
                    .append("<p>Test Period: ");
                
                if (testStartTime != null) {
                    html.append(DISPLAY_FORMATTER.get().format(testStartTime));
                }
                html.append(" to ");
                if (testEndTime != null) {
                    html.append(DISPLAY_FORMATTER.get().format(testEndTime));
                }
                html.append("</p></div>");
            } else {
                for (int i = 0; i < testScreenshots.size(); i++) {
                    ScreenshotInfo info = testScreenshots.get(i);
                    // FIX: Use calculated relative path
                    String imagePath = info.getRelativeImagePath();
                    
                    html.append("<div class='screenshot-box'>")
                        .append("<h3>").append(i + 1).append(". ").append(info.description).append("</h3>")
                        .append("<div class='screenshot-meta'>")
                        .append("<strong>File:</strong> <code>").append(info.filename).append("</code><br>")
                        .append("<strong>Captured at:</strong> ").append(info.getFormattedTime())
                        .append("</div>")
                        .append("<div class='image-container'>")
                        .append("<img src=\"").append(imagePath).append("\" ")
                        .append("alt='").append(info.description).append("' ")
                        .append("onerror=\"this.style.display='none';")
                        .append("this.parentElement.innerHTML+='<div class=\\'image-not-found\\'>❌ Image not found: ")
                        .append(info.filename).append("</div>';\">")
                        .append("</div>")
                        .append("</div>");
                }
            }
            
            html.append("</div>");

            html.append("<div class='footer'>")
                .append("<p>📂 Report saved in: ").append(REPORT_DIR).append("</p>")
                .append("<p>Generated by Vi SIM Toolkit Automation Framework</p>")
                .append("<p>Report generated: ").append(DISPLAY_FORMATTER.get().format(new Date())).append("</p>")
                .append("</div>")
                .append("</body></html>");

            FileUtils.writeStringToFile(new File(reportPath), html.toString(), "UTF-8");
            System.out.println("✅ Screenshot report saved: " + reportPath);
            System.out.println("📊 Screenshots in report: " + testScreenshots.size());

        } catch (Exception e) {
            System.err.println("❌ Screenshot report generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Map<String, Boolean> verifyRequiredScreenshots(SIMToolkitConfig.SIMType simType) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        List<String> required = getRequiredScreenshots(simType);

        File dir = new File(SIMToolkitConfig.SCREENSHOT_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            // Directory doesn't exist, mark all as false
            for (String req : required) {
                results.put(req, false);
            }
            return results;
        }
        
        File[] actualFiles = dir.listFiles((d, n) -> n.endsWith(".png"));

        for (String req : required) {
            boolean found = false;
            String key = req.toLowerCase().replace(" ", "_");

            if (actualFiles != null) {
                for (File f : actualFiles) {
                    if (f.getName().toLowerCase().contains(key)) {
                        found = true;
                        break;
                    }
                }
            }
            results.put(req, found);
        }
        return results;
    }

    public void clearScreenshots() {
        try {
            File dir = new File(SIMToolkitConfig.SCREENSHOT_DIR);
            if (dir.exists() && dir.isDirectory()) {
                FileUtils.cleanDirectory(dir);
                System.out.println("🧹 Cleared screenshot directory: " + SIMToolkitConfig.SCREENSHOT_DIR);
            }
            
            synchronized (capturedScreenshots) {
                capturedScreenshots.clear();
                screenshotDescriptions.clear();
                screenshotTimestamps.clear();
            }
            screenshotCounter = 0;
            
        } catch (Exception e) {
            System.err.println("❌ Clear screenshots failed: " + e.getMessage());
        }
    }

    public void printScreenshotSummary() {
        int size;
        synchronized (capturedScreenshots) {
            size = capturedScreenshots.size();
        }
        
        System.out.println("   Total screenshots: " + size);
        if (size == 0) {
            System.out.println("   ⚠️ No screenshots found!");
        } else {
            synchronized (capturedScreenshots) {
                for (int i = 0; i < Math.min(size, 10); i++) {
                    System.out.println("   " + (i + 1) + ". " + screenshotDescriptions.get(i));
                }
                if (size > 10) {
                    System.out.println("   ... and " + (size - 10) + " more");
                }
            }
        }
    }

    private List<String> getRequiredScreenshots(SIMToolkitConfig.SIMType simType) {
        List<String> list = new ArrayList<>();
        list.add("SIM Toolkit Launch");
        if (simType != SIMToolkitConfig.SIMType.SINGLE_SIM) {
            list.add("SIM Selection Screen");
        }
        list.add("Vi Menu Home");
        list.add("Flash Option");
        list.add("Roaming Menu");
        list.add("Vodafone IN");
        list.add("International");
        return list;
    }
    
    public int getScreenshotCount() {
        synchronized (capturedScreenshots) {
            return capturedScreenshots.size();
        }
    }
    
    /**
     * Optional: Get list of all screenshot files with full paths
     */
    public List<String> getAllScreenshotPaths() {
        List<String> paths = new ArrayList<>();
        synchronized (capturedScreenshots) {
            for (String filename : capturedScreenshots) {
                paths.add(new File(SIMToolkitConfig.SCREENSHOT_DIR, filename).getAbsolutePath());
            }
        }
        return paths;
    }
}