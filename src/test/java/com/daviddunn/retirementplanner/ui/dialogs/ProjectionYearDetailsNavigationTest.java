package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import com.daviddunn.retirementplanner.ui.summary.*;
import com.daviddunn.retirementplanner.ui.views.ResultsView;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionYearDetailsNavigationTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); }
        task.get(30, TimeUnit.SECONDS);
    }

    @Test void firstMiddleLastAndRoundTrip() throws Exception {
        fx(() -> {
            List<ProjectionYear> years = List.of(year(2027), year(2028), year(2029));
            for (int i = 0; i < years.size(); i++) {
                var dialog = dialog(years, years.get(i));
                state(dialog, years.get(i), i == 0, i == 2);
            }
            var dialog = dialog(years, years.getFirst());
            button(dialog, "nextButton").fire();
            state(dialog, years.get(1), false, false);
            button(dialog, "nextButton").fire();
            state(dialog, years.getLast(), false, true);
            button(dialog, "nextButton").fire();
            state(dialog, years.getLast(), false, true);
            button(dialog, "previousButton").fire();
            button(dialog, "previousButton").fire();
            state(dialog, years.getFirst(), true, false);
        });
    }

    @Test void gapsSingleAndInvalidInputs() throws Exception {
        fx(() -> {
            List<ProjectionYear> years = new ArrayList<>(List.of(year(2027), year(2029), year(2032)));
            var dialog = dialog(years, years.getFirst());
            var last = years.getLast();
            years.clear(); // Captured results cannot change underneath the dialog.
            button(dialog, "nextButton").fire();
            assertEquals(2029, dialog.getProjectionYear().getCalendarYear());
            button(dialog, "nextButton").fire();
            state(dialog, last, false, true);
            var only = year(2027);
            state(dialog(List.of(only), only), only, true, true);
            state(dialog(null, only), null, true, true);
            state(dialog(List.of(), null), null, true, true);
            state(dialog(List.of(only), year(2040)), null, true, true);
            state(new ProjectionYearDetailsDialog(null, null, null), null, true, true);
        });
    }

    @Test void allSectionsRefreshWithBaselineAndAssetsWhileWindowAndScrollStayPut() throws Exception {
        fx(() -> {
            var first = year(2027);
            var second = year(2028);
            // Match baseline by calendar year, independently of list position.
            var baseline = ProjectionYearBuilder.aProjectionYear().withCalendarYear(2028)
                    .withBeginningInvestableAssets(999).build();
            var assets = List.of(new NonInvestableAssetProjection(2028, List.of(), new BigDecimal("54321")));
            var dialog = new ProjectionYearDetailsDialog(
                    new ProjectionYearDetailsRequest(List.of(first, second), first, assets),
                    List.of(baseline), assets);
            Stage stage = field(dialog, "stage");
            ProjectionYearDetailsPane pane = field(dialog, "pane");
            ScrollPane scroll = (ScrollPane) pane.getCenter();
            stage.show();
            try {
                stage.setX(80); stage.setY(90); stage.setWidth(1200); stage.setHeight(700);
                pane.applyCss(); pane.layout(); scroll.setVvalue(0.4);
                double x = stage.getX(), y = stage.getY(), width = stage.getWidth(), height = stage.getHeight();
                var before = texts(pane);
                button(dialog, "nextButton").fire();
                pane.applyCss(); pane.layout();
                assertSame(pane, field(dialog, "pane"));
                assertSame(scroll, pane.getCenter());
                assertEquals(0.4, scroll.getVvalue(), 0.001);
                assertEquals(x, stage.getX()); assertEquals(y, stage.getY());
                assertEquals(width, stage.getWidth()); assertEquals(height, stage.getHeight());
                assertNotEquals(before, texts(pane));
                assertEquals(texts(new ProjectionYearDetailsPane(second, baseline,
                        new BigDecimal("54321"), new BigDecimal("54321"))), texts(pane));
                for (long value : new long[]{20280, 20281, 20282, 20283, 20284, 20285, 54321, 999}) {
                    assertTrue(texts(pane).contains(UIFormatters.money(BigDecimal.valueOf(value))), "Missing " + value);
                }
                button(dialog, "previousButton").fire();
                assertEquals(texts(new ProjectionYearDetailsPane(first, null, BigDecimal.ZERO, null)), texts(pane));
            } finally { stage.close(); }
        });
    }

    @Test void resultsViewPassesSelectedYearAndOriginalOrderWithoutRecalculation() throws Exception {
        fx(() -> {
            var first = year(2027); var middle = year(2029); var last = year(2032);
            Projection projection = new Projection();
            projection.addYear(first); projection.addYear(middle); projection.addYear(last);
            ResultsView view = new ResultsView();
            view.load(projection, List.of());
            // Table ordering is independent of the ordered projection snapshot.
            view.getTable().getItems().setAll(last, middle, first);
            AtomicReference<ProjectionYearDetailsRequest> captured = new AtomicReference<>();
            view.setOnYearDoubleClick(captured::set);
            TableRow<ProjectionYear> row = view.getTable().getRowFactory().call(view.getTable());
            row.updateTableView(view.getTable()); row.updateIndex(1);
            row.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED,
                    0, 0, 0, 0, MouseButton.PRIMARY, 2, false, false, false, false,
                    false, false, false, false, false, false, null));
            assertSame(middle, captured.get().initialYear());
            assertEquals(List.of(first, middle, last), captured.get().years());
            var dialog = new ProjectionYearDetailsDialog(captured.get(), null, null);
            state(dialog, middle, false, false);
            view.load(null, null);
            button(dialog, "nextButton").fire();
            state(dialog, last, false, true);
        });
    }

    @Test void keyboardNavigationRespectsFocusedControls() throws Exception {
        fx(() -> {
            var years = List.of(year(2027), year(2028), year(2029));
            var dialog = dialog(years, years.get(1));
            Stage stage = field(dialog, "stage");
            stage.show();
            try {
                Button next = button(dialog, "nextButton");
                next.requestFocus();
                next.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                        false, false, false, false));
                state(dialog, years.getLast(), false, true);
                Button previous = button(dialog, "previousButton");
                previous.requestFocus();
                previous.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.LEFT,
                        false, false, false, false));
                state(dialog, years.get(1), false, false);
                TextField editor = new TextField("editable");
                ((javafx.scene.layout.BorderPane) stage.getScene().getRoot()).setBottom(editor);
                editor.requestFocus();
                editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                        false, false, false, false));
                state(dialog, years.get(1), false, false);
                assertEquals("Next Projection Year", next.getTooltip().getText());
                assertEquals("Previous Projection Year", previous.getAccessibleText());
            } finally { stage.close(); }
        });
    }

    private static ProjectionYear year(int year) {
        long amount = year * 10L;
        return ProjectionYearBuilder.aProjectionYear().withCalendarYear(year).withPrimaryPersonAge(year - 1960)
                .withBeginningInvestableAssets(amount).withInvestmentGrowth(amount + 1)
                .withGuaranteedIncome(amount + 2).withAnnualExpenses(amount + 3)
                .withRequiredMinimumDistribution(amount + 4).withAfterTaxEstateValue(amount + 5).build();
    }
    private static ProjectionYearDetailsDialog dialog(List<ProjectionYear> years, ProjectionYear initial) {
        return new ProjectionYearDetailsDialog(new ProjectionYearDetailsRequest(years, initial, null), null, null);
    }
    private static void state(ProjectionYearDetailsDialog dialog, ProjectionYear year, boolean previous, boolean next) {
        assertSame(year, dialog.getProjectionYear());
        assertEquals(previous, button(dialog, "previousButton").isDisabled());
        assertEquals(next, button(dialog, "nextButton").isDisabled());
    }
    private static Button button(Object target, String name) { return field(target, name); }
    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static List<String> texts(Node node) {
        List<String> result = new ArrayList<>();
        if (node instanceof Label label) result.add(label.getText());
        if (node instanceof ScrollPane scroll) result.addAll(texts(scroll.getContent()));
        else if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) result.addAll(texts(child));
        return result;
    }
    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null); Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }
}
