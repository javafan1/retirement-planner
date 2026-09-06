package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.util.Objects;

/** Runtime audit record for one owner-preserving projected Roth conversion. */
public record RothConversionAllocation(
        AccountOwnership ownership,
        Account sourceAccount,
        Account destinationAccount,
        BigDecimal amount) {

    public RothConversionAllocation {
        Objects.requireNonNull(ownership, "Account ownership is required.");
        Objects.requireNonNull(sourceAccount, "Source account is required.");
        Objects.requireNonNull(destinationAccount, "Destination account is required.");
        Objects.requireNonNull(amount, "Conversion amount is required.");

        if (ownership == AccountOwnership.JOINT
                || sourceAccount.getOwnership() != ownership
                || destinationAccount.getOwnership() != ownership) {
            throw new IllegalArgumentException(
                    "A Roth conversion must preserve individual account ownership.");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Conversion allocation amount must be positive.");
        }
    }
}
