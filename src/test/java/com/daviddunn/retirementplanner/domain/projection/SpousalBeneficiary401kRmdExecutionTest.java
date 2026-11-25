package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.Traditional403B;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition;
import com.daviddunn.retirementplanner.domain.rmd.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.model.DeathScenario.*;
import static com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

public class SpousalBeneficiary401kRmdExecutionTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test void olderSurvivorKeepsSeparateOwnerRmdWhileEmployerPlanDefersTo2045() {
        var plan = SpousalBeneficiaryIraRmdExecutionTest.select(
                SpousalIraRmdExecutionTest.fixture(SPOUSE_DIES, 2035, "246000", "500000", true));
        replaceOriginalIra(plan, SPOUSE, "500000");
        var younger = plan.getHousehold().getSpouse();
        younger.setBirthDate(LocalDate.of(1970, 6, 15));
        younger.replaceIncomeSource(younger.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Own benefit", SPOUSE, younger.getBirthDate().plusYears(70), null, ZERO, 70, ZERO, 2033));
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(), 14, old.getProjectionStartDate(),
                old.getStateTaxAssumptions()));

        var projection = applied(plan);
        money("10000", projection.getYearAt(2).getRequiredMinimumDistribution());
        var deferred = projection.getYearAt(3);
        assertEquals(2036, deferred.getCalendarYear());
        assertEquals(2045, obligation(deferred).firstDistributionYear());
        money("500000", obligation(deferred).priorBalance());
        money("0", obligation(deferred).requiredAmount());
        assertNull(obligation(deferred).divisor());
        money("9957.81", deferred.getRequiredMinimumDistribution());
        assertTrue(receipts(deferred).isEmpty());
        money("500000", projection.getYearAt(11).getEndingAccountSnapshots().getFirst().getEndingBalance());

        var first = projection.getYearAt(12);
        assertEquals(2045, first.getCalendarYear());
        money("8.1", obligation(first).divisor());
        money("61728.40", obligation(first).requiredAmount());
        money("61728.40", receipts(first).getFirst().grossAmount());
        assertEquals(PRIMARY, receipts(first).getFirst().owner().orElseThrow());
        assertEquals(SPOUSE, receipts(first).getFirst().account().orElseThrow().owner());
        money("438271.60", first.getEndingAccountSnapshots().getFirst().getEndingBalance());
        var next = projection.getYearAt(13);
        money("7.6", obligation(next).divisor());
        money("438271.60", obligation(next).priorBalance());
        money("57667.32", receipts(next).getFirst().grossAmount());
    }

    @Test void youngerSurvivorPaysFromOriginalEmployerPositionAtSingleLifeAge73() {
        var plan = fixture("500000");
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        var projection = applied(plan);
        assertTrue(projection.getYearAt(1).beneficiary401kRmds().isEmpty());
        money("0", projection.getYearAt(1).getRequiredMinimumDistribution());
        var first = projection.getYearAt(2);
        assertEquals(2035, obligation(first).firstDistributionYear());
        money("16.4", obligation(first).divisor());
        money("30487.80", first.getRequiredMinimumDistribution());
        money("30487.80", first.getAdjustedGrossIncome());
        money("30487.80", first.getHouseholdCashSettlement().projectedPeriodRmdCash());
        money("469512.20", first.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("123000", first.getEndingAccountSnapshots().get(1).getEndingBalance());
        assertTrue(first.spousal401kRollovers().isEmpty());
        assertNull(first.beneficiaryIraRmd());
        var debit = receipts(first).getFirst();
        assertEquals(1, receipts(first).size());
        assertEquals(SPOUSE, debit.owner().orElseThrow());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(source), debit.account().orElseThrow());
        assertEquals(obligation(first).source(), debit.account().orElseThrow());
        assertEquals(AccountType.TRADITIONAL_401K, obligation(first).source().type());
        money("30487.80", debit.modeledTaxableAmount().orElseThrow());
        money("15.6", obligation(projection.getYearAt(3)).divisor());
        money("30096.94", obligation(projection.getYearAt(3)).requiredAmount());
    }

    @Test void mixedMultiplePlansPayTheirOwnAmountsInEitherWithdrawalOrder() {
        var plan = fixture("164000");
        var portfolio = plan.getAccountPortfolio();
        var firstPlan = portfolio.getAccounts().getFirst();
        var secondPlan = new Traditional401K("Other plan", PRIMARY, new BigDecimal("82000"));
        portfolio.addAccount(secondPlan);
        portfolio.addAccount(new TraditionalIRA("Original beneficiary IRA", PRIMARY, new BigDecimal("32800")));
        portfolio.addAccount(new Traditional401K("Living plan", SPOUSE, new BigDecimal("24600")));
        for (int order = 0; order < 2; order++) {
            var project = applied(plan);
            var first = project.getYearAt(2);
            assertEquals(2, first.beneficiary401kRmds().size());
            money("2000", first.beneficiaryIraRmd().requiredAmount());
            money("17000", first.getRequiredMinimumDistribution());
            assertEquals(2, receipts(first).size());
            var byPlan = receipts(first).stream().collect(Collectors.toMap(
                    row -> row.account().orElseThrow(), IncomeSourceAttribution::grossAmount));
            money("10000", byPlan.get(IncomeSourceAttribution.AccountReference.capture(firstPlan)));
            money("5000", byPlan.get(IncomeSourceAttribution.AccountReference.capture(secondPlan)));
            var balances = first.getEndingAccountSnapshots().stream().collect(Collectors.toMap(
                    ProjectedAccountSnapshot::getAccount, ProjectedAccountSnapshot::getEndingBalance));
            money("154000", balances.get(firstPlan));
            money("77000", balances.get(secondPlan));
            var livingDebits = project.getYearAt(4).getIncomeSources().stream()
                    .filter(row -> row.kind() == IncomeSourceAttribution.Kind.RMD && row.successorRmd().isEmpty()).toList();
            assertEquals(2, livingDebits.size());
            var byType = livingDebits.stream().collect(Collectors.toMap(
                    row -> row.account().orElseThrow().type(), IncomeSourceAttribution::grossAmount));
            money("5000", byType.get(AccountType.TRADITIONAL_IRA));
            money("1000", byType.get(AccountType.TRADITIONAL_401K));
            if (order == 0) {
                var reordered = new ArrayList<>(portfolio.getAccounts());
                Collections.swap(reordered, reordered.indexOf(firstPlan), reordered.indexOf(secondPlan));
                plan = new RetirementPlan(plan.getHousehold(), new AccountPortfolio(reordered), plan.getPlanningAssumptions());
                plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
            }
        }
    }

    @Test void householdTaxAndMedicareUseActualEmployerDebitsOnce() {
        var plan = fixture("246000");
        var spouse = plan.getHousehold().getSpouse();
        var start = LocalDate.of(2035, 1, 1);
        spouse.replaceIncomeSource(spouse.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Spouse benefit", SPOUSE, spouse.getBirthDate().plusYears(67),
                        null, new BigDecimal("2000"), 67, ZERO, 2033));
        spouse.addIncomeSource(new Pension("Spouse pension", SPOUSE, start, null, new BigDecimal("2000"), ZERO));
        plan.getHousehold().addExpense(new Expense("One-time spending", new BigDecimal("59200"), GrowthCategory.GENERAL,
                start, start, ExpenseType.ONE_TIME));
        var year = applied(plan).getYearAt(2);
        // $51,000 provisional income gives $4,500 + .85 × ($51,000 - $34,000) taxable Social Security.
        money("15000", obligation(year).requiredAmount());
        money("15000", year.getHouseholdCashSettlement().projectedPeriodRmdCash());
        money("18950", year.getTaxableSocialSecurity());
        money("57950", year.getAdjustedGrossIncome());
        money("57950", year.getModifiedAdjustedGrossIncome());
        money("4774", year.getFederalIncomeTax());
        money("2434.80", year.getAnnualMedicarePremium());
        money("3408.80", year.getTaxFundingWithdrawal());
        money("49286.80", year.getEndingRetainedNonQualifiedAssets());
        money("414786.80", year.getAfterTaxEstateValue());
        money("231000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
    }

    @Test void employerShortfallCannotUseOtherPlansAndZeroCannotReplaceMissingPrior() throws Exception {
        var plan = fixture("246000");
        var other = new Traditional401K("Other plan", PRIMARY, new BigDecimal("82000"));
        plan.getAccountPortfolio().addAccount(other);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var policy = new SpousalIraRmdPolicy(plan, rules.getRmdRules(), SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K);
        var opening = ProjectedPortfolio.from(plan.getAccountPortfolio());
        var source = opening.getAccountBalances().getFirst();
        var own = opening.getAccountBalances().get(1);
        var otherBalance = opening.getAccountBalances().getLast();
        var date = LocalDate.of(2034, 12, 31);
        var zeroSnapshot = new RmdBalanceSnapshot(date, List.of(new ProjectedAccountBalance(source.getAccount(), ZERO), own, otherBalance));
        var zero = policy.membership(2035, opening, zeroSnapshot).beneficiaryEmployerObligations().getFirst();
        money("0", zero.requiredAmount());
        money("16.4", zero.divisor());
        for (var prior : List.of(new RmdBalanceSnapshot(date, List.of(own, otherBalance)),
                new RmdBalanceSnapshot(date, List.of(source, own, otherBalance, source)))) {
            assertEquals(MISSING_OR_DUPLICATE_SOURCE,
                    assertThrows(SpousalIraRmdUnavailable.class, () -> policy.membership(2035, opening, prior)).reason());
        }
        var enough = RmdBalanceSnapshot.from(date, opening);
        var funds = opening.withWithdrawal(source.getAccount(), new BigDecimal("231001"));
        var membership = policy.membership(2035, funds, enough);
        var household = new HouseholdRmdCalculator().calculate(plan, enough, 2035, rules, Set.of(SPOUSE), membership);
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ProjectedWithdrawalAllocator().allocateHouseholdRmds(funds, household, membership));
        var constraint = assertInstanceOf(FundingConstraint.class, error.getCause());
        assertEquals(FundingFailure.Stage.ACCOUNT_RMD, constraint.stage);
        money("15000", constraint.required);
        money("14999", constraint.available);
        money("82000", funds.getBalance(other));

        var first = membership.beneficiaryEmployerObligations().getFirst();
        var alternate = membership.beneficiaryEmployerObligations().getLast();
        var resolved = policy.ownership(2035).accounts().get(first.source());
        assertTrue(resolved.effectiveRmdOwner().isEmpty());
        assertEquals(SPOUSE, resolved.effectiveRmdRecipient().orElseThrow());
        var debit = IncomeSourceAttribution.withdrawal(source.getAccount(), new BigDecimal("15000"),
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2035);
        assertThrows(IllegalArgumentException.class, () -> debit.forSuccessorRmd(resolved));
        assertThrows(IllegalArgumentException.class, () -> debit.forBeneficiary401kRmd(resolved, alternate));
        assertThrows(IllegalArgumentException.class, () -> debit.forBeneficiary401kRmd(resolved,
                new Beneficiary401kRmd(first.source(), 2036, 2035, SPOUSE, first.priorBalance(), first.divisor(), first.requiredAmount())));
        assertThrows(IllegalArgumentException.class, () -> debit.forBeneficiary401kRmd(resolved, zero));
        assertThrows(IllegalArgumentException.class, () ->
                new Beneficiary401kRmd(first.source(), 2035, 2035, PRIMARY, first.priorBalance(), first.divisor(), first.requiredAmount()));
    }

    @Test void aZeroBalancePlanStillHasAnExplicitApplicableObligation() {
        var plan = fixture("0");
        assertTrue(ProjectionEngine.requestsSpousalBeneficiary401kRmd(plan));
        var year = applied(plan).getYearAt(2);
        money("0", obligation(year).priorBalance());
        money("16.4", obligation(year).divisor());
        money("0", obligation(year).requiredAmount());
        assertTrue(receipts(year).isEmpty());
    }

    @Test void employerBeneficiaryRmdUsesExistingCutoffAndNeverSuppliesFixedOrTargetedRoth() {
        var plan = fixture("246000");
        plan.getAccountPortfolio().addAccount(new RothIRA("Deceased original Roth", PRIMARY, ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2035, new BigDecimal("1000"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        money("0", applied(plan).getYearAt(2).getRothConversion());
        for (var strategy : List.of(RothConversionStrategy.FIXED_AMOUNT, RothConversionStrategy.FILL_12_PERCENT_BRACKET)) {
            plan.setRothConversionRequest(new RothConversionRequest(true, 2035, new BigDecimal("1000"),
                    RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME));
            var year = applied(plan).getYearAt(2);
            money(strategy == RothConversionStrategy.FIXED_AMOUNT ? "1000" : "51500", year.getRothConversion());
            money("0", year.getPrimaryRothConversion());
            money("231000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        }
    }

    @Test void unresolvedHistoryUnsupportedStatesPlansAndDiscretionaryDrawsRefuse() {
        var late = SpousalBeneficiaryIraRmdExecutionTest.select(
                SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2035, "246000", "123000", false));
        replaceOriginalIra(late, PRIMARY, "246000");
        assertEquals(UNRESOLVED_MANDATORY_DISTRIBUTION, refusal(late).reason());

        var history = fixture("246000");
        history.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2033, new BigDecimal("246000"), BigDecimal.ONE));
        assertEquals(INCOMPATIBLE_ACCOUNT_HISTORY, refusal(history).reason());

        var contradiction = fixture("246000");
        contradiction.getAccountPortfolio().getAccounts().getFirst().setRetirementParticipantFacts(new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2035, 1, 1), null, null));
        assertEquals(INCOMPATIBLE_ACCOUNT_HISTORY, refusal(contradiction).reason());

        var unsupported = fixture("246000");
        unsupported.getAccountPortfolio().addAccount(new Traditional403B("Inherited 403(b)", PRIMARY, BigDecimal.ONE));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(unsupported).reason());

        var draw = fixture("246000");
        var start = LocalDate.of(2034, 1, 1);
        draw.getHousehold().addExpense(new Expense("Unsupported spending", new BigDecimal("101000"), GrowthCategory.GENERAL,
                start, start, ExpenseType.ONE_TIME));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(draw).reason());

        for (var state : List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.AR)) {
            var plan = fixture("246000");
            plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
            assertFalse(ProjectionEngine.requestsSpousalBeneficiary401kRmd(plan));
            assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(plan).reason());
        }
        var advanced = fixture("246000");
        advanced.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        assertFalse(ProjectionEngine.requestsSpousalBeneficiary401kRmd(advanced));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(advanced).reason());

        var alive = SpousalBeneficiaryIraRmdExecutionTest.select(SpousalIraRmdExecutionTest.fixture(BOTH_SURVIVE, 0,
                "246000", "123000", false));
        replaceOriginalIra(alive, PRIMARY, "246000");
        assertFalse(ProjectionEngine.requestsSpousalBeneficiary401kRmd(alive));
        var original = new ProjectionEngine().project(alive).getYearAt(2);
        money("10000", original.getRequiredMinimumDistribution());
        assertTrue(original.beneficiary401kRmds().isEmpty());
        assertTrue(original.getIncomeSources().stream().noneMatch(row -> row.successorRmd().isPresent()));
    }

    public static RetirementPlan fixture(String amount) {
        var plan = SpousalBeneficiaryIraRmdExecutionTest.fixture(amount);
        replaceOriginalIra(plan, PRIMARY, amount);
        return plan;
    }

    private static void replaceOriginalIra(RetirementPlan plan, AccountOwnership owner, String amount) {
        var original = plan.getAccountPortfolio().getAccounts().stream()
                .filter(account -> account.getType() == AccountType.TRADITIONAL_IRA && account.getOwnership() == owner)
                .findFirst().orElseThrow();
        plan.getAccountPortfolio().replaceAccount(original, new Traditional401K("Original employer plan", owner, new BigDecimal(amount)));
    }

    private static Beneficiary401kRmd obligation(ProjectionYear year) {
        assertEquals(1, year.beneficiary401kRmds().size());
        return year.beneficiary401kRmds().getFirst();
    }

    private static Projection applied(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                new ProjectionEngine().projectSpousalBeneficiary401kRmd(plan)).projection();
    }

    private static SpousalIraRmdUnavailable refusal(RetirementPlan plan) {
        return assertThrows(SpousalIraRmdUnavailable.class, () -> applied(plan));
    }

    private static List<IncomeSourceAttribution> receipts(ProjectionYear year) {
        return year.getIncomeSources().stream().filter(row -> row.successorRmd().map(authorization ->
                authorization.treatment() == RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1)
                .orElse(false)).toList();
    }

    private static void money(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
