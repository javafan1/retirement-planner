package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityExecutionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloMortalityAccumulatorTest {

    @Test
    void annualPopulationsReconcileAcrossOpeningDeathFailureAndLongLivedSurvivors() {
        var result = result(List.of(
                success(0, 2027, 2027, 10),
                success(1, 2028, 2030, 100),
                failure(2, 2035, 2040, 2030, 200),
                success(3, 2042, 2028, 300)));
        assertEquals(2027, result.firstReportingYear());
        assertEquals(Optional.of(2041), result.lastReportingYear());
        assertEquals(15, result.annualResults().size());
        for (var annual : result.annualResults().values()) {
            reconcile(annual);
            int year = annual.year();
            assertEquals(year < 2030 ? 3 : year < 2040 ? 2 : 1, annual.livingHouseholdCount());
            assertEquals(year >= 2030 && year < 2040 ? 1 : 0, annual.livingFundingFailedByYearCount());
            var values = new ArrayList<BigDecimal>();
            if (year < 2030) {
                values.add(BigDecimal.valueOf(100));
                values.add(BigDecimal.valueOf(200));
            }
            values.add(BigDecimal.valueOf(300));
            assertEquals(MonteCarloPercentiles.of(values), annual.investableAssets());
        }
        assertEquals(3, result.annualResults().get(2027).bothAliveCount());
        assertEquals(1, result.annualResults().get(2028).primaryOnlyAliveCount());
        assertEquals(1, result.annualResults().get(2028).spouseOnlyAliveCount());
        assertEquals(Optional.of(new BigDecimal("0.5")),
                result.annualResults().get(2030).livingFundingRate());
        assertEquals(BigDecimal.ZERO, result.outcomes().getFirst().terminal().orElseThrow().lifetimeTaxes());
        assertEquals(3, result.endingInvestableAssets().orElseThrow().sampleCount());
        assertEquals(Optional.of(LocalDate.of(2027, 1, 1)), result.earliestSuccessfulTerminalBalanceDate());
        assertEquals(Optional.of(LocalDate.of(2041, 12, 31)), result.latestSuccessfulTerminalBalanceDate());
        assertEquals(Map.of(2027, 1, 2029, 1, 2041, 1), result.successfulTerminalYearCounts());
    }

    @Test
    void typeSevenAnnualAndAllFourTerminalDistributionsUseOnlyCompletedObservations() {
        var result = result(List.of(success(0, 2029, 2029, 100), success(1, 2031, 2031, 200),
                failure(2, 2033, 2033, 2027, 999)));
        assertPercentiles(result.annualResults().get(2027).investableAssets(), 100, 200);
        assertPercentiles(result.endingInvestableAssets(), 100, 200);
        assertPercentiles(result.endingNetWorth(), 200, 400);
        assertPercentiles(result.afterTaxEstate(), 50, 100);
        assertPercentiles(result.lifetimeTaxes(), 10, 20);
        assertEquals(Map.of(2028, 1, 2030, 1), result.successfulTerminalYearCounts());
        assertEquals(0, result.annualResults().get(2032).completedLivingYearSampleCount());
        assertTrue(result.annualResults().get(2032).investableAssets().isEmpty());
        assertEquals(Optional.of(BigDecimal.ZERO), result.annualResults().get(2032).livingFundingRate());
    }

    @Test
    void sevenOfTenWorldsGiveSeventyPercentFundingAndSevenTerminalSamples() {
        var worlds = new ArrayList<WorldOutcome>();
        for (int i = 0; i < 10; i++) {
            worlds.add(i < 7 ? success(i, 2030, 2031, 100 + i)
                    : failure(i, 2032, 2033, 2029, 500));
        }
        var result = result(worlds);
        assertEquals(new BigDecimal("0.7"), result.fundingProbability());
        assertEquals(7, result.completedCount());
        assertEquals(3, result.fundingFailureCount());
        for (var distribution : List.of(result.endingInvestableAssets(), result.endingNetWorth(),
                result.afterTaxEstate(), result.lifetimeTaxes())) {
            assertEquals(7, distribution.orElseThrow().sampleCount());
            assertTrue(distribution.orElseThrow().minimum().signum() > 0);
        }
        assertEquals(7, result.successfulTerminalYearCounts().values().stream().mapToInt(Integer::intValue).sum());
        var stats = result.fundingFailureStatistics().orElseThrow();
        assertEquals(Map.of(2029, 3L), stats.countByYear());
        assertEquals(new BigDecimal("0.3"), stats.probabilityByYear().get(2029));
        assertEquals(BigDecimal.ONE, stats.fractionOfFailuresByYear().get(2029));
        assertEquals(0, BigDecimal.TEN.compareTo(stats.shortfallAmount().minimum()));
        result.annualResults().values().forEach(MonteCarloMortalityAccumulatorTest::reconcile);
    }

    @Test
    void allSuccessIncludesZeroEstateAndSamplesEqualLivingPopulation() {
        var result = result(List.of(success(0, 2028, 2030, 0), success(1, 2032, 2031, 100)));
        assertEquals(BigDecimal.ONE, result.fundingProbability());
        assertTrue(result.fundingFailureStatistics().isEmpty());
        assertEquals(0, result.endingInvestableAssets().orElseThrow().minimum().signum());
        result.annualResults().values().forEach(annual -> {
            reconcile(annual);
            assertEquals(annual.livingHouseholdCount(), annual.completedLivingYearSampleCount());
            assertEquals(0, annual.livingFundingFailedByYearCount());
            assertEquals(Optional.of(BigDecimal.ONE), annual.livingFundingRate());
        });
    }

    @Test
    void allFailureRetainsPrefixesButHasNoTerminalDistributionsOrDates() {
        var result = result(List.of(failure(0, 2030, 2031, 2029, 100), failure(1, 2032, 2033, 2028, 200)));
        assertEquals(BigDecimal.ZERO, result.fundingProbability());
        assertTrue(result.endingInvestableAssets().isEmpty());
        assertTrue(result.endingNetWorth().isEmpty());
        assertTrue(result.afterTaxEstate().isEmpty());
        assertTrue(result.lifetimeTaxes().isEmpty());
        assertTrue(result.earliestSuccessfulTerminalBalanceDate().isEmpty());
        assertTrue(result.latestSuccessfulTerminalBalanceDate().isEmpty());
        assertTrue(result.successfulTerminalYearCounts().isEmpty());
        assertEquals(2, result.annualResults().get(2027).completedLivingYearSampleCount());
        assertEquals(1, result.annualResults().get(2028).completedLivingYearSampleCount());
        assertTrue(result.annualResults().get(2029).investableAssets().isEmpty());
        result.annualResults().values().forEach(MonteCarloMortalityAccumulatorTest::reconcile);
    }

    @Test
    void allOpeningDeathsHaveEmptyReportingRangeAndSuccessfulOpeningTerminals() {
        var result = result(List.of(success(0, 2027, 2027, 100), success(1, 2027, 2027, 200)));
        assertTrue(result.annualResults().isEmpty());
        assertTrue(result.lastReportingYear().isEmpty());
        assertEquals(BigDecimal.ONE, result.fundingProbability());
        assertPercentiles(result.endingInvestableAssets(), 100, 200);
        assertEquals(0, result.lifetimeTaxes().orElseThrow().maximum().signum());
        assertEquals(Map.of(2027, 2), result.successfulTerminalYearCounts());
        var noLiving = new MonteCarloMortalityAnnualResult(2028, 2, 0, 0, 0, 2, 0, 0, 0, Optional.empty());
        assertTrue(noLiving.livingFundingRate().isEmpty());
    }

    @Test
    void rejectsMissingWorldsMissingPrefixRowsAndSamplesAtOrAfterFailure() {
        var valid = success(0, 2030, 2030, 100);
        var missing = new TreeMap<>(valid.annualInvestableAssets());
        missing.remove(2028);
        assertThrows(IllegalArgumentException.class, () -> result(List.of(new WorldOutcome(0,
                valid.lifetimeScenario(), missing, valid.terminal(), valid.fundingFailure()))));
        var failed = failure(0, 2032, 2032, 2029, 100);
        var extra = new TreeMap<>(failed.annualInvestableAssets());
        extra.put(2029, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> result(List.of(new WorldOutcome(0,
                failed.lifetimeScenario(), extra, failed.terminal(), failed.fundingFailure()))));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloMortalityAnnualResult(
                2027, 2, 2, 1, 0, 0, 2, 0, 0, MonteCarloPercentiles.of(List.of(BigDecimal.ONE))));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloMortalityAnnualResult(
                2027, 2, 2, 2, 0, 0, 2, 0, 0, MonteCarloPercentiles.of(List.of(BigDecimal.ONE))));
    }

    @Test
    void aggregateCollectionsAreImmutableAndInputListIsNotRetained() {
        var worlds = new ArrayList<>(List.of(success(0, 2030, 2030, 100)));
        var result = result(worlds);
        worlds.clear();
        assertEquals(1, result.completedCount());
        assertThrows(UnsupportedOperationException.class, () -> result.annualResults().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.successfulTerminalYearCounts().clear());
    }

    static void reconcile(MonteCarloMortalityAnnualResult annual) {
        assertEquals(annual.requestedWorldCount(), annual.livingHouseholdCount() + annual.bothDeceasedCount());
        assertEquals(annual.livingHouseholdCount(),
                annual.completedLivingYearSampleCount() + annual.livingFundingFailedByYearCount());
        assertEquals(annual.livingHouseholdCount(),
                annual.bothAliveCount() + annual.primaryOnlyAliveCount() + annual.spouseOnlyAliveCount());
        assertEquals(annual.completedLivingYearSampleCount(),
                annual.investableAssets().map(MonteCarloPercentiles::sampleCount).orElse(0));
    }

    private static void assertPercentiles(Optional<MonteCarloPercentiles> actual, int low, int high) {
        var p = actual.orElseThrow();
        assertEquals(2, p.sampleCount());
        // Hand-checkable two-observation Type-7 expectations, no test-side quantile algorithm.
        assertEquals(0, BigDecimal.valueOf(low).compareTo(p.minimum()));
        assertEquals(0, BigDecimal.valueOf(high).compareTo(p.maximum()));
        assertEquals(0, BigDecimal.valueOf(low).multiply(new BigDecimal("1.1")).compareTo(p.p10()));
        assertEquals(0, BigDecimal.valueOf(low).multiply(new BigDecimal("1.25")).compareTo(p.p25()));
        assertEquals(0, BigDecimal.valueOf(low).multiply(new BigDecimal("1.5")).compareTo(p.p50()));
        assertEquals(0, BigDecimal.valueOf(low).multiply(new BigDecimal("1.75")).compareTo(p.p75()));
        assertEquals(0, BigDecimal.valueOf(low).multiply(new BigDecimal("1.9")).compareTo(p.p90()));
    }

    private static MonteCarloMortalityAnalysisResult result(List<WorldOutcome> worlds) {
        return new MonteCarloMortalityAnalysisResult(request(plan(), worlds.size(), "0"), "test", "test", worlds);
    }

    private static WorldOutcome success(int index, int primary, int spouse, int value) {
        int last = Math.max(primary, spouse) - 1;
        var amount = BigDecimal.valueOf(value);
        return new WorldOutcome(index, lifetime(primary, spouse), prefix(last, amount),
                Optional.of(new TerminalOutcome(last < 2027 ? LocalDate.of(2027, 1, 1) : LocalDate.of(last, 12, 31),
                        amount, amount.multiply(BigDecimal.TWO), amount.divide(BigDecimal.TWO),
                        last < 2027 ? BigDecimal.ZERO : amount.divide(BigDecimal.TEN))), Optional.empty());
    }

    private static WorldOutcome failure(int index, int primary, int spouse, int year, int value) {
        return new WorldOutcome(index, lifetime(primary, spouse), prefix(year - 1, BigDecimal.valueOf(value)),
                Optional.empty(), Optional.of(new FundingFailure(year, year - 2027, Optional.empty(), Optional.empty(),
                        FundingFailure.Stage.WITHDRAWAL_ALLOCATION, Optional.empty(), BigDecimal.TEN,
                        BigDecimal.ZERO, BigDecimal.TEN)));
    }

    private static HouseholdLifetimeScenario lifetime(int primary, int spouse) {
        return new HouseholdLifetimeScenario(Optional.of(Year.of(primary)), Optional.of(Year.of(spouse)));
    }

    private static Map<Integer, BigDecimal> prefix(int last, BigDecimal value) {
        var result = new TreeMap<Integer, BigDecimal>();
        for (int year = 2027; year <= last; year++) {
            result.put(year, value);
        }
        return result;
    }
}
