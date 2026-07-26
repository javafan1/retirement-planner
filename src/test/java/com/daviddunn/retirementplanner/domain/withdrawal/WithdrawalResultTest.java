package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WithdrawalResultTest {

    private WithdrawalCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WithdrawalCalculator();
    }


    @Test
    void calculatesExcessRmdWhenRmdExceedsCashFlowNeed() {

        WithdrawalResult result =
                new WithdrawalResult(
                        new BigDecimal("40000"),
                        new BigDecimal("55000"),
                        new BigDecimal("55000"));

        assertEquals(
                0,
                new BigDecimal("15000")
                        .compareTo(
                                result.getExcessRmd()));
    }

    @Test
    void excessRmdIsZeroWhenCashFlowNeedExceedsRmd() {

        WithdrawalResult result =
                new WithdrawalResult(
                        new BigDecimal("40000"),
                        new BigDecimal("30000"),
                        new BigDecimal("40000"));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getExcessRmd()));
    }

    @Test
    void retainsWithdrawalComponents() {

        WithdrawalResult result =
                new WithdrawalResult(
                        new BigDecimal("40000"),
                        new BigDecimal("30000"),
                        new BigDecimal("40000"));

        assertEquals(
                0,
                new BigDecimal("40000")
                        .compareTo(
                                result.getCashFlowNeed()));

        assertEquals(
                0,
                new BigDecimal("30000")
                        .compareTo(
                                result.getRequiredMinimumDistribution()));

        assertEquals(
                0,
                new BigDecimal("40000")
                        .compareTo(
                                result.getTotalWithdrawal()));
    }

    @Test
    void rejectsNegativeAmounts() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new WithdrawalResult(
                        new BigDecimal("-1"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));

        assertThrows(
                IllegalArgumentException.class,
                () -> new WithdrawalResult(
                        BigDecimal.ZERO,
                        new BigDecimal("-1"),
                        BigDecimal.ZERO));

        assertThrows(
                IllegalArgumentException.class,
                () -> new WithdrawalResult(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("-1")));
    }
    @Test
    void cashFlowNeedControlsWhenGreaterThanRmd() {

        WithdrawalResult result =
                calculator.calculateWithdrawal(
                        new BigDecimal("60000"),
                        new BigDecimal("100000"),
                        new BigDecimal("30000"));

        assertEquals(
                0,
                new BigDecimal("40000")
                        .compareTo(
                                result.getCashFlowNeed()));

        assertEquals(
                0,
                new BigDecimal("30000")
                        .compareTo(
                                result.getRequiredMinimumDistribution()));

        assertEquals(
                0,
                new BigDecimal("40000")
                        .compareTo(
                                result.getTotalWithdrawal()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getExcessRmd()));
    }

    @Test
    void rmdControlsWhenGreaterThanCashFlowNeed() {

        WithdrawalResult result =
                calculator.calculateWithdrawal(
                        new BigDecimal("60000"),
                        new BigDecimal("100000"),
                        new BigDecimal("55000"));

        assertEquals(
                0,
                new BigDecimal("40000")
                        .compareTo(
                                result.getCashFlowNeed()));

        assertEquals(
                0,
                new BigDecimal("55000")
                        .compareTo(
                                result.getRequiredMinimumDistribution()));

        assertEquals(
                0,
                new BigDecimal("55000")
                        .compareTo(
                                result.getTotalWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("15000")
                        .compareTo(
                                result.getExcessRmd()));
    }

    @Test
    void rmdForcesWithdrawalEvenWhenIncomeCoversExpenses() {

        WithdrawalResult result =
                calculator.calculateWithdrawal(
                        new BigDecimal("120000"),
                        new BigDecimal("100000"),
                        new BigDecimal("30000"));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getCashFlowNeed()));

        assertEquals(
                0,
                new BigDecimal("30000")
                        .compareTo(
                                result.getTotalWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("30000")
                        .compareTo(
                                result.getExcessRmd()));
    }

    @Test
    void createsDispositionWhenRmdExceedsCashFlowNeed() {

        WithdrawalResult result =
                new WithdrawalResult(
                        new BigDecimal("30000.00"),
                        new BigDecimal("39430.89"),
                        new BigDecimal("39430.89"));

        WithdrawalDisposition disposition =
                result.getDisposition();

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                disposition.getSpent()));

        assertEquals(
                0,
                new BigDecimal("9430.89")
                        .compareTo(
                                disposition.getReinvested()));

        assertEquals(
                0,
                new BigDecimal("39430.89")
                        .compareTo(
                                disposition.getTotalDistributed()));
    }

    @Test
    void createsDispositionWhenCashFlowNeedExceedsRmd() {

        WithdrawalResult result =
                new WithdrawalResult(
                        new BigDecimal("50000.00"),
                        new BigDecimal("30000.00"),
                        new BigDecimal("50000.00"));

        WithdrawalDisposition disposition =
                result.getDisposition();

        assertEquals(
                0,
                new BigDecimal("50000.00")
                        .compareTo(
                                disposition.getSpent()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        disposition.getReinvested()));

        assertEquals(
                0,
                new BigDecimal("50000.00")
                        .compareTo(
                                disposition.getTotalDistributed()));
    }

    @Test
    void calculatesAdditionalWithdrawalWhenRmdIsLessThanCashFlowNeed() {

        WithdrawalCalculator calculator =
                new WithdrawalCalculator();

        WithdrawalResult result =
                calculator.calculateWithdrawal(
                        new BigDecimal("60000"),
                        new BigDecimal("100000"),
                        new BigDecimal("25000"));

        assertEquals(
                0,
                new BigDecimal("15000")
                        .compareTo(
                                result.getAdditionalWithdrawalRequired()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getExcessRmd()));
    }

    @Test
    void calculatesExcessRmdWhenRmdExceedsCashFlowNeed2() {

        WithdrawalCalculator calculator =
                new WithdrawalCalculator();

        WithdrawalResult result =
                calculator.calculateWithdrawal(
                        new BigDecimal("80000"),
                        new BigDecimal("100000"),
                        new BigDecimal("30000"));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getAdditionalWithdrawalRequired()));

        assertEquals(
                0,
                new BigDecimal("10000")
                        .compareTo(
                                result.getExcessRmd()));
    }

}