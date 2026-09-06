package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.SocialSecurityProjectionIncomeProvider;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates one supplied Social Security strategy through an isolated complete
 * retirement plan. This service performs no search, ranking, or mortality weighting.
 */
public final class IntegratedSocialSecurityStrategyEvaluator {

    private final RetirementPlanScenarioCopyService copyService;
    private final ProjectionMetricsCalculator metricsCalculator;

    public IntegratedSocialSecurityStrategyEvaluator() {
        this(new RetirementPlanScenarioCopyService(), new ProjectionMetricsCalculator());
    }

    IntegratedSocialSecurityStrategyEvaluator(
            RetirementPlanScenarioCopyService copyService,
            ProjectionMetricsCalculator metricsCalculator) {
        this.copyService = Objects.requireNonNull(copyService);
        this.metricsCalculator = Objects.requireNonNull(metricsCalculator);
    }

    public IntegratedSocialSecurityStrategyResult evaluate(
            RetirementPlan sourcePlan,
            SocialSecurityHouseholdClaimingStrategy strategy) {
        Objects.requireNonNull(sourcePlan, "Retirement plan is required.");
        Objects.requireNonNull(strategy, "Social Security strategy is required.");
        requireAdvancedPlan(sourcePlan);
        validateStrategy(sourcePlan, strategy);

        RetirementPlan scenario = copyService.copy(sourcePlan);
        return project(scenario, strategy, List.of(),
                ProjectionEvaluationContext.withSocialSecurityStrategy(strategy));
    }

    public IntegratedSocialSecurityStrategyResult evaluateCurrentStrategy(
            RetirementPlan sourcePlan) {
        Objects.requireNonNull(sourcePlan, "Retirement plan is required.");
        requireAdvancedPlan(sourcePlan);
        SocialSecurityHouseholdClaimingStrategy current = extractCurrentStrategy(sourcePlan);
        RetirementPlan scenario = copyService.copy(sourcePlan);
        return project(scenario, current, List.of(
                "Current plan survivor behavior was evaluated using its configured "
                        + "deterministic survivor policy."),
                ProjectionEvaluationContext.empty());
    }

    public SocialSecurityHouseholdClaimingStrategy extractCurrentStrategy(
            RetirementPlan plan) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        requireAdvancedPlan(plan);
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        SocialSecurityIncome primarySource = source(primary, AccountOwnership.PRIMARY);
        SocialSecurityIncome spouseSource = source(spouse, AccountOwnership.SPOUSE);
        Integer survivorAge = plan.getPlanningAssumptions()
                .getDeathScenarioAssumptions().getSurvivorClaimingAge();
        int representedSurvivorAge = survivorAge != null ? survivorAge : 60;
        return new SocialSecurityHouseholdClaimingStrategy(
                primarySource.getClaimingAge(),
                spouseSource.getClaimingAge(),
                primarySource.getStartDate(),
                spouseSource.getStartDate(),
                survivorCandidate(primary, representedSurvivorAge),
                survivorCandidate(spouse, representedSurvivorAge));
    }

    private IntegratedSocialSecurityStrategyResult project(
            RetirementPlan scenario,
            SocialSecurityHouseholdClaimingStrategy strategy,
            List<String> warnings,
            ProjectionEvaluationContext evaluationContext) {
        boolean advanced = new SocialSecurityProjectionIncomeProvider()
                .supportsAdvancedPath(scenario);
        Projection projection = new ProjectionEngine().project(
                scenario, evaluationContext);
        ProjectionMetrics metrics = metricsCalculator.calculate(scenario, projection);
        return new IntegratedSocialSecurityStrategyResult(
                strategy, projection, metrics, advanced, warnings);
    }

    private static void requireAdvancedPlan(RetirementPlan plan) {
        if (!new SocialSecurityProjectionIncomeProvider().supportsAdvancedPath(plan)) {
            throw new IllegalArgumentException(
                    "Integrated Social Security strategy evaluation requires two modern-cohort "
                            + "people with exactly one correctly owned Social Security source each; "
                            + "the advanced path is unavailable for this plan.");
        }
    }

    private static void validateStrategy(
            RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy) {
        validateOwnerStrategy(plan.getHousehold().getPrimaryPerson(),
                strategy.primaryRetirementAge(), strategy.primaryRetirementClaimDate(),
                "Primary");
        validateOwnerStrategy(plan.getHousehold().getSpouse(),
                strategy.spouseRetirementAge(), strategy.spouseRetirementClaimDate(),
                "Spouse");
    }

    private static void validateOwnerStrategy(
            Person person,
            int age,
            LocalDate claimDate,
            String owner) {
        if (age < 62 || age > 70) {
            throw new IllegalArgumentException(
                    owner + " retirement claim age must be between 62 and 70.");
        }
        LocalDate expected = SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(person.getBirthDate(), age);
        if (!expected.equals(claimDate)) {
            throw new IllegalArgumentException(
                    owner + " retirement claim date " + claimDate
                            + " does not match age " + age + " for DOB "
                            + person.getBirthDate() + ".");
        }
    }

    private static SocialSecurityIncome source(
            Person person,
            AccountOwnership ownership) {
        List<SocialSecurityIncome> sources = person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .filter(value -> value.getOwnership() == ownership)
                .toList();
        if (sources.size() != 1) {
            throw new IllegalArgumentException(
                    ownership + " must have exactly one Social Security source.");
        }
        return sources.getFirst();
    }

    private static SocialSecuritySurvivorClaimingCandidate survivorCandidate(
            Person person,
            int age) {
        LocalDate date = person.getBirthDate().plusYears(age);
        return new SocialSecuritySurvivorClaimingCandidate(
                date, age, 0, "Age " + age + " (current plan policy)");
    }
}
