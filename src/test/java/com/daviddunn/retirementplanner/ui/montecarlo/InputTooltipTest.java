package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.dialogs.*;
import com.daviddunn.retirementplanner.ui.views.*;
import com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityStrategyAnalyzerDialog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Audits explicit forms before skins, with small, documented exclusions. */
class InputTooltipTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void helperIsDescriptiveOnlyAndPreservesGeometryAndAccessibility() throws Exception {
        fx(() -> {
            var input = new TextField("42");
            input.setDisable(true);
            input.setAccessibleText("Original accessible name");
            input.setPrefWidth(123);
            var changes = new AtomicInteger();
            input.textProperty().addListener((o, a, b) -> changes.incrementAndGet());
            var label = new Label("Amount");
            var grid = new GridPane(); grid.addRow(0, label, input);
            InputHelp.install(input, "Annual amount in dollars.");
            InputHelp.linkGridLabels(grid);
            assertEquals("42", input.getText());
            assertTrue(input.isDisabled());
            assertEquals(123, input.getPrefWidth());
            assertEquals("Original accessible name", input.getAccessibleText());
            assertEquals("Annual amount in dollars.", input.getAccessibleHelp());
            assertSame(input, label.getLabelFor());
            assertSame(input.getTooltip(), label.getTooltip());
            assertEquals(0, changes.get());
            check(input, "dollars");
            return null;
        });
    }

    @Test void householdHelpKeepsNamesOptionalAndValidationUnchanged() throws Exception {
        fx(() -> {
            var card = new PersonCard();
            var plan = MonteCarloComparisonFixtures.plan();
            var person = plan.getHousehold().getPrimaryPerson();
            card.load(person);
            var before = card.readValidated();
            audit(card, Set.of(control(card, "firstNameField"), control(card, "lastNameField")));
            check(control(card, "birthDatePicker"), "Medicare");
            check(control(card, "mortalityCategory"), "mortality-table");
            assertEquals(before, card.readValidated());
            ((DatePicker) control(card, "birthDatePicker")).getEditor().setText("bad date");
            assertThrows(IllegalArgumentException.class, card::readValidated);
            assertEquals(before.birthDate(), person.getBirthDate());
            return null;
        });
    }

    @Test void accountAndInheritedInputsHaveHelpWithoutChangingChoices() throws Exception {
        fx(() -> {
            var d = new AccountDialog(null);
            audit(d.getDialogPane().getContent(), Set.of(control(d, "nameField")));
            check(control(d, "balanceField"), "opening");
            check(control(d, "beneficiaryRelationshipCombo"), "original owner");
            assertNotNull(((ComboBox<?>) control(d, "typeCombo")).getValue());
            assertFalse(control(d, "originalOwnerDobPicker").isVisible());
            return null;
        });
    }

    @Test void pensionHelpExplainsDecimalColaAndOptionalSurvivorDollars() throws Exception {
        fx(() -> {
            var d = new PensionDialog(null);
            audit(d.getDialogPane().getContent(), Set.of(control(d, "nameField")));
            check(control(d, "colaRateField"), "0.02");
            check(control(d, "survivorMonthlyBenefitField"), "blank");
            assertEquals("0.0", ((TextField) control(d, "colaRateField")).getText());
            return null;
        });
    }

    @Test void expenseAndAssetHelpExplainBaseYearAndSeparateWealth() throws Exception {
        fx(() -> {
            var d = new ExpenseDialog(null);
            audit(d.getDialogPane().getContent(), Set.of(control(d, "descriptionField")));
            check(control(d, "annualAmountField"), "opening-year");
            check(control(d, "effectiveDatePicker"), "calendar-year overlap");
            var asset = new NonInvestableAssetDialog(null);
            audit(asset.getDialogPane().getContent(), Set.of(control(asset, "nameField")));
            check(control(asset, "valueField"), "Total Net Worth");
            assertTrue(asset.getDialogPane().lookupButton(asset.getDialogPane().getButtonTypes().getFirst()).isDisabled());
            return null;
        });
    }

    @Test void socialSecurityInputsPreserveDerivedDateAndMissingBirthDateValidation() throws Exception {
        fx(() -> {
            var p = MonteCarloComparisonFixtures.plan();
            var d = new SocialSecurityDialog(null, 2027, p.getHousehold());
            audit(d.getDialogPane().getContent(), Set.of(control(d, "nameField"), control(d, "startDatePicker")));
            check(control(d, "fraBenefitField"), "valuation year");
            check(control(d, "useTodaysDollarConventionCheckBox"), "does not convert");
            assertTrue(control(d, "startDatePicker").isDisabled());
            assertFalse(d.getDialogPane().lookupButton(ButtonType.OK).isDisabled());
            p.getHousehold().getPrimaryPerson().setBirthDate(null);
            var missing = new SocialSecurityDialog(null, 2027, p.getHousehold());
            assertTrue(missing.getDialogPane().lookupButton(ButtonType.OK).isDisabled());
            return null;
        });
    }

    @Test void assumptionsAndRothKeepPlanAndDirtyStateUnchanged() throws Exception {
        fx(() -> {
            var p = MonteCarloComparisonFixtures.plan();
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            var before = mapper.writeValueAsString(p);
            var a = new AssumptionsView(); a.load(p);
            var r = new RothConversionView(); r.load(p);
            var changes = new AtomicInteger();
            a.setOnPlanChanged(changes::incrementAndGet); r.setOnPlanChanged(changes::incrementAndGet);
            audit(a, Set.of()); audit(r, Set.of());
            check(control(a, "futureFederalMarginalRateAdjustmentField"), "percentage points");
            check(control(a, "stateIncomeTaxRateField"), "does not override");
            check(control(r, "targetTaxableIncomeField"), "not the amount to convert");
            assertTrue(control(a, "deathYearField").isDisabled());
            assertTrue(control(a, "applyButton").isDisabled());
            assertTrue(control(r, "applyButton").isDisabled());
            assertEquals(0, changes.get());
            assertEquals(before, mapper.writeValueAsString(p));
            return null;
        });
    }

    @Test void resultsQuickEditorsReuseTheSamePlanningAndRothSemantics() throws Exception {
        fx(() -> {
            var view = new ResultsSummaryView(new Controller(MonteCarloComparisonFixtures.plan()));
            audit(view, Set.of());
            check(control(view, "rothAmountField"), "same-owner");
            check(control(view, "inflationField"), "one-time");
            return null;
        });
    }

    @Test void openingRmdHelpDescribesHistoricalInputsWithoutSavingThem() throws Exception {
        fx(() -> {
            var p = MonteCarloUiFixtures.plan("10000", 3);
            p.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1950, 1, 1));
            var account = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA",
                    com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, new java.math.BigDecimal("10000"));
            p.getAccountPortfolio().addAccount(account);
            var d = new OpeningRmdDialog(p);
            audit(d.getDialogPane().getContent(), Set.of());
            Map<?, TextField> balances = field(d, "priorBalanceFields");
            Map<?, TextField> distributed = field(d, "distributedFields");
            assertEquals(1, balances.size());
            check(balances.values().iterator().next(), "historical");
            check(distributed.values().iterator().next(), "Enter 0");
            assertNull(account.getOpeningRmdAccountData());
            assertTrue(((Label) field(d, "statusLabel")).getText().contains("non-negative"));
            return null;
        });
    }

    @Test void analyzerInputsRetainHazardAndValuationHelpWithoutRunning() throws Exception {
        fx(() -> {
            var p = MonteCarloComparisonFixtures.plan();
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            String before = mapper.writeValueAsString(p);
            var d = new SocialSecurityStrategyAnalyzerDialog(null, p);
            Stage stage = field(d, "stage");
            try {
                audit(stage.getScene().getRoot(), Set.of());
                check(control(d, "primaryMortalityAdjustment"), "hazard");
                check(control(d, "discountRate"), "enter 1 for 1%");
                check(control(d, "mortalityDate"), "alive");
                check(control(d, "pvDate"), "common financial date");
                check(control(d, "integratedCandidateCount"), "Quick Comparison");
                assertEquals(before, mapper.writeValueAsString(p));
                assertFalse(control(d, "runButton").isDisabled());
            } finally { stage.close(); }
            return null;
        });
    }

    @Test void monteCarloHelpInstallsWithoutSubmittingWorkOrMutatingPlan() throws Exception {
        fx(() -> {
            var p = MonteCarloComparisonFixtures.plan();
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            String before = mapper.writeValueAsString(p);
            var tasks = new ArrayDeque<Runnable>();
            var single = new MonteCarloAnalysisView(new Controller(p), tasks::add,
                    (plan, settings, reference, progress, cancellation) -> { fail("Help must not run analysis"); return null; });
            var paired = new MonteCarloStrategyComparisonView(new Controller(p));
            try {
                audit(single, Set.of()); audit(paired, Set.of());
                check(control(single, "volatility"), "standard deviation");
                check(control(paired, "inflationFloor"), "realized mean");
                check(control(paired, "survivorB"), "Saved Baseline");
                assertEquals("417", ((TextField) control(single, "seed")).getText());
                assertTrue(tasks.isEmpty());
                assertEquals(MonteCarloSession.State.IDLE, single.session().state());
                assertEquals(MonteCarloStrategyComparisonSession.State.IDLE, paired.session().state());
                assertEquals(before, mapper.writeValueAsString(p));
            } finally { single.close(); paired.close(); }
            return null;
        });
    }

    static void check(Control c, String phrase) {
        assertNotNull(c.getTooltip(), c.toString());
        assertTrue(c.getTooltip().getText().contains(phrase), c.getTooltip().getText());
        assertTrue(c.getTooltip().isWrapText());
        assertEquals(375, c.getTooltip().getMaxWidth());
        assertEquals(250, c.getTooltip().getShowDelay().toMillis());
    }

    static void audit(Node node, Set<Control> excluded) {
        if (node instanceof Control c && !excluded.contains(c)
                && (c instanceof TextField || c instanceof ComboBox<?> || c instanceof DatePicker
                || c instanceof Spinner<?> || c instanceof CheckBox)) {
            check(c, "");
            assertFalse(c.getTooltip().getText().isBlank(), c.toString());
            return; // Composite editors are covered by their owning control, not their skins.
        }
        if (node instanceof ScrollPane s) audit(s.getContent(), excluded);
        else if (node instanceof TitledPane t) audit(t.getContent(), excluded);
        else if (node instanceof TabPane t) t.getTabs().forEach(tab -> audit(tab.getContent(), excluded));
        else if (node instanceof Parent p) p.getChildrenUnmodifiable().forEach(child -> audit(child, excluded));
    }

    static Control control(Object owner, String name) { return field(owner, name); }
    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name) {
        try {
            var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(owner);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
