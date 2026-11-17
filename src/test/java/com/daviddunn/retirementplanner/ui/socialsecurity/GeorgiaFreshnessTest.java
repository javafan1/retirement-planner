package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.GeorgiaProjectionAcceptanceTest;
import com.daviddunn.retirementplanner.domain.tax.state.georgia.GeorgiaTaxRules;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeorgiaFreshnessTest {
    @Test
    void capturesNcVersionAndChangedRulesMakeHistoryOutdatedWithoutChangingIt() {
        var fixture = SnapshotFixtures.snapshot(UUID.randomUUID());
        var completion = new AcceptedAnalysisCompletion(fixture.planId(), null, fixture.kind(), fixture.runId(),
                fixture.completedAt(), fixture.inputFingerprint(), fixture.contextFingerprint(), fixture.algorithms(),
                fixture.references(), fixture.payload(), fixture.context())
                .withConditionalBenefits(GeorgiaProjectionAcceptanceTest.plan(true));
        var current = completion.presentationSnapshot(new AnalysisSnapshotCodec());
        assertEquals(GeorgiaTaxRules.VERSION, current.references().get(GeorgiaTaxRules.REFERENCE_KEY));
        var inputs = new AnalysisFingerprintService.Assessment(current.inputFingerprint(), current.contextFingerprint(), null);
        var policy = new AnalysisCompatibilityPolicy();
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,
                policy.assess(current, inputs, fixture.algorithms(), fixture.references()).state());
        var oldReferences = new TreeMap<>(current.references());
        oldReferences.put(GeorgiaTaxRules.REFERENCE_KEY, "GA-previous-reviewed-rules");
        var historical = new AnalysisSnapshot(current.schemaVersion(), current.fingerprintVersion(), current.planId(),
                current.kind(), current.shape(), current.runId(), current.completedAt(), current.applicationBuild(),
                current.algorithms(), oldReferences, current.inputFingerprint(), current.contextFingerprint(),
                current.payloadDigest(), current.payload(), current.context(), current.originPlanId());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED,
                policy.assess(historical, inputs, fixture.algorithms(), fixture.references()).state());
        assertEquals("GA-previous-reviewed-rules", historical.references().get(GeorgiaTaxRules.REFERENCE_KEY));
        assertSame(current.payload(), historical.payload());
        assertSame(current.context(), historical.context());
        assertEquals(current.payloadDigest(), historical.payloadDigest());
    }

    @Test
    void existingStatesAndSsOnlyReferencesRemainExactlyUnchangedAndBothComparisonSidesAreChecked() {
        var nc = GeorgiaProjectionAcceptanceTest.plan(true);
        var mi = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(nc);
        mi.setPlanningAssumptions(mi.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.michigan()));
        var references = Map.of("existing", "unchanged");
        assertSame(references, AnalysisVersions.withGeorgiaRules(AnalysisKind.COUPLE_WEIGHTED, references, mi));
        assertSame(references, AnalysisVersions.withGeorgiaRules(AnalysisKind.COUPLE_SOCIAL_SECURITY, references, nc));
        assertEquals(GeorgiaTaxRules.VERSION, AnalysisVersions.withGeorgiaRules(
                AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_FIXED, references, mi, nc).get(GeorgiaTaxRules.REFERENCE_KEY));
        var saved = SnapshotFixtures.snapshot(UUID.randomUUID());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new AnalysisCompatibilityPolicy().assess(saved,
                new AnalysisFingerprintService.Assessment(saved.inputFingerprint(), saved.contextFingerprint(), null),
                saved.algorithms(), saved.references()).state());
    }
}
