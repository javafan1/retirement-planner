package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class SpousalPostRbd401kOwnerExecutionTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rolloverYearPaysBeneficiaryAndOwnIraMinimumsSeparately(boolean primaryDies) {
        var plan = fixture(primaryDies, true);
        var projection = run(plan);
        var death = projection.getYearAt(0);
        money("30000", death.getRequiredMinimumDistribution());
        money("438000", death.getEndingAccountSnapshots().getFirst().getEndingBalance());
        var rollover = projection.getYearAt(1);
        var minimum = rollover.beneficiary401kRmds().getFirst();
        money("12.3", minimum.divisor());
        money("35609.76", minimum.requiredAmount());
        money("402390.24", rollover.spousal401kRollovers().getFirst().eligibleTransferredBalance());
        money("45496.77", rollover.getRequiredMinimumDistribution());
        money("45496.77", rollover.getAdjustedGrossIncome());
        money("392503.23", rollover.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("175000.00", rollover.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("33779.95", projection.getYearAt(2).getRequiredMinimumDistribution());
    }

    @Test
    void refusesWithoutDirectRolloverProof() {
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(fixture(true, false)));
    }

    @Test
    void januaryPaymentLeavesTheInvestmentGrowthBasisBeforeRollover() {
        var plan = fixture(true, true);
        var old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(old.withEconomicAssumptions(
                new com.daviddunn.retirementplanner.domain.model.EconomicAssumptions(new BigDecimal("0.10"), BigDecimal.ZERO)
                        .withSpousalInheritance(old.getEconomicAssumptions().getSpousalInheritance())));
        var projection = run(plan);
        money("483800", cents(projection.getYearAt(0).getEndingAccountSnapshots().getFirst().getEndingBalance()));
        var rollover = projection.getYearAt(1);
        money("39333.33", rollover.beneficiary401kRmds().getFirst().requiredAmount());
        money("444466.67", cents(rollover.spousal401kRollovers().getFirst().eligibleTransferredBalance()));
        money("50265.53", rollover.getRequiredMinimumDistribution());
        money("477981.14", rollover.getEndingAccountSnapshots().getFirst().getEndingBalance()
                .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void everyPlanPaysAndRollsItsOwnBalance() {
        var plan = fixture(false, true);
        var original = plan.getAccountPortfolio().getAccounts().getFirst();
        var other = new com.daviddunn.retirementplanner.domain.financial.Traditional401K(
                "Second employer", original.getOwnership(), new BigDecimal("229000"));
        other.setRetirementParticipantFacts(original.getRetirementParticipantFacts());
        other.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                2042, other.getCurrentBalance(), BigDecimal.ZERO, true));
        plan.getAccountPortfolio().addAccount(other);
        var projection = run(plan);
        money("40000", projection.getYearAt(0).getRequiredMinimumDistribution());
        var year = projection.getYearAt(1);
        assertEquals(2, year.beneficiary401kRmds().size());
        assertEquals(2, year.spousal401kRollovers().size());
        money("17804.88", year.beneficiary401kRmds().stream().filter(row -> row.source().name().equals("Second employer"))
                .findFirst().orElseThrow().requiredAmount());
        money("201195.12", year.spousal401kRollovers().stream().filter(row -> row.source().name().equals("Second employer"))
                .findFirst().orElseThrow().eligibleTransferredBalance());
        money("63301.65", year.getAdjustedGrossIncome());
    }

    @Test
    void rolloverYearOrdinaryFundingSettlesTaxAndCrossesMedicareTier() {
        var plan = fixture(true, true);
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(BigDecimal.ZERO);
        expense(plan, 2042, "27565.20");
        expense(plan, 2043, "95000.00");
        var year = run(plan).getYearAt(1);
        money("3583.20", year.getAnnualMedicarePremium());
        money("45496.77", year.getRequiredMinimumDistribution());
        money("402390.24", year.spousal401kRollovers().getFirst().eligibleTransferredBalance());
        var funding = year.getTaxFundingWithdrawal();
        assertTrue(funding.subtract(new BigDecimal("16485.01")).abs().compareTo(new BigDecimal("0.02")) <= 0,
                funding::toPlainString);
        money("0", year.getEndingRetainedNonQualifiedAssets());
        assertEquals(1, year.getIncomeSources().stream().filter(row -> row.successorRmd().map(proof ->
                proof.treatment().name().equals("MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1")).orElse(false)).count());
        assertTrue(year.getIncomeSources().stream().anyMatch(row -> row.successorOrdinary().isPresent()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unconfirmed RBD", "late retirement", "unknown prior obligations", "paid in January", "Advanced", "Michigan"})
    void contradictoryOrUnavailableEvidenceRefuses(String defect) {
        var plan = fixture(true, true);
        var original = plan.getAccountPortfolio().getAccounts().getFirst();
        var facts = original.getRetirementParticipantFacts();
        switch (defect) {
            case "unconfirmed RBD", "late retirement" -> original.setRetirementParticipantFacts(new RetirementParticipantFacts(
                    facts.separationStatus(), defect.equals("late retirement") ? java.time.LocalDate.of(2041, 5, 1) : facts.separationDate(),
                    facts.retirementConditionsStatus(), facts.retirementConditionsMetDate(),
                    defect.equals("unconfirmed RBD") ? null : facts.confirmedPostRbdBeneficiaryRequiredBeginningDate(), true));
            case "unknown prior obligations", "paid in January" ->
                original.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                        2042, original.getCurrentBalance(), defect.equals("paid in January") ? BigDecimal.ONE : BigDecimal.ZERO,
                        defect.equals("paid in January") ? Boolean.TRUE : null));
            case "Advanced" -> plan.setFinancialCalculationMode(com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.ADVANCED);
            case "Michigan" -> plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(
                    com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.simplified(
                            com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MI)));
            default -> throw new AssertionError(defect);
        }
        assertThrows(SpousalIraRmdUnavailable.class, () -> run(plan), defect);
        money("458000", original.getCurrentBalance());
    }

    @Test
    void deathYearPlanShortfallStopsBeforeAnyTransfer() {
        var plan = fixture(true, true);
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(assumptions.withEconomicAssumptions(
                new com.daviddunn.retirementplanner.domain.model.EconomicAssumptions(new BigDecimal("-0.99"), BigDecimal.ZERO)
                        .withSpousalInheritance(assumptions.getEconomicAssumptions().getSpousalInheritance())));
        var result = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class,
                new ProjectionEngine().projectSpousalPostRbd401kOwner(plan));
        assertEquals(FundingFailure.Stage.ACCOUNT_RMD, result.fundingFailure().stage());
        assertTrue(result.completedYears().isEmpty());
    }

    private static void expense(RetirementPlan plan, int year, String dollars) {
        var date = java.time.LocalDate.of(year, 1, 1);
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense(
                "Controlled synthetic expense", new BigDecimal(dollars),
                com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL, date, date,
                com.daviddunn.retirementplanner.domain.financial.ExpenseType.ONE_TIME));
    }

    public static RetirementPlan fixture(boolean primaryDies, boolean confirmed) {
        var plan = SpousalPostRbd401kExecutionTest.fixture(primaryDies, 2042, 1965, 1960, "458000", "185000");
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(assumptions.withEconomicAssumptions(assumptions.getEconomicAssumptions()
                .withSpousalInheritance(new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER))));
        var account = plan.getAccountPortfolio().getAccounts().getFirst();
        var facts = account.getRetirementParticipantFacts();
        account.setRetirementParticipantFacts(new RetirementParticipantFacts(facts.separationStatus(),
                facts.separationDate(), facts.retirementConditionsStatus(), facts.retirementConditionsMetDate(),
                facts.confirmedPostRbdBeneficiaryRequiredBeginningDate(), confirmed ? Boolean.TRUE : null));
        var opening = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                opening.getEconomicAssumptions(), opening.getTaxAssumptions(), opening.getWithdrawalAssumptions(),
                opening.getDeathScenarioAssumptions(), 3, opening.getProjectionStartDate(), opening.getStateTaxAssumptions()));
        return plan;
    }

    private static Projection run(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                new ProjectionEngine().projectSpousalPostRbd401kOwner(plan)).projection();
    }

    private static BigDecimal cents(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " differs from " + actual);
    }
}
