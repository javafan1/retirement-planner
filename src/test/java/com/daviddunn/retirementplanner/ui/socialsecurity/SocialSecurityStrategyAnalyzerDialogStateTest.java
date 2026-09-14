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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityStrategyAnalyzerDialogStateTest {

    @Test
    void deterministicHeatMapInteractionsUseFrozenResultsAndExactUnlistedStrategyDetails() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            var source = DeterministicHeatMapAdapterTest.fixture();
            set(dialog, "exhaustivePresentation", source);
            var render = dialog.getClass().getDeclaredMethod("renderExhaustive", ExhaustiveIntegratedSearchPresentation.class);
            render.setAccessible(true);
            render.invoke(dialog, source);
            var heatMap = field(dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            var tabs = field(dialog, "deterministicResultTabs", TabPane.class);
            var table = field(dialog, "exhaustiveTable", TableView.class);
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            var model = field(heatMap, "model", ClaimingStrategyHeatMapModel.class);
            long generation = jobs.generation();
            long revision = field(dialog, "assumptionsRevision", Long.class);
            var items = List.copyOf(table.getItems());
            assertEquals(List.of("Ranked Strategies", "Claiming-Age Heat Map"), tabs.getTabs().stream().map(Tab::getText).toList());
            assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
            var cell = ClaimingStrategyHeatMapViewTest.button(heatMap, 62, 70);
            tabs.getSelectionModel().selectLast();
            cell.fire();
            assertTrue(text(heatMap).contains("Deterministic estate rank: 2"));
            assertTrue(text(heatMap).contains("Primary survivor age: 67 years 4 months"));
            for (var metric : heatMap.metric.getItems()) {
                heatMap.metric.setValue(metric);
                assertTrue(text(heatMap).contains(metric + ": " + ClaimingStrategyHeatMapView.format(metric, model.cell(62, 70).value(metric))));
            }
            assertEquals(4, heatMap.metric.getItems().size());
            assertFalse(heatMap.metric.getItems().contains(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE));
            tabs.getSelectionModel().selectFirst();
            tabs.getSelectionModel().selectLast();
            assertTrue(cell.isSelected());
            assertSame(model, field(heatMap, "model", ClaimingStrategyHeatMapModel.class));
            heatMap.fullAnalysis.fire();
            assertEquals("Ranked Strategies", tabs.getSelectionModel().getSelectedItem().getText());
            assertTrue(table.getSelectionModel().isEmpty(), "Do not select a different grouped representative");
            var details = field(dialog, "exhaustiveDetails", TextArea.class).getText();
            assertTrue(details.contains("Original strategy occurrence: 2"));
            assertTrue(details.contains("Deterministic After-Tax Estate Rank: 2"));
            assertEquals(items, table.getItems(), "Do not insert missing groups or alter the ranked table");
            assertEquals(generation, jobs.generation());
            assertEquals(revision, field(dialog, "assumptionsRevision", Long.class));
            assertSame(source, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, jobs.state());
            invoke(dialog, "setAnalysisBusy", true);
            assertTrue(heatMap.metric.isDisabled());
            assertTrue(cell.isDisabled());
            assertTrue(heatMap.fullAnalysis.isDisabled());
            invoke(dialog, "setAnalysisBusy", false);
            assertFalse(cell.isDisabled());
            field(dialog, "exhaustiveStale", Label.class).setText("Plan changed — rerun");
            assertEquals("Plan changed — rerun", heatMap.resultNotice.getText());
            render.invoke(dialog, source);
            assertEquals(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL, heatMap.metric.getValue());
            assertTrue(ClaimingStrategyHeatMapViewTest.button(heatMap, 69, 62).isSelected());
            heatMap.fullAnalysis.fire();
            assertSame(source.groups().getFirst(), table.getSelectionModel().getSelectedItem());
        });
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void desktopHeatMapTabRevealsAllRowsAndNavigatesBackToRankedDetails(boolean deterministic) throws Exception {
        var holder = new java.util.concurrent.atomic.AtomicReference<SocialSecurityStrategyAnalyzerDialog>();
        var coordinator = new SocialSecurityAnalysisJobCoordinator(new SocialSecurityAnalyzerJobControllerTest.ManualExecutor());
        FutureTask<Void> open = new FutureTask<>(() -> {
            var jobs = new SocialSecurityAnalyzerJobController(coordinator, Runnable::run);
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, plan, jobs);
            holder.set(dialog);
            var stage = field(dialog, "stage", Stage.class);
            stage.setWidth(1880);
            stage.setHeight(1000);
            var modes = field(dialog, "modes", TabPane.class);
            modes.getSelectionModel().selectLast();
            var integrated = (TabPane) ((javafx.scene.layout.VBox) modes.getTabs().getLast().getContent()).getChildren().getFirst();
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            if (deterministic) {
                integrated.getSelectionModel().selectFirst();
                var deterministicTabs = (TabPane) ((javafx.scene.layout.VBox) integrated.getTabs().getFirst().getContent())
                        .getChildren().stream().filter(TabPane.class::isInstance).findFirst().orElseThrow();
                deterministicTabs.getSelectionModel().selectLast();
                var source = DeterministicHeatMapAdapterTest.fixture();
                set(dialog, "exhaustivePresentation", source);
                var render = dialog.getClass().getDeclaredMethod("renderExhaustive", ExhaustiveIntegratedSearchPresentation.class);
                render.setAccessible(true);
                render.invoke(dialog, source);
            } else {
                integrated.getSelectionModel().selectLast();
                view.render(ClaimingStrategyHeatMapViewTest.fixture(), "Current elections");
            }
            stage.show();
            settle(stage.getScene().getRoot());
            var tabs = deterministic ? field(dialog, "deterministicResultTabs", TabPane.class) : view.resultTabs;
            tabs.getSelectionModel().selectLast();
            return null;
        });
        Platform.runLater(open);
        try {
            open.get(30, TimeUnit.SECONDS);
            // Queued after the tab's reveal callback, so this tests actual navigation, not a manual scroll.
            FutureTask<Void> verify = new FutureTask<>(() -> {
                var dialog = holder.get();
                var stage = field(dialog, "stage", Stage.class);
                settle(stage.getScene().getRoot());
                var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
                var heatMap = deterministic ? field(dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class) : view.heatMap;
                var outer = (ScrollPane) ((BorderPane) stage.getScene().getRoot()).getCenter();
                var viewport = outer.lookup(".viewport");
                var visible = viewport.localToScene(viewport.getBoundsInLocal());
                assertEquals(81, heatMap.lookupAll(".heat-map-cell").size());
                for (int spouse = 62; spouse <= 70; spouse++) for (int primary = 62; primary <= 70; primary++) {
                    var cell = ClaimingStrategyHeatMapViewTest.button(heatMap, primary, spouse);
                    var bounds = cell.localToScene(cell.getBoundsInLocal());
                    assertTrue(bounds.getMinY() >= visible.getMinY() && bounds.getMaxY() <= visible.getMaxY(),
                            "Entire age grid must fit the desktop viewport");
                    assertTrue(bounds.getMinX() >= visible.getMinX() && bounds.getMaxX() <= visible.getMaxX());
                }
                var gridArea = heatMap.lookup("#heat-map-grid-area");
                var details = heatMap.lookup("#heat-map-details");
                assertTrue(details.getBoundsInParent().getMinX() >= gridArea.getBoundsInParent().getMaxX());
                heatMap.metric.setValue(deterministic ? ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE
                        : ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE);
                settle(stage.getScene().getRoot());
                assertTrue(ClaimingStrategyHeatMapViewTest.button(heatMap, 62, 62).getWidth() >= 100,
                        "Desktop cells must accommodate eight-digit currency values at normal font size");
                ClaimingStrategyHeatMapViewTest.button(heatMap, 62, 70).fire();
                heatMap.fullAnalysis.fire();
                if (deterministic) {
                    var tabs = field(dialog, "deterministicResultTabs", TabPane.class);
                    assertEquals("Ranked Strategies", tabs.getSelectionModel().getSelectedItem().getText());
                    assertTrue(field(dialog, "exhaustiveDetails", TextArea.class).getText().contains("Original strategy occurrence: 2"));
                    settle(stage.getScene().getRoot());
                    var table = field(dialog, "exhaustiveTable", TableView.class);
                    assertEquals(table.prefHeight(table.getWidth()), table.getHeight(), 1);
                    assertEquals(javafx.scene.layout.Region.USE_COMPUTED_SIZE, table.getPrefHeight());
                } else {
                    assertSame(view.rankedTab, view.resultTabs.getSelectionModel().getSelectedItem());
                    assertEquals(2, view.table.getSelectionModel().getSelectedItem().inputOrder());
                }
                return null;
            });
            Platform.runLater(verify);
            verify.get(30, TimeUnit.SECONDS);
        } finally {
            FutureTask<Void> close = new FutureTask<>(() -> {
                if (holder.get() != null) {
                    var closeMethod = holder.get().getClass().getDeclaredMethod("close");
                    closeMethod.setAccessible(true);
                    closeMethod.invoke(holder.get());
                    field(holder.get(), "stage", Stage.class).close();
                }
                coordinator.close();
                return null;
            });
            Platform.runLater(close);
            close.get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void heatMapInteractionsPreserveAnalysisJobsRevisionsAndResults() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            var weighted = ClaimingStrategyHeatMapViewTest.fixture();
            set(dialog, "weightedPresentation", weighted);
            set(dialog, "weightedCurrent", true);
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            view.render(weighted, "Known current elections");
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            long generation = jobs.generation();
            long assumptionsRevision = field(dialog, "assumptionsRevision", Long.class);
            var originalPlan = plan.getPlanningAssumptions();
            view.resultTabs.getSelectionModel().select(view.heatMapTab);
            view.resultTabs.getSelectionModel().select(view.rankedTab);
            view.resultTabs.getSelectionModel().select(view.heatMapTab);
            var cell = ClaimingStrategyHeatMapViewTest.button(view.heatMap, 62, 70);
            cell.fire();
            for (var metric : view.heatMap.metric.getItems()) view.heatMap.metric.setValue(metric);
            view.heatMap.fullAnalysis.fire();
            assertSame(view.rankedTab, view.resultTabs.getSelectionModel().getSelectedItem());
            assertEquals(2, view.table.getSelectionModel().getSelectedItem().inputOrder());
            assertEquals(generation, jobs.generation());
            assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, jobs.state());
            assertEquals(assumptionsRevision, field(dialog, "assumptionsRevision", Long.class));
            assertTrue(field(dialog, "weightedCurrent", Boolean.class));
            assertTrue(field(dialog, "socialSecurityResultCurrent", Boolean.class));
            assertSame(weighted, field(dialog, "weightedPresentation", LongevityWeightedIntegratedPresentation.class));
            assertSame(exhaustive, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            assertSame(originalPlan, plan.getPlanningAssumptions());
            assertTrue(view.stale.getText().isEmpty());
            invoke(dialog, "setAnalysisBusy", true);
            assertTrue(view.heatMap.metric.isDisabled());
            assertTrue(cell.isDisabled());
            invoke(dialog, "setAnalysisBusy", false);
            assertFalse(cell.isDisabled());
        });
    }

    @Test
    void weightedBaselineSummaryShowsSourcesMetricsPositionAndFailureWithoutFabrication() throws Exception {
        onFx(dialog -> {
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            var captured = CurrentStrategyBaseline.capture(plan, "66", "70");
            var original = LongevityWeightedIntegratedPresentationTest.entry(0, "95.003", null);
            var base = new LongevityWeightedIntegratedStrategyComparisonEntry(0, captured.strategy().orElseThrow(),
                    original.aggregate(), original.failure(), original.scenarioDetails(), original.rank(),
                    original.pvDifferenceFromBaseline(), original.nominalDifferenceFromBaseline());
            var model = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100.004", 1)), java.util.Optional.of(base));
            view.render(model, captured);
            String summary = field(view, "current", Label.class).getText();
            for (var election : captured.elections()) {
                assertTrue(summary.contains(election.name() + ": " + election.value() + " (" + election.source() + ")"));
            }
            assertTrue(summary.contains("Metric position among tested strategies: 2"));
            assertFalse(summary.contains("Weighted rank:"));
            assertTrue(summary.contains("Difference from Highest Strategy (current minus highest PV): -$5"));
            for (String metric : List.of("Expected Investable Assets at Second Death", "Expected After-Tax Heir Value", "Expected PV After-Tax Estate")) {
                assertTrue(summary.contains(metric));
            }
            var failed = new LongevityWeightedIntegratedStrategyComparisonEntry(0, base.strategy(), java.util.Optional.empty(),
                    java.util.Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(base.strategy(), "Controlled baseline failure",
                            IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)), java.util.Optional.empty(),
                    java.util.OptionalInt.empty(), java.util.Optional.empty(), java.util.Optional.empty());
            view.render(LongevityWeightedIntegratedPresentationTest.model(model.result().orderedEntries(), java.util.Optional.of(failed)), captured);
            summary = field(view, "current", Label.class).getText();
            assertTrue(summary.contains("Baseline evaluation unavailable: Controlled baseline failure"));
            assertFalse(summary.contains("$"));
            assertEquals(1, view.table.getItems().size());
            assertNull(view.table.getColumns().get(8).getCellData(0));
        });
    }

    @Test
    void differenceColumnKeepsPositiveNegativeAndZeroBigDecimalsWithoutRounding() throws Exception {
        onFx(dialog -> {
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            var entries = new java.util.ArrayList<LongevityWeightedIntegratedStrategyComparisonEntry>();
            var differences = List.of(new BigDecimal("0.004"), BigDecimal.ZERO, new BigDecimal("-0.003"));
            for (int i = 0; i < differences.size(); i++) {
                var entry = LongevityWeightedIntegratedPresentationTest.entry(i + 1, "100", i + 1);
                entries.add(new LongevityWeightedIntegratedStrategyComparisonEntry(entry.inputOrder(), entry.strategy(), entry.aggregate(),
                        entry.failure(), entry.scenarioDetails(), entry.rank(), java.util.Optional.of(differences.get(i)), java.util.Optional.empty()));
            }
            view.render(LongevityWeightedIntegratedPresentationTest.model(entries, java.util.Optional.empty()), "Known elections");
            for (int i = 0; i < differences.size(); i++) {
                assertEquals(differences.get(i), view.table.getColumns().get(8).getCellData(i));
            }
        });
    }

    @Test
    void survivorBaselineEditsOnlyInvalidateWeightedAndKeepFrozenSummary() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            var baseline = field(dialog, "baselineInputs", CurrentStrategyBaselineView.class);
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            var previous = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)), java.util.Optional.empty());
            set(dialog, "weightedPresentation", previous);
            set(dialog, "weightedCurrent", true);
            view.render(previous, baseline.snapshot());
            String frozen = field(view, "current", Label.class).getText();
            assertTrue(frozen.contains("Primary survivor claiming age: Not specified"));
            assertTrue(frozen.contains("Spouse survivor claiming age: Not specified"));
            long revision = field(dialog, "assumptionsRevision", Long.class);
            baseline.primarySurvivor.setText("66");
            baseline.spouseSurvivor.setText("67");
            assertFalse(field(dialog, "weightedCurrent", Boolean.class));
            assertTrue(view.stale.getText().contains("baseline changed"));
            assertTrue(field(dialog, "socialSecurityResultCurrent", Boolean.class));
            assertEquals("", field(dialog, "integratedStale", Label.class).getText());
            assertEquals("", field(dialog, "exhaustiveStale", Label.class).getText());
            assertEquals(revision, field(dialog, "assumptionsRevision", Long.class));
            assertEquals(frozen, field(view, "current", Label.class).getText());
            assertSame(exhaustive, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            assertTrue(text(field(dialog, "inputSummary", SocialSecurityAnalyzerInputView.class)).contains("Complete; weighted comparison only"));
        });
    }

    @Test
    void baselineSessionRetainsEditsButNewPlanResetsAndInputsHaveHelp() throws Exception {
        onFx(dialog -> {
            var inputs = field(dialog, "baselineInputs", CurrentStrategyBaselineView.class);
            var first = LongevityWeightedAnalysisRequestFactoryTest.plan();
            CurrentStrategyBaselineTest.scenario(first, DeathScenario.PRIMARY_DIES, 65);
            inputs.load(first);
            assertEquals("", inputs.primarySurvivor.getText());
            assertEquals("65", inputs.spouseSurvivor.getText());
            inputs.primarySurvivor.setText("66");
            inputs.spouseSurvivor.setText("67");
            var frozen = inputs.snapshot();
            inputs.load(first);
            assertEquals("66", inputs.primarySurvivor.getText());
            assertEquals("67", inputs.spouseSurvivor.getText());
            assertEquals("Analyzer override", inputs.snapshot().elections().get(3).source());
            assertEquals(65, first.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            var second = LongevityWeightedAnalysisRequestFactoryTest.plan();
            CurrentStrategyBaselineTest.scenario(second, DeathScenario.SPOUSE_DIES, 64);
            inputs.load(second);
            assertEquals("64", inputs.primarySurvivor.getText());
            assertEquals("", inputs.spouseSurvivor.getText());
            assertEquals(66, frozen.strategy().orElseThrow().primarySurvivorElection().ageYears());
            inputs.primarySurvivor.setText("69");
            assertEquals(64, second.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            assertEquals("Analyzer override", inputs.snapshot().elections().get(2).source());
            for (var control : List.of(inputs.primarySurvivor, inputs.spouseSurvivor)) {
                assertNotNull(control.getTooltip());
                assertTrue(control.getTooltip().getText().contains("does not change the deterministic plan"));
                assertEquals(375, control.getTooltip().getMaxWidth());
                assertTrue(control.isFocusTraversable());
            }
            inputs.load(LongevityWeightedAnalysisRequestFactoryTest.plan());
            assertEquals("", inputs.primarySurvivor.getText());
            assertEquals("", inputs.spouseSurvivor.getText());
        });
    }

    @Test
    void invalidOptionalBaselineDoesNotDisableCandidateRunOrPublishStaleJob() throws Exception {
        onFx(dialog -> {
            var baseline = field(dialog, "baselineInputs", CurrentStrategyBaselineView.class);
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            baseline.primarySurvivor.setText("bad");
            assertFalse(view.run.isDisabled());
            assertTrue(baseline.snapshot().strategy().isEmpty());
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            var published = new java.util.ArrayList<String>();
            assertTrue(jobs.start(SocialSecurityAnalyzerJobController.Mode.WEIGHTED,
                    (p, c) -> "old result", published::add, failure -> fail(failure)));
            baseline.primarySurvivor.setText("66");
            var coordinator = field(jobs, "coordinator", SocialSecurityAnalysisJobCoordinator.class);
            field(coordinator, "executor", SocialSecurityAnalyzerJobControllerTest.ManualExecutor.class).run();
            assertTrue(published.isEmpty());
            assertEquals("Inputs changed - run analysis again.", jobs.status());
        });
    }

    @Test
    void longevityTooltipsCoverEveryInputAndReuseEconomicHelpAppearance() throws Exception {
        onFx(dialog -> {
            Tooltip reference = new com.daviddunn.retirementplanner.ui.controls.HelpIcon("Reference").getTooltip();
            var names = List.of("primaryMortalityAdjustment",
                    "spouseMortalityAdjustment", "mortalityDate", "pvDate", "discountRate");
            var grid = (javafx.scene.layout.GridPane) field(dialog, "primaryMortalityAdjustment", Control.class).getParent();
            assertEquals(5, grid.getChildren().stream().filter(node -> node instanceof Control
                    && !(node instanceof Label)).count());
            assertEquals(11, grid.getChildren().size());
            assertEquals(10, grid.getHgap());
            assertEquals(6, grid.getVgap());
            for (String name : names) {
                Control control = field(dialog, name, Control.class);
                Tooltip tooltip = control.getTooltip();
                assertNotNull(tooltip, name);
                assertTrue(tooltip.isWrapText());
                assertEquals(reference.getMaxWidth(), tooltip.getMaxWidth());
                assertEquals(reference.getStyle(), tooltip.getStyle());
                assertEquals(reference.getStyleClass(), tooltip.getStyleClass());
                assertEquals(reference.getShowDelay(), tooltip.getShowDelay());
                assertEquals(reference.getShowDuration(), tooltip.getShowDuration());
                assertEquals(reference.getHideDelay(), tooltip.getHideDelay());
                assertTrue(control.isFocusTraversable());
                assertNull(control.getAccessibleText());
            }
            assertThrows(NoSuchFieldException.class, () -> dialog.getClass().getDeclaredField("primaryCategory"));
            assertThrows(NoSuchFieldException.class, () -> dialog.getClass().getDeclaredField("spouseCategory"));
            for (String name : List.of("primaryMortalityAdjustment", "spouseMortalityAdjustment")) {
                String help = field(dialog, name, Control.class).getTooltip().getText();
                for (String phrase : List.of("1.00 means standard", "0.75 means 75%", "1.25 means 125%",
                        "mortality hazard", "not multiplied directly", "remaining life")) {
                    assertTrue(help.contains(phrase), phrase);
                }
            }
            String conditioning = field(dialog, "mortalityDate", Control.class).getTooltip().getText();
            assertTrue(conditioning.contains("both people are alive"));
            assertTrue(conditioning.contains("does not change the retirement-plan projection start date"));
            String valuation = field(dialog, "pvDate", Control.class).getTooltip().getText();
            assertTrue(valuation.contains("present-value results"));
            assertTrue(valuation.contains("does not change the mortality conditioning date"));
            assertTrue(field(dialog, "discountRate", Control.class).getTooltip().getText()
                    .contains("discount inflation-adjusted future values back to the valuation date"));
        });
    }

    @Test
    void longevityTooltipCreationPreservesInputsAndAnalysisState() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            long valuationRevision = jobs.valuationRevision();
            long mortalityRevision = jobs.mortalityConditioningRevision();
            var names = List.of("primaryMortalityAdjustment",
                    "spouseMortalityAdjustment", "mortalityDate", "pvDate", "discountRate");
            var before = names.stream().map(name -> inputValue(uncheckedField(dialog, name, Control.class))).toList();
            var assumptions = plan.getPlanningAssumptions();
            var method = dialog.getClass().getDeclaredMethod("inputs");
            method.setAccessible(true);
            method.invoke(dialog);
            assertEquals(before, names.stream().map(name -> inputValue(uncheckedField(dialog, name, Control.class))).toList());
            assertSame(assumptions, plan.getPlanningAssumptions());
            assertEquals(valuationRevision, jobs.valuationRevision());
            assertEquals(mortalityRevision, jobs.mortalityConditioningRevision());
            assertTrue(field(dialog, "socialSecurityResultCurrent", Boolean.class));
            assertTrue(field(dialog, "stale", Label.class).getText().isEmpty());
            assertTrue(field(dialog, "integratedStale", Label.class).getText().isEmpty());
            assertSame(exhaustive, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
        });
    }

    private static Object inputValue(Control control) {
        if (control instanceof TextField text) return text.getText();
        if (control instanceof DatePicker date) return date.getValue();
        return ((ComboBox<?>) control).getValue();
    }

    @Test
    void dateControlsUpdateCurrentInputsWithoutChangingFrozenResultsOrTheOtherDate() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            var mortality = field(dialog, "mortalityDate", DatePicker.class);
            var valuation = field(dialog, "pvDate", DatePicker.class);
            assertEquals(plan.getPlanningAssumptions().getProjectionStartDate(), mortality.getValue());
            assertEquals(mortality.getValue(), valuation.getValue());
            var context = new SocialSecurityStrategyAnalysisRequestFactory().create(plan,
                    SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE,
                    new BigDecimal("0.01"), valuation.getValue());
            var render = dialog.getClass().getDeclaredMethod("render", SocialSecurityStrategyAnalysisContext.class,
                    SocialSecurityStrategyAnalyzerPresentation.class);
            render.setAccessible(true);
            render.invoke(dialog, context, socialSecurity);
            var frozenText = field(dialog, "assumptions", TextArea.class).getText();
            assertTrue(frozenText.contains("Mortality conditioning date:"));
            var weighted = LongevityWeightedIntegratedPresentationTest.model(
                    List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)), java.util.Optional.empty());
            set(dialog, "weightedPresentation", weighted);
            set(dialog, "weightedCurrent", true);
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            view.render(weighted, "Current elections");
            var frozenWeighted = field(view, "methodology", Label.class).getText();
            valuation.setValue(LocalDate.of(2025, 1, 1));
            assertEquals(LocalDate.of(2026, 7, 1), mortality.getValue());
            assertFalse(field(dialog, "socialSecurityResultCurrent", Boolean.class));
            assertFalse(field(dialog, "weightedCurrent", Boolean.class));
            assertFalse(field(dialog, "integratedStale", Label.class).getText().isBlank());
            assertTrue(field(dialog, "exhaustiveStale", Label.class).getText().isBlank());
            assertSame(exhaustive, field(dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            assertTrue(text(field(dialog, "inputSummary", SocialSecurityAnalyzerInputView.class)).contains("2025-01-01"));
            mortality.setValue(LocalDate.of(2027, 1, 1));
            assertEquals(LocalDate.of(2025, 1, 1), valuation.getValue());
            assertTrue(text(field(dialog, "inputSummary", SocialSecurityAnalyzerInputView.class)).contains("2027-01-01"));
            assertEquals(frozenText, field(dialog, "assumptions", TextArea.class).getText());
            assertEquals(frozenWeighted, field(view, "methodology", Label.class).getText());
            assertEquals(LocalDate.of(2026, 7, 1), plan.getPlanningAssumptions().getProjectionStartDate());
            assertSame(weighted, field(dialog, "weightedPresentation", LongevityWeightedIntegratedPresentation.class));
            var jobs = field(dialog, "jobs", SocialSecurityAnalyzerJobController.class);
            assertEquals(1, jobs.valuationRevision());
            assertEquals(1, jobs.mortalityConditioningRevision());
        });
    }

    @ParameterizedTest
    @CsvSource({"1180,820", "900,650", "1366,768"})
    void sharedInputSectionExpandsFullyAndLeavesRankingViewportStable(int width, int height) throws Exception {
        onFx(dialog -> {
            var stage = field(dialog, "stage", Stage.class);
            stage.setWidth(width);
            stage.setHeight(height);
            var modes = field(dialog, "modes", TabPane.class);
            modes.getSelectionModel().selectLast();
            var integrated = (TabPane) ((javafx.scene.layout.VBox) modes.getTabs().getLast().getContent()).getChildren().getFirst();
            integrated.getSelectionModel().selectLast();
            stage.show();
            var root = stage.getScene().getRoot();
            settle(root);
            var table = field(dialog, "weightedView", LongevityWeightedIntegratedView.class).table;
            var section = field(dialog, "inputSection", TitledPane.class);
            var summary = field(dialog, "inputSummary", SocialSecurityAnalyzerInputView.class);
            double tableHeight = table.getHeight();
            double collapsed = section.getHeight();
            assertEquals(table.prefHeight(table.getWidth()), tableHeight, 1);
            section.requestFocus();
            section.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.SPACE, false, false, false, false));
            settle(root);
            assertTrue(section.isExpanded());
            double expanded = section.getHeight();
            assertTrue(expanded > collapsed);
            assertNaturalContent(summary);
            assertEquals(section.prefHeight(section.getWidth()), section.getHeight(), 1);
            assertEquals(tableHeight, table.getHeight(), 1);
            var outer = (ScrollPane) ((BorderPane) root).getCenter();
            assertTrue(outer.isFitToWidth());
            assertFalse(outer.isFitToHeight());
            assertTrue(outer.getContent().getLayoutBounds().getHeight() > outer.getViewportBounds().getHeight());
            assertTrue(outer.getContent().getLayoutBounds().getWidth() <= outer.getViewportBounds().getWidth() + 1);
            var header = field(dialog, "header", javafx.scene.layout.VBox.class);
            assertTrue(header.getBoundsInParent().getMaxY() <= modes.getBoundsInParent().getMinY() + 1);
            section.setExpanded(false);
            settle(root);
            assertTrue(section.getHeight() < expanded);
            section.setExpanded(true);
            settle(root);
            assertEquals(expanded, section.getHeight(), 1);
            assertEquals(tableHeight, table.getHeight(), 1);
        });
    }

    private static void settle(Parent root) {
        for (int pass = 0; pass < 5; pass++) { root.applyCss(); root.layout(); }
    }

    private static void assertNaturalContent(Node node) {
        if (node instanceof javafx.scene.layout.Region region) {
            assertTrue(region.getHeight() + 1 >= region.prefHeight(region.getWidth()),
                    () -> node.getClass().getSimpleName() + " clipped at width " + region.getWidth());
        }
        if (node instanceof Parent parent && !(node instanceof Control)) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                assertTrue(child.getBoundsInParent().getMaxY() <= parent.getLayoutBounds().getHeight() + 1);
                assertNaturalContent(child);
            }
        }
    }

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
            assertTrue(text(field(dialog, "header", javafx.scene.layout.VBox.class)).contains("Longevity and Valuation Assumptions"));
            assertTrue(text(field(dialog, "header", javafx.scene.layout.VBox.class)).contains("Valuation date:"));
            assertFalse(text(field(dialog, "header", javafx.scene.layout.VBox.class)).contains("Run "));
            assertFalse(text(root).contains("PV base date"));
            TabPane modes = field(dialog, "modes", TabPane.class);
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
            assertTrue(text(field(dialog, "header", javafx.scene.layout.VBox.class)).contains("Mortality conditioning date"));
            assertTrue(text(field(dialog, "header", javafx.scene.layout.VBox.class)).contains("Quick Comparison uses these assumptions for candidate selection"));
        });
    }

    @Test
    void assumptionChangesInvalidateOnlySocialSecurityAndQuickComparison() throws Exception {
        onFx(dialog -> {
            List<Runnable> changes = List.of(
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
            source.getCurrentPlan().getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.FEMALE);
            source.markModified();
            var next = new SocialSecurityStrategyAnalysisRequestFactory().create(source.getCurrentPlan(),
                    SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(),
                    BigDecimal.ZERO, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));
            assertEquals(SocialSecurityMortalityCategory.FEMALE, next.primaryMortalityCategory());
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
            assertEquals(9, view.table.getColumns().size());
            assertInstanceOf(BigDecimal.class, view.table.getColumns().get(7).getCellData(0));
            view.table.getSortOrder().setAll(view.table.getColumns().get(7));
            view.table.sort();
            assertEquals(new BigDecimal("9"), view.table.getColumns().get(7).getCellData(0));
            assertEquals(10, view.table.getItems().getFirst().rank().orElseThrow());
            assertTrue(field(view, "current", Label.class).getText().contains("No survivor age has been assumed"));
            assertTrue(field(view, "detail", Label.class).getText().contains("Evaluated probability coverage"));
            var stage = field(dialog, "stage", Stage.class);
            var top = field(dialog, "modes", TabPane.class);
            top.getSelectionModel().selectLast();
            var inner = (TabPane) ((javafx.scene.layout.VBox) top.getSelectionModel().getSelectedItem().getContent()).getChildren().getFirst();
            inner.getSelectionModel().selectLast();
            assertSame(view, inner.getSelectionModel().getSelectedItem().getContent());
            var weightedScroll = (ScrollPane) ((BorderPane) stage.getScene().getRoot()).getCenter();
            assertTrue(weightedScroll.isFitToWidth());
            assertFalse(weightedScroll.isFitToHeight());
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
            for (String metric : List.of("Expected Investable Assets at Second Death", "Expected After-Tax Heir Value",
                    "ranking objective; valuation-date dollars", "Nominal future-dollar expectations")) {
                assertTrue(current.contains(metric));
                assertTrue(field(view, "highest", Label.class).getText().contains(metric));
                assertTrue(field(view, "detail", Label.class).getText().contains(metric));
            }
            assertTrue(current.contains("PV Difference vs Current: $0"));
            assertTrue(current.contains("Tested strategies tied at current expected PV: 1"));
            assertFalse(current.contains("unavailable"));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void deterministicAssetColumnsUseFinalProjectionValuesAndNumericSorting() throws Exception {
        onFx(dialog -> {
            installResults(dialog);
            var render = dialog.getClass().getDeclaredMethod("renderExhaustive", ExhaustiveIntegratedSearchPresentation.class);
            render.setAccessible(true);
            render.invoke(dialog, exhaustive);
            var renderQuick = dialog.getClass().getDeclaredMethod("renderIntegrated", IntegratedSocialSecurityComparisonPresentation.class);
            renderQuick.setAccessible(true);
            renderQuick.invoke(dialog, quick);
            var table = (TableView<ExhaustiveIntegratedSearchPresentation.Group>) field(dialog, "exhaustiveTable", TableView.class);
            for (String name : List.of("Ending Investable Assets", "After-Tax Heir Value")) {
                var column = (TableColumn<ExhaustiveIntegratedSearchPresentation.Group, BigDecimal>) table.getColumns()
                        .stream().filter(c -> name.equals(c.getText())).findFirst().orElseThrow();
                var metrics = table.getItems().getFirst().representative().metrics().orElseThrow();
                assertEquals(name.startsWith("Ending") ? metrics.endingInvestableAssets() : metrics.afterTaxEstate(),
                        column.getCellData(0));
                assertTrue(column.isSortable());
                assertTrue(column.getComparator().compare(new BigDecimal("9"), new BigDecimal("100")) < 0);
                table.getSortOrder().setAll(column);
                table.sort();
            }
            assertEquals(exhaustive.result().rankedSuccessfulEntries().getFirst().afterTaxEstateRank(),
                    table.getItems().getFirst().representative().afterTaxEstateRank());
            var quickTable = (TableView<IntegratedSocialSecurityComparisonPresentation.Row>) field(dialog, "integratedTable", TableView.class);
            var finalYear = quick.rows().getFirst().entry().integratedResult().orElseThrow().projection().getLastYear();
            assertEquals(plan.getPlanningAssumptions().getProjectionStartDate().getYear()
                    + plan.getPlanningAssumptions().getProjectionLengthYears() - 1, finalYear.getCalendarYear());
            for (String name : List.of("Ending Investable Assets", "After-Tax Heir Value")) {
                var column = (TableColumn<IntegratedSocialSecurityComparisonPresentation.Row, BigDecimal>) quickTable.getColumns()
                        .stream().filter(c -> name.equals(c.getText())).findFirst().orElseThrow();
                assertEquals(name.startsWith("Ending") ? finalYear.getEndingInvestableAssets() : finalYear.getAfterTaxEstateValue(),
                        column.getCellData(0));
                assertTrue(column.getComparator().compare(new BigDecimal("9"), new BigDecimal("100")) < 0);
            }
            assertTrue(text(field(dialog, "exhaustiveSummary", javafx.scene.layout.VBox.class)).contains("Current Ending Investable Assets"));
            assertTrue(field(dialog, "exhaustiveDetails", TextArea.class).getText().contains("After-Tax Heir Value"));
            assertTrue(field(dialog, "integratedDetails", TextArea.class).getText().contains("After-Tax Heir Value"));
        });
    }

    @Test
    void weightedMetricsKeepNominalAndPvValuesDistinctInTableCardsAndDetails() throws Exception {
        onFx(dialog -> {
            var original = LongevityWeightedIntegratedPresentationTest.entry(1, "800.003", 1);
            var assets = new BigDecimal("1000.005");
            var heir = new BigDecimal("900.004");
            var pv = new BigDecimal("800.003");
            var entry = new LongevityWeightedIntegratedStrategyComparisonEntry(original.inputOrder(), original.strategy(),
                    java.util.Optional.of(new LongevityWeightedStrategyAggregate(pv, heir, assets,
                            new BigDecimal("700"), new BigDecimal("1100"), BigDecimal.ONE, 2, 1)),
                    original.failure(), original.scenarioDetails(), original.rank(),
                    java.util.Optional.of(new BigDecimal("2.003")), java.util.Optional.of(new BigDecimal("25.004")));
            var view = field(dialog, "weightedView", LongevityWeightedIntegratedView.class);
            view.render(LongevityWeightedIntegratedPresentationTest.model(List.of(entry), java.util.Optional.of(entry)), "Current");
            assertEquals(assets, view.table.getColumns().get(5).getCellData(0));
            assertEquals(heir, view.table.getColumns().get(6).getCellData(0));
            assertEquals(pv, view.table.getColumns().get(7).getCellData(0));
            assertEquals(new BigDecimal("2.003"), view.table.getColumns().get(8).getCellData(0));
            for (int index : List.of(5, 6, 7)) {
                var header = (Label) view.table.getColumns().get(index).getGraphic();
                assertTrue(header.getTooltip().getText().contains(index == 7 ? "ranking objective" : "Nominal future-dollar"));
            }
            for (String fieldName : List.of("highest", "current", "detail")) {
                var value = field(view, fieldName, Label.class).getText();
                assertTrue(value.contains("Expected Investable Assets at Second Death: "
                        + com.daviddunn.retirementplanner.ui.util.UIFormatters.money(assets)));
                assertTrue(value.contains("Expected After-Tax Heir Value: "
                        + com.daviddunn.retirementplanner.ui.util.UIFormatters.money(heir)));
                assertTrue(value.contains("Expected PV After-Tax Estate (ranking objective; valuation-date dollars): "
                        + com.daviddunn.retirementplanner.ui.util.UIFormatters.money(pv)));
            }
            assertTrue(field(view, "detail", Label.class).getText().contains("PV Difference vs Current: +"
                    + com.daviddunn.retirementplanner.ui.util.UIFormatters.money(new BigDecimal("2.003"))));
            assertTrue(field(view, "detail", Label.class).getText().contains("Minimum nominal scenario estate"));
            assertEquals(LongevityWeightedComparisonObjective.EXPECTED_PV_AFTER_TAX_ESTATE,
                    LongevityWeightedIntegratedPresentationTest.model(List.of(entry), java.util.Optional.empty()).result().objective());
            assertEquals(1, entry.rank().orElseThrow());
        });
    }

    private static Person person(String name, AccountOwnership owner, LocalDate birth) {
        Person person = new Person(name, "Planner", birth);
        person.setMortalityCategory(owner == AccountOwnership.PRIMARY ? MortalityCategory.MALE : MortalityCategory.FEMALE);
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
