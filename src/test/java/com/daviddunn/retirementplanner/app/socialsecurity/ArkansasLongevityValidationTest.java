package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationCell;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.interestEngine;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasLongevityValidationTest {
    @Test
    void actualEngineSupportsBoundedComparisonAndWeightedSurvivorContextsWithoutAcceptedHistory() throws Exception {
        var plan = Stage4TestPlans.plan();
        var primary = plan.getHousehold().getPrimaryPerson();
        primary.getIncomeSources().stream().filter(Pension.class::isInstance).toList().forEach(primary::removeIncomeSource);
        var input = CompleteProjectionSnapshot.freeze(plan);
        var engine = interestEngine("80000");
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator(engine);
        var strategy = Stage4TestPlans.strategy(plan);
        var early = new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy(
                62, strategy.spouseRetirementAge(),
                com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator
                        .calculateRetirementClaimDate(primary.getBirthDate(), 62),
                strategy.spouseRetirementClaimDate(), strategy.primarySurvivorElection(), strategy.spouseSurvivorElection());
        long start = System.nanoTime();
        var comparison = new IntegratedSocialSecurityStrategyComparisonService(evaluator).compare(plan, List.of(strategy, early));
        var explicit = evaluator.evaluate(plan, early);
        assertTrue(explicit.projection().getYears().stream().allMatch(y -> y.arkansasValidationTax().isPresent()));
        assertThrows(IllegalArgumentException.class, () -> RetainedProjectionSnapshot.capture(explicit));
        assertEquals(2, comparison.uniqueCandidateCount());
        System.out.println("Arkansas validation bounded Social Security comparison us=" + (System.nanoTime() - start) / 1000);

        var request = LongevityContinuationTest.request(plan,
                LongevityContinuationTest.mortality(plan, List.of(2031, 2032), List.of(2033)));
        var independent = new LongevityWeightedIntegratedStrategyEvaluator(engine).evaluate(request);
        var continued = new LongevityWeightedContinuationEvaluator(engine).evaluate(request);
        LongevityContinuationTest.financialEquals(independent, continued);
        assertEquals(input, CompleteProjectionSnapshot.freeze(plan));

        for (var continuation : List.of(false, true)) {
            var invalid = interestEngine("18000");
            var failure = assertThrows(IllegalStateException.class, () -> {
                if (continuation) new LongevityWeightedContinuationEvaluator(invalid).evaluate(request);
                else new LongevityWeightedIntegratedStrategyEvaluator(invalid).evaluate(request);
            });
            assertTrue(failure.getMessage().contains("No completed expected value"));
            assertInstanceOf(ArkansasProjectionUnavailableException.class, failure.getCause());
        }
    }

    @Test
    void retainsActualQuickAndWeightedContinuationResultsWithFrozenProjectionDependency(@TempDir Path folder) throws Exception {
        var plan = Stage4TestPlans.plan();
        var primary = plan.getHousehold().getPrimaryPerson();
        var pension = primary.getIncomeSources().stream().filter(Pension.class::isInstance).map(Pension.class::cast).findFirst().orElseThrow();
        pension.setSourceOrigin(com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var engine = interestEngine("80000");
        var owner = UUID.randomUUID();
        var started = Instant.parse("2026-01-01T00:00:00Z");
        var projection = CompleteProjectionSnapshot.captureArkansas(owner, plan, engine.project(plan), started);
        var strategy = Stage4TestPlans.strategy(plan);
        var supplied = new SocialSecurityMortalityWeightedStrategyValue(ZERO, ZERO, ZERO, ZERO, ZERO, 1);
        var quickCapture = ArkansasAnalysisProvenance.capture(engine, AnalysisKind.COUPLE_QUICK, List.of(projection), () -> {
            var quick = new IntegratedSocialSecurityStrategyComparisonService(new IntegratedSocialSecurityStrategyEvaluator(engine))
                    .compareAnalyzerCandidates(plan, List.of(new SocialSecuritySurvivorClaimingOptimizationCell(strategy, supplied)));
            return QuickComparisonSnapshot.captureArkansas(quick, List.of(1),
                    List.of(new QuickComparisonSnapshot.Candidate(1, WeightedSnapshots.Strategy.from(strategy), supplied)),
                    "a".repeat(64), UUID.randomUUID(), 1, "Original strategy");
        });
        var codec = new AnalysisSnapshotCodec();
        var fingerprints = new AnalysisFingerprintService();
        var quickInputs = new AnalysisFingerprintService.Inputs(null, null, null, null, null, 1, "a".repeat(64),
                List.of(new AnalysisFingerprintService.Candidate(WeightedSnapshots.Strategy.from(strategy), 1, supplied)), List.of(), null);
        var quickFingerprint = fingerprints.assess(AnalysisKind.COUPLE_QUICK, plan, quickInputs, null);
        assertTrue(quickFingerprint.valid());
        var quick = roundTrip(folder, owner, started, codec, AnalysisKind.COUPLE_QUICK, quickCapture, null, quickFingerprint);
        var detail = ((QuickComparisonSnapshot) quick.payload()).restore().entries().getFirst().integratedResult().orElseThrow();
        assertTrue(detail.projection().getFirstYear().arkansasValidationTax().orElseThrow().restoredHistory());
        quick.arkansasExecution().validateDependency(projection);
        assertEquals(2, quick.arkansasExecution().dependencies().getFirst().completedRuns());

        var request = LongevityContinuationTest.request(plan,
                LongevityContinuationTest.mortality(plan, List.of(2031, 2032), List.of(2033)));
        var weightedRequest = new LongevityWeightedIntegratedStrategyComparisonRequest(plan,
                List.of(strategy), request.longevityScenarios(), request.valuationDate(), request.realDiscountRate(), Optional.of(strategy),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of()), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var service = new LongevityWeightedIntegratedStrategyComparisonService(engine);
        var independent = service.compareExact(weightedRequest);
        var weightedCapture = ArkansasAnalysisProvenance.capture(engine, AnalysisKind.COUPLE_WEIGHTED, List.of(projection), () -> {
            var weighted = service.compare(weightedRequest);
            assertEquals(independent.rankedSuccessfulEntries().getFirst().aggregate().orElseThrow().expectedPvAfterTaxEstate(),
                    weighted.rankedSuccessfulEntries().getFirst().aggregate().orElseThrow().expectedPvAfterTaxEstate());
            return new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(weighted), null);
        });
        var baselineContext = new SnapshotContext.Baseline(List.of(), WeightedSnapshots.Strategy.from(strategy), "");
        var mortality = request.longevityScenarios().assumptions();
        var weightedInputs = new AnalysisFingerprintService.Inputs(mortality.primaryAdjustment().factor(), mortality.spouseAdjustment().factor(),
                mortality.mortalityBaseDate(), request.valuationDate(), request.realDiscountRate(), null, null, List.of(),
                baselineContext.fingerprintSections(), null);
        var weightedFingerprint = fingerprints.assess(AnalysisKind.COUPLE_WEIGHTED, plan, weightedInputs, null);
        assertTrue(weightedFingerprint.valid());
        var weighted = roundTrip(folder, owner, started, codec, AnalysisKind.COUPLE_WEIGHTED, weightedCapture, baselineContext, weightedFingerprint);
        var frozen = weighted.payload().couple().restore();
        assertTrue(frozen.work().continuationEvaluations() > 0);
        var baseline = frozen.baseline().orElseThrow();
        assertEquals(2, baseline.scenarioDetails().orElseThrow().size());
        assertEquals(2033, baseline.scenarioDetails().orElseThrow().getFirst().spouseDeathYear());
        weighted.arkansasExecution().validateDependency(projection);
        assertTrue(weighted.arkansasExecution().weightedPensions().stream().anyMatch(receipt ->
                receipt.recipient() == com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE));
        assertEquals(quick.arkansasExecution().dependencies().getFirst().annualDigest(),
                weighted.arkansasExecution().dependencies().getFirst().annualDigest());
        var changed = new HashMap<>(ArkansasValidationTax.reviewedVersions());
        changed.put("arkansas-roth-target", "future-target-policy");
        assertTrue(weighted.arkansasExecution().policiesMatch(changed));
        changed.put("arkansas-tax-funding", "future-funding-policy");
        assertFalse(weighted.arkansasExecution().policiesMatch(changed));
        changed = new HashMap<>(ArkansasValidationTax.reviewedVersions());
        changed.put("arkansas-income-tax-rules", "future-tax-policy");
        assertFalse(quick.arkansasExecution().policiesMatch(changed));
        assertThrows(IllegalStateException.class, () -> ArkansasAnalysisProvenance.capture(
                new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine(),
                AnalysisKind.COUPLE_QUICK, List.of(projection), quickCapture::payload));

        var historical = List.of(quick, weighted);
        var reference = new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest.Reference(projection.id(), "0".repeat(64));
        var associations = historical.stream().map(s -> new com.daviddunn.retirementplanner.app.projection.AnalyzerAssociation(
                1, owner, projection.id(), projection.inputDigest(), s.kind(), s.runId(), s.inputFingerprint(), s.contextFingerprint(),
                s.payloadDigest(), "0".repeat(64), s.completedAt())).toList();
        plan.assignPlanId(owner);
        plan.installProjectionComponents(Map.of(projection.id(), projection));
        plan.installAssociatedAnalyzerResults(Map.of(quick.runId(), quick, weighted.runId(), weighted));
        plan.setProjectionState(new com.daviddunn.retirementplanner.app.projection.ProjectionRoleManifest(2, owner, reference, null, associations));
        var repository = new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository();
        var file = folder.resolve("arkansas-integrated.json");
        repository.saveWithIdentity(plan, file, owner);
        var reopened = repository.load(file);
        var store = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents();
        var components = store.load(file, reopened.getProjectionState());
        for (var association : reopened.getProjectionState().associations()) {
            var saved = store.readAnalyzer(file, association);
            store.validateArkansasAnalyzer(saved, components.get(projection.id()), components);
            assertEquals(historical.stream().filter(s -> s.kind() == saved.kind()).findFirst().orElseThrow(), saved);
        }
        var policy = new AnalysisCompatibilityPolicy();
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, policy.assessArkansas(weighted, weightedFingerprint,
                weighted.algorithms(), weighted.references(), ArkansasValidationTax.reviewedVersions()).state());
        pension.setSourceOrigin(com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin.UNKNOWN);
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, policy.assessArkansas(weighted,
                fingerprints.assess(AnalysisKind.COUPLE_WEIGHTED, plan, weightedInputs, null),
                weighted.algorithms(), weighted.references(), ArkansasValidationTax.reviewedVersions()).state());
    }

    private static AnalysisSnapshot roundTrip(Path folder, UUID owner, Instant started, AnalysisSnapshotCodec codec,
            AnalysisKind kind, ArkansasAnalysisProvenance.Captured captured, SnapshotContext.Baseline baseline,
            AnalysisFingerprintService.Assessment fingerprint) throws Exception {
        var context = new SnapshotContext("Couple", "Primary", "Spouse", started, List.of(), List.of(), List.of(), Map.of(), baseline);
        var completion = new AcceptedAnalysisCompletion(owner, folder.resolve("plan.json"), kind, UUID.randomUUID(),
                started.plusSeconds(1), fingerprint.fingerprint(), fingerprint.contextFingerprint(), AnalysisVersions.calculatedAlgorithms(kind, captured.payload()),
                AnalysisVersions.references(kind), captured.payload(), context).withArkansasExecution(captured);
        var snapshot = completion.snapshot(codec);
        var encoded = codec.encode(snapshot);
        var restored = codec.decode(encoded);
        assertEquals(snapshot, restored);
        assertArrayEquals(encoded, codec.encode(restored));
        System.out.println("Arkansas " + kind + " rawBytes=" + codec.raw(restored).length + " gzipBytes=" + encoded.length);
        return restored;
    }
}
