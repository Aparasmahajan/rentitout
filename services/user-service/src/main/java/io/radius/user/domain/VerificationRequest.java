package io.radius.user.domain;

import io.radius.common.web.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One review. An IDENTITY check says "this is a real person we have seen ID
 * for"; a PROFESSIONAL check says "and we have seen their trade paperwork".
 *
 * Deliberately not called an endorsement anywhere the member can see. The badge
 * reads "ID checked" — the platform is stating what it looked at, not vouching
 * for the quality of anybody's wiring.
 */
@Entity
@Table(name = "verification_request")
public class VerificationRequest {

    public enum Kind { IDENTITY, PROFESSIONAL }

    public enum State { SUBMITTED, IN_REVIEW, APPROVED, REJECTED, WITHDRAWN }

    /** How long evidence is kept after a decision before it should be destroyed. */
    private static final int EVIDENCE_RETENTION_DAYS = 30;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String state = State.SUBMITTED.name();

    /** Comma-separated object keys. The bytes never come near this service. */
    @Column(name = "evidence_keys")
    private String evidenceKeys;

    @Column(name = "member_note")
    private String memberNote;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "purge_after")
    private LocalDate purgeAfter;

    protected VerificationRequest() {}

    public VerificationRequest(UUID userId, Kind kind, String evidenceKeys, String memberNote) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind.name();
        this.evidenceKeys = evidenceKeys;
        this.memberNote = memberNote;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getKind() { return kind; }
    public String getState() { return state; }
    public String getEvidenceKeys() { return evidenceKeys; }
    public String getMemberNote() { return memberNote; }
    public String getDecisionNote() { return decisionNote; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public LocalDate getPurgeAfter() { return purgeAfter; }

    public boolean isOpen() {
        return State.SUBMITTED.name().equals(state) || State.IN_REVIEW.name().equals(state);
    }

    public void claim(UUID adminId) {
        requireOpen();
        this.state = State.IN_REVIEW.name();
        this.decidedBy = adminId;
    }

    public void approve(UUID adminId, String note) {
        decide(State.APPROVED, adminId, note);
    }

    public void reject(UUID adminId, String note) {
        if (note == null || note.isBlank()) {
            // A rejection a member cannot act on is worse than no answer.
            throw ApiException.badRequest("reason_required", "Say why, so they can fix it and re-apply");
        }
        decide(State.REJECTED, adminId, note);
    }

    public void withdraw() {
        requireOpen();
        this.state = State.WITHDRAWN.name();
        this.decidedAt = Instant.now();
        this.purgeAfter = LocalDate.now();
    }

    private void decide(State target, UUID adminId, String note) {
        requireOpen();
        this.state = target.name();
        this.decidedBy = adminId;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
        this.purgeAfter = LocalDate.now().plusDays(EVIDENCE_RETENTION_DAYS);
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw ApiException.conflict("already_decided", "That request has already been dealt with");
        }
    }
}
