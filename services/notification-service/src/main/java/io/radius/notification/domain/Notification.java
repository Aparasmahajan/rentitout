package io.radius.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String title;

    private String body;

    @Column(name = "deep_link")
    private String deepLink;

    @Column(nullable = false)
    private String channel = "inbox";

    @Column(nullable = false)
    private String state = "NEW";

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(UUID userId, String kind, String title, String body, String deepLink,
                        String channel, String eventId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.deepLink = deepLink;
        this.channel = channel == null ? "inbox" : channel;
        this.eventId = eventId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getDeepLink() { return deepLink; }
    public String getChannel() { return channel; }
    public String getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }

    public void markSent() { this.state = "SENT"; }
    public void markFailed() { this.state = "FAILED"; }

}
