package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class LongevityWeightedComparisonTestSupport {
    static HouseholdLongevityScenarios scenarios(RetirementPlan plan) {
        return PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(71, new BigDecimal("0.25")),
                        new SocialSecurityMortalityProbability(74, new BigDecimal("0.75"))),
                List.of(new SocialSecurityMortalityProbability(72, BigDecimal.ONE)));
    }

    static SocialSecurityHouseholdClaimingStrategy strategy(RetirementPlan plan, int primaryAge, int spouseAge) {
        var p = plan.getHousehold().getPrimaryPerson().getBirthDate();
        var s = plan.getHousehold().getSpouse().getBirthDate();
        return new SocialSecurityHouseholdClaimingStrategy(primaryAge, spouseAge,
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(p, primaryAge),
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(s, spouseAge),
                new SocialSecuritySurvivorClaimingCandidate(p.plusYears(62), 62, 0, "Primary explicit 62"),
                new SocialSecuritySurvivorClaimingCandidate(s.plusYears(62), 62, 0, "Spouse explicit 62"));
    }

    static LongevityWeightedIntegratedStrategyComparisonRequest request(RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates) {
        return new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios(plan),
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"));
    }

    static LongevityWeightedIntegratedStrategyResult direct(RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy, HouseholdLongevityScenarios scenarios) {
        return new LongevityWeightedIntegratedStrategyEvaluator().evaluate(new LongevityWeightedIntegratedStrategyRequest(
                plan, strategy, scenarios, LocalDate.of(2029, 7, 1), new BigDecimal("0.03")));
    }
}
