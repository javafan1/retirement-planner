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
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;

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
        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        portfolio.addAccount(
                traditionalIra);

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

        assertEquals(
                0,
                new BigDecimal("976000.00")
                        .compareTo(
                                year.getEndingBalance(
                                        traditionalIra)));
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
         * The RMD now participates in the withdrawal
         * calculation.
         *
         * There is no cash-flow need in this test,
         * so the entire portfolio withdrawal is
         * caused by the RMD.
         */
        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                secondYear
                                        .getPortfolioWithdrawal()));

        /*
         * Beginning assets = $1,000,000.00
         * Growth           =          $0.00
         * RMD withdrawal   =     $40,650.41
         *                    ---------------
         * Ending assets    =    $959,349.59
         */
        assertEquals(
                0,
                new BigDecimal("1000000.00")
                        .compareTo(
                                secondYear
                                        .getEndingInvestableAssets()));

        /*
         * There is no cash-flow shortfall in this test,
         * so the entire portfolio withdrawal is caused
         * by the RMD.
         */
        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                secondYear
                                        .getPortfolioWithdrawal()));


        /*
         * With no cash-flow shortfall, the entire
         * RMD is an excess RMD.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        secondYear
                                .getCashFlowNeed()));


    }

    @Test
    void rmdReducesTraditionalIraButDoesNotReduceRothIra() {

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

        portfolio.addAccount(
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000")));

        /*
         * No growth, inflation, income, or expenses.
         *
         * We need three years:
         *
         * 2034 - no RMD
         * 2035 - first RMD
         * 2036 - proves the 2035 RMD came specifically
         *        from the Traditional IRA
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        3,
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

        ProjectionYear thirdYear =
                projection.getYearAt(2);

        /*
         * 2034:
         *
         * No prior modeled December 31 snapshot,
         * so there is no RMD.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        firstYear
                                .getRequiredMinimumDistribution()));

        /*
         * Total investable assets:
         *
         * Traditional IRA = $1,000,000
         * Roth IRA        =    500,000
         *                  ------------
         * Total           = $1,500,000
         */
        assertEquals(
                0,
                new BigDecimal("1500000")
                        .compareTo(
                                firstYear
                                        .getEndingInvestableAssets()));

        /*
         * 2035 RMD must be calculated only from the
         * $1,000,000 Traditional IRA.
         *
         * $1,000,000 / 24.6
         * = $40,650.41
         */
        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                secondYear
                                        .getRequiredMinimumDistribution()));

        /*
         * There are no expenses, so the entire RMD
         * is excess RMD and remains an investable
         * household asset as unallocated cash.
         *
         * Therefore total investable assets remain
         * $1,500,000.
         */
        assertEquals(
                0,
                new BigDecimal("1500000")
                        .compareTo(
                                secondYear
                                        .getEndingInvestableAssets()));

        /*
         * Internally, however, the 2035 ending
         * portfolio should now be:
         *
         * Traditional IRA = $959,349.59
         * Roth IRA        =  500,000.00
         * Excess RMD cash =   40,650.41
         *                  -------------
         * Total           = $1,500,000.00
         *
         * Therefore the 2036 RMD must use
         * $959,349.59 -- NOT $1,000,000 and
         * certainly not the entire $1,500,000.
         *
         * Age 76 divisor = 23.7.
         *
         * $959,349.59 / 23.7
         * = $40,478.89
         */
        assertEquals(
                0,
                new BigDecimal("40478.89")
                        .compareTo(
                                thirdYear
                                        .getRequiredMinimumDistribution()));
    }

    @Test
    void projectionCalculatesExcessRmdWhenRmdExceedsCashFlowNeed() {

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

        /*
         * Annual expenses = $30,000.
         *
         * There is no guaranteed income, so the
         * cash-flow need is also $30,000.
         */
        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("30000")));

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
         *
         * 2034 creates the prior 12/31 balance.
         * 2035 is the first RMD year.
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

        ProjectionYear secondYear =
                projection.getYearAt(1);

        /*
         * Cash-flow need:
         *
         * $30,000 expenses
         * - $0 guaranteed income
         * = $30,000
         */
        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                secondYear
                                        .getCashFlowNeed()));

        /*
         * RMD:
         *
         * $970,000 prior-year ending balance
         * / 24.6
         * = $39,430.89
         *
         * The prior-year balance is $970,000
         * because 2034 also required $30,000
         * for expenses.
         */
        assertEquals(
                0,
                new BigDecimal("39430.89")
                        .compareTo(
                                secondYear
                                        .getRequiredMinimumDistribution()));

        /*
         * RMD exceeds the cash-flow need,
         * so the RMD controls the withdrawal.
         */
        assertEquals(
                0,
                new BigDecimal("39430.89")
                        .compareTo(
                                secondYear
                                        .getPortfolioWithdrawal()));

        /*
         * Excess RMD:
         *
         * $39,430.89 - $30,000
         * = $9,430.89
         */
        assertEquals(
                0,
                new BigDecimal("9430.89")
                        .compareTo(
                                secondYear
                                        .getExcessRmd()));

        /*
         * Until taxes and withholding are modeled,
         * the entire excess RMD is potentially
         * available for reinvestment.
         */
        assertEquals(
                0,
                new BigDecimal("9430.89")
                        .compareTo(
                                secondYear
                                        .getReinvestableExcessRmd()));


    }

    @Test
    void projectionUsesSelectedWithdrawalStrategy() {

        /*
         * No guaranteed income.
         * Annual expenses = $30,000.
         *
         * Therefore the portfolio must supply
         * the entire $30,000.
         */

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

        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("30000")));

        /*
         * TAXABLE-FIRST PLAN
         */

        TraditionalIRA taxableFirstTraditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        BrokerageAccount taxableFirstBrokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio taxableFirstPortfolio =
                new AccountPortfolio();

        /*
         * Deliberately put Traditional IRA first.
         *
         * This ensures portfolio order cannot
         * accidentally make the test pass.
         */
        taxableFirstPortfolio.addAccount(
                taxableFirstTraditionalIra);

        taxableFirstPortfolio.addAccount(
                taxableFirstBrokerage);

        PlanningAssumptions taxableFirstAssumptions =
                new PlanningAssumptions(
                        new EconomicAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new WithdrawalAssumptions(
                                WithdrawalStrategyType.TAXABLE_FIRST),
                        1,
                        LocalDate.of(2026, 1, 1));

        RetirementPlan taxableFirstPlan =
                new RetirementPlan(
                        household,
                        taxableFirstPortfolio,
                        taxableFirstAssumptions);

        /*
         * TAX-DEFERRED-FIRST PLAN
         */

        TraditionalIRA taxDeferredFirstTraditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        BrokerageAccount taxDeferredFirstBrokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio taxDeferredFirstPortfolio =
                new AccountPortfolio();

        taxDeferredFirstPortfolio.addAccount(
                taxDeferredFirstTraditionalIra);

        taxDeferredFirstPortfolio.addAccount(
                taxDeferredFirstBrokerage);

        PlanningAssumptions taxDeferredFirstAssumptions =
                new PlanningAssumptions(
                        new EconomicAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new WithdrawalAssumptions(
                                WithdrawalStrategyType.TAX_DEFERRED_FIRST),
                        1,
                        LocalDate.of(2026, 1, 1));

        RetirementPlan taxDeferredFirstPlan =
                new RetirementPlan(
                        household,
                        taxDeferredFirstPortfolio,
                        taxDeferredFirstAssumptions);

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection taxableFirstProjection =
                engine.project(
                        taxableFirstPlan);

        Projection taxDeferredFirstProjection =
                engine.project(
                        taxDeferredFirstPlan);

        ProjectionYear taxableFirstYear =
                taxableFirstProjection.getYearAt(0);

        ProjectionYear taxDeferredFirstYear =
                taxDeferredFirstProjection.getYearAt(0);

        /*
         * Both plans require the same $30,000
         * portfolio withdrawal.
         */
        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                taxableFirstYear
                                        .getPortfolioWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                taxDeferredFirstYear
                                        .getPortfolioWithdrawal()));

        /*
         * TAXABLE_FIRST
         *
         * Brokerage supplies the entire $30,000.
         *
         * Traditional IRA = $100,000
         * Brokerage       =  $70,000
         */
        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(
                                taxableFirstYear
                                        .getEndingBalance(
                                                taxableFirstTraditionalIra)));

        assertEquals(
                0,
                new BigDecimal("70000")
                        .compareTo(
                                taxableFirstYear
                                        .getEndingBalance(
                                                taxableFirstBrokerage)));

        /*
         * TAX_DEFERRED_FIRST
         *
         * Traditional IRA supplies the entire $30,000.
         *
         * Traditional IRA = $70,000
         * Brokerage       = $100,000
         */
        assertEquals(
                0,
                new BigDecimal("70000")
                        .compareTo(
                                taxDeferredFirstYear
                                        .getEndingBalance(
                                                taxDeferredFirstTraditionalIra)));

        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(
                                taxDeferredFirstYear
                                        .getEndingBalance(
                                                taxDeferredFirstBrokerage)));

        /*
         * Strategy changes account selection,
         * but not the aggregate withdrawal or
         * total ending assets.
         *
         * $200,000 - $30,000 = $170,000
         */
        assertEquals(
                0,
                new BigDecimal("170000.00")
                        .compareTo(
                                taxableFirstYear
                                        .getEndingInvestableAssets()));

        assertEquals(
                0,
                new BigDecimal("170000.00")
                        .compareTo(
                                taxDeferredFirstYear
                                        .getEndingInvestableAssets()));
    }


}