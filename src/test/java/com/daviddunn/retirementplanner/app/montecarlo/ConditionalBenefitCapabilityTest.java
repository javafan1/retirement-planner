package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitCapabilityTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    /** Structural-only fixture; execution correctness is independently tested against ProjectionEngine. */
    static MonteCarloSnapshot fixture(boolean couple, boolean longevity, boolean paired) {
        var original = paired ? PairedExpenseCapabilityTest.fixture(couple, longevity, ".25", ".17")
                : StandaloneExpenseCapabilityTest.enabledFixture(couple, longevity);
        var r = original.request();
        var a = MonteCarloPlanDocument.read(r.planA());
        a.setConditionalBenefits(List.of(BenefitFixtures.monthly()));
        String aDocument = MonteCarloPlanDocument.capture(a);
        var request = new MonteCarloSnapshot.Request(aDocument, r.planB(), r.labelA(), r.labelB(), r.start(),
                r.endingYear(), r.fixedLife(), r.mortality());
        var capability = ConditionalBenefitAnalysisExecution.capture(original.kind(), request.planA(), request.planB());
        return new MonteCarloSnapshot(paired ? 5 : 4, original.mode(), couple, original.settings(), request,
                original.provenance(), original.fixed(), original.mortality(), original.paired(), original.display(),
                original.startedAt(), original.completedAt(), paired ? null : ExpenseExecution.fromDocument(aDocument),
                paired ? PairedExpenseExecution.capture(aDocument, r.planB()) : null, capability);
    }

    @ParameterizedTest @CsvSource({"true,false,false", "true,true,false", "false,false,false", "false,true,false",
            "true,false,true", "true,true,true", "false,false,true", "false,true,true"})
    void allEightStrictCapabilityCombinationsRoundTrip(boolean couple, boolean longevity, boolean paired) throws Exception {
        var payload = fixture(couple, longevity, paired);
        var saved = StandaloneExpenseCapabilityTest.envelope(payload);
        assertEquals(paired ? 6 : 5, saved.fingerprintVersion());
        assertEquals(paired ? 5 : 4, payload.version());
        assertEquals(ConditionalBenefitAnalysisExecution.ALGORITHM, saved.algorithms().get(ConditionalBenefitAnalysisExecution.KEY));
        var codec = new AnalysisSnapshotCodec();
        assertEquals(saved, codec.decode(codec.encode(saved)));
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new MonteCarloFingerprintService().compatibility(saved,
                new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), payload.request())).state());
        assertTrue(payload.benefitExecution().disclosure().stream().anyMatch(s -> s.contains("fully taxable ordinary non-pension")));
    }

    @Test void missingSwappedAndTamperedCapabilitiesCannotClaimEnabledExecution() {
        var p = fixture(true, false, true); var c = p.benefitExecution();
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloSnapshot(5, p.mode(), true, p.settings(), p.request(),
                p.provenance(), null, null, p.paired(), p.display(), p.startedAt(), p.completedAt(), null, p.pairedExpenseExecution()));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalBenefitAnalysisExecution(1, c.model(), c.family(),
                c.scenarioProtocol(), List.of(c.candidates().get(1), c.candidates().getFirst())));
        var original = c.candidates().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new ConditionalBenefitAnalysisExecution.Candidate(original.role(),
                original.originalInputs(), "0".repeat(64), original.definitionDigest(), true));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalBenefitAnalysisExecution(2, c.model(), c.family(),
                c.scenarioProtocol(), c.candidates()));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalBenefitAnalysisExecution(1, c.model(), c.family(),
                "UNKNOWN", c.candidates()));
        assertThrows(IllegalArgumentException.class, () -> c.validate(p.kind(), p.request().planB(), p.request().planA()));
    }

    @Test void financialFingerprintIgnoresOpaqueIdentityButBindsCapabilityAndIndependentCandidates() {
        var p = fixture(true, false, true); var service = new MonteCarloFingerprintService();
        var original = service.storedFingerprint(p);
        var a = MonteCarloPlanDocument.read(p.request().planA());
        var b = a.getConditionalBenefits().getFirst();
        a.setConditionalBenefits(List.of(BenefitFixtures.change(BenefitFixtures.change(b, "id", UUID.randomUUID()), "label", "Renamed")));
        var r = p.request();
        var changed = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(a), r.planB(), r.labelA(), r.labelB(), r.start(),
                r.endingYear(), r.fixedLife(), r.mortality());
        assertEquals(original.fingerprint(), service.forAcceptance(new MonteCarloFingerprintService.Inputs(p.kind(), p.settings(), changed)).fingerprint());
        a.setConditionalBenefits(List.of(BenefitFixtures.change(b, "grossAmount", new java.math.BigDecimal("3000"))));
        changed = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(a), r.planB(), r.labelA(), r.labelB(), r.start(),
                r.endingYear(), r.fixedLife(), r.mortality());
        assertNotEquals(original.fingerprint(), service.forAcceptance(new MonteCarloFingerprintService.Inputs(p.kind(), p.settings(), changed)).fingerprint());
    }

    @Test void completedEnabledStorageFailureAndRetryKeepTheSameArtifactWithoutSimulation() throws Exception {
        var plan = StochasticExpenseMonteCarloTest.plan(false);
        plan.setConditionalBenefits(ConditionalBenefitMonteCarloExecutionTest.benefits(plan,"1200"));
        var settings = MonteCarloSettings.forPlan(plan,2,417,new java.math.BigDecimal(".12"));
        var run = new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRunService().run(plan,settings,null,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        var payload = com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloSnapshotPresentationMapper.capture(run,plan,
                StandaloneExpenseCapabilityTest.START,StandaloneExpenseCapabilityTest.START.plusSeconds(1));
        var repo = new MonteCarloResultLifecycleTest.Memory(); var store = new AnalysisResultStore(repo);
        var ui = new MonteCarloResultLifecycleTest.Ui();
        var binding = new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloResultLifecycle.Binding(UUID.randomUUID(),directory.resolve("synthetic.json"),payload.kind());
        var lifecycle = new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloResultLifecycle<Object>(store,ui,
                s -> { fail("Storage retry cannot restore or simulate a result");return null; },r -> {},()->{},()->{});
        lifecycle.bind(binding); lifecycle.assess(()->()->new MonteCarloFingerprintService.Inputs(payload.kind(),payload.settings(),payload.request()));
        var completion = com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloResultLifecycle.completion(binding,payload);
        lifecycle.accept(completion);ui.until(()->lifecycle.status().contains("SAVED"));
        var previous = repo.load(binding.planId(),payload.kind()).orElseThrow();repo.fail=true;
        var replacement = com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloResultLifecycle.completion(binding,payload);
        lifecycle.accept(replacement);ui.until(lifecycle::retryAvailable);
        assertEquals(previous,repo.load(binding.planId(),payload.kind()).orElseThrow());
        assertSame(payload,store.inMemory(new AnalysisResultStore.Slot(binding.planId(),payload.kind())).orElseThrow().payload());
        repo.fail=false;lifecycle.retry();lifecycle.close();
        assertEquals(AnalysisResultStore.Durability.SAVED,store.whenSettled(new AnalysisResultStore.Slot(binding.planId(),payload.kind()))
                .toCompletableFuture().get(15,java.util.concurrent.TimeUnit.SECONDS).durability());
        assertEquals(replacement.runId(),repo.load(binding.planId(),payload.kind()).orElseThrow().runId());
        assertEquals(payload,repo.load(binding.planId(),payload.kind()).orElseThrow().payload());
        MonteCarloResultLifecycleTest.close(store);
    }
}
