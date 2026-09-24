package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloWorldGeneratorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloMortalityDistributionTest {

    private static final int SAMPLE_COUNT = 100_000;
    private static final BigDecimal COUNT = BigDecimal.valueOf(SAMPLE_COUNT);
    // Declared in advance: pool cells with <25 expected observations and use
    // an eight-sigma + five-observation bound. Fixed seed, no retries or tuning.
    private static final BigDecimal MIN_EXPECTED = BigDecimal.valueOf(25);
    private static final BigDecimal SIGMAS = BigDecimal.valueOf(8);

    @Test
    void generatedWorldFrequenciesMatchAuthoritativeJointAndBothMarginals() {
        var generator = new MonteCarloWorldGenerator(request(plan(), settings(SAMPLE_COUNT, 417, "0")));
        var prepared = generator.mortalityScenarios();
        Map<String, BigDecimal> jointExpected = new LinkedHashMap<>();
        Map<String, BigDecimal> primaryExpected = new LinkedHashMap<>();
        Map<String, BigDecimal> spouseExpected = new LinkedHashMap<>();
        prepared.scenarios().forEach(scenario -> jointExpected.put(
                scenario.primaryDeathAge() + ":" + scenario.spouseDeathAge(), scenario.jointProbability()));
        prepared.primary().distribution().probabilities().forEach(value ->
                primaryExpected.put(Integer.toString(value.deathAge()), value.probability()));
        prepared.spouse().distribution().probabilities().forEach(value ->
                spouseExpected.put(Integer.toString(value.deathAge()), value.probability()));

        Map<String, Integer> jointActual = new HashMap<>();
        Map<String, Integer> primaryActual = new HashMap<>();
        Map<String, Integer> spouseActual = new HashMap<>();
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            var lifetime = generator.generate(index).lifetimeScenario();
            int primaryAge = lifetime.primaryDeathYear().orElseThrow().getValue() - 1963;
            int spouseAge = lifetime.spouseDeathYear().orElseThrow().getValue() - 1965;
            jointActual.merge(primaryAge + ":" + spouseAge, 1, Integer::sum);
            primaryActual.merge(Integer.toString(primaryAge), 1, Integer::sum);
            spouseActual.merge(Integer.toString(spouseAge), 1, Integer::sum);
        }
        verifyFrequencies("joint", jointExpected, jointActual);
        verifyFrequencies("primary", primaryExpected, primaryActual);
        verifyFrequencies("spouse", spouseExpected, spouseActual);
    }

    @Test
    void exactOrderedJointMassesRespectEveryPositiveCellBoundary() {
        var prepared = new MonteCarloWorldGenerator(request(plan(), settings(1, 417, "0"))).mortalityScenarios();
        var probabilities = prepared.scenarios().stream().map(SocialSecurityJointMortalityScenario::jointProbability).toList();
        var sampler = new DiscreteProbabilitySampler(probabilities);
        int scale = probabilities.stream().map(BigDecimal::stripTrailingZeros).mapToInt(BigDecimal::scale).max().orElseThrow();
        BigInteger lower = BigInteger.ZERO;
        for (int index = 0; index < probabilities.size(); index++) {
            var mass = probabilities.get(index).setScale(scale).unscaledValue();
            if (mass.signum() > 0) {
                assertEquals(index, sampler.sample(bytesFor(lower)));
                assertEquals(index, sampler.sample(bytesFor(lower.add(mass).subtract(BigInteger.ONE))));
            }
            lower = lower.add(mass);
        }
        assertEquals(BigInteger.TEN.pow(scale), lower);
    }

    private static DiscreteProbabilitySampler.RandomBytes bytesFor(BigInteger value) {
        return destination -> {
            Arrays.fill(destination, (byte) 0);
            var bytes = value.toByteArray();
            int length = Math.min(bytes.length, destination.length);
            System.arraycopy(bytes, bytes.length - length, destination, destination.length - length, length);
        };
    }

    private static void verifyFrequencies(String label, Map<String, BigDecimal> expected, Map<String, Integer> actual) {
        assertTrue(expected.keySet().containsAll(actual.keySet()), label + " sampled an unsupported outcome");
        assertEquals(SAMPLE_COUNT, actual.values().stream().mapToInt(Integer::intValue).sum());
        BigDecimal rareProbability = BigDecimal.ZERO;
        int rareCount = 0;
        for (var entry : expected.entrySet()) {
            int observed = actual.getOrDefault(entry.getKey(), 0);
            if (entry.getValue().signum() == 0) {
                assertEquals(0, observed, label + " zero-probability cell");
            } else if (entry.getValue().multiply(COUNT).compareTo(MIN_EXPECTED) < 0) {
                rareProbability = rareProbability.add(entry.getValue());
                rareCount += observed;
            } else {
                assertFrequency(label + " " + entry.getKey(), entry.getValue(), observed);
            }
        }
        assertFrequency(label + " pooled rare cells", rareProbability, rareCount);
    }

    private static void assertFrequency(String label, BigDecimal probability, int observed) {
        BigDecimal expected = probability.multiply(COUNT);
        BigDecimal variance = expected.multiply(BigDecimal.ONE.subtract(probability));
        BigDecimal tolerance = variance.sqrt(MathContext.DECIMAL128).multiply(SIGMAS).add(BigDecimal.valueOf(5));
        BigDecimal error = BigDecimal.valueOf(observed).subtract(expected).abs();
        assertTrue(error.compareTo(tolerance) <= 0,
                () -> label + ": observed=" + observed + ", expected=" + expected + ", tolerance=" + tolerance);
    }
}
