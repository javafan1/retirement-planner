package com.daviddunn.retirementplanner.domain.roth;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledRothConversionPolicyTest {

    private final ScheduledRothConversionPolicy policy =
            new ScheduledRothConversionPolicy();


    @Test
    void oneTimeConversionExecutesOnlyInStartYear() {

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionFrequency.ONE_TIME);

        assertTrue(
                policy.shouldExecuteConvert(
                        request,
                        2026,
                        false));

        assertFalse(
                policy.shouldExecuteConvert(
                        request,
                        2027,
                        false));
    }


    @Test
    void annualConversionExecutesEveryYearStartingAtStartYear() {

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionFrequency.ANNUAL);

        assertTrue(
                policy.shouldExecuteConvert(
                        request,
                        2026,
                        false));

        assertTrue(
                policy.shouldExecuteConvert(
                        request,
                        2027,
                        false));

        assertTrue(
                policy.shouldExecuteConvert(
                        request,
                        2028,
                        false));
    }


    @Test
    void annualConversionDoesNotExecuteBeforeStartYear() {

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionFrequency.ANNUAL);

        assertFalse(
                policy.shouldExecuteConvert(
                        request,
                        2025,
                        false));
    }


    @Test
    void annualConversionStopsWhenHouseholdBecomesSubjectToRmd() {

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionFrequency.ANNUAL);

        assertTrue(
                policy.shouldExecuteConvert(
                        request,
                        2026,
                        false));

        assertTrue(
                policy.shouldExecuteConvert(
                        request,
                        2027,
                        false));

        assertFalse(
                policy.shouldExecuteConvert(
                        request,
                        2028,
                        true));
    }


    @Test
    void annualConversionRemainsStoppedAfterFirstHouseholdRmd() {

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionFrequency.ANNUAL);

        assertFalse(
                policy.shouldExecuteConvert(
                        request,
                        2029,
                        true));
    }


    @Test
    void disabledConversionNeverExecutes() {

        RothConversionRequest request =
                new RothConversionRequest(
                        false,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionFrequency.ANNUAL);

        assertFalse(
                policy.shouldExecuteConvert(
                        request,
                        2026,
                        false));

        assertFalse(
                policy.shouldExecuteConvert(
                        request,
                        2027,
                        false));
    }
}