package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PensionAlgorithmCompatibilityTest {
    static CompleteProjectionSnapshot snapshot(RetirementPlan plan, String provenance) {
        var projection = new ProjectionEngine().project(plan);
        var document = CompleteProjectionSnapshot.freeze(plan);
        return new CompleteProjectionSnapshot(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH,
                document, CompleteProjectionSnapshot.digest(document), provenance, RetainedProjectionSnapshot.capture(projection),
                projection.getYears().stream().map(y -> new CompleteProjectionSnapshot.NonInvestableYear(
                        y.getCalendarYear(), List.of(), BigDecimal.ZERO)).toList(),
                new CompleteProjectionSnapshot.Summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), BreakEvenPlanSummary.from(plan));
    }

    @Test void affectedLegacyProjectionRetainsIdentityAndNumbersButIsNotCurrent() throws Exception {
        var plan = PensionCashTaxConsistencyTest.survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        var old = snapshot(plan, "Accepted normal projection; original build version not recorded");
        var original = LifetimeProjectionTestSupport.json(old);
        assertTrue(old.matches(plan));
        assertTrue(old.matchesDeterministicInputs(plan), "Exact input identity is not algorithm applicability");
        assertFalse(old.calculationSemanticsCurrent());
        assertEquals(original, LifetimeProjectionTestSupport.json(old));
        var corrected = new CompleteProjectionSnapshot(old.version(), old.id(), old.owner(), old.sourcePlanId(), old.acceptedAt(),
                old.inputsDocument(), old.inputDigest(), PensionSelectionSemantics.PROVENANCE + old.provenance(),
                old.annual(), old.nonInvestable(), old.summary(), old.people());
        assertTrue(corrected.calculationSemanticsCurrent());
        assertEquals(old.annual(), corrected.annual());
        assertEquals(old.inputDigest(), corrected.inputDigest());
        assertEquals(old.id(), old.ownedBy(UUID.randomUUID()).id());
        assertFalse(old.ownedBy(UUID.randomUUID()).calculationSemanticsCurrent());
    }

    @Test void unaffectedOwnerAndInactivePensionsRemainApplicableWithoutNewProvenance() {
        var plan = PensionCashTaxConsistencyTest.survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        var person = plan.getHousehold().getPrimaryPerson();
        person.getIncomeSources().stream().filter(Pension.class::isInstance).toList().forEach(person::removeIncomeSource);
        assertFalse(PensionSelectionSemantics.potentiallyAffected(plan), "No pension");
        plan.getHousehold().getSpouse().addIncomeSource(new Pension("Living owner", AccountOwnership.SPOUSE,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("100"), BigDecimal.ZERO));
        assertFalse(PensionSelectionSemantics.potentiallyAffected(plan));
        assertTrue(snapshot(plan, "Legacy").calculationSemanticsCurrent());
        person.addIncomeSource(new Pension("Ended", AccountOwnership.PRIMARY, LocalDate.of(2029, 1, 1),
                LocalDate.of(2030, 12, 31), new BigDecimal("200"), BigDecimal.ZERO));
        assertFalse(PensionSelectionSemantics.potentiallyAffected(plan));
        person.addIncomeSource(new Pension("Future", AccountOwnership.PRIMARY, LocalDate.of(2040, 1, 1),
                null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2500")));
        assertFalse(PensionSelectionSemantics.potentiallyAffected(plan));
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void legacyAndCorrectedMonteCarloMapsDecodeStrictlyWithoutChangingPayloadOrFingerprints(boolean enabled, boolean paired) throws Exception {
        var plan = PensionCashTaxConsistencyTest.survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        if (enabled) {
            List.copyOf(plan.getHousehold().getExpenses()).forEach(plan.getHousehold()::removeExpense);
            plan.getHousehold().addExpense(new Expense("Synthetic", new BigDecimal("10000"), GrowthCategory.NONE,
                    null, null, ExpenseType.RECURRING, new BigDecimal(".10")));
        }
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
        MonteCarloSnapshot payload;
        if (paired) {
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                    a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), 1, a.getProjectionStartDate(), a.getStateTaxAssumptions()));
            assertFalse(PensionSelectionSemantics.potentiallyAffected(plan));
            assertTrue(PensionSelectionSemantics.potentiallyAffected(plan, 2030, 2034), "Comparison uses its own effective horizon");
            var unaffected = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
            PensionCashTaxConsistencyTest.removePensions(unaffected);
            var request = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("Current", unaffected),
                    new MonteCarloStrategyCandidate("Baseline", plan), new MonteCarloStrategyComparisonRequest.Fixed(settings,
                    plan.getPlanningAssumptions().getProjectionStartDate(), 2034, MonteCarloLifetime.couple(
                    new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.empty()))));
            var run = new MonteCarloStrategyComparisonRunService().run(new MonteCarloStrategyComparisonRunService.Prepared(
                    request, MonteCarloMode.FIXED_LIFESPAN, "Synthetic paired history"),
                    AnalysisProgressListener.none(), AnalysisCancellationToken.none());
            payload = MonteCarloSnapshotPresentationMapper.capture(run, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        } else {
            var run = new MonteCarloRunService().run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
            payload = MonteCarloSnapshotPresentationMapper.capture(run, plan, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        }
        var service = new MonteCarloFingerprintService();
        var fp = service.storedFingerprint(payload);
        var context = new SnapshotContext("Synthetic", "Primary", "Spouse", Instant.EPOCH,
                List.of(), List.of(), List.of(), Map.of());
        var codec = new AnalysisSnapshotCodec();
        var legacy = new AnalysisSnapshot(1, payload.fingerprintVersion(), UUID.randomUUID(), payload.kind(), payload.kind().shape(),
                UUID.randomUUID(), payload.completedAt(), "legacy-test", AnalysisVersions.algorithms(payload.kind(), payload),
                AnalysisVersions.references(payload.kind()), fp.fingerprint(), fp.contextFingerprint(), codec.digest(payload, context),
                payload, context, null);
        var bytes = codec.encode(legacy);
        assertEquals(legacy, codec.decode(bytes));
        var inputs = new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), payload.request());
        var applicability = service.compatibility(legacy, inputs);
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, applicability.state());
        var projectionId = UUID.randomUUID();
        var association = new AnalyzerAssociation(1, legacy.planId(), projectionId, "0".repeat(64), legacy.kind(), legacy.runId(),
                legacy.inputFingerprint(), legacy.contextFingerprint(), legacy.payloadDigest(), AnalysisSnapshotCodec.sha256(bytes), legacy.completedAt());
        var recorded = AnalysisResultFreshness.association(legacy, legacy.planId(), projectionId, "0".repeat(64), true, List.of(association));
        assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION, recorded, "An outdated algorithm does not change its recorded association");
        assertTrue(new AnalysisResultFreshness(applicability, recorded).status().startsWith("Historical Analysis"));
        var corrected = new AnalysisSnapshot(legacy.schemaVersion(), legacy.fingerprintVersion(), legacy.planId(), legacy.kind(),
                legacy.shape(), legacy.runId(), legacy.completedAt(), legacy.applicationBuild(),
                AnalysisVersions.calculatedAlgorithms(payload.kind(), payload), legacy.references(), legacy.inputFingerprint(),
                legacy.contextFingerprint(), legacy.payloadDigest(), legacy.payload(), legacy.context(), legacy.originPlanId());
        assertEquals(corrected, codec.decode(codec.encode(corrected)));
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, service.compatibility(corrected, inputs).state());
        assertEquals(legacy.payload(), corrected.payload());
        assertEquals(legacy.inputFingerprint(), corrected.inputFingerprint());
        assertEquals(legacy, codec.decode(bytes), "Assessing never rewrites the original artifact");
        assertFalse(AnalysisVersions.algorithms(payload.kind(), payload).containsKey(PensionSelectionSemantics.KEY));
        if (enabled) {
            var unknown = new TreeMap<>(corrected.algorithms());
            unknown.put(PensionSelectionSemantics.KEY, "unknown-v99");
            assertThrows(IllegalArgumentException.class, () -> new AnalysisSnapshot(legacy.schemaVersion(), legacy.fingerprintVersion(),
                    legacy.planId(), legacy.kind(), legacy.shape(), legacy.runId(), legacy.completedAt(), legacy.applicationBuild(),
                    unknown, legacy.references(), legacy.inputFingerprint(), legacy.contextFingerprint(), legacy.payloadDigest(),
                    legacy.payload(), legacy.context(), null));
        }
    }

    @Test void legacyIntegratedApplicabilityRequiresEvidenceButCorrectedAndLifetimeContractsRemainReadable() throws Exception {
        var fixture = new ExhaustiveSnapshotTest();
        var legacy = fixture.envelope(ExhaustiveSnapshotTest.payload(ExhaustiveSnapshotTest.actualResult()));
        var policy = new AnalysisCompatibilityPolicy();
        var unknown = new AnalysisFingerprintService.Assessment(legacy.inputFingerprint(), legacy.contextFingerprint(), null);
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED,
                policy.assess(legacy, unknown, legacy.algorithms(), legacy.references()).state());
        var unaffected = new AnalysisFingerprintService.Assessment(legacy.inputFingerprint(), legacy.contextFingerprint(), null, false);
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,
                policy.assess(legacy, unaffected, legacy.algorithms(), legacy.references()).state());
        var affected = new AnalysisFingerprintService.Assessment(legacy.inputFingerprint(), legacy.contextFingerprint(), null, true);
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED,
                policy.assess(legacy, affected, legacy.algorithms(), legacy.references()).state());
        var corrected = new AnalysisSnapshot(legacy.schemaVersion(), legacy.fingerprintVersion(), legacy.planId(), legacy.kind(),
                legacy.shape(), legacy.runId(), legacy.completedAt(), legacy.applicationBuild(),
                AnalysisVersions.calculatedAlgorithms(legacy.kind(), legacy.payload()), legacy.references(), legacy.inputFingerprint(),
                legacy.contextFingerprint(), legacy.payloadDigest(), legacy.payload(), legacy.context(), null);
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,
                policy.assess(corrected, affected, legacy.algorithms(), legacy.references()).state());
        var codec = new AnalysisSnapshotCodec();
        assertEquals(legacy, codec.decode(codec.encode(legacy)));
        assertEquals(corrected, codec.decode(codec.encode(corrected)));
        var weighted = com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures.snapshot(UUID.randomUUID());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, policy.assess(weighted,
                new AnalysisFingerprintService.Assessment(weighted.inputFingerprint(), weighted.contextFingerprint(), null, true),
                weighted.algorithms(), weighted.references()).state(), "Correctly selected lifetime calculations are unaffected");
    }
}
