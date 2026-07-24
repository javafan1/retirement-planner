package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;

public final class AccountFactory {

    private AccountFactory() {
    }

    public static Account create(
            AccountType type,
            String name,
            AccountOwnership ownership,
            BigDecimal balance) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        Objects.requireNonNull(ownership);
        Objects.requireNonNull(balance);

        return switch (type) {

            case TRADITIONAL_IRA ->
                    new TraditionalIRA(
                            name,
                            ownership,
                            balance);

            case ROTH_IRA ->
                    new RothIRA(
                            name,
                            ownership,
                            balance);

            case ROLLOVER_IRA ->
                    new RolloverIRA(
                            name,
                            ownership,
                            balance);

            case TRADITIONAL_401K ->
                    new Traditional401K(
                            name,
                            ownership,
                            balance);

            case TRADITIONAL_403B ->
                    new Traditional403B(
                            name,
                            ownership,
                            balance);

            case ROTH_401K ->
                    new Roth401K(
                            name,
                            ownership,
                            balance);

            case BROKERAGE ->
                    new BrokerageAccount(
                            name,
                            ownership,
                            balance);

            case CHECKING ->
                    new CheckingAccount(
                            name,
                            ownership,
                            balance);

            case SAVINGS ->
                    new SavingsAccount(
                            name,
                            ownership,
                            balance);

            default ->
                    throw new UnsupportedOperationException(
                            "Account type not yet implemented: " + type);
        };
    }

    public static java.util.List<AccountType> getSupportedTypes() {

        return java.util.List.of(
                AccountType.TRADITIONAL_IRA,
                AccountType.ROLLOVER_IRA,
                AccountType.TRADITIONAL_401K,
                AccountType.TRADITIONAL_403B,
                AccountType.ROTH_IRA,
                AccountType.ROTH_401K,
                AccountType.BROKERAGE,
                AccountType.CHECKING,
                AccountType.SAVINGS);
    }

}
