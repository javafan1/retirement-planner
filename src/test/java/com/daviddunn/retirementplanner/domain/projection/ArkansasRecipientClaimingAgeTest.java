package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasRecipientDiagnostic;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.policy;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasRegularTaxOnlyTest.selectPolicy;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

public class ArkansasRecipientClaimingAgeTest {
    private static final ProjectionEngine ENGINE = ProjectionEngine.forArkansasProduction();
    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final LocalDate BIRTH = LocalDate.of(1966, 6, 15);
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2056, 1, 1);
    private static final String REFUSAL = "Arkansas taxable receipt requires an active individual recipient.";
    private static final ConditionalBenefitDefinition INSURANCE = policy(PersonReference.PRIMARY, "8000", null, null);

    @ParameterizedTest
    @CsvSource({"61,REFUSE", "62,REFUSE", "63,5978.53", "64,14804.03", "67,23695.20"})
    void claimingAgeAloneSelectsThe2056CashSource(int age, String expected) {
        var plan = fixture(age, 30, "770000", REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        var records = new ArrayList<String>();
        if (expected.equals("REFUSE")) {
            var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> observed(plan, records));
            assertRefusal(failure);
            assertSpendingRecord(records, "INITIAL", REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
            assertTrue(cashThrough2056(age).compareTo(new BigDecimal("767565.20")) < 0);
            return;
        }
        var result = observed(plan, records);
        assertTrue(records.isEmpty(), "A passing recipient check does not emit a diagnostic");
        var year = result.getYears().getLast();
        money(new BigDecimal(expected), year.getEndingRetainedNonQualifiedAssets());
        money(cashThrough2056(age).subtract(new BigDecimal("770000")), year.getEndingRetainedNonQualifiedAssets());
        money(new BigDecimal("2434.80"), year.getMedicarePremiumCalculation().totalAnnualMedicarePremium());
        money(ZERO, year.getAdjustedGrossIncome());
        money(ZERO, year.getFederalIncomeTax());
        money(ZERO, year.getStateIncomeTax());
        money(ZERO, year.getRothConversion());
        money(ZERO, year.getTaxFundingWithdrawal());
        assertFalse(year.getIncomeSources().stream().anyMatch(s -> s.isRetirementDistribution()));
        assertEquals(FilingStatus.SINGLE, year.arkansasValidationTax().orElseThrow().filingStatus());
    }

    @Test
    void samePlanFirstDiffersIn2028AndKeepsTheSameQualifiedReceipt() {
        var plan = fixture(62, 29, "770000", REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        var early = run(plan);
        setAge(plan, 63);
        var late = run(plan);
        var firstEarly = early.getYearAt(0);
        var firstLate = late.getYearAt(0);
        assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, firstEarly.arkansasValidationTax().orElseThrow().filingStatus());
        assertEquals(firstEarly.getIncomeSources(), firstLate.getIncomeSources());
        var benefit = firstEarly.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).findFirst().orElseThrow();
        assertEquals(PersonReference.SPOUSE, benefit.recipient());
        money(new BigDecimal("8000"), benefit.excluded());
        money(ZERO, benefit.taxable());
        assertEquals(firstEarly.getSocialSecurityResult(), firstLate.getSocialSecurityResult());
        money(new BigDecimal("8000"), firstEarly.getEndingRetainedNonQualifiedAssets());
        var early2028 = early.getYearAt(1);
        var late2028 = late.getYearAt(1);
        assertEquals(2028, early2028.getCalendarYear());
        money(new BigDecimal("16725.03"), early2028.getSocialSecurityResult().householdBenefit());
        money(ZERO, late2028.getSocialSecurityResult().householdBenefit());
        assertEquals(FilingStatus.SINGLE, early2028.arkansasValidationTax().orElseThrow().filingStatus());
        for (int i = 0; i < early.size(); i++) {
            var earlierYear = early.getYearAt(i);
            var laterYear = late.getYearAt(i);
            money(earlierYear.getSocialSecurityResult().spouseOwnBenefit(), laterYear.getSocialSecurityResult().spouseOwnBenefit());
            money(ZERO, earlierYear.getAdjustedGrossIncome());
            money(ZERO, laterYear.getAdjustedGrossIncome());
            assertFalse(earlierYear.getIncomeSources().stream().anyMatch(s -> s.isRetirementDistribution()));
            assertFalse(laterYear.getIncomeSources().stream().anyMatch(s -> s.isRetirementDistribution()));
        }
        money(new BigDecimal("737984.99"), early.getYears().getLast().getEndingRetainedNonQualifiedAssets());
        money(new BigDecimal("748276.17"), late.getYears().getLast().getEndingRetainedNonQualifiedAssets());
    }

    @ParameterizedTest
    @EnumSource(ArkansasLowIncomePolicy.class)
    void narrowerExpenseHitsOnlyTheMedicareAwarePassInBothTaxPolicies(ArkansasLowIncomePolicy taxPolicy) {
        var plan = fixture(62, 30, "765000", taxPolicy);
        var records = new ArrayList<String>();
        assertRefusal(assertThrows(ArkansasProjectionUnavailableException.class, () -> observed(plan, records)));
        assertSpendingRecord(records, "MEDICARE_AWARE", taxPolicy);
        var samePlanUnobserved = assertThrows(ArkansasProjectionUnavailableException.class, () -> run(plan));
        assertRefusal(samePlanUnobserved);
        assertFalse(ArkansasRecipientDiagnostic.isEnabled());
        setAge(plan, 63);
        money(new BigDecimal("10978.53"), run(plan).getYears().getLast().getEndingRetainedNonQualifiedAssets());
    }

    @Test
    void age61IsOfferedAsASurvivorElectionAndRejectedAsAnOwnRetirementElection() {
        var plan = fixture(61, 30, "770000", REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        assertTrue(SurvivorBenefitClaimingPolicy.choices(plan.getHousehold(), DeathScenario.PRIMARY_DIES, 2027).ages().contains(61));
        assertThrows(IllegalArgumentException.class, () -> new SocialSecurityIncome("Synthetic", SPOUSE,
                BIRTH.plusYears(61), null, new BigDecimal("700"), 61, ZERO, 2027));
    }

    @Test
    void positiveTaxFundingAfterALivingOwnerConversionIsDistinguishedFromSpending() {
        var start = LocalDate.of(2053, 1, 1);
        var plan = ArkansasLifePrincipalProjectionTest.fixture(start, 4, DeathScenario.PRIMARY_DIES, 2054, "0");
        plan.getHousehold().members().forEach(person -> person.setBirthDate(LocalDate.of(1993, 6, 15)));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        selectPolicy(plan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        var inactive = plan.getAccountPortfolio().getAccounts().getFirst();
        inactive.setCurrentBalance(new BigDecimal("50000"));
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(new BigDecimal("100000"));
        plan.getAccountPortfolio().addAccount(new RothIRA("Synthetic Roth", SPOUSE, ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2056, new BigDecimal("50000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var records = new ArrayList<String>();
        assertRefusal(assertThrows(ArkansasProjectionUnavailableException.class, () -> observed(plan, records)));
        assertEquals(1, records.size());
        assertTrue(records.getFirst().contains("receiptPurpose=TAX_FUNDING\n"));
        assertTrue(records.getFirst().contains("stage=EXACT_PRICING\ncandidate=POSITIVE\n"));
        inactive.setCurrentBalance(ZERO);
        var year = run(plan).getYears().getLast();
        money(new BigDecimal("50000"), year.getSpouseRothConversion());
        money(new BigDecimal("5884.24"), year.getTaxFundingWithdrawal());
    }

    public static RetirementPlan fixture(int age, int years, String expense, ArkansasLowIncomePolicy taxPolicy) {
        var plan = ArkansasLifePrincipalProjectionTest.fixture(START, years, DeathScenario.PRIMARY_DIES, 2027, "0");
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        setAge(plan, age);
        selectPolicy(plan, taxPolicy);
        for (var owner : List.of(PRIMARY, SPOUSE)) {
            var person = owner == PRIMARY ? plan.getHousehold().getPrimaryPerson() : plan.getHousehold().getSpouse();
            person.setBirthDate(BIRTH);
            person.addIncomeSource(new SocialSecurityIncome("Synthetic Social Security", owner, BIRTH.plusYears(67), null,
                    new BigDecimal(owner == AccountOwnership.PRIMARY ? "3000" : "700"), 67, ZERO, 2027));
        }
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("100000"));
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(ZERO);
        plan.setConditionalBenefits(List.of(INSURANCE));
        plan.getHousehold().addExpense(new Expense("Synthetic expense", new BigDecimal(expense), GrowthCategory.GENERAL,
                EXPENSE_DATE, EXPENSE_DATE, ExpenseType.ONE_TIME));
        return plan;
    }

    private static void setAge(RetirementPlan plan, int age) {
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2027, age),
                old.getProjectionLengthYears(), START, old.getStateTaxAssumptions()));
    }

    private static Projection observed(RetirementPlan plan, List<String> records) {
        return ArkansasRecipientDiagnostic.capture(AnalysisCancellationToken.none(), records::add, () -> run(plan));
    }

    public static Projection run(RetirementPlan plan) {
        return ((ProjectionExecutionResult.Completed) ENGINE.projectArkansasWithOutcome(
                plan, AnalysisCancellationToken.none(), ignored -> { })).projection();
    }

    private static void assertRefusal(ArkansasProjectionUnavailableException failure) {
        assertEquals(2056, failure.year());
        assertEquals(ArkansasProjectionUnavailableException.Reason.SOURCE_OR_TAX, failure.reason());
        assertEquals(ArkansasTaxIssue.Kind.SOURCE_OR_TAX, failure.issue().kind());
        assertEquals(REFUSAL, failure.detail());
    }

    private static void assertSpendingRecord(List<String> records, String pass, ArkansasLowIncomePolicy policy) {
        assertEquals(1, records.size());
        for (var expected : List.of("year=2056", "filing=SINGLE", "activeOwners=SPOUSE", "receiptOwner=PRIMARY",
                "receiptKind=ORDINARY_RETIREMENT_WITHDRAWAL", "receiptPurpose=CASH_FLOW", "accountType=TRADITIONAL_IRA",
                "deathYear=SURVIVOR", "mode=BASIC", "policy=" + policy.name(), "pass=" + pass,
                "stage=EXACT_PRICING", "rothPath=FIXED_OR_NONE", "candidate=ZERO", "category=ATTRIBUTION")) {
            assertTrue(records.getFirst().lines().anyMatch(expected::equals), expected);
        }
    }

    private static BigDecimal cashThrough2056(int age) {
        var reduction = new BigDecimal(".285").multiply(BigDecimal.valueOf(67 - age))
                .divide(BigDecimal.valueOf(7), 12, RoundingMode.HALF_UP);
        var monthly = new BigDecimal("3000").multiply(BigDecimal.ONE.subtract(reduction)).setScale(2, RoundingMode.HALF_UP);
        int claimYear = BIRTH.getYear() + age;
        int months = 7 + (2056 - claimYear) * 12;
        return monthly.multiply(BigDecimal.valueOf(months)).add(new BigDecimal("8000"))
                .subtract(new BigDecimal("2434.80").multiply(BigDecimal.valueOf(26)));
    }

    private static void money(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> expected + " expected, " + actual + " received");
    }
}
