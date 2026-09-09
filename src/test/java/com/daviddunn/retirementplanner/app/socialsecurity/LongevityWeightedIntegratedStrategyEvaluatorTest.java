package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class LongevityWeightedIntegratedStrategyEvaluatorTest {
    private final LongevityWeightedIntegratedStrategyEvaluator evaluator = new LongevityWeightedIntegratedStrategyEvaluator();

    @ParameterizedTest
    @CsvSource({"71,72", "74,69", "72,70", "75,73", "71,80", "80,82", "120,120", "70,68"})
    void mapsBothDeathOrdersSameYearsOpeningBoundaryAndExtendedTerminalCoverage(int primaryAge, int spouseAge)
            throws Exception {
        var plan = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(plan);
        var strategy = Stage4TestPlans.strategy(plan);
        var scenarios = prepared(plan, List.of(prob(primaryAge, "1")), List.of(prob(spouseAge, "1")));
        var result = evaluator.evaluate(request(plan, scenarios));
        assertEquals(1, result.originalScenarioCount());
        assertEquals(Math.max(1960 + primaryAge, 1962 + spouseAge) == 2030 ? 0 : 1,
                result.actualProjectionRunCount());
        assertEquals(BigDecimal.ONE, result.totalEvaluatedProbability());
        var outcome = result.scenarioOutcomes().getFirst();
        assertEquals(1960 + primaryAge, outcome.primaryDeathYear());
        assertEquals(1962 + spouseAge, outcome.spouseDeathYear());
        var death = LocalDate.of(Math.max(1960 + primaryAge, 1962 + spouseAge), 1, 1);
        assertEquals(death, outcome.secondDeathDate());
        var lifetime = new HouseholdLifetimeScenarioMapper().map(scenarios.scenarios().getFirst());
        var direct = new ProjectionEngine().project(plan,
                ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime).withEndingYear(death.getYear() - 1));
        assertEquals(Math.max(2034, death.getYear() - 1), direct.getEndYear());
        assertEquals(new EstateAtSecondDeathCalculator().calculate(plan, direct, death), outcome.estateSnapshot());
        assertEquals(before, Stage4TestPlans.json(plan));
        assertEquals(5, plan.getPlanningAssumptions().getProjectionLengthYears());
    }

    @Test
    void twoScenarioWeightsAndZeroMassPreserveProbabilityAndAggregateWithoutRounding() {
        var plan = Stage4TestPlans.plan();
        var scenarios = prepared(plan, List.of(prob(71, "0.25"), prob(74, "0.75"), prob(69, "0.00")),
                List.of(prob(70, "1.0")));
        var result = evaluator.evaluate(request(plan, scenarios));
        assertEquals(3, result.originalScenarioCount());
        assertEquals(2, result.actualProjectionRunCount());
        assertEquals(new BigDecimal("1.000"), result.totalEvaluatedProbability());
        var a = result.scenarioOutcomes().get(0);
        var b = result.scenarioOutcomes().get(1);
        assertEquals(new BigDecimal("0.250"), a.probability());
        assertEquals(new BigDecimal("0.750"), b.probability());
        assertEquals(a.estateSnapshot().nominalAfterTaxEstate().multiply(new BigDecimal("0.250"))
                .add(b.estateSnapshot().nominalAfterTaxEstate().multiply(new BigDecimal("0.750"))),
                result.expectedNominalEstateAtSecondDeath());
        assertEquals(a.pvEstate().multiply(new BigDecimal("0.250"))
                .add(b.pvEstate().multiply(new BigDecimal("0.750"))), result.expectedPvAfterTaxEstate());
        assertEquals(a.estateSnapshot().nominalAfterTaxEstate().min(b.estateSnapshot().nominalAfterTaxEstate()),
                result.minimumNominalScenarioEstate());
        assertEquals(a.estateSnapshot().nominalAfterTaxEstate().max(b.estateSnapshot().nominalAfterTaxEstate()),
                result.maximumNominalScenarioEstate());
        assertThrows(UnsupportedOperationException.class, () -> result.scenarioOutcomes().clear());
        assertFalse(result.financialLimitations().isEmpty());
    }

    @Test
    void twoScenarioHandCalculationWithNoIncomeTaxesOrSpending() {
        var fixture = Stage4TestPlans.plan();
        // Both people are below Medicare/RMD ages and retire after financial coverage.
        var primary = new Person("Primary", "Test", LocalDate.of(1970, 2, 28));
        var spouse = new Person("Spouse", "Test", LocalDate.of(1972, 6, 15));
        primary.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(
                "SS", AccountOwnership.PRIMARY, primary.getBirthDate().plusYears(67), null,
                new BigDecimal("3000"), 67, BigDecimal.ZERO, 2030));
        spouse.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(
                "SS", AccountOwnership.SPOUSE, spouse.getBirthDate().plusYears(67), null,
                new BigDecimal("1000"), 67, BigDecimal.ZERO, 2030));
        var plan = new RetirementPlan(new Household(primary, spouse), fixture.getAccountPortfolio(),
                new PlanningAssumptions(new EconomicAssumptions(new BigDecimal("0.03"), BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("0.09")),
                        fixture.getPlanningAssumptions().getTaxAssumptions(),
                        fixture.getPlanningAssumptions().getWithdrawalAssumptions(),
                        fixture.getPlanningAssumptions().getDeathScenarioAssumptions(), 5, LocalDate.of(2030, 1, 1)));
        var scenarios = prepared(plan, List.of(prob(60, "0.25"), prob(61, "0.75")), List.of(prob(58, "1")));
        var result = evaluator.evaluate(new LongevityWeightedIntegratedStrategyRequest(plan,
                Stage4TestPlans.strategy(plan), scenarios, LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        // Opening: 800,000 - 25% * 500,000 = 675,000.
        // Next January: 3% growth -> 824,000 - 25% * 515,000 = 695,250.
        assertEquals(0, new BigDecimal("675000").compareTo(result.minimumNominalScenarioEstate()));
        assertEquals(0, new BigDecimal("695250").compareTo(result.maximumNominalScenarioEstate()));
        assertEquals(0, new BigDecimal("690187.50").compareTo(result.expectedNominalEstateAtSecondDeath()));
        assertEquals(0, new BigDecimal("690187.50").compareTo(result.expectedPvAfterTaxEstate()));
    }

    @Test
    void valuationDateIsIndependentOfConditioningAndUsesGeneralInflationNotCola() {
        var plan = Stage4TestPlans.plan();
        var scenarios = prepared(plan, List.of(prob(72, "1")), List.of(prob(70, "1")));
        LocalDate valuation = LocalDate.of(2028, 2, 29);
        var result = evaluator.evaluate(new LongevityWeightedIntegratedStrategyRequest(plan,
                Stage4TestPlans.strategy(plan), scenarios, valuation, new BigDecimal("0.03")));
        var outcome = result.scenarioOutcomes().getFirst();
        assertEquals(valuation, result.valuationDate());
        assertNotEquals(valuation, scenarios.assumptions().mortalityBaseDate());
        assertEquals(new BigDecimal("0.02"), result.generalInflationRate());
        assertEquals(0, outcome.pvEstate().compareTo(outcome.estateSnapshot().nominalAfterTaxEstate()
                .multiply(new EstatePresentValueCalculator().discountFactor(valuation, outcome.secondDeathDate(),
                        new BigDecimal("0.02"), new BigDecimal("0.03")))));
        assertNotEquals(outcome.pvEstate(), outcome.estateSnapshot().nominalAfterTaxEstate().multiply(
                new EstatePresentValueCalculator().discountFactor(valuation, outcome.secondDeathDate(),
                        new BigDecimal("0.01"), new BigDecimal("0.03"))));
    }

    @Test
    void positiveScenarioFailureAbortsEntireEvaluationAndLeavesSourceUntouched() throws Exception {
        var plan = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(plan);
        var progress = new ArrayList<AnalysisProgress>();
        var scenarios = prepared(plan, List.of(prob(71, "0.5"), prob(69, "0.5")), List.of(prob(67, "1")));
        var failure = assertThrows(IllegalStateException.class, () -> evaluator.evaluate(
                new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), scenarios,
                        LocalDate.of(2030, 1, 1), BigDecimal.ZERO, progress::add, AnalysisCancellationToken.none())));
        assertTrue(failure.getMessage().contains("No completed expected value"));
        assertTrue(failure.getCause().getMessage().contains("opening balances"));
        assertEquals(1, progress.getLast().completedWork());
        assertEquals(before, Stage4TestPlans.json(plan));
    }

    @Test
    void cancellationAtScenarioBoundaryAndAfterFinalProgressNeverReturnsCompleteValue() throws Exception {
        var plan = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(plan);
        var scenarios = prepared(plan, List.of(prob(71, "0.5"), prob(72, "0.5")), List.of(prob(72, "1")));
        for (int stopAt : List.of(0, 1, 2)) {
            var cancelled = new AtomicBoolean();
            assertThrows(AnalysisCancelledException.class, () -> evaluator.evaluate(
                    new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), scenarios,
                            LocalDate.of(2030, 1, 1), BigDecimal.ZERO,
                            progress -> cancelled.set(progress.completedWork() == stopAt), cancelled::get)));
        }
        assertEquals(before, Stage4TestPlans.json(plan));
    }

    @Test
    void weightedRunsUseLifetimeSurvivorMechanicsAndPreserveDeterministicResults() throws Exception {
        var plan = Stage4TestPlans.plan();
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("10000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT,
                RothConversionFrequency.ANNUAL, null));
        var engine = new ProjectionEngine();
        var deterministic = Stage4TestPlans.json(engine.project(plan));
        var source = Stage4TestPlans.json(plan);
        var scenarios = prepared(plan, List.of(prob(71, "1")), List.of(prob(75, "1")));
        var strategy = Stage4TestPlans.strategy(plan);
        var direct = engine.project(plan, ProjectionEvaluationContext.withSocialSecurityStrategy(strategy,
                new HouseholdLifetimeScenarioMapper().map(scenarios.scenarios().getFirst())).withEndingYear(2036));
        var survivor = direct.getYearAt(5);
        assertEquals(0, survivor.getRequiredMinimumDistribution().signum());
        assertEquals(0, survivor.getRothConversion().signum());
        assertTrue(survivor.getGuaranteedIncome().compareTo(survivor.getSocialSecurityResult().householdBenefit()) > 0);
        var result = evaluator.evaluate(request(plan, scenarios));
        assertEquals(direct.getLastYear().getAfterTaxEstateValue(), result.scenarioOutcomes().getFirst()
                .estateSnapshot().nominalAfterTaxEstate());
        assertEquals(deterministic, Stage4TestPlans.json(engine.project(plan)));
        assertEquals(source, Stage4TestPlans.json(plan));
    }

    @Test
    void rejectsMortalityForDifferentHouseholdAndInconsistentStrategy() {
        var plan = Stage4TestPlans.plan();
        var scenarios = PreparedLongevityTestSupport.create(LocalDate.of(1961, 2, 28),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(prob(72, "1")), List.of(prob(72, "1")));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(request(plan, scenarios)));
        var valid = Stage4TestPlans.strategy(plan);
        var invalid = new SocialSecurityHouseholdClaimingStrategy(62, valid.spouseRetirementAge(),
                valid.primaryRetirementClaimDate(), valid.spouseRetirementClaimDate(),
                valid.primarySurvivorElection(), valid.spouseSurvivorElection());
        var matching = prepared(plan, List.of(prob(72, "1")), List.of(prob(72, "1")));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(new LongevityWeightedIntegratedStrategyRequest(
                plan, invalid, matching, LocalDate.of(2030, 1, 1), BigDecimal.ZERO)));
    }

    static HouseholdLongevityScenarios prepared(RetirementPlan plan, List<SocialSecurityMortalityProbability> primary,
            List<SocialSecurityMortalityProbability> spouse) {
        return PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1), primary, spouse);
    }

    static SocialSecurityMortalityProbability prob(int age, String probability) {
        return new SocialSecurityMortalityProbability(age, new BigDecimal(probability));
    }

    private static LongevityWeightedIntegratedStrategyRequest request(RetirementPlan plan, HouseholdLongevityScenarios scenarios) {
        return new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), scenarios,
                LocalDate.of(2030, 1, 1), new BigDecimal("0.03"));
    }
}
