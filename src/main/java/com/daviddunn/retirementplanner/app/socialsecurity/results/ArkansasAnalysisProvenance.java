package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPlanDocument;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ArkansasAnnualProvenance;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/** Compact proof of gated financial execution and the original accepted projection dependencies. */
public record ArkansasAnalysisProvenance(int version, StateTaxJurisdiction jurisdiction, List<Dependency> dependencies,
        Map<String, String> policies, List<WeightedPension> weightedPensions) {
    public record WeightedPension(WeightedSnapshots.Strategy strategy, int primaryDeathYear, int spouseDeathYear,
            int year, int pensionIndex, AccountOwnership recipient, BigDecimal annualReceipt,
            com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.SourceQualification qualification,
            ArkansasAnnualProvenance.Receipt evidence) {
        public WeightedPension {
            Objects.requireNonNull(strategy);
            Objects.requireNonNull(qualification);
            Objects.requireNonNull(recipient);
            if (!strategy.restore().hasSpouse() || pensionIndex < 0 || year >= Math.max(primaryDeathYear, spouseDeathYear)
                    || annualReceipt.signum() <= 0 || recipient == AccountOwnership.JOINT
                    || year >= (recipient == AccountOwnership.PRIMARY ? primaryDeathYear : spouseDeathYear)) {
                throw new IllegalArgumentException("Invalid Arkansas longevity pension receipt.");
            }
        }
    }
    public record Dependency(UUID projectionId, String inputDigest, String annualDigest,
            int completedRuns, int insufficientFundsRuns, long completedYears, boolean targetedRothUsed) {
        public Dependency {
            Objects.requireNonNull(projectionId);
            if (!inputDigest.matches("[0-9a-f]{64}") || !annualDigest.matches("[0-9a-f]{64}")
                    || completedRuns < 0 || insufficientFundsRuns < 0
                    || (long) completedRuns + insufficientFundsRuns == 0
                    || completedYears < completedRuns || (targetedRothUsed && completedYears == 0)) {
                throw new IllegalArgumentException("Invalid Arkansas analyzer projection dependency.");
            }
        }
    }

    public ArkansasAnalysisProvenance {
        dependencies = List.copyOf(dependencies);
        policies = Map.copyOf(policies);
        weightedPensions = List.copyOf(weightedPensions);
        boolean targeted = dependencies.stream().anyMatch(Dependency::targetedRothUsed);
        if (version != 1 || jurisdiction != StateTaxJurisdiction.AR
                || dependencies.isEmpty() || dependencies.size() > 2
                || weightedPensions.size() > 10_000
                || dependencies.stream().map(Dependency::projectionId).distinct().count() != dependencies.size()
                || !policies.equals(applicable(RetainedProjectionSnapshot.Arkansas.V1, targeted))) {
            throw new IllegalArgumentException("Unsupported Arkansas analyzer provenance.");
        }
    }

    /** Only a successful invocation using the supplied gated engine can create this acceptance token. */
    public static final class Captured {
        private final AnalysisPayload payload;
        private final ArkansasAnalysisProvenance provenance;

        private Captured(AnalysisPayload payload, ArkansasAnalysisProvenance provenance) {
            this.payload = payload;
            this.provenance = provenance;
        }

        public AnalysisPayload payload() { return payload; }

        ArkansasAnalysisProvenance forPayload(AnalysisPayload original) {
            if (original != payload) throw new IllegalArgumentException("Arkansas provenance belongs to another analysis result.");
            return provenance;
        }
    }

    public static Captured capture(ProjectionEngine engine, AnalysisKind kind, List<CompleteProjectionSnapshot> projections,
            Supplier<? extends AnalysisPayload> work) {
        var observed = new LinkedHashMap<String, Observation>();
        for (var projection : List.copyOf(projections)) {
            if (!projection.arkansasHistory() || observed.putIfAbsent(projection.inputDigest(), new Observation(projection)) != null) {
                throw new IllegalArgumentException("Arkansas analysis requires unambiguous frozen Arkansas projection inputs for each side.");
            }
        }
        var weightedPensions = new java.util.LinkedHashSet<WeightedPension>();
        var payload = engine.observeArkansasRuns((plan, context, execution) -> {
            var input = CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan));
            var observation = observed.get(input);
            if (observation == null) throw new IllegalArgumentException("Arkansas analysis ran against different frozen inputs.");
            observation.accept(execution);
            if (kind == AnalysisKind.COUPLE_WEIGHTED) {
                capturePensions(plan.getHousehold(), context, execution, weightedPensions);
            }
        }, work);
        var dependencies = new ArrayList<Dependency>();
        var executedPolicies = new TreeMap<String, String>();
        for (var observation : observed.values()) {
            dependencies.add(observation.dependency());
            if (observation.versions != null) executedPolicies.putAll(observation.versions);
        }
        boolean targeted = dependencies.stream().anyMatch(Dependency::targetedRothUsed);
        var provenance = new ArkansasAnalysisProvenance(1, StateTaxJurisdiction.AR,
                dependencies, applicable(executedPolicies, targeted), List.copyOf(weightedPensions));
        provenance.validatePayload(kind, payload);
        return new Captured(payload, provenance);
    }

    private static void capturePensions(com.daviddunn.retirementplanner.domain.model.Household household,
            ProjectionEvaluationContext context, ProjectionExecutionResult execution, java.util.Set<WeightedPension> evidence) {
        var strategy = WeightedSnapshots.Strategy.from(context.socialSecurityStrategy().orElseThrow());
        var lifetime = context.householdLifetimeScenario().orElseThrow();
        int primaryDeath = lifetime.primaryDeathYear().orElseThrow().getValue();
        int spouseDeath = lifetime.spouseDeathYear().orElseThrow().getValue();
        for (var year : execution.completedYears()) {
            var provenance = year.arkansasValidationTax().orElseThrow().provenance();
            for (var pension : provenance.pensions()) {
                var receipt = year.getIncomeSources().get(pension.sourceIndex());
                int index = CompleteProjectionSnapshot.findOriginalPension(household, receipt.description(),
                        RetainedProjectionSnapshot.PensionFacts.capture(receipt.pension().orElseThrow()), pension.origin());
                var facts = provenance.receipts().stream().filter(r -> r.sourceIndex() == pension.sourceIndex()).findFirst().orElse(null);
                evidence.add(new WeightedPension(strategy, primaryDeath, spouseDeath, year.getCalendarYear(), index,
                        receipt.owner().orElseThrow(), receipt.grossAmount(), pension.qualification(), facts));
                if (evidence.size() > 10_000) throw new IllegalArgumentException("Arkansas longevity source evidence exceeds the retained budget.");
            }
        }
    }

    private static final class Observation {
        private final CompleteProjectionSnapshot projection;
        private int completed;
        private int insolvent;
        private long years;
        private boolean target;
        private Map<String, String> versions;

        private Observation(CompleteProjectionSnapshot projection) { this.projection = projection; }

        void accept(ProjectionExecutionResult execution) {
            if (execution.status() == ProjectionExecutionResult.Status.COMPLETED) completed++;
            else insolvent++;
            for (var year : execution.completedYears()) {
                var tax = year.arkansasValidationTax().orElseThrow();
                if (!tax.versions().equals(projection.annual().arkansas().versions())) {
                    throw new IllegalArgumentException("Arkansas analyzer used different calculation policies from its projection.");
                }
                versions = tax.versions();
                target |= tax.targetMetrics().isPresent();
                years++;
            }
        }

        Dependency dependency() {
            return new Dependency(projection.id(), projection.inputDigest(), annualDigest(projection),
                    completed, insolvent, years, target);
        }
    }

    private static Map<String, String> applicable(Map<String, String> versions, boolean targeted) {
        var result = new TreeMap<>(versions);
        if (!targeted) result.remove("arkansas-roth-target");
        return Map.copyOf(result);
    }

    public boolean policiesMatch(Map<String, String> effective) {
        return policies.equals(applicable(effective, policies.containsKey("arkansas-roth-target")));
    }

    public boolean policiesCurrent() { return policiesMatch(ArkansasValidationTax.reviewedVersions()); }

    public void validatePayload(AnalysisKind kind, AnalysisPayload payload) {
        if (kind != AnalysisKind.COUPLE_WEIGHTED && !weightedPensions.isEmpty()) {
            throw new IllegalArgumentException("Unexpected Arkansas longevity receipt provenance.");
        }
        if (kind.isMonteCarlo() && payload instanceof MonteCarloSnapshot monteCarlo) {
            var request = monteCarlo.request();
            var documents = request.planB() == null ? List.of(request.planA()) : List.of(request.planA(), request.planB());
            if (documents.size() != dependencies.size()) {
                throw new IllegalArgumentException("Arkansas Monte Carlo side count differs from its input documents.");
            }
            for (int i = 0; i < documents.size(); i++) {
                var digest = CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(MonteCarloPlanDocument.read(documents.get(i))));
                if (!digest.equals(dependencies.get(i).inputDigest())) {
                    throw new IllegalArgumentException("Arkansas Monte Carlo side does not identify its frozen projection inputs.");
                }
            }
            if (monteCarlo.fixed() != null) {
                var source = dependencies.getFirst();
                long failures = monteCarlo.fixed().outcomes().stream().filter(outcome -> outcome.failure() != null).count();
                if (source.insufficientFundsRuns() != failures || source.completedRuns() < monteCarlo.settings().trials() - failures) {
                    throw new IllegalArgumentException("Arkansas Monte Carlo funding outcomes differ from observed executions.");
                }
            }
            return;
        }
        if (dependencies.size() == 1 && kind == AnalysisKind.COUPLE_WEIGHTED && payload instanceof WeightedSnapshots.Payload weighted
                && weighted.couple() != null) {
            var strategies = new java.util.HashSet<WeightedSnapshots.Strategy>();
            weighted.couple().entries().forEach(entry -> strategies.add(entry.strategy()));
            if (weighted.couple().baseline() != null) strategies.add(weighted.couple().baseline().strategy());
            if (weightedPensions.stream().anyMatch(pension -> !strategies.contains(pension.strategy()))) {
                throw new IllegalArgumentException("Arkansas longevity source evidence does not identify a submitted strategy.");
            }
            return;
        }
        if (dependencies.size() == 1 && kind == AnalysisKind.COUPLE_QUICK && payload instanceof QuickComparisonSnapshot quick
                && quick.baseline().arkansas() != null
                && quick.entries().stream().filter(e -> e.detail() != null).allMatch(e -> e.detail().arkansas() != null)) {
            var baseline = quick.baseline();
            var annual = new RetainedProjectionSnapshot(baseline.schemaVersion(), baseline.accounts(), baseline.years(),
                    null, null, null, List.of(), baseline.arkansas());
            if (!annualDigest(annual).equals(dependencies.getFirst().annualDigest())) {
                throw new IllegalArgumentException("Arkansas integrated baseline differs from its accepted projection.");
            }
            return;
        }
        throw new IllegalArgumentException("Analysis kind does not support Arkansas financial execution provenance.");
    }

    public void validateAssociation(UUID projectionId, String inputDigest) {
        var source = dependencies.getFirst();
        if (!source.projectionId().equals(projectionId) || !source.inputDigest().equals(inputDigest)) {
            throw new IllegalArgumentException("Arkansas analysis belongs to a different projection.");
        }
    }

    public void validateDependency(CompleteProjectionSnapshot projection) {
        var source = dependencies.stream().filter(d -> d.projectionId().equals(projection.id())).findFirst().orElseThrow();
        if (!projection.arkansasHistory() || !source.inputDigest().equals(projection.inputDigest())
                || !source.annualDigest().equals(annualDigest(projection))) {
            throw new IllegalArgumentException("Arkansas analysis dependency differs from the original retained projection.");
        }
        if (!weightedPensions.isEmpty()) {
            var pensions = projection.restoreInputs().getHousehold().members().stream()
                    .flatMap(member -> member.getIncomeSources().stream())
                    .filter(com.daviddunn.retirementplanner.domain.income.Pension.class::isInstance)
                    .map(com.daviddunn.retirementplanner.domain.income.Pension.class::cast).toList();
            for (var receipt : weightedPensions) {
                if (receipt.pensionIndex() >= pensions.size()) throw new IllegalArgumentException("Missing Arkansas longevity pension source.");
                var pension = pensions.get(receipt.pensionIndex());
                int ownerDeath = pension.getOwnership() == AccountOwnership.PRIMARY ? receipt.primaryDeathYear() : receipt.spouseDeathYear();
                if ((pension.getOwnership() == receipt.recipient()) != (receipt.year() < ownerDeath)
                        || (receipt.year() >= ownerDeath && (pension.getSurvivorMonthlyBenefit() == null
                            || pension.getSurvivorMonthlyBenefit().signum() <= 0))) {
                    throw new IllegalArgumentException("Arkansas longevity receipt does not identify the original pension recipient.");
                }
            }
        }
    }

    private static String annualDigest(CompleteProjectionSnapshot projection) { return annualDigest(projection.annual()); }

    private static String annualDigest(RetainedProjectionSnapshot annual) {
        try { return AnalysisSnapshotCodec.sha256(new RetainedProjectionCodec().encode(annual).bytes()); }
        catch (IOException invalid) { throw new IllegalArgumentException("Invalid Arkansas analyzer projection dependency.", invalid); }
    }
}
