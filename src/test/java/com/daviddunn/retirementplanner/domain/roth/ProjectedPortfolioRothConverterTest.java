package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import org.junit.jupiter.api.Test;

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