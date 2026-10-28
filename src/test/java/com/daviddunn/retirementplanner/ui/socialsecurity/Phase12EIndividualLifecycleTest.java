package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.IndividualCompletionPersistenceTest.fx;
import static org.junit.jupiter.api.Assertions.*;

class Phase12EIndividualLifecycleTest {
    @TempDir Path directory;
    @BeforeAll static void fxStart() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }
    ApplicationController controller(String suffix) throws Exception {
        var c = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(directory.resolve("settings-"+suffix)),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        c.newPlan(SinglePersonMortalityAnalysisTest.plan()); return c;
    }
    static WeightedResultPersistence persistence(SinglePersonIntegratedView v, AnalysisKind kind) throws Exception {
        if (kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY) return v.ssView.persistence;
        var f = SinglePersonIntegratedView.class.getDeclaredField("persistence"); f.setAccessible(true); return (WeightedResultPersistence) f.get(v);
    }
    static void stop(ApplicationController c) throws Exception {
        c.getSnapshotCopyCompletion().toCompletableFuture().get();
        assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(15)).complete()); c.getAnalysisParameters().shutdown();
        assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(15)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
    }
    @ParameterizedTest @EnumSource(value=AnalysisKind.class,names={"INDIVIDUAL_DETERMINISTIC","INDIVIDUAL_SOCIAL_SECURITY"})
    void actualAcceptedCallbacksPersistAndRestoreWhileFailedAndCanceledRerunsKeepThePreviousResult(AnalysisKind kind) throws Exception {
        var c=controller("original"); var coordinator=new SocialSecurityAnalysisJobCoordinator();
        var jobs=fx(()->new SocialSecurityAnalyzerJobController(coordinator));
        SinglePersonIntegratedView view=null; ApplicationController restored=null;
        try {
            c.getCurrentProjection(); var file=directory.resolve("plan.json"); c.saveAs(file);
            view=fx(()->{ var v=new SinglePersonIntegratedView(c.getCurrentPlan(),jobs); v.attachParameters(c); IndividualCompletionPersistence.attach(v.ssView,c); return v; });
            var v=view;
            fx(()->{ if(kind==AnalysisKind.INDIVIDUAL_DETERMINISTIC) v.start(); else v.ssView.start(); return null; });
            var slot=new AnalysisResultStore.Slot(c.getAnalysisSessionId(),kind);
            for(int i=0;i<1000&&c.getAnalysisResults().inMemory(slot).isEmpty();i++) Thread.sleep(20);
            var accepted=c.getAnalysisResults().inMemory(slot).orElseThrow();
            var p=persistence(view,kind); WeightedRestorationTest.await(p.notice,"Current Results");
            assertEquals(1,c.getAnalyzerAssociations(c.getAcceptedProjectionSnapshot().id()).size());
            var pin=c.getAnalyzerAssociations(c.getAcceptedProjectionSnapshot().id()).getFirst(); assertEquals(accepted.runId(),pin.runId());
            fx(()->{
                if(kind==AnalysisKind.INDIVIDUAL_DETERMINISTIC) v.calculation=r->(progress,token)->{throw new IllegalStateException("Injected failure");};
                else v.ssView.calculation=r->(progress,token)->{throw new IllegalStateException("Injected failure");};
                if(kind==AnalysisKind.INDIVIDUAL_DETERMINISTIC) v.start(); else v.ssView.start(); return null;
            });
            for(int i=0;i<500&&fx(jobs::state)!=SocialSecurityAnalyzerJobController.State.IDLE;i++) Thread.sleep(20);
            assertSame(accepted,c.getAnalysisResults().inMemory(slot).orElseThrow()); assertEquals(pin,c.getAnalyzerAssociations(pin.projectionId()).getFirst());
            fx(()->{
                if(kind==AnalysisKind.INDIVIDUAL_DETERMINISTIC) v.calculation=r->(progress,token)->{while(!token.isCancellationRequested()) Thread.onSpinWait(); token.throwIfCancellationRequested(); throw new AssertionError();};
                else v.ssView.calculation=r->(progress,token)->{while(!token.isCancellationRequested()) Thread.onSpinWait(); token.throwIfCancellationRequested(); throw new AssertionError();};
                if(kind==AnalysisKind.INDIVIDUAL_DETERMINISTIC) v.start(); else v.ssView.start(); jobs.cancel(); return null;
            });
            for(int i=0;i<500&&fx(jobs::state)!=SocialSecurityAnalyzerJobController.State.IDLE;i++) Thread.sleep(20);
            assertSame(accepted,c.getAnalysisResults().inMemory(slot).orElseThrow()); assertEquals(pin,c.getAnalyzerAssociations(pin.projectionId()).getFirst());
            fx(()->{v.closeParameters();jobs.close();return null;});
            assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(15)).safeToExit()); c.save();
            restored=controller("restored"); BaselineDetailsTest.set(restored,"projectionEngine",Phase12EAssociationTest.throwing());
            restored.open(file); restored.getProjectionRestoration().toCompletableFuture().get(); var reopened=restored;
            var newJobs=fx(()->new SocialSecurityAnalyzerJobController(coordinator));
            var next=fx(()->{
                var n=new SinglePersonIntegratedView(reopened.getCurrentPlan(),newJobs); n.attachParameters(reopened); IndividualCompletionPersistence.attach(n.ssView,reopened);
                n.calculation=r->{throw new AssertionError("Restoration cannot calculate deterministic results");};
                n.ssView.calculation=r->{throw new AssertionError("Restoration cannot calculate SS results");};
                return n;
            });
            var persisted=persistence(next,kind); fx(()->{persisted.load();return null;}); WeightedRestorationTest.await(persisted.notice,"Current Results");
            assertEquals(0,fx(newJobs::generation));
            assertEquals(accepted.payload(),new JsonAnalysisSnapshotRepository(directory.resolve("results")).load(reopened.getAnalysisSessionId(),kind).orElseThrow().payload());
            fx(()->{assertTrue(kind==AnalysisKind.INDIVIDUAL_DETERMINISTIC?next.canExportPdf():next.ssView.canExportPdf()); next.closeParameters();newJobs.close();return null;});
        } finally {
            var v=view; fx(()->{if(v!=null)v.closeParameters();jobs.close();return null;}); coordinator.close(); stop(c); if(restored!=null)stop(restored);
        }
    }
}
