package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.Objects;

/** Distribution plus the exact versioned inputs and modeling convention used. */
public record SocialSecurityMortalityDistributionResult(
        SocialSecurityMortalityDistribution distribution,
        SocialSecurityMortalityTableMetadata tableMetadata,
        SocialSecurityMortalityDistributionRequest request,
        int firstModeledAttainedAge,
        int terminalDeathAge,
        SocialSecurityMortalityPartialYearConvention partialYearConvention) {

    public SocialSecurityMortalityDistributionResult {
        Objects.requireNonNull(distribution, "Mortality distribution is required.");
        Objects.requireNonNull(tableMetadata, "Mortality table metadata is required.");
        Objects.requireNonNull(request, "Mortality distribution request is required.");
        Objects.requireNonNull(
                partialYearConvention,
                "Partial-year convention is required.");
    }
}
