package com.daviddunn.retirementplanner.domain.baseline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

public class ProjectionBaseline {

    private final RetirementPlanSnapshot snapshot;

    private final LocalDateTime savedAt;

    private final String description;

    @JsonCreator
    public ProjectionBaseline(
            @JsonProperty("snapshot")
            RetirementPlanSnapshot snapshot,

            @JsonProperty("savedAt")
            LocalDateTime savedAt,

            @JsonProperty("description")
            String description) {

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

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public String getDescription() {
        return description;
    }
}