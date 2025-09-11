package com.example.adauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdAuthApplication {

    public static void main(String[] args) {
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
        System.out.println("=== SPRING CONTEXT INITIALIZED ===");
        System.out.println("Active Profiles: " + java.util.Arrays.toString(activeProfiles));
        System.out.println("===================================");
        

    }

}