package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloFoundationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void constantPathMatchesEverySerializedAnnualValueAndDoesNotMutatePlan() throws Exception {
        var plan = MonteCarloFixtures.household();
        String before = MAPPER.writeValueAsString(plan);
        var engine = new ProjectionEngine();
        var normal = engine.project(plan);
        var map = new TreeMap<Integer, BigDecimal>();
        for (int year = 2027; year <= 2056; year++) {
            map.put(year, new BigDecimal("0.045"));
        }
        var supplied = engine.project(plan, ProjectionEvaluationContext.empty(), ProjectionEconomicPath.annual(map));
        assertEquals(MAPPER.valueToTree(normal.getYears()), MAPPER.valueToTree(supplied.getYears()));
        assertEquals(before, MAPPER.writeValueAsString(plan));
        assertTrue(normal.getYears().stream().anyMatch(y -> y.getRothConversion().signum() > 0));
        assertTrue(normal.getYears().stream().anyMatch(y -> y.getRequiredMinimumDistribution().signum() > 0));
        assertTrue(normal.getYears().stream().anyMatch(y -> y.getFederalIncomeTax().signum() > 0));
        assertTrue(normal.getYears().stream().anyMatch(y -> y.getMichiganIncomeTax().signum() > 0));
        assertTrue(normal.getYears().stream().anyMatch(y -> y.getSocialSecurityResult().householdBenefit().signum() > 0));
    }

    @Test
    void constantPathsPreserveBothSurvivorDirectionsAndContextHorizon() {
        for (var death : List.of(DeathScenario.PRIMARY_DIES, DeathScenario.SPOUSE_DIES)) {
            var plan = MonteCarloFixtures.household();
            var old = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                    old.getWithdrawalAssumptions(), new DeathScenarioAssumptions(death, 2030, 67, BigDecimal.ONE),
                    null, null, old.getProjectionLengthYears(), old.getProjectionStartDate()));
            var engine = new ProjectionEngine();
            var context = ProjectionEvaluationContext.empty().withExactEndingYear(2040);
            var normal = engine.project(plan, context);
            var path = ProjectionEconomicPath.constant(old.getExpectedAnnualInvestmentReturn());
            assertEquals(MAPPER.valueToTree(normal.getYears()), MAPPER.valueToTree(engine.project(plan, context, path).getYears()));
            assertEquals(2040, normal.getEndYear());
        }
    }

    @Test
    void varyingReturnsUseOpeningAssetsAndRetainPartialYearAndAllCashFlowOrdering() {
        var plan = MonteCarloFixtures.household();
        plan.setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.045"), new BigDecimal("0.02"), 4, LocalDate.of(2037, 7, 1)));
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Additional spending requiring tax funding", new BigDecimal("200000")));
        var rates = List.of(new BigDecimal("0.10"), new BigDecimal("-0.10"), new BigDecimal("0.05"), BigDecimal.ZERO);
        var path = new TreeMap<Integer, BigDecimal>();
        for (int i = 0; i < 4; i++) {
            path.put(2037 + i, rates.get(i));
        }
        var projection = new ProjectionEngine().project(plan, ProjectionEvaluationContext.empty(), ProjectionEconomicPath.annual(path));
        for (int i = 0; i < 4; i++) {
            var year = projection.getYearAt(i);
            BigDecimal expected = year.getBeginningInvestableAssets().multiply(rates.get(i));
            if (i == 0) {
                expected = expected.multiply(BigDecimal.valueOf(6)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            }
            assertEquals(0, expected.setScale(2, RoundingMode.HALF_UP).compareTo(year.getInvestmentGrowth()));
            if (i > 0) {
                assertTrue(projection.getYearAt(i - 1).getEndingInvestableAssets().subtract(year.getBeginningInvestableAssets()).abs().compareTo(new BigDecimal("0.02")) <= 0);
            }
        }
        assertTrue(projection.getYears().stream().anyMatch(y -> y.getRothConversion().signum() > 0));
        assertTrue(projection.getYears().stream().anyMatch(y -> y.getRequiredMinimumDistribution().signum() > 0));
        assertTrue(projection.getYears().stream().anyMatch(y -> y.getTaxFundingWithdrawal().signum() > 0));
    }

    @Test
    void seededPathsAndAggregatesRepeatAndZeroVolatilityCollapsesToReference() throws Exception {
        var plan = MonteCarloFixtures.household();
        var engine = new CountingEngine();
        var analyzer = new MonteCarloAnalyzer(engine);
        var reference = engine.project(plan);
        engine.runs = 0;
        String before = MAPPER.writeValueAsString(plan);
        var settings = MonteCarloSettings.forPlan(plan, 8, 417, new BigDecimal("0.12"));
        var first = analyzer.analyze(plan, settings, reference);
        var second = analyzer.analyze(plan, settings, reference);
        assertEquals(first, second);
        assertEquals(16, engine.runs);
        assertEquals(8, first.outcomes().size());
        assertEquals(8, first.completedCount() + first.fundingFailureCount());
        assertEquals(before, MAPPER.writeValueAsString(plan));
        var zero = analyzer.analyze(plan, MonteCarloSettings.forPlan(plan, 5, 1, BigDecimal.ZERO), reference);
        assertEquals(5, zero.completedCount());
        for (var year : reference.getYears()) {
            var quantiles = zero.annualInvestableAssets().get(year.getCalendarYear()).orElseThrow();
            assertEquals(5, quantiles.sampleCount());
            for (var value : List.of(quantiles.minimum(), quantiles.p10(), quantiles.p25(), quantiles.p50(), quantiles.p75(), quantiles.p90(), quantiles.maximum())) {
                assertEquals(0, year.getEndingInvestableAssets().compareTo(value));
            }
        }
        assertEquals(0, zero.endingNetWorth().orElseThrow().p50().compareTo(zero.deterministicReference().metrics().orElseThrow().endingNetWorth()));
        assertEquals(0, zero.endingAfterTaxEstate().orElseThrow().p50().compareTo(zero.deterministicReference().metrics().orElseThrow().afterTaxEstate()));
    }

    @Test
    void failuresAreRetainedAndNeverInventBalancesOrSuccess() {
        var plan = MonteCarloFixtures.household();
        var settings = MonteCarloSettings.forPlan(plan, 3, 1, BigDecimal.ZERO);
        var result = new MonteCarloAnalyzer().analyze(plan, settings, null, index -> {
            if (index == 1) {
                return ProjectionEconomicPath.constant(new BigDecimal("-1"));
            }
            return ProjectionEconomicPath.constant(new BigDecimal("0.045"));
        });
        assertEquals(3, result.outcomes().size());
        assertEquals(2, result.completedCount());
        assertEquals(1, result.fundingFailureCount());
        assertTrue(result.outcomes().get(1).fundingFailure().isPresent());
        assertTrue(result.outcomes().get(1).firstNonpositiveYearEnd().isEmpty());
        assertEquals(2, result.endingInvestableAssets().orElseThrow().sampleCount());
        assertTrue(result.annualInvestableAssets().values().stream().allMatch(p -> p.orElseThrow().sampleCount() == 2));
        var allFailed = new MonteCarloAnalyzer().analyze(plan, settings, null, index -> ProjectionEconomicPath.constant(new BigDecimal("-1")));
        assertEquals(3, allFailed.fundingFailureCount());
        assertTrue(allFailed.endingInvestableAssets().isEmpty());
        assertTrue(allFailed.probabilityPositiveEveryYearAmongCompleted().isEmpty());
    }

    @Test
    void temporaryZeroIsAnObservationNotPermanentFailureAndEmptyReferenceIsRejected() {
        var plan = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createEmptyPlan();
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1985, 1, 1));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1985, 1, 1));
        plan.setPlanningAssumptions(new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 2, LocalDate.of(2027, 1, 1)));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension("Later income", AccountOwnership.PRIMARY, LocalDate.of(2028, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO));
        var result = new MonteCarloAnalyzer().analyze(plan, MonteCarloSettings.forPlan(plan, 2, 1, BigDecimal.ZERO));
        assertEquals(2, result.completedCount());
        assertEquals(2, result.observedNonpositiveCount());
        assertEquals(2027, result.outcomes().getFirst().firstNonpositiveYearEnd().orElseThrow());
        assertTrue(result.endingInvestableAssets().orElseThrow().minimum().signum() > 0);
        assertEquals(0, result.probabilityPositiveEveryYearAmongCompleted().orElseThrow().signum());
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyze(plan, MonteCarloSettings.forPlan(plan, 1, 1, BigDecimal.ZERO), new Projection()));
    }

    @Test
    void quantilesAndPathValidation() {
        var q = MonteCarloPercentiles.of(List.of(new BigDecimal("100"), BigDecimal.ZERO)).orElseThrow();
        assertEquals(0, q.p10().compareTo(new BigDecimal("10")));
        assertEquals(0, q.p25().compareTo(new BigDecimal("25")));
        assertEquals(0, q.p50().compareTo(new BigDecimal("50")));
        assertEquals(0, q.p75().compareTo(new BigDecimal("75")));
        assertEquals(0, q.p90().compareTo(new BigDecimal("90")));
        assertEquals(0, q.minimum().signum());
        assertEquals(new BigDecimal("100"), q.maximum());
        assertTrue(MonteCarloPercentiles.of(List.of()).isEmpty());
        assertEquals(new BigDecimal("0.001"), MonteCarloPercentiles.of(List.of(new BigDecimal("0.001"))).orElseThrow().p50());
        assertEquals(0, MonteCarloPercentiles.of(List.of(new BigDecimal("-2"), BigDecimal.ZERO, new BigDecimal("2"))).orElseThrow().p50().signum());
        assertThrows(IllegalArgumentException.class, () -> ProjectionEconomicPath.annual(Map.of(2027, new BigDecimal("-1.001"))));
        var map = new HashMap<Integer, BigDecimal>();
        map.put(2027, BigDecimal.ZERO);
        var path = ProjectionEconomicPath.annual(map);
        map.clear();
        assertEquals(BigDecimal.ZERO, path.investmentReturnForYear(2027));
        assertThrows(IllegalArgumentException.class, () -> path.requireCoverage(2027, 2028));
        assertThrows(UnsupportedOperationException.class, () -> path.annualReturns().clear());
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloSettings(0, 1, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloSettings(1, 1, BigDecimal.ZERO, new BigDecimal("-0.1")));
    }

    @Test
    void returnMomentsBoundariesAndStreamsAreDeterministic() {
        var settings = new MonteCarloSettings(1, 42, new BigDecimal("0.045"), new BigDecimal("0.12"));
        var generator = new MonteCarloScenarioGenerator();
        var a = generator.generate(2027, 2056, settings, 417).annualReturns();
        assertEquals(a, generator.generate(2027, 2056, settings, 417).annualReturns());
        assertNotEquals(a, generator.generate(2027, 2056, settings, 418).annualReturns());
        assertNotEquals(a, generator.generate(2027, 2056, new MonteCarloSettings(1, 43, settings.expectedReturn(), settings.returnVolatility()), 417).annualReturns());
        assertEquals(a, generator.generate(2027, 2056, new MonteCarloSettings(1000, 42, settings.expectedReturn(), settings.returnVolatility()), 417).annualReturns());
        var sample = generator.generate(0, 99999, settings, 0).annualReturns().values();
        double mean = sample.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
        double sd = StrictMath.sqrt(sample.stream().mapToDouble(v -> StrictMath.pow(v.doubleValue() - mean, 2)).average().orElseThrow());
        assertEquals(0.045, mean, 0.002);
        assertEquals(0.12, sd, 0.002);
        assertTrue(sample.stream().allMatch(v -> v.compareTo(BigDecimal.ONE.negate()) >= 0));
        var zero = new MonteCarloSettings(1, 42, new BigDecimal("0.045"), BigDecimal.ZERO);
        assertEquals(zero.expectedReturn(), generator.generate(2027, 2027, zero, () -> {
            throw new AssertionError("Zero volatility must not draw");
        }).investmentReturnForYear(2027));
        assertEquals(new BigDecimal("-1.0"), generator.generate(2027, 2027, settings, () -> -10000).investmentReturnForYear(2027));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(2027, 2027, settings, () -> Double.NaN));
    }

    private static final class CountingEngine extends ProjectionEngine {
        int runs;

        @Override
        public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionEconomicPath path) {
            runs++;
            return super.projectWithOutcome(plan, context, path);
        }
    }
}