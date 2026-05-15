package com.connecthub.notification.service;

import com.connecthub.notification.entity.DeviceToken;
import com.connecthub.notification.repository.DeviceTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class FcmSenderTest {

    @Mock private DeviceTokenRepository deviceTokenRepository;

    @Test
    void sendToUser_nullUserDoesNothing() {
        FcmSender sender = sender();

        sender.sendToUser(null, "title", "body", null);

        verify(deviceTokenRepository, never()).findByUserId(0);
    }

    @Test
    void sendToUser_unconfiguredSkipsRepositoryLookup() {
        FcmSender sender = sender();

        sender.sendToUser(7, "title", "body", null);

        verify(deviceTokenRepository, never()).findByUserId(7);
    }

    @Test
    void sendToUser_configuredButNoTokensFinishesWithoutNetworkCall() {
        FcmSender sender = configuredSender();
        when(deviceTokenRepository.findByUserId(7)).thenReturn(List.of());

        assertDoesNotThrow(() -> sender.sendToUser(7, "title", "body", null));

        verify(deviceTokenRepository).findByUserId(7);
    }

    @Test
    void accessToken_returnsCachedTokenWhenStillFresh() {
        FcmSender sender = configuredSender();
        ReflectionTestUtils.setField(sender, "cachedAccessToken", "cached-token");
        ReflectionTestUtils.setField(sender, "cachedAccessTokenExpiresAt", Instant.now().getEpochSecond() + 300);

        String token = ReflectionTestUtils.invokeMethod(sender, "accessToken");

        assertEquals("cached-token", token);
    }

    @Test
    void signedJwt_usesConfiguredPrivateKey() throws Exception {
        FcmSender sender = configuredSender();

        String jwt = ReflectionTestUtils.invokeMethod(sender, "signedJwt", 1_700_000_000L);

        assertNotNull(jwt);
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        assertTrue(parts[0].length() > 10);
        assertTrue(parts[1].length() > 10);
        assertTrue(parts[2].length() > 10);
    }

    @Test
    void sendToUser_configuredTokenSendsFcmPayload() throws Exception {
        FcmSender sender = configuredSender();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> fcmResponse = mock(HttpResponse.class);
        DeviceToken token = DeviceToken.builder().userId(7).token("device-token").platform("WEB").build();

        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        when(deviceTokenRepository.findByUserId(7)).thenReturn(List.of(token));
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"access-1\",\"expires_in\":3600}");
        when(fcmResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse, fcmResponse);

        sender.sendToUser(7, null, null, Map.of("roomId", "r1"));

        verify(deviceTokenRepository).findByUserId(7);
        verify(httpClient, org.mockito.Mockito.times(2))
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void sendToUser_tokenRequestFailureIsCaught() throws Exception {
        FcmSender sender = configuredSender();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        DeviceToken token = DeviceToken.builder().userId(7).token("device-token").platform("WEB").build();

        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        when(deviceTokenRepository.findByUserId(7)).thenReturn(List.of(token));
        when(tokenResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(tokenResponse);

        assertDoesNotThrow(() -> sender.sendToUser(7, "Title", "Body", null));
    }

    private FcmSender sender() {
        return new FcmSender(deviceTokenRepository, new ObjectMapper());
    }

    private FcmSender configuredSender() {
        FcmSender sender = sender();
        ReflectionTestUtils.setField(sender, "projectId", "connecthub-test");
        ReflectionTestUtils.setField(sender, "clientEmail", "firebase@test.iam.gserviceaccount.com");
        ReflectionTestUtils.setField(sender, "privateKeyPem", privateKeyPem());
        return sender;
    }

    private String privateKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            String key = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + key + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
