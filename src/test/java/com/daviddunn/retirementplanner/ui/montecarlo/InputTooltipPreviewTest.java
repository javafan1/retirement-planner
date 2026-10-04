package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.dialogs.*;
import com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityStrategyAnalyzerDialog;
import com.daviddunn.retirementplanner.ui.views.*;
import javafx.geometry.Bounds;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.InputTooltipTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in JavaFX hover checks. Uses demo data, never starts an analysis. */
@EnabledIfSystemProperty(named = "input.tooltips.preview", matches = "true")
class InputTooltipPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void representativeScreensAndRealHoverPopups() throws Exception {
        var folder = Path.of("target/input-tooltip-preview");
        Files.createDirectories(folder);
        var results = new StringBuilder();
        for (String name : List.of("household", "accounts", "pension", "expenses", "assumptions",
                "social-security", "roth", "opening-rmd", "non-investable", "ss-deterministic",
                "ss-weighted", "mc-fixed", "mc-longevity", "mc-comparison")) {
            var preview = fx(() -> create(name));
            try {
                Thread.sleep(200);
                var original = fx(() -> {
                    preview.window().getScene().getRoot().applyCss();
                    preview.window().getScene().getRoot().layout();
                    var control = preview.input();
                    assertNotNull(control.getTooltip());
                    var bounds = control.localToScreen(control.getBoundsInLocal());
                    assertNotNull(bounds);
                    return control.localToScene(control.getBoundsInLocal());
                });
                Thread.sleep(100);
                fx(() -> {
                    // Alternating previews exercise label hover as well as the input.
                    Node hover = preview.input().isDisabled() || name.equals("pension") || name.equals("assumptions")
                            ? labelFor(preview.window().getScene().getRoot(), preview.input()).orElse(preview.input())
                            : preview.input();
                    var b = hover.localToScreen(hover.getBoundsInLocal());
                    // Deliver the same hover event deterministically even on a noninteractive Windows desktop.
                    hover.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, 5, 5, b.getCenterX(), b.getCenterY(),
                            MouseButton.NONE, 0, false, false, false, false, false, false, false,
                            false, false, false, new PickResult(hover, 5, 5)));
                    return null;
                });
                Thread.sleep(800);
                fx(() -> {
                    Tooltip tooltip = preview.input().getTooltip();
                    assertTrue(tooltip.isShowing(), name + " JavaFX hover tooltip");
                    assertTrue(tooltip.getWidth() <= 395, name + " wrapped width " + tooltip.getWidth());
                    assertTrue(tooltip.getHeight() < 330, name + " popup height " + tooltip.getHeight());
                    assertEquals(original, preview.input().localToScene(preview.input().getBoundsInLocal()), name + " unchanged input geometry");
                    var w = preview.window();
                    write(w.getScene().getRoot().snapshot(null, null),
                            folder.resolve(name + ".png"));
                    // Separate actual popup snapshot remains readable even near a window edge.
                    write(tooltip.getScene().getRoot().snapshot(null, null), folder.resolve(name + "-help.png"));
                    results.append(name).append(": JavaFX hover shown; delay 250ms; popup ")
                            .append(tooltip.getWidth()).append(" x ").append(tooltip.getHeight())
                            .append("; input geometry unchanged\n");
                    Node hover = preview.input().isDisabled() || name.equals("pension") || name.equals("assumptions")
                            ? labelFor(w.getScene().getRoot(), preview.input()).orElse(preview.input()) : preview.input();
                    hover.fireEvent(new MouseEvent(MouseEvent.MOUSE_EXITED, 5, 5, 0, 0,
                            MouseButton.NONE, 0, false, false, false, false, false, false, false,
                            false, false, false, new PickResult(hover, 5, 5)));
                    tooltip.hide();
                    return null;
                });
            } finally { fx(() -> { preview.close().run(); preview.window().hide(); return null; }); }
        }
        Files.writeString(folder.resolve("geometry.txt"), results);
    }

    private record Preview(Window window, Control input, Runnable close) { }

    private static Preview create(String name) {
        var p = MonteCarloComparisonFixtures.plan();
        return switch (name) {
            case "household" -> {
                var card = new PersonCard(); card.load(p.getHousehold().getPrimaryPerson());
                yield stage(card, control(card, "birthDatePicker"), () -> { });
            }
            case "accounts" -> {
                var d = new AccountDialog(null);
                ((ComboBox<AccountType>) control(d, "typeCombo")).setValue(AccountType.INHERITED_TRADITIONAL_IRA);
                yield dialog(d, control(d, "beneficiaryRelationshipCombo"));
            }
            case "pension" -> { var d = new PensionDialog(null); yield dialog(d, control(d, "colaRateField")); }
            case "expenses" -> { var d = new ExpenseDialog(null); yield dialog(d, control(d, "annualAmountField")); }
            case "assumptions" -> {
                var v = new AssumptionsView(); v.load(p);
                yield stage(v, control(v, "investmentReturnField"), () -> { });
            }
            case "social-security" -> {
                var d = new SocialSecurityDialog(null, 2027, p.getHousehold());
                yield dialog(d, control(d, "fraBenefitField"));
            }
            case "roth" -> {
                var v = new RothConversionView(); v.load(p);
                yield stage(v, control(v, "strategyComboBox"), () -> { });
            }
            case "opening-rmd" -> {
                var rmdPlan = MonteCarloUiFixtures.plan("10000", 3);
                rmdPlan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1950, 1, 1));
                rmdPlan.getAccountPortfolio().addAccount(new TraditionalIRA("Example IRA", AccountOwnership.PRIMARY, new BigDecimal("10000")));
                var d = new OpeningRmdDialog(rmdPlan);
                Map<?, TextField> fields = field(d, "distributedFields");
                yield dialog(d, fields.values().iterator().next());
            }
            case "non-investable" -> { var d = new NonInvestableAssetDialog(null); yield dialog(d, control(d, "valueField")); }
            case "ss-deterministic", "ss-weighted" -> {
                var d = new SocialSecurityStrategyAnalyzerDialog(null, p);
                Stage w = field(d, "stage");
                TabPane modes = field(d, "modes"); modes.getSelectionModel().select(1);
                var integrated = (VBox) modes.getTabs().get(1).getContent();
                var tabs = (TabPane) integrated.getChildren().getFirst();
                if (name.equals("ss-weighted")) tabs.getSelectionModel().select(1);
                w.setX(30); w.setY(30); w.setWidth(1250); w.setHeight(950); w.show();
                yield new Preview(w, control(d, name.equals("ss-weighted") ? "mortalityDate" : "discountRate"), w::close);
            }
            case "mc-fixed", "mc-longevity" -> {
                var v = new MonteCarloAnalysisView(new Controller(p));
                if (name.equals("mc-longevity")) ((ComboBox<MonteCarloMode>) control(v, "mode")).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                ((ComboBox<String>) control(v, "inflationMode")).setValue("Stochastic");
                yield stage(v, control(v, name.equals("mc-longevity") ? "primaryAdjustment" : "expected"), v::close);
            }
            case "mc-comparison" -> {
                var v = new MonteCarloStrategyComparisonView(new Controller(p));
                ((ComboBox<MonteCarloMode>) control(v, "mode")).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
                ((ComboBox<String>) control(v, "inflation")).setValue("Stochastic");
                yield stage(v, control(v, "inflationFloor"), v::close);
            }
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static Preview stage(Parent content, Control input, Runnable close) {
        var scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        var stage = new Stage(); stage.setX(30); stage.setY(30);
        stage.setScene(new Scene(scroll, 1250, 940)); stage.show();
        return new Preview(stage, input, close);
    }

    private static Preview dialog(Dialog<?> dialog, Control input) {
        dialog.setX(150); dialog.setY(100); dialog.show();
        return new Preview(dialog.getDialogPane().getScene().getWindow(), input, dialog::close);
    }

    private static Optional<Node> labelFor(Node node, Control input) {
        if (node instanceof Label label && label.getLabelFor() == input) return Optional.of(label);
        if (node instanceof Parent parent) for (var child : parent.getChildrenUnmodifiable()) {
            var found = labelFor(child, input); if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static void write(WritableImage image, Path path) throws Exception {
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", path.toFile());
    }
}
