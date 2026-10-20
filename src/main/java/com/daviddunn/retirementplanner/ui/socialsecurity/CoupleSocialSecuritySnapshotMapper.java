package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.CoupleSocialSecuritySnapshot;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationResult;

/** Pure mappings. Restoration constructs immutable values; no calculators or mortality factories. */
final class CoupleSocialSecuritySnapshotMapper {
    private CoupleSocialSecuritySnapshotMapper() { }
    static CoupleSocialSecuritySnapshot capture(SocialSecurityStrategyAnalysisContext context,
            SocialSecuritySurvivorClaimingOptimizationResult result) {
        if (!CoupleSocialSecuritySnapshot.Request.from(context.request()).equals(CoupleSocialSecuritySnapshot.Request.from(result.request())))
            throw new IllegalArgumentException("Frozen request mismatch.");
        return CoupleSocialSecuritySnapshot.from(result, new CoupleSocialSecuritySnapshot.FrozenContext(
                context.primaryName(), context.spouseName(), context.longevityAssumptions(),
                context.primaryFraMonthlyBenefit(), context.spouseFraMonthlyBenefit(),
                context.primaryBenefitValuationYear(), context.spouseBenefitValuationYear(),
                context.primaryCurrentClaimDate(), context.spouseCurrentClaimDate()));
    }
    static SocialSecurityStrategyAnalysisContext context(CoupleSocialSecuritySnapshot saved) {
        var request = saved.request().restore();
        var frozen = saved.frozen(); var longevity = frozen.longevity();
        return new SocialSecurityStrategyAnalysisContext(request, frozen.primaryName(), frozen.spouseName(),
                frozen.primaryBenefit(), frozen.spouseBenefit(),
                frozen.primaryValuationYear(), frozen.spouseValuationYear(), frozen.primaryClaimDate(), frozen.spouseClaimDate(),
                longevity.primaryCategory(), longevity.spouseCategory(), longevity.primaryAdjustment(), longevity.spouseAdjustment(),
                longevity.tableMetadata(), longevity);
    }
}
