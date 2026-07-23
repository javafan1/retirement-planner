package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionEngineTest {

    @Test
    void projectionCalculatesFirstYearCorrectly() {

        int currentYear =
                Year.now().getValue();

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965, 2, 28));

        /*
         * $3,000/month pension beginning January 1
         * of the projection year.
         *
         * Annual guaranteed income = $36,000.
         */
        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(currentYear, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO));

        Household household =
                new Household(
                        primary,
                        spouse);

        /*
         * $60,000 annual expenses.
         */
        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("60000")));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        /*
         * Start with $1,000,000.
         */
        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        /*
         * No investment growth.
         * No inflation.
         * One projection year.
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1);

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear year =
                projection.getYearAt(0);

        assertEquals(
                currentYear,
                year.getCalendarYear());

        assertEquals(
                new BigDecimal("1000000"),
                year.getBeginningInvestableAssets());

        assertEquals(
                new BigDecimal("0.00"),
                year.getInvestmentGrowth());

        assertEquals(
                new BigDecimal("36000"),
                year.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("60000.00"),
                year.getAnnualExpenses());

        assertEquals(
                new BigDecimal("24000.00"),
                year.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("976000.00"),
                year.getEndingInvestableAssets());
    }

    @Test
    void projectionHandlesPartialYearPensionIncome() {

        int currentYear =
                Year.now().getValue();

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965, 2, 28));

        /*
         * Pension begins July 1.
         *
         * $3,000 × 6 months = $18,000
         * of guaranteed income in the first year.
         */
        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(currentYear, 7, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO));

        Household household =
                new Household(
                        primary,
                        spouse);

        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("60000")));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1);

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear year =
                projection.getYearAt(0);

        assertEquals(
                new BigDecimal("18000"),
                year.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("42000.00"),
                year.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("958000.00"),
                year.getEndingInvestableAssets());
    }

    @Test
    void projectionAppliesPensionColaAndExpenseInflation() {

        int currentYear =
                Year.now().getValue();

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965, 2, 28));

        /*
         * $3,000/month pension
         * with 2% annual COLA.
         */
        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(currentYear, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        new BigDecimal("0.02")));

        Household household =
                new Household(
                        primary,
                        spouse);

        /*
         * Starting annual expenses = $60,000.
         */
        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("60000")));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        /*
         * No investment return for this test.
         * 3% annual inflation.
         * Two projection years.
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        new BigDecimal("0.03"),
                        2);

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear firstYear =
                projection.getYearAt(0);

        ProjectionYear secondYear =
                projection.getYearAt(1);

        /*
         * YEAR 1
         *
         * Pension:
         * $3,000 × 12 = $36,000
         *
         * Expenses:
         * $60,000
         *
         * Withdrawal:
         * $60,000 - $36,000 = $24,000
         *
         * Ending assets:
         * $1,000,000 - $24,000 = $976,000
         */

        assertEquals(
                new BigDecimal("36000"),
                firstYear.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("60000.00"),
                firstYear.getAnnualExpenses());

        assertEquals(
                new BigDecimal("24000.00"),
                firstYear.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("976000.00"),
                firstYear.getEndingInvestableAssets());

        /*
         * YEAR 2
         *
         * Pension with 2% COLA:
         * $36,000 × 1.02 = $36,720
         *
         * Expenses with 3% inflation:
         * $60,000 × 1.03 = $61,800
         *
         * Withdrawal:
         * $61,800 - $36,720 = $25,080
         *
         * Ending assets:
         * $976,000 - $25,080 = $950,920
         */

        assertEquals(
                new BigDecimal("36720.00"),
                secondYear.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("61800.00"),
                secondYear.getAnnualExpenses());

        assertEquals(
                new BigDecimal("25080.00"),
                secondYear.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("950920.00"),
                secondYear.getEndingInvestableAssets());
    }

    @Test
    void projectionAppliesInvestmentGrowth() {

        int currentYear =
                Year.now().getValue();

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965, 2, 28));

        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(currentYear, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO));

        Household household =
                new Household(
                        primary,
                        spouse);

        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("60000")));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        /*
         * 5% investment return
         * 0% inflation
         * 1 projection year
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.05"),
                        BigDecimal.ZERO,
                        1);

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear year =
                projection.getYearAt(0);

        assertEquals(
                new BigDecimal("1000000"),
                year.getBeginningInvestableAssets());

        assertEquals(
                new BigDecimal("50000.00"),
                year.getInvestmentGrowth());

        assertEquals(
                new BigDecimal("36000"),
                year.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("60000.00"),
                year.getAnnualExpenses());

        assertEquals(
                new BigDecimal("24000.00"),
                year.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("1026000.00"),
                year.getEndingInvestableAssets());
    }
}