package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyEvaluationFailure;
import com.daviddunn.retirementplanner.app.socialsecurity.LongevityFinancialObservation;
import com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedScenarioOutcome;
import com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyAggregate;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.ArkansasAnnualProvenance;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.SourceQualification;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** V2 dictionary ordinals follow first use in the genuine execution sequence. */
public record ArkansasAnalysisEvidenceV2(UUID request, String fingerprint, String resultDigest,
        List<PensionFact> facts, List<List<Integer>> pensionVectors, List<Execution> executions, ArkansasPairedEvidence paired,
        ArkansasDeterministicEvidence deterministic, ArkansasIndividualMortalityEvidence individualMortality,
        ArkansasWeightedAttribution weightedAttribution) {
    public static final int MAX_FACTS = 8_192;
    public static final int MAX_EXECUTIONS = 32_768;
    public static final int MAX_PENSION_VECTORS = 8_192;
    public static final int MAX_REFERENCES_PER_EXECUTION = 16_384;
    public static final int MAX_REFERENCES = 600_000;
    public static final int MAX_LOGICAL_RECEIPTS = 3_000_000;
    static final AnalysisSnapshotCodec CANONICAL = new AnalysisSnapshotCodec();

    public record PensionFact(int pensionIndex, AccountOwnership originalOwner, AccountOwnership recipient,
            RetirementIncomeSourceOrigin origin, int year, BigDecimal gross, SourceQualification qualification,
            ArkansasAnnualProvenance.Receipt receipt) {
        public PensionFact {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(qualification);
            Objects.requireNonNull(gross);
            if (pensionIndex < 0 || !individual(originalOwner) || !individual(recipient) || gross.signum() <= 0) {
                throw new IllegalArgumentException("Invalid Arkansas pension fact.");
            }
        }

        private static boolean individual(AccountOwnership owner) {
            return owner == AccountOwnership.PRIMARY || owner == AccountOwnership.SPOUSE;
        }
    }

    public record Shortage(int year, int projectionYear, Integer primaryAge, Integer spouseAge,
            FundingFailure.Stage stage, AccountOwnership owner, BigDecimal required, BigDecimal available, BigDecimal shortfall) {
        public Shortage {
            new FundingFailure(year, projectionYear, java.util.Optional.ofNullable(primaryAge),
                    java.util.Optional.ofNullable(spouseAge), stage, java.util.Optional.ofNullable(owner), required, available, shortfall);
        }

        static Shortage from(FundingFailure failure) {
            return new Shortage(failure.calendarYear(), failure.projectionYearIndex(), failure.primaryAge().orElse(null),
                    failure.spouseAge().orElse(null), failure.stage(), failure.owner().orElse(null),
                    failure.requiredAmount(), failure.availableAmount(), failure.shortfallAmount());
        }
    }

    public record Execution(int occurrence, int candidate, int primaryDeath, int spouseDeath,
            int firstYear, int requestedLastYear, int completedYears, Shortage shortage, String annualDigest,
            int pensionVector, String pensionDigest) {
        public Execution {
            if (occurrence < 0 || candidate < 0 || pensionVector < 0
                    || completedYears < 0 || requestedLastYear != Math.max(primaryDeath, spouseDeath) - 1
                    || requestedLastYear < firstYear
                    || (shortage == null && firstYear + (long) completedYears - 1 != requestedLastYear)
                    || (shortage != null && (shortage.year() != firstYear + (long) completedYears || shortage.year() > requestedLastYear))) {
                throw new IllegalArgumentException("Invalid Arkansas longevity execution coverage.");
            }
            requireDigest(annualDigest);
            requireDigest(pensionDigest);
        }
    }

    public ArkansasAnalysisEvidenceV2 {
        Objects.requireNonNull(request);
        requireDigest(fingerprint);
        requireDigest(resultDigest);
        if (facts.size() > MAX_FACTS || executions.size() > MAX_EXECUTIONS || pensionVectors.size() > MAX_PENSION_VECTORS
                || pensionVectors.stream().mapToLong(List::size).sum() > MAX_REFERENCES) throw capacity();
        int families = (paired == null ? 0 : 1) + (deterministic == null ? 0 : 1)
                + (weightedAttribution == null ? 0 : 1) + (individualMortality == null ? 0 : 1);
        if (families != 1 || (weightedAttribution == null && (!executions.isEmpty() || !facts.isEmpty() || !pensionVectors.isEmpty()))) {
            throw new IllegalArgumentException("Exactly one Arkansas execution evidence family is required.");
        }
        facts = List.copyOf(facts);
        pensionVectors = pensionVectors.stream().map(List::copyOf).toList();
        executions = List.copyOf(executions);
        if (new HashSet<>(facts).size() != facts.size()) throw new IllegalArgumentException("Duplicate Arkansas pension fact.");
        // Hash each canonical fact once. Validating a compact archive never expands its receipt graph.
        var factDigests = facts.stream().map(CANONICAL::executionDigest).toList();
        if (new HashSet<>(pensionVectors).size() != pensionVectors.size()) throw new IllegalArgumentException("Duplicate Arkansas pension vector.");
        var vectorDigests = new ArrayList<String>();
        int nextFact = 0;
        for (var refs : pensionVectors) {
            if (refs.size() > MAX_REFERENCES_PER_EXECUTION) throw capacity();
            var unique = new HashSet<Integer>();
            var orderedDigests = new ArrayList<String>(refs.size());
            for (int ordinal : refs) {
                if (ordinal < 0 || ordinal >= facts.size() || ordinal > nextFact || !unique.add(ordinal)) {
                    throw new IllegalArgumentException("Invalid Arkansas pension fact reference.");
                }
                if (ordinal == nextFact) nextFact++;
                orderedDigests.add(factDigests.get(ordinal));
            }
            vectorDigests.add(CANONICAL.executionDigest(orderedDigests));
        }
        int nextVector = 0;
        long logical = 0;
        for (int index = 0; index < executions.size(); index++) {
            var execution = executions.get(index);
            int vector = execution.pensionVector();
            if (execution.occurrence() != index || vector >= pensionVectors.size() || vector > nextVector
                    || !vectorDigests.get(vector).equals(execution.pensionDigest())) {
                throw new IllegalArgumentException("Unordered execution or invalid Arkansas pension vector.");
            }
            if (vector == nextVector) nextVector++;
            var refs = pensionVectors.get(vector);
            logical += refs.size();
            if (logical > MAX_LOGICAL_RECEIPTS) throw capacity();
            for (int ordinal : refs) {
                var fact = facts.get(ordinal);
                int recipientDeath = fact.recipient() == AccountOwnership.PRIMARY
                        ? execution.primaryDeath() : execution.spouseDeath();
                if (fact.year() < execution.firstYear() || fact.year() >= execution.firstYear() + (long) execution.completedYears()
                        || fact.year() >= recipientDeath) {
                    throw new IllegalArgumentException("Arkansas pension fact is outside the execution's recipient or year coverage.");
                }
            }
        }
        if (nextFact != facts.size() || nextVector != pensionVectors.size()) throw new IllegalArgumentException("Unused Arkansas pension evidence.");
    }

    void validatePayload(AnalysisKind kind, AnalysisPayload payload) {
        if (!resultDigest.equals(CANONICAL.executionDigest(payload))) {
            throw new IllegalArgumentException("Arkansas execution evidence does not identify this retained analysis.");
        }
        if (deterministic != null) {
            deterministic.validatePayload(kind, payload);
            return;
        }
        if (individualMortality != null) {
            individualMortality.validatePayload(kind, payload);
            return;
        }
        if (paired != null) {
            paired.validatePayload(kind, payload);
            return;
        }
        if (kind != AnalysisKind.COUPLE_WEIGHTED || !(payload instanceof WeightedSnapshots.Payload weighted)
                || weighted.couple() == null) {
            throw new IllegalArgumentException("Arkansas execution evidence does not identify this retained analysis.");
        }
        var result = weighted.couple();
        long completed = executions.stream().filter(execution -> execution.shortage() == null).count();
        if (result.work().projectionEngineRuns() < executions.size()
                || result.work().completedProjectionEngineRuns() != completed) {
            throw new IllegalArgumentException("Arkansas execution coverage differs from the observed weighted comparison.");
        }
        weightedAttribution.validate(result, executions);
    }

    void validateSource(com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot plan) {
        var pensions = plan.getHousehold().members().stream().flatMap(member -> member.getIncomeSources().stream())
                .filter(Pension.class::isInstance).map(Pension.class::cast).toList();
        for (var fact : facts) {
            if (fact.pensionIndex() >= pensions.size()) throw new IllegalArgumentException("Missing Arkansas pension source.");
            var pension = pensions.get(fact.pensionIndex());
            if (pension.getOwnership() != fact.originalOwner() || pension.getSourceOrigin() != fact.origin()
                    || fact.recipient() != fact.originalOwner() && (pension.getSurvivorMonthlyBenefit() == null
                    || pension.getSurvivorMonthlyBenefit().signum() <= 0)) {
                throw new IllegalArgumentException("Arkansas pension fact differs from its frozen source.");
            }
        }
        int firstYear = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        if (weightedAttribution.opening() != null
                && !weightedAttribution.opening().equals(ArkansasIndividualMortalityEvidence.openingAssets(plan))) {
            throw new IllegalArgumentException("Weighted opening-death evidence differs from the frozen source assets.");
        }
        for (var execution : executions) {
            if (execution.firstYear() != firstYear) throw new IllegalArgumentException("Arkansas execution starts outside the frozen plan.");
            for (int ordinal : pensionVectors.get(execution.pensionVector())) {
                var fact = facts.get(ordinal);
                int ownerDeath = fact.originalOwner() == AccountOwnership.PRIMARY ? execution.primaryDeath() : execution.spouseDeath();
                if ((fact.originalOwner() == fact.recipient()) != (fact.year() < ownerDeath)) {
                    throw new IllegalArgumentException("Arkansas receipt does not identify the original pension recipient.");
                }
            }
        }
    }

    static void requireDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid Arkansas execution digest.");
    }

    static BudgetExceeded capacity() {
        return new BudgetExceeded();
    }

    public static final class BudgetExceeded extends IllegalArgumentException {
        private BudgetExceeded() {
            super("Arkansas analysis exceeded its retained evidence budget. Previous successful results were retained.");
        }
    }

    static final class Collector implements LongevityFinancialObservation {
        private final CompleteProjectionSnapshot source;
        private final AnalysisCancellationToken cancellation;
        private final Map<PensionFact, Integer> facts = new LinkedHashMap<>();
        private final List<String> factDigests = new ArrayList<>();
        private final Map<List<Integer>, Integer> vectors = new LinkedHashMap<>();
        private final List<String> vectorDigests = new ArrayList<>();
        private final List<Execution> executions = new ArrayList<>();
        private final List<ArkansasWeightedAttribution.Candidate> candidates = new ArrayList<>();
        private final List<ArkansasWeightedAttribution.ObservedScenario> scenarios = new ArrayList<>();
        private final List<ArkansasWeightedAttribution.World> worlds = new ArrayList<>();
        private com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathSnapshot opening;
        private final Map<HouseholdLifetimeScenario, Integer> carriers = new LinkedHashMap<>();
        private int order;
        private WeightedSnapshots.Strategy election;
        private int references;
        private int logicalReceipts;
        private int scenarioLinks;

        Collector(CompleteProjectionSnapshot source, AnalysisCancellationToken cancellation) {
            this.source = source;
            this.cancellation = cancellation;
        }

        void observe(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionExecutionResult execution) {
            cancellation.throwIfCancellationRequested();
            if (executions.size() == MAX_EXECUTIONS) throw capacity();
            if (!source.inputDigest().equals(CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan)))) {
                throw new IllegalArgumentException("Arkansas analysis ran against different frozen inputs.");
            }
            var strategy = WeightedSnapshots.Strategy.from(context.socialSecurityStrategy().orElseThrow());
            if (!strategy.equals(election)) throw new IllegalArgumentException("Arkansas longevity execution belongs to another candidate occurrence.");
            var lifetime = context.householdLifetimeScenario().orElseThrow();
            var projection = new Projection();
            var links = new ArrayList<Integer>();
            var orderedDigests = new ArrayList<String>();
            for (var year : execution.completedYears()) {
                cancellation.throwIfCancellationRequested();
                projection.addYear(year);
                var provenance = year.arkansasValidationTax().orElseThrow().provenance();
                for (var pension : provenance.pensions()) {
                    var sourceReceipt = year.getIncomeSources().get(pension.sourceIndex());
                    var original = RetainedProjectionSnapshot.PensionFacts.capture(sourceReceipt.pension().orElseThrow());
                    int index = CompleteProjectionSnapshot.findOriginalPension(plan.getHousehold(), sourceReceipt.description(),
                            original, pension.origin());
                    var receipt = provenance.receipts().stream().filter(r -> r.sourceIndex() == pension.sourceIndex()).findFirst().orElse(null);
                    var fact = new PensionFact(index, original.owner(), sourceReceipt.owner().orElseThrow(), pension.origin(),
                            year.getCalendarYear(), sourceReceipt.grossAmount(), pension.qualification(), receipt);
                    if (links.size() == MAX_REFERENCES_PER_EXECUTION || logicalReceipts == MAX_LOGICAL_RECEIPTS) throw capacity();
                    int ordinal = facts.computeIfAbsent(fact, unused -> {
                        if (facts.size() == MAX_FACTS) throw capacity();
                        factDigests.add(CANONICAL.executionDigest(fact));
                        return facts.size();
                    });
                    links.add(ordinal);
                    orderedDigests.add(factDigests.get(ordinal));
                    logicalReceipts++;
                }
            }
            String annual = CANONICAL.executionDigest(execution.completedYears().isEmpty()
                    ? List.of() : RetainedProjectionSnapshot.captureArkansas(projection));
            int vector = vectors.computeIfAbsent(List.copyOf(links), newVector -> {
                if (vectors.size() == MAX_PENSION_VECTORS || references + newVector.size() > MAX_REFERENCES) throw capacity();
                references += newVector.size();
                vectorDigests.add(CANONICAL.executionDigest(orderedDigests));
                return vectors.size();
            });
            if (execution.status() == ProjectionExecutionResult.Status.COMPLETED) carriers.put(lifetime, executions.size());
            executions.add(new Execution(executions.size(), order, lifetime.primaryDeathYear().orElseThrow().getValue(),
                    lifetime.spouseDeathYear().orElseThrow().getValue(), plan.getPlanningAssumptions().getProjectionStartDate().getYear(),
                    context.endingYearOverride().orElseThrow(), execution.completedYears().size(),
                    execution instanceof ProjectionExecutionResult.InsufficientFunds shortage ? Shortage.from(shortage.fundingFailure()) : null,
                    annual, vector, vectorDigests.get(vector)));
        }

        @Override public void starting(int order, SocialSecurityHouseholdClaimingStrategy strategy) {
            cancellation.throwIfCancellationRequested();
            if (election != null || candidates.size() == MAX_EXECUTIONS) throw capacity();
            this.order = order;
            election = WeightedSnapshots.Strategy.from(strategy);
        }

        @Override public void scenario(LongevityWeightedIntegratedScenarioOutcome outcome, HouseholdLifetimeScenario execution) {
            cancellation.throwIfCancellationRequested();
            if (scenarioLinks == ArkansasWeightedAttribution.MAX_SCENARIO_LINKS || scenarios.size() == ArkansasWeightedAttribution.MAX_WORLDS) throw capacity();
            var index = execution == null ? null : carriers.get(execution);
            if (execution != null && index == null) throw new IllegalStateException("Weighted outcome is missing an observed financial projection.");
            var world = ArkansasWeightedAttribution.World.from(outcome);
            if (scenarios.size() == worlds.size()) worlds.add(world);
            else if (!worlds.get(scenarios.size()).equals(world)) throw new IllegalStateException("Weighted mortality coverage differs between candidates.");
            if (execution == null) {
                if (opening == null) opening = outcome.estateSnapshot();
                else if (!opening.equals(outcome.estateSnapshot())) throw new IllegalStateException("Weighted opening assets changed between candidates.");
            }
            scenarios.add(new ArkansasWeightedAttribution.ObservedScenario(index, outcome));
            scenarioLinks++;
        }

        @Override public void completed(LongevityWeightedStrategyAggregate aggregate) {
            cancellation.throwIfCancellationRequested();
            candidates.add(ArkansasWeightedAttribution.Candidate.captured(order, election, scenarios, aggregate, null, null));
            reset();
        }

        @Override public void failed(IntegratedSocialSecurityStrategyEvaluationFailure failure, RuntimeException cause) {
            cancellation.throwIfCancellationRequested();
            if (election == null) throw cause;
            ArkansasDeterministicEvidence.Unavailable unavailable = null;
            for (Throwable error = cause; error != null; error = error.getCause()) {
                if (error instanceof com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException tax) {
                    unavailable = ArkansasDeterministicEvidence.Unavailable.from(tax);
                    break;
                }
                if (error instanceof BudgetExceeded exceeded) throw exceeded;
            }
            candidates.add(ArkansasWeightedAttribution.Candidate.captured(order, election, scenarios, null,
                    new WeightedSnapshots.Failure(failure.message(), failure.category()), unavailable));
            reset();
        }

        @Override public void reused(int order, SocialSecurityHouseholdClaimingStrategy strategy, int representative) {
            cancellation.throwIfCancellationRequested();
            if (candidates.size() == MAX_EXECUTIONS) throw capacity();
            var source = candidates.get(representative - candidates.getFirst().order());
            candidates.add(new ArkansasWeightedAttribution.Candidate(order, WeightedSnapshots.Strategy.from(strategy),
                    representative, 0, "", null, source.aggregateDigest(), null, null));
        }

        private void reset() {
            election = null;
            scenarios.clear();
            carriers.clear();
        }

        ArkansasAnalysisEvidenceV2 finish(UUID request, String fingerprint, AnalysisPayload payload) {
            cancellation.throwIfCancellationRequested();
            return new ArkansasAnalysisEvidenceV2(request, fingerprint, CANONICAL.executionDigest(payload),
                    new ArrayList<>(facts.keySet()), new ArrayList<>(vectors.keySet()), executions, null, null, null,
                    new ArkansasWeightedAttribution(worlds, opening, candidates));
        }
    }
}
