package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.Objects;

/** Immutable identity and supported-age metadata for one mortality table. */
public record SocialSecurityMortalityTableMetadata(
        String tableId,
        String displayName,
        String sourceName,
        String sourceVersion,
        SocialSecurityMortalityTableType tableType,
        int minimumAge,
        int maximumAge,
        String sourceDescription) {

    public SocialSecurityMortalityTableMetadata {
        tableId = requireText(tableId, "Mortality table ID");
        displayName = requireText(displayName, "Mortality table display name");
        sourceName = requireText(sourceName, "Mortality table source name");
        sourceVersion = requireText(sourceVersion, "Mortality table source version");
        Objects.requireNonNull(tableType, "Mortality table type is required.");
        sourceDescription = requireText(
                sourceDescription,
                "Mortality table source description");
        if (minimumAge < 0) {
            throw new IllegalArgumentException(
                    "Mortality table minimum age cannot be negative.");
        }
        if (maximumAge <= minimumAge
                || maximumAge > SocialSecurityDeathDateCalculator.MAXIMUM_DEATH_AGE) {
            throw new IllegalArgumentException(
                    "Mortality table maximum age must be greater than minimum age and no greater than "
                            + SocialSecurityDeathDateCalculator.MAXIMUM_DEATH_AGE
                            + ".");
        }
    }

    private static String requireText(String value, String description) {
        Objects.requireNonNull(value, description + " is required.");
        if (value.isBlank()) {
            throw new IllegalArgumentException(description + " cannot be blank.");
        }
        return value;
    }
}
