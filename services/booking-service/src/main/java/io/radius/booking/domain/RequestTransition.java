package io.radius.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The audit trail. Written on every transition, never updated, never deleted. */
@Entity
@Table(name = "request_transition")
public class RequestTransition {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    private String reason;

    @Column(name = "at", nullable = false)
    private Instant at = Instant.now();

    protected RequestTransition() {}

    public RequestTransition(UUID requestId, String fromStatus, String toStatus, UUID actorId, String reason) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getAt() { return at; }
}
