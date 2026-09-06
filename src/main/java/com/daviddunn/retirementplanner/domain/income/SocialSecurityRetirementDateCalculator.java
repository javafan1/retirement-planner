package com.daviddunn.retirementplanner.domain.income;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Date-level retirement-age conventions used by the monthly strategy
 * analysis path. A January 1 birth is treated as December 31 of the
 * preceding year for Social Security age and FRA purposes.
 */
public final class SocialSecurityRetirementDateCalculator {

    private SocialSecurityRetirementDateCalculator() {
    }

    public static LocalDate calculateFullRetirementDate(
            LocalDate birthDate) {

        LocalDate effectiveBirthDate = effectiveBirthDate(birthDate);
        FullRetirementAge fullRetirementAge =
                FullRetirementAgeCalculator.determine(
                        effectiveBirthDate);

        return effectiveBirthDate
                .plusYears(fullRetirementAge.years())
                .plusMonths(fullRetirementAge.months());
    }

    public static LocalDate calculateEarliestRetirementClaimDate(
            LocalDate birthDate) {

        return calculateRetirementClaimDate(birthDate, 62);
    }

    public static LocalDate calculateLatestRetirementClaimDate(
            LocalDate birthDate) {

        return calculateRetirementClaimDate(birthDate, 70);
    }

    /**
     * Converts a whole-year retirement claiming age into the exact claim
     * date used by the monthly analyzer, including the January 1 convention.
     */
    public static LocalDate calculateRetirementClaimDate(
            LocalDate birthDate,
            int claimingAge) {

        if (claimingAge < 62 || claimingAge > 70) {
            throw new IllegalArgumentException(
                    "Retirement claiming age must be between 62 and 70.");
        }
        return effectiveBirthDate(birthDate)
                .plusYears(claimingAge);
    }

    public static LocalDate effectiveBirthDate(
            LocalDate birthDate) {

        Objects.requireNonNull(
                birthDate,
                "Birth date is required.");

        return birthDate.getMonthValue() == 1
                && birthDate.getDayOfMonth() == 1
                ? birthDate.minusDays(1)
                : birthDate;
    }
}
