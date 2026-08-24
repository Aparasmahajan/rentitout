package io.radius.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_search")
public class SavedSearch {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String label;

    @Column(name = "raw_query")
    private String rawQuery;

    @Column(name = "parsed_json")
    private String parsedJson;

    @Column(name = "radius_km", nullable = false)
    private int radiusKm = 5;

    @Column(name = "digest_frequency", nullable = false)
    private String digestFrequency = "daily";

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SavedSearch() {}

    public SavedSearch(UUID userId, String label, String rawQuery, String parsedJson,
                       int radiusKm, String digestFrequency) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.label = label;
        this.rawQuery = rawQuery;
        this.parsedJson = parsedJson;
        this.radiusKm = radiusKm;
        this.digestFrequency = digestFrequency;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getRawQuery() { return rawQuery; }
    public String getParsedJson() { return parsedJson; }
    public int getRadiusKm() { return radiusKm; }
    public String getDigestFrequency() { return digestFrequency; }
    public Instant getLastRunAt() { return lastRunAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRun() { this.lastRunAt = Instant.now(); }

}
