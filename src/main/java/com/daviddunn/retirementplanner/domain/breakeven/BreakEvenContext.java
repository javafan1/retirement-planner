package com.daviddunn.retirementplanner.domain.breakeven;

import java.util.List;
import java.util.Map;

/** Explanatory context only: never used to weight any deterministic metric. */
public record BreakEvenContext(List<BreakEvenEvent> events, Map<Integer, BreakEvenSurvivalPoint> survival,
        String survivalExplanation) {
    public BreakEvenContext {
        events = List.copyOf(events);
        survival = Map.copyOf(survival);
    }
    public static BreakEvenContext unavailable() {
        return new BreakEvenContext(List.of(), Map.of(), "Survival probability unavailable: mortality inputs have not been supplied.");
    }
}
