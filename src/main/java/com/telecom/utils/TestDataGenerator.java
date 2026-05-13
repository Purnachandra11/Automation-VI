package com.telecom.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.util.Random;

public class TestDataGenerator {
    
    public static void generateSampleExcelFile(String filePath) {
        try (Workbook workbook = new XSSFWorkbook(); 
             FileOutputStream fos = new FileOutputStream(filePath)) {
            
            Sheet sheet = workbook.createSheet("TestContacts");
            
            // Create header row
            String[] headers = {"Name", "B Party Number", "Actual Call Duration timer", 
                              "No of attempts to call", "C Party Number", "B Party Send SMS Count"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            
            // Sample test data
            String[][] sampleData = {
                {"John Doe", "9876543210", "00:30", "2", "9876543211", "2"},
                {"Jane Smith", "9876543212", "15", "1", "", "1"},
                {"Mike Johnson", "9876543213", "00:45", "3", "9876543214", "3"},
                {"Sarah Wilson", "9876543215", "20", "1", "9876543216", "0"},
                {"Tom Brown", "9876543217", "00:25", "2", "", "2"}
            };
            
            // Add sample data
            for (int i = 0; i < sampleData.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < sampleData[i].length; j++) {
                    row.createCell(j).setCellValue(sampleData[i][j]);
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(fos);
            System.out.println("✅ Sample Excel file generated: " + filePath);
            
        } catch (Exception e) {
            System.out.println("❌ Failed to generate sample Excel file: " + e.getMessage());
        }
    }
    
    public static String generateRandomPhoneNumber() {
        Random random = new Random();
        return "98765" + String.format("%05d", random.nextInt(100000));
    }
}