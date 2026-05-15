package com.connecthub.websocket.interceptor;

import com.connecthub.websocket.client.RoomServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class JwtChannelInterceptorTest {

    private JwtChannelInterceptor interceptor;
    private MessageChannel channel;
    private RoomServiceClient roomServiceClient;
    private String jwtSecretBase64;

    @BeforeEach
    void setUp() {
        roomServiceClient = mock(RoomServiceClient.class);
        interceptor = new JwtChannelInterceptor(roomServiceClient);
        channel = mock(MessageChannel.class);

        byte[] raw = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        jwtSecretBase64 = Base64.getEncoder().encodeToString(raw);
        ReflectionTestUtils.setField(interceptor, "jwtSecret", jwtSecretBase64);
    }

    @Test
    void preSend_connectWithValidJwt_setsPrincipalWithTier() {
        String token = token("7", "alice", "PRO");
        Message<byte[]> message = connectMessage("Bearer " + token);

        Message<?> result = interceptor.preSend(message, channel);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);

        assertNotNull(accessor.getUser());
        assertEquals("7", accessor.getUser().getName());
        assertTrue(accessor.getUser() instanceof StompPrincipal);
        StompPrincipal principal = (StompPrincipal) accessor.getUser();
        assertEquals("alice", principal.username());
        assertEquals("PRO", principal.subscriptionTier());
    }

    @Test
    void preSend_connectWithMissingTier_defaultsToFree() {
        String token = tokenWithoutTier("8", "bob");
        Message<byte[]> message = connectMessage("Bearer " + token);

        Message<?> result = interceptor.preSend(message, channel);
        StompPrincipal principal = (StompPrincipal) StompHeaderAccessor
                .wrap(result).getUser();

        assertEquals("FREE", principal.subscriptionTier());
    }

    @Test
    void preSend_connectMissingAuthorization_throws() {
        Message<byte[]> message = connectMessage(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interceptor.preSend(message, channel));
        assertEquals("Missing Authorization", ex.getMessage());
    }

    @Test
    void preSend_connectInvalidJwt_throws() {
        Message<byte[]> message = connectMessage("Bearer invalid.token");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interceptor.preSend(message, channel));
        assertEquals("Invalid JWT", ex.getMessage());
    }

    @Test
    void preSend_nonConnect_passthrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_subscribeToRoom_allowsMember() {
        Message<byte[]> message = subscribeMessage("room-1", new StompPrincipal("7", "alice"));
        when(roomServiceClient.isMember("room-1", "7", "7")).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
        verify(roomServiceClient).isMember("room-1", "7", "7");
    }

    @Test
    void preSend_subscribeWithoutPrincipalThrows() {
        Message<byte[]> message = subscribeMessage("room-1", null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interceptor.preSend(message, channel));

        assertEquals("Unauthenticated subscription", ex.getMessage());
    }

    @Test
    void preSend_subscribeToRoomWhenNotMemberThrows() {
        Message<byte[]> message = subscribeMessage("room-1", new StompPrincipal("7", "alice"));
        when(roomServiceClient.isMember("room-1", "7", "7")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interceptor.preSend(message, channel));

        assertEquals("Not allowed to subscribe to this room", ex.getMessage());
    }

    @Test
    void preSend_subscribeToRoomWhenMembershipCheckFailsThrows() {
        Message<byte[]> message = subscribeMessage("room-1", new StompPrincipal("7", "alice"));
        when(roomServiceClient.isMember("room-1", "7", "7")).thenThrow(new RuntimeException("room down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interceptor.preSend(message, channel));

        assertEquals("Not allowed to subscribe to this room", ex.getMessage());
    }

    @Test
    void preSend_subscribeToNonRoomDestinationPassesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/messages");
        accessor.setUser(new StompPrincipal("7", "alice"));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void stompPrincipal_twoArgConstructor_defaultsTierAndName() {
        StompPrincipal principal = new StompPrincipal("1", "user");

        assertEquals("1", principal.getName());
        assertEquals("FREE", principal.subscriptionTier());
    }

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String roomId, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/room/" + roomId + "/messages");
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String token(String subject, String username, String tier) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecretBase64));
        return Jwts.builder()
                .subject(subject)
                .claim("username", username)
                .claim("subscriptionTier", tier)
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();
    }

    private String tokenWithoutTier(String subject, String username) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecretBase64));
        return Jwts.builder()
                .subject(subject)
                .claim("username", username)
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();
    }
}
