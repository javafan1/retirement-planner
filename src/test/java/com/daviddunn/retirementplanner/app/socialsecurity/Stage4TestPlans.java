package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;

final class Stage4TestPlans {
    static RetirementPlan plan() {
        Person primary = new Person("Primary", "Test", LocalDate.of(1960, 2, 29));
        Person spouse = new Person("Spouse", "Test", LocalDate.of(1962, 6, 15));
        primary.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.PRIMARY,
                primary.getBirthDate().plusYears(67), null, new BigDecimal("3000"), 67,
                BigDecimal.ZERO, 2030));
        spouse.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.SPOUSE,
                spouse.getBirthDate().plusYears(67), null, new BigDecimal("1000"), 67,
                BigDecimal.ZERO, 2030));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("1000"),
                new BigDecimal("0.02"), new BigDecimal("500")));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("40000")));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("500000")));
        portfolio.addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        portfolio.addAccount(new BrokerageAccount("Cash", AccountOwnership.SPOUSE, new BigDecimal("200000")));
        return new RetirementPlan(household, portfolio, new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal("0.03"), new BigDecimal("0.02"),
                        new BigDecimal("0.04"), new BigDecimal("0.01")),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.25")),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2032, 62),
                5, LocalDate.of(2030, 1, 1)));
    }

    static SocialSecurityHouseholdClaimingStrategy strategy(RetirementPlan plan) {
        return new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
    }

    static String json(Object value) throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(value);
    }
}
