package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.introduction.AnalyzerIntroductionDialog;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.Mode.*;

class AnalyzerIntroductionNavigationTest {
    @TempDir Path directory;
    @BeforeAll static void start()throws Exception{ClaimingStrategyHeatMapViewTest.startFx();}
    static <T>T fx(Callable<T> task)throws Exception{var f=new FutureTask<>(task);Platform.runLater(f);return f.get(30,TimeUnit.SECONDS);}
    static <T>T field(Object o,String name,Class<T> type)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(o));}
    static DialogPane intro(Stage owner){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==owner).map(w->w.getScene().getRoot().lookup(".dialog-pane"))
            .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).filter(p->p.lookup("#suppress-analyzer-introduction")!=null).findFirst().orElseThrow();}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualChoicesCancelAndContinuePreserveInputsAndNeverAdmitWork(boolean couple)throws Exception{
        var c=new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings")),new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        c.newPlan(BaselineDetailsTest.plan(couple));c.getCurrentProjection();
        var snapshot=c.getAcceptedProjectionSnapshot();var frozen=CompleteProjectionSnapshot.freeze(c.getCurrentPlan());boolean dirty=c.isModified();long revision=c.getSourcePlanRevision();
        try{
            fx(()->{
                var executor=new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();var coordinator=new SocialSecurityAnalysisJobCoordinator(executor);
                var jobs=new SocialSecurityAnalyzerJobController(coordinator,Runnable::run);var owner=new Stage();owner.setScene(new Scene(new VBox(),900,650));owner.show();
                var dialog=new SocialSecurityStrategyAnalyzerDialog(owner,c,jobs);dialog.configureIntroductions();var stage=field(dialog,"stage",Stage.class);stage.show();
                var navigation=couple?field(dialog,"navigation",AnalyzerNavigationView.class):field(field(dialog,"singlePersonView",SinglePersonIntegratedView.class),"navigation",AnalyzerNavigationView.class);
                try{
                    var modes=couple?List.of(QUICK,EXHAUSTIVE,WEIGHTED,SOCIAL_SECURITY):List.of(SOCIAL_SECURITY,WEIGHTED,EXHAUSTIVE);
                    for(var target:modes){
                        var old=navigation.selected();navigation.requestSelect(target);var pane=intro(stage);
                        assertSame(old,navigation.selected());((Button)pane.lookupButton(ButtonType.CANCEL)).fire();assertSame(old,navigation.selected());
                        navigation.requestSelect(target);((Button)intro(stage).lookupButton(AnalyzerIntroductionDialog.CONTINUE)).fire();assertEquals(target,navigation.selected().mode());
                        assertEquals(0,jobs.generation());assertEquals(SocialSecurityAnalyzerJobController.State.IDLE,jobs.state());assertTrue(executor.queue.isEmpty());
                    }
                    var old=navigation.selected();navigation.select(modes.getFirst());assertNotSame(old,navigation.selected());
                    assertEquals(0,Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==stage).count(),"Passive selection must not open an introduction");
                    var refocused=new java.util.concurrent.atomic.AtomicInteger();
                    navigation.requestSelect(navigation.selected().mode(),refocused::incrementAndGet);
                    assertEquals(1,refocused.get(),"Reselecting the current workspace must retain keyboard focus restoration without an introduction");
                    if(couple){navigation.select(QUICK);assertTrue(field(dialog,"integratedRunButton",Button.class).isDisabled());}
                }finally{stage.close();owner.hide();coordinator.close();}
                return null;
            });
            assertEquals(frozen,CompleteProjectionSnapshot.freeze(c.getCurrentPlan()));assertSame(snapshot,c.getAcceptedProjectionSnapshot());assertEquals(dirty,c.isModified());assertEquals(revision,c.getSourcePlanRevision());
        }finally{c.getAnalysisParameters().shutdown();c.getAnalysisResults().drain(java.time.Duration.ofSeconds(10));c.getAnalysisResults().shutdownAfterSuccessfulDrain();c.getSettingsService().close();}
    }
}
