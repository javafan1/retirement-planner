package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;

import java.util.Objects;

public final class WithdrawalStrategyFactory {

    private WithdrawalStrategyFactory() {
    }

    public static WithdrawalStrategy create(
            WithdrawalStrategyType strategyType) {

        Objects.requireNonNull(
                strategyType,
                "Withdrawal strategy type is required.");

        return switch (strategyType) {

            case TAXABLE_FIRST ->
                    new TaxableFirstWithdrawalStrategy();

            case TAX_DEFERRED_FIRST ->
                    new TaxDeferredFirstWithdrawalStrategy();
        };
    }
}