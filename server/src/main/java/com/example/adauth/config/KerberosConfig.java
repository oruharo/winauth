package com.example.adauth.config;

import java.io.File;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.kerberos.authentication.KerberosAuthenticationProvider;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosClient;
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter;
import org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint;
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider;
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosTicketValidator;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;

@Configuration
@EnableWebSecurity  
@Profile("kerberos")
public class KerberosConfig extends WebSecurityConfigurerAdapter {
    
    // コンストラクタでクラスが読み込まれたことを確認
    public KerberosConfig() {
        System.out.println("=== KERBEROS CONFIG INITIALIZED ===");
        System.out.println("KerberosConfig class loaded successfully");
        System.out.println("Profile: kerberos");
        System.out.println("===================================");
    }

    @Value("${kerberos.keytab}")
    private String keytabLocation;

    @Value("${kerberos.principal}")
    private String servicePrincipal;
    
    // Keytabファイルの存在確認とログ出力
    private void validateKeytab() {
        System.out.println("=== KERBEROS KEYTAB VALIDATION ===");
        System.out.println("Keytab location: " + keytabLocation);
        System.out.println("Service principal: " + servicePrincipal);
        
        File keytabFile = new File(keytabLocation);
        System.out.println("Keytab file exists: " + keytabFile.exists());
        System.out.println("Keytab file readable: " + keytabFile.canRead());
        System.out.println("Keytab file size: " + keytabFile.length() + " bytes");
        System.out.println("Keytab absolute path: " + keytabFile.getAbsolutePath());
        System.out.println("==================================");
        
        if (!keytabFile.exists()) {
            System.err.println("WARNING: Keytab file does not exist!");
        } else if (!keytabFile.canRead()) {
            System.err.println("WARNING: Cannot read keytab file!");
        }
    }

    @Value("${ad.domain}")
    private String adDomain;

    @Value("${ad.url}")
    private String adUrl;

    @Value("${ad.searchBase:}")
    private String searchBase;

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider activeDirectoryLdapAuthenticationProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider = 
            new ActiveDirectoryLdapAuthenticationProvider(adDomain, adUrl, searchBase);
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        return provider;
    }

    @Bean
    public SunJaasKerberosClient kerberosClient() {
        SunJaasKerberosClient client = new SunJaasKerberosClient();
        client.setDebug(true);
        return client;
    }

    @Bean
    public SunJaasKerberosTicketValidator sunJaasKerberosTicketValidator() {
        SunJaasKerberosTicketValidator ticketValidator = new SunJaasKerberosTicketValidator();
        ticketValidator.setServicePrincipal(servicePrincipal);
        ticketValidator.setKeyTabLocation(new FileSystemResource(keytabLocation));
        ticketValidator.setDebug(true);
        return ticketValidator;
    }

    @Bean
    public KerberosServiceAuthenticationProvider kerberosServiceAuthenticationProvider() {
        // Keytabファイルの検証を実行
        validateKeytab();
        
        KerberosServiceAuthenticationProvider provider = new KerberosServiceAuthenticationProvider();
        provider.setTicketValidator(sunJaasKerberosTicketValidator());
        provider.setUserDetailsService(kerberosUserDetailsService());
        return provider;
    }

    @Bean
    public KerberosAuthenticationProvider kerberosAuthenticationProvider() {
        KerberosAuthenticationProvider provider = new KerberosAuthenticationProvider();
        provider.setKerberosClient(kerberosClient());
        provider.setUserDetailsService(kerberosUserDetailsService());
        return provider;
    }

    @Bean
    public KerberosUserDetailsService kerberosUserDetailsService() {
        return new KerberosUserDetailsService();
    }

    @Bean
    public SpnegoEntryPoint spnegoEntryPoint() {
        return new SpnegoEntryPoint("/login");
    }

    @Bean
    public SpnegoAuthenticationProcessingFilter spnegoAuthenticationProcessingFilter() {
        SpnegoAuthenticationProcessingFilter filter = new SpnegoAuthenticationProcessingFilter();
        try {
            filter.setAuthenticationManager(authenticationManagerBean());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SpnegoAuthenticationProcessingFilter", e);
        }
        return filter;
    }
    
    @Bean
    public FilterRegistrationBean<NegotiateDebugFilter> negotiateDebugFilter() {
        FilterRegistrationBean<NegotiateDebugFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new NegotiateDebugFilter());
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(1); // SPNEGOフィルターより前に実行
        return registrationBean;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .authenticationProvider(kerberosServiceAuthenticationProvider())
            .authenticationProvider(kerberosAuthenticationProvider())
            .authenticationProvider(activeDirectoryLdapAuthenticationProvider());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                // 公開エンドポイント
                .antMatchers("/", "/login", "/error", "/css/**", "/js/**").permitAll()
                // API公開エンドポイント
                .antMatchers("/api/health", "/api/login").permitAll()
                // その他は認証必須
                .anyRequest().authenticated()
                .and()
            // SPNEGO認証フィルターを追加
            .addFilterBefore(spnegoAuthenticationProcessingFilter(), BasicAuthenticationFilter.class)
            .exceptionHandling()
                .authenticationEntryPoint(spnegoEntryPoint())
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
            .csrf().disable()
            .cors();
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}