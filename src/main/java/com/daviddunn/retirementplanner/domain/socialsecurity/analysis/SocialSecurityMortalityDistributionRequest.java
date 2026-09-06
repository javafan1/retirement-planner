package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.Objects;

/** Person-specific inputs for a reproducible table-derived distribution. */
public record SocialSecurityMortalityDistributionRequest(
        LocalDate dateOfBirth,
        LocalDate mortalityBaseDate,
        SocialSecurityMortalityCategory mortalityCategory,
        SocialSecurityMortalityAdjustment mortalityAdjustment) {

    public SocialSecurityMortalityDistributionRequest(
            LocalDate dateOfBirth,
            LocalDate mortalityBaseDate,
            SocialSecurityMortalityCategory mortalityCategory) {
        this(dateOfBirth, mortalityBaseDate, mortalityCategory,
                SocialSecurityMortalityAdjustment.standard());
    }

    public SocialSecurityMortalityDistributionRequest {
        Objects.requireNonNull(dateOfBirth, "Date of birth is required.");
        Objects.requireNonNull(mortalityBaseDate, "Mortality base date is required.");
        Objects.requireNonNull(mortalityCategory, "Mortality category is required.");
        Objects.requireNonNull(mortalityAdjustment, "Mortality adjustment is required.");
        if (mortalityBaseDate.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException(
                    "Mortality base date "
                            + mortalityBaseDate
                            + " precedes date of birth "
                            + dateOfBirth
                            + ".");
        }
    }
}
