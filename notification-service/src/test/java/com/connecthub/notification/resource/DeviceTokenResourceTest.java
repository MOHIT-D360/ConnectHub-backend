package com.connecthub.notification.resource;

import com.connecthub.notification.entity.DeviceToken;
import com.connecthub.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenResourceTest {

    @Mock private DeviceTokenRepository repo;

    @InjectMocks
    private DeviceTokenResource resource;

    @Test
    void register_nullBodyReturnsBadRequest() {
        ResponseEntity<DeviceToken> response = resource.register(1, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(repo);
    }

    @Test
    void register_blankTokenReturnsBadRequest() {
        ResponseEntity<DeviceToken> response = resource.register(1, Map.of("token", " "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(repo, never()).save(any());
    }

    @Test
    void register_newTokenDefaultsPlatformAndTrimsToken() {
        when(repo.findByToken("abc")).thenReturn(Optional.empty());
        when(repo.save(any(DeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<DeviceToken> response = resource.register(7, Map.of("token", " abc "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        DeviceToken body = response.getBody();
        assertNotNull(body);
        assertEquals(7, body.getUserId());
        assertEquals("abc", body.getToken());
        assertEquals("WEB", body.getPlatform());
        assertNotNull(body.getLastSeenAt());
    }

    @Test
    void register_existingTokenUpdatesOwnerAndPlatform() {
        DeviceToken existing = new DeviceToken();
        when(repo.findByToken("abc")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        ResponseEntity<DeviceToken> response = resource.register(7, Map.of("token", "abc", "platform", "ANDROID"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(7, response.getBody().getUserId());
        assertEquals("ANDROID", response.getBody().getPlatform());
    }

    @Test
    void unregister_nullOrBlankBodyIsNoContentWithoutDelete() {
        assertEquals(HttpStatus.NO_CONTENT, resource.unregister(7, null).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, resource.unregister(7, Map.of("token", " ")).getStatusCode());

        verify(repo, never()).deleteByUserIdAndToken(any(Integer.class), any(String.class));
    }

    @Test
    void unregister_validTokenDeletesTrimmedToken() {
        ResponseEntity<Void> response = resource.unregister(7, Map.of("token", " abc "));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(repo).deleteByUserIdAndToken(7, "abc");
    }
}
