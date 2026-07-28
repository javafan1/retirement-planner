package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;

import java.util.Objects;

public final class ProjectedWithdrawalAllocation {

    private final ProjectedPortfolio portfolio;
    private final WithdrawalBreakdown withdrawalBreakdown;

    public ProjectedWithdrawalAllocation(
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown withdrawalBreakdown) {

        this.portfolio =
                Objects.requireNonNull(
                        portfolio,
                        "Projected portfolio is required.");

        this.withdrawalBreakdown =
                Objects.requireNonNull(
                        withdrawalBreakdown,
                        "Withdrawal breakdown is required.");
    }

    public ProjectedPortfolio getPortfolio() {
        return portfolio;
    }

    public WithdrawalBreakdown getWithdrawalBreakdown() {
        return withdrawalBreakdown;
    }
}