package io.radius.notification.service;

import io.radius.notification.domain.Notification;
import io.radius.notification.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The inbox, plus a live stream for whoever has the app open.
 *
 * The emitter map is per-instance, which is fine while one replica serves a
 * member. Behind more than one, put the fan-out on a Redis pub/sub channel —
 * the shape of this class does not change.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final List<Channel> channels;
    private final Map<UUID, List<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository repo, List<Channel> channels) {
        this.repo = repo;
        this.channels = channels;
    }

    /** A delivery route. Push and SMS implement this; the inbox is always written. */
    public interface Channel {
        String name();
        boolean supports(String hint);
        void deliver(Notification notification);
    }

    @Transactional
    public void record(UUID userId, String kind, String title, String body, String deepLink,
                       String channelHint, String eventId) {
        if (eventId != null && repo.existsByEventId(eventId)) return;    // Kafka redelivered it

        Notification saved = repo.save(new Notification(userId, kind, title, body, deepLink,
                channelHint, eventId));
        push(saved);

        channels.stream()
                .filter(c -> c.supports(channelHint))
                .forEach(c -> {
                    try {
                        c.deliver(saved);
                        saved.markSent();
                    } catch (RuntimeException e) {
                        saved.markFailed();
                        log.warn("channel {} failed for notification {}", c.name(), saved.getId(), e);
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<Notification> inbox(UUID userId, int limit) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(Math.clamp(limit, 1, 100)));
    }

    @Transactional(readOnly = true)
    public long unread(UUID userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repo.markAllRead(userId);
    }

    public SseEmitter stream(UUID userId) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        listeners.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    private void push(Notification notification) {
        List<SseEmitter> emitters = listeners.get(notification.getUserId());
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(Map.of(
                        "id", notification.getId(),
                        "kind", notification.getKind(),
                        "title", notification.getTitle(),
                        "body", notification.getBody() == null ? "" : notification.getBody(),
                        "deepLink", notification.getDeepLink() == null ? "" : notification.getDeepLink())));
            } catch (IOException | IllegalStateException e) {
                remove(notification.getUserId(), emitter);
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = listeners.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) listeners.remove(userId);
        }
    }
}
