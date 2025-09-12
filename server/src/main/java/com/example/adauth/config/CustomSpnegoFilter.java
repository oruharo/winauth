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
        System.err.println("***** CUSTOM SPNEGO FILTER: " + uri + " *****");
        System.err.flush();
        
        System.out.println("=== CUSTOM SPNEGO FILTER ===");
        System.out.println("URI: " + uri);
        System.out.println("Method: " + httpRequest.getMethod());
        System.out.println("Authorization header: " + httpRequest.getHeader("Authorization"));
        System.out.println("============================");
        
        try {
            super.doFilter(request, response, chain);
            System.out.println("SPNEGO filter completed successfully for: " + uri);
        } catch (Exception e) {
            System.err.println("SPNEGO filter exception for " + uri + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}