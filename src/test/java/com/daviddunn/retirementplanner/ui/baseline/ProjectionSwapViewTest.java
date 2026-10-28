package com.daviddunn.retirementplanner.ui.baseline;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenAnalyzer;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartView;
import com.daviddunn.retirementplanner.ui.socialsecurity.ProjectionAnalyzerHistoryDialog;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set;

class ProjectionSwapViewTest {
    @TempDir Path dir;
    final List<ApplicationController> sources = new ArrayList<>();
    @BeforeAll static void startFx() throws Exception { BaselineDetailsViewTest.startFx(); }
    static <T> T fx(Callable<T> action) throws Exception { return BaselineDetailsViewTest.fx(action); }
    ApplicationController source(boolean couple) throws Exception {
        var c = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("settings"+sources.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("slots"+sources.size()))));
        sources.add(c); c.newPlan(BaselineDetailsTest.plan(couple)); c.getCurrentProjection(); c.saveCurrentAsBaseline("Original baseline");
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1300000.009900"));
        c.invalidateProjection(); c.getCurrentProjection(); c.saveAs(dir.resolve("plan"+sources.size()+".json")); c.getBaselineProjection();
        set(c,"restoredProjectionMode",true); // Display the exact accepted data, as the restored view does.
        set(c,"projectionEngine",new ProjectionEngine() {
            @Override public Projection project(RetirementPlan p) { throw new AssertionError("UI must not project"); }
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw new AssertionError("UI must not project"); }
        });
        set(c,"breakEvenExplicitOnly",true);
        set(c,"breakEvenAnalyzerFactory",(java.util.function.Supplier<BreakEvenAnalyzer>) () -> { throw new AssertionError("Rendering must not analyze"); });
        return c;
    }
    @AfterEach void finish() throws Exception {
        for(var c:sources) {
            c.getPlanPreferenceWrite().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete()); c.getAnalysisParameters().shutdown();
            assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
        }
    }
    static void image(Node node,String name) throws Exception {
        var output=Path.of("target/phase12f-previews"); Files.createDirectories(output);
        node.applyCss(); if(node instanceof Parent parent) parent.layout();
        var picture=node.snapshot(null,null); var pixels=new java.awt.image.BufferedImage((int)picture.getWidth(),(int)picture.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<pixels.getHeight();y++) for(int x=0;x<pixels.getWidth();x++) pixels.setRGB(x,y,picture.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(pixels,"png",output.resolve(name+".png").toFile());
    }
    static void theme(Scene scene,boolean dark) {
        scene.getStylesheets().add(ProjectionSwapViewTest.class.getResource("/css/application.css").toExternalForm());
        if(dark) scene.getRoot().setStyle("-fx-base:#202328;-fx-background:#202328;-fx-control-inner-background:#252830;-fx-text-inner-color:#f1f1f1;-fx-text-base-color:#f1f1f1;");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void summaryAndBaselineDetailsShowOriginalSwappedValuesWithoutCalculation(boolean couple) throws Exception {
        var c=source(couple); var original=c.getCurrentPlan().getProjectionState();
        fx(() -> {
            var chart=new ProjectionChartView(); chart.load(c.getAcceptedProjectionChart(c.peekCurrentProjection()));
            var root=new VBox(new Label("Before swap — Current ending "+c.peekCurrentProjection().getEndYear()),chart);
            new Scene(root,1180,820); image(root,(couple?"couple":"individual")+"-before"); return null;
        });
        var prepared=c.prepareProjectionSwap().toCompletableFuture().get(); fx(() -> {assertTrue(c.commitProjectionSwap(prepared));return null;});
        fx(() -> {
            var view=new ResultsSummaryView(c); view.load(c.getCurrentPlan(),c.peekCurrentProjection(),c.getCurrentNonInvestableAssetProjections());
            var summaryScene = new Scene(view,couple?1180:900,couple?820:650); theme(summaryScene,!couple); view.applyCss(); view.layout();
            var chart=new ProjectionChartView(); chart.load(c.getAcceptedProjectionChart(c.peekCurrentProjection()));
            var root=new VBox(new Label("After swap — Original snapshot "+c.getAcceptedProjectionSnapshot().id()),chart);
            var scene=new Scene(root,couple?1180:900,couple?820:650);theme(scene,!couple);image(root,(couple?"couple":"individual")+"-after");
            var swap=(Button)view.lookup("#swap-projections-action"); assertNotNull(swap); assertFalse(swap.isDisabled()); assertTrue(swap.isFocusTraversable());
            var siblings=((Pane)swap.getParent()).getChildren(); assertEquals(siblings.indexOf(view.lookup("#baseline-details-action"))+1,siblings.indexOf(swap));
            image(swap.getParent(),(couple?"couple":"individual")+"-swap-actions");
            assertNull(c.getCachedBreakEvenAnalysis()); assertEquals(original.baseline().id(),c.getAcceptedProjectionSnapshot().id());
            var session=c.prepareBaselineDetails(c.peekCurrentProjection()); assertNotNull(session);
            var details=new BaselineDetailsView(session); var scroller=new ScrollPane(details);scroller.setFitToWidth(true);
            var detailsScene=new Scene(new StackPane(scroller),couple?1180:900,couple?820:650);theme(detailsScene,!couple); image(detailsScene.getRoot(),(couple?"couple":"individual")+"-baseline-details");
            var history=new ProjectionAnalyzerHistoryDialog(c,c.getAcceptedProjectionSnapshot().id());
            history.show(); image(history.getDialogPane(),(couple?"couple":"individual")+"-analyzer-history"); history.close();
            assertTrue(c.isModified()); assertTrue(c.currentProjectionInputsMatch()); return null;
        });
    }
    @Test void dirtyConfirmationCancelLeavesEntireArrangementUnchangedAndStaleRefusalExplainsWhy() throws Exception {
        var c=source(true); var prepared=c.prepareProjectionSwap().toCompletableFuture().get();
        fx(() -> {c.commitProjectionSwap(prepared);return null;});
        var owner=c.getCurrentPlan();var roles=owner.getProjectionState();var document=c.getAcceptedProjectionSnapshot().inputsDocument();
        fx(() -> {
            var window=new MainWindow(c);var stage=new Stage();window.setStage(stage);stage.setScene(window.createScene());stage.show();
            try {
                Platform.runLater(() -> {
                    var dialogs=Window.getWindows().stream().filter(w->w.isShowing()&&w!=stage).toList();
                    var pane=dialogs.stream().map(w->w.getScene().getRoot()).filter(n->n instanceof DialogPane).map(n->(DialogPane)n).findFirst().orElseThrow();
                    assertEquals("Save Before Swapping Projections?",pane.getHeaderText());
                    try{image(pane,"save-before-swap");}catch(Exception e){throw new RuntimeException(e);}
                    ((Button)pane.lookupButton(ButtonType.CANCEL)).fire();
                });
                var method=MainWindow.class.getDeclaredMethod("swapCurrentAndBaseline");method.setAccessible(true);method.invoke(window);
                assertSame(owner,c.getCurrentPlan());assertEquals(roles,owner.getProjectionState());assertTrue(c.isModified());
                assertEquals(document,c.getAcceptedProjectionSnapshot().inputsDocument());
                owner.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("3"));
                var message=c.projectionSwapUnavailableReason();assertTrue(message.contains("matching"));
                var explanation=new Label(message);explanation.setWrapText(true);var root=new StackPane(explanation);new Scene(root,900,200);image(root,"stale-input-refusal");
            } finally {stage.hide();}
            return null;
        });
    }
    @Test void saveAndContinueRunsActualWindowTransactionAndIgnoresRepeatedClick() throws Exception {
        var c=source(false);var first=c.prepareProjectionSwap().toCompletableFuture().get();fx(()->{c.commitProjectionSwap(first);return null;});
        var owner=c.getCurrentPlan();var roles=owner.getProjectionState();var stage=new Stage[1];
        fx(()->{
            var window=new MainWindow(c);stage[0]=new Stage();window.setStage(stage[0]);stage[0].setScene(window.createScene());stage[0].show();
            Platform.runLater(()->{
                var pane=Window.getWindows().stream().filter(w->w.isShowing()&&w!=stage[0]).map(w->w.getScene().getRoot())
                        .filter(n->n instanceof DialogPane).map(n->(DialogPane)n).findFirst().orElseThrow();
                var save=pane.getButtonTypes().stream().filter(b->b.getText().equals("Save and Continue")).findFirst().orElseThrow();
                ((Button)pane.lookupButton(save)).fire();
            });
            var action=MainWindow.class.getDeclaredMethod("swapCurrentAndBaseline");action.setAccessible(true);action.invoke(window);action.invoke(window);
            return null;
        });
        try {
            long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
            while(c.getCurrentPlan()==owner&&System.nanoTime()<deadline)Thread.sleep(10);
            fx(()->{
                assertNotSame(owner,c.getCurrentPlan());assertEquals(roles.baseline().id(),c.getAcceptedProjectionSnapshot().id());
                assertTrue(c.isModified());assertTrue(c.currentProjectionInputsMatch());assertNull(c.getCachedBreakEvenAnalysis());return null;
            });
            var durable=new JsonRetirementPlanRepository().load(c.getCurrentFile());assertEquals(roles,durable.getProjectionState(),"Save-before committed the old roles; the new arrangement still requires explicit Save");
        } finally {fx(()->{stage[0].hide();return null;});}
    }
    @Test void historicalKindsFollowEachOriginalHouseholdShapeAfterPromotion() throws Exception {
        var c=source(true);
        // Existing baseline is couple; accept a single-person current projection through the normal path.
        c.getCurrentPlan().setSpouse(null);c.invalidateProjection();
        set(c,"projectionEngine",new ProjectionEngine());c.getCurrentProjection();c.save();
        var prepared=c.prepareProjectionSwap().toCompletableFuture().get();fx(()->{c.commitProjectionSwap(prepared);return null;});
        fx(()->{
            assertTrue(c.getCurrentPlan().getHousehold().hasSpouse());
            var history=new ProjectionAnalyzerHistoryDialog(c,c.getAcceptedProjectionSnapshot().id());
            var roles=BaselineDetailsTest.get(history,"roles",ComboBox.class);var kinds=BaselineDetailsTest.get(history,"kinds",ComboBox.class);
            assertTrue(kinds.getItems().stream().allMatch(k->((AnalysisKind)k).shape()==AnalysisKind.Shape.COUPLE));
            roles.getSelectionModel().select(1);
            assertTrue(kinds.getItems().stream().allMatch(k->((AnalysisKind)k).shape()==AnalysisKind.Shape.SINGLE));
            history.close();return null;
        });
    }
    @Test void canceledPreparationCannotReleaseOrCancelNewerSwap() throws Exception {
        var c = source(true); var owner = c.getCurrentPlan(); var roles = owner.getProjectionState();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var blocker = c.getAnalysisResults().background(() -> {
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test worker timed out");
            return null;
        });
        assertTrue(entered.await(10, TimeUnit.SECONDS)); var stage = new Stage[1];
        try {
            fx(() -> {
                var window = new MainWindow(c); stage[0] = new Stage(); window.setStage(stage[0]);
                var scene = window.createScene(); stage[0].setScene(scene); stage[0].show();
                var action = MainWindow.class.getDeclaredMethod("swapCurrentAndBaseline"); action.setAccessible(true);
                action.invoke(window);
                var feedbackField = MainWindow.class.getDeclaredField("swapFeedback"); feedbackField.setAccessible(true);
                var feedback = feedbackField.get(window); var show = feedback.getClass().getDeclaredMethod("showSubtle"); show.setAccessible(true); show.invoke(feedback);
                var dialog = javafx.stage.Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == stage[0]).findFirst().orElseThrow();
                var cancel = (Button) dialog.getScene().lookup("#plan-operation-cancel"); assertNotNull(cancel); assertTrue(cancel.isVisible());
                cancel.fire();
                assertSame(owner, c.getCurrentPlan()); assertEquals(roles, owner.getProjectionState());
                assertFalse(scene.getRoot().lookup(".menu-bar").isDisabled());
                action.invoke(window); // Old work is still queued; its callback must not cancel this request.
                assertTrue(scene.getRoot().lookup(".menu-bar").isDisabled()); return null;
            });
            release.countDown(); blocker.toCompletableFuture().get(15, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (c.getCurrentPlan() == owner && System.nanoTime() < deadline) Thread.sleep(10);
            fx(() -> {
                assertNotSame(owner, c.getCurrentPlan()); assertEquals(roles.baseline().id(), c.getAcceptedProjectionSnapshot().id());
                assertFalse(stage[0].getScene().lookup(".menu-bar").isDisabled()); return null;
            });
        } finally { release.countDown(); fx(() -> { if (stage[0] != null) stage[0].hide(); return null; }); }
    }
}
