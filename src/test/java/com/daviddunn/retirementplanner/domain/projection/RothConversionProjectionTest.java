package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RothConversionProjectionTest {

    @Test
    void projectionAppliesOneTimeRothConversionIn2026() {

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
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,RothConversionFrequency.ONE_TIME));

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
         * The conversion creates federal income tax.
         */
        assertTrue(
                firstYear
                        .getFederalIncomeTax()
                        .compareTo(BigDecimal.ZERO) > 0);

        /*
         * The conversion creates a tax-funding
         * withdrawal.
         */
        assertTrue(
                firstYear
                        .getTaxFundingWithdrawal()
                        .compareTo(BigDecimal.ZERO) > 0);

        /*
         * Federal tax and the tax-funding withdrawal
         * should agree within the tax engine's
         * one-cent convergence tolerance.
         */
        assertTrue(
                firstYear
                        .getFederalIncomeTax()
                        .subtract(
                                firstYear
                                        .getTaxFundingWithdrawal())
                        .abs()
                        .compareTo(
                                new BigDecimal("0.01")) < 0);

        /*
         * The Roth IRA receives the full conversion.
         */
        assertEquals(
                0,
                new BigDecimal("150000")
                        .compareTo(
                                firstYear.getEndingBalance(
                                        rothIra)));

        /*
         * The Traditional IRA is reduced by the
         * $50,000 conversion plus the tax funding.
         */
        assertEquals(
                0,
                new BigDecimal("48022.22242")
                        .compareTo(
                                firstYear.getEndingBalance(
                                        traditionalIra)));

        /*
         * 2027:
         *
         * The conversion is one-time, so it does not
         * occur again.
         */
        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                secondYear.getRothConversion()));

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                secondYear
                                        .getTaxFundingWithdrawal()));
    }


    @Test
    void projectionAppliesOneTimeRothConversionInScheduledYear() {

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

        /*
         * The important difference from the first test:
         *
         * The conversion is scheduled for 2027.
         */
        plan.setRothConversionRequest(
                new RothConversionRequest(
                        true,
                        2027,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionFrequency.ONE_TIME));

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
         * The conversion is scheduled for 2027,
         * so nothing should happen in 2026.
         */
        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                firstYear.getRothConversion()));

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(
                                firstYear
                                        .getTaxFundingWithdrawal()));

        /*
         * 2027:
         *
         * The scheduled $50,000 conversion occurs.
         */
        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                secondYear.getRothConversion()));

        /*
         * The 2027 conversion creates federal tax.
         */
        assertTrue(
                secondYear
                        .getFederalIncomeTax()
                        .compareTo(BigDecimal.ZERO) > 0);

        /*
         * The 2027 tax is funded from the portfolio.
         */
        assertTrue(
                secondYear
                        .getTaxFundingWithdrawal()
                        .compareTo(BigDecimal.ZERO) > 0);

        /*
         * The Roth IRA should contain the original
         * $100,000 plus the $50,000 conversion.
         */
        assertEquals(
                0,
                new BigDecimal("150000")
                        .compareTo(
                                secondYear.getEndingBalance(
                                        rothIra)));
    }
}