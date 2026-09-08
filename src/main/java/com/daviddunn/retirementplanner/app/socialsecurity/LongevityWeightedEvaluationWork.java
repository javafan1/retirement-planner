package com.daviddunn.retirementplanner.app.socialsecurity;

/** Internal accounting only; events do not change financial calculations or public progress. */
enum LongevityWeightedEvaluationWork {
    SCENARIO_STARTED,
    PROJECTION_STARTED,
    PROJECTION_COMPLETED,
    SCENARIO_COMPLETED
}
