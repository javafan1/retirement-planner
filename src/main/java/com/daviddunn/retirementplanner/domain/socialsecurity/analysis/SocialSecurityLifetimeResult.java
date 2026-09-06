package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Complete nominal result for one finite strategy request. Present value and
 * probability weighting are intentionally outside this foundation pass.
 */
public record SocialSecurityLifetimeResult(
        SocialSecurityStrategyRequest request,
        List<SocialSecurityMonthlyResult> monthlyResults,
        List<SocialSecurityAnnualResult> annualResults,
        BigDecimal primaryTotalOwnRetirementBenefits,
        BigDecimal primaryTotalSpousalExcessBenefits,
        BigDecimal primaryTotalSurvivorBenefits,
        BigDecimal spouseTotalOwnRetirementBenefits,
        BigDecimal spouseTotalSpousalExcessBenefits,
        BigDecimal spouseTotalSurvivorBenefits,
        BigDecimal householdLifetimeNominalBenefits) {

    public SocialSecurityLifetimeResult {

        Objects.requireNonNull(request, "Strategy request is required.");
        monthlyResults = List.copyOf(
                Objects.requireNonNull(
                        monthlyResults,
                        "Monthly results are required."));
        annualResults = List.copyOf(
                Objects.requireNonNull(
                        annualResults,
                        "Annual results are required."));
        Objects.requireNonNull(
                primaryTotalOwnRetirementBenefits,
                "Primary lifetime own benefits are required.");
        Objects.requireNonNull(
                primaryTotalSpousalExcessBenefits,
                "Primary lifetime spousal excess benefits are required.");
        Objects.requireNonNull(
                primaryTotalSurvivorBenefits,
                "Primary lifetime survivor benefits are required.");
        Objects.requireNonNull(
                spouseTotalOwnRetirementBenefits,
                "Spouse lifetime own benefits are required.");
        Objects.requireNonNull(
                spouseTotalSpousalExcessBenefits,
                "Spouse lifetime spousal excess benefits are required.");
        Objects.requireNonNull(
                spouseTotalSurvivorBenefits,
                "Spouse lifetime survivor benefits are required.");
        Objects.requireNonNull(
                householdLifetimeNominalBenefits,
                "Household lifetime benefits are required.");

        BigDecimal monthlyHouseholdTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::householdBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualHouseholdTotal = annualResults.stream()
                .map(SocialSecurityAnnualResult::householdBenefits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyPrimaryOwnTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::primaryOwnBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlySpouseOwnTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::spouseOwnBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyPrimarySpousalTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::primarySpousalExcessBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlySpouseSpousalTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::spouseSpousalExcessBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyPrimarySurvivorTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::primarySurvivorBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlySpouseSurvivorTotal = monthlyResults.stream()
                .map(SocialSecurityMonthlyResult::spouseSurvivorBenefit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (primaryTotalOwnRetirementBenefits.compareTo(
                monthlyPrimaryOwnTotal) != 0
                || spouseTotalOwnRetirementBenefits.compareTo(
                monthlySpouseOwnTotal) != 0) {
            throw new IllegalArgumentException(
                    "Lifetime owner benefits must reconcile to monthly results.");
        }

        if (primaryTotalSpousalExcessBenefits.compareTo(
                monthlyPrimarySpousalTotal) != 0
                || spouseTotalSpousalExcessBenefits.compareTo(
                monthlySpouseSpousalTotal) != 0) {
            throw new IllegalArgumentException(
                    "Lifetime spousal benefits must reconcile to monthly results.");
        }

        if (primaryTotalSurvivorBenefits.compareTo(
                monthlyPrimarySurvivorTotal) != 0
                || spouseTotalSurvivorBenefits.compareTo(
                monthlySpouseSurvivorTotal) != 0) {
            throw new IllegalArgumentException(
                    "Lifetime survivor benefits must reconcile to monthly results.");
        }

        if (householdLifetimeNominalBenefits.compareTo(
                monthlyHouseholdTotal) != 0
                || householdLifetimeNominalBenefits.compareTo(
                annualHouseholdTotal) != 0) {
            throw new IllegalArgumentException(
                    "Lifetime benefits must reconcile to monthly and annual results.");
        }
    }
}
