package com.example.adauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        System.out.println("=== CORS Configuration Initialized ===");
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 許可するオリジン（開発環境）
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        
        // 許可するHTTPメソッド
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // 許可するヘッダー
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // クレデンシャル（Cookie、認証ヘッダー）を許可
        configuration.setAllowCredentials(true);
        
        // プリフライトリクエストのキャッシュ時間
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        System.out.println("CORS設定: " + configuration);
        return source;
    }
}