package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloNumericCompatibilityTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @BeforeAll static void fixtures() { MonteCarloResultLifecycleTest.calculateIndependentFixturesOnce(); }

    @Test void allEightLegacyContractsReassessWithoutMutatingBytesOrFinancialInputs() throws Exception {
        var codec = new AnalysisSnapshotCodec(); var service = new MonteCarloFingerprintService();
        for (var frozen : MonteCarloResultLifecycleTest.FIXTURES.values()) {
            var binding = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), Path.of("target/compatibility-plan.json"), frozen.kind());
            var saved = MonteCarloResultLifecycle.completion(binding, frozen).presentationSnapshot(codec);
            var bytes = codec.encode(saved); var restored = codec.decode(bytes);
            assertEquals(service.storedV1(frozen).fingerprint(), restored.inputFingerprint());
            var s = frozen.settings();
            var scaled = new MonteCarloSnapshot.Settings(s.trials(), s.seed(), scale(s.expectedReturn()), scale(s.volatility()), s.inflation());
            var request = scaledMortality(frozen.request(), false);
            var next = new MonteCarloFingerprintService.Inputs(frozen.kind(), scaled, request);
            assertNotEquals(service.storedV1(frozen).fingerprint(), service.assess(next, MonteCarloFingerprintService.Contract.EXACT_V1).fingerprint());
            assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, service.compatibility(restored, next).state());
            var changed = new MonteCarloSnapshot.Settings(s.trials(), s.seed(), s.expectedReturn().add(new BigDecimal("0.01")), s.volatility(), s.inflation());
            assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(restored,
                    new MonteCarloFingerprintService.Inputs(frozen.kind(), changed, request)).state());
            var seed = new MonteCarloSnapshot.Settings(s.trials(), s.seed() + 1, s.expectedReturn(), s.volatility(), s.inflation());
            assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(restored,
                    new MonteCarloFingerprintService.Inputs(frozen.kind(), seed, request)).state());
            if (request.mortality() != null) assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(restored,
                    new MonteCarloFingerprintService.Inputs(frozen.kind(), scaled, scaledMortality(frozen.request(), true))).state());
            // Financial plan decimal scale remains significant in both compatibility versions.
            var plan = MonteCarloPlanDocument.read(request.planA());
            var account = plan.getAccountPortfolio().getAccounts().getFirst(); account.setCurrentBalance(scale(account.getCurrentBalance()));
            var financial = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(plan), request.planB(), request.labelA(), request.labelB(),
                    request.start(), request.endingYear(), request.fixedLife(), request.mortality());
            assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(restored,
                    new MonteCarloFingerprintService.Inputs(frozen.kind(), scaled, financial)).state());
            if (request.planB() != null) {
                var baseline = MonteCarloPlanDocument.read(request.planB());
                var b = baseline.getAccountPortfolio().getAccounts().getFirst(); b.setCurrentBalance(scale(b.getCurrentBalance()));
                var baselineScale = new MonteCarloSnapshot.Request(request.planA(), MonteCarloPlanDocument.capture(baseline), request.labelA(), request.labelB(),
                        request.start(), request.endingYear(), request.fixedLife(), request.mortality());
                assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(restored,
                        new MonteCarloFingerprintService.Inputs(frozen.kind(), scaled, baselineScale)).state());
            }
            assertEquals(AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION, service.compatibility(restored, null).state());
            assertFalse(service.assess(new MonteCarloFingerprintService.Inputs(frozen.kind(), null, request)).valid());
            var report = frozen.paired() == null ? MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreRun(frozen))
                    : MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreComparison(frozen));
            new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(
                    MonteCarloHistoricalPdf.from(report, frozen, MonteCarloHistoricalPdf.Currency.CURRENT, "Saved", false),
                    directory.resolve(frozen.kind() + ".pdf"));
            var afterRestart = new AnalysisSnapshotCodec().decode(bytes);
            assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new MonteCarloFingerprintService().compatibility(afterRestart, next).state());
            assertEquals(frozen.completedAt(), afterRestart.completedAt());
            assertArrayEquals(bytes, codec.encode(restored));
            assertEquals(frozen, restored.payload());
            var bad = new AnalysisSnapshot(saved.schemaVersion(), saved.fingerprintVersion(), saved.planId(), saved.kind(), saved.shape(), saved.runId(),
                    saved.completedAt(), saved.applicationBuild(), saved.algorithms(), saved.references(), "0".repeat(64), saved.contextFingerprint(),
                    saved.payloadDigest(), saved.payload(), saved.context(), saved.originPlanId());
            assertEquals(AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE, service.compatibility(bad, next).state());
        }
    }

    @Test void temporaryNumericComparisonIsExactAndDoesNotConflateOptionalInflation() {
        var s = MonteCarloResultLifecycleTest.FIXTURES.values().iterator().next();
        var service = new MonteCarloFingerprintService();
        var a = new MonteCarloSnapshot.Settings(5000, 417, new BigDecimal("0.05"), new BigDecimal("0.12"),
                new MonteCarloInflationSettings(new BigDecimal("0.0275"), new BigDecimal("0.0175"), new BigDecimal("-0.02")));
        var b = new MonteCarloSnapshot.Settings(5000, 417, new BigDecimal("0.050"), new BigDecimal("0.1200"),
                new MonteCarloInflationSettings(new BigDecimal("0.02750"), new BigDecimal("0.017500"), new BigDecimal("-0.0200")));
        var aa = new MonteCarloFingerprintService.Inputs(s.kind(), a, s.request());
        var bb = new MonteCarloFingerprintService.Inputs(s.kind(), b, s.request());
        assertEquals(service.assess(aa), service.assess(bb));
        var absent = new MonteCarloSnapshot.Settings(5000, 417, a.expectedReturn(), a.volatility(), null);
        assertNotEquals(service.assess(aa), service.assess(new MonteCarloFingerprintService.Inputs(s.kind(), absent, s.request())));
    }

    private static BigDecimal scale(BigDecimal value) { return value.setScale(value.scale() + 1); }
    private static MonteCarloSnapshot.Request scaledMortality(MonteCarloSnapshot.Request r, boolean change) {
        var m = r.mortality(); if (m == null) return r;
        var p = m.primary(); var c = m.coupleInputs();
        var adjustment = SocialSecurityMortalityAdjustment.of(change ? new BigDecimal("1.01") : scale(p.mortalityAdjustment().factor()));
        var primary = new SocialSecurityMortalityDistributionRequest(p.dateOfBirth(), p.mortalityBaseDate(), p.mortalityCategory(), adjustment);
        var couple = c == null ? null : new AnalyzerLongevityAssumptions(c.primaryCategory(), adjustment, c.spouseCategory(),
                SocialSecurityMortalityAdjustment.of(scale(c.spouseAdjustment().factor())), c.mortalityBaseDate(), c.tableMetadata(), c.partialYearConvention());
        return new MonteCarloSnapshot.Request(r.planA(), r.planB(), r.labelA(), r.labelB(), r.start(), r.endingYear(), r.fixedLife(),
                new MonteCarloSnapshot.MortalityInputs(primary, couple, m.table(), m.entries(), m.survivorAge()));
    }
}
