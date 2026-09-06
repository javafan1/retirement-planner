package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiOwnerRothConversionProjectionTest {

    @Test
    void spouseOwnedTraditionalIraFundsHouseholdFixedConversionWhenPrimaryHasNoSource() {
        RothIRA primaryRoth = roth("Primary Roth", AccountOwnership.PRIMARY, "10000");
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "150000");
        RothIRA spouseRoth = roth("Spouse Roth", AccountOwnership.SPOUSE, "10000");

        ProjectionYear year = project(primaryRoth, spouseTraditional, spouseRoth,
                RothConversionStrategy.FIXED_AMOUNT, new BigDecimal("50000"));

        assertMoney(new BigDecimal("50000"), year.getRothConversion());
        assertMoney(BigDecimal.ZERO, year.getPrimaryRothConversion());
        assertMoney(new BigDecimal("50000"), year.getSpouseRothConversion());
        assertEquals(true, year.getEndingBalance(spouseTraditional)
                .compareTo(new BigDecimal("100000")) <= 0);
        assertEquals(true, year.getEndingBalance(spouseRoth)
                .compareTo(new BigDecimal("60000")) >= 0);
        assertMoney(new BigDecimal("150000"), spouseTraditional.getCurrentBalance());
    }

    @Test
    void customTaxableIncomeTargetCanUseSpouseOwnedEligibleAccounts() {
        RothIRA primaryRoth = roth("Primary Roth", AccountOwnership.PRIMARY, "10000");
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "500000");
        RothIRA spouseRoth = roth("Spouse Roth", AccountOwnership.SPOUSE, "10000");

        ProjectionYear year = project(primaryRoth, spouseTraditional, spouseRoth,
                RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,
                new BigDecimal("60000"));

        assertEquals(true, year.getRothConversion().signum() > 0);
        assertMoney(BigDecimal.ZERO, year.getPrimaryRothConversion());
        assertMoney(year.getRothConversion(), year.getSpouseRothConversion());
    }

    @Test
    void bracketFillCanUseSpouseOwnedEligibleAccounts() {
        RothIRA primaryRoth = roth("Primary Roth", AccountOwnership.PRIMARY, "10000");
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "500000");
        RothIRA spouseRoth = roth("Spouse Roth", AccountOwnership.SPOUSE, "10000");

        ProjectionYear year = project(primaryRoth, spouseTraditional, spouseRoth,
                RothConversionStrategy.FILL_12_PERCENT_BRACKET,
                BigDecimal.ZERO);

        assertEquals(true, year.getRothConversion().signum() > 0);
        assertMoney(BigDecimal.ZERO, year.getPrimaryRothConversion());
        assertMoney(year.getRothConversion(), year.getSpouseRothConversion());
    }

    @Test
    void spouseRmdIsWithdrawnBeforeHerRemainingAssetsAreConverted() {
        RothIRA primaryRoth = roth("Primary Roth", AccountOwnership.PRIMARY, "10000");
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "100000");
        spouseTraditional.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("100000"), BigDecimal.ZERO));
        RothIRA spouseRoth = roth("Spouse Roth", AccountOwnership.SPOUSE, "10000");

        ProjectionYear year = project(1955, 1950,
                primaryRoth, spouseTraditional, spouseRoth,
                RothConversionStrategy.FIXED_AMOUNT, new BigDecimal("40000"));

        assertEquals(true, year.getRequiredMinimumDistribution().signum() > 0);
        assertEquals(true, year.getRmdDistributedInProjection().signum() > 0);
        assertMoney(new BigDecimal("40000"), year.getSpouseRothConversion());
        assertEquals(true, year.getEndingBalance(spouseTraditional).compareTo(
                new BigDecimal("60000")) < 0);
    }

    @Test
    void fullyPreSatisfiedOpeningRmdStillAllowsSpouseConversion() {
        RothIRA primaryRoth = roth("Primary Roth", AccountOwnership.PRIMARY, "10000");
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "100000");
        spouseTraditional.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("100000"), new BigDecimal("4219.41")));
        RothIRA spouseRoth = roth("Spouse Roth", AccountOwnership.SPOUSE, "10000");

        ProjectionYear year = project(1955, 1950,
                primaryRoth, spouseTraditional, spouseRoth,
                RothConversionStrategy.FIXED_AMOUNT, new BigDecimal("50000"));

        assertMoney(BigDecimal.ZERO, year.getRmdDistributedInProjection());
        assertMoney(new BigDecimal("50000"), year.getSpouseRothConversion());
    }

    @Test
    void partialConversionReportsRequestedAmountAndTaxesOnlyExecutedAmount() {
        TraditionalIRA primaryTraditional = traditional("Primary Traditional",
                AccountOwnership.PRIMARY, "20000");
        RothIRA primaryRoth = roth("Primary Roth", AccountOwnership.PRIMARY, "0");
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "30000");
        RothIRA spouseRoth = roth("Spouse Roth", AccountOwnership.SPOUSE, "0");

        ProjectionYear underfilled = project(
                RothConversionStrategy.FIXED_AMOUNT, new BigDecimal("100000"),
                primaryTraditional, primaryRoth, spouseTraditional, spouseRoth);
        ProjectionYear fullyRequested = project(
                RothConversionStrategy.FIXED_AMOUNT, new BigDecimal("50000"),
                traditional("Primary Traditional", AccountOwnership.PRIMARY, "20000"),
                roth("Primary Roth", AccountOwnership.PRIMARY, "0"),
                traditional("Spouse Traditional", AccountOwnership.SPOUSE, "30000"),
                roth("Spouse Roth", AccountOwnership.SPOUSE, "0"));

        assertMoney(new BigDecimal("100000"), underfilled.getRequestedRothConversion());
        assertMoney(new BigDecimal("50000"), underfilled.getRothConversion());
        assertMoney(new BigDecimal("50000"), underfilled.getRothConversionShortfall());
        assertMoney(underfilled.getRothConversion(), underfilled.getPrimaryRothConversion()
                .add(underfilled.getSpouseRothConversion()));
        assertMoney(fullyRequested.getRothConversion(), underfilled.getRothConversion());
        assertMoney(fullyRequested.getAdjustedGrossIncome(), underfilled.getAdjustedGrossIncome());
        assertMoney(fullyRequested.getFederalIncomeTax(), underfilled.getFederalIncomeTax());
    }

    @Test
    void missingRothDestinationDoesNotPreventOpeningRmdFromBeingProcessed() {
        TraditionalIRA spouseTraditional = traditional("Spouse Traditional",
                AccountOwnership.SPOUSE, "100000");
        spouseTraditional.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("100000"), new BigDecimal("4219.41")));

        ProjectionYear year = project(1955, 1950,
                RothConversionStrategy.FIXED_AMOUNT, new BigDecimal("50000"),
                roth("Primary Roth", AccountOwnership.PRIMARY, "10000"),
                spouseTraditional,
                roth("Primary Roth Two", AccountOwnership.PRIMARY, "0"));

        assertEquals(true, year.getRequiredMinimumDistribution().signum() > 0);
        assertMoney(BigDecimal.ZERO, year.getRmdDistributedInProjection());
        assertMoney(new BigDecimal("4219.41"), year.getRmdDistributedBeforeProjection());
        assertMoney(BigDecimal.ZERO, year.getRothConversion());
        assertMoney(new BigDecimal("50000"), year.getRequestedRothConversion());
        assertEquals(true, year.getAdjustedGrossIncome()
                .compareTo(new BigDecimal("4219.41")) >= 0);
        assertMoney(year.getAnnualMedicarePremium(), year.getCashFlowNeed());
    }

    private ProjectionYear project(
            com.daviddunn.retirementplanner.domain.financial.Account first,
            com.daviddunn.retirementplanner.domain.financial.Account second,
            com.daviddunn.retirementplanner.domain.financial.Account third,
            RothConversionStrategy strategy,
            BigDecimal amountOrTarget) {

        return project(1965, 1966, first, second, third, strategy, amountOrTarget);
    }

    private ProjectionYear project(
            RothConversionStrategy strategy,
            BigDecimal amountOrTarget,
            com.daviddunn.retirementplanner.domain.financial.Account... accounts) {

        return project(1965, 1966, strategy, amountOrTarget, accounts);
    }

    private ProjectionYear project(
            int primaryBirthYear,
            int spouseBirthYear,
            com.daviddunn.retirementplanner.domain.financial.Account first,
            com.daviddunn.retirementplanner.domain.financial.Account second,
            com.daviddunn.retirementplanner.domain.financial.Account third,
            RothConversionStrategy strategy,
            BigDecimal amountOrTarget) {

        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(first);
        portfolio.addAccount(second);
        portfolio.addAccount(third);

        Household household = new Household(
                new Person("Primary", "Person", LocalDate.of(primaryBirthYear, 1, 1)),
                new Person("Spouse", "Person", LocalDate.of(spouseBirthYear, 1, 1)));

        RetirementPlan plan = new RetirementPlan(household, portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 1,
                        LocalDate.of(2026, 1, 1)));

        plan.setRothConversionRequest(new RothConversionRequest(
                true,
                2026,
                strategy == RothConversionStrategy.FIXED_AMOUNT
                        ? amountOrTarget : BigDecimal.ZERO,
                RothConversionStopRule.NEVER,
                strategy,
                RothConversionFrequency.ONE_TIME,
                strategy == RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET
                        ? amountOrTarget : null));

        return new ProjectionEngine().project(plan).getYearAt(0);
    }

    private ProjectionYear project(
            int primaryBirthYear,
            int spouseBirthYear,
            RothConversionStrategy strategy,
            BigDecimal amountOrTarget,
            com.daviddunn.retirementplanner.domain.financial.Account... accounts) {

        AccountPortfolio portfolio = new AccountPortfolio();
        for (com.daviddunn.retirementplanner.domain.financial.Account account : accounts) {
            portfolio.addAccount(account);
        }

        Household household = new Household(
                new Person("Primary", "Person", LocalDate.of(primaryBirthYear, 1, 1)),
                new Person("Spouse", "Person", LocalDate.of(spouseBirthYear, 1, 1)));

        RetirementPlan plan = new RetirementPlan(household, portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 1,
                        LocalDate.of(2026, 1, 1)));

        plan.setRothConversionRequest(new RothConversionRequest(
                true, 2026,
                strategy == RothConversionStrategy.FIXED_AMOUNT ? amountOrTarget : BigDecimal.ZERO,
                RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME,
                strategy == RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET ? amountOrTarget : null));

        return new ProjectionEngine().project(plan).getYearAt(0);
    }

    private TraditionalIRA traditional(
            String name,
            AccountOwnership ownership,
            String balance) {

        return new TraditionalIRA(name, ownership, new BigDecimal(balance));
    }

    private RothIRA roth(
            String name,
            AccountOwnership ownership,
            String balance) {

        return new RothIRA(name, ownership, new BigDecimal(balance));
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
