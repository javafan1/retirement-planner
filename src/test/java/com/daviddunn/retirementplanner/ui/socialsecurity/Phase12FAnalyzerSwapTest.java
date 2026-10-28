package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class Phase12FAnalyzerSwapTest {
    @TempDir Path directory;
    Phase12EAssociationTest fixtures;
    @BeforeAll static void preparePayloads() throws Exception { Phase12EAssociationTest.fixtures(); }
    @BeforeEach void setup() { fixtures = new Phase12EAssociationTest(); fixtures.directory = directory; }
    @AfterEach void close() throws Exception { fixtures.close(); }

    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void everyAnalyzerFollowsProjectionUuidAcrossSwapSaveRestartAndClone(AnalysisKind kind) throws Exception {
        var c = fixtures.controller(kind.shape() == AnalysisKind.Shape.COUPLE); c.getCurrentProjection();
        c.saveCurrentAsBaseline("Original source"); c.saveAs(directory.resolve("plan.json"));
        var projection = c.getAcceptedProjectionSnapshot(); var token = c.prepareAnalyzerAssociation(kind, "a".repeat(64));
        var completion = fixtures.completion(c, token); assertTrue(c.associateAnalyzer(token,completion));
        c.getAnalysisResults().accept(completion);
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (c.getAnalysisResults().needsDrain() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(c.getAnalysisResults().needsDrain());
        c.save(); var ref = c.getAnalyzerAssociations(projection.id()).getFirst();
        var original = c.loadAssociatedAnalyzer(ref).toCompletableFuture().get();
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1250000.0012300"));
        c.markModified(); c.invalidateProjection(); c.getCurrentProjection(); c.save();
        var currentId = c.getAcceptedProjectionSnapshot().id();
        assertEquals(AnalysisResultFreshness.Association.HISTORICAL_PROJECTION, c.analysisAssociation(original));
        var obsolete = c.prepareAnalyzerAssociation(kind,"a".repeat(64)); var obsoleteCompletion = fixtures.completion(c,obsolete);
        BaselineDetailsTest.set(c,"projectionEngine",Phase12EAssociationTest.throwing());
        assertTrue(c.commitProjectionSwap(c.prepareProjectionSwap().toCompletableFuture().get()));
        assertEquals(projection.id(),c.getCurrentPlan().getProjectionState().current().id());
        assertEquals(currentId,c.getCurrentPlan().getProjectionState().baseline().id());
        assertEquals(ref,c.getAnalyzerAssociations(projection.id()).getFirst());
        assertFalse(c.associateAnalyzer(obsolete,obsoleteCompletion));
        assertEquals(original,c.loadAssociatedAnalyzer(ref).toCompletableFuture().get());
        assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION, c.analysisAssociation(original));
        // The replaceable last-success slot is not changed by exchanging historical roles.
        var repository = new JsonAnalysisSnapshotRepository(directory.resolve("slots-0"));
        var slot = repository.load(c.getAnalysisSessionId(),kind).orElseThrow();
        assertEquals(original.payload(),slot.payload()); assertEquals(original.runId(),slot.runId());
        c.save();
        var reopened = fixtures.controller(kind.shape() == AnalysisKind.Shape.COUPLE);
        BaselineDetailsTest.set(reopened,"projectionEngine",Phase12EAssociationTest.throwing()); reopened.open(c.getCurrentFile());
        reopened.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
        assertEquals(projection.id(),reopened.getAcceptedProjectionSnapshot().id());
        assertEquals(original,reopened.loadAssociatedAnalyzer(ref).toCompletableFuture().get());
        assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION, reopened.analysisAssociation(original));
        reopened.saveAs(directory.resolve("clone.json")); reopened.getSnapshotCopyCompletion().toCompletableFuture().get();
        var clonedRef = reopened.getAnalyzerAssociations(projection.id()).getFirst();
        assertNotEquals(ref.owner(),clonedRef.owner()); assertEquals(ref.runId(),clonedRef.runId());
        assertEquals(original.payload(),reopened.loadAssociatedAnalyzer(clonedRef).toCompletableFuture().get().payload());
        assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION,
                reopened.analysisAssociation(reopened.loadAssociatedAnalyzer(clonedRef).toCompletableFuture().get()));
        assertEquals(AnalysisResultFreshness.Association.UNRECORDED, reopened.analysisAssociation(original));
        assertEquals(ref,c.getAnalyzerAssociations(projection.id()).getFirst());
        assertTrue(c.commitProjectionSwap(c.prepareProjectionSwap().toCompletableFuture().get()));
        assertEquals(currentId, c.getAcceptedProjectionSnapshot().id());
        assertEquals(AnalysisResultFreshness.Association.HISTORICAL_PROJECTION, c.analysisAssociation(original));
    }
}
