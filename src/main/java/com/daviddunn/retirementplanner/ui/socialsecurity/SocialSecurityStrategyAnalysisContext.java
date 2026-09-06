package com.daviddunn.retirementplanner.ui.socialsecurity;

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
        SocialSecurityMortalityTableMetadata mortalityMetadata) {

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
    }
}
