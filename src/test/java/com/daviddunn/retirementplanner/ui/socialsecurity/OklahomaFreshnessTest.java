package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.OklahomaProjectionAcceptanceTest;
import com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaFreshnessTest {
    @Test
    void capturesNcVersionAndChangedRulesMakeHistoryOutdatedWithoutChangingIt() {
        var fixture = SnapshotFixtures.snapshot(UUID.randomUUID());
        var completion = new AcceptedAnalysisCompletion(fixture.planId(), null, fixture.kind(), fixture.runId(),
                fixture.completedAt(), fixture.inputFingerprint(), fixture.contextFingerprint(), fixture.algorithms(),
                fixture.references(), fixture.payload(), fixture.context())
                .withConditionalBenefits(OklahomaProjectionAcceptanceTest.plan(true));
        var current = completion.presentationSnapshot(new AnalysisSnapshotCodec());
        assertEquals(OklahomaTaxRules.VERSION, current.references().get(OklahomaTaxRules.REFERENCE_KEY));
        var inputs = new AnalysisFingerprintService.Assessment(current.inputFingerprint(), current.contextFingerprint(), null);
        var policy = new AnalysisCompatibilityPolicy();
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,
                policy.assess(current, inputs, fixture.algorithms(), fixture.references()).state());
        var oldReferences = new TreeMap<>(current.references());
        oldReferences.put(OklahomaTaxRules.REFERENCE_KEY, "OK-previous-reviewed-rules");
        var historical = new AnalysisSnapshot(current.schemaVersion(), current.fingerprintVersion(), current.planId(),
                current.kind(), current.shape(), current.runId(), current.completedAt(), current.applicationBuild(),
                current.algorithms(), oldReferences, current.inputFingerprint(), current.contextFingerprint(),
                current.payloadDigest(), current.payload(), current.context(), current.originPlanId());
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED,
                policy.assess(historical, inputs, fixture.algorithms(), fixture.references()).state());
        assertEquals("OK-previous-reviewed-rules", historical.references().get(OklahomaTaxRules.REFERENCE_KEY));
        assertSame(current.payload(), historical.payload());
        assertSame(current.context(), historical.context());
        assertEquals(current.payloadDigest(), historical.payloadDigest());
    }

    @Test
    void existingStatesAndSsOnlyReferencesRemainExactlyUnchangedAndBothComparisonSidesAreChecked() {
        var nc = OklahomaProjectionAcceptanceTest.plan(true);
        var mi = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(nc);
        mi.setPlanningAssumptions(mi.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.michigan()));
        var references = Map.of("existing", "unchanged");
        assertSame(references, AnalysisVersions.withOklahomaRules(AnalysisKind.COUPLE_WEIGHTED, references, mi));
        assertSame(references, AnalysisVersions.withOklahomaRules(AnalysisKind.COUPLE_SOCIAL_SECURITY, references, nc));
        assertEquals(OklahomaTaxRules.VERSION, AnalysisVersions.withOklahomaRules(
                AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_FIXED, references, mi, nc).get(OklahomaTaxRules.REFERENCE_KEY));
        var saved = SnapshotFixtures.snapshot(UUID.randomUUID());
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT, new AnalysisCompatibilityPolicy().assess(saved,
                new AnalysisFingerprintService.Assessment(saved.inputFingerprint(), saved.contextFingerprint(), null),
                saved.algorithms(), saved.references()).state());
    }
}
