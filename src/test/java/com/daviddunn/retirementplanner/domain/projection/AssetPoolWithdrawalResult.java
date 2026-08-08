package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;

import java.util.Objects;

public final class AssetPoolWithdrawalResult {

    private final ProjectedAssetPools assetPools;

    private final WithdrawalBreakdown withdrawalBreakdown;

    public AssetPoolWithdrawalResult(
            ProjectedAssetPools assetPools,
            WithdrawalBreakdown withdrawalBreakdown) {

        this.assetPools =
                Objects.requireNonNull(
                        assetPools,
                        "Projected asset pools are required.");

        this.withdrawalBreakdown =
                Objects.requireNonNull(
                        withdrawalBreakdown,
                        "Withdrawal breakdown is required.");
    }

    public ProjectedAssetPools getAssetPools() {
        return assetPools;
    }

    public WithdrawalBreakdown getWithdrawalBreakdown() {
        return withdrawalBreakdown;
    }
}