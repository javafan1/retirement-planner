package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;

import java.math.BigDecimal;

public final class AccountFactory {

    private AccountFactory() {
    }

    public static Account create(AccountType type,
                                 String name,
                                 BigDecimal balance) {

        switch (type) {

            case TRADITIONAL_IRA:
                return new TraditionalIRA(name, balance);

            case ROTH_IRA:
                return new RothIRA(name, balance);

            default:
                throw new IllegalArgumentException(
                        "Unsupported account type: " + type);
        }
    }
}