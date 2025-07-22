package com.example.winauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("ldap")
public class SimpleLdapConfig {

    @Bean
    public SecurityFilterChain ldapFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/", "/api/", "/home", "/api/home", "/error", "/login-error", "/login").permitAll()
                .anyRequest().authenticated()
                .and()
            .httpBasic()
                .realmName("LDAP Test Authentication")
                .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/secure")
                .failureUrl("/login-error")
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .and()
            .csrf().disable();

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails testUser = User.builder()
            .username("testuser")
            .password("password123")
            .roles("USER")
            .build();

        UserDetails adminUser = User.builder()
            .username("admin")
            .password("admin123")
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(testUser, adminUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // テスト用なのでNoOpPasswordEncoderを使用（本番では使用しない）
        return NoOpPasswordEncoder.getInstance();
    }
}