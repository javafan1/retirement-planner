/*
package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;
import org.junit.jupiter.api.Test;

import static com.daviddunn.retirementplanner.domain.financial.TestMoney.money;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectedAssetWithdrawalCalculatorTest {

    private final ProjectedAssetWithdrawalCalculator calculator =
            new ProjectedAssetWithdrawalCalculator();

    @Test
    void shouldReturnSameAssetPoolsForZeroWithdrawal() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("200000"),
                        money("500000"),
                        money("300000"));

        AssetPoolWithdrawalResult result =
                calculator.applyWithdrawal(
                        pools,
                        money("0"),
                        WithdrawalStrategy.TAXABLE_FIRST);

        assertEquals(
                money("200000"),
                result.getAssetPools().getTaxableBalance());

        assertEquals(
                money("500000"),
                result.getAssetPools().getTaxDeferredBalance());

        assertEquals(
                money("300000"),
                result.getAssetPools().getRothBalance());

        assertEquals(
                money("1000000"),
                result.getAssetPools().getTotalBalance());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getCashWithdrawal());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getTaxableWithdrawal());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getTaxDeferredWithdrawal());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getRothWithdrawal());
    }

    @Test
    void shouldWithdrawFromTaxableAssetsFirst() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("200000"),
                        money("500000"),
                        money("300000"));

        AssetPoolWithdrawalResult result =
                calculator.applyWithdrawal(
                        pools,
                        money("75000"),
                        WithdrawalStrategy.TAXABLE_FIRST);

        assertEquals(
                money("125000"),
                result.getAssetPools().getTaxableBalance());

        assertEquals(
                money("500000"),
                result.getAssetPools().getTaxDeferredBalance());

        assertEquals(
                money("300000"),
                result.getAssetPools().getRothBalance());

        assertEquals(
                money("925000"),
                result.getAssetPools().getTotalBalance());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getCashWithdrawal());

        assertEquals(
                money("75000"),
                result.getWithdrawalBreakdown().getTaxableWithdrawal());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getTaxDeferredWithdrawal());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getRothWithdrawal());
    }

    @Test
    void shouldContinueWithTaxDeferredAssetsWhenTaxableAssetsAreExhausted() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("50000"),
                        money("500000"),
                        money("300000"));

        AssetPoolWithdrawalResult result =
                calculator.applyWithdrawal(
                        pools,
                        money("100000"),
                        WithdrawalStrategy.TAXABLE_FIRST);

        assertEquals(
                money("0"),
                result.getAssetPools().getTaxableBalance());

        assertEquals(
                money("450000"),
                result.getAssetPools().getTaxDeferredBalance());

        assertEquals(
                money("300000"),
                result.getAssetPools().getRothBalance());

        assertEquals(
                money("750000"),
                result.getAssetPools().getTotalBalance());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getCashWithdrawal());

        assertEquals(
                money("50000"),
                result.getWithdrawalBreakdown().getTaxableWithdrawal());

        assertEquals(
                money("50000"),
                result.getWithdrawalBreakdown().getTaxDeferredWithdrawal());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getRothWithdrawal());
    }

    @Test
    void shouldContinueWithRothAssetsWhenTaxableAndTaxDeferredAssetsAreExhausted() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("50000"),
                        money("100000"),
                        money("300000"));

        AssetPoolWithdrawalResult result =
                calculator.applyWithdrawal(
                        pools,
                        money("200000"),
                        WithdrawalStrategy.TAXABLE_FIRST);

        assertEquals(
                money("0"),
                result.getAssetPools().getTaxableBalance());

        assertEquals(
                money("0"),
                result.getAssetPools().getTaxDeferredBalance());

        assertEquals(
                money("250000"),
                result.getAssetPools().getRothBalance());

        assertEquals(
                money("250000"),
                result.getAssetPools().getTotalBalance());

        assertEquals(
                money("0"),
                result.getWithdrawalBreakdown().getCashWithdrawal());

        assertEquals(
                money("50000"),
                result.getWithdrawalBreakdown().getTaxableWithdrawal());

        assertEquals(
                money("100000"),
                result.getWithdrawalBreakdown().getTaxDeferredWithdrawal());

        assertEquals(
                money("50000"),
                result.getWithdrawalBreakdown().getRothWithdrawal());
    }
}

 */