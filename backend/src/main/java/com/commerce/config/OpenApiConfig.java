package com.commerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Swagger UI / OpenAPI setup. Exists only when {@code springdoc.swagger-ui.enabled=true} (the "local" profile), so
 * production exposes neither the UI nor the API description.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true")
public class OpenApiConfig {

    /**
     * The documentation pages get their own security chain: they are read-only and public, and the main chain's strict
     * Content-Security-Policy ({@code default-src 'none'}) would stop Swagger UI from loading its own scripts and styles.
     * Calling the API itself from the UI still goes through the normal chain (session cookie + CSRF + role checks).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    public OpenAPI commerceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Commerce API")
                .version("v1")
                .description("""
                        REST API of the jewelry store.

                        **Trying protected endpoints:** call `POST /api/v1/auth/login` below first (demo accounts: `customer` / `Customer@12345!`, \
                        `admin` / `Admin@12345!`). The browser keeps the session cookie, so later calls from this page are signed in. \
                        Admin endpoints (`/api/v1/admin/**`) need the admin account.
                        """));
    }
}
