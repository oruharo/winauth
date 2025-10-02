package com.example.adauth.config;

import com.example.adauth.security.SimpleNtlmAuthenticationFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@Profile("ntlm")
public class SimpleNtlmSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                // Public endpoints
                .antMatchers("/", "/login", "/error", "/css/**", "/js/**").permitAll()
                .antMatchers("/api/health").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
                .and()
            // Add NTLM filter before BasicAuthenticationFilter
            .addFilterBefore(new SimpleNtlmAuthenticationFilter(), BasicAuthenticationFilter.class)
            .csrf().disable()
            .headers().frameOptions().deny()
                .and()
            .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"success\":false,\"message\":\"NTLM Authentication required\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                });
    }
}
