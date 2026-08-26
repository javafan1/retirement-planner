package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.daviddunn.retirementplanner.domain.financial.TestMoney.money;
import static org.junit.jupiter.api.Assertions.*;

class ProjectedPortfolioRothConverterTest {

    private final ProjectedPortfolioRothConverter converter =
            new ProjectedPortfolioRothConverter();

    @Test
    void shouldConvertFromTraditionalToRoth() {

        TraditionalIRA primaryTraditional =
                new TraditionalIRA(
                        "Primary Traditional IRA",
                        AccountOwnership.PRIMARY,
                        money("500000"));

        RothIRA primaryRoth =
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        money("100000"));

        ProjectedPortfolio projectedPortfolio =
                createProjectedPortfolio(
                        primaryTraditional,
                        primaryRoth);

        ProjectedPortfolio result =
                converter.convert(
                        projectedPortfolio,
                        AccountOwnership.PRIMARY,
                        money("50000"));

        assertEquals(
                money("450000"),
                result.getBalance(primaryTraditional));

        assertEquals(
                money("150000"),
                result.getBalance(primaryRoth));
    }

    @Test
    void shouldNotModifySpouseAccounts() {

        TraditionalIRA primaryTraditional =
                new TraditionalIRA(
                        "Primary Traditional IRA",
                        AccountOwnership.PRIMARY,
                        money("500000"));

        RothIRA primaryRoth =
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        money("100000"));

        TraditionalIRA spouseTraditional =
                new TraditionalIRA(
                        "Spouse Traditional IRA",
                        AccountOwnership.SPOUSE,
                        money("300000"));

        RothIRA spouseRoth =
                new RothIRA(
                        "Spouse Roth IRA",
                        AccountOwnership.SPOUSE,
                        money("50000"));

        ProjectedPortfolio projectedPortfolio =
                createProjectedPortfolio(
                        primaryTraditional,
                        primaryRoth,
                        spouseTraditional,
                        spouseRoth);

        ProjectedPortfolio result =
                converter.convert(
                        projectedPortfolio,
                        AccountOwnership.PRIMARY,
                        money("50000"));

        assertEquals(
                money("300000"),
                result.getBalance(spouseTraditional));

        assertEquals(
                money("50000"),
                result.getBalance(spouseRoth));
    }

    @Test
    void shouldReturnSamePortfolioForZeroConversion() {

        TraditionalIRA primaryTraditional =
                new TraditionalIRA(
                        "Primary Traditional IRA",
                        AccountOwnership.PRIMARY,
                        money("500000"));

        RothIRA primaryRoth =
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        money("100000"));

        ProjectedPortfolio projectedPortfolio =
                createProjectedPortfolio(
                        primaryTraditional,
                        primaryRoth);

        ProjectedPortfolio result =
                converter.convert(
                        projectedPortfolio,
                        AccountOwnership.PRIMARY,
                        money("0"));

        assertSame(
                projectedPortfolio,
                result);
    }

    @Test
    void shouldRejectNegativeConversionAmount() {

        TraditionalIRA primaryTraditional =
                new TraditionalIRA(
                        "Primary Traditional IRA",
                        AccountOwnership.PRIMARY,
                        money("500000"));

        RothIRA primaryRoth =
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        money("100000"));

        ProjectedPortfolio projectedPortfolio =
                createProjectedPortfolio(
                        primaryTraditional,
                        primaryRoth);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> converter.convert(
                                projectedPortfolio,
                                AccountOwnership.PRIMARY,
                                money("-1")));

        assertEquals(
                "Conversion amount cannot be negative.",
                exception.getMessage());
    }

    @Test
    void shouldThrowWhenTraditionalBalanceIsInsufficient() {

        TraditionalIRA primaryTraditional =
                new TraditionalIRA(
                        "Primary Traditional IRA",
                        AccountOwnership.PRIMARY,
                        money("10000"));

        RothIRA primaryRoth =
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        money("100000"));

        ProjectedPortfolio projectedPortfolio =
                createProjectedPortfolio(
                        primaryTraditional,
                        primaryRoth);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> converter.convert(
                                projectedPortfolio,
                                AccountOwnership.PRIMARY,
                                money("50000")));

        assertEquals(
                "No eligible tax-deferred account found.",
                exception.getMessage());
    }

    @Test
    void shouldThrowWhenNoRothAccountExists() {

        TraditionalIRA primaryTraditional =
                new TraditionalIRA(
                        "Primary Traditional IRA",
                        AccountOwnership.PRIMARY,
                        money("500000"));

        ProjectedPortfolio projectedPortfolio =
                createProjectedPortfolio(
                        primaryTraditional);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> converter.convert(
                                projectedPortfolio,
                                AccountOwnership.PRIMARY,
                                money("50000")));

        assertEquals(
                "No eligible Roth account found.",
                exception.getMessage());
    }

    @Test
    void householdConversionUsesSpouseSourceWhenPrimaryHasNoEligibleSource() {

        RothIRA primaryRoth = new RothIRA(
                "Primary Roth", AccountOwnership.PRIMARY, money("10000"));
        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("100000"));
        RothIRA spouseRoth = new RothIRA(
                "Spouse Roth", AccountOwnership.SPOUSE, money("20000"));

        var result = converter.convertHousehold(createProjectedPortfolio(
                primaryRoth, spouseTraditional, spouseRoth), money("50000"));

        assertEquals(0, money("50000").compareTo(result.getTotalConversion()));
        assertEquals(0, money("0").compareTo(result.getConversion(AccountOwnership.PRIMARY)));
        assertEquals(0, money("50000").compareTo(result.getConversion(AccountOwnership.SPOUSE)));
        assertEquals(0, money("50000").compareTo(result.getPortfolio().getBalance(spouseTraditional)));
        assertEquals(0, money("70000").compareTo(result.getPortfolio().getBalance(spouseRoth)));
        assertEquals(0, money("10000").compareTo(result.getPortfolio().getBalance(primaryRoth)));
        assertEquals(AccountOwnership.SPOUSE,
                result.getAllocations().getFirst().ownership());
    }

    @Test
    void householdConversionSpansOwnersAndAccountsWithoutCrossingOwnership() {

        TraditionalIRA primaryFirst = new TraditionalIRA(
                "Primary First", AccountOwnership.PRIMARY, money("20000"));
        TraditionalIRA primarySecond = new TraditionalIRA(
                "Primary Second", AccountOwnership.PRIMARY, money("20000"));
        RothIRA primaryRoth = new RothIRA(
                "Primary Roth", AccountOwnership.PRIMARY, money("0"));
        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("500000"));
        RothIRA spouseRoth = new RothIRA(
                "Spouse Roth", AccountOwnership.SPOUSE, money("0"));

        var result = converter.convertHousehold(createProjectedPortfolio(
                primaryFirst, primarySecond, primaryRoth,
                spouseTraditional, spouseRoth), money("100000"));

        assertEquals(money("100000"), result.getTotalConversion());
        assertEquals(money("40000"), result.getConversion(AccountOwnership.PRIMARY));
        assertEquals(money("60000"), result.getConversion(AccountOwnership.SPOUSE));
        assertEquals(money("40000"), result.getPortfolio().getBalance(primaryRoth));
        assertEquals(money("60000"), result.getPortfolio().getBalance(spouseRoth));
        assertTrue(result.getAllocations().stream().allMatch(allocation ->
                allocation.sourceAccount().getOwnership()
                        == allocation.destinationAccount().getOwnership()));
    }

    @Test
    void householdConversionStopsAtEligibleHouseholdSourceCapacity() {

        TraditionalIRA primaryTraditional = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("20000"));
        RothIRA primaryRoth = new RothIRA(
                "Primary Roth", AccountOwnership.PRIMARY, money("0"));
        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("30000"));
        RothIRA spouseRoth = new RothIRA(
                "Spouse Roth", AccountOwnership.SPOUSE, money("0"));

        var result = converter.convertHousehold(createProjectedPortfolio(
                primaryTraditional, primaryRoth,
                spouseTraditional, spouseRoth), money("100000"));

        assertEquals(money("50000"), result.getTotalConversion());
        assertEquals(money("0"), result.getPortfolio().getBalance(primaryTraditional));
        assertEquals(money("0"), result.getPortfolio().getBalance(spouseTraditional));
        assertEquals(money("20000"), result.getPortfolio().getBalance(primaryRoth));
        assertEquals(money("30000"), result.getPortfolio().getBalance(spouseRoth));
    }

    @Test
    void householdConversionSkipsOwnerWithoutSameOwnerRothDestination() {

        TraditionalIRA primaryTraditional = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("40000"));
        RothIRA primaryRoth = new RothIRA(
                "Primary Roth", AccountOwnership.PRIMARY, money("0"));
        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("500000"));

        var result = converter.convertHousehold(createProjectedPortfolio(
                primaryTraditional, primaryRoth, spouseTraditional), money("100000"));

        assertEquals(money("40000"), result.getTotalConversion());
        assertEquals(money("40000"), result.getConversion(AccountOwnership.PRIMARY));
        assertEquals(BigDecimal.ZERO, result.getConversion(AccountOwnership.SPOUSE));
        assertEquals(money("500000"), result.getPortfolio().getBalance(spouseTraditional));
        assertEquals(money("40000"), result.getPortfolio().getBalance(primaryRoth));
        assertTrue(result.getAllocations().stream().allMatch(allocation ->
                allocation.ownership() == AccountOwnership.PRIMARY));
    }

    @Test
    void householdConversionContinuesToSpouseWhenPrimaryHasNoRothDestination() {

        TraditionalIRA primaryTraditional = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("80000"));
        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("500000"));
        RothIRA spouseRoth = new RothIRA(
                "Spouse Roth", AccountOwnership.SPOUSE, money("0"));

        var result = converter.convertHousehold(createProjectedPortfolio(
                primaryTraditional, spouseTraditional, spouseRoth), money("100000"));

        assertEquals(money("100000"), result.getTotalConversion());
        assertEquals(BigDecimal.ZERO, result.getConversion(AccountOwnership.PRIMARY));
        assertEquals(money("100000"), result.getConversion(AccountOwnership.SPOUSE));
        assertEquals(money("80000"), result.getPortfolio().getBalance(primaryTraditional));
        assertEquals(money("100000"), result.getPortfolio().getBalance(spouseRoth));
    }

    @Test
    void householdConversionUsesFirstSameOwnerRothDestinationInPortfolioOrder() {

        TraditionalIRA primaryTraditional = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("100000"));
        RothIRA firstPrimaryRoth = new RothIRA(
                "First Primary Roth", AccountOwnership.PRIMARY, money("300000"));
        RothIRA secondPrimaryRoth = new RothIRA(
                "Second Primary Roth", AccountOwnership.PRIMARY, money("200000"));
        ProjectedPortfolio portfolio = createProjectedPortfolio(
                primaryTraditional, firstPrimaryRoth, secondPrimaryRoth);

        var firstRun = converter.convertHousehold(portfolio, money("50000"));
        var secondRun = converter.convertHousehold(portfolio, money("50000"));

        assertEquals(money("350000"), firstRun.getPortfolio().getBalance(firstPrimaryRoth));
        assertEquals(money("200000"), firstRun.getPortfolio().getBalance(secondPrimaryRoth));
        assertEquals(firstRun.getPortfolio().getBalance(firstPrimaryRoth),
                secondRun.getPortfolio().getBalance(firstPrimaryRoth));
        assertEquals(firstPrimaryRoth,
                firstRun.getAllocations().getFirst().destinationAccount());
    }

    @Test
    void householdConversionIsZeroWhenNoOwnerHasASameOwnerRothDestination() {

        TraditionalIRA primaryTraditional = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("100000"));
        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("100000"));

        var result = converter.convertHousehold(createProjectedPortfolio(
                primaryTraditional, spouseTraditional), money("100000"));

        assertEquals(BigDecimal.ZERO, result.getTotalConversion());
        assertTrue(result.getAllocations().isEmpty());
        assertEquals(money("100000"), result.getPortfolio().getBalance(primaryTraditional));
        assertEquals(money("100000"), result.getPortfolio().getBalance(spouseTraditional));
    }

    @Test
    void householdRoleAllocationDoesNotDependOnInterleavedAccountOrder() {

        TraditionalIRA primaryTraditionalA = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("40000"));
        RothIRA primaryRothA = new RothIRA(
                "Primary Roth", AccountOwnership.PRIMARY, money("0"));
        TraditionalIRA spouseTraditionalA = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("100000"));
        RothIRA spouseRothA = new RothIRA(
                "Spouse Roth", AccountOwnership.SPOUSE, money("0"));

        var firstOrder = converter.convertHousehold(createProjectedPortfolio(
                primaryTraditionalA, spouseTraditionalA, primaryRothA, spouseRothA),
                money("100000"));

        TraditionalIRA primaryTraditionalB = new TraditionalIRA(
                "Primary Traditional", AccountOwnership.PRIMARY, money("40000"));
        RothIRA primaryRothB = new RothIRA(
                "Primary Roth", AccountOwnership.PRIMARY, money("0"));
        TraditionalIRA spouseTraditionalB = new TraditionalIRA(
                "Spouse Traditional", AccountOwnership.SPOUSE, money("100000"));
        RothIRA spouseRothB = new RothIRA(
                "Spouse Roth", AccountOwnership.SPOUSE, money("0"));

        var swappedOrder = converter.convertHousehold(createProjectedPortfolio(
                spouseRothB, primaryRothB, spouseTraditionalB, primaryTraditionalB),
                money("100000"));

        assertEquals(money("40000"), firstOrder.getConversion(AccountOwnership.PRIMARY));
        assertEquals(money("60000"), firstOrder.getConversion(AccountOwnership.SPOUSE));
        assertEquals(firstOrder.getConversion(AccountOwnership.PRIMARY),
                swappedOrder.getConversion(AccountOwnership.PRIMARY));
        assertEquals(firstOrder.getConversion(AccountOwnership.SPOUSE),
                swappedOrder.getConversion(AccountOwnership.SPOUSE));
    }

    private static ProjectedPortfolio createProjectedPortfolio(
            Account... accounts) {

        AccountPortfolio portfolio =
                new AccountPortfolio();

        for (Account account : accounts) {
            portfolio.addAccount(account);
        }

        return ProjectedPortfolio.from(portfolio);
    }


}
