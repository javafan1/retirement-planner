package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.AnalyzerLongevityAssumptions;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityPartialYearConvention;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityCategory;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityTableMetadata;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable UI/application boundary for one read-only analyzer run. */
public record SocialSecurityStrategyAnalysisContext(
        SocialSecuritySurvivorClaimingOptimizationRequest request,
        String primaryName,
        String spouseName,
        BigDecimal primaryFraMonthlyBenefit,
        BigDecimal spouseFraMonthlyBenefit,
        int primaryBenefitValuationYear,
        int spouseBenefitValuationYear,
        LocalDate primaryCurrentClaimDate,
        LocalDate spouseCurrentClaimDate,
        SocialSecurityMortalityCategory primaryMortalityCategory,
        SocialSecurityMortalityCategory spouseMortalityCategory,
        SocialSecurityMortalityAdjustment primaryMortalityAdjustment,
        SocialSecurityMortalityAdjustment spouseMortalityAdjustment,
        SocialSecurityMortalityTableMetadata mortalityMetadata,
        AnalyzerLongevityAssumptions longevityAssumptions) {

    /** Compatibility constructor for callers using the original analyzer boundary. */
    public SocialSecurityStrategyAnalysisContext(
            SocialSecuritySurvivorClaimingOptimizationRequest request,
            String primaryName,
            String spouseName,
            BigDecimal primaryFraMonthlyBenefit,
            BigDecimal spouseFraMonthlyBenefit,
            int primaryBenefitValuationYear,
            int spouseBenefitValuationYear,
            LocalDate primaryCurrentClaimDate,
            LocalDate spouseCurrentClaimDate,
            SocialSecurityMortalityCategory primaryMortalityCategory,
            SocialSecurityMortalityCategory spouseMortalityCategory,
            SocialSecurityMortalityAdjustment primaryMortalityAdjustment,
            SocialSecurityMortalityAdjustment spouseMortalityAdjustment,
            SocialSecurityMortalityTableMetadata mortalityMetadata) {
        this(request, primaryName, spouseName, primaryFraMonthlyBenefit, spouseFraMonthlyBenefit,
                primaryBenefitValuationYear, spouseBenefitValuationYear,
                primaryCurrentClaimDate, spouseCurrentClaimDate,
                primaryMortalityCategory, spouseMortalityCategory,
                primaryMortalityAdjustment, spouseMortalityAdjustment, mortalityMetadata,
                new AnalyzerLongevityAssumptions(
                        primaryMortalityCategory, primaryMortalityAdjustment,
                        spouseMortalityCategory, spouseMortalityAdjustment,
                        request.retirementGridRequest().mortalityBaseDate(), mortalityMetadata,
                        SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL));
    }
    public SocialSecurityStrategyAnalysisContext {
        Objects.requireNonNull(request);
        Objects.requireNonNull(primaryName);
        Objects.requireNonNull(spouseName);
        Objects.requireNonNull(primaryFraMonthlyBenefit);
        Objects.requireNonNull(spouseFraMonthlyBenefit);
        Objects.requireNonNull(primaryCurrentClaimDate);
        Objects.requireNonNull(spouseCurrentClaimDate);
        Objects.requireNonNull(primaryMortalityCategory);
        Objects.requireNonNull(spouseMortalityCategory);
        Objects.requireNonNull(primaryMortalityAdjustment);
        Objects.requireNonNull(spouseMortalityAdjustment);
        Objects.requireNonNull(mortalityMetadata);
        Objects.requireNonNull(longevityAssumptions);
        if (primaryMortalityCategory != longevityAssumptions.primaryCategory()
                || spouseMortalityCategory != longevityAssumptions.spouseCategory()
                || !primaryMortalityAdjustment.equals(longevityAssumptions.primaryAdjustment())
                || !spouseMortalityAdjustment.equals(longevityAssumptions.spouseAdjustment())
                || !mortalityMetadata.equals(longevityAssumptions.tableMetadata())
                || !request.retirementGridRequest().mortalityBaseDate()
                        .equals(longevityAssumptions.mortalityBaseDate())) {
            throw new IllegalArgumentException("Analyzer mortality metadata must match longevity assumptions.");
        }
    }
}
