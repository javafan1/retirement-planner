package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.time.Instant;
import java.util.*;

/** Accepted immutable calculation/context references. No live plan, controls or services. */
record SocialSecurityReportSession(UUID planId, UUID runId, String household, boolean householdRecorded,
        SocialSecurityStrategyAnalysisContext context, SocialSecurityStrategyAnalyzerPresentation presentation,
        Instant started, Instant completed) {
    static final String HOUSEHOLD_SOURCE = "Original household name";
    static final String MISSING_NAME = "Original household name was not recorded.";
    SocialSecurityReportSession {
        Objects.requireNonNull(runId); Objects.requireNonNull(household); Objects.requireNonNull(context);
        Objects.requireNonNull(presentation); Objects.requireNonNull(started); Objects.requireNonNull(completed);
        if (completed.isBefore(started)) throw new IllegalArgumentException("Invalid accepted report timestamps.");
    }
    static SocialSecurityReportSession restored(AnalysisSnapshot saved, SocialSecurityStrategyAnalysisContext context,
                                                SocialSecurityStrategyAnalyzerPresentation model) {
        String household = saved.context().household();
        boolean recorded = !"Couple".equals(household) || saved.context().request().stream().anyMatch(section ->
                section.title().equals(HOUSEHOLD_SOURCE) && section.lines().equals(List.of(household)));
        return new SocialSecurityReportSession(saved.planId(), saved.runId(), household, recorded, context, model,
                saved.context().startedAt(), saved.completedAt());
    }
    String reportHousehold() { return householdRecorded ? household : MISSING_NAME; }
}
