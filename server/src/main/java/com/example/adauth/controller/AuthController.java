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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private Environment environment;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        System.out.println("=== HEALTH CHECK ===");
        System.out.println("Health endpoint called");
        System.out.println("====================");
        return ResponseEntity.ok("Server is running");
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
            ResponseEntity<?> userResult = getCurrentUser(request);
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
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        System.out.println("=== GET CURRENT USER ===");
        
        // セッション情報を確認
        HttpSession session = request.getSession(false);
        System.out.println("Session ID: " + (session != null ? session.getId() : "null"));
        System.out.println("Session exists: " + (session != null));
        System.out.println("Cookie header: " + request.getHeader("Cookie"));
        System.out.println("JSESSIONID cookie: " + request.getHeader("JSESSIONID"));
        
        // Authorization ヘッダーの詳細確認
        String authHeader = request.getHeader("Authorization");
        System.out.println("=== AUTHORIZATION HEADER DEBUG ===");
        System.out.println("Authorization header: " + authHeader);
        if (authHeader != null) {
            if (authHeader.startsWith("Negotiate ")) {
                String token = authHeader.substring("Negotiate ".length());
                System.out.println("Negotiate token length: " + token.length());
                System.out.println("Token (first 50 chars): " + (token.length() > 50 ? token.substring(0, 50) + "..." : token));
            } else {
                System.out.println("Non-Negotiate auth header: " + authHeader);
            }
        } else {
            System.out.println("No Authorization header found!");
            System.out.println("Available headers:");
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                System.out.println("  " + headerName + ": " + request.getHeader(headerName));
            }
        }
        System.out.println("===================================");
        
        // 新しいセッションも試行
        HttpSession newSession = request.getSession(true);
        System.out.println("New session ID: " + newSession.getId());
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // セッションから直接認証情報を取得を試行
        if (session != null) {
            Object sessionAuth = session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (sessionAuth instanceof org.springframework.security.core.context.SecurityContext) {
                org.springframework.security.core.context.SecurityContext securityContext = 
                    (org.springframework.security.core.context.SecurityContext) sessionAuth;
                Authentication sessionAuthentication = securityContext.getAuthentication();
                
                System.out.println("Session authentication: " + sessionAuthentication);
                System.out.println("Session auth name: " + (sessionAuthentication != null ? sessionAuthentication.getName() : "null"));
                
                if (sessionAuthentication != null && !"anonymousUser".equals(sessionAuthentication.getName())) {
                    authentication = sessionAuthentication; // セッションの認証情報を使用
                }
            }
        }
        
        System.out.println("Authentication object: " + authentication);
        System.out.println("Is authenticated: " + (authentication != null ? authentication.isAuthenticated() : "null"));
        System.out.println("Authentication name: " + (authentication != null ? authentication.getName() : "null"));
        System.out.println("Authentication class: " + (authentication != null ? authentication.getClass().getSimpleName() : "null"));
        System.out.println("Principal: " + (authentication != null ? authentication.getPrincipal() : "null"));
        System.out.println("Principal class: " + (authentication != null && authentication.getPrincipal() != null ? authentication.getPrincipal().getClass().getSimpleName() : "null"));
        
        if (authentication != null && authentication.isAuthenticated()) {
            System.out.println("=== AUTHENTICATION DETAILS ===");
            System.out.println("Name: '" + authentication.getName() + "'");
            System.out.println("Is anonymous: " + "anonymousUser".equals(authentication.getName()));
            System.out.println("==============================");
            
            if (!"anonymousUser".equals(authentication.getName())) {
            System.out.println("User principal: " + authentication.getPrincipal());
            System.out.println("User name: " + authentication.getName());
            System.out.println("Authorities: " + authentication.getAuthorities());
            
            // ユーザーの詳細情報を取得
            Object principal = authentication.getPrincipal();
            String username = authentication.getName();
            String fullName = null;
            String domain = null;
            
            // プリンシパルからより詳細な情報を抽出
            if (principal instanceof org.springframework.security.ldap.userdetails.LdapUserDetailsImpl) {
                org.springframework.security.ldap.userdetails.LdapUserDetailsImpl ldapUser = 
                    (org.springframework.security.ldap.userdetails.LdapUserDetailsImpl) principal;
                fullName = ldapUser.getDn();
                System.out.println("LDAP DN: " + fullName);
            } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                org.springframework.security.core.userdetails.User user = 
                    (org.springframework.security.core.userdetails.User) principal;
                username = user.getUsername();
                System.out.println("Spring User: " + username);
            }
            
            // ドメイン情報を推測
            if (username.contains("@")) {
                String[] parts = username.split("@");
                username = parts[0];
                domain = parts[1];
            } else if (username.contains("\\")) {
                String[] parts = username.split("\\\\");
                if (parts.length > 1) {
                    domain = parts[0];
                    username = parts[1];
                }
            }
            
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
                
            // より詳細なレスポンスを作成
            return ResponseEntity.ok(new LoginResponse(
                true,
                "User authenticated",
                username,
                roles
            ));
            } // if文の終了
        }
        
        System.out.println("User not authenticated or anonymous");
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