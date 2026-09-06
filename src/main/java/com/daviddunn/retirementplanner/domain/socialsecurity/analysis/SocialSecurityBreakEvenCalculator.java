package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Detects every nonzero cumulative nominal leader reversal. */
final class SocialSecurityBreakEvenCalculator {

    SocialSecurityBreakEvenResult calculate(
            List<SocialSecurityMonthlyComparison> comparisons) {

        Map<YearMonth, BigDecimal> differences = new LinkedHashMap<>();
        for (SocialSecurityMonthlyComparison comparison : comparisons) {
            differences.put(
                    comparison.month(),
                    comparison.cumulativeNominalDifference());
        }
        return calculateCumulativeDifferences(differences);
    }

    SocialSecurityBreakEvenResult calculateCumulativeDifferences(
            Map<YearMonth, BigDecimal> cumulativeDifferences) {

        Objects.requireNonNull(
                cumulativeDifferences,
                "Cumulative differences are required.");
        Integer initialSign = null;
        Integer lastNonZeroSign = null;
        YearMonth zeroPlateauStart = null;
        List<SocialSecurityBreakEvenEvent> events = new ArrayList<>();

        for (Map.Entry<YearMonth, BigDecimal> entry
                : cumulativeDifferences.entrySet()) {
            int sign = Objects.requireNonNull(
                            entry.getValue(),
                            "Cumulative difference is required.")
                    .signum();
            if (sign == 0) {
                if (lastNonZeroSign != null && zeroPlateauStart == null) {
                    zeroPlateauStart = entry.getKey();
                }
                continue;
            }

            if (initialSign == null) {
                initialSign = sign;
                lastNonZeroSign = sign;
                zeroPlateauStart = null;
                continue;
            }

            if (sign != lastNonZeroSign) {
                events.add(new SocialSecurityBreakEvenEvent(
                        zeroPlateauStart == null
                                ? entry.getKey()
                                : zeroPlateauStart,
                        winner(lastNonZeroSign),
                        winner(sign),
                        zeroPlateauStart == null
                                ? entry.getValue()
                                : BigDecimal.ZERO));
            }

            lastNonZeroSign = sign;
            zeroPlateauStart = null;
        }

        if (initialSign == null) {
            return new SocialSecurityBreakEvenResult(
                    SocialSecurityBreakEvenStatus.IDENTICAL,
                    StrategyComparisonWinner.TIE,
                    StrategyComparisonWinner.TIE,
                    List.of());
        }

        StrategyComparisonWinner initialWinner = winner(initialSign);
        StrategyComparisonWinner endingWinner = winner(lastNonZeroSign);
        if (!events.isEmpty()) {
            return new SocialSecurityBreakEvenResult(
                    SocialSecurityBreakEvenStatus.CROSSOVER,
                    initialWinner,
                    endingWinner,
                    events);
        }

        return new SocialSecurityBreakEvenResult(
                initialSign > 0
                        ? SocialSecurityBreakEvenStatus.ALWAYS_AHEAD_A
                        : SocialSecurityBreakEvenStatus.ALWAYS_AHEAD_B,
                initialWinner,
                endingWinner,
                List.of());
    }

    private StrategyComparisonWinner winner(int sign) {
        return sign > 0
                ? StrategyComparisonWinner.STRATEGY_A
                : StrategyComparisonWinner.STRATEGY_B;
    }
}
