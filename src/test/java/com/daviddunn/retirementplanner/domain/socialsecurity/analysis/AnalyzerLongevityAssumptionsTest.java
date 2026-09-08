package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerLongevityAssumptionsTest {

    @Test
    void immutableValuePreservesIndependentPersonAssumptionsAndMethodology() {
        var first = assumptions();
        var second = assumptions();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(SocialSecurityMortalityCategory.MALE, first.primaryCategory());
        assertEquals(SocialSecurityMortalityCategory.FEMALE, first.spouseCategory());
        assertEquals(new BigDecimal("0.80"), first.primaryAdjustment().factor());
        assertEquals(new BigDecimal("1.50"), first.spouseAdjustment().factor());
        assertEquals(LocalDate.of(2026, 7, 1), first.mortalityBaseDate());
        assertEquals(SocialSecurityMortalityTables.ssaPeriod2022().metadata(), first.tableMetadata());
        assertTrue(first.assumesIndependentMortality());
        assertEquals(SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL,
                first.partialYearConvention());
        assertNotEquals(first, new AnalyzerLongevityAssumptions(
                first.spouseCategory(), first.spouseAdjustment(), first.primaryCategory(), first.primaryAdjustment(),
                first.mortalityBaseDate(), first.tableMetadata(), first.partialYearConvention()));
    }

    @Test
    void everyAssumptionIsRequired() {
        var a = assumptions();
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(null, a.primaryAdjustment(), a.spouseCategory(), a.spouseAdjustment(), a.mortalityBaseDate(), a.tableMetadata(), a.partialYearConvention())),
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(a.primaryCategory(), null, a.spouseCategory(), a.spouseAdjustment(), a.mortalityBaseDate(), a.tableMetadata(), a.partialYearConvention())),
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(a.primaryCategory(), a.primaryAdjustment(), null, a.spouseAdjustment(), a.mortalityBaseDate(), a.tableMetadata(), a.partialYearConvention())),
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(a.primaryCategory(), a.primaryAdjustment(), a.spouseCategory(), null, a.mortalityBaseDate(), a.tableMetadata(), a.partialYearConvention())),
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(a.primaryCategory(), a.primaryAdjustment(), a.spouseCategory(), a.spouseAdjustment(), null, a.tableMetadata(), a.partialYearConvention())),
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(a.primaryCategory(), a.primaryAdjustment(), a.spouseCategory(), a.spouseAdjustment(), a.mortalityBaseDate(), null, a.partialYearConvention())),
                () -> assertThrows(NullPointerException.class, () -> new AnalyzerLongevityAssumptions(a.primaryCategory(), a.primaryAdjustment(), a.spouseCategory(), a.spouseAdjustment(), a.mortalityBaseDate(), a.tableMetadata(), null)));
    }

    private AnalyzerLongevityAssumptions assumptions() {
        return new AnalyzerLongevityAssumptions(
                SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityAdjustment.of(new BigDecimal("0.80")),
                SocialSecurityMortalityCategory.FEMALE, SocialSecurityMortalityAdjustment.of(new BigDecimal("1.50")),
                LocalDate.of(2026, 7, 1), SocialSecurityMortalityTables.ssaPeriod2022().metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
    }
}
