package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RothSchedulingExecutionTest {
    @ParameterizedTest
    @CsvSource({
            "ANNUAL, 2025, 2027, 20000, 20000, 0",
            "ANNUAL, 2027, 2030, 0, 20000, 20000",
            "ANNUAL, 2027, 2027, 0, 20000, 0",
            "ANNUAL, 2024, 2025, 0, 0, 0",
            "ANNUAL, 2029, 2030, 0, 0, 0",
            "ONE_TIME, 2025, 2025, 0, 0, 0",
            "ONE_TIME, 2027, 2027, 0, 20000, 0"
    })
    void configuredWindowIntersectsModeledYearsWithoutClamping(RothConversionFrequency frequency,
            int start, int end, String first, String second, String third) {
        var plan = plan("246000", "0");
        var request = new RothConversionRequest(true, start, new BigDecimal("20000"), null,
                RothConversionStrategy.FIXED_AMOUNT, frequency, null,
                new RothConversionRequest.Scheduling(1, end, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR));
        plan.setRothConversionRequest(request);
        var projection = run(plan);
        money(first, projection.getYearAt(0).getRothConversion());
        money(second, projection.getYearAt(1).getRothConversion());
        money(third, projection.getYearAt(2).getRothConversion());
        assertSame(request, plan.getRothConversionRequest());
    }

    @Test
    void continuePaysTenThousandFirstAndMovesTwentyThousandToRoth() {
        var plan = plan("246000", "0");
        apply(plan, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR, 2026, 2026, RothConversionStrategy.FIXED_AMOUNT);
        var year = run(plan).getYearAt(0);
        money("10000", year.getRequiredMinimumDistribution());
        money("20000", year.getRothConversion());
        money("216000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("20000", year.getEndingAccountSnapshots().get(2).getEndingBalance());
        money("30000", year.getAdjustedGrossIncome());
    }

    @ParameterizedTest
    @EnumSource(RothRmdSchedulingPolicy.class)
    void fullyPaidOpeningRequirementIsStillAnEvent(RothRmdSchedulingPolicy policy) {
        var plan = plan("246000", "10000");
        apply(plan, policy, 2026, 2026, RothConversionStrategy.FIXED_AMOUNT);
        var year = run(plan).getYearAt(0);
        money("10000", year.getRequiredMinimumDistribution());
        money("0", year.getRmdDistributedInProjection());
        money(policy == RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR ? "20000" : "0", year.getRothConversion());
    }

    @ParameterizedTest
    @EnumSource(RothRmdSchedulingPolicy.class)
    void positiveYearBeforeWindowOnlyBlocksPermanent(RothRmdSchedulingPolicy policy) {
        var plan = plan("24600", "0");
        var opening = LocalDate.of(2026, 1, 1);
        plan.getHousehold().addExpense(new Expense("Synthetic spend-down", new BigDecimal("22165.20"),
                GrowthCategory.GENERAL, opening, opening, ExpenseType.ONE_TIME));
        apply(plan, policy, 2027, 2027, RothConversionStrategy.FIXED_AMOUNT);
        var projection = run(plan);
        money("1000", projection.getYearAt(0).getRequiredMinimumDistribution());
        money("0", projection.getYearAt(0).getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("0", projection.getYearAt(1).getRequiredMinimumDistribution());
        money(policy == RothRmdSchedulingPolicy.PERMANENTLY_STOP_AT_FIRST_RMD ? "0" : "20000",
                projection.getYearAt(1).getRothConversion());
    }

    @Test
    void rmdConsumesTwelvePercentBracketRoom() {
        var plan = plan("246000", "0");
        var old = plan.getPlanningAssumptions();
        var tax = new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE, BigDecimal.ZERO);
        plan.setPlanningAssumptions(new PlanningAssumptions(old.getEconomicAssumptions(), tax,
                old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(), 3, old.getProjectionStartDate(),
                old.getStateTaxAssumptions()));
        var start = LocalDate.of(2026, 1, 1);
        var primary = plan.getHousehold().getPrimaryPerson();
        var pension = new com.daviddunn.retirementplanner.domain.income.Pension("Synthetic pension",
                AccountOwnership.PRIMARY, start, null, new BigDecimal("2500"), BigDecimal.ZERO);
        primary.addIncomeSource(pension);
        apply(plan, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR, 2026, 2026,
                RothConversionStrategy.FILL_12_PERCENT_BRACKET);
        var year = run(plan).getYearAt(0);
        money("10000", year.getRequiredMinimumDistribution());
        money("26500", year.getRothConversion());
        money("50400", year.getFederalTaxableIncome());
        primary.replaceIncomeSource(pension, new com.daviddunn.retirementplanner.domain.income.Pension("Large synthetic pension",
                AccountOwnership.PRIMARY, start, null, new BigDecimal("6000"), BigDecimal.ZERO));
        money("0", run(plan).getYearAt(0).getRothConversion());
    }

    @ParameterizedTest
    @CsvSource({
            "2026, 2027, CONTINUE_THROUGH_END_YEAR",
            "2027, 2027, CONTINUE_THROUGH_END_YEAR",
            "2026, 2026, SKIP_POSITIVE_RMD_YEARS"
    })
    void changedScheduleWithRebuiltIdentityStillRequiresIndependentReplay(int start, int end,
            RothRmdSchedulingPolicy policy) {
        var plan = plan("246000", "0");
        apply(plan, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR, 2026, 2026, RothConversionStrategy.FIXED_AMOUNT);
        var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController();
        controller.newPlan(plan);
        controller.getCurrentProjection();
        var accepted = controller.getAcceptedProjectionSnapshot();
        assertTrue(accepted.rothSchedulingDisclosure().contains("2026–2026"));
        apply(plan, policy, start, end, RothConversionStrategy.FIXED_AMOUNT);
        var input = com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.freeze(plan);
        var provenance = com.daviddunn.retirementplanner.app.projection.ConditionalBenefitExecutionSemantics.completed(
                plan, input, accepted.annual(), com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.PROVENANCE
                        + "Synthetic tamper"
                        + com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.rothSchedulingStamp(
                                plan.getRothConversionRequest(), input, accepted.annual()));
        assertThrows(com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable.class,
                () -> new com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot(
                        accepted.version(), accepted.id(), accepted.owner(), accepted.sourcePlanId(), accepted.acceptedAt(),
                        input, com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.digest(input), provenance,
                        accepted.annual(), accepted.nonInvestable(), accepted.summary(), accepted.people()));
    }

    @Test
    void youngerDecedentDefersBeneficiaryMinimumUntil2051AndNeverSuppliesConversion() {
        var household = new Household(new Person("Primary", "Example", LocalDate.of(1963, 6, 1)),
                new Person("Spouse", "Example", LocalDate.of(1976, 6, 1)));
        var accounts = new AccountPortfolio();
        accounts.addAccount(new TraditionalIRA("Living original", AccountOwnership.PRIMARY, new BigDecimal("1500000")));
        accounts.addAccount(new TraditionalIRA("Beneficiary source", AccountOwnership.SPOUSE, new BigDecimal("660000")));
        accounts.addAccount(new RothIRA("Living owner Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        accounts.addAccount(new CheckingAccount("Cash for spending and taxes", AccountOwnership.JOINT, new BigDecimal("2000000")));
        var opening = LocalDate.of(2030, 1, 1);
        var defaults = new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 22, opening);
        var plan = new RetirementPlan(household, accounts, new PlanningAssumptions(defaults.getEconomicAssumptions()
                .withSpousalInheritance(new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY)),
                defaults.getTaxAssumptions(), defaults.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.SPOUSE_DIES, 2030), 22, opening,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("200000"), null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL, null,
                new RothConversionRequest.Scheduling(1, 2051, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR)));
        var projection = run(plan);
        money("100000", projection.getYearAt(7).getRothConversion());
        money("0", projection.getYearAt(8).getRequiredMinimumDistribution());
        money("0", projection.getYearAt(8).getRothConversion());
        money("0", projection.getYearAt(20).getRequiredMinimumDistribution());
        money("100000", projection.getYearAt(21).getRequiredMinimumDistribution());
        money("0", projection.getYearAt(21).getRothConversion());
    }

    private static RetirementPlan plan(String amount, String alreadyPaid) {
        var household = new Household(new Person("Primary", "Example", LocalDate.of(1951, 1, 1)),
                new Person("Spouse", "Example", LocalDate.of(1965, 1, 1)));
        var ira = new TraditionalIRA("Original IRA", AccountOwnership.PRIMARY, new BigDecimal(amount));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(2026, new BigDecimal(amount), new BigDecimal(alreadyPaid)));
        var accounts = new AccountPortfolio();
        accounts.addAccount(ira);
        accounts.addAccount(new TraditionalIRA("Younger IRA", AccountOwnership.SPOUSE, new BigDecimal("100000")));
        accounts.addAccount(new RothIRA("Primary Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        accounts.addAccount(new RothIRA("Spouse Roth", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        var assumptions = new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 3, LocalDate.of(2026, 1, 1))
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL));
        var plan = new RetirementPlan(household, accounts, assumptions);
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        return plan;
    }

    private static void apply(RetirementPlan plan, RothRmdSchedulingPolicy policy, int start, int end,
            RothConversionStrategy strategy) {
        plan.setRothConversionRequest(new RothConversionRequest(true, start, new BigDecimal("20000"), null, strategy,
                RothConversionFrequency.ANNUAL, null, new RothConversionRequest.Scheduling(1, end, policy)));
    }

    private static Projection run(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                new ProjectionEngine().projectNormalWithRothScheduling(plan)).projection();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " differs from " + actual);
    }
}
