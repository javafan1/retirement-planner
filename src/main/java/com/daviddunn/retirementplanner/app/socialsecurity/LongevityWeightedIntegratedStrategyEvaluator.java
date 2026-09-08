package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Sequential, isolated full-plan evaluation of one strategy, with no search or ranking. */
public final class LongevityWeightedIntegratedStrategyEvaluator {
    private final RetirementPlanScenarioCopyService copyService = new RetirementPlanScenarioCopyService();
    private final HouseholdLifetimeScenarioMapper mapper = new HouseholdLifetimeScenarioMapper();
    private final EstateAtSecondDeathCalculator estateCalculator = new EstateAtSecondDeathCalculator();
    private final EstatePresentValueCalculator pvCalculator = new EstatePresentValueCalculator();

    public LongevityWeightedIntegratedStrategyResult evaluate(LongevityWeightedIntegratedStrategyRequest request) {
        return evaluate(request, event -> { });
    }

    LongevityWeightedIntegratedStrategyResult evaluate(LongevityWeightedIntegratedStrategyRequest request,
            java.util.function.Consumer<LongevityWeightedEvaluationWork> workObserver) {
        Objects.requireNonNull(request, "Evaluation request is required.");
        Objects.requireNonNull(workObserver, "Work observer is required.");
        request.cancellationToken().throwIfCancellationRequested();
        var baseline = copyService.copy(request.sourcePlan());
        IntegratedSocialSecurityStrategyEvaluator.requireAdvancedPlan(baseline);
        IntegratedSocialSecurityStrategyEvaluator.validateStrategy(baseline, request.strategy());
        var prepared = request.longevityScenarios();
        if (!baseline.getHousehold().getPrimaryPerson().getBirthDate().equals(
                prepared.primary().request().dateOfBirth())
                || !baseline.getHousehold().getSpouse().getBirthDate().equals(
                prepared.spouse().request().dateOfBirth())) {
            throw new IllegalArgumentException("Prepared mortality dates of birth must match the plan household.");
        }
        var inflation = baseline.getPlanningAssumptions().getEconomicAssumptions().getGeneralInflationRate();
        EstatePresentValueCalculator.validateRate(inflation);
        var scenarios = prepared.scenarios();
        int total = (int) scenarios.stream().filter(s -> s.jointProbability().signum() > 0).count();
        if (total == 0 || scenarios.stream().anyMatch(s -> s.jointProbability().signum() < 0)) {
            throw new IllegalArgumentException("Mortality scenarios require positive total mass and nonnegative probabilities.");
        }
        var engine = new ProjectionEngine();
        Map<LocalDate, BigDecimal> discountFactors = new HashMap<>();
        List<LongevityWeightedIntegratedScenarioOutcome> outcomes = new ArrayList<>();
        BigDecimal probability = BigDecimal.ZERO;
        BigDecimal expectedNominal = BigDecimal.ZERO;
        BigDecimal expectedPv = BigDecimal.ZERO;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        request.progressListener().onProgress(new AnalysisProgress(
                AnalysisPhase.LONGEVITY_INTEGRATED_SCENARIOS, 0, total));
        for (var mortality : scenarios) {
            request.cancellationToken().throwIfCancellationRequested();
            if (mortality.jointProbability().signum() == 0) {
                continue;
            }
            workObserver.accept(LongevityWeightedEvaluationWork.SCENARIO_STARTED);
            var lifetime = mapper.map(mortality);
            int primaryYear = lifetime.primaryDeathYear().orElseThrow().getValue();
            int spouseYear = lifetime.spouseDeathYear().orElseThrow().getValue();
            LocalDate death = LocalDate.of(Math.max(primaryYear, spouseYear), 1, 1);
            EstateAtSecondDeathSnapshot snapshot;
            try {
                estateCalculator.validateCoverageStart(baseline, death);
                var isolated = copyService.copy(baseline);
                var context = ProjectionEvaluationContext.withSocialSecurityStrategy(request.strategy(), lifetime)
                        .withEndingYear(death.getYear() - 1);
                workObserver.accept(LongevityWeightedEvaluationWork.PROJECTION_STARTED);
                var projection = engine.project(isolated, context);
                workObserver.accept(LongevityWeightedEvaluationWork.PROJECTION_COMPLETED);
                snapshot = estateCalculator.calculate(isolated, projection, death);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Lifetime scenario failed: primary death year " + primaryYear
                        + ", spouse death year " + spouseYear + ", probability " + mortality.jointProbability()
                        + ". No completed expected value is available.", failure);
            }
            var factor = discountFactors.computeIfAbsent(death, date -> pvCalculator.discountFactor(
                    request.valuationDate(), date, inflation, request.realDiscountRate()));
            var pv = snapshot.nominalAfterTaxEstate().multiply(factor);
            var weight = mortality.jointProbability();
            probability = probability.add(weight);
            expectedNominal = expectedNominal.add(weight.multiply(snapshot.nominalAfterTaxEstate()));
            expectedPv = expectedPv.add(weight.multiply(pv));
            minimum = minimum == null ? snapshot.nominalAfterTaxEstate() : minimum.min(snapshot.nominalAfterTaxEstate());
            maximum = maximum == null ? snapshot.nominalAfterTaxEstate() : maximum.max(snapshot.nominalAfterTaxEstate());
            outcomes.add(new LongevityWeightedIntegratedScenarioOutcome(primaryYear, spouseYear, weight, snapshot, pv));
            workObserver.accept(LongevityWeightedEvaluationWork.SCENARIO_COMPLETED);
            request.progressListener().onProgress(new AnalysisProgress(
                    AnalysisPhase.LONGEVITY_INTEGRATED_SCENARIOS, outcomes.size(), total));
        }
        request.cancellationToken().throwIfCancellationRequested();
        return new LongevityWeightedIntegratedStrategyResult(request.strategy(), expectedPv, expectedNominal,
                probability, scenarios.size(), outcomes.size(), minimum, maximum, request.valuationDate(),
                inflation, request.realDiscountRate(), prepared.assumptions(),
                "January 1 second death uses prior December 31 ending investable estate, or the matching "
                        + "January 1 opening snapshot. Coverage extends through second-death year minus one. "
                        + "Nominal estate is deflated by general inflation and discounted at the real rate "
                        + "using actual days / 365.25 from the analyzer PV base date. Original probabilities are not renormalized.",
                List.of("Deceased-owner accounts remain invested household assets available for spending and tax funding, "
                                + "with unchanged tax classification and estate inclusion; no owner RMD or Roth conversion.",
                        "No inheritance, retitling, inherited-account distribution schedule, beneficiary rules or estate settlement. "
                                + "Omitted distributions may materially affect expected estate.",
                        "Heir tax is the existing estimated tax-deferred haircut, not a legal estate settlement. "
                                + "Non-investable assets are excluded. Annual engine financial precision is retained.",
                        "Independent mortality and January 1 annual deaths are modeling assumptions; "
                                + "living owners beyond the supported RMD-table age remain unsupported."), outcomes);
    }
}
