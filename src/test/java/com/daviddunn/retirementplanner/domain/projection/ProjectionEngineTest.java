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
                        1,
                        LocalDate.of(currentYear, 1, 1));

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
                        1,
                        LocalDate.of(currentYear, 1, 1));

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
                        2,
                        LocalDate.of(currentYear, 1, 1));
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
                        1,
                        LocalDate.of(currentYear, 1, 1));

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

    @Test
    void projectionProratesExpensesForMidYearStart() {

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
         * $3,000 × 6 months = $18,000.
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

        /*
         * $60,000 annual expenses.
         *
         * July through December = 6 months.
         *
         * First-year expenses should therefore
         * be $30,000.
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

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1,
                        LocalDate.of(
                                currentYear,
                                7,
                                1));

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

        /*
         * July through December:
         *
         * Expenses:       $30,000
         * Pension:        $18,000
         * Withdrawal:     $12,000
         *
         * Ending assets:
         * $1,000,000 - $12,000
         * = $988,000
         */

        assertEquals(
                new BigDecimal("30000.00"),
                year.getAnnualExpenses());

        assertEquals(
                new BigDecimal("18000"),
                year.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("12000.00"),
                year.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("988000.00"),
                year.getEndingInvestableAssets());
    }

    @Test
    void projectionProratesInvestmentGrowthForMidYearStart() {

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

        Household household =
                new Household(
                        primary,
                        spouse);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        /*
         * 6% annual investment return.
         * Projection begins July 1.
         *
         * July through December = 6 months.
         *
         * $1,000,000 × 6% × 6/12
         * = $30,000
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.06"),
                        BigDecimal.ZERO,
                        1,
                        LocalDate.of(
                                currentYear,
                                7,
                                1));

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
                new BigDecimal("30000.00"),
                year.getInvestmentGrowth());

        assertEquals(
                new BigDecimal("0.00"),
                year.getAnnualExpenses());

        assertEquals(
                BigDecimal.ZERO,
                year.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("0.00"),
                year.getPortfolioWithdrawal());

        assertEquals(
                new BigDecimal("1030000.00"),
                year.getEndingInvestableAssets());
    }

    @Test
    void projectionUsesPriorYearEndBalanceForRmd() {

        /*
         * Primary is born June 15, 1960.
         *
         * Under the government rules currently
         * loaded by ProjectionEngine, RMDs begin
         * at age 75 for this person.
         *
         * Therefore:
         *
         * 2034 -> no RMD yet
         * 2035 -> first RMD year
         */
        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(
                                1960,
                                6,
                                15));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(
                                1965,
                                2,
                                28));

        Household household =
                new Household(
                        primary,
                        spouse);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        /*
         * No investment growth.
         * No inflation.
         * No income.
         * No expenses.
         *
         * Therefore the portfolio remains exactly
         * $1,000,000 through 2034.
         *
         * We project two years:
         *
         * 2034
         * 2035
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        2,
                        LocalDate.of(
                                2034,
                                1,
                                1));

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
         * First projection year:
         *
         * We deliberately do not have a modeled
         * 12/31/2033 snapshot.
         *
         * Therefore RMD = $0.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        firstYear
                                .getRequiredMinimumDistribution()));

        /*
         * 2034 ends with $1,000,000 because there
         * is no growth and no withdrawal.
         *
         * That ending portfolio becomes the
         * 12/31/2034 RMD snapshot.
         */
        assertEquals(
                0,
                new BigDecimal("1000000.00")
                        .compareTo(
                                firstYear
                                        .getEndingInvestableAssets()));

        /*
         * In 2035 the primary owner is age 75.
         *
         * Uniform Lifetime Table divisor = 24.6
         *
         * $1,000,000 / 24.6
         * = $40,650.41
         *
         * This proves ProjectionEngine is using
         * the projected 12/31/2034 balance.
         */
        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                secondYear
                                        .getRequiredMinimumDistribution()));

        /*
         * RMD is currently REPORTING ONLY.
         *
         * It must not yet reduce ending assets.
         */
        assertEquals(
                0,
                new BigDecimal("1000000.00")
                        .compareTo(
                                secondYear
                                        .getEndingInvestableAssets()));

        /*
         * Nor should it currently be treated as
         * the portfolio withdrawal required to
         * fund expenses.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        secondYear
                                .getPortfolioWithdrawal()));
    }

}