package com.example.winauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("ad")
public class ActiveDirectoryConfig {

    @Value("${ad.domain}")
    private String adDomain;

    @Value("${ad.url}")
    private String adUrl;

    @Value("${ad.rootDn:}")
    private String rootDn;

    @Value("${ad.searchFilter:(sAMAccountName={0})}")
    private String searchFilter;

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider activeDirectoryLdapAuthenticationProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider = 
            new ActiveDirectoryLdapAuthenticationProvider(adDomain, adUrl, rootDn);
        
        // オプション: カスタム検索フィルター
        if (searchFilter != null && !searchFilter.isEmpty()) {
            provider.setSearchFilter(searchFilter);
        }
        
        // SSL証明書の検証をスキップする場合（開発環境のみ）
        // provider.setContextEnvironmentProperties(Collections.singletonMap(
        //     "java.naming.ldap.factory.socket", "com.example.winauth.config.TrustAllSSLSocketFactory"
        // ));
        
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        
        return provider;
    }

    @Bean
    public SecurityFilterChain adFilterChain(HttpSecurity http, 
                                            ActiveDirectoryLdapAuthenticationProvider adProvider) throws Exception {
        http
            .authenticationProvider(adProvider)
            .authorizeRequests()
                .antMatchers("/", "/api/", "/home", "/api/home", "/error", "/login-error", "/login").permitAll()
                .anyRequest().authenticated()
                .and()
            .httpBasic()
                .realmName("Active Directory Authentication")
                .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/api/secure")
                .failureUrl("/login-error")
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .and()
            .csrf().disable()
            .cors();

        return http.build();
    }
}