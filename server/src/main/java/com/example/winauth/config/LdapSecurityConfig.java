package com.example.winauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("ldap-advanced")
public class LdapSecurityConfig {

    @Bean
    public BaseLdapPathContextSource contextSource() {
        return new DefaultSpringSecurityContextSource("ldap://localhost:8389/dc=testdomain,dc=local");
    }

    @Bean
    public LdapUserSearch ldapUserSearch() {
        return new FilterBasedLdapUserSearch("ou=users", "uid={0}", contextSource());
    }

    @Bean
    public DefaultLdapAuthoritiesPopulator ldapAuthoritiesPopulator() {
        var populator = new DefaultLdapAuthoritiesPopulator(contextSource(), "ou=groups");
        populator.setGroupSearchFilter("member={0}");
        populator.setSearchSubtree(true);
        populator.setRolePrefix("ROLE_");
        populator.setGroupRoleAttribute("cn");
        return populator;
    }

    @Bean
    public UserDetailsService ldapUserDetailsService() {
        return new LdapUserDetailsService(ldapUserSearch(), ldapAuthoritiesPopulator());
    }

    @Bean
    public SecurityFilterChain ldapFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/", "/home", "/error", "/login-error", "/login").permitAll()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/secure")
                .failureUrl("/login-error")
                .and()
            .httpBasic()
                .realmName("LDAP Authentication")
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .and()
            .csrf().disable();

        return http.build();
    }

    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .ldapAuthentication()
                .userDnPatterns("uid={0},ou=users")
                .groupSearchBase("ou=groups")
                .contextSource(contextSource())
                .passwordCompare()
                .passwordAttribute("userPassword");
    }
}