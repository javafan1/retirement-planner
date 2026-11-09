package com.daviddunn.retirementplanner.domain.benefit;

import java.math.BigDecimal;
import java.util.Objects;

/** Saved arithmetic evidence. Remaining investment is a tax ledger, never an account asset. */
public record AnnuityBasisRecovery(NonqualifiedAnnuityTerms terms, int paymentsBefore,
        int payments, BigDecimal opening, BigDecimal recovery, BigDecimal closing) {
    public AnnuityBasisRecovery {
        Objects.requireNonNull(terms);
        Objects.requireNonNull(opening); Objects.requireNonNull(recovery); Objects.requireNonNull(closing);
        if (paymentsBefore < 0 || paymentsBefore > 108000 || payments < 0 || payments > 12
                || opening.signum() < 0 || recovery.signum() < 0 || closing.signum() < 0
                || opening.compareTo(terms.openingInvestment().subtract(terms.excludedPerPayment()
                    .multiply(BigDecimal.valueOf(paymentsBefore))).max(BigDecimal.ZERO)) != 0
                || recovery.compareTo(opening.min(terms.excludedPerPayment().multiply(BigDecimal.valueOf(payments)))) != 0
                || closing.compareTo(opening.subtract(recovery)) != 0)
            throw new IllegalArgumentException("Invalid finite annuity basis evidence.");
    }
}
