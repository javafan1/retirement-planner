package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerAssociationStorageTest {
    @TempDir Path directory;
    final List<ApplicationController> sources = new ArrayList<>();
    ApplicationController source(JsonRetirementPlanRepository repository) throws Exception {
        var c = new ApplicationController(repository, new JsonApplicationSettingsRepository(directory.resolve("settings" + sources.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("slots" + sources.size()))));
        sources.add(c); c.newPlan(BaselineDetailsTest.plan(true)); return c;
    }
    @AfterEach void stop() throws Exception {
        for (var c : sources) {
            c.getProjectionRestoration().toCompletableFuture().get(); c.getSnapshotCopyCompletion().toCompletableFuture().get();
            assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete()); c.getAnalysisParameters().shutdown();
            assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
        }
    }
    AnalyzerAssociation pin(ApplicationController c) {
        var token = c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_WEIGHTED, "a".repeat(64)); assertNotNull(token);
        var completion = new AcceptedAnalysisCompletion(c.getAnalysisSessionId(), c.getCurrentFile(), token.kind(), token.ticket(), Instant.now(),
                "a".repeat(64), "b".repeat(64), AnalysisVersions.algorithms(token.kind()), AnalysisVersions.references(token.kind()), SnapshotFixtures.couple(), SnapshotFixtures.context());
        assertTrue(c.associateAnalyzer(token, completion)); return c.getAnalyzerAssociations(token.projectionId()).getFirst();
    }
    @ParameterizedTest @ValueSource(strings={"VERIFY","BACKUP","REPLACE"})
    void failedManifestCommitRetainsPreviousPinAndRetryNeedsNoCalculation(String stage) throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var repo = new JsonRetirementPlanRepository(new AtomicFileWriter((s,p) -> {
            if (fail.get() && s.name().equals(stage)) throw new java.io.IOException("Injected manifest failure");
        }), new ObjectMapper());
        var c=source(repo); c.getCurrentProjection(); var file=directory.resolve("plan.json"); c.saveAs(file);
        var a=pin(c); c.save(); a=c.getAnalyzerAssociations(a.projectionId()).getFirst();
        var durable=Files.readAllBytes(file); var original=new ProjectionSnapshotComponents().readAnalyzer(file,a);
        var b=pin(c); assertNotEquals(a.runId(),b.runId());
        fail.set(true); assertThrows(java.io.IOException.class,c::save); assertArrayEquals(durable,Files.readAllBytes(file));
        assertEquals(original,new ProjectionSnapshotComponents().readAnalyzer(file,a));
        assertTrue(c.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(b.runId()));
        BaselineDetailsTest.set(c,"projectionEngine",ProjectionSnapshotPersistenceTest.throwing());
        fail.set(false); c.save(); assertTrue(c.getCurrentPlan().getPendingAnalyzerAssociations().isEmpty());
        var reloaded=source(new JsonRetirementPlanRepository()); BaselineDetailsTest.set(reloaded,"projectionEngine",ProjectionSnapshotPersistenceTest.throwing());
        reloaded.open(file); reloaded.getProjectionRestoration().toCompletableFuture().get();
        assertEquals(b.runId(),reloaded.getAnalyzerAssociations(b.projectionId()).getFirst().runId());
        var backup=source(new JsonRetirementPlanRepository()); BaselineDetailsTest.set(backup,"projectionEngine",ProjectionSnapshotPersistenceTest.throwing());
        backup.openRecoveredPlan(file); backup.getProjectionRestoration().toCompletableFuture().get();
        assertEquals(a.runId(),backup.getAnalyzerAssociations(a.projectionId()).getFirst().runId());
    }
    @Test void componentFailureCannotPublishAnIncompleteManifest() throws Exception {
        var c=source(new JsonRetirementPlanRepository()); c.getCurrentProjection(); var file=directory.resolve("plan.json"); c.saveAs(file);
        var original=Files.readAllBytes(file); var pin=pin(c);
        var storage=directory.resolve(".retirement-planner-projections").resolve(pin.owner().toString());
        Files.createDirectory(storage.resolve("analysis-"+pin.runId()+".json.gz"));
        assertThrows(java.io.IOException.class,c::save); assertArrayEquals(original,Files.readAllBytes(file));
        assertTrue(c.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(pin.runId()));
    }
    @Test void sharedStorageBudgetRefusesNewPinsWithoutDiscardingTheOldDurableResult() throws Exception {
        var c=source(new JsonRetirementPlanRepository()); c.getCurrentProjection(); var file=directory.resolve("plan.json"); c.saveAs(file);
        var previous=pin(c); c.save(); previous=c.getAnalyzerAssociations(previous.projectionId()).getFirst();
        var before=Files.readAllBytes(file); var store=new ProjectionSnapshotComponents(); var saved=store.readAnalyzer(file,previous);
        var storage=directory.resolve(".retirement-planner-projections").resolve(previous.owner().toString());
        try(var pressure=new java.io.RandomAccessFile(storage.resolve("bounded-storage-pressure.bin").toFile(),"rw")) {pressure.setLength(128L*1024*1024);}
        pin(c); assertThrows(java.io.IOException.class,c::save); assertArrayEquals(before,Files.readAllBytes(file));
        assertEquals(saved,store.readAnalyzer(file,previous));
    }
    @Test void missingOversizedAndUnsupportedComponentsAreRejectedUnderTheSameReferenceRules() throws Exception {
        var c=source(new JsonRetirementPlanRepository()); c.getCurrentProjection(); var file=directory.resolve("plan.json"); c.saveAs(file);
        var ref=pin(c); c.save(); ref=c.getAnalyzerAssociations(ref.projectionId()).getFirst(); final var reference=ref;
        var path=directory.resolve(".retirement-planner-projections").resolve(ref.owner().toString()).resolve("analysis-"+ref.runId()+".json.gz");
        var bytes=Files.readAllBytes(path); var store=new ProjectionSnapshotComponents(); var saved=store.readAnalyzer(file,ref);
        var held=path.resolveSibling("held-component.bin"); Files.move(path,held);
        assertThrows(java.io.IOException.class,()->store.readAnalyzer(file,reference)); Files.move(held,path);
        try(var pressure=new java.io.RandomAccessFile(path.toFile(),"rw")) {pressure.setLength(AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES+1L);}
        assertThrows(java.io.IOException.class,()->store.readAnalyzer(file,reference)); Files.write(path,bytes);
        var codec=new AnalysisSnapshotCodec(); var raw=new String(codec.raw(saved),java.nio.charset.StandardCharsets.UTF_8).replace("\"schemaVersion\":1","\"schemaVersion\":77");
        var output=new java.io.ByteArrayOutputStream(); try(var gzip=new java.util.zip.GZIPOutputStream(output)){gzip.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        Files.write(path,output.toByteArray());
        var unsupported=new AnalyzerAssociation(1,ref.owner(),ref.projectionId(),ref.projectionInputDigest(),ref.kind(),ref.runId(),ref.inputFingerprint(),ref.contextFingerprint(),ref.payloadDigest(),AnalysisSnapshotCodec.sha256(output.toByteArray()),ref.acceptedAt());
        assertThrows(java.io.IOException.class,()->store.readAnalyzer(file,unsupported));
        Files.write(path,bytes); assertEquals(saved,store.readAnalyzer(file,ref));
    }
    @Test void historicalAssociationCountIsBoundedWithoutDiscardingPreviouslyAcceptedReferences() throws Exception {
        var c=source(new JsonRetirementPlanRepository()); c.getCurrentProjection(); var original=c.getCurrentPlan().getProjectionState();
        var pins=new ArrayList<ProjectionRoleManifest.Reference>(); var refs=new ArrayList<AnalyzerAssociation>();
        for (int i=0;i<30;i++) {
            var id=UUID.randomUUID(); pins.add(new ProjectionRoleManifest.Reference(id,"a".repeat(64)));
            refs.add(new AnalyzerAssociation(1,original.owner(),id,"a".repeat(64),AnalysisKind.COUPLE_WEIGHTED,UUID.randomUUID(),
                    "a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(64),Instant.now()));
        }
        c.getCurrentPlan().setProjectionState(new ProjectionRoleManifest(2,original.owner(),original.current(),null,refs,pins));
        var token=c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_WEIGHTED, "a".repeat(64));
        var completion=new AcceptedAnalysisCompletion(c.getAnalysisSessionId(),null,token.kind(),token.ticket(),Instant.now(),"a".repeat(64),"b".repeat(64),
                AnalysisVersions.algorithms(token.kind()),AnalysisVersions.references(token.kind()),SnapshotFixtures.couple(),SnapshotFixtures.context());
        assertFalse(c.associateAnalyzer(token,completion)); assertEquals(refs,c.getCurrentPlan().getProjectionState().associations());
        assertTrue(c.getAnalyzerAssociationNotice(token.kind()).contains("capacity reached"));
        assertThrows(IllegalArgumentException.class,()->new ProjectionRoleManifest(3,original.owner(),original.current(),null,refs,pins));
    }
}
