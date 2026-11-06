package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.*;
import java.util.ArrayDeque;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneExpensePreviewTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void actualEnabledDialogsRenderAtBothSizesInBothThemes(boolean couple, boolean longevity) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var kind = com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.valueOf(
                (couple ? "COUPLE" : "INDIVIDUAL") + "_MONTE_CARLO_" + (longevity ? "LONGEVITY" : "FIXED"));
        var controller = fixture.source(kind, "preview", new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository());
        fx(() -> {
            StandaloneExpenseHistoryTest.volatility(controller.getCurrentPlan(), ".25");
            controller.markModified(); controller.invalidateProjection(); controller.getCurrentProjection(); controller.save(); return null;
        });
        var tasks = new ArrayDeque<Runnable>();
        var view = fx(() -> new MonteCarloAnalysisView(controller, tasks::add,
                (p, s, r, progress, cancel) -> new MonteCarloRunService().run(p, s, r, progress, cancel),
                (p, r, progress, cancel) -> new MonteCarloRunService().runMortality(p, r, progress, cancel)));
        var dialog = fx(() -> new MonteCarloAnalysisDialog(null, view));
        var themes = fx(() -> ApplicationThemeManager.start(ApplicationTheme.LIGHT));
        String family = (couple ? "couple" : "single") + (longevity ? "-longevity" : "-fixed");
        try {
            fx(() -> {
                dialog.getDialogPane().setPrefSize(900, 600); dialog.show(); dialog.setWidth(900); dialog.setHeight(650);
                @SuppressWarnings("unchecked") var mode = (ComboBox<MonteCarloMode>) view.lookup("#mc-mode");
                mode.setValue(longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN);
                @SuppressWarnings("unchecked") var count = (ComboBox<Integer>) view.lookup("#mc-simulations");
                count.setValue(2); field(view, "#mc-seed").setText("417");
                if (couple && longevity) field(view, "#mc-survivor-age").setText("67");
                button(view, "#mc-run").fire(); assertEquals(1, tasks.size());
                assertTrue(view.session().busy()); assertTrue(button(view, "#mc-run").isDisabled());
                return null;
            });
            awaitLayoutPulses(dialog.getDialogPane().getScene());
            fx(() -> { snapshot(dialog, family + "-busy"); return null; });
            tasks.remove().run();
            for (int i = 0; i < 500 && fx(() -> view.session().busy()); i++) Thread.sleep(20);
            fx(() -> {
                assertFalse(view.session().busy()); assertNotNull(view.session().result());
                assertTrue(view.lookup("#mc-expense-assumptions").isManaged());
                assertTrue(view.preparePdfReport().context().stream().anyMatch(s -> s.contains("expense 1 = 25%")));
                assertFalse(button(view, "#mc-run").isDisabled()); return null;
            });
            MonteCarloParameterPersistenceTest.await((Label) view.lookup("#mc-parameters-status"), "SAVED");
            for (var theme : ApplicationTheme.values()) for (int width : new int[] {900, 1180}) {
                fx(() -> {
                    themes.apply(theme); dialog.setWidth(width); dialog.setHeight(width == 900 ? 650 : 820);
                    var scroll = (ScrollPane) dialog.getDialogPane().lookup("#mc-content-scroll");
                    var label = view.lookup("#mc-expense-assumptions");
                    double y = view.sceneToLocal(label.localToScene(label.getBoundsInLocal())).getMinY();
                    scroll.setVvalue(Math.max(0, y - 35) / Math.max(1, view.getHeight() - scroll.getViewportBounds().getHeight()));
                    return null;
                });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> {
                    var label = (Label) view.lookup("#mc-expense-assumptions");
                    assertFalse(label.isTextTruncated());
                    var status = (Label) dialog.getDialogPane().lookup("#mc-status");
                    var bounds = status.localToScene(status.getBoundsInLocal());
                    assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= dialog.getDialogPane().getScene().getHeight());
                    snapshot(dialog, family + "-" + theme + "-" + width); return null;
                });
                fx(() -> {
                    var detail = (TitledPane) view.lookup("#mc-analysis-details");
                    detail.setAnimated(false); detail.setExpanded(true);
                    var original = (Label) view.lookup("#mc-expense-details");
                    assertNotNull(original); assertTrue(original.getText().contains("expense 1 = 25%"));
                    assertTrue(original.getText().contains("INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1"));
                    ((ScrollPane) dialog.getDialogPane().lookup("#mc-content-scroll")).setVvalue(1); return null;
                });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> { ((ScrollPane) dialog.getDialogPane().lookup("#mc-content-scroll")).setVvalue(1); return null; });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> { snapshot(dialog, family + "-" + theme + "-" + width + "-details");
                    assertFalse(((Label) view.lookup("#mc-expense-details")).isTextTruncated());
                    ((TitledPane) view.lookup("#mc-analysis-details")).setExpanded(false); return null; });
            }
        } finally {
            fx(() -> { dialog.close(); themes.close(); return null; });
            MonteCarloParameterPersistenceTest.stop(controller);
        }
    }

    private static void snapshot(MonteCarloAnalysisDialog dialog, String name) throws Exception {
        var image = dialog.getDialogPane().snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), 2);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
            png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var folder = Path.of("target/phase21b2/previews"); Files.createDirectories(folder);
        javax.imageio.ImageIO.write(png, "png", folder.resolve(name + ".png").toFile());
    }
}
