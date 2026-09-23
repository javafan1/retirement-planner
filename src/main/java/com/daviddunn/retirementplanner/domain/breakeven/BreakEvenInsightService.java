package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explains existing results; sums only the analyzer's actual shared years, inclusively.
 * Gross withdrawals already include RMD and tax funding, exclude Roth transfers,
 * and may include cash subsequently redeposited. They are not consumption.
 */
public final class BreakEvenInsightService {
    public BreakEvenInsight prepare(BreakEvenAnalysisResult result) {
        return prepare(result, List.of(), List.of());
    }

    public BreakEvenInsight prepare(BreakEvenAnalysisResult result,
            List<ProjectionYear> baseline, List<ProjectionYear> current) {
        Objects.requireNonNull(result);
        Integer reference = result.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).sustainedBreakEvenYear();
        if (reference == null) reference = result.comparisonEndYear();
        Map<BreakEvenMetric, BreakEvenYearResult> snapshot = new EnumMap<>(BreakEvenMetric.class);
        for (var metric : result.metrics().values()) {
            for (var point : metric.years()) {
                if (Objects.equals(reference, point.year())) snapshot.put(metric.metric(), point);
            }
        }
        List<BreakEvenInsight.Observation> observations = new ArrayList<>();
        if (reference != null) {
            int through = reference;
            var years = result.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).years().stream()
                    .map(BreakEvenYearResult::year).filter(year -> year <= through).toList();
            var b = index(baseline);
            var c = index(current);
            // Missing raw data omits drivers, never substitutes zero or a different year.
            if (!years.isEmpty() && b.keySet().containsAll(years) && c.keySet().containsAll(years)) {
                for (var driver : BreakEvenInsight.Driver.values()) {
                    Function<ProjectionYear, BigDecimal> value = switch (driver) {
                        case GROSS_PORTFOLIO_WITHDRAWALS -> ProjectionYear::getPortfolioWithdrawal;
                        case INCOME_TAXES -> ProjectionYear::getTotalIncomeTax;
                        case INVESTMENT_GROWTH -> ProjectionYear::getInvestmentGrowth;
                    };
                    observations.add(new BreakEvenInsight.Observation(driver,
                            years.stream().map(year -> value.apply(b.get(year))).reduce(BigDecimal.ZERO, BigDecimal::add),
                            years.stream().map(year -> value.apply(c.get(year))).reduce(BigDecimal.ZERO, BigDecimal::add)));
                }
            }
        }
        return new BreakEvenInsight(reference, snapshot, observations);
    }

    private Map<Integer, ProjectionYear> index(List<ProjectionYear> years) {
        return years.stream().collect(Collectors.toUnmodifiableMap(ProjectionYear::getCalendarYear, Function.identity()));
    }
}
