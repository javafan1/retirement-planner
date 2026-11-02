package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.ui.help.HelpText;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
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
    void requiredIndicatorsMirrorAssumptionsApplyConditionsAndPreserveText() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            Label start = associatedLabel(f.view, control(f.view, "projectionStartDatePicker"));
            Label horizon = associatedLabel(f.view, control(f.view, "projectionLengthField"));
            Label investment = associatedLabel(f.view, control(f.view, "investmentReturnField"));
            Label state = associatedLabel(f.view, control(f.view, "stateJurisdictionComboBox"));
            Label deathYear = associatedLabel(f.view, control(f.view, "deathYearField"));
            Label futureRate = associatedLabel(f.view, control(f.view, "futureFederalMarginalRateAdjustmentField"));
            Label futureYear = associatedLabel(f.view, control(f.view, "futureFederalMarginalRateEffectiveYearField"));
            assertMarked(start, true);
            assertMarked(horizon, true);
            assertMarked(investment, true); // Prefilled, but parsed on every Apply.
            assertMarked(state, true);
            assertEquals("State of Residence", state.getText());
            assertEquals(1, f.view.lookupAll(".required-field-legend").size());
            assertMarked(deathYear, true);
            assertMarked(futureRate, false);
            assertMarked(futureYear, false);
            @SuppressWarnings("unchecked") ComboBox<DeathScenario> scenario = control(f.view, "deathScenarioComboBox");
            scenario.setValue(DeathScenario.BOTH_SURVIVE);
            assertMarked(deathYear, false);
            field(f.view, "futureFederalMarginalRateAdjustmentField").setText("2");
            assertMarked(futureRate, true);
            assertMarked(futureYear, true);
        });
    }

    @Test
    void inputTooltipsRemainWithoutLabelSideHelpIcons() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            String[][] inputs = {
                    {"projectionLengthField", HelpText.PLANNING_HORIZON},
                    {"investmentReturnField", HelpText.INVESTMENT_RETURN},
                    {"inflationRateField", HelpText.GENERAL_INFLATION},
                    {"healthcareInflationField", HelpText.HEALTHCARE_INFLATION},
                    {"socialSecurityColaField", HelpText.SOCIAL_SECURITY_COLA}
            };
            for (String[] input : inputs) {
                TextField field = field(f.view, input[0]);
                assertNotNull(field.getTooltip());
                assertEquals(input[1], field.getTooltip().getText());
            }
            assertTrue(f.view.lookupAll(".help-icon").isEmpty());
            assertTrue(f.view.lookupAll("*").stream().noneMatch(HelpIcon.class::isInstance));
        });
    }

    @Test
    void initialAndUnloadedStateAreClean() throws Exception {
        fx(() -> {
            clean(new AssumptionsView());
            clean(new Fixture().view);
        });
    }

    @Test
    void claimingAgeBenefitsPreviewMatchesValuationYearAndApplyCancelDraftSemantics() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var primary = f.plan().getHousehold().getPrimaryPerson();
            var spouse = f.plan().getHousehold().getSpouse();
            SocialSecurityIncome primarySource = socialSecurity("Primary", AccountOwnership.PRIMARY, 70);
            SocialSecurityIncome spouseSource = socialSecurity("Spouse", AccountOwnership.SPOUSE, 62);
            primary.addIncomeSource(primarySource);
            spouse.addIncomeSource(spouseSource);
            f.view.load(f.plan());

            @SuppressWarnings("unchecked")
            var rows = (java.util.List<Object>) control(f.view, "socialSecurityDraftRows");
            ComboBox<Integer> primaryAge = (ComboBox<Integer>) control(rows.get(0), "age");
            ComboBox<Integer> spouseAge = (ComboBox<Integer>) control(rows.get(1), "age");
            assertNotNull(primaryAge);
            assertNotNull(spouseAge);
            Label primaryBenefit = benefitValue(primaryAge);
            Label spouseBenefit = benefitValue(spouseAge);
            assertEquals("$3,720", primaryBenefit.getText());
            assertEquals("$2,100", spouseBenefit.getText());
            assertTrue(primaryBenefit.getAccessibleHelp().contains("2026 dollars"));

            primaryAge.setValue(69);
            assertEquals("$3,480", primaryBenefit.getText());
            assertEquals(70, primarySource.getClaimingAge(), "Preview edits must remain a detached draft.");
            dirty(f.view);
            f.view.cancelChanges();
            @SuppressWarnings("unchecked")
            var restoredRows = (java.util.List<Object>) control(f.view, "socialSecurityDraftRows");
            ComboBox<Integer> restoredPrimaryAge = (ComboBox<Integer>) control(restoredRows.get(0), "age");
            assertEquals(70, restoredPrimaryAge.getValue());
            assertEquals(70, primarySource.getClaimingAge());
            clean(f.view);

            restoredPrimaryAge.setValue(69);
            ComboBox<Integer> restoredSpouseAge = (ComboBox<Integer>) control(restoredRows.get(1), "age");
            restoredSpouseAge.setValue(65);
            assertTrue(f.view.applyChanges());
            assertEquals(69, primary.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                    .map(SocialSecurityIncome.class::cast).findFirst().orElseThrow().getClaimingAge());
            assertEquals(65, spouse.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                    .map(SocialSecurityIncome.class::cast).findFirst().orElseThrow().getClaimingAge());
            assertEquals(1, f.notifications.get());
        });
    }

    @Test
    void singlePersonAssumptionsShowsOnlyPrimaryBenefitWhenConfigured() throws Exception {
        fx(() -> {
            var controller = new CountingController();
            var plan = controller.getCurrentPlan();
            var primary = plan.getHousehold().getPrimaryPerson();
            primary.setBirthDate(LocalDate.of(1960, 1, 1));
            primary.addIncomeSource(socialSecurity("Primary", AccountOwnership.PRIMARY, 67));
            AssumptionsView view = new AssumptionsView();
            view.load(plan);
            @SuppressWarnings("unchecked")
            var rows = (java.util.List<Object>) control(view, "socialSecurityDraftRows");
            assertEquals(1, rows.size());
            ComboBox<Integer> age = (ComboBox<Integer>) control(rows.getFirst(), "age");
            assertEquals("$3,000", benefitValue(age).getText());
        });
    }

    @Test
    void invalidClaimingAgeBlocksGlobalValidationBeforeAnyModelMutation() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var person = f.plan().getHousehold().getPrimaryPerson();
            var source = socialSecurity("Primary", AccountOwnership.PRIMARY, 70);
            person.addIncomeSource(source);
            f.view.load(f.plan());
            @SuppressWarnings("unchecked")
            var rows = (java.util.List<Object>) control(f.view, "socialSecurityDraftRows");
            ComboBox<Integer> age = (ComboBox<Integer>) control(rows.getFirst(), "age");
            age.setValue(null);
            field(f.view, "investmentReturnField").setText("8.5");
            assertFalse(f.view.validateChanges());
            assertSame(source, person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                    .findFirst().orElseThrow());
            assertRate("0.08", f.assumptions().getEconomicAssumptions().getExpectedAnnualInvestmentReturn());
            assertTrue(f.view.isDirty());
        });
    }

    @Test
    void assumptionsBenefitControlsRenderInLightAndDarkAtSupportedSizes() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var primary = f.plan().getHousehold().getPrimaryPerson();
            primary.addIncomeSource(socialSecurity("Primary", AccountOwnership.PRIMARY, 70));
            f.view.load(f.plan());
            var themes = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
            Path output = Path.of("target/phase13ja/previews");
            Files.createDirectories(output);
            try {
                for (ApplicationTheme theme : ApplicationTheme.values()) {
                    themes.apply(theme);
                    for (int width : new int[]{1180, 900}) {
                        AssumptionsView preview = new AssumptionsView();
                        preview.load(f.plan());
                        Stage stage = new Stage();
                        stage.setScene(new Scene(preview, width, width == 900 ? 650 : 820));
                        stage.show();
                        try {
                            preview.applyCss();
                            preview.layout();
                            assertEquals(1, ((java.util.List<?>) control(preview, "socialSecurityDraftRows")).size());
                            Node card = preview.lookup("#assumptions-social-security-benefits");
                            assertNotNull(card);
                            assertTrue(card.getStyleClass().contains("assumptions-card"));
                            assertEquals("Social Security Claiming Ages & Benefits",
                                    ((Label) ((VBox) card).getChildren().getFirst()).getText());
                            assertTrue(card.getParent() instanceof VBox, "Benefits must occupy their own column card.");
                            @SuppressWarnings("unchecked")
                            ComboBox<Integer> age = (ComboBox<Integer>) preview.lookup("#primary-social-security-claiming-age");
                            GridPane ageRow = (GridPane) age.getParent();
                            GridPane benefitRow = (GridPane) benefitValue(age).getParent();
                            assertEquals(1, GridPane.getColumnIndex(age));
                            assertEquals(1, GridPane.getColumnIndex(benefitValue(age)));
                            assertEquals(55.0, ageRow.getColumnConstraints().get(0).getPercentWidth());
                            assertEquals(45.0, ageRow.getColumnConstraints().get(1).getPercentWidth());
                            assertEquals(ageRow.getColumnConstraints().get(0).getPercentWidth(),
                                    benefitRow.getColumnConstraints().get(0).getPercentWidth());
                            assertEquals(ageRow.getColumnConstraints().get(1).getPercentWidth(),
                                    benefitRow.getColumnConstraints().get(1).getPercentWidth());
                            saveScreenshot(card, output.resolve("assumptions-social-security-" + theme + "-" + width + ".png"));
                            saveScreenshot(preview, output.resolve("assumptions-" + theme + "-" + width + ".png"));
                        } finally {
                            stage.hide();
                        }
                    }
                }
            } finally {
                themes.close();
            }
        });
    }

    private static void saveScreenshot(Node node, Path file) throws Exception {
        var snapshot = node.snapshot(null, null);
        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        }
        javax.imageio.ImageIO.write(image, "png", file.toFile());
    }

    private static SocialSecurityIncome socialSecurity(String name, AccountOwnership owner, int age) {
        LocalDate birth = owner == AccountOwnership.PRIMARY ? LocalDate.of(1963, 6, 4) : LocalDate.of(1965, 2, 28);
        var person = new com.daviddunn.retirementplanner.domain.model.Person(name, "Test", birth);
        var start = com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitStartDateCalculator
                .calculate(person, age).orElseThrow();
        return new SocialSecurityIncome(name, owner, start, null, new BigDecimal("3000"), age,
                new BigDecimal("0.02"), 2026);
    }

    private static Label benefitValue(ComboBox<Integer> age) {
        var personRows = (VBox) age.getParent().getParent();
        var grid = (GridPane) personRows.getChildren().get(1);
        return (Label) grid.getChildren().stream().filter(Label.class::isInstance)
                .map(Label.class::cast).filter(label -> label.getStyleClass().contains("assumption-value"))
                .findFirst().orElseThrow();
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
            assertEquals(2031, a.getDeathScenarioAssumptions().getDeathYear());
            assertEquals(67, a.getDeathScenarioAssumptions().getSurvivorClaimingAge());
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
            assertEquals(2030, f.assumptions().getDeathScenarioAssumptions().getDeathYear());
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
                {"projectionLengthField", "31"}, {"deathYearField", "2031"},
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
        age.setValue(67);
    }

    static TextField field(Object view, String name) throws Exception {
        return control(view, name);
    }

    private static Label associatedLabel(AssumptionsView view, javafx.scene.Node input) {
        return findLabel(view, input);
    }

    private static Label findLabel(javafx.scene.Parent root, javafx.scene.Node input) {
        Label found = findLabelOrNull(root, input);
        if (found != null) return found;
        throw new AssertionError("No label associated with input " + input);
    }

    private static Label findLabelOrNull(javafx.scene.Parent root, javafx.scene.Node input) {
        if (root instanceof javafx.scene.control.Label label && label.getLabelFor() == input) return label;
        if (root instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() instanceof javafx.scene.Parent content) {
            Label found = findLabelOrNull(content, input);
            if (found != null) return found;
        }
        if (root instanceof javafx.scene.control.TitledPane titled && titled.getContent() instanceof javafx.scene.Parent content) {
            Label found = findLabelOrNull(content, input);
            if (found != null) return found;
        }
        for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof javafx.scene.Parent parent) {
                Label found = findLabelOrNull(parent, input);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertMarked(Label label, boolean expected) {
        var marker = label.getGraphic() instanceof javafx.scene.text.TextFlow flow
                ? flow.getChildren().stream().filter(javafx.scene.text.Text.class::isInstance)
                    .map(javafx.scene.text.Text.class::cast)
                    .filter(text -> text.getStyleClass().contains("required-field-indicator"))
                    .findFirst().orElse(null)
                : null;
        assertEquals(expected, marker != null && marker.isVisible(), label.getText());
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
            plan().setSpouse(new com.daviddunn.retirementplanner.domain.model.Person("", "", null));
            plan().getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1963, 6, 4));
            plan().getHousehold().getSpouse().setBirthDate(LocalDate.of(1965, 2, 28));
            PlanningAssumptions a = assumptions();
            plan().setPlanningAssumptions(new PlanningAssumptions(
                    a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                    new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2030, 67, BigDecimal.ONE),
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
