package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class WithdrawalAssumptions {

    private final WithdrawalStrategyType withdrawalStrategyType;

    @JsonCreator
    public WithdrawalAssumptions(
            @JsonProperty("withdrawalStrategyType")
            WithdrawalStrategyType withdrawalStrategyType) {

        this.withdrawalStrategyType =
                Objects.requireNonNull(
                        withdrawalStrategyType,
                        "Withdrawal strategy type is required.");
    }

    public WithdrawalStrategyType getWithdrawalStrategyType() {
        return withdrawalStrategyType;
    }

    @Override
    public String toString() {

        return "WithdrawalAssumptions{" +
                "withdrawalStrategyType=" +
                withdrawalStrategyType +
                '}';
    }
}