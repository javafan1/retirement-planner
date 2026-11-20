package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

/** Optional worker-only observation at existing occurrence and proved financial reuse boundaries. */
public interface LongevityFinancialObservation {
    void starting(int order, SocialSecurityHouseholdClaimingStrategy strategy);
    void scenario(LongevityWeightedIntegratedScenarioOutcome outcome, HouseholdLifetimeScenario execution);
    void completed(LongevityWeightedStrategyAggregate aggregate);
    void failed(IntegratedSocialSecurityStrategyEvaluationFailure failure, RuntimeException cause);
    void reused(int order, SocialSecurityHouseholdClaimingStrategy strategy, int representative);
}
