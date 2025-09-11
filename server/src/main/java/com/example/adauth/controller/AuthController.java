package com.example.adauth.controller;

import com.example.adauth.dto.LoginRequest;
import com.example.adauth.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Server is running");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        
        // DOMAIN\username形式からusernameのみを抽出
        if (username.contains("\\")) {
            username = username.substring(username.lastIndexOf("\\") + 1);
        }
        
        logger.info("Login attempt for user: {} (original: {})", username, loginRequest.getUsername());
        logger.debug("Authentication configuration - AD Domain: {}, AD URL: {}", 
            System.getProperty("ad.domain"), System.getProperty("ad.url"));
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    username,
                    loginRequest.getPassword()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

            return ResponseEntity.ok(new LoginResponse(
                true,
                "Authentication successful",
                authentication.getName(),
                roles
            ));
        } catch (AuthenticationException e) {
            logger.error("Authentication failed for user: {} - Error: {}", 
                loginRequest.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse(
                    false,
                    "Authentication failed: " + e.getMessage(),
                    null,
                    null
                ));
        }
    }

    @GetMapping("/user")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
                
            return ResponseEntity.ok(new LoginResponse(
                true,
                "User authenticated",
                authentication.getName(),
                roles
            ));
        }
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new LoginResponse(false, "Not authenticated", null, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(new LoginResponse(
            true,
            "Logout successful",
            null,
            null
        ));
    }
}