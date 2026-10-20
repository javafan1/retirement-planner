package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Version-one SS graph: request/distributions and cells occur once; ties use checked local references. */
public record CoupleSocialSecuritySnapshot(Request request, FrozenContext frozen,
        List<SocialSecurityJointMortalityScenario> scenarios,
        List<SocialSecurityMortalityWeightedClaimingGridCell> gridCells,
        Ranking nominal, Ranking real, Ranking presentValue, int gridEvaluationCount,
        List<Integer> retainedCells, List<SocialSecuritySurvivorClaimingCandidate> primarySurvivors,
        List<SocialSecuritySurvivorClaimingCandidate> spouseSurvivors,
        List<SocialSecuritySurvivorClaimingOptimizationCell> strategies,
        BigDecimal highestPresentValue, List<Integer> highestStrategies,
        int strategyCount, int evaluationCount) implements AnalysisPayload {

    public CoupleSocialSecuritySnapshot {
        Objects.requireNonNull(request); Objects.requireNonNull(frozen);
        scenarios = List.copyOf(scenarios); gridCells = List.copyOf(gridCells);
        retainedCells = List.copyOf(retainedCells); primarySurvivors = List.copyOf(primarySurvivors);
        spouseSurvivors = List.copyOf(spouseSurvivors); strategies = List.copyOf(strategies);
        highestStrategies = List.copyOf(highestStrategies); Objects.requireNonNull(highestPresentValue);
    }
    public record Request(SocialSecurityStrategyRequest base, List<Integer> primaryAges, List<Integer> spouseAges,
            List<SocialSecurityMortalityProbability> primaryMortality, List<SocialSecurityMortalityProbability> spouseMortality,
            LocalDate conditioning, LocalDate valuation, BigDecimal discount, SocialSecuritySurvivorOptimizationSettings settings) {
        public Request {
            primaryAges = List.copyOf(primaryAges); spouseAges = List.copyOf(spouseAges);
            primaryMortality = List.copyOf(primaryMortality); spouseMortality = List.copyOf(spouseMortality);
            Objects.requireNonNull(settings);
        }
        public static Request from(SocialSecuritySurvivorClaimingOptimizationRequest r) {
            var g = r.retirementGridRequest();
            return new Request(g.baseStrategy(), g.primaryClaimAges(), g.spouseClaimAges(),
                    g.primaryMortality().probabilities(), g.spouseMortality().probabilities(),
                    g.mortalityBaseDate(), g.presentValueBaseDate(), g.realDiscountRate(), r.settings());
        }
        public SocialSecuritySurvivorClaimingOptimizationRequest restore() {
            return new SocialSecuritySurvivorClaimingOptimizationRequest(new SocialSecurityMortalityWeightedClaimingGridRequest(
                    base, primaryAges, spouseAges, new SocialSecurityMortalityDistribution(primaryMortality),
                    new SocialSecurityMortalityDistribution(spouseMortality), conditioning, valuation, discount), settings);
        }
    }
    public record FrozenContext(String primaryName, String spouseName, AnalyzerLongevityAssumptions longevity,
            BigDecimal primaryBenefit, BigDecimal spouseBenefit, int primaryValuationYear, int spouseValuationYear,
            LocalDate primaryClaimDate, LocalDate spouseClaimDate) {
        public FrozenContext {
            Objects.requireNonNull(primaryName); Objects.requireNonNull(spouseName); Objects.requireNonNull(longevity);
            Objects.requireNonNull(primaryBenefit); Objects.requireNonNull(spouseBenefit);
            Objects.requireNonNull(primaryClaimDate); Objects.requireNonNull(spouseClaimDate);
        }
    }
    public record Ranking(SocialSecurityExpectedValueMeasure measure, BigDecimal highest, List<Integer> cells) {
        public Ranking { Objects.requireNonNull(measure); Objects.requireNonNull(highest); cells = List.copyOf(cells); }
        static Ranking from(SocialSecurityMortalityWeightedClaimingGridRanking ranking,
                List<SocialSecurityMortalityWeightedClaimingGridCell> cells) {
            return new Ranking(ranking.measure(), ranking.highestValue(), indices(cells, ranking.highestCells()));
        }
        SocialSecurityMortalityWeightedClaimingGridRanking restore(List<SocialSecurityMortalityWeightedClaimingGridCell> all) {
            var selected = references(all, cells);
            var expected = all.stream().filter(c -> value(c, measure).compareTo(highest) == 0).toList();
            if (all.stream().anyMatch(c -> value(c, measure).compareTo(highest) > 0) || !selected.equals(expected))
                throw new IllegalArgumentException("Snapshot grid maxima/ties do not reconcile.");
            return new SocialSecurityMortalityWeightedClaimingGridRanking(measure, highest, selected);
        }
    }
    public static CoupleSocialSecuritySnapshot from(SocialSecuritySurvivorClaimingOptimizationResult r, FrozenContext frozen) {
        var g = r.retirementGrid();
        return new CoupleSocialSecuritySnapshot(Request.from(r.request()), frozen, g.jointMortalityScenarios(), g.cells(),
                Ranking.from(g.highestExpectedNominal(), g.cells()), Ranking.from(g.highestExpectedReal(), g.cells()),
                Ranking.from(g.highestExpectedPresentValue(), g.cells()), g.strategyEvaluationCount(),
                indices(g.cells(), r.retainedRetirementCells()), r.primarySurvivorCandidates(), r.spouseSurvivorCandidates(),
                r.evaluatedStrategies(), r.highestExpectedPresentValue(), indices(r.evaluatedStrategies(), r.highestExpectedPresentValueStrategies()),
                r.stageTwoStrategyCount(), r.stageTwoDeterministicEvaluationCount());
    }
    public SocialSecuritySurvivorClaimingOptimizationResult restore() {
        var r = request.restore();
        if (!request.conditioning().equals(frozen.longevity().mortalityBaseDate()))
            throw new IllegalArgumentException("Frozen conditioning date mismatch.");
        if (scenarios.size() != Math.multiplyExact(request.primaryMortality().size(), request.spouseMortality().size()))
            throw new IllegalArgumentException("Snapshot mortality reference count mismatch.");
        int scenarioIndex = 0;
        BigDecimal totalProbability = BigDecimal.ZERO;
        for (var primary : request.primaryMortality()) for (var spouse : request.spouseMortality()) {
            var scenario = scenarios.get(scenarioIndex++);
            if (scenario.primaryDeathAge() != primary.deathAge() || scenario.spouseDeathAge() != spouse.deathAge()
                    || !scenario.primaryProbability().equals(primary.probability()) || !scenario.spouseProbability().equals(spouse.probability())
                    || scenario.jointProbability().signum() < 0 || scenario.jointProbability().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("Snapshot mortality reference mismatch.");
            totalProbability = totalProbability.add(scenario.jointProbability());
        }
        if (totalProbability.compareTo(BigDecimal.ONE) != 0) throw new IllegalArgumentException("Snapshot mortality total mismatch.");
        int index = 0;
        for (int primary : request.primaryAges()) for (int spouse : request.spouseAges()) {
            var cell = gridCells.get(index++);
            if (cell.primaryClaimAge() != primary || cell.spouseClaimAge() != spouse
                    || cell.expectedValue().strategyEvaluationCount() != scenarios.size())
                throw new IllegalArgumentException("Snapshot grid identity/count mismatch.");
        }
        if (nominal.measure() != SocialSecurityExpectedValueMeasure.EXPECTED_NOMINAL
                || real.measure() != SocialSecurityExpectedValueMeasure.EXPECTED_REAL
                || presentValue.measure() != SocialSecurityExpectedValueMeasure.EXPECTED_PRESENT_VALUE)
            throw new IllegalArgumentException("Snapshot ranking measure mismatch.");
        var grid = new SocialSecurityMortalityWeightedClaimingGridResult(r.retirementGridRequest(), request.primaryAges(),
                request.spouseAges(), scenarios, gridCells, nominal.restore(gridCells), real.restore(gridCells),
                presentValue.restore(gridCells), gridEvaluationCount);
        var highest = references(strategies, highestStrategies);
        if (!highest.equals(strategies.stream().filter(c -> c.expectedPresentValue().compareTo(highestPresentValue) == 0).toList())
                || strategies.stream().anyMatch(c -> c.expectedPresentValue().compareTo(highestPresentValue) > 0))
            throw new IllegalArgumentException("Snapshot strategy maxima/ties do not reconcile.");
        var retained = references(gridCells, retainedCells);
        if (scenarios.isEmpty() || retained.isEmpty() || primarySurvivors.isEmpty() || spouseSurvivors.isEmpty())
            throw new IllegalArgumentException("Incomplete successful snapshot.");
        for (var cell : strategies) {
            var s = cell.strategy();
            if (retained.stream().noneMatch(c -> c.primaryClaimDate().equals(s.primaryRetirementClaimDate())
                    && c.spouseClaimDate().equals(s.spouseRetirementClaimDate()))
                    || !primarySurvivors.contains(s.primarySurvivorElection()) || !spouseSurvivors.contains(s.spouseSurvivorElection())
                    || cell.expectedValue().strategyEvaluationCount() != scenarios.size())
                throw new IllegalArgumentException("Snapshot strategy reference mismatch.");
        }
        if (strategies.stream().map(SocialSecuritySurvivorClaimingOptimizationCell::strategy).distinct().count() != strategies.size())
            throw new IllegalArgumentException("Duplicate snapshot strategy identity.");
        return new SocialSecuritySurvivorClaimingOptimizationResult(r, grid, retained, primarySurvivors, spouseSurvivors,
                strategies, highestPresentValue, highest, strategyCount, evaluationCount);
    }
    @Override public void validate(AnalysisKind kind) {
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY) throw new IllegalArgumentException("Payload kind mismatch.");
        restore();
    }
    @Override public boolean successful() { return !strategies.isEmpty(); }
    private static BigDecimal value(SocialSecurityMortalityWeightedClaimingGridCell c, SocialSecurityExpectedValueMeasure m) {
        return switch (m) { case EXPECTED_NOMINAL -> c.expectedNominalBenefits(); case EXPECTED_REAL -> c.expectedRealBenefits();
            case EXPECTED_PRESENT_VALUE -> c.expectedPresentValue(); };
    }
    private static <T> List<Integer> indices(List<T> all, List<T> selected) {
        var index = new HashMap<T, Integer>();
        for (int i = 0; i < all.size(); i++) index.putIfAbsent(all.get(i), i);
        return selected.stream().map(value -> { var i = index.get(value);
            if (i == null) throw new IllegalArgumentException("Missing snapshot reference."); return i; }).toList();
    }
    private static <T> List<T> references(List<T> all, List<Integer> indices) {
        if (new HashSet<>(indices).size() != indices.size()) throw new IllegalArgumentException("Duplicate snapshot reference.");
        return indices.stream().map(i -> {
            if (i == null || i < 0 || i >= all.size()) throw new IllegalArgumentException("Invalid snapshot reference.");
            return all.get(i);
        }).toList();
    }
}
