package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseVolatilityUiTest {
    @BeforeAll static void toolkit() throws Exception { NewPlanWizardTestSupport.startFx(); }

    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void normalAndGuidedEditorsPreserveImportedPrecisionAndRender(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                for (boolean guided : new boolean[]{false, true}) {
                    var original = new Expense("Recurring household spending", new BigDecimal("12000.00"), GrowthCategory.HEALTHCARE,
                            null, null, ExpenseType.RECURRING, new BigDecimal("0.123456789000"));
                    var dialog = new ExpenseDialog(original, guided);
                    dialog.show();
                    try {
                        TextField volatility = field(dialog, "volatilityField");
                        assertEquals("12.3456789000", volatility.getText());
                        assertNotNull(volatility.getTooltip());
                        assertTrue(volatility.getTooltip().getText().contains("not a maximum"));
                        assertTrue(volatility.getTooltip().getText().contains("expected escalated annual amount"));
                        assertTrue(volatility.getTooltip().getText().contains("unbounded positive tail"));
                        assertFalse(volatility.getTooltip().getText().contains("Set annual expense volatility to 0%"));
                        dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                        save(dialog.getDialogPane(), theme + (guided ? "-guided" : "-standard"));
                        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                        assertEquals(original.getAnnualExpenseVolatility(), dialog.getResult().getAnnualExpenseVolatility());
                        assertEquals(GrowthCategory.HEALTHCARE, dialog.getResult().getGrowthCategory());
                    } finally { dialog.close(); }
                }
            }
        });
    }

    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void guidedValidationAndVisibleOneTimeClearing(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                var dialog = new ExpenseDialog(new Expense("Spending", BigDecimal.TEN), true);
                dialog.show();
                try {
                    TextField volatility = field(dialog, "volatilityField");
                    var ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
                    for (String invalid : new String[]{"-1", "100.01", "1.234", "NaN"}) {
                        volatility.setText(invalid); ok.fire(); assertNull(dialog.getResult()); assertTrue(dialog.isShowing());
                    }
                    volatility.setText("25.50");
                    ComboBox<ExpenseType> type = field(dialog, "expenseTypeComboBox"); type.setValue(ExpenseType.ONE_TIME);
                    assertEquals("0", volatility.getText()); assertFalse(volatility.isVisible());
                    Label notice = field(dialog, "volatilityNotice"); assertTrue(notice.getText().contains("was cleared"));
                    type.setValue(ExpenseType.RECURRING); assertTrue(volatility.isVisible());
                    volatility.setText("25.50"); ok.fire();
                    assertEquals(new BigDecimal("0.2550"), dialog.getResult().getAnnualExpenseVolatility());
                } finally { dialog.close(); }
                var negative = new ExpenseDialog(new Expense("Legacy credit", BigDecimal.TEN.negate()), true);
                negative.show();
                try {
                    TextField volatility = field(negative, "volatilityField"); assertTrue(volatility.isDisabled());
                    Label notice = field(negative, "volatilityNotice"); assertTrue(notice.getText().contains("Negative legacy"));
                    ((Button) negative.getDialogPane().lookupButton(ButtonType.CANCEL)).fire(); assertNull(negative.getResult());
                } finally { negative.close(); }
            }
        });
    }

    private static void save(DialogPane pane, String name) throws Exception {
        var image = pane.snapshot(null, null);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/phase14b3a/previews"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(pixels, "png", directory.resolve(name + ".png").toFile());
    }
}
