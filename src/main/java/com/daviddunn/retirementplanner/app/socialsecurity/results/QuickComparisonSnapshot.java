package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;
import java.util.*;

/** Complete baseline/candidate graphs in original caller order. No new optimization or ranking. */
public record QuickComparisonSnapshot(int version, RetainedProjectionSnapshot baseline, int supplied,
        List<Entry> entries, List<Integer> socialSecurityRanks, List<Candidate> candidates, String prerequisiteFingerprint, UUID prerequisiteRun,
        int requested, String baselineDescription) implements AnalysisPayload {
    public QuickComparisonSnapshot {
        if (version != 1) throw new IllegalArgumentException("Unsupported Quick result version.");
        Objects.requireNonNull(baseline); entries = List.copyOf(entries); socialSecurityRanks = List.copyOf(socialSecurityRanks); candidates = List.copyOf(candidates);
        Objects.requireNonNull(prerequisiteRun);
        Objects.requireNonNull(baselineDescription);
        if (prerequisiteFingerprint == null || !prerequisiteFingerprint.matches("[0-9a-f]{64}")
                || requested < 1 || requested > 20 || supplied < entries.size() || supplied > 100_000
                || candidates.size() != supplied || entries.size() != socialSecurityRanks.size() || socialSecurityRanks.stream().anyMatch(r -> r < 1))
            throw new IllegalArgumentException("Invalid Quick provenance or bounds.");
    }
    public static QuickComparisonSnapshot capture(IntegratedSocialSecurityStrategyComparisonResult result, List<Integer> ranks,
            List<Candidate> candidates, String prerequisite, UUID run, int requested, String baselineDescription) {
        return new QuickComparisonSnapshot(1, RetainedProjectionSnapshot.capture(result.currentPlanBaseline()), result.suppliedCandidateCount(),
                result.entries().stream().map(e -> new Entry(e.callerOrder(), WeightedSnapshots.Strategy.from(e.strategy()),
                        e.socialSecurityOnlyExpectedValue().orElse(null), e.integratedResult().map(RetainedProjectionSnapshot::capture).orElse(null),
                        e.differencesFromCurrentPlan().orElse(null), e.failure().map(f -> new WeightedSnapshots.Failure(f.message(), f.category())).orElse(null))).toList(),
                ranks, candidates, prerequisite, run, requested, baselineDescription);
    }
    @Override public boolean successful() { return entries.stream().anyMatch(e -> e.detail() != null); }
    @Override public void validate(AnalysisKind kind) {
        if (kind != AnalysisKind.COUPLE_QUICK) throw new IllegalArgumentException("Quick result kind mismatch.");
        restore();
    }
    public IntegratedSocialSecurityStrategyComparisonResult restore() {
        var base = baseline.restoreStrategyResult();
        if (!base.evaluatedStrategy().hasSpouse()) throw new IllegalArgumentException("Quick requires a couple.");
        var seen = new HashSet<WeightedSnapshots.Strategy>(); int previous = 0;
        var values = new ArrayList<IntegratedSocialSecurityStrategyComparisonEntry>();
        var firstSeen = new LinkedHashMap<WeightedSnapshots.Strategy, Integer>();
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            if (!candidate.strategy().restore().hasSpouse()) throw new IllegalArgumentException("Quick candidate household mismatch.");
            firstSeen.putIfAbsent(candidate.strategy(), i + 1);
        }
        if (firstSeen.size() != entries.size()) throw new IllegalArgumentException("Quick unique candidate count mismatch.");
        for (var e : entries) {
            if (e.order() <= previous || e.order() > supplied || !seen.add(e.strategy()) || !e.strategy().restore().hasSpouse())
                throw new IllegalArgumentException("Invalid Quick candidate identity.");
            if (!Objects.equals(firstSeen.get(e.strategy()), e.order())) throw new IllegalArgumentException("Quick first-seen order mismatch.");
            previous = e.order();
            var candidate = candidates.get(e.order()-1);
            if (!candidate.strategy().equals(e.strategy()) || !Objects.equals(candidate.expected(), e.expected())
                    || candidate.rank() != socialSecurityRanks.get(values.size())) throw new IllegalArgumentException("Quick candidate provenance mismatch.");
            var value = e.detail() == null ? null : e.detail().restoreStrategyResult();
            if (value != null && !value.evaluatedStrategy().equals(e.strategy().restore())) throw new IllegalArgumentException("Quick detail mismatch.");
            values.add(new IntegratedSocialSecurityStrategyComparisonEntry(e.order(), e.strategy().restore(), Optional.ofNullable(e.expected()),
                    Optional.ofNullable(value), Optional.ofNullable(e.differences()), Optional.ofNullable(e.failure())
                            .map(f -> new IntegratedSocialSecurityStrategyEvaluationFailure(e.strategy().restore(), f.message(), f.category()))));
        }
        return new IntegratedSocialSecurityStrategyComparisonResult(base, supplied, values);
    }
    public record Entry(int order, WeightedSnapshots.Strategy strategy, SocialSecurityMortalityWeightedStrategyValue expected,
            RetainedProjectionSnapshot detail, ProjectionMetrics differences, WeightedSnapshots.Failure failure) {
        public Entry { Objects.requireNonNull(strategy); }
    }
    public record Candidate(int rank, WeightedSnapshots.Strategy strategy, SocialSecurityMortalityWeightedStrategyValue expected) {
        public Candidate { if (rank < 1) throw new IllegalArgumentException("Invalid SS rank."); Objects.requireNonNull(strategy); Objects.requireNonNull(expected); }
    }
}
