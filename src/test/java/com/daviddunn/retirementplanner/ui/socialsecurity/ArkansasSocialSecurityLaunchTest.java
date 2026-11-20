package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.app.socialsecurity.results.QuickComparisonSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.ExhaustiveSnapshot;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.couple;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasSocialSecurityLaunchTest {
    @BeforeAll static void startFx() throws Exception {
        var ready = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(ready); }
        catch (IllegalStateException running) { Platform.runLater(ready); }
        ready.get(15, TimeUnit.SECONDS);
    }

    @Test void actualQuickLauncherRetainsArkansasSurvivorDetails(@TempDir Path folder) throws Exception {
        runQuickAndOptionallyExhaustive(folder, false);
    }

    static void runQuickAndOptionallyExhaustive(Path folder, boolean runExhaustive) throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("results")));
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var opening = LocalDate.of(2030, 1, 1);
        var plan = selectArkansas(couple(opening, 4, DeathScenario.PRIMARY_DIES));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        var inputs = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(inputs.getEconomicAssumptions(), inputs.getTaxAssumptions(),
                inputs.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2031, 62),
                4, opening, inputs.getStateTaxAssumptions()));
        for (var owner : List.of(PRIMARY, SPOUSE)) {
            var person = plan.getHousehold().peopleByOwner().get(owner);
            person.setMortalityCategory(owner == PRIMARY ? MortalityCategory.MALE : MortalityCategory.FEMALE);
            var pension = new Pension(owner + " pension", owner, opening, null, new BigDecimal("7500"), ZERO, new BigDecimal("6000"));
            pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            person.addIncomeSource(pension);
            person.addIncomeSource(new SocialSecurityIncome("Social Security", owner, person.getBirthDate().plusYears(67), null,
                    new BigDecimal(owner == PRIMARY ? "3000" : "1200"), 67, ZERO, opening.getYear()));
        }
        fx(() -> controller.newPlan(plan));
        try {
            var ticket = fx(() -> controller.beginArkansasProjection(ignored -> { }));
            ticket.completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertEquals(ApplicationController.ControlledProjectionStatus.ACCEPTED, fx(() -> controller.finishControlledArkansasProjection(ticket)));
            var original = fx(controller::getAcceptedProjectionSnapshot);
            var context = new SocialSecurityStrategyAnalysisRequestFactory().create(plan,
                    SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(), ZERO, opening, opening);
            var grid = context.request().retirementGridRequest();
            var primaryMortality = new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(79, BigDecimal.ONE)));
            var spouseMortality = new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(82, BigDecimal.ONE)));
            var smallGrid = new SocialSecurityMortalityWeightedClaimingGridRequest(grid.baseStrategy(), List.of(67), List.of(67),
                    primaryMortality, spouseMortality, grid.mortalityBaseDate(), grid.presentValueBaseDate(), grid.realDiscountRate());
            var prerequisite = new SocialSecuritySurvivorClaimingOptimizationCalculator()
                    .calculate(new SocialSecuritySurvivorClaimingOptimizationRequest(smallGrid));
            var model = SocialSecurityStrategyAnalyzerPresentation.from(prerequisite);
            var dialog = fx(() -> new SocialSecurityStrategyAnalyzerDialog(null, controller));
            try {
                fx(() -> {
                    var now = Instant.now();
                    set(dialog, "presentation", model);
                    set(dialog, "socialSecurityResultCurrent", true);
                    set(dialog, "socialSecurityReport", new SocialSecurityReportSession(controller.getAnalysisSessionId(), UUID.randomUUID(),
                            plan.getHousehold().getHouseholdName(), true, context, model, now, now));
                    @SuppressWarnings("unchecked") var candidateCount = (Spinner<Integer>) field(dialog, "integratedCandidateCount");
                    candidateCount.getValueFactory().setValue(1);
                    invoke(dialog, "runIntegratedAnalysis");
                    return null;
                });
                var slot = new AnalysisResultStore.Slot(controller.getAnalysisSessionId(), AnalysisKind.COUPLE_QUICK);
                long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (store.inMemory(slot).isEmpty() && System.nanoTime() < limit) Thread.sleep(20);
                var completion = store.inMemory(slot).orElseThrow(() -> new AssertionError("Arkansas Quick did not produce an accepted result."));
                assertEquals(original.id(), completion.arkansasExecution().dependencies().getFirst().projectionId());
                assertTrue(fx(() -> controller.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(completion.runId())));
                var payload = assertInstanceOf(QuickComparisonSnapshot.class, completion.payload());
                assertNotNull(payload.entries().getFirst().detail().arkansas());
                assertTrue(payload.entries().getFirst().detail().arkansas().years().stream().flatMap(year -> year.sourceEvidence().pensions().stream())
                        .anyMatch(pension -> pension.origin() == RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN));
                if (runExhaustive) {
                    fx(() -> { invoke(dialog, "runExhaustiveSearch"); return null; });
                    var rankSlot = new AnalysisResultStore.Slot(controller.getAnalysisSessionId(), AnalysisKind.COUPLE_EXHAUSTIVE);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
                    while (store.inMemory(rankSlot).isEmpty() && System.nanoTime() < deadline) {
                        String status = fx(() -> ((Label) field(dialog, "exhaustiveStatus")).getText());
                        assertFalse(status.startsWith("Analysis failed"), status);
                        Thread.sleep(30);
                    }
                    var completed = store.inMemory(rankSlot).orElseThrow(() -> new AssertionError("Exhaustive launcher did not accept a ranking."));
                    var ranks = assertInstanceOf(ExhaustiveSnapshot.class, completed.payload());
                    assertTrue(ranks.entries().size() > 1_000);
                    assertEquals(ranks.entries().size() + 1, completed.arkansasExecution().executionEvidence().deterministic().candidates().size());
                    assertEquals(original.id(), completed.arkansasExecution().dependencies().getFirst().projectionId());
                    var codec = new AnalysisSnapshotCodec();
                    var snapshot = completed.presentationSnapshot(codec);
                    assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
                }
            } finally {
                fx(() -> { invoke(dialog, "close"); return null; });
            }
        } finally {
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    static Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static void invoke(Object target, String name) throws ReflectiveOperationException {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    static <T> T fx(Callable<T> call) throws Exception {
        var task = new FutureTask<>(call);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
