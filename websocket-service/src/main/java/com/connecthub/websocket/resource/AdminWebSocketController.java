package com.connecthub.websocket.resource;

import com.connecthub.websocket.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/ws/admin")
@RequiredArgsConstructor
public class AdminWebSocketController {

    private static final String WS_USER_SESSIONS_PREFIX = "ws:user:sessions:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> connections(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!isAdmin(role)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Set<String> sessionKeys = redis.keys(WS_USER_SESSIONS_PREFIX + "*");
        long activeUsers = sessionKeys == null ? 0 : sessionKeys.size();
        long activeConnections = 0;
        if (sessionKeys != null) {
            for (String key : sessionKeys) {
                Long sessions = redis.opsForSet().size(key);
                activeConnections += sessions == null ? 0 : sessions;
            }
        }

        return ResponseEntity.ok(Map.of(
                "activeUsers", activeUsers,
                "activeConnections", activeConnections
        ));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestBody Map<String, String> request) {
        if (!isAdmin(role)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        String title = normalize(request.get("title"), "ConnectHub update");
        String message = normalize(request.get("message"), "");
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("message", "Broadcast message is required"));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "PLATFORM_BROADCAST");
        payload.put("title", title);
        payload.put("message", message);
        payload.put("sentBy", adminId);
        payload.put("sentAt", Instant.now().toString());

        try {
            redis.convertAndSend(RedisConfig.BROADCAST_CHANNEL, objectMapper.writeValueAsString(payload));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.<String, Object>of("message", "Broadcast dispatch failed"));
        }
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.trim().isBlank()) return fallback;
        return value.trim();
    }

    private boolean isAdmin(String role) {
        return "ADMIN".equals(role) || "PLATFORM_ADMIN".equals(role);
    }
}
