package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Typed cause on the original exception class/message. Never classify by message.
 */
final class FundingConstraint extends RuntimeException {

    final FundingFailure.Stage stage;
    final BigDecimal required;
    final BigDecimal available;
    final Optional<AccountOwnership> owner;

    FundingConstraint(
            FundingFailure.Stage stage,
            BigDecimal required,
            BigDecimal available,
            AccountOwnership owner) {
        super("Required allocation exceeds eligible funds", null, false, false);
        this.stage = stage;
        this.required = required;
        this.available = available;
        this.owner = Optional.ofNullable(owner);
    }

    static IllegalStateException insufficient(
            String message,
            FundingFailure.Stage stage,
            BigDecimal required,
            BigDecimal remaining,
            AccountOwnership owner) {
        return new IllegalStateException(
                message, new FundingConstraint(stage, required, required.subtract(remaining), owner));
    }
}
