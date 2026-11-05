package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import static com.daviddunn.retirementplanner.ui.views.AssumptionsViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RothConversionViewTest {
    @BeforeAll static void startFx() throws Exception { AssumptionsViewTest.startFx(); }

    @Test void requiredIndicatorsFollowEnabledStrategyWithoutChangingLabels() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            Label strategy = (Label) control(f.view, "strategyLabel");
            Label year = (Label) control(f.view, "conversionYearLabel");
            Label amount = (Label) control(f.view, "conversionAmountLabel");
            Label target = (Label) control(f.view, "targetTaxableIncomeLabel");
            Label frequency = (Label) control(f.view, "frequencyLabel");
            Label stop = (Label) control(f.view, "stopRuleLabel");
            CheckBox enabled = (CheckBox) control(f.view, "enabledCheckBox");
            Label legend = (Label) f.view.getChildren().stream()
                    .filter(Label.class::isInstance).filter(node -> ((Label) node).getStyleClass().contains("required-field-legend"))
                    .findFirst().orElseThrow();
            assertTrue(marked(strategy));
            assertTrue(legend.isVisible());
            enabled.setSelected(false);
            assertFalse(marked(strategy));
            assertFalse(legend.isVisible());
            enabled.setSelected(true);
            assertTrue(marked(strategy));
            assertTrue(legend.isVisible());
            assertTrue(marked(year));
            assertTrue(marked(frequency));
            assertTrue(marked(stop));
            assertTrue(marked(amount));
            assertFalse(marked(target));
            ((ComboBox<RothConversionStrategy>) control(f.view, "strategyComboBox"))
                    .setValue(RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET);
            assertFalse(marked(amount));
            assertTrue(marked(target));
            assertEquals("Target Taxable Income:", target.getText());
            ((CheckBox) control(f.view, "enabledCheckBox")).setSelected(false);
            assertFalse(marked(strategy));
            assertFalse(marked(target));
            assertFalse(legend.isVisible());
        });
    }

    private static boolean marked(Label label) {
        javafx.scene.text.Text marker = requiredMarker(label);
        return marker != null && marker.isVisible();
    }

    private static javafx.scene.text.Text requiredMarker(Label label) {
        if (!(label.getGraphic() instanceof javafx.scene.text.TextFlow flow)) return null;
        return flow.getChildren().stream().filter(javafx.scene.text.Text.class::isInstance)
                .map(javafx.scene.text.Text.class::cast)
                .filter(text -> text.getStyleClass().contains("required-field-indicator"))
                .findFirst().orElse(null);
    }

    @Test void initialCleanAndEquivalentApplyDoesNotNotify() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            clean(f.view);
            field(f.view, "conversionYearField").setText(" 02026 ");
            field(f.view, "conversionAmountField").setText("1000.000");
            clean(f.view);
            assertTrue(f.view.applyChanges());
            assertSame(f.original, f.request());
            assertEquals(0, f.events.get());
            assertFalse(f.controller.isModified());
        });
    }

    @Test void workspaceApplyCancelCallbacksRunOnlyAfterTheirExistingTransactions() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            AtomicInteger navigation = new AtomicInteger();
            f.view.setOnWorkspaceEditCompleted(navigation::incrementAndGet);
            field(f.view, "conversionYearField").setText("2027");
            ((Button) control(f.view, "applyButton")).fire();
            assertEquals(1, navigation.get());
            assertEquals(2027, f.request().getStartYear());

            field(f.view, "conversionYearField").setText("not a year");
            ((Button) control(f.view, "applyButton")).fire();
            assertEquals(1, navigation.get(), "Failed Apply must not leave the workspace.");
            assertEquals("not a year", field(f.view, "conversionYearField").getText());

            field(f.view, "conversionYearField").setText("2028");
            ((Button) control(f.view, "cancelButton")).fire();
            assertEquals(2, navigation.get());
            assertEquals(2027, f.request().getStartYear(), "Cancel restores the last accepted request.");
        });
    }

    @ParameterizedTest
    @CsvSource({"conversionYearField,2027,2026", "conversionAmountField,2000,1000"})
    void editsAndManualRevertsUseTypedComparison(String name, String changed, String original) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, name).setText(changed);
            dirty(f.view);
            assertSame(f.original, f.request());
            field(f.view, name).setText(original);
            clean(f.view);
        });
    }

    @ParameterizedTest @EnumSource(RothConversionStrategy.class)
    void everyStrategyAppliesAndCancels(RothConversionStrategy strategy) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            combo(f.view, "strategyComboBox").setValue(strategy);
            field(f.view, "conversionYearField").setText("2028");
            field(f.view, "conversionAmountField").setText("2500");
            field(f.view, "targetTaxableIncomeField").setText("125000");
            combo(f.view, "frequencyComboBox").setValue(RothConversionFrequency.ANNUAL);
            combo(f.view, "stopRuleComboBox").setValue(RothConversionStopRule.NEVER);
            dirty(f.view);
            assertTrue(f.view.applyChanges());
            assertEquals(strategy, f.request().getStrategy());
            assertEquals(2028, f.request().getStartYear());
            assertEquals(RothConversionFrequency.ANNUAL, f.request().getFrequency());
            assertEquals(RothConversionStopRule.NEVER, f.request().getStopRule());
            if (strategy == RothConversionStrategy.FIXED_AMOUNT) {
                assertEquals(0, new BigDecimal("2500").compareTo(f.request().getAnnualAmount()));
            }
            if (strategy == RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET) {
                assertEquals(0, new BigDecimal("125000").compareTo(f.request().getCustomTargetTaxableIncome()));
                field(f.view, "targetTaxableIncomeField").setText("125000.00");
                clean(f.view);
            }
            assertEquals(1, f.events.get());
            clean(f.view);
            var applied = f.request();
            field(f.view, "conversionYearField").setText("invalid");
            assertFalse(f.view.applyChanges());
            dirty(f.view);
            f.view.cancelChanges();
            assertSame(applied, f.request());
            assertEquals("2028", field(f.view, "conversionYearField").getText());
            assertTrue(f.controller.isModified());
            assertEquals(1, f.events.get());
            assertEquals("", ((Label) control(f.view, "statusLabel")).getText());
            clean(f.view);
        });
    }

    @ParameterizedTest
    @CsvSource({"conversionYearField,1899", "conversionYearField,bad", "conversionAmountField,-1",
            "conversionAmountField,bad", "targetTaxableIncomeField,-1", "targetTaxableIncomeField,bad"})
    void invalidSnapshotNeverMutatesOrNotifies(String name, String value) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            new Scene(f.view);
            field(f.view, "conversionYearField").setText("2030");
            if (name.equals("targetTaxableIncomeField")) {
                combo(f.view, "strategyComboBox").setValue(RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET);
            }
            field(f.view, name).setText(value);
            ((Button) control(f.view, "applyButton")).fire();
            assertSame(f.original, f.request());
            assertEquals(0, f.events.get());
            assertFalse(f.controller.isModified());
            assertTrue(((Label) control(f.view, "statusLabel")).getText().length() > 0);
            assertSame(field(f.view, name), f.view.getScene().getFocusOwner());
            assertEquals(value, field(f.view, name).getText());
            dirty(f.view);
            ((Button) control(f.view, "cancelButton")).fire();
            clean(f.view);
            assertEquals("", ((Label) control(f.view, "statusLabel")).getText());
        });
    }

    @ParameterizedTest @CsvSource({"strategyComboBox", "frequencyComboBox", "stopRuleComboBox"})
    void missingSelectionBlocksEntireApply(String name) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, "conversionYearField").setText("2030");
            combo(f.view, name).setValue(null);
            assertFalse(f.view.applyChanges());
            assertSame(f.original, f.request());
            assertEquals(0, f.events.get());
            dirty(f.view);
        });
    }

    @Test void disableAndReenableRevertsAndExplicitDisablePreservesConfiguration() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            CheckBox enabled = control(f.view, "enabledCheckBox");
            enabled.setSelected(false);
            dirty(f.view);
            assertTrue(field(f.view, "conversionYearField").isDisabled());
            enabled.setSelected(true);
            clean(f.view);
            enabled.setSelected(false);
            field(f.view, "conversionYearField").setText("invalid");
            assertTrue(f.view.applyChanges());
            assertFalse(f.request().isEnabled());
            assertEquals(f.original.getAnnualAmount(), f.request().getAnnualAmount());
            assertEquals(f.original.getStartYear(), f.request().getStartYear());
            clean(f.view);
            assertEquals(1, f.events.get());
        });
    }

    @Test void dormantFieldsDoNotCreateChangesAndDisabledPersistedRequestSurvivesSave() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, "targetTaxableIncomeField").setText("invalid");
            clean(f.view);
            var disabled = new RothConversionRequest(false, 2026, new BigDecimal("999"),
                    RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL);
            f.controller.getCurrentPlan().setRothConversionRequest(disabled);
            f.view.load(f.controller.getCurrentPlan());
            field(f.view, "conversionAmountField").setText("invalid");
            clean(f.view);
            assertTrue(f.view.save(f.controller.getCurrentPlan()));
            assertSame(disabled, f.request());
            assertEquals(0, f.events.get());
        });
    }

    @Test void refreshPreservesDraftAndReplacementResetsIt() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, "conversionYearField").setText("invalid");
            f.view.refresh(f.controller.getCurrentPlan());
            assertEquals("invalid", field(f.view, "conversionYearField").getText());
            dirty(f.view);
            f.controller.newPlan();
            f.view.refresh(f.controller.getCurrentPlan());
            clean(f.view);
            assertFalse(((CheckBox) control(f.view, "enabledCheckBox")).isSelected());
        });
    }

    @Test void cancelRestoresEveryControlAndSelectionRevertsAreClean() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            combo(f.view, "frequencyComboBox").setValue(RothConversionFrequency.ANNUAL);
            dirty(f.view);
            combo(f.view, "frequencyComboBox").setValue(RothConversionFrequency.ONE_TIME);
            clean(f.view);
            combo(f.view, "stopRuleComboBox").setValue(RothConversionStopRule.NEVER);
            dirty(f.view);
            combo(f.view, "stopRuleComboBox").setValue(RothConversionStopRule.FIRST_HOUSEHOLD_RMD);
            clean(f.view);
            combo(f.view, "strategyComboBox").setValue(RothConversionStrategy.FILL_22_PERCENT_BRACKET);
            dirty(f.view);
            combo(f.view, "strategyComboBox").setValue(RothConversionStrategy.FIXED_AMOUNT);
            clean(f.view);
            field(f.view, "conversionYearField").setText("2031");
            field(f.view, "conversionAmountField").setText("123");
            field(f.view, "targetTaxableIncomeField").setText("456");
            combo(f.view, "strategyComboBox").setValue(RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET);
            combo(f.view, "frequencyComboBox").setValue(RothConversionFrequency.ANNUAL);
            combo(f.view, "stopRuleComboBox").setValue(RothConversionStopRule.NEVER);
            ((CheckBox) control(f.view, "enabledCheckBox")).setSelected(false);
            f.view.cancelChanges();
            assertTrue(((CheckBox) control(f.view, "enabledCheckBox")).isSelected());
            assertEquals("2026", field(f.view, "conversionYearField").getText());
            assertEquals("1000", field(f.view, "conversionAmountField").getText());
            assertEquals("", field(f.view, "targetTaxableIncomeField").getText());
            assertEquals(RothConversionStrategy.FIXED_AMOUNT, combo(f.view, "strategyComboBox").getValue());
            assertEquals(RothConversionFrequency.ONE_TIME, combo(f.view, "frequencyComboBox").getValue());
            assertEquals(RothConversionStopRule.FIRST_HOUSEHOLD_RMD, combo(f.view, "stopRuleComboBox").getValue());
            assertSame(f.original, f.request());
            assertFalse(f.controller.isModified());
            clean(f.view);
        });
    }

    @Test void customCancelRestoresTargetAndDormantBracketAmountSurvivesUnrelatedApply() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var plan = f.controller.getCurrentPlan();
            plan.setRothConversionRequest(new RothConversionRequest(true, 2026, new BigDecimal("999"),
                    RothConversionStopRule.NEVER, RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,
                    RothConversionFrequency.ANNUAL, new BigDecimal("125000")));
            f.view.load(plan);
            clean(f.view);
            field(f.view, "targetTaxableIncomeField").setText("126000");
            dirty(f.view);
            field(f.view, "targetTaxableIncomeField").setText("125000.00");
            clean(f.view);
            field(f.view, "targetTaxableIncomeField").setText("invalid");
            assertFalse(f.view.applyChanges());
            f.view.cancelChanges();
            assertEquals("125000", field(f.view, "targetTaxableIncomeField").getText());
            clean(f.view);
            field(f.view, "conversionYearField").setText("2027");
            assertTrue(f.view.applyChanges());
            assertEquals(new BigDecimal("999"), f.request().getAnnualAmount());
            clean(f.view);
        });
    }

    static ComboBox<Object> combo(Object view, String name) throws Exception { return control(view, name); }
    static void clean(RothConversionView view) throws Exception {
        assertFalse(view.isDirty());
        assertFalse(view.dirtyProperty().get());
        assertTrue(((Button) control(view, "applyButton")).isDisabled());
        assertTrue(((Button) control(view, "cancelButton")).isDisabled());
    }
    static void dirty(RothConversionView view) throws Exception {
        assertTrue(view.isDirty());
        assertTrue(view.dirtyProperty().get());
        assertFalse(((Button) control(view, "applyButton")).isDisabled());
        assertFalse(((Button) control(view, "cancelButton")).isDisabled());
    }
    static class Fixture {
        final ApplicationController controller = new ApplicationController();
        final RothConversionView view = new RothConversionView();
        final AtomicInteger events = new AtomicInteger();
        final RothConversionRequest original = new RothConversionRequest(true, 2026, new BigDecimal("1000"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT,
                RothConversionFrequency.ONE_TIME);
        Fixture() {
            controller.getCurrentPlan().getAccountPortfolio().addAccount(
                    new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                            "Supported source", com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, BigDecimal.ZERO));
            controller.getCurrentPlan().setRothConversionRequest(original);
            view.load(controller.getCurrentPlan());
            view.setOnPlanChanged(() -> {
                events.incrementAndGet();
                controller.markModified();
                controller.invalidateProjection();
            });
        }
        RothConversionRequest request() { return controller.getCurrentPlan().getRothConversionRequest(); }
    }
}
