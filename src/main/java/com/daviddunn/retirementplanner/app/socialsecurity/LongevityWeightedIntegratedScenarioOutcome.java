package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Compact financial outcome; no projection or account references are retained. */
public record LongevityWeightedIntegratedScenarioOutcome(
        int primaryDeathYear,
        int spouseDeathYear,
        BigDecimal probability,
        EstateAtSecondDeathSnapshot estateSnapshot,
        BigDecimal pvEstate) {
    public LongevityWeightedIntegratedScenarioOutcome {
        Objects.requireNonNull(probability);
        Objects.requireNonNull(estateSnapshot);
        Objects.requireNonNull(pvEstate);
    }

    public LocalDate secondDeathDate() {
        return estateSnapshot.effectiveSecondDeathDate();
    }
}
