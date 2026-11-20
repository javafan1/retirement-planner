package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.couple;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.single;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.fx;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasMonteCarloLaunchTest {
    @BeforeAll static void startFx() throws Exception { MonteCarloViewTest.startFx(); }

    @Test void fixedLongevityAndPairedLaunchesRetainObservedDependencies(@TempDir Path folder) throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("results")));
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var opening = LocalDate.of(2026, 1, 1);
        var plan = selectArkansas(single(LocalDate.of(1957, 1, 1), opening, 4, TAXABLE_FIRST, "900000"));
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(
                com.daviddunn.retirementplanner.domain.model.MortalityCategory.MALE);
        var pension = new Pension("Employer pension", PRIMARY, opening, null, new BigDecimal("8000"), BigDecimal.ZERO);
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        fx(() -> controller.newPlan(plan));
        try {
            acceptProjection(controller);
            var baseline = fx(controller::getAcceptedProjectionSnapshot);
            fx(() -> {
                controller.saveCurrentAsBaseline("Original Arkansas inputs");
                plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1000000"));
                controller.markModified();
                return null;
            });
            acceptProjection(controller);
            var current = fx(controller::getAcceptedProjectionSnapshot);

            var standalone = fx(() -> new MonteCarloAnalysisView(controller));
            try {
                fx(() -> {
                    new Scene(standalone);
                    count(standalone, "#mc-simulations", 2);
                    ((Button) standalone.lookup("#mc-run")).fire();
                    return null;
                });
                var fixed = awaitResult(controller, AnalysisKind.INDIVIDUAL_MONTE_CARLO_FIXED, standalone, "#mc-status");
                assertEquals(current.id(), fixed.arkansasExecution().dependencies().getFirst().projectionId());
                assertTrue(fixed.arkansasExecution().dependencies().getFirst().completedRuns() >= 2);
                assertTrue(fx(() -> controller.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(fixed.runId())));
                fx(() -> {
                    @SuppressWarnings("unchecked") var mode = (ComboBox<MonteCarloMode>) standalone.lookup("#mc-mode");
                    mode.setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                    count(standalone, "#mc-simulations", 2);
                    ((Button) standalone.lookup("#mc-run")).fire();
                    return null;
                });
                var longevity = awaitResult(controller, AnalysisKind.INDIVIDUAL_MONTE_CARLO_LONGEVITY, standalone, "#mc-status");
                assertEquals(current.inputDigest(), longevity.arkansasExecution().dependencies().getFirst().inputDigest());
            } finally {
                fx(() -> { standalone.close(); return null; });
            }

            var pair = fx(() -> new MonteCarloStrategyComparisonView(controller));
            try {
                fx(() -> {
                    new Scene(pair);
                    count(pair, "#mcc-count", 2);
                    ((Button) pair.lookup("#mcc-run")).fire();
                    return null;
                });
                var paired = awaitResult(controller, AnalysisKind.INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED, pair, "#mcc-status");
                assertEquals(java.util.List.of(current.id(), baseline.id()), paired.arkansasExecution().dependencies()
                        .stream().map(dependency -> dependency.projectionId()).toList());
                assertEquals(baseline.id(), fx(() -> controller.getCurrentPlan().getProjectionState().associations().stream()
                        .filter(ref -> ref.runId().equals(paired.runId())).findFirst().orElseThrow().alternativeProjectionId()));
            } finally {
                fx(() -> { pair.close(); return null; });
            }
        } finally {
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void equalFrozenInputsPreserveOrderedDependenciesWithSharedOrDistinctUuids(boolean shared, @TempDir Path folder) throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("results")));
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var opening = LocalDate.of(2026, 1, 1);
        var plan = selectArkansas(single(LocalDate.of(1957, 1, 1), opening, 4, TAXABLE_FIRST, "900000"));
        var pension = new Pension("Employer pension", PRIMARY, opening, null, new BigDecimal("8000"), BigDecimal.ZERO);
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        fx(() -> controller.newPlan(plan));
        try {
            acceptProjection(controller);
            var baseline = fx(controller::getAcceptedProjectionSnapshot);
            fx(() -> { controller.saveCurrentAsBaseline("Equal inputs"); return null; });
            if (!shared) acceptProjection(controller);
            var current = fx(controller::getAcceptedProjectionSnapshot);
            assertEquals(current.inputDigest(), baseline.inputDigest());
            assertEquals(shared, current.id().equals(baseline.id()));
            var view = fx(() -> new MonteCarloStrategyComparisonView(controller));
            try {
                fx(() -> {
                    new Scene(view);
                    count(view, "#mcc-count", 2);
                    ((Button) view.lookup("#mcc-run")).fire();
                    return null;
                });
                var completion = awaitResult(controller, AnalysisKind.INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED, view, "#mcc-status");
                var proof = completion.arkansasExecution().executionEvidence().paired();
                assertEquals(current.id(), proof.current().source().acceptedId());
                assertEquals(baseline.id(), proof.baseline().source().acceptedId());
                assertNotEquals(proof.current().source().role(), proof.baseline().source().role());
                var association = fx(() -> controller.getAnalyzerAssociations(current.id()).getFirst());
                assertEquals(shared ? null : baseline.id(), association.alternativeProjectionId());
                fx(() -> { controller.saveAs(folder.resolve("equal.json")); return null; });
                assertEquals(completion.arkansasExecution(), loadAssociated(controller, current.id(), completion.runId()).arkansasExecution());
            } finally {
                fx(() -> { view.close(); return null; });
            }
        } finally {
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pairedLongevityBindsBothMixedOrientationsAndTemporarySurvivorElections(boolean arkansasCurrent, @TempDir Path folder) throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("results")));
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var opening = LocalDate.of(2030, 1, 1);
        var plan = couple(opening, 5, DeathScenario.BOTH_SURVIVE);
        var a = arkansasCurrent ? StateTaxJurisdiction.AR : StateTaxJurisdiction.TX;
        var b = arkansasCurrent ? StateTaxJurisdiction.TX : StateTaxJurisdiction.AR;
        select(plan, b, opening);
        for (var personOwner : List.of(PRIMARY, SPOUSE)) {
            var person = plan.getHousehold().peopleByOwner().get(personOwner);
            person.setMortalityCategory(personOwner == PRIMARY ? MortalityCategory.MALE : MortalityCategory.FEMALE);
            var pension = new Pension(personOwner + " pension", personOwner, opening, null,
                    new BigDecimal("8000"), BigDecimal.ZERO, new BigDecimal("6500"));
            pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            person.addIncomeSource(pension);
            person.addIncomeSource(new SocialSecurityIncome("Social Security", personOwner, person.getBirthDate().plusYears(67),
                    null, new BigDecimal("2400"), 67, BigDecimal.ZERO, opening.getYear()));
        }
        fx(() -> controller.newPlan(plan));
        try {
            if (b == StateTaxJurisdiction.AR) acceptProjection(controller);
            else fx(controller::getCurrentProjection);
            var baseline = fx(controller::getAcceptedProjectionSnapshot);
            fx(() -> {
                controller.saveCurrentAsBaseline("Original Baseline");
                select(plan, a, opening);
                controller.markModified();
                controller.invalidateProjection();
                return null;
            });
            if (a == StateTaxJurisdiction.AR) acceptProjection(controller);
            else fx(controller::getCurrentProjection);
            var current = fx(controller::getAcceptedProjectionSnapshot);
            var view = fx(() -> new MonteCarloStrategyComparisonView(controller));
            try {
                fx(() -> {
                    new Scene(view);
                    @SuppressWarnings("unchecked") var mode = (ComboBox<MonteCarloMode>) view.lookup("#mcc-mode");
                    mode.setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                    ((TextField) view.lookup("#mcc-survivor-a")).setText("62");
                    ((TextField) view.lookup("#mcc-survivor-b")).setText("67");
                    count(view, "#mcc-count", 2);
                    ((Button) view.lookup("#mcc-run")).fire();
                    return null;
                });
                var pair = awaitResult(controller, AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_LONGEVITY, view, "#mcc-status");
                var evidence = pair.arkansasExecution().executionEvidence().paired();
                assertEquals(current.id(), evidence.current().source().acceptedId());
                assertEquals(baseline.id(), evidence.baseline().source().acceptedId());
                assertEquals(a, evidence.current().source().jurisdiction());
                assertEquals(b, evidence.baseline().source().jurisdiction());
                assertEquals(62, evidence.current().source().survivorOverride());
                assertEquals(67, evidence.baseline().source().survivorOverride());
                assertNotEquals(current.inputDigest(), evidence.current().source().effectiveInput());
                assertNotEquals(baseline.inputDigest(), evidence.baseline().source().effectiveInput());
                var codec = new AnalysisSnapshotCodec();
                var accepted = pair.presentationSnapshot(codec);
                assertEquals(accepted, codec.decode(codec.encode(accepted)));
                assertTrue(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(accepted)));
                fx(() -> { controller.saveAs(folder.resolve("original.json")); return null; });
                controller.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
                assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
                var saved = loadAssociated(controller, current.id(), pair.runId());
                assertEquals(accepted.arkansasExecution(), saved.arkansasExecution());
                assertTrue(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(saved)));
                swapAndSave(controller);
                assertEquals(baseline.id(), fx(controller::getAcceptedProjectionSnapshot).id());
                assertFalse(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(saved)));
                var reversed = loadAssociated(controller, current.id(), pair.runId());
                assertEquals(saved.payload(), reversed.payload());
                assertEquals(saved.arkansasExecution(), reversed.arkansasExecution());
                swapAndSave(controller);
                assertEquals(current.id(), fx(controller::getAcceptedProjectionSnapshot).id());
                var copy = folder.resolve("copied.json");
                fx(() -> { controller.saveAs(copy); return null; });
                controller.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
                fx(() -> controller.open(copy));
                controller.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
                var reloaded = loadAssociated(controller, current.id(), pair.runId());
                assertEquals(saved.payload(), reloaded.payload());
                assertEquals(saved.arkansasExecution(), reloaded.arkansasExecution());
                assertTrue(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(reloaded)));
            } finally {
                fx(() -> { view.close(); return null; });
            }
        } finally {
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    private static void select(RetirementPlan plan, StateTaxJurisdiction state, LocalDate opening) {
        var inputs = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(inputs.getEconomicAssumptions(), inputs.getTaxAssumptions(),
                inputs.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 65),
                5, opening, StateTaxAssumptions.simplified(state)));
    }

    private static void swapAndSave(ApplicationController controller) throws Exception {
        var operation = fx(controller::prepareProjectionSwap).toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(fx(() -> controller.commitProjectionSwap(operation)));
        fx(() -> { controller.save(); return null; });
    }

    private static com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot loadAssociated(
            ApplicationController controller, java.util.UUID projection, java.util.UUID run) throws Exception {
        var association = fx(() -> controller.getAnalyzerAssociations(projection).stream()
                .filter(reference -> reference.runId().equals(run)).findFirst().orElseThrow());
        return fx(() -> controller.loadAssociatedAnalyzer(association)).toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static void acceptProjection(ApplicationController controller) throws Exception {
        var ticket = fx(() -> controller.beginArkansasProjection(ignored -> assertFalse(javafx.application.Platform.isFxApplicationThread())));
        ticket.completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> controller.finishControlledArkansasProjection(ticket));
        assertEquals(ApplicationController.ControlledProjectionStatus.ACCEPTED,
                fx(() -> controller.finishControlledArkansasProjection(ticket)));
    }

    private static void count(Pane view, String id, int value) {
        @SuppressWarnings("unchecked") var count = (ComboBox<Integer>) view.lookup(id);
        assertNotNull(count, id);
        count.setValue(value);
    }

    private static com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion awaitResult(
            ApplicationController controller, AnalysisKind kind, Pane view, String statusId) throws Exception {
        var slot = new AnalysisResultStore.Slot(controller.getAnalysisSessionId(), kind);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        do {
            var result = fx(() -> controller.getAnalysisResults().inMemory(slot));
            if (result.isPresent()) return result.orElseThrow();
            String status = fx(() -> ((Label) view.lookup(statusId)).getText());
            assertFalse(status.contains("failed"), status);
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        String validation = statusId.replace("status", "validation");
        return fail("Result not accepted. " + fx(() -> ((Label) view.lookup(statusId)).getText()
                + " " + ((Label) view.lookup(validation)).getText()));
    }
}
