package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts conditional annual qx values into unconditional forward death-age
 * probabilities. Annual interval x to x+1 maps to deterministic death age x+1.
 */
public final class SocialSecurityMortalityDistributionProvider {

    public static final MathContext PROBABILITY_MATH_CONTEXT = MathContext.DECIMAL128;

    private final SocialSecurityMortalityTable table;

    public SocialSecurityMortalityDistributionProvider(
            SocialSecurityMortalityTable table) {
        this.table = Objects.requireNonNull(table, "Mortality table is required.");
    }

    public SocialSecurityMortalityTableMetadata metadata() {
        return table.metadata();
    }

    public SocialSecurityMortalityDistributionResult createDistribution(
            SocialSecurityMortalityDistributionRequest request) {
        Objects.requireNonNull(request, "Mortality distribution request is required.");

        int firstAttainedAge = firstModeledAttainedAge(request);
        SocialSecurityMortalityTableMetadata metadata = table.metadata();
        if (firstAttainedAge < metadata.minimumAge()) {
            throw new IllegalArgumentException(
                    "Mortality table "
                            + metadata.tableId()
                            + " does not contain required starting age "
                            + firstAttainedAge
                            + ".");
        }
        if (firstAttainedAge >= metadata.maximumAge()) {
            throw new IllegalArgumentException(
                    "Person has already reached or exceeded mortality table terminal age "
                            + metadata.maximumAge()
                            + " at mortality base date "
                            + request.mortalityBaseDate()
                            + ".");
        }

        List<SocialSecurityMortalityProbability> probabilities = new ArrayList<>();
        BigDecimal survival = BigDecimal.ONE;
        BigDecimal nonTerminalTotal = BigDecimal.ZERO;

        for (int attainedAge = firstAttainedAge;
                attainedAge < metadata.maximumAge();
                attainedAge++) {
            BigDecimal qx = request.mortalityAdjustment().adjust(
                    table.probabilityOfDeathWithinYear(
                            request.mortalityCategory(),
                            attainedAge));
            int deathAge = attainedAge + 1;

            if (deathAge == metadata.maximumAge()) {
                // Includes both final-interval mortality and all residual survival.
                BigDecimal terminalProbability = BigDecimal.ONE.subtract(
                        nonTerminalTotal);
                probabilities.add(new SocialSecurityMortalityProbability(
                        deathAge,
                        terminalProbability));
                break;
            }

            BigDecimal deathProbability = survival.multiply(
                    qx,
                    PROBABILITY_MATH_CONTEXT);
            probabilities.add(new SocialSecurityMortalityProbability(
                    deathAge,
                    deathProbability));
            nonTerminalTotal = nonTerminalTotal.add(deathProbability);
            survival = survival.subtract(deathProbability);
        }

        SocialSecurityMortalityDistribution distribution =
                new SocialSecurityMortalityDistribution(probabilities);
        return new SocialSecurityMortalityDistributionResult(
                distribution,
                metadata,
                request,
                firstAttainedAge,
                metadata.maximumAge(),
                SocialSecurityMortalityPartialYearConvention
                        .NEXT_COMPLETE_BIRTHDAY_INTERVAL);
    }

    private int firstModeledAttainedAge(
            SocialSecurityMortalityDistributionRequest request) {
        int attainedAge = request.mortalityBaseDate().getYear()
                - request.dateOfBirth().getYear();
        if (request.mortalityBaseDate().isBefore(
                request.dateOfBirth().plusYears(attainedAge))) {
            attainedAge--;
        }
        boolean exactBirthday = request.mortalityBaseDate().equals(
                request.dateOfBirth().plusYears(attainedAge));
        return exactBirthday ? attainedAge : attainedAge + 1;
    }
}
