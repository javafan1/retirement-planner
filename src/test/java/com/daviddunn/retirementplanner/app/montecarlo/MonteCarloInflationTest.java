package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloInflationTest {
    static MonteCarloInflationSettings inflation(String volatility) {
        return new MonteCarloInflationSettings(new BigDecimal("0.02"), new BigDecimal(volatility), new BigDecimal("-0.02"));
    }

    @Test
    void zeroVolatilityIsExactAndDoesNotDraw() {
        var path = new MonteCarloInflationGenerator().generate(2027, 2056, inflation("0"), bytes -> fail("No draws"));
        assertEquals(30, path.annualRates().size());
        assertEquals(new BigDecimal("0.02"), path.inflationForYear(2027));
        assertEquals(new BigDecimal("0.02"), path.inflationForYear(2056));
        assertTrue(path.annualRates().values().stream().allMatch(inflation("0").expectedInflationRate()::equals));
        assertThrows(UnsupportedOperationException.class, () -> path.annualRates().clear());
        assertThrows(IllegalArgumentException.class, () -> path.inflationForYear(2057));
    }

    @Test
    void deterministicNormalPopulationHasExpectedMomentsAndFloor() {
        var parameters = new MonteCarloInflationSettings(new BigDecimal("0.025"), new BigDecimal("0.0175"), new BigDecimal("-0.90"));
        var generator = new MonteCarloInflationGenerator();
        var samples = generator.generate(0, 99999, parameters,
                MonteCarloRandomStreams.create(417, 0, 4, 1)).annualRates().values();
        double mean = samples.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
        double sd = StrictMath.sqrt(samples.stream().mapToDouble(v -> StrictMath.pow(v.doubleValue() - mean, 2)).average().orElseThrow());
        assertEquals(0.025, mean, 0.0002);
        assertEquals(0.0175, sd, 0.0002);
        var floored = generator.generate(0, 9999, inflation("0.20"), MonteCarloRandomStreams.create(417, 0, 4, 1));
        assertTrue(floored.annualRates().containsValue(new BigDecimal("-0.02")));
        assertTrue(floored.annualRates().values().stream().allMatch(rate -> rate.compareTo(new BigDecimal("-0.02")) >= 0));
        assertTrue(floored.annualRates().values().stream().allMatch(rate -> BigDecimal.ONE.add(rate).signum() > 0));
    }

    @Test
    void repeatabilityScenarioCountOrderAndYearPrefixesAreStable() {
        var settings = new MonteCarloSettings(2, 417, new BigDecimal("0.045"), new BigDecimal("0.12")).withInflation(inflation("0.0175"));
        var generator = new MonteCarloInflationGenerator();
        var a = generator.generate(2027, 2056, settings, 0).orElseThrow().annualRates();
        assertEquals(a, generator.generate(2027, 2056, settings, 0).orElseThrow().annualRates());
        assertNotEquals(a, generator.generate(2027, 2056, settings, 1).orElseThrow().annualRates());
        var larger = new MonteCarloSettings(5000, 417, settings.expectedReturn(), settings.returnVolatility(), settings.inflation());
        assertEquals(a, generator.generate(2027, 2056, larger, 0).orElseThrow().annualRates());
        var longer = generator.generate(2027, 2090, larger, 0).orElseThrow();
        a.forEach((year, value) -> assertEquals(value, longer.inflationForYear(year)));
        assertTrue(generator.generate(2027, 2056, new MonteCarloSettings(2, 417, BigDecimal.ZERO, BigDecimal.ZERO), 0).isEmpty());
    }

    @Test
    void inflationParametersCannotChangeMarketOrMortalityAndMortalityCannotChangeInflationPrefix() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var base = MonteCarloSettings.forPlan(plan, 5, 417, new BigDecimal("0.12"));
        var stochastic = base.withInflation(inflation("0.0175"));
        var changed = base.withInflation(inflation("0.08"));
        var worlds = new MonteCarloWorldGenerator(MonteCarloWorldGeneratorTest.request(plan, base));
        var inflationWorlds = new MonteCarloWorldGenerator(MonteCarloWorldGeneratorTest.request(plan, stochastic));
        var changedWorlds = new MonteCarloWorldGenerator(MonteCarloWorldGeneratorTest.request(plan, changed));
        var assumptions = new LongevitySessionSettings(plan.getPlanningAssumptions().getProjectionStartDate(),
                new SocialSecurityMortalityAdjustment(new BigDecimal("0.8")), new SocialSecurityMortalityAdjustment(new BigDecimal("1.2")));
        var changedMortality = new MonteCarloWorldGenerator(new MonteCarloMortalityRequest(plan, stochastic, assumptions));
        for (int i : new int[]{224, 1, 0, 224}) {
            var baseWorld = worlds.generate(i);
            var inflated = inflationWorlds.generate(i);
            var different = changedWorlds.generate(i);
            assertEquals(baseWorld.lifetimeScenario(), inflated.lifetimeScenario());
            assertEquals(baseWorld.lifetimeScenario(), different.lifetimeScenario());
            assertEquals(baseWorld.economicPath().annualReturns(), inflated.economicPath().annualReturns());
            assertEquals(baseWorld.economicPath().annualReturns(), different.economicPath().annualReturns());
            var other = changedMortality.generate(i).inflationPath().orElseThrow();
            inflated.inflationPath().orElseThrow().annualRates().forEach((year, rate) -> {
                if (other.annualRates().containsKey(year)) assertEquals(rate, other.inflationForYear(year));
            });
        }
    }

    @Test
    void zeroVolatilityEqualsOldResultsInBothModesAndPositiveVolatilityChangesOnlyFinancialOutcomes() throws Exception {
        var plan = MonteCarloMortalityExecutionTest.plan();
        String before = MonteCarloMortalityExecutionTest.JSON.writeValueAsString(plan);
        var base = MonteCarloSettings.forPlan(plan, 12, 417, new BigDecimal("0.12"));
        var zero = base.withInflation(inflation("0"));
        var stochastic = base.withInflation(inflation("0.0175"));
        var analyzer = new MonteCarloAnalyzer();
        var fixed = analyzer.analyze(plan, base);
        var fixedZero = analyzer.analyze(plan, zero);
        assertEquals(fixed.outcomes(), fixedZero.outcomes());
        assertEquals(fixed.annualInvestableAssets(), fixedZero.annualInvestableAssets());
        assertEquals(fixed.endingAfterTaxEstate(), fixedZero.endingAfterTaxEstate());
        var varied = analyzer.analyze(plan, stochastic);
        assertNotEquals(fixed.outcomes(), varied.outcomes());
        assertEquals(varied, analyzer.analyze(plan, stochastic));
        var longevity = analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, base));
        var longevityZero = analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, zero));
        assertEquals(longevity.outcomes(), longevityZero.outcomes());
        assertEquals(longevity.annualResults(), longevityZero.annualResults());
        var request = MonteCarloWorldGeneratorTest.request(plan, stochastic);
        var sampled = analyzer.analyzeMortality(plan, request);
        assertEquals(sampled.outcomes(), analyzer.analyzeMortality(plan, request).outcomes());
        assertNotEquals(longevity.outcomes(), sampled.outcomes());
        for (int i = 0; i < 12; i++) assertEquals(longevity.outcomes().get(i).lifetimeScenario(), sampled.outcomes().get(i).lifetimeScenario());
        assertEquals(before, MonteCarloMortalityExecutionTest.JSON.writeValueAsString(plan));
    }

    @Test
    void suppliedWorldPathMatchesDirectEngineIncludingTerminalAndAnnualMetrics() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var settings = MonteCarloSettings.forPlan(plan, 3, 417, new BigDecimal("0.12")).withInflation(inflation("0.0175"));
        var request = MonteCarloWorldGeneratorTest.request(plan, settings);
        var worlds = new MonteCarloWorldGenerator(request);
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request);
        for (int i = 0; i < 3; i++) {
            var world = worlds.generate(i);
            var context = MonteCarloMortalityExecutionTest.context(world).withSurvivorClaimingAge(67)
                    .withInflationPath(world.inflationPath().orElseThrow());
            var projection = new ProjectionEngine().project(plan, context, world.economicPath());
            var actual = result.outcomes().get(i);
            assertEquals(actual.secondDeathYear() - 1, projection.getEndYear());
            var totals = new ProjectionMetricsCalculator().calculate(plan, projection);
            assertEquals(totals.afterTaxEstate(), actual.terminal().orElseThrow().afterTaxEstate());
            assertEquals(totals.totalTaxes(), actual.terminal().orElseThrow().lifetimeTaxes());
            projection.getYears().forEach(year -> assertEquals(year.getEndingInvestableAssets(),
                    actual.annualInvestableAssets().get(year.getCalendarYear())));
        }
    }

    @Test
    void fundingFailuresKeepTheirClassificationAndConditionalPopulations() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var settings = MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO).withInflation(inflation("0.0175"));
        var result = new MonteCarloAnalyzer().analyze(plan, settings, null, index ->
                ProjectionEconomicPath.constant(index == 1 ? BigDecimal.ONE.negate() : new BigDecimal("0.045")));
        assertEquals(1, result.fundingFailureCount());
        assertTrue(result.outcomes().get(1).fundingFailure().isPresent());
        assertEquals(2, result.endingAfterTaxEstate().orElseThrow().sampleCount());
        assertTrue(result.annualInvestableAssets().values().stream().allMatch(q -> q.orElseThrow().sampleCount() == 2));
    }

    @Test
    void openingDeathDoesNotRunEngineOrRequireUnusedInflationYears() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var world = MonteCarloMortalityExecutionTest.world(0, 2027, 2027, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var inflated = new MonteCarloWorld(0, world.economicPath(), world.lifetimeScenario(),
                Optional.of(new ProjectionInflationPath(List.of())));
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(com.daviddunn.retirementplanner.domain.model.RetirementPlan p,
                    ProjectionEvaluationContext c, ProjectionEconomicPath e) { throw new AssertionError("No engine call at opening death"); }
        };
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO).withInflation(inflation("0.0175"));
        var result = new MonteCarloAnalyzer(engine).analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, settings),
                index -> inflated, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, result.completedCount());
        assertTrue(result.annualResults().isEmpty());
        assertEquals(MonteCarloMortalityExecutionTest.run(plan, world).outcomes(), result.outcomes());
    }

    @Test
    void invalidParametersFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> inflation("-0.01"));
        assertThrows(IllegalArgumentException.class, () -> inflation("1e999"));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloInflationSettings(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloInflationSettings(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.01")));
        assertThrows(NullPointerException.class, () -> new MonteCarloInflationSettings(null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloInflationGenerator().generate(2028, 2027, inflation("0"), bytes -> {}));
    }
}
