package com.connecthub.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2ConfigLogger {

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void logOAuth2Configuration() {
        String gatewayUrl = environment.getProperty("GATEWAY_URL", "");
        String frontendUrl = environment.getProperty("FRONTEND_URL", "");
        String googleRedirectUri = environment.getProperty(
                "spring.security.oauth2.client.registration.google.redirect-uri", "");
        String googleClientId = environment.getProperty(
                "spring.security.oauth2.client.registration.google.client-id", "");

        log.info("OAuth2 configuration ready: gatewayUrl={}, frontendUrl={}, googleRedirectUri={}, googleClientIdSet={}",
                valueOrEmpty(gatewayUrl),
                valueOrEmpty(frontendUrl),
                valueOrEmpty(googleRedirectUri),
                StringUtils.hasText(googleClientId));
    }

    private String valueOrEmpty(String value) {
        return StringUtils.hasText(value) ? value : "<empty>";
    }
}
