package io.radius.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Proof we have already processed a gateway callback. */
@Entity
@Table(name = "gateway_event")
public class GatewayEvent {

    @Id
    private UUID id;

    @Column(name = "gateway_ref", nullable = false)
    private String gatewayRef;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected GatewayEvent() {}

    public GatewayEvent(String gatewayRef, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.gatewayRef = gatewayRef;
        this.eventType = eventType;
        this.payload = payload;
    }

}
