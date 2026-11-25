package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Evidence of a same-day, fully pretax direct rollover with no mandatory amount due. */
public record Spousal401kRollover(AccountReference source, AccountOwnership survivor, LocalDate effectiveDate,
                                 BigDecimal eligibleTransferredBalance) {
    public Spousal401kRollover {
        Objects.requireNonNull(source);
        Objects.requireNonNull(survivor);
        Objects.requireNonNull(effectiveDate);
        Objects.requireNonNull(eligibleTransferredBalance);
        if (source.type() != AccountType.TRADITIONAL_401K || source.inheritedInformation().isPresent()
                || source.owner() == AccountOwnership.JOINT || survivor == AccountOwnership.JOINT
                || survivor == source.owner() || effectiveDate.getDayOfYear() != 1
                || eligibleTransferredBalance.signum() <= 0) {
            throw new IllegalArgumentException("A positive pretax 401(k) rollover must bind the source and survivor on January 1.");
        }
    }
}
