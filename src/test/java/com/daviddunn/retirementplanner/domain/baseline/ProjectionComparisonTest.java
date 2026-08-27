package com.daviddunn.retirementplanner.domain.baseline;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionComparisonTest {

    @Test
    void calculatesChangesAsCurrentMinusBaseline() {

        ProjectionComparison comparison =
                new ProjectionComparison(
                        2040,
                        amount(100), amount(130),
                        amount(200), amount(250),
                        amount(300), amount(275),
                        amount(50), amount(40),
                        amount(1_000), amount(1_200),
                        amount(1_500), amount(1_800),
                        amount(900), amount(1_100));

        assertEquals(amount(30), comparison.getInvestmentGrowthChange());
        assertEquals(amount(50), comparison.getTotalIncomeChange());
        assertEquals(amount(-25), comparison.getTotalTaxesChange());
        assertEquals(amount(-10), comparison.getPeakAnnualTaxChange());
        assertEquals(amount(200), comparison.getEndingInvestableAssetsChange());
        assertEquals(amount(300), comparison.getNetWorthChange());
        assertEquals(amount(200), comparison.getAfterTaxEstateChange());
    }

    private BigDecimal amount(long value) {
        return BigDecimal.valueOf(value);
    }
}
