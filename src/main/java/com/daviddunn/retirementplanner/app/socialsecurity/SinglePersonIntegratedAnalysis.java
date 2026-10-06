package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import java.time.Year;
import java.util.*;

/** Frozen deterministic presentation inputs. Break-even consumes completed projections, never reruns them. */
public record SinglePersonIntegratedAnalysis(
        IntegratedSocialSecurityCompleteStrategySearchResult result,
        Map<Integer, BreakEvenAnalysisResult> breakEvenByClaimingAge,
        String primaryName, int startYear, int endYear, Optional<Year> primaryDeathYear) {
    public SinglePersonIntegratedAnalysis {
        Objects.requireNonNull(result);
        breakEvenByClaimingAge = Collections.unmodifiableMap(new LinkedHashMap<>(breakEvenByClaimingAge));
        Objects.requireNonNull(primaryName);
        Objects.requireNonNull(primaryDeathYear);
    }

    public static SinglePersonIntegratedAnalysis calculate(IntegratedSocialSecurityCompleteStrategySearchRequest request,
            AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        var plan = request.plan();
        if (plan.getHousehold().hasSpouse()) throw new IllegalArgumentException("Single-person presentation requires one person.");
        var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request, progress, cancellation);
        var baseline = result.currentPlanBaseline();
        int start = baseline.projection().getYears().getFirst().getCalendarYear();
        int end = baseline.projection().getYears().getLast().getCalendarYear();
        var assets = new NonInvestableAssetProjectionService().project(plan.getNonInvestableAssets(), start, end);
        var summary = BreakEvenPlanSummary.from(plan.getHousehold());
        var primary = summary.primary();
        var baselineSnapshot = new BreakEvenProjectionSnapshot(baseline.projection().getYears(), assets, summary);
        Map<Integer, BreakEvenAnalysisResult> comparisons = new LinkedHashMap<>();
        for (var entry : result.entries()) {
            cancellation.throwIfCancellationRequested();
            entry.retainedDetail().ifPresent(detail -> {
                var strategy = entry.strategy();
                var candidate = new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary(primary.name(),
                        primary.birthDate(), strategy.primaryRetirementAge(), strategy.primaryRetirementClaimDate(),
                        primary.mortalityCategory()), null);
                comparisons.put(strategy.primaryRetirementAge(), new BreakEvenAnalyzer().analyze(baselineSnapshot,
                        new BreakEvenProjectionSnapshot(detail.projection().getYears(), assets, candidate)));
            });
        }
        return new SinglePersonIntegratedAnalysis(result, comparisons, plan.getHousehold().getPrimaryPerson().getFullName(),
                start, end, request.primaryDeathYear());
    }
}
