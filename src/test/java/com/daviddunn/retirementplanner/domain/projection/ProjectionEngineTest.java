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

import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                0,
                new BigDecimal("24000.00")
                        .compareTo(
                                year.getCashFlowNeed()));
        assertTrue(
                year
                        .getTaxFundingWithdrawal()
                        .signum() > 0);

        assertEquals(
                0,
                year
                        .getCashFlowNeed()
                        .add(
                                year
                                        .getTaxFundingWithdrawal())
                        .compareTo(
                                year
                                        .getPortfolioWithdrawal()));


        BigDecimal expectedEndingAssets =
                year
                        .getBeginningInvestableAssets()
                        .add(
                                year.getInvestmentGrowth())
                        .subtract(
                                year.getPortfolioWithdrawal())
                        .add(
                                year.getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        year
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));


        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        year
                                .getEndingBalance(
                                        traditionalIra)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));
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
                new BigDecimal("60000.00"),
                year.getAnnualExpenses());

        assertEquals(
                0,
                new BigDecimal("42000.00")
                        .compareTo(
                                year.getCashFlowNeed()));

        assertTrue(
                year.getTaxFundingWithdrawal()
                        .signum() > 0);

        assertEquals(
                0,
                year.getCashFlowNeed()
                        .add(
                                year.getTaxFundingWithdrawal())
                        .compareTo(
                                year.getPortfolioWithdrawal()));

        BigDecimal expectedEndingAssets =
                year.getBeginningInvestableAssets()
                        .add(
                                year.getInvestmentGrowth())
                        .subtract(
                                year.getPortfolioWithdrawal())
                        .add(
                                year.getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        year.getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));
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

        /*
         * YEAR 1
         */

        assertEquals(
                new BigDecimal("36000"),
                firstYear.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("60000.00"),
                firstYear.getAnnualExpenses());

        assertEquals(
                0,
                new BigDecimal("24000.00")
                        .compareTo(
                                firstYear.getCashFlowNeed()));

        assertTrue(
                firstYear
                        .getTaxFundingWithdrawal()
                        .signum() > 0);

        assertEquals(
                0,
                firstYear
                        .getCashFlowNeed()
                        .add(
                                firstYear
                                        .getTaxFundingWithdrawal())
                        .compareTo(
                                firstYear
                                        .getPortfolioWithdrawal()));

        BigDecimal expectedFirstYearEndingAssets =
                firstYear
                        .getBeginningInvestableAssets()
                        .add(
                                firstYear.getInvestmentGrowth())
                        .subtract(
                                firstYear.getPortfolioWithdrawal())
                        .add(
                                firstYear.getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedFirstYearEndingAssets.compareTo(
                        firstYear
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));

        /*
         * YEAR 2
         */

        assertEquals(
                new BigDecimal("36720.00"),
                secondYear.getGuaranteedIncome());

        assertEquals(
                new BigDecimal("61800.00"),
                secondYear.getAnnualExpenses());

        assertEquals(
                0,
                new BigDecimal("25080.00")
                        .compareTo(
                                secondYear.getCashFlowNeed()));

        assertTrue(
                secondYear
                        .getTaxFundingWithdrawal()
                        .signum() > 0);

        assertEquals(
                0,
                secondYear
                        .getCashFlowNeed()
                        .add(
                                secondYear
                                        .getTaxFundingWithdrawal())
                        .compareTo(
                                secondYear
                                        .getPortfolioWithdrawal()));

        BigDecimal expectedSecondYearEndingAssets =
                secondYear
                        .getBeginningInvestableAssets()
                        .add(
                                secondYear.getInvestmentGrowth())
                        .subtract(
                                secondYear.getPortfolioWithdrawal())
                        .add(
                                secondYear.getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedSecondYearEndingAssets.compareTo(
                        secondYear
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));
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
                0,
                new BigDecimal("24000.00")
                        .compareTo(
                                year.getCashFlowNeed()));

        assertTrue(
                year.getTaxFundingWithdrawal()
                        .signum() > 0);

        assertEquals(
                0,
                year.getCashFlowNeed()
                        .add(
                                year.getTaxFundingWithdrawal())
                        .compareTo(
                                year.getPortfolioWithdrawal()));

        BigDecimal expectedEndingAssets =
                year.getBeginningInvestableAssets()
                        .add(
                                year.getInvestmentGrowth())
                        .subtract(
                                year.getPortfolioWithdrawal())
                        .add(
                                year.getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        year.getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));
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

        assertEquals(
                0,
                secondYear
                        .getRequiredMinimumDistribution()
                        .add(
                                secondYear
                                        .getTaxFundingWithdrawal())
                        .compareTo(
                                secondYear
                                        .getPortfolioWithdrawal()));
        BigDecimal expectedEndingAssets =
                new BigDecimal("1000000.00")
                        .subtract(
                                secondYear
                                        .getTaxFundingWithdrawal())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        secondYear
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));


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

        /*
         * Keep references to the individual accounts
         * so we can inspect their projected ending
         * balances.
         */
        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000"));

        portfolio.addAccount(
                traditionalIra);

        portfolio.addAccount(
                rothIra);

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
         * The RMD itself is taxable income.
         *
         * Because there are no living expenses, the
         * entire RMD is excess RMD and remains an
         * investable household asset.
         *
         * Federal income tax, however, is a real
         * household outflow and therefore reduces
         * total investable assets.
         */
        assertTrue(
                secondYear
                        .getTaxFundingWithdrawal()
                        .signum() > 0);

        BigDecimal expectedEndingAssets =
                new BigDecimal("1500000")
                        .subtract(
                                secondYear
                                        .getTaxFundingWithdrawal())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        secondYear
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));

        /*
         * The Roth IRA must remain untouched.
         */
        assertEquals(
                0,
                new BigDecimal("500000")
                        .compareTo(
                                secondYear
                                        .getEndingBalance(
                                                rothIra)));

        /*
         * The Traditional IRA pays both the RMD and
         * the tax-funding withdrawal.
         */
        BigDecimal expectedTraditionalIraBalance =
                new BigDecimal("1000000")
                        .subtract(
                                secondYear
                                        .getRequiredMinimumDistribution())
                        .subtract(
                                secondYear
                                        .getTaxFundingWithdrawal());

        assertEquals(
                0,
                expectedTraditionalIraBalance
                        .setScale(
                                2,
                                RoundingMode.HALF_UP)
                        .compareTo(
                                secondYear
                                        .getEndingBalance(
                                                traditionalIra)
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP)));

        /*
         * The following year's RMD must therefore use
         * the actual ending Traditional IRA balance,
         * not the original $1,000,000 balance and not
         * total household assets.
         *
         * Age 76 divisor = 23.7.
         */
        BigDecimal expectedNextRmd =
                secondYear
                        .getEndingBalance(
                                traditionalIra)
                        .divide(
                                new BigDecimal("23.7"),
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedNextRmd.compareTo(
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

        ProjectionYear firstYear =
                projection.getYearAt(0);

        System.out.println("First year withdrawal = "
                + firstYear.getPortfolioWithdrawal());

        System.out.println("First year ending assets = "
                + firstYear.getEndingInvestableAssets());

        System.out.println("Second year RMD = "
                + secondYear.getRequiredMinimumDistribution());
        /*
         * Prior-year Traditional IRA balance is
         * $1,000,000.
         *
         * At age 75 the Uniform Lifetime Table
         * divisor is 24.6:
         *
         * $1,000,000 / 24.6 = $40,650.41
         */
        BigDecimal expectedRmd =
                firstYear
                        .getEndingBalance(
                                traditionalIra)
                        .divide(
                                new BigDecimal("24.6"),
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedRmd.compareTo(
                        secondYear
                                .getRequiredMinimumDistribution()));

        System.out.println(
                "Second year cash flow need = "
                        + secondYear.getCashFlowNeed());

        /*
         * Household expenses are $30,000 and there
         * is no guaranteed income.
         */
        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                secondYear
                                        .getCashFlowNeed()));

        /*
         * The RMD exceeds the household's spending
         * need.
         *
         * $40,650.41 - $10,000.00
         * = $30,650.41 excess RMD.
         */
        BigDecimal expectedExcessRmd =
                secondYear
                        .getRequiredMinimumDistribution()
                        .subtract(
                                secondYear
                                        .getCashFlowNeed());

        assertEquals(
                0,
                expectedExcessRmd.compareTo(
                        secondYear
                                .getExcessRmd()));

        /*
         * The RMD is taxable income, so federal tax
         * must also be funded.
         */
        assertTrue(
                secondYear
                        .getTaxFundingWithdrawal()
                        .signum() > 0);

        /*
         * Total portfolio distributions consist of
         * the RMD plus the additional withdrawal
         * needed to fund federal tax.
         */
        BigDecimal expectedPortfolioWithdrawal =
                secondYear
                        .getRequiredMinimumDistribution()
                        .add(
                                secondYear
                                        .getTaxFundingWithdrawal());

        assertEquals(
                0,
                expectedPortfolioWithdrawal.compareTo(
                        secondYear
                                .getPortfolioWithdrawal()));

        /*
         * The excess portion of the RMD remains an
         * investable household asset.
         *
         * Therefore:
         *
         * beginning assets
         * + investment growth
         * - total portfolio withdrawal
         * + excess RMD
         *
         * gives ending investable assets.
         */
        BigDecimal expectedEndingAssets =
                secondYear
                        .getBeginningInvestableAssets()
                        .add(
                                secondYear
                                        .getInvestmentGrowth())
                        .subtract(
                                secondYear
                                        .getPortfolioWithdrawal())
                        .add(
                                secondYear
                                        .getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        secondYear
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));

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
                                        .getCashFlowNeed()));

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                taxDeferredFirstYear
                                        .getCashFlowNeed()));


        assertEquals(
                BigDecimal.ZERO,
                taxableFirstYear.getAdjustedGrossIncome());

        assertEquals(
                new BigDecimal("30000.00"),
                taxDeferredFirstYear.getAdjustedGrossIncome());

        assertEquals(
                BigDecimal.ZERO,
                taxableFirstYear.getFederalIncomeTax());

        assertEquals(
                BigDecimal.ZERO,
                taxDeferredFirstYear.getFederalIncomeTax());

        assertEquals(
                BigDecimal.ZERO,
                taxableFirstYear.getTaxFundingWithdrawal());

        assertEquals(
                BigDecimal.ZERO,
                taxDeferredFirstYear.getTaxFundingWithdrawal());
        assertEquals(
                0,
                taxableFirstYear
                        .getCashFlowNeed()
                        .add(
                                taxableFirstYear
                                        .getTaxFundingWithdrawal())
                        .compareTo(
                                taxableFirstYear
                                        .getPortfolioWithdrawal()));

        assertEquals(
                0,
                taxDeferredFirstYear
                        .getCashFlowNeed()
                        .add(
                                taxDeferredFirstYear
                                        .getTaxFundingWithdrawal())
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


    /*
    ProjectionEngine
      ↓
Pension income                    $18,000
      +
Traditional IRA withdrawal        $25,000
      ↓
TaxIncome                         $43,000
      ↓
FederalTaxCalculator
      ↓
ProjectionYear
      ├─ AGI                      $43,000
      ├─ Taxable Social Security       $0
      ├─ Taxable income           $10,800
      └─ Federal tax               $1,080
     */

    @Test
    void projectionStoresFederalTaxCalculation() {

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
         * $1,500/month pension:
         *
         * $1,500 × 12 = $18,000.
         */
        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("1500"),
                        BigDecimal.ZERO));

        Household household =
                new Household(
                        primary,
                        spouse);

        /*
         * Expenses are $43,000.
         *
         * Pension supplies $18,000,
         * leaving a $25,000 portfolio need.
         */
        household.addExpense(
                new Expense(
                        "Living Expenses",
                        new BigDecimal("43000")));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        /*
         * Because we use TAX_DEFERRED_FIRST below,
         * the entire $25,000 withdrawal will come
         * from this Traditional IRA.
         */
        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        portfolio.addAccount(
                traditionalIra);

        PlanningAssumptions assumptions =
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

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(
                        plan);

        ProjectionYear year =
                projection.getYearAt(0);


        /*
         * Ordinary taxable income:
         *
         * Pension                 $18,000
         * Traditional IRA         25,000
         *                         -------
         * AGI                     $43,000
         *
         * There is no Social Security.
         */
        assertEquals(
                0,
                new BigDecimal("44200.00")
                        .compareTo(
                                year.getAdjustedGrossIncome()
                                        .setScale(2, RoundingMode.HALF_UP)));

        assertEquals(
                0,
                new BigDecimal("12000.00")
                        .compareTo(
                                year.getFederalTaxableIncome()
                                        .setScale(2, RoundingMode.HALF_UP)));

        assertEquals(
                0,
                new BigDecimal("1200.00")
                        .compareTo(
                                year.getFederalIncomeTax()
                                        .setScale(2, RoundingMode.HALF_UP)));

        assertEquals(
                0,
                new BigDecimal("1200.00")
                        .compareTo(
                                year.getTaxFundingWithdrawal()
                                        .setScale(2, RoundingMode.HALF_UP)));
    }
    @Test
    void projectionAppliesOneTimeRothConversion() {

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

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                traditionalIra);

        portfolio.addAccount(
                rothIra);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        2,
                        LocalDate.of(2026, 1, 1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        plan.setRothConversionRequest(
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy.FIXED_AMOUNT,
                        RothConversionFrequency.ONE_TIME));

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear firstYear =
                projection.getYearAt(0);

        ProjectionYear secondYear =
                projection.getYearAt(1);

        /*
         * 2026:
         *
         * A one-time $50,000 Roth conversion occurs.
         */
        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                firstYear.getRothConversion()));

        /*
         * The $50,000 conversion is taxable income
         * and therefore creates federal income tax.
         */
        assertTrue(
                firstYear
                        .getFederalIncomeTax()
                        .compareTo(BigDecimal.ZERO) > 0);

        /*
         * This test scenario produces no Michigan
         * income tax.
         */
        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                firstYear
                                        .getMichiganIncomeTax()));

        /*
         * Federal tax is funded by an additional
         * portfolio withdrawal.
         */
        /*
         * Federal tax is funded by an additional
         * portfolio withdrawal.
         */
        assertTrue(
                firstYear
                        .getFederalIncomeTax()
                        .subtract(
                                firstYear.getTaxFundingWithdrawal())
                        .abs()
                        .compareTo(
                                new BigDecimal("0.01")) < 0);

        /*
         * The Roth IRA receives the full $50,000
         * conversion.
         */
        assertEquals(
                0,
                new BigDecimal("150000")
                        .compareTo(
                                firstYear.getEndingBalance(
                                        rothIra)));

        /*
         * The Traditional IRA is reduced by:
         *
         * $50,000 conversion
         * + $1,977.77758 tax funding
         *
         * = $48,022.22242 remaining.
         */
        assertEquals(
                0,
                new BigDecimal("48022.22242")
                        .compareTo(
                                firstYear.getEndingBalance(
                                        traditionalIra)));

        /*
         * Total ending assets are reduced only by
         * the taxes. The conversion itself is a
         * transfer between accounts.
         */
        assertEquals(
                0,
                new BigDecimal("198022.22")
                        .compareTo(
                                firstYear
                                        .getEndingInvestableAssets()));

        /*
         * 2027:
         *
         * The conversion was one-time, so there is
         * no second $50,000 conversion.
         */
        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                secondYear.getRothConversion()));

        /*
         * There is no Roth-conversion tax funding
         * withdrawal in 2027.
         */
        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                secondYear
                                        .getTaxFundingWithdrawal()));


    }

    @Test
    void projectionAppliesAnnualRothConversionsUntilFirstHouseholdRmd() {

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

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                traditionalIra);

        portfolio.addAccount(
                rothIra);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        3,
                        LocalDate.of(2026, 1, 1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        plan.setRothConversionRequest(
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy.FIXED_AMOUNT,
                        RothConversionFrequency.ANNUAL));

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
         * A $50,000 Roth conversion should occur
         * in each of the three projection years.
         */
        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                firstYear.getRothConversion()));

        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                secondYear.getRothConversion()));

        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                thirdYear.getRothConversion()));

        /*
         * Roth IRA:
         *
         * Starting balance = $100,000
         *
         * 2026 = $150,000
         * 2027 = $200,000
         * 2028 = $250,000
         */
        assertEquals(
                0,
                new BigDecimal("150000")
                        .compareTo(
                                firstYear.getEndingBalance(
                                        rothIra)));

        assertEquals(
                0,
                new BigDecimal("200000")
                        .compareTo(
                                secondYear.getEndingBalance(
                                        rothIra)));

        assertEquals(
                0,
                new BigDecimal("250000")
                        .compareTo(
                                thirdYear.getEndingBalance(
                                        rothIra)));

        /*
         * The Traditional IRA is reduced by both
         * the Roth conversion and the additional
         * withdrawal used to fund the resulting
         * federal income tax.
         *
         * Each year:
         *
         * $50,000 conversion
         * $1,977.77758 tax funding withdrawal
         *
         * After three years:
         *
         * $500,000
         * - $150,000 conversions
         * - $5,933.33274 tax funding
         * = $344,066.66726
         */
        assertEquals(
                0,
                new BigDecimal("448022.22242000000000")
                        .compareTo(
                                firstYear.getEndingBalance(
                                        traditionalIra)));

        assertEquals(
                0,
                new BigDecimal("396044.44484000000000")
                        .compareTo(
                                secondYear.getEndingBalance(
                                        traditionalIra)));

        assertEquals(
                0,
                new BigDecimal("344066.66726000000000")
                        .compareTo(
                                thirdYear.getEndingBalance(
                                        traditionalIra)));
    }

    @Test
    void annualRothConversionStopsInFirstHouseholdRmdYear() {

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

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                traditionalIra);

        portfolio.addAccount(
                rothIra);

        /*
         * Project from 2026 through 2038.
         *
         * 2026 = year 0
         * 2037 = year 11
         * 2038 = year 12
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        13,
                        LocalDate.of(2026, 1, 1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        plan.setRothConversionRequest(
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy.FIXED_AMOUNT,
                        RothConversionFrequency.ANNUAL));

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        /*
         * 2037 is the year immediately before
         * David becomes subject to RMDs.
         */
        ProjectionYear year2037 =
                projection.getYearAt(11);

        /*
         * 2038 is the first household RMD year.
         */
        ProjectionYear year2038 =
                projection.getYearAt(12);

        /*
         * The annual Roth conversion should still
         * occur in the year immediately before RMDs.
         */
        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                year2037.getRothConversion()));

        /*
         * The conversion must stop in the first
         * household RMD year.
         */
        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                year2038.getRothConversion()));

        /*
         * Once RMDs begin, the Traditional IRA
         * should have an RMD for the year.
         */
        assertTrue(
                year2038
                        .getRequiredMinimumDistribution()
                        .signum() > 0);
    }

    @Test
    void projectionFills22PercentFederalBracket() {

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

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                traditionalIra);

        portfolio.addAccount(
                rothIra);

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
                        LocalDate.of(2026, 1, 1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        plan.setRothConversionRequest(
                new RothConversionRequest(
                        true,
                        2026,
                        BigDecimal.ZERO,
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                        RothConversionFrequency.ONE_TIME));

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear year =
                projection.getYearAt(0);



        assertEquals(
                0,
                new BigDecimal("200779.77")
                        .compareTo(
                                year.getRothConversion()
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP)));

        BigDecimal taxableIncomeDifference =
                new BigDecimal("211400.00")
                        .subtract(
                                year.getFederalTaxableIncome())
                        .abs();

        assertTrue(
                taxableIncomeDifference.compareTo(
                        new BigDecimal("0.01")) <= 0);

        assertEquals(
                0,
                new BigDecimal("300779.77")
                        .compareTo(
                                year.getEndingBalance(rothIra)
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP)));


    }

}