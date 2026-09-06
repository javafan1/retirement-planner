package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxAssumptionsFutureRateChangeTest {

    @Test
    void representsUnconfiguredStateWithNullPair() {

        TaxAssumptions assumptions = assumptions(null, null);

        assertNull(
                assumptions.getFutureFederalMarginalRateAdjustment());
        assertNull(
                assumptions.getFutureFederalMarginalRateEffectiveYear());
    }

    @Test
    void acceptsConfiguredZeroPercentAsDistinctState() {

        TaxAssumptions assumptions =
                assumptions(BigDecimal.ZERO, 2030);

        assertEquals(
                BigDecimal.ZERO,
                assumptions.getFutureFederalMarginalRateAdjustment());
        assertEquals(
                2030,
                assumptions.getFutureFederalMarginalRateEffectiveYear());
    }

    @Test
    void rejectsPartialConfiguration() {

        assertThrows(
                IllegalArgumentException.class,
                () -> assumptions(
                        new BigDecimal("0.05"),
                        null));

        assertThrows(
                IllegalArgumentException.class,
                () -> assumptions(
                        null,
                        2030));
    }

    private TaxAssumptions assumptions(
            BigDecimal adjustment,
            Integer effectiveYear) {

        return new TaxAssumptions(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                FilingStatus.MARRIED_FILING_JOINTLY,
                new BigDecimal("0.25"),
                adjustment,
                effectiveYear);
    }
}
