package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicDeathCharacterizationTest {
    @ParameterizedTest
    @EnumSource(DeathScenario.class)
    void ordinaryAndExistingEmptyContextsPreserveCompleteResults(DeathScenario scenario) throws Exception {
        var plan = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(
                scenario, scenario == DeathScenario.BOTH_SURVIVE ? null : 2031, 67,
                new BigDecimal("0.75")));
        String before = LifetimeProjectionTestSupport.json(plan);
        var engine = new ProjectionEngine();
        var ordinary = engine.project(plan);
        assertEquals(LifetimeProjectionTestSupport.json(ordinary),
                LifetimeProjectionTestSupport.json(engine.project(plan, ProjectionEvaluationContext.empty())));
        assertEquals(LifetimeProjectionTestSupport.json(ordinary),
                LifetimeProjectionTestSupport.json(engine.project(plan,
                        new ProjectionEvaluationContext(Optional.empty()))));
        assertEquals(before, LifetimeProjectionTestSupport.json(plan));
        assertEquals(5, ordinary.size());
        assertEquals(0, ordinary.getYearAt(0).getAnnualExpenses().compareTo(new BigDecimal("20000")));
        assertEquals(0, ordinary.getYearAt(1).getAnnualExpenses().compareTo(
                new BigDecimal(scenario == DeathScenario.BOTH_SURVIVE ? "40000" : "30000")));
    }
}
