package com.daviddunn.retirementplanner.domain.baseline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

public class ProjectionBaseline {

    private final RetirementPlanSnapshot snapshot;

    private final LocalDateTime savedAt;

    private final String description;
    private final java.util.UUID projectionSnapshotId;

    @JsonCreator
    public ProjectionBaseline(
            @JsonProperty("snapshot")
            RetirementPlanSnapshot snapshot,

            @JsonProperty("savedAt")
            LocalDateTime savedAt,

            @JsonProperty("description")
            String description,
            @JsonProperty("projectionSnapshotId") java.util.UUID projectionSnapshotId) {

        this.projectionSnapshotId = projectionSnapshotId;

        this.snapshot =
                Objects.requireNonNull(
                        snapshot);

        this.savedAt =
                Objects.requireNonNull(
                        savedAt);

        this.description =
                Objects.requireNonNull(
                        description);
    }

    public RetirementPlanSnapshot getSnapshot() {
        return snapshot;
    }

    public ProjectionBaseline(RetirementPlanSnapshot snapshot, LocalDateTime savedAt, String description) {
        this(snapshot, savedAt, description, null);
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public java.util.UUID getProjectionSnapshotId() { return projectionSnapshotId; }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public String getDescription() {
        return description;
    }
}
