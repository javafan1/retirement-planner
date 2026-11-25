package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.util.Objects;

/** Annual obligation for the one supported same-decedent, same-beneficiary IRA group. */
public record BeneficiaryIraRmd(int year, int firstDistributionYear, AccountOwnership decedent,
                                AccountOwnership beneficiary, BigDecimal priorBalance, BigDecimal divisor,
                                BigDecimal requiredAmount) {
    public BeneficiaryIraRmd {
        Objects.requireNonNull(decedent);
        Objects.requireNonNull(beneficiary);
        Objects.requireNonNull(priorBalance);
        Objects.requireNonNull(requiredAmount);
        boolean dueYear = year >= firstDistributionYear;
        if (year <= 0 || firstDistributionYear <= 0 || decedent == beneficiary
                || decedent == AccountOwnership.JOINT || beneficiary == AccountOwnership.JOINT
                || priorBalance.signum() < 0 || requiredAmount.signum() < 0
                || (divisor != null) != dueYear || (dueYear && divisor.signum() <= 0)
                || (!dueYear && requiredAmount.signum() != 0)) {
            throw new IllegalArgumentException("Invalid spousal beneficiary IRA obligation.");
        }
    }
}
