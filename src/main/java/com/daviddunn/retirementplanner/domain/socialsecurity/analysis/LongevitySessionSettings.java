package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.Objects;

/** Non-persisted conditioning/adjustment choices shared by explanatory analysis windows. */
public record LongevitySessionSettings(LocalDate conditioningDate,
        SocialSecurityMortalityAdjustment primaryAdjustment,
        SocialSecurityMortalityAdjustment spouseAdjustment) {
    public LongevitySessionSettings {
        Objects.requireNonNull(conditioningDate);
        Objects.requireNonNull(primaryAdjustment);
        Objects.requireNonNull(spouseAdjustment);
    }
    public static LongevitySessionSettings defaults(LocalDate projectionStartDate) {
        return new LongevitySessionSettings(projectionStartDate,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard());
    }
}
