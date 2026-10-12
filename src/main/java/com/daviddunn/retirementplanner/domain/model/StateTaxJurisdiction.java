package com.daviddunn.retirementplanner.domain.model;

/** Production-enabled full-year residency choices, independent of the research dataset. */
public enum StateTaxJurisdiction {
    MI("Michigan"),
    FL("Florida");

    private final String displayName;

    StateTaxJurisdiction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Persist stable jurisdiction codes; numeric enum ordinals are not jurisdictions. */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static StateTaxJurisdiction fromCode(String code) {
        return valueOf(java.util.Objects.requireNonNull(code, "State of residence is required."));
    }

    @Override
    public String toString() {
        return displayName;
    }
}
