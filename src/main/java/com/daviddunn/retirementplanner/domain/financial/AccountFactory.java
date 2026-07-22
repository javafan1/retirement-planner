package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.PersonRole;

import java.math.BigDecimal;
import java.util.Objects;

public final class AccountFactory {

    private AccountFactory() {
    }

    public static Account create(
            AccountType type,
            String name,
            PersonRole owner,
            BigDecimal balance) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(balance);

        return switch (type) {

            case TRADITIONAL_IRA ->
                    new TraditionalIRA(
                            name,
                            owner,
                            balance);

            case ROTH_IRA ->
                    new RothIRA(
                            name,
                            owner,
                            balance);

            default ->
                    throw new UnsupportedOperationException(
                            "Account type not yet implemented: " + type);
        };
    }
}
/*
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
*/

