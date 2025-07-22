package com.example.winauth.controller;

import com.example.winauth.dto.AuthResponse;
import com.example.winauth.dto.GroupInfo;
import com.example.winauth.dto.UserInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import waffle.servlet.WindowsPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    @GetMapping("/")
    public ResponseEntity<AuthResponse> home() {
        return ResponseEntity.ok(
            AuthResponse.success("Windows統合認証APIサーバーへようこそ", null)
        );
    }

    @GetMapping("/home")
    public ResponseEntity<AuthResponse> homePage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && !isAnonymous(auth)) {
            UserInfo userInfo = extractUserInfo(auth);
            return ResponseEntity.ok(
                AuthResponse.success("認証情報を取得しました", userInfo)
            );
        } else {
            return ResponseEntity.ok(
                AuthResponse.success("認証されていません", null)
            );
        }
    }

    @GetMapping("/secure")
    public ResponseEntity<AuthResponse> securePage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && !isAnonymous(auth)) {
            UserInfo userInfo = extractUserInfo(auth);
            return ResponseEntity.ok(
                AuthResponse.success("Windows認証に成功しました", userInfo)
            );
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.failure(
                    "認証が必要です。ドメインアカウントでログインしてください。", 
                    "AUTH_REQUIRED"
                ));
        }
    }

    @GetMapping("/login-error")
    public ResponseEntity<AuthResponse> loginError() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(AuthResponse.failure(
                "Windows認証に失敗しました。ドメインアカウントでログインしているか確認してください。",
                "AUTH_FAILED"
            ));
    }

    @GetMapping("/user-info")
    public ResponseEntity<AuthResponse> getUserInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && !isAnonymous(auth)) {
            UserInfo userInfo = extractUserInfo(auth);
            return ResponseEntity.ok(
                AuthResponse.success("ユーザー情報を取得しました", userInfo)
            );
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.failure("認証されていません", "NOT_AUTHENTICATED"));
        }
    }

    private UserInfo extractUserInfo(Authentication auth) {
        UserInfo userInfo = new UserInfo();
        
        // Windows認証の場合
        if (auth.getPrincipal() instanceof WindowsPrincipal) {
            WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();
            userInfo.setUsername(principal.getName());
            userInfo.setDomain(principal.getName()); // WAFFLEでは統合されている
            
            // SIDの取得（安全に）
            try {
                userInfo.setSid(new String(principal.getSid()));
            } catch (Exception e) {
                userInfo.setSid("Unable to retrieve SID");
            }
            
            userInfo.setAuthenticationType("Windows Authentication (WAFFLE)");
            
            // グループ情報を取得（簡単な方法）
            List<GroupInfo> groups = new ArrayList<>();
            try {
                if (principal.getGroups() != null) {
                    // 権限情報からグループを取得
                    auth.getAuthorities().forEach(authority -> {
                        groups.add(new GroupInfo(
                            authority.getAuthority(),
                            authority.getAuthority(),
                            null
                        ));
                    });
                }
            } catch (Exception e) {
                // グループ取得に失敗した場合は空のリストを設定
                groups.add(new GroupInfo("Domain Users", "Domain Users", null));
            }
            userInfo.setGroups(groups);
        }
        // LDAP認証の場合
        else if (auth.getPrincipal() instanceof LdapUserDetails) {
            LdapUserDetails ldapUser = (LdapUserDetails) auth.getPrincipal();
            userInfo.setUsername(ldapUser.getUsername());
            userInfo.setFullName(ldapUser.getDn());
            userInfo.setDomain("testdomain.local");
            userInfo.setAuthenticationType("LDAP Authentication");
            
            // 権限をグループとして設定
            List<GroupInfo> groups = auth.getAuthorities().stream()
                .map(authority -> new GroupInfo(
                    authority.getAuthority(),
                    authority.getAuthority(),
                    null
                ))
                .collect(Collectors.toList());
            userInfo.setGroups(groups);
        }
        // その他の認証方式
        else {
            userInfo.setUsername(auth.getName());
            userInfo.setDomain("local");
            userInfo.setAuthenticationType("Basic Authentication");
            
            List<GroupInfo> groups = auth.getAuthorities().stream()
                .map(authority -> new GroupInfo(
                    authority.getAuthority(),
                    authority.getAuthority(),
                    null
                ))
                .collect(Collectors.toList());
            userInfo.setGroups(groups);
        }
        
        return userInfo;
    }
    
    private boolean isAnonymous(Authentication auth) {
        return auth.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ANONYMOUS".equals(authority.getAuthority()));
    }
}