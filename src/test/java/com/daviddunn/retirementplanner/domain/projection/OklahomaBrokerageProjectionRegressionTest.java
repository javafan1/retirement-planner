package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OklahomaBrokerageProjectionRegressionTest {

    /** Restores the ordinary brokerage source that the Oklahoma acceptance fixture replaces with cash. */
    public static RetirementPlan plan(String scenario) {
        var plan = OklahomaProjectionAcceptanceTest.plan(false);
        var portfolio = plan.getAccountPortfolio();
        for (var account : List.copyOf(portfolio.getAccounts())) {
            if (account.getType() == AccountType.CHECKING) {
                portfolio.replaceAccount(account, new BrokerageAccount(
                        account.getName(), account.getOwnership(),
                        new BigDecimal(scenario.equals("mixed") ? "10000" : "400000")));
            } else if (scenario.equals("principal")) {
                portfolio.removeAccount(account);
            }
        }
        if (scenario.equals("principal")) {
            var person = plan.getHousehold().getPrimaryPerson();
            for (var source : List.copyOf(person.getIncomeSources())) {
                person.removeIncomeSource(source);
            }
            // No eligible conversion sources remain; disable the scheduled request explicitly.
            plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                    false, 2027, BigDecimal.ZERO,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        }
        return plan;
    }

    @ParameterizedTest
    @ValueSource(strings = {"principal", "mixed", "conversion"})
    void ordinaryBrokerageWithdrawalAdmittedIn2027AfterSuccessfulMichiganProjection(String scenario) {
        var plan = plan(scenario);
        var balances = plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).toList();
        var oklahomaAssumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(oklahomaAssumptions.withStateTaxAssumptions(StateTaxAssumptions.michigan()));
        var michigan = new ProjectionEngine().project(plan);
        var prior = michigan.getYearAt(0);
        assertEquals(2027, prior.getCalendarYear());
        assertOrdinaryBrokerage(prior);
        plan.setPlanningAssumptions(oklahomaAssumptions);
        var oklahoma = assertDoesNotThrow(() -> new ProjectionEngine().project(plan));
        var first = oklahoma.getYearAt(0);
        assertEquals(2027, first.getCalendarYear());
        assertEquals(michigan.getYears().size(), oklahoma.getYears().size());
        for (var year : oklahoma.getYears()) {
            assertEquals(StateTaxJurisdiction.OK, year.getStateTaxJurisdiction());
            assertEquals(StateTaxCalculationStatus.CALCULATED,
                    year.stateTaxCalculation().orElseThrow().calculationStatus());
            assertCashConservation(year);
        }
        assertOrdinaryBrokerage(first);
        if (scenario.equals("principal")) {
            assertEquals(0, first.getAdjustedGrossIncome().signum());
            assertEquals(0, first.getFederalIncomeTax().signum());
            assertEquals(0, first.getStateIncomeTax().signum());
        } else {
            assertTrue(first.getSocialSecurityResult().householdBenefit().signum() > 0);
            assertEquals(0, new BigDecimal("100000").compareTo(first.getRothConversion()));
            assertTrue(first.getTaxFundingWithdrawal().signum() > 0);
            // Unknown brokerage principal contributes nothing to the federal AGI already modeled.
            var knownOrdinaryIncome = first.getIncomeSources().stream()
                    .filter(source -> source.kind() != IncomeSourceAttribution.Kind.SOCIAL_SECURITY)
                    .flatMap(source -> source.modeledTaxableAmount().stream())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertMoney(knownOrdinaryIncome.add(first.getTaxableSocialSecurity()), first.getAdjustedGrossIncome());
            if (scenario.equals("mixed")) {
                assertTrue(first.getIncomeSources().stream().anyMatch(source ->
                        source.kind() == IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL
                                && source.grossAmount().signum() > 0));
            }
        }
        assertEquals(balances, plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).toList());
        assertEquals(FloridaProductionProjectionTest.financialRows(oklahoma),
                FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(plan)));
    }

    public static void assertOrdinaryBrokerage(ProjectionYear year) {
        var sources = year.getIncomeSources().stream().filter(source ->
                source.kind() == IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL
                        && source.account().orElseThrow().type() == AccountType.BROKERAGE
                        && source.grossAmount().signum() > 0).toList();
        assertFalse(sources.isEmpty(), "Regression requires a real ordinary BrokerageAccount withdrawal");
        assertTrue(sources.stream().allMatch(source -> source.modeledTaxableAmount().isEmpty()));
    }

    public static void assertCashConservation(ProjectionYear year) {
        // The established funding solver admits a raw residual of at most $0.01.
        // Compare the residual itself; separately rounding each side can differ by a cent.
        var residual = year.getGuaranteedIncome().add(year.getPortfolioWithdrawal())
                .subtract(year.getAnnualExpenses()).subtract(year.getAnnualMedicarePremium())
                .subtract(year.getTotalIncomeTax()).subtract(year.getRetainedHouseholdSurplus());
        assertTrue(residual.abs().compareTo(new BigDecimal("0.01")) <= 0, residual::toPlainString);
        assertMoney(year.getBeginningInvestableAssets().add(year.getInvestmentGrowth())
                .subtract(year.getPortfolioWithdrawal()).add(year.getRetainedHouseholdSurplus()),
                year.getEndingInvestableAssets());
        assertMoney(year.getFederalIncomeTax().add(year.getStateIncomeTax()), year.getTotalIncomeTax());
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.setScale(2, RoundingMode.HALF_UP)
                .compareTo(actual.setScale(2, RoundingMode.HALF_UP)), () -> expected + " != " + actual);
    }
}
