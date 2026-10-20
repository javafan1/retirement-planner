package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationResult;

/** Applied only after existing job eligibility. Structured candidate failures do not imply fatal job failure. */
public final class AnalysisSuccessPolicy {
    private AnalysisSuccessPolicy() { }
    public static boolean accepted(SocialSecuritySurvivorClaimingOptimizationResult r) { return !r.evaluatedStrategies().isEmpty(); }
    public static boolean accepted(IntegratedSocialSecurityStrategyComparisonResult r) { return r.entries().stream().anyMatch(e -> e.successful()); }
    public static boolean accepted(IntegratedSocialSecurityCompleteStrategySearchResult r) { return !r.rankedSuccessfulEntries().isEmpty(); }
    public static boolean accepted(SinglePersonIntegratedAnalysis r) { return accepted(r.result()); }
    public static boolean accepted(LongevityWeightedIntegratedStrategyComparisonResult r) { return !r.rankedSuccessfulEntries().isEmpty(); }
    public static boolean accepted(SinglePersonMortalityAnalysis.Result r) { return r.entries().size() == 9; }
}
