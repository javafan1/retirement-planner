package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenAnalysisResult;
import java.time.Year;
import java.util.*;

/** Original primary-only search and its completed comparisons. Never calculates during restoration. */
public record IndividualDeterministicSnapshot(int version, RetainedProjectionSnapshot baseline,
        IntegratedStrategyRankingMeasure measure, List<ExhaustiveSnapshot.Entry> entries, List<Integer> ranked,
        Integer currentRank, Map<Integer, BreakEvenAnalysisResult> breakEven, String primary,
        int startYear, int endYear, Integer deathYear) implements AnalysisPayload {
    public IndividualDeterministicSnapshot {
        if (version != 1) throw new IllegalArgumentException("Unsupported individual result version.");
        Objects.requireNonNull(baseline); Objects.requireNonNull(measure); Objects.requireNonNull(primary);
        entries = List.copyOf(entries); ranked = List.copyOf(ranked); breakEven = Map.copyOf(breakEven);
        if (breakEven.size() > 9 || entries.size() > 9 || entries.isEmpty() || startYear > endYear)
            throw new IllegalArgumentException("Invalid individual result bounds.");
    }
    public static IndividualDeterministicSnapshot capture(SinglePersonIntegratedAnalysis result) {
        var search = result.result();
        return new IndividualDeterministicSnapshot(1, RetainedProjectionSnapshot.capture(search.currentPlanBaseline()),
                search.rankingMeasure(), search.entries().stream().map(ExhaustiveSnapshot.Entry::capture).toList(),
                search.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList(),
                search.currentPlanRank().isPresent() ? search.currentPlanRank().getAsInt() : null,
                result.breakEvenByClaimingAge(), result.primaryName(), result.startYear(), result.endYear(),
                result.primaryDeathYear().map(Year::getValue).orElse(null));
    }
    @Override public boolean successful() { return !ranked.isEmpty(); }
    @Override public void validate(AnalysisKind kind) {
        if (kind != AnalysisKind.INDIVIDUAL_DETERMINISTIC) throw new IllegalArgumentException("Individual result kind mismatch.");
        restore();
    }
    public SinglePersonIntegratedAnalysis restore() {
        var base = baseline.restoreStrategyResult();
        if (base.evaluatedStrategy().hasSpouse() || base.projection().getStartYear() != startYear || base.projection().getEndYear() != endYear)
            throw new IllegalArgumentException("Invalid individual baseline context.");
        var ordered = new LinkedHashMap<Integer, IntegratedSocialSecurityCompleteStrategySearchEntry>();
        var strategies = new HashSet<WeightedSnapshots.Strategy>();
        for (var entry : entries) {
            if (entry.order() != ordered.size()+1 || !strategies.add(entry.strategy())) throw new IllegalArgumentException("Invalid individual candidate identity.");
            ordered.put(entry.order(), entry.restore(false));
        }
        var rankedEntries = new ArrayList<IntegratedSocialSecurityCompleteStrategySearchEntry>();
        var seen = new HashSet<Integer>(); java.math.BigDecimal previous = null; int rank = 0; int lastOrder = 0;
        for (int id : ranked) {
            var entry = ordered.get(id);
            if (entry == null || !entry.successful() || !seen.add(id)) throw new IllegalArgumentException("Invalid individual ranking reference.");
            var value = entry.metrics().orElseThrow().afterTaxEstate();
            if (previous == null || value.compareTo(previous) != 0) rank = rankedEntries.size()+1;
            if (entry.afterTaxEstateRank().orElseThrow() != rank || previous != null && (value.compareTo(previous)>0 || value.compareTo(previous)==0 && id<lastOrder))
                throw new IllegalArgumentException("Invalid stored individual ranking.");
            rankedEntries.add(entry); previous=value; lastOrder=id;
        }
        if (seen.size() != ordered.values().stream().filter(e -> e.successful()).count()
                || currentRank != null && (currentRank<1 || currentRank>ranked.size()+1)
                || breakEven.keySet().stream().anyMatch(age -> ordered.values().stream().noneMatch(e -> e.strategy().primaryRetirementAge()==age)))
            throw new IllegalArgumentException("Invalid individual comparison references.");
        var result = new IntegratedSocialSecurityCompleteStrategySearchResult(base, measure, List.copyOf(ordered.values()), rankedEntries,
                currentRank == null ? OptionalInt.empty() : OptionalInt.of(currentRank));
        return new SinglePersonIntegratedAnalysis(result, breakEven, primary, startYear, endYear, Optional.ofNullable(deathYear).map(Year::of));
    }
}