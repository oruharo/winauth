package com.example.adauth.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

public class KerberosUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Kerberosで認証されたユーザーの詳細を設定
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // デフォルトロールを設定
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        
        // ドメイン管理者などの特別なロールを設定（必要に応じて）
        if (username.toLowerCase().contains("admin")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        
        return new User(username, "", true, true, true, true, authorities);
    }
}