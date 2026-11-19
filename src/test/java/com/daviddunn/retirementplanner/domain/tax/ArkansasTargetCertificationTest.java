package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator.PensionIncomeResult;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.rmd.AccountRmd;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding.Cash;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding.Facts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationRequest;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.*;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasRothFunding.TargetStatus.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasTargetCertificationTest {
    private static final BigDecimal CENT = b(".01");
    private static final LocalDate SENIOR = LocalDate.of(1950, 1, 1);
    private static final LocalDate EARLY = LocalDate.of(1972, 1, 1);
    private final ArkansasRothFunding solver = new ArkansasRothFunding();

    @BeforeAll static void initialize() throws Exception { loadRules(); }

    @ParameterizedTest
    @ValueSource(strings = {".25", ".09"})
    void realMfjRmdPoolsCreateDisconnectedFeasibleConversionRegions(String limit) {
        var primary = ira("Primary small IRA", b("5999.93").add(b(limit)).toPlainString());
        var spouse = new TraditionalIRA("Spouse first funding", SPOUSE, b("6060.05"));
        var roth = new RothIRA("Primary Roth", PRIMARY, ZERO);
        var starting = portfolio(spouse, primary, roth);
        var afterRmd = ALLOCATOR.applyAccountRmds(starting, List.of(
                new AccountRmd(primary, b("5999.93")), new AccountRmd(spouse, b("5900.05"))));
        var receipts = List.of(rmd(primary, "5999.93"), rmd(spouse, "5900.05"));
        var facts = withIncome(joint(SENIOR, SENIOR), FilingStatus.MARRIED_FILING_JOINTLY,
                new TaxIncome(ZERO, b("20000"), b("11899.98"), ZERO, b("99640.97"), receipts),
                new Cash(b("20000"), b("11899.98"), ZERO, b("17904.68"), ZERO), ArkansasTaxEvidence.NONE);
        var target = b("96440.95");
        var feasible = exhaustive(facts, afterRmd, DEFERRED_FIRST, target, b(limit));
        assertTrue(feasible.contains(b(".02")));
        assertFalse(feasible.contains(b(".03")));
        assertFalse(feasible.contains(b(".09")));
        if (limit.equals(".25")) {
            assertTrue(feasible.contains(b(".10")));
            assertTrue(feasible.contains(b(".25")));
        }
        var result = measured("disconnected MFJ " + limit, facts, afterRmd, DEFERRED_FIRST, target);
        var trial = certified(result);
        money(limit.equals(".25") ? ".25" : ".02", trial.conversion().getTotalConversion());
        assertEquals(feasible.getLast(), trial.requested());
        assertEquals(SPOUSE, calculated(trial.funding()).allocation().getIncomeSources().getFirst().owner().orElseThrow());
        assertEquals(PRIMARY, trial.conversion().getAllocations().getFirst().ownership());
        assertFundsConserved(trial, afterRmd);
    }

    @Test
    void shortOraclesCheckBothSidesOfEveryArkansasAdjustmentAndTheExit() {
        var ira = ira("Early own IRA", ".03");
        var roth = new RothIRA("Same-owner Roth", PRIMARY, ZERO);
        var cash = new CheckingAccount("Checking", PRIMARY, b("30000"));
        var starting = portfolio(ira, roth, cash);
        for (int band = 0; band <= 29; band++) {
            var boundary = BigDecimal.valueOf(94701L + 100L * band);
            var interest = boundary.add(b("2469.99"));
            var facts = ordinary(EARLY, WithdrawalBreakdown.zero(), "0", interest.toPlainString(), "0");
            var target = interest.add(b(".02")).subtract(b("16100"));
            var feasible = exhaustive(facts, starting, TAXABLE_FIRST, target, b(".03"));
            assertEquals(b(".02"), feasible.getLast());
            var trial = certified(solver.targeted(facts, starting, TAXABLE_FIRST, target));
            assertEquals(feasible.getLast(), trial.requested(), "Band " + band);
            var net = boundary.add(CENT);
            var adjustment = BigDecimal.valueOf(Math.max(290 - 10L * band, 0));
            var expectedTax = net.multiply(b(".037")).subtract(b("79.90")).subtract(adjustment)
                    .subtract(b("29")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(expectedTax, calculated(trial.funding()).stateTax().incomeTax());
        }
    }

    @Test
    void zeroCapacityAndMissingDestinationLeaveTheTargetUnfilledWithoutInventingAConversion() {
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var roth = new RothIRA("Roth", PRIMARY, ZERO);
        var cash = new CheckingAccount("Cash", PRIMARY, b("10000"));
        var withoutBalance = solver.targeted(facts, portfolio(ira("Zero", "0"), roth, cash), TAXABLE_FIRST, b("50400"));
        var withoutDestination = solver.targeted(facts, portfolio(ira("IRA", "35000"), cash), TAXABLE_FIRST, b("50400"));
        for (var result : List.of(withoutBalance, withoutDestination)) {
            money("0", certified(result).conversion().getTotalConversion());
            money("16500", result.unusedTarget().orElseThrow());
        }
        money("10", solver.fixed(facts, portfolio(ira("IRA", "35000"), cash), TAXABLE_FIRST, b("10")).shortfall());
        money("0", solver.fixed(facts, portfolio(cash), TAXABLE_FIRST, ZERO).conversion().getTotalConversion());
    }

    @Test
    void terminalFractionAndRothFundingWorkAfterAllDeferredSourcesAreConverted() {
        var ira = ira("Small IRA", ".025");
        var roth = new RothIRA("Roth with spendable cash", PRIMARY, b("10000"));
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var start = portfolio(ira, roth);
        var result = solver.targeted(facts, start, DEFERRED_FIRST, b("50400"));
        var trial = certified(result);
        money(".025", trial.requested());
        money(".025", exhaustive(facts, start, DEFERRED_FIRST, b("50400"), b(".025")).getLast());
        assertTrue(calculated(trial.funding()).allocation().getRothWithdrawal().signum() > 0);
        money("0", calculated(trial.funding()).portfolio().getBalance(ira));
        assertFundsConserved(trial, start);
    }

    @Test
    void taxableFirstAndDeferredFirstUseDifferentActualCashSourcesWithRetainedAssetsFirst() {
        var ira = ira("IRA", "20000");
        var broker = new BrokerageAccount("Principal only", PRIMARY, b("4000"));
        var roth = new RothIRA("Roth", PRIMARY, ZERO);
        var start = portfolio(ira, broker, roth).withAdditionalRetainedNonQualifiedAssets(b("500"));
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var taxable = certified(solver.targeted(facts, start, TAXABLE_FIRST, b("50400")));
        var deferred = certified(solver.targeted(facts, start, DEFERRED_FIRST, b("50400")));
        assertTrue(taxable.requested().compareTo(deferred.requested()) > 0);
        var debit = calculated(taxable.funding()).allocation().getIncomeSources();
        assertTrue(debit.getFirst().account().isEmpty());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(broker), debit.get(1).account().orElseThrow());
        assertTrue(debit.get(1).modeledTaxableAmount().isEmpty());
        assertTrue(calculated(taxable.funding()).taxableFundingIncome().signum() > 0);
        assertFundsConserved(taxable, start);
        assertFundsConserved(deferred, start);
    }

    @Test
    void converterAndFundingFollowTheirDifferentOwnerAndPersistedAccountOrders() {
        var first = ira("First primary IRA", "300");
        var second = ira("Second primary IRA", "400");
        var spouse = new TraditionalIRA("Spouse first in funding", SPOUSE, b("60000"));
        var pRoth = new RothIRA("Primary Roth", PRIMARY, ZERO);
        var sRoth = new RothIRA("Spouse Roth", SPOUSE, ZERO);
        var facts = withIncome(joint(AGE_60, EARLY), FilingStatus.MARRIED_FILING_JOINTLY,
                new TaxIncome(ZERO, ZERO, ZERO, ZERO, b("80000")), noCash(), ArkansasTaxEvidence.NONE);
        var noPenalty = Map.of(IncomeSourceAttribution.AccountReference.capture(spouse),
                ArkansasTaxEvidence.Form5329Status.NO_APPLICABLE_ADDITIONAL_TAX);
        var confirmed = new Facts(facts.seed(), facts.evidence(), Optional.empty(), noPenalty, facts.cash());
        var start = portfolio(spouse, first, second, sRoth, pRoth);
        var trial = certified(measured("two owners", confirmed, start, DEFERRED_FIRST, b("60000")));
        assertEquals(List.of(first, second, spouse), trial.conversion().getAllocations().stream()
                .map(transfer -> transfer.sourceAccount()).toList());
        assertSame(pRoth, trial.conversion().getAllocations().getFirst().destinationAccount());
        assertSame(sRoth, trial.conversion().getAllocations().getLast().destinationAccount());
        assertEquals(SPOUSE, calculated(trial.funding()).allocation().getIncomeSources().getFirst().owner().orElseThrow());
        assertFundsConserved(trial, start);
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void survivorSingleOnlyConvertsTheActiveOwnersFunds(AccountOwnership survivingOwner) {
        var deceased = survivingOwner == PRIMARY ? SPOUSE : PRIMARY;
        var pension = new Pension("Established spouse survivor pension", deceased, START.minusYears(5), null,
                b("500"), ZERO, b("250"));
        var receipt = IncomeSourceAttribution.pension(pension, survivingOwner, b("3000"), 2026);
        var context = joint(SENIOR, SENIOR).withActiveOwners(Set.of(survivingOwner)).withPensionIncome(
                Optional.of(new PensionIncomeResult(b("3000"), List.of(receipt))));
        var evidence = new ArkansasTaxEvidence(Map.of(receipt, new ArkansasTaxEvidence.ReceiptFacts(Optional.empty(),
                ArkansasTaxEvidence.SourceQualification.ORDINARY_EMPLOYMENT_PENSION,
                ArkansasTaxEvidence.Form5329Status.UNKNOWN)), Map.of(deceased, START.minusYears(1)));
        var facts = withIncome(context, FilingStatus.SINGLE,
                new TaxIncome(b("3000"), ZERO, ZERO, ZERO, b("60000"), List.of(receipt)),
                new Cash(b("3000"), ZERO, ZERO, b("3000"), ZERO), evidence);
        var survivor = new TraditionalIRA("Surviving IRA", survivingOwner, b("7000"));
        var inactive = new TraditionalIRA("Inactive", deceased, b("4000"));
        var roth = new RothIRA("Surviving Roth", survivingOwner, ZERO);
        var otherRoth = new RothIRA("Inactive Roth", deceased, ZERO);
        var cash = new CheckingAccount("Survivor cash", survivingOwner, b("12000"));
        var start = portfolio(inactive, survivor, otherRoth, roth, cash);
        var trial = certified(solver.targeted(facts, start, TAXABLE_FIRST, b("50400")));
        assertEquals(survivingOwner, trial.conversion().getAllocations().getFirst().ownership());
        money("4000", calculated(trial.funding()).portfolio().getBalance(inactive));
        money("0", calculated(trial.funding()).portfolio().getBalance(otherRoth));
        assertTrue(calculated(trial.funding()).incomeSources().contains(receipt));
    }

    @Test
    void aSpouseFundingAccountCanExhaustBeforeThePrimaryConversionAccount() {
        var primary = ira("Primary conversion IRA", "60000");
        var spouse = new TraditionalIRA("Small spouse IRA", SPOUSE, b("200"));
        var facts = withIncome(joint(AGE_60, AGE_60), FilingStatus.MARRIED_FILING_JOINTLY,
                new TaxIncome(ZERO, ZERO, ZERO, ZERO, b("80000")), noCash(), ArkansasTaxEvidence.NONE);
        var start = portfolio(spouse, primary, new RothIRA("Primary Roth", PRIMARY, ZERO));
        var trial = certified(solver.targeted(facts, start, DEFERRED_FIRST, b("60000")));
        var funded = calculated(trial.funding());
        assertEquals(List.of(SPOUSE, PRIMARY), funded.allocation().getIncomeSources().stream()
                .map(source -> source.owner().orElseThrow()).toList());
        money("200", funded.allocation().getIncomeSources().getFirst().grossAmount());
        money("0", funded.portfolio().getBalance(spouse));
        assertTrue(funded.portfolio().getBalance(primary).signum() > 0);
        assertFundsConserved(trial, start);
    }

    @Test
    void taxableSocialSecurityChangesTheExactFederalCeilingAtTheSecondTier() {
        var contributing = ira("IRA after RMD", ".08");
        var roth = new RothIRA("Roth", PRIMARY, ZERO);
        var ss = b("40000");
        var rmd = rmd(contributing, "6000");
        var facts = withIncome(single(SENIOR), FilingStatus.SINGLE,
                new TaxIncome(ZERO, ss, b("6000"), ZERO, b("7999.99"), List.of(rmd)),
                new Cash(ss, b("6000"), ZERO, b("30000"), ZERO), ArkansasTaxEvidence.NONE);
        var start = portfolio(contributing, roth);
        var target = b("2400.037");
        var trial = certified(solver.targeted(facts, start, DEFERRED_FIRST, target));
        money(".03", trial.requested());
        money("4500.017", calculated(trial.funding()).annualTaxFacts().federal().getTaxableSocialSecurity());
        money(".03", exhaustive(facts, start, DEFERRED_FIRST, target, b(".08")).getLast());
    }

    @Test
    void firstSocialSecurityThresholdIsRecomputedEvenWhenTheFederalTargetHasAZeroPlateau() {
        var ira = ira("IRA", ".03");
        var ss = b("22000");
        var facts = withIncome(single(SENIOR), FilingStatus.SINGLE,
                new TaxIncome(ZERO, ss, b("6000"), ZERO, b("7999.99"), List.of(rmd(ira, "6000"))),
                new Cash(ss, b("6000"), ZERO, b("20000"), ZERO), ArkansasTaxEvidence.NONE);
        var start = portfolio(ira, new RothIRA("Roth", PRIMARY, ZERO));
        var trial = certified(solver.targeted(facts, start, DEFERRED_FIRST, ZERO));
        money(".03", trial.requested());
        money(".01", calculated(trial.funding()).annualTaxFacts().federal().getTaxableSocialSecurity());
        money(".03", exhaustive(facts, start, DEFERRED_FIRST, ZERO, b(".03")).getLast());
    }

    @Test
    void exactNiitAndFederalTargetAreDifferentQuantities() {
        var interest = b("199999.90");
        var sources = List.of(IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.INTEREST,
                IncomeSourceAttribution.Purpose.INCOME, "Evidenced interest", interest, Optional.of(interest)).inYear(2026),
                IncomeSourceAttribution.calculationPolicy(FinancialCalculationMode.ADVANCED, 2026));
        var facts = withIncome(single(AGE_60), FilingStatus.SINGLE,
                new TaxIncome(ZERO, ZERO, ZERO, ZERO, interest, sources), noCash(), ArkansasTaxEvidence.NONE);
        var start = portfolio(ira("IRA", ".30"), new RothIRA("Roth", PRIMARY, ZERO),
                new CheckingAccount("Cash", PRIMARY, b("100000")));
        var target = b("183900.15");
        var trial = certified(solver.targeted(facts, start, TAXABLE_FIRST, target));
        money(".25", trial.requested());
        money(".01", calculated(trial.funding()).annualTaxFacts().federal().niit().orElseThrow().tax());
        money(".25", exhaustive(facts, start, TAXABLE_FIRST, target, b(".30")).getLast());
    }

    @Test
    void statesDistinguishProvedTargetMissInsolvencyAndUnsupportedEvidence() {
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var portfolio = portfolio(ira("IRA", "90000"), new RothIRA("Roth", PRIMARY, ZERO));
        assertEquals(NO_FEASIBLE_TARGET, measured("target miss after funding", facts, portfolio, DEFERRED_FIRST, b("33910")).status());
        assertEquals(INSUFFICIENT_FUNDS, solver.targeted(facts, portfolio(), DEFERRED_FIRST, b("50400")).status());
        var budget = new ArkansasRothFunding(1).targeted(facts, portfolio, DEFERRED_FIRST, b("50400"));
        assertEquals(UNSUPPORTED, budget.status());
        assertEquals(com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.Kind.TARGET_BUDGET,
                budget.issue().orElseThrow().kind());
        var lowIncome = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "18000", "0");
        var unavailable = measured("unsupported low-income", lowIncome, portfolio, DEFERRED_FIRST, b("3000"));
        assertEquals(UNSUPPORTED, unavailable.status(), unavailable.detail());
        assertEquals(com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.Kind.LOW_INCOME,
                unavailable.issue().orElseThrow().kind());
        assertTrue(unavailable.detail().contains("low-income"));
        var uncertainAge = ordinary(LocalDate.of(1966, 8, 31), WithdrawalBreakdown.zero(), "0", "60000", "0");
        assertEquals(NO_FEASIBLE_TARGET, solver.targeted(uncertainAge, portfolio, DEFERRED_FIRST, b("50400")).status());
        var availableCash = portfolio(ira("IRA", "90000"), new RothIRA("Roth", PRIMARY, ZERO),
                new CheckingAccount("Known tax cash", PRIMARY, b("20000")));
        assertEquals(UNSUPPORTED, solver.targeted(uncertainAge, availableCash, TAXABLE_FIRST, b("50400")).status());
        var knownWindow = new ArkansasTaxEvidence.ReceiptWindow(START.plusMonths(3), START.plusMonths(6));
        var knownAge = new Facts(uncertainAge.seed(), uncertainAge.evidence(), Optional.of(knownWindow),
                Map.of(), uncertainAge.cash());
        money("6500", certified(solver.targeted(knownAge, availableCash, TAXABLE_FIRST, b("50400"))).requested());
    }

    @Test
    void cancellationAtAnActualDeferredTargetIntervalPropagatesWithoutACandidate() {
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var portfolio = portfolio(ira("IRA", "90000"), new RothIRA("Roth", PRIMARY, ZERO));
        var walker = StackWalker.getInstance();
        long started = System.nanoTime();
        assertThrows(com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException.class, () ->
                solver.targeted(facts, portfolio, DEFERRED_FIRST, b("50400"), () -> walker.walk(frames ->
                        frames.anyMatch(f -> f.getClassName().equals(ArkansasRothFunding.class.getName() + "$Pass")
                                && f.getMethodName().equals("search")))));
        System.out.println("Arkansas controlled target interval cancellation us=" + (System.nanoTime() - started) / 1000);
        money("90000", portfolio.getTotalBalance());
        money("8949.09", certified(solver.targeted(facts, portfolio, DEFERRED_FIRST, b("50400"))).requested());
    }

    @Test
    void twelveAccountDeferredProofIsDeterministicWithABoundedNumberOfExactTrials() {
        var accounts = new ArrayList<Account>();
        IntStream.range(0, 11).forEach(i -> accounts.add(ira("IRA " + i, "3000")));
        accounts.add(new RothIRA("Roth destination", PRIMARY, ZERO));
        var base = portfolio(accounts.toArray(Account[]::new));
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var result = measured("twelve-account", facts, base, DEFERRED_FIRST, b("50400"));
        var trial = certified(result);
        money("8949.09", trial.conversion().getTotalConversion());
        assertEquals(3, trial.conversion().getAllocations().size());
        money("7550.91", calculated(trial.funding()).taxableFundingIncome());
        assertTrue(result.trials() < 20);
        var repeat = solver.targeted(facts, base, DEFERRED_FIRST, b("50400"));
        assertEquals(result.metrics(), repeat.metrics());
        assertEquals(calculated(trial.funding()).allocation().getIncomeSources(),
                calculated(certified(repeat).funding()).allocation().getIncomeSources());
        assertFundsConserved(trial, base);
    }

    @Test
    void medicarePassUsesFreshPostSpendingBalances() {
        var ira = ira("IRA", "160000");
        var roth = new RothIRA("Roth", PRIMARY, ZERO);
        var start = portfolio(ira, roth);
        var target = b("60000");
        var facts = ordinary(SENIOR, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var first = certified(solver.targeted(facts, start, DEFERRED_FIRST, target));
        var premium = new MedicarePremiumCalculator().calculate(calculated(first.funding()).annualTaxFacts().federal(),
                FilingStatus.SINGLE, rules, 1).totalAnnualMedicarePremium();
        var spending = ALLOCATOR.allocateAdditionalWithdrawal(start, premium, DEFERRED_FIRST);
        var adjusted = ordinary(SENIOR, spending.getWithdrawalBreakdown(), "0", "50000", premium.toPlainString());
        var second = certified(solver.targeted(adjusted, spending.getPortfolio(), DEFERRED_FIRST, target));
        assertTrue(second.requested().compareTo(first.requested()) < 0);
        money(premium.toPlainString(), ArkansasAnnualFunding.settle(calculated(second.funding())).cash().medicarePremiums());
        money("160000", start.getBalance(ira));
        assertFundsConserved(second, spending.getPortfolio());
    }

    @Test
    void openingRmdSharedPoolChangesTheSeniorElectionAtTheTarget() {
        var ira = ira("Post-opening IRA", ".05");
        var opening = IncomeSourceAttribution.withdrawal(ira, b("783.76"),
                IncomeSourceAttribution.Kind.OPENING_RMD, IncomeSourceAttribution.Purpose.INCOME).inYear(2026);
        var context = new TaxHouseholdContext(START.plusMonths(6), Map.of(PRIMARY, SENIOR),
                Set.of(PRIMARY), Set.of(PRIMARY), Optional.empty());
        var ordinary = b("2267.204").divide(b(".037"), 8, RoundingMode.HALF_UP).add(b("2470"));
        var income = new TaxIncome(ZERO, ZERO, b("783.76"), ZERO, ordinary.subtract(b("783.76")), List.of(opening));
        var facts = withIncome(context, FilingStatus.SINGLE, income, noCash(), ArkansasTaxEvidence.NONE);
        var start = portfolio(ira, new RothIRA("Roth", PRIMARY, ZERO),
                new CheckingAccount("Checking", PRIMARY, b("30000")));
        var target = ordinary.subtract(b("16100")).add(b(".05"));
        var zero = calculated(solver.fixed(facts, start, TAXABLE_FIRST, ZERO).funding());
        var trial = certified(solver.targeted(facts, start, TAXABLE_FIRST, target));
        var funded = calculated(trial.funding());
        assertTrue(zero.stateTax().limitations().getLast().contains("retirement exclusion 0"));
        assertTrue(funded.stateTax().limitations().getLast().contains("retirement exclusion 783.81"));
        money(".05", exhaustive(facts, start, TAXABLE_FIRST, target, b(".05")).getLast());
        money("0", funded.otherTaxCash());
    }

    private ArkansasRothFunding.TargetResult measured(String description, Facts facts,
            ProjectedPortfolio base, WithdrawalStrategy order, BigDecimal target) {
        long start = System.nanoTime();
        var result = solver.targeted(facts, base, order, target);
        System.out.printf("Arkansas target %s %s %.3f ms %s%n", description, result.status(),
                (System.nanoTime() - start) / 1_000_000.0, result.metrics());
        return result;
    }

    /** Independent outer oracle. Enumerates the small allowed lattice using only the converter and P2 inner authority. */
    private static List<BigDecimal> exhaustive(Facts facts, ProjectedPortfolio base, WithdrawalStrategy strategy,
                                               BigDecimal target, BigDecimal capacity) {
        assertTrue(capacity.compareTo(b(".50")) <= 0);
        var found = new ArrayList<BigDecimal>();
        var converter = new ProjectedPortfolioRothConverter();
        var funding = new ArkansasMinimumFundingSolver();
        var requests = new ArrayList<BigDecimal>();
        for (var request = b("0.00"); request.compareTo(capacity) <= 0; request = request.add(CENT)) requests.add(request);
        if (requests.getLast().compareTo(capacity) < 0) requests.add(capacity);
        for (var request : requests) {
            var executed = converter.convertHousehold(base, request, facts.seed().household().activeOwners(), facts.seed().taxYear());
            assertEquals(0, executed.getTotalConversion().compareTo(request));
            var result = funding.solve(facts, executed, strategy);
            assertNotEquals(ArkansasMinimumFundingSolver.Status.UNSUPPORTED, result.status(), result.detail());
            if (result.candidate().filter(c -> c.annualTaxFacts().federal().getTaxableIncome().compareTo(target) <= 0).isPresent()) {
                found.add(request);
            }
        }
        return found;
    }

    private static ArkansasRothFunding.Trial certified(ArkansasRothFunding.TargetResult result) {
        assertEquals(CERTIFIED, result.status(), () -> result.detail() + " " + result.metrics());
        return result.trial().orElseThrow();
    }

    private static void assertFundsConserved(ArkansasRothFunding.Trial trial, ProjectedPortfolio before) {
        var funded = calculated(trial.funding());
        var settled = ArkansasAnnualFunding.settle(funded);
        money(before.getTotalBalance().toPlainString(), trial.conversion().getPortfolio().getTotalBalance());
        money(before.getTotalBalance().subtract(funded.spendable()).toPlainString(), funded.portfolio().getTotalBalance());
        money(funded.portfolio().getTotalBalance().add(settled.cash().retainedHouseholdSurplus()).toPlainString(),
                settled.portfolio().getTotalBalance());
        money(funded.surplus().toPlainString(), settled.cash().retainedFromTaxFundingSurplus());
    }

    private static Facts withIncome(TaxHouseholdContext context, FilingStatus filing, TaxIncome income, Cash cash,
                                     ArkansasTaxEvidence evidence) {
        return new Facts(new StateTaxCalculationRequest(2026, filing, context, income,
                new FederalTaxCalculator().calculate(income, filing, rules), rules,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)), evidence, Optional.empty(), Map.of(), cash);
    }

    private static Cash noCash() { return new Cash(ZERO, ZERO, ZERO, ZERO, ZERO); }

    private static TaxHouseholdContext single(LocalDate birth) {
        return TaxHouseholdContext.capture(new Household(new Person("A", "Owner", birth), null), START, null);
    }

    private static TaxHouseholdContext joint(LocalDate primary, LocalDate spouse) {
        return TaxHouseholdContext.capture(new Household(new Person("A", "Owner", primary),
                new Person("B", "Owner", spouse)), START, null);
    }

    private static IncomeSourceAttribution rmd(Account account, String amount) {
        return IncomeSourceAttribution.withdrawal(account, b(amount), IncomeSourceAttribution.Kind.RMD,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
    }
}
