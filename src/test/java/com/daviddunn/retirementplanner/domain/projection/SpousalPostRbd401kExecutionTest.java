package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class SpousalPostRbd401kExecutionTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void olderSurvivorUsesDecedentFactor(boolean primaryDies) {
        var plan = fixture(primaryDies, 2042, 1965, 1960, "458000", "0");
        var projection = run(plan);
        var death = projection.getYearAt(0);
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        var payment = death.getIncomeSources().stream()
                .filter(row -> row.successorRmd().isPresent()).findFirst().orElseThrow();
        assertNotEquals(source.getOwnership(), payment.owner().orElseThrow());
        assertEquals(source.getOwnership(), payment.account().orElseThrow().owner());
        money("20000", death.getRequiredMinimumDistribution());
        money("20000", payment.grossAmount());
        money("438000", death.getEndingAccountSnapshots().getFirst().getEndingBalance());
        var subsequent = projection.getYearAt(1).beneficiary401kRmds().getFirst();
        money("12.3", subsequent.divisor());
        money("35609.76", subsequent.requiredAmount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void youngerSurvivorFactorAndOwnIraRemainSeparate(boolean primaryDies) {
        var projection = run(fixture(primaryDies, 2040, 1960, 1965, "808000", "246000"));
        var death = projection.getYearAt(0);
        money("50000", death.getRequiredMinimumDistribution());
        money("50000", death.getAdjustedGrossIncome());
        money("768000", death.getEndingAccountSnapshots().getFirst().getEndingBalance());
        var next = projection.getYearAt(1);
        var employer = next.beneficiary401kRmds().getFirst();
        money("14.1", employer.divisor());
        money("54468.09", employer.requiredAmount());
        money("64425.90", next.getRequiredMinimumDistribution());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong year", "retired late"})
    void refusesUnconfirmedOrContradictoryEmployerRbd(String defect) {
        var plan = fixture(true, 2042, 1965, 1960, "458000", "0");
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        source.setRetirementParticipantFacts(facts(defect.equals("retired late") ? 2041 : 2030,
                defect.equals("missing") ? null : LocalDate.of(defect.equals("wrong year") ? 2040 : 2041, 4, 1)));
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(plan));
    }

    @Test
    void eachPlanHasItsOwnMinimumInBothYears() {
        var plan = fixture(true, 2042, 1965, 1960, "458000", "0");
        var original = plan.getAccountPortfolio().getAccounts().getFirst();
        var other = new Traditional401K("Other employer", original.getOwnership(), new BigDecimal("229000"));
        other.setRetirementParticipantFacts(original.getRetirementParticipantFacts());
        other.setOpeningRmdAccountData(new OpeningRmdAccountData(2042, new BigDecimal("229000"), BigDecimal.ZERO, true));
        plan.getAccountPortfolio().addAccount(other);
        var projection = run(plan);
        var death = projection.getYearAt(0);
        money("30000", death.getRequiredMinimumDistribution());
        var receipts = death.getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).toList();
        assertEquals(2, receipts.size());
        money("10000", receipts.stream().filter(row -> row.account().orElseThrow().name().equals("Other employer"))
                .findFirst().orElseThrow().grossAmount());
        var following = projection.getYearAt(1).beneficiary401kRmds();
        assertEquals(2, following.size());
        money("17804.88", following.stream().filter(row -> row.source().name().equals("Other employer"))
                .findFirst().orElseThrow().requiredAmount());
    }

    @Test
    void mandatoryShortfallCannotUseAnotherPlanOrHouseholdLiquidity() {
        var plan = fixture(true, 2042, 1965, 1960, "458000", "0");
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        var other = new Traditional401K("Other employer", source.getOwnership(), new BigDecimal("4580000"));
        other.setRetirementParticipantFacts(source.getRetirementParticipantFacts());
        other.setOpeningRmdAccountData(new OpeningRmdAccountData(2042, new BigDecimal("4580000"), BigDecimal.ZERO, true));
        plan.getAccountPortfolio().addAccount(other);
        var assumptions = plan.getPlanningAssumptions();
        var loss = new com.daviddunn.retirementplanner.domain.model.EconomicAssumptions(new BigDecimal("-0.99"), BigDecimal.ZERO)
                .withSpousalInheritance(assumptions.getEconomicAssumptions().getSpousalInheritance());
        plan.setPlanningAssumptions(assumptions.withEconomicAssumptions(loss));
        var result = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class,
                new ProjectionEngine().projectSpousalPostRbd401k(plan));
        assertEquals(FundingFailure.Stage.ACCOUNT_RMD, result.fundingFailure().stage());
        assertTrue(result.completedYears().isEmpty());
        money("458000", source.getCurrentBalance());
        money("4580000", other.getCurrentBalance());
    }

    @Test
    void refusesAprilBoundaryAndJointLifeCase() {
        var preRbd = fixture(true, 2041, 1965, 1960, "458000", "0");
        assertFalse(ProjectionEngine.requestsSpousalPostRbd401k(preRbd));
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(preRbd));
        var jointLife = fixture(false, 2042, 1965, 1976, "458000", "0");
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(jointLife));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong year", "paid", "uncleared", "incompatible balance"})
    void openingPlanEvidenceIsRequired(String defect) {
        var plan = fixture(true, 2042, 1965, 1960, "458000", "0");
        var source = plan.getAccountPortfolio().getAccounts().getFirst();
        source.setOpeningRmdAccountData(switch (defect) {
            case "missing" -> null;
            case "wrong year" -> new OpeningRmdAccountData(2041, new BigDecimal("458000"), BigDecimal.ZERO, true);
            case "paid" -> new OpeningRmdAccountData(2042, new BigDecimal("458000"), BigDecimal.ONE, true);
            case "uncleared" -> new OpeningRmdAccountData(2042, new BigDecimal("458000"), BigDecimal.ZERO);
            default -> new OpeningRmdAccountData(2042, new BigDecimal("458001"), BigDecimal.ZERO, true);
        });
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(plan));
    }

    @Test
    void beneficiaryCashAndTaxFundingUseOneOriginalEmployerDebitPerReceipt() {
        var plan = fixture(true, 2040, 1960, 1965, "808000", "246000");
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(BigDecimal.ZERO);
        expense(plan, 2040, "45785.20");
        expense(plan, 2041, "80000");
        var projection = run(plan);
        var death = projection.getYearAt(0);
        money("0", death.getEndingRetainedNonQualifiedAssets());
        assertEquals(1, death.getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).count());
        var next = projection.getYearAt(1);
        var ordinary = next.getIncomeSources().stream().filter(row -> row.successorOrdinary().isPresent()).toList();
        assertTrue(ordinary.stream().anyMatch(row -> row.purpose()
                == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING));
        assertTrue(ordinary.stream().anyMatch(row -> row.purpose()
                == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW));
        var actual = ordinary.stream().map(row -> row.grossAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        money(new BigDecimal("768000").subtract(new BigDecimal("54468.09")).subtract(actual).toPlainString(),
                next.getEndingAccountSnapshots().getFirst().getEndingBalance());
    }

    @Test
    void earlierModeledEmployeeMinimumsEstablishPriorYearCompliance() {
        var plan = fixture(true, 2042, 1965, 1960, "246000", "0");
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                assumptions.getEconomicAssumptions(), assumptions.getTaxAssumptions(), assumptions.getWithdrawalAssumptions(),
                assumptions.getDeathScenarioAssumptions(), 4, LocalDate.of(2040, 1, 1), assumptions.getStateTaxAssumptions()));
        var accounts = plan.getAccountPortfolio().getAccounts();
        accounts.getFirst().setOpeningRmdAccountData(new OpeningRmdAccountData(2040, new BigDecimal("246000"), BigDecimal.ZERO));
        accounts.get(1).setOpeningRmdAccountData(new OpeningRmdAccountData(2040, BigDecimal.ZERO, BigDecimal.ZERO));
        var projection = run(plan);
        money("10000", projection.getYearAt(0).getRequiredMinimumDistribution());
        money("9957.81", projection.getYearAt(1).getRequiredMinimumDistribution());
        money("9870.84", projection.getYearAt(2).getRequiredMinimumDistribution());
        assertTrue(projection.getYearAt(1).getIncomeSources().stream().noneMatch(row -> row.successorRmd().isPresent()));
        assertEquals(1, projection.getYearAt(2).getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).count());
    }

    @Test
    void employerMinimumTriggersExistingFirstRmdRothStopBeforeAnySurvivorMinimum() {
        var plan = fixture(true, 2040, 1960, 1970, "808000", "246000");
        var survivor = plan.getAccountPortfolio().getAccounts().get(1).getOwnership();
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA(
                "Surviving spouse Roth", survivor, BigDecimal.ZERO));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                true, 2040, new BigDecimal("10000"),
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
        var death = run(plan).getYearAt(0);
        money("40000", death.getRequiredMinimumDistribution());
        money("0", death.getRothConversion());
        money("768000", death.getEndingAccountSnapshots().getFirst().getEndingBalance());
    }

    @Test
    void otherStateAndAdvancedModeDoNotRequestEmployerExecution() {
        var advanced = fixture(true, 2042, 1965, 1960, "458000", "0");
        advanced.setFinancialCalculationMode(com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.ADVANCED);
        assertFalse(ProjectionEngine.requestsSpousalPostRbd401k(advanced));
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(advanced));

        var michigan = fixture(true, 2042, 1965, 1960, "458000", "0");
        michigan.setPlanningAssumptions(michigan.getPlanningAssumptions().withStateTaxAssumptions(
                new com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions(
                        com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MI,
                        com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                        null)));
        assertFalse(ProjectionEngine.requestsSpousalPostRbd401k(michigan));
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(michigan));
    }

    @Test
    void deathYearMedicareRepricesWithTaxFunding() {
        var original = fixture(true, 2040, 1960, 1965, "4040000", "246000");
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
        assertEquals(1, year.getIncomeSources().stream().filter(row -> row.successorRmd().isPresent()).count());
        money("3840000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        assertTrue(year.getTaxFundingWithdrawal().subtract(new BigDecimal("37799.96")).abs()
                .compareTo(new BigDecimal("0.02")) <= 0, year.getTaxFundingWithdrawal()::toPlainString);
    }

    private static void expense(RetirementPlan plan, int year, String dollars) {
        var date = LocalDate.of(year, 1, 1);
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense(
                "One-time household expense", new BigDecimal(dollars),
                com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL, date, date,
                com.daviddunn.retirementplanner.domain.financial.ExpenseType.ONE_TIME));
    }

    public static RetirementPlan fixture(boolean primaryDies, int year, int decedentBirth, int survivorBirth,
            String employerBalance, String survivorBalance) {
        var plan = SpousalIraDeathYearExecutionTest.opening(primaryDies, true, year, employerBalance, survivorBalance,
                decedentBirth, survivorBirth);
        var portfolio = plan.getAccountPortfolio();
        var original = portfolio.getAccounts().getFirst();
        var employer = new Traditional401K("Original employer", original.getOwnership(), original.getCurrentBalance());
        employer.setOpeningRmdAccountData(original.getOpeningRmdAccountData());
        employer.setRetirementParticipantFacts(facts(2030, LocalDate.of(decedentBirth + 76, 4, 1)));
        portfolio.replaceAccount(original, employer);
        return plan;
    }

    private static RetirementParticipantFacts facts(int retirementYear, LocalDate rbd) {
        var retired = LocalDate.of(retirementYear, 1, 1);
        return new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED, retired,
                RetirementParticipantFacts.RetirementConditionsStatus.MET, retired, rbd);
    }

    private static Projection run(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                new ProjectionEngine().projectSpousalPostRbd401k(plan)).projection();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " differs from " + actual);
    }
}
