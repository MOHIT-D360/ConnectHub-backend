package com.connecthub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CorsConfig — CORS (Cross-Origin Resource Sharing) Configuration for API Gateway
 *
 * PURPOSE:
 *   Enables the frontend (running on a different origin) to make HTTP requests to
 *   the backend API gateway. Without CORS configuration, the browser would block
 *   all cross-origin requests at the network layer, even if the backend would allow them.
 *
 * HOW CORS WORKS:
 *   1. Simple requests (GET, POST with certain headers) are sent directly.
 *   2. Complex requests (custom headers, DELETE, PUT, etc.) trigger a preflight:
 *      - Browser sends OPTIONS request to the server first
 *      - Server responds with CORS headers (Access-Control-Allow-*)
 *      - Browser checks if the actual method/headers are allowed
 *      - If allowed, browser sends the actual request
 *
 * CRITICAL FIX:
 *   The JwtAuthenticationFilter must SKIP OPTIONS requests, otherwise preflight
 *   fails before this CORS filter can respond. See JwtAuthenticationFilter.java
 *   for the fix.
 *
 * FILTER ORDER IN SPRING CLOUD GATEWAY:
 *   GlobalFilters execute first (in order):
 *     1. TraceIdFilter (-2)          — adds X-Trace-Id header
 *     2. JwtAuthenticationFilter (-1) — validates JWT, adds user headers
 *     3. RateLimitFilter (0)         — enforces per-user rate limits
 *   Then WebFilters (including CorsWebFilter) execute
 *   Finally, the request is routed to the downstream service
 *
 * SECURITY CONSIDERATIONS:
 *   - Credentials: Cookies and Authorization headers are NOT included by default.
 *     Setting allowCredentials(true) requires specific origin (no wildcards).
 *   - Exposed headers: Only listed headers are readable by JavaScript. Token info
 *     is in the response body (ApiResponse), not in headers, so it's safe.
 *   - MaxAge: Browsers cache preflight results for 1 hour, reducing preflight load.
 */
@Configuration
public class CorsConfig {

    @Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    /**
     * corsWebFilter — Reactive CORS filter for Spring Cloud Gateway.
     * Returns a 200 OK response to preflight OPTIONS requests with proper headers.
     * Allows the browser to proceed with the actual cross-origin request.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {

        CorsConfiguration config = new CorsConfiguration();

        /*
         * ALLOWED ORIGINS:
         * - Explicitly list all allowed frontends
         * - Production Vercel deployments (main branch and feature branch)
         * - Local development (both localhost and 127.0.0.1 with any port)
         * - Do NOT use "allowCredentials(true)" with wildcard origins — browsers reject this
         */
        config.setAllowedOriginPatterns(List.of(
                frontendUrl,

                // Production Vercel URLs (must update if deploying to different hosts)
                "https://connect-hub-frontend-one.vercel.app",
                "https://connect-hub-frontend-git-main-mohit-d360s-projects.vercel.app",

                // Local development (localhost + any port for flexibility)
                "http://localhost:4200",
                "http://localhost:*",
                "http://127.0.0.1:4200",
                "http://127.0.0.1:*"
        ));

        /*
         * ALLOWED HTTP METHODS:
         * Explicitly allow each method the frontend needs.
         * OPTIONS is automatically allowed by the CORS spec for preflight.
         */
        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "OPTIONS"
        ));

        /*
         * ALLOWED REQUEST HEADERS:
         * Headers the frontend can send in requests to the backend.
         * Authorization header carries JWT tokens.
         * X-* headers are custom headers used internally between gateway and services.
         */
        config.setAllowedHeaders(List.of(
                "Authorization",        // JWT token
                "Content-Type",         // application/json, multipart/form-data, etc.
                "X-Requested-With",     // typically "XMLHttpRequest" from older AJAX
                "Accept",               // acceptable response content types
                "Origin",               // the origin making the request (sent by browser)
                "X-Trace-Id",           // distributed tracing ID (added by gateway)
                "X-User-Id",            // user ID (added by gateway after JWT validation)
                "X-User-Email",         // user email (added by gateway after JWT validation)
                "X-User-Username",      // username (added by gateway after JWT validation)
                "X-User-Role",          // user role (added by gateway after JWT validation)
                "X-Subscription-Tier"   // subscription level (added by gateway)
        ));

        /*
         * EXPOSED RESPONSE HEADERS:
         * Headers the browser JavaScript is allowed to read from responses.
         * By default, browsers only expose a limited set of headers (Content-Type, Cache-Control, etc.).
         * We explicitly expose Authorization in case any service uses it (e.g., refresh token in response).
         * User data is in the response body (ApiResponse), not in headers.
         */
        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-RateLimit-Limit",      // rate limit info from gateway
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset"
        ));

        /*
         * ALLOW CREDENTIALS:
         * If true, the browser will include cookies and Authorization headers
         * in cross-origin requests. We set this to true because the frontend
         * sends Authorization headers (JWT tokens).
         * 
         * SECURITY: When allowCredentials(true), wildcard origins (*) are not allowed.
         * That's why we use setAllowedOriginPatterns() with specific patterns instead.
         */
        config.setAllowCredentials(true);

        /*
         * MAX AGE:
         * How long (in seconds) the browser can cache preflight responses.
         * 3600 seconds = 1 hour. After this, the browser will send another preflight.
         * Reduces preflight overhead for active sessions but allows updates if needed.
         */
        config.setMaxAge(3600L);

        /*
         * Register the CORS configuration for all paths (/**).
         * This ensures every route to every downstream service is CORS-enabled.
         */
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}