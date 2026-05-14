package com.telecom.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.telecom.config.ConfigReader;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *  ENHANCED REPORT GENERATOR WITH RING TIME & DIRECTION HANDLING
 */
public class ReportGenerator {
    private static final String REPORT_DIR = "test-output/comprehensive-reports/";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");
    
    static {
        new File(REPORT_DIR).mkdirs();
    }
    
    // ========== ENHANCED CALLING REPORT WITH RING TIME & DIRECTION ==========
    
    /**
     *  GENERATE ENHANCED CALLING EXCEL REPORT
     */
    public static String generateCallingExcelReport(List<Map<String, Object>> results) {
    	String dialingNumber = System.getProperty("aPartyNumber");
        String fileName = "Calling_Report_" + dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".xlsx";
        String filePath = REPORT_DIR + fileName;
        
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("Calling Test Results");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle successStyle = createSuccessStyle(workbook);
            CellStyle failedStyle = createFailedStyle(workbook);
            CellStyle partialStyle = createPartialStyle(workbook);
            
         // Update the headers array in generateCallingExcelReport method:
            String[] headers = {
            	    "Test Name",
            	    "Direction", 
            	    "Caller Number",
            	    "Receiver Number",
            	    "A Party Network", 
            	    "A Party VoLTE", 
            	    "B Party Network", 
            	    "B Party VoLTE",
            	    "Auto Answer",
//            	    "Call Handling",
            	    "Ring Time (s)",
            	    "Target Duration (s)", 
            	    "Actual Duration (s)", 
            	    "Attempts", 
            	    "Call Status", 
            	    "Call Type", 
            	    "Final Status", 
            	    "Before Balance",       
            	    "After Balance",        
            	    "Balance Deduction",   
            	    "Call Cost",          
            	    "A Party MSISDN",
            	    "Comments", 
            	    "Timestamp"
            	};
            
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (Map<String, Object> result : results) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                
                // Test Name
                row.createCell(col++).setCellValue(getStringValue(result.get("name")));
                
                // Direction
                String direction = getStringValue(result.get("direction"));
                row.createCell(col++).setCellValue(direction);
                
                // Caller and Receiver
                row.createCell(col++).setCellValue(getStringValue(result.get("callerNumber")));
                row.createCell(col++).setCellValue(getStringValue(result.get("receiverNumber")));
                
                //  FIX: Clean network type display
                String aPartyNetwork = cleanNetworkType(getStringValue(result.get("aPartyNetworkType")));
                String bPartyNetwork = cleanNetworkType(getStringValue(result.get("bPartyNetworkType")));
                
                row.createCell(col++).setCellValue(aPartyNetwork);
                row.createCell(col++).setCellValue(getStringValue(result.get("aPartyVolteEnabled")));
                row.createCell(col++).setCellValue(bPartyNetwork);
                row.createCell(col++).setCellValue(getStringValue(result.get("bPartyVolteEnabled")));
                
                //  CRITICAL FIX: Use autoAnswerEnabled boolean, not autoAnswerStatus string
                boolean autoAnswerEnabled = getBooleanValue(result.get("autoAnswerEnabled"));
                row.createCell(col++).setCellValue(autoAnswerEnabled ? "YES" : "NO");
                
                // Call Handling
//                String handling = getStringValue(result.get("callHandling"));
//                row.createCell(col++).setCellValue(handling);
                
                //  CRITICAL FIX: Use ringTime integer field
                int ringTime = getIntValue(result.get("ringTime"));
                row.createCell(col++).setCellValue(ringTime);
                
                // Durations
                row.createCell(col++).setCellValue(getIntValue(result.get("duration")));
                row.createCell(col++).setCellValue(getIntValue(result.get("actualDuration")));
                
                // Attempts
                row.createCell(col++).setCellValue(getIntValue(result.get("attemptNumber")));
                
                // Call Status and Type
                row.createCell(col++).setCellValue(getStringValue(result.get("callStatus")));
                row.createCell(col++).setCellValue(getStringValue(result.get("callType")));
                
             // Final Status
                row.createCell(col++).setCellValue(getStringValue(result.get("finalStatus")));

             // Before Balance
                Object beforeBalance = null;
                if ("INCOMING".equals(direction)) {
                    beforeBalance = result.get("bPartyBeforeBalance");
                } else {
                    beforeBalance = result.get("beforeBalance");
                }

                if (beforeBalance instanceof String) {
                    row.createCell(col++).setCellValue((String) beforeBalance);
                } else if (beforeBalance instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) beforeBalance);
                } else if (beforeBalance instanceof Integer) {
                    row.createCell(col++).setCellValue("₹" + (Integer) beforeBalance);
                } else {
                    row.createCell(col++).setCellValue(getStringValue(beforeBalance));
                }

                // After Balance
                Object afterBalance = null;
                if ("INCOMING".equals(direction)) {
                    afterBalance = result.get("bPartyAfterBalance");
                } else {
                    afterBalance = result.get("afterBalance");
                }

                if (afterBalance instanceof String) {
                    row.createCell(col++).setCellValue((String) afterBalance);
                } else if (afterBalance instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) afterBalance);
                } else if (afterBalance instanceof Integer) {
                    row.createCell(col++).setCellValue("₹" + (Integer) afterBalance);
                } else {
                    row.createCell(col++).setCellValue(getStringValue(afterBalance));
                }

                // Balance Deduction
                Object deduction = null;
                if ("INCOMING".equals(direction)) {
                    deduction = result.get("bPartyBalanceDeduction");
                } else {
                    deduction = result.get("balanceDeduction");
                }

                if (deduction instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) deduction);
                } else if (deduction instanceof Integer) {
                    row.createCell(col++).setCellValue("₹" + (Integer) deduction);
                } else if (beforeBalance != null && afterBalance != null) {
                    try {
                        double before = getDoubleValue(beforeBalance);
                        double after = getDoubleValue(afterBalance);
                        double calcDeduction = before - after;
                        row.createCell(col++).setCellValue("₹" + calcDeduction);
                    } catch (Exception e) {
                        row.createCell(col++).setCellValue("");
                    }
                } else {
                    row.createCell(col++).setCellValue("");
                }

                // Call Cost (same as deduction)
                if (deduction instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) deduction);
                } else if (deduction instanceof Integer) {
                    row.createCell(col++).setCellValue("₹" + (Integer) deduction);
                } else {
                    row.createCell(col++).setCellValue("");
                }

             //  NEW: A Party MSISDN (renamed from Sender MSISDN)
                String aPartyMSISDN = "";
                if ("INCOMING".equals(direction)) {
                    // For incoming calls, A-Party is the receiver
                    aPartyMSISDN = getStringValue(result.get("receiverMSISDN"));
                } else {
                    // For outgoing calls, A-Party is the caller
                    aPartyMSISDN = getStringValue(result.get("callerMSISDN"));
                }
                row.createCell(col++).setCellValue(aPartyMSISDN);
                
                // Final Status
//                row.createCell(col++).setCellValue(getStringValue(result.get("finalStatus")));
                
                // Comments
                row.createCell(col++).setCellValue(getStringValue(result.get("comments")));
                
                // Timestamp
                row.createCell(col).setCellValue(getStringValue(result.get("testTimestamp")));
                
                // Apply style based on final status
                CellStyle styleToApply;
                String finalStatus = getStringValue(result.get("finalStatus"));
                if (finalStatus.contains("SUCCESS") && !finalStatus.contains("PARTIAL")) {
                    styleToApply = successStyle;
                } else if (finalStatus.contains("FAILED")) {
                    styleToApply = failedStyle;
                } else if (finalStatus.contains("PARTIAL")) {
                    styleToApply = partialStyle;
                } else {
                    styleToApply = workbook.createCellStyle();
                }
                
                for (int i = 0; i < headers.length; i++) {
                    if (row.getCell(i) != null) {
                        row.getCell(i).setCellStyle(styleToApply);
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(fos);
            System.out.println(" Enhanced Calling Excel Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ Calling Excel report failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    
    /**
     *  GENERATE ENHANCED HTML REPORT
     */
    public static String generateCallingHTMLReport(List<Map<String, Object>> results) {
    	String dialingNumber = System.getProperty("aPartyNumber");
        String fileName = "Calling_Report_" + dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".html";
        String filePath = REPORT_DIR + fileName;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(generateHTMLHeader("Enhanced Calling Test Report"));
            writer.println("<div class='container'>");
            writer.println("<h1>📞 Enhanced Calling Test Report</h1>");
            writer.println(generateEnhancedSummarySection(results));
            writer.println(generateEnhancedCallingTable(results));
            writer.println("</div></body></html>");
            
            System.out.println(" Calling HTML Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ Calling HTML report failed: " + e.getMessage());
            return null;
        }
    }
    
    // ========== SMS REPORT METHODS ==========
    
    public static String generateSMSExcelReport(List<Map<String, Object>> results) {
        String dialingNumber = System.getProperty("aPartyNumber");
        
        String fileName = "SMS_Report_" + dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".xlsx";
        String filePath = REPORT_DIR + fileName;
        
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("SMS Test Results");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle successStyle = createSuccessStyle(workbook);
            CellStyle failedStyle = createFailedStyle(workbook);
            CellStyle partialStyle = createPartialStyle(workbook);
            
            // Updated headers based on actual execution data
            String[] headers = {
                "Test Name",
                "Test Type", 
                "Message Type",
                "Direction",
                "A Party Number",
                "B Party Number",
                "Recipient",
                "Group Name",
                "Message",
//              "Sender Number",
//              "Receiver Number",
//              "Individual Test",
//              "Group Test",
                "Before Balance",
                "After Balance",
                "Balance Deduction",
                "Sender MSISDN",
//                "Delivery Time (ms)",
                "Delivery Time (s)",
                "Delivery Status",
                "Verification Status",  //  NEW: Added verification status
                "Message Delivered",
                "Total SMS",
                "Successful SMS",
                "Failed SMS",
                "Test Start Time",
                "Test End Time",
                "Sender Timestamp",
                "Receiver Time",       //  Formatted receiver time or status
                "Final Status",
                "Comments"
//              "Receiver Timestamp",  //  Receiver timestamp (long value)	
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (Map<String, Object> result : results) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                
                // Test Name - Fixed: Get from "name" field
                row.createCell(col++).setCellValue(getStringValue(result.get("name")));
                
                // Test Type
                row.createCell(col++).setCellValue(getStringValue(result.get("testType")));
                
                // Message Type
                row.createCell(col++).setCellValue(getStringValue(result.get("messageType")));
                
                // Direction
                row.createCell(col++).setCellValue(getStringValue(result.get("direction")));
                
                // A Party Number - Fixed: Get from "aPartyNumber"
                row.createCell(col++).setCellValue(getStringValue(result.get("aPartyNumber")));
                
                // B Party Number - Fixed: Get from "bPartyNumber"
                row.createCell(col++).setCellValue(getStringValue(result.get("bPartyNumber")));
                
                // Recipient
                row.createCell(col++).setCellValue(getStringValue(result.get("recipient")));
                
                // Group Name
                row.createCell(col++).setCellValue(getStringValue(result.get("groupName")));
                
                // Message
                row.createCell(col++).setCellValue(getStringValue(result.get("message")));
                
                // Comment out the sender/receiver number calculation and individual/group test columns
                // These were previously here but are now commented out
                /*
                String direction = getStringValue(result.get("direction"));
                boolean isIndividual = getBooleanValue(result.get("isIndividual"));
                String senderNumber;
                String receiverNumber;

               
                if ("OUTGOING".equals(direction)) {
                    senderNumber = getStringValue(result.get("aPartyNumber"));
                    receiverNumber = isIndividual ? getStringValue(result.get("recipient")) : 
                                    getStringValue(result.get("bPartyNumber"));
                    
                 // INCOMING
                } else { 
                    String recipientValue = getStringValue(result.get("recipient"));
                    String bPartyValue = getStringValue(result.get("bPartyNumber"));
                    
                    senderNumber = isIndividual ? recipientValue : bPartyValue;
                    receiverNumber = getStringValue(result.get("aPartyNumber"));
                }

                // If senderMSISDN is available, use it for sender number
                if (result.containsKey("senderMSISDN")) {
                    String senderMSISDN = getStringValue(result.get("senderMSISDN"));
                    if (senderMSISDN != null && !senderMSISDN.isEmpty()) {
                        senderNumber = senderMSISDN;
                    }
                }

                // Now use these calculated values
                row.createCell(col++).setCellValue(senderNumber);
                row.createCell(col++).setCellValue(receiverNumber);
                
                // Individual Test
                row.createCell(col++).setCellValue(getBooleanValue(result.get("isIndividual")) ? "YES" : "NO");
                
                // Group Test
                row.createCell(col++).setCellValue(getBooleanValue(result.get("isGroup")) ? "YES" : "NO");
                */
                
                // Before Balance - Fixed: Get from "beforeBalance"
                Object beforeBalance = result.get("beforeBalance");
                if (beforeBalance instanceof String) {
                    row.createCell(col++).setCellValue((String) beforeBalance);
                } else if (beforeBalance instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) beforeBalance);
                } else {
                    row.createCell(col++).setCellValue(getStringValue(beforeBalance));
                }
                
                // After Balance - Fixed: Get from "afterBalance"
                Object afterBalance = result.get("afterBalance");
                if (afterBalance instanceof String) {
                    row.createCell(col++).setCellValue((String) afterBalance);
                } else if (afterBalance instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) afterBalance);
                } else {
                    row.createCell(col++).setCellValue(getStringValue(afterBalance));
                }
                
                // Balance Deduction - Fixed: Calculate if not present
                Object deduction = result.get("balanceDeduction");
                if (deduction instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) deduction);
                } else if (beforeBalance != null && afterBalance != null) {
                    try {
                        double before = getDoubleValue(beforeBalance);
                        double after = getDoubleValue(afterBalance);
                        double calcDeduction = before - after;
                        row.createCell(col++).setCellValue("₹" + calcDeduction);
                    } catch (Exception e) {
                        row.createCell(col++).setCellValue("");
                    }
                } else {
                    row.createCell(col++).setCellValue("");
                }
                
                // Sender MSISDN - Fixed: Get from USSD result
                row.createCell(col++).setCellValue(getStringValue(result.get("senderMSISDN")));
                
                // Delivery Time (ms)
//                row.createCell(col++).setCellValue(getLongValue(result.get("deliveryTimeMs")));
                
                // Delivery Time (s)
                if (result.containsKey("deliveryTimeSec")) {
                    row.createCell(col++).setCellValue(getDoubleValue(result.get("deliveryTimeSec")));
                } else {
                    row.createCell(col++).setCellValue("");
                }
                
                // Delivery Status
                row.createCell(col++).setCellValue(getStringValue(result.get("deliveryStatus")));
                
             //  NEW: Verification Status
                row.createCell(col++).setCellValue(getStringValue(result.get("verificationStatus")));
                
                // Message Delivered
                row.createCell(col++).setCellValue(getBooleanValue(result.get("messageDelivered")) ? "YES" : "NO");
                
                // Total SMS
                row.createCell(col++).setCellValue(getIntValue(result.get("totalSMS")));
                
                // Successful SMS
                row.createCell(col++).setCellValue(getIntValue(result.get("successfulSMS")));
                
                // Failed SMS
                row.createCell(col++).setCellValue(getIntValue(result.get("failedSMS")));
                
                // Test Start Time
                row.createCell(col++).setCellValue(getStringValue(result.get("testStartTime")));
                
                // Test End Time
                row.createCell(col++).setCellValue(getStringValue(result.get("testEndTime")));
                
                // Sender Timestamp - Fixed: Get from textResult or voiceResult
                String senderTime = "";
                if (result.containsKey("textResult")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> textResult = (Map<String, Object>) result.get("textResult");
                    if (textResult != null && textResult.containsKey("senderTime")) {
                        senderTime = getStringValue(textResult.get("senderTime"));
                    }
                } else if (result.containsKey("voiceResult")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> voiceResult = (Map<String, Object>) result.get("voiceResult");
                    if (voiceResult != null && voiceResult.containsKey("senderTime")) {
                        senderTime = getStringValue(voiceResult.get("senderTime"));
                    }
                }
                row.createCell(col++).setCellValue(senderTime);
                
//             //  NEW: Receiver Timestamp (raw long value)
//                Long receiverTimestamp = getLongValue(result.get("receiverTimestamp"));
//                if (receiverTimestamp != null && receiverTimestamp > 0) {
//                    row.createCell(col++).setCellValue(receiverTimestamp);
//                } else {
//                    row.createCell(col++).setCellValue("0");
//                }

                //  NEW: Receiver Time (formatted or status)
                String receiverTime = getStringValue(result.get("receiverTime"));
                // Check if we have formatted time, otherwise use the status
                if (receiverTime == null || receiverTime.isEmpty()) {
                    receiverTime = getStringValue(result.get("verificationStatus"));
                }
                row.createCell(col++).setCellValue(receiverTime);
                // Final Status
                row.createCell(col++).setCellValue(getStringValue(result.get("finalStatus")));
                
                // Comments
                row.createCell(col++).setCellValue(getStringValue(result.get("comments")));
                
                // Apply style based on final status
                CellStyle styleToApply;
                String finalStatus = getStringValue(result.get("finalStatus"));
                if (finalStatus.contains("SUCCESS") && !finalStatus.contains("PARTIAL")) {
                    styleToApply = successStyle;
                } else if (finalStatus.contains("FAILED")) {
                    styleToApply = failedStyle;
                } else if (finalStatus.contains("PARTIAL") || finalStatus.contains("UNVERIFIED")) {
                    styleToApply = partialStyle;
                } else {
                    styleToApply = workbook.createCellStyle();
                }
                
                for (int i = 0; i < headers.length; i++) {
                    if (row.getCell(i) != null) {
                        row.getCell(i).setCellStyle(styleToApply);
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(fos);
            System.out.println(" Enhanced SMS Excel Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ Enhanced SMS Excel report failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    // ========== ENHANCED SMS REPORT METHOD ==========
    public static String generateSMSTestReport(List<Map<String, Object>> results, String deviceId, String deviceNumber) {
    	String dialingNumber = System.getProperty("aPartyNumber");
        String fileName = "SMS_Detailed_Report_" + dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".html";
        String filePath = REPORT_DIR + fileName;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(generateHTMLHeader("SMS Test Detailed Report"));
            writer.println("<div class='container'>");
            writer.println("<h1>💬 SMS Test Detailed Report</h1>");
            writer.println(generateEnhancedSMSSummarySection(results, deviceId, deviceNumber));
            writer.println(generateEnhancedSMSTable(results));
            writer.println("</div></body></html>");
            
            System.out.println(" SMS Detailed Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ SMS Detailed report failed: " + e.getMessage());
            return null;
        }
    }
    
    // ========== DATA USAGE REPORT METHODS ==========
    
    public static String generateDataUsageExcelReport(List<Map<String, Object>> results) {
        String dialingNumber = System.getProperty("DaPartyNumber");
        if (dialingNumber == null || dialingNumber.isEmpty()) {
            dialingNumber = ConfigReader.getDialingNumber();
        }
        
        dialingNumber = dialingNumber.replace("+", "").replace(" ", "");
        String fileName = "DataUsage_Report_"+ dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".xlsx";
        String filePath = REPORT_DIR + fileName;
        
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("Data Usage Results");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            //  UPDATED HEADERS to match your target format
            String[] headers = {
            	"A Party Number", 
                "Target Data (GB)", 
                "Duration (min)", 
                "Apps", 
                "Initial Data", 
                "Final Data", 
                "Consumed Data", 
                "Target Achieved", 
                "APN", 
                "Network Type", 
                "Before Balance", 
                "After Balance", 
                "Balance Deduction", 
                "Final Status", 
                "Comments", 
                "Timestamp"
            };
            
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (Map<String, Object> result : results) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                
                row.createCell(col++).setCellValue(getStringValue(result.get("apartyNumber")));
                row.createCell(col++).setCellValue(getDoubleValue(result.get("targetData")));
                row.createCell(col++).setCellValue(getIntValue(result.get("duration")));
                row.createCell(col++).setCellValue(getStringValue(result.get("appsToUse")));
                row.createCell(col++).setCellValue(getStringValue(result.get("initialData")));
                row.createCell(col++).setCellValue(getStringValue(result.get("finalData")));
                row.createCell(col++).setCellValue(getStringValue(result.get("consumedData")));
                row.createCell(col++).setCellValue(getBooleanValue(result.get("targetAchieved")) ? "YES" : "NO");
                
                //  APN Column (combine name and APN VALUE, not type)
                String apnName = getStringValue(result.get("apnName"));
                String apnValue = getStringValue(result.get("apn")); 
                String apnDisplay = apnName + " (" + apnValue + ")";
                row.createCell(col++).setCellValue(apnDisplay);
                
                row.createCell(col++).setCellValue(getStringValue(result.get("networkType")));
                
                //  Balance columns
                row.createCell(col++).setCellValue(getStringValue(result.get("beforeBalance")));
                row.createCell(col++).setCellValue(getStringValue(result.get("afterBalance")));
                
                //  Balance deduction
                Object deduction = result.get("balanceDeduction");
                if (deduction instanceof Double) {
                    row.createCell(col++).setCellValue("₹" + (Double) deduction);
                } else if (deduction instanceof String) {
                    row.createCell(col++).setCellValue((String) deduction);
                } else {
                    row.createCell(col++).setCellValue(getStringValue(deduction));
                }
                
                row.createCell(col++).setCellValue(getStringValue(result.get("finalStatus")));
                row.createCell(col++).setCellValue(getStringValue(result.get("comments")));
                row.createCell(col).setCellValue(getStringValue(result.get("testTimestamp")));
                
                for (int i = 0; i < headers.length; i++) {
                    if (row.getCell(i) != null) {
                        row.getCell(i).setCellStyle(dataStyle);
                    }
                }
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(fos);
            System.out.println(" Data Usage Excel Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ Data Usage Excel report failed: " + e.getMessage());
            return null;
        }
    }
    
    public static String generateDataUsageHTMLReport(List<Map<String, Object>> results) {
    	String dialingNumber = System.getProperty("DaPartyNumber");
        if (dialingNumber == null || dialingNumber.isEmpty()) {
            dialingNumber = ConfigReader.getDialingNumber();
        }
        
        dialingNumber = dialingNumber.replace("+", "").replace(" ", "");
        String fileName = "DataUsage_Report_"+ dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".html";
        String filePath = REPORT_DIR + fileName;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(generateHTMLHeader("Data Usage Test Report"));
            writer.println("<div class='container'>");
            writer.println("<h1>🌐 Data Usage Test Report</h1>");
            writer.println(generateSummarySection(results));
            writer.println(generateDataUsageTable(results));
            writer.println("</div></body></html>");
            
            System.out.println(" Data Usage HTML Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ Data Usage HTML report failed: " + e.getMessage());
            return null;
        }
    }
    
    // ========== SIM AUTO-LATCH REPORT METHODS ==========
    
    public static String generateSIMAutoLatchExcelReport(List<Map<String, Object>> results) {
        String dialingNumber = System.getProperty("aPartyNumber");
        String fileName = "SIM_AutoLatch_Report_" + dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".xlsx";
        String filePath = REPORT_DIR + fileName;
        
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("SIM Auto-Latch Results");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle passStyle = createSuccessStyle(workbook);
            CellStyle marginalStyle = createPartialStyle(workbook);
            CellStyle slowStyle = createFailedStyle(workbook);
            
            //  CORRECTED: Added "Test Name" and "Device Type" columns
            String[] headers = {
                "Test Name",           // NEW: Added test name
                "Device ID", 
                "Device Type",         // NEW: Added device type (A-PARTY/B-PARTY)
                "Party Number", 
                "Preferred Network",
                "Timeout (s)",
                "Attempts",
                "Successful Attempts", // NEW: Added successful attempts count
                "Initial Network", 
                "Initial RAT", 
                "Initial IMS",
                "Final Network", 
                "Final RAT", 
                "Final IMS",
                "Auto-Latch Time (ms)", 
                "Auto-Latch Time (s)", 
                "Test Result",
                "Transitions",
                "Comments", 
                "Timestamp"
            };
            
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (Map<String, Object> result : results) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                
                //  CORRECTED: Write data in order matching headers
                row.createCell(col++).setCellValue(getStringValue(result.get("name")));
                row.createCell(col++).setCellValue(getStringValue(result.get("deviceId")));
                row.createCell(col++).setCellValue(getStringValue(result.get("deviceType")));
                row.createCell(col++).setCellValue(getStringValue(result.get("partyNumber")));
                row.createCell(col++).setCellValue(getStringValue(result.get("preferredNetwork")));
                row.createCell(col++).setCellValue(getIntValue(result.get("timeoutSeconds")));
                row.createCell(col++).setCellValue(getIntValue(result.get("totalAttempts")));
                row.createCell(col++).setCellValue(getIntValue(result.get("successfulAttempts")));
                row.createCell(col++).setCellValue(getStringValue(result.get("initialNetworkState")));
                row.createCell(col++).setCellValue(getStringValue(result.get("initialRAT")));
                row.createCell(col++).setCellValue(getBooleanValue(result.get("initialIMSRegistered")) ? "" : "❌");
                row.createCell(col++).setCellValue(getStringValue(result.get("finalNetworkState")));
                row.createCell(col++).setCellValue(getStringValue(result.get("finalRAT")));
                row.createCell(col++).setCellValue(getBooleanValue(result.get("finalIMSRegistered")) ? "" : "❌");
                row.createCell(col++).setCellValue(getLongValue(result.get("autoLatchTimeMs")));
                row.createCell(col++).setCellValue(getDoubleValue(result.get("autoLatchTimeSeconds")));
                row.createCell(col++).setCellValue(getStringValue(result.get("testResult")));
                row.createCell(col++).setCellValue(getStringValue(result.get("transitions")));
                row.createCell(col++).setCellValue(getStringValue(result.get("comments")));
                row.createCell(col).setCellValue(getStringValue(result.get("testTimestamp")));
                
                // Apply style based on test result
                CellStyle styleToApply = dataStyle;
                String testResult = getStringValue(result.get("testResult"));
                if ("PASS".equals(testResult)) {
                    styleToApply = passStyle;
                } else if ("MARGINAL".equals(testResult)) {
                    styleToApply = marginalStyle;
                } else if ("SLOW".equals(testResult) || "FAIL".equals(testResult) || "ERROR".equals(testResult)) {
                    styleToApply = slowStyle;
                }
                
                for (int i = 0; i < headers.length; i++) {
                    if (row.getCell(i) != null) {
                        row.getCell(i).setCellStyle(styleToApply);
                    }
                }
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(fos);
            System.out.println(" SIM Auto-Latch Excel Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ SIM Auto-Latch Excel report failed: " + e.getMessage());
            return null;
        }
    }
    
    public static String generateSIMAutoLatchHTMLReport(List<Map<String, Object>> results) {
    	String dialingNumber = System.getProperty("aPartyNumber");
        String fileName = "SIM_AutoLatch_Report_"+ dialingNumber + "_" + FILE_DATE_FORMAT.format(new Date()) + ".html";
        String filePath = REPORT_DIR + fileName;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(generateHTMLHeader("SIM Auto-Latch Test Report"));
            writer.println("<div class='container'>");
            writer.println("<h1>📡 SIM Auto-Latch Test Report</h1>");
            writer.println(generateSIMAutoLatchSummarySection(results));
            writer.println(generateSIMAutoLatchTable(results));
            writer.println("</div></body></html>");
            
            System.out.println(" SIM Auto-Latch HTML Report: " + filePath);
            return filePath;
            
        } catch (Exception e) {
            System.out.println("❌ SIM Auto-Latch HTML report failed: " + e.getMessage());
            return null;
        }
    }
    
    // ========== HTML GENERATION HELPERS ==========
    private static String generateHTMLHeader(String title) {
        return "<!DOCTYPE html>\n<html lang='en'>\n<head>\n" +
               "<meta charset='UTF-8'>\n" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
               "<title>" + title + "</title>\n" +
               "<style>\n" +
               "body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n" +
               ".container { max-width: 95%; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
               "h1 { color: #333; text-align: center; margin-bottom: 30px; }\n" +
               ".summary { background: #e8f4fd; padding: 15px; border-radius: 5px; margin-bottom: 20px; }\n" +
               ".party-info { background: #f0f8ff; padding: 10px; border-radius: 5px; margin: 10px 0; }\n" +
               ".party-info h3 { margin-top: 0; color: #2c5282; }\n" +
               "table { width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 12px; }\n" +
               "th, td { padding: 8px; text-align: left; border: 1px solid #ddd; }\n" +
               "th { background-color: #4CAF50; color: white; position: sticky; top: 0; }\n" +
               "tr:hover { background-color: #f5f5f5; }\n" +
               ".success { background-color: #d4edda; }\n" +
               ".failed { background-color: #f8d7da; }\n" +
               ".partial { background-color: #fff3cd; }\n" +
               ".network-info { font-size: 11px; color: #555; }\n" +
               "</style>\n" +
               "</head>\n<body>\n";
    }
    
    private static String generateEnhancedSummarySection(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "<div class='summary'><p>No test results available</p></div>";
        }
        
        int total = results.size();
        long success = results.stream().filter(r -> "SUCCESS".equals(r.get("finalStatus"))).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.get("finalStatus"))).count();
        long partial = results.stream().filter(r -> "PARTIAL_SUCCESS".equals(r.get("finalStatus"))).count();
        
        // Calculate direction breakdown
        long outgoing = results.stream().filter(r -> "OUTGOING".equals(r.get("direction"))).count();
        long incoming = results.stream().filter(r -> "INCOMING".equals(r.get("direction"))).count();
        
        // Calculate auto-answer stats
        long autoAnswerTests = results.stream().filter(r -> getBooleanValue(r.get("autoAnswerEnabled"))).count();
        long manualTests = total - autoAnswerTests;
        
        // Calculate average ring time
        double avgRingTime = results.stream()
            .mapToInt(r -> getIntValue(r.get("ringTime")))
            .average()
            .orElse(0.0);
        
        StringBuilder summary = new StringBuilder();
        summary.append("<div class='summary'>\n");
        summary.append("<h3>📊 Test Summary</h3>\n");
        summary.append("<div style='display: grid; grid-template-columns: 1fr 1fr; gap: 20px;'>\n");
        
        // Left column
        summary.append("<div>\n");
        summary.append("<h4>Overall Results</h4>\n");
        summary.append("<p><strong>Total Tests:</strong> ").append(total).append("</p>\n");
        summary.append("<p><strong> Success:</strong> ").append(success).append("</p>\n");
        summary.append("<p><strong>⚠️ Partial:</strong> ").append(partial).append("</p>\n");
        summary.append("<p><strong>❌ Failed:</strong> ").append(failed).append("</p>\n");
        summary.append("<p><strong>Success Rate:</strong> ")
            .append(String.format("%.1f%%", (success * 100.0 / total)))
            .append("</p>\n");
        summary.append("</div>\n");
        
        // Right column
        summary.append("<div>\n");
        summary.append("<h4>Call Statistics</h4>\n");
        summary.append("<p><strong>📤 Outgoing:</strong> ").append(outgoing).append("</p>\n");
        summary.append("<p><strong>📥 Incoming:</strong> ").append(incoming).append("</p>\n");
        summary.append("<p><strong>🤖 Auto-Answer:</strong> ").append(autoAnswerTests).append("</p>\n");
        summary.append("<p><strong>👤 Manual:</strong> ").append(manualTests).append("</p>\n");
        summary.append("<p><strong>⏱️ Avg Ring Time:</strong> ")
            .append(String.format("%.1f", avgRingTime))
            .append("s</p>\n");
        summary.append("</div>\n");
        
        summary.append("</div>\n");
        summary.append("<p><strong>Generated:</strong> ").append(DATE_FORMAT.format(new Date())).append("</p>\n");
        summary.append("</div>\n");
        
        return summary.toString();
    }
    
    private static String generateSummarySection(List<Map<String, Object>> results) {
        int total = results.size();
        long success = results.stream().filter(r -> "SUCCESS".equals(r.get("finalStatus"))).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.get("finalStatus"))).count();
        
        return "<div class='summary'>\n" +
               "<h3>Test Summary</h3>\n" +
               "<p><strong>Total Tests:</strong> " + total + "</p>\n" +
               "<p><strong>Successful:</strong> " + success + " | <strong>Failed:</strong> " + failed + "</p>\n" +
               "<p><strong>Success Rate:</strong> " + String.format("%.2f", (success * 100.0 / total)) + "%</p>\n" +
               "<p><strong>Generated:</strong> " + DATE_FORMAT.format(new Date()) + "</p>\n" +
               "</div>\n";
    }
    
    private static String generateEnhancedCallingTable(List<Map<String, Object>> results) {
        StringBuilder html = new StringBuilder();
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>Test Name</th><th>Direction</th><th>From → To</th>");
        html.append("<th>Ring Time</th><th>Duration</th><th>Auto Answer</th>");
        html.append("<th>Network</th><th>Call Status</th><th>Final Status</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");
        
        for (Map<String, Object> result : results) {
            String statusClass = getStatusClass(getStringValue(result.get("finalStatus")));
            html.append("<tr class='").append(statusClass).append("'>\n");
            
            // Test Name
            html.append("<td>").append(getStringValue(result.get("name"))).append("</td>\n");
            
            // Direction with icon
            String direction = getStringValue(result.get("direction"));
            String dirIcon = "INCOMING".equals(direction) ? "📥" : "📤";
            html.append("<td>").append(dirIcon).append(" ").append(direction).append("</td>\n");
            
            // From → To
            String caller = getStringValue(result.get("callerNumber"));
            String receiver = getStringValue(result.get("receiverNumber"));
            html.append("<td>").append(caller).append(" → ").append(receiver).append("</td>\n");
            
            // Ring Time
            int ringTime = getIntValue(result.get("ringTime"));
            html.append("<td>").append(ringTime > 0 ? ringTime + "s" : "-").append("</td>\n");
            
            // Duration
            int actualDuration = getIntValue(result.get("actualDuration"));
            int targetDuration = getIntValue(result.get("duration"));
            html.append("<td>").append(actualDuration).append("s / ").append(targetDuration).append("s</td>\n");
            
            // Auto Answer
            boolean autoAnswer = getBooleanValue(result.get("autoAnswerEnabled"));
            String autoAnswerIcon = autoAnswer ? " YES" : "👤 NO";
            html.append("<td>").append(autoAnswerIcon);
            
            String handling = getStringValue(result.get("callHandling"));
            if (!autoAnswer && handling != null) {
                html.append("<br><small>(").append(handling).append(")</small>");
            }
            html.append("</td>\n");
            
            // Network Info
            String aNetwork = getStringValue(result.get("aPartyNetworkType"));
            String aVolte = getStringValue(result.get("aPartyVolteEnabled"));
            html.append("<td>").append(aNetwork);
            if ("true".equals(aVolte)) {
                html.append(" <span style='color: green;'>✓ VoLTE</span>");
            }
            html.append("</td>\n");
            
            // Call Status
            html.append("<td>").append(getStringValue(result.get("callStatus"))).append("</td>\n");
            
            // Final Status
            String finalStatus = getStringValue(result.get("finalStatus"));
            String statusIcon = getStatusIcon(finalStatus);
            html.append("<td><strong>").append(statusIcon).append(" ").append(finalStatus).append("</strong></td>\n");
            
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n");
        return html.toString();
    }
    
    private static String generateEnhancedSMSSummarySection(List<Map<String, Object>> results, String deviceId, String deviceNumber) {
        if (results.isEmpty()) {
            return "<div class='summary'><p>No test results available</p></div>";
        }
        
        int total = results.size();
        long success = results.stream().filter(r -> "SUCCESS".equals(r.get("finalStatus"))).count();
        long partial = results.stream().filter(r -> "PARTIAL_SUCCESS".equals(r.get("finalStatus"))).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.get("finalStatus"))).count();
        long error = results.stream().filter(r -> "ERROR".equals(r.get("finalStatus"))).count();
        
        // Calculate SMS statistics
        int totalSMS = results.stream()
            .mapToInt(r -> (Integer) r.getOrDefault("totalSMS", 0))
            .sum();
        
        int successfulSMS = results.stream()
            .mapToInt(r -> (Integer) r.getOrDefault("successfulSMS", 0))
            .sum();
        
        // Calculate by message type
        Map<String, Long> byMessageType = results.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> (String) r.getOrDefault("messageType", "sms"),
                java.util.stream.Collectors.counting()
            ));
        
        // Calculate by direction
        Map<String, Long> byDirection = results.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> (String) r.getOrDefault("direction", "UNKNOWN"),
                java.util.stream.Collectors.counting()
            ));
        
        StringBuilder summary = new StringBuilder();
        summary.append("<div class='summary'>\n")
               .append("<h3>📊 SMS Test Summary</h3>\n")
               .append("<div style='display: flex; justify-content: space-between;'>\n")
               .append("<div style='flex: 1; margin-right: 20px;'>\n")
               .append("<h4>Device Information</h4>\n")
               .append("<p><strong>Device ID:</strong> ").append(deviceId).append("</p>\n")
               .append("<p><strong>Device Number:</strong> ").append(deviceNumber).append("</p>\n")
               .append("<p><strong>Total Tests:</strong> ").append(total).append("</p>\n")
               .append("<p><strong>Generated:</strong> ").append(DATE_FORMAT.format(new Date())).append("</p>\n")
               .append("</div>\n")
               .append("<div style='flex: 1;'>\n")
               .append("<h4>Test Results</h4>\n")
               .append("<p><strong> Success:</strong> ").append(success).append("</p>\n")
               .append("<p><strong>⚠️ Partial Success:</strong> ").append(partial).append("</p>\n")
               .append("<p><strong>❌ Failed:</strong> ").append(failed).append("</p>\n")
               .append("<p><strong>🚨 Error:</strong> ").append(error).append("</p>\n")
               .append("<p><strong>Overall Success Rate:</strong> ")
               .append(String.format("%.1f%%", (success + partial) * 100.0 / total))
               .append("</p>\n")
               .append("</div>\n")
               .append("</div>\n")
               .append("<div style='margin-top: 20px;'>\n")
               .append("<h4>SMS Delivery Statistics</h4>\n")
               .append("<p><strong>Total SMS Sent:</strong> ").append(totalSMS).append("</p>\n")
               .append("<p><strong>Successful SMS:</strong> ").append(successfulSMS).append("</p>\n")
               .append("<p><strong>Failed SMS:</strong> ").append(totalSMS - successfulSMS).append("</p>\n")
               .append("<p><strong>SMS Success Rate:</strong> ")
               .append(totalSMS > 0 ? String.format("%.1f%%", (successfulSMS * 100.0 / totalSMS)) : "N/A")
               .append("</p>\n")
               .append("</div>\n");
        
        // Add breakdown by message type
        if (!byMessageType.isEmpty()) {
            summary.append("<div style='margin-top: 20px;'>\n")
                   .append("<h4>Breakdown by Message Type</h4>\n")
                   .append("<table style='width: 50%; border-collapse: collapse; font-size: 12px;'>\n")
                   .append("<tr><th>Message Type</th><th>Count</th><th>Percentage</th></tr>\n");
            
            for (Map.Entry<String, Long> entry : byMessageType.entrySet()) {
                double percentage = entry.getValue() * 100.0 / total;
                summary.append("<tr>")
                       .append("<td>").append(entry.getKey().toUpperCase()).append("</td>")
                       .append("<td>").append(entry.getValue()).append("</td>")
                       .append("<td>").append(String.format("%.1f%%", percentage)).append("</td>")
                       .append("</tr>\n");
            }
            
            summary.append("</table>\n").append("</div>\n");
        }
        
        // Add breakdown by direction
        if (!byDirection.isEmpty()) {
            summary.append("<div style='margin-top: 20px;'>\n")
                   .append("<h4>Breakdown by Direction</h4>\n")
                   .append("<table style='width: 50%; border-collapse: collapse; font-size: 12px;'>\n")
                   .append("<tr><th>Direction</th><th>Count</th><th>Percentage</th></tr>\n");
            
            for (Map.Entry<String, Long> entry : byDirection.entrySet()) {
                double percentage = entry.getValue() * 100.0 / total;
                summary.append("<tr>")
                       .append("<td>").append(entry.getKey()).append("</td>")
                       .append("<td>").append(entry.getValue()).append("</td>")
                       .append("<td>").append(String.format("%.1f%%", percentage)).append("</td>")
                       .append("</tr>\n");
            }
            
            summary.append("</table>\n").append("</div>\n");
        }
        
        summary.append("</div>\n");
        return summary.toString();
    }
    
    private static String generateEnhancedSMSTable(List<Map<String, Object>> results) {
        StringBuilder html = new StringBuilder();
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>Test Name</th><th>Type</th><th>Direction</th><th>Recipient/Group</th>");
        html.append("<th>SMS Count</th><th>Successful</th><th>Failed</th><th>Sender Time</th>");
        html.append("<th>Receiver Time/Status</th><th>Network</th>");  //  Updated column name
        html.append("<th>Device</th><th>Final Status</th><th>Timestamp</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");
        
        for (Map<String, Object> result : results) {
            String statusClass = getStatusClass(getStringValue(result.get("finalStatus")));
            html.append("<tr class='").append(statusClass).append("'>\n");
            
            // Test Name
            html.append("<td>").append(getStringValue(result.get("name"))).append("</td>\n");
            
            // Message Type
            String messageType = getStringValue(result.get("messageType"));
            String typeIcon = "📝";
            if ("voice".equalsIgnoreCase(messageType)) typeIcon = "🎤";
            else if ("mms".equalsIgnoreCase(messageType)) typeIcon = "🖼️";
            html.append("<td>").append(typeIcon).append(" ").append(messageType.toUpperCase()).append("</td>\n");
            
            // Direction
            String direction = getStringValue(result.get("direction"));
            String directionIcon = "↔️";
            if ("INCOMING".equals(direction)) directionIcon = "📥";
            else if ("OUTGOING".equals(direction)) directionIcon = "📤";
            html.append("<td>").append(directionIcon).append(" ").append(direction).append("</td>\n");
            
            // Recipient/Group
            boolean isIndividual = getBooleanValue(result.get("isIndividual"));
            if (isIndividual) {
                html.append("<td>").append(getStringValue(result.get("recipient"))).append("</td>\n");
            } else {
                String groupName = getStringValue(result.get("groupName"));
                int participants = getIntValue(result.get("participantCount"));
                html.append("<td>").append(groupName).append("<div class='network-info'>")
                    .append(participants).append(" participants</div></td>\n");
            }
            
            // SMS Count
            int totalSMS = getIntValue(result.get("totalSMS"));
            int successfulSMS = getIntValue(result.get("successfulSMS"));
            int failedSMS = getIntValue(result.get("failedSMS"));
            html.append("<td>").append(totalSMS).append("</td>\n");
            
            // Successful SMS
            html.append("<td>").append(successfulSMS).append("</td>\n");
            
         //  NEW: Receiver Timestamp with status handling
            String receiverTime = getStringValue(result.get("receiverTime"));
            String verificationStatus = getStringValue(result.get("verificationStatus"));
            String deliveryStatus = getStringValue(result.get("deliveryStatus"));
            
            // Determine what to display
            String receiverDisplay;
            if (receiverTime != null && !receiverTime.isEmpty() && !receiverTime.equals("N/A")) {
                if (receiverTime.equals("DEVICE_UNAVAILABLE")) {
                    receiverDisplay = "<span style='color: orange;'>⚠️ DEVICE_UNAVAILABLE</span>";
                } else {
                    receiverDisplay = receiverTime;
                }
            } else if (verificationStatus != null && !verificationStatus.isEmpty()) {
                receiverDisplay = getVerificationStatusDisplay(verificationStatus);
            } else if (deliveryStatus != null && !deliveryStatus.isEmpty()) {
                receiverDisplay = getDeliveryStatusDisplay(deliveryStatus);
            } else {
                receiverDisplay = "N/A";
            }
            
            html.append("<td>").append(receiverDisplay).append("</td>\n");
            
            // Failed SMS
            html.append("<td>").append(failedSMS).append("</td>\n");
            
            // Network
            html.append("<td>").append(getStringValue(result.get("networkType"))).append("</td>\n");
            
            // Device Info
            String deviceNumber = getStringValue(result.get("deviceNumber"));
            String deviceId = getStringValue(result.get("deviceId"));
            html.append("<td>").append(deviceNumber).append("<div class='network-info'>")
                .append(deviceId).append("</div></td>\n");
            
            // Final Status
            String finalStatus = getStringValue(result.get("finalStatus"));
            String statusIcon = "❓";
            if (finalStatus.contains("SUCCESS") && !finalStatus.contains("PARTIAL")) statusIcon = "";
            else if (finalStatus.contains("PARTIAL")) statusIcon = "⚠️";
            else if (finalStatus.contains("FAILED")) statusIcon = "❌";
            else if (finalStatus.contains("ERROR")) statusIcon = "🚨";
            html.append("<td><strong>").append(statusIcon).append(" ").append(finalStatus).append("</strong></td>\n");
            
            // Timestamp
            html.append("<td>").append(getStringValue(result.get("testTimestamp"))).append("</td>\n");
            
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n");
        return html.toString();
    }
    
    private static String generateDataUsageTable(List<Map<String, Object>> results) {
        StringBuilder html = new StringBuilder();
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>Scenario</th><th>A Party Number</th><th>Target (GB)</th><th>Duration (min)</th><th>Consumed</th>"); //  ADDED
        html.append("<th>Target Achieved</th><th>Network</th><th>Final Status</th><th>Comments</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");
        
        for (Map<String, Object> result : results) {
            String statusClass = getStatusClass(getStringValue(result.get("finalStatus")));
            html.append("<tr class='").append(statusClass).append("'>\n");
            html.append("<td>").append(getStringValue(result.get("apartyNumber"))).append("</td>\n");
            html.append("<td>").append(getDoubleValue(result.get("targetData"))).append("</td>\n");
            html.append("<td>").append(getIntValue(result.get("duration"))).append("</td>\n");
            html.append("<td>").append(getStringValue(result.get("consumedData"))).append("</td>\n");
            html.append("<td>").append(getBooleanValue(result.get("targetAchieved")) ? "YES" : "NO").append("</td>\n");
            html.append("<td>").append(getStringValue(result.get("networkType"))).append("</td>\n");
            html.append("<td><strong>").append(getStringValue(result.get("finalStatus"))).append("</strong></td>\n");
            html.append("<td>").append(getStringValue(result.get("comments"))).append("</td>\n");
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n");
        return html.toString();
    }
    
    private static String generateSIMAutoLatchSummarySection(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "<div class='summary'><p>No test results available</p></div>";
        }
        
        int total = results.size();
        long pass = results.stream().filter(r -> "PASS".equals(r.get("testResult"))).count();
        long marginal = results.stream().filter(r -> "MARGINAL".equals(r.get("testResult"))).count();
        long slow = results.stream().filter(r -> "SLOW".equals(r.get("testResult"))).count();
        long failed = results.stream().filter(r -> "FAIL".equals(r.get("testResult")) || "ERROR".equals(r.get("testResult"))).count();
        
        // Calculate average time for successful tests
        List<Double> successfulTimes = new ArrayList<>();
        for (Map<String, Object> result : results) {
            if (!"FAIL".equals(result.get("testResult")) && !"ERROR".equals(result.get("testResult"))) {
                Double timeSec = (Double) result.get("autoLatchTimeSeconds");
                if (timeSec != null && timeSec > 0) {
                    successfulTimes.add(timeSec);
                }
            }
        }
        
        double avgTime = successfulTimes.isEmpty() ? 0 : 
            successfulTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        return "<div class='summary'>\n" +
               "<h3>Test Summary</h3>\n" +
               "<p><strong>Total Tests:</strong> " + total + "</p>\n" +
               "<p><strong>Pass (＜30s):</strong> " + pass + " | <strong>Marginal (30-60s):</strong> " + marginal + 
               " | <strong>Slow (＞60s):</strong> " + slow + " | <strong>Failed:</strong> " + failed + "</p>\n" +
               "<p><strong>Success Rate:</strong> " + String.format("%.2f", ((pass + marginal) * 100.0 / total)) + "%</p>\n" +
               "<p><strong>Average Auto-Latch Time:</strong> " + String.format("%.2f", avgTime) + " seconds</p>\n" +
               "<p><strong>Generated:</strong> " + DATE_FORMAT.format(new Date()) + "</p>\n" +
               "</div>\n";
    }
    
    private static String generateSIMAutoLatchTable(List<Map<String, Object>> results) {
        StringBuilder html = new StringBuilder();
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>Test Name</th><th>Device</th><th>Network</th><th>Auto-Latch Time</th>");
        html.append("<th>Result</th><th>Initial State</th><th>Final State</th><th>IMS</th>");
        html.append("<th>Transitions</th><th>Comments</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");
        
        for (Map<String, Object> result : results) {
            String resultClass = getSIMAutoLatchStatusClass(getStringValue(result.get("testResult")));
            html.append("<tr class='").append(resultClass).append("'>\n");
            
            // Test Name
            html.append("<td>").append(getStringValue(result.get("name"))).append("</td>\n");
            
            // Device Info
            html.append("<td>").append(getStringValue(result.get("deviceType")))
                .append("<div class='network-info'>")
                .append(getStringValue(result.get("deviceId")))
                .append("</div></td>\n");
            
            // Network
            html.append("<td>").append(getStringValue(result.get("preferredNetwork"))).append("</td>\n");
            
            // Auto-Latch Time
            long timeMs = getLongValue(result.get("autoLatchTimeMs"));
            double timeSec = getDoubleValue(result.get("autoLatchTimeSeconds"));
            html.append("<td>").append(timeMs).append(" ms<br><small>(")
                .append(String.format("%.2f", timeSec)).append("s)</small></td>\n");
            
            // Result with color coding
            String testResult = getStringValue(result.get("testResult"));
            html.append("<td><strong>").append(testResult).append("</strong>");
            if (timeMs > 0) {
                html.append("<br><small>");
                if (timeMs <= 30000) html.append("✓ Fast (<30s)");
                else if (timeMs <= 60000) html.append("⚠ Acceptable");
                else html.append("✗ Slow (>60s)");
                html.append("</small>");
            }
            html.append("</td>\n");
            
            // Initial State
            html.append("<td>").append(getStringValue(result.get("initialNetworkState")))
                .append("<div class='network-info'>")
                .append(getStringValue(result.get("initialRAT")))
                .append(" | IMS: ").append(getBooleanValue(result.get("initialIMSRegistered")) ? "✓" : "✗")
                .append("</div></td>\n");
            
            // Final State
            html.append("<td>").append(getStringValue(result.get("finalNetworkState")))
                .append("<div class='network-info'>")
                .append(getStringValue(result.get("finalRAT")))
                .append(" | IMS: ").append(getBooleanValue(result.get("finalIMSRegistered")) ? "✓" : "✗")
                .append("</div></td>\n");
            
            // IMS Status
            boolean imsInitial = getBooleanValue(result.get("initialIMSRegistered"));
            boolean imsFinal = getBooleanValue(result.get("finalIMSRegistered"));
            html.append("<td>").append(imsInitial ? "✓" : "✗").append(" → ")
                .append(imsFinal ? "✓" : "✗").append("</td>\n");
            
            // Transitions
            html.append("<td><small>").append(getStringValue(result.get("transitions")))
                .append("</small></td>\n");
            
            // Comments
            html.append("<td>").append(getStringValue(result.get("comments"))).append("</td>\n");
            
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n");
        return html.toString();
    }
    
    // ========== STATUS HELPER METHODS ==========
    
    private static String getStatusIcon(String status) {
        if (status.contains("SUCCESS") && !status.contains("PARTIAL")) return "";
        if (status.contains("PARTIAL")) return "⚠️";
        if (status.contains("FAILED")) return "❌";
        return "❓";
    }
    
    private static String getStatusClass(String status) {
        if (status.contains("SUCCESS") && !status.contains("PARTIAL")) return "success";
        if (status.contains("FAILED")) return "failed";
        if (status.contains("PARTIAL")) return "partial";
        return "";
    }
    
    private static String getSIMAutoLatchStatusClass(String status) {
        if ("PASS".equals(status)) return "success";
        if ("MARGINAL".equals(status)) return "partial";
        if ("SLOW".equals(status) || "FAIL".equals(status) || "ERROR".equals(status)) return "failed";
        return "";
    }
    
    // ========== CELL STYLE HELPERS ==========
    
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
    
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    private static CellStyle createSuccessStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    private static CellStyle createFailedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.RED1.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    private static CellStyle createPartialStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_YELLOW.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
    
    // ========== VALUE HELPERS ==========
    
    private static String getStringValue(Object value) {
        return value == null ? "" : value.toString();
    }
    
    private static int getIntValue(Object value) {
        if (value == null) return 0;
        try {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static double getDoubleValue(Object value) {
        if (value == null) return 0.0;
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private static long getLongValue(Object value) {
        if (value == null) return 0L;
        try {
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
    private static String cleanNetworkType(String networkType) {
        if (networkType == null || networkType.isEmpty()) {
            return "Unknown";
        }
        
        // Remove trailing ",Unknown" or similar patterns
        String cleaned = networkType.replaceAll(",\\s*(Unknown|UNKNOWN|unknown)\\s*$", "");
        
        // Remove leading/trailing commas
        cleaned = cleaned.replaceAll("^,\\s*|\\s*,$", "");
        
        // If empty after cleaning, return "Unknown"
        if (cleaned.trim().isEmpty()) {
            return "Unknown";
        }
        
        return cleaned.trim();
    }
    
 //  NEW: Helper method for verification status display
    private static String getVerificationStatusDisplay(String status) {
        switch (status) {
            case "DEVICE_UNAVAILABLE":
                return "<span style='color: orange;'>⚠️ Device Unavailable</span>";
            case "NOT_RECEIVED":
                return "<span style='color: red;'>❌ Not Received</span>";
            case "RECEIVED_VIA_NOTIFICATION":
                return "<span style='color: green;'> Received (Notification)</span>";
            case "RECEIVED_IN_CONVERSATION":
                return "<span style='color: green;'> Received (Conversation)</span>";
            case "VERIFICATION_ERROR":
                return "<span style='color: red;'>🚨 Verification Error</span>";
            case "UNVERIFIED":
                return "<span style='color: gray;'>❓ Unverified</span>";
            default:
                return status;
        }
    }

    //  NEW: Helper method for delivery status display
    private static String getDeliveryStatusDisplay(String status) {
        switch (status) {
            case "SUCCESS":
                return "<span style='color: green;'> Success</span>";
            case "FAILED_SLA":
                return "<span style='color: orange;'>⚠️ Failed SLA</span>";
            case "DEVICE_UNAVAILABLE":
                return "<span style='color: orange;'>⚠️ Device Unavailable</span>";
            case "UNVERIFIED":
                return "<span style='color: gray;'>❓ Unverified</span>";
            default:
                return status;
        }
    }

    
    private static boolean getBooleanValue(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        String strVal = value.toString().toLowerCase();
        return strVal.equals("true") || strVal.equals("yes") || strVal.equals("1");
    }
}