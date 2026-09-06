package com.daviddunn.retirementplanner.domain.projection.summary;

import java.math.BigDecimal;
import java.util.Objects;

/** Headless projection metrics using the Results Summary definitions. */
public record ProjectionMetrics(
        BigDecimal totalInvestmentGrowth,
        BigDecimal totalIncome,
        BigDecimal totalTaxes,
        BigDecimal peakAnnualTax,
        BigDecimal endingInvestableAssets,
        BigDecimal endingNetWorth,
        BigDecimal afterTaxEstate,
        BigDecimal lifetimePortfolioWithdrawals,
        BigDecimal lifetimeRothConversions,
        BigDecimal lifetimeRequiredMinimumDistributions,
        BigDecimal lifetimeMedicarePremiums,
        BigDecimal lifetimeHouseholdSocialSecurity,
        BigDecimal lifetimePrimarySocialSecurity,
        BigDecimal lifetimeSpouseSocialSecurity) {

    public ProjectionMetrics {
        Objects.requireNonNull(totalInvestmentGrowth);
        Objects.requireNonNull(totalIncome);
        Objects.requireNonNull(totalTaxes);
        Objects.requireNonNull(peakAnnualTax);
        Objects.requireNonNull(endingInvestableAssets);
        Objects.requireNonNull(endingNetWorth);
        Objects.requireNonNull(afterTaxEstate);
        Objects.requireNonNull(lifetimePortfolioWithdrawals);
        Objects.requireNonNull(lifetimeRothConversions);
        Objects.requireNonNull(lifetimeRequiredMinimumDistributions);
        Objects.requireNonNull(lifetimeMedicarePremiums);
        Objects.requireNonNull(lifetimeHouseholdSocialSecurity);
        Objects.requireNonNull(lifetimePrimarySocialSecurity);
        Objects.requireNonNull(lifetimeSpouseSocialSecurity);
    }
}
