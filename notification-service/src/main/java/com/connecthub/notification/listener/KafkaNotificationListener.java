package com.connecthub.notification.listener;

import com.connecthub.notification.client.AuthServiceClient;
import com.connecthub.notification.dto.UserProfileDto;
import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.service.EmailSender;
import com.connecthub.notification.service.FcmSender;
import com.connecthub.notification.service.NotifService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationListener {

    private final NotifService notifService;
    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;
    private final EmailSender emailSender;
    private final FcmSender fcmSender;

    @KafkaListener(
            topics = "notifications.offline",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processOfflineNotification(
            @Payload String messageJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {

            log.debug("Consuming {}[{}]@{}", topic, partition, offset);

            if (messageJson == null || messageJson.isBlank()) {
                throw new IllegalArgumentException("Offline notification payload is blank");
            }

            Map<String, Object> payload = objectMapper.readValue(
                    messageJson,
                    new TypeReference<Map<String, Object>>() {}
            );

            validatePayload(payload);

            Notification notif = new Notification();

            notif.setRecipientId(toInteger(payload.get("recipientId")));
            notif.setActorId(toInteger(payload.get("actorId")));
            notif.setType((String) payload.get("type"));
            notif.setTitle((String) payload.get("title"));
            notif.setMessage((String) payload.get("message"));
            notif.setRoomId((String) payload.get("roomId"));
            notif.setMessageId((String) payload.get("messageId"));
            notif.setRead(false);
            notif.setCreatedAt(LocalDateTime.now());

            notifService.send(notif);

            dispatchPushNotification(notif);

            maybeSendMissedDirectMessageEmail(payload, notif);

            log.info(
                    "Processed offline notification for user {} from {}[{}]@{}",
                    notif.getRecipientId(),
                    topic,
                    partition,
                    offset
            );

        } catch (Exception e) {

            log.error(
                    "Failed to process Kafka notification message at {}[{}]@{} : {}",
                    topic,
                    partition,
                    offset,
                    e.getMessage(),
                    e
            );

            throw new com.connecthub.notification.exception.NotificationProcessingException("Kafka notification processing failed", e);
        }
    }

    private void validatePayload(Map<String, Object> payload) {

        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Offline notification payload is empty");
        }

        if (toInteger(payload.get("recipientId")) == null) {
            throw new IllegalArgumentException("recipientId is required");
        }

        if (isBlank(payload.get("type"))) {
            throw new IllegalArgumentException("type is required");
        }

        if (isBlank(payload.get("title"))) {
            throw new IllegalArgumentException("title is required");
        }

        if (isBlank(payload.get("message"))) {
            throw new IllegalArgumentException("message is required");
        }
    }

    private Integer toInteger(Object value) {

        if (value instanceof Integer integer) {
            return integer;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String text && !text.isBlank()) {
            return Integer.valueOf(text);
        }

        return null;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private void dispatchPushNotification(Notification notif) {

        try {

            Map<String, String> data = new HashMap<>();

            if (notif.getRoomId() != null) {
                data.put("roomId", notif.getRoomId());
            }

            if (notif.getMessageId() != null) {
                data.put("messageId", notif.getMessageId());
            }

            if (notif.getType() != null) {
                data.put("type", notif.getType());
            }

            fcmSender.sendToUser(
                    notif.getRecipientId(),
                    notif.getTitle(),
                    notif.getMessage(),
                    data
            );

        } catch (Exception e) {

            log.warn(
                    "Push notification skipped for user {} : {}",
                    notif.getRecipientId(),
                    e.getMessage()
            );
        }
    }

    private void maybeSendMissedDirectMessageEmail(
            Map<String, Object> payload,
            Notification notif) {

        if (!"NEW_MESSAGE".equals(notif.getType())) {
            return;
        }

        if (!"DM".equals(String.valueOf(payload.getOrDefault("roomType", "")))) {
            return;
        }

        try {

            UserProfileDto recipient =
                    authServiceClient.getProfile(notif.getRecipientId());

            if (recipient == null
                    || recipient.getEmail() == null
                    || recipient.getEmail().isBlank()) {
                return;
            }

            LocalDateTime lastSeenAt = recipient.getLastSeenAt();

            if (lastSeenAt == null
                    || lastSeenAt.isAfter(LocalDateTime.now().minusMinutes(30))) {
                return;
            }

            UserProfileDto actor =
                    notif.getActorId() == null
                            ? null
                            : authServiceClient.getProfile(notif.getActorId());

            String senderName = "Someone";
            if (actor != null) {
                if (actor.getFullName() != null && !actor.getFullName().isBlank()) {
                    senderName = actor.getFullName();
                } else if (actor.getUsername() != null && !actor.getUsername().isBlank()) {
                    senderName = actor.getUsername();
                }
            }

            emailSender.sendMissedDirectMessage(
                    recipient.getEmail(),
                    senderName,
                    notif.getMessage(),
                    notif.getRoomId()
            );

        } catch (Exception e) {

            log.warn(
                    "Missed-message email skipped for user {} : {}",
                    notif.getRecipientId(),
                    e.getMessage()
            );
        }
    }

    @KafkaListener(
            topics = "notifications.offline.dlq",
            groupId = "notification-service-dlq-group"
    )
    public void handleDlq(
            @Payload String messageJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.error(
                "DLQ MESSAGE at {}@{} : {} — manual intervention required",
                topic,
                offset,
                messageJson
        );
    }
}