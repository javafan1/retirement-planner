package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloRunFeedbackTest {
    @TempDir Path directory;
    @BeforeAll
    static void toolkit() throws Exception {
        startFx();
    }

    @Test
    void failedRunFeedbackRemainsVisibleWhileInspectingPreviousResult() throws Exception {
        var previous = MonteCarloUiFixtures.run("1000");
        var plan = MonteCarloUiFixtures.plan("1000", 3);
        var dialog = fx(() -> {
            var view = new MonteCarloAnalysisView(new Controller(plan), Runnable::run,
                    (p, s, r, progress, cancel) -> { throw new IllegalStateException("Synthetic fast worker failure"); });
            var created = new MonteCarloAnalysisDialog(null, view);
            created.getDialogPane().setPrefSize(900, 600);
            created.show();
            created.setWidth(900);
            created.setHeight(650);
            return created;
        });
        try {
            var view = fx(() -> (MonteCarloAnalysisView) dialog.getDialogPane().lookup("#monte-carlo-analysis"));
            fx(() -> {
                view.session().restore(previous, true);
                ((Button) dialog.getDialogPane().lookup("#mc-run")).fire();
                return null;
            });
            for (int i = 0; i < 100 && fx(() -> view.session().busy()); i++) Thread.sleep(20);
            fx(() -> {
                assertFalse(view.session().busy());
                assertSame(previous, view.session().result());
                assertFalse(((Button) dialog.getDialogPane().lookup("#mc-run")).isDisabled());
                ((ScrollPane) dialog.getDialogPane().lookup(".scroll-pane")).setVvalue(1);
                return null;
            });
            awaitLayoutPulses(dialog.getDialogPane().getScene());
            fx(() -> {
                var status = (Label) dialog.getDialogPane().lookup("#mc-status");
                var bounds = status.localToScene(status.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0, "Run feedback scrolled outside the dialog: " + bounds);
                assertTrue(bounds.getMaxY() <= dialog.getDialogPane().getScene().getHeight());
                return null;
            });
        } finally {
            fx(() -> { dialog.close(); return null; });
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationTheme.class)
    void realDialogKeepsAdmissionAndValidationFeedbackVisibleAtBothSizes(ApplicationTheme theme) throws Exception {
        var previous = MonteCarloUiFixtures.run("1000");
        for (int width : new int[] {900, 1180}) {
            var plan = MonteCarloUiFixtures.plan("1000", 3);
            var old = plan.getHousehold().getExpenses().getFirst();
            plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount(),
                    old.getGrowthCategory(), old.getStartDate(), old.getEndDate(), old.getExpenseType(), new BigDecimal("0.25")));
            // Positive volatility is supported in B2. A genuinely unsupported occurrence count
            // retains the B1 preflight/visibility regression without reinstating the old feature gate.
            for (int index = 0; index < 1000; index++) plan.getHousehold().addExpense(new Expense("Synthetic limit entry", BigDecimal.ONE));
            var tasks = new ArrayDeque<Runnable>();
            var controller = new Controller(plan);
            var dialog = fx(() -> {
                var view = new MonteCarloAnalysisView(controller, tasks::add, (p, s, r, progress, cancel) -> {
                    fail("Admission must reject before submission"); return null;
                });
                return new MonteCarloAnalysisDialog(null, view);
            });
            var manager = fx(() -> ApplicationThemeManager.start(theme));
            try {
                fx(() -> {
                    dialog.getDialogPane().setPrefSize(width, width == 900 ? 600 : 770);
                    dialog.show(); dialog.setWidth(width); dialog.setHeight(width == 900 ? 650 : 820);
                    var view = (MonteCarloAnalysisView) dialog.getDialogPane().lookup("#monte-carlo-analysis");
                    view.session().restore(previous, true);
                    assertFalse(((Button) dialog.getDialogPane().lookup("#mc-run")).isDisabled());
                    ((Button) dialog.getDialogPane().lookup("#mc-run")).fire();
                    assertTrue(tasks.isEmpty());
                    assertSame(previous, view.session().result());
                    assertFalse(view.session().busy());
                    assertTrue(label(dialog, "mc-validation").getText().contains("Too many expense sampling specifications"));
                    assertFalse(label(dialog, "mc-validation").getText().contains("Set annual expense volatility to 0"));
                    assertFalse(dialog.getDialogPane().lookup("#mc-progress").isManaged());
                    ((ScrollPane) dialog.getDialogPane().lookup("#mc-content-scroll")).setVvalue(1);
                    return null;
                });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> {
                    assertFeedbackBounds(dialog);
                    assertFalse(label(dialog, "mc-validation").isTextTruncated());
                    assertEquals(label(dialog, "mc-validation").getText(), label(dialog, "mc-validation").getAccessibleText());
                    savePreview(dialog, theme + "-" + width + "-admission");
                    ((TextField) dialog.getDialogPane().lookup("#mc-return")).setText("NaN");
                    ((Button) dialog.getDialogPane().lookup("#mc-run")).fire();
                    assertTrue(tasks.isEmpty());
                    assertTrue(label(dialog, "mc-validation").getText().contains("Expected return"));
                    assertSame(previous, ((MonteCarloAnalysisView) dialog.getDialogPane().lookup("#monte-carlo-analysis")).session().result());
                    return null;
                });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> {
                    assertFeedbackBounds(dialog);
                    savePreview(dialog, theme + "-" + width + "-validation");
                    return null;
                });
            } finally {
                fx(() -> { dialog.close(); manager.close(); return null; });
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MonteCarloMode.class)
    void positiveVolatilityIsAdmittedInEachStandaloneMode(MonteCarloMode mode) throws Exception {
        var plan = MonteCarloMortalityUiFixtures.plan();
        var old = new Expense("Synthetic spending", BigDecimal.TEN);
        plan.getHousehold().addExpense(new Expense(old.getDescription(), old.getAnnualAmount(), old.getGrowthCategory(),
                null, null, old.getExpenseType(), new BigDecimal("0.1")));
        var tasks = new ArrayDeque<Runnable>();
        fx(() -> {
            var view = new MonteCarloAnalysisView(new Controller(plan), tasks::add, (p, s, r, progress, cancel) -> {
                fail("No fixed simulation permitted"); return null;
            }, (p, r, progress, cancel) -> { fail("No longevity simulation permitted"); return null; });
            try {
                new Scene(view);
                @SuppressWarnings("unchecked") var modes = (ComboBox<MonteCarloMode>) view.lookup("#mc-mode");
                modes.setValue(mode);
                ((TextField) view.lookup("#mc-survivor-age")).setText("67");
                button(view, "#mc-run").fire();
                assertEquals(1, tasks.size());
                assertTrue(((Label) view.lookup("#mc-validation")).getText().isBlank());
                assertTrue(button(view, "#mc-run").isDisabled());
            } finally { view.close(); }
            return null;
        });
    }

    @ParameterizedTest
    @EnumSource(ApplicationTheme.class)
    void busyAndEmptyWorkerFailureStayVisibleInCompactDialog(ApplicationTheme theme) throws Exception {
        var previous = MonteCarloUiFixtures.run("1000");
        var tasks = new ArrayDeque<Runnable>();
        var view = fx(() -> new MonteCarloAnalysisView(new Controller(MonteCarloUiFixtures.plan("1000", 3)),
                tasks::add, (p, s, r, progress, cancel) -> { throw new IllegalStateException(); }));
        var dialog = fx(() -> new MonteCarloAnalysisDialog(null, view));
        var manager = fx(() -> ApplicationThemeManager.start(theme));
        try {
            fx(() -> {
                dialog.getDialogPane().setPrefSize(900, 600); dialog.show(); dialog.setWidth(900); dialog.setHeight(650);
                view.session().restore(previous, true);
                button(view, "#mc-run").fire();
                assertTrue(button(view, "#mc-run").isFocusTraversable());
                assertTrue(button(view, "#mc-run").isDisabled());
                assertFalse(button(view, "#mc-cancel").isDisabled());
                ((ScrollPane) dialog.getDialogPane().lookup("#mc-content-scroll")).setVvalue(1);
                return null;
            });
            awaitLayoutPulses(dialog.getDialogPane().getScene());
            fx(() -> {
                assertTrue(dialog.getDialogPane().lookup("#mc-progress").isManaged());
                savePreview(dialog, theme + "-900-busy");
                return null;
            });
            tasks.remove().run(); waitForFx();
            awaitLayoutPulses(dialog.getDialogPane().getScene());
            fx(() -> {
                var scroll = (ScrollPane) dialog.getDialogPane().lookup("#mc-content-scroll");
                scroll.setVvalue(1);
                assertTrue(label(dialog, "mc-status").getText().contains("unexpected error"));
                assertSame(previous, view.session().result());
                assertFalse(dialog.getDialogPane().lookup("#mc-progress").isManaged());
                var bounds = label(dialog, "mc-status").localToScene(label(dialog, "mc-status").getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= dialog.getDialogPane().getScene().getHeight());
                assertFalse(label(dialog, "mc-status").isTextTruncated());
                savePreview(dialog, theme + "-900-worker-failure");
                return null;
            });
        } finally { fx(() -> { dialog.close(); manager.close(); return null; }); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldOrOutdatedResultDoesNotBlockCurrentInputsAndSuccessfulReplacement(boolean outdated) throws Exception {
        var previous = MonteCarloUiFixtures.run("1000");
        var plan = MonteCarloUiFixtures.plan("2000", 3);
        var tasks = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        var controller = new Controller(plan);
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(controller, tasks::add, (p, s, r, progress, cancel) -> {
                calls.incrementAndGet(); assertNotSame(plan, p);
                assertEquals(new BigDecimal("2000"), p.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
                return new MonteCarloRunService().run(p, s, r, progress, cancel);
            });
            new Scene(created);
            @SuppressWarnings("unchecked") var count = (ComboBox<Integer>) created.lookup("#mc-simulations");
            count.setValue(1);
            created.session().restore(previous, outdated);
            assertFalse(button(created, "#mc-run").isDisabled());
            button(created, "#mc-run").fire();
            assertEquals(MonteCarloSession.State.RUNNING, created.session().state());
            button(created, "#mc-run").fire();
            assertEquals(1, tasks.size(), "Duplicate activation must not queue a second job");
            return created;
        });
        try {
            tasks.remove().run(); waitForFx();
            fx(() -> {
                assertEquals(MonteCarloSession.State.COMPLETED, view.session().state());
                assertNotSame(previous, view.session().result());
                assertEquals(1, calls.get());
                assertFalse(button(view, "#mc-run").isDisabled());
                assertTrue(((Label) view.lookup("#mc-status")).getText().contains("Analysis complete"));
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void emptyWorkerFailureRetainsResultAndHasPersistentReadableFallback(boolean expenseEnabled) throws Exception {
        var previous = MonteCarloUiFixtures.run("1000");
        var tasks = new ArrayDeque<Runnable>();
        var failure = new IllegalStateException();
        var view = fx(() -> {
            var plan = MonteCarloUiFixtures.plan("1000", 3);
            if (expenseEnabled) StandaloneExpenseHistoryTest.volatility(plan, ".25");
            var created = new MonteCarloAnalysisView(new Controller(plan),
                    tasks::add, (p, s, r, progress, cancel) -> { throw failure; });
            new Scene(created); created.session().restore(previous, true);
            button(created, "#mc-run").fire(); return created;
        });
        try {
            tasks.remove().run(); waitForFx();
            fx(() -> {
                assertEquals(MonteCarloSession.State.FAILED, view.session().state());
                assertSame(failure, view.session().failure());
                assertSame(previous, view.session().result());
                assertFalse(view.lookup("#mc-progress").isManaged());
                assertFalse(button(view, "#mc-run").isDisabled());
                assertTrue(button(view, "#mc-cancel").isDisabled());
                var status = (Label) view.lookup("#mc-status");
                assertTrue(status.isManaged()); assertTrue(status.isVisible());
                assertTrue(status.getText().contains("unexpected error"));
                assertFalse(status.getText().contains("null"));
                assertFalse(status.getText().contains("Outdated"), "Freshness belongs to the saved result, not a failed run");
                assertEquals(status.getText(), status.getAccessibleText());
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }

    @Test
    void executorRejectionRestoresControlsAndLogsNoExceptionMessage() throws Exception {
        var records = new java.util.concurrent.CopyOnWriteArrayList<LogRecord>();
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        MonteCarloRunDiagnostics.LOG.addHandler(handler);
        try {
            fx(() -> {
                var view = new MonteCarloAnalysisView(new Controller(MonteCarloUiFixtures.plan("1000", 3)),
                        task -> { throw new RejectedExecutionException("PRIVATE-user-path-and-values"); },
                        (p, s, r, progress, cancel) -> { fail("Rejected task cannot execute"); return null; });
                try {
                    new Scene(view); button(view, "#mc-run").fire();
                    assertEquals(MonteCarloSession.State.FAILED, view.session().state());
                    assertFalse(view.session().busy());
                    assertFalse(button(view, "#mc-run").isDisabled());
                    assertTrue(button(view, "#mc-cancel").isDisabled());
                } finally { view.close(); }
                return null;
            });
            assertTrue(records.stream().anyMatch(r -> r.getMessage().contains("category=EXECUTOR")
                    && r.getMessage().contains("workerSubmitted=false")));
            for (var record : records) {
                assertNull(record.getThrown());
                assertFalse(record.getMessage().contains("PRIVATE"));
            }
        } finally { MonteCarloRunDiagnostics.LOG.removeHandler(handler); }
    }

    @Test
    void emptyPreparationFailureProducesNonemptyFeedbackWithoutSubmitting() throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        fx(() -> {
            var controller = new Controller(MonteCarloUiFixtures.plan("1000", 3)) {
                @Override public com.daviddunn.retirementplanner.domain.projection.Projection peekCurrentProjection() {
                    throw new IllegalStateException();
                }
            };
            var view = new MonteCarloAnalysisView(controller, tasks::add,
                    (p, s, r, progress, cancel) -> { fail("Preparation failed"); return null; });
            try {
                new Scene(view); button(view, "#mc-run").fire();
                assertTrue(tasks.isEmpty());
                assertTrue(((Label) view.lookup("#mc-validation")).getText().contains("could not start"));
                assertTrue(((Label) view.lookup("#mc-validation")).isManaged());
                assertTrue(((Label) view.lookup("#mc-status")).getText().contains("Run not started"));
                assertFalse(view.session().busy());
                assertFalse(button(view, "#mc-run").isDisabled());
            } finally { view.close(); }
            return null;
        });
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"cancel,false", "revision,false", "close,false", "cancel,true", "revision,true", "close,true"})
    void cancellationRevisionAndClosureNeverReplacePreviousResult(String action, boolean expenseEnabled) throws Exception {
        var previous = MonteCarloUiFixtures.run("1000");
        var plan = MonteCarloUiFixtures.plan("1000", 3);
        if (expenseEnabled) StandaloneExpenseHistoryTest.volatility(plan, ".25");
        var controller = new Controller(plan);
        var tasks = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(controller, tasks::add, (p, s, r, progress, cancel) -> {
                calls.incrementAndGet(); return previous;
            });
            new Scene(created); created.session().restore(previous, true);
            button(created, "#mc-run").fire();
            assertTrue(created.session().busy());
            assertTrue(button(created, "#mc-run").isDisabled());
            switch (action) {
                case "cancel" -> button(created, "#mc-cancel").fire();
                case "revision" -> controller.markModified();
                case "close" -> created.close();
                default -> fail("Unexpected action");
            }
            return created;
        });
        try {
            tasks.remove().run(); waitForFx();
            fx(() -> {
                assertEquals(action.equals("close") ? MonteCarloSession.State.CLOSED : MonteCarloSession.State.CANCELLED,
                        view.session().state());
                assertSame(previous, view.session().result());
                assertEquals(0, calls.get(), "Cancellation before execution must not call financial work");
                if (!action.equals("close")) {
                    assertFalse(button(view, "#mc-run").isDisabled());
                    assertTrue(button(view, "#mc-cancel").isDisabled());
                    assertFalse(view.lookup("#mc-progress").isManaged());
                    assertTrue(((Label) view.lookup("#mc-status")).getText().contains("cancelled"));
                }
                return null;
            });
        } finally { if (!action.equals("close")) fx(() -> { view.close(); return null; }); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptedResultStorageFailureRemainsAvailableAndDoesNotLeaveControlsBusy(boolean expenseEnabled) throws Exception {
        var saveFailure = new java.util.concurrent.atomic.AtomicBoolean(true);
        var repository = new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshotRepository() {
            @Override public java.util.Optional<com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot> load(
                    java.util.UUID owner, com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind kind) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot> loadBackup(
                    java.util.UUID owner, com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind kind) {
                return java.util.Optional.empty();
            }
            @Override public void save(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot snapshot) throws java.io.IOException {
                if (saveFailure.get()) throw new java.io.IOException("Injected storage failure");
            }
        };
        var store = new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore(repository);
        var plan = MonteCarloUiFixtures.plan("1000", 3);
        if (expenseEnabled) StandaloneExpenseHistoryTest.volatility(plan, ".25");
        var controller = new Controller(plan) {
            @Override public com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore getAnalysisResults() { return store; }
            @Override public boolean canPersistAnalysisResults() { return true; }
            @Override public Path getCurrentFile() { return directory.resolve("synthetic.json"); }
        };
        var tasks = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(controller, tasks::add, (p, s, r, progress, cancel) -> {
                calls.incrementAndGet(); return new MonteCarloRunService().run(p, s, r, progress, cancel);
            });
            new Scene(created);
            @SuppressWarnings("unchecked") var count = (ComboBox<Integer>) created.lookup("#mc-simulations");
            count.setValue(1); button(created, "#mc-run").fire(); return created;
        });
        try {
            tasks.remove().run(); waitForFx();
            MonteCarloParameterPersistenceTest.await((Label) view.lookup("#mc-status"), "Result save failed");
            fx(() -> {
                assertEquals(MonteCarloSession.State.COMPLETED, view.session().state());
                assertNotNull(view.session().result());
                assertFalse(button(view, "#mc-run").isDisabled());
                assertTrue(button(view, "#mc-cancel").isDisabled());
                assertTrue(view.canExportPdf(), "Storage failure cannot discard the completed report");
                assertNotNull(view.preparePdfReport());
                saveFailure.set(false);
                button(view, "#mc-retry-save").fire();
                return null;
            });
            MonteCarloParameterPersistenceTest.await((Label) view.lookup("#mc-saved-status"), "SAVED");
            assertEquals(1, calls.get(), "Retry Save must not rerun the analysis");
        } finally {
            fx(() -> { view.close(); return null; });
            assertTrue(store.drain(java.time.Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getAnalysisParameters().drain(java.time.Duration.ofSeconds(10));
            controller.getAnalysisParameters().shutdown();
        }
    }

    private static Label label(MonteCarloAnalysisDialog dialog, String id) {
        return (Label) dialog.getDialogPane().lookup("#" + id);
    }

    private static void assertFeedbackBounds(MonteCarloAnalysisDialog dialog) {
        for (String id : new String[] {"mc-status", "mc-validation"}) {
            var node = label(dialog, id);
            var bounds = node.localToScene(node.getBoundsInLocal());
            assertTrue(node.isVisible());
            assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= dialog.getDialogPane().getScene().getHeight(), id + " " + bounds);
            assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= dialog.getDialogPane().getScene().getWidth(), id + " " + bounds);
        }
    }

    private static void savePreview(MonteCarloAnalysisDialog dialog, String name) throws Exception {
        var image = dialog.getDialogPane().snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), 2);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
            png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var folder = Path.of("target/phase21b1/previews"); Files.createDirectories(folder);
        javax.imageio.ImageIO.write(png, "png", folder.resolve(name + ".png").toFile());
    }
}
