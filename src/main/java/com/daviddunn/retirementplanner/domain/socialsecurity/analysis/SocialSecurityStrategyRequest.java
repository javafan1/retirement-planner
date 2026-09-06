package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable, finite household strategy scenario.
 *
 * Calculation includes the calendar months containing analysisDate and the
 * resolved end date. When both death dates exist, their later date supplies
 * the default end. If either death date is absent, analysisEndDate is required.
 */
public record SocialSecurityStrategyRequest(
        LocalDate analysisDate,
        LocalDate analysisEndDate,
        SocialSecurityClaimingElection primaryElection,
        SocialSecurityClaimingElection spouseElection,
        LocalDate primarySurvivorClaimDate,
        LocalDate spouseSurvivorClaimDate,
        LocalDate primaryDeathDate,
        LocalDate spouseDeathDate,
        BigDecimal socialSecurityColaRate) {

    private static final LocalDate MODERN_DEEMED_FILING_CUTOFF =
            LocalDate.of(1954, 1, 2);

    public SocialSecurityStrategyRequest {

        Objects.requireNonNull(analysisDate, "Analysis date is required.");
        Objects.requireNonNull(
                primaryElection,
                "Primary election is required.");
        Objects.requireNonNull(
                spouseElection,
                "Spouse election is required.");
        Objects.requireNonNull(
                socialSecurityColaRate,
                "Social Security COLA rate is required.");

        if (primaryElection.owner() != AccountOwnership.PRIMARY) {
            throw new IllegalArgumentException(
                    "Primary election must have PRIMARY ownership.");
        }

        if (spouseElection.owner() != AccountOwnership.SPOUSE) {
            throw new IllegalArgumentException(
                    "Spouse election must have SPOUSE ownership.");
        }

        validateModernDeemedFilingCohort(
                primaryElection.birthDate(),
                "Primary");
        validateModernDeemedFilingCohort(
                spouseElection.birthDate(),
                "Spouse");

        if (socialSecurityColaRate.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException(
                    "Social Security COLA rate must be greater than -100%.");
        }

        validateDeathDate(
                primaryDeathDate,
                primaryElection.birthDate(),
                "Primary");
        validateDeathDate(
                spouseDeathDate,
                spouseElection.birthDate(),
                "Spouse");

        validateSurvivorClaimDate(
                primarySurvivorClaimDate,
                primaryElection.birthDate(),
                primaryDeathDate,
                spouseDeathDate,
                "Primary");
        validateSurvivorClaimDate(
                spouseSurvivorClaimDate,
                spouseElection.birthDate(),
                spouseDeathDate,
                primaryDeathDate,
                "Spouse");

        if ((primaryDeathDate == null || spouseDeathDate == null)
                && analysisEndDate == null) {
            throw new IllegalArgumentException(
                    "Analysis end date is required unless both death dates are provided.");
        }

        LocalDate resolvedEndDate = resolveEndDate(
                analysisEndDate,
                primaryDeathDate,
                spouseDeathDate);

        if (resolvedEndDate.isBefore(analysisDate)) {
            throw new IllegalArgumentException(
                    "Analysis end date cannot be before analysis date.");
        }

        if (primaryDeathDate != null
                && spouseDeathDate != null
                && analysisEndDate != null
                && analysisEndDate.isBefore(
                        later(primaryDeathDate, spouseDeathDate))) {
            throw new IllegalArgumentException(
                    "Analysis end date cannot precede the later death date.");
        }
    }

    /**
     * Backward-compatible request shape for own/spousal-only analyses.
     */
    public SocialSecurityStrategyRequest(
            LocalDate analysisDate,
            LocalDate analysisEndDate,
            SocialSecurityClaimingElection primaryElection,
            SocialSecurityClaimingElection spouseElection,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate,
            BigDecimal socialSecurityColaRate) {

        this(
                analysisDate,
                analysisEndDate,
                primaryElection,
                spouseElection,
                null,
                null,
                primaryDeathDate,
                spouseDeathDate,
                socialSecurityColaRate);
    }

    public LocalDate resolvedAnalysisEndDate() {

        return resolveEndDate(
                analysisEndDate,
                primaryDeathDate,
                spouseDeathDate);
    }

    private static void validateDeathDate(
            LocalDate deathDate,
            LocalDate birthDate,
            String ownerDescription) {

        if (deathDate != null && deathDate.isBefore(birthDate)) {
            throw new IllegalArgumentException(
                    ownerDescription + " death date cannot be before birth date.");
        }
    }

    private static void validateSurvivorClaimDate(
            LocalDate survivorClaimDate,
            LocalDate survivorBirthDate,
            LocalDate survivorDeathDate,
            LocalDate otherDeathDate,
            String ownerDescription) {

        if (survivorClaimDate == null) {
            return;
        }

        LocalDate earliestClaimDate =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateEarliestSurvivorClaimDate(
                                survivorBirthDate);

        if (survivorClaimDate.isBefore(earliestClaimDate)) {
            throw new IllegalArgumentException(
                    ownerDescription
                            + " survivor claim date cannot be before age 60.");
        }
        if (otherDeathDate == null) {
            throw new IllegalArgumentException(
                    ownerDescription
                            + " survivor strategy requires the other spouse's death date.");
        }
        if (survivorDeathDate != null
                && !survivorClaimDate.isBefore(survivorDeathDate)) {
            throw new IllegalArgumentException(
                    ownerDescription
                            + " survivor claim date must be before their death date.");
        }
    }

    private static void validateModernDeemedFilingCohort(
            LocalDate birthDate,
            String ownerDescription) {

        if (birthDate.isBefore(MODERN_DEEMED_FILING_CUTOFF)) {
            throw new IllegalArgumentException(
                    ownerDescription
                            + " birth date is outside the supported modern "
                            + "deemed-filing cohort (January 2, 1954 or later).");
        }
    }

    private static LocalDate resolveEndDate(
            LocalDate analysisEndDate,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate) {

        if (analysisEndDate != null) {
            return analysisEndDate;
        }

        return later(primaryDeathDate, spouseDeathDate);
    }

    private static LocalDate later(
            LocalDate first,
            LocalDate second) {

        return first.isAfter(second) ? first : second;
    }
}
