package io.radius.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per event, written in the business transaction. The publisher drains
 * it. Every service that emits events owns a table with exactly this shape —
 * see the {@code V*__outbox.sql} migration in each service.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    /** Kafka topic. */
    @Column(nullable = false)
    private String topic;

    /** Partition key — always the aggregate id, so per-aggregate ordering holds. */
    @Column(name = "event_key", nullable = false)
    private String key;

    @Column(name = "event_type", nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxEvent() {}

    public OutboxEvent(String topic, String key, String type, String payload) {
        this.id = UUID.randomUUID();
        this.topic = topic;
        this.key = key;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getKey() { return key; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }

    public void markPublished() { this.publishedAt = Instant.now(); }
    public void markAttempt() { this.attempts++; }
}
