package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class LongevityWeightedProductionDistributionTest {
    @Test
    void exactProductionDistributionCoversTerminalMassAndLeavesSourceUnchangedAcrossThousandsOfRuns() throws Exception {
        var plan = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(plan);
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        var assumptions = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), LocalDate.of(2030, 1, 1), table.metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        var prepared = new HouseholdLongevityScenarioFactory(table).create(
                plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), assumptions);
        long started = System.nanoTime();
        var result = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(
                new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), prepared,
                        LocalDate.of(2029, 7, 1), new BigDecimal("0.03")));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        System.out.println("Stage 4 one-strategy benchmark: original=" + result.originalScenarioCount()
                + ", projections=" + result.actualProjectionRunCount() + ", elapsedMillis=" + elapsedMillis);
        assertTrue(result.actualProjectionRunCount() > 2000);
        assertEquals(prepared.scenarios().size(), result.originalScenarioCount());
        assertEquals(prepared.scenarios().stream().filter(s -> s.jointProbability().signum() > 0).count(),
                result.actualProjectionRunCount());
        assertEquals(prepared.scenarios().stream().map(SocialSecurityJointMortalityScenario::jointProbability)
                .reduce(BigDecimal.ZERO, BigDecimal::add), result.totalEvaluatedProbability());
        assertEquals(0, BigDecimal.ONE.compareTo(result.totalEvaluatedProbability()));
        var terminal = result.scenarioOutcomes().getLast();
        assertEquals(2080, terminal.primaryDeathYear());
        assertEquals(2082, terminal.spouseDeathYear());
        assertEquals(LocalDate.of(2081, 12, 31), terminal.estateSnapshot().balanceDate());
        assertTrue(terminal.probability().signum() > 0);
        assertEquals(before, Stage4TestPlans.json(plan));
    }
}
