package com.daviddunn.retirementplanner.ui.montecarlo;

import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.ArrayDeque;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonExpenseFeedbackTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    @Test void failedRunFeedbackCannotScrollAwayWhilePreviousComparisonRemainsInspectable() throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        var previous = MonteCarloComparisonFixtures.run();
        var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(
                new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository(),
                new com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository(directory.resolve("settings.json")),
                new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore(
                        new com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        controller.newPlan(MonteCarloComparisonFixtures.plan()); controller.saveAs(directory.resolve("synthetic.json"));
        var view = fx(() -> new MonteCarloStrategyComparisonView(controller, tasks::add,
                (input, progress, cancel) -> { throw new IllegalStateException(); }));
        var dialog = fx(() -> {
            var d = new MonteCarloStrategyComparisonDialog(null, view);
            d.getDialogPane().setPrefSize(900, 600); d.show(); d.setWidth(900); d.setHeight(650);
            @SuppressWarnings("unchecked") var count = (ComboBox<Integer>) view.lookup("#mcc-count");
            count.setValue(1); view.session().restore(previous, true);
            ((Button) view.lookup("#mcc-run")).fire(); assertEquals(1, tasks.size());
            return d;
        });
        try {
            tasks.remove().run();
            for (int i = 0; i < 100 && fx(() -> view.session().busy()); i++) Thread.sleep(20);
            fx(() -> {
                assertFalse(view.session().busy()); assertSame(previous, view.session().result());
                ((ScrollPane) dialog.getDialogPane().lookup(".scroll-pane")).setVvalue(1); return null;
            });
            awaitLayoutPulses(dialog.getDialogPane().getScene());
            fx(() -> {
                snapshot(dialog, "feedback");
                var status = (Label) dialog.getDialogPane().lookup("#mcc-status");
                var bounds = status.localToScene(status.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= dialog.getDialogPane().getScene().getHeight(),
                        "Current comparison failure feedback scrolled away: " + bounds);
                assertFalse(status.getText().contains("null"));
                assertFalse(status.getText().isBlank());
                return null;
            });
        } finally {
            fx(() -> { dialog.close(); return null; }); MonteCarloParameterPersistenceTest.stop(controller);
        }
    }

    static void snapshot(MonteCarloStrategyComparisonDialog dialog, String name) throws Exception {
        var image = dialog.getDialogPane().snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), 2);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
            png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var folder = Path.of("target/phase21b3/previews"); Files.createDirectories(folder);
        javax.imageio.ImageIO.write(png, "png", folder.resolve(name + ".png").toFile());
    }
}
