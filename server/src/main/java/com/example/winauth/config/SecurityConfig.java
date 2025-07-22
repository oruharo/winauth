package com.example.winauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import waffle.servlet.spi.NegotiateSecurityFilterProvider;
import waffle.servlet.spi.SecurityFilterProvider;
import waffle.servlet.spi.SecurityFilterProviderCollection;
import waffle.spring.NegotiateSecurityFilter;
import waffle.spring.NegotiateSecurityFilterEntryPoint;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public WindowsAuthProviderImpl windowsAuthProvider() {
        return new WindowsAuthProviderImpl();
    }

    @Bean
    public SecurityFilterProviderCollection securityFilterProviderCollection(
            WindowsAuthProviderImpl windowsAuthProvider) {
        SecurityFilterProvider[] providers = {
            new NegotiateSecurityFilterProvider(windowsAuthProvider)
        };
        return new SecurityFilterProviderCollection(providers);
    }

    @Bean
    public NegotiateSecurityFilterEntryPoint negotiateSecurityFilterEntryPoint(
            SecurityFilterProviderCollection providers) {
        NegotiateSecurityFilterEntryPoint entryPoint = new NegotiateSecurityFilterEntryPoint();
        entryPoint.setProvider(providers);
        return entryPoint;
    }

    @Bean
    public NegotiateSecurityFilter negotiateSecurityFilter(
            SecurityFilterProviderCollection providers) {
        NegotiateSecurityFilter filter = new NegotiateSecurityFilter();
        filter.setProvider(providers);
        return filter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            NegotiateSecurityFilter negotiateSecurityFilter,
            NegotiateSecurityFilterEntryPoint entryPoint) throws Exception {
        
        http
            .authorizeRequests()
                .antMatchers("/", "/home", "/error", "/login-error").permitAll()
                .anyRequest().authenticated()
                .and()
            .addFilterBefore(negotiateSecurityFilter, BasicAuthenticationFilter.class)
            .exceptionHandling()
                .authenticationEntryPoint(entryPoint)
                .accessDeniedPage("/error")
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .and()
            .csrf().disable();

        return http.build();
    }
}