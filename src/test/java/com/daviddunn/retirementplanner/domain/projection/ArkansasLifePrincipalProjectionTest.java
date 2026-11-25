package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ArkansasValidationReport;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.ContractualPremiumPolicy;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAX_DEFERRED_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

public class ArkansasLifePrincipalProjectionTest {
    public static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void exactFiftyThousandRetainedAndIndependentDirectTaxOracle(@TempDir Path folder) throws Exception {
        var plan = fixedTaxFixture();
        plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "100000", null, null)));
        var projection = run(plan);
        var year = projection.getFirstYear();

        // MFJ in 2027: the 1.5% federal index yields a $32,683 deduction and $25,172 first bracket.
        // Federal: $2,517.20 + 12% of $39,313. Arkansas: $86,228 net after the $6,000
        // retirement exclusion and $4,940 deduction. Its four slices yield $2,823.236 before $58 credits.
        money("7234.76", year.getFederalIncomeTax());
        money("2765.24", year.getStateIncomeTax());
        money("10000.00", year.getHouseholdCashSettlement().incomeTaxes());
        money("40000", year.getHouseholdCashSettlement().ordinaryExpenses());
        money("0", year.getHouseholdCashSettlement().spendingWithdrawal());
        money("0", year.getTaxFundingWithdrawal());
        money("0", year.getRequiredMinimumDistribution());
        money("97168", year.getRothConversion());
        money("6000", year.arkansasValidationTax().orElseThrow().provenance().election().retirementExclusion());
        money("100000", year.getHouseholdCashSettlement().guaranteedIncome());
        money("50000", year.getEndingRetainedNonQualifiedAssets());
        money("0", year.getRetainedNonQualifiedAssetGrowth());
        money("50000", projection.getLastYear().getBeginningRetainedNonQualifiedAssets());
        money("2500", projection.getLastYear().getRetainedNonQualifiedAssetGrowth());
        money("100000", receipts(year).getFirst().excluded());
        money("0", receipts(year).getFirst().taxable());
        assertEquals(PersonReference.SPOUSE, receipts(year).getFirst().recipient());
        assertTrue(receipts(projection.getLastYear()).isEmpty());
        assertEquals(ArkansasValidationTax.withLifePrincipalPolicy(ArkansasValidationTax.reviewedVersions()),
                projection.getLastYear().arkansasValidationTax().orElseThrow().versions());
        projection.getYears().forEach(ArkansasProjectionIntegrationTest::cashBalances);

        var comparison = fixedTaxFixture();
        // A funded checking account holds taxable inputs constant while the zero-dollar death benefit
        // exercises the same admission and receipt evidence.
        comparison.getAccountPortfolio().addAccount(new CheckingAccount("Synthetic checking",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE, b("50000")));
        comparison.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "0", null, null)));
        var zero = run(comparison).getFirstYear();
        money("97168", year.getAdjustedGrossIncome());
        money(year.getAdjustedGrossIncome().toPlainString(), zero.getAdjustedGrossIncome());
        money(year.getTaxableSocialSecurity().toPlainString(), zero.getTaxableSocialSecurity());
        money(year.getFederalIncomeTax().toPlainString(), zero.getFederalIncomeTax());
        money(year.getStateIncomeTax().toPlainString(), zero.getStateIncomeTax());
        money(year.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome().toPlainString(),
                zero.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome());

        var pdf = folder.resolve("life-summary.pdf");
        ArkansasValidationReport.captureAccepted(retainedRoundTrip(projection), true).writePdf(pdf);
        try (var document = Loader.loadPDF(pdf.toFile())) {
            var content = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(content.contains("Life insurance death benefit (2027), recipient SPOUSE:"), content);
            assertTrue(content.contains("gross $100000.00, taxable principal $0.00, Arkansas-excluded principal $100000.00"), content);
            assertTrue(content.contains("retained nonqualified assets"), content);
            assertTrue(content.contains("No qualifying life insurance payment in this year."), content);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void medicareSecondPassUsesOneReceiptAndNoPrematureGrowth(DeathScenario order) {
        var insured = order == DeathScenario.PRIMARY_DIES ? PersonReference.PRIMARY : PersonReference.SPOUSE;
        var plan = fixture(START, 2, order, 2026, ".05");
        plan.getHousehold().members().forEach(person -> person.setBirthDate(LocalDate.of(1958, 6, 1)));
        plan.getAccountPortfolio().getAccounts().forEach(account -> account.setCurrentBalance(ZERO));
        plan.getHousehold().addExpense(new Expense("Household outlays", b("40000")));
        plan.setConditionalBenefits(List.of(policy(insured, "100000", START, START)));
        var result = run(plan);
        var y = result.getFirstYear();
        money("100000", y.getGuaranteedIncome());
        assertEquals(1, receipts(y).size());
        assertEquals(1, y.getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertTrue(y.getMedicarePremiumCalculation().totalAnnualMedicarePremium().signum() > 0);
        var cash = y.getHouseholdCashSettlement();
        money("0", cash.spendingWithdrawal());
        money("0", cash.taxFundingWithdrawal());
        money("0", cash.incomeTaxes());
        money(b("60000").subtract(cash.medicarePremiums()).toPlainString(), y.getEndingInvestableAssets());
        money(y.getEndingInvestableAssets().toPlainString(), y.getAfterTaxEstateValue());
        money("0", y.getInvestmentGrowth());
        assertTrue(receipts(result.getLastYear()).isEmpty());
        cashBalances(y);
    }

    @Test
    void targetedRothRepricesCashAndConversionAndKeepsBothProofVersions() {
        var plan = fixture(START, 1, DeathScenario.PRIMARY_DIES, 2026, "0");
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(ZERO);
        plan.getHousehold().addExpense(new Expense("Household outlays", b("40000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Survivor Roth",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE, ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_12_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        var without = run(plan).getFirstYear();
        plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "100000", null, null)));
        var with = run(plan).getFirstYear();

        // MFJ 12% ceiling $100,800 plus the $32,200 standard deduction. The benefit funds all taxes
        // and spending, so the entire combined-income allowance goes to the executed Roth conversion.
        money("133000", with.getRothConversion());
        money("100800", with.getFederalTaxableIncome());
        money("11600", with.getFederalIncomeTax());
        // $122,060 taxable net at 3.7%, minus $79.90 and two $29 credits.
        money("4378.32", with.getStateIncomeTax());
        money("0", with.getTaxFundingWithdrawal());
        money("0", with.getHouseholdCashSettlement().spendingWithdrawal());
        assertTrue(without.getTaxFundingWithdrawal().signum() > 0);
        money("40000", without.getHouseholdCashSettlement().spendingWithdrawal());
        assertTrue(without.getRothConversion().compareTo(with.getRothConversion()) < 0);
        var proof = with.arkansasValidationTax().orElseThrow();
        assertEquals(512, proof.fundingMetrics().candidateBudget());
        assertEquals("AR-FUNDING-P3D2E-1", proof.versions().get("arkansas-tax-funding"));
        assertEquals("AR-TARGET-P3D2G-1", proof.versions().get("arkansas-roth-target"));
        assertTrue(proof.targetMetrics().orElseThrow().innerEvaluations() <= 4096);
        System.out.println("Arkansas life principal targeted proof " + proof.targetMetrics().orElseThrow());
        cashBalances(with);

        var published = new AtomicInteger();
        var stack = StackWalker.getInstance();
        assertThrows(AnalysisCancelledException.class, () -> ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, () -> stack.walk(frames -> frames.anyMatch(frame ->
                        frame.getClassName().equals(com.daviddunn.retirementplanner.domain.tax.ArkansasRothFunding.class.getName()))),
                        published::set));
        assertEquals(0, published.get());
    }

    @Test
    void inheritedSourceProofUsesTheNewPostSpendingCapacity() {
        var date = START.plusYears(2);
        var plan = fixture(date, 1, DeathScenario.SPOUSE_DIES, 2028, "0");
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(ZERO);
        plan.getAccountPortfolio().addAccount(new RothIRA("Survivor Roth", AccountOwnership.PRIMARY, ZERO));
        var inherited = new InheritedTraditionalIRA("Synthetic inherited IRA", AccountOwnership.PRIMARY, b("1000"),
                new InheritedAccountInformation(LocalDate.of(1962, 1, 1), START, BeneficiaryRelationship.CHILD));
        plan.getAccountPortfolio().addAccount(inherited);
        var pension = new Pension("Synthetic employer pension", AccountOwnership.PRIMARY, date, null, b("7000"), ZERO);
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        plan.getHousehold().addExpense(new Expense("Household outlays", b("90000")));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2028, ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        plan.setConditionalBenefits(List.of(policy(PersonReference.SPOUSE, "1000", null, null)));

        var y = run(plan).getFirstYear();
        // $211,400 bracket ceiling plus $32,200 deduction, less $84,000 pension and $5,000
        // spending withdrawal, leaves $154,600 for combined taxable funding and conversion.
        // At the ceiling federal tax is $35,932. Arkansas has $232,660 taxable net and two $29 credits.
        money("5000", y.getHouseholdCashSettlement().spendingWithdrawal());
        money("211400", y.getFederalTaxableIncome());
        money("35932", y.getFederalIncomeTax());
        money("8470.52", y.getStateIncomeTax());
        money("44402.52", y.getTaxFundingWithdrawal());
        money("110197.48", y.getRothConversion());
        var tax = y.arkansasValidationTax().orElseThrow();
        money("44402.52", tax.taxableFundingIncome());
        var metrics = tax.targetMetrics().orElseThrow();
        assertTrue(metrics.arkansasProofCalls() > 0 && metrics.innerEvaluations() <= 4096, metrics::toString);
        money("1000", y.getEndingBalance(inherited));
        var reference = IncomeSourceAttribution.AccountReference.capture(inherited);
        assertFalse(y.getIncomeSources().stream().anyMatch(s -> s.account().filter(reference::equals).isPresent()));
        cashBalances(y);
        System.out.println("Arkansas life principal inherited-prefix proof " + metrics);
    }

    @Test
    void lifeProceedsDoNotReduceTheStatutoryRmdOrJoinTheConversion() {
        var plan = fixture(START, 1, DeathScenario.PRIMARY_DIES, 2026, "0");
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1952, 6, 15));
        var survivorIra = plan.getAccountPortfolio().getAccounts().getLast();
        survivorIra.setCurrentBalance(b("1530000"));
        survivorIra.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                2026, b("1530000"), ZERO));
        plan.getAccountPortfolio().addAccount(new RothIRA("Survivor Roth", AccountOwnership.SPOUSE, ZERO));
        plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "100000", null, null)));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, b("10000"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var y = run(plan).getFirstYear();
        // Age 74: 1,530,000 / 25.5 = 60,000. Entire annual requirement occurs inside the run.
        money("60000", y.getRequiredMinimumDistribution());
        money("60000", y.getRmdDistributedInProjection());
        money("0", y.getRmdDistributedBeforeProjection());
        money("10000", y.getRothConversion());
        money("70000", y.getAdjustedGrossIncome());
        money("0", y.getTaxFundingWithdrawal());
        money("1460000", y.getEndingBalance(survivorIra));
        money("100000", receipts(y).getFirst().excluded());
        cashBalances(y);
    }

    @ParameterizedTest
    @CsvSource({
            "2026,2026-01-01,,,2026", "2027,2026-01-01,,,2027", "2028,2026-01-01,,,0",
            "2026,2026-07-01,,,0", "2026,2026-01-01,2026-01-01,,2026",
            "2026,2026-01-01,,2026-01-01,2026", "2026,2026-01-01,2026-01-02,,0",
            "2027,2026-01-01,,2026-12-31,0", "0,2026-01-01,,,0"
    })
    void deterministicDeathAndCoverageBoundaries(int death, LocalDate start, LocalDate coverageFrom,
            LocalDate coverageTo, int paid) {
        var plan = fixture(start, 2, death == 0 ? DeathScenario.BOTH_SURVIVE : DeathScenario.PRIMARY_DIES, death, "0");
        plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "42000", coverageFrom, coverageTo)));
        var projection = run(plan);
        var receipts = projection.getYears().stream().flatMap(y -> receipts(y).stream()).toList();
        assertEquals(paid == 0 ? List.of() : List.of(paid), receipts.stream().map(ConditionalBenefitReceipt::year).toList());
        assertTrue(projection.getYears().stream().allMatch(y -> y.arkansasValidationTax().orElseThrow().versions()
                .containsKey(ArkansasValidationTax.LIFE_PRINCIPAL_POLICY_KEY)));
        if (death > 2026) money("0", projection.getFirstYear().getEndingRetainedNonQualifiedAssets());
    }

    @Test
    void noReceiptForBeneficiaryWhoDiesFirstOrInSameYear() {
        var plan = fixture(START, 1, DeathScenario.BOTH_SURVIVE, 0, "0");
        var definition = policy(PersonReference.PRIMARY, "100000", null, null);
        for (int spouseDeath : List.of(2025, 2026)) {
            var view = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), plan.getPlanningAssumptions()
                    .getDeathScenarioAssumptions(), Optional.of(new HouseholdLifetimeScenario(
                            Optional.of(Year.of(2026)), Optional.of(Year.of(spouseDeath)))));
            var result = new ConditionalBenefitEvaluator().evaluate(List.of(definition),
                    BenefitEvaluationContext.capture(plan.getHousehold(), view, START, 2026));
            assertTrue(result.receipts().isEmpty());
        }
    }

    @Test
    void independentPremiumEndsAndDistinctPolicyIdsCanPayTogether() {
        var plan = fixture(START, 2, DeathScenario.PRIMARY_DIES, 2027, "0");
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Opening premium cash",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, b("1200")));
        plan.getHousehold().addExpense(new Expense("Premium", b("1200"), GrowthCategory.NONE, START, null,
                ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM, null,
                new ContractualPremiumPolicy(ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,
                        ContractualPremiumPolicy.Insured.PRIMARY)));
        plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "50000", null, null),
                policy(PersonReference.PRIMARY, "50000", null, null)));
        var projection = run(plan);
        var first = projection.getFirstYear();
        var second = projection.getLastYear();
        money("1200", first.getAnnualExpenses());
        money("0", first.getAdjustedGrossIncome());
        money("0", first.getEndingRetainedNonQualifiedAssets());
        assertTrue(receipts(first).isEmpty());
        money("0", second.getAnnualExpenses());
        assertEquals(2, receipts(second).size());
        money("100000", second.getEndingRetainedNonQualifiedAssets());
        money("100000", second.getAfterTaxEstateValue().subtract(first.getAfterTaxEstateValue()));
        assertTrue(plan.getNonInvestableAssets().isEmpty());
    }

    @Test
    void refusesUnsupportedDefinitionsBeforeAnyEventAndPreservesAdvancedMode() {
        var plan = fixture(START, 1, DeathScenario.BOTH_SURVIVE, 0, "0");
        var generic = new ConditionalBenefitDefinition(UUID.randomUUID(), "Future taxable", Category.CONDITIONAL_INCOME,
                new Trigger(TriggerType.FIXED_DATE, START.plusYears(30), null, null), PersonReference.PRIMARY,
                PaymentPattern.MONTHLY_RECURRING, b("100"), null, null, null, null, ZERO,
                EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR, TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION,
                ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH);
        plan.setConditionalBenefits(List.of(generic));
        assertEquals(com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AR,
                assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class, () -> run(plan))
                        .unsupportedState().orElseThrow());
        var taxableDeathPayment = new ConditionalBenefitDefinition(UUID.randomUUID(), "Insurer interest", Category.DEATH_BENEFIT,
                new Trigger(TriggerType.DEATH, null, PersonReference.PRIMARY, null), PersonReference.SPOUSE,
                PaymentPattern.ONE_TIME_LUMP_SUM, b("100"), null, null, null, null, ZERO,
                EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR, TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION,
                ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH);
        plan.setConditionalBenefits(List.of(taxableDeathPayment));
        assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class, () -> run(plan));
        var annuity = com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest
                .annuity(PersonReference.PRIMARY, START, START.plusYears(1), "1200", "720", "10000");
        plan.setConditionalBenefits(List.of(annuity));
        assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class, () -> run(plan));
        var valid = policy(PersonReference.PRIMARY, "50000", null, null);
        plan.setConditionalBenefits(List.of(valid));
        assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                () -> ConditionalBenefitAdmission.requireAnalyzerSupported(plan));
        plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> run(plan));
        assertEquals(ArkansasTaxIssue.Kind.NIIT, failure.issue().kind());
        assertEquals(FinancialCalculationMode.ADVANCED, plan.getFinancialCalculationMode());
        assertThrows(IllegalArgumentException.class, () -> plan.setConditionalBenefits(List.of(valid, valid)));
        var single = selectArkansas(single(START.minusYears(61), START, 1, TAX_DEFERRED_FIRST, "0"));
        assertThrows(IllegalArgumentException.class, () -> single.setConditionalBenefits(List.of(valid)));
    }

    static RetirementPlan fixedTaxFixture() {
        var plan = fixture(START.plusYears(1), 2, DeathScenario.PRIMARY_DIES, 2027, ".05");
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(ZERO);
        var original = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(original.getEconomicAssumptions(),
                new TaxAssumptions(b(".015"), b(".015"), ZERO, ZERO, FilingStatus.MARRIED_FILING_JOINTLY, b(".25")),
                original.getWithdrawalAssumptions(), original.getDeathScenarioAssumptions(), 2,
                original.getProjectionStartDate(), original.getStateTaxAssumptions()));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.getHousehold().addExpense(new Expense("Household outlays", b("40000"), GrowthCategory.GENERAL,
                original.getProjectionStartDate(), original.getProjectionStartDate(), ExpenseType.ONE_TIME));
        plan.getAccountPortfolio().addAccount(new RothIRA("Survivor Roth",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE, ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, b("97168"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        return plan;
    }

    public static RetirementPlan fixture(LocalDate start, int years, DeathScenario death, int deathYear, String growth) {
        var plan = selectArkansas(couple(start, years, death));
        plan.getHousehold().members().forEach(person -> person.setBirthDate(LocalDate.of(1964, 6, 15)));
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(b(growth), ZERO),
                old.getTaxAssumptions(), old.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(death, deathYear == 0 ? null : deathYear), years, start, old.getStateTaxAssumptions()));
        return plan;
    }

    public static ConditionalBenefitDefinition policy(PersonReference insured, String amount, LocalDate from, LocalDate through) {
        var recipient = insured == PersonReference.PRIMARY ? PersonReference.SPOUSE : PersonReference.PRIMARY;
        return new ConditionalBenefitDefinition(UUID.randomUUID(), "Synthetic life policy", Category.DEATH_BENEFIT,
                new Trigger(TriggerType.DEATH, null, insured, null), recipient, PaymentPattern.ONE_TIME_LUMP_SUM,
                b(amount), null, null, from, through, ZERO, EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR,
                TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED,
                ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH, LifeInsuranceQualification.PERSONAL_LIFE_PRINCIPAL_ONLY_V1);
    }

    public static Projection run(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class, ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), count -> { })).projection();
    }

    private static List<ConditionalBenefitReceipt> receipts(ProjectionYear year) {
        return year.getIncomeSources().stream().flatMap(source -> source.conditionalBenefit().stream()).toList();
    }
}
