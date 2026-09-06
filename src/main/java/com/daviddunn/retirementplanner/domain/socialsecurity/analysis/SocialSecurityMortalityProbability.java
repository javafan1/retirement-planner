package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/** One whole-age death probability in a complete mortality distribution. */
public record SocialSecurityMortalityProbability(
        int deathAge,
        BigDecimal probability) {

    public SocialSecurityMortalityProbability {
        SocialSecurityDeathDateCalculator.validateDeathAge(
                deathAge,
                "Mortality death age " + deathAge);
        Objects.requireNonNull(probability, "Mortality probability is required.");
        if (probability.signum() < 0 || probability.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Mortality probability for death age "
                            + deathAge
                            + " must be between 0 and 1.");
        }
    }
}
