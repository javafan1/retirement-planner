package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Gross Social Security valuation for one supplied deterministic strategy. */
public record SocialSecurityStrategyValuation(
        SocialSecurityLifetimeResult strategyResult,
        List<SocialSecurityMonthlyValuation> monthlyValuations,
        BigDecimal nominalLifetimeBenefits,
        BigDecimal realLifetimeBenefits,
        BigDecimal presentValue) {

    public SocialSecurityStrategyValuation {
        Objects.requireNonNull(strategyResult, "Strategy result is required.");
        monthlyValuations = List.copyOf(
                Objects.requireNonNull(
                        monthlyValuations,
                        "Monthly valuations are required."));
        Objects.requireNonNull(
                nominalLifetimeBenefits,
                "Nominal lifetime benefits are required.");
        Objects.requireNonNull(
                realLifetimeBenefits,
                "Real lifetime benefits are required.");
        Objects.requireNonNull(presentValue, "Present value is required.");

        if (monthlyValuations.size()
                != strategyResult.monthlyResults().size()) {
            throw new IllegalArgumentException(
                    "Monthly valuations must align with strategy results.");
        }
        if (!monthlyValuations.isEmpty()) {
            SocialSecurityMonthlyValuation last = monthlyValuations.getLast();
            if (nominalLifetimeBenefits.compareTo(
                    last.cumulativeNominalBenefit()) != 0
                    || realLifetimeBenefits.compareTo(
                    last.cumulativeRealBenefit()) != 0
                    || presentValue.compareTo(
                    last.cumulativePresentValue()) != 0) {
                throw new IllegalArgumentException(
                        "Strategy valuation must reconcile to monthly valuations.");
            }
        }
    }
}
