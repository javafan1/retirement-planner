package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;

class SharedClaimingAgeGridTest {
    @ParameterizedTest @CsvSource({"1180,820,LIGHT", "900,650,LIGHT", "1180,820,DARK", "900,650,DARK"})
    void applicationThemesKeepExactGridPaletteAndReadableLayout(int width, int height,
            com.daviddunn.retirementplanner.application.settings.ApplicationTheme theme) throws Exception {
        fx(() -> {
            var themes = com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager.start(theme);
            var view = new ClaimingStrategyHeatMapView(ClaimingStrategyHeatMapProfile.deterministic(), order -> { });
            view.render(DeterministicHeatMapAdapter.from(DeterministicHeatMapAdapterTest.fixture()));
            var scroll = new ScrollPane(view); scroll.setFitToWidth(true);
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(scroll), width, height)); stage.show();
            try {
                view.applyCss(); view.layout();
                assertEquals(81, view.lookupAll(".heat-map-cell").size());
                for (var node : view.lookupAll(".heat-map-cell")) {
                    var button = (ToggleButton) node;
                    assertEquals(javafx.scene.paint.Color.web("#142536"), button.getTextFill());
                    assertTrue(button.getBackground().getFills().stream().anyMatch(fill ->
                            java.util.Arrays.stream(ClaimingHeatMapPalette.values()).anyMatch(t -> fill.getFill().equals(javafx.scene.paint.Color.web(t.color)))));
                }
                Files.createDirectories(Path.of("target/phase13b-previews"));
                javax.imageio.ImageIO.write(toBuffered(stage.getScene().snapshot(null)), "png",
                        Path.of("target/phase13b-previews/grid-" + width + "-" + theme + ".png").toFile());
            } finally { stage.close(); themes.close(); }
            return null;
        });
    }
    static SocialSecurityStrategyAnalyzerPresentation presentation;
    @BeforeAll static void prepare() throws Exception {
        startFx();
        var context = CoupleSocialSecurityPersistenceTest.fixture(true, false);
        presentation = SocialSecurityStrategyAnalyzerPresentation.from(new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(context.request()));
    }

    @Test void everyStageOneValueAxisTieAndRetentionIsPreservedIndependentlyOfStageTwo() {
        var copy = SocialSecurityClaimingGridAdapter.from(presentation);
        var original = presentation.result().retirementGrid();
        assertEquals(original.primaryClaimAges(), copy.primaryAges());
        assertEquals(original.spouseClaimAges(), copy.spouseAges());
        assertEquals(original.cells().size(), copy.cells().size());
        for (int i = 0; i < copy.cells().size(); i++) {
            var cell = copy.cells().get(i);
            assertSame(original.cells().get(i), cell.source());
            assertEquals(original.cells().get(i).expectedPresentValue(), cell.source().expectedPresentValue());
            assertEquals(original.highestExpectedPresentValue().highestCells().contains(cell.source()), cell.optimal());
            assertEquals(presentation.advancedToStageTwo(cell.pair().primary(), cell.pair().spouse()), cell.retained());
            assertTrue(cell.display().description().contains("Stage 1 expected Social Security PV"));
        }
    }

    @Test void distinctStageOneAndStageTwoOptimaAreNotSubstituted() {
        // Deliberately distinct display-only Stage 1 data proves the adapter cannot use Stage 2 values.
        var original = presentation.result();
        var grid = modifiedGrid(new BigDecimal("10000000.0000"), BigDecimal.ONE);
        var mixed = new SocialSecuritySurvivorClaimingOptimizationResult(original.request(), grid, original.retainedRetirementCells(),
                original.primarySurvivorCandidates(), original.spouseSurvivorCandidates(), original.evaluatedStrategies(),
                original.highestExpectedPresentValue(), original.highestExpectedPresentValueStrategies(),
                original.stageTwoStrategyCount(), original.stageTwoDeterministicEvaluationCount());
        var display = SocialSecurityClaimingGridAdapter.from(SocialSecurityStrategyAnalyzerPresentation.from(mixed));
        assertTrue(display.cells().getFirst().optimal());
        assertEquals(new BigDecimal("10000000.0000"), display.cells().getFirst().source().expectedPresentValue());
        assertNotEquals(display.cells().getFirst().source().expectedPresentValue(),
                presentation.bestForRetirement(62, 62).map(SocialSecuritySurvivorClaimingOptimizationCell::expectedPresentValue).orElse(null));
        var bestComplete = presentation.rankedStrategies().getFirst().cell().strategy();
        assertFalse(bestComplete.primaryRetirementAge() == 62 && bestComplete.spouseRetirementAge() == 62);
    }

    @ParameterizedTest @CsvSource({"99.5,heat-map-tier-1", "99.4999999999999999999999999999999999999999,heat-map-tier-2",
            "99.0,heat-map-tier-2", "98.999999,heat-map-tier-3", "97,heat-map-tier-3",
            "96.999999,heat-map-tier-4", "95,heat-map-tier-4", "94.999999,heat-map-tier-5", "99.9999,heat-map-tier-1"})
    void tiersDoNotRoundAcrossBoundariesOrCreateFalseOptima(String value, String css) {
        var grid = modifiedGrid(new BigDecimal(value), new BigDecimal("100.0000"));
        var adapted = SocialSecurityClaimingGridAdapter.from(grid, (p, s) -> true);
        assertEquals(css, ClaimingHeatMapPalette.forPercent(adapted.cells().getFirst().percent()).css);
        assertFalse(adapted.cells().getFirst().optimal());
        assertEquals(new BigDecimal(value), adapted.cells().getFirst().source().expectedPresentValue());
    }

    @ParameterizedTest @CsvSource({"0.000", "-1.00"})
    void nonpositiveOptimumRetainsExactTiesAndUsesUnavailablePercent(String value) throws Exception {
        var grid = modifiedGrid(new BigDecimal(value), new BigDecimal(value));
        var adapted = SocialSecurityClaimingGridAdapter.from(grid, (p, s) -> true);
        assertTrue(adapted.cells().stream().allMatch(c -> c.percent().isEmpty() && c.optimal() && c.retained()));
        fx(() -> {
            var view = new ClaimingAgeGridView();
            view.render(adapted.primaryAges(), adapted.spouseAges(), adapted.cells().stream().map(SocialSecurityClaimingGridAdapter.Cell::display).toList(), "test-", pair -> { });
            var button = (ToggleButton) view.lookup("#test-62-62");
            assertTrue(button.getText().contains("★ ▶"));
            assertTrue(button.getAccessibleText().contains("Retained for Stage 2"));
            assertTrue(button.getStyleClass().contains("heat-map-unavailable"));
            return null;
        });
    }

    private static SocialSecurityMortalityWeightedClaimingGridResult modifiedGrid(BigDecimal first, BigDecimal maximum) {
        var source = presentation.result().retirementGrid();
        var cells = source.cells().stream().map(cell -> {
            var pv = cell.primaryClaimAge() == 62 && cell.spouseClaimAge() == 62 ? first : maximum;
            var v = cell.expectedValue();
            return new SocialSecurityMortalityWeightedClaimingGridCell(cell.primaryClaimAge(), cell.spouseClaimAge(),
                    cell.primaryClaimDate(), cell.spouseClaimDate(), new SocialSecurityMortalityWeightedStrategyValue(
                    v.expectedNominalBenefits(), v.expectedRealBenefits(), pv, v.expectedPrimarySelectedBenefits(), v.expectedSpouseSelectedBenefits(), v.strategyEvaluationCount()));
        }).toList();
        var highest = first.max(maximum);
        var best = new SocialSecurityMortalityWeightedClaimingGridRanking(SocialSecurityExpectedValueMeasure.EXPECTED_PRESENT_VALUE,
                highest, cells.stream().filter(c -> c.expectedPresentValue().compareTo(highest) == 0).toList());
        return new SocialSecurityMortalityWeightedClaimingGridResult(source.request(), source.primaryClaimAges(), source.spouseClaimAges(),
                source.jointMortalityScenarios(), cells, source.highestExpectedNominal(), source.highestExpectedReal(), best, source.strategyEvaluationCount());
    }

    @Test void missingPairsAreNotManufacturedAndKeyboardSelectionUsesAgeIdentity() throws Exception {
        fx(() -> {
            var view = new ClaimingAgeGridView();
            var a = new ClaimingAgeGridView.Pair(63, 64);
            var b = new ClaimingAgeGridView.Pair(69, 64);
            var chosen = new ArrayList<ClaimingAgeGridView.Pair>();
            view.render(List.of(63, 69), List.of(64, 68), List.of(display(a), display(b)), "test-", chosen::add);
            view.select(a);
            var button = (ToggleButton) view.lookup("#test-63-64");
            assertEquals(2, view.lookupAll(".heat-map-cell").size());
            button.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false));
            assertEquals(b, view.selectedPair());
            assertEquals(List.of(a, b), chosen);
            assertTrue(view.lookup("#test-69-64").getAccessibleText().endsWith(" — Selected"));
            return null;
        });
    }
    private static ClaimingAgeGridView.Cell display(ClaimingAgeGridView.Pair pair) {
        return new ClaimingAgeGridView.Cell(pair, "$100", Optional.of(new BigDecimal("100")), true, false, "Ages " + pair, "Tooltip");
    }

    @ParameterizedTest @CsvSource({"1920,1080", "1366,768", "1180,820", "900,650", "760,650"})
    void ssLogicalLayoutsAreReadableAndProduceInspectedPreviews(int width, int height) throws Exception {
        fx(() -> {
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, LongevityWeightedAnalysisRequestFactoryTest.plan());
            field(dialog, "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY);
            var context = CoupleSocialSecurityPersistenceTest.fixture(true, false);
            var render = dialog.getClass().getDeclaredMethod("render", SocialSecurityStrategyAnalysisContext.class, SocialSecurityStrategyAnalyzerPresentation.class);
            render.setAccessible(true); render.invoke(dialog, context, presentation);
            var tabs = field(dialog, "socialSecurityTabs", TabPane.class); tabs.getSelectionModel().select(1);
            // Use exact logical scene dimensions rather than the native decorated stage dimensions.
            var stage = field(dialog, "stage", Stage.class);
            stage.setMinWidth(0); stage.setMinHeight(0);
            var root = stage.getScene().getRoot();
            stage.getScene().setRoot(new Pane());
            stage.setScene(new Scene(root, width, height)); stage.sizeToScene(); stage.show();
            try {
                root.applyCss(); root.layout();
                var grid = field(dialog, "claimingGrid", ClaimingAgeGridView.class);
                assertEquals(81, grid.lookupAll(".heat-map-cell").size());
                var first = (ToggleButton) grid.lookup("#ss-claiming-grid-62-62");
                var across = (ToggleButton) grid.lookup("#ss-claiming-grid-70-62");
                var down = (ToggleButton) grid.lookup("#ss-claiming-grid-62-70");
                assertEquals(first.getLayoutY(), across.getLayoutY());
                assertEquals(first.getLayoutX(), down.getLayoutX());
                assertTrue(across.getLayoutX() > first.getLayoutX());
                assertTrue(down.getLayoutY() > first.getLayoutY());
                assertTrue(first.getWidth() >= 55, "Cells must retain readable currency width");
                assertTrue(grid.localToScene(grid.getBoundsInLocal()).getMaxX() <= width + 1,
                        "Grid right=" + grid.localToScene(grid.getBoundsInLocal()).getMaxX() + " scene=" + width);
                assertFalse(grid.getParent().getParent() instanceof ScrollPane);
                var original = presentation.result().retirementGrid().highestExpectedPresentValue().highestCells().getFirst();
                var optimum = (ToggleButton) grid.lookup("#ss-claiming-grid-" + original.primaryClaimAge() + "-" + original.spouseClaimAge());
                assertTrue(optimum.getText().contains("★"));
                assertTrue(optimum.getText().contains("▶"));
                assertFalse(((javafx.scene.text.Text) optimum.lookup(".text")).getText().contains("…"),
                        "Rendered selected optimum must retain its dollar value beside both markers");
                assertTrue(optimum.getAccessibleText().contains("Exact Stage 1 optimum"));
                assertTrue(first.getTooltip().getText().contains("Stage 1 nominal benefits"));
                var selected = grid.selectedPair();
                render.invoke(dialog, context, presentation);
                assertEquals(selected, grid.selectedPair());
                Files.createDirectories(Path.of("target/phase11b-previews"));
                root.applyCss(); root.layout();
                IntegratedAnalysisResultTabs.reveal(tabs);
                root.layout();
                var image = root.snapshot(null, null);
                javax.imageio.ImageIO.write(toBuffered(image), "png", Path.of("target/phase11b-previews/ss-" + width + "x" + height + ".png").toFile());
                var content = tabs.getTabs().get(1).getContent();
                javax.imageio.ImageIO.write(toBuffered(content.snapshot(null, null)), "png",
                        Path.of("target/phase11b-previews/ss-grid-" + width + "x" + height + ".png").toFile());
            } finally { stage.close(); }
            return null;
        });
    }
    @ParameterizedTest @CsvSource({"1920,1080,true", "1366,768,true", "1180,820,true", "900,650,true",
            "1920,1080,false", "1366,768,false", "1180,820,false", "900,650,false"})
    void integratedSharedRendererPreviews(int width, int height, boolean deterministic) throws Exception {
        fx(() -> {
            var view = new ClaimingStrategyHeatMapView(deterministic ? ClaimingStrategyHeatMapProfile.deterministic()
                    : ClaimingStrategyHeatMapProfile.weighted(), order -> { });
            view.render(deterministic ? DeterministicHeatMapAdapter.from(DeterministicHeatMapAdapterTest.fixture())
                    : LongevityWeightedHeatMapAdapter.from(ClaimingStrategyHeatMapViewTest.fixture()));
            var scroll = new ScrollPane(view); scroll.setFitToWidth(true);
            var root = new StackPane(scroll); var stage = new Stage(); stage.setScene(new Scene(root, width, height)); stage.show();
            try {
                root.applyCss(); root.layout();
                assertEquals(81, view.lookupAll(".heat-map-cell").size());
                assertTrue(view.getWidth() <= width);
                Files.createDirectories(Path.of("target/phase11b-previews"));
                javax.imageio.ImageIO.write(toBuffered(view.snapshot(null, null)), "png",
                        Path.of("target/phase11b-previews/" + (deterministic ? "exhaustive-" : "weighted-") + width + "x" + height + ".png").toFile());
            } finally { stage.close(); }
            return null;
        });
    }
    static java.awt.image.BufferedImage toBuffered(javafx.scene.image.WritableImage image) {
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var pixels = image.getPixelReader();
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, pixels.getArgb(x, y));
        return output;
    }
}
