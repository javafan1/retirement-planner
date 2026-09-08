package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.List;

/** Test-only preparation of exact, normalized distributions without changing the production API. */
public final class PreparedLongevityTestSupport {
    public static HouseholdLongevityScenarios create(LocalDate primaryBirth, LocalDate spouseBirth,
            LocalDate conditioning, List<SocialSecurityMortalityProbability> primary,
            List<SocialSecurityMortalityProbability> spouse) {
        var metadata = SocialSecurityMortalityTables.ssaPeriod2022().metadata();
        var convention = SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL;
        var assumptions = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), conditioning, metadata, convention);
        return new HouseholdLongevityScenarios(assumptions,
                new SocialSecurityMortalityDistributionResult(new SocialSecurityMortalityDistribution(primary),
                        metadata, new SocialSecurityMortalityDistributionRequest(primaryBirth, conditioning,
                        SocialSecurityMortalityCategory.MALE), primary.getFirst().deathAge() - 1, 120, convention),
                new SocialSecurityMortalityDistributionResult(new SocialSecurityMortalityDistribution(spouse),
                        metadata, new SocialSecurityMortalityDistributionRequest(spouseBirth, conditioning,
                        SocialSecurityMortalityCategory.FEMALE), spouse.getFirst().deathAge() - 1, 120, convention));
    }
}
