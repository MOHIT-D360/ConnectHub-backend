package com.connecthub.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Payment Service Security Configuration
 *
 * SECURITY MODEL:
 * - Stateless JWT-based authentication
 * - No HTTP session or browser-cookie authentication
 * - API Gateway validates JWT and injects trusted headers
 * - Razorpay webhook is public but protected using Razorpay signature verification
 *
 * WHY CSRF IS DISABLED:
 * CSRF protection is mainly required for browser-based applications that use
 * cookie/session authentication. This service is a stateless REST API using
 * gateway-validated JWT/header authentication, so CSRF tokens are unnecessary.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http

                /*
                 * Safe to disable CSRF because:
                 * 1. This service is fully stateless
                 * 2. Authentication is NOT cookie/session based
                 * 3. JWT is validated by the API Gateway
                 * 4. Webhook security uses Razorpay signature verification
                 */
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // Razorpay webhook endpoint
                        .requestMatchers("/api/v1/payments/webhook").permitAll()

                        // Actuator endpoints
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Swagger/OpenAPI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // All remaining endpoints require authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        new JwtHeaderFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}