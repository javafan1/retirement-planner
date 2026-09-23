package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * First unsatisfied allocation request, not total annual unmet spending.
 * Available funds respect owner/account constraints. Ages are attained at December 31.
 */
public record FundingFailure(
        int calendarYear,
        int projectionYearIndex,
        Optional<Integer> primaryAge,
        Optional<Integer> spouseAge,
        Stage stage,
        Optional<AccountOwnership> owner,
        BigDecimal requiredAmount,
        BigDecimal availableAmount,
        BigDecimal shortfallAmount) {

    public enum Stage {
        WITHDRAWAL_ALLOCATION,
        WITHDRAWAL_ESTIMATE,
        IRA_RMD,
        ACCOUNT_RMD
    }

    public FundingFailure {
        Objects.requireNonNull(primaryAge);
        Objects.requireNonNull(spouseAge);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(requiredAmount);
        Objects.requireNonNull(availableAmount);
        Objects.requireNonNull(shortfallAmount);
        if (requiredAmount.signum() < 0
                || availableAmount.signum() < 0
                || shortfallAmount.signum() <= 0
                || requiredAmount.subtract(availableAmount).compareTo(shortfallAmount) != 0) {
            throw new IllegalArgumentException("Invalid funding shortfall amounts.");
        }
    }
}
