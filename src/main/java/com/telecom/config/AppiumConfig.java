package com.telecom.config;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;

import java.net.URL;

public class AppiumConfig {
    
    private static AppiumDriverLocalService service;
    
    @SuppressWarnings("deprecation")
	public static URL getAppiumServerURL() {
        try {
            return new URL("http://127.0.0.1:4723/");
        } catch (Exception e) {
            throw new RuntimeException("Invalid Appium server URL", e);
        }
    }
    
    public static void startAppiumServer() {
        try {
            if (service != null && service.isRunning()) {
                System.out.println("Appium server already running");
                return;
            }
            
            AppiumServiceBuilder builder = new AppiumServiceBuilder();
            builder.withIPAddress("127.0.0.1")
                   .usingPort(4723)
                   .withArgument(GeneralServerFlag.SESSION_OVERRIDE)
                   .withArgument(GeneralServerFlag.LOG_LEVEL, "info")
                   .withArgument(GeneralServerFlag.RELAXED_SECURITY)
                   .withArgument(GeneralServerFlag.BASEPATH, "/")
                   .withTimeout(java.time.Duration.ofSeconds(60));
            
            service = AppiumDriverLocalService.buildService(builder);
            service.start();
            
            System.out.println(" Appium server started on: " + service.getUrl());
            
        } catch (Exception e) {
            System.out.println("⚠ Could not start Appium server automatically: " + e.getMessage());
            System.out.println("Please ensure Appium server is running manually on port 4723");
        }
    }
    
    public static void stopAppiumServer() {
        try {
            if (service != null && service.isRunning()) {
                service.stop();
                System.out.println(" Appium server stopped");
            }
        } catch (Exception e) {
            System.err.println("Error stopping Appium server: " + e.getMessage());
        }
    }
}
