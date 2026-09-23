package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.daviddunn.retirementplanner.ui.views.AssumptionsViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanningHorizonLayoutTest {
    @TempDir Path directory;

    @BeforeAll
    static void startFx() throws Exception {
        AssumptionsViewTest.startFx();
    }

    @Test
    void editorGroupsHorizonFirstAndPreservesExistingJsonProperty() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            List<String> labels = labels(f.view);
            assertTrue(labels.indexOf("Planning Horizon") < labels.indexOf("Economic Assumptions"));
            assertFalse(labels.contains("Death Scenario"));
            assertTrue(labels.contains("Assumed second death / end of household projection:"));
            field(f.view, "projectionLengthField").setText("32");
            assertTrue(f.view.applyChanges());
            assertEquals(32, f.assumptions().getProjectionLengthYears());
            var repository = new JsonRetirementPlanRepository();
            Path file = directory.resolve("plan.json");
            repository.save(f.plan(), file);
            String json = Files.readString(file);
            assertTrue(json.contains("\"projectionLengthYears\" : 32"));
            assertFalse(json.contains("planningHorizon"));
            assertEquals(32, repository.load(file).getPlanningAssumptions().getProjectionLengthYears());
        });
    }

    @Test
    void summaryOrdersCardsAndEditsTheSameProjectionLength() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            DatePicker startDate = control(f.view, "projectionStartDatePicker");
            startDate.setValue(LocalDate.of(2027, 1, 1));
            assertTrue(f.view.applyChanges());
            ResultsSummaryView summary = new ResultsSummaryView(f.controller);
            summary.setOnDeathScenarioApply(f.plan()::setPlanningAssumptions);
            summary.load(f.plan(), null, List.of());
            VBox panel = (VBox) summary.getRight();
            ScrollPane scroller = (ScrollPane) panel.getChildren().stream()
                    .filter(ScrollPane.class::isInstance).findFirst().orElseThrow();
            VBox cards = (VBox) scroller.getContent();
            assertEquals(List.of("Planning Horizon", "SOCIAL SECURITY", "ECONOMIC ASSUMPTIONS", "ROTH CONVERSIONS"),
                    cards.getChildren().stream().limit(4).map(card -> labels(card).getFirst()).toList());
            assertFalse(labels(cards).contains("DEATH SCENARIO"));
            field(summary, "projectionLengthField").setText("25");
            Button apply = control(summary, "applyDeathButton");
            apply.fire();
            assertEquals(25, f.assumptions().getProjectionLengthYears());
            summary.load(f.plan(), null, List.of());
            Label value = control(summary, "planningHorizonValue");
            assertEquals("Through 2051 · 25 years", value.getText());
            assertEquals("Assumed second death / end of household projection", value.getTooltip().getText());
            f.view.load(f.plan());
            assertEquals("25", field(f.view, "projectionLengthField").getText());
            field(f.view, "projectionLengthField").setText("1");
            assertTrue(f.view.applyChanges());
            summary.load(f.plan(), null, List.of());
            assertEquals("Through 2027 · 1 year", value.getText());
            assertEquals("1", field(summary, "projectionLengthField").getText());
        });
    }

    @Test
    void summarySurvivorFieldUsesFullCardWidthAndWrapsForBothSurvivors() throws Exception {
        fx(() -> {
            Fixture f = namedFixture();
            ResultsSummaryView summary = new ResultsSummaryView(f.controller);
            summary.load(f.plan(), null, List.of());
            VBox sidebar = (VBox) summary.getRight();
            summary.setRight(null);
            StackPane root = new StackPane(sidebar);
            root.getStyleClass().add("results-summary");
            root.getStylesheets().addAll(summary.getStylesheets());
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 850));
            try {
                stage.show();
                Label label = control(summary, "survivorAgeLabel");
                ComboBox<Integer> ages = control(summary, "survivorAgeComboBox");
                ComboBox<String> scenario = control(summary, "deathScenarioComboBox");
                GridPane grid = (GridPane) ages.getParent();
                assertEquals(320, sidebar.getPrefWidth());
                assertEquals(300, sidebar.getMinWidth());
                assertEquals(2, GridPane.getColumnSpan(label));
                assertEquals(2, GridPane.getColumnSpan(ages));
                assertEquals(GridPane.getRowIndex(label) + 1, GridPane.getRowIndex(ages));
                for (String survivor : List.of("Lisa", "David")) {
                    boolean spouse = survivor.equals("Lisa");
                    scenario.setValue(spouse ? "Primary Dies" : "Spouse Dies");
                    field(summary, "deathYearField").setText(spouse ? "2034" : "2032");
                    assertEquals(survivor + " — Survivor Benefit Claiming Age", label.getText());
                    assertEquals("Immediate at death (Age 68)", ages.getConverter().toString(ages.getValue()));
                    for (int width : List.of(320, 300, 360)) {
                        root.resize(width, 850);
                        root.applyCss();
                        root.layout();
                        assertTrue(label.isWrapText());
                        assertEquals(grid.getWidth(), ages.getWidth(), 1.0);
                        assertEquals(grid.getWidth(), label.getWidth(), 1.0);
                        Node viewport = sidebar.lookup(".viewport");
                        assertTrue(ages.localToScene(ages.getBoundsInLocal()).getMaxX()
                                <= viewport.localToScene(viewport.getBoundsInLocal()).getMaxX() + 1,
                                "The full-width control must remain inside the sidebar viewport at width " + width
                                        + "; control=" + ages.localToScene(ages.getBoundsInLocal())
                                        + "; viewport=" + viewport.localToScene(viewport.getBoundsInLocal()));
                        assertTrue(ages.getLayoutY() >= label.getLayoutY() + label.getHeight());
                        assertRenderedText(label, label.getText());
                        assertRenderedText(((ComboBoxListViewSkin<?>) ages.getSkin()).getDisplayNode(),
                                "Immediate at death (Age 68)");
                        assertTrue(ages.getTooltip().getText().contains("Immediate at death (Age 68)"));
                        assertTrue(label.getTooltip().getText().contains("Separate from their own retirement"));
                    }
                    field(summary, "deathYearField").setText(spouse ? "2030" : "2028");
                    root.applyCss();
                    root.layout();
                    assertEquals(List.of(64, 65, 66, 67), ages.getItems());
                    assertFalse(ages.isDisabled());
                    for (int age : ages.getItems()) {
                        ages.setValue(age);
                        root.layout();
                        assertRenderedText(((ComboBoxListViewSkin<?>) ages.getSkin()).getDisplayNode(), Integer.toString(age));
                        assertTrue(ages.getTooltip().getText().startsWith(age + "\n"));
                    }
                    ages.show();
                    Region popup = (Region) ((ComboBoxListViewSkin<?>) ages.getSkin()).getPopupContent();
                    popup.applyCss();
                    popup.layout();
                    assertTrue(popup.getWidth() >= ages.getWidth() - 1);
                    for (Node node : popup.lookupAll(".list-cell")) {
                        if (node instanceof ListCell<?> cell && !cell.isEmpty()) {
                            assertRenderedText(cell, cell.getText());
                        }
                    }
                    ages.hide();
                }
                scenario.setValue("Both Survive");
                assertTrue(ages.isDisabled());
                assertTrue(ages.getItems().isEmpty());
                assertEquals("Not Applicable", ages.getPromptText());
            } finally {
                stage.close();
            }
        });
    }

    @Test
    void assumptionsEditorShowsFullSurvivorTextWithoutChangingItsLayout() throws Exception {
        fx(() -> {
            Fixture f = namedFixture();
            StackPane root = new StackPane(f.view);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 900, 850));
            try {
                stage.show();
                ComboBox<com.daviddunn.retirementplanner.domain.model.DeathScenario> scenario = control(f.view, "deathScenarioComboBox");
                ComboBox<Integer> ages = control(f.view, "survivorClaimingAgeComboBox");
                Label label = control(f.view, "survivorAgeLabel");
                for (String survivor : List.of("Lisa", "David")) {
                    boolean spouse = survivor.equals("Lisa");
                    scenario.setValue(spouse ? com.daviddunn.retirementplanner.domain.model.DeathScenario.PRIMARY_DIES
                            : com.daviddunn.retirementplanner.domain.model.DeathScenario.SPOUSE_DIES);
                    field(f.view, "deathYearField").setText(spouse ? "2034" : "2032");
                    root.applyCss();
                    root.layout();
                    assertRenderedText(label, survivor + " — Survivor Benefit Claiming Age");
                    assertRenderedText(((ComboBoxListViewSkin<?>) ages.getSkin()).getDisplayNode(),
                            "Immediate at death (Age 68)");
                }
            } finally {
                stage.close();
            }
        });
    }

    private static Fixture namedFixture() {
        Fixture f = new Fixture();
        f.plan().getHousehold().getPrimaryPerson().setFirstName("David");
        f.plan().getHousehold().getSpouse().setFirstName("Lisa");
        f.view.load(f.plan());
        return f;
    }

    private static void assertRenderedText(Node node, String expected) {
        Text text = (Text) node.lookup(".text");
        assertNotNull(text);
        assertEquals(expected, text.getText(), "Rendered text should not have an ellipsis");
    }

    private static List<String> labels(Node node) {
        List<String> result = new ArrayList<>();
        if (node instanceof Label label) result.add(label.getText());
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) result.addAll(labels(child));
        }
        return result;
    }
}
