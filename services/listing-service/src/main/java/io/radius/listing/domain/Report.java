package io.radius.listing.domain;

import io.radius.common.web.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One report row for every reportable thing, keyed by {@code (targetType, targetId)}
 * rather than a table per kind. Adding a fourth reportable thing later is then a
 * new enum constant, not a new migration.
 *
 * The state machine follows the same rule as the booking one: legal transitions
 * are a map on the entity, and nothing outside {@code moveTo} sets a state.
 */
@Entity
@Table(name = "report")
public class Report {

    public enum TargetType { LISTING, COMMENT, RATING }

    public enum State { OPEN, REVIEWING, ACTIONED, DISMISSED }

    private static final Map<State, Set<State>> LEGAL = Map.of(
            State.OPEN,      Set.of(State.REVIEWING, State.ACTIONED, State.DISMISSED),
            State.REVIEWING, Set.of(State.ACTIONED, State.DISMISSED),
            State.ACTIONED,  Set.of(),
            State.DISMISSED, Set.of()
    );

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false)
    private String reason;

    private String detail;

    @Column(nullable = false)
    private String state = State.OPEN.name();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String note;

    protected Report() {}

    public Report(UUID reporterId, TargetType targetType, UUID targetId, String reason, String detail) {
        this.reporterId = reporterId;
        this.targetType = targetType.name();
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public TargetType getTargetType() { return TargetType.valueOf(targetType); }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public State getState() { return State.valueOf(state); }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getNote() { return note; }

    public boolean isOpen() {
        State s = getState();
        return s == State.OPEN || s == State.REVIEWING;
    }

    /**
     * The only way a report changes state. A decision records who made it and
     * when, so a queue that was worked can be audited afterwards.
     */
    public void moveTo(State next, UUID by, String note) {
        State current = getState();
        if (!LEGAL.getOrDefault(current, Set.of()).contains(next)) {
            throw ApiException.conflict("illegal_transition",
                    "A report cannot go from " + current + " to " + next);
        }
        this.state = next.name();
        if (next == State.ACTIONED || next == State.DISMISSED) {
            this.decidedBy = by;
            this.decidedAt = Instant.now();
            this.note = note;
        }
    }
}
