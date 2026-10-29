package com.daviddunn.retirementplanner.ui.introduction;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerIntroductionMenuTest {
    @TempDir Path directory;
    @BeforeAll static void start()throws Exception{AnalyzerIntroductionUiTest.start();}
    static MenuItem item(Stage owner,String name){return ((MenuBar)owner.getScene().lookup(".menu-bar")).getMenus().stream().flatMap(m->m.getItems().stream()).filter(i->name.equals(i.getText())).findFirst().orElseThrow();}
    static void forbidProjection(ApplicationController c)throws Exception{
        var field=c.getClass().getDeclaredField("projectionEngine");field.setAccessible(true);field.set(c,new ProjectionEngine(){
            @Override public Projection project(RetirementPlan p){throw new AssertionError("Introductions cannot project");}
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p){throw new AssertionError("Introductions cannot project");}
        });
    }
    @Test void changedPlanCannotLaunchPendingMenuSelection()throws Exception{
        var c=new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings")),new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        c.newPlan(BaselineDetailsTest.plan(true));c.getCurrentProjection();forbidProjection(c);
        try{
            AnalyzerIntroductionUiTest.fx(()->{
                var window=new MainWindow(c);var owner=new Stage();window.setStage(owner);owner.setScene(window.createScene());owner.show();
                try{
                    item(owner,"Monte Carlo Retirement Analysis...").fire();var pending=AnalyzerIntroductionUiTest.pane(owner);
                    var replacement=BaselineDetailsTest.plan(false);c.newPlan(replacement);AnalyzerIntroductionUiTest.continued(pending);
                    assertSame(replacement,c.getCurrentPlan());
                    assertEquals(0,Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==owner).count());
                }finally{owner.hide();}return null;
            });
        }finally{c.getAnalysisParameters().shutdown();c.getAnalysisResults().drain(java.time.Duration.ofSeconds(10));c.getAnalysisResults().shutdownAfterSuccessfulDrain();c.getSettingsService().close();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void realMenuPathsRetainCorrectEntryAndNoWorkBeforeContinue(boolean couple)throws Exception{
        var c=new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings")),new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        c.newPlan(BaselineDetailsTest.plan(couple));c.getCurrentProjection();c.saveCurrentAsBaseline("Original");forbidProjection(c);
        var before=CompleteProjectionSnapshot.freeze(c.getCurrentPlan());var snapshot=c.getAcceptedProjectionSnapshot();boolean dirty=c.isModified();long revision=c.getSourcePlanRevision();var stage=new Stage[1];
        try{
            AnalyzerIntroductionUiTest.fx(()->{
                var window=new MainWindow(c);stage[0]=new Stage();window.setStage(stage[0]);stage[0].setScene(window.createScene());stage[0].show();
                item(stage[0],"Social Security Strategy Analyzer...").fire();var intro=AnalyzerIntroductionUiTest.pane(stage[0]);
                assertEquals(couple?"Social Security Only":"Deterministic Integrated",((Stage)intro.getScene().getWindow()).getTitle());AnalyzerIntroductionUiTest.canceled(intro);
                for(var name:List.of("Monte Carlo Retirement Analysis...","Monte Carlo Strategy Comparison...")){
                    var menu=item(stage[0],name);menu.fire();intro=AnalyzerIntroductionUiTest.pane(stage[0]);
                    assertTrue(intro.lookup("#suppress-analyzer-introduction")!=null);menu.fire();
                    assertEquals(1,Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==stage[0]).count());
                    AnalyzerIntroductionUiTest.continued(intro);var result=AnalyzerIntroductionUiTest.pane(stage[0]);
                    var content=((ScrollPane)result.getContent()).getContent();
                    assertEquals(name.startsWith("Monte Carlo Retirement")?MonteCarloAnalysisView.class:MonteCarloStrategyComparisonView.class,content.getClass());
                    menu.fire();assertEquals(1,Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==stage[0]).count());
                    ((Button)result.lookupButton(ButtonType.CLOSE)).fire();
                }
                item(stage[0],"Social Security Strategy Analyzer...").fire();var pending=AnalyzerIntroductionUiTest.pane(stage[0]);
                Platform.runLater(()->AnalyzerIntroductionUiTest.continued(pending));return null;
            });
            AnalyzerIntroductionUiTest.fx(()->{
                var analyzer=Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getOwner()==stage[0]&&s.getTitle().equals("Social Security Strategy Analyzer")).findFirst().orElseThrow();
                assertNull(analyzer.getScene().lookup("#suppress-analyzer-introduction"));((Stage)analyzer).close();return null;
            });
            assertEquals(before,CompleteProjectionSnapshot.freeze(c.getCurrentPlan()));assertSame(snapshot,c.getAcceptedProjectionSnapshot());assertEquals(dirty,c.isModified());assertEquals(revision,c.getSourcePlanRevision());
        }finally{
            AnalyzerIntroductionUiTest.fx(()->{if(stage[0]!=null)stage[0].hide();return null;});
            c.getAnalysisParameters().shutdown();c.getAnalysisResults().drain(java.time.Duration.ofSeconds(10));c.getAnalysisResults().shutdownAfterSuccessfulDrain();c.getSettingsService().close();
        }
    }
}
