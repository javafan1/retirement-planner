package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent input paths: real wizard dialogs versus established editor domain builders. */
class WizardFinancialEquivalenceTest {

    enum Scenario {
        SINGLE, COUPLE, OPENING_RMD, EMPTY;

        boolean couple() { return this == COUPLE; }
        boolean populated() { return this != EMPTY; }
        LocalDate birth() { return LocalDate.of(this == EMPTY ? 2000 : this == OPENING_RMD ? 1950 : 1964, 5, 6); }
        String first() { return this == EMPTY || this == OPENING_RMD ? "" : "Alex"; }
        String last() { return first().isEmpty() ? "" : "Example"; }
    }

    private static final LocalDate START = LocalDate.of(2027, 7, 1);
    private static final LocalDate SPOUSE_BIRTH = LocalDate.of(1966, 7, 8);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @TempDir Path directory;

    @BeforeAll static void start() throws Exception { startFx(); }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void independentInputsAnnualResultsPersistenceAndSeededRunsAreEquivalent(Scenario scenario) throws Exception {
        var created = new AtomicReference<RetirementPlan>();
        fx(() -> created.set(throughWizard(scenario)));
        RetirementPlan wizard = created.get();
        RetirementPlan established = throughEstablishedBuilders(scenario);
        String wizardIdentity = wizard.getPlanId();
        assertNotEquals(established.getPlanId(), wizardIdentity, "Independent construction paths create independent plan identities");

        assertInputs(established, wizard, scenario + " inputs");
        assertProjections(established, wizard, scenario + " projection");

        // Exercise the same transactional repository used by Save As, including previous-version backup.
        var repository = new JsonRetirementPlanRepository();
        Path file = directory.resolve(scenario + ".json");
        repository.save(wizard, file);
        assertEquals(wizardIdentity, repository.load(file).getPlanId());
        repository.save(wizard, file);
        assertEquals(wizardIdentity, repository.loadBackup(file).getPlanId());
        assertInputs(established, repository.load(file), scenario + " reload");
        assertInputs(established, repository.loadBackup(file), scenario + " backup");
        assertProjections(established, repository.load(file), scenario + " reload projection");

        if (scenario.populated()) {
            var settings = MonteCarloSettings.forPlan(established, 3, 7052026L, new BigDecimal("0.10"));
            var expected = new MonteCarloAnalyzer().analyze(established, settings);
            var actual = new MonteCarloAnalyzer().analyze(wizard, settings);
            assertEquals(3, expected.completedCount(), "Fixture must exercise complete funded outcomes");
            assertEquals(expected, actual, scenario + " entire seeded output contract");
        }
        assertInputs(established, wizard, scenario + " calculation must not mutate inputs");
        assertEquals(wizardIdentity, wizard.getPlanId(), "Calculations must not change persistence identity");
    }

    private static RetirementPlan throughWizard(Scenario scenario) throws Exception {
        var wizard = new NewRetirementPlanWizard(null);
        wizard.show();
        try {
            DialogPane pane = wizard.getDialogPane();
            fill(person(pane, "primary"), scenario.first(), scenario.last(), scenario.birth(), MortalityCategory.FEMALE);
            if (scenario.couple()) {
                ((Button) pane.lookup("#wizard-add-spouse")).fire();
                fill(person(pane, "spouse"), "Sam", "Example", SPOUSE_BIRTH, MortalityCategory.MALE);
            }
            // Apply a fixed basis year through real navigation before opening SS dialogs.
            for (int index = 0; index < 5; index++) button(pane, "Next").fire();
            assumptions(pane, LocalDate.of(2026, 1, 1), scenario.couple());
            button(pane, "Next").fire();
            if (scenario.populated()) {
                editSection(pane, "accounts");
                CompleteWizardFixtures.account(pane, "Primary IRA", AccountType.TRADITIONAL_IRA, AccountOwnership.PRIMARY, "800000.1200");
                CompleteWizardFixtures.account(pane, "Primary Roth", AccountType.ROTH_IRA, AccountOwnership.PRIMARY, "200000.3400");
                CompleteWizardFixtures.account(pane, "Investments", AccountType.BROKERAGE,
                        scenario.couple() ? AccountOwnership.JOINT : AccountOwnership.PRIMARY, "400000.5600");
                if (scenario.couple()) {
                    CompleteWizardFixtures.account(pane, "Spouse IRA", AccountType.TRADITIONAL_IRA, AccountOwnership.SPOUSE, "500000.7800");
                    CompleteWizardFixtures.account(pane, "Spouse Roth", AccountType.ROTH_IRA, AccountOwnership.SPOUSE, "150000.9000");
                }
                button(pane, "Next").fire();
                ss(pane, "primary", "Primary SS", "3000.1200", 68);
                if (scenario.couple()) ss(pane, "spouse", "Spouse SS", "2400.3400", 66);
                button(pane, "Next").fire();
                pension(pane, AccountOwnership.PRIMARY, "Primary pension", "1000.5600", scenario.couple() ? "600.7800" : null);
                if (scenario.couple()) pension(pane, AccountOwnership.SPOUSE, "Spouse pension", "900.1200", "400.3400");
                button(pane, "Next").fire();
                spending(pane, "Living", "36000.1200", GrowthCategory.GENERAL, ExpenseType.RECURRING, START, LocalDate.of(2046, 12, 31));
                spending(pane, "Healthcare", "6000.3400", GrowthCategory.HEALTHCARE, ExpenseType.RECURRING, START, LocalDate.of(2035, 12, 31));
                spending(pane, "Purchase", "9876.5400", GrowthCategory.GENERAL, ExpenseType.ONE_TIME,
                        LocalDate.of(2030, 5, 6), LocalDate.of(2030, 5, 6));
                button(pane, "Next").fire();
            }
            else editSection(pane, "assumptions");
            assumptions(pane, START, scenario.couple());
            button(pane, "Next").fire();
            assertTrue(button(pane, "Create Plan").isVisible());
            assertEquals(scenario.couple() ? 2 : scenario.populated() ? 1 : 0,
                    pane.lookupAll(".wizard-valuation-notice").size());
            if (scenario == Scenario.OPENING_RMD) openingHistory(pane);
            button(pane, "Create Plan").fire();
            assertNotNull(wizard.getResult(), "Must complete through normal validation/Create");
            return wizard.getResult();
        }
        finally { wizard.close(); }
    }

    private static void editSection(DialogPane pane, String section) {
        ((Button) pane.lookup("#wizard-review-edit-" + section)).fire();
    }

    @SuppressWarnings("unchecked")
    private static void assumptions(DialogPane pane, LocalDate date, boolean couple) {
        ((DatePicker) pane.lookup("#wizard-assumption-start")).setValue(date);
        ((TextField) pane.lookup("#wizard-assumption-length")).setText("20");
        ((TextField) pane.lookup("#wizard-assumption-return")).setText("6.25");
        ((TextField) pane.lookup("#wizard-assumption-inflation")).setText("2.25");
        pane.applyCss(); pane.layout();
        ((TitledPane) pane.lookup("#wizard-advanced-assumptions")).setExpanded(true);
        pane.applyCss(); pane.layout();
        ((TextField) pane.lookup("#wizard-assumption-healthcare")).setText("4.125");
        ((TextField) pane.lookup("#wizard-assumption-cola")).setText("1.875");
        ((ComboBox<FilingStatus>) pane.lookup("#wizard-assumption-filing-status")).setValue(
                couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        ((ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence")).setValue(StateTaxJurisdiction.FL);
    }

    @SuppressWarnings("unchecked")
    private static void ss(DialogPane pane, String owner, String name, String amount, int age) {
        CompleteWizardFixtures.edit(pane, "wizard-add-social-security-" + owner, "wizard-input-fra-monthly-benefit", dialog -> {
            ((TextField) dialog.lookup("#wizard-input-name")).setText(name);
            ((TextField) dialog.lookup("#wizard-input-fra-monthly-benefit")).setText(amount);
            ((ComboBox<Integer>) dialog.lookup("#wizard-input-claiming-age")).setValue(age);
        });
    }

    @SuppressWarnings("unchecked")
    private static void pension(DialogPane pane, AccountOwnership owner, String name, String amount, String survivor) {
        CompleteWizardFixtures.edit(pane, "wizard-add-pension", "wizard-input-monthly-benefit", dialog -> {
            ((TextField) dialog.lookup("#wizard-input-name")).setText(name);
            ((ComboBox<AccountOwnership>) dialog.lookup("#wizard-input-owner")).setValue(owner);
            ((DatePicker) dialog.lookup("#wizard-input-start-date")).setValue(LocalDate.of(2027, 3, 1));
            ((DatePicker) dialog.lookup("#wizard-input-end-date")).setValue(LocalDate.of(2042, 12, 31));
            ((TextField) dialog.lookup("#wizard-input-monthly-benefit")).setText(amount);
            ((TextField) dialog.lookup("#wizard-input-annual-cola-rate")).setText("0.0175");
            if (survivor != null) ((TextField) dialog.lookup("#wizard-input-survivor-monthly-benefit")).setText(survivor);
        });
    }

    @SuppressWarnings("unchecked")
    private static void spending(DialogPane pane, String name, String amount, GrowthCategory growth,
                                 ExpenseType type, LocalDate start, LocalDate end) {
        CompleteWizardFixtures.edit(pane, "wizard-add-expense", "wizard-input-amount", dialog -> {
            ((TextField) dialog.lookup("#wizard-input-description")).setText(name);
            ((ComboBox<ExpenseType>) dialog.lookup("#wizard-input-expense-type")).setValue(type);
            ((TextField) dialog.lookup("#wizard-input-amount")).setText(amount);
            ((ComboBox<GrowthCategory>) dialog.lookup("#wizard-input-escalation")).setValue(growth);
            ((DatePicker) dialog.lookup("#wizard-input-effective-date")).setValue(start);
            if (type == ExpenseType.RECURRING) ((DatePicker) dialog.lookup("#wizard-input-end-date")).setValue(end);
        });
    }

    private static void openingHistory(DialogPane wizard) {
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            var dialog = Window.getWindows().stream().filter(Window::isShowing)
                    .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).filter(pane -> pane != wizard).findFirst().orElseThrow();
            try {
                dialog.applyCss(); dialog.layout();
                var inputs = dialog.lookupAll(".text-field").stream().map(TextField.class::cast)
                        .sorted(Comparator.comparingDouble(input -> input.localToScene(input.getBoundsInLocal()).getMinY())).toList();
                assertEquals(2, inputs.size());
                inputs.get(0).setText("820000.1200");
                inputs.get(1).setText("1000.2500");
                ((Button) dialog.lookupButton(ButtonType.OK)).fire();
            }
            catch (Throwable error) { failure.set(error); button(dialog, "Cancel").fire(); }
        });
        ((Button) wizard.lookup("#wizard-opening-rmd")).fire();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    /** No wizard draft, wizard helper, copied Plan A data or JSON reconstruction is used here. */
    private static RetirementPlan throughEstablishedBuilders(Scenario scenario) {
        RetirementPlan plan = RetirementPlanFactory.createSinglePersonPlan();
        Person primary = plan.getHousehold().getPrimaryPerson();
        primary.setFirstName(scenario.first());
        primary.setLastName(scenario.last());
        primary.setBirthDate(scenario.birth());
        primary.setMortalityCategory(MortalityCategory.FEMALE);
        if (scenario.couple()) {
            Person spouse = new Person("Sam", "Example", SPOUSE_BIRTH);
            spouse.setMortalityCategory(MortalityCategory.MALE);
            plan.setSpouse(spouse);
        }
        if (scenario.populated()) {
            plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.TRADITIONAL_IRA, "Primary IRA", AccountOwnership.PRIMARY, new BigDecimal("800000.1200")));
            plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.ROTH_IRA, "Primary Roth", AccountOwnership.PRIMARY, new BigDecimal("200000.3400")));
            plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.BROKERAGE, "Investments",
                    scenario.couple() ? AccountOwnership.JOINT : AccountOwnership.PRIMARY, new BigDecimal("400000.5600")));
            if (scenario.couple()) {
                plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.TRADITIONAL_IRA, "Spouse IRA", AccountOwnership.SPOUSE, new BigDecimal("500000.7800")));
                plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.ROTH_IRA, "Spouse Roth", AccountOwnership.SPOUSE, new BigDecimal("150000.9000")));
            }
            primary.addIncomeSource(new SocialSecurityIncome("Primary SS", AccountOwnership.PRIMARY, scenario.birth().plusYears(68), null,
                    new BigDecimal("3000.1200"), 68, BigDecimal.ZERO, 2026));
            primary.addIncomeSource(new Pension("Primary pension", AccountOwnership.PRIMARY, LocalDate.of(2027, 3, 1), LocalDate.of(2042, 12, 31),
                    new BigDecimal("1000.5600"), new BigDecimal("0.0175"), scenario.couple() ? new BigDecimal("600.7800") : null));
            if (scenario.couple()) {
                plan.getHousehold().getSpouse().addIncomeSource(new SocialSecurityIncome("Spouse SS", AccountOwnership.SPOUSE,
                        SPOUSE_BIRTH.plusYears(66), null, new BigDecimal("2400.3400"), 66, BigDecimal.ZERO, 2026));
                plan.getHousehold().getSpouse().addIncomeSource(new Pension("Spouse pension", AccountOwnership.SPOUSE,
                        LocalDate.of(2027, 3, 1), LocalDate.of(2042, 12, 31), new BigDecimal("900.1200"), new BigDecimal("0.0175"), new BigDecimal("400.3400")));
            }
            plan.getHousehold().addExpense(new Expense("Living", new BigDecimal("36000.1200"), GrowthCategory.GENERAL, START, LocalDate.of(2046, 12, 31), ExpenseType.RECURRING));
            plan.getHousehold().addExpense(new Expense("Healthcare", new BigDecimal("6000.3400"), GrowthCategory.HEALTHCARE, START, LocalDate.of(2035, 12, 31), ExpenseType.RECURRING));
            plan.getHousehold().addExpense(new Expense("Purchase", new BigDecimal("9876.5400"), GrowthCategory.GENERAL,
                    LocalDate.of(2030, 5, 6), LocalDate.of(2030, 5, 6), ExpenseType.ONE_TIME));
        }
        var original = plan.getPlanningAssumptions();
        var tax = original.getTaxAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(new BigDecimal("0.0625"),
                new BigDecimal("0.0225"), new BigDecimal("0.04125"), new BigDecimal("0.01875")),
                new TaxAssumptions(tax.getFederalTaxBracketGrowthRate(), tax.getStandardDeductionGrowthRate(),
                        tax.getStateIncomeTaxRate(), tax.getLocalIncomeTaxRate(), scenario.couple() ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE,
                        tax.getEstimatedHeirTaxRateOnTaxDeferredAssets()), original.getWithdrawalAssumptions(), original.getDeathScenarioAssumptions(),
                20, START, StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        if (scenario == Scenario.OPENING_RMD) plan.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2027, new BigDecimal("820000.1200"), new BigDecimal("1000.2500")));
        plan.validateHouseholdReferences();
        return plan;
    }

    private static void assertInputs(RetirementPlan expected, RetirementPlan actual, String context) {
        var expectedInputs = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.valueToTree(expected);
        var actualInputs = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.valueToTree(actual);
        // Identity is deliberately different for independently created plans; all financial fields remain exact.
        expectedInputs.remove("planId"); actualInputs.remove("planId");
        assertEquals(expectedInputs, actualInputs, context + " complete financial persisted input contract");
        var accounts = expected.getAccountPortfolio().getAccounts();
        for (int index = 0; index < accounts.size(); index++) {
            Account left = accounts.get(index), right = actual.getAccountPortfolio().getAccounts().get(index);
            assertEquals(left.getCurrentBalance(), right.getCurrentBalance(), context + " exact account amount/scale " + index);
            if (left.getOpeningRmdAccountData() != null) {
                assertEquals(left.getOpeningRmdAccountData().getPriorDecember31Balance(), right.getOpeningRmdAccountData().getPriorDecember31Balance());
                assertEquals(left.getOpeningRmdAccountData().getRmdAlreadyDistributedBeforeProjection(), right.getOpeningRmdAccountData().getRmdAlreadyDistributedBeforeProjection());
            }
        }
        for (var owner : expected.getHousehold().peopleByOwner().keySet()) {
            var left = expected.getHousehold().peopleByOwner().get(owner).getIncomeSources();
            var right = actual.getHousehold().peopleByOwner().get(owner).getIncomeSources();
            for (int index = 0; index < left.size(); index++) {
                if (left.get(index) instanceof SocialSecurityIncome ss) assertEquals(ss.getFullRetirementMonthlyBenefit(), ((SocialSecurityIncome) right.get(index)).getFullRetirementMonthlyBenefit());
                if (left.get(index) instanceof Pension pension) {
                    var other = (Pension) right.get(index);
                    assertEquals(pension.getMonthlyBenefit(), other.getMonthlyBenefit());
                    assertEquals(pension.getAnnualColaRate(), other.getAnnualColaRate());
                    assertEquals(pension.getSurvivorMonthlyBenefit(), other.getSurvivorMonthlyBenefit());
                }
            }
        }
        for (int index = 0; index < expected.getHousehold().getExpenses().size(); index++)
            assertEquals(expected.getHousehold().getExpenses().get(index).getAnnualAmount(), actual.getHousehold().getExpenses().get(index).getAnnualAmount(), context + " exact expense amount/scale " + index);
    }

    private static void assertProjections(RetirementPlan expectedPlan, RetirementPlan actualPlan, String context) throws Exception {
        var expected = new ProjectionEngine().project(expectedPlan);
        var actual = new ProjectionEngine().project(actualPlan);
        assertEquals(20, actual.size());
        assertEquals(expected.size(), actual.size());
        if (!expectedPlan.getHousehold().getExpenses().isEmpty()) {
            // Existing opening-year rule: ($36,000.1200 + $6,000.3400) x 6/12.
            assertEquals(new BigDecimal("21000.23"), actual.getFirstYear().getAnnualExpenses(), context + " July opening-year proration");
        }
        for (int index = 0; index < expected.size(); index++) {
            var left = expected.getYearAt(index);
            var right = actual.getYearAt(index);
            // Cover every exposed scalar financial result, with exact BigDecimal equality (including scale).
            for (var method : ProjectionYear.class.getMethods()) {
                if (method.getParameterCount() != 0 || !method.getName().startsWith("get")) continue;
                if (method.getName().equals("getSpouseRothConversion") && !expectedPlan.getHousehold().hasSpouse()) continue;
                var type = method.getReturnType();
                if (type == BigDecimal.class || type == int.class || type.isEnum())
                    assertEquals(method.invoke(left), method.invoke(right), context + " first differing year " + left.getCalendarYear() + " / " + method.getName());
            }
            assertEquals(left.getSocialSecurityResult(), right.getSocialSecurityResult(), context + " SS " + left.getCalendarYear());
            assertEquals(left.spouseRothConversion(), right.spouseRothConversion(), context + " absent/present spouse conversion");
            assertEquals(left.getHouseholdCashSettlement(), right.getHouseholdCashSettlement(), context + " cash " + left.getCalendarYear());
            for (int account = 0; account < expectedPlan.getAccountPortfolio().getAccounts().size(); account++)
                assertEquals(left.getEndingBalance(expectedPlan.getAccountPortfolio().getAccounts().get(account)),
                        right.getEndingBalance(actualPlan.getAccountPortfolio().getAccounts().get(account)), context + " account " + account + " / " + left.getCalendarYear());
        }
        assertEquals(new ProjectionMetricsCalculator().calculate(expectedPlan, expected), new ProjectionMetricsCalculator().calculate(actualPlan, actual), context + " all summary metrics");
        assertEquals(expected.getHighestInvestableAssets(), actual.getHighestInvestableAssets());
        assertEquals(expected.getLowestInvestableAssets(), actual.getLowestInvestableAssets());
    }
}
