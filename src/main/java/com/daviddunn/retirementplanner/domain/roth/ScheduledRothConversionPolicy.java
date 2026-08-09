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

        if (calendarYear != request.getStartYear()) {
            return false;
        }

        if (request.getStopRule() ==
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD
                && householdSubjectToRmd) {

            return false;
        }

        return true;
    }
}