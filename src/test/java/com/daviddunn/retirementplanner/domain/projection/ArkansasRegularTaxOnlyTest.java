package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference;
import com.daviddunn.retirementplanner.domain.financial.ContractualPremiumPolicy;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED;
import static com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.STRICT;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.fixture;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.policy;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.run;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

public class ArkansasRegularTaxOnlyTest {
    private static final LocalDate START = LocalDate.of(2028, 1, 1);

    enum Disposition { SUPPORTED_SUCCESS, EXPECTED_STRICT_REFUSAL, EXPECTED_UNSUPPORTED_SOURCE_REFUSAL }

    @ParameterizedTest
    @CsvSource({
            "false,false,0,STRICT,SUPPORTED_SUCCESS,1390.02,2840,0,0",
            "false,false,0,REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED,SUPPORTED_SUCCESS,1390.02,2840,0,0",
            "true,false,30000,STRICT,EXPECTED_STRICT_REFUSAL,-,-,-,-",
            "true,true,70000,STRICT,EXPECTED_STRICT_REFUSAL,-,-,-,-",
            "true,false,30000,REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED,SUPPORTED_SUCCESS,312.67,0,0,312.67",
            "true,false,70000,REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED,SUPPORTED_SUCCESS,2014.59,4865.6264,40000,6880.22",
            "true,true,70000,REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED,SUPPORTED_SUCCESS,302.04,0,0,0"
    })
    void survivorMatrix(boolean dies, boolean paid, String spending, ArkansasLowIncomePolicy policy, Disposition disposition,
            String state, String federal, String spendingWithdrawal, String taxWithdrawal) {
        var plan = survivorPlan(dies, paid, "2500", "0", 2);
        addOneTimeExpense(plan, spending);
        selectPolicy(plan, policy);
        if (disposition == Disposition.EXPECTED_STRICT_REFUSAL) {
            assertEquals(ArkansasTaxIssue.Kind.LOW_INCOME, refuse(plan).issue().kind(), disposition::name);
            return;
        }
        var projection = run(plan);
        var year = projection.getFirstYear();
        assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, year.arkansasValidationTax().orElseThrow().filingStatus());
        money(state, year.getStateIncomeTax());
        money(federal, year.getFederalIncomeTax());
        money(spendingWithdrawal, year.getHouseholdCashSettlement().spendingWithdrawal());
        money(taxWithdrawal, year.getTaxFundingWithdrawal());
        assertEquals(policy, year.arkansasValidationTax().orElseThrow().lowIncomePolicy());
        if (dies) {
            var later = projection.getLastYear();
            assertEquals(FilingStatus.SINGLE, later.arkansasValidationTax().orElseThrow().filingStatus());
            money("415.02", later.getStateIncomeTax());
            money("1420", later.getFederalIncomeTax());
        }
        if (paid) {
            var receipt = year.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).findFirst().orElseThrow();
            money("100000", receipt.excluded());
            money("0", receipt.taxable());
            money("30000", year.getAdjustedGrossIncome());
            money("30000", year.getModifiedAdjustedGrossIncome());
            money("59697.96", year.getEndingRetainedNonQualifiedAssets());
            money("309697.96", year.getEndingInvestableAssets());
        }
        cashConserves(year);
    }

    @ParameterizedTest
    @CsvSource({"0,38000,1970,false,true,798.02", "2500,10000,1965,true,false,650.02", "2500,0,1965,false,false,302.04"})
    void eachStrictRefusalBecomesAnExactRegularEstimate(String survivor, String conversion, int birthYear,
            boolean gross, boolean net, String tax) {
        var plan = survivorPlan(true, true, survivor, "0", 1);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(birthYear, 6, 15));
        if (!conversion.equals("0")) roth(plan, RothConversionStrategy.FIXED_AMOUNT, conversion);
        var diagnostics = new ArrayList<ArkansasLowIncomeDiagnostic>();
        var engine = ProjectionEngine.forArkansasProduction();
        assertEquals(ArkansasTaxIssue.Kind.LOW_INCOME, assertThrows(ArkansasProjectionUnavailableException.class,
                () -> engine.projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), ignored -> { },
                        Optional.of(diagnostics::add))).issue().kind());
        var refusal = diagnostics.getFirst();
        assertEquals(gross ? ArkansasTaxIssue.Bound.PASS : ArkansasTaxIssue.Bound.FAIL, refusal.section301Gross());
        assertEquals(net ? ArkansasTaxIssue.Bound.PASS : ArkansasTaxIssue.Bound.FAIL, refusal.section501Net());
        selectPolicy(plan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        var chosen = run(plan).getFirstYear();
        money(tax, chosen.getStateIncomeTax());
        money(conversion, chosen.getRothConversion());
        money("0", chosen.getTaxFundingWithdrawal());
        assertEquals(ArkansasValidationTax.REGULAR_TAX_ONLY_POLICY, chosen.arkansasValidationTax().orElseThrow().versions()
                .get(ArkansasValidationTax.LOW_INCOME_POLICY_KEY));
        cashConserves(chosen);
    }

    @Test
    void targetWithTaxFundingHasAnIndependentCapacityOptimumAndCancels() {
        var plan = survivorPlan(true, false, "2500", "0", 1);
        selectPolicy(plan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        addOneTimeExpense(plan, "30000");
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(b("10000"));
        roth(plan, RothConversionStrategy.FILL_12_PERCENT_BRACKET, "0");
        var chosen = run(plan).getFirstYear();
        // Full conversion is the capacity upper bound. The resulting Roth cash is available to fund tax.
        // A = $40,000, regular AR = $650.02 and federal = $780, with no additional taxable draw.
        money("10000", chosen.getRothConversion());
        money("1430.02", chosen.getTaxFundingWithdrawal());
        money("40000", chosen.getAdjustedGrossIncome());
        money("650.02", chosen.getStateIncomeTax());
        money("780", chosen.getFederalIncomeTax());
        var validation = chosen.arkansasValidationTax().orElseThrow();
        assertEquals(ArkansasValidationTax.REGULAR_TAX_ONLY_TARGET, validation.versions().get("arkansas-roth-target"));
        assertEquals(ArkansasValidationTax.REGULAR_TAX_ONLY_FUNDING, validation.versions().get("arkansas-tax-funding"));
        assertEquals(512, validation.fundingMetrics().candidateBudget());
        money("0", validation.taxableFundingIncome());
        assertTrue(validation.targetMetrics().orElseThrow().innerEvaluations() <= 4096);
        System.out.println("Regular tax only synthetic target " + validation.targetMetrics().orElseThrow());
        cashConserves(chosen);

        var stack = StackWalker.getInstance();
        assertThrows(AnalysisCancelledException.class, () -> ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, () -> stack.walk(frames -> frames.anyMatch(frame ->
                        frame.getClassName().equals("com.daviddunn.retirementplanner.domain.tax.ArkansasRothFunding"))),
                        count -> fail("A cancelled target must not publish a year")));
    }

    @Test
    void medicareRepricesTheRegularPolicyAndReceivesTheDeathPrincipalOnce() {
        var plan = survivorPlan(true, true, "1620", "0", 1);
        addOneTimeExpense(plan, "119440");
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1962, 6, 15));
        assertEquals(ArkansasTaxIssue.Kind.LOW_INCOME, refuse(plan).issue().kind());
        selectPolicy(plan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        var year = run(plan).getFirstYear();
        // $202.90 per month induces an ordinary draw. Arkansas net begins at $10,934.80.
        // .02 × (10,934.80 − 5,600 + 20.10) − 87 rounds to the exact funding cent $20.10.
        money("2434.80", year.getHouseholdCashSettlement().medicarePremiums());
        money("2434.80", year.getHouseholdCashSettlement().spendingWithdrawal());
        money("20.10", year.getTaxFundingWithdrawal());
        money("20.10", year.getStateIncomeTax());
        money("0", year.getFederalIncomeTax());
        money("21894.90", year.getAdjustedGrossIncome());
        money("19440", year.getModifiedAdjustedGrossIncome());
        assertEquals(1, year.getIncomeSources().stream().filter(s -> s.conditionalBenefit().isPresent()).count());
        cashConserves(year);
    }

    @Test
    void zeroTaxHasPolicyEvidenceAndLaterGrowthUsesRetainedCash() {
        var plan = survivorPlan(true, true, "400", ".05", 2);
        selectPolicy(plan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        addOneTimeExpense(plan, "54800");
        var projection = run(plan);
        var paid = projection.getFirstYear();
        money("0", paid.getStateIncomeTax());
        money("0", paid.getRetainedNonQualifiedAssetGrowth());
        money("50000", paid.getEndingRetainedNonQualifiedAssets());
        assertTrue(paid.arkansasValidationTax().orElseThrow().versions().containsKey(ArkansasValidationTax.LOW_INCOME_POLICY_KEY));
        var later = projection.getLastYear();
        money("2500", later.getRetainedNonQualifiedAssetGrowth());
        money("57300", later.getEndingRetainedNonQualifiedAssets());
        assertTrue(later.getIncomeSources().stream().noneMatch(s -> s.conditionalBenefit().isPresent()));
    }

    @Test
    void insuredPremiumEndsAndRequiredDistributionAndConversionStaySeparate() {
        var premiumPlan = fixture(START, 2, DeathScenario.SPOUSE_DIES, 2029, "0");
        premiumPlan.getHousehold().members().forEach(person -> person.setBirthDate(LocalDate.of(1965, 6, 15)));
        selectPolicy(premiumPlan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        premiumPlan.getAccountPortfolio().getAccounts().forEach(account -> account.setCurrentBalance(ZERO));
        premiumPlan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.CheckingAccount(
                "Synthetic premium cash", PRIMARY, b("1200")));
        premiumPlan.setConditionalBenefits(List.of(policy(PersonReference.SPOUSE, "100000", null, null)));
        premiumPlan.getHousehold().addExpense(new Expense("Synthetic premium", b("1200"), GrowthCategory.NONE, START, null,
                ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM, null, new ContractualPremiumPolicy(
                    ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1, ContractualPremiumPolicy.Insured.SPOUSE)));
        var premium = run(premiumPlan);
        money("1200", premium.getFirstYear().getAnnualExpenses());
        money("0", premium.getLastYear().getAnnualExpenses());
        money("100000", premium.getLastYear().getEndingRetainedNonQualifiedAssets());

        var rmdPlan = survivorPlan(true, true, "0", "0", 1);
        selectPolicy(rmdPlan, REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        rmdPlan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1954, 6, 15));
        var ira = rmdPlan.getAccountPortfolio().getAccounts().getFirst();
        ira.setCurrentBalance(b("1530000"));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(2028, b("1530000"), ZERO));
        roth(rmdPlan, RothConversionStrategy.FIXED_AMOUNT, "10000");
        var required = run(rmdPlan).getFirstYear();
        money("60000", required.getRequiredMinimumDistribution());
        money("10000", required.getRothConversion());
        money("70000", required.getAdjustedGrossIncome());
        money("0", required.getTaxFundingWithdrawal());
        cashConserves(required);
    }

    @ParameterizedTest
    @EnumSource(ArkansasLowIncomePolicy.class)
    void expectedUnsupportedSourceRefusalAndAnalyzerBoundaries(ArkansasLowIncomePolicy policy) {
        var plan = survivorPlan(true, false, "2500", "0", 1);
        selectPolicy(plan, policy);
        var pension = (Pension) plan.getHousehold().getSpouse().getIncomeSources().getFirst();
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.UNKNOWN);
        assertEquals(ArkansasTaxIssue.Kind.PENSION_ORIGIN, refuse(plan).issue().kind(),
                Disposition.EXPECTED_UNSUPPORTED_SOURCE_REFUSAL::name);
        if (policy != STRICT) {
            assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                    () -> ConditionalBenefitAdmission.requireAnalyzerSupported(plan));
            var engine = ProjectionEngine.forArkansasProduction();
            assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                    () -> engine.runArkansasAnalysis(AnalysisCancellationToken.none(), () -> engine.project(plan)));
            plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
            assertEquals(ArkansasTaxIssue.Kind.NIIT, refuse(plan).issue().kind());
        }
    }

    @Test
    void provenUnreachedInheritedSourceKeepsTheCappedPoolAndExactTargetInBothPolicies() {
        var plan = survivorPlan(true, false, "7000", "0", 1);
        plan.getAccountPortfolio().addAccount(new InheritedTraditionalIRA("Synthetic inherited IRA", PRIMARY, b("1000"),
                new InheritedAccountInformation(LocalDate.of(1962, 1, 1), START.minusYears(2), BeneficiaryRelationship.CHILD)));
        plan.setConditionalBenefits(List.of(policy(PersonReference.SPOUSE, "1000", null, null)));
        addOneTimeExpense(plan, "90000");
        roth(plan, RothConversionStrategy.FILL_22_PERCENT_BRACKET, "0");
        for (var policy : ArkansasLowIncomePolicy.values()) {
            selectPolicy(plan, policy);
            var year = run(plan).getFirstYear();
            money("110197.48", year.getRothConversion());
            money("44402.52", year.getTaxFundingWithdrawal());
            money("8470.52", year.getStateIncomeTax());
            money("6000", year.arkansasValidationTax().orElseThrow().provenance().election().retirementExclusion());
            cashConserves(year);
        }
    }

    public static RetirementPlan survivorPlan(boolean dies, boolean policy, String survivorMonthly, String growth, int years) {
        var plan = fixture(START, years, dies ? DeathScenario.SPOUSE_DIES : DeathScenario.BOTH_SURVIVE,
                dies ? 2028 : 0, growth);
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.getHousehold().members().forEach(person -> person.setBirthDate(LocalDate.of(1965, 6, 15)));
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(ZERO);
        var pension = new Pension("Synthetic pension", AccountOwnership.SPOUSE, START, null, b("5000"), ZERO, b(survivorMonthly));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getSpouse().addIncomeSource(pension);
        if (policy) plan.setConditionalBenefits(List.of(policy(PersonReference.SPOUSE, "100000", null, null)));
        return plan;
    }

    public static void selectPolicy(RetirementPlan plan, ArkansasLowIncomePolicy policy) {
        var assumptions = plan.getPlanningAssumptions();
        var state = assumptions.getStateTaxAssumptions();
        plan.setPlanningAssumptions(assumptions.withStateTaxAssumptions(new StateTaxAssumptions(state.jurisdiction(),
                state.calculationMethod(), state.effectiveRateOverride(), policy)));
    }

    private static void addOneTimeExpense(RetirementPlan plan, String amount) {
        if (!amount.equals("0")) plan.getHousehold().addExpense(new Expense("Synthetic expenses", b(amount), GrowthCategory.GENERAL,
                START, START, ExpenseType.ONE_TIME));
    }

    private static void roth(RetirementPlan plan, RothConversionStrategy strategy, String amount) {
        plan.getAccountPortfolio().addAccount(new RothIRA("Synthetic Roth IRA", PRIMARY, ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2028, b(amount), RothConversionStopRule.NEVER,
                strategy, RothConversionFrequency.ONE_TIME));
    }

    private static ArkansasProjectionUnavailableException refuse(RetirementPlan plan) {
        return assertThrows(ArkansasProjectionUnavailableException.class, () -> ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), count -> fail("No year published")));
    }

    private static void cashConserves(ProjectionYear year) {
        var cash = year.getHouseholdCashSettlement();
        var totalCash = cash.guaranteedIncome().add(cash.projectedPeriodRmdCash())
                .add(cash.spendingWithdrawal()).add(cash.taxFundingWithdrawal());
        var used = cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes()).add(cash.retainedHouseholdSurplus());
        assertEquals(0, totalCash.compareTo(used));
    }

    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, b(expected).compareTo(actual), actual::toPlainString);
    }
}
