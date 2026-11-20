package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
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
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.ui.socialsecurity.ArkansasSocialSecurityLaunchTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasWeightedLauncherPerformanceTest {
    @BeforeAll static void startFx() throws Exception { ArkansasSocialSecurityLaunchTest.startFx(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualWeightedLauncherRetainsTheStandardCandidateUniverse(boolean dark, @TempDir Path folder) throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("results")));
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var start = LocalDate.of(2030, 1, 1);
        var plan = selectArkansas(couple(start, 3, DeathScenario.BOTH_SURVIVE));
        for (var owner : List.of(PRIMARY, SPOUSE)) {
            var person = plan.getHousehold().peopleByOwner().get(owner);
            person.setMortalityCategory(owner == PRIMARY ? MortalityCategory.MALE : MortalityCategory.FEMALE);
            var pension = new Pension(owner + " Pension", owner, start, null,
                    new BigDecimal("9000"), new BigDecimal("0.025"), new BigDecimal("7300"));
            pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            person.addIncomeSource(pension);
            person.addIncomeSource(new SocialSecurityIncome("Social Security", owner, person.getBirthDate().plusYears(67),
                    null, new BigDecimal("2500"), 67, new BigDecimal("0.02"), start.getYear()));
        }
        fx(() -> controller.newPlan(plan));
        try {
            var projection = fx(() -> controller.beginArkansasProjection(ignored -> { }));
            projection.completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertEquals(ApplicationController.ControlledProjectionStatus.ACCEPTED, fx(() -> controller.finishControlledArkansasProjection(projection)));
            var source = fx(controller::getAcceptedProjectionSnapshot);
            var theme = dark ? com.daviddunn.retirementplanner.application.settings.ApplicationTheme.DARK
                    : com.daviddunn.retirementplanner.application.settings.ApplicationTheme.LIGHT;
            var themes = fx(() -> ApplicationThemeManager.start(theme));
            try {
                var dialog = fx(() -> new SocialSecurityStrategyAnalyzerDialog(null, controller));
                try {
                    var slot = new AnalysisResultStore.Slot(source.owner(), AnalysisKind.COUPLE_WEIGHTED);
                    fx(() -> {
                        var stage = (Stage) field(dialog, "stage");
                        stage.show();
                        String scale = System.getProperty("glass.win.uiScale");
                        if (scale != null) assertEquals(Double.parseDouble(scale), stage.getOutputScaleX(), 0.01);
                        var date = (DatePicker) field(dialog, "mortalityDate");
                        date.setValue(plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(118).plusMonths(1));
                        invoke(dialog, "runWeightedAnalysis");
                        return null;
                    });
                    var weighted = fx(() -> (LongevityWeightedIntegratedView) field(dialog, "weightedView"));
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
                    while (store.inMemory(slot).isEmpty() && System.nanoTime() < deadline) {
                        String status = fx(weighted.status::getText);
                        assertFalse(status.startsWith("Analysis failed") || status.startsWith("No candidate")
                                || status.contains("unavailable for"), status);
                        Thread.sleep(30);
                    }
                    String finalStatus = fx(weighted.status::getText);
                    var accepted = store.inMemory(slot).orElseThrow(() -> new AssertionError("Weighted launcher did not accept a result. " + finalStatus));
                    var codec = new AnalysisSnapshotCodec();
                    var snapshot = accepted.presentationSnapshot(codec);
                    assertEquals(5_184, snapshot.payload().couple().entries().size());
                    assertTrue(snapshot.payload().successful());
                    assertEquals(source.id(), snapshot.arkansasExecution().dependencies().getFirst().projectionId());
                    assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
                    assertTrue(fx(() -> controller.hasCurrentArkansasAnalysisDependencies(snapshot)));

                    var file = folder.resolve("weighted-history.json");
                    fx(() -> { controller.saveAs(file); return null; });
                    fx(() -> controller.open(file));
                    controller.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
                    var history = fx(() -> {
                        var historical = new ProjectionAnalyzerHistoryDialog(controller, source.id(),
                                AnalysisKind.COUPLE_WEIGHTED);
                        historical.show();
                        return historical;
                    });
                    try {
                        var notice = (Label) field(history, "notice");
                        long restoreDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                        String historyStatus = fx(notice::getText);
                        while (historyStatus.startsWith("Loading") && System.nanoTime() < restoreDeadline) {
                            Thread.sleep(20);
                            historyStatus = fx(notice::getText);
                        }
                        assertTrue(historyStatus.contains("Committed immutable historical component."), historyStatus);
                        assertTrue(historyStatus.contains(source.id().toString()), historyStatus);
                    } finally {
                        fx(() -> { history.close(); return null; });
                    }
                } finally {
                    fx(() -> { invoke(dialog, "close"); return null; });
                }
            } finally {
                fx(() -> { themes.close(); return null; });
            }
        } finally {
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }
}
