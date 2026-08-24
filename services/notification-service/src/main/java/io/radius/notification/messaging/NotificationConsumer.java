package io.radius.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * One fan-in topic. Any service that wants to reach a member publishes a
 * NotificationRequested and stops caring how it gets there — which is why
 * booking-service has no idea what a push token is.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public NotificationConsumer(NotificationService notifications, ObjectMapper mapper) {
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.NOTIFICATION, groupId = "notification-service.fanin")
    public void onNotification(@Payload String payload,
                               @Header(name = Topics.HEADER_EVENT_ID, required = false) String eventId)
            throws Exception {
        var e = mapper.readValue(payload, RadiusEvents.NotificationRequested.class);
        notifications.record(e.userId(), e.kind(), e.title(), e.body(), e.deepLink(),
                e.channelHint(), eventId);
        log.debug("notification recorded for {}", e.userId());
    }

    /** A welcome message is the cheapest way to prove sign-up worked. */
    @KafkaListener(topics = Topics.USER, groupId = "notification-service.user")
    public void onUser(@Payload String payload,
                       @Header(name = Topics.HEADER_EVENT_TYPE, required = false) String type,
                       @Header(name = Topics.HEADER_EVENT_ID, required = false) String eventId)
            throws Exception {
        if (!"UserRegistered".equals(type)) return;
        var e = mapper.readValue(payload, RadiusEvents.UserRegistered.class);
        notifications.record(e.userId(), "welcome", "Welcome to Radius",
                "Set your area and add a few tags so neighbours can find you.", "/me", "push", eventId);
    }
}
