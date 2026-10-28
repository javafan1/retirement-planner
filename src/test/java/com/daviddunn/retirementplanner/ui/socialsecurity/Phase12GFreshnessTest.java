package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.IndividualCompletionPersistenceTest.fx;

class Phase12GFreshnessTest {
    @TempDir Path directory;
    Phase12EAssociationTest fixtures;
    @BeforeAll static void prepare() throws Exception { Phase12EAssociationTest.fixtures(); }
    @BeforeEach void setup() { fixtures = new Phase12EAssociationTest(); fixtures.directory = directory; }
    @AfterEach void close() throws Exception { fixtures.close(); }

    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void applicabilityNeverInventsAssociationForEquivalentInputsOrWrongOwner(AnalysisKind kind) throws Exception {
        var source = fixtures.controller(kind.shape() == AnalysisKind.Shape.COUPLE);
        source.getCurrentProjection();
        var projection = source.getAcceptedProjectionSnapshot();
        var token = source.prepareAnalyzerAssociation(kind, "a".repeat(64));
        var saved = fixtures.completion(source, token).presentationSnapshot(new AnalysisSnapshotCodec());
        var ref = new AnalyzerAssociation(1, saved.planId(), projection.id(), projection.inputDigest(), kind,
                saved.runId(), saved.inputFingerprint(), saved.contextFingerprint(), saved.payloadDigest(), "c".repeat(64), saved.completedAt());
        assertEquals(AnalysisResultFreshness.Association.UNRECORDED, source.analysisAssociation(saved));
        assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION,
                AnalysisResultFreshness.association(saved, saved.planId(), projection.id(), projection.inputDigest(), true, List.of(ref)));
        // Same financial digest, different UUID must remain historical, never a newly inferred pin.
        assertEquals(AnalysisResultFreshness.Association.HISTORICAL_PROJECTION,
                AnalysisResultFreshness.association(saved, saved.planId(), UUID.randomUUID(), projection.inputDigest(), true, List.of(ref)));
        assertEquals(AnalysisResultFreshness.Association.INPUTS_EDITED,
                AnalysisResultFreshness.association(saved, saved.planId(), projection.id(), projection.inputDigest(), false, List.of(ref)));
        assertEquals(AnalysisResultFreshness.Association.UNRECORDED,
                AnalysisResultFreshness.association(saved, UUID.randomUUID(), projection.id(), projection.inputDigest(), true, List.of(ref)));
        var corrupt = new AnalyzerAssociation(1, saved.planId(), projection.id(), projection.inputDigest(), kind,
                saved.runId(), saved.inputFingerprint(), saved.contextFingerprint(), "0".repeat(64), "c".repeat(64), saved.completedAt());
        assertEquals(AnalysisResultFreshness.Association.UNRECORDED,
                AnalysisResultFreshness.association(saved, saved.planId(), projection.id(), projection.inputDigest(), true, List.of(corrupt)));
        var compatible = new AnalysisResultFreshness(new AnalysisCompatibilityPolicy.Assessment(
                AnalysisCompatibilityPolicy.State.CURRENT, "Matching effective inputs"), AnalysisResultFreshness.Association.UNRECORDED);
        assertTrue(compatible.compatible()); assertFalse(compatible.mismatch());
        assertTrue(compatible.status().contains("compatible but unassociated"));
        assertFalse(new AnalysisResultFreshness(null, AnalysisResultFreshness.Association.UNRECORDED).mismatch());
    }

    @Test void explicitActionOpensOnlyRecordedKindWithoutPromotingSessionOrRunningEngine() throws Exception {
        var source = fixtures.controller(true); source.getCurrentProjection();
        var projection = source.getAcceptedProjectionSnapshot();
        var token = source.prepareAnalyzerAssociation(AnalysisKind.COUPLE_QUICK, "a".repeat(64));
        var completion = fixtures.completion(source, token);
        var absent = fx(() -> new AssociatedAnalyzerAction(source, () -> AnalysisKind.COUPLE_QUICK));
        assertTrue(fx(absent::isDisabled));
        assertTrue(source.associateAnalyzer(token, completion));
        com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(source, "projectionEngine", Phase12EAssociationTest.throwing());
        var inputDocument = projection.inputsDocument();
        var configurations = source.getCurrentPlan().getAnalyzerConfigurations();
        fx(() -> { absent.refresh(); return null; }); assertFalse(fx(absent::isDisabled));
        assertTrue(source.getAnalysisResults().inMemory(new AnalysisResultStore.Slot(source.getAnalysisSessionId(), AnalysisKind.COUPLE_QUICK)).isEmpty());
        fx(() -> {
            var history = new ProjectionAnalyzerHistoryDialog(source, projection.id(), AnalysisKind.COUPLE_QUICK);
            assertEquals(AnalysisKind.COUPLE_QUICK, WeightedRestorationTest.field(history, "kinds", javafx.scene.control.ComboBox.class).getValue());
            history.close(); return null;
        });
        assertSame(configurations, source.getCurrentPlan().getAnalyzerConfigurations());
        assertEquals(inputDocument, source.getAcceptedProjectionSnapshot().inputsDocument());
        assertFalse(source.isModified());
    }

    @Test void delayedSocialSecurityRestorationIsPendingRatherThanOutdated() throws Exception {
        var source = fixtures.controller(true);
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan());
        source.saveAs(directory.resolve("pending-plan.json"));
        var completion = CoupleSocialSecurityPersistenceTest.completion(source);
        source.getAnalysisResults().accept(completion);
        WeightedRestorationTest.settle(source.getAnalysisResults(), completion.planId(), completion.kind());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        source.getAnalysisResults().background(() -> {
            entered.countDown();
            if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Test release timeout");
            return null;
        });
        assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            assertTrue(fx(() -> view.persistence().freshness().pending()));
            assertFalse(fx(() -> view.persistence().freshness().mismatch()));
            assertFalse(fx(() -> view.persistence().notice.getText().contains("Outdated")));
            assertEquals("Assessing completed result inputs…", fx(() -> view.persistence().warning()));
            release.countDown(); WeightedRestorationTest.await(view.persistence().notice, "Current Results");
            assertEquals(0, fx(view.jobs()::generation));
        } finally { release.countDown(); }
    }
}
