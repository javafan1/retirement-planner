package com.daviddunn.retirementplanner.domain.factory;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RetirementPlanFactory {

    private static final BigDecimal DEFAULT_INFLATION_RATE =
            new BigDecimal("0.03");

    private static final BigDecimal DEFAULT_INVESTMENT_RETURN =
            new BigDecimal("0.08");

    private static final int INITIAL_DEFAULT_PROJECTION_LENGTH_YEARS =
            40;

    private RetirementPlanFactory() {
    }

    public static RetirementPlan createEmptyPlan() {

        Person primary =
                new Person(
                        "",
                        "",
                        null);

        Person spouse =
                new Person(
                        "",
                        "",
                        null);

        Household household =
                new Household(
                        primary,
                        spouse);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        DEFAULT_INVESTMENT_RETURN,
                        DEFAULT_INFLATION_RATE,
                        INITIAL_DEFAULT_PROJECTION_LENGTH_YEARS,
                        LocalDate.now());

        AccountPortfolio accountPortfolio =
                new AccountPortfolio();

        return new RetirementPlan(
                household,
                accountPortfolio,
                assumptions);
    }
}