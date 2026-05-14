package com.telecom.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  CORRECTED EXCEL READER FOR YOUR FORMAT
 * Excel Columns: Test Type | A Party Number | Recipient | Group Name | Message type | Message | Direction
 */
public class EnhancedExcelReader {
    
    // ==================== SMS TEST READER (CORRECTED FOR YOUR EXCEL) ====================
    
    public static List<Map<String, Object>> readSMSTestData(String filePath) {
        List<Map<String, Object>> testCases = new ArrayList<>();
        
        if (filePath == null || filePath.trim().isEmpty()) {
            System.out.println("❌ File path cannot be null or empty");
            return testCases;
        }
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("SMS");
            if (sheet == null) {
                System.out.println("❌ Sheet 'SMS' not found in file: " + filePath);
                return testCases;
            }
            
            System.out.println("📊 Reading SMS Test Data");
            System.out.println("=".repeat(100));
            
            Map<String, Integer> columnMap = createColumnMapping(sheet.getRow(0));
            
            System.out.println("📋 Detected Columns: " + columnMap.keySet());
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isEmptyRow(row)) continue;
                
                Map<String, Object> testCase = extractSMSTestCase(row, columnMap);
                if (testCase != null) {
                    testCases.add(testCase);
                    logSMSTestCase(testCase);
                }
            }
            
            System.out.println("=".repeat(100));
            System.out.println(" Successfully loaded " + testCases.size() + " SMS tests\n");
            printSMSTestSummary(testCases);
            
        } catch (Exception e) {
            System.out.println("❌ Error reading SMS test data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return testCases;
    }
    
    /**
     *  EXTRACT SMS TEST CASE - CORRECTED FOR YOUR EXCEL FORMAT
     * 
     * Your Excel Format:
     * Column 0: Test Type (Individual/Group)
     * Column 1: A Party Number (7413082258)
     * Column 2: Recipient (8696904544) - This is B Party Number for Individual tests
     * Column 3: Group Name (Automation or -)
     * Column 4: Message type (Text/Voice)
     * Column 5: Message (Hello test or -)
     * Column 6: Direction (OUTGOING/INCOMING)
     */
    private static Map<String, Object> extractSMSTestCase(Row row, Map<String, Integer> columnMap) {
        try {
            //  READ COLUMNS ACCORDING TO YOUR EXCEL
            String testType = getCellValue(row.getCell(columnMap.getOrDefault("test type", 0))).toUpperCase();
            String aPartyNumber = cleanPhoneNumber(getCellValue(row.getCell(columnMap.getOrDefault("a party number", 1))));
            String recipient = cleanPhoneNumber(getCellValue(row.getCell(columnMap.getOrDefault("recipient", 2))));
            String groupName = getCellValue(row.getCell(columnMap.getOrDefault("group name", 3)));
            String messageType = getCellValue(row.getCell(columnMap.getOrDefault("message type", 4))).toUpperCase();
            String message = getCellValue(row.getCell(columnMap.getOrDefault("message", 5)));
            String direction = getCellValue(row.getCell(columnMap.getOrDefault("direction", 6))).toUpperCase();
            
            //  CRITICAL: For Individual tests, recipient = B Party Number
            //  CRITICAL: For Group tests, use group name
            
            // Determine if it's individual or group based on Test Type column
            boolean isIndividual = testType.contains("INDIVIDUAL");
            boolean isGroup = testType.contains("GROUP");
            
            // If Test Type is empty, check Group Name
            if (!isIndividual && !isGroup) {
                isGroup = !groupName.isEmpty() && !groupName.equals("-");
                isIndividual = !isGroup;
            }
            
            //  For INDIVIDUAL tests: recipient column is the B Party Number
            String bPartyNumber = isIndividual ? recipient : "";
            
            // Validate required fields
            if (aPartyNumber.isEmpty()) {
                System.out.println("⚠️ Skipping test - No A Party number");
                return null;
            }
            
            if (isIndividual && bPartyNumber.isEmpty()) {
                System.out.println("⚠️ Skipping individual SMS - No B Party number (recipient)");
                return null;
            }
            
            if (isGroup && (groupName.isEmpty() || groupName.equals("-"))) {
                System.out.println("⚠️ Skipping group SMS - No group name");
                return null;
            }
            
            // Set default test type if empty
            if (testType.isEmpty()) {
                testType = isIndividual ? "INDIVIDUAL" : "GROUP";
            }
            
            // Normalize message type
            String normalizedMessageType = normalizeMessageType(messageType);
            
            // Handle empty messages
            if (message.isEmpty() || message.equals("-")) {
                if (normalizedMessageType.equals("voice")) {
                    message = ""; // Voice messages don't need text
                } else {
                    message = "Test SMS from " + aPartyNumber;
                }
            }
            
            // Determine if incoming
            boolean isIncoming = direction.contains("INCOMING");
            
            //  BUILD TEST CASE
            Map<String, Object> testCase = new HashMap<>();
            testCase.put("testType", testType);
            testCase.put("isIndividual", isIndividual);
            testCase.put("isGroup", isGroup);
            testCase.put("groupName", groupName);
            testCase.put("aPartyNumber", aPartyNumber);
            testCase.put("bPartyNumber", bPartyNumber); // B Party = Recipient for individual
            testCase.put("recipient", bPartyNumber);    // Same as bPartyNumber for individual
            testCase.put("message", message);
            testCase.put("smsCount", 1);
            testCase.put("expectedMembers", isGroup ? 2 : 0);
            testCase.put("messageType", normalizedMessageType);
            testCase.put("direction", direction);
            testCase.put("isIncoming", isIncoming);
            
            //  GENERATE TEST NAME
            if (isIndividual) {
                String phoneSuffix = bPartyNumber.length() > 4 ? 
                    bPartyNumber.substring(bPartyNumber.length() - 4) : bPartyNumber;
                testCase.put("name", "SMS_" + normalizedMessageType.toUpperCase() + "_" + 
                    (isIncoming ? "IN_" : "OUT_") + phoneSuffix);
            } else {
                String groupNameClean = groupName.replace(" ", "_").replace("-", "");
                testCase.put("name", "GROUP_" + normalizedMessageType.toUpperCase() + "_" + 
                    (isIncoming ? "IN_" : "OUT_") + groupNameClean);
            }
            
            return testCase;
            
        } catch (Exception e) {
            System.out.println("❌ Error extracting SMS test case: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     *  Normalize message type
     */
    private static String normalizeMessageType(String messageType) {
        if (messageType == null || messageType.trim().isEmpty()) return "text";
        
        String lower = messageType.toLowerCase().trim();
        
        if (lower.contains("voice") || lower.contains("audio")) {
            return "voice";
        }
        
        if (lower.contains("mms") || lower.contains("multimedia")) {
            return "mms";
        }
        
        return "text";
    }
    
    /**
     *  LOG SMS TEST CASE
     */
    private static void logSMSTestCase(Map<String, Object> testCase) {
        String emoji = (Boolean) testCase.get("isIndividual") ? "📱" : "👥";
        String direction = (Boolean) testCase.get("isIncoming") ? "← INCOMING" : "→ OUTGOING";
        String messageType = ((String) testCase.get("messageType")).toUpperCase();
        String typeEmoji = messageType.equals("VOICE") ? "🎤" : messageType.equals("MMS") ? "🖼️" : "💬";
        
        System.out.println(String.format(
            "%s %s %s %s %s",
            emoji, typeEmoji, direction, messageType, 
            (Boolean) testCase.get("isIndividual") ? "INDIVIDUAL" : "GROUP"
        ));
        
        if ((Boolean) testCase.get("isIndividual")) {
            System.out.println(String.format(
                "   A-Party: %s | B-Party: %s",
                testCase.get("aPartyNumber"),
                testCase.get("bPartyNumber")
            ));
            
            if (!((String) testCase.get("message")).isEmpty()) {
                String msg = (String) testCase.get("message");
                String displayMsg = msg.length() > 50 ? msg.substring(0, 50) + "..." : msg;
                System.out.println("   Message: '" + displayMsg + "'");
            }
        } else {
            System.out.println(String.format(
                "   Group: %s | A-Party: %s",
                testCase.get("groupName"),
                testCase.get("aPartyNumber")
            ));
            
            if (!((String) testCase.get("message")).isEmpty()) {
                String msg = (String) testCase.get("message");
                String displayMsg = msg.length() > 50 ? msg.substring(0, 50) + "..." : msg;
                System.out.println("   Message: '" + displayMsg + "'");
            }
        }
        
        System.out.println();
    }
    
    /**
     *  PRINT SMS TEST SUMMARY
     */
    private static void printSMSTestSummary(List<Map<String, Object>> testCases) {
        long individualCount = testCases.stream().filter(t -> (Boolean) t.get("isIndividual")).count();
        long groupCount = testCases.stream().filter(t -> (Boolean) t.get("isGroup")).count();
        long incomingCount = testCases.stream().filter(t -> (Boolean) t.get("isIncoming")).count();
        long outgoingCount = testCases.size() - incomingCount;
        long voiceCount = testCases.stream().filter(t -> "voice".equals(t.get("messageType"))).count();
        long textCount = testCases.stream().filter(t -> "text".equals(t.get("messageType"))).count();
        long mmsCount = testCases.stream().filter(t -> "mms".equals(t.get("messageType"))).count();
        
        System.out.println("📊 SMS TEST SUMMARY:");
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ TEST TYPE BREAKDOWN                                             │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 📱 Individual SMS:    " + String.format("%-41d", individualCount) + "│");
        System.out.println("│ 👥 Group SMS:         " + String.format("%-41d", groupCount) + "│");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ DIRECTION BREAKDOWN                                             │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 📤 Outgoing:          " + String.format("%-41d", outgoingCount) + "│");
        System.out.println("│ 📥 Incoming:          " + String.format("%-41d", incomingCount) + "│");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ MESSAGE TYPE BREAKDOWN                                          │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 💬 Text Messages:     " + String.format("%-41d", textCount) + "│");
        System.out.println("│ 🎤 Voice Messages:    " + String.format("%-41d", voiceCount) + "│");
        System.out.println("│ 🖼️ MMS Messages:      " + String.format("%-41d", mmsCount) + "│");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        
        // Group by A Party Number
        Map<String, Long> byAParty = testCases.stream()
            .collect(Collectors.groupingBy(
                t -> (String) t.getOrDefault("aPartyNumber", "UNKNOWN"),
                Collectors.counting()
            ));
        
        if (!byAParty.isEmpty()) {
            System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
            System.out.println("│ TESTS BY A-PARTY NUMBER                                         │");
            System.out.println("├─────────────────────────────────────────────────────────────────┤");
            byAParty.forEach((aParty, count) -> {
                System.out.println("│ " + String.format("%-55s", aParty + ": " + count + " tests") + " │");
            });
            System.out.println("└─────────────────────────────────────────────────────────────────┘");
        }
        
        System.out.println();
    }
    
    // ==================== SIM AUTO-LATCH TEST READER ====================
    
    public static List<Map<String, Object>> readSIMAutoLatchTestData(String filePath) {
        List<Map<String, Object>> testCases = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("SIM_Auto_Latch");
            if (sheet == null) {
                System.out.println("⚠️ Sheet 'SIM_Auto_Latch' not found in Excel file");
                return testCases;
            }
            
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndex = new HashMap<>();
            
            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim().toLowerCase();
                columnIndex.put(header, cell.getColumnIndex());
            }
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Map<String, Object> testCase = new HashMap<>();
                
                if (columnIndex.containsKey("aparty number")) {
                    Cell cell = row.getCell(columnIndex.get("aparty number"));
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING:
                                testCase.put("partyNumber", cell.getStringCellValue().trim());
                                break;
                            case NUMERIC:
                                testCase.put("partyNumber", String.valueOf((long) cell.getNumericCellValue()));
                                break;
                            default:
                                testCase.put("partyNumber", "");
                        }
                    } else {
                        testCase.put("partyNumber", "");
                    }
                }
                
                if (columnIndex.containsKey("network & auto latch")) {
                    Cell cell = row.getCell(columnIndex.get("network & auto latch"));
                    String network = (cell != null && cell.getCellType() == CellType.STRING) ? 
                                   cell.getStringCellValue().trim() : "AUTO";
                    testCase.put("preferredNetwork", network.isEmpty() ? "AUTO" : network);
                } else {
                    testCase.put("preferredNetwork", "AUTO");
                }
                
                if (columnIndex.containsKey("auto latch time (s)")) {
                    Cell cell = row.getCell(columnIndex.get("auto latch time (s)"));
                    int timeout = 120;
                    if (cell != null) {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            timeout = (int) cell.getNumericCellValue();
                        } else if (cell.getCellType() == CellType.STRING && 
                                  !cell.getStringCellValue().trim().isEmpty()) {
                            try {
                                timeout = Integer.parseInt(cell.getStringCellValue().trim());
                            } catch (NumberFormatException e) {
                                timeout = 120;
                            }
                        }
                    }
                    testCase.put("timeoutSeconds", timeout);
                } else {
                    testCase.put("timeoutSeconds", 120);
                }
                
                if (columnIndex.containsKey("attempts")) {
                    Cell cell = row.getCell(columnIndex.get("attempts"));
                    int attempts = 1;
                    if (cell != null) {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            attempts = (int) cell.getNumericCellValue();
                        } else if (cell.getCellType() == CellType.STRING && 
                                  !cell.getStringCellValue().trim().isEmpty()) {
                            try {
                                attempts = Integer.parseInt(cell.getStringCellValue().trim());
                            } catch (NumberFormatException e) {
                                attempts = 1;
                            }
                        }
                    }
                    testCase.put("attempts", attempts);
                } else {
                    testCase.put("attempts", 1);
                }
                
                testCase.put("name", String.format("SIM Auto-Latch: %s -> %s (%ds, %d attempts)",
                    testCase.get("partyNumber"),
                    testCase.get("preferredNetwork"),
                    testCase.get("timeoutSeconds"),
                    testCase.get("attempts")));
                
                if (testCase.get("partyNumber") != null && 
                    !((String) testCase.get("partyNumber")).isEmpty()) {
                    testCases.add(testCase);
                }
            }
            
            System.out.println(" Read " + testCases.size() + " SIM auto-latch test cases from Excel");
            
        } catch (Exception e) {
            System.out.println("❌ Error reading SIM auto-latch test data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return testCases;
    }
    
    // ==================== CALLING TEST READER ====================
    
 // ==================== CALLING TEST READER ====================
    
    public static List<Map<String, Object>> readCallingTestData(String filePath) {
        List<Map<String, Object>> testCases = new ArrayList<>();
        
        if (filePath == null || filePath.trim().isEmpty()) {
            System.out.println("❌ File path cannot be null or empty");
            return testCases;
        }
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("Calling");
            if (sheet == null) {
                System.out.println("❌ Sheet 'Calling' not found in file: " + filePath);
                return testCases;
            }
            
            System.out.println("📊 Reading CALLING Test Data");
            System.out.println("=".repeat(100));
            
            Map<String, Integer> columnMap = createColumnMapping(sheet.getRow(0));
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isEmptyRow(row)) continue;
                
                Map<String, Object> testCase = extractCallingTestCase(row, columnMap);
                if (testCase != null) {
                    testCases.add(testCase);
                    logCallingTestCase(testCase);
                }
            }
            
            System.out.println("=".repeat(100));
            System.out.println(" Successfully loaded " + testCases.size() + " calling tests\n");
            printCallingTestSummary(testCases);
            
        } catch (Exception e) {
            System.out.println("❌ Error reading calling test data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return testCases;
    }
    
    private static Map<String, Object> extractCallingTestCase(Row row, Map<String, Integer> columnMap) {
        try {
            String testName = getCellValue(row.getCell(columnMap.getOrDefault("name", 0)));
            String aPartyNumber = cleanPhoneNumber(getCellValue(row.getCell(columnMap.getOrDefault("a party number", 1))));
            String bPartyNumber = cleanPhoneNumber(getCellValue(row.getCell(columnMap.getOrDefault("b party number", 2))));
            String cPartyNumber = cleanPhoneNumber(getCellValue(row.getCell(columnMap.getOrDefault("c party number", 3))));
            String preferredNetwork = getCellValue(row.getCell(columnMap.getOrDefault("preferred network", 4)));
            String volteSupported = getCellValue(row.getCell(columnMap.getOrDefault("volte supported", 5)));
            String durationStr = getCellValue(row.getCell(columnMap.getOrDefault("duration (s)", 6)));
            String attemptsStr = getCellValue(row.getCell(columnMap.getOrDefault("attempts", 7)));
            String callType = getCellValue(row.getCell(columnMap.getOrDefault("call type", 8)));
            String direction = getCellValue(row.getCell(columnMap.getOrDefault("direction", 9)));
            
            if (bPartyNumber.isEmpty()) {
                return null;
            }
            
            int duration = parseIntValue(durationStr, 15);
            int attempts = parseIntValue(attemptsStr, 1);
            
            String normalizedNetwork = normalizeNetworkType(preferredNetwork);
            boolean isVolteTest = parseBoolean(volteSupported);
            String normalizedCallType = normalizeCallType(callType);
            String normalizedDirection = normalizeDirection(direction);
            
            Map<String, Object> testCase = new HashMap<>();
            testCase.put("name", testName.isEmpty() ? "Test_" + System.nanoTime() : testName);
            testCase.put("aPartyNumber", aPartyNumber);
            testCase.put("bPartyNumber", bPartyNumber);
            testCase.put("cPartyNumber", cPartyNumber);
            testCase.put("preferredNetwork", normalizedNetwork);
            testCase.put("volteSupported", isVolteTest);
            testCase.put("volteRequired", isVolteTest && normalizedCallType.equals("VOICE"));
            testCase.put("duration", duration);
            testCase.put("attempts", attempts);
            testCase.put("callType", normalizedCallType);
            testCase.put("direction", normalizedDirection);
            testCase.put("isConference", !cPartyNumber.isEmpty());
            testCase.put("isIncoming", normalizedDirection.equals("INCOMING"));
            
            return testCase;
            
        } catch (Exception e) {
            System.out.println("❌ Error extracting calling test case: " + e.getMessage());
            return null;
        }
    }
    
    private static void logCallingTestCase(Map<String, Object> testCase) {
        String emoji = getCallTypeEmoji((String) testCase.get("callType"));
        String direction = (Boolean) testCase.get("isIncoming") ? "←" : "→";
        String conference = (Boolean) testCase.get("isConference") ? " [CONF]" : "";
        String volte = (Boolean) testCase.get("volteSupported") ? " [VoLTE]" : "";
        
        System.out.println(String.format(
            "%s %s %-40s | %s -> %s | %s | %3ds | %d attempts%s%s",
            emoji, direction, testCase.get("name"),
            testCase.get("aPartyNumber"), testCase.get("bPartyNumber"),
            testCase.get("preferredNetwork"), testCase.get("duration"),
            testCase.get("attempts"), volte, conference
        ));
    }
    
    private static void printCallingTestSummary(List<Map<String, Object>> testCases) {
        Map<String, Long> networkCount = testCases.stream()
            .collect(Collectors.groupingBy(t -> (String) t.get("preferredNetwork"), Collectors.counting()));
        
        Map<String, Long> typeCount = testCases.stream()
            .collect(Collectors.groupingBy(t -> (String) t.get("callType"), Collectors.counting()));
        
        long volteCount = testCases.stream().filter(t -> (Boolean) t.get("volteSupported")).count();
        long conferenceCount = testCases.stream().filter(t -> (Boolean) t.get("isConference")).count();
        long incomingCount = testCases.stream().filter(t -> (Boolean) t.get("isIncoming")).count();
        
        System.out.println("📊 CALLING TEST SUMMARY:");
        System.out.println("   Networks: " + networkCount);
        System.out.println("   Call Types: " + typeCount);
        System.out.println("   VoLTE Tests: " + volteCount);
        System.out.println("   Conference Calls: " + conferenceCount);
        System.out.println("   Incoming Calls: " + incomingCount + "\n");
    }
    
    public static List<Map<String, Object>> readDataUsageTestData(String filePath, String sheetName) {
        List<Map<String, Object>> dataTests = new ArrayList<>();
        
        if (filePath == null || filePath.trim().isEmpty()) {
            System.out.println("❌ File path cannot be null or empty");
            return dataTests;
        }
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                System.out.println("❌ Sheet '" + sheetName + "' not found");
                return dataTests;
            }
            
            System.out.println("📄 Reading Data Usage Test Data: " + sheetName);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isEmptyRow(row)) continue;
                
                String scenario = getCellValue(row.getCell(0));
                double targetGB = parseDoubleValue(getCellValue(row.getCell(1)), 0.5);
                int durationMin = parseIntValue(getCellValue(row.getCell(2)), 15);
                String apps = getCellValue(row.getCell(3));
                String criteria = getCellValue(row.getCell(4));
                
                if (!scenario.isEmpty()) {
                    Map<String, Object> dataTest = new HashMap<>();
                    dataTest.put("scenario", scenario);
                    dataTest.put("targetData", targetGB);
                    dataTest.put("duration", durationMin);
                    dataTest.put("appsToUse", apps.isEmpty() ? "Browser,YouTube" : apps);
                    dataTest.put("criteria", criteria.isEmpty() ? "Data consumption validation" : criteria);
                    dataTests.add(dataTest);
                    
                    System.out.println(" " + scenario + " | " + targetGB + " GB | " + durationMin + " min");
                }
            }
            
            System.out.println(" Loaded " + dataTests.size() + " data usage tests");
            
        } catch (Exception e) {
            System.out.println("❌ Error reading data usage test data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return dataTests;
    }

    
    // ==================== HELPER METHODS ====================
    
    private static Map<String, Integer> createColumnMapping(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String headerName = getCellValue(cell).toLowerCase().trim();
                if (!headerName.isEmpty()) {
                    columnMap.put(headerName, cell.getColumnIndex());
                }
            }
        }
        return columnMap;
    }
    
    private static boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            String value = getCellValue(cell);
            if (value != null && !value.trim().isEmpty() && !value.equals("-")) {
                return false;
            }
        }
        return true;
    }
    
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numValue = cell.getNumericCellValue();
                        if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                            return String.valueOf((long) numValue);
                        } else {
                            return String.valueOf(numValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    private static String cleanPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty() || phone.equals("-")) return "";
        String cleaned = phone.replaceAll("[^0-9+]", "").trim();
        if (cleaned.startsWith("++")) {
            cleaned = "+" + cleaned.substring(2);
        }
        return cleaned;
    }
    
    private static String normalizeNetworkType(String network) {
        if (network == null || network.trim().isEmpty()) return "AUTO";
        String upper = network.toUpperCase().trim();
        
        if (upper.contains("5G")) return "5G";
        if (upper.contains("4G") || upper.contains("LTE")) return "4G";
        if (upper.contains("3G")) return "3G";
        if (upper.contains("2G")) return "2G";
        if (upper.contains("AUTO")) return "AUTO";
        
        return "AUTO";
    }
    
    private static String normalizeCallType(String type) {
        if (type == null || type.trim().isEmpty()) return "VOICE";
        String upper = type.toUpperCase().trim();
        
        if (upper.contains("VIDEO")) return "VIDEO";
        if (upper.contains("CONFERENCE") || upper.contains("3-WAY")) return "CONFERENCE";
        if (upper.contains("VOLTE")) return "VOLTE";
        
        return "VOICE";
    }
    
    private static String normalizeDirection(String direction) {
        if (direction == null || direction.trim().isEmpty()) return "OUTGOING";
        String upper = direction.toUpperCase().trim();
        
        if (upper.contains("INCOMING") || upper.contains("INBOUND") || upper.equals("←")) return "INCOMING";
        
        return "OUTGOING";
    }
    
    private static int parseIntValue(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            String cleaned = value.replaceAll("[^0-9-]", "");
            if (cleaned.isEmpty()) return defaultValue;
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Improved double parsing
     */
    private static double parseDoubleValue(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        
        try {
            // Allow decimal points and negative numbers
            String cleaned = value.replaceAll("[^0-9.-]", "");
            if (cleaned.isEmpty()) return defaultValue;
            
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            System.out.println("⚠️ Warning: Cannot parse double value '" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    private static boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String lower = value.toLowerCase().trim();
        return lower.equals("true") || lower.equals("yes") || lower.equals("1") || lower.equals("enabled");
    }
    
    private static String getCallTypeEmoji(String type) {
        switch (type) {
            case "VOICE": return "☎️";
            case "VIDEO": return "📹";
            case "VOLTE": return "📞";
            case "CONFERENCE": return "👥";
            default: return "📱";
        }
    }
}    
    