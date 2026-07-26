package com.daviddunn.retirementplanner.domain.withdrawal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WithdrawalDispositionTest {

    @Test
    void calculatesTotalDistributed() {

        WithdrawalDisposition disposition =
                new WithdrawalDisposition(
                        new BigDecimal("30000.00"),
                        new BigDecimal("9430.89"));

        assertEquals(
                0,
                new BigDecimal("39430.89")
                        .compareTo(
                                disposition
                                        .getTotalDistributed()));
    }

    @Test
    void rejectsNegativeSpentAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new WithdrawalDisposition(
                        new BigDecimal("-1.00"),
                        BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeReinvestedAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new WithdrawalDisposition(
                        BigDecimal.ZERO,
                        new BigDecimal("-1.00")));
    }
}