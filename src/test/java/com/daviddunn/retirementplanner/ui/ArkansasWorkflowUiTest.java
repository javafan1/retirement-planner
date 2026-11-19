package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.IncomeSourcesView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.single;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.validationEngine;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.surplusPlan;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasWorkflowUiTest {
    @BeforeAll static void startFx() throws Exception { PlanFileOperationsTest.startFx(); }

    @Test void loadedPlanProjectsThroughResultsSummaryAndReviewsTheLivePension(@TempDir Path folder) throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var plan = selectArkansas(surplusPlan("1000000", 1));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        var file = folder.resolve("arkansas.json");
        repository.save(plan, file);
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("analyses")));
        var controller = new ApplicationController(repository, new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var owner = PlanFileOperationsTest.fx(() -> {
            controller.open(file);
            var main = new MainWindow(controller);
            var stage = new Stage();
            stage.setScene(main.createScene());
            main.setStage(stage);
            stage.show();
            return stage;
        });
        var release = new CountDownLatch(1);
        try {
            var storage = store.background(() -> {
                assertFalse(Platform.isFxApplicationThread());
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Queued fixture timed out.");
                return null;
            });
            PlanFileOperationsTest.fx(() -> { recalculate(owner); return null; });
            awaitFx(() -> visibleControl("#plan-operation-cancel") != null);
            long cancellation = System.nanoTime();
            PlanFileOperationsTest.fx(() -> {
                assertNull(controller.peekCurrentProjection());
                ((Button) visibleControl("#plan-operation-cancel")).fire();
                assertFalse(controller.isArkansasProjectionRunning());
                return null;
            });
            System.out.printf("Arkansas queued UI cancellation dispatch ms=%.3f%n", (System.nanoTime() - cancellation) / 1_000_000.0);
            assertFalse(storage.toCompletableFuture().isDone());
            release.countDown();
            storage.toCompletableFuture().get(10, TimeUnit.SECONDS);
            PlanFileOperationsTest.fx(() -> { recalculate(owner); return null; });
            awaitFx(() -> controller.getAcceptedProjectionSnapshot() != null);
            var original = PlanFileOperationsTest.fx(controller::getAcceptedProjectionSnapshot);
            assertTrue(original.calculationSemanticsCurrent());
            assertEquals(controller.getAnalysisSessionId(), original.owner());

            PlanFileOperationsTest.fx(() -> {
                var pension = new Pension("Review this pension", PRIMARY, LocalDate.of(2026, 1, 1), null,
                        BigDecimal.valueOf(12000), BigDecimal.ZERO);
                pension.setPlanClassification(PensionPlanClassification.DEFINED_BENEFIT_414J);
                controller.getCurrentPlan().getHousehold().getPrimaryPerson().addIncomeSource(pension);
                controller.markModified();
                recalculate(owner);
                return null;
            });
            openReviewEditor();
            PlanFileOperationsTest.fx(() -> {
                var editor = (DialogPane) visibleControl("#pension-source-origin").getScene().getRoot();
                ((Button) editor.lookupButton(ButtonType.CANCEL)).fire();
                assertEquals(original.id(), controller.getAcceptedProjectionSnapshot().id());
                return null;
            });
            PlanFileOperationsTest.fx(() -> { recalculate(owner); return null; });
            openReviewEditor();
            PlanFileOperationsTest.fx(() -> {
                @SuppressWarnings("unchecked") var origin = (ComboBox<RetirementIncomeSourceOrigin>) visibleControl("#pension-source-origin");
                assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, origin.getValue());
                origin.setValue(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
                ((Button) ((DialogPane) origin.getScene().getRoot()).lookupButton(ButtonType.OK)).fire();
                return null;
            });
            awaitFx(() -> !original.id().equals(controller.getAcceptedProjectionSnapshot().id()));
            var pension = PlanFileOperationsTest.fx(() -> (Pension) controller.getCurrentPlan().getHousehold()
                    .getPrimaryPerson().getIncomeSources().getLast());
            assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN, pension.getSourceOrigin());
            assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J, pension.getPlanClassification());
        } finally {
            release.countDown();
            PlanFileOperationsTest.fx(() -> { owner.hide(); return null; });
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    private static void recalculate(Stage owner) {
        var menuBar = (javafx.scene.control.MenuBar) owner.getScene().lookup(".menu-bar");
        menuBar.getMenus().stream().filter(menu -> "view-menu".equals(menu.getId())).findFirst().orElseThrow()
                .getItems().stream().filter(item -> "view-results-summary".equals(item.getId())).findFirst().orElseThrow().fire();
        var button = (Button) owner.getScene().lookup("#recalculate-projection");
        assertNotNull(button);
        button.fire();
    }

    private static void openReviewEditor() throws Exception {
        awaitFx(() -> visibleControl("#arkansas-pension-review") != null);
        PlanFileOperationsTest.fx(() -> {
            var pane = (DialogPane) visibleControl("#arkansas-pension-review").getScene().getRoot();
            var review = pane.getButtonTypes().stream().filter(type -> "Review Pensions".equals(type.getText())).findFirst().orElseThrow();
            ((Button) pane.lookupButton(review)).fire();
            return null;
        });
        awaitFx(() -> visibleControl("#pension-source-origin") != null);
    }

    private static javafx.scene.Node visibleControl(String selector) {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(scene -> scene.lookup(selector))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static void awaitFx(java.util.concurrent.Callable<Boolean> ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (PlanFileOperationsTest.fx(ready)) return;
            Thread.sleep(15);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for the Arkansas UI operation.");
    }

    @Test void reviewAndProgressUseExistingDialogsInLightAndDark() throws Exception {
        var opening = LocalDate.of(2026, 1, 1);
        var plan = single(LocalDate.of(1965, 7, 1), opening, 1, TAXABLE_FIRST, "500000");
        var pension = new Pension("Company pension", PRIMARY, opening, null, BigDecimal.valueOf(5000), BigDecimal.ZERO);
        pension.setPlanClassification(PensionPlanClassification.DEFINED_BENEFIT_414J);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> validationEngine().project(plan));
        var review = failure.pensionsToReview().getFirst();
        for (var theme : new ApplicationTheme[]{ApplicationTheme.LIGHT, ApplicationTheme.DARK}) {
            PlanFileOperationsTest.fx(() -> {
                try (var themes = ApplicationThemeManager.start(theme)) {
                    var income = new IncomeSourcesView();
                    income.load(plan);
                    var owner = new Stage();
                    owner.setScene(new Scene(income, 850, 680));
                    owner.show();
                    try {
                        String scale = System.getProperty("glass.win.uiScale");
                        if (scale != null) assertEquals(Double.parseDouble(scale), owner.getOutputScaleX(), .01);
                        var warning = new ArkansasProjectionWarning(owner, failure, failure.pensionsToReview());
                        warning.dialog().show();
                        try {
                            var pane = warning.dialog().getDialogPane();
                            pane.applyCss();
                            pane.layout();
                            assertEquals("State Tax Unavailable", warning.dialog().getTitle());
                            var text = (Label) ((javafx.scene.layout.VBox) pane.getContent()).getChildren().getFirst();
                            assertTrue(text.getText().contains("2026"));
                            assertTrue(text.getText().contains("original retirement income source"));
                            assertTrue(text.getHeight() + 1 >= text.prefHeight(text.getWidth()));
                            var button = warning.dialog().getButtonTypes().stream().filter(b -> "Review Pensions".equals(b.getText()))
                                    .findFirst().orElseThrow();
                            assertEquals(review, warning.selectedReview(button).orElseThrow());
                            assertTrue(warning.selectedReview(ButtonType.CLOSE).isEmpty());
                            capture(pane, "warning-" + theme + "-" + scale);
                        } finally {
                            warning.dialog().close();
                        }
                        var feedback = new PlanOperationFeedback(owner, "Calculating Arkansas projection");
                        try {
                            var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
                            feedback.phase("Year 1 complete. Calculating Arkansas projection.", () -> cancelled.set(true));
                            feedback.showSubtle();
                            capture(feedback.view(), "progress-" + theme + "-" + scale);
                            ((Button) feedback.view().lookup("#plan-operation-cancel")).fire();
                            assertTrue(cancelled.get());
                        } finally {
                            feedback.close();
                        }

                        income.selectPension(ArkansasPensionPreflight.resolve(plan.getHousehold(), review).orElseThrow());
                        setOriginInOpenEditor(income, pension, ButtonType.CANCEL);
                        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, pension.getSourceOrigin());
                        if (theme == ApplicationTheme.DARK) {
                            setOriginInOpenEditor(income, pension, ButtonType.OK);
                            var saved = (Pension) plan.getHousehold().getPrimaryPerson().getIncomeSources().getLast();
                            assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN, saved.getSourceOrigin());
                            assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J, saved.getPlanClassification());
                        }
                    } finally {
                        owner.hide();
                    }
                }
                return null;
            });
        }
        assertDoesNotThrow(() -> validationEngine().project(plan));
    }

    private static void setOriginInOpenEditor(IncomeSourcesView income, Pension pension, ButtonType button) {
        var caught = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            var pane = Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                    .filter(java.util.Objects::nonNull).map(Scene::getRoot).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).filter(p -> p.lookup("#pension-source-origin") != null).findFirst().orElseThrow();
            try {
                @SuppressWarnings("unchecked")
                var source = (ComboBox<RetirementIncomeSourceOrigin>) pane.lookup("#pension-source-origin");
                assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, source.getValue());
                source.setValue(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            } catch (Throwable failure) {
                caught.set(failure);
            } finally {
                ((Button) pane.lookupButton(button)).fire();
            }
        });
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        income.editSelectedPension(pension, () -> accepted.set(true));
        assertEquals(button == ButtonType.OK, accepted.get());
        assertNull(caught.get());
    }

    private static void capture(javafx.scene.Node node, String name) throws Exception {
        var folder = Files.createDirectories(Path.of("target/phase6e-p3d1-previews"));
        var snapshot = node.snapshot(null, null);
        int width = (int) snapshot.getWidth();
        int height = (int) snapshot.getHeight();
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        }
        javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }
}
