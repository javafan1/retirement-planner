package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityBreakEvenCalculatorTest {

    private final SocialSecurityBreakEvenCalculator calculator =
            new SocialSecurityBreakEvenCalculator();

    @Test
    void detectsEveryCumulativeLeaderReversal() {
        Map<YearMonth, BigDecimal> differences = new LinkedHashMap<>();
        differences.put(YearMonth.of(2030, 1), new BigDecimal("-10"));
        differences.put(YearMonth.of(2030, 2), BigDecimal.ZERO);
        differences.put(YearMonth.of(2030, 3), new BigDecimal("5"));
        differences.put(YearMonth.of(2030, 4), new BigDecimal("-2"));

        SocialSecurityBreakEvenResult result =
                calculator.calculateCumulativeDifferences(differences);

        assertEquals(SocialSecurityBreakEvenStatus.CROSSOVER, result.status());
        assertEquals(2, result.crossoverEvents().size());
        assertEquals(YearMonth.of(2030, 2),
                result.crossoverEvents().get(0).crossoverMonth());
        assertEquals(YearMonth.of(2030, 4),
                result.crossoverEvents().get(1).crossoverMonth());
    }

    @Test
    void classifiesIdenticalAndAlwaysAheadTimelines() {
        assertEquals(SocialSecurityBreakEvenStatus.IDENTICAL,
                calculator.calculateCumulativeDifferences(
                                Map.of(YearMonth.of(2030, 1), BigDecimal.ZERO))
                        .status());
        assertEquals(SocialSecurityBreakEvenStatus.ALWAYS_AHEAD_A,
                calculator.calculateCumulativeDifferences(
                                Map.of(YearMonth.of(2030, 1), BigDecimal.ONE))
                        .status());
        assertEquals(SocialSecurityBreakEvenStatus.ALWAYS_AHEAD_B,
                calculator.calculateCumulativeDifferences(
                                Map.of(YearMonth.of(2030, 1), BigDecimal.ONE.negate()))
                        .status());
    }
}
