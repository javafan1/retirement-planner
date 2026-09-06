package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** One row-major retirement-age cell, including either its full result or failure. */
public record IntegratedRetirementClaimingGridCell(
        int primaryRetirementAge,
        int spouseRetirementAge,
        LocalDate primaryRetirementClaimDate,
        LocalDate spouseRetirementClaimDate,
        SocialSecurityHouseholdClaimingStrategy strategy,
        Optional<IntegratedSocialSecurityStrategyResult> integratedResult,
        Optional<ProjectionMetrics> differencesFromCurrentPlan,
        Optional<IntegratedSocialSecurityStrategyEvaluationFailure> failure) {

    public IntegratedRetirementClaimingGridCell {
        Objects.requireNonNull(primaryRetirementClaimDate);
        Objects.requireNonNull(spouseRetirementClaimDate);
        Objects.requireNonNull(strategy);
        integratedResult = Objects.requireNonNull(integratedResult);
        differencesFromCurrentPlan = Objects.requireNonNull(differencesFromCurrentPlan);
        failure = Objects.requireNonNull(failure);
        if (integratedResult.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("A grid cell requires either a result or failure.");
        }
        if (integratedResult.isPresent() != differencesFromCurrentPlan.isPresent()) {
            throw new IllegalArgumentException(
                    "Successful grid cells require differences; failed cells cannot have them.");
        }
    }

    public boolean successful() {
        return integratedResult.isPresent();
    }

    public static IntegratedRetirementClaimingGridCell success(
            SocialSecurityHouseholdClaimingStrategy strategy,
            IntegratedSocialSecurityStrategyResult result,
            ProjectionMetrics differences) {
        return new IntegratedRetirementClaimingGridCell(
                strategy.primaryRetirementAge(), strategy.spouseRetirementAge(),
                strategy.primaryRetirementClaimDate(), strategy.spouseRetirementClaimDate(),
                strategy, Optional.of(result), Optional.of(differences), Optional.empty());
    }

    public static IntegratedRetirementClaimingGridCell failure(
            SocialSecurityHouseholdClaimingStrategy strategy,
            IntegratedSocialSecurityStrategyEvaluationFailure failure) {
        return new IntegratedRetirementClaimingGridCell(
                strategy.primaryRetirementAge(), strategy.spouseRetirementAge(),
                strategy.primaryRetirementClaimDate(), strategy.spouseRetirementClaimDate(),
                strategy, Optional.empty(), Optional.empty(), Optional.of(failure));
    }
}
