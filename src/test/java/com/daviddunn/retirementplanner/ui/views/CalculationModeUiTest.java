package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.*;

class CalculationModeUiTest {
    @TempDir Path directory;
    static final Path OUTPUT=Path.of("target/phase23e/previews");
    @BeforeAll static void start() throws Exception { AssumptionsViewTest.startFx();Files.createDirectories(OUTPUT); }
    static <T>T fx(Callable<T> action) throws Exception {var task=new FutureTask<>(action);Platform.runLater(task);return task.get(30,TimeUnit.SECONDS);}
    ApplicationController controller() {
        return new ApplicationController(new JsonRetirementPlanRepository(),new JsonApplicationSettingsRepository(directory.resolve("settings-"+java.util.UUID.randomUUID()+".json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses"))));
    }
    @Test void addingFirstAnnuityToLegacyInputsDisclosesProspectiveGuardedNiit() throws Exception {
        fx(()->{
            var legacy=NonqualifiedAnnuityFinancialTest.fixture(true);legacy.setConditionalBenefits(java.util.List.of());
            assertNull(legacy.getFinancialCalculationMode());
            var dialog=new com.daviddunn.retirementplanner.ui.dialogs.NonqualifiedAnnuityDialog(legacy,null,false);dialog.show();
            try {
                assertTrue(dialog.getDialogPane().lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label->label.getText().contains("NIIT: domestic")));
                assertTrue(((CheckBox)dialog.getDialogPane().lookup("#annuity-qualification")).getText().contains("NIIT"));
            }finally{dialog.close();}
            assertNull(legacy.getFinancialCalculationMode());assertTrue(legacy.getConditionalBenefits().isEmpty());return null;
        });
    }
    @Test void yearlyDetailsUseOriginalModesAndSuppressAsymmetricHistoricalDifferences() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        var basic=new ProjectionEngine().project(plan).getYearAt(0);plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        var advanced=new ProjectionEngine().project(plan).getYearAt(0);
        fx(()->{
            var pane=new com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane(advanced,basic,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO);
            var stage=new Stage();stage.setScene(new Scene(pane,1180,820));stage.show();
            try {
            pane.applyCss();pane.layout();
            var note=(Label)pane.lookup("#year-financial-calculation-mode");assertNotNull(note);assertTrue(note.isWrapText());
            assertTrue(note.getText().contains("Advanced"));assertTrue(note.getText().contains("NIIT Not Modeled"));
            assertTrue(note.getText().contains("numerical differences are hidden"));
            assertTrue(pane.lookupAll(".comparison-positive").isEmpty());assertTrue(pane.lookupAll(".comparison-negative").isEmpty());
            } finally {stage.close();}
            return null;
        });
    }
    @Test void basicAnnuityEditorStoresContractOnlyFactsAndAdvancedRequiresSeparateConfirmation() throws Exception {
        fx(()->{
            var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
            var original=plan.getConditionalBenefits().getFirst();
            var basic=new com.daviddunn.retirementplanner.ui.dialogs.NonqualifiedAnnuityDialog(plan,original,false);basic.show();
            ((CheckBox)basic.getDialogPane().lookup("#annuity-qualification")).setSelected(true);
            ((Button)basic.getDialogPane().lookupButton(com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitDialog.SAVE)).fire();
            var contract=basic.getResult();assertNotNull(contract);assertEquals(original.id(),contract.id());
            assertEquals(com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityTerms.Qualification.PERSONAL_GENERAL_RULE_FIXED_SINGLE_LIFE_V2,
                    contract.annuityTerms().qualification());
            assertEquals(original,plan.getConditionalBenefits().getFirst());
            plan.setConditionalBenefits(java.util.List.of(contract));plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
            var advanced=new com.daviddunn.retirementplanner.ui.dialogs.NonqualifiedAnnuityDialog(plan,contract,false);advanced.show();
            var acknowledgment=(CheckBox)advanced.getDialogPane().lookup("#annuity-qualification");assertFalse(acknowledgment.isSelected());
            var save=(Button)advanced.getDialogPane().lookupButton(com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitDialog.SAVE);
            save.fire();assertTrue(advanced.isShowing());assertNull(advanced.getResult());
            acknowledgment.setSelected(true);save.fire();assertNotNull(advanced.getResult());
            assertEquals(com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1,
                    advanced.getResult().annuityTerms().qualification());
            assertEquals(contract,plan.getConditionalBenefits().getFirst());return null;
        });
    }
    static void stop(ApplicationController c) throws Exception {
        c.getProjectionRestoration().toCompletableFuture().get(20,TimeUnit.SECONDS);
        c.getSnapshotCopyCompletion().toCompletableFuture().get(20,TimeUnit.SECONDS);
        assertTrue(c.getAnalysisParameters().drain(java.time.Duration.ofSeconds(10)).complete());c.getAnalysisParameters().shutdown();
        assertTrue(c.getAnalysisResults().drain(java.time.Duration.ofSeconds(10)).safeToExit());c.getAnalysisResults().shutdownAfterSuccessfulDrain();
        c.getSettingsService().close();
    }
    @Test void settingsCancelAndDurableDefaultNeverChangeExistingPlanOrRevision() throws Exception {
        var c=controller();
        try {
            var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
            c.newPlan(plan);var projection=c.getCurrentProjection();long revision=c.getSourcePlanRevision();var accepted=c.getAcceptedProjectionSnapshot();
            var manager=fx(()->ApplicationThemeManager.start(ApplicationTheme.LIGHT));
            var dialog=fx(()->{
                var d=new ApplicationSettingsDialog(null,c.getSettingsService(),manager);d.show();
                var choice=(ComboBox<FinancialCalculationMode>)d.getDialogPane().lookup("#default-financial-calculation-mode");
                assertEquals(2,choice.getItems().size());assertFalse(choice.isDisabled());choice.setValue(FinancialCalculationMode.ADVANCED);
                ((Button)d.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();return d;
            });
            assertEquals(FinancialCalculationMode.BASIC,c.getSettingsService().snapshot().getDefaultFinancialCalculationMode());
            var saved=fx(()->{
                var d=new ApplicationSettingsDialog(null,c.getSettingsService(),manager);d.show();
                ((ComboBox<FinancialCalculationMode>)d.getDialogPane().lookup("#default-financial-calculation-mode")).setValue(FinancialCalculationMode.ADVANCED);
                ((Button)d.getDialogPane().lookupButton(d.getDialogPane().getButtonTypes().getFirst())).fire();return d;
            });
            for(int i=0;i<300&&fx(saved::isShowing);i++)Thread.sleep(20);
            assertFalse(fx(saved::isShowing));assertEquals(FinancialCalculationMode.ADVANCED,c.getSettingsService().snapshot().getDefaultFinancialCalculationMode());
            assertEquals(FinancialCalculationMode.BASIC,plan.getFinancialCalculationMode());assertEquals(revision,c.getSourcePlanRevision());
            assertSame(projection,c.peekCurrentProjection());assertEquals(accepted.id(),c.getAcceptedProjectionSnapshot().id());
            assertEquals(FinancialCalculationMode.ADVANCED,c.newPlan().getFinancialCalculationMode());
            fx(()->{manager.close();return null;});
        } finally {stop(c);}
    }
    @Test void assumptionsApplyChangesModeOnceWithoutAutomaticallyProjectingAndCancelIsDetached() throws Exception {
        var c=controller();var calls=new AtomicInteger();
        try {
            var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);c.newPlan(plan);
            set(c,"projectionEngine",new ProjectionEngine(){@Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p){calls.incrementAndGet();return super.projectWithOutcome(p);}});
            fx(()->{
                var window=new MainWindow(c);var stage=new Stage();window.setStage(stage);stage.setScene(window.createScene());stage.show();
                var view=get(window,"assumptionsView",AssumptionsView.class);
                var combo=(ComboBox<FinancialCalculationMode>)view.lookup("#plan-financial-calculation-mode");
                int before=calls.get();long revision=c.getSourcePlanRevision();
                combo.setValue(FinancialCalculationMode.ADVANCED);assertEquals(FinancialCalculationMode.BASIC,plan.getFinancialCalculationMode());
                assertTrue(view.applyChanges());assertEquals(FinancialCalculationMode.ADVANCED,plan.getFinancialCalculationMode());
                assertEquals(before,calls.get());assertEquals(revision+1,c.getSourcePlanRevision());
                combo.setValue(FinancialCalculationMode.BASIC);view.cancelChanges();assertEquals(FinancialCalculationMode.ADVANCED,plan.getFinancialCalculationMode());
                assertEquals(revision+1,c.getSourcePlanRevision());stage.close();return null;
            });
        } finally {stop(c);}
    }
    @Test void lightDarkSettingsAndPlanScenesAtCompactAndLargeSizes() throws Exception {
        for(var theme:ApplicationTheme.values())for(int width:new int[]{900,1180}) {
            int height=width==900?650:820;
            try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve(theme+"-"+width+".json")))) {
                var manager=fx(()->ApplicationThemeManager.start(theme));
                var dialog=fx(()->{
                    var d=new ApplicationSettingsDialog(null,service,manager);d.getDialogPane().setPrefSize(width,height);d.show();
                    get(d,"theme",ComboBox.class).setValue(theme);return d;
                });
                try {
                    pulse(fx(()->dialog.getDialogPane().getScene()));
                    fx(()->{var pane=dialog.getDialogPane();var button=pane.lookupButton(pane.getButtonTypes().getFirst());
                        var bounds=button.localToScene(button.getBoundsInLocal());assertTrue(bounds.getMaxY()<=pane.getScene().getHeight()+1);
                        image(pane,"settings-"+theme+"-"+width+".png");return null;});
                }finally{fx(()->{dialog.close();return null;});}
                var stage=fx(()->{
                    manager.apply(theme);
                    var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(theme==ApplicationTheme.LIGHT?FinancialCalculationMode.BASIC:FinancialCalculationMode.ADVANCED);
                    var view=new AssumptionsView();view.load(plan);
                    var s=new Stage();s.setScene(new Scene(view,width,height));s.show();return s;
                });
                try {pulse(fx(stage::getScene));fx(()->{
                    var scroll=(ScrollPane)stage.getScene().lookup("#assumptions-scroll");
                    var choice=stage.getScene().lookup("#plan-financial-calculation-mode");
                    var bounds=scroll.getContent().sceneToLocal(choice.localToScene(choice.getBoundsInLocal()));
                    double range=scroll.getContent().getBoundsInLocal().getHeight()-scroll.getViewportBounds().getHeight();
                    scroll.setVvalue(range<=0?0:Math.max(0,Math.min(1,(bounds.getMinY()-60)/range)));return null;
                });pulse(fx(stage::getScene));fx(()->{
                    assertTrue(stage.getScene().getRoot().getStylesheets().stream().anyMatch(s->s.endsWith("theme-"+theme.name().toLowerCase(java.util.Locale.ROOT)+".css")));
                    var choice=stage.getScene().lookup("#plan-financial-calculation-mode");assertNotNull(choice);assertTrue(choice.isVisible());
                    var box=choice.localToScene(choice.getBoundsInLocal());assertTrue(box.getMaxY()<stage.getScene().getHeight());
                    image(stage.getScene().getRoot(),"plan-"+theme+"-"+width+".png");return null;});}
                finally {fx(()->{stage.close();manager.close();return null;});}
            }
        }
    }
    static void pulse(Scene scene) throws Exception {
        var done=new CompletableFuture<Void>();fx(()->{scene.addPostLayoutPulseListener(new Runnable(){public void run(){scene.removePostLayoutPulseListener(this);done.complete(null);}});Platform.requestNextPulse();return null;});
        done.get(10,TimeUnit.SECONDS);
    }
    static void image(Node node,String name) throws Exception {
        var image=node.snapshot(null,null);var bitmap=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(bitmap,"png",OUTPUT.resolve(name).toFile());
    }
}
