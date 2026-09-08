package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.LocalDate;

final class LifetimeProjectionTestSupport {
    static RetirementPlan plan(DeathScenarioAssumptions death) {
        Person primary = new Person("Primary", "Test", LocalDate.of(1960, 2, 29));
        Person spouse = new Person("Spouse", "Test", LocalDate.of(1962, 6, 15));
        primary.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.PRIMARY,
                LocalDate.of(2027, 2, 28), null, new BigDecimal("3000"), 67,
                BigDecimal.ZERO, 2030));
        spouse.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.SPOUSE,
                LocalDate.of(2029, 6, 15), null, new BigDecimal("1000"), 67,
                BigDecimal.ZERO, 2030));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), LocalDate.of(2033, 6, 30),
                new BigDecimal("1000"), new BigDecimal("0.10"), new BigDecimal("500")));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("40000")));
        household.addExpense(new Expense("Scheduled", new BigDecimal("5000"),
                GrowthCategory.GENERAL, LocalDate.of(2033, 1, 1), LocalDate.of(2033, 12, 31), ExpenseType.ONE_TIME));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount("Savings", AccountOwnership.PRIMARY,
                new BigDecimal("500000")));
        return new RetirementPlan(household, portfolio, new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal("0.03"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                death, 5, LocalDate.of(2030, 7, 1)));
    }

    static String json(Object value) throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(value);
    }
}
