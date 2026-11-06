package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitAnalyzerContractTest {
    @Test void nonMonteCarloCapabilityIsOptionalForLegacyAndStrictlyBoundForEnabledArtifacts() throws Exception {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var result = SinglePersonMortalityAnalysisTest.run(plan, SinglePersonMortalityAnalysisTest.mortality(plan, "1.00"),
                "0.0100", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        // A future event outside all retained scenarios permits an independent legacy numerical oracle.
        plan.setConditionalBenefits(List.of(BenefitFixtures.change(BenefitFixtures.monthly(), "trigger",
                new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.Trigger(
                        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.TriggerType.FIXED_DATE,
                        LocalDate.of(9999, 1, 1), null, null))));
        var payload = new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(result));
        var context = SnapshotFixtures.individualContext(plan);
        var kind = AnalysisKind.INDIVIDUAL_WEIGHTED;
        var fingerprints = new AnalysisFingerprintService();
        var inputs = ConditionalBenefitAnalyzerValidation.inputs(payload, context);
        var assessment = fingerprints.assess(kind, plan, inputs, null);
        var completion = new AcceptedAnalysisCompletion(UUID.randomUUID(), null, kind, UUID.randomUUID(), Instant.now(),
                assessment.fingerprint(), assessment.contextFingerprint(), AnalysisVersions.algorithms(kind), Map.of(), payload, context)
                .withConditionalBenefits(plan);
        var codec = new AnalysisSnapshotCodec();
        var saved = completion.presentationSnapshot(codec);
        assertEquals(saved, codec.decode(codec.encode(saved)));
        assertEquals(payload, saved.payload());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new AnalysisCompatibilityPolicy().assess(saved, assessment,
                AnalysisVersions.algorithms(kind), saved.references()).state());
        var old = new AcceptedAnalysisCompletion(completion.planId(), null, kind, completion.runId(), completion.completedAt(),
                assessment.fingerprint(), assessment.contextFingerprint(), AnalysisVersions.algorithms(kind), Map.of(), payload, context)
                .presentationSnapshot(codec);
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED, new AnalysisCompatibilityPolicy().assess(old, assessment,
                AnalysisVersions.algorithms(kind), old.references()).state());
        for (String property : List.of("benefitExecution", "kind", "planId", "payload", "inputFingerprint")) {
            ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(codec.raw(saved));
            tree.remove(property);
            assertThrows(java.io.IOException.class, () -> codec.decode(AnalysisSnapshotCodecTest.gzip(tree.toString())), property);
        }
        ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(codec.raw(saved));
        ((ObjectNode) tree.path("benefitExecution")).put("scenarioProtocol", "UNSUPPORTED");
        assertThrows(java.io.IOException.class, () -> codec.decode(AnalysisSnapshotCodecTest.gzip(tree.toString())));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisSnapshot(saved.schemaVersion(), saved.fingerprintVersion(),
                saved.planId(), kind, kind.shape(), saved.runId(), saved.completedAt(), saved.applicationBuild(), saved.algorithms(),
                saved.references(), "0".repeat(64), saved.contextFingerprint(), saved.payloadDigest(), payload, context, null, saved.benefitExecution()));
    }
}
