package com.example.adauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;

@Configuration
@EnableWebSecurity
@Profile("!kerberos")
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${ad.domain}")
    private String domain;

    @Value("${ad.url}")
    private String url;

    @Value("${ad.searchBase:}")
    private String searchBase;

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider activeDirectoryLdapAuthenticationProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider = 
            new ActiveDirectoryLdapAuthenticationProvider(domain, url, searchBase);
        
        // 変換エラーを例外に変換
        provider.setConvertSubErrorCodesToExceptions(true);
        
        // 認証時のユーザー資格情報を使用
        provider.setUseAuthenticationRequestCredentials(true);
        
        // ユーザー名の形式を設定（sAMAccountName形式を受け入れる）
        provider.setSearchFilter("(&(objectClass=user)(sAMAccountName={0}))");
        
        return provider;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(activeDirectoryLdapAuthenticationProvider());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                // 公開エンドポイント
                .antMatchers("/", "/login", "/error", "/css/**", "/js/**").permitAll()
                // API公開エンドポイント
                .antMatchers("/api/health", "/api/login", "/api/user", "/api/test").permitAll()
                // 全てのAPIエンドポイントを一時的に公開
                .antMatchers("/api/**").permitAll()
                // その他は認証必須
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/home", true)
                .failureUrl("/login?error=true")
                .permitAll()
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
                .and()
            .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    // API リクエストの場合は JSON で 401 を返す
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\"}");
                    } else {
                        // 通常のページリクエストはログインページにリダイレクト
                        response.sendRedirect("/login");
                    }
                })
                .and()
            .csrf().disable()  // APIのため無効化（本番では要検討）
            .cors();
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}