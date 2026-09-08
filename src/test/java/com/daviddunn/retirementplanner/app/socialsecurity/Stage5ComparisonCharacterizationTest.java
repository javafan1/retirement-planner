package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Stage5ComparisonCharacterizationTest {
    @Test
    void stageFourResultsAndDeterministicProjectionRepeatExactly() throws Exception {
        var plan = Stage4TestPlans.plan();
        var source = Stage4TestPlans.json(plan);
        var deterministic = Stage4TestPlans.json(new ProjectionEngine().project(plan));
        var scenarios = PreparedLongevityTestSupport.create(
                plan.getHousehold().getPrimaryPerson().getBirthDate(), plan.getHousehold().getSpouse().getBirthDate(),
                LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(71, new BigDecimal("0.25")),
                        new SocialSecurityMortalityProbability(74, new BigDecimal("0.75"))),
                List.of(new SocialSecurityMortalityProbability(72, BigDecimal.ONE)));
        var strategy = Stage4TestPlans.strategy(plan);
        var request = new LongevityWeightedIntegratedStrategyRequest(plan, strategy, scenarios,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"));
        var evaluator = new LongevityWeightedIntegratedStrategyEvaluator();
        var first = evaluator.evaluate(request);
        assertEquals(2, first.actualProjectionRunCount());
        assertEquals(new BigDecimal("1.00"), first.totalEvaluatedProbability());
        assertEquals(first, evaluator.evaluate(request));
        assertEquals(source, Stage4TestPlans.json(plan));
        assertEquals(deterministic, Stage4TestPlans.json(new ProjectionEngine().project(plan)));
    }
}
