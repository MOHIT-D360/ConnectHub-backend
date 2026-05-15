package com.connecthub.notification.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Notification Service Security Configuration
 *
 * SECURITY MODEL:
 * - Stateless REST APIs
 * - Authentication handled through gateway-propagated JWT/header context
 * - No session or cookie-based authentication
 * - All APIs require authentication except actuator and Swagger endpoints
 *
 * WHY CSRF IS DISABLED:
 * CSRF attacks primarily target browser applications using session cookies.
 * This service is fully stateless and does not rely on browser sessions,
 * therefore CSRF protection is not applicable here.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthFilter gatewayAuthFilter;

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http

                /*
                 * Safe to disable CSRF because:
                 * 1. Service is stateless
                 * 2. No HTTP session authentication is used
                 * 3. Authentication is propagated through gateway headers/JWT
                 * 4. APIs are consumed programmatically, not through browser sessions
                 */
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // Public actuator endpoints
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Swagger/OpenAPI endpoints
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // All remaining APIs require authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        gatewayAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}