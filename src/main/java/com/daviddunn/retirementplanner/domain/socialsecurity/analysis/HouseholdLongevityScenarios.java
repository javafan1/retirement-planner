package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.List;

/** Immutable prepared distributions, paired outcomes, and reproducible methodology. */
public final class HouseholdLongevityScenarios {

    private final AnalyzerLongevityAssumptions assumptions;
    private final SocialSecurityMortalityDistributionResult primary;
    private final SocialSecurityMortalityDistributionResult spouse;
    private final List<SocialSecurityJointMortalityScenario> scenarios;

    HouseholdLongevityScenarios(
            AnalyzerLongevityAssumptions assumptions,
            SocialSecurityMortalityDistributionResult primary,
            SocialSecurityMortalityDistributionResult spouse) {
        this.assumptions = assumptions;
        this.primary = primary;
        this.spouse = spouse;
        this.scenarios = HouseholdLongevityScenarioFactory.combine(
                primary.request().dateOfBirth(), spouse.request().dateOfBirth(),
                primary.distribution(), spouse.distribution());
    }

    public AnalyzerLongevityAssumptions assumptions() {
        return assumptions;
    }

    public SocialSecurityMortalityDistributionResult primary() {
        return primary;
    }

    public SocialSecurityMortalityDistributionResult spouse() {
        return spouse;
    }

    public List<SocialSecurityJointMortalityScenario> scenarios() {
        return scenarios;
    }
}
