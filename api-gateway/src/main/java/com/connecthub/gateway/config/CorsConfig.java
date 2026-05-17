package com.connecthub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public CorsWebFilter corsWebFilter() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                frontendUrl,

                // Production Vercel URLs
                "https://connect-hub-frontend-one.vercel.app",
                "https://connect-hub-frontend-git-main-mohit-d360s-projects.vercel.app",

                // Local development
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "X-User-Id",
                "X-User-Email",
                "X-User-Username",
                "X-User-Role",
                "X-Subscription-Tier"
        ));

        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        config.setAllowCredentials(true);

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}