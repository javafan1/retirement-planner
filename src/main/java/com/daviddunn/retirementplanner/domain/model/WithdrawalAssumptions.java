package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.withdrawal.RothConversionStrategy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class WithdrawalAssumptions {

    private final WithdrawalStrategyType withdrawalStrategyType;

    private final RothConversionStrategy
            rothConversionStrategy;


    @JsonCreator
    public WithdrawalAssumptions(

            @JsonProperty("withdrawalStrategyType")
            WithdrawalStrategyType withdrawalStrategyType,

            @JsonProperty("rothConversionStrategy")
            RothConversionStrategy rothConversionStrategy) {

        this.withdrawalStrategyType =
                Objects.requireNonNull(
                        withdrawalStrategyType,
                        "Withdrawal strategy type is required.");

        this.rothConversionStrategy =
                rothConversionStrategy == null
                        ? RothConversionStrategy.NONE
                        : rothConversionStrategy;
    }

    public WithdrawalAssumptions(
            WithdrawalStrategyType withdrawalStrategyType) {

        this(
                withdrawalStrategyType,
                RothConversionStrategy.NONE);
    }

    public WithdrawalStrategyType getWithdrawalStrategyType() {
        return withdrawalStrategyType;
    }

    @Override
    public String toString() {

        return "WithdrawalAssumptions{" +
                "withdrawalStrategyType=" +
                withdrawalStrategyType +
                ", rothConversionStrategy=" +
                rothConversionStrategy +
                '}';
    }

    public RothConversionStrategy
    getRothConversionStrategy() {

        return rothConversionStrategy;
    }
}