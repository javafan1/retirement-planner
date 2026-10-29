package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.ExpensesView;
import com.daviddunn.retirementplanner.ui.wizard.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.fx;

class ExpenseEscalationUiTest {
    @BeforeAll static void toolkit() throws Exception { NewPlanWizardTestSupport.startFx(); }

    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void editorAndWizardPreserveLegacyChoicesSupportNoneAndRenderBothThemes(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                var owner = new Stage(); owner.setScene(new Scene(new StackPane(), 900, 650)); owner.show();
                try {
                    for (boolean guided : List.of(false, true)) {
                        for (var category : GrowthCategory.values()) {
                            var original = new Expense("Opening-year spending", new BigDecimal("1234.5600"), category,
                                    LocalDate.of(2029, 7, 1), LocalDate.of(2045, 12, 31), ExpenseType.RECURRING);
                            var dialog = new ExpenseDialog(original, guided); dialog.initOwner(owner); dialog.show();
                            try {
                                var selector = field(dialog, "growthCategoryComboBox", ComboBox.class);
                                assertEquals(List.of(GrowthCategory.GENERAL, GrowthCategory.HEALTHCARE, GrowthCategory.NONE), selector.getItems());
                                assertEquals(category, selector.getValue());
                                assertEquals(category.displayName(), selector.getConverter().toString(category));
                                assertTrue(selector.getTooltip().getText().contains("once"));
                                var pane = dialog.getDialogPane(); pane.applyCss(); pane.layout();
                                assertTrue(selector.getWidth() > 100);
                                assertTrue(selector.getBoundsInParent().getWidth() >= selector.prefWidth(-1) - 1);
                                save(pane, "expense-" + theme + "-" + category + "-" + guided + ".png");
                                ((Button) pane.lookupButton(ButtonType.OK)).fire();
                                var result = dialog.getResult(); assertNotNull(result);
                                assertEquals(category, result.getGrowthCategory());
                                assertEquals(original.getAnnualAmount(), result.getAnnualAmount());
                                assertEquals(original.getStartDate(), result.getStartDate());
                                assertEquals(original.getEndDate(), result.getEndDate());
                            } finally { dialog.close(); }
                        }
                    }
                    var original = new Expense("Healthcare", new BigDecimal("1000.0000"), GrowthCategory.HEALTHCARE);
                    var canceled = new ExpenseDialog(original, true); canceled.initOwner(owner); canceled.show();
                    field(canceled, "growthCategoryComboBox", ComboBox.class).setValue(GrowthCategory.NONE);
                    ((Button) canceled.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                    assertNull(canceled.getResult()); assertEquals(GrowthCategory.HEALTHCARE, original.getGrowthCategory());
                    var changed = new ExpenseDialog(original, true); changed.initOwner(owner); changed.show();
                    field(changed, "growthCategoryComboBox", ComboBox.class).setValue(GrowthCategory.NONE);
                    ((Button) changed.getDialogPane().lookupButton(ButtonType.OK)).fire();
                    assertEquals(GrowthCategory.NONE, changed.getResult().getGrowthCategory());
                    assertEquals(original.getAnnualAmount(), changed.getResult().getAnnualAmount());
                } finally { owner.hide(); }
            }
        });
    }

    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void defaultGeneralOneTimeControlsAndListsUseCorrectEscalationLabels(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                var dialog = new ExpenseDialog(null, true); dialog.show();
                try {
                    var selector = field(dialog, "growthCategoryComboBox", ComboBox.class);
                    assertEquals(GrowthCategory.GENERAL, selector.getValue());
                    selector.setValue(GrowthCategory.NONE);
                    field(dialog, "expenseTypeComboBox", ComboBox.class).setValue(ExpenseType.ONE_TIME);
                    assertFalse(selector.isVisible()); assertFalse(selector.isManaged());
                    field(dialog, "descriptionField", TextField.class).setText("Purchase");
                    field(dialog, "annualAmountField", TextField.class).setText("123.4500");
                    field(dialog, "effectiveDatePicker", DatePicker.class).setValue(LocalDate.of(2030, 1, 1));
                    ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                    assertEquals(GrowthCategory.GENERAL, dialog.getResult().getGrowthCategory());
                    assertEquals(ExpenseType.ONE_TIME, dialog.getResult().getExpenseType());
                } finally { dialog.close(); }
                var draft = new NewPlanDraft();
                var expense = new Expense("Fixed nominal", new BigDecimal("4567.8900"), GrowthCategory.NONE);
                draft.getPlan().getHousehold().addExpense(expense);
                var wizard = new ExpensesWizardStep(draft);
                var table = (TableView<?>) wizard.content().lookup("#wizard-expenses");
                table.getSelectionModel().select(0);
                var details = (Label) wizard.content().lookup("#wizard-spending-details");
                assertTrue(details.getText().contains("No Inflation"));
                var column = table.getColumns().stream().filter(c -> c.getText().equals("Escalation")).findFirst().orElseThrow();
                assertEquals("No Inflation", column.getCellData(0));
                var view = new ExpensesView(); view.load(draft.getPlan());
                var expenseTable = field(view, "table", TableView.class);
                var expenseColumn = (TableColumn<?, ?>) expenseTable.getColumns().stream().filter(c -> ((TableColumn<?, ?>) c).getText().equals("Escalation")).findFirst().orElseThrow();
                assertEquals("No Inflation", expenseColumn.getCellData(0));
                assertSame(expense, draft.getPlan().getHousehold().getExpenses().getFirst());
            }
        });
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
    }

    private static void save(DialogPane pane, String name) throws Exception {
        var image = pane.snapshot(null, null);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/phase14a/previews"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(pixels, "png", directory.resolve(name).toFile());
    }
}
