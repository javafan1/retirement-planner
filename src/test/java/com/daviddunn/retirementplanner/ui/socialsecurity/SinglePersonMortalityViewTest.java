package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonMortalityViewTest {
    static SinglePersonMortalityAnalysis.Result ss;
    static SinglePersonMortalityAnalysis.Result weighted;
    @BeforeAll static void prepare() throws Exception {
        var startup = new FutureTask<Void>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(startup); } catch (IllegalStateException started) { Platform.runLater(startup); }
        startup.get(20, TimeUnit.SECONDS);
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var mortality = SinglePersonMortalityAnalysisTest.mortality(plan, "1");
        ss = SinglePersonMortalityAnalysisTest.run(plan, mortality, "0.01", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        weighted = SinglePersonMortalityAnalysisTest.run(plan, mortality, "0.01", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
    }

    @Test void cachedSelectionsFrozenMetadataStaleInputsAndGeometry() throws Exception {
        fx(() -> {
            try (var coordinator = new SocialSecurityAnalysisJobCoordinator(); var jobs = new SocialSecurityAnalyzerJobController(coordinator)) {
                for (var result : java.util.List.of(ss, weighted)) {
                    var view = new SinglePersonMortalityView(SinglePersonMortalityAnalysisTest.plan(), jobs, result.mode());
                    jobs.onChanged(view::refresh);
                    view.render(result);
                    var stage = new Stage();
                    stage.setScene(new Scene(view, 1180, 900));
                    stage.show(); view.applyCss(); view.layout();
                    try {
                        assertEquals(9, view.table.getItems().size());
                        assertNotNull(view.factor.getTooltip());
                        assertTrue(view.factor.getTooltip().getText().contains("Below 1"));
                        assertEquals(0, jobs.generation());
                        for (int i = 0; i < 9; i++) view.table.getSelectionModel().select(i);
                        assertEquals(0, jobs.generation());
                        assertTrue(view.details.getText().contains("individual death scenarios"));
                        assertTrue(view.summary.getText().contains("FEMALE"));
                        assertTrue(view.details.localToScene(view.details.getBoundsInLocal()).getMaxY() <= 900);
                        assertTrue(view.table.localToScene(view.table.getBoundsInLocal()).getMaxX() <= 1180);
                        if (Boolean.getBoolean("single.stage4a.preview")) write(view, result.mode().name() + ".png");
                        var frozen = view.summary.getText();
                        view.factor.setText("0.8");
                        assertTrue(view.status.getText().contains("stale"));
                        assertEquals(frozen, view.summary.getText());
                        assertTrue(view.table.getAccessibleHelp().contains("stale"));
                        if (Boolean.getBoolean("single.stage4a.preview")) write(view, result.mode().name() + "-stale.png");
                    } finally { stage.close(); }
                }
            }
            return null;
        });
    }

    @Test void runCancellationAndInputValidationDoNotPublishPartialResults() throws Exception {
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        var refs = fx(() -> {
            var jobs = new SocialSecurityAnalyzerJobController(coordinator);
            var view = new SinglePersonMortalityView(SinglePersonMortalityAnalysisTest.plan(), jobs, SinglePersonMortalityAnalysis.Mode.INTEGRATED);
            jobs.onChanged(view::refresh);
            view.factor.setText("0"); view.start();
            assertEquals(0, jobs.generation());
            assertTrue(view.status.getText().contains("Check inputs"));
            view.factor.setText("1"); view.start();
            assertTrue(view.run.isDisabled()); assertTrue(view.factor.isDisabled());
            view.cancel.fire();
            return new Object[]{jobs, view};
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (fx(() -> ((SocialSecurityAnalyzerJobController) refs[0]).state()) != SocialSecurityAnalyzerJobController.State.IDLE
                    && System.nanoTime() < deadline) Thread.sleep(20);
            fx(() -> {
                var view = (SinglePersonMortalityView) refs[1];
                assertTrue(view.table.getItems().isEmpty());
                assertFalse(view.run.isDisabled());
                ((SocialSecurityAnalyzerJobController) refs[0]).close();
                return null;
            });
        } finally { coordinator.close(); }
    }

    @Test void coupleWeightedHeatMapStillHasEightyOneActualStrategyResults() throws Exception {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.SavingsAccount(
                "Reserves", com.daviddunn.retirementplanner.domain.model.AccountOwnership.JOINT, new BigDecimal("1000000")));
        var current = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        var ages = java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList();
        var candidates = new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, ages, ages,
                List.of(current.primarySurvivorElection()), List.of(current.spouseSurvivorElection()),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 9).strategies();
        var mortality = PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2026, 7, 1),
                List.of(new SocialSecurityMortalityProbability(80, BigDecimal.ONE)),
                List.of(new SocialSecurityMortalityProbability(82, BigDecimal.ONE)));
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareExact(
                new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, mortality,
                        LocalDate.of(2026, 7, 1), new BigDecimal("0.01"), Optional.of(current),
                        LongevityWeightedDetailRetentionPolicy.aggregateOnly(), AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertEquals(81, result.completedStrategyCount());
        var context = new SocialSecurityStrategyAnalysisRequestFactory().create(plan,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(), new BigDecimal("0.01"),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));
        var original = context.request().retirementGridRequest();
        var ssRequest = new SocialSecuritySurvivorClaimingOptimizationRequest(new SocialSecurityMortalityWeightedClaimingGridRequest(
                original.baseStrategy(), ages, ages, mortality.primary().distribution(), mortality.spouse().distribution(),
                original.mortalityBaseDate(), original.presentValueBaseDate(), original.realDiscountRate()));
        var ssResult = new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(ssRequest);
        fx(() -> {
            var model = new LongevityWeightedIntegratedPresentation(result, 0, 0);
            var view = new ClaimingStrategyHeatMapView(order -> { });
            view.render(LongevityWeightedHeatMapAdapter.from(model));
            var stage = new Stage(); stage.setScene(new Scene(view, 1400, 1040)); stage.show();
            try {
                view.applyCss(); view.layout();
                assertEquals(81, view.lookupAll(".heat-map-cell").size());
                if (Boolean.getBoolean("single.stage4a.preview")) write(view, "couple-weighted-heat-map.png");
            } finally { stage.close(); }
            var card = new com.daviddunn.retirementplanner.ui.components.PersonCard();
            card.load(SinglePersonMortalityAnalysisTest.plan().getHousehold().getPrimaryPerson());
            var personStage = new Stage(); personStage.setScene(new Scene(card, 650, 500)); personStage.show();
            try { card.applyCss(); card.layout(); if (Boolean.getBoolean("single.stage4a.preview")) write(card, "primary-person.png"); }
            finally { personStage.close(); }
            if (Boolean.getBoolean("single.stage4a.preview")) {
                try (var coordinator = new SocialSecurityAnalysisJobCoordinator(); var jobs = new SocialSecurityAnalyzerJobController(coordinator)) {
                    var dialog = new SocialSecurityStrategyAnalyzerDialog(null, plan, jobs);
                    var render = dialog.getClass().getDeclaredMethod("render", SocialSecurityStrategyAnalysisContext.class,
                            SocialSecurityStrategyAnalyzerPresentation.class);
                    render.setAccessible(true); render.invoke(dialog, context, SocialSecurityStrategyAnalyzerPresentation.from(ssResult));
                    var field = dialog.getClass().getDeclaredField("stage"); field.setAccessible(true);
                    var dialogStage = (Stage) field.get(dialog);
                    dialogStage.setWidth(1900); dialogStage.setHeight(1040); dialogStage.show();
                    try {
                        dialogStage.getScene().getRoot().applyCss(); dialogStage.getScene().getRoot().layout();
                        write(dialogStage.getScene().getRoot(), "couple-ss-analyzer.png");
                        var grid = dialog.getClass().getDeclaredField("claimingGrid"); grid.setAccessible(true);
                        write((javafx.scene.Node) grid.get(dialog), "couple-ss-grid.png");
                    } finally { dialogStage.close(); }
                }
            }
            return null;
        });
    }

    private static void write(javafx.scene.Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/single-stage4a-preview"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(buffered, "png", directory.resolve(name).toFile());
    }
    private static <T> T fx(Callable<T> call) throws Exception {
        var task = new FutureTask<T>(call); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
}
