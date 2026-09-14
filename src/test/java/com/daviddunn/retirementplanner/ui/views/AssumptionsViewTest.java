package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AssumptionsViewTest {
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void startFx() throws Exception {
        FutureTask<Void> startup = new FutureTask<>(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
        try {
            Platform.startup(startup);
        }
        catch (IllegalStateException alreadyStarted) {
            Platform.runLater(startup);
        }
        startup.get(20, TimeUnit.SECONDS);
    }

    @Test
    void initialAndUnloadedStateAreClean() throws Exception {
        fx(() -> {
            clean(new AssumptionsView());
            clean(new Fixture().view);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "projectionLengthField,31", "deathYearField,2045",
            "postDeathExpenseFactorField,75", "investmentReturnField,6.25",
            "inflationRateField,3.75", "healthcareInflationField,4.25",
            "socialSecurityColaField,2.75", "federalBracketGrowthField,3.25",
            "standardDeductionGrowthField,3.25", "futureFederalMarginalRateAdjustmentField,2",
            "futureFederalMarginalRateEffectiveYearField,2035", "stateIncomeTaxRateField,4",
            "localIncomeTaxRateField,1", "estimatedHeirTaxRateField,30"
    })
    void everyTextFieldTracksEditsAndManualRevert(String name, String value) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            TextField field = field(f.view, name);
            String original = field.getText();
            PlanningAssumptions before = f.assumptions();
            field.setText(value);
            dirty(f.view);
            assertSame(before, f.assumptions());
            assertFalse(f.controller.isModified());
            field.setText(original);
            clean(f.view);
            assertEquals(0, f.notifications.get());
        });
    }

    @Test
    void dateAndBothSelectionsTrackEditsAndRevert() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            DatePicker date = control(f.view, "projectionStartDatePicker");
            LocalDate originalDate = date.getValue();
            date.setValue(originalDate.plusDays(1));
            dirty(f.view);
            date.setValue(originalDate);
            clean(f.view);
            date.getEditor().setText("bad date");
            dirty(f.view);
            assertFalse(f.view.applyChanges());
            assertTrue(status(f.view).getText().contains("Projection start date"));
            date.getEditor().setText(date.getConverter().toString(originalDate));
            clean(f.view);
            ComboBox<DeathScenario> scenario = control(f.view, "deathScenarioComboBox");
            scenario.setValue(DeathScenario.SPOUSE_DIES);
            dirty(f.view);
            scenario.setValue(DeathScenario.PRIMARY_DIES);
            clean(f.view);
            ComboBox<Integer> age = control(f.view, "survivorClaimingAgeComboBox");
            age.setValue(68);
            dirty(f.view);
            age.setValue(67);
            clean(f.view);
        });
    }

    @Test
    void equivalentNumbersAndNoOpApplyDoNotNotifyOrReplaceModel() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            PlanningAssumptions before = f.assumptions();
            TextField rate = field(f.view, "inflationRateField");
            rate.setText("  " + new BigDecimal(rate.getText()).setScale(4).toPlainString() + " ");
            field(f.view, "projectionLengthField").setText("0" + f.assumptions().getProjectionLengthYears());
            clean(f.view);
            assertTrue(f.view.applyChanges());
            assertSame(before, f.assumptions());
            assertEquals(0, f.notifications.get());
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void validApplyWritesEveryFieldAndNotifiesAndInvalidatesExactlyOnce() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            PlanningAssumptions before = f.assumptions();
            editAll(f.view);
            dirty(f.view);
            button(f.view, "applyButton").fire();
            PlanningAssumptions a = f.assumptions();
            assertEquals(LocalDate.of(2027, 2, 3), a.getProjectionStartDate());
            assertEquals(31, a.getProjectionLengthYears());
            assertRate("0.0625", a.getEconomicAssumptions().getExpectedAnnualInvestmentReturn());
            assertRate("0.0375", a.getEconomicAssumptions().getGeneralInflationRate());
            assertRate("0.0425", a.getEconomicAssumptions().getHealthcareInflationRate());
            assertRate("0.0275", a.getEconomicAssumptions().getSocialSecurityColaRate());
            assertRate("0.0325", a.getTaxAssumptions().getFederalTaxBracketGrowthRate());
            assertRate("0.0325", a.getTaxAssumptions().getStandardDeductionGrowthRate());
            assertRate("0.02", a.getTaxAssumptions().getFutureFederalMarginalRateAdjustment());
            assertEquals(2035, a.getTaxAssumptions().getFutureFederalMarginalRateEffectiveYear());
            assertRate("0.04", a.getTaxAssumptions().getStateIncomeTaxRate());
            assertRate("0.01", a.getTaxAssumptions().getLocalIncomeTaxRate());
            assertRate("0.30", a.getTaxAssumptions().getEstimatedHeirTaxRateOnTaxDeferredAssets());
            assertEquals(DeathScenario.SPOUSE_DIES, a.getDeathScenarioAssumptions().getDeathScenario());
            assertEquals(2045, a.getDeathScenarioAssumptions().getDeathYear());
            assertEquals(68, a.getDeathScenarioAssumptions().getSurvivorClaimingAge());
            assertRate("0.75", a.getDeathScenarioAssumptions().getPostDeathExpenseFactor());
            assertSame(before.getWithdrawalAssumptions(), a.getWithdrawalAssumptions());
            assertEquals(before.getTaxAssumptions().getFilingStatus(), a.getTaxAssumptions().getFilingStatus());
            clean(f.view);
            assertEquals(1, f.notifications.get());
            assertEquals(1, f.controller.invalidations);
            assertEquals(1, f.revisions.get());
            assertTrue(f.controller.isModified());
            assertFalse(f.controller.hasCurrentFile());
            assertTrue(f.view.applyChanges());
            assertEquals(1, f.notifications.get());
        });
    }

    @ParameterizedTest
    @CsvSource({
            "estimatedHeirTaxRateField,invalid", "estimatedHeirTaxRateField,101",
            "postDeathExpenseFactorField,-1", "postDeathExpenseFactorField,101",
            "projectionLengthField,0", "deathYearField,0",
            "futureFederalMarginalRateEffectiveYearField,-1"
    })
    void invalidFieldRejectsEntireApplyAndSaveWithoutRevision(String name, String value) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            PlanningAssumptions before = f.assumptions();
            field(f.view, "investmentReturnField").setText("8.25");
            if (name.startsWith("future")) {
                field(f.view, "futureFederalMarginalRateAdjustmentField").setText("2");
            }
            field(f.view, name).setText(value);
            assertFalse(f.view.applyChanges());
            assertFalse(f.view.save(f.plan()));
            assertSame(before, f.assumptions());
            assertFalse(f.controller.isModified());
            assertEquals(0, f.notifications.get());
            assertEquals(0, f.controller.invalidations);
            assertEquals(0, f.revisions.get());
            assertEquals("8.25", field(f.view, "investmentReturnField").getText());
            assertEquals(value, field(f.view, name).getText());
            assertFalse(status(f.view).getText().isEmpty());
            dirty(f.view);
            button(f.view, "cancelButton").fire();
            clean(f.view);
            assertEquals("", status(f.view).getText());
            assertSame(before, f.assumptions());
        });
    }

    @Test
    void missingDateScenarioAndSurvivorAgeAreRejected() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            DatePicker date = control(f.view, "projectionStartDatePicker");
            date.getEditor().clear();
            assertFalse(f.view.applyChanges());
            f.view.cancelChanges();
            ComboBox<DeathScenario> scenario = control(f.view, "deathScenarioComboBox");
            scenario.setValue(null);
            assertFalse(f.view.applyChanges());
            f.view.cancelChanges();
            ComboBox<Integer> age = control(f.view, "survivorClaimingAgeComboBox");
            age.setValue(null);
            assertFalse(f.view.applyChanges());
            assertEquals(0, f.notifications.get());
        });
    }

    @Test
    void futureRatePairSupportsEquivalentInputAndClearing() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, "futureFederalMarginalRateAdjustmentField").setText("2%");
            assertFalse(f.view.applyChanges());
            field(f.view, "futureFederalMarginalRateEffectiveYearField").setText("2035");
            assertTrue(f.view.applyChanges());
            field(f.view, "futureFederalMarginalRateAdjustmentField").setText(" 2.000% ");
            clean(f.view);
            field(f.view, "futureFederalMarginalRateAdjustmentField").clear();
            field(f.view, "futureFederalMarginalRateEffectiveYearField").clear();
            assertTrue(f.view.applyChanges());
            assertNull(f.assumptions().getTaxAssumptions().getFutureFederalMarginalRateAdjustment());
            assertNull(f.assumptions().getTaxAssumptions().getFutureFederalMarginalRateEffectiveYear());
            clean(f.view);
        });
    }

    @Test
    void cancelRestoresAllControlsWithoutModelOrApplicationChanges() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            PlanningAssumptions before = f.assumptions();
            editAll(f.view);
            f.view.cancelChanges();
            clean(f.view);
            assertSame(before, f.assumptions());
            assertFalse(f.controller.isModified());
            assertEquals(0, f.notifications.get());
            assertEquals("", status(f.view).getText());
            // Clean requires every restored control to numerically match the original model.
            assertEquals(before.getProjectionStartDate(),
                    ((DatePicker) control(f.view, "projectionStartDatePicker")).getValue());
        });
    }

    @Test
    void applyThenEditThenCancelPreservesAppliedValuesAndAppDirty() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, "inflationRateField").setText("3.75");
            assertTrue(f.view.applyChanges());
            field(f.view, "inflationRateField").setText("5");
            f.view.cancelChanges();
            assertEquals("3.75", field(f.view, "inflationRateField").getText());
            assertRate("0.0375", f.assumptions().getEconomicAssumptions().getGeneralInflationRate());
            clean(f.view);
            assertTrue(f.controller.isModified());
            assertEquals(1, f.notifications.get());
        });
    }

    @Test
    void navigationAndRefreshPreserveDraftsAndReplacementClearsThem() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            TabPane tabs = new TabPane(new Tab("Assumptions", f.view), new Tab("Other"));
            field(f.view, "inflationRateField").setText("invalid draft");
            assertFalse(f.view.applyChanges());
            String feedback = status(f.view).getText();
            tabs.getSelectionModel().select(1);
            f.view.refresh(f.plan());
            tabs.getSelectionModel().select(0);
            assertEquals("invalid draft", field(f.view, "inflationRateField").getText());
            assertEquals(feedback, status(f.view).getText());
            dirty(f.view);
            f.controller.newPlan();
            f.view.refresh(f.plan());
            clean(f.view);
            assertEquals("", status(f.view).getText());
            assertNotEquals("invalid draft", field(f.view, "inflationRateField").getText());
        });
    }

    @Test
    void savePersistsPendingValidEditsAndReopenResetsBaseline() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            Path file = temporaryDirectory.resolve("assumptions.json");
            f.controller.saveAs(file);
            String originalJson = Files.readString(file);
            field(f.view, "inflationRateField").setText("3.75");
            assertTrue(f.view.applyChanges());
            assertEquals(originalJson, Files.readString(file));
            field(f.view, "inflationRateField").setText("4.25");
            assertTrue(f.view.save(f.plan()));
            f.controller.save();
            assertFalse(f.controller.isModified());
            clean(f.view);
            field(f.view, "inflationRateField").setText("bad");
            f.controller.open(file);
            f.view.load(f.plan());
            assertEquals("4.25", field(f.view, "inflationRateField").getText());
            clean(f.view);
        });
    }

    @Test
    void defaultBothSurviveAndStoredDormantPolicyLoadAndSaveWithoutChanges() throws Exception {
        fx(() -> {
            ApplicationController controller = new ApplicationController();
            AssumptionsView view = new AssumptionsView();
            view.load(controller.getCurrentPlan());
            clean(view);
            PlanningAssumptions before = controller.getCurrentPlan().getPlanningAssumptions();
            assertTrue(view.save(controller.getCurrentPlan()));
            assertSame(before, controller.getCurrentPlan().getPlanningAssumptions());
            controller.getCurrentPlan().setPlanningAssumptions(new PlanningAssumptions(
                    before.getEconomicAssumptions(), before.getTaxAssumptions(), before.getWithdrawalAssumptions(),
                    new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 68, BigDecimal.ONE),
                    before.getProjectionLengthYears(), before.getProjectionStartDate()));
            view.load(controller.getCurrentPlan());
            clean(view);
            assertTrue(view.save(controller.getCurrentPlan()));
            assertEquals(68, controller.getCurrentPlan().getPlanningAssumptions()
                    .getDeathScenarioAssumptions().getSurvivorClaimingAge());
        });
    }

    @Test
    void untouchedHighPrecisionPersistedRateRemainsCleanAndIsPreservedOnUnrelatedApply() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            PlanningAssumptions a = f.assumptions();
            TaxAssumptions tax = a.getTaxAssumptions();
            BigDecimal exactRate = new BigDecimal("0.012345678912345");
            f.plan().setPlanningAssumptions(new PlanningAssumptions(
                    a.getEconomicAssumptions(),
                    new TaxAssumptions(tax.getFederalTaxBracketGrowthRate(),
                            tax.getStandardDeductionGrowthRate(), tax.getStateIncomeTaxRate(),
                            tax.getLocalIncomeTaxRate(), tax.getFilingStatus(),
                            tax.getEstimatedHeirTaxRateOnTaxDeferredAssets(), exactRate, 2035),
                    a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(),
                    a.getProjectionLengthYears(), a.getProjectionStartDate()));
            f.view.load(f.plan());
            clean(f.view);
            assertTrue(f.view.save(f.plan()));
            assertEquals(0, f.notifications.get());
            field(f.view, "inflationRateField").setText("3.75");
            assertTrue(f.view.applyChanges());
            assertEquals(exactRate, f.assumptions().getTaxAssumptions().getFutureFederalMarginalRateAdjustment());
            clean(f.view);
        });
    }

    @Test
    void samePlanRefreshComparesDraftToLatestAppliedValues() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            field(f.view, "inflationRateField").setText("3.75");
            AssumptionsView otherEditor = new AssumptionsView();
            otherEditor.load(f.plan());
            field(otherEditor, "inflationRateField").setText("3.75");
            assertTrue(otherEditor.applyChanges());
            f.view.refresh(f.plan());
            clean(f.view);
            assertEquals("3.75", field(f.view, "inflationRateField").getText());
            assertTrue(f.view.applyChanges());
            assertEquals(0, f.notifications.get());
        });
    }

    @Test
    void switchingDeathScenarioOffClearsYearAndAgeOnlyOnApply() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            ComboBox<DeathScenario> scenario = control(f.view, "deathScenarioComboBox");
            scenario.setValue(DeathScenario.BOTH_SURVIVE);
            assertEquals(2040, f.assumptions().getDeathScenarioAssumptions().getDeathYear());
            dirty(f.view);
            scenario.setValue(DeathScenario.PRIMARY_DIES);
            clean(f.view);
            scenario.setValue(DeathScenario.BOTH_SURVIVE);
            assertTrue(f.view.applyChanges());
            assertNull(f.assumptions().getDeathScenarioAssumptions().getDeathYear());
            assertNull(f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            clean(f.view);
        });
    }

    static void editAll(AssumptionsView view) throws Exception {
        DatePicker date = control(view, "projectionStartDatePicker");
        date.getEditor().setText(date.getConverter().toString(LocalDate.of(2027, 2, 3)));
        String[][] edits = {
                {"projectionLengthField", "31"}, {"deathYearField", "2045"},
                {"postDeathExpenseFactorField", "75"}, {"investmentReturnField", "6.25"},
                {"inflationRateField", "3.75"}, {"healthcareInflationField", "4.25"},
                {"socialSecurityColaField", "2.75"}, {"federalBracketGrowthField", "3.25"},
                {"standardDeductionGrowthField", "3.25"}, {"futureFederalMarginalRateAdjustmentField", "2"},
                {"futureFederalMarginalRateEffectiveYearField", "2035"}, {"stateIncomeTaxRateField", "4"},
                {"localIncomeTaxRateField", "1"}, {"estimatedHeirTaxRateField", "30"}
        };
        for (String[] edit : edits) {
            field(view, edit[0]).setText(edit[1]);
        }
        ComboBox<DeathScenario> scenario = control(view, "deathScenarioComboBox");
        scenario.setValue(DeathScenario.SPOUSE_DIES);
        ComboBox<Integer> age = control(view, "survivorClaimingAgeComboBox");
        age.setValue(68);
    }

    static TextField field(Object view, String name) throws Exception {
        return control(view, name);
    }

    @SuppressWarnings("unchecked")
    static <T> T control(Object view, String name) throws Exception {
        var field = view.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(view);
    }

    static Button button(AssumptionsView view, String name) throws Exception {
        return control(view, name);
    }

    static Label status(AssumptionsView view) throws Exception {
        return control(view, "statusLabel");
    }

    static void clean(AssumptionsView view) throws Exception {
        assertFalse(view.isDirty());
        assertFalse(view.dirtyProperty().get());
        assertTrue(button(view, "applyButton").isDisabled());
        assertTrue(button(view, "cancelButton").isDisabled());
    }

    static void dirty(AssumptionsView view) throws Exception {
        assertTrue(view.isDirty());
        assertTrue(view.dirtyProperty().get());
        assertFalse(button(view, "applyButton").isDisabled());
        assertFalse(button(view, "cancelButton").isDisabled());
    }

    static void assertRate(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    static void fx(CheckedAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    interface CheckedAction { void run() throws Exception; }

    static class CountingController extends ApplicationController {
        int invalidations;

        @Override
        public void invalidateProjection() {
            invalidations++;
            super.invalidateProjection();
        }
    }

    static class Fixture {
        final CountingController controller = new CountingController();
        final AssumptionsView view = new AssumptionsView();
        final AtomicInteger notifications = new AtomicInteger();
        final AtomicInteger revisions = new AtomicInteger();

        Fixture() {
            PlanningAssumptions a = assumptions();
            plan().setPlanningAssumptions(new PlanningAssumptions(
                    a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                    new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2040, 67, BigDecimal.ONE),
                    a.getProjectionLengthYears(), a.getProjectionStartDate()));
            controller.addSourcePlanRevisionListener(revisions::incrementAndGet);
            view.setOnPlanChanged(() -> {
                notifications.incrementAndGet();
                controller.markModified();
                controller.invalidateProjection();
            });
            view.load(plan());
        }

        RetirementPlan plan() { return controller.getCurrentPlan(); }
        PlanningAssumptions assumptions() { return plan().getPlanningAssumptions(); }
    }
}
