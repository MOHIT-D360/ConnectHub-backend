package com.connecthub.websocket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnreadCountServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks UnreadCountService unreadCountService;

    @Test
    void increment_callsRedisIncrement() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("unread:1:room42")).thenReturn(3L);
        unreadCountService.increment(1, "room42");
        verify(valueOps).increment("unread:1:room42");
    }

    @Test
    void reset_deletesKey() {
        unreadCountService.reset(1, "room42");
        verify(redis).delete("unread:1:room42");
    }

    @Test
    void getCount_whenKeyExists_returnsCount() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:room42")).thenReturn("7");
        assertThat(unreadCountService.getCount(1, "room42")).isEqualTo(7L);
    }

    @Test
    void getCount_whenKeyAbsent_returnsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:room42")).thenReturn(null);
        assertThat(unreadCountService.getCount(1, "room42")).isEqualTo(0L);
    }

    @Test
    void getCount_whenValueMalformed_returnsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:room42")).thenReturn("not-a-number");
        assertThat(unreadCountService.getCount(1, "room42")).isEqualTo(0L);
    }

    @Test
    void increment_whenRedisReturnsNullReturnsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("unread:1:room42")).thenReturn(null);

        assertThat(unreadCountService.increment(1, "room42")).isZero();
    }

    @Test
    void getAllForUser_returnsPositiveRoomCountsOnly() {
        when(redis.keys("unread:1:*")).thenReturn(Set.of("unread:1:roomA", "unread:1:roomB", "unread:1:roomC"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:roomA")).thenReturn("2");
        when(valueOps.get("unread:1:roomB")).thenReturn("0");
        when(valueOps.get("unread:1:roomC")).thenReturn("bad");

        assertThat(unreadCountService.getAllForUser(1))
                .containsEntry("roomA", 2L)
                .doesNotContainKeys("roomB", "roomC");
    }

    @Test
    void getAllForUser_whenNoKeysReturnsEmptyMap() {
        when(redis.keys("unread:1:*")).thenReturn(null);

        assertThat(unreadCountService.getAllForUser(1)).isEmpty();
    }
}
