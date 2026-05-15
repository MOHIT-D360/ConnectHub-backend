package com.connecthub.websocket.resource;

import com.connecthub.websocket.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWebSocketControllerTest {

    @Mock private StringRedisTemplate redis;
    @Mock private SetOperations<String, String> setOps;

    private AdminWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminWebSocketController(redis, new ObjectMapper());
    }

    @Test
    void connections_nonAdminForbidden() {
        ResponseEntity<Map<String, Object>> response = controller.connections("USER");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(redis);
    }

    @Test
    void connections_countsUsersAndSessions() {
        when(redis.keys("ws:user:sessions:*")).thenReturn(Set.of("ws:user:sessions:1", "ws:user:sessions:2"));
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.size("ws:user:sessions:1")).thenReturn(2L);
        when(setOps.size("ws:user:sessions:2")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.connections("PLATFORM_ADMIN");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, response.getBody().get("activeUsers"));
        assertEquals(2L, response.getBody().get("activeConnections"));
    }

    @Test
    void broadcast_nonAdminForbidden() {
        ResponseEntity<Map<String, Object>> response = controller.broadcast("USER", "1", Map.of("message", "hello"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(redis);
    }

    @Test
    void broadcast_blankMessageReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> response = controller.broadcast("ADMIN", "1", Map.of("message", " "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Broadcast message is required", response.getBody().get("message"));
    }

    @Test
    void broadcast_validMessagePublishesPayload() {
        ResponseEntity<Map<String, Object>> response = controller.broadcast(
                "ADMIN",
                "7",
                Map.of("title", "  Notice  ", "message", "  Maintenance tonight  "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PLATFORM_BROADCAST", response.getBody().get("type"));
        assertEquals("Notice", response.getBody().get("title"));
        assertEquals("Maintenance tonight", response.getBody().get("message"));
        assertEquals("7", response.getBody().get("sentBy"));
        assertTrue(response.getBody().containsKey("sentAt"));
        verify(redis).convertAndSend(eq(RedisConfig.BROADCAST_CHANNEL), anyString());
    }

    @Test
    void broadcast_redisFailureReturnsServerError() {
        when(redis.convertAndSend(eq(RedisConfig.BROADCAST_CHANNEL), anyString())).thenThrow(new RuntimeException("down"));

        ResponseEntity<Map<String, Object>> response = controller.broadcast("ADMIN", "7", Map.of("message", "hello"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Broadcast dispatch failed", response.getBody().get("message"));
    }
}
