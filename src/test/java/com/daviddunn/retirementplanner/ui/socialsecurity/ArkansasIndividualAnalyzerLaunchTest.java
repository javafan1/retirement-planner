package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.app.socialsecurity.results.IndividualDeterministicSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisProvenance;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.IndividualLongevityScenarios;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityDistribution;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityDistributionResult;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityProbability;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityTables;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.single;
import static com.daviddunn.retirementplanner.ui.socialsecurity.ArkansasSocialSecurityLaunchTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasIndividualAnalyzerLaunchTest {
    @BeforeAll static void startFx() throws Exception { ArkansasSocialSecurityLaunchTest.startFx(); }

    @Test void actualIndividualLaunchersCaptureOverrideAndLifetimeWithoutRewritingSource(@TempDir Path folder) throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("results")));
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var opening = LocalDate.of(2026, 1, 1);
        var plan = selectArkansas(single(LocalDate.of(1957, 5, 1), opening, 4, TAXABLE_FIRST, "950000"));
        var primary = plan.getHousehold().getPrimaryPerson();
        primary.setMortalityCategory(MortalityCategory.MALE);
        var pension = new Pension("Employer pension", PRIMARY, opening, null, new BigDecimal("8000"), new BigDecimal("0.02"));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        primary.addIncomeSource(pension);
        primary.addIncomeSource(new SocialSecurityIncome("Social Security", PRIMARY, primary.getBirthDate().plusYears(67),
                null, new BigDecimal("2600"), 67, BigDecimal.ZERO, opening.getYear()));
        fx(() -> controller.newPlan(plan));
        try {
            var ticket = fx(() -> controller.beginArkansasProjection(ignored -> { }));
            ticket.completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertEquals(ApplicationController.ControlledProjectionStatus.ACCEPTED, fx(() -> controller.finishControlledArkansasProjection(ticket)));
            var original = fx(controller::getAcceptedProjectionSnapshot);
            var dialog = fx(() -> new SocialSecurityStrategyAnalyzerDialog(null, controller));
            try {
                var view = fx(() -> (SinglePersonIntegratedView) field(dialog, "singlePersonView"));
                fx(() -> { view.start(); return null; });
                var defaultRun = await(store, original.owner(), AnalysisKind.INDIVIDUAL_DETERMINISTIC, view.status, null);
                fx(() -> { view.deathYear.setText("2030"); view.start(); return null; });
                var override = await(store, original.owner(), AnalysisKind.INDIVIDUAL_DETERMINISTIC, view.status, defaultRun.runId());
                var search = assertInstanceOf(IndividualDeterministicSnapshot.class, override.payload());
                assertEquals(2030, search.deathYear());
                var proof = override.arkansasExecution().executionEvidence().deterministic();
                assertEquals(10, proof.candidates().size());
                assertTrue(proof.candidates().stream().allMatch(candidate -> candidate.primaryDeathOverride() == 2030));
                assertNull(defaultRun.arkansasExecution().executionEvidence().deterministic().candidates().getFirst().primaryDeathOverride());
                assertEquals(original.id(), override.arkansasExecution().dependencies().getFirst().projectionId());
                var codec = new AnalysisSnapshotCodec();
                assertEquals(override.presentationSnapshot(codec), codec.decode(codec.encode(override.presentationSnapshot(codec))));
                fx(() -> { view.deathYear.setText("2029"); view.start(); return null; });
                long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!fx(view.status::getText).contains("unavailable for 2029") && System.nanoTime() < timeout) Thread.sleep(25);
                assertTrue(fx(view.status::getText).contains("unavailable for 2029"));
                assertEquals(override.runId(), store.inMemory(new AnalysisResultStore.Slot(original.owner(),
                        AnalysisKind.INDIVIDUAL_DETERMINISTIC)).orElseThrow().runId());

                fx(() -> { view.weightedView.start(); return null; });
                var longevity = await(store, original.owner(), AnalysisKind.INDIVIDUAL_WEIGHTED, view.weightedView.status, null);
                var mortality = longevity.arkansasExecution().executionEvidence().individualMortality();
                assertEquals(9, longevity.payload().individual().entries().size());
                assertTrue(mortality.scenarios().stream().anyMatch(scenario -> scenario.annualDigest() != null));
                var snapshot = longevity.presentationSnapshot(codec);
                assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
                assertEquals(original, fx(controller::getAcceptedProjectionSnapshot));
                assertTrue(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(snapshot)));

                var mortalityBasis = IndividualLongevityScenarios.create(plan.getHousehold(),
                        SocialSecurityMortalityAdjustment.standard(), opening, SocialSecurityMortalityTables.ssaPeriod2022()).individual();
                var openingOnly = new IndividualLongevityScenarios(new SocialSecurityMortalityDistributionResult(
                        new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(69, BigDecimal.ONE))),
                        mortalityBasis.tableMetadata(), mortalityBasis.request(), 68, 69, mortalityBasis.partialYearConvention()));
                var openingRun = ArkansasAnalysisProvenance.runIndividualMortalityProductionV2(original, UUID.randomUUID(),
                        longevity.inputFingerprint(), plan, openingOnly, opening, BigDecimal.ZERO,
                        AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                assertEquals(0, openingRun.result().projectionCount());
                assertEquals(9, openingRun.result().entries().size());
                assertNotNull(openingRun.evidence());
                fx(() -> {
                    plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("980000"));
                    controller.markModified();
                    return null;
                });
                assertFalse(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(snapshot)));
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

    private static com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion await(
            AnalysisResultStore store, java.util.UUID owner, AnalysisKind kind, Label status, java.util.UUID prior) throws Exception {
        var slot = new AnalysisResultStore.Slot(owner, kind);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        while (System.nanoTime() < deadline) {
            var found = store.inMemory(slot).filter(result -> !result.runId().equals(prior));
            if (found.isPresent()) return found.orElseThrow();
            var feedback = fx(status::getText);
            assertFalse(feedback.contains("failed") || feedback.contains("unavailable"), feedback);
            Thread.sleep(25);
        }
        return fail("No accepted " + kind + " result. " + fx(status::getText));
    }
}
