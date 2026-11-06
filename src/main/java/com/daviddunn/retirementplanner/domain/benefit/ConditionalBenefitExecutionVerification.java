package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import java.util.List;

/** New execution acceptance only. Never invoked during historical restoration or reporting. */
public final class ConditionalBenefitExecutionVerification {
    private ConditionalBenefitExecutionVerification() { }

    public static void verify(RetirementPlan frozen, ProjectionEvaluationContext context, List<ProjectionYear> completed) {
        if (frozen.getConditionalBenefits().isEmpty()) return;
        var deaths = EffectiveHouseholdDeathView.resolve(frozen.getHousehold(),
                frozen.getPlanningAssumptions().getDeathScenarioAssumptions(), context.householdLifetimeScenario());
        var evaluator = new ConditionalBenefitEvaluator();
        for (var year : completed) {
            var expected = evaluator.evaluate(frozen.getConditionalBenefits(), BenefitEvaluationContext.capture(
                    frozen.getHousehold(), deaths, frozen.getPlanningAssumptions().getProjectionStartDate(), year.getCalendarYear()));
            var actual = year.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).toList();
            if (!expected.receipts().equals(actual))
                throw new IllegalArgumentException("Analyzer projection did not execute every applicable conditional benefit under its scenario deaths.");
        }
    }
}
