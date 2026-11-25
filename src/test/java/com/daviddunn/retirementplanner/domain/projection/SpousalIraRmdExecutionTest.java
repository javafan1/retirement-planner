package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.model.DeathScenario.*;
import static org.junit.jupiter.api.Assertions.*;

public class SpousalIraRmdExecutionTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @ParameterizedTest
    @CsvSource({"123000,15000.00,231000.00", "120000,14878.05,231121.95"})
    void youngerSurvivorBeginsAtActualAge75(String spouseBalance, String required, String primaryEnding) {
        var plan = fixture(PRIMARY_DIES, 2034, "246000", spouseBalance, false);
        var projection = applied(plan);
        for (int index : List.of(1, 2, 3)) {
            var year = projection.getYearAt(index);
            money("0", year.getRmdDistributedInProjection());
            assertTrue(rmdSources(year).isEmpty());
            money("0", year.getAdjustedGrossIncome());
        }
        var year = projection.getYearAt(4);
        money(required, year.getRequiredMinimumDistribution());
        money(required, year.getRmdDistributedInProjection());
        money(required, year.getAdjustedGrossIncome());
        money(required, year.getModifiedAdjustedGrossIncome());
        money("0", year.getFederalIncomeTax());
        money("2434.80", year.getAnnualMedicarePremium());
        money(primaryEnding, year.getEndingAccountSnapshots().get(0).getEndingBalance());
        money(spouseBalance, year.getEndingAccountSnapshots().get(1).getEndingBalance());
        var source = rmdSources(year).getFirst();
        assertEquals(1, rmdSources(year).size());
        assertEquals(SPOUSE, source.owner().orElseThrow());
        assertEquals(IncomeSourceAttribution.AccountReference.capture(plan.getAccountPortfolio().getAccounts().getFirst()),
                source.account().orElseThrow());
        assertEquals(PRIMARY, source.account().orElseThrow().owner());
        assertEquals(2037, source.taxYear().orElseThrow());
        money(required, source.grossAmount());
        money(required, source.modeledTaxableAmount().orElseThrow());
        assertTrue(source.successorRmd().isPresent());
        if (spouseBalance.equals("123000")) {
            money("12565.20", year.getHouseholdCashSettlement().retainedHouseholdSurplus());
            // Six person-years of flat Medicare at $2,434.80; no other expense or tax.
            money("454391.20", year.getEndingInvestableAssets());
            money("365891.20", year.getAfterTaxEstateValue());
        }
        money("246000", plan.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
    }

    @Test void olderSurvivorAlreadyTakingRmdUsesYoungerOriginalAccountWhenFirst() {
        var plan = fixture(SPOUSE_DIES, 2036, "246000", "123000", true);
        var projection = applied(plan);
        money("10000.00", projection.getYearAt(2).getRmdDistributedInProjection());
        money("9957.81", projection.getYearAt(3).getRmdDistributedInProjection());
        var year = projection.getYearAt(4);
        money("15242.02", year.getRmdDistributedInProjection());
        money("15242.02", year.getAdjustedGrossIncome());
        money("107757.98", year.getEndingAccountSnapshots().get(0).getEndingBalance());
        money("226042.19", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        var source = rmdSources(year).getFirst();
        assertEquals(PRIMARY, source.owner().orElseThrow());
        assertEquals(SPOUSE, source.account().orElseThrow().owner());
        assertEquals(1, rmdSources(year).size());
    }

    @Test void splitAndReversedPriorityUseTheSameVerifiedGroup() {
        var split = applied(fixture(PRIMARY_DIES, 2034, "6000", "363000", false)).getYearAt(4);
        var debits = rmdSources(split);
        assertEquals(2, debits.size());
        money("6000", debits.get(0).grossAmount());
        money("9000", debits.get(1).grossAmount());
        assertEquals(List.of(PRIMARY, SPOUSE), debits.stream().map(d -> d.account().orElseThrow().owner()).toList());
        assertTrue(debits.stream().allMatch(d -> d.owner().orElseThrow() == SPOUSE));
        money("0", split.getEndingAccountSnapshots().get(0).getEndingBalance());
        money("354000", split.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("15000", split.getAdjustedGrossIncome());

        var reversed = applied(fixture(PRIMARY_DIES, 2034, "246000", "123000", true)).getYearAt(4);
        var onlyDebit = rmdSources(reversed).getFirst();
        assertEquals(SPOUSE, onlyDebit.account().orElseThrow().owner());
        assertTrue(onlyDebit.successorRmd().isEmpty());
        money("108000", reversed.getEndingAccountSnapshots().getFirst().getEndingBalance());
    }

    @Test void membershipDistinguishesMissingDuplicateAndZeroBalancesFromRealShortfalls() throws Exception {
        var plan = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var policy = new SpousalIraRmdPolicy(plan, rules.getRmdRules());
        var opening = ProjectedPortfolio.from(plan.getAccountPortfolio());
        var date = LocalDate.of(2036, 12, 31);
        var original = plan.getAccountPortfolio().getAccounts().getFirst();
        var spouse = plan.getAccountPortfolio().getAccounts().get(1);
        var zero = new RmdBalanceSnapshot(date, List.of(new ProjectedAccountBalance(original, ZERO),
                new ProjectedAccountBalance(spouse, ZERO)));
        var calculator = new HouseholdRmdCalculator();
        var allocator = new ProjectedWithdrawalAllocator();
        money("0", calculator.calculate(plan, zero, 2037, rules, Set.of(SPOUSE),
                policy.membership(2037, opening, zero)).getTotalRmd());
        for (var invalid : List.of(new RmdBalanceSnapshot(date, List.of(new ProjectedAccountBalance(original, ZERO))),
                new RmdBalanceSnapshot(date, List.of(new ProjectedAccountBalance(original, ZERO),
                        new ProjectedAccountBalance(original, ZERO), new ProjectedAccountBalance(spouse, ZERO))),
                new RmdBalanceSnapshot(date.minusDays(1), zero.getAccountBalances()))) {
            assertEquals(SpousalIraRmdUnavailable.Reason.MISSING_OR_DUPLICATE_SOURCE,
                    assertThrows(SpousalIraRmdUnavailable.class, () -> policy.membership(2037, opening, invalid)).reason());
        }

        var prior = RmdBalanceSnapshot.from(date, opening);
        var actual = opening.withWithdrawal(original, new BigDecimal("240000"))
                .withWithdrawal(spouse, new BigDecimal("114000"));
        var shared = policy.membership(2037, actual, prior);
        var obligation = calculator.calculate(plan, prior, 2037, rules, Set.of(SPOUSE), shared);
        var allocated = allocator.allocateHouseholdRmds(actual, obligation, shared);
        money("6000", allocated.getWithdrawalBreakdown().getIncomeSources().getFirst().grossAmount());
        money("9000", allocated.getWithdrawalBreakdown().getIncomeSources().getLast().grossAmount());
        money("0", allocated.getPortfolio().getBalance(original));
        money("0", allocated.getPortfolio().getBalance(spouse));
        var underfunded = actual.withWithdrawal(spouse, new BigDecimal("1"));
        var failure = assertThrows(IllegalStateException.class,
                () -> allocator.allocateHouseholdRmds(underfunded, obligation, shared));
        var constraint = assertInstanceOf(FundingConstraint.class, failure.getCause());
        assertEquals(FundingFailure.Stage.IRA_RMD, constraint.stage);
        money("15000", constraint.required);
        money("14999", constraint.available);
        money("6000", underfunded.getBalance(original));
    }

    @Test void fixedAndTargetedConversionsAreBoundToLivingOriginalPositions() {
        var engine = new ProjectionEngine();
        for (var strategy : List.of(RothConversionStrategy.FIXED_AMOUNT, RothConversionStrategy.FILL_12_PERCENT_BRACKET)) {
            boolean targeted = strategy == RothConversionStrategy.FILL_12_PERCENT_BRACKET;
            String amount = targeted ? "30000" : "1000";
            var plan = fixture(PRIMARY_DIES, 2034, "246000", amount, false);
            plan.getAccountPortfolio().addAccount(new RothIRA("Original primary Roth", PRIMARY, ZERO));
            plan.setRothConversionRequest(new RothConversionRequest(true, 2035, new BigDecimal("1000"),
                    RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME));
            var year = assertInstanceOf(ProjectionExecutionResult.Completed.class, engine.projectSpousalIraRmd(plan))
                    .projection().getYearAt(2);
            money(amount, year.getRequestedRothConversion());
            money(amount, year.getRothConversion());
            money("0", year.getPrimaryRothConversion());
            money(amount, year.spouseRothConversion().orElseThrow());
            money(amount, year.getAdjustedGrossIncome());
            money(targeted ? "1420" : "0", year.getFederalIncomeTax());
            money(targeted ? "1420" : "0", year.getTaxFundingWithdrawal());
            assertTrue(year.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION)
                    .allMatch(s -> s.account().orElseThrow().owner() == SPOUSE && s.destination().orElseThrow().owner() == SPOUSE));
        }
    }

    @Test void existingFirstRmdLatchRespondsToActualSurvivorObligation() {
        var plan = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2034, new BigDecimal("1000"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        var projection = applied(plan);
        for (int index : List.of(1, 2, 3)) money("1000", projection.getYearAt(index).getRothConversion());
        money("14878.05", projection.getYearAt(4).getRmdDistributedInProjection());
        money("0", projection.getYearAt(4).getRothConversion());
    }

    @Test void taxableRmdFlowsOnceThroughSocialSecurityMedicareAndCashFunding() {
        var plan = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        var spouse = plan.getHousehold().getSpouse();
        var yearStart = LocalDate.of(2037, 1, 1);
        spouse.replaceIncomeSource(spouse.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Spouse benefit", SPOUSE, spouse.getBirthDate().plusYears(67),
                        null, new BigDecimal("2000"), 67, ZERO, 2033));
        spouse.addIncomeSource(new Pension("Spouse pension", SPOUSE, yearStart, null, new BigDecimal("2000"), ZERO));
        plan.getHousehold().addExpense(new Expense("One-time spending", new BigDecimal("59200"), GrowthCategory.GENERAL,
                yearStart, yearStart, ExpenseType.ONE_TIME));
        var projection = applied(plan);
        money("12000", projection.getYearAt(0).getSocialSecurityResult().primarySelectedBenefit());
        var year = projection.getYearAt(4);
        // Single 2026 rules, no inflation. $51,000 provisional income; $4,500 + .85($51,000 - $34,000).
        money("15000", year.getRmdDistributedInProjection());
        money("18950", year.getTaxableSocialSecurity());
        money("57950", year.getAdjustedGrossIncome());
        money("57950", year.getModifiedAdjustedGrossIncome());
        money("16100", year.getFederalStandardDeduction());
        money("41850", year.getFederalTaxableIncome());
        money("4774", year.getFederalIncomeTax());
        money("2434.80", year.getAnnualMedicarePremium());
        money("3408.80", year.getTaxFundingWithdrawal());
        money("0", year.getHouseholdCashSettlement().retainedHouseholdSurplus());
        money("231000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        // Include the primary's $12,000 spousal top-up in 2033 while both spouses are alive.
        money("92417.20", year.getEndingRetainedNonQualifiedAssets());
        money("457917.20", year.getAfterTaxEstateValue());
    }

    @Test void legacyAndUnadmittedInputsKeepTheirExistingRoutes() {
        var engine = new ProjectionEngine();
        var plan = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        money("10000", engine.project(plan).getYearAt(2).getRmdDistributedInProjection());
        var alive = fixture(BOTH_SURVIVE, 0, "246000", "123000", false);
        assertFalse(ProjectionEngine.requestsSpousalIraRmd(alive));
        money("10000", engine.project(alive).getYearAt(2).getRmdDistributedInProjection());
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(assumptions.withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.MI)));
        assertFalse(ProjectionEngine.requestsSpousalIraRmd(plan));
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                assertThrows(SpousalIraRmdUnavailable.class, () -> engine.projectSpousalIraRmd(plan)).reason());
        plan.setPlanningAssumptions(assumptions.withEconomicAssumptions(assumptions.getEconomicAssumptions()
                .withSpousalInheritance(new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY))));
        assertFalse(ProjectionEngine.requestsSpousalIraRmd(plan));
        money("10000", engine.project(plan).getYearAt(2).getRmdDistributedInProjection());
    }

    @Test void unresolvedObligationsAndContradictoryHistoryRefuseInsteadOfReturningZero() {
        var boundary = fixture(PRIMARY_DIES, 2035, "246000", "123000", false);
        assertEquals(SpousalIraRmdUnavailable.Reason.UNRESOLVED_MANDATORY_DISTRIBUTION,
                assertThrows(SpousalIraRmdUnavailable.class, () -> applied(boundary)).reason());
        var employer = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        employer.getAccountPortfolio().addAccount(new Traditional401K("Deceased workplace plan", PRIMARY, new BigDecimal("1")));
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                assertThrows(SpousalIraRmdUnavailable.class, () -> applied(employer)).reason());
        var incompatible = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        incompatible.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2033, new BigDecimal("246000"), new BigDecimal("1")));
        assertEquals(SpousalIraRmdUnavailable.Reason.INCOMPATIBLE_ACCOUNT_HISTORY,
                assertThrows(SpousalIraRmdUnavailable.class, () -> applied(incompatible)).reason());
        var discretionary = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        var deathDate = LocalDate.of(2034, 1, 1);
        discretionary.getHousehold().addExpense(new Expense("Spending above reserve", new BigDecimal("100001"),
                GrowthCategory.GENERAL, deathDate, deathDate, ExpenseType.ONE_TIME));
        var refusal = assertThrows(SpousalIraRmdUnavailable.class, () -> applied(discretionary));
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT, refusal.reason());
        assertEquals(2034, refusal.year());
        var independent = fixture(PRIMARY_DIES, 2034, "246000", "123000", false);
        independent.getAccountPortfolio().addAccount(new Traditional401K("Survivor workplace plan", SPOUSE, new BigDecimal("24600")));
        var year = applied(independent).getYearAt(4);
        money("16000", year.getRmdDistributedInProjection());
        money("1000", rmdSources(year).getLast().grossAmount());
        assertTrue(rmdSources(year).getLast().successorRmd().isEmpty());
    }

    public static RetirementPlan fixture(DeathScenario death, int deathYear, String primaryBalance,
                                         String spouseBalance, boolean spouseFirst) {
        var start = LocalDate.of(2033, 1, 1);
        var base = ArkansasLifePrincipalProjectionTest.fixture(start, 5, death, deathYear, "0");
        var assumptions = base.getPlanningAssumptions();
        var portfolio = new AccountPortfolio();
        var primary = new TraditionalIRA("Original primary IRA", PRIMARY, new BigDecimal(primaryBalance));
        var spouse = new TraditionalIRA("Original spouse IRA", SPOUSE, new BigDecimal(spouseBalance));
        portfolio.addAccount(spouseFirst ? spouse : primary);
        portfolio.addAccount(spouseFirst ? primary : spouse);
        portfolio.addAccount(new RothIRA("Original spouse Roth", SPOUSE, ZERO));
        portfolio.addAccount(new CheckingAccount("Household reserve", JOINT, new BigDecimal("100000")));
        var plan = new RetirementPlan(base.getHousehold(), portfolio, assumptions
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL))
                .withEconomicAssumptions(assumptions.getEconomicAssumptions().withSpousalInheritance(
                        new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER))));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        for (AccountOwnership owner : List.of(PRIMARY, SPOUSE)) {
            var person = plan.getHousehold().peopleByOwner().get(owner);
            person.setBirthDate(LocalDate.of(owner == PRIMARY ? 1960 : 1962, 6, 15));
            person.addIncomeSource(new SocialSecurityIncome("Own benefit", owner, person.getBirthDate().plusYears(70),
                    null, ZERO, 70, ZERO, 2033));
        }
        return plan;
    }

    private static Projection applied(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class, new ProjectionEngine().projectSpousalIraRmd(plan)).projection();
    }

    private static List<IncomeSourceAttribution> rmdSources(ProjectionYear year) {
        return year.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.RMD).toList();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
