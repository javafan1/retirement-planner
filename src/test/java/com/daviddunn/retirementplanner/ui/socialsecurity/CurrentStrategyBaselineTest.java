package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CurrentStrategyBaselineTest {
    static void scenario(RetirementPlan plan, DeathScenario scenario, Integer age) {
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), new DeathScenarioAssumptions(scenario,
                scenario == DeathScenario.BOTH_SURVIVE ? null : 2040, age, BigDecimal.ONE),
                old.getProjectionLengthYears(), old.getProjectionStartDate()));
    }

    @ParameterizedTest
    @CsvSource({"BOTH_SURVIVE,Not specified,Not specified", "PRIMARY_DIES,Not specified,65", "SPOUSE_DIES,65,Not specified"})
    void initializesOnlyTheLegitimateOwner(DeathScenario scenario, String primary, String spouse) {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        scenario(plan, scenario, 65);
        var baseline = CurrentStrategyBaseline.fromPlan(plan);
        assertEquals("67", baseline.elections().get(0).value());
        assertEquals("67", baseline.elections().get(1).value());
        assertEquals("Plan", baseline.elections().get(0).source());
        assertEquals("Plan", baseline.elections().get(1).source());
        assertEquals(primary, baseline.elections().get(2).value());
        assertEquals(spouse, baseline.elections().get(3).value());
        assertTrue(baseline.strategy().isEmpty());
        assertFalse(baseline.summary().contains("2040"));
    }

    @ParameterizedTest
    @CsvSource(value = {"'','',false", "'66','',false", "'','65',false", "66,65,true",
            "59,65,false", "71,65,true", "invalid,65,false", "60,70,true", "66.5,65,false"})
    void completenessAndSupportedAges(String primary, String spouse, boolean complete) {
        var baseline = CurrentStrategyBaseline.capture(LongevityWeightedAnalysisRequestFactoryTest.plan(), primary, spouse);
        assertEquals(complete, baseline.strategy().isPresent());
        assertEquals(complete, baseline.problem().isEmpty());
    }

    @Test void explainsMissingFieldsAndOverridesWithoutPlanMutation() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var missing = CurrentStrategyBaseline.fromPlan(plan);
        assertTrue(missing.problem().contains("Primary Survivor Benefit Claiming Age is not specified"));
        assertTrue(missing.problem().contains("Spouse Survivor Benefit Claiming Age is not specified"));
        scenario(plan, DeathScenario.PRIMARY_DIES, 65);
        var assumptions = plan.getPlanningAssumptions();
        var supplied = CurrentStrategyBaseline.capture(plan, "66", "65");
        assertEquals("Plan death scenario", supplied.elections().get(3).source());
        var overridden = CurrentStrategyBaseline.capture(plan, "66", "67");
        assertEquals("Analyzer", overridden.elections().get(2).source());
        assertEquals("Analyzer override", overridden.elections().get(3).source());
        assertSame(assumptions, plan.getPlanningAssumptions());
        assertEquals(65, assumptions.getDeathScenarioAssumptions().getSurvivorClaimingAge());
        assertEquals(2040, assumptions.getDeathScenarioAssumptions().getDeathYear());
        assertEquals(plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(66),
                overridden.strategy().orElseThrow().primarySurvivorElection().claimDate());
    }

    @Test void missingPersonOmitsBaselineWithoutFabrication() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var spouse = plan.getHousehold().getSpouse();
        spouse.removeIncomeSource(spouse.getIncomeSources().getFirst());
        var baseline = CurrentStrategyBaseline.capture(plan, "66", "67");
        assertTrue(baseline.strategy().isEmpty());
        assertTrue(baseline.problem().contains("Spouse requires one correctly owned Social Security record"));
    }
}
