package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityMortalityAdjustmentTest {

    @Test
    void standardFactorPreservesExactInputAndBoundaries() {
        BigDecimal qx = new BigDecimal("0.012345");
        assertSame(qx, SocialSecurityMortalityAdjustment.standard().adjust(qx));
        assertEquals(BigDecimal.ZERO,
                SocialSecurityMortalityAdjustment.of(new BigDecimal("1.5"))
                        .adjust(BigDecimal.ZERO));
        assertEquals(BigDecimal.ONE,
                SocialSecurityMortalityAdjustment.of(new BigDecimal("0.5"))
                        .adjust(BigDecimal.ONE));
    }

    @Test
    void proportionalHazardAdjustmentMovesQxInExpectedDirection() {
        BigDecimal qx = new BigDecimal("0.20");
        BigDecimal higher = SocialSecurityMortalityAdjustment.of(new BigDecimal("2"))
                .adjust(qx);
        BigDecimal lower = SocialSecurityMortalityAdjustment.of(new BigDecimal("0.5"))
                .adjust(qx);

        assertEquals(0.36d, higher.doubleValue(), 1.0e-15);
        assertTrue(higher.compareTo(qx) > 0);
        assertTrue(lower.compareTo(qx) < 0);
        assertEquals(0.10557280900008414d, lower.doubleValue(), 1.0e-15);
    }

    @Test
    void rejectsInvalidFactorsAndQx() {
        assertThrows(NullPointerException.class,
                () -> SocialSecurityMortalityAdjustment.of(null));
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityMortalityAdjustment.of(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityMortalityAdjustment.of(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityMortalityAdjustment.standard()
                        .adjust(new BigDecimal("1.01")));
    }
}
