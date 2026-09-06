package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sequentially evaluates every requested retirement-age pair through the full
 * deterministic plan while holding exact survivor elections constant.
 */
public final class IntegratedRetirementClaimingGridCalculator {

    private final IntegratedSocialSecurityStrategyEvaluator evaluator;

    public IntegratedRetirementClaimingGridCalculator() {
        this(new IntegratedSocialSecurityStrategyEvaluator());
    }

    IntegratedRetirementClaimingGridCalculator(
            IntegratedSocialSecurityStrategyEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public IntegratedRetirementClaimingGridResult calculate(RetirementPlan plan) {
        IntegratedRetirementClaimingGridSurvivorPolicy policy = survivorPolicy(plan);
        return calculate(IntegratedRetirementClaimingGridRequest.standard(plan, policy));
    }

    public IntegratedRetirementClaimingGridSurvivorPolicy survivorPolicy(
            RetirementPlan plan) {
        SocialSecurityHouseholdClaimingStrategy current =
                evaluator.extractCurrentStrategy(plan);
        return new IntegratedRetirementClaimingGridSurvivorPolicy(
                current.primarySurvivorElection(),
                current.spouseSurvivorElection(),
                "Current persisted shared survivor claiming age converted to each "
                        + "person's exact birthday at that age.");
    }

    public IntegratedRetirementClaimingGridResult calculate(
            IntegratedRetirementClaimingGridRequest request) {
        Objects.requireNonNull(request, "Integrated retirement grid request is required.");
        RetirementPlan plan = request.plan();
        IntegratedSocialSecurityStrategyResult baseline =
                evaluator.evaluateCurrentStrategy(plan);
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        List<IntegratedRetirementClaimingGridCell> cells = new ArrayList<>();

        for (int primaryAge : request.primaryRetirementAges()) {
            LocalDate primaryDate = SocialSecurityRetirementDateCalculator
                    .calculateRetirementClaimDate(primary.getBirthDate(), primaryAge);
            for (int spouseAge : request.spouseRetirementAges()) {
                LocalDate spouseDate = SocialSecurityRetirementDateCalculator
                        .calculateRetirementClaimDate(spouse.getBirthDate(), spouseAge);
                SocialSecurityHouseholdClaimingStrategy strategy =
                        new SocialSecurityHouseholdClaimingStrategy(
                                primaryAge,
                                spouseAge,
                                primaryDate,
                                spouseDate,
                                request.survivorPolicy().primaryElection(),
                                request.survivorPolicy().spouseElection());
                cells.add(evaluateCell(plan, strategy, baseline));
            }
        }
        return new IntegratedRetirementClaimingGridResult(
                baseline,
                request.primaryRetirementAges(),
                request.spouseRetirementAges(),
                request.survivorPolicy(),
                cells);
    }

    private IntegratedRetirementClaimingGridCell evaluateCell(
            RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy,
            IntegratedSocialSecurityStrategyResult baseline) {
        try {
            IntegratedSocialSecurityStrategyResult result = evaluator.evaluate(plan, strategy);
            return IntegratedRetirementClaimingGridCell.success(
                    strategy,
                    result,
                    ProjectionMetricsDifferenceCalculator.subtract(
                            result.metrics(), baseline.metrics()));
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Unable to evaluate the retirement claiming grid cell."
                    : exception.getMessage();
            IntegratedSocialSecurityStrategyEvaluationFailure.Category category =
                    exception instanceof IllegalArgumentException
                            ? IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION
                            : IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION;
            return IntegratedRetirementClaimingGridCell.failure(
                    strategy,
                    new IntegratedSocialSecurityStrategyEvaluationFailure(
                            strategy, message, category));
        }
    }
}
