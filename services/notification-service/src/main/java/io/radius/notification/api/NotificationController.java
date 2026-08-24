package io.radius.notification.api;

import io.radius.common.security.AuthUser;
import io.radius.notification.domain.Notification;
import io.radius.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "The inbox, and a live stream for an open app")
public class NotificationController {

    public record NotificationDto(UUID id, String kind, String title, String body, String deepLink,
                                  String state, Instant createdAt, Instant readAt) {}

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationDto> inbox(AuthUser me, @RequestParam(defaultValue = "30") int limit) {
        return notifications.inbox(me.id(), limit).stream().map(NotificationController::toDto).toList();
    }

    @GetMapping("/unread")
    public Map<String, Long> unread(AuthUser me) {
        return Map.of("count", notifications.unread(me.id()));
    }

    @PostMapping("/read")
    public Map<String, Integer> markRead(AuthUser me) {
        return Map.of("marked", notifications.markAllRead(me.id()));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-sent events — cheaper than a socket for one-way traffic")
    public SseEmitter stream(AuthUser me) {
        return notifications.stream(me.id());
    }

    private static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getDeepLink(),
                n.getState(), n.getCreatedAt(), n.getReadAt());
    }
}
