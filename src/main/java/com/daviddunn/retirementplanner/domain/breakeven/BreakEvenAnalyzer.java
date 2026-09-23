package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import java.math.BigDecimal;
import java.util.*;

/** Observational analysis of supplied results. No projection engine or plan writes.
 * Crossover tolerance is exactly zero: even a sub-cent deficit is still a deficit.
 * SS accumulation starts at the first shared year and includes only shared years.
 */
public final class BreakEvenAnalyzer {
    public BreakEvenAnalysisResult analyze(BreakEvenProjectionSnapshot baseline, BreakEvenProjectionSnapshot current) {
        var baselineYears = index(baseline.years());
        var currentYears = index(current.years());
        var shared = new TreeSet<>(baselineYears.keySet());
        shared.retainAll(currentYears.keySet());
        Map<BreakEvenMetric, BreakEvenMetricResult> metrics = new EnumMap<>(BreakEvenMetric.class);
        for (BreakEvenMetric metric : BreakEvenMetric.values()) {
            List<BreakEvenYearResult> points = new ArrayList<>();
            BigDecimal baselineTotal = BigDecimal.ZERO, currentTotal = BigDecimal.ZERO;
            for (int year : shared) {
                var b = baselineYears.get(year);
                var c = currentYears.get(year);
                BigDecimal bv = value(metric, b, baseline);
                BigDecimal cv = value(metric, c, current);
                if (metric == BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY) {
                    baselineTotal = baselineTotal.add(bv);
                    currentTotal = currentTotal.add(cv);
                    bv = baselineTotal;
                    cv = currentTotal;
                }
                points.add(new BreakEvenYearResult(year, c.getPrimaryPersonAge(),
                        current.assumptions().spouse().ageIn(year), bv, cv, cv.subtract(bv)));
            }
            metrics.put(metric, summarize(metric, points));
        }
        return new BreakEvenAnalysisResult(first(baselineYears), last(baselineYears),
                first(currentYears), last(currentYears), shared.isEmpty() ? null : shared.first(),
                shared.isEmpty() ? null : shared.last(), shared.size(),
                !baselineYears.keySet().equals(currentYears.keySet()),
                baseline.assumptions(), current.assumptions(), metrics);
    }

    private BreakEvenMetricResult summarize(BreakEvenMetric metric, List<BreakEvenYearResult> points) {
        List<BreakEvenMetricResult.Crossing> crossings = new ArrayList<>();
        Integer first = null, sustained = null;
        int lastNegative = -1;
        boolean identical = true;
        for (int i = 0; i < points.size(); i++) {
            int sign = points.get(i).difference().signum();
            identical &= sign == 0;
            if (sign < 0) lastNegative = i;
            if (i > 0) {
                int previousSign = points.get(i - 1).difference().signum();
                if (previousSign < 0 && sign >= 0) {
                    if (first == null) first = points.get(i).year();
                    crossings.add(new BreakEvenMetricResult.Crossing(points.get(i).year(), true));
                } else if (previousSign >= 0 && sign < 0) {
                    crossings.add(new BreakEvenMetricResult.Crossing(points.get(i).year(), false));
                }
            }
        }
        if (lastNegative >= 0 && lastNegative < points.size() - 1) {
            sustained = points.get(lastNegative + 1).year();
        }
        BreakEvenStatus status;
        if (points.isEmpty()) status = BreakEvenStatus.NO_COMPARABLE_YEARS;
        else if (identical) status = BreakEvenStatus.IDENTICAL;
        else if (lastNegative < 0) status = BreakEvenStatus.CURRENT_ALREADY_AHEAD;
        else if (sustained != null) status = BreakEvenStatus.BREAK_EVEN_REACHED;
        else if (first != null) status = BreakEvenStatus.CROSSOVER_NOT_SUSTAINED;
        else status = BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD;
        return new BreakEvenMetricResult(metric, status, points, first, sustained, crossings);
    }

    private BigDecimal value(BreakEvenMetric metric, ProjectionYear year, BreakEvenProjectionSnapshot snapshot) {
        return switch (metric) {
            case CUMULATIVE_SOCIAL_SECURITY -> year.getSocialSecurityResult().householdBenefit();
            case INVESTABLE_ASSETS -> year.getEndingInvestableAssets();
            case AFTER_TAX_ESTATE -> year.getAfterTaxEstateValue();
            case TOTAL_NET_WORTH -> year.getEndingInvestableAssets().add(snapshot.nonInvestableAssets().stream()
                    .filter(asset -> asset.getCalendarYear() == year.getCalendarYear())
                    .map(com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection::getTotalValue)
                    .findFirst().orElse(BigDecimal.ZERO));
        };
    }

    private NavigableMap<Integer, ProjectionYear> index(List<ProjectionYear> years) {
        NavigableMap<Integer, ProjectionYear> result = new TreeMap<>();
        for (ProjectionYear year : years) {
            if (result.putIfAbsent(year.getCalendarYear(), year) != null) {
                throw new IllegalArgumentException("Duplicate projection calendar year: " + year.getCalendarYear());
            }
        }
        return result;
    }
    private Integer first(NavigableMap<Integer, ProjectionYear> years) { return years.isEmpty() ? null : years.firstKey(); }
    private Integer last(NavigableMap<Integer, ProjectionYear> years) { return years.isEmpty() ? null : years.lastKey(); }
}
