package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WithdrawalStrategyFactoryTest {

    @Test
    void createsTaxableFirstWithdrawalStrategy() {

        WithdrawalStrategy strategy =
                WithdrawalStrategyFactory.create(
                        WithdrawalStrategyType.TAXABLE_FIRST);

        assertInstanceOf(
                TaxableFirstWithdrawalStrategy.class,
                strategy);
    }

    @Test
    void createsTaxDeferredFirstWithdrawalStrategy() {

        WithdrawalStrategy strategy =
                WithdrawalStrategyFactory.create(
                        WithdrawalStrategyType.TAX_DEFERRED_FIRST);

        assertInstanceOf(
                TaxDeferredFirstWithdrawalStrategy.class,
                strategy);
    }
}