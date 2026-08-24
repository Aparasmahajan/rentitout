package io.radius.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deposit_hold")
public class DepositHold {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "claim_state", nullable = false)
    private String claimState = "NONE";

    protected DepositHold() {}

    public DepositHold(UUID requestId, UUID payerId, long amountMinor) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.payerId = payerId;
        this.amountMinor = amountMinor;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getPayerId() { return payerId; }
    public long getAmountMinor() { return amountMinor; }
    public Instant getReleasedAt() { return releasedAt; }
    public String getClaimState() { return claimState; }

    public void release() { if (releasedAt == null) this.releasedAt = Instant.now(); }
    public void openClaim() { this.claimState = "OPEN"; }

}
