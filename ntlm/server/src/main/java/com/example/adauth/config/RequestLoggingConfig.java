package com.example.adauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter logFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(false);
        filter.setAfterMessagePrefix("REQUEST DATA: ");
        return filter;
    }

    @Bean
    public Filter requestDebugFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                
                System.out.println("=== INCOMING REQUEST ===");
                System.out.println("Method: " + httpRequest.getMethod());
                
                // Authorization ヘッダーをチェック
                String authHeader = httpRequest.getHeader("Authorization");
                if (authHeader != null) {
                    System.out.println("Authorization: " + (authHeader.length() > 50 ? 
                        authHeader.substring(0, 50) + "..." : authHeader));
                }
                System.out.println("URL: " + httpRequest.getRequestURL());
                System.out.println("Path: " + httpRequest.getRequestURI());
                System.out.println("Query: " + httpRequest.getQueryString());
                System.out.println("Content-Type: " + httpRequest.getContentType());
                System.out.println("Content-Length: " + httpRequest.getContentLength());
                System.out.println("Remote Address: " + httpRequest.getRemoteAddr());
                System.out.println("========================");
                
                chain.doFilter(request, response);
                
                System.out.println("=== RESPONSE ===");
                System.out.println("Status: " + httpResponse.getStatus());
                System.out.println("================");
            }
        };
    }
}