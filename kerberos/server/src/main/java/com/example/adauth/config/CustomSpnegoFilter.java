package com.example.adauth.config;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomSpnegoFilter extends SpnegoAuthenticationProcessingFilter {
    
    public CustomSpnegoFilter(AuthenticationManager authenticationManager) {
        super.setAuthenticationManager(authenticationManager);
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String uri = httpRequest.getRequestURI();
        
        // SPNEGO authentication enabled for all endpoints
        System.err.println("***** CUSTOM SPNEGO FILTER: " + uri + " *****");
        System.err.flush();
        
        System.out.println("=== CUSTOM SPNEGO FILTER ===");
        System.out.println("URI: " + uri);
        System.out.println("Method: " + httpRequest.getMethod());
        String authHeader = httpRequest.getHeader("Authorization");
        System.out.println("Authorization header: " + (authHeader != null ? authHeader.substring(0, Math.min(50, authHeader.length())) + "..." : "null"));
        System.out.println("============================");
        
        try {
            System.out.println("=== CALLING SUPER.DOFILTER ===");
            System.out.flush();
            System.err.println("About to call super.doFilter for: " + uri);
            System.err.flush();
            super.doFilter(request, response, chain);
            System.out.println("SPNEGO filter completed successfully for: " + uri);
            System.err.println("Super.doFilter completed without exception for: " + uri);
        } catch (Throwable e) {
            System.err.println("***** SPNEGO FILTER EXCEPTION *****");
            System.err.println("URI: " + uri);
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Exception message: " + e.getMessage());
            System.err.println("Stack trace:");
            e.printStackTrace();
            System.err.println("**********************************");
            System.err.flush();
            
            // エラーの場合でもチェーンを続行してControllerに到達させる
            System.err.println("***** ATTEMPTING TO CONTINUE FILTER CHAIN *****");
            System.err.flush();
            try {
                System.out.println("Continuing filter chain despite SPNEGO error...");
                System.out.flush();
                chain.doFilter(request, response);
                System.out.println("Filter chain continuation successful!");
            } catch (Exception chainException) {
                System.err.println("***** FILTER CHAIN CONTINUATION FAILED *****");
                System.err.println("Chain continuation exception: " + chainException.getClass().getName());
                System.err.println("Chain continuation message: " + chainException.getMessage());
                chainException.printStackTrace();
                System.err.println("********************************************");
                System.err.flush();
                
                // チェーンの続行に失敗した場合はレスポンスを直接書き込む
                httpResponse.setStatus(401);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"success\":false,\"message\":\"SPNEGO authentication failed\"}");
                return; // throw しない
            }
        }
    }
}