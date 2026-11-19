package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedScenarioOutcome;
import com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyAggregate;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathSnapshot;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisEvidenceV2.CANONICAL;

/** The common positive mortality sequence is stored once. A candidate maps its prefix to actual financial executions. */
public record ArkansasWeightedAttribution(List<World> worlds, EstateAtSecondDeathSnapshot opening,
        List<Candidate> candidates) {
    public static final int MAX_WORLDS = 16_384;
    public static final int MAX_SCENARIO_LINKS = 1_000_000;

    public record World(int primaryDeath, int spouseDeath, BigDecimal probability) {
        public World {
            if (Objects.requireNonNull(probability).signum() <= 0) throw new IllegalArgumentException("Invalid weighted mortality probability.");
        }

        static World from(LongevityWeightedIntegratedScenarioOutcome outcome) {
            return new World(outcome.primaryDeathYear(), outcome.spouseDeathYear(), outcome.probability());
        }

        int lastYear() { return Math.max(primaryDeath, spouseDeath) - 1; }
    }

    record ObservedScenario(Integer execution, LongevityWeightedIntegratedScenarioOutcome outcome) { }

    public record Candidate(int order, WeightedSnapshots.Strategy strategy, Integer representative,
            int scenarioCount, String executionLinks, String outcomesDigest, String aggregateDigest,
            WeightedSnapshots.Failure failure, ArkansasDeterministicEvidence.Unavailable unavailable) {
        public Candidate {
            if (order < 0 || !Objects.requireNonNull(strategy).restore().hasSpouse()
                    || (aggregateDigest == null) == (failure == null)
                    || (unavailable != null && failure == null)
                    || scenarioCount < 0 || scenarioCount > MAX_WORLDS
                    || (representative != null && (representative <= 0 || representative >= order || scenarioCount != 0 || failure != null))) {
                throw new IllegalArgumentException("Invalid weighted evidence occurrence.");
            }
            if (aggregateDigest != null) ArkansasAnalysisEvidenceV2.requireDigest(aggregateDigest);
            if (representative == null) ArkansasAnalysisEvidenceV2.requireDigest(outcomesDigest);
            else if (outcomesDigest != null) throw new IllegalArgumentException("A reused occurrence cannot have independent financial results.");
            unpack(executionLinks, scenarioCount);
        }

        static Candidate captured(int order, WeightedSnapshots.Strategy strategy, List<ObservedScenario> scenarios,
                LongevityWeightedStrategyAggregate aggregate, WeightedSnapshots.Failure failure,
                ArkansasDeterministicEvidence.Unavailable unavailable) {
            if (aggregate != null && !expectedAggregate(scenarios, aggregate).equals(aggregate)) {
                throw new IllegalArgumentException("Weighted values differ from the observed mortality coverage.");
            }
            var codes = ByteBuffer.allocate(Math.multiplyExact(scenarios.size(), Short.BYTES));
            for (var scenario : scenarios) {
                codes.putShort(scenario.execution() == null ? 0 : (short) (scenario.execution() + 1));
            }
            return new Candidate(order, strategy, null, scenarios.size(), Base64.getEncoder().encodeToString(codes.array()),
                    CANONICAL.executionDigest(scenarios.stream().map(ObservedScenario::outcome).toList()),
                    aggregate == null ? null : CANONICAL.executionDigest(aggregate), failure, unavailable);
        }
    }

    public ArkansasWeightedAttribution {
        if (candidates.isEmpty() || candidates.size() > ArkansasAnalysisEvidenceV2.MAX_EXECUTIONS || worlds.size() > MAX_WORLDS
                || candidates.stream().mapToLong(Candidate::scenarioCount).sum() > MAX_SCENARIO_LINKS)
            throw ArkansasAnalysisEvidenceV2.capacity();
        candidates = List.copyOf(candidates);
        worlds = List.copyOf(worlds);
        if (new HashSet<>(worlds.stream().map(world -> List.of(world.primaryDeath(), world.spouseDeath())).toList()).size() != worlds.size())
            throw new IllegalArgumentException("Duplicate weighted mortality scenario.");
        int first = candidates.getFirst().order();
        if (first != 0 && first != 1) throw new IllegalArgumentException("Missing first weighted occurrence.");
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).order() != first + index || candidates.get(index).scenarioCount() > worlds.size())
                throw new IllegalArgumentException("Invalid weighted occurrence order or mortality prefix.");
        }
    }

    void validate(WeightedSnapshots.Couple result, List<ArkansasAnalysisEvidenceV2.Execution> executions) {
        var entries = new ArrayList<WeightedSnapshots.Entry>();
        if (result.baseline() != null) entries.add(result.baseline());
        entries.addAll(result.entries());
        if (entries.size() != candidates.size() || worlds.size() > result.metadata().originalScenarioCount())
            throw new IllegalArgumentException("Missing weighted evidence occurrence.");
        var used = new boolean[executions.size()];
        var probability = worlds.stream().map(World::probability).reduce(BigDecimal.ZERO, BigDecimal::add);
        int offset = candidates.getFirst().order();
        int actualReuses = 0;
        boolean usedOpening = false;
        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            var entry = entries.get(index);
            if (candidate.order() != entry.order() || !candidate.strategy().equals(entry.strategy())
                    || !Objects.equals(candidate.failure(), entry.failure())
                    || !Objects.equals(candidate.aggregateDigest(), entry.aggregate() == null ? null : CANONICAL.executionDigest(entry.aggregate()))) {
                throw new IllegalArgumentException("Weighted result differs from the observed candidate.");
            }
            if (candidate.representative() != null) {
                var representative = candidates.get(candidate.representative() - offset);
                if (representative.representative() != null || representative.failure() != null
                        || !Objects.equals(representative.aggregateDigest(), candidate.aggregateDigest())
                        || result.equivalence() == null
                        || !candidate.representative().equals(result.equivalence().representatives().get(candidate.order() - 1))) {
                    throw new IllegalArgumentException("Weighted result uses an unproved equivalent representative.");
                }
                validateDetails(entry, representative);
                actualReuses++;
                continue;
            }
            var links = unpack(candidate.executionLinks(), candidate.scenarioCount());
            for (int scenario = 0; scenario < candidate.scenarioCount(); scenario++) {
                var world = worlds.get(scenario);
                int ordinal = Short.toUnsignedInt(links.getShort()) - 1;
                if (ordinal >= 0) {
                    if (ordinal >= executions.size()) throw new IllegalArgumentException("Missing weighted financial execution.");
                    var execution = executions.get(ordinal);
                    if (execution.candidate() != candidate.order() || execution.shortage() != null || !covers(execution, world)) {
                        throw new IllegalArgumentException("Weighted scenario does not identify its proved execution.");
                    }
                    used[ordinal] = true;
                } else {
                    if (opening == null || !opening.balanceDate().equals(LocalDate.of(world.lastYear() + 1, 1, 1)))
                        throw new IllegalArgumentException("Zero-execution weighted outcome requires opening balance evidence.");
                    usedOpening = true;
                }
            }
            if (entry.aggregate() != null) {
                if (candidate.scenarioCount() == 0 || candidate.scenarioCount() != worlds.size()
                        || entry.aggregate().originalScenarioCount() != result.metadata().originalScenarioCount()
                        || !probability.equals(entry.aggregate().totalEvaluatedProbability())) {
                    throw new IllegalArgumentException("Weighted values differ from the observed mortality coverage.");
                }
                validateDetails(entry, candidate);
            }
        }
        if (actualReuses != result.evaluationsAvoided() || usedOpening != (opening != null))
            throw new IllegalArgumentException("Weighted reuse or opening evidence count mismatch.");
        for (var execution : executions) {
            int index = execution.candidate() - offset;
            if (index < 0 || index >= candidates.size() || candidates.get(index).representative() != null) {
                throw new IllegalArgumentException("Weighted execution does not identify its original occurrence.");
            }
            // A failed speculative carrier can be discarded by the existing continuation proof.
            if (!used[execution.occurrence()] && execution.shortage() == null && candidates.get(index).failure() == null
                    && result.work().continuation().failedCarriers() == 0) {
                throw new IllegalArgumentException("Unused successful Arkansas weighted financial execution.");
            }
        }
    }

    private static ByteBuffer unpack(String encoded, int count) {
        int bytes = Math.multiplyExact(count, Short.BYTES);
        if (encoded == null || encoded.length() != 4 * ((bytes + 2) / 3))
            throw new IllegalArgumentException("Invalid weighted scenario link length.");
        byte[] links = Base64.getDecoder().decode(encoded);
        if (links.length != bytes || !Base64.getEncoder().encodeToString(links).equals(encoded))
            throw new IllegalArgumentException("Noncanonical weighted scenario links.");
        return ByteBuffer.wrap(links);
    }

    private static boolean covers(ArkansasAnalysisEvidenceV2.Execution execution, World world) {
        int primary = world.primaryDeath(), spouse = world.spouseDeath();
        int last = world.lastYear();
        return execution.firstYear() <= last && last <= execution.requestedLastYear()
                // The existing continuation planner puts all simultaneous deaths in one both-alive prefix group.
                && (primary == spouse ? execution.primaryDeath() == execution.spouseDeath() && execution.primaryDeath() >= primary
                    : primary < spouse ? execution.primaryDeath() == primary && execution.spouseDeath() >= spouse
                    : execution.spouseDeath() == spouse && execution.primaryDeath() >= primary);
    }

    private static void validateDetails(WeightedSnapshots.Entry result, Candidate source) {
        if (result.details() != null && (result.details().size() != source.scenarioCount()
                || !CANONICAL.executionDigest(result.details()).equals(source.outcomesDigest()))) {
            throw new IllegalArgumentException("Weighted retained details differ from the observed scenario outcomes.");
        }
    }

    private static LongevityWeightedStrategyAggregate expectedAggregate(List<ObservedScenario> scenarios, LongevityWeightedStrategyAggregate original) {
        var pv = BigDecimal.ZERO;
        var estate = BigDecimal.ZERO;
        var assets = BigDecimal.ZERO;
        var probability = BigDecimal.ZERO;
        BigDecimal min = null, max = null;
        for (var scenario : scenarios) {
            var outcome = scenario.outcome();
            var nominal = outcome.estateSnapshot().nominalAfterTaxEstate();
            var weight = outcome.probability();
            pv = pv.add(weight.multiply(outcome.pvEstate()));
            estate = estate.add(weight.multiply(nominal));
            assets = assets.add(weight.multiply(outcome.estateSnapshot().nominalInvestableAssets()));
            probability = probability.add(weight);
            min = min == null ? nominal : min.min(nominal);
            max = max == null ? nominal : max.max(nominal);
        }
        return new LongevityWeightedStrategyAggregate(pv, estate, assets, min, max, probability,
                original.originalScenarioCount(), original.actualProjectionRunCount());
    }
}
