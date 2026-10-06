package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonIntegratedViewTest {
    private static SinglePersonIntegratedAnalysis completed;
    private static ExhaustiveIntegratedSearchPresentation coupleCompleted;
    private static com.daviddunn.retirementplanner.domain.model.RetirementPlan couplePlan;

    @BeforeAll static void prepare() throws Exception {
        var startup = new FutureTask<Void>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(startup); } catch (IllegalStateException started) { Platform.runLater(startup); }
        startup.get(20, TimeUnit.SECONDS);
        completed = SinglePersonIntegratedAnalysis.calculate(IntegratedSocialSecurityCompleteStrategySearchRequest.standard(
                SinglePersonDeterministicStrategyTest.plan()), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        if (Boolean.getBoolean("single.stage3.preview")) {
            var single = SinglePersonDeterministicStrategyTest.plan();
            var spouse = new com.daviddunn.retirementplanner.domain.model.Person("Spouse", "Couple", java.time.LocalDate.of(1967, 3, 1));
            spouse.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome("Spouse SS",
                    com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE, spouse.getBirthDate().plusYears(67), null,
                    new java.math.BigDecimal("1800"), 67, java.math.BigDecimal.ZERO, 2027));
            var household = new com.daviddunn.retirementplanner.domain.model.Household(single.getHousehold().getPrimaryPerson(), spouse);
            single.getHousehold().getExpenses().forEach(household::addExpense);
            var pair = new com.daviddunn.retirementplanner.domain.model.RetirementPlan(household,
                    single.getAccountPortfolio(), single.getPlanningAssumptions());
            couplePlan = pair;
            var current = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(pair);
            var ages = java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList();
            var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(pair, ages, ages,
                    java.util.List.of(current.primarySurvivorElection()), java.util.List.of(current.spouseSurvivorElection()),
                    IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 9);
            var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
            assertEquals(81, result.entries().size());
            assertEquals(81, result.successfulStrategyCount());
            coupleCompleted = ExhaustiveIntegratedSearchPresentation.from(result, java.time.Duration.ZERO, 20);
        }
    }

    @Test void nineRowsSelectionUsesFrozenResultsAndAssumptionEditMarksStale() throws Exception {
        fx(() -> {
            try (var coordinator = new SocialSecurityAnalysisJobCoordinator();
                 var jobs = new SocialSecurityAnalyzerJobController(coordinator)) {
                var plan = SinglePersonDeterministicStrategyTest.plan();
                var view = new SinglePersonIntegratedView(plan, jobs);
                assertTrue(view.breakEven.isDisabled());
                assertNotNull(view.deathYear.getTooltip());
                view.render(completed);
                assertEquals(9, view.table.getItems().size());
                assertEquals(0, jobs.generation());
                for (int i = 0; i < 9; i++) {
                    view.table.getSelectionModel().select(i);
                    assertTrue(view.details.getText().contains("Total Taxes"));
                    assertFalse(view.details.getText().contains("Spouse"));
                    assertFalse(view.breakEven.isDisabled());
                }
                assertEquals(0, jobs.generation(), "Selection must not start analysis");
                assertTrue(view.table.getItems().stream().anyMatch(e -> e.strategy().primaryRetirementAge() == 67));
                assertEquals(1, view.table.getItems().getFirst().afterTaxEstateRank().orElseThrow());
                var summary = view.summary.getText();
                view.deathYear.setText("2038");
                assertTrue(view.breakEven.isDisabled());
                assertTrue(view.status.getText().contains("changed"));
                assertEquals(summary, view.summary.getText(), "Completed metadata remains frozen");
            }
            return null;
        });
    }

    @Test void dialogUsesSinglePresentationAndOffersMortalityWithoutStartingJobs() throws Exception {
        fx(() -> {
            try (var coordinator = new SocialSecurityAnalysisJobCoordinator();
                 var jobs = new SocialSecurityAnalyzerJobController(coordinator)) {
                var dialog = new SocialSecurityStrategyAnalyzerDialog(null, SinglePersonDeterministicStrategyTest.plan(), jobs);
                var field = dialog.getClass().getDeclaredField("stage"); field.setAccessible(true);
                var stage = (Stage) field.get(dialog);
                try {
                    stage.show(); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                    assertNotNull(stage.getScene().lookup("#single-claiming-strategies"));
                    assertTrue(stage.getScene().getRoot().lookupAll(".heat-map-cell").isEmpty());
                    assertEquals(0, jobs.generation());
                    var tabs = (TabPane) stage.getScene().getRoot().lookup(".tab-pane");
                    assertEquals(java.util.List.of("Deterministic Integrated", "Social Security Only", "Longevity-Weighted Integrated"),
                            tabs.getTabs().stream().map(Tab::getText).toList());
                    assertInstanceOf(SinglePersonMortalityView.class, tabs.getTabs().get(1).getContent());
                } finally { stage.close(); }
            }
            return null;
        });
    }

    @Test void cancellationPublishesNoPartialResult() throws Exception {
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        var refs = fx(() -> {
            var jobs = new SocialSecurityAnalyzerJobController(coordinator);
            var view = new SinglePersonIntegratedView(SinglePersonDeterministicStrategyTest.plan(), jobs);
            view.start();
            assertTrue(view.run.isDisabled());
            view.cancel.fire();
            return new Object[]{jobs, view};
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (fx(() -> ((SocialSecurityAnalyzerJobController) refs[0]).state()) != SocialSecurityAnalyzerJobController.State.IDLE
                    && System.nanoTime() < deadline) Thread.sleep(20);
            fx(() -> {
                var view = (SinglePersonIntegratedView) refs[1];
                assertTrue(view.table.getItems().isEmpty());
                assertTrue(view.breakEven.isDisabled());
                assertTrue(view.status.getText().contains("cancelled"));
                ((SocialSecurityAnalyzerJobController) refs[0]).close();
                return null;
            });
        } finally { coordinator.close(); }
    }

    @Test void socialSecurityInputsOfferOnlyPrimaryAndDeriveDate() throws Exception {
        fx(() -> {
            var plan = SinglePersonDeterministicStrategyTest.plan();
            var dialog = new com.daviddunn.retirementplanner.ui.dialogs.SocialSecurityDialog(null, 2027, plan.getHousehold());
            var grid = (GridPane) dialog.getDialogPane().getContent();
            var combos = grid.getChildren().stream().filter(ComboBox.class::isInstance).map(ComboBox.class::cast).toList();
            var owner = combos.stream().filter(c -> c.getItems().contains(com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY)).findFirst().orElseThrow();
            assertEquals(java.util.List.of(com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY), owner.getItems());
            assertNotNull(owner.getTooltip());
            var age = combos.stream().filter(c -> c.getItems().contains(70)).findFirst().orElseThrow();
            age.setValue(70);
            var date = (DatePicker) grid.getChildren().stream().filter(DatePicker.class::isInstance).findFirst().orElseThrow();
            assertTrue(date.isDisabled());
            assertEquals(java.time.LocalDate.of(2035, 2, 1), date.getValue());
            if (Boolean.getBoolean("single.stage3.preview")) {
                dialog.show();
                try { dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout(); write(dialog.getDialogPane(), "single-social-security-input.png"); }
                finally { dialog.close(); }
            }
            return null;
        });
    }

    @Test void previewAndGeometry() throws Exception {
        fx(() -> {
            try (var coordinator = new SocialSecurityAnalysisJobCoordinator();
                 var jobs = new SocialSecurityAnalyzerJobController(coordinator)) {
                var view = new SinglePersonIntegratedView(SinglePersonDeterministicStrategyTest.plan(), jobs);
                view.render(completed);
                var stage = new Stage();
                stage.setScene(new Scene(new ScrollPane(view), 1180, 820));
                ((ScrollPane) stage.getScene().getRoot()).setFitToWidth(true);
                stage.show();
                try {
                    stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                    assertTrue(view.table.getWidth() > 1050);
                    assertTrue(view.table.localToScene(view.table.getBoundsInLocal()).getMaxX() <= 1180);
                    assertTrue(view.details.localToScene(view.details.getBoundsInLocal()).getMaxY() <= 820);
                    if (Boolean.getBoolean("single.stage3.preview")) {
                        write(stage.getScene().getRoot(), "single-integrated-1180.png");
                        var comparison = completed.breakEvenByClaimingAge().get(62);
                        var breakView = new com.daviddunn.retirementplanner.ui.breakeven.BreakEvenAnalysisView(comparison);
                        var scroll = new ScrollPane(breakView); scroll.setFitToWidth(true);
                        stage.setScene(new Scene(scroll, 1180, 900));
                        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                        assertFalse(text(breakView).contains("Spouse"));
                        write(scroll, "single-break-even.png");
                        var couple = new ClaimingStrategyHeatMapView(ClaimingStrategyHeatMapProfile.deterministic(), i -> {});
                        couple.render(DeterministicHeatMapAdapter.from(coupleCompleted));
                        stage.setScene(new Scene(new StackPane(couple), 1180, 820));
                        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                        write(stage.getScene().getRoot(), "couple-heat-map.png");
                        var dialog = new SocialSecurityStrategyAnalyzerDialog(null, couplePlan, jobs);
                        var presentationField = dialog.getClass().getDeclaredField("exhaustivePresentation");
                        presentationField.setAccessible(true); presentationField.set(dialog, coupleCompleted);
                        var render = dialog.getClass().getDeclaredMethod("renderExhaustive", ExhaustiveIntegratedSearchPresentation.class);
                        render.setAccessible(true); render.invoke(dialog, coupleCompleted);
                        var stageField = dialog.getClass().getDeclaredField("stage"); stageField.setAccessible(true);
                        var coupleStage = (Stage) stageField.get(dialog);
                        coupleStage.setWidth(1900); coupleStage.setHeight(1040); coupleStage.show();
                        try {
                            selectTabs(coupleStage.getScene().getRoot());
                            coupleStage.getScene().getRoot().applyCss(); coupleStage.getScene().getRoot().layout();
                            write(coupleStage.getScene().getRoot(), "couple-analyzer-inputs.png");
                            var outer = (ScrollPane) ((BorderPane) coupleStage.getScene().getRoot()).getCenter();
                            outer.setVvalue(1);
                            coupleStage.getScene().getRoot().layout();
                            write(coupleStage.getScene().getRoot(), "couple-ranked-strategies.png");
                            var tableField = dialog.getClass().getDeclaredField("exhaustiveTable"); tableField.setAccessible(true);
                            write((TableView<?>) tableField.get(dialog), "couple-ranked-table.png");
                        } finally { coupleStage.close(); }
                    }
                } finally { stage.close(); }
            }
            return null;
        });
    }

    private static String text(javafx.scene.Node node) {
        var s = new StringBuilder();
        if (node instanceof Labeled label) s.append(label.getText());
        if (node instanceof javafx.scene.Parent parent) parent.getChildrenUnmodifiable().forEach(n -> s.append(text(n)));
        return s.toString();
    }

    private static void selectTabs(javafx.scene.Node node) {
        if (node instanceof TabPane tabs) {
            for (var tab : tabs.getTabs()) {
                if (java.util.Set.of("Integrated Retirement Plan", "Deterministic", "Deterministic Exhaustive Search", "Ranked Strategies")
                        .contains(tab.getText())) tabs.getSelectionModel().select(tab);
                if (tab.getContent() != null) selectTabs(tab.getContent());
            }
        } else if (node instanceof ScrollPane scroll) {
            selectTabs(scroll.getContent());
        } else if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(SinglePersonIntegratedViewTest::selectTabs);
        }
    }

    private static void write(javafx.scene.Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        var directory = Path.of("target/single-stage3-preview"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(buffered, "png", directory.resolve(name).toFile());
    }

    private static <T> T fx(Callable<T> task) throws Exception {
        var future = new FutureTask<T>(task); Platform.runLater(future); return future.get(30, TimeUnit.SECONDS);
    }
}
