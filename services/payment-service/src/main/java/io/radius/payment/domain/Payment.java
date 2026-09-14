package io.radius.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {

    public enum State { PENDING, AUTHORISED, SETTLED, FAILED, REFUNDED }

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;

    @Column(name = "gateway_ref")
    private String gatewayRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(name = "deposit_minor", nullable = false)
    private long depositMinor;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private String state = State.PENDING.name();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Payment() {}

    public Payment(UUID requestId, UUID payerId, UUID payeeId, long amountMinor, long feeMinor,
                   long depositMinor, String currency, String gatewayRef) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.payerId = payerId;
        this.payeeId = payeeId;
        this.amountMinor = amountMinor;
        this.feeMinor = feeMinor;
        this.depositMinor = depositMinor;
        this.currency = currency;
        this.gatewayRef = gatewayRef;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getPayerId() { return payerId; }
    public UUID getPayeeId() { return payeeId; }
    public String getGatewayRef() { return gatewayRef; }
    public long getAmountMinor() { return amountMinor; }
    public long getFeeMinor() { return feeMinor; }
    public long getDepositMinor() { return depositMinor; }
    public String getCurrency() { return currency; }
    public String getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }

    public void moveTo(State target) {
        this.state = target.name();
        this.updatedAt = Instant.now();
    }

}
