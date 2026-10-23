package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.charts.*;
import java.time.Instant;
import java.util.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot.*;

/** Pure codec adapter only. No controls, controller, store, worker, rendering or lifecycle callbacks. */
public final class MonteCarloSnapshotPresentationMapper {
    private MonteCarloSnapshotPresentationMapper() { }

    public static MonteCarloSnapshot capture(MonteCarloRun run, RetirementPlan frozenPlan,
            Instant started, Instant completed) {
        return capture(run, frozenPlan, started, completed, null);
    }
    public static MonteCarloSnapshot capture(MonteCarloRun run, RetirementPlan frozenPlan,
            Instant started, Instant completed, String suppliedWorldFingerprint) {
        var display = display(run);
        return run.mode() == MonteCarloMode.FIXED_LIFESPAN
                ? MonteCarloSnapshotMapping.fixed(run.result(), frozenPlan, display, started, completed, suppliedWorldFingerprint)
                : MonteCarloSnapshotMapping.mortality(run.mortalityResult(), frozenPlan, display, started, completed, suppliedWorldFingerprint);
    }
    public static MonteCarloSnapshot capture(MonteCarloStrategyComparisonRun run, Instant started, Instant completed) {
        return capture(run, started, completed, null);
    }
    public static MonteCarloSnapshot capture(MonteCarloStrategyComparisonRun run, Instant started, Instant completed, String suppliedWorldFingerprint) {
        var r = run.result();
        var display = new Display(null, "", "", List.of(), new Chart(List.of(), List.of(), List.of(), List.of()),
                r.request().assumptions().start().getYear(), r.summary().annualResults().keySet().stream().max(Integer::compareTo).orElse(r.request().assumptions().start().getYear() - 1),
                false, run.elapsedNanos(), run.details());
        return MonteCarloSnapshotMapping.comparison(r, display, started, completed, suppliedWorldFingerprint);
    }
    public static MonteCarloRun restoreRun(MonteCarloSnapshot snapshot) {
        var d = snapshot.display();
        var result = snapshot.mode() == Mode.FIXED
                ? new MonteCarloRun.Fixed(MonteCarloSnapshotMapping.restoreFixed(snapshot))
                : new MonteCarloRun.Mortality(MonteCarloSnapshotMapping.restoreMortality(snapshot));
        var context = new ProjectionChartModel(d.chart().years().stream().map(p -> {
            var metrics = new EnumMap<ProjectionChartMetric, java.math.BigDecimal>(ProjectionChartMetric.class);
            p.values().forEach((k, v) -> metrics.put(ProjectionChartMetric.valueOf(k), v));
            return new ProjectionChartModel.Point(p.year(), p.primaryAge(), metrics, p.composition(), p.complete());
        }).toList(), d.chart().claims().stream().map(c -> new ProjectionChartModel.Claim(c.year(), c.person(), c.age(), c.date())).toList(),
                d.chart().roth().stream().map(p -> new ProjectionChartModel.Period(p.firstYear(), p.lastYear())).toList(),
                d.chart().rmd().stream().map(p -> new ProjectionChartModel.Period(p.firstYear(), p.lastYear())).toList());
        var fan = new MonteCarloFanModel(d.years().stream().map(y -> new MonteCarloFanModel.Year(y.year(), Optional.ofNullable(y.assets()),
                Optional.ofNullable(y.deterministic()), Optional.ofNullable(y.population()).map(MonteCarloSnapshotPresentationMapper::population))).toList(),
                snapshot.settings().trials(), context, snapshot.mode() == Mode.FIXED ? MonteCarloMode.FIXED_LIFESPAN : MonteCarloMode.LONGEVITY_ADJUSTED,
                d.primaryName(), d.spouseName());
        return new MonteCarloRun(result, fan, d.people(), d.firstYear(), d.lastYear(), d.referenceIncomplete(), d.elapsedNanos(), d.details());
    }
    public static MonteCarloStrategyComparisonRun restoreComparison(MonteCarloSnapshot snapshot) {
        return new MonteCarloStrategyComparisonRun(MonteCarloSnapshotMapping.restoreComparison(snapshot),
                snapshot.mode() == Mode.PAIRED_FIXED ? MonteCarloMode.FIXED_LIFESPAN : MonteCarloMode.LONGEVITY_ADJUSTED,
                snapshot.display().details(), snapshot.display().elapsedNanos());
    }
    private static Display display(MonteCarloRun run) {
        var fan = run.fan(); var c = fan.context();
        var chart = new Chart(c.years().stream().map(p -> {
            var values = new TreeMap<String, java.math.BigDecimal>(); p.values().forEach((k, v) -> values.put(k.name(), v));
            return new ChartPoint(p.year(), p.primaryAge(), values, p.composition(), p.compositionComplete());
        }).toList(), c.claims().stream().map(v -> new Claim(v.year(), v.person(), v.age(), v.date())).toList(),
                c.rothPeriods().stream().map(v -> new Period(v.firstYear(), v.lastYear())).toList(),
                c.rmdPeriods().stream().map(v -> new Period(v.firstYear(), v.lastYear())).toList());
        return new Display(run.people(), fan.primaryName(), fan.spouseName(), fan.years().stream().map(y -> new DisplayYear(y.calendarYear(), y.percentiles().orElse(null),
                y.deterministic().orElse(null), y.population().map(MonteCarloSnapshotPresentationMapper::population).orElse(null))).toList(),
                chart, run.firstYear(), run.lastYear(), run.referenceIncomplete(), run.elapsedNanos(), run.planDetails());
    }
    private static MortalityYear population(MonteCarloMortalityAnnualResult a) {
        return new MortalityYear(a.year(), a.requestedWorldCount(), a.livingHouseholdCount(), a.completedLivingYearSampleCount(),
                a.livingFundingFailedByYearCount(), a.deceasedCount(), a.couplePopulation().orElse(null), a.investableAssets().orElse(null));
    }
    private static MonteCarloMortalityAnnualResult population(MortalityYear a) {
        return new MonteCarloMortalityAnnualResult(a.year(), a.requested(), a.living(), a.funded(), a.failed(), a.deceased(), Optional.ofNullable(a.couple()), Optional.ofNullable(a.assets()));
    }
}
