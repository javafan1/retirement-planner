package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class SpousalIraDeathYearExecutionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deathYearAndPostRbdOrdersReconcile(boolean primaryDies) {
        for (boolean beneficiary : new boolean[]{false, true}) {
            var plan = opening(primaryDies, beneficiary, 2040, "808000", "246000");
            var projection = run(plan);
            var death = projection.getYearAt(0);
            var survivor = primaryDies ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
            money("50000", death.getRequiredMinimumDistribution());
            money("50000", death.getAdjustedGrossIncome());
            money("1780", death.getFederalIncomeTax());
            var receipt = death.getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).findFirst().orElseThrow();
            assertEquals(survivor, receipt.owner().orElseThrow());
            assertNotEquals(survivor, receipt.account().orElseThrow().owner());
            money("40000", receipt.grossAmount());
            money("768000", death.getEndingAccountSnapshots().getFirst().getEndingBalance());
            money("236000", death.getEndingAccountSnapshots().get(1).getEndingBalance());
            var next = projection.getYearAt(1);
            money(beneficiary ? "64425.90" : "42362.87", next.getRequiredMinimumDistribution());
            if (beneficiary) {
                money("14.1", next.beneficiaryIraRmd().divisor());
                money("54468.09", next.beneficiaryIraRmd().requiredAmount());
            } else {
                assertNull(next.beneficiaryIraRmd());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void olderSurvivorUsesDecedentRemainingFactor(boolean primaryDies) {
        var plan = opening(primaryDies, true, 2042, "458000", "0", 1965, 1960);
        var projection = run(plan);
        money("20000", projection.getYearAt(0).getRequiredMinimumDistribution());
        money("438000", projection.getYearAt(0).getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("12.3", projection.getYearAt(1).beneficiaryIraRmd().divisor());
        money("35609.76", projection.getYearAt(1).beneficiaryIraRmd().requiredAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = {2035, 2036})
    void januaryDeathBeforeAprilRbdHasNoDecedentMinimum(int year) {
        var plan = opening(true, true, year, "246000", "0");
        var projection = run(plan);
        money("0", projection.getYearAt(0).getRequiredMinimumDistribution());
        assertEquals(year + 1, projection.getYearAt(1).beneficiaryIraRmd().firstDistributionYear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong year", "paid", "uncleared", "incompatible balance"})
    void incompleteOpeningEvidenceRefuses(String defect) {
        var plan = opening(true, false, 2040, "808000", "246000");
        var account = plan.getAccountPortfolio().getAccounts().getFirst();
        account.setOpeningRmdAccountData(switch (defect) {
            case "missing" -> null;
            case "wrong year" -> new OpeningRmdAccountData(2039, new BigDecimal("808000"), BigDecimal.ZERO, true);
            case "paid" -> new OpeningRmdAccountData(2040, new BigDecimal("808000"), BigDecimal.ONE, true);
            case "uncleared" -> new OpeningRmdAccountData(2040, new BigDecimal("808000"), BigDecimal.ZERO);
            default -> new OpeningRmdAccountData(2040, new BigDecimal("808001"), BigDecimal.ZERO, true);
        });
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(plan));
    }

    @Test
    void firstLivingYearPaymentDoesNotRepeatBeforeAprilRbd() {
        var plan = opening(true, true, 2036, "246000", "0");
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(), 3, LocalDate.of(2035, 1, 1),
                old.getStateTaxAssumptions()));
        var original = plan.getAccountPortfolio().getAccounts().getFirst();
        original.setOpeningRmdAccountData(new OpeningRmdAccountData(2035, new BigDecimal("246000"), BigDecimal.ZERO));
        plan.getAccountPortfolio().getAccounts().get(1).setOpeningRmdAccountData(null);
        var projection = run(plan);
        money("10000", projection.getYearAt(0).getRequiredMinimumDistribution());
        assertTrue(projection.getYearAt(0).getIncomeSources().stream().noneMatch(row -> row.successorRmd().isPresent()));
        money("0", projection.getYearAt(1).getRequiredMinimumDistribution());
        money("236000", projection.getYearAt(1).getEndingAccountSnapshots().getFirst().getEndingBalance());
        assertEquals(2037, projection.getYearAt(2).beneficiaryIraRmd().firstDistributionYear());
    }

    @Test
    void preRbdDeathRefusesIfAnEarlierModeledMinimumWouldRequireTableTwo() {
        var plan = opening(true, true, 2036, "246000", "0", 1960, 1972);
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(), 3, LocalDate.of(2035, 1, 1),
                old.getStateTaxAssumptions()));
        var original = plan.getAccountPortfolio().getAccounts().getFirst();
        original.setOpeningRmdAccountData(new OpeningRmdAccountData(2035, new BigDecimal("246000"), BigDecimal.ZERO));
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                assertThrows(SpousalIraRmdUnavailable.class, () -> run(plan)).reason());
    }

    @Test
    void firstJanuaryAfterRbdTriggersExistingRothCutoffWithoutSurvivorMinimum() {
        var plan = opening(true, false, 2037, "229000", "246000", 1960, 1970);
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2037,
                new BigDecimal("1000"), com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
        var year = run(plan).getYearAt(0);
        money("10000", year.getRequiredMinimumDistribution());
        money("246000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("0", year.getRequestedRothConversion());
        money("0", year.getRothConversion());
    }

    @Test
    void mandatoryGroupDebitsTwoOriginalIrasAndDoesNotUseSurvivorIra() {
        var plan = opening(true, false, 2040, "20200", "246000");
        var second = new TraditionalIRA("Second original IRA", AccountOwnership.PRIMARY, new BigDecimal("787800"));
        second.setOpeningRmdAccountData(new OpeningRmdAccountData(2040, second.getCurrentBalance(), BigDecimal.ZERO, true));
        plan.getAccountPortfolio().addAccount(second);
        var year = run(plan).getYearAt(0);
        var receipts = year.getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).toList();
        assertEquals(2, receipts.size());
        money("20200", receipts.getFirst().grossAmount());
        money("19800", receipts.getLast().grossAmount());
        money("236000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("0", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("768000", year.getEndingAccountSnapshots().getLast().getEndingBalance());
    }

    @Test
    void successorSpendingAndFederalGrossUpUseThePreviouslyDebitedBalances() {
        var plan = opening(true, false, 2040, "808000", "246000");
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(BigDecimal.ZERO);
        expense(plan, 2040, "45785.20");
        expense(plan, 2041, "52000");
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2040,
                new BigDecimal("10000"), com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
        var projection = run(plan);
        money("0", projection.getYearAt(0).getEndingRetainedNonQualifiedAssets());
        money("0", projection.getYearAt(0).getRothConversion());
        var next = projection.getYearAt(1);
        var ordinary = next.getIncomeSources().stream().filter(row -> row.successorOrdinary().isPresent()).toList();
        money("12071.93", ordinary.stream().filter(row -> row.purpose() == IncomeSourceAttribution.Purpose.CASH_FLOW)
                .findFirst().orElseThrow().grossAmount());
        var funding = ordinary.stream().filter(row -> row.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING)
                .findFirst().orElseThrow().grossAmount();
        assertTrue(funding.subtract(new BigDecimal("4945.65")).abs().compareTo(new BigDecimal("0.02")) <= 0);
        assertTrue(next.getFederalIncomeTax().subtract(funding).abs().compareTo(new BigDecimal("0.02")) <= 0);
    }

    @Test
    void deathYearMedicareRepricesWhenLivingIraFundsTax() {
        var original = opening(true, true, 2040, "4040000", "246000");
        var reordered = new java.util.ArrayList<>(original.getAccountPortfolio().getAccounts());
        java.util.Collections.swap(reordered, 0, 1);
        reordered.getLast().setCurrentBalance(BigDecimal.ZERO);
        var plan = new RetirementPlan(original.getHousehold(),
                new com.daviddunn.retirementplanner.domain.financial.AccountPortfolio(reordered), original.getPlanningAssumptions());
        plan.setFinancialCalculationMode(original.getFinancialCalculationMode());
        expense(plan, 2040, "210000");
        var year = run(plan).getYearAt(0);
        money("210000", year.getRequiredMinimumDistribution());
        money("3583.20", year.getAnnualMedicarePremium());
        money(year.getAdjustedGrossIncome().toPlainString(), year.getModifiedAdjustedGrossIncome());
        assertEquals(1, year.getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).count());
        money("3840000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        // At the 24% bracket the fixed point is 35,932 + .24 × (funding − 30,016.80).
        assertTrue(year.getTaxFundingWithdrawal().subtract(new BigDecimal("37799.96")).abs()
                .compareTo(new BigDecimal("0.02")) <= 0, year.getTaxFundingWithdrawal()::toPlainString);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void employerOnlyExtensionAndJointLifeTablesRemainOutsideScope(boolean beneficiary) {
        var plan = opening(true, beneficiary, 2040, "808000", "0", 1960, 1972);
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                assertThrows(SpousalIraRmdUnavailable.class, () -> run(plan)).reason());
        var mixed = opening(true, beneficiary, 2040, "808000", "246000");
        mixed.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.Traditional401K(
                "Original employer account", AccountOwnership.PRIMARY, BigDecimal.ONE));
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(mixed));
        var otherState = opening(true, beneficiary, 2040, "808000", "246000");
        otherState.setPlanningAssumptions(otherState.getPlanningAssumptions()
                .withStateTaxAssumptions(com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.michigan()));
        assertFalse(ProjectionEngine.requestsSpousalIraDeathYear(otherState));
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(otherState));
    }

    @Test
    void duplicateOriginalSourceRefuses() {
        var duplicate = opening(true, false, 2040, "808000", "246000");
        duplicate.getAccountPortfolio().addAccount(duplicate.getAccountPortfolio().getAccounts().getFirst());
        assertEquals(SpousalIraRmdUnavailable.Reason.MISSING_OR_DUPLICATE_SOURCE,
                assertThrows(SpousalIraRmdUnavailable.class, () -> run(duplicate)).reason());

    }

    @Test
    void unavailableDeathYearDiscretionaryAccessAndOriginalGroupShortfallRefuse() {
        var expensePlan = opening(true, true, 2040, "808000", "0");
        expensePlan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(BigDecimal.ZERO);
        expense(expensePlan, 2040, "40001");
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                assertThrows(SpousalIraRmdUnavailable.class, () -> run(expensePlan)).reason());

        var marketLoss = opening(true, false, 2040, "808000", "246000");
        var prior = marketLoss.getPlanningAssumptions();
        var loss = new com.daviddunn.retirementplanner.domain.model.EconomicAssumptions(
                new BigDecimal("-0.99"), BigDecimal.ZERO)
                .withSpousalInheritance(prior.getEconomicAssumptions().getSpousalInheritance());
        marketLoss.setPlanningAssumptions(prior.withEconomicAssumptions(loss));
        var result = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class,
                new ProjectionEngine().projectSpousalIraDeathYear(marketLoss));
        assertEquals(FundingFailure.Stage.IRA_RMD, result.fundingFailure().stage());
        assertTrue(result.completedYears().isEmpty());
        money("808000", marketLoss.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
    }

    private static void expense(RetirementPlan plan, int year, String dollars) {
        var date = LocalDate.of(year, 1, 1);
        plan.getHousehold().addExpense(new Expense("One-time household expense", new BigDecimal(dollars),
                GrowthCategory.GENERAL, date, date, ExpenseType.ONE_TIME));
    }

    public static RetirementPlan opening(boolean primaryDies, boolean beneficiary, int year, String deceased, String survivor) {
        return opening(primaryDies, beneficiary, year, deceased, survivor, 1960, 1965);
    }

    static RetirementPlan opening(boolean primaryDies, boolean beneficiary, int year, String deceased, String survivor,
            int decedentBirth, int survivorBirth) {
        var plan = SpousalIraRmdExecutionTest.fixture(primaryDies ? DeathScenario.PRIMARY_DIES : DeathScenario.SPOUSE_DIES,
                year, primaryDies ? deceased : survivor, primaryDies ? survivor : deceased, !primaryDies);
        if (beneficiary) SpousalBeneficiaryIraRmdExecutionTest.select(plan);
        var participants = plan.getHousehold().peopleByOwner();
        participants.forEach((owner, person) -> {
            int birth = (owner == AccountOwnership.PRIMARY) == primaryDies ? decedentBirth : survivorBirth;
            var date = LocalDate.of(birth, 6, 15);
            person.setBirthDate(date);
            person.replaceIncomeSource(person.getIncomeSources().getFirst(),
                    new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome("Own benefit", owner,
                            date.plusYears(70), null, BigDecimal.ZERO, 70, BigDecimal.ZERO, 2033));
        });
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(), 2, LocalDate.of(year, 1, 1),
                old.getStateTaxAssumptions()));
        var accounts = plan.getAccountPortfolio().getAccounts();
        for (int i = 0; i < 2; i++) {
            var account = accounts.get(i);
            account.setOpeningRmdAccountData(new OpeningRmdAccountData(year, account.getCurrentBalance(), BigDecimal.ZERO, i == 0));
        }
        return plan;
    }

    private static Projection run(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class, new ProjectionEngine()
                .projectSpousalIraDeathYear(plan)).projection();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " differs from " + actual);
    }
}
