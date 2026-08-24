package io.radius.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout")
public class Payout {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(nullable = false)
    private String currency = "EUR";

    @Column(nullable = false)
    private String period;

    @Column(nullable = false)
    private String state = "PENDING";

    @Column(name = "statement_ref")
    private String statementRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payout() {}

    public Payout(UUID ownerId, long amountMinor, long feeMinor, String currency, String period) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.amountMinor = amountMinor;
        this.feeMinor = feeMinor;
        this.currency = currency;
        this.period = period;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public long getAmountMinor() { return amountMinor; }
    public long getFeeMinor() { return feeMinor; }
    public String getCurrency() { return currency; }
    public String getPeriod() { return period; }
    public String getState() { return state; }
    public String getStatementRef() { return statementRef; }
    public Instant getCreatedAt() { return createdAt; }

}
