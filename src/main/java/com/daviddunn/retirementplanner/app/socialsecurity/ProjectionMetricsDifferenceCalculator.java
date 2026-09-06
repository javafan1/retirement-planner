package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;

import java.math.BigDecimal;
import java.util.Objects;

/** Calculates raw candidate-minus-baseline differences using established metrics. */
final class ProjectionMetricsDifferenceCalculator {

    private ProjectionMetricsDifferenceCalculator() {
    }

    static ProjectionMetrics subtract(
            ProjectionMetrics candidate,
            ProjectionMetrics baseline) {
        Objects.requireNonNull(candidate, "Candidate metrics are required.");
        Objects.requireNonNull(baseline, "Baseline metrics are required.");
        return new ProjectionMetrics(
                difference(candidate.totalInvestmentGrowth(), baseline.totalInvestmentGrowth()),
                difference(candidate.totalIncome(), baseline.totalIncome()),
                difference(candidate.totalTaxes(), baseline.totalTaxes()),
                difference(candidate.peakAnnualTax(), baseline.peakAnnualTax()),
                difference(candidate.endingInvestableAssets(), baseline.endingInvestableAssets()),
                difference(candidate.endingNetWorth(), baseline.endingNetWorth()),
                difference(candidate.afterTaxEstate(), baseline.afterTaxEstate()),
                difference(candidate.lifetimePortfolioWithdrawals(),
                        baseline.lifetimePortfolioWithdrawals()),
                difference(candidate.lifetimeRothConversions(),
                        baseline.lifetimeRothConversions()),
                difference(candidate.lifetimeRequiredMinimumDistributions(),
                        baseline.lifetimeRequiredMinimumDistributions()),
                difference(candidate.lifetimeMedicarePremiums(),
                        baseline.lifetimeMedicarePremiums()),
                difference(candidate.lifetimeHouseholdSocialSecurity(),
                        baseline.lifetimeHouseholdSocialSecurity()),
                difference(candidate.lifetimePrimarySocialSecurity(),
                        baseline.lifetimePrimarySocialSecurity()),
                difference(candidate.lifetimeSpouseSocialSecurity(),
                        baseline.lifetimeSpouseSocialSecurity()));
    }

    private static BigDecimal difference(BigDecimal candidate, BigDecimal baseline) {
        return candidate.subtract(baseline);
    }
}
