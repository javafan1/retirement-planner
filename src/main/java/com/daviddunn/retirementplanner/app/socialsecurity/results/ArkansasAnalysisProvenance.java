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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
@JsonDeserialize(using = ArkansasAnalysisProvenanceReader.class)
public record ArkansasAnalysisProvenance(int version, StateTaxJurisdiction jurisdiction, List<Dependency> dependencies,
        Map<String, String> policies, List<WeightedPension> weightedPensions,
        @JsonInclude(JsonInclude.Include.NON_NULL) ArkansasAnalysisEvidenceV2 executionEvidence) {
    public ArkansasAnalysisProvenance(int version, StateTaxJurisdiction jurisdiction, List<Dependency> dependencies,
            Map<String, String> policies, List<WeightedPension> weightedPensions) {
        this(version, jurisdiction, dependencies, policies, weightedPensions, null);
    }
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
        if (jurisdiction != StateTaxJurisdiction.AR
                || dependencies.isEmpty() || dependencies.size() > 2
                || weightedPensions.size() > 10_000
                || ((executionEvidence == null || executionEvidence.paired() == null)
                        && dependencies.stream().map(Dependency::projectionId).distinct().count() != dependencies.size())
                || !(policies.equals(applicable(RetainedProjectionSnapshot.Arkansas.V1, targeted))
                        || policies.equals(applicable(RetainedProjectionSnapshot.Arkansas.CAPPED_FUNDING, targeted))
                        || policies.equals(applicable(RetainedProjectionSnapshot.Arkansas.REACHABLE_TARGET, targeted)))
                || (version == 1 && executionEvidence != null)
                || (version == 1 && dependencies.stream().anyMatch(d -> d.completedRuns() == 0 && d.insufficientFundsRuns() == 0))
                || (version == 2 && (executionEvidence == null || !weightedPensions.isEmpty()))
                || (version != 1 && version != 2)) {
            throw new IllegalArgumentException("Unsupported Arkansas analyzer provenance.");
        }
        if (version == 2 && executionEvidence.paired() != null) {
            var paired = executionEvidence.paired();
            if (dependencies.size() != 2 || !dependencies.get(0).projectionId().equals(paired.current().source().acceptedId())
                    || !dependencies.get(1).projectionId().equals(paired.baseline().source().acceptedId())) {
                throw new IllegalArgumentException("Ordered paired evidence differs from its accepted dependencies.");
            }
        }
    }

    /** Only a successful invocation using the supplied gated engine can create this acceptance token. */
    public static final class Captured {
        private final AnalysisPayload payload;
        private final ArkansasAnalysisProvenance provenance;
        private final Map<UUID, java.lang.ref.WeakReference<CompleteProjectionSnapshot>> verified;

        private Captured(AnalysisPayload payload, ArkansasAnalysisProvenance provenance,
                List<CompleteProjectionSnapshot> projections) {
            this.payload = payload;
            this.provenance = provenance;
            if (provenance.version() == 2) {
                ArkansasAnalysisEvidenceV2.CANONICAL.requireArkansasArchiveCapacity(payload, provenance);
            }
            var references = new LinkedHashMap<UUID, java.lang.ref.WeakReference<CompleteProjectionSnapshot>>();
            for (var projection : projections) {
                provenance.validateDependency(projection);
                references.put(projection.id(), new java.lang.ref.WeakReference<>(projection));
            }
            verified = Map.copyOf(references);
        }

        public AnalysisPayload payload() { return payload; }

        ArkansasAnalysisProvenance forPayload(AnalysisPayload original) {
            if (original != payload) throw new IllegalArgumentException("Arkansas provenance belongs to another analysis result.");
            return provenance;
        }

        public boolean dependenciesStillInstalled(Map<UUID, CompleteProjectionSnapshot> available) {
            return verified.entrySet().stream().allMatch(entry -> {
                var original = entry.getValue().get();
                return original != null && original == available.get(entry.getKey());
            });
        }
    }

    public static Captured capture(ProjectionEngine engine, AnalysisKind kind, List<CompleteProjectionSnapshot> projections,
            Supplier<? extends AnalysisPayload> work) {
        return captureRun(engine, kind, projections, work, java.util.function.Function.identity()).evidence();
    }

    public record ObservedRun<T>(T result, AnalysisPayload payload, Captured evidence) { }

    /** Side engines and their observation callback are confined to one controlled comparison worker. */
    public static <T> ObservedRun<T> runPairedProductionV2(AnalysisKind kind, List<CompleteProjectionSnapshot> sources,
            com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonRequest request, UUID identity,
            String fingerprint, com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.Function<com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonAnalyzer, T> work,
            java.util.function.Function<? super T, ? extends AnalysisPayload> payloadFactory) {
        if (sources.size() != 2 || sources.stream().anyMatch(source -> !source.calculationSemanticsCurrent()
                || !source.owner().equals(sources.getFirst().owner())) || sources.stream().noneMatch(CompleteProjectionSnapshot::arkansasHistory)) {
            throw new IllegalArgumentException("Arkansas paired analysis requires accepted Current and Baseline projections.");
        }
        if (request.assumptions().settings().simulationCount() > ArkansasPairedEvidence.MAX_WORLDS) {
            throw ArkansasAnalysisEvidenceV2.capacity();
        }
        boolean longevity = request.assumptions() instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonRequest.Longevity;
        var current = ArkansasPairedEvidence.Source.capture(ArkansasPairedEvidence.Role.CURRENT_A, sources.getFirst(), request.strategyA(), longevity);
        var baseline = ArkansasPairedEvidence.Source.capture(ArkansasPairedEvidence.Role.BASELINE_B, sources.getLast(), request.strategyB(), longevity);
        int first = request.assumptions().start().getYear();
        var currentObserver = new ArkansasPairedEvidence.Observer(current, first);
        var baselineObserver = new ArkansasPairedEvidence.Observer(baseline, first);
        var gated = ProjectionEngine.forArkansasProduction();
        var calculate = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonAnalyzer(
                current.jurisdiction() == StateTaxJurisdiction.AR ? gated : new ProjectionEngine(),
                baseline.jurisdiction() == StateTaxJurisdiction.AR ? gated : new ProjectionEngine(),
                (side, world, execution, outcome) -> {
                    var observer = side == com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonAnalyzer.Stage.STRATEGY_A
                            ? currentObserver : baselineObserver;
                    observer.observe(side, world.scenarioIndex(), execution, outcome, cancellation);
                });
        var result = gated.runArkansasAnalysis(cancellation, () -> work.apply(calculate));
        cancellation.throwIfCancellationRequested();
        var payload = payloadFactory.apply(result);
        if (payload == null || !payload.successful()) return new ObservedRun<>(result, payload, null);
        var dependencies = List.of(currentObserver.dependency(sources.getFirst()), baselineObserver.dependency(sources.getLast()));
        var evidence = new ArkansasAnalysisEvidenceV2(identity, fingerprint,
                ArkansasAnalysisEvidenceV2.CANONICAL.executionDigest(payload), List.of(), List.of(), List.of(),
                new ArkansasPairedEvidence(currentObserver.finish(), baselineObserver.finish()), null, null, null);
        var provenance = new ArkansasAnalysisProvenance(2, StateTaxJurisdiction.AR, dependencies,
                applicable(ArkansasValidationTax.reviewedVersions(), dependencies.stream().anyMatch(Dependency::targetedRothUsed)),
                List.of(), evidence);
        provenance.validatePayload(kind, payload);
        var captured = new Captured(payload, provenance, sources);
        cancellation.throwIfCancellationRequested();
        return new ObservedRun<>(result, payload, captured);
    }

    public static <T> ObservedRun<T> runDeterministicProductionV2(AnalysisKind kind, CompleteProjectionSnapshot source,
            UUID request, String fingerprint, Integer deathOverride,
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.Function<com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchCalculator, T> work,
            java.util.function.Function<? super T, ? extends AnalysisPayload> payloadFactory) {
        if (!source.arkansasHistory() || !source.calculationSemanticsCurrent()) {
            throw new IllegalArgumentException("This analysis requires a matching accepted Arkansas production projection.");
        }
        var engine = ProjectionEngine.forArkansasProduction();
        var observation = new Observation(source);
        var evidence = new ArkansasDeterministicEvidence.Observer(source, deathOverride, cancellation);
        var calculator = new com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchCalculator(
                new com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyEvaluator(engine), evidence);
        return engine.runArkansasAnalysis(cancellation, () -> {
            var result = engine.observeArkansasRuns((plan, context, execution) -> {
                observation.accept(execution);
                evidence.projected(plan, context, execution);
            }, () -> work.apply(calculator));
            cancellation.throwIfCancellationRequested();
            var payload = payloadFactory.apply(result);
            if (payload == null || !payload.successful()) return new ObservedRun<>(result, payload, null);
            var dependency = observation.dependency();
            var v2 = new ArkansasAnalysisEvidenceV2(request, fingerprint, ArkansasAnalysisEvidenceV2.CANONICAL.executionDigest(payload),
                    List.of(), List.of(), List.of(), null, evidence.finish(), null, null);
            var provenance = new ArkansasAnalysisProvenance(2, StateTaxJurisdiction.AR, List.of(dependency),
                    applicable(ArkansasValidationTax.reviewedVersions(), dependency.targetedRothUsed()), List.of(), v2);
            provenance.validatePayload(kind, payload);
            var captured = new Captured(payload, provenance, List.of(source));
            cancellation.throwIfCancellationRequested();
            return new ObservedRun<>(result, payload, captured);
        });
    }

    public static ObservedRun<com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis.Result>
            runIndividualMortalityProductionV2(CompleteProjectionSnapshot source, UUID request, String fingerprint,
                    com.daviddunn.retirementplanner.domain.model.RetirementPlan plan,
                    com.daviddunn.retirementplanner.domain.socialsecurity.analysis.IndividualLongevityScenarios mortality,
                    java.time.LocalDate valuation, BigDecimal discount,
                    com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener progress,
                    com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation) {
        if (!source.arkansasHistory() || !source.calculationSemanticsCurrent() || !source.matches(plan)) {
            throw new IllegalArgumentException("This analysis requires a matching accepted Arkansas production projection.");
        }
        var kind = AnalysisKind.INDIVIDUAL_WEIGHTED;
        var engine = ProjectionEngine.forArkansasProduction();
        var observation = new Observation(source);
        var evidence = new ArkansasIndividualMortalityEvidence.Observer(source, cancellation);
        var analysis = new com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis();
        return engine.runArkansasAnalysis(cancellation, () -> {
            var result = engine.observeArkansasRuns((inputs, context, execution) -> {
                observation.accept(execution);
                evidence.projected(inputs, context, execution);
            }, () -> analysis.calculate(plan, mortality, valuation, discount,
                    com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis.Mode.INTEGRATED,
                    progress, cancellation, engine, evidence));
            cancellation.throwIfCancellationRequested();
            var payload = new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(result));
            var dependency = observation.dependency();
            var v2 = new ArkansasAnalysisEvidenceV2(request, fingerprint, ArkansasAnalysisEvidenceV2.CANONICAL.executionDigest(payload),
                    List.of(), List.of(), List.of(), null, null, evidence.finish(), null);
            var provenance = new ArkansasAnalysisProvenance(2, StateTaxJurisdiction.AR, List.of(dependency),
                    applicable(ArkansasValidationTax.reviewedVersions(), dependency.targetedRothUsed()), List.of(), v2);
            provenance.validatePayload(kind, payload);
            var captured = new Captured(payload, provenance, List.of(source));
            cancellation.throwIfCancellationRequested();
            return new ObservedRun<>(result, payload, captured);
        });
    }

    public static <T> ObservedRun<T> runWeightedProductionV2(CompleteProjectionSnapshot source, UUID request,
            String fingerprint, com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.Function<com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonService, T> work,
            java.util.function.Function<? super T, ? extends AnalysisPayload> payloadFactory) {
        if (!source.arkansasHistory() || !source.calculationSemanticsCurrent()) {
            throw new IllegalArgumentException("This analysis requires a matching accepted Arkansas production projection.");
        }
        var engine = ProjectionEngine.forArkansasProduction();
        var observation = new Observation(source);
        var evidence = new ArkansasAnalysisEvidenceV2.Collector(source, cancellation);
        var comparison = new com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonService(engine, evidence);
        return engine.runArkansasAnalysis(cancellation, () -> {
            var result = engine.observeArkansasRuns((plan, context, execution) -> {
                observation.accept(execution);
                evidence.observe(plan, context, execution);
            }, () -> work.apply(comparison));
            cancellation.throwIfCancellationRequested();
            var payload = payloadFactory.apply(result);
            if (payload == null || !payload.successful()) return new ObservedRun<>(result, payload, null);
            var dependency = observation.dependency();
            var provenance = new ArkansasAnalysisProvenance(2, StateTaxJurisdiction.AR, List.of(dependency),
                    applicable(ArkansasValidationTax.reviewedVersions(), dependency.targetedRothUsed()),
                    List.of(), evidence.finish(request, fingerprint, payload));
            provenance.validatePayload(AnalysisKind.COUPLE_WEIGHTED, payload);
            var captured = new Captured(payload, provenance, List.of(source));
            cancellation.throwIfCancellationRequested();
            return new ObservedRun<>(result, payload, captured);
        });
    }

    public static <T> ObservedRun<T> runProduction(AnalysisKind kind, List<CompleteProjectionSnapshot> projections,
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.Function<ProjectionEngine, T> work,
            java.util.function.Function<? super T, ? extends AnalysisPayload> payloadFactory) {
        if (projections.isEmpty() || projections.stream().anyMatch(projection -> !projection.arkansasHistory()
                || !projection.calculationSemanticsCurrent())) {
            throw new IllegalArgumentException("This analysis requires matching accepted Arkansas production projections.");
        }
        var engine = ProjectionEngine.forArkansasProduction();
        return engine.runArkansasAnalysis(cancellation,
                () -> captureRun(engine, kind, projections, () -> work.apply(engine), payloadFactory));
    }

    /** Captures the payload instance that is eligible for acceptance, together with observations of the actual work. */
    public static <T> ObservedRun<T> captureRun(ProjectionEngine engine, AnalysisKind kind,
            List<CompleteProjectionSnapshot> projections, Supplier<T> work,
            java.util.function.Function<? super T, ? extends AnalysisPayload> payloadFactory) {
        var observed = new LinkedHashMap<String, Observation>();
        for (var projection : List.copyOf(projections)) {
            if (!projection.arkansasHistory() || observed.putIfAbsent(projection.inputDigest(), new Observation(projection)) != null) {
                throw new IllegalArgumentException("Arkansas analysis requires unambiguous frozen Arkansas projection inputs for each side.");
            }
        }
        var weightedPensions = new java.util.LinkedHashSet<WeightedPension>();
        var result = engine.observeArkansasRuns((plan, context, execution) -> {
            var input = CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan));
            var observation = observed.get(input);
            if (observation == null) throw new IllegalArgumentException("Arkansas analysis ran against different frozen inputs.");
            observation.accept(execution);
            if (kind == AnalysisKind.COUPLE_WEIGHTED && weightedPensions.size() <= 10_000) {
                capturePensions(plan.getHousehold(), context, execution, weightedPensions);
            }
        }, work);
        var payload = payloadFactory.apply(result);
        if (payload == null || !payload.successful()) return new ObservedRun<>(result, payload, null);
        if (weightedPensions.size() > 10_000) {
            throw new IllegalArgumentException("Arkansas weighted analysis exceeded the retained pension evidence limit. Previous successful results were retained.");
        }
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
        return new ObservedRun<>(result, payload, new Captured(payload, provenance, projections));
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
        if (version == 2) {
            executionEvidence.validatePayload(kind, payload);
            validateObservedCounts(payload);
            return;
        }
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

    public void validateRequest(UUID request, String fingerprint) {
        if (version == 2 && (!executionEvidence.request().equals(request) || !executionEvidence.fingerprint().equals(fingerprint))) {
            throw new IllegalArgumentException("Arkansas evidence belongs to a different analysis request.");
        }
    }

    private void validateObservedCounts(AnalysisPayload payload) {
        if (executionEvidence.paired() != null) {
            var paired = ((MonteCarloSnapshot) payload).paired();
            var proof = executionEvidence.paired();
            for (int index = 0; index < 2; index++) {
                boolean current = index == 0;
                var side = current ? proof.current() : proof.baseline();
                long complete = side.trials().stream().filter(t -> t.status() == ArkansasPairedEvidence.Status.COMPLETED).count();
                long shortages = side.trials().stream().filter(t -> t.status() == ArkansasPairedEvidence.Status.INSUFFICIENT_FUNDS).count();
                long years = paired.outcomes().stream().mapToLong(pair -> {
                    var history = (current ? pair.a() : pair.b()).history();
                    return history.sharedLength() + history.suffix().size();
                }).sum();
                validateCounts(dependencies.get(index), complete, shortages, years);
            }
            return;
        }
        if (dependencies.size() != 1) throw new IllegalArgumentException("Exactly one accepted Arkansas projection is required.");
        long completed;
        long shortages;
        long years;
        if (executionEvidence.deterministic() != null) {
            var candidates = executionEvidence.deterministic().candidates();
            completed = candidates.stream().filter(candidate -> candidate.metricsDigest() != null).count();
            shortages = candidates.stream().filter(candidate -> candidate.shortage() != null).count();
            years = candidates.stream().mapToLong(ArkansasDeterministicEvidence.Candidate::completedYears).sum();
        } else if (executionEvidence.individualMortality() != null) {
            var mortality = executionEvidence.individualMortality();
            completed = mortality.scenarios().stream().filter(scenario -> scenario.annualDigest() != null).count();
            shortages = 0;
            years = mortality.scenarios().stream().mapToLong(scenario -> scenario.deathYear() - (long) mortality.firstYear()).sum();
        } else {
            var executions = executionEvidence.executions();
            completed = executions.stream().filter(execution -> execution.shortage() == null).count();
            shortages = executions.size() - completed;
            years = executions.stream().mapToLong(ArkansasAnalysisEvidenceV2.Execution::completedYears).sum();
        }
        validateCounts(dependencies.getFirst(), completed, shortages, years);
    }

    private static void validateCounts(Dependency dependency, long complete, long shortages, long years) {
        if (dependency.completedRuns() != complete || dependency.insufficientFundsRuns() != shortages || dependency.completedYears() != years) {
            throw new IllegalArgumentException("Retained financial execution counts differ from their Arkansas dependency.");
        }
    }

    public void validateAssociation(UUID projectionId, String inputDigest) {
        var source = dependencies.getFirst();
        if (!source.projectionId().equals(projectionId) || !source.inputDigest().equals(inputDigest)) {
            throw new IllegalArgumentException("Arkansas analysis belongs to a different projection.");
        }
    }

    public void validateDependency(CompleteProjectionSnapshot projection) {
        if (projection.arkansasHistory() && !policiesMatch(projection.annual().arkansas().versions())) {
            throw new IllegalArgumentException("Arkansas analysis policy differs from its original retained projection.");
        }
        if (version == 2 && executionEvidence.paired() != null) {
            var pair = executionEvidence.paired();
            boolean matched = false;
            for (int i = 0; i < dependencies.size(); i++) {
                var dependency = dependencies.get(i);
                if (!dependency.projectionId().equals(projection.id())) continue;
                var side = i == 0 ? pair.current() : pair.baseline();
                validateIdentity(projection, dependency);
                side.source().validate(projection);
                matched = true;
            }
            if (!matched) throw new IllegalArgumentException("Missing original paired projection dependency.");
            return;
        }
        var source = dependencies.stream().filter(d -> d.projectionId().equals(projection.id())).findFirst().orElseThrow();
        if (!projection.arkansasHistory() || !source.inputDigest().equals(projection.inputDigest())
                || !source.annualDigest().equals(annualDigest(projection))) {
            throw new IllegalArgumentException("Arkansas analysis dependency differs from the original retained projection.");
        }
        if (version == 2) {
            if (projection.restoreInputs().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction() != StateTaxJurisdiction.AR)
                throw new IllegalArgumentException("Arkansas v2 evidence requires original Arkansas inputs.");
            if (executionEvidence.deterministic() != null) executionEvidence.deterministic().validateSource(projection);
            else if (executionEvidence.individualMortality() != null) executionEvidence.individualMortality().validateSource(projection);
            else executionEvidence.validateSource(projection.restoreInputs());
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

    private static void validateIdentity(CompleteProjectionSnapshot projection, Dependency dependency) {
        if (!dependency.inputDigest().equals(projection.inputDigest()) || !dependency.annualDigest().equals(annualDigest(projection))) {
            throw new IllegalArgumentException("Arkansas analysis dependency differs from the original retained projection.");
        }
    }

    static String annualDigest(CompleteProjectionSnapshot projection) { return annualDigest(projection.annual()); }

    private static String annualDigest(RetainedProjectionSnapshot annual) {
        try { return AnalysisSnapshotCodec.sha256(new RetainedProjectionCodec().encode(annual).bytes()); }
        catch (IOException invalid) { throw new IllegalArgumentException("Invalid Arkansas analyzer projection dependency.", invalid); }
    }
}
