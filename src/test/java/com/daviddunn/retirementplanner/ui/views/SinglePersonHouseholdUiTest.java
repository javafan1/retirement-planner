package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.dialogs.*;
import com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonHouseholdUiTest {
    @TempDir Path temporary;

    @BeforeAll static void startup() throws Exception {
        var task = new FutureTask<Void>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); }
        task.get(20, TimeUnit.SECONDS);
    }

    @Test void newPrimaryOnlyValidatesOnlyTheActualPersonAndCancelRestoresMembership() throws Exception {
        fx(() -> {
            var controller = new ApplicationController();
            controller.newPlan();
            var plan = controller.getCurrentPlan();
            var view = new HouseholdView(); view.load(plan);
            assertFalse(plan.getHousehold().hasSpouse());
            assertFalse(ProjectionReadiness.isReady(plan));
            assertFalse(view.applyChanges());
            assertEquals("Birth date is required.", status(view).getText());
            fillPrimary(view);
            assertTrue(view.applyChanges());
            assertTrue(ProjectionReadiness.isReady(plan));
            assertEquals(1, plan.getHousehold().members().size());
            assertFalse(pane(view, 1).isManaged());
            var include = field(view, "includeSpouse", CheckBox.class);
            assertNotNull(include.getTooltip());
            include.setSelected(true);
            assertTrue(pane(view, 1).isVisible());
            assertFalse(view.applyChanges(), "An included spouse must satisfy the same required fields");
            assertFalse(plan.getHousehold().hasSpouse());
            view.cancelChanges();
            assertFalse(include.isSelected());
            include.setSelected(true);
            var spouse = new Person("Spouse", "Example", LocalDate.of(1967, 3, 1));
            spouse.setMortalityCategory(MortalityCategory.MALE);
            card(view, 1).load(spouse);
            assertTrue(view.applyChanges());
            assertTrue(plan.getHousehold().hasSpouse());
            assertEquals(spouse.getBirthDate(), plan.getHousehold().getSpouse().getBirthDate());
            var originalPrimary = plan.getHousehold().getPrimaryPerson();
            var oldHousehold = plan.getHousehold();
            plan.setBaseline(ProjectionBaselineFactory.create(plan, "Couple snapshot"));
            var capturedHousehold = plan.getBaseline().getSnapshot().getHousehold();
            include.setSelected(false);
            assertTrue(view.applyChanges());
            assertFalse(plan.getHousehold().hasSpouse());
            assertSame(originalPrimary, plan.getHousehold().getPrimaryPerson());
            assertSame(capturedHousehold, plan.getBaseline().getSnapshot().getHousehold());
            assertTrue(capturedHousehold.hasSpouse());
            assertTrue(oldHousehold.hasSpouse(), "Membership editing must not mutate baseline membership");
            return null;
        });
    }

    @Test void removalNamesFinancialDependenciesAndDoesNotApplyOtherDraftEdits() throws Exception {
        fx(() -> {
            var controller = uiPlan(); var plan = controller.getCurrentPlan();
            var spouse = new Person("Spouse", "Example", LocalDate.of(1967, 3, 1));
            spouse.setMortalityCategory(MortalityCategory.MALE); plan.setSpouse(spouse);
            var account = new TraditionalIRA("Spouse retirement savings", AccountOwnership.SPOUSE, BigDecimal.TEN);
            plan.getAccountPortfolio().addAccount(account);
            var view = new HouseholdView(); view.load(plan);
            field(card(view, 0), "firstNameField", TextField.class).setText("Unapplied");
            field(view, "includeSpouse", CheckBox.class).setSelected(false);
            assertFalse(view.applyChanges());
            assertTrue(status(view).getText().contains(account.getName()));
            assertSame(spouse, plan.getHousehold().getSpouse());
            assertEquals("Primary", plan.getHousehold().getPrimaryPerson().getFirstName());
            assertEquals(AccountOwnership.SPOUSE, account.getOwnership());
            assertEquals(4, plan.getAccountPortfolio().getAccounts().size());
            return null;
        });
    }

    @Test void membershipValidationAlsoProtectsJointAccountsSurvivorPensionsAndExpenses() {
        var plan = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createEmptyPlan();
        var household = plan.getHousehold();
        var expense = new Expense("Household living expenses", BigDecimal.TEN);
        household.addExpense(expense);
        var joint = new BrokerageAccount("Joint savings", AccountOwnership.JOINT, BigDecimal.TEN);
        plan.getAccountPortfolio().addAccount(joint);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> plan.setSpouse(null)).getMessage().contains("Joint savings"));
        assertSame(household, plan.getHousehold());
        plan.getAccountPortfolio().removeAccount(joint);
        var pension = new Pension("Survivor election", AccountOwnership.PRIMARY, LocalDate.of(2027, 1, 1),
                null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE);
        household.getPrimaryPerson().addIncomeSource(pension);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> plan.setSpouse(null)).getMessage().contains("Survivor election"));
        household.getPrimaryPerson().removeIncomeSource(pension);
        plan.setSpouse(null);
        assertSame(expense, plan.getHousehold().getExpenses().getFirst());
        assertSame(household.getPrimaryPerson(), plan.getHousehold().getPrimaryPerson());
    }

    @Test void membershipRefreshHidesInapplicableDraftsWithoutDiscardingOtherAssumptions() throws Exception {
        fx(() -> {
            var controller = uiPlan(); var plan = controller.getCurrentPlan();
            plan.setSpouse(new Person("Spouse", "Example", LocalDate.of(1967, 1, 1)));
            var view = new AssumptionsView(); view.load(plan);
            field(view, "deathScenarioComboBox", ComboBox.class).setValue(DeathScenario.PRIMARY_DIES);
            field(view, "inflationRateField", TextField.class).setText("2.75");
            plan.setSpouse(null); view.refresh(plan);
            assertFalse(field(view, "deathScenarioComboBox", ComboBox.class).isVisible());
            assertEquals("2.75", field(view, "inflationRateField", TextField.class).getText());
            assertTrue(view.applyChanges());
            assertEquals(DeathScenario.BOTH_SURVIVE, plan.getPlanningAssumptions().getDeathScenarioAssumptions().getDeathScenario());
            assertEquals(0, new BigDecimal("0.0275").compareTo(plan.getPlanningAssumptions().getEconomicAssumptions().getGeneralInflationRate()));
            return null;
        });
    }

    @Test void currentUiPlanPersistsEditsReprojectsAndUsesOnlyPrimaryIncomeAndRmds() throws Exception {
        fx(() -> {
            var controller = uiPlan(); var plan = controller.getCurrentPlan();
            var projection = controller.getCurrentProjection();
            assertNotNull(projection);
            assertEquals(20, projection.getYears().size());
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getSocialSecurityResult().householdBenefit().signum() > 0));
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getRequiredMinimumDistribution().signum() > 0));
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getRothConversion().signum() > 0));
            for (var year : projection.getYears()) {
                assertFalse(year.getSocialSecurityResult().hasSpouse());
                assertNull(year.getSocialSecurityResult().spouseSelection());
                assertTrue(year.getAfterTaxEstateValue().signum() > 0);
            }
            var summary = controller.getCurrentProjectionSummary().getIncomeSummary();
            assertFalse(summary.hasSpouse());
            assertNull(summary.getSpouseSocialSecurityMonthly());
            assertEquals(summary.getPrimarySocialSecurityMonthly().add(summary.getPrimaryPensionMonthly()), summary.getTotalGuaranteedMonthlyIncome());
            var income = new IncomeSourcesView(); income.load(plan);
            assertEquals(2, income.getTable().getItems().size());
            assertTrue(new OpeningRmdWorkflowService().getApplicableAccounts(plan).stream()
                    .allMatch(row -> row.account().getOwnership() == AccountOwnership.PRIMARY));
            var results = new ResultsSummaryView(controller);
            results.load(plan, projection, java.util.List.of());
            assertTrue(field(results, "exportButton", Button.class).isDisabled());
            assertFalse(field(results, "deathScenarioComboBox", ComboBox.class).isVisible());
            var details = new ProjectionYearDetailsPane(projection.getYears().getLast(), null, BigDecimal.ZERO, BigDecimal.ZERO);
            assertFalse(text(details).contains("Spouse"));
            var ending = projection.getYears().getLast().getEndingInvestableAssets();
            var file = temporary.resolve("single-ui.json");
            controller.saveAs(file); controller.newPlan(); controller.open(file);
            assertFalse(controller.getCurrentPlan().getHousehold().hasSpouse());
            assertEquals(3, controller.getCurrentPlan().getAccountPortfolio().getAccounts().size());
            var view = new HouseholdView(); view.load(controller.getCurrentPlan());
            field(card(view, 0), "lastNameField", TextField.class).setText("Edited");
            assertTrue(view.applyChanges()); controller.markModified(); controller.saveAs(file);
            controller.newPlan(); controller.open(file);
            assertFalse(controller.getCurrentPlan().getHousehold().hasSpouse());
            assertEquals("Edited", controller.getCurrentPlan().getHousehold().getPrimaryPerson().getLastName());
            assertEquals(ending, controller.getCurrentProjection().getYears().getLast().getEndingInvestableAssets());
            assertFalse(Files.readString(file).contains("\"spouse\": {"));
            return null;
        });
    }

    @Test void uiCreatedPlanReachesAllThreeNineStrategyAnalyses() throws Exception {
        var plan = fx(() -> uiPlan().getCurrentPlan());
        var request = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        assertEquals(9, request.strategyCount());
        var deterministic = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        assertEquals(9, deterministic.rankedSuccessfulEntries().size());
        assertEquals(67, deterministic.currentPlanBaseline().evaluatedStrategy().primaryRetirementAge());
        var mortality = SinglePersonMortalityAnalysisTest.mortality(plan, "1");
        for (var mode : SinglePersonMortalityAnalysis.Mode.values()) {
            var result = SinglePersonMortalityAnalysisTest.run(plan, mortality, "0.01", mode);
            assertEquals(9, result.entries().size());
            assertEquals(67, result.current().strategy().primaryRetirementAge());
            assertEquals(1, result.rank(result.ranked().getFirst()));
            assertTrue(result.entries().stream().noneMatch(e -> e.strategy().hasSpouse()));
        }
    }

    @Test void filingStatusIsExplicitAndOwnersAndCoupleInputsFollowActualMembership() throws Exception {
        fx(() -> {
            var controller = uiPlan(); var plan = controller.getCurrentPlan();
            var view = new AssumptionsView(); view.load(plan);
            assertFalse(field(view, "deathScenarioComboBox", ComboBox.class).isVisible());
            var filing = field(view, "filingStatusComboBox", ComboBox.class);
            assertEquals(plan.getPlanningAssumptions().getTaxAssumptions().getFilingStatus(), filing.getValue());
            filing.setValue(com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE);
            assertTrue(view.applyChanges());
            assertEquals(com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE,
                    plan.getPlanningAssumptions().getTaxAssumptions().getFilingStatus());
            var account = new AccountDialog(null, plan.getHousehold());
            assertEquals(java.util.List.of(AccountOwnership.PRIMARY), field(account, "ownershipCombo", ComboBox.class).getItems());
            var pension = new PensionDialog(null, plan.getHousehold());
            assertFalse(field(pension, "survivorMonthlyBenefitField", TextField.class).isManaged());
            assertEquals(java.util.List.of(AccountOwnership.PRIMARY), field(pension, "ownershipCombo", ComboBox.class).getItems());
            plan.setSpouse(new Person("Spouse", "Example", LocalDate.of(1967, 1, 1)));
            view.load(plan);
            assertTrue(field(view, "deathScenarioComboBox", ComboBox.class).isVisible());
            assertEquals(com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE,
                    plan.getPlanningAssumptions().getTaxAssumptions().getFilingStatus(), "Adding spouse must not infer a tax status");
            return null;
        });
    }

    @Test void normalWindowLoadsSingleResultsAndEnablesMonteCarlo() throws Exception {
        fx(() -> {
            var controller = uiPlan();
            var file = temporary.resolve("window-single.json"); controller.saveAs(file);
            var window = new MainWindow();
            var active = field(window, "controller", ApplicationController.class);
            active.open(file); invoke(window, "loadCurrentPlan");
            assertNotNull(active.getCurrentProjectionSummary());
            var root = field(window, "root", BorderPane.class);
            var menu = ((MenuBar) root.getTop()).getMenus().stream().filter(m -> m.getText().equals("Analysis")).findFirst().orElseThrow();
            menu.getOnShowing().handle(null);
            assertFalse(menu.getItems().stream().filter(i -> "monte-carlo-analysis-menu".equals(i.getId())).findFirst().orElseThrow().isDisable());
            assertTrue(menu.getItems().stream().filter(i -> "monte-carlo-comparison-menu".equals(i.getId())).findFirst().orElseThrow().isDisable());
            assertFalse(menu.getItems().stream().anyMatch(i -> i.isVisible() && i.getText().contains("Stage 4B")));
            assertFalse(menu.getItems().getFirst().isDisable(), "Supported Social Security analyzer remains reachable");
            if (Boolean.getBoolean("single.stage4a1.preview")) {
                var stage = new Stage(); stage.setScene(new Scene(root, 1900, 1040)); stage.show();
                try { snapshot(stage, "single-results-summary"); } finally { stage.close(); }
            }
            return null;
        });
    }

    @Test void visualHouseholdStates() throws Exception {
        if (!Boolean.getBoolean("single.stage4a1.preview")) return;
        fx(() -> {
            var controller = uiPlan(); var plan = controller.getCurrentPlan();
            var view = new HouseholdView(); view.load(plan);
            var stage = new Stage(); stage.setScene(new Scene(view, 1000, 760)); stage.show();
            try {
                snapshot(stage, "primary-only");
                field(view, "includeSpouse", CheckBox.class).setSelected(true);
                assertFalse(view.applyChanges());
                snapshot(stage, "add-spouse-validation");
                var spouse = new Person("Spouse", "Example", LocalDate.of(1967, 3, 1));
                spouse.setMortalityCategory(MortalityCategory.MALE); card(view, 1).load(spouse);
                assertTrue(view.applyChanges()); snapshot(stage, "couple");
                var dependent = new BrokerageAccount("Spouse savings", AccountOwnership.SPOUSE, BigDecimal.TEN);
                plan.getAccountPortfolio().addAccount(dependent);
                field(view, "includeSpouse", CheckBox.class).setSelected(false);
                assertFalse(view.applyChanges()); snapshot(stage, "blocked-removal");
                plan.getAccountPortfolio().removeAccount(dependent);
                field(view, "includeSpouse", CheckBox.class).setSelected(false);
                assertTrue(view.applyChanges()); snapshot(stage, "removed-spouse");
                stage.getScene().setRoot(new VBox());
                stage.setScene(new Scene(view, 720, 560)); stage.sizeToScene();
                assertEquals(720, stage.getScene().getWidth());
                assertEquals(560, stage.getScene().getHeight());
                snapshot(stage, "primary-narrow");
                var assumptions = new AssumptionsView(); assumptions.load(plan);
                var scroll = new ScrollPane(assumptions); scroll.setFitToWidth(true);
                stage.setScene(new Scene(scroll, 1100, 1000)); stage.sizeToScene(); snapshot(stage, "single-assumptions");
            } finally { stage.close(); }
            return null;
        });
    }

    @Test void uiCreatedSavedReloadedPlanRunsFixedLongevityAndPairedMonteCarlo() throws Exception {
        var controller = fx(() -> {
            var c = uiPlan();
            var file = temporary.resolve("monte-carlo-single.json");
            c.saveAs(file);
            c.newPlan();
            c.open(file);
            assertFalse(c.getCurrentPlan().getHousehold().hasSpouse());
            assertNotNull(c.getCurrentProjection());
            return c;
        });
        var queue = new java.util.ArrayDeque<Runnable>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var service = new MonteCarloRunService();
        var view = fx(() -> {
            var v = new MonteCarloAnalysisView(controller, queue::add,
                    (p, s, r, u, c) -> { calls.incrementAndGet(); return service.run(p, s, r, u, c); },
                    (p, r, u, c) -> { calls.incrementAndGet(); return service.runMortality(p, r, u, c); });
            new Scene(v, 1900, 1040);
            return v;
        });
        try {
            for (var mode : MonteCarloMode.values()) {
                fx(() -> {
                    ((ComboBox<MonteCarloMode>) view.lookup("#mc-mode")).setValue(mode);
                    ((ComboBox<Integer>) view.lookup("#mc-simulations")).setValue(100);
                    ((ComboBox<String>) view.lookup("#mc-inflation-mode")).setValue("Stochastic");
                    ((TextField) view.lookup("#mc-seed")).setText("417");
                    if (mode == MonteCarloMode.LONGEVITY_ADJUSTED) {
                        assertFalse(view.lookup("#mc-spouse-adjustment").getParent().isManaged());
                        assertFalse(view.lookup("#mc-survivor-age").getParent().isManaged());
                    }
                    ((Button) view.lookup("#mc-run")).fire();
                    assertEquals(MonteCarloSession.State.RUNNING, view.session().state());
                    return null;
                });
                queue.remove().run(); // Actual engine execution, off FX.
                fx(() -> {
                    assertEquals(MonteCarloSession.State.COMPLETED, view.session().state(), String.valueOf(view.session().failure()));
                    assertFalse(view.session().result().people().hasSpouse());
                    assertEquals("Share of simulated market and lifetime scenarios that completed all modeled obligations through the person's death.",
                            MonteCarloMortalityPresentation.lifetimeText(MonteCarloMortalityPresentation.FUNDING_HELP, false));
                    assertEquals(417, view.session().result().settings().seed());
                    assertFalse(view.canExportPdf());
                    assertThrows(UnsupportedOperationException.class, () -> MonteCarloPdfReportAdapter.from(view.session().result()));
                    String visible = visibleText(view).toLowerCase();
                    assertFalse(visible.contains("second death") || visible.contains("both alive") || visible.contains("survivor"));
                    if (Boolean.getBoolean("single.stage4b.preview")) {
                        mcSnapshot(view, mode == MonteCarloMode.FIXED_LIFESPAN ? "fixed" : "longevity");
                        if (mode == MonteCarloMode.LONGEVITY_ADJUSTED) {
                            var chart = view.lookup("#monte-carlo-fan");
                            if (chart != null) chart.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                                    "", "", javafx.scene.input.KeyCode.END, false, false, false, false));
                            mcSnapshot(view, "longevity-late");
                        }
                    }
                    return null;
                });
            }
            assertEquals(2, calls.get());
            fx(() -> {
                ((TextField) view.lookup("#mc-volatility")).setText("12");
                assertTrue(view.session().stale());
                assertFalse(view.canExportPdf());
                assertFalse(controller.getCurrentPlan().getHousehold().hasSpouse());
                return null;
            });
            assertEquals(2, calls.get(), "Selection and input edits do not run analysis");
        } finally { fx(() -> { view.close(); return null; }); }

        var comparisonQueue = new java.util.ArrayDeque<Runnable>();
        var comparison = fx(() -> {
            controller.getCurrentPlan().setBaseline(ProjectionBaselineFactory.create(controller.getCurrentPlan(), "Single baseline"));
            var v = new MonteCarloStrategyComparisonView(controller, comparisonQueue::add, new MonteCarloStrategyComparisonRunService()::run);
            new Scene(v, 1900, 1040);
            field(v, "count", ComboBox.class).setValue(30);
            field(v, "mode", ComboBox.class).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            field(v, "run", Button.class).fire();
            return v;
        });
        try {
            comparisonQueue.remove().run();
            fx(() -> {
                assertEquals(MonteCarloStrategyComparisonSession.State.COMPLETED, comparison.session().state(),
                        String.valueOf(comparison.session().failure()));
                assertFalse(comparison.session().result().result().request().assumptions().hasSpouse());
                assertFalse(comparison.canExportPdf());
                assertFalse(visibleText(comparison).toLowerCase().contains("second-death"));
                if (Boolean.getBoolean("single.stage4b.preview")) mcSnapshot(comparison, "comparison");
                return null;
            });
        } finally { fx(() -> { comparison.close(); return null; }); }
    }

    private static String visibleText(javafx.scene.Node node) {
        if (!node.isVisible() || !node.isManaged()) return "";
        var own = node instanceof Labeled labeled ? labeled.getText() : "";
        if (node instanceof javafx.scene.Parent parent)
            for (var child : parent.getChildrenUnmodifiable()) own += " " + visibleText(child);
        return own;
    }

    private static void mcSnapshot(javafx.scene.Parent view, String name) throws Exception {
        var scene = view.getScene();
        view.applyCss(); view.layout();
        var image = scene.snapshot(null);
        assertEquals(1900, image.getWidth()); assertEquals(1040, image.getHeight());
        var output = new java.awt.image.BufferedImage(1900, 1040, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 1040; y++) for (int x = 0; x < 1900; x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/single-stage4b-preview"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }
    private static ApplicationController uiPlan() throws Exception {
        var controller = new ApplicationController(); controller.newPlan();
        var view = new HouseholdView(); view.load(controller.getCurrentPlan()); fillPrimary(view);
        assertTrue(view.applyChanges());
        var plan = controller.getCurrentPlan();
        var template = SinglePersonMortalityAnalysisTest.plan();
        plan.setPlanningAssumptions(template.getPlanningAssumptions());
        var assumptions = new AssumptionsView(); assumptions.load(plan);
        field(assumptions, "filingStatusComboBox", ComboBox.class).setValue(com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE);
        field(assumptions, "localIncomeTaxRateField", TextField.class).setText("0");
        assertTrue(assumptions.applyChanges());
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                true, 2027, new BigDecimal("5000"),
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        template.getHousehold().getExpenses().forEach(plan.getHousehold()::addExpense);
        for (var original : template.getAccountPortfolio().getAccounts()) {
            var dialog = new AccountDialog(original, plan.getHousehold());
            plan.getAccountPortfolio().addAccount(dialog.getResultConverter().call(ButtonType.OK));
        }
        for (var original : template.getHousehold().getPrimaryPerson().getIncomeSources()) {
            IncomeSource entered = original instanceof Pension pension
                    ? new PensionDialog(pension, plan.getHousehold()).getResultConverter().call(ButtonType.OK)
                    : new SocialSecurityDialog((SocialSecurityIncome) original, 2027, plan.getHousehold()).getResultConverter().call(ButtonType.OK);
            plan.getHousehold().getPrimaryPerson().addIncomeSource(entered);
        }
        controller.markModified(); controller.invalidateProjection();
        return controller;
    }

    private static void fillPrimary(HouseholdView view) {
        card(view, 0).load(SinglePersonMortalityAnalysisTest.plan().getHousehold().getPrimaryPerson());
    }
    private static PersonCard card(HouseholdView view, int index) { return (PersonCard) pane(view, index).getContent(); }
    private static TitledPane pane(HouseholdView view, int index) {
        return (TitledPane) view.getChildren().filtered(n -> n instanceof TitledPane).get(index);
    }
    private static Label status(HouseholdView view) { return (Label) view.getChildren().filtered(n -> n instanceof Label).getFirst(); }
    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(object));
    }
    private static void invoke(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(object);
    }
    private static String text(javafx.scene.Node node) {
        var own = node instanceof Labeled labeled ? labeled.getText() : "";
        if (node instanceof ScrollPane scroll) own += text(scroll.getContent());
        if (node instanceof javafx.scene.Parent parent) for (var child : parent.getChildrenUnmodifiable()) own += text(child);
        return own;
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(60, TimeUnit.SECONDS);
    }
    private static void snapshot(Stage stage, String name) throws Exception {
        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        if (stage.getScene().getRoot() instanceof HouseholdView view) {
            for (var child : view.getChildren()) if (child.isVisible() && child.isManaged()) {
                var bounds = child.localToScene(child.getBoundsInLocal());
                assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= stage.getScene().getWidth(), name + " horizontal bounds");
                assertTrue(bounds.getMaxY() <= stage.getScene().getHeight(), name + " vertical bounds");
            }
            for (int member = 0; member < 2; member++) if (pane(view, member).isVisible()) {
                for (var node : card(view, member).getChildren()) if (node instanceof Label label) {
                    assertTrue(label.getWidth() >= label.prefWidth(-1), name + ": label must not be truncated: " + label.getText());
                }
            }
        }
        var image = stage.getScene().snapshot(null);
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/single-stage4a1-preview"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }
}
