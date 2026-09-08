package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.Objects;

/** Mortality inputs only; conditioning is independent of financial valuation. */
public record AnalyzerLongevityAssumptions(
        SocialSecurityMortalityCategory primaryCategory,
        SocialSecurityMortalityAdjustment primaryAdjustment,
        SocialSecurityMortalityCategory spouseCategory,
        SocialSecurityMortalityAdjustment spouseAdjustment,
        LocalDate mortalityBaseDate,
        SocialSecurityMortalityTableMetadata tableMetadata,
        SocialSecurityMortalityPartialYearConvention partialYearConvention) {

    public AnalyzerLongevityAssumptions {
        Objects.requireNonNull(primaryCategory, "Primary mortality category is required.");
        Objects.requireNonNull(primaryAdjustment, "Primary mortality adjustment is required.");
        Objects.requireNonNull(spouseCategory, "Spouse mortality category is required.");
        Objects.requireNonNull(spouseAdjustment, "Spouse mortality adjustment is required.");
        Objects.requireNonNull(mortalityBaseDate, "Mortality conditioning date is required.");
        Objects.requireNonNull(tableMetadata, "Mortality table metadata is required.");
        Objects.requireNonNull(partialYearConvention, "Mortality timing convention is required.");
    }

    public boolean assumesIndependentMortality() {
        return true;
    }
}
