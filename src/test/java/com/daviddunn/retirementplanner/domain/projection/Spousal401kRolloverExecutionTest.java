package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;
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

public class Spousal401kRolloverExecutionTest {

    @Test void youngerSurvivorDirectRolloverKeepsOnePositionAndCreatesNoIncomeOrCash() {
        var plan = fixture(PRIMARY_DIES, 2034);
        assertFalse(ProjectionEngine.requestsSpousalIraRmd(plan));
        assertTrue(ProjectionEngine.requestsSpousal401kRollover(plan));
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        var original = IncomeSourceAttribution.AccountReference.capture(source);
        var projection = applied(plan);
        var rollover = projection.getYearAt(2);
        assertEquals(1, rollover.spousal401kRollovers().size());
        var event = rollover.spousal401kRollovers().getFirst();
        assertEquals(original, event.source());
        assertEquals(SPOUSE, event.survivor());
        assertEquals(LocalDate.of(2035, 1, 1), event.effectiveDate());
        money("246000", event.eligibleTransferredBalance());
        for (int index : List.of(0, 1, 2, 3)) {
            var year = projection.getYearAt(index);
            money("0", year.getRequiredMinimumDistribution());
            money("0", year.getAdjustedGrossIncome());
            money("0", year.getHouseholdCashSettlement().projectedPeriodRmdCash());
            money("246000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
            assertEquals(4, year.getEndingAccountSnapshots().size());
            assertSame(source, year.getEndingAccountSnapshots().getFirst().getAccount());
            assertEquals(AccountType.TRADITIONAL_401K, source.getType());
            assertTrue(rmds(year).isEmpty());
            assertEquals(index == 2, !year.spousal401kRollovers().isEmpty());
        }
        money("2434.80", rollover.getPortfolioWithdrawal());
        var year = projection.getYearAt(4);
        money("15000", year.getRequiredMinimumDistribution());
        money("15000", year.getRmdDistributedInProjection());
        money("15000", year.getAdjustedGrossIncome());
        money("15000", year.getModifiedAdjustedGrossIncome());
        money("2434.80", year.getAnnualMedicarePremium());
        money("12565.20", year.getEndingRetainedNonQualifiedAssets());
        money("231000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("123000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("365891.20", year.getAfterTaxEstateValue());
        var receipt = rmds(year).getFirst();
        assertEquals(1, rmds(year).size());
        assertEquals(original, receipt.account().orElseThrow());
        assertEquals(SPOUSE, receipt.owner().orElseThrow());
        assertEquals(RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_401K_DIRECT_ROLLOVER_V1,
                receipt.successorRmd().orElseThrow().treatment());
        money("15000", receipt.modeledTaxableAmount().orElseThrow());
        money("246000", source.getCurrentBalance());
    }

    @Test void olderSurvivorMayPayOwnRmdFromRolloverIraButItsBalanceCountsTheNextYear() {
        var projection = applied(fixture(SPOUSE_DIES, 2035));
        // 2035: $246,000 / 24.6. 2036: only the existing IRA's $236,000 / 23.7.
        money("10000", projection.getYearAt(2).getRequiredMinimumDistribution());
        var rolloverYear = projection.getYearAt(3);
        money("123000", rolloverYear.spousal401kRollovers().getFirst().eligibleTransferredBalance());
        money("9957.81", rolloverYear.getRequiredMinimumDistribution());
        money("9957.81", rolloverYear.getAdjustedGrossIncome());
        var operative = rmds(rolloverYear).getFirst();
        assertEquals(1, rmds(rolloverYear).size());
        assertEquals(PRIMARY, operative.owner().orElseThrow());
        assertEquals(SPOUSE, operative.account().orElseThrow().owner());
        money("113042.19", rolloverYear.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("236000", rolloverYear.getEndingAccountSnapshots().get(1).getEndingBalance());
        var later = projection.getYearAt(4);
        // 2037: ($113,042.19 + $236,000) / 22.9.
        money("15242.02", later.getRequiredMinimumDistribution());
        money("15242.02", later.getAdjustedGrossIncome());
        money("97800.17", later.getEndingAccountSnapshots().getFirst().getEndingBalance());
        assertEquals(1, rmds(later).size());
        assertTrue(later.spousal401kRollovers().isEmpty());
    }

    @Test void equalCommencementYearRequiresEmployerBeneficiaryAmountBeforeRollover() {
        var failure = assertThrows(SpousalIraRmdUnavailable.class, () -> applied(fixture(SPOUSE_DIES, 2036)));
        assertEquals(UNRESOLVED_MANDATORY_DISTRIBUTION, failure.reason());
        assertEquals(2037, failure.year());
        assertTrue(failure.getMessage().contains("beneficiary distribution"));
        var requestedForward = fixture(PRIMARY_DIES, 2034);
        setParticipantBirth(requestedForward, 1960);
        var forwardBoundary = assertThrows(SpousalIraRmdUnavailable.class, () -> applied(requestedForward));
        assertEquals(UNRESOLVED_MANDATORY_DISTRIBUTION, forwardBoundary.reason());
        assertEquals(2035, forwardBoundary.year());
        var later = assertThrows(SpousalIraRmdUnavailable.class, () -> applied(fixture(PRIMARY_DIES, 2036)));
        assertEquals(UNRESOLVED_MANDATORY_DISTRIBUTION, later.reason());
        assertEquals(2036, later.year());
    }

    @Test void multipleEmployerSourcesAndInheritedIraSplitOneOrderedOwnerRequirement() {
        var plan = fixture(PRIMARY_DIES, 2034);
        var original = plan.getAccountPortfolio().getAccounts();
        var sources = new ArrayList<Account>(List.of(
                new Traditional401K("First employer plan", PRIMARY, new BigDecimal("6000")),
                new Traditional401K("Second employer plan", PRIMARY, new BigDecimal("4000")),
                new TraditionalIRA("Original primary IRA", PRIMARY, new BigDecimal("3000")),
                new TraditionalIRA("Original spouse IRA", SPOUSE, new BigDecimal("356000"))));
        sources.addAll(original.subList(2, original.size()));
        sources.add(new Traditional401K("Living employer plan", SPOUSE, new BigDecimal("24600")));
        sources.add(new Traditional401K("Empty employer plan", PRIMARY, BigDecimal.ZERO));
        var mixed = withAccounts(plan, sources);
        var projection = applied(mixed);
        assertEquals(2, projection.getYearAt(2).spousal401kRollovers().size());
        var year = projection.getYearAt(4);
        money("16000", year.getRequiredMinimumDistribution());
        var receipts = rmds(year);
        assertEquals(5, receipts.size());
        assertTrue(receipts.stream().allMatch(receipt -> receipt.owner().orElseThrow() == SPOUSE));
        for (int i = 0; i < 4; i++) {
            assertEquals(IncomeSourceAttribution.AccountReference.capture(sources.get(i)), receipts.get(i).account().orElseThrow());
            money(List.of("6000", "4000", "3000", "2000").get(i), receipts.get(i).grossAmount());
        }
        money("1000", receipts.getLast().grossAmount());
        assertTrue(receipts.getLast().successorRmd().isEmpty());
        assertEquals(AccountType.TRADITIONAL_401K, receipts.getLast().account().orElseThrow().type());
        money("16000", year.getAdjustedGrossIncome());

        sources.addFirst(sources.remove(3));
        var reversed = applied(withAccounts(plan, sources)).getYearAt(4);
        assertEquals(2, rmds(reversed).size());
        money("15000", rmds(reversed).getFirst().grossAmount());
        assertTrue(rmds(reversed).getFirst().successorRmd().isEmpty());
    }

    @Test void taxableRolledPositionRmdFeedsSocialSecurityMedicareFundingAndEstateOnce() {
        var plan = fixture(PRIMARY_DIES, 2034);
        var spouse = plan.getHousehold().getSpouse();
        var start = LocalDate.of(2037, 1, 1);
        spouse.replaceIncomeSource(spouse.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Spouse benefit", SPOUSE, spouse.getBirthDate().plusYears(67),
                        null, new BigDecimal("2000"), 67, BigDecimal.ZERO, 2033));
        spouse.addIncomeSource(new Pension("Spouse pension", SPOUSE, start, null, new BigDecimal("2000"), BigDecimal.ZERO));
        plan.getHousehold().addExpense(new Expense("One-time spending", new BigDecimal("59200"), GrowthCategory.GENERAL,
                start, start, ExpenseType.ONE_TIME));
        var year = applied(plan).getYearAt(4);
        // $51,000 Single provisional income under fixture rules, $18,950 taxable SS, $4,774 federal tax.
        money("18950", year.getTaxableSocialSecurity());
        money("57950", year.getAdjustedGrossIncome());
        money("57950", year.getModifiedAdjustedGrossIncome());
        money("4774", year.getFederalIncomeTax());
        money("2434.80", year.getAnnualMedicarePremium());
        money("3408.80", year.getTaxFundingWithdrawal());
        money("92417.20", year.getEndingRetainedNonQualifiedAssets());
        money("457917.20", year.getAfterTaxEstateValue());
        money("231000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
    }

    @Test void onlyLivingOriginalSourcesCanExecuteFixedAndTargetedRoth() {
        for (var strategy : List.of(RothConversionStrategy.FIXED_AMOUNT, RothConversionStrategy.FILL_12_PERCENT_BRACKET)) {
            var plan = fixture(PRIMARY_DIES, 2034);
            plan.getAccountPortfolio().getAccounts().get(1).setCurrentBalance(new BigDecimal("30000"));
            plan.getAccountPortfolio().addAccount(new RothIRA("Original primary Roth", PRIMARY, BigDecimal.ZERO));
            plan.setRothConversionRequest(new RothConversionRequest(true, 2035, new BigDecimal("1000"),
                    RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME));
            var year = applied(plan).getYearAt(2);
            boolean targeted = strategy == RothConversionStrategy.FILL_12_PERCENT_BRACKET;
            money(targeted ? "30000" : "1000", year.getRothConversion());
            money(targeted ? "1420" : "0", year.getTaxFundingWithdrawal());
            money("0", year.getPrimaryRothConversion());
            money("246000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        }
    }

    @Test void unsupportedSourcesOpeningHistoryAndDiscretionaryDrawsRefuse() {
        var unsupported = fixture(PRIMARY_DIES, 2034);
        unsupported.getAccountPortfolio().addAccount(new Roth401K("Designated Roth balance", PRIMARY, BigDecimal.ONE));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, assertThrows(SpousalIraRmdUnavailable.class, () -> applied(unsupported)).reason());
        var inherited = fixture(PRIMARY_DIES, 2034);
        inherited.getAccountPortfolio().addAccount(new InheritedTraditionalIRA("Inherited balance", PRIMARY, BigDecimal.ONE,
                new InheritedAccountInformation(LocalDate.of(1950, 1, 1), LocalDate.of(2026, 1, 1),
                        BeneficiaryRelationship.CHILD)));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, assertThrows(SpousalIraRmdUnavailable.class, () -> applied(inherited)).reason());
        var history = fixture(PRIMARY_DIES, 2034);
        history.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2033, new BigDecimal("246000"), BigDecimal.ONE));
        assertEquals(INCOMPATIBLE_ACCOUNT_HISTORY, assertThrows(SpousalIraRmdUnavailable.class, () -> applied(history)).reason());
        var facts = fixture(PRIMARY_DIES, 2034);
        facts.getAccountPortfolio().getAccounts().getFirst().setRetirementParticipantFacts(new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2034, 6, 1),
                RetirementParticipantFacts.RetirementConditionsStatus.UNKNOWN, null));
        assertEquals(INCOMPATIBLE_ACCOUNT_HISTORY, assertThrows(SpousalIraRmdUnavailable.class, () -> applied(facts)).reason());
        var draw = fixture(PRIMARY_DIES, 2034);
        var start = LocalDate.of(2035, 1, 1);
        draw.getHousehold().addExpense(new Expense("Unsupported spending", new BigDecimal("101000"), GrowthCategory.GENERAL,
                start, start, ExpenseType.ONE_TIME));
        assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, assertThrows(SpousalIraRmdUnavailable.class, () -> applied(draw)).reason());
    }

    @Test void rolloverCannotBindToAbsentMismatchedOrDuplicatedPriorPositions() throws Exception {
        var plan = fixture(PRIMARY_DIES, 2034);
        var opening = ProjectedPortfolio.from(plan.getAccountPortfolio());
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var policy = new SpousalIraRmdPolicy(plan, rules.getRmdRules(), true);
        var date = LocalDate.of(2034, 12, 31);
        var position = opening.getAccountBalances().getFirst();
        var missing = new RmdBalanceSnapshot(date, List.of());
        var duplicate = new RmdBalanceSnapshot(date, List.of(position, position));
        var wrongDate = RmdBalanceSnapshot.from(date.minusDays(1), opening);
        for (var invalid : List.of(missing, duplicate, wrongDate)) {
            assertEquals(MISSING_OR_DUPLICATE_SOURCE,
                    assertThrows(SpousalIraRmdUnavailable.class, () -> policy.rolloversAtOpening(2035, opening, invalid)).reason());
        }
        var mismatch = new RmdBalanceSnapshot(date, List.of(new ProjectedAccountBalance(position.getAccount(), BigDecimal.ZERO)));
        assertEquals(INCOMPATIBLE_ACCOUNT_HISTORY,
                assertThrows(SpousalIraRmdUnavailable.class, () -> policy.rolloversAtOpening(2035, opening, mismatch)).reason());
    }

    @Test void verifiedRolloverMembershipUsesExistingIraShortfallWithoutAnotherEmployerDebit() throws Exception {
        var plan = fixture(PRIMARY_DIES, 2034);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var policy = new SpousalIraRmdPolicy(plan, rules.getRmdRules(), true);
        var opening = ProjectedPortfolio.from(plan.getAccountPortfolio());
        var positions = plan.getAccountPortfolio().getAccounts();
        var source = IncomeSourceAttribution.AccountReference.capture(positions.getFirst());
        var events = policy.rolloversAtOpening(2035, opening, RmdBalanceSnapshot.from(LocalDate.of(2034, 12, 31), opening));
        var before = com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver.resolve(
                java.time.Year.of(2033), LocalDate.of(2033, 1, 1),
                EffectiveHouseholdDeathView.resolve(plan.getHousehold(), plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                        java.util.Optional.empty()),
                java.util.Map.of(PRIMARY, LocalDate.of(1961, 6, 15), SPOUSE, LocalDate.of(1962, 6, 15)), rules.getRmdRules(),
                List.of(source), List.of()).accounts().get(source);
        assertEquals(RmdAccountCategory.TRADITIONAL_401K, before.effectiveRmdCategory());
        var after = policy.ownership(2035, events).accounts().get(source);
        assertEquals(RmdAccountCategory.IRA, after.effectiveRmdCategory());
        assertEquals(SPOUSE, after.effectiveRmdOwner().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> IncomeSourceAttribution.withdrawal(
                positions.getFirst(), BigDecimal.ONE, IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW)
                .inYear(2036).forSuccessorRmd(after));
        var prior = RmdBalanceSnapshot.from(LocalDate.of(2036, 12, 31), opening);
        var available = opening.withWithdrawal(positions.get(0), new BigDecimal("240000"))
                .withWithdrawal(positions.get(1), new BigDecimal("114001"));
        var membership = policy.membership(2037, available, prior, events);
        var obligation = new HouseholdRmdCalculator().calculate(plan, prior, 2037, rules, Set.of(SPOUSE), membership);
        var failure = assertThrows(IllegalStateException.class,
                () -> new ProjectedWithdrawalAllocator().allocateHouseholdRmds(available, obligation, membership));
        var constraint = assertInstanceOf(FundingConstraint.class, failure.getCause());
        assertEquals(FundingFailure.Stage.IRA_RMD, constraint.stage);
        money("15000", constraint.required);
        money("14999", constraint.available);
    }

    @Test void unsupportedStateOrAnalyzerDoesNotApplyThePolicy() {
        var plan = fixture(PRIMARY_DIES, 2034);
        assertTrue(new ProjectionEngine().project(plan).getYearAt(2).spousal401kRollovers().isEmpty());
        var assumptions = plan.getPlanningAssumptions();
        for (var state : List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.AR)) {
            plan.setPlanningAssumptions(assumptions.withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
            assertFalse(ProjectionEngine.requestsSpousal401kRollover(plan));
            assertEquals(UNSUPPORTED_POLICY_OR_ACCOUNT, assertThrows(SpousalIraRmdUnavailable.class, () -> applied(plan)).reason());
        }
    }

    public static RetirementPlan fixture(DeathScenario death, int deathYear) {
        boolean reverse = death == SPOUSE_DIES;
        var plan = SpousalIraRmdExecutionTest.fixture(death, deathYear, "246000", "123000", reverse);
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        plan.getAccountPortfolio().replaceAccount(source, new Traditional401K("Original employer plan",
                source.getOwnership(), source.getCurrentBalance()));
        // A 1960 participant dying in 2034 has a plan beneficiary obligation in the 2035 rollover year.
        if (!reverse) setParticipantBirth(plan, 1961);
        return plan;
    }

    private static void setParticipantBirth(RetirementPlan plan, int birthYear) {
        var person = plan.getHousehold().getPrimaryPerson();
        person.setBirthDate(LocalDate.of(birthYear, 6, 15));
        person.replaceIncomeSource(person.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Own benefit", PRIMARY, person.getBirthDate().plusYears(70),
                        null, BigDecimal.ZERO, 70, BigDecimal.ZERO, 2033));
    }

    private static RetirementPlan withAccounts(RetirementPlan base, List<Account> accounts) {
        var plan = new RetirementPlan(base.getHousehold(), new AccountPortfolio(accounts), base.getPlanningAssumptions());
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        return plan;
    }

    private static Projection applied(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class, new ProjectionEngine().projectSpousal401kRollover(plan)).projection();
    }

    private static List<IncomeSourceAttribution> rmds(ProjectionYear year) {
        return year.getIncomeSources().stream().filter(receipt -> receipt.kind() == IncomeSourceAttribution.Kind.RMD).toList();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
