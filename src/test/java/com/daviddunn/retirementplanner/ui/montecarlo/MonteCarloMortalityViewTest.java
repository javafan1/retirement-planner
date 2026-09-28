package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.*;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloMortalityViewTest {
    @BeforeAll
    static void init() throws Exception {
        startFx();
    }

    @SuppressWarnings("unchecked")
    static ComboBox<MonteCarloMode> mode(MonteCarloAnalysisView view) {
        return (ComboBox<MonteCarloMode>) view.lookup("#mc-mode");
    }

    static String text(MonteCarloAnalysisView view, String id) {
        return ((Label) view.lookup(id)).getText();
    }

    @Test
    void defaultsSharedFactorsCategoriesAndMissingAnalysisAgeValidateBeforeSubmitting() throws Exception {
        var plan = MonteCarloMortalityUiFixtures.plan();
        MonteCarloMortalityUiFixtures.survivorPolicy(plan, null);
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var before = json.writeValueAsString(plan);
        fx(() -> {
            var queue = new ArrayDeque<Runnable>();
            var controller = new Controller(plan);
            controller.setLongevitySessionSettings(new LongevitySessionSettings(LocalDate.of(2030, 1, 1),
                    SocialSecurityMortalityAdjustment.of(new BigDecimal("0.75")), SocialSecurityMortalityAdjustment.standard()));
            var view = new MonteCarloAnalysisView(controller, queue::add, (p, s, r, u, c) -> null,
                    (p, r, u, c) -> { throw new AssertionError("Validation must precede work"); });
            try {
                new Scene(view);
                assertEquals(MonteCarloMode.FIXED_LIFESPAN, mode(view).getValue());
                assertTrue(queue.isEmpty());
                assertEquals(MonteCarloSession.State.IDLE, view.session().state());
                assertFalse(view.lookup("#mc-longevity-inputs").isManaged());
                assertEquals("0.75", field(view, "#mc-primary-adjustment").getText());
                mode(view).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                assertTrue(queue.isEmpty());
                assertTrue(view.lookup("#mc-longevity-inputs").isManaged());
                assertFalse(field(view, "#mc-primary-adjustment").isDisabled());
                assertTrue(text(view, "#mc-mortality-context").contains("Alex mortality: Male"));
                assertTrue(text(view, "#mc-mortality-context").contains("Sam mortality: Female"));
                assertTrue(field(view, "#mc-primary-adjustment").getTooltip().getText().contains("underlying mortality hazard"));
                assertEquals("", field(view, "#mc-survivor-age").getText(), "No invented default");
                button(view, "#mc-run").fire();
                assertTrue(queue.isEmpty());
                assertEquals("Select a survivor Social Security claiming age for longevity-adjusted Monte Carlo.", text(view, "#mc-validation"));
                field(view, "#mc-survivor-age").setText("67");
                field(view, "#mc-primary-adjustment").setText("0.49");
                button(view, "#mc-run").fire();
                assertTrue(queue.isEmpty());
                assertTrue(text(view, "#mc-validation").contains("0.50 to 3.00"));
                field(view, "#mc-primary-adjustment").setText("1.25");
                assertEquals(LocalDate.of(2030, 1, 1), controller.getLongevitySessionSettings().conditioningDate());
                assertEquals(new BigDecimal("1.25"), controller.getLongevitySessionSettings().primaryAdjustment().factor());
                assertEquals(before, json.writeValueAsString(plan));
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void modeRoundTripUsesSeparateRunPathsAndRestoresFixedPresentation() throws Exception {
        var fixed = MonteCarloUiFixtures.run("1000");
        var mortality = MonteCarloMortalityUiFixtures.run(3, false);
        var fixedCalls = new java.util.concurrent.atomic.AtomicInteger();
        var mortalityCalls = new java.util.concurrent.atomic.AtomicInteger();
        var queue = new ArrayDeque<Runnable>();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()), queue::add,
                    (p, s, r, u, c) -> { fixedCalls.incrementAndGet(); return fixed; },
                    (p, r, u, c) -> { mortalityCalls.incrementAndGet(); return mortality; });
            new Scene(created);
            assertTrue(queue.isEmpty());
            return created;
        });
        try {
            for (var selection : List.of(MonteCarloMode.FIXED_LIFESPAN, MonteCarloMode.LONGEVITY_ADJUSTED,
                    MonteCarloMode.FIXED_LIFESPAN)) {
                fx(() -> {
                    var prior = view.session().result();
                    mode(view).setValue(selection);
                    assertTrue(queue.isEmpty(), "Selecting a mode must not run analysis");
                    if (prior != null) {
                        assertTrue(view.session().stale());
                        assertSame(prior, view.session().result());
                        assertEquals(prior.mode() == MonteCarloMode.FIXED_LIFESPAN
                                ? "FUNDING PROBABILITY" : "LIFETIME FUNDING PROBABILITY", text(view, "#mc-funding-title"));
                    }
                    button(view, "#mc-run").fire();
                    return null;
                });
                queue.remove().run();
                waitForFx();
                fx(() -> {
                    boolean isFixed = selection == MonteCarloMode.FIXED_LIFESPAN;
                    assertEquals(selection, view.session().result().mode());
                    assertEquals(isFixed, view.lookup("#mc-outcomes").isManaged());
                    assertEquals(!isFixed, view.lookup("#mc-lifetime-outcomes").isManaged());
                    assertEquals(isFixed, view.lookup(".mc-reference-legend").isManaged());
                    assertFalse(view.session().stale());
                    if (isFixed) {
                        assertEquals(MonteCarloPresentation.CONDITIONAL_NOTICE, text(view, "#mc-conditional-notice"));
                        var details = (TitledPane) view.lookup("#mc-analysis-details");
                        assertTrue(((Label) details.getContent()).getText().contains("complete planning horizon")
                                || ((Label) details.getContent()).getText().contains("completed the planning horizon"));
                    }
                    return null;
                });
            }
            assertEquals(2, fixedCalls.get());
            assertEquals(1, mortalityCalls.get());
        } finally {
            fx(() -> { view.close(); return null; });
        }
    }

    @Test
    void missingPersonCategoryBlocksMortalityButDoesNotAddCategoryControls() throws Exception {
        fx(() -> {
            var plan = com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFixtures.household();
            MonteCarloMortalityUiFixtures.survivorPolicy(plan, 67);
            plan.getHousehold().getPrimaryPerson().setMortalityCategory(
                    com.daviddunn.retirementplanner.domain.model.MortalityCategory.MALE);
            var queue = new ArrayDeque<Runnable>();
            var view = new MonteCarloAnalysisView(new Controller(plan), queue::add, (p, s, r, u, c) -> null);
            try {
                new Scene(view);
                mode(view).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                button(view, "#mc-run").fire();
                assertTrue(queue.isEmpty());
                assertTrue(text(view, "#mc-validation").contains("Person information"));
                assertTrue(text(view, "#mc-mortality-context").contains("Sam mortality: Not set"));
                assertEquals(3, view.lookupAll(".combo-box").size(), "Only analysis mode, inflation mode and simulations are selectable lists");
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void mortalityRunsRealAnalyzerOffFxWithCapturedInputsAndProgress() throws Exception {
        var plan = MonteCarloMortalityUiFixtures.plan();
        MonteCarloMortalityUiFixtures.survivorPolicy(plan, null);
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = json.writeValueAsString(plan);
        var done = new CompletableFuture<MonteCarloRun>();
        var executor = Executors.newSingleThreadExecutor();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(plan), executor,
                    (p, s, r, u, c) -> { throw new AssertionError("Must not execute fixed analysis"); },
                    (snapshot, request, progress, cancellation) -> {
                        assertFalse(Platform.isFxApplicationThread());
                        assertNotSame(plan, snapshot);
                        assertNull(snapshot.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
                        assertEquals(com.daviddunn.retirementplanner.domain.model.DeathScenario.BOTH_SURVIVE,
                                snapshot.getPlanningAssumptions().getDeathScenarioAssumptions().getDeathScenario());
                        assertEquals(java.util.Optional.of(60), request.survivorClaimingAge());
                        assertEquals(LocalDate.of(2027, 1, 1), request.longevityAssumptions().mortalityBaseDate());
                        assertEquals(SocialSecurityMortalityCategory.MALE, request.longevityAssumptions().primaryCategory());
                        return new MonteCarloRunService().runMortality(snapshot, request, progress, cancellation);
                    });
            new Scene(created);
            ((ComboBox<Integer>) created.lookup("#mc-simulations")).setValue(3);
            mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            assertEquals("", field(created, "#mc-survivor-age").getText());
            field(created, "#mc-survivor-age").setText("60");
            ((Label) created.lookup("#mc-status")).textProperty().addListener((o, a, b) -> {
                if (created.session().state() == MonteCarloSession.State.COMPLETED) done.complete(created.session().result());
                if (created.session().state() == MonteCarloSession.State.FAILED) done.completeExceptionally(created.session().failure());
            });
            button(created, "#mc-run").fire();
            assertTrue(mode(created).isDisabled());
            assertTrue(field(created, "#mc-primary-adjustment").isDisabled());
            return created;
        });
        try {
            var result = done.get(30, TimeUnit.SECONDS);
            assertEquals(MonteCarloMode.LONGEVITY_ADJUSTED, result.mode());
            assertEquals(3, result.mortalityResult().requestedSimulationCount());
            fx(() -> {
                assertEquals(3, view.session().progress().completedWork());
                assertEquals("LIFETIME FUNDING PROBABILITY", text(view, "#mc-funding-title"));
                assertFalse(mode(view).isDisabled());
                return null;
            });
            assertEquals(before, json.writeValueAsString(plan));
        } finally {
            fx(() -> { view.close(); return null; });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mode", "primary-adjustment", "spouse-adjustment", "survivor-age", "seed", "return", "volatility", "simulations", "plan"})
    void everyRelevantInputMarksFrozenResultStaleWithoutRerunning(String change) throws Exception {
        var completed = MonteCarloMortalityUiFixtures.run(3, false);
        var queue = new ArrayDeque<Runnable>();
        var controller = new Controller(MonteCarloMortalityUiFixtures.plan());
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(controller, queue::add, (p, s, r, u, c) -> null,
                    (p, r, u, c) -> completed);
            new Scene(created);
            mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            button(created, "#mc-run").fire();
            return created;
        });
        queue.remove().run();
        waitForFx();
        fx(() -> {
            try {
                switch (change) {
                    case "mode" -> mode(view).setValue(MonteCarloMode.FIXED_LIFESPAN);
                    case "simulations" -> ((ComboBox<Integer>) view.lookup("#mc-simulations")).setValue(1000);
                    case "plan" -> controller.markModified();
                    default -> field(view, "#mc-" + change).setText("1.5");
                }
                assertTrue(view.session().stale());
                assertSame(completed, view.session().result());
                assertEquals("LIFETIME FUNDING PROBABILITY", text(view, "#mc-funding-title"));
                assertTrue(queue.isEmpty());
                assertThrows(IllegalStateException.class, completed::result);
            } finally {
                view.close();
            }
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "error", "invalidate", "close"})
    void mortalityCancellationFailureAndLateCompletionNeverPublishPartialResults(String action) throws Exception {
        var completed = MonteCarloMortalityUiFixtures.run(3, false);
        var queue = new ArrayDeque<Runnable>();
        var controller = new Controller(MonteCarloMortalityUiFixtures.plan());
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var before = json.valueToTree(controller.getCurrentPlan());
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(controller, queue::add, (p, s, r, u, c) -> null,
                    (p, r, u, c) -> {
                        if (action.equals("error")) throw new IllegalStateException("Mortality diagnostic");
                        return completed;
                    });
            new Scene(created);
            mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            field(created, "#mc-survivor-age").setText("60");
            button(created, "#mc-run").fire();
            return created;
        });
        // Complete worker work while retaining its queued UI publication, then cancel/invalidate on FX.
        fx(() -> {
            queue.remove().run();
            switch (action) {
                case "cancel" -> button(view, "#mc-cancel").fire();
                case "invalidate" -> controller.markModified();
                case "close" -> view.close();
                default -> { }
            }
            return null;
        });
        waitForFx();
        fx(() -> {
            try {
                assertNull(view.session().result());
                assertEquals(action.equals("error") ? MonteCarloSession.State.FAILED
                        : action.equals("close") ? MonteCarloSession.State.CLOSED : MonteCarloSession.State.CANCELLED,
                        view.session().state());
                if (action.equals("error")) assertTrue(text(view, "#mc-status").contains("Mortality diagnostic"));
                assertEquals(before, json.valueToTree(controller.getCurrentPlan()));
            } finally {
                view.close();
            }
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4})
    void authoritativePopulationTerminalDistributionsAndDisclosuresRender(int funded) throws Exception {
        var completed = MonteCarloMortalityUiFixtures.run(funded, false);
        var view = render(completed);
        fx(() -> {
            try {
                var result = completed.mortalityResult();
                assertEquals(funded == 0 ? "0.0%" : funded == 4 ? "100.0%" : "75.0%", text(view, "#mc-funding"));
                assertEquals(MonteCarloMortalityPresentation.FUNDING_HELP, ((Label) view.lookup("#mc-funding")).getTooltip().getText());
                assertTrue(text(view, "#mc-chart-notice").contains("still living and remain funded"));
                var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                key(chart, KeyCode.HOME);
                for (int y = 2027; y < 2050; y++) key(chart, KeyCode.RIGHT);
                var annual = result.annualResults().get(2050);
                var readout = chart.selectedDetailProperty().get();
                assertTrue(readout.contains("3 living of 4"));
                assertTrue(readout.contains(annual.completedLivingYearSampleCount() + " funded through 2050"));
                assertTrue(readout.contains("1 both alive · 1 Alex only · 1 Sam only"));
                assertEquals("Investable Assets. " + readout, chart.getAccessibleText());
                assertEquals(chart.selectedSummaryProperty().get(), text(view, "#mc-selected-year"));
                assertTrue(readout.startsWith(text(view, "#mc-selected-year")));
                var lines = text(view, "#mc-selected-year").split("\n");
                assertEquals(2, lines.length);
                assertTrue(lines[0].startsWith("2050 — Median "));
                assertTrue(lines[0].contains(" · P10 "));
                assertTrue(lines[0].contains(" · P90 "));
                if (funded == 0) {
                    assertFalse(lines[0].contains("$"));
                    assertTrue(lines[0].contains("Median unavailable"));
                }
                assertTrue(chart.getData().get(3).getData().isEmpty());
                assertFalse(view.lookup(".mc-reference-legend").isManaged());
                assertTrue(completed.fan().context().claims().isEmpty());
                assertTrue(completed.fan().context().rmdPeriods().isEmpty());
                assertTrue(completed.fan().context().rothPeriods().isEmpty());
                assertEquals(2085, completed.fan().years().getLast().calendarYear());
                var table = (TableView<?>) view.lookup("#mc-lifetime-outcomes");
                assertEquals(funded == 0 ? 0 : 7, table.getItems().size());
                if (funded == 0) {
                    assertFalse(table.isManaged());
                    assertEquals(MonteCarloMortalityPresentation.NO_TERMINALS, text(view, "#mc-conditional-notice"));
                } else {
                    assertEquals(List.of("Percentile", "Investable Assets", "Total Net Worth", "After-Tax Estate", "Lifetime Taxes"),
                            table.getColumns().stream().map(TableColumn::getText).toList());
                    assertEquals(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(result.endingInvestableAssets().orElseThrow().p50()),
                            table.getColumns().get(1).getCellData(3));
                    assertEquals(MonteCarloMortalityPresentation.terminalDates(result), text(view, "#mc-terminal-dates"));
                    assertTrue(text(view, "#mc-conditional-notice").contains("nominal future dollars"));
                }
                var details = (TitledPane) view.lookup("#mc-analysis-details");
                assertFalse(details.isExpanded());
                String disclosure = MonteCarloMortalityPresentation.details(completed).toString();
                for (String required : List.of("January 1", "independent", "retitling", "General spending inflation uses the selected mode",
                        "not inserted as zeros", "not measured in one common year", "SS claiming markers", "hidden", "Conditioning date")) {
                    assertTrue(disclosure.contains(required), required);
                }
                details.setExpanded(true);
                key(chart, KeyCode.END);
                assertSame(completed, view.session().result());
                assertFalse(view.session().stale());
            } finally {
                view.close();
            }
            return null;
        });
    }

    static MonteCarloAnalysisView render(MonteCarloRun completed) throws Exception {
        var queue = new ArrayDeque<Runnable>();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()), queue::add,
                    (p, s, r, u, c) -> { throw new AssertionError("Unexpected fixed run"); }, (p, r, u, c) -> completed);
            new Scene(created);
            mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            button(created, "#mc-run").fire();
            return created;
        });
        queue.remove().run();
        waitForFx();
        assertTrue(queue.isEmpty());
        return view;
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void persistedAgeInitializesSessionButEditsAndFrozenResultsNeverChangeThePlan(String scenario) throws Exception {
        var plan = MonteCarloMortalityUiFixtures.plan();
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                new com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions(
                        com.daviddunn.retirementplanner.domain.model.DeathScenario.valueOf(scenario), 2030, 66),
                a.getProjectionLengthYears(), a.getProjectionStartDate()));
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var before = json.valueToTree(plan);
        var queue = new ArrayDeque<Runnable>();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(plan), queue::add,
                    (p, s, r, u, c) -> { throw new AssertionError("No fixed run"); },
                    (p, r, u, c) -> {
                        assertFalse(Platform.isFxApplicationThread());
                        assertEquals(java.util.Optional.of(60), r.survivorClaimingAge());
                        assertEquals(before, json.valueToTree(p));
                        return new MonteCarloRunService().runMortality(p, r, u, c);
                    });
            new Scene(created);
            assertEquals("66", field(created, "#mc-survivor-age").getText());
            mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            field(created, "#mc-survivor-age").setText("60");
            ((ComboBox<Integer>) created.lookup("#mc-simulations")).setValue(3);
            button(created, "#mc-run").fire();
            return created;
        });
        queue.remove().run();
        waitForFx();
        fx(() -> {
            try {
                assertEquals(MonteCarloSession.State.COMPLETED, view.session().state());
                var completed = view.session().result();
                var details = (TitledPane) view.lookup("#mc-analysis-details");
                var frozenContent = details.getContent();
                String rendered = frozenContent.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                        .collect(java.util.stream.Collectors.joining("\n"));
                assertTrue(rendered.contains("Survivor Social Security claiming age: 60 (shared analysis assumption"));
                String frozen = MonteCarloMortalityPresentation.details(completed).toString();
                assertTrue(frozen.contains("Survivor Social Security claiming age: 60 (shared analysis assumption"));
                field(view, "#mc-survivor-age").setText("65");
                assertTrue(view.session().stale());
                assertSame(frozenContent, details.getContent());
                assertEquals(rendered, details.getContent().lookupAll(".label").stream().map(node -> ((Label) node).getText())
                        .collect(java.util.stream.Collectors.joining("\n")));
                assertEquals(frozen, MonteCarloMortalityPresentation.details(view.session().result()).toString());
                key((MonteCarloFanChart) view.lookup("#monte-carlo-fan"), KeyCode.END);
                details.setExpanded(true);
                mode(view).setValue(MonteCarloMode.FIXED_LIFESPAN);
                assertFalse(view.lookup("#mc-longevity-inputs").isManaged());
                mode(view).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                assertEquals("65", field(view, "#mc-survivor-age").getText());
                assertTrue(queue.isEmpty());
                assertEquals(before, json.valueToTree(plan));
            } finally {
                view.close();
            }
            return null;
        });
        assertEquals(before, json.valueToTree(plan));
    }

    @ParameterizedTest
    @ValueSource(strings = {"59", "60.5", "abc", "2147483648"})
    void invalidAnalysisAgeNeverSubmitsWork(String invalid) throws Exception {
        fx(() -> {
            var queue = new ArrayDeque<Runnable>();
            var view = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()), queue::add,
                    (p, s, r, u, c) -> null);
            try {
                new Scene(view);
                mode(view).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                field(view, "#mc-survivor-age").setText(invalid);
                button(view, "#mc-run").fire();
                assertTrue(queue.isEmpty());
                assertTrue(text(view, "#mc-validation").contains("60"));
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void emptyAnnualRangeRetainsOpeningTerminalOutcomesAndAccessibleExplanation() throws Exception {
        var view = render(MonteCarloMortalityUiFixtures.run(4, true));
        fx(() -> {
            try {
                var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                key(chart, KeyCode.END);
                assertTrue(chart.getAccessibleText().contains("all sampled lifetimes end at opening"));
                assertEquals("", chart.selectedDetailProperty().get());
                assertTrue(text(view, "#mc-terminal-dates").contains("2027-01-01"));
                assertEquals(7, ((TableView<?>) view.lookup("#mc-lifetime-outcomes")).getItems().size());
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void seededPreviewTailMatchesCachedAnnualAggregates() {
        var plan = MonteCarloMortalityUiFixtures.plan();
        MonteCarloMortalityUiFixtures.survivorPolicy(plan, null);
        var request = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityRequest(plan,
                com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings.forPlan(plan, 5000, 417, new BigDecimal("0.12")),
                LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()), 67);
        var run = new MonteCarloRunService().runMortality(plan, request,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        MonteCarloMortalityUiFixtures.assertPreviewTail(run, true);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"100,2000,true", "101,5000,false", "100,1999,false",
            "99,2000,true", "1,20,true", "1,19,false", "0,5000,false"})
    void smallSampleNoticeUsesBothInclusiveThresholds(int sample, int requested, boolean warned) throws Exception {
        fx(() -> {
            var p = com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPercentiles.of(
                    java.util.Collections.nCopies(sample, new BigDecimal("1234567")));
            var annual = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnnualResult(
                    2070, requested, sample, sample, 0, requested - sample, 0, 0, sample, p);
            var year = new MonteCarloFanModel.Year(2070, p, java.util.Optional.empty(), java.util.Optional.of(annual));
            var model = new MonteCarloFanModel(List.of(year), requested,
                    com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.empty(),
                    MonteCarloMode.LONGEVITY_ADJUSTED, "David", "Lisa");
            var text = MonteCarloMortalityPresentation.selectedYear(model, year);
            assertEquals(warned, text.contains(MonteCarloMortalityPresentation.SMALL_SAMPLE_NOTICE));
            assertEquals(2, text.split("\n").length);
            assertTrue(text.contains("0 David only"));
            assertTrue(text.contains(sample + " Lisa only"));
            assertTrue(MonteCarloPresentation.tooltip(model, year).startsWith(text));
            assertSame(p, year.percentiles());
            if (sample == 0) assertFalse(text.contains("$"));
            return null;
        });
    }

    @Test
    void selectedSummaryNavigationUsesCachedResultsWithoutSubmittingWork() throws Exception {
        var completed = MonteCarloMortalityUiFixtures.run(3, false);
        var queue = new ArrayDeque<Runnable>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()), queue::add,
                    (p, s, r, u, c) -> null, (p, r, u, c) -> { calls.incrementAndGet(); return completed; });
            new Scene(created);
            mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            button(created, "#mc-run").fire();
            return created;
        });
        queue.remove().run();
        waitForFx();
        fx(() -> {
            try {
                var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                for (var key : List.of(KeyCode.END, KeyCode.LEFT, KeyCode.HOME, KeyCode.RIGHT)) {
                    String prior = text(view, "#mc-selected-year");
                    key(chart, key);
                    String visible = text(view, "#mc-selected-year");
                    assertNotEquals(prior, visible);
                    assertTrue(chart.getAccessibleText().contains(visible));
                }
                key(chart, KeyCode.HOME);
                for (int year = 2027; year < 2050; year++) key(chart, KeyCode.RIGHT);
                assertTrue(text(view, "#mc-selected-year").contains("3 living of 4 · 2 funded through 2050"));
                assertTrue(chart.isFocusTraversable());
                assertFalse(view.lookup("#mc-selected-year").isFocusTraversable());
                assertTrue(chart.lookupAll("Rectangle").stream().noneMatch(javafx.scene.Node::isFocusTraversable));
                assertTrue(queue.isEmpty());
                assertEquals(1, calls.get());
                assertSame(completed, view.session().result());
                assertFalse(view.session().stale());
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void mortalityGuideAlignsWithMedianVerticesAtFirstMiddleAndFinalYears() throws Exception {
        var model = MonteCarloMortalityUiFixtures.run(4, false).fan();
        var stage = fx(() -> {
            var chart = new MonteCarloFanChart();
            chart.load(model);
            var created = new Stage();
            created.setScene(new Scene(new BorderPane(chart), 1500, 480));
            created.show();
            chart.requestFocus();
            return created;
        });
        try {
            for (int year : new int[]{2027, 2056, 2085}) {
                fx(() -> {
                    var chart = (MonteCarloFanChart) stage.getScene().lookup("#monte-carlo-fan");
                    key(chart, KeyCode.HOME);
                    for (int y = 2027; y < year; y++) key(chart, KeyCode.RIGHT);
                    return null;
                });
                awaitLayoutPulses(stage.getScene());
                fx(() -> {
                    assertMortalityGuide((MonteCarloFanChart) stage.getScene().lookup("#monte-carlo-fan"), year);
                    return null;
                });
            }
        } finally {
            fx(() -> { stage.close(); return null; });
        }
    }

    static void assertMortalityGuide(MonteCarloFanChart chart, int year) {
        var guide = (javafx.scene.shape.Line) chart.lookup(".mc-active-year");
        double x = chart.getXAxis().getDisplayPosition(year);
        assertEquals(x, guide.getStartX(), 0.000001);
        assertEquals(x, guide.getEndX(), 0.000001);
        assertTrue(chart.selectedDetailProperty().get().startsWith(year + " — Median "));
        var series = chart.getData().get(2);
        int index = java.util.stream.IntStream.range(0, series.getData().size())
                .filter(i -> series.getData().get(i).getXValue().intValue() == year).findFirst().orElseThrow();
        var path = (javafx.scene.shape.Path) series.getNode();
        var positions = path.getElements().stream().map(vertex -> vertex instanceof javafx.scene.shape.MoveTo move
                ? move.getX() : ((javafx.scene.shape.LineTo) vertex).getX()).distinct().toList();
        assertEquals(x, positions.get(index), 0.000001);
    }
}
