package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;

import java.math.BigDecimal;
import java.util.Objects;

/** One annual obligation for one retained employer plan. Only this source may pay it. */
public record Beneficiary401kRmd(AccountReference source, int year, int firstDistributionYear,
                                 AccountOwnership beneficiary, BigDecimal priorBalance, BigDecimal divisor,
                                 BigDecimal requiredAmount) {
    public Beneficiary401kRmd {
        Objects.requireNonNull(source);
        Objects.requireNonNull(beneficiary);
        Objects.requireNonNull(priorBalance);
        Objects.requireNonNull(requiredAmount);
        boolean dueYear = year >= firstDistributionYear;
        if (source.type() != AccountType.TRADITIONAL_401K || source.owner() == AccountOwnership.JOINT
                || beneficiary == AccountOwnership.JOINT || source.owner() == beneficiary
                || year <= 0 || firstDistributionYear <= 0 || priorBalance.signum() < 0
                || (divisor != null) != dueYear || (dueYear && divisor.signum() <= 0)
                || requiredAmount.compareTo(dueYear
                    ? RmdCalculator.divideByDistributionPeriod(priorBalance, divisor) : BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Invalid spousal beneficiary 401(k) obligation.");
        }
    }
}
