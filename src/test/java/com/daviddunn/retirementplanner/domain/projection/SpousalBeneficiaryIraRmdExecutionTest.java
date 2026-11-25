package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.model.DeathScenario.*;
import static com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

public class SpousalBeneficiaryIraRmdExecutionTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test void olderSurvivorHasAnIndependentOwnerRmdDuringTenYearAgeDifferenceDeferral() {
        var plan = select(SpousalIraRmdExecutionTest.fixture(SPOUSE_DIES, 2035, "246000", "500000", true));
        var younger = plan.getHousehold().getSpouse();
        younger.setBirthDate(LocalDate.of(1970, 6, 15));
        younger.replaceIncomeSource(younger.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Own benefit", SPOUSE, younger.getBirthDate().plusYears(70), null, ZERO, 70, ZERO, 2033));
        changeHorizon(plan, 14);

        var projection = applied(plan);
        var deferred = projection.getYearAt(3);
        assertEquals(2036, deferred.getCalendarYear());
        assertEquals(2045, deferred.beneficiaryIraRmd().firstDistributionYear());
        money("500000", deferred.beneficiaryIraRmd().priorBalance());
        money("0", deferred.beneficiaryIraRmd().requiredAmount());
        assertNull(deferred.beneficiaryIraRmd().divisor());
        money("10000", projection.getYearAt(2).getRequiredMinimumDistribution());
        money("9957.81", deferred.getRequiredMinimumDistribution());
        assertTrue(beneficiaryReceipts(deferred).isEmpty());
        money("500000", projection.getYearAt(11).getEndingAccountSnapshots().getFirst().getEndingBalance());

        var first = projection.getYearAt(12);
        assertEquals(2045, first.getCalendarYear());
        money("8.1", first.beneficiaryIraRmd().divisor()); // IRS Table I, age 85.
        money("61728.40", first.beneficiaryIraRmd().requiredAmount());
        assertEquals(PRIMARY, beneficiaryReceipts(first).getFirst().owner().orElseThrow());
        assertEquals(SPOUSE, beneficiaryReceipts(first).getFirst().account().orElseThrow().owner());
        money("438271.60", first.getEndingAccountSnapshots().getFirst().getEndingBalance());
        var next = projection.getYearAt(13);
        money("7.6", next.beneficiaryIraRmd().divisor()); // Age 86, independently redetermined.
        money("438271.60", next.beneficiaryIraRmd().priorBalance());
        money("57667.32", beneficiaryReceipts(next).getFirst().grossAmount());
    }

    @Test void youngerSurvivorBeginsInDecedentsApplicableYearWithSingleLifeDivisor() {
        var plan = fixture("500000");
        var projection = applied(plan);
        money("0", projection.getYearAt(1).getRequiredMinimumDistribution());
        assertNull(projection.getYearAt(1).beneficiaryIraRmd());
        var first = projection.getYearAt(2);
        assertEquals(2035, first.beneficiaryIraRmd().firstDistributionYear());
        money("16.4", first.beneficiaryIraRmd().divisor()); // IRS Table I, age 73.
        money("30487.80", first.getRequiredMinimumDistribution());
        money("30487.80", first.getAdjustedGrossIncome());
        money("469512.20", first.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("123000", first.getEndingAccountSnapshots().get(1).getEndingBalance());
        var debit = beneficiaryReceipts(first).getFirst();
        assertEquals(1, beneficiaryReceipts(first).size());
        assertEquals(SPOUSE, debit.owner().orElseThrow());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(plan.getAccountPortfolio().getAccounts().getFirst()),
                debit.account().orElseThrow());
        money("30487.80", debit.modeledTaxableAmount().orElseThrow());
        money("15.6", projection.getYearAt(3).beneficiaryIraRmd().divisor());
        money("30096.94", projection.getYearAt(3).getRequiredMinimumDistribution());

        var ownerPlan = SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2034, "500000", "123000", false);
        var engine = new ProjectionEngine();
        var owner = assertInstanceOf(ProjectionExecutionResult.Completed.class, engine.projectSpousalIraRmd(ownerPlan)).projection();
        money("0", owner.getYearAt(2).getRequiredMinimumDistribution());
        assertNull(owner.getYearAt(2).beneficiaryIraRmd());
        money("10000", engine.project(fixture("246000")).getYearAt(2).getRequiredMinimumDistribution());
    }

    @Test void cashAndTaxSettlementRecognizeOnlyTheExecutedBeneficiaryPayment() {
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
        // $246,000 / 16.4 = $15,000. $51,000 provisional income yields $18,950 taxable SS.
        money("15000", year.beneficiaryIraRmd().requiredAmount());
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

    @Test void splitAndReversedInheritedOrderNeverCrossesOwnIraOrEmployerGroup() {
        var base = fixture("246000");
        var original = base.getAccountPortfolio().getAccounts();
        var accounts = new ArrayList<Account>(List.of(
                new TraditionalIRA("Smaller inherited IRA", PRIMARY, new BigDecimal("6000")),
                original.get(1), new TraditionalIRA("Larger inherited IRA", PRIMARY, new BigDecimal("240000")),
                original.get(2), original.get(3), new Traditional401K("Living plan", SPOUSE, new BigDecimal("24600"))));
        var projection = applied(withAccounts(base, accounts));
        var year = projection.getYearAt(2);
        money("15000", year.beneficiaryIraRmd().requiredAmount());
        var actual = beneficiaryReceipts(year);
        assertEquals(2, actual.size());
        money("6000", actual.get(0).grossAmount());
        money("9000", actual.get(1).grossAmount());
        money("123000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        var at75 = projection.getYearAt(4);
        var ownerDebits = at75.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.RMD
                && s.successorRmd().isEmpty()).toList();
        assertEquals(2, ownerDebits.size());
        money("5000", ownerDebits.getFirst().grossAmount());
        money("1000", ownerDebits.getLast().grossAmount());
        assertEquals(AccountType.TRADITIONAL_401K, ownerDebits.getLast().account().orElseThrow().type());

        accounts.addFirst(accounts.remove(2));
        var reversed = beneficiaryReceipts(applied(withAccounts(base, accounts)).getYearAt(2));
        assertEquals(1, reversed.size());
        money("15000", reversed.getFirst().grossAmount());
        assertEquals("Larger inherited IRA", reversed.getFirst().account().orElseThrow().name());
    }

    @Test void onceBenefitRmdStartsItObeysExistingCutoffAndIneligibleConversionSourcesStayExcluded() {
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

    @Test void exactMembershipDistinguishesZeroMissingDuplicateAndInsufficientBalances() throws Exception {
        var plan = fixture("246000");
        var government = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var policy = new SpousalIraRmdPolicy(plan, government.getRmdRules(), SpousalIraRmdPolicy.Treatment.BENEFICIARY_IRA);
        var opening = ProjectedPortfolio.from(plan.getAccountPortfolio());
        var source = opening.getAccountBalances().getFirst();
        var own = opening.getAccountBalances().get(1);
        var date = LocalDate.of(2034, 12, 31);
        var explicitZero = new RmdBalanceSnapshot(date, List.of(new ProjectedAccountBalance(source.getAccount(), ZERO), own));
        var zero = policy.membership(2035, opening, explicitZero);
        money("0", zero.beneficiaryObligation().requiredAmount());
        money("16.4", zero.beneficiaryObligation().divisor());
        for (var invalid : List.of(new RmdBalanceSnapshot(date, List.of(own)),
                new RmdBalanceSnapshot(date, List.of(source, own, source)))) {
            assertEquals(MISSING_OR_DUPLICATE_SOURCE,
                    assertThrows(SpousalIraRmdUnavailable.class, () -> policy.membership(2035, opening, invalid)).reason());
        }
        var prior = RmdBalanceSnapshot.from(date, opening);
        var available = opening.withWithdrawal(source.getAccount(), new BigDecimal("231001"));
        var membership = policy.membership(2035, available, prior);
        var obligation = new HouseholdRmdCalculator().calculate(plan, prior, 2035, government, Set.of(SPOUSE), membership);
        var failure = assertThrows(IllegalStateException.class,
                () -> new ProjectedWithdrawalAllocator().allocateHouseholdRmds(available, obligation, membership));
        var constraint = assertInstanceOf(FundingConstraint.class, failure.getCause());
        assertEquals(FundingFailure.Stage.IRA_RMD, constraint.stage);
        money("15000", constraint.required);
        money("14999", constraint.available);

        var original = IncomeSourceAttribution.AccountReference.capture(source.getAccount());
        var resolved = policy.ownership(2035).accounts().get(original);
        assertTrue(resolved.effectiveRmdOwner().isEmpty());
        assertEquals(SPOUSE, resolved.effectiveRmdRecipient().orElseThrow());
        var debit = IncomeSourceAttribution.withdrawal(source.getAccount(), new BigDecimal("15001"),
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2035);
        assertThrows(IllegalArgumentException.class, () -> debit.forSuccessorRmd(resolved));
        assertThrows(IllegalArgumentException.class, () -> debit.forSuccessorRmd(resolved, zero.beneficiaryObligation()));
        assertThrows(IllegalArgumentException.class, () -> debit.forSuccessorRmd(resolved, membership.beneficiaryObligation()));
    }

    @Test void mandatoryHistoryUnsupportedPositionsAndDiscretionaryCashCannotSilentlyApply() {
        var late = select(SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2035, "246000", "123000", false));
        assertEquals(UNRESOLVED_MANDATORY_DISTRIBUTION, refusal(late).reason());
        var history = fixture("246000");
        history.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2033, new BigDecimal("246000"), BigDecimal.ONE));
        assertEquals(INCOMPATIBLE_ACCOUNT_HISTORY, refusal(history).reason());
        for (var unsupported : List.of(new Traditional401K("Unmodeled inherited employer plan", PRIMARY, BigDecimal.ONE),
                new RolloverIRA("Unverified rollover history", PRIMARY, BigDecimal.ONE),
                new InheritedTraditionalIRA("Different decedent", SPOUSE, BigDecimal.ONE,
                        new InheritedAccountInformation(LocalDate.of(1950, 1, 1), LocalDate.of(2026, 1, 1), BeneficiaryRelationship.CHILD)))) {
            var plan = fixture("246000");
            plan.getAccountPortfolio().addAccount(unsupported);
            assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(plan).reason());
        }
        var draw = fixture("246000");
        var start = LocalDate.of(2034, 1, 1);
        draw.getHousehold().addExpense(new Expense("Unsupported spending", new BigDecimal("101000"), GrowthCategory.GENERAL,
                start, start, ExpenseType.ONE_TIME));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(draw).reason());
        for (var state : List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.AR)) {
            var plan = fixture("246000");
            plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
            assertFalse(ProjectionEngine.requestsSpousalBeneficiaryIraRmd(plan));
            assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, refusal(plan).reason());
        }
    }

    public static RetirementPlan fixture(String inheritedBalance) {
        return select(SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2034, inheritedBalance, "123000", false));
    }

    public static RetirementPlan select(RetirementPlan plan) {
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(old.withEconomicAssumptions(old.getEconomicAssumptions().withSpousalInheritance(
                new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY))));
        return plan;
    }

    private static void changeHorizon(RetirementPlan plan, int years) {
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(), years, old.getProjectionStartDate(),
                old.getStateTaxAssumptions()));
    }

    private static RetirementPlan withAccounts(RetirementPlan base, List<Account> accounts) {
        var plan = new RetirementPlan(base.getHousehold(), new AccountPortfolio(accounts), base.getPlanningAssumptions());
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        return plan;
    }

    private static Projection applied(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                new ProjectionEngine().projectSpousalBeneficiaryIraRmd(plan)).projection();
    }

    private static SpousalIraRmdUnavailable refusal(RetirementPlan plan) {
        return assertThrows(SpousalIraRmdUnavailable.class, () -> applied(plan));
    }

    private static List<IncomeSourceAttribution> beneficiaryReceipts(ProjectionYear year) {
        return year.getIncomeSources().stream().filter(s -> s.successorRmd().map(authorization -> authorization.treatment()
                == RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_BENEFICIARY_IRA_SINGLE_LIFE_V1).orElse(false)).toList();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
