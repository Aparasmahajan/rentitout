package io.radius.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One thread per request — there is no chat without something to talk about. */
@Entity
@Table(name = "message")
public class Message {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Message() {}

    public Message(UUID requestId, UUID senderId, String body) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.senderId = senderId;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getSenderId() { return senderId; }
    public String getBody() { return body; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }

    public void markRead() { if (readAt == null) this.readAt = Instant.now(); }
}
