package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RothEligibilityExecutionTest {
    private final ProjectedPortfolioRothConverter converter = new ProjectedPortfolioRothConverter();
    private static final BigDecimal AMOUNT = new BigDecimal("50000.00");

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void everyAccountTypeHasTheCorrectPretaxSourceCapacity(AccountType type) {
        Account source = account(type, AccountOwnership.PRIMARY, new BigDecimal("100000.00"));
        Account destination = account(AccountType.ROTH_IRA, AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var portfolio = portfolio(source, destination);
        boolean supported = Set.of(AccountType.TRADITIONAL_IRA, AccountType.ROLLOVER_IRA,
                AccountType.TRADITIONAL_401K, AccountType.TRADITIONAL_403B).contains(type);
        assertEquals(supported, source.isEligibleForRothConversion());
        assertEquals(0, converter.getMaximumConvertibleAmount(portfolio)
                .compareTo(supported ? source.getCurrentBalance() : BigDecimal.ZERO));
        var result = converter.convertHousehold(portfolio, AMOUNT);
        assertEquals(0, result.getTotalConversion().compareTo(supported ? AMOUNT : BigDecimal.ZERO));
        assertEquals(portfolio.getTotalBalance(), result.getPortfolio().getTotalBalance());
        assertEquals(new BigDecimal("100000.00"), source.getCurrentBalance());
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"ROTH_401K", "INHERITED_ROTH_IRA"})
    void unsupportedRothDestinationNeverSuppliesCapacityOrReceivesFunds(AccountType type) {
        Account source = account(AccountType.TRADITIONAL_IRA, AccountOwnership.PRIMARY, AMOUNT);
        Account invalid = account(type, AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var portfolio = portfolio(source, invalid);
        assertEquals(0, converter.getMaximumConvertibleAmount(portfolio).signum());
        var result = converter.convertHousehold(portfolio, AMOUNT);
        assertEquals(0, result.getTotalConversion().signum());
        assertTrue(result.getAllocations().isEmpty());
        assertEquals(AMOUNT, result.getPortfolio().getBalance(source));
        assertThrows(IllegalStateException.class,
                () -> converter.convert(portfolio, AccountOwnership.PRIMARY, AMOUNT));
    }

    @Test
    void skipsUnsupportedDestinationsAndUsesFirstOrdinarySameOwnerRothIra() {
        Account source = account(AccountType.ROLLOVER_IRA, AccountOwnership.SPOUSE, AMOUNT);
        Account employerRoth = account(AccountType.ROTH_401K, AccountOwnership.SPOUSE, BigDecimal.ZERO);
        Account inherited = account(AccountType.INHERITED_ROTH_IRA, AccountOwnership.SPOUSE, BigDecimal.ZERO);
        Account wrongOwner = account(AccountType.ROTH_IRA, AccountOwnership.PRIMARY, BigDecimal.ZERO);
        Account first = account(AccountType.ROTH_IRA, AccountOwnership.SPOUSE, BigDecimal.ZERO);
        Account second = account(AccountType.ROTH_IRA, AccountOwnership.SPOUSE, BigDecimal.ZERO);
        var portfolio = portfolio(source, employerRoth, inherited, wrongOwner, first, second);
        var result = converter.convertHousehold(portfolio, AMOUNT);
        assertEquals(AMOUNT, result.getPortfolio().getBalance(first));
        assertEquals(0, result.getPortfolio().getBalance(second).signum());
        assertSame(first, result.getAllocations().getFirst().destinationAccount());
        assertEquals(AMOUNT, result.getConversion(AccountOwnership.SPOUSE));
        assertEquals(portfolio.getTotalBalance(), result.getPortfolio().getTotalBalance());
    }

    @Test
    void rothEmployerAccountCannotConvertToItself() {
        Account roth = account(AccountType.ROTH_401K, AccountOwnership.PRIMARY, AMOUNT);
        var result = converter.convertHousehold(portfolio(roth), AMOUNT);
        assertEquals(0, result.getTotalConversion().signum());
        assertTrue(result.getIncomeSources(2026).isEmpty());
        assertEquals(AMOUNT, result.getPortfolio().getBalance(roth));
    }

    @Test
    void absentOwnerCannotSupplyCapacityAndZeroBalanceIsNotAnEligibleFund() {
        Account source = account(AccountType.ROLLOVER_IRA, AccountOwnership.SPOUSE, AMOUNT);
        Account destination = account(AccountType.ROTH_IRA, AccountOwnership.SPOUSE, BigDecimal.ZERO);
        var portfolio = portfolio(source, destination);
        assertEquals(0, converter.getMaximumConvertibleAmount(portfolio, Set.of(AccountOwnership.PRIMARY)).signum());
        assertEquals(0, converter.convertHousehold(portfolio, AMOUNT, Set.of(AccountOwnership.PRIMARY))
                .getTotalConversion().signum());
        var exhausted = portfolio.withBalance(source, BigDecimal.ZERO);
        assertTrue(source.isEligibleForRothConversion());
        assertEquals(0, converter.getMaximumConvertibleAmount(exhausted).signum());
        assertEquals(0, converter.convertHousehold(exhausted, AMOUNT).getTotalConversion().signum());
    }

    static Account account(AccountType type, AccountOwnership owner, BigDecimal balance) {
        if (type == AccountType.INHERITED_ROTH_IRA || type == AccountType.INHERITED_TRADITIONAL_IRA) {
            return AccountFactory.createInherited(type, "Inherited", owner, balance,
                    new InheritedAccountInformation(LocalDate.of(1930, 1, 1), LocalDate.of(2020, 1, 1),
                            BeneficiaryRelationship.CHILD));
        }
        return AccountFactory.create(type, "Account", owner, balance);
    }

    static ProjectedPortfolio portfolio(Account... accounts) {
        return ProjectedPortfolio.from(new AccountPortfolio(List.of(accounts)));
    }
}
