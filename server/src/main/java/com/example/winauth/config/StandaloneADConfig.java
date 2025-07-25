package com.example.winauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("standalone-ad")
public class StandaloneADConfig {

    @Value("${ad.domain}")
    private String adDomain;

    @Value("${ad.url}")
    private String adUrl;

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider activeDirectoryLdapAuthenticationProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider = 
            new ActiveDirectoryLdapAuthenticationProvider(adDomain, adUrl);
        
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                         ActiveDirectoryLdapAuthenticationProvider adProvider) throws Exception {
        http
            .authenticationProvider(adProvider)
            .authorizeRequests()
                .antMatchers("/", "/api/", "/home", "/api/home", "/error", 
                           "/login", "/login-error", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/api/secure", true)
                .failureUrl("/login-error")
                .permitAll()
                .and()
            .httpBasic()
                .realmName("Active Directory Authentication")
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
                .and()
            .csrf().disable()
            .cors();

        return http.build();
    }
}