package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonTest.*;

class MonteCarloPairedAggregationTest {
    static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), actual.toPlainString());
    }

    static MonteCarloStrategyOutcome completed(String value, int last) {
        var amount = new BigDecimal(value);
        var annual = new TreeMap<Integer, BigDecimal>();
        for (int year = 2027; year <= last; year++) {
            annual.put(year, amount);
        }
        return new MonteCarloStrategyOutcome(annual, Optional.of(
                new MonteCarloMortalityAnalysisResult.TerminalOutcome(
                        last < 2027 ? LocalDate.of(2027, 1, 1) : LocalDate.of(last, 12, 31),
                        amount, amount, amount, amount)), Optional.empty());
    }

    static MonteCarloStrategyOutcome failed(int year) {
        var annual = new TreeMap<Integer, BigDecimal>();
        for (int y = 2027; y < year; y++) {
            annual.put(y, BigDecimal.TEN);
        }
        return new MonteCarloStrategyOutcome(annual, Optional.empty(), Optional.of(new FundingFailure(
                year, year - 2027, Optional.of(60), Optional.of(58), FundingFailure.Stage.IRA_RMD,
                Optional.of(com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY),
                BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("9"))));
    }

    static MonteCarloPairedOutcome pair(int index, MonteCarloStrategyOutcome a, MonteCarloStrategyOutcome b) {
        return new MonteCarloPairedOutcome(index, HouseholdLifetimeScenario.bothSurvive(), a, b);
    }

    static MonteCarloStrategyComparisonResult result(List<MonteCarloPairedOutcome> pairs, int last) {
        var plan = MonteCarloFundingTest.plan("10000", "10", 3);
        return new MonteCarloStrategyComparisonResult(fixed(plan, plan, pairs.size(), last),
                MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED, pairs);
    }

    static List<MonteCarloPairedMetricSummary> metrics(MonteCarloStrategyComparisonSummary summary) {
        return List.of(summary.terminalInvestableAssetsDifference(), summary.terminalNetWorthDifference(),
                summary.terminalAfterTaxEstateDifference(), summary.lifetimeTaxesDifference());
    }

    static void zero(MonteCarloPairedMetricSummary metric) {
        assertEquals(0, metric.greaterCount());
        assertEquals(0, metric.lessCount());
        assertEquals(metric.sampleCount(), metric.equalCount());
        if (metric.sampleCount() == 0) {
            assertTrue(metric.differencePercentiles().isEmpty());
            assertTrue(metric.meanDifference().isEmpty());
            assertTrue(metric.equalProbabilityAmongComparable().isEmpty());
        } else {
            var p = metric.differencePercentiles().orElseThrow();
            for (var value : List.of(p.minimum(), p.p10(), p.p25(), p.p50(), p.p75(), p.p90(), p.maximum(),
                    metric.meanDifference().orElseThrow())) {
                money("0", value);
            }
            money("1", metric.equalProbabilityAmongComparable().orElseThrow());
        }
    }

    @Test
    void percentilesAreOfPairedDifferencesNotDifferencesOfMarginalPercentiles() {
        // A=[0,100,101], B=[0,1,100]. Medians differ by 99; paired deltas=[0,99,1], median=1.
        var r = result(List.of(pair(0, completed("0", 2027), completed("0", 2027)),
                pair(1, completed("100", 2027), completed("1", 2027)),
                pair(2, completed("101", 2027), completed("100", 2027))), 2027);
        var p = r.summary().terminalInvestableAssetsDifference().differencePercentiles().orElseThrow();
        money("0", p.minimum());
        money("0.2", p.p10());
        money("0.5", p.p25());
        money("1", p.p50());
        money("50", p.p75());
        money("79.4", p.p90());
        money("99", p.maximum());
        var a = MonteCarloPercentiles.of(List.of(BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("101"))).orElseThrow();
        var b = MonteCarloPercentiles.of(List.of(BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("100"))).orElseThrow();
        money("99", a.p50().subtract(b.p50()));
        assertNotEquals(0, p.p50().compareTo(a.p50().subtract(b.p50())));
        assertEquals(new BigDecimal("100").divide(new BigDecimal("3"), java.math.MathContext.DECIMAL128),
                r.summary().terminalInvestableAssetsDifference().meanDifference().orElseThrow());
    }

    @Test
    void fourFundingStatesAndIndependentFailureStatisticsReconcile() {
        var ok = completed("100", 2029);
        var fail = failed(2028);
        var r = result(List.of(pair(0, ok, ok), pair(1, ok, fail), pair(2, fail, ok), pair(3, fail, fail)), 2029);
        var s = r.summary();
        assertEquals(new MonteCarloPairedStateSummary(4, 1, 1, 1, 1), s.pairedStates());
        var state = s.pairedStates();
        money("0.25", state.bothCompletedProbability());
        money("0.25", state.aCompletedBFailedProbability());
        money("0.25", state.aFailedBCompletedProbability());
        money("0.25", state.bothFailedProbability());
        money("0.5", state.aFundingProbability());
        money("0.5", state.bFundingProbability());
        assertEquals(0, state.fundingProbabilityDifference().compareTo(state.netAsymmetricFundingAdvantage()));
        for (var stats : List.of(s.strategyAFailureStatistics().orElseThrow(), s.strategyBFailureStatistics().orElseThrow())) {
            assertEquals(2, stats.count());
            assertEquals(Map.of(2028, 2L), stats.countByYear());
            money("9", stats.shortfallAmount().p50());
        }
        assertEquals(fail.fundingFailure(), r.outcomes().get(1).outcomeB().fundingFailure());
        assertEquals(4, s.annualResults().get(2027).comparableCount());
        var annual = s.annualResults().get(2028);
        assertEquals(1, annual.comparableCount());
        assertEquals(1, annual.aCompletedBFailedCount());
        assertEquals(1, annual.aFailedBCompletedCount());
        assertEquals(1, annual.bothFailedCount());
        assertEquals(2, annual.aAvailableCount());
        assertEquals(2, annual.bAvailableCount());
        metrics(s).forEach(MonteCarloPairedAggregationTest::zero);
    }

    @ParameterizedTest
    @EnumSource(MonteCarloPairedOutcome.Status.class)
    void homogeneousFundingPopulations(MonteCarloPairedOutcome.Status status) {
        var ok = completed("10", 2027);
        var fail = failed(2027);
        boolean a = status == MonteCarloPairedOutcome.Status.BOTH_COMPLETED || status == MonteCarloPairedOutcome.Status.A_COMPLETED_B_FAILED;
        boolean b = status == MonteCarloPairedOutcome.Status.BOTH_COMPLETED || status == MonteCarloPairedOutcome.Status.A_FAILED_B_COMPLETED;
        var s = result(List.of(pair(0, a ? ok : fail, b ? ok : fail)), 2027).summary();
        money(a ? "1" : "0", s.pairedStates().aFundingProbability());
        money(b ? "1" : "0", s.pairedStates().bFundingProbability());
        assertEquals(s.pairedStates().fundingProbabilityDifference(), s.pairedStates().netAsymmetricFundingAdvantage());
        metrics(s).forEach(MonteCarloPairedAggregationTest::zero);
        if (!(a && b)) {
            for (var metric : metrics(s)) {
                assertTrue(metric.greaterProbabilityAmongComparable().isEmpty());
                assertTrue(metric.lessProbabilityAmongComparable().isEmpty());
            }
        }
    }

    @Test
    void repeatingFractionsSumExactlyAndFundingDifferenceEqualsNetAsymmetry() {
        var example = new MonteCarloPairedStateSummary(1000, 970, 14, 9, 7);
        money("0.984", example.aFundingProbability());
        money("0.979", example.bFundingProbability());
        money("0.005", example.fundingProbabilityDifference());
        money("0.005", example.netAsymmetricFundingAdvantage());
        for (int a = 0; a < 7; a++) {
            for (int b = 0; b < 7; b++) {
                var state = new MonteCarloPairedStateSummary(7 + a + b, 4, a, b, 3);
                money("1", state.bothCompletedProbability().add(state.aCompletedBFailedProbability())
                        .add(state.aFailedBCompletedProbability()).add(state.bothFailedProbability()));
                assertEquals(0, state.fundingProbabilityDifference().compareTo(state.netAsymmetricFundingAdvantage()));
            }
        }
        var m = MonteCarloPairedMetricSummary.fromDifferences(List.of(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE.negate()));
        assertEquals(1, m.greaterCount());
        assertEquals(1, m.equalCount());
        assertEquals(1, m.lessCount());
        money("1", m.greaterProbabilityAmongComparable().orElseThrow().add(m.equalProbabilityAmongComparable().orElseThrow())
                .add(m.lessProbabilityAmongComparable().orElseThrow()));
    }

    @Test
    void reductionAndFinalCollectionsAreIsolatedFromMutableInputs() {
        var pairs = new ArrayList<>(List.of(pair(0, completed("10", 2027), completed("9", 2027))));
        var result = result(pairs, 2027);
        pairs.clear();
        assertEquals(1, result.outcomes().size());
        var summary = result.summary();
        var annual = new TreeMap<>(summary.annualResults());
        var copy = new MonteCarloStrategyComparisonSummary(summary.pairedStates(),
                summary.strategyAFailureStatistics(), summary.strategyBFailureStatistics(),
                summary.terminalInvestableAssetsDifference(), summary.terminalNetWorthDifference(),
                summary.terminalAfterTaxEstateDifference(), summary.lifetimeTaxesDifference(), annual);
        annual.clear();
        assertEquals(summary, copy);
        assertEquals(summary.hashCode(), copy.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> copy.annualResults().clear());
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloStrategyComparisonSummary(
                new MonteCarloPairedStateSummary(1, 0, 0, 0, 1), Optional.empty(), Optional.empty(),
                summary.terminalInvestableAssetsDifference(), summary.terminalNetWorthDifference(),
                summary.terminalAfterTaxEstateDifference(), summary.lifetimeTaxesDifference(), copy.annualResults()));
    }

    @Test
    void hugeValuesNegativeDifferencesAndUnroundedRelations() {
        var huge = new BigDecimal("1E1000");
        var tiny = new BigDecimal("1E-1000");
        var m = MonteCarloPairedMetricSummary.fromDifferences(List.of(huge, huge.negate(), tiny, tiny.negate(), BigDecimal.ZERO));
        assertEquals(2, m.greaterCount());
        assertEquals(2, m.lessCount());
        assertEquals(1, m.equalCount());
        money("0", m.meanDifference().orElseThrow());
        assertEquals(huge.negate(), m.differencePercentiles().orElseThrow().minimum());
        assertEquals(huge, m.differencePercentiles().orElseThrow().maximum());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void identicalStrategiesWithStochasticInflationHaveOnlyExactZeroDifferences(boolean mortality) {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var request = longevity(plan, plan, 12);
        if (!mortality) {
            request = request(plan, plan, new MonteCarloStrategyComparisonRequest.Fixed(request.assumptions().settings(),
                    LocalDate.of(2027, 1, 1), 2056, HouseholdLifetimeScenario.bothSurvive()));
        }
        var result = run(request);
        var s = result.summary();
        assertSame(s, result.summary());
        assertEquals(0, s.pairedStates().aCompletedBFailed());
        assertEquals(0, s.pairedStates().aFailedBCompleted());
        assertEquals(s.pairedStates().aFundingProbability(), s.pairedStates().bFundingProbability());
        money("0", s.pairedStates().fundingProbabilityDifference());
        assertTrue(s.pairedStates().bothCompleted() > 0);
        result.outcomes().forEach(p -> assertEquals(p.outcomeA(), p.outcomeB()));
        metrics(s).forEach(MonteCarloPairedAggregationTest::zero);
        s.annualResults().values().forEach(y -> zero(y.investableAssetsDifference()));
        assertEquals(s, MonteCarloStrategyComparisonAccumulator.reduce(request.assumptions(), result.outcomes()));
        assertEquals(s.hashCode(), MonteCarloStrategyComparisonAccumulator.reduce(request.assumptions(), result.outcomes()).hashCode());
        assertThrows(UnsupportedOperationException.class, () -> s.annualResults().clear());
    }

    @Test
    void longevityOpeningDeathLateDeathAndFailurePopulationsAreSeparate() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var request = longevity(plan, plan, 3);
        var opening = new MonteCarloPairedOutcome(0, new HouseholdLifetimeScenario(Optional.of(Year.of(2027)), Optional.of(Year.of(2027))),
                completed("10", 2026), completed("10", 2026));
        var late = new HouseholdLifetimeScenario(Optional.of(Year.of(2120)), Optional.of(Year.of(2150)));
        var pairs = List.of(opening, new MonteCarloPairedOutcome(1, late, completed("10", 2149), failed(2028)),
                new MonteCarloPairedOutcome(2, late, failed(2027), failed(2027)));
        var s = new MonteCarloStrategyComparisonResult(request,
                MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED, pairs).summary();
        assertEquals(2149, s.annualResults().keySet().stream().max(Integer::compare).orElseThrow());
        assertEquals(1, s.annualResults().get(2027).comparableCount());
        assertEquals(0, s.annualResults().get(2028).comparableCount());
        assertTrue(s.annualResults().get(2028).investableAssetsDifference().differencePercentiles().isEmpty());
        s.annualResults().values().forEach(y -> {
            assertEquals(2, y.livingHouseholdCount());
            assertEquals(1, y.bothDeceasedCount());
        });
        var one = longevity(plan, plan, 1);
        var onlyOpening = MonteCarloStrategyComparisonAccumulator.reduce(one.assumptions(), List.of(opening));
        assertTrue(onlyOpening.annualResults().isEmpty());
        assertEquals(1, onlyOpening.pairedStates().bothCompleted());
    }

    @Test
    void impossiblePrefixesDatesCountsAndPopulationsFailFast() {
        var ok = completed("10", 2028);
        var missing = new MonteCarloStrategyOutcome(Map.of(2028, BigDecimal.TEN), ok.terminal(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> result(List.of(pair(0, missing, ok)), 2028));
        assertThrows(IllegalArgumentException.class, () -> result(List.of(pair(0, ok, ok)), 2029));
        assertThrows(IllegalArgumentException.class, () -> result(List.of(pair(1, ok, ok)), 2028));
        assertThrows(IllegalArgumentException.class, () -> result(List.of(pair(0, failed(2030), ok)), 2028));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPairedStateSummary(1, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPairedMetricSummary(1, Optional.empty(), Optional.empty(), 1, 0, 0));
        var empty = MonteCarloPairedMetricSummary.fromDifferences(List.of());
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPairedAnnualResult(2027, 1, 1, 1, 0, 0, 0, empty));
    }

    @Test
    void earlierFundingFailureStopsCountingAsLivingFailureAtSecondDeath() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var request = longevity(plan, plan, 2);
        var earlier = new HouseholdLifetimeScenario(Optional.of(Year.of(2029)), Optional.of(Year.of(2030)));
        var later = new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.of(Year.of(2032)));
        var summary = MonteCarloStrategyComparisonAccumulator.reduce(request.assumptions(), List.of(
                new MonteCarloPairedOutcome(0, earlier, failed(2028), failed(2028)),
                new MonteCarloPairedOutcome(1, later, completed("10", 2031), completed("10", 2031))));
        assertEquals(1, summary.annualResults().get(2029).bothFailedCount());
        var deathYear = summary.annualResults().get(2030);
        assertEquals(1, deathYear.livingHouseholdCount());
        assertEquals(1, deathYear.bothDeceasedCount());
        assertEquals(0, deathYear.bothFailedCount());
        assertEquals(1, deathYear.comparableCount());
        assertEquals(1, summary.pairedStates().bothFailed(), "Lifetime funding state remains authoritative");
    }
}
