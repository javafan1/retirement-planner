package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StandaloneExpenseCapabilityTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PairedExpenseCapabilityTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    static Stream<Arguments> enabledMatrix() {
        return Stream.of(true, false).flatMap(couple -> Stream.of(true, false).flatMap(longevity ->
                Stream.of(new String[]{".25", "0"}, new String[]{"0", ".17"}, new String[]{".25", ".17"})
                        .map(rates -> Arguments.of(couple, longevity, rates[0], rates[1]))));
    }

    /** Structural checkpoint fixture only; aggregate financial evidence comes from the execution tests. */
    static MonteCarloSnapshot fixture(boolean couple, boolean longevity, String aRate, String bRate) {
        var a = plan(couple); var b = plan(couple);
        var settings = MonteCarloSettings.forPlan(a, 2, 417, new BigDecimal(".12"));
        int last = a.getPlanningAssumptions().getProjectionStartDate().getYear() + a.getPlanningAssumptions().getProjectionLengthYears() - 1;
        var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison(a, b, settings, longevity, last));
        var legacy = MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN, "Structural fixture only", 0), START, START.plusSeconds(1));
        enable(a, aRate); enable(b, bRate);
        var r = legacy.request();
        var request = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(a), MonteCarloPlanDocument.capture(b),
                r.labelA(), r.labelB(), r.start(), r.endingYear(), r.fixedLife(), r.mortality());
        return with(legacy, request, PairedExpenseExecution.capture(request.planA(), request.planB()));
    }

    static MonteCarloSnapshot with(MonteCarloSnapshot s, MonteCarloSnapshot.Request request, PairedExpenseExecution execution) {
        return new MonteCarloSnapshot(3, s.mode(), s.hasSpouse(), s.settings(), request, s.provenance(), null, null,
                s.paired(), s.display(), s.startedAt(), s.completedAt(), null, execution);
    }

    @ParameterizedTest @MethodSource("enabledMatrix")
    void allFourFamiliesAndAsymmetricCapabilitiesRoundTrip(boolean couple, boolean longevity, String a, String b) throws Exception {
        var payload = fixture(couple, longevity, a, b); var saved = envelope(payload);
        var codec = new AnalysisSnapshotCodec();
        assertEquals(saved, codec.decode(codec.encode(saved)));
        assertEquals(1, saved.schemaVersion()); assertEquals(4, saved.fingerprintVersion());
        assertEquals(PairedExpenseExecution.ALGORITHM, saved.algorithms().get("paired-recurring-expenses"));
        assertFalse(saved.algorithms().containsKey("recurring-expenses"));
        assertEquals(new BigDecimal(a).signum() > 0, payload.pairedExpenseExecution().current() != null);
        assertEquals(new BigDecimal(b).signum() > 0, payload.pairedExpenseExecution().baseline() != null);
        var restored = MonteCarloSnapshotMapping.restoreComparison((MonteCarloSnapshot) codec.decode(codec.encode(saved)).payload());
        assertEquals(payload.pairedExpenseExecution(), restored.expenseExecution());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new MonteCarloFingerprintService().compatibility(saved,
                new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), payload.request())).state());
    }

    @Test void candidateManifestsCannotBeSwappedOmittedOrBoundToEditedInputs() {
        var payload = fixture(true, false, ".25", ".17"); var cap = payload.pairedExpenseExecution();
        for (var invalid : java.util.List.of(new PairedExpenseExecution(1, cap.baseline(), cap.current()),
                new PairedExpenseExecution(1, null, cap.baseline()), new PairedExpenseExecution(1, cap.current(), null)))
            assertThrows(IllegalArgumentException.class, () -> with(payload, payload.request(), invalid).validate(payload.kind()));
        var changed = MonteCarloPlanDocument.read(payload.request().planA()); enable(changed, ".22");
        var r = payload.request();
        var edited = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(changed), r.planB(), r.labelA(), r.labelB(),
                r.start(), r.endingYear(), r.fixedLife(), r.mortality());
        assertThrows(IllegalArgumentException.class, () -> with(payload, edited, cap).validate(payload.kind()));
        assertThrows(IllegalArgumentException.class, () -> new PairedExpenseExecution(1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PairedExpenseExecution(2, cap.current(), cap.baseline()));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloSnapshot(2, payload.mode(), true, payload.settings(),
                r, payload.provenance(), null, null, payload.paired(), payload.display(), START, START.plusSeconds(1), null, cap));
    }

    @Test void codecRejectsStrictPropertyVersionWorldFingerprintAndDigestCorruption() throws Exception {
        var saved = envelope(fixture(false, true, ".25", ".17")); var codec = new AnalysisSnapshotCodec();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String path : java.util.List.of("/payload/pairedExpenseExecution/current/planDigest", "/inputFingerprint",
                "/contextFingerprint", "/payloadDigest")) {
            var tree = json.readTree(codec.raw(saved)); int split = path.lastIndexOf('/');
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at(path.substring(0, split))).put(path.substring(split + 1), "0".repeat(64));
            assertThrows(java.io.IOException.class, () -> codec.decode(gzip(tree.toString())), path);
        }
        for (String field : java.util.List.of("current", "baseline", "version")) {
            var tree = json.readTree(codec.raw(saved));
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/payload/pairedExpenseExecution")).remove(field);
            assertThrows(java.io.IOException.class, () -> codec.decode(gzip(tree.toString())), field);
        }
        var tree = json.readTree(codec.raw(saved));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/payload/pairedExpenseExecution")).put("unknown", 1);
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip(tree.toString())));
        for (int version : new int[]{1, 2, 3, 5}) {
            var changed = json.readTree(codec.raw(saved)); ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("fingerprintVersion", version);
            assertThrows(java.io.IOException.class, () -> codec.decode(gzip(changed.toString())));
        }
        var badWorld = json.readTree(codec.raw(saved));
        ((com.fasterxml.jackson.databind.node.ObjectNode) badWorld.at("/payload/provenance")).put("returns", "CALLER_SUPPLIED_WORLDS");
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip(badWorld.toString())));
    }

    @Test void comparisonIdentityIncludesEachCandidateAndCapabilityWithoutNamesOrUuids() throws Exception {
        var payload = fixture(false, false, ".25", ".17"); var service = new MonteCarloFingerprintService();
        var original = service.assess(payload); assertTrue(original.valid());
        var s = payload.settings();
        var scaled = new MonteCarloSnapshot.Settings(s.trials(), s.seed(), s.expectedReturn().setScale(6), s.volatility().setScale(6), s.inflation());
        assertEquals(original, service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), scaled, payload.request())));
        var plan = MonteCarloPlanDocument.read(payload.request().planB()); enable(plan, ".22");
        var r = payload.request();
        var edited = new MonteCarloSnapshot.Request(r.planA(), MonteCarloPlanDocument.capture(plan), r.labelA(), r.labelB(),
                r.start(), r.endingYear(), r.fixedLife(), r.mortality());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, service.compatibility(envelope(payload),
                new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), edited)).state());
        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(r.planA());
        tree.put("planId", java.util.UUID.randomUUID().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.at("/household/primaryPerson")).put("firstName", "Synthetic rename");
        var renamed = new MonteCarloSnapshot.Request(tree.toString(), r.planB(), "Renamed", r.labelB(), r.start(), r.endingYear(), r.fixedLife(), null);
        assertEquals(original, service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), renamed)));
        var swapped = new MonteCarloSnapshot.Request(r.planB(), r.planA(), r.labelB(), r.labelA(), r.start(), r.endingYear(), r.fixedLife(), null);
        assertNotEquals(original.fingerprint(), service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), swapped)).fingerprint());
    }

    @Test void acceptedStandaloneCapabilityStillUsesItsOriginalWireAndVersionContract() throws Exception {
        var old = StandaloneExpenseCapabilityTest.enabledFixture(true, false); var saved = envelope(old); var codec = new AnalysisSnapshotCodec();
        var raw = codec.raw(saved);
        assertFalse(new String(raw, java.nio.charset.StandardCharsets.UTF_8).contains("pairedExpenseExecution"));
        assertEquals(3, saved.fingerprintVersion()); assertEquals(2, old.version());
        assertArrayEquals(raw, codec.raw(codec.decode(codec.encode(saved))));
    }

    @Test void atomicFailurePreservesOldArtifactAndRetryPublishesVerifiedPairedResultWithBackup() throws Exception {
        var saved = envelope(fixture(false, false, ".25", ".17"));
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var repository = com.daviddunn.retirementplanner.persistence.AnalysisSnapshotFaults.failVerification(directory, fail::get);
        repository.save(saved); var file = repository.path(saved.planId(), saved.kind());
        byte[] original = java.nio.file.Files.readAllBytes(file);
        fail.set(true); assertThrows(java.io.IOException.class, () -> repository.save(saved));
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
        fail.set(false); repository.save(saved);
        assertEquals(saved, repository.load(saved.planId(), saved.kind()).orElseThrow());
        assertEquals(saved, repository.loadBackup(saved.planId(), saved.kind()).orElseThrow());
        var owner = java.util.UUID.randomUUID(); var wrong = repository.path(owner, saved.kind());
        java.nio.file.Files.createDirectories(wrong.getParent()); java.nio.file.Files.write(wrong, original);
        assertThrows(java.io.IOException.class, () -> repository.load(owner, saved.kind()));
    }
}
