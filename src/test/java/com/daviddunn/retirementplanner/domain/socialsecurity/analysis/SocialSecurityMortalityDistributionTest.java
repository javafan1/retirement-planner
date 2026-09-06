package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityMortalityDistributionTest {

    @Test
    void preservesOrderAllowsZeroAndDefensivelyCopies() {
        List<SocialSecurityMortalityProbability> source = new ArrayList<>(List.of(
                probability(80, "0.25"),
                probability(85, "0.00"),
                probability(90, "0.75")));

        SocialSecurityMortalityDistribution distribution =
                new SocialSecurityMortalityDistribution(source);
        source.clear();

        assertEquals(List.of(80, 85, 90), distribution.deathAges());
        assertEquals(new BigDecimal("0.00"),
                distribution.probabilityFor(85).orElseThrow().probability());
        assertThrows(UnsupportedOperationException.class,
                () -> distribution.probabilities().clear());
    }

    @Test
    void rejectsIncompleteDuplicateAndInvalidProbabilities() {
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityMortalityDistribution(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityMortalityDistribution(List.of(
                        probability(80, "0.40"),
                        probability(90, "0.50"))));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityMortalityDistribution(List.of(
                        probability(80, "0.50"),
                        probability(80, "0.50"))));
        assertThrows(IllegalArgumentException.class,
                () -> probability(80, "-0.01"));
        assertThrows(IllegalArgumentException.class,
                () -> probability(80, "1.01"));
        assertThrows(IllegalArgumentException.class,
                () -> probability(121, "1.0"));
    }

    @Test
    void independentJointProbabilitiesUseExactBigDecimalArithmetic() {
        SocialSecurityIndependentJointMortalityCalculator calculator =
                new SocialSecurityIndependentJointMortalityCalculator();
        List<BigDecimal> joint = List.of(
                calculator.calculate(new BigDecimal("0.25"), new BigDecimal("0.40")),
                calculator.calculate(new BigDecimal("0.25"), new BigDecimal("0.60")),
                calculator.calculate(new BigDecimal("0.75"), new BigDecimal("0.40")),
                calculator.calculate(new BigDecimal("0.75"), new BigDecimal("0.60")));

        assertEquals(List.of(
                new BigDecimal("0.1000"),
                new BigDecimal("0.1500"),
                new BigDecimal("0.3000"),
                new BigDecimal("0.4500")), joint);
        assertTrue(joint.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(BigDecimal.ONE) == 0);
    }

    private SocialSecurityMortalityProbability probability(int age, String value) {
        return new SocialSecurityMortalityProbability(age, new BigDecimal(value));
    }
}
