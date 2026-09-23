package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.noninvestable.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class MonteCarloFixtures {
    public static RetirementPlan household() {
        var plan = RetirementPlanFactory.createEmptyPlan();
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        primary.setFirstName("Alex");
        primary.setBirthDate(LocalDate.of(1963, 6, 4));
        spouse.setFirstName("Sam");
        spouse.setBirthDate(LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(new SocialSecurityIncome("Alex SS", AccountOwnership.PRIMARY, LocalDate.of(2033, 6, 4), null, new BigDecimal("3000"), 70, BigDecimal.ZERO, 2027));
        spouse.addIncomeSource(new SocialSecurityIncome("Sam SS", AccountOwnership.SPOUSE, LocalDate.of(2027, 2, 28), null, new BigDecimal("2000"), 62, BigDecimal.ZERO, 2027));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY, LocalDate.of(2027, 1, 1), null, new BigDecimal("1000.01"), new BigDecimal("0.025")));
        plan.setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.045"), new BigDecimal("0.02"), 30, LocalDate.of(2027, 1, 1)));
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("80000.01")));
        plan.getAccountPortfolio().addAccount(new BrokerageAccount("Joint brokerage", AccountOwnership.JOINT, new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Alex IRA", AccountOwnership.PRIMARY, new BigDecimal("2500000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Alex Roth", AccountOwnership.PRIMARY, new BigDecimal("500000")));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Sam IRA", AccountOwnership.SPOUSE, new BigDecimal("500000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Sam Roth", AccountOwnership.SPOUSE, new BigDecimal("500000")));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("75000"), RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        plan.addNonInvestableAsset(new NonInvestableAsset("Home equity", new BigDecimal("500000"), new BigDecimal("0.02")));
        return plan;
    }
}
