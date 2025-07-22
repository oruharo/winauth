package com.example.adauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdAuthApplication {

    public static void main(String[] args) {
        // 起動時の確実なログ出力
        System.err.println("***** ADAUTH APPLICATION MAIN METHOD CALLED *****");
        System.err.println("***** JAVA VERSION: " + System.getProperty("java.version") + " *****");
        System.err.println("***** OS: " + System.getProperty("os.name") + " *****");
        System.err.println("***** SPRING PROFILES ACTIVE: " + System.getProperty("spring.profiles.active") + " *****");
        System.err.flush();
        
        System.out.println("================================");
        System.out.println("AdAuth Application Starting...");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("JVM Profile Arg: " + System.getProperty("spring.profiles.active"));
        System.out.println("================================");
        
        // SpringApplicationを作成してプロファイルを確認
        SpringApplication app = new SpringApplication(AdAuthApplication.class);
        org.springframework.context.ConfigurableApplicationContext context = app.run(args);
        
        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        System.err.println("***** SPRING CONTEXT PROFILES: " + java.util.Arrays.toString(activeProfiles) + " *****");
        System.err.flush();
        
        System.out.println("=== SPRING CONTEXT INITIALIZED ===");
        System.out.println("Active Profiles: " + java.util.Arrays.toString(activeProfiles));
        System.out.println("===================================");
    }

}