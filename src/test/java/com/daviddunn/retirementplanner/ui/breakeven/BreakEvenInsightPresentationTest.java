package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenInsightPresentationTest {
    @Test void beforeSameAndAfterUseCalendarYearGapsAndSelectedMetric() {
        assertTrue(headline(reached(2043), reached(2048)).contains("5 years after"));
        assertTrue(headline(reached(2043), reached(2043)).contains("both reach sustained break-even in 2043"));
        assertTrue(headline(reached(2048), reached(2043)).contains("5 years before"));
        assertTrue(BreakEvenInsightPresentation.headline(result(reached(2043), reached(2048)),
                BreakEvenMetric.TOTAL_NET_WORTH).startsWith("Total Net Worth"));
    }

    @Test void unreachedAlreadyAheadIdenticalAndTemporaryCrossingsRemainDistinct() {
        for (boolean ssMissing : List.of(true, false)) {
            var text = headline(ssMissing ? state(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD) : reached(2043),
                    ssMissing ? reached(2048) : state(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD));
            assertTrue(text.contains("no sustained break-even"));
            assertTrue(text.contains("sustained break-even in"));
        }
        assertTrue(headline(state(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD),
                state(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD)).contains("behind at 2051"));
        assertTrue(headline(state(BreakEvenStatus.CURRENT_ALREADY_AHEAD), state(BreakEvenStatus.CURRENT_ALREADY_AHEAD))
                .contains("starts at or above Baseline and remains so through 2051"));
        assertTrue(headline(state(BreakEvenStatus.IDENTICAL), state(BreakEvenStatus.IDENTICAL)).contains("equal throughout"));
        assertTrue(headline(reached(2043), state(BreakEvenStatus.CROSSOVER_NOT_SUSTAINED)).contains("first crossover in 2040, but no sustained"));
        var multiple = new BreakEvenMetricResult(BreakEvenMetric.INVESTABLE_ASSETS, BreakEvenStatus.BREAK_EVEN_REACHED,
                List.of(), 2040, 2048, List.of());
        assertTrue(headline(reached(2043), multiple).contains("first crosses in 2040, but stays at or above Baseline only from 2048"));
    }

    @Test void snapshotHasExplicitReferenceAndPreciseSignedValuesAreNotClassifiedByDisplay() {
        var result = result(reached(2043), reached(2048));
        var insight = new BreakEvenInsight(2043, Map.of(), List.of(new BreakEvenInsight.Observation(
                BreakEvenInsight.Driver.GROSS_PORTFOLIO_WITHDRAWALS, BigDecimal.ONE, new BigDecimal("0.999"))));
        assertTrue(BreakEvenInsightPresentation.snapshotHeading(result, insight).contains("At SS sustained break-even (2043)"));
        assertTrue(BreakEvenInsightPresentation.withdrawal(insight).contains("lower"));
        assertTrue(BreakEvenInsightPresentation.snapshotHeading(result(state(BreakEvenStatus.IDENTICAL), reached(2048)), insight)
                .contains("At comparison end"));
    }

    private String headline(BreakEvenMetricResult ss, BreakEvenMetricResult assets) {
        return BreakEvenInsightPresentation.headline(result(ss, assets), BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY);
    }
    private BreakEvenMetricResult reached(int year) {
        return new BreakEvenMetricResult(BreakEvenMetric.INVESTABLE_ASSETS, BreakEvenStatus.BREAK_EVEN_REACHED,
                List.of(), year, year, List.of());
    }
    private BreakEvenMetricResult state(BreakEvenStatus status) {
        return new BreakEvenMetricResult(BreakEvenMetric.INVESTABLE_ASSETS, status,
                List.of(new BreakEvenYearResult(2051, 80, 78, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("-9"))),
                status == BreakEvenStatus.CROSSOVER_NOT_SUSTAINED ? 2040 : null, null, List.of());
    }
    private BreakEvenAnalysisResult result(BreakEvenMetricResult ss, BreakEvenMetricResult assets) {
        Map<BreakEvenMetric, BreakEvenMetricResult> metrics = new EnumMap<>(BreakEvenMetric.class);
        for (var metric : BreakEvenMetric.values()) {
            var source = metric == BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY ? ss : assets;
            metrics.put(metric, new BreakEvenMetricResult(metric, source.status(), source.years(),
                    source.firstCrossoverYear(), source.sustainedBreakEvenYear(), source.crossings()));
        }
        var person = new BreakEvenPlanSummary.PersonSummary("Alex", LocalDate.of(1960, 1, 1), 70);
        return new BreakEvenAnalysisResult(2027, 2051, 2027, 2060, 2027, 2051, 25, true,
                new BreakEvenPlanSummary(person, person), new BreakEvenPlanSummary(person, person), metrics);
    }
}
