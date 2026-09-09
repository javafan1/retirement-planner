package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityContinuationTest.*;

class Stage5GExactHorizonTest {
    @Test
    void explicitPolicyPreservesOldConstructorsAndRejectsInvalidRanges() throws Exception {
        var p = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(p);
        var engine = new ProjectionEngine();
        var empty = ProjectionEvaluationContext.empty();
        assertEquals(ProjectionEvaluationContext.HorizonPolicy.CONFIGURED, empty.horizonPolicy());
        assertEquals(2034, engine.project(p, empty).getEndYear());
        assertEquals(2034, engine.project(p, empty.withEndingYear(2031)).getEndYear());
        assertEquals(2038, engine.project(p, empty.withEndingYear(2038)).getEndYear());
        assertEquals(2031, engine.project(p, empty.withExactEndingYear(2031)).getEndYear());
        assertEquals(2038, engine.project(p, empty.withExactEndingYear(2038)).getEndYear());
        assertEquals(ProjectionEvaluationContext.HorizonPolicy.EXTEND_TO_REQUESTED,
                new ProjectionEvaluationContext(Optional.empty(), Optional.empty(), Optional.of(2031)).horizonPolicy());
        assertThrows(IllegalArgumentException.class, () -> engine.project(p, empty.withExactEndingYear(2029)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEvaluationContext(Optional.empty(),
                Optional.empty(), Optional.empty(), ProjectionEvaluationContext.HorizonPolicy.EXACT_REQUESTED));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEvaluationContext(Optional.empty(),
                Optional.empty(), Optional.of(2031), ProjectionEvaluationContext.HorizonPolicy.CONFIGURED));
        assertEquals(before, Stage4TestPlans.json(p));
    }

    @ParameterizedTest
    @ValueSource(ints = {2031, 2034, 2035, 2040})
    void independentReferenceUsesExactSnapshotAndActualInvocationCounts(int deathYear) throws Exception {
        var p = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(p);
        var req = request(p, mortality(p, List.of(2031), List.of(deathYear)));
        var work = new ArrayList<LongevityWeightedEvaluationWork>();
        var result = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req, work::add);
        var lifetime = new HouseholdLifetimeScenarioMapper().map(req.longevityScenarios().scenarios().getFirst());
        var projection = new ProjectionEngine().project(p, ProjectionEvaluationContext
                .withSocialSecurityStrategy(req.strategy(), lifetime).withExactEndingYear(deathYear - 1));
        assertEquals(deathYear - 1, projection.getEndYear());
        assertEquals(new EstateAtSecondDeathCalculator().calculate(p, projection, LocalDate.of(deathYear, 1, 1)),
                result.scenarioOutcomes().getFirst().estateSnapshot());
        assertEquals(1, result.actualProjectionRunCount());
        assertEquals(1, Collections.frequency(work, LongevityWeightedEvaluationWork.PROJECTION_STARTED));
        assertEquals(1, Collections.frequency(work, LongevityWeightedEvaluationWork.PROJECTION_COMPLETED));
        assertEquals(BigDecimal.ONE, result.totalEvaluatedProbability());
        assertEquals(before, Stage4TestPlans.json(p));
    }

    @Test
    void openingSnapshotRequiresNoEngineAndStillRejectsInconsistentDistributions() {
        var p = Stage4TestPlans.plan();
        p.getHousehold().addExpense(new Expense("Unneeded later cost", new BigDecimal("100000000"),
                GrowthCategory.GENERAL, LocalDate.of(2034, 1, 1), LocalDate.of(2034, 12, 31), ExpenseType.ONE_TIME));
        var req = request(p, mortality(p, List.of(2030), List.of(2030)));
        var work = new ArrayList<LongevityWeightedEvaluationWork>();
        var result = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req, work::add);
        assertEquals(0, result.actualProjectionRunCount());
        assertEquals(List.of(LongevityWeightedEvaluationWork.SCENARIO_STARTED,
                LongevityWeightedEvaluationWork.SCENARIO_COMPLETED), work);
        assertEquals(BigDecimal.ONE, result.totalEvaluatedProbability());
        var snapshot = new EstateAtSecondDeathCalculator().calculateOpening(p, LocalDate.of(2030, 1, 1));
        assertEquals(snapshot, result.scenarioOutcomes().getFirst().estateSnapshot());
        var factor = new EstatePresentValueCalculator().discountFactor(req.valuationDate(), LocalDate.of(2030, 1, 1),
                p.getPlanningAssumptions().getGeneralInflationRate(), req.realDiscountRate());
        assertEquals(snapshot.nominalAfterTaxEstate().multiply(factor), result.expectedPvAfterTaxEstate());
        p.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2030, new BigDecimal("500000"), BigDecimal.ONE));
        var invalid = request(p, req.longevityScenarios());
        var error = assertThrows(IllegalStateException.class,
                () -> new LongevityWeightedIntegratedStrategyEvaluator().evaluate(invalid));
        assertTrue(error.getCause().getMessage().contains("already-distributed RMD"));
    }

    @ParameterizedTest
    @ValueSource(ints = {2030, 2031, 2034})
    void failureIsRelevantOnlyThroughTheSnapshotYear(int expenseYear) {
        var p = Stage4TestPlans.plan();
        p.getHousehold().addExpense(new Expense("Cost", new BigDecimal("100000000"), GrowthCategory.GENERAL,
                LocalDate.of(expenseYear, 1, 1), LocalDate.of(expenseYear, 12, 31), ExpenseType.ONE_TIME));
        var req = request(p, mortality(p, List.of(2031), List.of(2031)));
        if (expenseYear == 2030) {
            assertThrows(IllegalStateException.class, () -> new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req));
        } else {
            assertEquals(1, new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req).scenarioOutcomes().size());
        }
    }
}
