package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.model.WithdrawalAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.RMD;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.PENSION;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED;
import static com.daviddunn.retirementplanner.domain.model.DeathScenario.*;
import static com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.*;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class SurvivorHouseholdPoolingCharacterizationTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate SPEND = LocalDate.of(2028, 1, 1);

    @ParameterizedTest
    @CsvSource({"BOTH_SURVIVE,PRIMARY,PRIMARY", "PRIMARY_DIES,PRIMARY,PRIMARY",
            "SPOUSE_DIES,SPOUSE,SPOUSE", "PRIMARY_DIES,SPOUSE,SPOUSE"})
    void floridaPoolsSeparateAccountsAndKeepsTheActualDebitLabel(DeathScenario death, AccountOwnership accountOwner,
                                                                  AccountOwnership reportedOwner) {
        var primary = new TraditionalIRA("Synthetic primary", PRIMARY, money(accountOwner == PRIMARY ? "100000" : "0"));
        var spouse = new TraditionalIRA("Synthetic spouse", SPOUSE, money(accountOwner == SPOUSE ? "100000" : "0"));
        var plan = plan(FL, death, TAXABLE_FIRST, primary, spouse);
        expense(plan, "15000");
        var projection = run(plan);
        var last = projection.getYears().getLast();
        var source = last.getIncomeSources().stream().filter(s -> s.purpose() == CASH_FLOW).findFirst().orElseThrow();
        assertEquals(reportedOwner, source.owner().orElseThrow());
        assertEquals(accountOwner, source.account().orElseThrow().owner());
        equal("15000", source.grossAmount());
        equal("15000", source.modeledTaxableAmount().orElseThrow());
        equal("15000", last.getAdjustedGrossIncome());
        equal("0", last.getStateIncomeTax());
        equal("85000", last.getEndingInvestableAssets());
        equal("100000", projection.getYearAt(1).getEndingInvestableAssets());
        cashConserves(last);
        equal("100000", (accountOwner == PRIMARY ? primary : spouse).getCurrentBalance());
    }

    @ParameterizedTest
    @EnumSource(value = StateTaxJurisdiction.class, names = {"AR", "PA", "MI", "FL"})
    void identicalSurvivorIraDrawSeparatesStateRefusalFromFederalTaxBase(StateTaxJurisdiction state) {
        var plan = plan(state, PRIMARY_DIES, TAXABLE_FIRST,
                new TraditionalIRA("Synthetic primary", PRIMARY, money("100000")),
                new TraditionalIRA("Synthetic spouse", SPOUSE, money("100000")));
        expense(plan, "15000");
        if (state == AR) {
            assertArkansasInactive(plan);
        } else if (state == PA) {
            var failure = assertThrows(StateTaxUnavailableException.class, () -> run(plan));
            assertTrue(failure.getMessage().contains("MISSING_RETIREMENT_QUALIFICATION"));
            assertTrue(failure.getMessage().contains("active recipient"));
        } else {
            var last = run(plan).getYears().getLast();
            var sources = last.getIncomeSources().stream().filter(s -> s.isRetirementDistribution()).toList();
            assertFalse(sources.isEmpty());
            assertTrue(sources.stream().allMatch(source -> source.owner().orElseThrow() == PRIMARY));
            assertTrue(sources.stream().allMatch(source -> source.account().orElseThrow().owner() == PRIMARY));
            int direction = last.getAdjustedGrossIncome().compareTo(money("15000"));
            if (state == MI) {
                assertTrue(direction > 0, "Conservative state tax requires additional funding");
                var michigan = last.michiganTaxCalculation().orElseThrow();
                equal("0", michigan.retirementDeduction());
                assertTrue(michigan.eligibilityDecisions().stream()
                        .anyMatch(d -> d.reason().equals("DECEASED_OWNER_OR_SUCCESSOR_QUALIFICATION_UNAVAILABLE")));
                assertTrue(last.getStateIncomeTax().signum() > 0);
            } else {
                assertEquals(0, direction);
                equal("0", last.getStateIncomeTax());
            }
            if (state == MI) legacyPositiveFundingResidual(last);
            else cashConserves(last);
        }
    }

    @ParameterizedTest
    @EnumSource(value = StateTaxJurisdiction.class, names = {"AR", "PA", "MI"})
    void activeOwnerIsAnIndependentRetirementQualificationControl(StateTaxJurisdiction state) {
        var plan = plan(state, PRIMARY_DIES, TAXABLE_FIRST,
                new TraditionalIRA("Synthetic survivor", SPOUSE, money("100000")));
        expense(plan, "15000");
        var year = run(plan).getYears().getLast();
        assertTrue(year.getIncomeSources().stream().filter(s -> s.isRetirementDistribution())
                .allMatch(s -> s.owner().orElseThrow() == SPOUSE && s.account().orElseThrow().owner() == SPOUSE));
        if (state == AR) {
            var validation = year.arkansasValidationTax().orElseThrow();
            assertEquals(FilingStatus.SINGLE, validation.filingStatus());
            equal("6000", validation.provenance().election().retirementExclusion());
        } else {
            equal("15000", year.getAdjustedGrossIncome());
            equal("0", year.getStateIncomeTax());
            if (state == MI) equal("15000", year.getMichiganRetirementDeduction());
        }
        cashConserves(year);
    }

    @Test
    void arkansasAdmitsJointCashAndIndividualBrokerageCashButRefusesInactiveRoth() {
        var cash = plan(AR, PRIMARY_DIES, TAXABLE_FIRST, new CheckingAccount("Synthetic joint", JOINT, money("10000")));
        var brokerage = plan(AR, PRIMARY_DIES, TAXABLE_FIRST, new BrokerageAccount("Synthetic brokerage", PRIMARY, money("10000")));
        var roth = plan(AR, PRIMARY_DIES, TAXABLE_FIRST, new RothIRA("Synthetic Roth", PRIMARY, money("10000")));
        for (var plan : List.of(cash, brokerage, roth)) expense(plan, "1000");
        for (var plan : List.of(cash, brokerage)) {
            var year = run(plan).getYears().getLast();
            var source = year.getIncomeSources().stream().filter(s -> s.purpose() == CASH_FLOW).findFirst().orElseThrow();
            assertEquals(plan.getAccountPortfolio().getAccounts().getFirst().getOwnership(), source.owner().orElseThrow());
            equal("0", year.getAdjustedGrossIncome());
            equal("9000", year.getEndingInvestableAssets());
            cashConserves(year);
        }
        assertArkansasInactive(roth);
        var florida = plan(FL, PRIMARY_DIES, TAXABLE_FIRST, new RothIRA("Synthetic Roth", PRIMARY, money("10000")));
        expense(florida, "1000");
        var year = run(florida).getYears().getLast();
        var withdrawal = year.getIncomeSources().stream().filter(s -> s.purpose() == CASH_FLOW).findFirst().orElseThrow();
        assertEquals(PRIMARY, withdrawal.owner().orElseThrow());
        equal("0", withdrawal.modeledTaxableAmount().orElseThrow());
        equal("0", year.getAdjustedGrossIncome());
        cashConserves(year);
    }

    @Test
    void qualifiedLifePrincipalAvoidsAnInactiveDrawWithoutChangingIraLineage() {
        var ira = new TraditionalIRA("Synthetic primary", PRIMARY, money("100000"));
        var plan = plan(AR, PRIMARY_DIES, TAXABLE_FIRST, ira);
        plan.setConditionalBenefits(List.of(ArkansasLifePrincipalProjectionTest.policy(PersonReference.PRIMARY, "2000", null, null)));
        expense(plan, "1000");
        var projection = run(plan);
        var death = projection.getYearAt(1);
        assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, death.arkansasValidationTax().orElseThrow().filingStatus());
        var benefit = death.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).findFirst().orElseThrow();
        assertEquals(PersonReference.SPOUSE, benefit.recipient());
        equal("2000", benefit.excluded());
        equal("0", benefit.taxable());
        var year = projection.getYears().getLast();
        equal("1000", year.getEndingRetainedNonQualifiedAssets());
        equal("100000", year.getEndingBalance(ira));
        assertEquals(PRIMARY, year.getEndingAccountSnapshots().getFirst().getAccount().getOwnership());
        cashConserves(year);
        assertThrows(UnsupportedOperationException.class, () -> ConditionalBenefitAdmission.requireAnalyzerSupported(plan));
    }

    @Test
    void firstDeathYearAlsoKeepsTheOriginalDebitOwnerWhileASurvivorPensionNamesItsRecipient() {
        var florida = plan(FL, PRIMARY_DIES, TAXABLE_FIRST,
                new TraditionalIRA("Synthetic primary", PRIMARY, money("100000")));
        var deathDate = START.plusYears(1);
        florida.getHousehold().addExpense(new Expense("Synthetic first-death expense", money("1000"),
                GrowthCategory.GENERAL, deathDate, deathDate, ExpenseType.ONE_TIME));
        var floridaYear = run(florida).getYearAt(1);
        assertEquals(PRIMARY, floridaYear.getIncomeSources().stream().filter(s -> s.purpose() == CASH_FLOW)
                .findFirst().orElseThrow().owner().orElseThrow());
        equal("1000", floridaYear.getAdjustedGrossIncome());
        cashConserves(floridaYear);

        var arkansas = plan(AR, PRIMARY_DIES, TAXABLE_FIRST,
                new TraditionalIRA("Synthetic primary", PRIMARY, money("100000")));
        var pension = new Pension("Synthetic survivor pension", PRIMARY, deathDate, null, money("200"), ZERO, money("100"));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        arkansas.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var arkansasYear = run(arkansas).getYearAt(1);
        var paid = arkansasYear.getIncomeSources().stream().filter(s -> s.kind() == PENSION).findFirst().orElseThrow();
        assertEquals(SPOUSE, paid.owner().orElseThrow());
        assertEquals(PRIMARY, paid.pension().orElseThrow().originalOwner());
        equal("1200", paid.grossAmount());
        assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, arkansasYear.arkansasValidationTax().orElseThrow().filingStatus());
        assertEquals(PRIMARY, arkansasYear.getEndingAccountSnapshots().getFirst().getAccount().getOwnership());
        cashConserves(arkansasYear);
    }

    @Test
    void explicitInheritedAccountKeepsItsEnteredBeneficiaryAndDoesNotGenerateBeneficiaryRmd() {
        var original = new InheritedAccountInformation(LocalDate.of(1935, 1, 1), LocalDate.of(2025, 1, 1),
                BeneficiaryRelationship.CHILD);
        var inherited = new InheritedTraditionalIRA("Synthetic inherited", SPOUSE, money("100000"), original);
        var florida = plan(FL, PRIMARY_DIES, TAXABLE_FIRST, inherited);
        expense(florida, "1000");
        var year = run(florida).getYears().getLast();
        equal("0", year.getRmdDistributedInProjection());
        var source = year.getIncomeSources().stream().filter(s -> s.purpose() == CASH_FLOW).findFirst().orElseThrow();
        assertEquals(SPOUSE, source.owner().orElseThrow());
        assertEquals(original.getOriginalOwnerDateOfDeath(),
                source.account().orElseThrow().inheritedInformation().orElseThrow().getOriginalOwnerDateOfDeath());
        equal("1000", year.getAdjustedGrossIncome());
        cashConserves(year);

        var arkansas = plan(AR, PRIMARY_DIES, TAXABLE_FIRST, inherited);
        expense(arkansas, "1000");
        var error = assertThrows(ArkansasProjectionUnavailableException.class, () -> run(arkansas));
        assertEquals(ArkansasTaxIssue.Kind.RETIREMENT_SOURCE, error.issue().kind());
        assertTrue(error.detail().contains("Inherited retirement history"));
    }

    @Test
    void bothStrategiesKeepTheirPriorityAndDoNotCreateANewRetirementOwner() {
        for (var order : List.of(TAXABLE_FIRST, TAX_DEFERRED_FIRST)) {
            var primary = new TraditionalIRA("Synthetic priority first", PRIMARY, money("500"));
            var spouse = new TraditionalIRA("Synthetic priority second", SPOUSE, money("3000"));
            var cash = new CheckingAccount("Synthetic joint", JOINT, money("1500"));
            var plan = plan(FL, PRIMARY_DIES, order, primary, spouse, cash);
            expense(plan, "1000");
            var year = run(plan).getYears().getLast();
            var sources = year.getIncomeSources().stream().filter(s -> s.purpose() == CASH_FLOW).toList();
            if (order == TAXABLE_FIRST) {
                assertEquals(List.of(JOINT), sources.stream().map(s -> s.owner().orElseThrow()).toList());
                equal("0", year.getAdjustedGrossIncome());
            } else {
                assertEquals(List.of(PRIMARY, SPOUSE), sources.stream().map(s -> s.owner().orElseThrow()).toList());
                equal("1000", year.getAdjustedGrossIncome());
            }
            equal("4000", year.getEndingInvestableAssets());
            cashConserves(year);
        }
    }

    @Test
    void legacyDeterministicRmdUsesOriginalAgeWhileArkansasDoesNotTransferTheBalance() {
        for (var state : List.of(FL, AR)) {
            var primary = new TraditionalIRA("Synthetic primary", PRIMARY, money("26500"));
            var plan = plan(state, PRIMARY_DIES, TAXABLE_FIRST, primary,
                    new CheckingAccount("Synthetic joint", JOINT, money("50000")));
            plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1955, 1, 1));
            var last = run(plan).getYears().getLast();
            if (state == FL) {
                equal("1000", last.getRmdDistributedInProjection());
                assertEquals(PRIMARY, last.getIncomeSources().stream().filter(s -> s.kind() == RMD)
                        .findFirst().orElseThrow().owner().orElseThrow());
                equal("25500", last.getEndingBalance(primary));
            } else {
                equal("0", last.getRmdDistributedInProjection());
                equal("26500", last.getEndingBalance(primary));
            }
            assertEquals(PRIMARY, last.getEndingAccountSnapshots().getFirst().getAccount().getOwnership());
            cashConserves(last);
        }
    }

    @ParameterizedTest
    @EnumSource(value = RothConversionStrategy.class, names = {"FIXED_AMOUNT", "FILL_12_PERCENT_BRACKET"})
    void conversionEligibilityDiffersByRouteAndNeverCrossesAccountOwners(RothConversionStrategy strategy) {
        for (var state : List.of(FL, AR)) {
            var primary = new TraditionalIRA("Synthetic primary", PRIMARY, money("100000"));
            var plan = plan(state, PRIMARY_DIES, TAXABLE_FIRST, primary, new RothIRA("Synthetic primary Roth", PRIMARY, ZERO),
                    new TraditionalIRA("Synthetic survivor", SPOUSE, money("5000")), new RothIRA("Synthetic survivor Roth", SPOUSE, ZERO),
                    new CheckingAccount("Synthetic joint", JOINT, money("100000")));
            plan.setRothConversionRequest(new RothConversionRequest(true, 2028, money("10000"),
                    RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME));
            var last = run(plan).getYears().getLast();
            if (state == AR) {
                equal("5000", last.getSpouseRothConversion());
                equal("0", last.getPrimaryRothConversion());
                equal("100000", last.getEndingBalance(primary));
            } else {
                assertTrue(last.getPrimaryRothConversion().signum() > 0);
                if (strategy == RothConversionStrategy.FIXED_AMOUNT) equal("10000", last.getPrimaryRothConversion());
            }
            last.getIncomeSources().stream().filter(s -> s.destination().isPresent()).forEach(s ->
                    assertEquals(s.account().orElseThrow().owner(), s.destination().orElseThrow().owner()));
            cashConserves(last);
        }
    }

    @Test
    void taxesOnAnActiveConversionCanReachAnInactiveOriginalOwner() {
        for (var state : List.of(AR, FL)) {
            var plan = plan(state, PRIMARY_DIES, TAXABLE_FIRST,
                    new TraditionalIRA("Synthetic primary", PRIMARY, money("100000")),
                    new TraditionalIRA("Synthetic survivor", SPOUSE, money("100000")),
                    new RothIRA("Synthetic survivor Roth", SPOUSE, ZERO));
            plan.setRothConversionRequest(new RothConversionRequest(true, 2028, money("50000"),
                    RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
            if (state == AR) {
                assertArkansasInactive(plan);
            } else {
                var last = run(plan).getYears().getLast();
                equal("50000", last.getSpouseRothConversion());
                assertTrue(last.getTaxFundingWithdrawal().signum() > 0);
                assertEquals(PRIMARY, last.getIncomeSources().stream().filter(s -> s.purpose() == TAX_FUNDING)
                        .findFirst().orElseThrow().owner().orElseThrow());
                legacyPositiveFundingResidual(last);
            }
        }
    }

    @Test
    void finalSurvivorEstateAndPersistenceKeepTheOriginalOwner(@TempDir Path folder) throws Exception {
        var plan = plan(FL, PRIMARY_DIES, TAXABLE_FIRST,
                new TraditionalIRA("Synthetic primary", PRIMARY, money("100000")),
                new BrokerageAccount("Synthetic survivor", SPOUSE, money("10000")));
        var frozenBefore = CompleteProjectionSnapshot.freeze(plan);
        var projection = run(plan);
        assertEquals(frozenBefore, CompleteProjectionSnapshot.freeze(plan));
        var repository = new JsonRetirementPlanRepository();
        var file = folder.resolve("synthetic.json");
        repository.save(plan, file);
        var restored = repository.load(file);
        assertEquals(frozenBefore, CompleteProjectionSnapshot.freeze(restored));
        var year = projection.getYears().getLast();
        assertEquals(2028, year.getCalendarYear());
        equal("110000", projection.getYearAt(1).getEndingInvestableAssets());
        equal("110000", year.getEndingInvestableAssets());
        equal("25000", year.getEstimatedHeirTax());
        equal("85000", year.getAfterTaxEstateValue());
        assertEquals(List.of(PRIMARY, SPOUSE),
                year.getEndingAccountSnapshots().stream().map(s -> s.getAccount().getOwnership()).toList());
        var codec = new RetainedProjectionCodec();
        var stored = codec.encode(RetainedProjectionSnapshot.capture(projection));
        var decoded = codec.decode(stored.bytes());
        assertArrayEquals(stored.bytes(), codec.encode(decoded).bytes());
        assertEquals(PRIMARY, decoded.restoreProjection().getYears().getLast()
                .getEndingAccountSnapshots().getFirst().getAccount().getOwnership());
    }

    private static RetirementPlan plan(StateTaxJurisdiction state, DeathScenario death, WithdrawalStrategyType order, Account... accounts) {
        var base = ArkansasLifePrincipalProjectionTest.fixture(START, 3, death, death == BOTH_SURVIVE ? 0 : 2027, "0");
        base.getHousehold().members().forEach(person -> person.setBirthDate(LocalDate.of(1965, 1, 1)));
        var old = base.getPlanningAssumptions();
        var portfolio = new AccountPortfolio();
        for (var account : accounts) portfolio.addAccount(account);
        var plan = new RetirementPlan(base.getHousehold(), portfolio, new PlanningAssumptions(old.getEconomicAssumptions(),
                old.getTaxAssumptions(), new WithdrawalAssumptions(order), old.getDeathScenarioAssumptions(),
                3, START, StateTaxAssumptions.simplified(state)));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        if (state == AR) ArkansasRegularTaxOnlyTest.selectPolicy(plan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        return plan;
    }

    private static void expense(RetirementPlan plan, String amount) {
        plan.getHousehold().addExpense(new Expense("Synthetic expense", money(amount), GrowthCategory.GENERAL,
                SPEND, SPEND, ExpenseType.ONE_TIME));
    }

    private static Projection run(RetirementPlan plan) {
        return ProjectionEngine.selectsArkansas(plan) ? ArkansasLifePrincipalProjectionTest.run(plan) : new ProjectionEngine().project(plan);
    }

    private static void assertArkansasInactive(RetirementPlan plan) {
        var error = assertThrows(ArkansasProjectionUnavailableException.class, () -> run(plan));
        assertEquals(2028, error.year());
        assertEquals(ArkansasTaxIssue.Kind.SOURCE_OR_TAX, error.issue().kind());
        assertEquals("Arkansas taxable receipt requires an active individual recipient.", error.detail());
    }

    private static void cashConserves(ProjectionYear year) {
        equal("0", cashResidual(year));
    }

    private static void legacyPositiveFundingResidual(ProjectionYear year) {
        var residual = cashResidual(year);
        assertTrue(residual.signum() < 0 && residual.abs().compareTo(money("0.02")) <= 0,
                () -> "Legacy tax funding leaves a small tolerated underage, observed " + residual);
    }

    private static BigDecimal cashResidual(ProjectionYear year) {
        var cash = year.getHouseholdCashSettlement();
        var inflows = cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal())
                .add(cash.taxFundingWithdrawal());
        var outflows = cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes())
                .add(cash.retainedHouseholdSurplus());
        return inflows.subtract(outflows);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual), () -> expected + " expected, " + actual + " received");
    }
}
