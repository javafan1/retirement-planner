package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.List;
import java.util.Objects;

/** Immutable output for one deterministic full-plan strategy evaluation. */
public record IntegratedSocialSecurityStrategyResult(
        SocialSecurityHouseholdClaimingStrategy evaluatedStrategy,
        Projection projection,
        ProjectionMetrics metrics,
        boolean advancedSocialSecurityPathUsed,
        List<String> warnings) {

    public IntegratedSocialSecurityStrategyResult {
        Objects.requireNonNull(evaluatedStrategy, "Evaluated strategy is required.");
        Objects.requireNonNull(projection, "Projection is required.");
        Objects.requireNonNull(metrics, "Projection metrics are required.");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "Warnings are required."));
    }
}
