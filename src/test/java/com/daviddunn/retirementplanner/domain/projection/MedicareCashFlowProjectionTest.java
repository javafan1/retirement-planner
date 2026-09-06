package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicareCashFlowProjectionTest {

    @Test
    void portfolioFundsTheSameMedicarePremiumReportedForTheYear() {
        ProjectionYear withoutMedicare = projectBrokeragePlan(1962, null, null);
        ProjectionYear withMedicare = projectBrokeragePlan(1950, null, null);
        BigDecimal premium = withMedicare.getAnnualMedicarePremium();

        assertMoney(BigDecimal.ZERO, withoutMedicare.getAnnualMedicarePremium());
        assertTrue(premium.signum() > 0);
        assertMoney(premium, withMedicare.getCashFlowNeed());
        assertMoney(premium, withMedicare.getPortfolioWithdrawal());
        assertMoney(premium, withoutMedicare.getEndingInvestableAssets()
                .subtract(withMedicare.getEndingInvestableAssets()));
    }

    @Test
    void guaranteedIncomeOffsetsMedicareBeforePortfolioFunding() {
        ProjectionYear year = projectBrokeragePlan(1950, "500", "5000");
        BigDecimal expectedNeed = new BigDecimal("5000")
                .add(year.getAnnualMedicarePremium())
                .subtract(new BigDecimal("6000"))
                .max(BigDecimal.ZERO);

        assertTrue(expectedNeed.signum() > 0);
        assertMoney(expectedNeed, year.getCashFlowNeed());
        assertMoney(expectedNeed, year.getPortfolioWithdrawal());
    }

    @Test
    void excessGuaranteedIncomeAvoidsUnnecessaryMedicareWithdrawal() {
        ProjectionYear year = projectBrokeragePlan(1950, "1000", "5000");

        assertTrue(year.getAnnualMedicarePremium().signum() > 0);
        assertTrue(new BigDecimal("12000").compareTo(
                new BigDecimal("5000").add(year.getAnnualMedicarePremium())) > 0);
        assertMoney(BigDecimal.ZERO, year.getCashFlowNeed());
        assertMoney(BigDecimal.ZERO, year.getPortfolioWithdrawal());
    }

    @Test
    void medicareConsumesRmdCashBeforeItBecomesRetainedAssets() {
        Person primary = new Person("Primary", "Person", LocalDate.of(1950, 1, 1));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1990, 1, 1));
        TraditionalIRA ira = new TraditionalIRA(
                "Traditional IRA", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("100000"), BigDecimal.ZERO));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(ira);
        RetirementPlan plan = plan(new Household(primary, spouse), portfolio);

        ProjectionYear year = new ProjectionEngine().project(plan).getFirstYear();
        BigDecimal expectedExcess = year.getRmdDistributedInProjection()
                .subtract(year.getAnnualMedicarePremium())
                .max(BigDecimal.ZERO);

        assertTrue(year.getAnnualMedicarePremium().signum() > 0);
        assertMoney(year.getAnnualMedicarePremium(), year.getCashFlowNeed());
        assertMoney(expectedExcess, year.getExcessRmd());
        assertMoney(expectedExcess, year.getEndingRetainedRmdAssets());
    }

    private ProjectionYear projectBrokeragePlan(
            int primaryBirthYear,
            String monthlyPension,
            String annualExpense) {
        Person primary = new Person(
                "Primary", "Person", LocalDate.of(primaryBirthYear, 1, 1));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1990, 1, 1));
        if (monthlyPension != null) {
            primary.addIncomeSource(new Pension(
                    "Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1),
                    null, new BigDecimal(monthlyPension), BigDecimal.ZERO));
        }
        Household household = new Household(primary, spouse);
        if (annualExpense != null) {
            household.addExpense(new Expense("Living", new BigDecimal(annualExpense)));
        }
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        return new ProjectionEngine().project(plan(household, portfolio)).getFirstYear();
    }

    private RetirementPlan plan(Household household, AccountPortfolio portfolio) {
        return new RetirementPlan(
                household,
                portfolio,
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1,
                        LocalDate.of(2026, 1, 1)));
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
