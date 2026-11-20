package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.couple;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.AGE_60;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.DEFERRED_FIRST;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.ira;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.ordinary;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.portfolio;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasSourceReachabilityTest {
    private static final LocalDate START = LocalDate.of(2028, 1, 1);
    private static final BigDecimal CENT = money("0.01");

    @BeforeAll static void loadRules() throws Exception { ArkansasAccountFundingTest.loadRules(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void productionProofCertifiesTheSameOptimumWithAnUnreachableInheritedAccount(boolean addInherited) {
        var plan = example(addInherited);
        var accounts = plan.getAccountPortfolio().getAccounts();
        var inherited = addInherited ? assertInstanceOf(InheritedTraditionalIRA.class, accounts.getLast()) : null;
        var completed = assertInstanceOf(ProjectionExecutionResult.Completed.class,
                ProjectionEngine.forArkansasProduction().projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), ignored -> {}));
        var year = completed.projection().getLastYear();
        var tax = year.arkansasValidationTax().orElseThrow();
        var metrics = tax.targetMetrics().orElseThrow();

        // Independent oracle, with no Social Security or premium and $6,000 of pretax spending withdrawals.
        var combined = money("211400").add(money("32200")).subtract(money("90000"));
        var federal = money("24800").multiply(money("0.10")).add(money("76000").multiply(money("0.12")))
                .add(money("110600").multiply(money("0.22")));
        var state = highScheduleTax(combined);
        var funding = federal.add(state);
        var conversion = combined.subtract(funding);
        assertAmount("153600", combined);
        assertAmount("35932.00", federal);
        assertAmount("8470.52", state);
        assertAmount("44402.52", funding);
        assertAmount("109197.48", conversion);
        assertAmount("0.0078", federal.subtract(CENT.multiply(money("0.22"))).add(highScheduleTax(combined.subtract(CENT)))
                .subtract(funding.subtract(CENT)));
        // For every higher cent conversion, Y above the ceiling violates the federal target.
        // For Y at or below the ceiling, taking k combined cents away reduces cash by k cents
        // and federal tax by .22k cents. Arkansas rounded tax falls by at most ceil(.037k)
        // cents inside the high schedule. Even using .037k + 1 for that ceiling, the extra
        // conversion cent leaves a positive deficit for k > 0, and leaves one cent for k = 0.
        assertTrue(money("90000").add(conversion).subtract(money("10940")).compareTo(money("97601")) > 0);
        assertTrue(money("90000").add(conversion).subtract(money("32200")).compareTo(money("100800")) > 0);
        assertAmount("0.743", BigDecimal.ONE.subtract(money("0.22")).subtract(money("0.037")));

        assertEquals(2028, year.getCalendarYear());
        assertAmount("211400.00", year.getFederalTaxableIncome());
        equalAmount(federal, year.getFederalIncomeTax());
        equalAmount(state, year.getStateIncomeTax());
        equalAmount(funding, year.getTaxFundingWithdrawal());
        equalAmount(funding, tax.taxableFundingIncome());
        equalAmount(conversion, year.getRothConversion());
        assertAmount("6000.00", tax.provenance().election().retirementExclusion());
        assertTrue(metrics.arkansasProofCalls() > 0, metrics::toString);
        assertTrue(metrics.exactTrials() <= 3 && metrics.innerEvaluations() <= 75, metrics::toString);
        assertEquals(512, tax.fundingMetrics().candidateBudget());

        var own = IncomeSourceAttribution.AccountReference.capture(accounts.getFirst());
        var funded = year.getIncomeSources().stream().filter(s -> s.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
        var converted = year.getIncomeSources().stream().filter(s -> s.purpose() == IncomeSourceAttribution.Purpose.CONVERSION).toList();
        assertEquals(1, funded.size());
        assertEquals(1, converted.size());
        for (var source : java.util.List.of(funded.getFirst(), converted.getFirst())) {
            assertEquals(own, source.account().orElseThrow());
            assertEquals(PRIMARY, source.owner().orElseThrow());
        }
        equalAmount(funding, funded.getFirst().grossAmount());
        equalAmount(conversion, converted.getFirst().grossAmount());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(accounts.get(2)), converted.getFirst().destination().orElseThrow());
        if (inherited != null) {
            assertEquals(SPOUSE, inherited.getOwnership());
            assertEquals(BeneficiaryRelationship.CHILD, inherited.getInheritedAccountInformation().getBeneficiaryRelationship());
            assertAmount("1000", inherited.getCurrentBalance());
            assertAmount("1000", year.getEndingBalance(inherited));
            var inheritedRef = IncomeSourceAttribution.AccountReference.capture(inherited);
            assertFalse(year.getIncomeSources().stream().anyMatch(s -> s.account().filter(inheritedRef::equals).isPresent()));
        }
        System.out.printf("Arkansas source reachability inherited=%s %s%n", addInherited, metrics);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "16499.99,false", "16499.999,false", "16500,false", "16500.001,true", "16500.01,true"})
    void onlyAnExactPrefixStrictlyAboveTheFederalCeilingAdmitsTheStateBound(String prefix, boolean admitted) {
        var start = portfolio(ira("Own IRA before inherited", prefix), inherited(PRIMARY, "1000"),
                new RothIRA("Own destination", PRIMARY, ZERO));
        var facts = singleFacts();
        assertEquals(admitted, invokesStateBound(facts, start, DEFERRED_FIRST, "50400"));
        assertEquals(money(prefix).compareTo(money("16499.99")) > 0,
                invokesStateBound(facts, start, DEFERRED_FIRST, "50399.99"));
        assertFalse(invokesStateBound(facts, start, DEFERRED_FIRST, "50400.01"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cashAndBrokerageDoNotCountAsPretaxPrefixInEitherWithdrawalOrder(boolean taxableFirst) {
        var order = taxableFirst ? TAXABLE_FIRST : DEFERRED_FIRST;
        for (String prefix : List.of("10000", "30000")) {
            var holdings = portfolio(new CheckingAccount("Checking", PRIMARY, money("9000")),
                    new BrokerageAccount("Brokerage", PRIMARY, money("10000")), ira("Own IRA", prefix),
                    inherited(PRIMARY, "1000"), inherited(SPOUSE, "1500"), new RothIRA("Own Roth", PRIMARY, ZERO));
            var retained = new ProjectedPortfolio(holdings.getAccountBalances(), money("10000"));
            assertEquals(prefix.equals("30000"), invokesStateBound(singleFacts(), retained, order, "50400"));
        }
    }

    @Test
    void reachableInheritedDebitsRemainUnavailableInFixedAndTargetedEvaluation() {
        var facts = singleFacts();
        var inherited = inherited(PRIMARY, "20000");
        var start = portfolio(ira("Small ordinary prefix", "1000"), inherited, new RothIRA("Own Roth", PRIMARY, ZERO));
        var solver = new ArkansasRothFunding();
        for (var fixed : List.of(ZERO, money("500"))) {
            var result = solver.fixed(facts, start, DEFERRED_FIRST, fixed).funding();
            assertEquals(ArkansasMinimumFundingSolver.Status.UNSUPPORTED, result.status());
            assertEquals(ArkansasTaxIssue.Kind.RETIREMENT_SOURCE, result.issue().orElseThrow().kind());
            assertTrue(result.candidate().isEmpty());
        }
        var target = solver.targeted(facts, start, DEFERRED_FIRST, money("50400"));
        assertEquals(ArkansasRothFunding.TargetStatus.UNSUPPORTED, target.status());
        assertEquals(ArkansasTaxIssue.Kind.RETIREMENT_SOURCE, target.issue().orElseThrow().kind());
        assertEquals(0, target.metrics().arkansasProofCalls());
        assertAmount("20000", start.getBalance(inherited));
    }

    @Test
    void employerConversionKeepsTheConservativeFallbackEvenWithAnUnreachableInheritedAccount() {
        var start = portfolio(ira("Own IRA", "100000"), inherited(PRIMARY, "1000"),
                new Traditional401K("Unreviewed conversion source", PRIMARY, money("10000")), new RothIRA("Own Roth", PRIMARY, ZERO));
        assertFalse(invokesStateBound(singleFacts(), start, DEFERRED_FIRST, "50400"));
    }

    @Test
    void conversionAfterTheInheritedFundingBoundaryStillContributesItsOwnersRetirementReceipts() {
        // The spouse's IRA funds first. Only the primary, whose IRA is later, has a destination.
        var control = ownIraAfterBoundary(false);
        var proved = ownIraAfterBoundary(true);
        assertTrue(proved.arkansasValidationTax().orElseThrow().targetMetrics().orElseThrow().arkansasProofCalls() > 0);
        equalAmount(control.getRothConversion(), proved.getRothConversion());
        equalAmount(control.getTaxFundingWithdrawal(), proved.getTaxFundingWithdrawal());
        equalAmount(control.getStateIncomeTax(), proved.getStateIncomeTax());
        assertAmount("12000", proved.arkansasValidationTax().orElseThrow().provenance().election().retirementExclusion());
        assertEquals(SPOUSE, proved.getIncomeSources().stream().filter(s -> s.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING)
                .findFirst().orElseThrow().owner().orElseThrow());
        assertEquals(PRIMARY, proved.getIncomeSources().stream().filter(s -> s.purpose() == IncomeSourceAttribution.Purpose.CONVERSION)
                .findFirst().orElseThrow().owner().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void survivorRebuildsTheProofWithTheActualRecipientsAndFilingStatus(DeathScenario death) {
        var start = START.minusYears(1);
        var plan = selectArkansas(couple(start, 2, death));
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(death, 2027, 67, ONE), 2, start,
                a.getStateTaxAssumptions()));
        var deceased = death == DeathScenario.PRIMARY_DIES ? PRIMARY : SPOUSE;
        var survivor = deceased == PRIMARY ? SPOUSE : PRIMARY;
        var pension = new Pension("Synthetic survivor pension", deceased, start, null, money("7000"), ZERO, money("7000"));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        (deceased == PRIMARY ? plan.getHousehold().getPrimaryPerson() : plan.getHousehold().getSpouse()).addIncomeSource(pension);
        plan.getAccountPortfolio().addAccount(new RothIRA("Survivor Roth", survivor, ZERO));
        var inherited = inherited(survivor, "1000");
        plan.getAccountPortfolio().addAccount(inherited);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2028, ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        var result = assertInstanceOf(ProjectionExecutionResult.Completed.class,
                ProjectionEngine.forArkansasProduction().projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), ignored -> {}));
        var years = result.projection().getYears();
        assertEquals(List.of(FilingStatus.MARRIED_FILING_JOINTLY, FilingStatus.SINGLE), years.stream()
                .map(y -> y.arkansasValidationTax().orElseThrow().filingStatus()).toList());
        var last = years.getLast();
        var pensionTrace = last.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION).findFirst().orElseThrow();
        assertEquals(deceased, pensionTrace.pension().orElseThrow().originalOwner());
        assertEquals(survivor, pensionTrace.owner().orElseThrow());
        assertAmount("6000", last.arkansasValidationTax().orElseThrow().provenance().election().retirementExclusion());
        assertAmount("1000", last.getEndingBalance(inherited));
        assertTrue(last.getRothConversion().signum() > 0);
    }

    @Test
    void cancellationAtReachabilityPreparationLeavesTheFrozenAssetsUntouched() {
        var start = portfolio(ira("Ordinary IRA", "90000"), inherited(PRIMARY, "1000"), new RothIRA("Own Roth", PRIMARY, ZERO));
        var walker = StackWalker.getInstance();
        var solver = new ArkansasRothFunding();
        long began = System.nanoTime();
        assertThrows(AnalysisCancelledException.class, () -> solver.targeted(singleFacts(), start, DEFERRED_FIRST, money("50400"),
                () -> walker.walk(frames -> frames.anyMatch(f -> f.getClassName().equals(ArkansasTargetBounds.class.getName())
                        && f.getMethodName().equals("prepareReachableStateFloor")))));
        System.out.printf("Arkansas source reachability cancellation us=%d%n", (System.nanoTime() - began) / 1000);
        assertAmount("91000", start.getTotalBalance());
        assertEquals(ArkansasRothFunding.TargetStatus.CERTIFIED,
                solver.targeted(singleFacts(), start, DEFERRED_FIRST, money("50400")).status());
    }

    private static ProjectionYear ownIraAfterBoundary(boolean addInherited) {
        var plan = example(false);
        var primary = plan.getAccountPortfolio().getAccounts().getFirst();
        plan.getAccountPortfolio().removeAccount(primary);
        if (addInherited) plan.getAccountPortfolio().addAccount(inherited(PRIMARY, "1000"));
        plan.getAccountPortfolio().addAccount(primary);
        return assertInstanceOf(ProjectionExecutionResult.Completed.class, ProjectionEngine.forArkansasProduction()
                .projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), ignored -> {})).projection().getLastYear();
    }

    private static ArkansasAnnualFunding.Facts singleFacts() {
        return ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
    }

    private static boolean invokesStateBound(ArkansasAnnualFunding.Facts facts, ProjectedPortfolio portfolio,
                                             WithdrawalStrategy order, String target) {
        var converter = new ProjectedPortfolioRothConverter();
        var full = converter.convertHousehold(portfolio, converter.getMaximumConvertibleAmount(portfolio), java.util.Set.of(PRIMARY), 2026);
        var bounds = new ArkansasTargetBounds(facts, portfolio, full, order);
        var ceiling = bounds.federalUpper(money(target));
        bounds.prepareReachableStateFloor(portfolio, order, ceiling, AnalysisCancellationToken.none());
        bounds.excludes(new ArkansasTargetBounds.Interval(ceiling, ceiling), ceiling, money(target));
        return bounds.stateProbes() > 0;
    }

    private static BigDecimal highScheduleTax(BigDecimal combined) {
        return money("90000").add(combined).subtract(money("6000")).subtract(money("4940"))
                .multiply(money("0.037")).subtract(money("79.90")).subtract(money("58"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    static RetirementPlan example(boolean addInherited) {
        var plan = selectArkansas(couple(START, 1, DeathScenario.BOTH_SURVIVE));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.getAccountPortfolio().addAccount(new RothIRA("Synthetic Roth", PRIMARY, ZERO));
        if (addInherited) plan.getAccountPortfolio().addAccount(inherited(SPOUSE, "1000"));
        var pension = new Pension("Synthetic employer pension", PRIMARY, START, null, money("7000"), ZERO);
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        plan.getHousehold().addExpense(new Expense("Synthetic spending", money("90000"), GrowthCategory.NONE,
                START, null, ExpenseType.RECURRING));
        plan.setRothConversionRequest(new RothConversionRequest(true, START.getYear(), ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        return plan;
    }

    private static InheritedTraditionalIRA inherited(AccountOwnership owner, String amount) {
        return new InheritedTraditionalIRA("Synthetic inherited", owner, money(amount),
                new InheritedAccountInformation(LocalDate.of(1962, 1, 1), LocalDate.of(2026, 1, 1), BeneficiaryRelationship.CHILD));
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }

    private static void assertAmount(String expected, BigDecimal actual) { equalAmount(money(expected), actual); }

    private static void equalAmount(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "Expected " + expected + ", found " + actual);
    }
}
