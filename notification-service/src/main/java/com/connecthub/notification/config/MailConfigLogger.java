package com.connecthub.notification.config;

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
public class MailConfigLogger {

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void logMailConfiguration() {
        String host = environment.getProperty("spring.mail.host", "");
        String port = environment.getProperty("spring.mail.port", "");
        String username = environment.getProperty("spring.mail.username", "");
        String password = environment.getProperty("spring.mail.password", "");
        String startTls = environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "");

        if (!StringUtils.hasText(host) || !StringUtils.hasText(port)) {
            log.warn("SMTP configuration is incomplete: hostSet={}, portSet={}",
                    StringUtils.hasText(host), StringUtils.hasText(port));
        }

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("SMTP credentials are incomplete: usernameSet={}, passwordSet={}",
                    StringUtils.hasText(username), StringUtils.hasText(password));
        }

        log.info("SMTP configuration ready: host={}, port={}, usernameSet={}, passwordSet={}, startTls={}",
                StringUtils.hasText(host) ? host : "<empty>",
                StringUtils.hasText(port) ? port : "<empty>",
                StringUtils.hasText(username),
                StringUtils.hasText(password),
                StringUtils.hasText(startTls) ? startTls : "<empty>");
    }
}
