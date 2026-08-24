package io.radius.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Percentage in basis points, so the fee is exact integer arithmetic. */
@Entity
@Table(name = "fee_config")
public class FeeConfig {

    @Id
    private UUID id;

    private String kind;

    @Column(name = "percent_bps", nullable = false)
    private int percentBps;

    @Column(name = "cap_minor")
    private Long capMinor;

    @Column(name = "min_fee_minor", nullable = false)
    private long minFeeMinor;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom = Instant.now();

    protected FeeConfig() {}

    public String getKind() { return kind; }
    public int getPercentBps() { return percentBps; }
    public Long getCapMinor() { return capMinor; }
    public long getMinFeeMinor() { return minFeeMinor; }

    /** Integer arithmetic all the way down — no rounding surprises on a cent. */
    public long feeFor(long amountMinor) {
        if (percentBps == 0 && minFeeMinor == 0) return 0;
        long fee = Math.max(amountMinor * percentBps / 10_000L, minFeeMinor);
        return capMinor == null ? fee : Math.min(fee, capMinor);
    }

}
