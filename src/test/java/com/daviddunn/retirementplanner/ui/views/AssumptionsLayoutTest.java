package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;

class AssumptionsLayoutTest {
    static final List<String> INPUTS = List.of("projectionStartDatePicker", "projectionLengthField", "deathScenarioComboBox",
            "deathYearField", "survivorClaimingAgeComboBox", "postDeathExpenseFactorField", "investmentReturnField",
            "inflationRateField", "healthcareInflationField", "socialSecurityColaField", "filingStatusComboBox",
            "stateJurisdictionComboBox", "federalBracketGrowthField", "standardDeductionGrowthField",
            "futureFederalMarginalRateAdjustmentField", "futureFederalMarginalRateEffectiveYearField",
            "stateIncomeTaxRateField", "localIncomeTaxRateField", "estimatedHeirTaxRateField");

    @BeforeAll static void toolkit() throws Exception { NewPlanWizardTestSupport.startFx(); }

    @ParameterizedTest @CsvSource({"LIGHT,1180,820", "DARK,1180,820", "LIGHT,900,650", "DARK,900,650", "LIGHT,1920,1080", "DARK,1920,1080"})
    void originalControlsRemainUniqueAccessibleAndResponsive(ApplicationTheme theme, int width, int height) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                for (boolean couple : new boolean[]{true, false}) {
                    var plan = BaselineDetailsTest.plan(couple);
                    var before = CompleteProjectionSnapshot.freeze(plan);
                    var view = new AssumptionsView(); view.load(plan);
                    var stage = new Stage(); stage.setScene(new Scene(view, width, height)); stage.show();
                    try {
                        view.applyCss(); view.layout();
                        var nodes = descendants(view);
                        for (String name : INPUTS) {
                            Control input = field(view, name);
                            assertEquals(1, nodes.stream().filter(node -> node == input).count(), name);
                            boolean survivorInput = Set.of("deathScenarioComboBox", "deathYearField",
                                    "survivorClaimingAgeComboBox", "postDeathExpenseFactorField").contains(name);
                            assertEquals(couple || !survivorInput, input.isVisible(), name);
                            assertTrue(input.isFocusTraversable(), name);
                            assertNotNull(input.getTooltip(), name);
                            assertNotNull(input.getAccessibleHelp(), name);
                            var label = nodes.stream().filter(Label.class::isInstance).map(Label.class::cast)
                                    .filter(candidate -> candidate.getLabelFor() == input).findFirst().orElseThrow();
                            assertNotNull(label.getTooltip(), name);
                        }
                        assertEquals(5, view.lookupAll(".assumptions-card").size());
                        assertEquals(Set.of("Planning Timeline", "Household & Survivor Planning", "Economic & Investment Assumptions",
                                "Income Tax Assumptions", "Estate & Heir Assumptions"),
                                view.lookupAll(".assumptions-section-heading").stream().map(Label.class::cast).map(Label::getText)
                                        .collect(java.util.stream.Collectors.toSet()));
                        assertColumns(view);
                        var scroll = (ScrollPane) view.lookup("#assumptions-scroll");
                        assertTrue(scroll.isFitToWidth());
                        assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getHbarPolicy());
                        Button apply = field(view, "applyButton"); Button cancel = field(view, "cancelButton");
                        assertTrue(apply.isDisabled()); assertTrue(cancel.isDisabled());
                        var footer = view.lookup(".assumptions-footer");
                        assertTrue(view.sceneToLocal(footer.localToScene(footer.getBoundsInLocal())).getMaxY() <= height);
                        scroll.setVvalue(1); view.layout();
                        ComboBox<DeathScenario> scenario = field(view, "deathScenarioComboBox");
                        assertEquals("Both survive", scenario.getConverter().toString(DeathScenario.BOTH_SURVIVE));
                        scenario.setValue(DeathScenario.BOTH_SURVIVE);
                        TextField deathYear = field(view, "deathYearField");
                        TextField factor = field(view, "postDeathExpenseFactorField");
                        assertTrue(deathYear.isDisabled()); assertEquals(couple, deathYear.isVisible());
                        assertTrue(factor.isDisabled()); assertEquals(couple, factor.isVisible());
                        view.cancelChanges();
                        assertEquals(before, CompleteProjectionSnapshot.freeze(plan));
                        view.load(BaselineDetailsTest.plan(!couple));
                        assertEquals(!couple, scenario.isVisible());
                        assertEquals(!couple, deathYear.isVisible());
                        assertEquals(!couple, factor.isVisible());
                        view.load(plan);
                        assertEquals(couple, scenario.isVisible());
                    } finally { stage.hide(); }
                }
            }
        });
    }

    @ParameterizedTest @CsvSource({"LIGHT,1180,820", "DARK,1180,820", "LIGHT,900,650", "DARK,900,650", "LIGHT,1920,1080", "DARK,1920,1080"})
    void renderScrolledScreenAndVerifyTabNavigation(ApplicationTheme theme, int width, int height) throws Exception {
        var stage = new Stage[1]; var view = new AssumptionsView[1]; var manager = new ApplicationThemeManager[1];
        try {
            fx(() -> {
                manager[0] = ApplicationThemeManager.start(theme);
                view[0] = new AssumptionsView(); view[0].load(BaselineDetailsTest.plan(true));
                stage[0] = new Stage(); stage[0].setScene(new Scene(view[0], width, height)); stage[0].show();
            });
            pulses();
            fx(() -> {
                DatePicker start = field(view[0], "projectionStartDatePicker");
                TextField length = field(view[0], "projectionLengthField");
                start.requestFocus();
                javafx.event.Event.fireEvent(start, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.TAB, false, false, false, false));
                assertSame(length, stage[0].getScene().getFocusOwner());
                save(view[0], theme + "-" + width + "x" + height + "-top");
                ((ScrollPane) view[0].lookup("#assumptions-scroll")).setVvalue(1);
            });
            pulses();
            fx(() -> {
                save(view[0], theme + "-" + width + "x" + height + "-bottom");
                TextField heir = field(view[0], "estimatedHeirTaxRateField");
                var bounds = heir.localToScene(heir.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() < height - 50, "Last field is reachable above the footer");
            });
        } finally {
            fx(() -> { if (stage[0] != null) stage[0].hide(); if (manager[0] != null) manager[0].close(); });
        }
    }

    static List<Node> descendants(Node root) {
        List<Node> result = new ArrayList<>(); result.add(root);
        if (root instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) result.addAll(descendants(child));
        return result;
    }
    static void save(Parent view, String name) throws Exception {
        var image = view.snapshot(null, null);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/assumptions-spacing/previews"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(pixels, "png", directory.resolve(name + ".png").toFile());
    }

    @ParameterizedTest @CsvSource({"LIGHT,true", "DARK,true", "LIGHT,false", "DARK,false"})
    void independentStacksAndResponsiveOrdering(ApplicationTheme theme, boolean couple) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                var view = new AssumptionsView(); view.load(BaselineDetailsTest.plan(couple));
                var stage = new Stage(); stage.setScene(new Scene(view, 1180, 820)); stage.show();
                try {
                    view.applyCss(); view.layout();
                    assertColumns(view);
                    var left = (VBox) view.lookup("#assumptions-left-column");
                    var right = (VBox) view.lookup("#assumptions-right-column");
                    var positions = left.getChildren().stream().map(Node::getLayoutY).toList();
                    var tax = (VBox) right.getChildren().get(1);
                    tax.setPrefHeight(tax.getHeight() + 400);
                    view.layout();
                    assertEquals(positions, left.getChildren().stream().map(Node::getLayoutY).toList(),
                            "A taller opposite-column card must not move left-column cards");
                    assertStack(left); assertStack(right);
                    view.resize(900, 650); view.layout();
                    assertColumns(view);
                    assertStack(left);
                    view.resize(1180, 820); view.layout();
                    assertColumns(view);
                    assertStack(left); assertStack(right);
                    for (String name : INPUTS) {
                        Control input = field(view, name);
                        assertEquals(1, descendants(view).stream().filter(node -> node == input).count(), name);
                    }
                } finally { stage.hide(); }
            }
        });
    }

    static void assertColumns(AssumptionsView view) {
        var body = (HBox) view.lookup("#assumptions-sections");
        var left = (VBox) view.lookup("#assumptions-left-column");
        var right = (VBox) view.lookup("#assumptions-right-column");
        boolean wide = body.getWidth() >= 1000;
        assertFalse(body.isFillHeight());
        assertEquals(wide, right.isManaged());
        assertEquals(wide ? List.of("Planning Timeline", "Economic & Investment Assumptions", "Estate & Heir Assumptions")
                        : List.of("Planning Timeline", "Household & Survivor Planning", "Economic & Investment Assumptions",
                        "Income Tax Assumptions", "Estate & Heir Assumptions"), titles(left));
        assertEquals(wide ? List.of("Household & Survivor Planning", "Income Tax Assumptions") : List.of(), titles(right));
        if (wide) assertEquals(left.getWidth(), right.getWidth(), 1);
        assertStack(left); assertStack(right);
    }

    static List<String> titles(VBox column) {
        return column.getChildren().stream().map(VBox.class::cast)
                .map(card -> ((Label) card.getChildren().getFirst()).getText()).toList();
    }

    static void assertStack(VBox column) {
        var cards = column.getChildren().stream().filter(Node::isManaged).toList();
        for (int i = 1; i < cards.size(); i++) {
            double gap = cards.get(i).getBoundsInParent().getMinY() - cards.get(i - 1).getBoundsInParent().getMaxY();
            assertEquals(16, gap, 0.5, "Natural-height cards must have a consistent gap without overlaps");
        }
    }
}
