package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpeningRmdProjectionTest {

    @Test
    void projectsOnlyRemainingOpeningRmdButTaxesTheFullAnnualRequirement() {
        TraditionalIRA ira = ira("1000000", "15000");
        RetirementPlan plan = plan(ira, 2);

        ProjectionYear year = new ProjectionEngine().project(plan).getYearAt(0);
        BigDecimal annualRmd = annualRmd();
        BigDecimal remainingRmd = annualRmd.subtract(new BigDecimal("15000"));

        assertMoney(annualRmd, year.getRequiredMinimumDistribution());
        assertMoney(new BigDecimal("15000"), year.getRmdDistributedBeforeProjection());
        assertMoney(remainingRmd, year.getRmdDistributedInProjection());
        assertMoney(
                remainingRmd
                        .subtract(year.getAnnualMedicarePremium())
                        .subtract(year.getTotalIncomeTax())
                        .max(BigDecimal.ZERO),
                year.getEndingRetainedNonQualifiedAssets());
        assertEquals(true, year.getAdjustedGrossIncome().compareTo(annualRmd) >= 0);
        assertMoney(new BigDecimal("900000"), ira.getCurrentBalance());
    }

    @Test
    void fullyDistributedOpeningRmdDoesNotWithdrawAgainAndStopsRothConversions() {
        TraditionalIRA ira = ira("1000000", annualRmd().toPlainString());
        RetirementPlan plan = plan(ira, 2);
        plan.setRothConversionRequest(new RothConversionRequest(
                true,
                2026,
                new BigDecimal("50000"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                RothConversionStrategy.FIXED_AMOUNT,
                RothConversionFrequency.ANNUAL));

        ProjectionYear firstYear = new ProjectionEngine().project(plan).getYearAt(0);

        assertMoney(annualRmd(), firstYear.getRequiredMinimumDistribution());
        assertMoney(annualRmd(), firstYear.getRmdDistributedBeforeProjection());
        assertMoney(BigDecimal.ZERO, firstYear.getRmdDistributedInProjection());
        assertMoney(BigDecimal.ZERO, firstYear.getRothConversion());
        assertMoney(BigDecimal.ZERO, firstYear.getEndingRetainedRmdAssets());
        assertMoney(new BigDecimal("900000"), ira.getCurrentBalance());
    }

    @Test
    void openingRmdIncomeCanEliminateCustomTaxableIncomeTargetConversion() {
        TraditionalIRA ira = ira("1000000", annualRmd().toPlainString());
        RetirementPlan plan = plan(ira, 2);
        plan.setRothConversionRequest(new RothConversionRequest(
                true,
                2026,
                BigDecimal.ZERO,
                RothConversionStopRule.NEVER,
                RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,
                RothConversionFrequency.ONE_TIME,
                new BigDecimal("10000")));

        ProjectionYear firstYear = new ProjectionEngine().project(plan).getYearAt(0);

        assertMoney(BigDecimal.ZERO, firstYear.getRothConversion());
        assertEquals(true, firstYear.getFederalTaxableIncome()
                .compareTo(new BigDecimal("10000")) >= 0);
    }

    @Test
    void secondYearUsesModeledDecember31BalanceInsteadOfOpeningData() {
        TraditionalIRA ira = ira("1000000", BigDecimal.ZERO.toPlainString());
        Projection projection = new ProjectionEngine().project(plan(ira, 3));

        ProjectionYear firstYear = projection.getYearAt(0);
        ProjectionYear secondYear = projection.getYearAt(1);
        BigDecimal expectedSecondYearRmd = firstYear.getEndingBalance(ira)
                .divide(new BigDecimal("22.9"), 2, RoundingMode.HALF_UP);

        assertMoney(expectedSecondYearRmd, secondYear.getRequiredMinimumDistribution());
    }

    private TraditionalIRA ira(String priorBalance, String alreadyTaken) {
        TraditionalIRA ira = new TraditionalIRA(
                "Traditional IRA", AccountOwnership.PRIMARY, new BigDecimal("900000"));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal(priorBalance), new BigDecimal(alreadyTaken)));
        return ira;
    }

    private RetirementPlan plan(TraditionalIRA ira, int years) {
        Household household = new Household(
                new Person("Primary", "Person", LocalDate.of(1950, 1, 1)),
                new Person("Spouse", "Person", LocalDate.of(1965, 1, 1)));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(ira);
        return new RetirementPlan(household, portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, years,
                        LocalDate.of(2026, 7, 1)));
    }

    private static BigDecimal annualRmd() {
        return new BigDecimal("1000000")
                .divide(new BigDecimal("23.7"), 2, RoundingMode.HALF_UP);
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
