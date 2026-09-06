package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/** MVP joint-life model: the two supplied mortality distributions are independent. */
public final class SocialSecurityIndependentJointMortalityCalculator {

    static final MathContext MATH_CONTEXT = MathContext.UNLIMITED;

    public BigDecimal calculate(
            BigDecimal primaryProbability,
            BigDecimal spouseProbability) {
        Objects.requireNonNull(primaryProbability, "Primary probability is required.");
        Objects.requireNonNull(spouseProbability, "Spouse probability is required.");
        return primaryProbability.multiply(spouseProbability, MATH_CONTEXT);
    }
}
