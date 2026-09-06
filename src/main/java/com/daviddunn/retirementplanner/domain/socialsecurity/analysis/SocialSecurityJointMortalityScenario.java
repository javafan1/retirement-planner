package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One immutable independent joint-life mortality scenario. */
public record SocialSecurityJointMortalityScenario(
        int primaryDeathAge,
        int spouseDeathAge,
        LocalDate primaryDeathDate,
        LocalDate spouseDeathDate,
        BigDecimal primaryProbability,
        BigDecimal spouseProbability,
        BigDecimal jointProbability) {

    public SocialSecurityJointMortalityScenario {
        Objects.requireNonNull(primaryDeathDate, "Primary death date is required.");
        Objects.requireNonNull(spouseDeathDate, "Spouse death date is required.");
        Objects.requireNonNull(primaryProbability, "Primary probability is required.");
        Objects.requireNonNull(spouseProbability, "Spouse probability is required.");
        Objects.requireNonNull(jointProbability, "Joint probability is required.");
    }
}
