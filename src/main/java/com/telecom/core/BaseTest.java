package com.telecom.core;

import io.appium.java_client.AppiumDriver;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

public class BaseTest {
    
    protected AppiumDriver driver;
    
    @BeforeSuite
    public void beforeSuite() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  Vi SIM Toolkit Screenshot Capture Test Suite");
        System.out.println("=".repeat(70));
        System.out.println("Initializing test suite...");
    }
    
    @AfterSuite
    public void afterSuite() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Test suite execution completed");
        System.out.println("=".repeat(70));
    }
}
