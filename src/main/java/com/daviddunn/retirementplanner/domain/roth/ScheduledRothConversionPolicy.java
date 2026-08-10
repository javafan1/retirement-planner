package com.daviddunn.retirementplanner.domain.roth;

import java.util.Objects;

public final class ScheduledRothConversionPolicy {

    public boolean shouldExecuteConvert(
            RothConversionRequest request,
            int calendarYear,
            boolean householdSubjectToRmd) {

        Objects.requireNonNull(
                request,
                "Roth conversion request is required.");

        if (!request.isEnabled()) {
            return false;
        }

        /*
         * Conversion cannot occur before the
         * requested start year.
         */
        if (calendarYear < request.getStartYear()) {
            return false;
        }

        /*
         * Stop when the household becomes subject
         * to its first RMD.
         */
        if (request.getStopRule() ==
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD
                && householdSubjectToRmd) {

            return false;
        }

        /*
         * A one-time conversion occurs only in
         * the requested start year.
         */
        if (request.getFrequency() ==
                RothConversionFrequency.ONE_TIME
                && calendarYear != request.getStartYear()) {

            return false;
        }

        /*
         * An annual conversion continues every year
         * beginning with the requested start year,
         * subject to the stop rule above.
         */
        return true;
    }
}