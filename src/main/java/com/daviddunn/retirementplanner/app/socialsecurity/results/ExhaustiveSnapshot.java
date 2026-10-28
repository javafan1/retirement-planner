package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit deterministic storage graph. References use original occurrence IDs, never row indices. */
public record ExhaustiveSnapshot(
        RetainedProjectionSnapshot baseline,
        IntegratedStrategyRankingMeasure rankingMeasure,
        List<Entry> entries,
        List<Integer> rankedOrders,
        Integer currentPlanRank,
        Duration runtime,
        int displayedGroupLimit,
        List<Group> displayedGroups,
        Report report,
        CrossReference socialSecurityReference,
        Instant completedAt,
        Selection selection) implements AnalysisPayload {

    public ExhaustiveSnapshot {
        Objects.requireNonNull(baseline); Objects.requireNonNull(rankingMeasure);
        entries = List.copyOf(entries); rankedOrders = List.copyOf(rankedOrders);
        Objects.requireNonNull(runtime); Objects.requireNonNull(report);
        displayedGroups = List.copyOf(displayedGroups);
        Objects.requireNonNull(socialSecurityReference); Objects.requireNonNull(completedAt);
        if (runtime.isNegative() || displayedGroupLimit < 1 || displayedGroups.size() > displayedGroupLimit
                || completedAt.isBefore(report.runStarted())) throw new IllegalArgumentException("Invalid frozen execution context.");
    }

    public static ExhaustiveSnapshot capture(IntegratedSocialSecurityCompleteStrategySearchResult result,
            Duration runtime, int groupLimit, List<Group> groups, Report report,
            CrossReference reference, Instant completed, Selection selection) {
        return new ExhaustiveSnapshot(RetainedProjectionSnapshot.capture(result.currentPlanBaseline()),
                result.rankingMeasure(), result.entries().stream().map(Entry::capture).toList(),
                result.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList(),
                result.currentPlanRank().isPresent() ? result.currentPlanRank().getAsInt() : null,
                runtime, groupLimit, groups, report, reference, completed, selection);
    }

    @Override public boolean successful() { return !rankedOrders.isEmpty(); }

    @Override public void validate(AnalysisKind kind) {
        if (kind != AnalysisKind.COUPLE_EXHAUSTIVE) throw new IllegalArgumentException("Payload kind mismatch.");
        restore();
    }

    /** Pure reconstruction: no ranking, metric difference, projection or benefit calculation. */
    public IntegratedSocialSecurityCompleteStrategySearchResult restore() {
        var restoredBaseline = baseline.restoreStrategyResult();
        if (!restoredBaseline.evaluatedStrategy().hasSpouse()) throw new IllegalArgumentException("Couple baseline required.");
        var byOrder = new LinkedHashMap<Integer, IntegratedSocialSecurityCompleteStrategySearchEntry>();
        var strategies = new HashSet<WeightedSnapshots.Strategy>();
        int previous = 0;
        for (var entry : entries) {
            if (entry.order() != previous + 1 || !strategies.add(entry.strategy()))
                throw new IllegalArgumentException("Duplicate or unordered candidate identity.");
            previous = entry.order();
            byOrder.put(entry.order(), entry.restore());
        }
        var ranked = new ArrayList<IntegratedSocialSecurityCompleteStrategySearchEntry>();
        var seen = new HashSet<Integer>();
        BigDecimal lastValue = null;
        int lastOrder = 0, rank = 0;
        for (int order : rankedOrders) {
            var entry = byOrder.get(order);
            if (entry == null || !entry.successful() || !seen.add(order))
                throw new IllegalArgumentException("Invalid successful ranking reference.");
            BigDecimal value = entry.metrics().orElseThrow().afterTaxEstate();
            if (lastValue == null || value.compareTo(lastValue) != 0) rank = ranked.size() + 1;
            if (lastValue != null && (value.compareTo(lastValue) > 0
                    || (value.compareTo(lastValue) == 0 && order < lastOrder)))
                throw new IllegalArgumentException("Invalid stored ranking order.");
            if (entry.afterTaxEstateRank().orElseThrow() != rank)
                throw new IllegalArgumentException("Invalid stored competition rank.");
            ranked.add(entry); lastValue = value; lastOrder = order;
        }
        if (seen.size() != byOrder.values().stream().filter(e -> e.successful()).count())
            throw new IllegalArgumentException("Ranking must reference every successful candidate exactly once.");
        if (currentPlanRank != null && (currentPlanRank < 1 || currentPlanRank > ranked.size() + 1))
            throw new IllegalArgumentException("Invalid current-plan rank.");
        var groupsSeen = new HashSet<Integer>();
        for (var group : displayedGroups) {
            if (!seen.contains(group.representativeOrder()) || !groupsSeen.add(group.representativeOrder())
                    || group.equivalentStrategyCount() < 1 || group.equivalentStrategyCount() > ranked.size())
                throw new IllegalArgumentException("Invalid displayed group reference.");
            var key = financialKey(byOrder.get(group.representativeOrder()).metrics().orElseThrow());
            if (ranked.stream().filter(e -> key.equals(financialKey(e.metrics().orElseThrow()))).count()
                    != group.equivalentStrategyCount()) throw new IllegalArgumentException("Invalid frozen group count.");
        }
        if (selection != null) selection.validate(byOrder);
        socialSecurityReference.validate(byOrder);
        return new IntegratedSocialSecurityCompleteStrategySearchResult(restoredBaseline, rankingMeasure,
                List.copyOf(byOrder.values()), List.copyOf(ranked), optional(currentPlanRank));
    }

    private static OptionalInt optional(Integer value) { return value == null ? OptionalInt.empty() : OptionalInt.of(value); }
    private static List<BigDecimal> financialKey(ProjectionMetrics m) {
        return List.of(m.afterTaxEstate(), m.endingInvestableAssets(), m.totalTaxes(),
                m.lifetimePortfolioWithdrawals(), m.lifetimeHouseholdSocialSecurity());
    }

    public record Entry(int order, WeightedSnapshots.Strategy strategy, Integer rank,
            ProjectionMetrics metrics, ProjectionMetrics differences,
            RetainedProjectionSnapshot detail, WeightedSnapshots.Failure failure) {
        public Entry { Objects.requireNonNull(strategy); }
        static Entry capture(IntegratedSocialSecurityCompleteStrategySearchEntry e) {
            return new Entry(e.generationOrder(), WeightedSnapshots.Strategy.from(e.strategy()),
                    e.afterTaxEstateRank().isPresent() ? e.afterTaxEstateRank().getAsInt() : null,
                    e.metrics().orElse(null), e.differencesFromCurrentPlan().orElse(null),
                    e.retainedDetail().map(RetainedProjectionSnapshot::capture).orElse(null),
                    e.failure().map(f -> new WeightedSnapshots.Failure(f.message(), f.category())).orElse(null));
        }
        IntegratedSocialSecurityCompleteStrategySearchEntry restore() {
            return restore(true);
        }
        IntegratedSocialSecurityCompleteStrategySearchEntry restore(boolean couple) {
            var election = strategy.restore();
            if (election.hasSpouse() != couple || (rank != null && rank < 1)) throw new IllegalArgumentException("Invalid entry household or rank.");
            var restoredDetail = detail == null ? null : detail.restoreStrategyResult();
            if (detail != null && (!strategy.equals(detail.strategy()) || !Objects.equals(metrics, detail.metrics())))
                throw new IllegalArgumentException("Retained detail does not belong to its candidate.");
            return new IntegratedSocialSecurityCompleteStrategySearchEntry(order, election, optional(rank),
                    Optional.ofNullable(metrics), Optional.ofNullable(differences), Optional.ofNullable(restoredDetail),
                    Optional.ofNullable(failure).map(f -> new IntegratedSocialSecurityStrategyEvaluationFailure(election, f.message(), f.category())));
        }
    }

    public record Group(int representativeOrder, int equivalentStrategyCount) { }

    /** These are the original report's complete assumptions, not text regenerated from a live plan. */
    public record Report(String household, String primary, String spouse, Instant runStarted,
            List<Section> assumptions, String baseline) {
        public Report {
            Objects.requireNonNull(household); Objects.requireNonNull(primary); Objects.requireNonNull(spouse);
            Objects.requireNonNull(runStarted); Objects.requireNonNull(baseline); assumptions = List.copyOf(assumptions);
        }
    }

    public enum ReferenceStatus { AVAILABLE, NO_CURRENT_SOCIAL_SECURITY_RESULT, OUTSIDE_SEARCH_UNIVERSE }

    /** Frozen exact numbers and source provenance; unavailable-at-completion is an explicit state. */
    public record CrossReference(ReferenceStatus status, String explanation,
            UUID sourceRunId, String sourceFingerprint, Long sourceRevision,
            WeightedSnapshots.Strategy recommendedStrategy, BigDecimal expectedPresentValue,
            Integer matchingOrder, BigDecimal estate, Integer rank, BigDecimal gapFromHighest) {
        public CrossReference {
            Objects.requireNonNull(status); Objects.requireNonNull(explanation);
            if (sourceFingerprint != null && !sourceFingerprint.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid SS source fingerprint.");
            if (sourceRevision != null && sourceRevision < 0) throw new IllegalArgumentException("Invalid source revision.");
        }
        void validate(Map<Integer, IntegratedSocialSecurityCompleteStrategySearchEntry> entries) {
            if (status == ReferenceStatus.NO_CURRENT_SOCIAL_SECURITY_RESULT) {
                if (recommendedStrategy != null || expectedPresentValue != null || matchingOrder != null
                        || estate != null || rank != null || gapFromHighest != null
                        || sourceRunId != null || sourceFingerprint != null || sourceRevision != null)
                    throw new IllegalArgumentException("Unavailable SS reference cannot contain a recommendation.");
                return;
            }
            Objects.requireNonNull(recommendedStrategy); recommendedStrategy.restore();
            Objects.requireNonNull(expectedPresentValue);
            if (sourceRunId == null && sourceFingerprint == null && sourceRevision == null)
                throw new IllegalArgumentException("SS reference source provenance is required.");
            if (status == ReferenceStatus.OUTSIDE_SEARCH_UNIVERSE) {
                if (matchingOrder != null || estate != null || rank != null || gapFromHighest != null)
                    throw new IllegalArgumentException("Unavailable integrated comparison cannot contain integrated values.");
                return;
            }
            var e = entries.get(matchingOrder);
            if (e == null || !e.successful() || !recommendedStrategy.equals(WeightedSnapshots.Strategy.from(e.strategy()))
                    || !Objects.equals(estate, e.metrics().orElseThrow().afterTaxEstate())
                    || !Objects.equals(rank, e.afterTaxEstateRank().orElseThrow()) || gapFromHighest == null)
                throw new IllegalArgumentException("Invalid frozen SS comparison reference.");
            var highest = entries.values().stream().filter(candidate -> candidate.successful()
                    && candidate.afterTaxEstateRank().orElseThrow() == 1).findFirst().orElseThrow();
            if (estate.subtract(highest.metrics().orElseThrow().afterTaxEstate()).compareTo(gapFromHighest) != 0)
                throw new IllegalArgumentException("Invalid frozen SS comparison gap.");
        }
    }

    public record Selection(Integer occurrence, WeightedSnapshots.Strategy strategy, String metric, String tab) {
        void validate(Map<Integer, IntegratedSocialSecurityCompleteStrategySearchEntry> entries) {
            if (!Set.of("PERCENT_OF_OPTIMAL", "FUTURE_DOLLAR_ESTATE", "DIFFERENCE_FROM_OPTIMAL", "DIFFERENCE_FROM_CURRENT")
                    .contains(metric) || !Set.of("ranked", "heat-map").contains(tab))
                throw new IllegalArgumentException("Unsupported deterministic selection preference.");
            if ((occurrence == null) != (strategy == null)) throw new IllegalArgumentException("Incomplete selected strategy identity.");
            if (occurrence != null) {
                var e = entries.get(occurrence);
                if (e == null || !strategy.equals(WeightedSnapshots.Strategy.from(e.strategy())))
                    throw new IllegalArgumentException("Invalid selected strategy reference.");
            }
        }
    }
}
