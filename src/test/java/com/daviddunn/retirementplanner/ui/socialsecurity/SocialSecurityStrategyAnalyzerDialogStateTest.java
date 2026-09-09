package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityStrategyAnalyzerDialogStateTest {

    private static RetirementPlan plan;
    private static SocialSecurityStrategyAnalyzerPresentation socialSecurity;
    private static IntegratedSocialSecurityComparisonPresentation quick;
    private static ExhaustiveIntegratedSearchPresentation exhaustive;

    @BeforeAll
    static void prepare() throws Exception {
        FutureTask<Void> startup = new FutureTask<>(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
        try {
            Platform.startup(startup);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(startup);
        }
        startup.get(20, TimeUnit.SECONDS);

        Person primary = person("Primary", AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4));
        Person spouse = person("Spouse", AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28));
        plan = new RetirementPlan(new Household(primary, spouse), new AccountPortfolio(),
                new PlanningAssumptions(new BigDecimal("0.03"), new BigDecimal("0.02"),
                        2, LocalDate.of(2026, 7, 1)));
        var context = new SocialSecurityStrategyAnalysisRequestFactory().create(plan,
                SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE,
                new BigDecimal("0.01"), LocalDate.of(2026, 7, 1));
        var original = context.request().retirementGridRequest();
        var mortality = new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(80, BigDecimal.ONE)));
        var grid = new SocialSecurityMortalityWeightedClaimingGridRequest(original.baseStrategy(),
                List.of(67), List.of(67), mortality, mortality, original.mortalityBaseDate(),
                original.presentValueBaseDate(), original.realDiscountRate());
        socialSecurity = SocialSecurityStrategyAnalyzerPresentation.from(
                new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(
                        new SocialSecuritySurvivorClaimingOptimizationRequest(grid)));
        var selected = socialSecurity.rankedStrategies().subList(0, 1);
        quick = IntegratedSocialSecurityComparisonPresentation.from(
                new IntegratedSocialSecurityStrategyComparisonService().compareAnalyzerCandidates(
                        plan, selected.stream().map(
                                SocialSecurityStrategyAnalyzerPresentation.RankedStrategy::cell).toList()),
                selected);
        exhaustive = ExhaustiveIntegratedSearchPresentation.from(
                new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(searchRequest()),
                Duration.ZERO, 20);
    }

    @Test
    void labelsAndActionsAreScopedToTheirAnalysis() throws Exception {
        onFx(dialog -> {
            BorderPane root = (BorderPane) field(dialog, "stage", Stage.class).getScene().getRoot();
            assertTrue(text(root.getTop()).contains("Longevity and Valuation Assumptions"));
            assertTrue(text(root.getTop()).contains("Valuation date:"));
            assertFalse(text(root.getTop()).contains("Run "));
            assertFalse(text(root).contains("PV base date"));
            TabPane modes = (TabPane) root.getCenter();
            assertEquals(List.of("Social Security Only", "Integrated Retirement Plan"),
                    modes.getTabs().stream().map(Tab::getText).toList());
            String ss = text(modes.getTabs().getFirst().getContent());
            assertTrue(ss.contains("Run Social Security Analysis"));
            assertTrue(ss.contains("Uses the longevity assumptions above to weight Social Security benefits."));
            String integrated = text(modes.getTabs().getLast().getContent());
            assertTrue(integrated.contains("Deterministic Integrated Retirement Plan"));
            assertTrue(integrated.contains("Deterministic integrated analysis uses the retirement plan's configured "
                    + "death scenario for full-plan outcomes. It does not use the longevity "
                    + "assumptions above to weight full retirement-plan projections."));
            assertTrue(integrated.contains("Candidate selection and Social Security Expected PV use the longevity "
                    + "assumptions above. Integrated retirement-plan outcome columns remain deterministic."));
            assertTrue(integrated.contains("Exhaustive Search evaluates all tested claiming strategies against the "
                    + "deterministic full retirement plan. Longevity assumptions above are not used in this search."));
            assertTrue(integrated.contains("Longevity-Weighted"));
            assertTrue(integrated.contains("Run Longevity-Weighted Exhaustive Search"));
            assertTrue(integrated.contains("Run Deterministic Exhaustive Search"));
            assertTrue(text(root.getTop()).contains("mortality conditioning date"));
            assertTrue(text(root.getTop()).contains("Quick Comparison uses these assumptions for candidate selection"));
        });
    }

    @Test
    void assumptionChangesInvalidateOnlySocialSecurityAndQuickComparison() throws Exception {
        onFx(dialog -> {
            List<Runnable> changes = List.of(
                    () -> uncheckedField(dialog, "primaryCategory", ComboBox.class)
                            .setValue(SocialSecurityMortalityCategory.FEMALE),
                    () -> uncheckedField(dialog, "spouseCategory", ComboBox.class)
                            .setValue(SocialSecurityMortalityCategory.MALE),
                    () -> uncheckedField(dialog, "primaryMortalityAdjustment", TextField.class).setText("0.80"),
                    () -> uncheckedField(dialog, "spouseMortalityAdjustment", TextField.class).setText("1.50"),
                    () -> uncheckedField(dialog, "discountRate", TextField.class).setText("2.0"),
                    () -> uncheckedField(dialog, "pvDate", DatePicker.class).setValue(LocalDate.of(2027, 7, 1)));
            for (Runnable change : changes) {
                installResults(dialog);
                change.run();
                assertFalse(field(dialog, "socialSecurityResultCurrent", Boolean.class));
                assertFalse(field(dialog, "stale", Label.class).getText().isEmpty());
                assertFalse(field(dialog, "integratedStale", Label.class).getText().isEmpty());
                assertEquals("", field(dialog, "exhaustiveStale", Label.class).getText());
                assertSame(exhaustive, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
                assertTrue(field(dialog, "exhaustiveSocialSecurityReference", Label.class)
                        .getText().contains("run current SS-only analysis first"));
                assertTrue(field(dialog, "integratedRunButton", Button.class).isDisabled());
                assertFalse(field(dialog, "exhaustiveRunButton", Button.class).isDisabled());
            }
        });
    }

    @Test
    void candidateCountInvalidatesOnlyQuickComparison() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            field(dialog, "integratedCandidateCount", Spinner.class).getValueFactory().setValue(5);
            assertTrue(field(dialog, "socialSecurityResultCurrent", Boolean.class));
            assertEquals("", field(dialog, "stale", Label.class).getText());
            assertFalse(field(dialog, "integratedStale", Label.class).getText().isEmpty());
            assertEquals("", field(dialog, "exhaustiveStale", Label.class).getText());
            assertSame(exhaustive, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
        });
    }

    @Test
    void editsBeforeFirstSocialSecurityRunDoNotMarkResultsStale() throws Exception {
        onFx(dialog -> {
            set(dialog, "exhaustivePresentation", exhaustive);
            field(dialog, "primaryMortalityAdjustment", TextField.class).setText("1.50");
            field(dialog, "integratedCandidateCount", Spinner.class).getValueFactory().setValue(5);
            assertEquals("", field(dialog, "stale", Label.class).getText());
            assertEquals("", field(dialog, "integratedStale", Label.class).getText());
            assertEquals("", field(dialog, "exhaustiveStale", Label.class).getText());
            assertTrue(field(dialog, "integratedRunButton", Button.class).isDisabled());
            assertFalse(field(dialog, "exhaustiveRunButton", Button.class).isDisabled());
        });
    }

    @Test
    void everyAnalysisDisablesCompetingRunsAndCancellationWaitsForCleanup() throws Exception {
        onFx(dialog -> {
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            var coordinator = field(jobs, "coordinator", SocialSecurityAnalysisJobCoordinator.class);
            var executor = field(coordinator, "executor", SocialSecurityAnalyzerJobControllerTest.ManualExecutor.class);
            for (var mode : SocialSecurityAnalyzerJobController.Mode.values()) {
                assertTrue(jobs.start(mode, (p, c) -> "done", value -> fail("cancelled"), error -> fail(error)));
                for (String button : List.of("runButton", "integratedRunButton", "exhaustiveRunButton")) {
                    assertTrue(field(dialog, button, Button.class).isDisabled());
                }
                assertTrue(field(dialog, "pvDate", DatePicker.class).isDisabled());
                assertFalse(field(dialog, "socialSecurityCancelButton", Button.class).isDisabled());
                field(dialog, "socialSecurityCancelButton", Button.class).fire();
                assertEquals(SocialSecurityAnalyzerJobController.State.CANCELLING, jobs.state());
                assertTrue(field(dialog, "socialSecurityCancelButton", Button.class).isDisabled());
                assertTrue(field(dialog, "runButton", Button.class).isDisabled());
                assertTrue(coordinator.isBusy());
                executor.run();
                assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, jobs.state());
                assertFalse(field(dialog, "runButton", Button.class).isDisabled());
                assertFalse(field(dialog, "pvDate", DatePicker.class).isDisabled());
                assertTrue(field(dialog, "integratedRunButton", Button.class).isDisabled());
            }
        });
    }

    @Test
    void footerWindowAndHideAllDisposeAndPreserveResults() throws Exception {
        for (String route : List.of("footer", "window", "hide")) {
            onFx(dialog -> {
                installResults(dialog);
                var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
                var coordinator = field(jobs, "coordinator", SocialSecurityAnalysisJobCoordinator.class);
                var executor = field(coordinator, "executor", SocialSecurityAnalyzerJobControllerTest.ManualExecutor.class);
                var stage = field(dialog, "stage", Stage.class);
                stage.show();
                jobs.start(SocialSecurityAnalyzerJobController.Mode.QUICK,
                        (p, c) -> "discard", value -> fail("post-close result"), error -> fail(error));
                String before = field(dialog, "integratedStatus", Label.class).getText();
                if (route.equals("footer")) {
                    var footer = (javafx.scene.layout.HBox) ((BorderPane) stage.getScene().getRoot()).getBottom();
                    ((Button) footer.getChildren().getLast()).fire();
                } else if (route.equals("window")) {
                    stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                } else {
                    stage.hide();
                }
                assertEquals(SocialSecurityAnalyzerJobController.State.CLOSED, jobs.state());
                assertTrue(coordinator.isBusy());
                executor.run();
                assertFalse(coordinator.isBusy());
                assertSame(quick, field(dialog, "integratedPresentation", IntegratedSocialSecurityComparisonPresentation.class));
                assertEquals(before, field(dialog, "integratedStatus", Label.class).getText());
            });
        }
    }

    @Test
    void frozenInputsPreservePlanAndCandidateBaselineCoherence() throws Exception {
        var source = new RetirementPlanScenarioCopyService().copy(plan);
        var selected = new java.util.ArrayList<>(socialSecurity.rankedStrategies().subList(0, 1));
        var quickInput = SocialSecurityAnalyzerInputs.quick(source, selected);
        var exhaustiveInput = SocialSecurityAnalyzerInputs.exhaustive(source);
        var birth = source.getHousehold().getPrimaryPerson().getBirthDate();
        source.getHousehold().getPrimaryPerson().setBirthDate(birth.plusYears(1));
        selected.clear();
        assertEquals(birth, quickInput.plan().getHousehold().getPrimaryPerson().getBirthDate());
        assertEquals(birth, exhaustiveInput.plan().getHousehold().getPrimaryPerson().getBirthDate());
        assertEquals(1, quickInput.selected().size());
        assertEquals(IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan).primarySurvivorCandidates(),
                exhaustiveInput.primarySurvivorCandidates());
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        assertEquals(evaluator.evaluateCurrentStrategy(plan).metrics(),
                evaluator.evaluateCurrentStrategy(quickInput.plan()).metrics());
    }
    @Test
    void exhaustiveRequestAndResultsRemainIndependentOfAnalyzerInputs() throws Exception {
        onFx(dialog -> {
            field(dialog, "primaryMortalityAdjustment", TextField.class).setText("0.50");
            field(dialog, "spouseMortalityAdjustment", TextField.class).setText("3.00");
            field(dialog, "discountRate", TextField.class).setText("5.0");
            field(dialog, "pvDate", DatePicker.class).setValue(LocalDate.of(2030, 1, 1));
        });
        var repeated = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(searchRequest());
        assertEquals(exhaustive.result().currentPlanBaseline().metrics(), repeated.currentPlanBaseline().metrics());
        assertEquals(exhaustive.result().entries(), repeated.entries());
    }

    @Test
    void sourcePlanNotificationMarksResultsAndDisposalDetachesListener() throws Exception {
        onFx(unused -> {
            var source = new com.daviddunn.retirementplanner.ui.controller.ApplicationController();
            set(source, "currentPlan", new RetirementPlanScenarioCopyService().copy(plan));
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, source);
            installResults(dialog);
            var weighted = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)), java.util.Optional.empty());
            set(dialog, "weightedPresentation", weighted);
            set(dialog, "weightedCurrent", true);
            source.markModified();
            assertFalse(field(dialog, "weightedCurrent", Boolean.class));
            assertSame(weighted, field(dialog, "weightedPresentation", LongevityWeightedIntegratedPresentation.class));
            assertFalse(field(dialog, "socialSecurityResultCurrent", Boolean.class));
            assertFalse(field(dialog, "exhaustiveStale", Label.class).getText().isEmpty());
            var close = dialog.getClass().getDeclaredMethod("close");
            close.setAccessible(true);
            close.invoke(dialog);
            var before = field(dialog, "plan", RetirementPlan.class);
            source.newPlan();
            assertSame(before, field(dialog, "plan", RetirementPlan.class));
        });
    }
    private static IntegratedSocialSecurityCompleteStrategySearchRequest searchRequest() {
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        return new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(67), List.of(67),
                standard.primarySurvivorCandidates().subList(0, 1),
                standard.spouseSurvivorCandidates().subList(0, 1), standard.rankingMeasure(), 0);
    }

    @Test
    void weightedStalesWithoutAnySocialSecurityRunAndCandidateCountPreservesIt() throws Exception {
        onFx(dialog -> {
            var weighted = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)), java.util.Optional.empty());
            set(dialog, "weightedPresentation", weighted);
            set(dialog, "weightedCurrent", true);
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            view.render(weighted, "Current retirement elections");
            field(dialog, "integratedCandidateCount", Spinner.class).getValueFactory().setValue(5);
            assertTrue(field(dialog, "weightedCurrent", Boolean.class));
            assertEquals("", view.stale.getText());
            for (Runnable edit : List.<Runnable>of(
                    () -> uncheckedField(dialog, "primaryMortalityAdjustment", TextField.class).setText("1.10"),
                    () -> uncheckedField(dialog, "discountRate", TextField.class).setText("3.0"),
                    () -> uncheckedField(dialog, "pvDate", DatePicker.class).setValue(LocalDate.of(2027, 1, 1)))) {
                set(dialog, "weightedCurrent", true);
                edit.run();
                assertFalse(field(dialog, "weightedCurrent", Boolean.class));
                assertEquals("Inputs changed — rerun", view.stale.getText());
                assertSame(weighted, field(dialog, "weightedPresentation", LongevityWeightedIntegratedPresentation.class));
            }
        });
    }

    @Test
    void weightedTypedSortAndAggregateDetailRemainUsableAtLaptopSizes() throws Exception {
        onFx(dialog -> {
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            var model = LongevityWeightedIntegratedPresentationTest.model(List.of(
                    LongevityWeightedIntegratedPresentationTest.entry(1, "9", 10),
                    LongevityWeightedIntegratedPresentationTest.entry(2, "100", 2)), java.util.Optional.empty());
            view.render(model, "Current retirement elections");
            assertEquals(2, view.table.getItems().getFirst().rank().orElseThrow());
            assertEquals(7, view.table.getColumns().size());
            assertInstanceOf(BigDecimal.class, view.table.getColumns().get(5).getCellData(0));
            view.table.getSortOrder().setAll(view.table.getColumns().get(5));
            view.table.sort();
            assertEquals(new BigDecimal("9"), view.table.getColumns().get(5).getCellData(0));
            assertEquals(10, view.table.getItems().getFirst().rank().orElseThrow());
            assertTrue(field(view, "current", Label.class).getText().contains("No survivor age has been assumed"));
            assertTrue(field(view, "detail", TextArea.class).getText().contains("Evaluated probability coverage"));
            var stage = field(dialog, "stage", Stage.class);
            var top = (TabPane) ((BorderPane) stage.getScene().getRoot()).getCenter();
            top.getSelectionModel().selectLast();
            var inner = (TabPane) ((javafx.scene.layout.VBox) top.getSelectionModel().getSelectedItem().getContent()).getChildren().getFirst();
            inner.getSelectionModel().selectLast();
            for (int[] size : List.of(new int[]{1180, 820}, new int[]{900, 650}, new int[]{1366, 768})) {
                stage.setWidth(size[0]); stage.setHeight(size[1]); stage.show();
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                assertTrue(view.table.getWidth() > 600);
                assertTrue(view.table.getHeight() >= 100);
                assertTrue(view.run.isFocusTraversable());
                assertTrue(field(dialog, "sharedCancel", Button.class).isFocusTraversable());
            }
        });
    }

    @Test
    void weightedRunUsesControllerAndCancellationPreservesResultBeforeExecution() throws Exception {
        onFx(dialog -> {
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            assertTrue(view.run.isDisabled());
            field(dialog, "primaryCategory", ComboBox.class).setValue(SocialSecurityMortalityCategory.MALE);
            field(dialog, "spouseCategory", ComboBox.class).setValue(SocialSecurityMortalityCategory.FEMALE);
            assertFalse(view.run.isDisabled());
            var previous = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)), java.util.Optional.empty());
            set(dialog, "weightedPresentation", previous);
            view.render(previous, "Current elections");
            view.run.fire();
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            assertEquals(SocialSecurityAnalyzerJobController.Mode.WEIGHTED, jobs.mode());
            assertTrue(view.run.isDisabled());
            assertTrue(view.status.getText().contains("Rerunning"));
            field(dialog, "sharedCancel", Button.class).fire();
            var coordinator = field(jobs, "coordinator", SocialSecurityAnalysisJobCoordinator.class);
            assertTrue(coordinator.isBusy());
            field(coordinator, "executor", SocialSecurityAnalyzerJobControllerTest.ManualExecutor.class).run();
            assertEquals("Analysis cancelled", view.status.getText());
            assertSame(previous, field(dialog, "weightedPresentation", LongevityWeightedIntegratedPresentation.class));
            assertFalse(view.run.isDisabled());
        });
    }

    @Test
    void weightedCompleteBaselineRendersItsRankWithoutInventingFailure() throws Exception {
        onFx(dialog -> {
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            var model = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)),
                    java.util.Optional.of(LongevityWeightedIntegratedPresentationTest.entry(0, "100", null)));
            view.render(model, "Current elections");
            String current = field(view, "current", Label.class).getText();
            assertTrue(current.contains("Weighted rank: 1"));
            assertTrue(current.contains("Expected PV After-Tax Estate"));
            assertTrue(current.contains("Tested strategies tied at current expected PV: 1"));
            assertFalse(current.contains("unavailable"));
        });
    }

    private static Person person(String name, AccountOwnership owner, LocalDate birth) {
        Person person = new Person(name, "Planner", birth);
        person.addIncomeSource(new SocialSecurityIncome("Social Security", owner, birth.plusYears(67),
                null, new BigDecimal("2000"), 67, BigDecimal.ZERO, 2025));
        return person;
    }

    private static void installResults(SocialSecurityStrategyAnalyzerDialog dialog) throws Exception {
        set(dialog, "presentation", socialSecurity);
        set(dialog, "integratedPresentation", quick);
        set(dialog, "exhaustivePresentation", exhaustive);
        set(dialog, "socialSecurityResultCurrent", true);
        field(dialog, "stale", Label.class).setText("");
        field(dialog, "integratedStale", Label.class).setText("");
    }

    private static String text(Node node) {
        Stream<String> own = node instanceof Labeled labeled ? Stream.of(labeled.getText()) : Stream.empty();
        Stream<String> children = node instanceof TabPane tabs
                ? tabs.getTabs().stream().map(tab -> tab.getText() + " " + text(tab.getContent()))
                : node instanceof ScrollPane scroll ? Stream.of(text(scroll.getContent()))
                : node instanceof TitledPane titled ? Stream.of(text(titled.getContent()))
                : node instanceof Parent parent
                        ? parent.getChildrenUnmodifiable().stream().map(SocialSecurityStrategyAnalyzerDialogStateTest::text)
                        : Stream.empty();
        return Stream.concat(own, children).reduce("", (a, b) -> a + " " + b);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static <T> T uncheckedField(Object target, String name, Class<T> type) {
        try {
            return field(target, name, type);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name, boolean busy) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, boolean.class);
        method.setAccessible(true);
        method.invoke(target, busy);
    }

    private static void onFx(DialogCheck check) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var executor = new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();
            var coordinator = new SocialSecurityAnalysisJobCoordinator(executor);
            var jobs = new SocialSecurityAnalyzerJobController(coordinator, Runnable::run);
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, plan, jobs);
            try {
                check.run(dialog);
            } finally {
                var close = dialog.getClass().getDeclaredMethod("close");
                close.setAccessible(true);
                close.invoke(dialog);
                field(dialog, "stage", Stage.class).close(); coordinator.close();
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    private interface DialogCheck {
        void run(SocialSecurityStrategyAnalyzerDialog dialog) throws Exception;
    }
}
