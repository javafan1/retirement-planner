package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit v1 graph. Shared leaf records contain only immutable scalar values, never services. */
public final class WeightedSnapshots {
    private WeightedSnapshots() { }

    public record Payload(Couple couple, Individual individual) implements AnalysisPayload {
        public void validate(AnalysisKind kind) {
            if ((couple == null) == (individual == null)) throw new IllegalArgumentException("Exactly one payload family is required.");
            if (couple != null) {
                if (kind != AnalysisKind.COUPLE_WEIGHTED) throw new IllegalArgumentException("Payload kind mismatch.");
                couple.restore();
            } else {
                if (kind != AnalysisKind.INDIVIDUAL_WEIGHTED && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
                    throw new IllegalArgumentException("Codec is not implemented for this kind.");
                var result = individual.restore();
                if ((kind == AnalysisKind.INDIVIDUAL_WEIGHTED) != (result.mode() == SinglePersonMortalityAnalysis.Mode.INTEGRATED))
                    throw new IllegalArgumentException("Individual mode mismatch.");
            }
        }
        public boolean successful() {
            return couple != null ? !couple.rankedOrders().isEmpty() : individual != null && individual.entries().size() == 9;
        }
    }

    public record Strategy(int primaryAge, Integer spouseAge, LocalDate primaryDate, LocalDate spouseDate,
            SocialSecuritySurvivorClaimingCandidate primarySurvivor,
            SocialSecuritySurvivorClaimingCandidate spouseSurvivor) {
        public static Strategy from(SocialSecurityHouseholdClaimingStrategy s) {
            return new Strategy(s.primaryRetirementAge(), s.spouseRetirementAge(), s.primaryRetirementClaimDate(),
                    s.spouseRetirementClaimDate(), s.primarySurvivorElection(), s.spouseSurvivorElection());
        }
        public SocialSecurityHouseholdClaimingStrategy restore() {
            return new SocialSecurityHouseholdClaimingStrategy(primaryAge, spouseAge, primaryDate, spouseDate, primarySurvivor, spouseSurvivor);
        }
    }

    public record Failure(String message, IntegratedSocialSecurityStrategyEvaluationFailure.Category category) { }
    public record Entry(int order, Strategy strategy, LongevityWeightedStrategyAggregate aggregate,
            Failure failure, List<LongevityWeightedIntegratedScenarioOutcome> details,
            Integer rank, BigDecimal pvDifference, BigDecimal nominalDifference) {
        public Entry { Objects.requireNonNull(strategy); if (details != null) details = List.copyOf(details); }
        static Entry from(LongevityWeightedIntegratedStrategyComparisonEntry e) {
            return new Entry(e.inputOrder(), Strategy.from(e.strategy()), e.aggregate().orElse(null),
                    e.failure().map(f -> new Failure(f.message(), f.category())).orElse(null),
                    e.scenarioDetails().orElse(null), e.rank().isPresent() ? e.rank().getAsInt() : null,
                    e.pvDifferenceFromBaseline().orElse(null), e.nominalDifferenceFromBaseline().orElse(null));
        }
        LongevityWeightedIntegratedStrategyComparisonEntry restore() {
            var s = strategy.restore();
            return new LongevityWeightedIntegratedStrategyComparisonEntry(order, s, Optional.ofNullable(aggregate),
                    Optional.ofNullable(failure).map(f -> new IntegratedSocialSecurityStrategyEvaluationFailure(s, f.message(), f.category())),
                    Optional.ofNullable(details), rank == null ? OptionalInt.empty() : OptionalInt.of(rank),
                    Optional.ofNullable(pvDifference), Optional.ofNullable(nominalDifference));
        }
    }

    public record Equivalence(List<List<Integer>> groups, List<Integer> representatives,
            long scheduleRequests, long scheduleCalculations, Duration elapsed,
            LongevityEquivalencePlanningWork coverage) {
        public Equivalence { groups = groups.stream().map(List::copyOf).toList(); representatives = List.copyOf(representatives); }
        static Equivalence from(LongevityWeightedStrategyEquivalencePlanner.Plan p) {
            return new Equivalence(p.groups(), p.representativeOrders(), p.scheduleRequests(), p.scheduleCalculations(),
                    p.elapsedTime(), p.coverageWork().orElse(null));
        }
        LongevityWeightedStrategyEquivalencePlanner.Plan restore() {
            return new LongevityWeightedStrategyEquivalencePlanner.Plan(groups, representatives, scheduleRequests,
                    scheduleCalculations, elapsed, Optional.ofNullable(coverage));
        }
    }

    public record Couple(LongevityWeightedComparisonObjective objective,
            LongevityWeightedIntegratedStrategyComparisonResult.Metadata metadata,
            List<Entry> entries, List<Integer> rankedOrders, Entry baseline,
            LongevityWeightedIntegratedStrategyComparisonResult.Work work, Duration elapsed,
            LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus status,
            Equivalence equivalence, long evaluationsAvoided) {
        public Couple { entries = List.copyOf(entries); rankedOrders = List.copyOf(rankedOrders); }
        public static Couple from(LongevityWeightedIntegratedStrategyComparisonResult r) {
            return new Couple(r.objective(), r.metadata(), r.orderedEntries().stream().map(Entry::from).toList(),
                    r.rankedSuccessfulEntries().stream().map(e -> e.inputOrder()).toList(),
                    r.baseline().map(Entry::from).orElse(null), r.work(), r.elapsedTime(), r.status(),
                    r.equivalencePlan().map(Equivalence::from).orElse(null), r.evaluationsAvoided());
        }
        public LongevityWeightedIntegratedStrategyComparisonResult restore() {
            Objects.requireNonNull(work); Objects.requireNonNull(work.continuation());
            Objects.requireNonNull(metadata); Objects.requireNonNull(elapsed); Objects.requireNonNull(status);
            var restored = entries.stream().map(Entry::restore).toList();
            Map<Integer, LongevityWeightedIntegratedStrategyComparisonEntry> byId = new LinkedHashMap<>();
            for (int i = 0; i < restored.size(); i++) {
                var e = restored.get(i);
                if (e.inputOrder() != i + 1 || !e.strategy().hasSpouse()) throw new IllegalArgumentException("Invalid candidate order/shape.");
                byId.put(e.inputOrder(), e);
            }
            if (new HashSet<>(rankedOrders).size() != rankedOrders.size()
                    || rankedOrders.size() != restored.stream().filter(e -> e.successful()).count())
                throw new IllegalArgumentException("Ranking must reference all successful occurrences once.");
            var ranked = rankedOrders.stream().map(id -> Objects.requireNonNull(byId.get(id))).toList();
            BigDecimal previous = null;
            int competitionRank = 0;
            for (int i = 0; i < ranked.size(); i++) {
                var e = ranked.get(i);
                var value = e.aggregate().orElseThrow().expectedPvAfterTaxEstate();
                if (previous != null && value.compareTo(previous) > 0) throw new IllegalArgumentException("Unordered ranking.");
                if (previous != null && value.compareTo(previous) == 0 && ranked.get(i - 1).inputOrder() > e.inputOrder())
                    throw new IllegalArgumentException("Tie order must preserve original occurrences.");
                if (previous == null || value.compareTo(previous) != 0) competitionRank = i + 1;
                if (e.rank().orElse(0) != competitionRank) throw new IllegalArgumentException("Invalid competition rank.");
                previous = value;
            }
            if (baseline != null && baseline.order() != 0) throw new IllegalArgumentException("Baseline must have order zero.");
            if ((restored.stream().anyMatch(e -> !e.successful()) || baseline != null && baseline.failure() != null)
                    && status != LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED_WITH_FAILURES)
                throw new IllegalArgumentException("Failure metadata is inconsistent.");
            if (equivalence != null) {
                var members = equivalence.groups().stream().flatMap(List::stream).toList();
                if (members.size() != entries.size() || new HashSet<>(members).size() != entries.size()
                        || !byId.keySet().equals(new HashSet<>(members))) throw new IllegalArgumentException("Invalid equivalence coverage.");
                if (equivalence.representatives().size() != entries.size()) throw new IllegalArgumentException("Invalid representative count.");
                for (var group : equivalence.groups()) for (int order : group) {
                    if (!equivalence.representatives().get(order - 1).equals(group.getFirst()))
                        throw new IllegalArgumentException("Invalid representative identity.");
                }
            }
            return new LongevityWeightedIntegratedStrategyComparisonResult(objective, metadata, restored, ranked,
                    Optional.ofNullable(baseline).map(Entry::restore), work, elapsed, status,
                    Optional.ofNullable(equivalence).map(Equivalence::restore), evaluationsAvoided);
        }
    }

    public record Mortality(List<SocialSecurityMortalityProbability> probabilities,
            SocialSecurityMortalityTableMetadata table, SocialSecurityMortalityDistributionRequest request,
            int firstAge, int terminalAge, SocialSecurityMortalityPartialYearConvention convention) {
        public Mortality { probabilities = List.copyOf(probabilities); }
        static Mortality from(IndividualLongevityScenarios m) {
            var d = m.individual();
            return new Mortality(d.distribution().probabilities(), d.tableMetadata(), d.request(),
                    d.firstModeledAttainedAge(), d.terminalDeathAge(), d.partialYearConvention());
        }
        IndividualLongevityScenarios restore() {
            return new IndividualLongevityScenarios(new SocialSecurityMortalityDistributionResult(
                    new SocialSecurityMortalityDistribution(probabilities), table, request, firstAge, terminalAge, convention));
        }
    }

    public record IndividualEntry(Strategy strategy, BigDecimal pv, BigDecimal nominal, BigDecimal assets,
            List<SinglePersonMortalityAnalysis.Outcome> outcomes) {
        public IndividualEntry { outcomes = List.copyOf(outcomes); }
    }
    public record Individual(SinglePersonMortalityAnalysis.Mode mode, String name, int currentAge,
            Mortality mortality, LocalDate valuationDate, BigDecimal discountRate, BigDecimal colaRate,
            BigDecimal inflationRate, List<IndividualEntry> entries, int projectionCount) {
        public Individual { entries = List.copyOf(entries); }
        public static Individual from(SinglePersonMortalityAnalysis.Result r) {
            return new Individual(r.mode(), r.primaryName(), r.currentAge(), Mortality.from(r.mortality()),
                    r.valuationDate(), r.discountRate(), r.colaRate(), r.inflationRate(), r.entries().stream().map(e ->
                    new IndividualEntry(Strategy.from(e.strategy()), e.expectedPresentValue(), e.expectedNominal(),
                            e.expectedInvestableAssets(), e.outcomes())).toList(), r.projectionCount());
        }
        public SinglePersonMortalityAnalysis.Result restore() {
            var frozenMortality = mortality.restore();
            var scenarios = frozenMortality.scenarios().stream().filter(s -> s.probability().signum() > 0).toList();
            var restored = new ArrayList<SinglePersonMortalityAnalysis.Entry>();
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                var s = e.strategy().restore();
                if (s.hasSpouse() || s.primaryRetirementAge() != i + 62) throw new IllegalArgumentException("Invalid individual election order.");
                Objects.requireNonNull(e.pv()); Objects.requireNonNull(e.nominal());
                if (mode == SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY && (e.assets() != null || !e.outcomes().isEmpty()))
                    throw new IllegalArgumentException("SS-only result cannot contain integrated outcomes.");
                if (mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED) Objects.requireNonNull(e.assets());
                if (mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED) {
                    if (e.outcomes().size() != scenarios.size()) throw new IllegalArgumentException("Missing retained mortality outcomes.");
                    for (int j = 0; j < scenarios.size(); j++) {
                        var o = e.outcomes().get(j); var expected = scenarios.get(j);
                        if (o.deathYear() != expected.deathDate().getYear() || !o.probability().equals(expected.probability()))
                            throw new IllegalArgumentException("Mortality outcome ordering/probability mismatch.");
                        Objects.requireNonNull(o.nominalEstate()); Objects.requireNonNull(o.investableAssets()); Objects.requireNonNull(o.presentValue());
                    }
                }
                restored.add(new SinglePersonMortalityAnalysis.Entry(s, e.pv(), e.nominal(), e.assets(), e.outcomes()));
            }
            var result = new SinglePersonMortalityAnalysis.Result(Objects.requireNonNull(mode), Objects.requireNonNull(name),
                    currentAge, frozenMortality, Objects.requireNonNull(valuationDate), Objects.requireNonNull(discountRate),
                    Objects.requireNonNull(colaRate), Objects.requireNonNull(inflationRate), restored, projectionCount);
            result.current();
            return result;
        }
    }
}
