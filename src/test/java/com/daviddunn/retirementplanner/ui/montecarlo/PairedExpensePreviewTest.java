package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PairedExpensePreviewTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_MONTE_CARLO_PAIRED_FIXED", "COUPLE_MONTE_CARLO_PAIRED_LONGEVITY",
            "INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED", "INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY"})
    void realEnabledDialogDisclosesBothFrozenCandidatesAndKeepsFailureVisibleInBothThemes(AnalysisKind kind) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "preview", new JsonRetirementPlanRepository());
        fx(() -> { PairedExpenseHistoryTest.configureInputs(source, ".25", ".17"); source.save(); return null; });
        var tasks = new ArrayDeque<Runnable>(); var fail = new AtomicBoolean();
        var view = fx(() -> new MonteCarloStrategyComparisonView(source, tasks::add, (p, u, c) -> {
            if (fail.get()) throw new IllegalStateException();
            return new MonteCarloStrategyComparisonRunService().run(p, u, c);
        }));
        var dialog = fx(() -> new MonteCarloStrategyComparisonDialog(null, view));
        var themes = fx(() -> ApplicationThemeManager.start(ApplicationTheme.LIGHT));
        try {
            fx(() -> {
                dialog.getDialogPane().setPrefSize(900, 600); dialog.show(); dialog.setWidth(900); dialog.setHeight(650);
                @SuppressWarnings("unchecked") var mode = (ComboBox<MonteCarloMode>) view.lookup("#mcc-mode");
                mode.setValue(kind.name().endsWith("LONGEVITY") ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN);
                @SuppressWarnings("unchecked") var count = (ComboBox<Integer>) view.lookup("#mcc-count"); count.setValue(2);
                ((TextField) view.lookup("#mcc-seed")).setText("417");
                ((TextField) view.lookup("#mcc-survivor-a")).setText("67"); ((TextField) view.lookup("#mcc-survivor-b")).setText("67");
                ((Button) view.lookup("#mcc-run")).fire(); assertEquals(1, tasks.size()); assertTrue(view.session().busy());
                return null;
            });
            awaitLayoutPulses(dialog.getDialogPane().getScene());
            fx(() -> { ComparisonExpenseFeedbackTest.snapshot(dialog, kind + "-busy"); return null; });
            tasks.remove().run();
            MonteCarloParameterPersistenceTest.await((Label) view.lookup("#mcc-parameters-status"), "SAVED");
            for (var theme : ApplicationTheme.values()) for (int width : new int[]{900, 1180}) {
                fx(() -> {
                    themes.apply(theme); dialog.setWidth(width); dialog.setHeight(width == 900 ? 650 : 820);
                    var detail = (TitledPane) view.lookup("#mcc-details"); detail.setAnimated(false); detail.setExpanded(true);
                    var text = (Label) detail.getContent(); assertTrue(text.getText().contains("expense 1 = 25%"));
                    assertTrue(text.getText().contains("expense 1 = 17%")); assertFalse(text.isTextTruncated());
                    return null;
                });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> { ((ScrollPane) dialog.getDialogPane().lookup("#mcc-content-scroll")).setVvalue(1); return null; });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> { assertFooter(dialog); ComparisonExpenseFeedbackTest.snapshot(dialog, kind + "-" + theme + "-" + width + "-details"); return null; });
            }
            var accepted = fx(() -> view.session().result()); fail.set(true);
            fx(() -> { ((TextField) view.lookup("#mcc-seed")).setText("418"); ((Button) view.lookup("#mcc-run")).fire(); return null; });
            tasks.remove().run();
            MonteCarloParameterPersistenceTest.await((Label) dialog.getDialogPane().lookup("#mcc-status"), "Comparison failed");
            for (var theme : ApplicationTheme.values()) {
                fx(() -> { themes.apply(theme); dialog.setWidth(900); dialog.setHeight(650); return null; });
                awaitLayoutPulses(dialog.getDialogPane().getScene());
                fx(() -> {
                    assertSame(accepted, view.session().result()); assertFalse(view.session().busy());
                    assertFalse(((Button) view.lookup("#mcc-run")).isDisabled());
                    assertFooter(dialog); ComparisonExpenseFeedbackTest.snapshot(dialog, kind + "-" + theme + "-failure"); return null;
                });
            }
        } finally { fx(() -> { dialog.close(); themes.close(); return null; }); MonteCarloParameterPersistenceTest.stop(source); }
    }

    private static void assertFooter(MonteCarloStrategyComparisonDialog dialog) {
        var status = (Label) dialog.getDialogPane().lookup("#mcc-status");
        var b = status.localToScene(status.getBoundsInLocal());
        assertTrue(b.getMinY() >= 0 && b.getMaxY() <= dialog.getDialogPane().getScene().getHeight());
        assertFalse(status.isTextTruncated()); assertEquals(status.getText(), status.getAccessibleText());
        assertFalse(status.getText().contains("null"));
    }
}
