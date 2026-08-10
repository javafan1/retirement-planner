package com.daviddunn.retirementplanner.data;

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
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RothConversionDemoFactory {

    private RothConversionDemoFactory() {
    }

    public static RetirementPlan createRetirementPlan() {

        Person david =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(
                                1963,
                                6,
                                4));

        Person lisa =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(
                                1965,
                                2,
                                28));

        Household household =
                new Household(
                        david,
                        lisa);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "David Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("2587000")));

        portfolio.addAccount(
                new RothIRA(
                        "David Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("400000")));

        portfolio.addAccount(
                new TraditionalIRA(
                        "Lisa Traditional IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("2165700")));

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.070"),
                        new BigDecimal("0.025"),
                        2,
                        LocalDate.of(
                                2026,
                                7,
                                1));

        RothConversionRequest rothConversionRequest =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy.FIXED_AMOUNT,
                        RothConversionFrequency.ONE_TIME
                );

        return new RetirementPlan(
                household,
                portfolio,
                assumptions,
                rothConversionRequest);
    }
}