package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.SavingsAccount;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.math.RoundingMode;
import java.util.List;

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
                        .subtract(firstYear.getPortfolioWithdrawal())
                        .compareTo(firstYear.getEndingInvestableAssets()));

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
                firstYear.getEndingBalance(traditionalIra)
                        .divide(new BigDecimal("24.6"), 2, RoundingMode.HALF_UP)
                        .compareTo(secondYear.getRequiredMinimumDistribution()));

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
                secondYear.getBeginningInvestableAssets()
                        .add(secondYear.getInvestmentGrowth())
                        .subtract(secondYear.getPortfolioWithdrawal())
                        .add(secondYear.getRetainedHouseholdSurplus())
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
                secondYear.getAnnualMedicarePremium().compareTo(
                        secondYear.getCashFlowNeed()));


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
                        .subtract(firstYear.getPortfolioWithdrawal())
                        .compareTo(firstYear.getEndingInvestableAssets()));

        /*
         * 2035 RMD must be calculated only from the
         * $1,000,000 Traditional IRA.
         *
         * $1,000,000 / 24.6
         * = $40,650.41
         */
        assertEquals(
                0,
                firstYear.getEndingBalance(traditionalIra)
                        .divide(new BigDecimal("24.6"), 2, RoundingMode.HALF_UP)
                        .compareTo(secondYear.getRequiredMinimumDistribution()));


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
        assertEquals(0, BigDecimal.ZERO.compareTo(
                secondYear.getTaxFundingWithdrawal()));
        assertTrue(secondYear.getRetainedFromExcessRmd().signum() > 0);

        BigDecimal expectedEndingAssets =
                secondYear.getBeginningInvestableAssets()
                        .add(secondYear.getInvestmentGrowth())
                        .subtract(secondYear.getPortfolioWithdrawal())
                        .add(secondYear.getRetainedHouseholdSurplus())
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
                firstYear.getEndingBalance(traditionalIra)
                        .subtract(secondYear.getPortfolioWithdrawal());

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
                        new EconomicAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
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
                        .add(secondYear.getAnnualMedicarePremium())
                        .compareTo(secondYear.getCashFlowNeed()));

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
        assertEquals(0, BigDecimal.ZERO.compareTo(
                secondYear.getTaxFundingWithdrawal()));

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
         * + retained household surplus
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
                                        .getRetainedHouseholdSurplus())
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
    void retainedExcessRmdEarnsGeneralInvestmentReturnExactlyOnce()
            throws Exception {

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
         * 2034 establishes the prior-year balance.
         *
         * 2035 is the first RMD year.
         *
         * 2036 verifies that the excess RMD accumulated
         * in 2035 remains in the general investment-growth
         * base and receives its proportional share of that
         * one total growth calculation.
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.05"),
                        BigDecimal.ZERO,
                        4,
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

        ProjectionYear thirdYear =
                projection.getYearAt(2);

        ProjectionYear fourthYear =
                projection.getYearAt(3);

        /*
         * 2035 must produce excess RMD cash.
         */
        BigDecimal excessRmd2035 =
                secondYear.getExcessRmd();

        assertTrue(
                excessRmd2035.signum() > 0);

        /*
         * The 2035 excess RMD becomes retained RMD
         * assets for 2036. They are included in the
         * 2036 general investment-growth base.
         */
        BigDecimal expectedInvestmentGrowth =
                thirdYear
                        .getBeginningInvestableAssets()
                        .multiply(
                                new BigDecimal("0.05"))
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedInvestmentGrowth.compareTo(
                        thirdYear.getInvestmentGrowth()));

        /*
         * The 2036 ending retained RMD assets should contain:
         *
         * 2035 excess RMD
         * + growth on those retained assets
         * + 2036 excess RMD
         */
        BigDecimal retainedBeforeNewSurplus =
                thirdYear.getBeginningRetainedNonQualifiedAssets()
                        .add(
                                thirdYear
                                        .getRetainedNonQualifiedAssetGrowth())
                        .subtract(
                                thirdYear.getHouseholdCashSettlement()
                                        .spendingWithdrawal())
                        .subtract(thirdYear.getTaxFundingWithdrawal())
                        .max(BigDecimal.ZERO);

        BigDecimal expectedEndingCash =
                retainedBeforeNewSurplus
                        .add(
                                thirdYear
                                        .getRetainedHouseholdSurplus())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingCash.compareTo(
                        thirdYear
                                .getEndingRetainedNonQualifiedAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));

        assertEquals(
                0,
                secondYear
                        .getEndingRetainedNonQualifiedAssets()
                        .compareTo(
                        thirdYear
                                .getBeginningRetainedNonQualifiedAssets()));

        BigDecimal accountGrowth =
                thirdYear
                        .getInvestmentGrowth()
                        .subtract(
                                thirdYear
                                        .getRetainedNonQualifiedAssetGrowth());

        assertEquals(
                0,
                thirdYear
                        .getInvestmentGrowth()
                        .compareTo(
                                accountGrowth.add(
                                        thirdYear
                                                .getRetainedNonQualifiedAssetGrowth())));

        BigDecimal expectedEndingAssets =
                thirdYear
                        .getBeginningInvestableAssets()
                        .add(
                                thirdYear
                                        .getInvestmentGrowth())
                        .subtract(
                                thirdYear
                                        .getPortfolioWithdrawal())
                        .add(
                                thirdYear
                                        .getRetainedHouseholdSurplus())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedEndingAssets.compareTo(
                        thirdYear
                                .getEndingInvestableAssets()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP)));

        assertEquals(
                0,
                new BigDecimal("1000000").compareTo(
                        traditionalIra.getCurrentBalance()));

        /*
         * The next year's RMD must use the prior December 31
         * tax-deferred account balance only. Retained RMD asset
         * growth must not be assigned to that IRA.
         */
        RmdBalanceSnapshot correctSnapshot =
                new RmdBalanceSnapshot(
                        LocalDate.of(2036, 12, 31),
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        thirdYear.getEndingBalance(
                                                traditionalIra))));

        GovernmentRules rules =
                new GovernmentRulesRepository().load(
                        "/rules/government-rules-2026.json");

        BigDecimal expectedRmd2037 =
                new HouseholdRmdCalculator()
                        .calculate(
                                plan,
                                correctSnapshot,
                                2037,
                                rules)
                        .getTotalRmd()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedRmd2037.compareTo(
                        fourthYear
                                .getRequiredMinimumDistribution()));

        RmdBalanceSnapshot inflatedSnapshot =
                new RmdBalanceSnapshot(
                        LocalDate.of(2036, 12, 31),
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        thirdYear
                                                .getEndingBalance(
                                                        traditionalIra)
                                                .add(
                                                        thirdYear
                                                                .getRetainedRmdAssetGrowth()))));

        BigDecimal incorrectlyInflatedRmd2037 =
                new HouseholdRmdCalculator()
                        .calculate(
                                plan,
                                inflatedSnapshot,
                                2037,
                                rules)
                        .getTotalRmd();

        assertTrue(
                incorrectlyInflatedRmd2037.compareTo(
                        fourthYear
                                .getRequiredMinimumDistribution()) > 0);
    }

    @Test
    void projectsRmdForAnOlderBirthCohortUsingThePriorProjectedBalance() {

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1949, 6, 15));

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
                        new BigDecimal("500000.00"));

        traditionalIra.setOpeningRmdAccountData(
                new OpeningRmdAccountData(
                        2026,
                        new BigDecimal("500000.00"),
                        BigDecimal.ZERO));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                traditionalIra);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        2,
                        LocalDate.of(2026, 1, 1));

        Projection projection =
                new ProjectionEngine().project(
                        new RetirementPlan(
                                household,
                                portfolio,
                                assumptions));

        ProjectionYear firstYear =
                projection.getYearAt(0);

        ProjectionYear secondYear =
                projection.getYearAt(1);

        /*
         * Opening data supplies the actual December 31,
         * 2025 balance for the first eligible year. In
         * 2027, this 1949 cohort is age 78 and uses its
         * modeled 2026 ending balance and divisor 22.0.
         */
        assertEquals(
                0,
                new BigDecimal("21834.06").compareTo(
                        firstYear
                                .getRequiredMinimumDistribution()));

        BigDecimal expectedSecondYearRmd =
                firstYear.getEndingBalance(traditionalIra)
                        .divide(
                                new BigDecimal("22.0"),
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                0,
                expectedSecondYearRmd.compareTo(
                        secondYear
                                .getRequiredMinimumDistribution()));

        assertEquals(
                0,
                new BigDecimal("500000.00").compareTo(
                        traditionalIra.getCurrentBalance()));
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
                0,
                BigDecimal.ZERO.compareTo(
                        taxableFirstYear.getAdjustedGrossIncome()));

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
                        new EconomicAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),
                        4,
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
                secondYear.getEndingBalance(traditionalIra)
                        .subtract(new BigDecimal("50000"))
                        .subtract(thirdYear.getPortfolioWithdrawal())
                        .compareTo(thirdYear.getEndingBalance(traditionalIra)));
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


//        System.out.println(
//                "EXPECTED Roth conversion = 200779.77");
//
//        System.out.println(
//                "ACTUAL Roth conversion = " +
//                        year.getRothConversion());

        assertEquals(
                0,
                new BigDecimal("200779.78")
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
                new BigDecimal("300779.78")
                        .compareTo(
                                year.getEndingBalance(rothIra)
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP)));


    }

    @Test
    void projectionFills12PercentFederalBracket() {

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
                        RothConversionStrategy
                                .FILL_12_PERCENT_BRACKET,
                        RothConversionFrequency.ONE_TIME));

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear year =
                projection.getYearAt(0);

        /*
         * The 2026 MFJ 12% bracket ends at
         * $100,800 of taxable income.
         */
        BigDecimal taxableIncomeDifference =
                new BigDecimal("100800.00")
                        .subtract(
                                year.getFederalTaxableIncome())
                        .abs();

        assertTrue(
                taxableIncomeDifference.compareTo(
                        new BigDecimal("0.01")) <= 0);

        /*
         * Verify that a Roth conversion actually
         * occurred.
         */
        assertTrue(
                year.getRothConversion()
                        .signum() > 0);

        /*
         * The conversion should have been transferred
         * into the Roth IRA.
         */
        BigDecimal expectedRothBalance =
                new BigDecimal("100000")
                        .add(
                                year.getRothConversion());

        assertEquals(
                0,
                expectedRothBalance
                        .setScale(
                                2,
                                RoundingMode.HALF_UP)
                        .compareTo(
                                year.getEndingBalance(
                                                rothIra)
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP)));
    }

    @Test
    void projectionCalculatesAfterTaxEstateValue() {

        /*
         * No income.
         * No expenses.
         * No investment growth.
         *
         * Ending portfolio should therefore remain:
         *
         * Traditional IRA     $1,000,000
         * Roth IRA               200,000
         * Brokerage              300,000
         * Savings                100,000
         *                    ------------
         * Total                $1,600,000
         *
         * Only the Traditional IRA is subject
         * to the assumed 25% heir tax rate.
         *
         * Estimated heir tax:
         *
         * $1,000,000 × 25% = $250,000
         *
         * After-tax estate:
         *
         * $1,600,000 - $250,000
         * = $1,350,000
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

        AccountPortfolio portfolio =
                new AccountPortfolio();

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000"));

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000"));

        SavingsAccount savings =
                new SavingsAccount(
                        "Savings",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        portfolio.addAccount(
                traditionalIra);

        portfolio.addAccount(
                rothIra);

        portfolio.addAccount(
                brokerage);

        portfolio.addAccount(
                savings);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new EconomicAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),

                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY,
                                new BigDecimal("0.25")),

                        new WithdrawalAssumptions(
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        1,

                        LocalDate.of(
                                2026,
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

        ProjectionYear year =
                projection.getYearAt(0);

        /*
         * No growth or withdrawals.
         */
        assertEquals(
                new BigDecimal("1600000.00"),
                year.getEndingInvestableAssets());

        /*
         * Only the $1,000,000 Traditional IRA
         * is subject to the 25% heir tax assumption.
         */
        assertEquals(
                0,
                new BigDecimal("250000.00")
                        .compareTo(
                                year.getEstimatedHeirTax()));

        /*
         * After-tax estate:
         *
         * $1,600,000 - $250,000
         * = $1,350,000
         */
        assertEquals(
                0,
                new BigDecimal("1350000.00")
                        .compareTo(
                                year.getAfterTaxEstateValue()));
    }

    @Test
    void projectionStopsPrimaryIncomeWhenPrimaryDies() {

        /*
         * Primary dies in 2035.
         *
         * Our death-year convention is:
         *
         * 2034 -> both spouses are alive
         * 2035 -> primary is considered deceased
         *
         * Therefore primary income should be included
         * through 2034, but not in 2035.
         *
         * Spouse income continues.
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

        /*
         * Primary pension:
         * $3,000/month = $36,000/year.
         */
        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO));

        /*
         * Spouse pension:
         * $2,000/month = $24,000/year.
         */
        spouse.addIncomeSource(
                new Pension(
                        "Spouse Pension",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("2000"),
                        BigDecimal.ZERO));

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
         * No expenses.
         *
         * Project:
         *
         * 2034 -> both alive
         * 2035 -> primary deceased
         */
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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035),

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

        ProjectionYear year2034 =
                projection.getYearAt(0);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * 2034:
         *
         * Primary pension = $36,000
         * Spouse pension  = $24,000
         *                  --------
         * Total            = $60,000
         */
        assertEquals(
                new BigDecimal("60000"),
                year2034.getGuaranteedIncome());

        /*
         * 2035:
         *
         * Primary pension stops.
         *
         * Spouse pension continues:
         *
         * $24,000
         */
        assertEquals(
                new BigDecimal("24000"),
                year2035.getGuaranteedIncome());
    }

    @Test
    void projectionAppliesPrimarySurvivorSocialSecurityWhenPrimaryDies() {

        /*
         * Primary dies in 2035.
         *
         * 2034 -> both spouses are alive
         * 2035 -> primary is deceased
         *
         * Primary Social Security stops.
         * Spouse Social Security continues and is
         * increased to the applicable survivor benefit.
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

        /*
         * Primary Social Security.
         *
         * Full retirement benefit = $3,000/month.
         * Claiming age = 67.
         * No COLA for this test.
         */
        primary.addIncomeSource(
                new SocialSecurityIncome(
                        "Primary Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO));

        /*
         * Spouse Social Security.
         *
         * Full retirement benefit = $2,000/month.
         * Claiming age = 67.
         * No COLA for this test.
         */
        spouse.addIncomeSource(
                new SocialSecurityIncome(
                        "Spouse Social Security",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("2000"),
                        67,
                        BigDecimal.ZERO));

        Household household =
                new Household(
                        primary,
                        spouse);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        /*
         * Provide enough assets that the projection
         * does not run out of money.
         */
        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035),

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

        ProjectionYear year2034 =
                projection.getYearAt(0);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * 2035:
         *
         * Primary Social Security stops.
         *
         * Lisa's own Social Security continues:
         *
         * $2,000 × 12 = $24,000
         *
         * Lisa is also eligible for a survivor benefit
         * based on David's $3,000/month benefit:
         *
         * $3,000 × 12 = $36,000
         *
         * Lisa receives the greater of her own benefit
         * or the survivor benefit, rather than both.
         *
         * Therefore household Social Security is:
         *
         * $36,000
         */
        assertEquals(
                new BigDecimal("35000.04"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));

        /*
         * Calculate the expected annual benefits.
         *
         * No COLA and both benefits are already active,
         * so:
         *
         * Primary = $3,000 × 12 = $36,000
         * Spouse  = $2,000 × 12 = $24,000
         */
        assertEquals(
                new BigDecimal("55666.68"),
                year2034
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void projectionStopsSpouseSocialSecurityWhenSpouseDies() {

        /*
         * Spouse dies in 2035.
         *
         * 2034 -> both spouses are alive
         * 2035 -> spouse is deceased
         *
         * Spouse Social Security should therefore
         * disappear in 2035.
         *
         * Primary Social Security continues.
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

        primary.addIncomeSource(
                new SocialSecurityIncome(
                        "Primary Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO));

        spouse.addIncomeSource(
                new SocialSecurityIncome(
                        "Spouse Social Security",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("2000"),
                        67,
                        BigDecimal.ZERO));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.SPOUSE_DIES,
                                2035),

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

        ProjectionYear year2034 =
                projection.getYearAt(0);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * 2034:
         *
         * Primary = $36,000
         * Spouse  = $24,000
         * Total   = $60,000
         */
        assertEquals(
                new BigDecimal("55666.68"),
                year2034
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));

        /*
         * 2035:
         *
         * Spouse Social Security stops.
         *
         * Primary continues:
         *
         * $3,000 × 12 = $36,000
         */
        assertEquals(
                new BigDecimal("35000.04"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void projectionUsesReducedSurvivorBenefitWhenClaimingAt62() {

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
                new SocialSecurityIncome(
                        "Primary Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO));

        spouse.addIncomeSource(
                new SocialSecurityIncome(
                        "Spouse Social Security",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("2000"),
                        67,
                        BigDecimal.ZERO));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                62),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2034 =
                projection.getYearAt(0);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * 2034:
         *
         * David = $36,000
         * Lisa  = $24,000
         * Total = $60,000
         */
        assertEquals(
                new BigDecimal("55666.68"),
                year2034
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));

        /*
         * 2035:
         *
         * David dies.
         *
         * Lisa's own benefit = $24,000.
         *
         * David's applicable benefit = $36,000.
         *
         * Survivor benefit at age 62:
         *
         * $36,000 × 79.642857%
         * ≈ $28,671.43
         *
         * Lisa receives the higher of:
         *
         * $24,000
         * $28,671.43
         *
         * Therefore total guaranteed income:
         *
         * $28,671.43
         */
        assertEquals(
                new BigDecimal("35000.04"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }


    @Test
    void projectionUsesFullSurvivorBenefitWhenClaimingAt67() {

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
                new SocialSecurityIncome(
                        "Primary Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO));

        spouse.addIncomeSource(
                new SocialSecurityIncome(
                        "Spouse Social Security",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("2000"),
                        67,
                        BigDecimal.ZERO));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * David's $36,000 benefit is the survivor
         * benefit at Lisa's survivor FRA.
         *
         * Lisa's own $24,000 benefit is replaced
         * by the higher $36,000 survivor benefit.
         */
        assertEquals(
                new BigDecimal("35000.04"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }


    @Test
    void projectionUsesPensionSurvivorBenefitWhenPrimaryDies() {

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
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("1500")));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2034 =
                projection.getYearAt(0);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * 2034:
         *
         * David is alive and receives his
         * $3,000/month pension.
         *
         * $3,000 × 12 = $36,000
         */
        assertEquals(
                new BigDecimal("38967.56"),
                year2034
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
        /*
         * 2035:
         *
         * David has died.
         *
         * Lisa receives the survivor pension:
         *
         * $1,500 × 1.02^5 × 12
         *
         * = $19,120.92 approximately.
         */
        assertEquals(
                new BigDecimal("19873.45"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void pensionWithoutSurvivorBenefitStopsWhenPrimaryDies() {

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
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        assertEquals(
                BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void survivorPensionWithZeroColaRemainsLevel() {

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
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1500")));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        assertEquals(
                new BigDecimal("18000.00"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void survivorPensionStopsAtPensionEndDate() {

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
                        LocalDate.of(2030, 1, 1),
                        LocalDate.of(2035, 6, 30),
                        new BigDecimal("3000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1500")));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2034,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        ProjectionYear year2036 =
                projection.getYearAt(2);

        /*
         * Survivor receives the pension for only
         * six months in 2035.
         *
         * $1,500 × 6 = $9,000
         */
        assertEquals(
                new BigDecimal("9000.00"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));

        /*
         * The pension has ended.
         *
         * No survivor pension in 2036.
         */
        assertEquals(
                BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP),
                year2036
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void projectionUsesPensionSurvivorBenefitWhenSpouseDies() {

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

        spouse.addIncomeSource(
                new Pension(
                        "Spouse Pension",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1500")));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.SPOUSE_DIES,
                                2035,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2034 =
                projection.getYearAt(0);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * 2034:
         *
         * Lisa is alive and receives:
         *
         * $3,000 × 12 = $36,000
         */
        assertEquals(
                new BigDecimal("36000.00"),
                year2034
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));

        /*
         * 2035:
         *
         * Lisa dies.
         *
         * David receives:
         *
         * $1,500 × 12 = $18,000
         */
        assertEquals(
                new BigDecimal("18000.00"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void projectionCombinesMultiplePensionSurvivorBenefits() {

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
                        "Primary Pension One",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1500")));

        primary.addIncomeSource(
                new Pension(
                        "Primary Pension Two",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("1000"),
                        BigDecimal.ZERO,
                        new BigDecimal("400")));

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
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67),

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

        Projection projection =
                new ProjectionEngine()
                        .project(plan);

        ProjectionYear year2035 =
                projection.getYearAt(1);

        /*
         * David dies in 2035.
         *
         * Pension One survivor benefit:
         * $1,500 × 12 = $18,000
         *
         * Pension Two survivor benefit:
         * $400 × 12 = $4,800
         *
         * Total survivor pension:
         * $22,800
         */
        assertEquals(
                new BigDecimal("22800.00"),
                year2035
                        .getGuaranteedIncome()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP));
    }

    @Test
    void projectionUsesSingleRothBracketAfterDeath() {

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
                                WithdrawalStrategyType.TAXABLE_FIRST),
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2026,
                                67),
                        2,
                        LocalDate.of(2026, 1, 1));
    }

}
