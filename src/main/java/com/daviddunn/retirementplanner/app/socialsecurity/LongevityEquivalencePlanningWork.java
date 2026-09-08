package com.daviddunn.retirementplanner.app.socialsecurity;

/** SS proof work only; never financial ProjectionEngine work. Counts belong to this planner invocation. */
public record LongevityEquivalencePlanningWork(
        long logicalScheduleRequests,
        long memberValidations,
        long carrierCompatibilityValidations,
        long carrierScheduleCalculations,
        long independentScheduleCalculations,
        long failedScheduleRequests,
        long annualRowsGenerated,
        int coverageGroups,
        int coveredScenarios,
        long scheduleCalculationsAvoidedByCoverage,
        int fallbackScenarios) {
    public long totalAuthoritativeCalculations() {
        return carrierScheduleCalculations + independentScheduleCalculations;
    }
}
