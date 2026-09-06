package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdCashSettlementProjectionTest {

    @Test
    void pensionSurplusFundsTaxesAndRetainsOnlyAfterTaxCash() {
        RetirementPlan plan = pensionPlan(1, BigDecimal.ZERO);

        ProjectionYear year = new ProjectionEngine().project(plan).getFirstYear();

        assertMoney("72000", year.getGuaranteedIncome());
        assertMoney("50000", year.getAnnualExpenses());
        assertMoney("4280", year.getTotalIncomeTax());
        assertMoney("0", year.getTaxFundingWithdrawal());
        assertMoney("17720", year.getRetainedHouseholdSurplus());
        assertMoney("17720", year.getRetainedFromGuaranteedIncome());
        assertMoney("0", year.getRetainedFromExcessRmd());
        assertMoney("117720", year.getEndingInvestableAssets());
        assertMoney(year.getEndingInvestableAssets(), year.getAfterTaxEstateValue());
        assertCashReconciles(year);
        printSettlement("PENSION", year);
    }

    @Test
    void retainedAssetsFundTheNextYearBeforePersistedAccounts() {
        RetirementPlan plan = pensionPlan(2, BigDecimal.ZERO);

        Projection projection = new ProjectionEngine().project(plan);
        ProjectionYear first = projection.getYearAt(0);
        ProjectionYear second = projection.getYearAt(1);

        assertTrue(first.getEndingRetainedNonQualifiedAssets().signum() > 0);
        assertMoney(first.getEndingRetainedNonQualifiedAssets(),
                second.getBeginningRetainedNonQualifiedAssets());
        assertMoney("0", second.getEndingRetainedNonQualifiedAssets());
        assertMoney("50000", second.getPortfolioWithdrawal());
        assertMoney("67720", second.getEndingInvestableAssets());
        assertCashReconciles(second);
        printSettlement("RETAINED_USED_NEXT_YEAR", second);
    }

    @Test
    void retainedAssetsReceiveTheConfiguredReturnExactlyOnce() {
        ProjectedPortfolio portfolio = new ProjectedPortfolio(
                List.of(), new BigDecimal("20000"));

        ProjectedPortfolio grown = portfolio.applyGrowth(new BigDecimal("0.05"));

        assertMoney("21000", grown.getRetainedNonQualifiedAssets());
        assertMoney("21000", grown.getTotalBalance());
    }

    @Test
    void authoritativeSocialSecuritySurplusIsRetainedAfterTaxes() {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(ss(AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4), "3000", 67));
        spouse.addIncomeSource(ss(AccountOwnership.SPOUSE,
                LocalDate.of(2030, 2, 28), "2000", 65));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("10000")));
        RetirementPlan plan = new RetirementPlan(
                household, new AccountPortfolio(), assumptions(
                1, LocalDate.of(2031, 1, 1), BigDecimal.ZERO));

        ProjectionYear year = new ProjectionEngine().project(plan).getFirstYear();

        assertTrue(year.getSocialSecurityResult().householdBenefit().signum() > 0);
        assertMoney(year.getSocialSecurityResult().householdBenefit(),
                year.getGuaranteedIncome());
        assertMoney("0", year.getTaxFundingWithdrawal());
        assertTrue(year.getRetainedFromGuaranteedIncome().signum() > 0);
        assertMoney(year.getRetainedFromGuaranteedIncome(),
                year.getEndingRetainedNonQualifiedAssets());
        assertCashReconciles(year);
        printSettlement("SOCIAL_SECURITY", year);
    }

    @Test
    void excessRmdIsRetainedOnceAfterMedicareAndTaxes() {
        Person primary = new Person("Primary", "Person", LocalDate.of(1950, 1, 1));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1990, 1, 1));
        TraditionalIRA ira = new TraditionalIRA(
                "Traditional IRA", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("100000"), BigDecimal.ZERO));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(ira);
        RetirementPlan plan = new RetirementPlan(
                new Household(primary, spouse), portfolio,
                assumptions(1, LocalDate.of(2026, 1, 1), BigDecimal.ZERO));

        ProjectionYear year = new ProjectionEngine().project(plan).getFirstYear();
        BigDecimal expected = year.getRmdDistributedInProjection()
                .subtract(year.getAnnualMedicarePremium())
                .subtract(year.getTotalIncomeTax())
                .max(BigDecimal.ZERO);

        assertMoney(expected, year.getRetainedHouseholdSurplus());
        assertMoney(expected, year.getRetainedFromExcessRmd());
        assertMoney(expected, year.getEndingRetainedNonQualifiedAssets());
        assertTrue(year.getExcessRmd().compareTo(
                year.getRetainedFromExcessRmd()) >= 0);
        assertCashReconciles(year);
        printSettlement("RMD", year);
    }

    private RetirementPlan pensionPlan(int years, BigDecimal returnRate) {
        Person primary = new Person("Primary", "Person", LocalDate.of(1970, 1, 1));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1972, 1, 1));
        primary.addIncomeSource(new Pension(
                "Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), new BigDecimal("6000"), BigDecimal.ZERO));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("50000")));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, new BigDecimal("100000")));
        return new RetirementPlan(
                household, portfolio,
                assumptions(years, LocalDate.of(2026, 1, 1), returnRate));
    }

    private PlanningAssumptions assumptions(
            int years, LocalDate start, BigDecimal returnRate) {
        return new PlanningAssumptions(
                returnRate, BigDecimal.ZERO, years, start);
    }

    private SocialSecurityIncome ss(
            AccountOwnership owner,
            LocalDate start,
            String monthlyBenefit,
            int claimingAge) {
        return new SocialSecurityIncome(
                "Social Security", owner, start, null,
                new BigDecimal(monthlyBenefit), claimingAge,
                BigDecimal.ZERO, 2030);
    }

    private void assertCashReconciles(ProjectionYear year) {
        HouseholdCashSettlement value = year.getHouseholdCashSettlement();
        BigDecimal sources = value.guaranteedIncome()
                .add(value.projectedPeriodRmdCash())
                .add(value.spendingWithdrawal())
                .add(value.taxFundingWithdrawal());
        BigDecimal uses = value.ordinaryExpenses()
                .add(value.medicarePremiums())
                .add(value.incomeTaxes())
                .add(value.retainedHouseholdSurplus());
        assertTrue(sources.subtract(uses).abs()
                .compareTo(new BigDecimal("0.02")) <= 0);
    }

    private void printSettlement(String name, ProjectionYear year) {
        HouseholdCashSettlement value = year.getHouseholdCashSettlement();
        System.out.println("CASH_SETTLEMENT_CONTROL," + name
                + ",guaranteedIncome=" + value.guaranteedIncome()
                + ",projectedRmd=" + value.projectedPeriodRmdCash()
                + ",spendingWithdrawal=" + value.spendingWithdrawal()
                + ",taxWithdrawal=" + value.taxFundingWithdrawal()
                + ",expenses=" + value.ordinaryExpenses()
                + ",medicare=" + value.medicarePremiums()
                + ",taxes=" + value.incomeTaxes()
                + ",retained=" + value.retainedHouseholdSurplus()
                + ",endingRetained="
                + year.getEndingRetainedNonQualifiedAssets());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
