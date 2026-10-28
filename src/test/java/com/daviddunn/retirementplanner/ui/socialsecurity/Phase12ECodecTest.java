package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Phase12ECodecTest {
    @BeforeAll static void prepare() throws Exception { CoupleSocialSecurityPersistenceTest.prepare(); }
    static AnalysisSnapshot snapshot(AnalysisKind kind, AnalysisPayload payload, SnapshotContext context) {
        return new AcceptedAnalysisCompletion(UUID.randomUUID(), null, kind, UUID.randomUUID(), Instant.now(), "a".repeat(64), "b".repeat(64),
                AnalysisVersions.algorithms(kind), AnalysisVersions.references(kind), payload, context).presentationSnapshot(new AnalysisSnapshotCodec());
    }
    @Test void quickCompleteCandidateGraphsAndOriginalReportRoundTrip() throws Exception {
        var selected = SocialSecurityStrategyAnalyzerPresentation.from(CoupleSocialSecurityPersistenceTest.result).rankedStrategies().stream().limit(3).toList();
        var model = SocialSecurityAndQuickPdfTest.comparison(selected);
        var started = Instant.now().minusSeconds(3);
        var prepared = new QuickReportSession.Prepared(UUID.randomUUID(), UUID.randomUUID(), new IntegratedReportContext("Original", "Primary", "Spouse", started,
                List.of(new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section("Original assumptions", List.of("Frozen"))), "Original baseline"),
                "c".repeat(64), UUID.randomUUID(), selected.size(), selected.stream().map(s -> new QuickReportSession.Candidate(s.rank(), s.cell().strategy(), s.cell().expectedValue())).toList(), List.of());
        var report = QuickReportSession.accepted(prepared, model, Instant.now());
        var payload = QuickSnapshotAdapter.capture(model, report); payload.validate(AnalysisKind.COUPLE_QUICK);
        var codec = new AnalysisSnapshotCodec(); var original = snapshot(AnalysisKind.COUPLE_QUICK, payload, QuickSnapshotAdapter.context(report));
        var restored = codec.decode(codec.encode(original));
        assertEquals(original, restored); assertArrayEquals(codec.encode(original), codec.encode(restored));
        var values = (QuickComparisonSnapshot) restored.payload();
        assertEquals(model.comparison().currentPlanBaseline().metrics(), values.restore().currentPlanBaseline().metrics());
        for (int i=0; i<model.rows().size(); i++) {
            var a=model.rows().get(i).entry(); var b=values.restore().entries().get(i);
            assertEquals(a.strategy(), b.strategy()); assertEquals(a.socialSecurityOnlyExpectedValue(), b.socialSecurityOnlyExpectedValue());
            assertEquals(a.differencesFromCurrentPlan(), b.differencesFromCurrentPlan()); assertEquals(a.failure(), b.failure());
            assertEquals(a.integratedResult().map(IntegratedSocialSecurityStrategyResult::metrics), b.integratedResult().map(IntegratedSocialSecurityStrategyResult::metrics));
        }
        var historical = QuickSnapshotAdapter.report(restored);
        assertEquals(report.prepared().financial(), historical.prepared().financial());
        assertEquals(report.prepared().candidates(), historical.prepared().candidates());
        assertEquals(report.rows(), historical.rows());
    }
    @Test void actualIndividualNineStrategyResultsAndExistingBreakEvenValuesRoundTrip() throws Exception {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var request = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var original = SinglePersonIntegratedAnalysis.calculate(request, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var payload = IndividualDeterministicSnapshot.capture(original); payload.validate(AnalysisKind.INDIVIDUAL_DETERMINISTIC);
        var codec = new AnalysisSnapshotCodec(); var snapshot = snapshot(AnalysisKind.INDIVIDUAL_DETERMINISTIC, payload, SnapshotFixtures.individualContext(plan));
        var restored = codec.decode(codec.encode(snapshot)); assertEquals(snapshot, restored);
        var actual = ((IndividualDeterministicSnapshot) restored.payload()).restore();
        assertEquals(original.breakEvenByClaimingAge(), actual.breakEvenByClaimingAge());
        assertEquals(original.result().currentPlanBaseline().metrics(), actual.result().currentPlanBaseline().metrics());
        for (int i=0; i<original.result().entries().size(); i++) {
            var a=original.result().entries().get(i); var b=actual.result().entries().get(i);
            assertEquals(a.strategy(), b.strategy()); assertEquals(a.metrics(), b.metrics()); assertEquals(a.differencesFromCurrentPlan(), b.differencesFromCurrentPlan());
            assertEquals(a.afterTaxEstateRank(), b.afterTaxEstateRank());
            assertEquals(a.retainedDetail().map(RetainedProjectionSnapshot::capture), b.retainedDetail().map(RetainedProjectionSnapshot::capture));
        }
    }
    @Test void genuinelyCalculatedQuickCutoffTiesRetainEveryProjectionAndFitExistingBounds() throws Exception {
        var selected = new IntegratedSocialSecurityCandidateSelector().select(
                SocialSecurityStrategyAnalyzerPresentation.from(CoupleSocialSecurityPersistenceTest.result).rankedStrategies(), 20);
        assertTrue(selected.size() > 20, "The production selector retains cutoff ties");
        var plan = ExhaustivePersistenceTest.plan();
        var p = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(p.getEconomicAssumptions(), p.getTaxAssumptions(),
                p.getWithdrawalAssumptions(), p.getDeathScenarioAssumptions(), 40, p.getProjectionStartDate(), p.getStateTaxAssumptions()));
        var result = new IntegratedSocialSecurityStrategyComparisonService().compareAnalyzerCandidates(plan, selected.stream().map(SocialSecurityStrategyAnalyzerPresentation.RankedStrategy::cell).toList());
        var model = IntegratedSocialSecurityComparisonPresentation.from(result, selected);
        var context = IntegratedAnalyzerPdfReportTest.context(true);
        var session = QuickReportSession.accepted(new QuickReportSession.Prepared(UUID.randomUUID(), UUID.randomUUID(), context, "a".repeat(64), UUID.randomUUID(), 20,
                selected.stream().map(s -> new QuickReportSession.Candidate(s.rank(), s.cell().strategy(), s.cell().expectedValue())).toList(), List.of()), model, Instant.now());
        var codec = new AnalysisSnapshotCodec(); var source = snapshot(AnalysisKind.COUPLE_QUICK, QuickSnapshotAdapter.capture(model, session), QuickSnapshotAdapter.context(session));
        long start = System.nanoTime(); var encoded = codec.encodeMeasured(source); long encodedAt = System.nanoTime();
        var decoded = codec.decode(encoded.bytes()); long decodedAt = System.nanoTime();
        var restored = ((QuickComparisonSnapshot) decoded.payload()).restore(); long restoredAt = System.nanoTime();
        assertEquals(result.entries().size(), restored.entries().size()); assertEquals(result.suppliedCandidateCount(), restored.suppliedCandidateCount());
        for (int i=0; i<result.entries().size(); i++) {
            var a=result.entries().get(i); var b=restored.entries().get(i);
            assertEquals(a.strategy(),b.strategy()); assertEquals(a.differencesFromCurrentPlan(),b.differencesFromCurrentPlan());
            assertEquals(a.integratedResult().map(RetainedProjectionSnapshot::capture), b.integratedResult().map(RetainedProjectionSnapshot::capture));
            assertEquals(a.failure(),b.failure());
        }
        var out = java.nio.file.Path.of("target/phase12e-previews"); java.nio.file.Files.createDirectories(out);
        java.nio.file.Files.writeString(out.resolve("quick-capacity.txt"), "Production-engine fixture; requested=20; supplied=" + result.suppliedCandidateCount()
                + "; unique=" + result.entries().size() + "; years=40; accounts=" + plan.getAccountPortfolio().getAccounts().size()
                + "; rawBytes=" + encoded.rawBytes() + "; gzipBytes=" + encoded.bytes().length + "; encodeMs=" + (encodedAt-start)/1_000_000
                + "; decodeMs=" + (decodedAt-encodedAt)/1_000_000 + "; reconstructionMs=" + (restoredAt-decodedAt)/1_000_000);
    }
    @Test void quickRejectsMissingCandidatesChangedReferencesVersionsAndCorruptedDigest() throws Exception {
        var selected = SocialSecurityStrategyAnalyzerPresentation.from(CoupleSocialSecurityPersistenceTest.result).rankedStrategies().stream().limit(3).toList();
        var model = SocialSecurityAndQuickPdfTest.comparison(selected);
        var q = QuickComparisonSnapshot.capture(model.comparison(), selected.stream().map(SocialSecurityStrategyAnalyzerPresentation.RankedStrategy::rank).toList(),
                selected.stream().map(s -> new QuickComparisonSnapshot.Candidate(s.rank(), WeightedSnapshots.Strategy.from(s.cell().strategy()), s.cell().expectedValue())).toList(), "a".repeat(64), UUID.randomUUID(),3,"Original");
        assertThrows(IllegalArgumentException.class, () -> new QuickComparisonSnapshot(2,q.baseline(),q.supplied(),q.entries(),q.socialSecurityRanks(),q.candidates(),q.prerequisiteFingerprint(),q.prerequisiteRun(),q.requested(),q.baselineDescription()));
        var missing = new QuickComparisonSnapshot(1,q.baseline(),q.supplied(),q.entries().subList(0,2),q.socialSecurityRanks().subList(0,2),q.candidates(),q.prerequisiteFingerprint(),q.prerequisiteRun(),q.requested(),q.baselineDescription());
        assertThrows(IllegalArgumentException.class,missing::restore);
        var altered = new ArrayList<>(q.entries()); var first=altered.getFirst();
        altered.set(0,new QuickComparisonSnapshot.Entry(0,first.strategy(),first.expected(),first.detail(),first.differences(),first.failure()));
        assertThrows(IllegalArgumentException.class, () -> new QuickComparisonSnapshot(1,q.baseline(),q.supplied(),altered,q.socialSecurityRanks(),q.candidates(),q.prerequisiteFingerprint(),q.prerequisiteRun(),q.requested(),q.baselineDescription()).restore());
        var codec=new AnalysisSnapshotCodec(); var original=snapshot(AnalysisKind.COUPLE_QUICK,q,SnapshotFixtures.context());
        var corrupt=new AnalysisSnapshot(1,1,original.planId(),original.kind(),original.shape(),original.runId(),original.completedAt(),original.applicationBuild(),original.algorithms(),original.references(),original.inputFingerprint(),original.contextFingerprint(),"f".repeat(64),q,original.context(),null);
        assertThrows(java.io.IOException.class,()->codec.encode(corrupt));
    }
    @Test void historicalExhaustiveCrossReferenceUsesEveryOriginalNumericalValue() throws Exception {
        ExhaustivePersistenceTest.fixture();
        var result=ExhaustivePersistenceTest.result; var model=ExhaustiveIntegratedSearchPresentation.from(result,Duration.ZERO,3);
        var original=result.rankedSuccessfulEntries().getLast();
        var payload=ExhaustiveResultSnapshots.capture(model,IntegratedAnalyzerPdfReportTest.context(true),
                new ExhaustiveResultSnapshots.SocialSecurityBasis(WeightedSnapshots.Strategy.from(original.strategy()),new java.math.BigDecimal("12345.0012300"),7L,UUID.randomUUID(),"a".repeat(64)),
                Instant.now(),"PERCENT_OF_OPTIMAL","heat-map");
        var reference=payload.socialSecurityReference(); assertEquals(ExhaustiveSnapshot.ReferenceStatus.AVAILABLE,reference.status());
        var text=ExhaustiveResultSnapshots.crossReferenceText(reference);
        assertTrue(text.contains(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(reference.expectedPresentValue())));
        assertTrue(text.contains(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(reference.estate())));
        assertTrue(text.contains("estate rank "+reference.rank()));
        assertTrue(text.contains(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(reference.gapFromHighest())));
    }
}
