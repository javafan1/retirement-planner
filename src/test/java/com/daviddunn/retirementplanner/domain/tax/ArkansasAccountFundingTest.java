package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculator;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.HouseholdCashSettlement;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectedWithdrawalAllocator;
import com.daviddunn.retirementplanner.domain.rmd.AccountRmd;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding.Cash;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding.Facts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.ProductionStateTaxResult;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationRequest;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasMinimumFundingSolver.Status.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

public class ArkansasAccountFundingTest {
    static final LocalDate START = LocalDate.of(2026, 1, 1);
    static final LocalDate AGE_60 = LocalDate.of(1965, 7, 1);
    static final WithdrawalStrategy DEFERRED_FIRST = new TaxDeferredFirstWithdrawalStrategy();
    static final WithdrawalStrategy TAXABLE_FIRST = new TaxableFirstWithdrawalStrategy();
    static final ProjectedWithdrawalAllocator ALLOCATOR = new ProjectedWithdrawalAllocator();
    static GovernmentRules rules;

    @BeforeAll
    static void loadRules() throws Exception {
        rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
    }

    @Test
    void originalDiscontinuitySettlesRealSurplusAndNextYearCanUseIt() {
        var ira = ira("IRA", "106197");
        var opening = portfolio(ira);
        var spending = ALLOCATOR.allocateAdditionalWithdrawal(opening, b("86197"), DEFERRED_FIRST);
        var facts = ordinary(AGE_60, spending.getWithdrawalBreakdown(), "86197", "0", "0");
        var noConversion = new ProjectedPortfolioRothConverter().convertHousehold(spending.getPortfolio(), ZERO);
        var evaluator = new ArkansasAnnualFunding.Evaluator(facts, noConversion, DEFERRED_FIRST);
        money("16975.09", evaluator.evaluate(b("16972.94")).candidate().totalTax().setScale(2, RoundingMode.HALF_UP));
        money("16972.94", evaluator.evaluate(b("16975.09")).candidate().totalTax().setScale(2, RoundingMode.HALF_UP));
        assertTrue(evaluator.evaluate(b("16973.99")).candidate().deficit().signum() > 0);

        var result = solve(facts, spending.getPortfolio(), DEFERRED_FIRST);
        var candidate = calculated(result);
        money("16974.00", candidate.requested());
        money("16972.66", candidate.totalTax());
        money("1.34", candidate.surplus());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(ira), candidate.allocation().getIncomeSources()
                .getFirst().account().orElseThrow());
        assertEquals(IncomeSourceAttribution.Purpose.TAX_FUNDING, candidate.allocation().getIncomeSources().getFirst().purpose());
        money("3026.00", candidate.portfolio().getBalance(ira));
        money("106197", ira.getCurrentBalance());
        assertTrue(result.metrics().candidates() < 100, result.metrics().toString());
        System.out.println("Arkansas discontinuity fixture " + result.metrics());
        assertThrows(IllegalArgumentException.class, () -> HouseholdCashSettlement.calculate(
                ZERO, ZERO, b("86197"), candidate.spendable(), b("86197"), ZERO, candidate.totalTax()));

        var accepted = ArkansasAnnualFunding.settle(candidate);
        money("1.34", accepted.cash().retainedFromTaxFundingSurplus());
        money("1.34", accepted.portfolio().getRetainedNonQualifiedAssets());
        money("0", candidate.portfolio().getRetainedNonQualifiedAssets());
        var next = accepted.portfolio().applyGrowth(b(".04"));
        money("1.3936", next.getRetainedNonQualifiedAssets());
        var withdrawal = ALLOCATOR.allocateAdditionalWithdrawal(next, b("1.3936"), DEFERRED_FIRST);
        assertTrue(withdrawal.getWithdrawalBreakdown().getIncomeSources().getFirst().account().isEmpty());
        money("0", withdrawal.getPortfolio().getRetainedNonQualifiedAssets());
        money("3147.04", withdrawal.getPortfolio().getBalance(ira));
        money("1.3132", accepted.portfolio().applyGrowth(b("-.02")).getRetainedNonQualifiedAssets());
    }

    @ParameterizedTest
    @ValueSource(strings = {"16973.99", "16974.01"})
    void sourceExhaustionEitherSideOfTheJump(String availableFirst) {
        var incomeIra = ira("Fund and spend", b("86197").add(b(availableFirst)).toPlainString());
        var backup = ira("Second account", "5");
        var spending = ALLOCATOR.allocateAdditionalWithdrawal(portfolio(incomeIra, backup), b("86197"), DEFERRED_FIRST);
        var facts = ordinary(AGE_60, spending.getWithdrawalBreakdown(), "86197", "0", "0");
        var candidate = calculated(solve(facts, spending.getPortfolio(), DEFERRED_FIRST));
        money("16974", candidate.requested());
        if (b(availableFirst).compareTo(candidate.requested()) < 0) {
            assertEquals(2, candidate.allocation().getIncomeSources().size());
            money(".01", candidate.allocation().getIncomeSources().getLast().grossAmount());
        } else {
            assertEquals(1, candidate.allocation().getIncomeSources().size());
            money(".01", candidate.portfolio().getBalance(incomeIra));
        }
    }

    @Test
    void accountBackedTaxAuthorityChecksAllAdjustmentTransitions() {
        var ira = ira("Active", "100");
        for (int band = 0; band <= 29; band++) {
            var edge = BigDecimal.valueOf(94701L + 100L * band);
            var ordinary = edge.add(b("8469"));
            var spendingIra = ira("Paid out", ordinary.toPlainString());
            var spent = ALLOCATOR.allocateAdditionalWithdrawal(portfolio(spendingIra, ira), ordinary, DEFERRED_FIRST);
            var conversion = new ProjectedPortfolioRothConverter().convertHousehold(spent.getPortfolio(), ZERO);
            var facts = ordinary(AGE_60, spent.getWithdrawalBreakdown(), ordinary.toPlainString(), "0", "0");
            var evaluator = new ArkansasAnnualFunding.Evaluator(facts, conversion, DEFERRED_FIRST);
            for (var amount : List.of(b(".99"), b("1.00"), b("1.01"))) {
                var net = edge.add(amount).subtract(BigDecimal.ONE);
                boolean before = amount.compareTo(BigDecimal.ONE) < 0;
                long adjustment = Math.max(290L - 10L * (before ? band - 1 : band), 0);
                var offset = before && band == 0 ? b("367.20") : b("79.90").add(BigDecimal.valueOf(adjustment));
                var expected = net.multiply(b(".037")).subtract(offset).subtract(b("29")).setScale(2, RoundingMode.HALF_UP);
                money(expected.toPlainString(), evaluator.evaluate(amount).candidate().stateTax().incomeTax());
            }
        }
    }

    @Test
    void fractionalTerminalIsUsedOnlyWhenExactCashCoversTheNativeLiability() {
        // Federal 2620 + 0.102*SS and Arkansas 992.41, leaving a native deficit of .0050154.
        var noDebit = WithdrawalBreakdown.zero();
        var facts = facts(AGE_60, noDebit, "0", "40000", "0", "4022.7227", "0", "4022.7227", List.of());
        var enough = new CheckingAccount("Subcent", PRIMARY, b(".006"));
        var accepted = calculated(solve(facts, portfolio(enough), TAXABLE_FIRST));
        money(".006", accepted.requested());
        money(".0009846", accepted.surplus());
        var shortCash = new CheckingAccount("Too little", PRIMARY, b(".004"));
        assertEquals(INSUFFICIENT_FUNDS, solve(facts, portfolio(shortCash), TAXABLE_FIRST).status());
    }

    @Test
    void withdrawalOrderPreservesBrokeragePrincipalAndRetainedHouseholdAssets() {
        var ira = ira("IRA", "20000");
        var broker = new BrokerageAccount("Basis unknown", PRIMARY, b("10000"));
        var base = portfolio(ira, broker).withAdditionalRetainedNonQualifiedAssets(b("100"));
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var taxable = calculated(solve(facts, base, TAXABLE_FIRST));
        var deferred = calculated(solve(facts, base, DEFERRED_FIRST));
        assertTrue(taxable.allocation().getIncomeSources().getFirst().account().isEmpty());
        assertTrue(taxable.allocation().getIncomeSources().getLast().modeledTaxableAmount().isEmpty());
        money("0", taxable.taxableFundingIncome());
        money("50000", taxable.annualTaxFacts().federal().getAdjustedGrossIncome());
        assertTrue(deferred.taxableFundingIncome().signum() > 0);
        assertTrue(deferred.spendable().compareTo(taxable.spendable()) > 0);
    }

    @Test
    void jointRecipientPoolsAndSurvivingIndividualUseActualFundingOwners() {
        var primary = ira("Primary", "1000");
        var spouse = new TraditionalIRA("Spouse", SPOUSE, b("20000"));
        var joint = new Household(new Person("A", "Owner", AGE_60), new Person("B", "Owner", LocalDate.of(1950, 1, 1)));
        var date = START;
        var ctx = TaxHouseholdContext.capture(joint, date, null);
        var income = new TaxIncome(ZERO, ZERO, ZERO, ZERO, b("60000"));
        var seed = seed(ctx, FilingStatus.MARRIED_FILING_JOINTLY, income);
        var facts = new Facts(seed, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new Cash(ZERO, ZERO, ZERO, ZERO, ZERO));
        var result = calculated(solve(facts, portfolio(primary, spouse), DEFERRED_FIRST));
        assertEquals(List.of(PRIMARY, SPOUSE), result.allocation().getIncomeSources().stream()
                .map(source -> source.owner().orElseThrow()).toList());
        assertTrue(result.stateTax().limitations().getLast().contains("retirement exclusion"));

        var survivor = ctx.withActiveOwners(Set.of(SPOUSE));
        var surviving = new Facts(seed(survivor, FilingStatus.SINGLE, income), ArkansasTaxEvidence.NONE,
                Optional.empty(), Map.of(), facts.cash());
        assertEquals(SPOUSE, calculated(solve(surviving, portfolio(spouse), DEFERRED_FIRST))
                .allocation().getIncomeSources().getFirst().owner().orElseThrow());
        assertEquals(UNSUPPORTED, solve(surviving, portfolio(primary, spouse), DEFERRED_FIRST).status());
    }

    @Test
    void fixedConversionUsesPostRmdPostConversionCapacityAndCoversActualTax() {
        var ira = ira("IRA", "59000");
        var roth = new RothIRA("Same owner Roth", PRIMARY, ZERO);
        var original = portfolio(ira, roth);
        var rmd = b("10000");
        var afterRmd = ALLOCATOR.applyAccountRmds(original, List.of(new AccountRmd(ira, rmd)));
        var payment = IncomeSourceAttribution.withdrawal(ira, rmd, IncomeSourceAttribution.Kind.RMD,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        var rmdBreakdown = new WithdrawalBreakdown(ZERO, ZERO, rmd, ZERO, List.of(payment));
        var facts = facts(LocalDate.of(1950, 1, 1), rmdBreakdown, "10000", "0", "0", "0", "10000", "0", List.of());
        var result = new ArkansasRothFunding().fixed(facts, afterRmd, DEFERRED_FIRST, b("48000"));
        money("48000", result.conversion().getTotalConversion());
        var candidate = calculated(result.funding());
        money("1000", candidate.allocation().getTaxDeferredWithdrawal());
        assertTrue(candidate.allocation().getRothWithdrawal().signum() > 0);
        money("0", candidate.portfolio().getBalance(ira));
        money("59000", ira.getCurrentBalance());

        var shortfall = new ArkansasRothFunding().fixed(facts, afterRmd, DEFERRED_FIRST, b("55000"));
        money("6000", shortfall.shortfall());
        money("49000", calculated(shortfall.funding()).annualTaxFacts().income().getRothConversion());
        var noDestinationFacts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "60000", "0");
        var noDestination = new ArkansasRothFunding().fixed(noDestinationFacts, portfolio(ira), DEFERRED_FIRST, b("10000"));
        money("10000", noDestination.shortfall());
        money("0", calculated(noDestination.funding()).annualTaxFacts().income().getRothConversion());
    }

    @Test
    void targetedProofCoversCashAndTheOriginalDeferredFundedOptimalCandidate() {
        var ira = ira("IRA", "90000");
        var roth = new RothIRA("Roth", PRIMARY, ZERO);
        var cash = new CheckingAccount("Cash", PRIMARY, b("20000"));
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var solver = new ArkansasRothFunding();
        long start = System.nanoTime();
        var supported = solver.targeted(facts, portfolio(ira, roth, cash), TAXABLE_FIRST, b("50400"));
        long cashMicros = (System.nanoTime() - start) / 1000;
        assertEquals(ArkansasRothFunding.TargetStatus.CERTIFIED, supported.status(), supported.detail());
        assertEquals(1, supported.trials());
        var funded = supported.trial().orElseThrow();
        money("16500", funded.conversion().getTotalConversion());
        money("50400", calculated(funded.funding()).annualTaxFacts().federal().getTaxableIncome());
        money("0", calculated(funded.funding()).taxableFundingIncome());

        start = System.nanoTime();
        var deferred = solver.targeted(facts, portfolio(ira, roth), DEFERRED_FIRST, b("50400"));
        long deferredMicros = (System.nanoTime() - start) / 1000;
        assertEquals(ArkansasRothFunding.TargetStatus.CERTIFIED, deferred.status(),
                () -> deferred.detail() + " " + deferred.metrics());
        var exact = deferred.trial().orElseThrow();
        money("8949.09", exact.conversion().getTotalConversion());
        var fundedFromIra = calculated(exact.funding());
        money("7550.91", fundedFromIra.spendable());
        money("7550.91", fundedFromIra.totalTax());
        money("50400", fundedFromIra.annualTaxFacts().federal().getTaxableIncome());
        money("73500", fundedFromIra.portfolio().getBalance(ira));
        money("8949.09", fundedFromIra.portfolio().getBalance(roth));
        money("90000", ira.getCurrentBalance());
        money("0", roth.getCurrentBalance());
        var nextCent = calculated(solver.fixed(facts, portfolio(ira, roth), DEFERRED_FIRST, b("8949.10")).funding());
        assertTrue(nextCent.annualTaxFacts().federal().getTaxableIncome().compareTo(b("50400")) > 0);
        assertTrue(deferred.trials() < 20, deferred.metrics().toString());
        System.out.println("Arkansas target cash " + cashMicros + " us " + supported.metrics());
        System.out.println("Arkansas target original deferred " + deferredMicros + " us " + deferred.metrics());
    }

    @Test
    void annualMedicarePassRebuildsSpendingAndOnlyFinalPortfolioIsRetained() {
        var ira = ira("IRA", "150000");
        var start = portfolio(ira);
        var birth = LocalDate.of(1955, 1, 1);
        var firstSpending = ALLOCATOR.allocateAdditionalWithdrawal(start, b("86197"), DEFERRED_FIRST);
        var first = calculated(solve(ordinary(birth, firstSpending.getWithdrawalBreakdown(), "86197", "0", "0"),
                firstSpending.getPortfolio(), DEFERRED_FIRST));
        var premium = new MedicarePremiumCalculator().calculate(first.annualTaxFacts().federal(), FilingStatus.SINGLE, rules, 1)
                .totalAnnualMedicarePremium();
        assertTrue(premium.signum() > 0);
        var finalSpending = ALLOCATOR.allocateAdditionalWithdrawal(start, b("86197").add(premium), DEFERRED_FIRST);
        var finalFacts = ordinary(birth, finalSpending.getWithdrawalBreakdown(), "86197", "0", premium.toPlainString());
        var result = calculated(solve(finalFacts, finalSpending.getPortfolio(), DEFERRED_FIRST));
        var repeat = calculated(solve(finalFacts, finalSpending.getPortfolio(), DEFERRED_FIRST));
        assertEquals(result.allocation().getIncomeSources(), repeat.allocation().getIncomeSources());
        var ledger = ArkansasAnnualFunding.settle(result);
        money(premium.toPlainString(), ledger.cash().medicarePremiums());
        money("0", first.portfolio().getRetainedNonQualifiedAssets());
        money("0", start.getRetainedNonQualifiedAssets());
        money("150000", ira.getCurrentBalance());
    }

    @Test
    void verifiedNiitIsRepricedAcrossMagiThreshold() {
        var ira = ira("IRA", "100000");
        var interest = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.INTEREST,
                IncomeSourceAttribution.Purpose.INCOME, "Known taxable interest", b("195000"), Optional.of(b("195000")));
        var mode = IncomeSourceAttribution.calculationPolicy(
                com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.ADVANCED, 2026);
        var facts = facts(AGE_60, WithdrawalBreakdown.zero(), "0", "195000", "0", "0", "0", "0",
                List.of(interest, mode));
        var candidate = calculated(solve(facts, portfolio(ira), DEFERRED_FIRST));
        var niit = candidate.annualTaxFacts().federal().niit().orElseThrow();
        assertTrue(niit.tax().signum() > 0);
        assertEquals(niit, candidate.incomeSources().getLast().niit().orElseThrow());
        money(candidate.requested().subtract(b("5000")).multiply(b(".038")).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                niit.tax());
    }

    @Test
    void federalSocialSecurityTaxRepricesAtTheFirstThresholdAndFundsExactly() {
        var ira = ira("IRA", "10000");
        var start = portfolio(ira);
        // State tax is stable zero in this low-ordinary-income example. Funding representative
        // federal taxable Social Security directly across both provisional-income thresholds.
        var seed = seed(TaxHouseholdContext.capture(new Household(new Person("A", "Owner", AGE_60), null), START, null),
                FilingStatus.SINGLE, new TaxIncome(ZERO, b("50000"), ZERO, ZERO, ZERO));
        var facts = new Facts(seed, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new Cash(b("50000"), ZERO, ZERO, b("50000"), ZERO));
        var evaluator = new ArkansasAnnualFunding.Evaluator(facts,
                new ProjectedPortfolioRothConverter().convertHousehold(start, ZERO), DEFERRED_FIRST);
        money("0", evaluator.evaluate(ZERO).candidate().annualTaxFacts().federal().getTaxableSocialSecurity());
        money(".50", evaluator.evaluate(b("1")).candidate().annualTaxFacts().federal().getTaxableSocialSecurity());
        money("4500.85", evaluator.evaluate(b("9001")).candidate().annualTaxFacts().federal().getTaxableSocialSecurity());
        money("0", calculated(solve(facts, start, DEFERRED_FIRST)).deficit());
    }

    @Test
    void boundedProofAndMissingEvidenceAreNeitherSuccessNorDepletion() {
        var ira = ira("IRA", "30000");
        var facts = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "86197", "0");
        var execution = new ProjectedPortfolioRothConverter().convertHousehold(portfolio(ira), ZERO);
        assertEquals(ArkansasMinimumFundingSolver.Reason.BUDGET_EXHAUSTED,
                new ArkansasMinimumFundingSolver(1).solve(facts, execution, DEFERRED_FIRST).reason());
        var early = ordinary(LocalDate.of(1980, 1, 1), WithdrawalBreakdown.zero(), "0", "50000", "0");
        var result = solve(early, portfolio(ira), DEFERRED_FIRST);
        assertEquals(UNSUPPORTED, result.status());
        assertTrue(result.detail().contains("Form 5329"), result.detail());
        assertEquals(INSUFFICIENT_FUNDS, solve(facts, portfolio(ira("Insufficient", "10")), DEFERRED_FIRST).status());
        var unexplainedCash = new Facts(facts.seed(), facts.evidence(), facts.projectedReceipts(),
                facts.additionalTax(), new Cash(b("90000"), ZERO, ZERO, ZERO, ZERO));
        assertEquals(UNSUPPORTED, solve(unexplainedCash, portfolio(ira), DEFERRED_FIRST).status());
        assertEquals(UNSUPPORTED, solve(facts, portfolio(ira, ira), DEFERRED_FIRST).status());
        var stale = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.INTEREST,
                IncomeSourceAttribution.Purpose.INCOME, "Prior interest evidence", b("50000"), Optional.of(b("50000"))).inYear(2025);
        var mismatch = assertThrows(ArkansasAnnualFunding.Unavailable.class,
                () -> facts(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0", "0", "0", "0", List.of(stale)));
        assertTrue(mismatch.getMessage().contains("another tax year"));
    }

    @Test
    void fundingDoesNotInferAgeQualificationFromTheAnnualEnd() {
        var ira = ira("IRA", "20000");
        var balance = portfolio(ira);
        var facts = ordinary(LocalDate.of(1966, 8, 31), WithdrawalBreakdown.zero(), "0", "50000", "0");
        var paymentEvidence = Map.of(IncomeSourceAttribution.AccountReference.capture(ira),
                ArkansasTaxEvidence.Form5329Status.NO_APPLICABLE_ADDITIONAL_TAX);
        var broad = new Facts(facts.seed(), facts.evidence(), Optional.empty(), paymentEvidence, facts.cash());
        var unknown = solve(broad, balance, DEFERRED_FIRST);
        assertEquals(UNSUPPORTED, unknown.status(), unknown.detail());
        assertTrue(unknown.detail().contains("timing") || unknown.reason() == ArkansasMinimumFundingSolver.Reason.BUDGET_EXHAUSTED);
        var afterBirthday = new ArkansasTaxEvidence.ReceiptWindow(START.plusMonths(3), START.plusMonths(5));
        var evidenced = new Facts(facts.seed(), facts.evidence(), Optional.of(afterBirthday), Map.of(), facts.cash());
        assertTrue(calculated(solve(evidenced, balance, DEFERRED_FIRST)).taxableFundingIncome().signum() > 0);
    }

    @Test
    void openingRmdEntersTaxBaseButCannotFundCurrentCashObligations() {
        var ira = ira("Existing IRA", "20000");
        var beforeOpening = IncomeSourceAttribution.withdrawal(ira, b("50000"),
                IncomeSourceAttribution.Kind.OPENING_RMD, IncomeSourceAttribution.Purpose.INCOME).inYear(2026);
        var household = new Household(new Person("A", "Owner", LocalDate.of(1950, 1, 1)), null);
        var partial = START.plusMonths(6);
        var ctx = TaxHouseholdContext.capture(household, partial, null);
        var seed = seed(ctx, FilingStatus.SINGLE, new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO));
        var captured = ArkansasAnnualFunding.captureIncome(seed, household, HouseholdSocialSecurityResult.primaryOnly(ZERO),
                WithdrawalBreakdown.zero(), b("50000"), ZERO, List.of(beforeOpening));
        var facts = new Facts(captured, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new Cash(ZERO, ZERO, ZERO, ZERO, ZERO));
        var candidate = calculated(solve(facts, portfolio(ira), DEFERRED_FIRST));
        money("0", candidate.otherTaxCash());
        assertTrue(candidate.spendable().signum() > 0);
        assertTrue(candidate.annualTaxFacts().federal().getAdjustedGrossIncome().compareTo(b("50000")) > 0);
        assertTrue(candidate.annualTaxFacts().income().getIncomeSources().contains(beforeOpening));
    }

    @Test
    void mediumPortfolioReplicatesTwoAnnualPassesUnderMatchedPaths() {
        var accounts = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> ira("IRA " + index, "1000")).toArray(Account[]::new);
        var start = portfolio(accounts);
        var yearOne = ordinary(AGE_60, WithdrawalBreakdown.zero(), "0", "50000", "0");
        var first = solve(yearOne, start, DEFERRED_FIRST);
        var accepted = ArkansasAnnualFunding.settle(calculated(first));
        assertTrue(first.metrics().sourceSegmentsVisited() >= 5);
        assertTrue(first.metrics().candidates() <= 100);
        money("0", accepted.portfolio().getBalance(accounts[0]));
        System.out.println("Arkansas medium portfolio " + first.metrics());
        var positive = accepted.portfolio().applyGrowth(b(".04"));
        var negative = accepted.portfolio().applyGrowth(b("-.02"));
        var projected = new GovernmentRuleProjectionService().project(rules,
                com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createSinglePersonPlan()
                        .getPlanningAssumptions(), 2027);
        var context = new TaxHouseholdContext(START.plusYears(1), Map.of(PRIMARY, AGE_60), Set.of(PRIMARY),
                Set.of(PRIMARY), Optional.empty());
        var income = new TaxIncome(ZERO, ZERO, ZERO, ZERO, b("38000"));
        var seed = new StateTaxCalculationRequest(2027, FilingStatus.SINGLE, context, income,
                new FederalTaxCalculator().calculate(income, FilingStatus.SINGLE, projected), projected,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL));
        var yearTwo = new Facts(seed, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new Cash(ZERO, ZERO, ZERO, ZERO, ZERO));
        var up = ArkansasAnnualFunding.settle(calculated(solve(yearTwo, positive, DEFERRED_FIRST)));
        var down = ArkansasAnnualFunding.settle(calculated(solve(yearTwo, negative, DEFERRED_FIRST)));
        assertTrue(up.portfolio().getTotalBalance().compareTo(down.portfolio().getTotalBalance()) > 0);
        money("1000", accounts[0].getCurrentBalance());
    }

    /** Test-local carrier for the existing annual DTO, which requires a registered state enum. */
    public static Projection accountingFixture() throws Exception {
        if (rules == null) loadRules();
        var ira = ira("IR Account", "106197");
        var spending = ALLOCATOR.allocateAdditionalWithdrawal(portfolio(ira), b("86197"), DEFERRED_FIRST);
        var facts = ordinary(AGE_60, spending.getWithdrawalBreakdown(), "86197", "0", "0");
        var candidate = calculated(solve(facts, spending.getPortfolio(), DEFERRED_FIRST));
        var paid = ArkansasAnnualFunding.settle(candidate);
        var federal = candidate.annualTaxFacts().federal();
        var stateEnvelope = new ProductionStateTaxResult(StateTaxJurisdiction.FL,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                StateTaxCalculationStatus.CALCULATED, candidate.stateTax().incomeTax(),
                List.of("Isolated Arkansas accounting test carrier"), Optional.empty());
        var year = new ProjectionYear(0, 2026, b("106197"), ZERO, ZERO, HouseholdSocialSecurityResult.primaryOnly(ZERO),
                b("86197"), b("86197"), b("86197").add(candidate.spendable()), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                paid.portfolio().getRetainedNonQualifiedAssets(), paid.cash(), paid.portfolio().getTotalBalance(),
                List.of(new ProjectedAccountSnapshot(ira, paid.portfolio().getBalance(ira))), federal, stateEnvelope,
                new MedicarePremiumCalculator().calculate(federal, FilingStatus.SINGLE, rules, 0),
                candidate.spendable(), ZERO, ZERO, ZERO, null, ZERO, paid.portfolio().getTotalBalance(), 60,
                candidate.incomeSources());
        var projection = new Projection();
        projection.addYear(year);
        return projection;
    }

    static Facts ordinary(LocalDate birth, WithdrawalBreakdown breakdown, String expense, String interest, String medicare) {
        return facts(birth, breakdown, expense, interest, medicare, "0", "0", "0", List.of());
    }

    private static Facts facts(LocalDate birth, WithdrawalBreakdown breakdown, String expense, String interest,
            String medicare, String guaranteed, String rmd, String ss, List<IncomeSourceAttribution> supplemental) {
        var household = new Household(new Person("A", "Owner", birth), null);
        var selected = HouseholdSocialSecurityResult.primaryOnly(b(ss));
        var base = seed(TaxHouseholdContext.capture(household, START, null), FilingStatus.SINGLE,
                new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO));
        var captured = ArkansasAnnualFunding.captureIncome(base, household, selected, breakdown, ZERO, b(interest), supplemental);
        var spending = b(expense).add(b(medicare)).subtract(b(guaranteed)).subtract(b(rmd)).max(ZERO);
        return new Facts(captured, ArkansasTaxEvidence.NONE, Optional.empty(), Map.of(),
                new Cash(b(guaranteed), b(rmd), spending, b(expense), b(medicare)));
    }

    private static StateTaxCalculationRequest seed(TaxHouseholdContext context, FilingStatus filing, TaxIncome income) {
        return new StateTaxCalculationRequest(2026, filing, context, income,
                new FederalTaxCalculator().calculate(income, filing, rules), rules,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL));
    }

    static ArkansasMinimumFundingSolver.Result solve(Facts facts, ProjectedPortfolio portfolio, WithdrawalStrategy strategy) {
        return new ArkansasRothFunding().fixed(facts, portfolio, strategy, ZERO).funding();
    }

    static ArkansasAnnualFunding.Candidate calculated(ArkansasMinimumFundingSolver.Result result) {
        assertEquals(CALCULATED, result.status(), () -> result.detail() + " " + result.metrics());
        return result.candidate().orElseThrow();
    }

    static TraditionalIRA ira(String name, String balance) { return new TraditionalIRA(name, PRIMARY, b(balance)); }

    static ProjectedPortfolio portfolio(Account... accounts) {
        return new ProjectedPortfolio(List.of(accounts).stream()
                .map(account -> new ProjectedAccountBalance(account, account.getCurrentBalance())).toList());
    }

    static BigDecimal b(String amount) { return new BigDecimal(amount); }
    static void money(String expected, BigDecimal actual) { assertEquals(0, b(expected).compareTo(actual), actual.toPlainString()); }
}
