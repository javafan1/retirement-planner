package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that genuinely unspent post-tax guaranteed income is retained. */
class SurplusGuaranteedIncomeProjectionTest {

    @Test
    void guaranteedIncomeAboveExpensesIsRetainedAfterTaxes() {
        ProjectionYear sixtyThousand = project("5000", null);
        ProjectionYear seventyTwoThousand = project("6000", null);

        assertMoney(BigDecimal.ZERO, sixtyThousand.getCashFlowNeed());
        assertMoney(BigDecimal.ZERO, seventyTwoThousand.getCashFlowNeed());
        assertTrue(seventyTwoThousand.getGuaranteedIncome()
                .compareTo(sixtyThousand.getGuaranteedIncome()) > 0);

        assertMoney(
                sixtyThousand.getGuaranteedIncome()
                        .subtract(sixtyThousand.getAnnualExpenses())
                        .subtract(sixtyThousand.getTotalIncomeTax()),
                sixtyThousand.getRetainedHouseholdSurplus());
        assertMoney(
                seventyTwoThousand.getGuaranteedIncome()
                        .subtract(seventyTwoThousand.getAnnualExpenses())
                        .subtract(seventyTwoThousand.getTotalIncomeTax()),
                seventyTwoThousand.getRetainedHouseholdSurplus());
        assertMoney(
                new BigDecimal("100000")
                        .add(seventyTwoThousand.getRetainedHouseholdSurplus()),
                seventyTwoThousand.getEndingInvestableAssets());
        assertTrue(seventyTwoThousand.getEndingInvestableAssets()
                .compareTo(sixtyThousand.getEndingInvestableAssets()) > 0);
        System.out.println("SURPLUS_CONTROL,income60="
                + sixtyThousand.getGuaranteedIncome() + ",income72="
                + seventyTwoThousand.getGuaranteedIncome() + ",expense=50000"
                + ",withdrawal60=" + sixtyThousand.getCashFlowNeed()
                + ",withdrawal72=" + seventyTwoThousand.getCashFlowNeed()
                + ",tax60=" + sixtyThousand.getTotalIncomeTax()
                + ",tax72=" + seventyTwoThousand.getTotalIncomeTax()
                + ",ending60=" + sixtyThousand.getEndingInvestableAssets()
                + ",ending72=" + seventyTwoThousand.getEndingInvestableAssets());
    }

    @Test
    void multipleGuaranteedIncomeSourcesAreCombinedBeforeTheZeroFloor() {
        ProjectionYear onePension = project("6000", null);
        ProjectionYear twoPensions = project("3000", "3000");

        assertMoney(onePension.getGuaranteedIncome(), twoPensions.getGuaranteedIncome());
        assertMoney(onePension.getCashFlowNeed(), twoPensions.getCashFlowNeed());
        assertMoney(onePension.getPortfolioWithdrawal(), twoPensions.getPortfolioWithdrawal());
        assertMoney(onePension.getEndingInvestableAssets(),
                twoPensions.getEndingInvestableAssets());
    }

    private ProjectionYear project(String firstMonthlyPension, String secondMonthlyPension) {
        Person primary = new Person("Primary", "Person", LocalDate.of(1970, 1, 1));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1972, 1, 1));
        primary.addIncomeSource(pension("First", firstMonthlyPension));
        if (secondMonthlyPension != null) {
            primary.addIncomeSource(pension("Second", secondMonthlyPension));
        }
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("50000")));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        RetirementPlan plan = new RetirementPlan(
                household,
                portfolio,
                new PlanningAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        1,
                        LocalDate.of(2026, 1, 1)));
        return new ProjectionEngine().project(plan).getFirstYear();
    }

    private Pension pension(String name, String monthlyAmount) {
        return new Pension(
                name,
                AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal(monthlyAmount),
                BigDecimal.ZERO);
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
