package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class LongevityIntegratedCharacterizationTest {
    @Test
    void configuredAndStrategyOnlyConstructorsPreserveHorizonAndFinancialResults() throws Exception {
        var plan = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(plan);
        var engine = new ProjectionEngine();
        var metrics = new ProjectionMetricsCalculator();
        var ordinary = engine.project(plan);
        assertEquals(5, ordinary.size());
        assertEquals(2034, ordinary.getEndYear());
        assertEquals(metrics.calculate(plan, ordinary), metrics.calculate(plan,
                engine.project(plan, new ProjectionEvaluationContext(Optional.empty(), Optional.empty()))));
        var strategy = Stage4TestPlans.strategy(plan);
        var explicit = engine.project(plan, new ProjectionEvaluationContext(Optional.of(strategy)));
        assertEquals(5, explicit.size());
        assertEquals(metrics.calculate(plan, explicit),
                new IntegratedSocialSecurityStrategyEvaluator().evaluate(plan, strategy).metrics());
        assertEquals(before, Stage4TestPlans.json(plan));
    }
}
