package com.example.adauth.config;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;

public class NegotiateDebugFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // すべてのリクエストをログ出力（一時的にフィルター無効化）
        String requestURI = httpRequest.getRequestURI();
        System.err.println("***** NEGOTIATE DEBUG FILTER: " + requestURI + " *****");
        System.err.flush();
        
        if (!requestURI.startsWith("/api/user")) {
            System.out.println("Skipping debug for: " + requestURI);
            chain.doFilter(request, response);
            return;
        }
        
        // /api/userのリクエストのみログ出力
        System.out.println("=== DEBUG FILTER CALLED FOR /api/user ===");
        System.out.println("URI: " + httpRequest.getRequestURI());
        System.out.println("Method: " + httpRequest.getMethod());
        
        String authHeader = httpRequest.getHeader("Authorization");
        System.out.println("Authorization header: " + authHeader);
        
        if (authHeader != null && authHeader.startsWith("Negotiate ")) {
            System.out.println("=== NEGOTIATE TOKEN DEBUG ===");
            System.out.println("Full Authorization Header: " + authHeader);
            
            String token = authHeader.substring("Negotiate ".length());
            System.out.println("Token length: " + token.length());
            
            try {
                // Base64デコードを試行
                byte[] decodedToken = Base64.getDecoder().decode(token);
                System.out.println("Decoded token length: " + decodedToken.length);
                System.out.println("Token hex (first 32 bytes): " + bytesToHex(decodedToken, 32));
                
                // GSSAPI/SPNEGO のマジックナンバーをチェック
                if (decodedToken.length >= 2) {
                    System.out.println("First 2 bytes: 0x" + 
                        String.format("%02x%02x", decodedToken[0] & 0xFF, decodedToken[1] & 0xFF));
                    
                    // SPNEGO OID check (starts with 0x60)
                    if ((decodedToken[0] & 0xFF) == 0x60) {
                        System.out.println("✓ Valid SPNEGO token structure");
                    } else {
                        System.out.println("✗ Invalid SPNEGO token - wrong magic number");
                    }
                }
                
            } catch (IllegalArgumentException e) {
                System.out.println("✗ Failed to decode Base64 token: " + e.getMessage());
            }
            
            System.out.println("Request URI: " + httpRequest.getRequestURI());
            System.out.println("Remote Address: " + httpRequest.getRemoteAddr());
            System.out.println("User Agent: " + httpRequest.getHeader("User-Agent"));
            System.out.println("==============================");
        } else {
            System.out.println("No Negotiate header found");
            System.out.println("============================");
        }
        
        chain.doFilter(request, response);
    }
    
    private String bytesToHex(byte[] bytes, int maxBytes) {
        if (bytes == null) return "null";
        
        int length = Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
            if (i < length - 1) sb.append(" ");
        }
        if (bytes.length > maxBytes) {
            sb.append("...");
        }
        return sb.toString();
    }
}