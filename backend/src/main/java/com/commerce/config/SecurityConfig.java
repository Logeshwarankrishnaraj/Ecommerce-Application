package com.commerce.config;

import com.commerce.security.SessionRevalidationFilter;
import com.commerce.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.http.HttpStatus;

/**
 * Session-cookie authentication (HttpOnly JSESSIONID) with double-submit CSRF cookie for the SPA.
 * Backend authorization is authoritative; the Angular guards are UX only.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AppUserRepository users) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // resolve the token eagerly so the cookie is always issued

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/v1/payments/webhooks/**")) // authenticated by HMAC signature instead
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/auth/**", "/api/v1/storefront/**", "/api/v1/categories/**",
                        "/api/v1/lookup-groups/**", "/media/**", "/api/v1/payments/webhooks/**", "/api/v1/dev/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/products/*/interactions/view").permitAll()
                .requestMatchers("/api/v1/account/me", "/api/v1/account/change-password").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/audit-logs/delete").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/account/**", "/api/v1/checkout/**", "/api/v1/payments/**").hasRole("CUSTOMER")
                .anyRequest().denyAll())
            .logout(l -> l
                .logoutUrl("/api/v1/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(this::unauthenticated)
                .accessDeniedHandler(this::denied))
            .addFilterBefore(new SessionRevalidationFilter(users), AuthorizationFilter.class);
        return http.build();
    }

    private void unauthenticated(HttpServletRequest req, HttpServletResponse res, Exception ex) throws IOException {
        com.commerce.security.SecurityErrors.write(req, res, 401, "UNAUTHENTICATED", "Please sign in to continue.");
    }

    private void denied(HttpServletRequest req, HttpServletResponse res, Exception ex) throws IOException {
        com.commerce.security.SecurityErrors.write(req, res, 403, "ACCESS_DENIED", "You do not have permission to perform this action.");
    }
}
