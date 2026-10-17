package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.ResultsView;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NewPlanFundingFailureTest {
    @TempDir Path directory;
    enum Case {
        SINGLE, COUPLE, LATER_YEAR, MEDICARE, FUNDED;
        String balance() { return this == FUNDED ? "10000" : this == LATER_YEAR ? "1500" : "100"; }
    }

    @BeforeAll static void start() throws Exception { startFx(); }

    static void enter(DialogPane pane, Case scenario) throws Exception {
        fill(person(pane, "primary"), "Synthetic", "Filer",
                LocalDate.of(scenario == Case.MEDICARE ? 1960 : 1980, 1, 1), MortalityCategory.FEMALE);
        if (scenario == Case.COUPLE) {
            ((Button) pane.lookup("#wizard-add-spouse")).fire();
            fill(person(pane, "spouse"), "Synthetic", "Spouse", LocalDate.of(1982, 1, 1), MortalityCategory.MALE);
        }
        button(pane, "Next").fire();
        if (scenario != Case.MEDICARE) CompleteWizardFixtures.account(pane, "Cash", AccountType.CHECKING,
                AccountOwnership.PRIMARY, scenario.balance());
        for (int index = 0; index < 3; index++) button(pane, "Next").fire();
        // A legal zero-dollar entry makes the Medicare case a populated plan; its only required cost is Medicare.
        CompleteWizardFixtures.expense(pane, "Annual spending", scenario == Case.MEDICARE ? "0" : "1000",
                com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL);
        button(pane, "Next").fire();
        ((DatePicker) pane.lookup("#wizard-assumption-start")).setValue(LocalDate.of(2027, 1, 1));
        for (String name : List.of("return", "inflation")) ((TextField) pane.lookup("#wizard-assumption-" + name)).setText("0");
        ((TextField) pane.lookup("#wizard-assumption-length")).setText("3");
        pane.applyCss(); pane.layout();
        ((TitledPane) pane.lookup("#wizard-advanced-assumptions")).setExpanded(true);
        pane.applyCss(); pane.layout();
        for (String name : List.of("healthcare", "cola")) ((TextField) pane.lookup("#wizard-assumption-" + name)).setText("0");
        @SuppressWarnings("unchecked") var state = (ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence");
        state.setValue(StateTaxJurisdiction.FL);
        @SuppressWarnings("unchecked") var filing = (ComboBox<FilingStatus>) pane.lookup("#wizard-assumption-filing-status");
        filing.setValue(scenario == Case.COUPLE ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        button(pane, "Next").fire();
        button(pane, "Create Plan").fire();
    }

    static RetirementPlan create(Case scenario) throws Exception {
        var plan = new AtomicReference<RetirementPlan>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try { enter(wizard.getDialogPane(), scenario); plan.set(wizard.getResult()); }
            finally { wizard.close(); }
        });
        assertNotNull(plan.get());
        return plan.get();
    }

    @ParameterizedTest @EnumSource(Case.class)
    void realWizardInputsReproduceExistingFundingOutcome(Case scenario) throws Exception {
        var plan = create(scenario);
        var engine = new ProjectionEngine();
        if (scenario == Case.FUNDED) {
            var completed = assertInstanceOf(ProjectionExecutionResult.Completed.class, engine.projectWithOutcome(plan));
            assertEquals(new BigDecimal("7000.00"), completed.projection().getFinalInvestableAssets());
            return;
        }
        var exception = assertThrows(IllegalStateException.class, () -> engine.project(plan));
        assertEquals("Insufficient projected assets to satisfy withdrawal.", exception.getMessage());
        assertEquals("FundingConstraint", exception.getCause().getClass().getSimpleName());
        var outcome = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, engine.projectWithOutcome(plan));
        var failure = outcome.fundingFailure();
        assertEquals(scenario == Case.LATER_YEAR ? 2028 : 2027, failure.calendarYear());
        assertEquals(scenario == Case.LATER_YEAR ? 1 : 0, outcome.completedYears().size());
        assertEquals(FundingFailure.Stage.WITHDRAWAL_ALLOCATION, failure.stage());
        if (scenario == Case.MEDICARE) {
            assertTrue(plan.getAccountPortfolio().getAccounts().isEmpty());
            assertTrue(failure.requiredAmount().signum() > 0);
            assertEquals(0, failure.availableAmount().signum());
        }
        else {
            assertEquals(0, new BigDecimal("1000").compareTo(failure.requiredAmount()));
            assertEquals(0, new BigDecimal(scenario == Case.LATER_YEAR ? "500" : "100").compareTo(failure.availableAmount()));
            assertEquals(new BigDecimal(scenario.balance()), plan.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
        }
        System.out.println("Funding reproduction " + scenario + ": " + failure);
    }

    @ParameterizedTest @EnumSource(Case.class)
    void fileNewPreservesCreatedPlanAndPresentsFundingFailureWithoutModal(Case scenario) throws Exception {
        fx(() -> {
            var window = new MainWindow();
            ApplicationController controller = field(window, "controller");
            var previous = controller.getCurrentPlan();
            var failure = new AtomicReference<Throwable>();
            Platform.runLater(() -> {
                var pane = activeWizardPane();
                try { enter(pane, scenario); }
                catch (Throwable exception) { failure.set(exception); button(pane, "Cancel").fire(); }
            });
            var root = (javafx.scene.layout.BorderPane) window.createScene().getRoot();
            ((MenuBar) root.getTop()).getMenus().getFirst().getItems().stream()
                    .filter(item -> item.getText().equals("New")).findFirst().orElseThrow().fire();
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertNotSame(previous, controller.getCurrentPlan());
            assertFalse(controller.hasCurrentFile());
            assertFalse(controller.isModified());
            var tabs = (javafx.scene.control.TabPane) root.getCenter();
            assertSame(field(window, "resultsSummaryView"), tabs.getSelectionModel().getSelectedItem().getContent());
            Label notice = (Label) root.lookup("#projection-funding-notice");
            assertNotNull(notice);
            ResultsView results = field(window, "resultsView");
            if (scenario == Case.FUNDED) {
                assertFalse(notice.isVisible());
                assertEquals(3, results.getTable().getItems().size());
            }
            else {
                assertTrue(notice.isVisible());
                assertTrue(notice.getText().contains("Plan created successfully"));
                assertTrue(notice.getText().contains("projection could not be completed"));
                assertTrue(notice.getText().contains(scenario == Case.LATER_YEAR ? "2028" : "2027"));
                assertTrue(notice.getText().contains(scenario == Case.MEDICARE ? "$2,434.80" : scenario == Case.LATER_YEAR ? "$500.00" : "$900.00"));
                assertNull(controller.getCurrentProjection());
                assertNull(controller.getCurrentProjectionSummary());
                assertTrue(results.getTable().getItems().isEmpty());
            }
        });
    }

    @Test void controllerCachesOnlyRecognizedFailureAndInvalidatesAcrossEditsAndPlans() throws Exception {
        var poor = create(Case.LATER_YEAR);
        var funded = create(Case.FUNDED);
        var controller = new ApplicationController(new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository(),
                new com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository(directory.resolve("settings.json")));
        controller.newPlan(poor);
        assertNull(controller.getCurrentProjection());
        var failure = controller.getCurrentFundingFailure().orElseThrow();
        assertEquals(2028, failure.calendarYear());
        for (int trial = 0; trial < 3; trial++) {
            assertNull(controller.getCurrentProjectionSummary());
            assertTrue(controller.getCurrentNonInvestableAssetProjections().isEmpty());
            assertSame(failure, controller.getCurrentFundingFailure().orElseThrow(), "No repeated engine evaluation for the cached failure");
        }
        controller.newPlan(funded);
        assertTrue(controller.getCurrentFundingFailure().isEmpty());
        var projection = controller.getCurrentProjection();
        assertSame(projection, controller.getCurrentProjection());
        controller.newPlan(poor);
        assertNull(controller.peekCurrentProjection());
        assertNull(controller.getCurrentProjection());
        poor.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("10000"));
        controller.markModified();
        controller.invalidateProjection();
        assertTrue(controller.getCurrentFundingFailure().isEmpty());
        var recovered = controller.getCurrentProjection();
        assertNotNull(recovered);
        var expected = new ProjectionEngine().project(poor);
        assertEquals(expected.size(), recovered.size());
        for (int index = 0; index < expected.size(); index++) {
            var left = expected.getYearAt(index);
            var right = recovered.getYearAt(index);
            for (var method : ProjectionYear.class.getMethods()) {
                if (method.getParameterCount() != 0 || !method.getName().startsWith("get")
                        || method.getName().equals("getSpouseRothConversion")) continue;
                var type = method.getReturnType();
                if (type == BigDecimal.class || type == int.class || type.isEnum())
                    assertEquals(method.invoke(left), method.invoke(right), left.getCalendarYear() + " / " + method.getName());
            }
            assertEquals(left.spouseRothConversion(), right.spouseRothConversion());
            assertEquals(left.getSocialSecurityResult(), right.getSocialSecurityResult());
            assertEquals(left.getHouseholdCashSettlement(), right.getHouseholdCashSettlement());
            var account = poor.getAccountPortfolio().getAccounts().getFirst();
            assertEquals(left.getEndingBalance(account), right.getEndingBalance(account));
        }
        assertTrue(controller.getCurrentFundingFailure().isEmpty());
    }

    @Test void unrelatedIllegalStateIsNotMisclassifiedAsInsufficientFunding() throws Exception {
        var plan = create(Case.FUNDED);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1950, 1, 1));
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                "Historical facts required", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        var controller = new ApplicationController(new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository(),
                new com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository(directory.resolve("settings.json")));
        controller.newPlan(plan);
        var exception = assertThrows(IllegalStateException.class, controller::getCurrentProjection);
        assertTrue(exception.getMessage().startsWith("Opening RMD information"));
        assertTrue(controller.getCurrentFundingFailure().isEmpty());
        assertNull(controller.peekCurrentProjection());
    }

    @Test void ordinaryRefreshClearsEveryOldResultAndAccountEditorRecoversWithoutDialogs() throws Exception {
        var plan = create(Case.FUNDED);
        var other = create(Case.LATER_YEAR);
        fx(() -> {
            var window = new MainWindow();
            window.createScene();
            ApplicationController controller = field(window, "controller");
            controller.newPlan(plan);
            invoke(window, "loadCurrentPlan");
            ResultsView results = field(window, "resultsView");
            var summary = (com.daviddunn.retirementplanner.ui.views.ResultsSummaryView) field(window, "resultsSummaryView");
            assertEquals(3, results.getTable().getItems().size());
            var good = controller.peekCurrentProjection();
            plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("100"));
            controller.markModified();
            for (int refresh = 0; refresh < 3; refresh++) {
                invoke(window, "refreshProjectionViews");
                assertNull(controller.peekCurrentProjection());
                assertTrue(results.getTable().getItems().isEmpty());
                assertTrue(((TableView<?>) field(summary, "projectionTable")).getItems().isEmpty());
                assertTrue(((Button) field(summary, "exportButton")).isDisabled());
                assertEquals("-", ((Label) field(summary, "endingAssetsValue")).getText());
                var chart = (com.daviddunn.retirementplanner.ui.charts.PortfolioChartView) field(window, "portfolioChartView");
                assertTrue(((javafx.scene.chart.LineChart<?, ?>) field(chart, "chart")).getData().isEmpty());
                var dashboard = (com.daviddunn.retirementplanner.ui.views.DashboardView) field(window, "dashboardView");
                assertEquals("", ((Label) field(dashboard, "endingAssetsLabel")).getText());
                assertNull(field(field(window, "projectionYearView"), "projection"));
                assertNoModal();
            }
            var accounts = (com.daviddunn.retirementplanner.ui.views.AccountsView) field(window, "accountsView");
            ((TableView<?>) field(accounts, "table")).getSelectionModel().selectFirst();
            var failure = new AtomicReference<Throwable>();
            Platform.runLater(() -> {
                var dialog = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                        .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
                try {
                    dialog.lookupAll(".text-field").stream().map(TextField.class::cast)
                            .filter(input -> input.getAccessibleHelp() != null && input.getAccessibleHelp().startsWith("Current account value"))
                            .findFirst().orElseThrow().setText("10000");
                    button(dialog, "OK").fire();
                }
                catch (Throwable error) { failure.set(error); button(dialog, "Cancel").fire(); }
            });
            invoke(accounts, "onEdit");
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertNotSame(good, controller.peekCurrentProjection());
            assertEquals(new BigDecimal("7000.00"), controller.getCurrentProjection().getFinalInvestableAssets());
            assertEquals(3, results.getTable().getItems().size());
            assertFalse(((Button) field(summary, "exportButton")).isDisabled());
            assertTrue(controller.getCurrentFundingFailure().isEmpty());
            Label notice = field(window, "projectionFundingNotice");
            assertFalse(notice.isVisible());
            controller.newPlan(other);
            invoke(window, "loadCurrentPlan");
            assertTrue(notice.isVisible());
            assertTrue(notice.getText().contains("2028"));
            assertFalse(notice.getText().contains("Plan created successfully"));
            assertTrue(results.getTable().getItems().isEmpty());
            assertNoModal();
        });
    }

    private static void assertNoModal() {
        assertTrue(javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .noneMatch(window -> window.getScene().getRoot() instanceof DialogPane), "Funding notice must not open a modal dialog");
    }

    private static void invoke(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(object);
    }

    @Test @EnabledIfSystemProperty(named = "funding.preview", matches = "true")
    void renderedFundingNoticeAndClearedResultsAreVisible() throws Exception {
        var plan = create(Case.LATER_YEAR);
        var reference = new AtomicReference<MainWindow>();
        var stage = new AtomicReference<javafx.stage.Stage>();
        fx(() -> {
            var window = new MainWindow();
            reference.set(window);
            ApplicationController controller = field(window, "controller");
            controller.newPlan(plan); invoke(window, "loadCurrentPlan");
            var host = new javafx.stage.Stage();
            stage.set(host); host.setScene(window.createScene()); host.show();
        });
        try {
            for (int width : List.of(1200, 780)) {
                fx(() -> { stage.get().setWidth(width); stage.get().setHeight(780); });
                pulses();
                fx(() -> {
                    var root = stage.get().getScene().getRoot();
                    Label notice = field(reference.get(), "projectionFundingNotice");
                    assertTrue(notice.isVisible());
                    var bounds = notice.localToScene(notice.getBoundsInLocal());
                    assertTrue(bounds.getMaxY() <= stage.get().getScene().getHeight());
                    assertTrue(bounds.getMaxX() <= stage.get().getScene().getWidth());
                    var folder = Path.of("target/post7c-funding-preview");
                    java.nio.file.Files.createDirectories(folder);
                    snapshot(root, folder.resolve("funding-" + width + ".png"));
                });
            }
            fx(() -> {
                var error = new AtomicReference<Throwable>();
                Platform.runLater(() -> {
                    var pane = activeWizardPane();
                    try { enter(pane, Case.SINGLE); }
                    catch (Throwable failure) { error.set(failure); button(pane, "Cancel").fire(); }
                });
                invoke(reference.get(), "onNew");
                if (error.get() != null) throw new AssertionError(error.get());
            });
            pulses();
            fx(() -> {
                Label notice = field(reference.get(), "projectionFundingNotice");
                assertTrue(notice.getText().startsWith("Plan created successfully."));
                assertEquals(notice.getText(), notice.getAccessibleText());
                assertTrue(notice.isFocusTraversable());
                snapshot(stage.get().getScene().getRoot(), Path.of("target/post7c-funding-preview/funding-after-create.png"));
            });
        }
        finally { fx(() -> stage.get().close()); }
    }

    private static void snapshot(javafx.scene.Node root, Path file) throws Exception {
        var image = root.snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", file.toFile());
    }
}
