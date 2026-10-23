package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import java.time.Instant;
import java.util.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot.*;

/** Pure mapping of completed evidence; this class never calls simulation, projection or percentile reducers. */
public final class MonteCarloSnapshotMapping {
    private MonteCarloSnapshotMapping() { }

    public static MonteCarloSnapshot fixed(MonteCarloAnalysisResult result, RetirementPlan frozenPlan,
            Display display, Instant started, Instant completed, String suppliedWorldFingerprint) {
        int first = frozenPlan.getPlanningAssumptions().getProjectionStartDate().getYear();
        var request = new Request(MonteCarloPlanDocument.capture(frozenPlan), null, "Analyzed Plan", null,
                frozenPlan.getPlanningAssumptions().getProjectionStartDate(),
                first + frozenPlan.getPlanningAssumptions().getProjectionLengthYears() - 1,
                fixedLife(frozenPlan), null);
        var ref = result.deterministicReference();
        var f = new Fixed(result.outcomes().stream().map(t -> new FixedTrial(t.scenarioIndex(), t.metrics().orElse(null),
                t.firstNonpositiveYearEnd().orElse(null), Failure.capture(t.fundingFailure().orElse(null)))).toList(),
                map(result.annualInvestableAssets(), v -> v.orElse(null)),
                new Distribution(result.endingInvestableAssets().orElse(null), result.endingNetWorth().orElse(null),
                        result.endingAfterTaxEstate().orElse(null), result.lifetimeTaxes().orElse(null)),
                History.capture(ref.annualInvestableAssets(), first, null), ref.metrics().orElse(null), Failure.capture(ref.fundingFailure().orElse(null)));
        return checked(new MonteCarloSnapshot(VERSION, Mode.FIXED, frozenPlan.getHousehold().hasSpouse(), Settings.capture(result.settings()),
                request, provenance(result.returnModel(), null, result.returnModel().equals("CALLER_SUPPLIED_PATHS")
                ? MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED : MonteCarloStrategyComparisonResult.WorldSource.GENERATED,
                suppliedWorldFingerprint), f, null, null, display, started, completed));
    }

    public static MonteCarloSnapshot mortality(MonteCarloMortalityAnalysisResult result, RetirementPlan frozenPlan,
            Display display, Instant started, Instant completed, String suppliedWorldFingerprint) {
        long observations = 0;
        for (var outcome : result.outcomes()) {
            observations += outcome.annualInvestableAssets().size();
            if (observations > MAX_ANNUAL_OBSERVATIONS) throw invalid("Expanded annual observation budget exceeded.");
        }
        if (result.request().hasSpouse() && !result.request().spouseBirthDate().equals(frozenPlan.getHousehold().getSpouse().getBirthDate()))
            throw invalid("Frozen spouse date differs from the completed request.");
        var request = new Request(MonteCarloPlanDocument.capture(frozenPlan), null, "Analyzed Plan", null,
                result.request().conditioningDate(), null, null, MortalityInputs.capture(result.request()));
        var summary = new MortalitySummary(map(result.annualResults(), MortalityYear::capture), (int) result.completedCount(),
                new Distribution(result.endingInvestableAssets().orElse(null), result.endingNetWorth().orElse(null),
                        result.afterTaxEstate().orElse(null), result.lifetimeTaxes().orElse(null)),
                result.earliestSuccessfulTerminalBalanceDate().orElse(null), result.latestSuccessfulTerminalBalanceDate().orElse(null),
                result.successfulTerminalYearCounts(), result.fundingFailureStatistics().orElse(null));
        int first = request.start().getYear();
        var outcomes = result.outcomes().stream().map(t -> new MortalityTrial(t.scenarioIndex(), Life.capture(t.lifetime()),
                new Outcome(History.capture(t.annualInvestableAssets(), first, null), t.terminal().orElse(null), Failure.capture(t.fundingFailure().orElse(null))))).toList();
        return checked(new MonteCarloSnapshot(VERSION, Mode.LONGEVITY, result.request().hasSpouse(), Settings.capture(result.request().settings()),
                request, provenance(result.returnModel(), result.mortalityModel(), result.returnModel().equals("CALLER_SUPPLIED_WORLDS")
                ? MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED : MonteCarloStrategyComparisonResult.WorldSource.GENERATED,
                suppliedWorldFingerprint), null, new Mortality(outcomes, summary), null, display, started, completed));
    }

    public static MonteCarloSnapshot comparison(MonteCarloStrategyComparisonResult result, Display display,
            Instant started, Instant completed, String suppliedWorldFingerprint) {
        long observations = 0;
        for (var outcome : result.outcomes()) {
            observations += (long) outcome.outcomeA().annualInvestableAssets().size() + outcome.outcomeB().annualInvestableAssets().size();
            if (observations > MAX_ANNUAL_OBSERVATIONS) throw invalid("Expanded annual observation budget exceeded.");
        }
        var r = result.request();
        boolean sampled = r.assumptions() instanceof MonteCarloStrategyComparisonRequest.Longevity;
        var f = r.assumptions() instanceof MonteCarloStrategyComparisonRequest.Fixed fixed ? fixed : null;
        var m = sampled ? ((MonteCarloStrategyComparisonRequest.Longevity) r.assumptions()).mortality() : null;
        var request = new Request(MonteCarloPlanDocument.capture(r.strategyA().copyForRun()), MonteCarloPlanDocument.capture(r.strategyB().copyForRun()),
                r.strategyA().label(), r.strategyB().label(), r.assumptions().start(), f == null ? null : f.endingYear(),
                f == null ? null : Life.capture(f.lifetime()), m == null ? null : MortalityInputs.capture(m));
        int first = request.start().getYear();
        var trials = result.outcomes().stream().map(t -> {
            var a = History.capture(t.outcomeA().annualInvestableAssets(), first, null);
            var b = History.capture(t.outcomeB().annualInvestableAssets(), first, a);
            return new PairTrial(t.scenarioIndex(), Life.capture(t.lifetime()),
                    new Outcome(a, t.outcomeA().terminal().orElse(null), Failure.capture(t.outcomeA().fundingFailure().orElse(null))),
                    new Outcome(b, t.outcomeB().terminal().orElse(null), Failure.capture(t.outcomeB().fundingFailure().orElse(null))));
        }).toList();
        var s = result.summary();
        var summary = new PairSummary(s.pairedStates(), s.strategyAFailureStatistics().orElse(null), s.strategyBFailureStatistics().orElse(null),
                Metric.capture(s.terminalInvestableAssetsDifference()), Metric.capture(s.terminalNetWorthDifference()),
                Metric.capture(s.terminalAfterTaxEstateDifference()), Metric.capture(s.lifetimeTaxesDifference()), map(s.annualResults(), PairYear::capture));
        boolean supplied = result.worldSource() == MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED;
        return checked(new MonteCarloSnapshot(VERSION, sampled ? Mode.PAIRED_LONGEVITY : Mode.PAIRED_FIXED, r.assumptions().hasSpouse(),
                Settings.capture(r.assumptions().settings()), request, provenance(supplied ? "CALLER_SUPPLIED_WORLDS" : MonteCarloScenarioGenerator.MODEL_VERSION,
                sampled ? (supplied ? "CALLER_SUPPLIED_WORLDS" : r.assumptions().hasSpouse() ? "HOUSEHOLD_MORTALITY_V1" : "PRIMARY_MORTALITY_V1") : null,
                result.worldSource(), suppliedWorldFingerprint), null, null, new Paired(trials, summary), display, started, completed));
    }

    public static MonteCarloAnalysisResult restoreFixed(MonteCarloSnapshot s) {
        s.validate(s.kind());
        return Objects.requireNonNull(s.fixed()).restore(s.settings().restore(), s.provenance().returns());
    }
    public static MonteCarloMortalityAnalysisResult restoreMortality(MonteCarloSnapshot s) {
        s.validate(s.kind());
        var request = s.request().mortality().restore(MonteCarloPlanDocument.read(s.request().planA()), s.settings());
        var m = Objects.requireNonNull(s.mortality()); var a = m.summary(); var d = a.terminal();
        var outcomes = m.outcomes().stream().map(t -> new MonteCarloMortalityAnalysisResult.WorldOutcome(t.index(), t.life().restore(),
                t.outcome().history().restore(null), Optional.ofNullable(t.outcome().terminal()), optionalFailure(t.outcome().failure()))).toList();
        var frozen = new MonteCarloMortalityAccumulator.Aggregates(map(a.annual(), MortalityYear::restore), a.completed(),
                Optional.ofNullable(d.investable()), Optional.ofNullable(d.netWorth()), Optional.ofNullable(d.estate()), Optional.ofNullable(d.taxes()),
                Optional.ofNullable(a.earliest()), Optional.ofNullable(a.latest()), a.terminalYears(), Optional.ofNullable(a.failures()));
        return new MonteCarloMortalityAnalysisResult(request, s.provenance().returns(), s.provenance().mortality(), outcomes, frozen);
    }
    public static MonteCarloStrategyComparisonResult restoreComparison(MonteCarloSnapshot s) {
        s.validate(s.kind());
        var r = s.request();
        var planA = MonteCarloPlanDocument.read(r.planA());
        var planB = MonteCarloPlanDocument.read(r.planB());
        MonteCarloStrategyComparisonRequest.Assumptions assumptions = s.mode() == Mode.PAIRED_FIXED
                ? new MonteCarloStrategyComparisonRequest.Fixed(s.settings().restore(), r.start(), r.endingYear(), r.fixedLife().restore())
                : new MonteCarloStrategyComparisonRequest.Longevity(r.mortality().restore(planA, s.settings()));
        var request = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate(r.labelA(), planA), new MonteCarloStrategyCandidate(r.labelB(), planB), assumptions);
        var p = Objects.requireNonNull(s.paired());
        var outcomes = p.outcomes().stream().map(t -> new MonteCarloPairedOutcome(t.index(), t.life().restore(),
                t.a().restore(null), t.b().restore(t.a().history()))).toList();
        return new MonteCarloStrategyComparisonResult(request, s.provenance().source(), outcomes, p.summary().restore());
    }

    private static Provenance provenance(String returns, String mortality, MonteCarloStrategyComparisonResult.WorldSource source, String supplied) {
        return new Provenance(returns, mortality, returns.equals("CALLER_SUPPLIED_WORLDS") ? "CALLER_SUPPLIED_WORLDS" : MonteCarloInflationGenerator.MODEL_VERSION,
                source == MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED ? "CALLER_SUPPLIED/see-source-fingerprint" : "RPMC-SHA256-v1/legacy-market-v1",
                AGGREGATION, source, supplied);
    }
    private static Life fixedLife(RetirementPlan plan) {
        var d = plan.getPlanningAssumptions().getDeathScenarioAssumptions();
        return new Life(d.getDeathScenario() == DeathScenario.PRIMARY_DIES ? d.getDeathYear() : null, plan.getHousehold().hasSpouse(),
                d.getDeathScenario() == DeathScenario.SPOUSE_DIES ? d.getDeathYear() : null);
    }
    private static MonteCarloSnapshot checked(MonteCarloSnapshot s) { s.validate(s.kind()); return s; }
}
