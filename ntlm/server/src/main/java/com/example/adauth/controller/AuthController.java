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
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
// NTLM support temporarily disabled

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    // クラス初期化時のログ
    {
        System.out.println("=== AuthController INITIALIZED ===");
        System.out.println("AuthController instance created");
        System.out.println("===================================");
    }
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired(required = false)
    private AuthenticationManager authenticationManager;

    @Autowired
    private Environment environment;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        System.err.println("***** HEALTH ENDPOINT CALLED *****");
        System.err.flush();
        System.out.println("=== HEALTH CHECK ===");
        System.out.println("Health endpoint called");
        System.out.println("Timestamp: " + java.time.Instant.now());
        System.out.println("Thread: " + Thread.currentThread().getName());
        System.out.println("====================");
        System.out.flush();
        return ResponseEntity.ok("Server is running - " + java.time.Instant.now());
    }
    
    @GetMapping("/debug-test")
    public ResponseEntity<String> debugTest() {
        System.err.println("***** DEBUG TEST ENDPOINT CALLED *****");
        System.err.flush();
        
        System.out.println("=== DEBUG TEST ===");
        System.out.println("System.out working");
        System.out.flush();
        
        logger.info("Logger info working");
        logger.debug("Logger debug working"); 
        logger.error("Logger error working");
        
        return ResponseEntity.ok("Debug test completed - check console");
    }
    
    @PostMapping("/test")
    public ResponseEntity<String> test(@RequestBody(required = false) String body, HttpServletRequest request) {
        System.out.println("=== TEST ENDPOINT ===");
        System.out.println("Test endpoint called");
        System.out.println("Body: " + body);
        
        HttpSession session = request.getSession(false);
        System.out.println("Test Session ID: " + (session != null ? session.getId() : "null"));
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Test Authentication: " + (auth != null ? auth.getName() : "null"));
        
        System.out.println("=====================");
        return ResponseEntity.ok("Test successful - Session: " + (session != null ? session.getId() : "none"));
    }
    
    @PostMapping("/login-and-get-user")
    public ResponseEntity<?> loginAndGetUser(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        System.out.println("=== LOGIN AND GET USER IN ONE REQUEST ===");
        
        // ログイン処理
        ResponseEntity<?> loginResult = authenticateUser(loginRequest, request);
        
        if (loginResult.getStatusCode().is2xxSuccessful()) {
            // 同じリクエスト内でユーザー情報を取得
            ResponseEntity<?> userResult = getCurrentUser(request, null);
            System.out.println("Same request user result: " + userResult.getBody());
            return userResult;
        }
        
        return loginResult;
    }
    
    @GetMapping("/kerberos-status")
    public ResponseEntity<String> kerberosStatus() {
        System.out.println("=== KERBEROS STATUS CHECK ===");
        
        // 実際のプロファイル確認
        String[] activeProfiles = environment.getActiveProfiles();
        System.out.println("Active profiles: " + java.util.Arrays.toString(activeProfiles));
        
        // システムプロパティ確認
        System.out.println("java.security.krb5.conf: " + System.getProperty("java.security.krb5.conf"));
        System.out.println("java.security.krb5.realm: " + System.getProperty("java.security.krb5.realm"));
        System.out.println("java.security.krb5.kdc: " + System.getProperty("java.security.krb5.kdc"));
        System.out.println("sun.security.krb5.debug: " + System.getProperty("sun.security.krb5.debug"));
        
        System.out.println("=============================");
        
        return ResponseEntity.ok("Kerberos configuration status logged to console");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        
        // 確実に出力されるログ
        System.out.println("=== LOGIN REQUEST START ===");
        System.out.println("Original username: " + loginRequest.getUsername());
        System.out.println("Processed username: " + username);
        System.out.println("Password length: " + (loginRequest.getPassword() != null ? loginRequest.getPassword().length() : "null"));
        System.out.println("============================");

        logger.info("Login attempt for user: {} (original: {})", username, loginRequest.getUsername());
        logger.debug("Authentication configuration - AD Domain: {}, AD URL: {}",
            System.getProperty("ad.domain"), System.getProperty("ad.url"));

        if (authenticationManager == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new LoginResponse(
                    false,
                    "Authentication not available in current profile",
                    null,
                    null
                ));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    username,
                    loginRequest.getPassword()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // セッションに認証情報を保存
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            
            System.out.println("=== LOGIN SUCCESS ===");
            System.out.println("Session ID: " + session.getId());
            System.out.println("Authentication name: " + authentication.getName());
            System.out.println("======================");

            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

            LoginResponse response = new LoginResponse(
                true,
                "Authentication successful - Session: " + session.getId(),
                authentication.getName(),
                roles
            );
            
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            System.out.println("=== AUTHENTICATION FAILED ===");
            System.out.println("User: " + username);
            System.out.println("Error: " + e.getMessage());
            System.out.println("Exception type: " + e.getClass().getSimpleName());
            e.printStackTrace();
            System.out.println("==============================");
            
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
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request, HttpServletResponse response) {
        // 最優先で確実に出力されるログ
        System.err.println("***** GET CURRENT USER CALLED *****");
        System.err.flush();
        
        System.out.println("***** GET CURRENT USER CALLED *****");
        System.out.flush();
        System.err.println("***** TIMESTAMP: " + System.currentTimeMillis() + " *****");
        System.err.println("***** THREAD: " + Thread.currentThread().getName() + " *****");
        System.err.flush();
        
        // 強制的に標準出力に出力（バッファなし）
        System.out.flush();
        System.err.flush();
        
        System.out.println("=== GET CURRENT USER START ===");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Request method: " + request.getMethod());
        System.out.println("Remote address: " + request.getRemoteAddr());
        System.out.println("Thread: " + Thread.currentThread().getName());
        System.out.println("Timestamp: " + java.time.Instant.now());
        System.out.println("===============================");
        
        // より詳細なヘッダー情報
        System.out.println("=== ALL REQUEST HEADERS ===");
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                System.out.println(headerName + ": " + headerValue);
                
                // Authorization ヘッダーの特別処理
                if ("Authorization".equalsIgnoreCase(headerName)) {
                    System.out.println("*** AUTHORIZATION HEADER FOUND ***");
                    System.out.println("Raw value: '" + headerValue + "'");
                    System.out.println("Length: " + (headerValue != null ? headerValue.length() : 0));
                    
                    if (headerValue != null && headerValue.startsWith("Negotiate ")) {
                        String token = headerValue.substring("Negotiate ".length());
                        System.out.println("*** NEGOTIATE TOKEN ANALYSIS ***");
                        System.out.println("Token length: " + token.length());
                        System.out.println("Token preview: " + (token.length() > 100 ? token.substring(0, 100) + "..." : token));
                        
                        // Base64デコードを試行
                        try {
                            byte[] decodedToken = java.util.Base64.getDecoder().decode(token);
                            System.out.println("Decoded token length: " + decodedToken.length + " bytes");
                            
                            // 先頭バイトの確認（SPNEGO/GSSAPIトークンの識別）
                            if (decodedToken.length > 10) {
                                System.out.print("Token header bytes: ");
                                for (int i = 0; i < Math.min(16, decodedToken.length); i++) {
                                    System.out.printf("%02X ", decodedToken[i]);
                                }
                                System.out.println();
                            }
                        } catch (Exception e) {
                            System.out.println("Failed to decode Base64 token: " + e.getMessage());
                        }
                        System.out.println("*********************************");
                    }
                    System.out.println("*********************************");
                }
            }
        } else {
            System.out.println("No headers found!");
        }
        System.out.println("============================");
        
        // セッション情報
        HttpSession session = request.getSession(false);
        System.out.println("=== SESSION INFO ===");
        System.out.println("Session exists: " + (session != null));
        if (session != null) {
            System.out.println("Session ID: " + session.getId());
            System.out.println("Session creation time: " + new java.util.Date(session.getCreationTime()));
            System.out.println("Session last access: " + new java.util.Date(session.getLastAccessedTime()));
            System.out.println("Session max inactive: " + session.getMaxInactiveInterval());
            System.out.println("Session is new: " + session.isNew());
        }
        System.out.println("===================");
        
        // 認証情報の詳細確認
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("=== AUTHENTICATION INFO ===");
        System.out.println("Authentication object: " + authentication);
        System.out.println("Auth class: " + (authentication != null ? authentication.getClass().getName() : "null"));
        System.out.println("Is authenticated: " + (authentication != null ? authentication.isAuthenticated() : "null"));
        System.out.println("Principal: " + (authentication != null ? authentication.getPrincipal() : "null"));
        System.out.println("Principal class: " + (authentication != null && authentication.getPrincipal() != null ? 
                                                 authentication.getPrincipal().getClass().getName() : "null"));
        System.out.println("Name: " + (authentication != null ? authentication.getName() : "null"));
        System.out.println("Credentials: " + (authentication != null ? authentication.getCredentials() : "null"));
        System.out.println("Authorities: " + (authentication != null ? authentication.getAuthorities() : "null"));
        System.out.println("===========================");
        
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getName())) {
            
            System.out.println("=== BUILDING SUCCESS RESPONSE ===");
            String username = authentication.getName();
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            System.out.println("Username for response: " + username);
            System.out.println("Roles for response: " + roles);
            System.out.println("=================================");
            
            LoginResponse loginResponse = new LoginResponse(
                true,
                "User authenticated successfully",
                username,
                roles
            );
            
            System.out.println("Returning success response: " + loginResponse);
            return ResponseEntity.ok(loginResponse);
        }
        
        System.out.println("=== BUILDING UNAUTHORIZED RESPONSE ===");
        System.out.println("Reason: " + (authentication == null ? "No authentication" :
                                       !authentication.isAuthenticated() ? "Not authenticated" :
                                       "Anonymous user"));
        System.out.println("======================================");
        
        LoginResponse loginResponse = new LoginResponse(false, "Not authenticated", null, null);
        System.out.println("Returning unauthorized response: " + loginResponse);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(loginResponse);
    }

    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> getCurrentUserInfo(HttpServletRequest request) {
        System.out.println("=== WHOAMI ENDPOINT CALLED ===");
        System.out.println("Request URI: " + request.getRequestURI());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            Map<String, Object> response = new HashMap<>();

            String fullUsername = auth.getName();
            String domain = "";
            String username = fullUsername;

            // Extract domain and username if format is DOMAIN\\user
            if (fullUsername.contains("\\")) {
                String[] parts = fullUsername.split("\\\\", 2);
                domain = parts[0];
                username = parts[1];
            }

            response.put("username", fullUsername);
            response.put("domain", domain);
            response.put("user", username);
            response.put("authenticated", true);
            response.put("authMethod", "NTLM");

            System.out.println("NTLM authentication successful for: " + fullUsername);

            return ResponseEntity.ok(response);
        }

        // Not authenticated
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", false);
        response.put("message", "Authentication required");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
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