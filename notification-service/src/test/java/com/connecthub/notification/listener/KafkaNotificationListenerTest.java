package com.connecthub.notification.listener;

import com.connecthub.notification.client.AuthServiceClient;
import com.connecthub.notification.dto.UserProfileDto;
import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.service.EmailSender;
import com.connecthub.notification.service.FcmSender;
import com.connecthub.notification.service.NotifService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationListenerTest {

    @Mock
    private NotifService notifService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private EmailSender emailSender;

    @Mock
    private FcmSender fcmSender;

    @InjectMocks
    private KafkaNotificationListener listener;

    @Test
    void processOfflineNotification_savesNotification() throws Exception {

        String json = """
        {
          "recipientId":1,
          "actorId":2,
          "type":"NEW_MESSAGE",
          "title":"New msg",
          "message":"Hello",
          "roomId":"r1",
          "messageId":"m1"
        }
        """;

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 1);
        payload.put("actorId", 2);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "New msg");
        payload.put("message", "Hello");
        payload.put("roomId", "r1");
        payload.put("messageId", "m1");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        listener.processOfflineNotification(
                json,
                "notifications.offline",
                0,
                0L
        );

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notifService).send(captor.capture());

        verify(fcmSender).sendToUser(
                eq(1),
                eq("New msg"),
                eq("Hello"),
                anyMap()
        );

        verifyNoInteractions(authServiceClient, emailSender);

        Notification saved = captor.getValue();

        assertThat(saved.getRecipientId()).isEqualTo(1);
        assertThat(saved.getActorId()).isEqualTo(2);
        assertThat(saved.getType()).isEqualTo("NEW_MESSAGE");
        assertThat(saved.getTitle()).isEqualTo("New msg");
        assertThat(saved.getMessage()).isEqualTo("Hello");
        assertThat(saved.getRoomId()).isEqualTo("r1");
        assertThat(saved.getMessageId()).isEqualTo("m1");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void processOfflineNotification_sendsMissedDirectMessageEmailForStaleDm()
            throws Exception {

        String json = """
        {
          "recipientId":"7",
          "actorId":8,
          "type":"NEW_MESSAGE",
          "title":"New msg",
          "message":"Hello",
          "roomId":"dm-1",
          "messageId":"m1",
          "roomType":"DM"
        }
        """;

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", "7");
        payload.put("actorId", 8);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "New msg");
        payload.put("message", "Hello");
        payload.put("roomId", "dm-1");
        payload.put("messageId", "m1");
        payload.put("roomType", "DM");

        UserProfileDto recipient = new UserProfileDto();
        recipient.setEmail("recipient@example.com");
        recipient.setLastSeenAt(LocalDateTime.now().minusHours(1));

        UserProfileDto actor = new UserProfileDto();
        actor.setFullName("Alex Sender");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        when(authServiceClient.getProfile(7))
                .thenReturn(recipient);

        when(authServiceClient.getProfile(8))
                .thenReturn(actor);

        listener.processOfflineNotification(
                json,
                "notifications.offline",
                1,
                20L
        );

        verify(notifService).send(any(Notification.class));

        verify(fcmSender).sendToUser(
                eq(7),
                eq("New msg"),
                eq("Hello"),
                anyMap()
        );

        verify(emailSender).sendMissedDirectMessage(
                "recipient@example.com",
                "Alex Sender",
                "Hello",
                "dm-1"
        );
    }

    @Test
    void processOfflineNotification_skipsEmailWhenRecipientWasRecentlySeen()
            throws Exception {

        String json = """
        {
          "recipientId":7,
          "actorId":8,
          "type":"NEW_MESSAGE",
          "title":"New msg",
          "message":"Hello",
          "roomId":"dm-1",
          "messageId":"m1",
          "roomType":"DM"
        }
        """;

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 7);
        payload.put("actorId", 8);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "New msg");
        payload.put("message", "Hello");
        payload.put("roomId", "dm-1");
        payload.put("messageId", "m1");
        payload.put("roomType", "DM");

        UserProfileDto recipient = new UserProfileDto();
        recipient.setEmail("recipient@example.com");
        recipient.setLastSeenAt(LocalDateTime.now().minusMinutes(5));

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        when(authServiceClient.getProfile(7))
                .thenReturn(recipient);

        listener.processOfflineNotification(
                json,
                "notifications.offline",
                1,
                21L
        );

        verify(notifService).send(any(Notification.class));

        verifyNoInteractions(emailSender);
    }

    @Test
    void processOfflineNotification_skipsEmailWhenRecipientIsNull()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 7);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomType", "DM");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        when(authServiceClient.getProfile(7))
                .thenReturn(null);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verifyNoInteractions(emailSender);
    }

    @Test
    void processOfflineNotification_skipsEmailWhenEmailBlank()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 7);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomType", "DM");

        UserProfileDto recipient = new UserProfileDto();
        recipient.setEmail(" ");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        when(authServiceClient.getProfile(7))
                .thenReturn(recipient);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verifyNoInteractions(emailSender);
    }

    @Test
    void processOfflineNotification_usesUsernameWhenFullNameBlank()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 7);
        payload.put("actorId", 8);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomId", "r1");
        payload.put("roomType", "DM");

        UserProfileDto recipient = new UserProfileDto();
        recipient.setEmail("recipient@test.com");
        recipient.setLastSeenAt(LocalDateTime.now().minusHours(1));

        UserProfileDto actor = new UserProfileDto();
        actor.setFullName(" ");
        actor.setUsername("fallbackUser");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        when(authServiceClient.getProfile(7))
                .thenReturn(recipient);

        when(authServiceClient.getProfile(8))
                .thenReturn(actor);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verify(emailSender).sendMissedDirectMessage(
                "recipient@test.com",
                "fallbackUser",
                "Hello",
                "r1"
        );
    }

    @Test
    void processOfflineNotification_usesSomeoneWhenActorNull()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 7);
        payload.put("actorId", 8);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomId", "r1");
        payload.put("roomType", "DM");

        UserProfileDto recipient = new UserProfileDto();
        recipient.setEmail("recipient@test.com");
        recipient.setLastSeenAt(LocalDateTime.now().minusHours(1));

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        when(authServiceClient.getProfile(7))
                .thenReturn(recipient);

        when(authServiceClient.getProfile(8))
                .thenReturn(null);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verify(emailSender).sendMissedDirectMessage(
                "recipient@test.com",
                "Someone",
                "Hello",
                "r1"
        );
    }

    @Test
    void processOfflineNotification_rejectsBlankPayload() {

        assertThatThrownBy(() ->
                listener.processOfflineNotification(
                        " ",
                        "notifications.offline",
                        0,
                        0L
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka notification processing failed");

        verifyNoInteractions(
                notifService,
                fcmSender,
                authServiceClient,
                emailSender
        );
    }

    @Test
    void processOfflineNotification_rejectsMissingRequiredFields()
            throws Exception {

        String json = """
        {
          "actorId":2,
          "type":"NEW_MESSAGE"
        }
        """;

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorId", 2);
        payload.put("type", "NEW_MESSAGE");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        assertThatThrownBy(() ->
                listener.processOfflineNotification(
                        json,
                        "notifications.offline",
                        0,
                        0L
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka notification processing failed");

        verifyNoInteractions(
                notifService,
                fcmSender,
                authServiceClient,
                emailSender
        );
    }

    @Test
    void processOfflineNotification_continuesWhenPushFails()
            throws Exception {

        String json = """
        {
          "recipientId":1,
          "actorId":2,
          "type":"NEW_MESSAGE",
          "title":"New msg",
          "message":"Hello",
          "roomId":"r1",
          "messageId":"m1"
        }
        """;

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 1);
        payload.put("actorId", 2);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "New msg");
        payload.put("message", "Hello");
        payload.put("roomId", "r1");
        payload.put("messageId", "m1");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        doThrow(new RuntimeException("fcm down"))
                .when(fcmSender)
                .sendToUser(eq(1), any(), any(), anyMap());

        listener.processOfflineNotification(
                json,
                "notifications.offline",
                0,
                0L
        );

        verify(notifService).send(any(Notification.class));
    }

    @Test
    void processOfflineNotification_handlesNullOptionalFields()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 1);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomId", null);
        payload.put("messageId", null);

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verify(notifService).send(any(Notification.class));

        verify(fcmSender).sendToUser(
                eq(1),
                eq("Title"),
                eq("Hello"),
                anyMap()
        );
    }

    @Test
    void processOfflineNotification_skipsEmailWhenNotDm()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 1);
        payload.put("type", "NEW_MESSAGE");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomType", "GROUP");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verifyNoInteractions(authServiceClient);
        verifyNoInteractions(emailSender);
    }

    @Test
    void processOfflineNotification_skipsEmailWhenTypeIsNotNewMessage()
            throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 1);
        payload.put("type", "FRIEND_REQUEST");
        payload.put("title", "Title");
        payload.put("message", "Hello");
        payload.put("roomType", "DM");

        when(objectMapper.readValue(
                anyString(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        listener.processOfflineNotification(
                "{}",
                "notifications.offline",
                0,
                0L
        );

        verifyNoInteractions(authServiceClient);
        verifyNoInteractions(emailSender);
    }

    @Test
    void handleDlq_logsWithoutThrowing() {

        listener.handleDlq(
                "{}",
                "notifications.offline.dlq",
                0L
        );

        assertThat(true).isTrue();
    }
}