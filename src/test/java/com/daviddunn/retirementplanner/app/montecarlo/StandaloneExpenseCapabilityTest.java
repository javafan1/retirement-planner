package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StandaloneExpenseCapabilityTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    static final Instant START = Instant.parse("2026-11-05T12:00:00Z");

    static void enable(RetirementPlan plan, String volatility) {
        var old = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount(), old.getGrowthCategory(),
                old.getStartDate(), old.getEndDate(), old.getExpenseType(), new BigDecimal(volatility)));
    }

    /** Structural codec fixture only; these aggregate values do not claim an enabled financial oracle. */
    static MonteCarloSnapshot enabledFixture(boolean couple, boolean longevity) {
        var plan = StochasticExpenseMonteCarloTest.plan(couple);
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, new BigDecimal("0.12"));
        var run = !longevity ? new MonteCarloRunService().run(plan, settings, null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                : new MonteCarloRunService().runMortality(plan, StochasticExpenseMonteCarloTest.mortality(plan, settings),
                        AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var legacy = MonteCarloSnapshotPresentationMapper.capture(run, plan, START, START.plusSeconds(1));
        enable(plan, "0.25");
        String document = MonteCarloPlanDocument.capture(plan);
        var r = legacy.request();
        var request = new MonteCarloSnapshot.Request(document, r.planB(), r.labelA(), r.labelB(), r.start(),
                r.endingYear(), r.fixedLife(), r.mortality());
        return new MonteCarloSnapshot(2, legacy.mode(), legacy.hasSpouse(), legacy.settings(), request,
                legacy.provenance(), legacy.fixed(), legacy.mortality(), legacy.paired(), legacy.display(), START,
                START.plusSeconds(1), ExpenseExecution.fromDocument(document));
    }

    static AnalysisSnapshot envelope(MonteCarloSnapshot payload) {
        return MonteCarloResultLifecycle.completion(new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), null, payload.kind()), payload)
                .presentationSnapshot(new AnalysisSnapshotCodec());
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void fourStandaloneCapabilityShapesRoundTripWithExactIdentity(boolean couple, boolean longevity) throws Exception {
        var payload = enabledFixture(couple, longevity);
        var saved = envelope(payload);
        var codec = new AnalysisSnapshotCodec();
        var restored = codec.decode(codec.encode(saved));
        assertEquals(saved, restored);
        assertEquals(1, saved.schemaVersion()); assertEquals(3, saved.fingerprintVersion());
        assertEquals(2, payload.version());
        assertEquals(ExpenseExecution.ALGORITHM, saved.algorithms().get("recurring-expenses"));
        assertEquals(saved.inputFingerprint(), new MonteCarloFingerprintService().storedFingerprint((MonteCarloSnapshot) restored.payload()).fingerprint());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new MonteCarloFingerprintService().compatibility(restored,
                new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), payload.request())).state());
    }

    @Test
    void legacyWireOmitsNewCapabilityAndRemainsExactlyReencodable() throws Exception {
        var plan = StochasticExpenseMonteCarloTest.plan(true);
        var run = new MonteCarloRunService().run(plan, MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO), null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var payload = MonteCarloSnapshotPresentationMapper.capture(run, plan, START, START.plusSeconds(1));
        var completed = envelope(payload);
        // Explicit historical map: fresh completions now carry the B0 pension semantic.
        var saved = new AnalysisSnapshot(completed.schemaVersion(), completed.fingerprintVersion(), completed.planId(), completed.kind(),
                completed.shape(), completed.runId(), completed.completedAt(), completed.applicationBuild(),
                AnalysisVersions.algorithms(completed.kind(), payload), completed.references(), completed.inputFingerprint(),
                completed.contextFingerprint(), completed.payloadDigest(), completed.payload(), completed.context(), completed.originPlanId());
        var codec = new AnalysisSnapshotCodec();
        byte[] original = codec.raw(saved);
        assertFalse(new String(original, java.nio.charset.StandardCharsets.UTF_8).contains("expenseExecution"));
        assertEquals(1, saved.fingerprintVersion());
        assertEquals(AnalysisVersions.algorithms(saved.kind()), saved.algorithms());
        assertArrayEquals(original, codec.raw(codec.decode(codec.encode(saved))));
        assertEquals(new MonteCarloFingerprintService().storedV1(payload).fingerprint(), saved.inputFingerprint());
    }

    @Test
    void rejectsMissingCapabilitiesWrongBindingsAndEnvelopeVersionMixes() throws Exception {
        var payload = enabledFixture(true, false);
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloSnapshot(2, payload.mode(), true,
                payload.settings(), payload.request(), payload.provenance(), payload.fixed(), null, null, payload.display(), START, START.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloSnapshot(1, payload.mode(), true,
                payload.settings(), payload.request(), payload.provenance(), payload.fixed(), null, null, payload.display(), START, START.plusSeconds(1), payload.expenseExecution()));
        var plan = MonteCarloPlanDocument.read(payload.request().planA()); enable(plan, "0.17");
        assertThrows(IllegalArgumentException.class, () -> payload.expenseExecution().validate(MonteCarloPlanDocument.capture(plan)));
        var saved = envelope(payload);
        assertThrows(IllegalArgumentException.class, () -> new AnalysisSnapshot(1, 1, saved.planId(), saved.kind(), saved.shape(),
                saved.runId(), saved.completedAt(), saved.applicationBuild(), saved.algorithms(), saved.references(), saved.inputFingerprint(),
                saved.contextFingerprint(), saved.payloadDigest(), saved.payload(), saved.context(), null));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisSnapshot(1, 3, saved.planId(), saved.kind(), saved.shape(),
                saved.runId(), saved.completedAt(), saved.applicationBuild(), AnalysisVersions.algorithms(saved.kind()), saved.references(), saved.inputFingerprint(),
                saved.contextFingerprint(), saved.payloadDigest(), saved.payload(), saved.context(), null));
        var codec = new AnalysisSnapshotCodec();
        var tree = new ObjectMapper().readTree(codec.raw(saved));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("payload")).remove("settings");
        byte[] missing = gzip(tree.toString());
        assertThrows(java.io.IOException.class, () -> codec.decode(missing));
        tree = new ObjectMapper().readTree(codec.raw(saved));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("payload")).put("unknownCapability", true);
        byte[] unknown = gzip(tree.toString());
        assertThrows(java.io.IOException.class, () -> codec.decode(unknown));
    }

    static byte[] gzip(String document) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(bytes)) { gzip.write(document.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        return bytes.toByteArray();
    }

    @Test
    void rejectsTamperedEnabledContractsWithoutWeakeningDigestOrStrictFieldChecks() throws Exception {
        var codec = new AnalysisSnapshotCodec();
        var saved = envelope(enabledFixture(true, false));
        for (String field : java.util.List.of("dimension", "samplingVersion", "correspondenceVersion", "version")) {
            var tree = new ObjectMapper().readTree(codec.raw(saved));
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/payload/expenseExecution")).put(field, 99);
            assertThrows(java.io.IOException.class, () -> codec.decode(gzip(tree.toString())), field);
        }
        for (String path : java.util.List.of("/payload/expenseExecution/planDigest", "/payloadDigest", "/inputFingerprint", "/contextFingerprint")) {
            var tree = new ObjectMapper().readTree(codec.raw(saved));
            int split = path.lastIndexOf('/');
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at(path.substring(0, split)))
                    .put(path.substring(split + 1), "0".repeat(64));
            assertThrows(java.io.IOException.class, () -> codec.decode(gzip(tree.toString())), path);
        }
        var tree = new ObjectMapper().readTree(codec.raw(saved));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/payload/expenseExecution")).put("unknown", true);
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip(tree.toString())));
    }

    @Test
    void unprovenLegacyPositiveArtifactIsPreservedButNeverReinterpretedAsEnabled() throws Exception {
        var enabled = enabledFixture(false, false);
        var legacy = new MonteCarloSnapshot(1, enabled.mode(), false, enabled.settings(), enabled.request(),
                enabled.provenance(), enabled.fixed(), null, null, enabled.display(), START, START.plusSeconds(1));
        var saved = envelope(legacy); var codec = new AnalysisSnapshotCodec();
        assertEquals(saved, codec.decode(codec.encode(saved)), "Preserve the artifact without claiming stochastic execution");
        assertThrows(IllegalArgumentException.class, () -> MonteCarloSnapshotPresentationMapper.restoreRun(legacy));
        assertEquals(AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE, new MonteCarloFingerprintService().compatibility(saved,
                new MonteCarloFingerprintService.Inputs(legacy.kind(), legacy.settings(), legacy.request())).state());
    }

    @Test
    void expenseFinancialFingerprintDoesNotAccidentallyDependOnDisplayNamesOrPlanIdentity() throws Exception {
        var payload = enabledFixture(false, false);
        var json = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload.request().planA());
        tree.put("planId", UUID.randomUUID().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/household/primaryPerson")).put("firstName", "Another synthetic name");
        var r = payload.request();
        var changed = new MonteCarloSnapshot.Request(tree.toString(), null, r.labelA(), null, r.start(), r.endingYear(), r.fixedLife(), null);
        var service = new MonteCarloFingerprintService();
        var original = service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), r));
        var renamed = service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), changed));
        assertTrue(renamed.valid());
        assertEquals(original, renamed);
    }

    @Test
    void failedEnabledAtomicVerificationPreservesPreviousArtifactAndSuccessfulRetryCreatesBackup() throws Exception {
        var saved = envelope(enabledFixture(true, false));
        var failure = new java.util.concurrent.atomic.AtomicBoolean();
        var repository = com.daviddunn.retirementplanner.persistence.AnalysisSnapshotFaults.failVerification(directory, failure::get);
        repository.save(saved);
        var path = repository.path(saved.planId(), saved.kind());
        byte[] original = java.nio.file.Files.readAllBytes(path);
        failure.set(true);
        assertThrows(java.io.IOException.class, () -> repository.save(saved));
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(path));
        assertEquals(saved, repository.load(saved.planId(), saved.kind()).orElseThrow());
        failure.set(false); repository.save(saved);
        assertEquals(saved, repository.load(saved.planId(), saved.kind()).orElseThrow());
        assertEquals(saved, repository.loadBackup(saved.planId(), saved.kind()).orElseThrow());
        var otherOwner = UUID.randomUUID();
        var wrongSlot = repository.path(otherOwner, saved.kind());
        java.nio.file.Files.createDirectories(wrongSlot.getParent());
        java.nio.file.Files.write(wrongSlot, original);
        assertThrows(java.io.IOException.class, () -> repository.load(otherOwner, saved.kind()));
    }

    @Test
    void expenseFingerprintDetectsVolatilityAndRetainsNumericSettingEquivalence() {
        var payload = enabledFixture(false, false);
        var service = new MonteCarloFingerprintService();
        var original = new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), payload.request());
        var settings = payload.settings();
        var reformatted = new MonteCarloSnapshot.Settings(settings.trials(), settings.seed(), settings.expectedReturn().setScale(6),
                settings.volatility().setScale(6), settings.inflation());
        assertEquals(service.assess(original).fingerprint(), service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), reformatted, payload.request())).fingerprint());
        var plan = MonteCarloPlanDocument.read(payload.request().planA()); enable(plan, "0.17");
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(envelope(payload),
                MonteCarloFingerprintService.standalone(plan, settings.restore(), null)).state());
    }
}
