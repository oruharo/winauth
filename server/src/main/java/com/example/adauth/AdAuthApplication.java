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
        System.out.println("================================");
        
        SpringApplication.run(AdAuthApplication.class, args);
        
        System.out.println("================================");
        System.out.println("AdAuth Application Started!");
        System.out.println("Server running on port 8082");
        System.out.println("================================");
    }

}