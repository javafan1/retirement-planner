package com.daviddunn.retirementplanner.ui.montecarlo;

import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;

class MonteCarloInflationViewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @SuppressWarnings("unchecked")
    static ComboBox<String> inflationMode(MonteCarloAnalysisView view) {
        return (ComboBox<String>) view.lookup("#mc-inflation-mode");
    }

    @Test
    void controlsDefaultOffValidateAndProvideAccessibleHelp() throws Exception {
        fx(() -> {
            var queue = new ArrayDeque<Runnable>();
            var view = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()), queue::add,
                    (p, s, r, progress, cancellation) -> { throw new AssertionError("Invalid request"); });
            try {
                new Scene(view);
                assertEquals("Deterministic", inflationMode(view).getValue());
                assertFalse(view.lookup("#mc-inflation-inputs").isManaged());
                assertEquals("2", field(view, "#mc-inflation-mean").getText());
                inflationMode(view).setValue("Stochastic");
                assertTrue(view.lookup("#mc-inflation-inputs").isVisible());
                for (String id : new String[]{"#mc-inflation-mean", "#mc-inflation-volatility", "#mc-inflation-floor"}) {
                    assertFalse(field(view, id).getAccessibleText().isBlank());
                    assertNotNull(field(view, id).getTooltip());
                }
                field(view, "#mc-inflation-floor").setText("3");
                button(view, "#mc-run").fire();
                assertTrue(queue.isEmpty());
                assertTrue(((Label) view.lookup("#mc-validation")).getText().contains("cannot exceed"));
                inflationMode(view).setValue("Deterministic");
                button(view, "#mc-run").fire();
                assertEquals(1, queue.size(), "Disabled stochastic inputs do not invalidate deterministic mode");
            } finally { view.close(); }
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void editsMarkStaleButDetailsStayFrozenAndChartSelectionDoesNotRerun(boolean longevity) throws Exception {
        var finished = new CompletableFuture<MonteCarloRun>();
        var calls = new AtomicInteger();
        var service = new MonteCarloRunService();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()),
                    command -> new Thread(command).start(),
                    (p, s, r, progress, cancellation) -> { calls.incrementAndGet(); return service.run(p, s, r, progress, cancellation); },
                    (p, r, progress, cancellation) -> { calls.incrementAndGet(); return service.runMortality(p, r, progress, cancellation); });
            new Scene(created, 1900, 1040);
            ((ComboBox<Integer>) created.lookup("#mc-simulations")).setValue(2);
            if (longevity) MonteCarloMortalityViewTest.mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            inflationMode(created).setValue("Stochastic");
            ((Label) created.lookup("#mc-status")).textProperty().addListener((o, a, b) -> {
                if (created.session().state() == MonteCarloSession.State.COMPLETED) finished.complete(created.session().result());
                if (created.session().state() == MonteCarloSession.State.FAILED) finished.completeExceptionally(created.session().failure());
            });
            button(created, "#mc-run").fire();
            assertTrue(inflationMode(created).isDisabled());
            assertTrue(field(created, "#mc-inflation-mean").isDisabled());
            return created;
        });
        try {
            var result = finished.get(30, TimeUnit.SECONDS);
            fx(() -> {
                var details = (TitledPane) view.lookup("#mc-analysis-details");
                String before = text(details.getContent());
                assertTrue(before.contains("mean 2.00%"));
                assertTrue(before.contains("no inflation/market correlation"));
                assertFalse(before.contains("No stochastic inflation"));
                assertFalse(before.contains("inflation and Social Security COLA remain deterministic"));
                key((MonteCarloFanChart) view.lookup("#monte-carlo-fan"), javafx.scene.input.KeyCode.RIGHT);
                assertEquals(1, calls.get());
                assertFalse(view.session().stale());
                field(view, "#mc-inflation-mean").setText("4.00");
                assertTrue(view.session().stale());
                assertSame(result, view.session().result());
                assertEquals(before, text(details.getContent()));
                inflationMode(view).setValue("Deterministic");
                assertEquals(before, text(details.getContent()));
                return null;
            });
        } finally { fx(() -> { view.close(); return null; }); }
    }

    private static String text(javafx.scene.Node node) {
        if (node instanceof Label label) return label.getText();
        if (node instanceof Parent parent) return parent.getChildrenUnmodifiable().stream()
                .map(MonteCarloInflationViewTest::text).collect(java.util.stream.Collectors.joining("\n"));
        return "";
    }
}
