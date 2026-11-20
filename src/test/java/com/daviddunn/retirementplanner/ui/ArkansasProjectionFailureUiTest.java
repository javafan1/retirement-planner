package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.surplusPlan;
import static com.daviddunn.retirementplanner.ui.PlanFileOperationsTest.fx;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasProjectionFailureUiTest {
    @BeforeAll static void startFx() throws Exception { PlanFileOperationsTest.startFx(); }

    @ParameterizedTest
    @EnumSource(ApplicationTheme.class)
    void realProjectionWarningAcknowledgesAndKeepsRecoveryAndHistoryAvailable(ApplicationTheme theme, @TempDir Path folder)
            throws Exception {
        try (var fixture = open(folder, theme)) {
            fx(() -> { fixture.recalculate(); return null; });
            await(() -> fixture.controller.getAcceptedProjectionSnapshot() != null);
            var original = fx(fixture.controller::getAcceptedProjectionSnapshot);
            var definition = ordinaryBenefit();
            fx(() -> {
                fixture.menu("File", "Save Current as Baseline");
                fixture.addBenefit(definition);
                fixture.recalculate().requestFocus();
                return null;
            });
            await(() -> !warnings().isEmpty());
            fx(() -> {
                var pane = warnings().getFirst();
                var warning = (Stage) pane.getScene().getWindow();
                var message = (Label) pane.lookup("#projection-failure-message");
                assertEquals("Unable to Calculate Projection", warning.getTitle());
                assertSame(fixture.owner, warning.getOwner());
                assertEquals(Modality.WINDOW_MODAL, warning.getModality());
                assertEquals(List.of(ButtonType.OK), pane.getButtonTypes());
                assertTrue(ok(pane).isVisible());
                assertTrue(ok(pane).isDefaultButton());
                assertTrue(ok(pane).isCancelButton());
                assertNotNull(pane.getGraphic());
                assertTrue(message.isWrapText());
                assertEquals(ProjectionFailureMessages.ARKANSAS_CONDITIONAL_INCOME, message.getText());
                assertTrue(message.getHeight() >= message.prefHeight(message.getWidth()) - 1);
                assertTrue(pane.getStylesheets().stream().anyMatch(sheet ->
                        sheet.endsWith(theme == ApplicationTheme.DARK ? "theme-dark.css" : "theme-light.css")));
                assertCentered(fixture.owner, warning);
                assertNoTechnicalText(fixture.status());
                assertTrue(((Label) fixture.owner.getScene().lookup("#projection-funding-notice"))
                        .getText().contains("Previous results retained"));
                assertFalse(fixture.controller.isArkansasProjectionRunning());
                assertEquals(original, fixture.controller.getAcceptedProjectionSnapshot());
                assertEquals(original.id(), fixture.controller.getCurrentPlan().getProjectionState().baseline().id());
                assertEquals(StateTaxJurisdiction.AR, fixture.state());
                assertEquals(List.of(definition), fixture.controller.getCurrentPlan().getConditionalBenefits());
                capture(pane, theme, warning.getOutputScaleX());
                press(pane, KeyCode.ENTER);
                return null;
            });
            await(() -> warnings().isEmpty());
            await(() -> fixture.owner.getScene().getFocusOwner() == fixture.owner.getScene().lookup("#recalculate-projection"));
            fx(() -> {
                fixture.menu("View", "Income");
                var benefits = fixture.owner.getScene().lookup("#conditional-benefits-section");
                assertNotNull(benefits);
                assertTrue(benefits.isVisible());
                fixture.menu("View", "Household");
                fixture.menu("View", "Assumptions");
                assertFalse(fixture.stateChoice().isDisabled());
                fixture.stateChoice().setValue(StateTaxJurisdiction.TX);
                fixture.assumptionsButton("Cancel").fire();
                assertEquals(StateTaxJurisdiction.AR, fixture.state());
                fixture.menu("View", "Assumptions");
                fixture.stateChoice().setValue(StateTaxJurisdiction.TX);
                fixture.assumptionsButton("Apply").fire();
                assertEquals(StateTaxJurisdiction.TX, fixture.state());
                assertNotEquals(original.id(), fixture.controller.getAcceptedProjectionSnapshot().id());
                fixture.recalculate();
                return null;
            });
            fx(() -> { fixture.menu("File", "Save"); return null; });
            var reloaded = fixture.repository.load(fixture.file);
            assertEquals(StateTaxJurisdiction.TX, reloaded.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            assertEquals(List.of(definition), reloaded.getConditionalBenefits());
            var manifest = reloaded.getProjectionState();
            assertEquals(original.id(), manifest.baseline().id());
            var baseline = new ProjectionSnapshotComponents().read(fixture.file, manifest.owner(), manifest.baseline());
            assertEquals(original.annual(), baseline.annual());
            assertEquals(original.inputDigest(), baseline.inputDigest());
            assertEquals(original.provenance(), baseline.provenance());
        }
    }

    @Test
    void explicitRetryCanWarnAgainButViewChangesAndSupersessionDoNotRepeatWarnings(@TempDir Path folder) throws Exception {
        try (var fixture = open(folder, ApplicationTheme.LIGHT)) {
            var count = new java.util.concurrent.atomic.AtomicInteger();
            javafx.collections.ListChangeListener<Window> listener = change -> {
                while (change.next()) for (var added : change.getAddedSubList()) {
                    if (added.getScene() != null && "projection-failure-warning".equals(added.getScene().getRoot().getId()))
                        count.incrementAndGet();
                }
            };
            fx(() -> {
                Window.getWindows().addListener(listener);
                fixture.addBenefit(ordinaryBenefit());
                fixture.recalculate();
                fixture.recalculate();
                return null;
            });
            try {
                await(() -> !warnings().isEmpty());
                fx(() -> { press(warnings().getFirst(), KeyCode.ESCAPE); return null; });
                await(() -> warnings().isEmpty());
                fx(() -> {
                    fixture.menu("View", "Income");
                    fixture.menu("View", "Results Summary");
                    var passiveRefresh = MainWindow.class.getDeclaredMethod("refreshProjectionViews", boolean.class, boolean.class);
                    passiveRefresh.setAccessible(true);
                    passiveRefresh.invoke(fixture.window, false, true);
                    assertEquals(1, count.get());
                    assertTrue(warnings().isEmpty());
                    fixture.recalculate();
                    return null;
                });
                await(() -> !warnings().isEmpty());
                fx(() -> {
                    assertEquals(2, count.get());
                    var window = warnings().getFirst().getScene().getWindow();
                    Event.fireEvent(window, new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
                    return null;
                });
                await(() -> warnings().isEmpty());
                assertNull(fx(fixture.controller::getAcceptedProjectionSnapshot));
            } finally {
                fx(() -> { Window.getWindows().removeListener(listener); return null; });
            }
        }
    }

    @Test
    void queuedCancellationAndPlanDepartureSuppressWarningsAndClearFeedback(@TempDir Path folder) throws Exception {
        try (var fixture = open(folder, ApplicationTheme.LIGHT)) {
            var replacement = folder.resolve("replacement.json");
            fixture.repository.save(surplusPlan("1000000", 1), replacement);
            var release = new CountDownLatch(1);
            var queued = fixture.store.background(() -> release.await(10, TimeUnit.SECONDS));
            try {
                fx(() -> { fixture.addBenefit(ordinaryBenefit()); fixture.recalculate(); return null; });
                await(() -> visible("#plan-operation-cancel") != null);
                var opening = fx(() -> {
                    ((Button) visible("#plan-operation-cancel")).fire();
                    assertFalse(fixture.controller.isArkansasProjectionRunning());
                    assertNull(visible("#plan-operation-cancel"));
                    fixture.recalculate();
                    fixture.controller.markModified();
                    fixture.recalculate();
                    return fixture.controller.preparePlanOpen(replacement);
                });
                var opened = opening.toCompletableFuture().get(10, TimeUnit.SECONDS);
                fx(() -> {
                    fixture.controller.finishPlanOpen(opened);
                    assertFalse(fixture.controller.isArkansasProjectionRunning());
                    assertEquals(replacement, fixture.controller.getCurrentFile());
                    return null;
                });
            } finally {
                release.countDown();
            }
            assertTrue(queued.toCompletableFuture().get(10, TimeUnit.SECONDS));
            fixture.store.background(() -> null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fx(() -> { assertTrue(warnings().isEmpty()); return null; });
        }
    }

    @Test
    void failureWaitsForAnOwnedModalAndAClosedOwnerCannotDisplayDeferredWarning(@TempDir Path folder) throws Exception {
        try (var fixture = open(folder, ApplicationTheme.LIGHT)) {
            var blockingModal = fx(() -> {
                fixture.addBenefit(ordinaryBenefit());
                fixture.recalculate();
                return blocker(fixture.owner);
            });
            try {
                await(() -> fixture.status().contains("Arkansas conditional income"));
                fx(() -> {
                    assertTrue(warnings().isEmpty());
                    assertFalse(fixture.controller.isArkansasProjectionRunning());
                    assertNull(visible("#plan-operation-cancel"));
                    blockingModal.hide();
                    return null;
                });
                await(() -> !warnings().isEmpty());
                fx(() -> { ok(warnings().getFirst()).fire(); return null; });
            } finally {
                fx(() -> { blockingModal.hide(); return null; });
            }
            var staleBlocker = fx(() -> {
                fixture.recalculate();
                return blocker(fixture.owner);
            });
            try {
                await(() -> fixture.status().contains("Arkansas conditional income"));
                fx(() -> {
                    // Exercise the frozen-input fence even if a source edit did not notify the window.
                    fixture.controller.getCurrentPlan().setConditionalBenefits(List.of(ordinaryBenefit()));
                    staleBlocker.hide();
                    return null;
                });
                fx(() -> { assertTrue(warnings().isEmpty()); return null; });
            } finally {
                fx(() -> { staleBlocker.hide(); return null; });
            }
            var lastBlocker = fx(() -> {
                fixture.recalculate();
                return blocker(fixture.owner);
            });
            try {
                await(() -> fixture.status().contains("Arkansas conditional income"));
                fx(() -> { fixture.owner.hide(); lastBlocker.hide(); return null; });
                fx(() -> { assertTrue(warnings().isEmpty()); return null; });
            } finally {
                fx(() -> { lastBlocker.hide(); return null; });
            }
        }
    }

    @Test
    void directRefusalUsesTheSameWarningAndUnknownFailureUsesSafeCopy(@TempDir Path folder) throws Exception {
        try (var fixture = open(folder, ApplicationTheme.LIGHT)) {
            fx(() -> {
                var failure = assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                        () -> ConditionalBenefitAdmission.requireDeterministicState(StateTaxJurisdiction.AR));
                var synchronous = MainWindow.class.getDeclaredMethod("handleProjectionFailure", Exception.class);
                synchronous.setAccessible(true);
                synchronous.invoke(fixture.window, failure);
                var pane = warnings().getFirst();
                assertEquals(ProjectionFailureMessages.ARKANSAS_CONDITIONAL_INCOME,
                        ((Label) pane.lookup("#projection-failure-message")).getText());
                ok(pane).fire();
                var asynchronous = MainWindow.class.getDeclaredMethod("presentArkansasProjectionFailure",
                        Exception.class, ApplicationController.ControlledProjectionRun.class);
                asynchronous.setAccessible(true);
                asynchronous.invoke(fixture.window, new java.util.concurrent.CompletionException(
                        new IllegalStateException("com.example.Internal projection detail")), null);
                pane = warnings().getFirst();
                var text = ((Label) pane.lookup("#projection-failure-message")).getText();
                assertEquals(ProjectionFailureMessages.ARKANSAS_UNEXPECTED_FAILURE, text);
                assertNoTechnicalText(text);
                assertNoTechnicalText(fixture.status());
                ok(pane).fire();
                return null;
            });
        }
    }

    private static Stage blocker(Stage owner) {
        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(new javafx.scene.Scene(new Label("Synthetic competing dialog"), 300, 100));
        stage.show();
        return stage;
    }

    private static ConditionalBenefitDefinition ordinaryBenefit() {
        return new ConditionalBenefitDefinition(UUID.randomUUID(), "Synthetic ordinary income",
                Category.CONDITIONAL_INCOME, new Trigger(TriggerType.FIXED_DATE, LocalDate.of(2027, 1, 1), null, null),
                PersonReference.PRIMARY, PaymentPattern.MONTHLY_RECURRING, new BigDecimal("2500"),
                null, null, null, null, BigDecimal.ZERO, EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR,
                TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION, ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH);
    }

    private static Fixture open(Path folder, ApplicationTheme theme) throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var plan = selectArkansas(surplusPlan("1000000", 2));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        Path file = folder.resolve("synthetic.json");
        repository.save(plan, file);
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("analyses")));
        var controller = new ApplicationController(repository,
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        return fx(() -> {
            controller.open(file);
            var manager = ApplicationThemeManager.start(theme);
            var window = new MainWindow(controller, ApplicationBranding.defaults(), manager, null);
            var owner = new Stage();
            owner.setScene(window.createScene());
            window.setStage(owner);
            var bounds = Screen.getPrimary().getVisualBounds();
            owner.setX(bounds.getMinX() + 20);
            owner.setY(bounds.getMinY() + 24);
            owner.setWidth(Math.min(1100, bounds.getWidth() - 40));
            owner.setHeight(Math.min(760, bounds.getHeight() - 48));
            owner.show();
            return new Fixture(repository, file, store, controller, window, owner, manager);
        });
    }

    private record Fixture(JsonRetirementPlanRepository repository, Path file, AnalysisResultStore store,
                           ApplicationController controller, MainWindow window, Stage owner,
                           ApplicationThemeManager manager) implements AutoCloseable {
        Button recalculate() {
            menu("View", "Results Summary");
            var button = (Button) owner.getScene().lookup("#recalculate-projection");
            button.fire();
            return button;
        }

        void menu(String menu, String item) {
            var bar = (MenuBar) owner.getScene().lookup(".menu-bar");
            bar.getMenus().stream().filter(value -> menu.equals(value.getText())).findFirst().orElseThrow()
                    .getItems().stream().filter(value -> item.equals(value.getText())).findFirst().orElseThrow().fire();
        }

        String status() {
            var footer = (javafx.scene.layout.VBox) ((javafx.scene.layout.BorderPane) owner.getScene().getRoot()).getBottom();
            return ((Label) footer.getChildren().getLast()).getText();
        }

        void addBenefit(ConditionalBenefitDefinition definition) {
            controller.getCurrentPlan().setConditionalBenefits(List.of(definition));
            controller.markModified();
        }

        StateTaxJurisdiction state() {
            return controller.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction();
        }

        @SuppressWarnings("unchecked")
        ComboBox<StateTaxJurisdiction> stateChoice() {
            return (ComboBox<StateTaxJurisdiction>) owner.getScene().lookup("#state-of-residence");
        }

        Button assumptionsButton(String title) {
            return owner.getScene().getRoot().lookupAll(".button").stream()
                    .filter(node -> node instanceof Button button && title.equals(button.getText()))
                    .map(Button.class::cast).filter(button -> button.getParent().isVisible() && !button.isDisabled())
                    .findFirst().orElseThrow();
        }

        @Override
        public void close() throws Exception {
            fx(() -> { owner.hide(); manager.close(); return null; });
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    private static void await(Callable<Boolean> test) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (fx(test)) return;
            Thread.sleep(15);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for the projection warning workflow.");
    }

    private static List<DialogPane> warnings() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).filter(pane -> "projection-failure-warning".equals(pane.getId())).toList();
    }

    private static javafx.scene.Node visible(String selector) {
        return Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().lookup(selector))
                .filter(java.util.Objects::nonNull).filter(javafx.scene.Node::isVisible).findFirst().orElse(null);
    }

    private static Button ok(DialogPane pane) { return (Button) pane.lookupButton(ButtonType.OK); }

    private static void press(DialogPane pane, KeyCode code) {
        Event.fireEvent(ok(pane), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static void assertNoTechnicalText(String message) {
        assertFalse(message.contains("Exception"));
        assertFalse(message.contains("com."));
    }

    private static void assertCentered(Stage owner, Stage warning) {
        double x = owner.getX() + owner.getWidth() / 2;
        double y = owner.getY() + owner.getHeight() / 2;
        var bounds = Screen.getScreensForRectangle(x, y, 1, 1).getFirst().getVisualBounds();
        assertEquals(Math.clamp(x - warning.getWidth() / 2, bounds.getMinX(), bounds.getMaxX() - warning.getWidth()),
                warning.getX(), 1);
        assertEquals(Math.clamp(y - warning.getHeight() / 2, bounds.getMinY(), bounds.getMaxY() - warning.getHeight()),
                warning.getY(), 1);
    }

    private static void capture(DialogPane pane, ApplicationTheme theme, double scale) throws Exception {
        var folder = Files.createDirectories(Path.of("target/phase6e-p3d2c/previews"));
        var parameters = new javafx.scene.SnapshotParameters();
        parameters.setTransform(javafx.scene.transform.Transform.scale(scale, scale));
        var snapshot = pane.snapshot(parameters, null);
        int width = (int) snapshot.getWidth(), height = (int) snapshot.getHeight();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(image, "png", folder.resolve(theme + "-" + scale + ".png").toFile());
        System.out.printf("Arkansas warning %s window output scale %.2f, capture %dx%d%n", theme, scale, width, height);
    }
}
