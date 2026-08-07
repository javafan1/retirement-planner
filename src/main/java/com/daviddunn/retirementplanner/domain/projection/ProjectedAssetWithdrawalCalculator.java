package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedAssetWithdrawalCalculator {

    public ProjectedAssetPools applyWithdrawal(
            ProjectedAssetPools assetPools,
            BigDecimal withdrawalAmount,
            WithdrawalStrategy withdrawalStrategy) {

        Objects.requireNonNull(
                assetPools,
                "Projected asset pools are required.");

        Objects.requireNonNull(
                withdrawalAmount,
                "Withdrawal amount is required.");

        Objects.requireNonNull(
                withdrawalStrategy,
                "Withdrawal strategy is required.");

        if (withdrawalAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount cannot be negative.");
        }

        return assetPools;
    }
}