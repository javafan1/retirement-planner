package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/** Annual qx observations for the lookup categories at one attained age. */
public record SocialSecurityMortalityTableEntry(
        int attainedAge,
        BigDecimal maleQx,
        BigDecimal femaleQx) {

    public SocialSecurityMortalityTableEntry {
        validateQx(maleQx, attainedAge, SocialSecurityMortalityCategory.MALE);
        validateQx(femaleQx, attainedAge, SocialSecurityMortalityCategory.FEMALE);
    }

    public BigDecimal probabilityOfDeathWithinYear(
            SocialSecurityMortalityCategory category) {
        Objects.requireNonNull(category, "Mortality category is required.");
        return switch (category) {
            case MALE -> maleQx;
            case FEMALE -> femaleQx;
        };
    }

    private static void validateQx(
            BigDecimal qx,
            int attainedAge,
            SocialSecurityMortalityCategory category) {
        Objects.requireNonNull(
                qx,
                "Mortality qx is required for age "
                        + attainedAge
                        + " and category "
                        + category
                        + ".");
        if (qx.signum() < 0 || qx.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Mortality qx must be between 0 and 1 for age "
                            + attainedAge
                            + " and category "
                            + category
                            + ".");
        }
    }
}
