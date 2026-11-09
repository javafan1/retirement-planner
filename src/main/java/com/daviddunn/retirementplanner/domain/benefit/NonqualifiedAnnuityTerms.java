package com.daviddunn.retirementplanner.domain.benefit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Documented tax facts, not an insurer quotation/pricing or a liquid asset. */
public record NonqualifiedAnnuityTerms(
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) LocalDate acquisitionDate,
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) LocalDate taxAnnuityStartingDate,
        BigDecimal excludedPerPayment,
        BigDecimal originalInvestment,
        BigDecimal openingInvestment,
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) LocalDate basisAsOf,
        Qualification qualification) {

    public enum Qualification { DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1,
        PERSONAL_GENERAL_RULE_FIXED_SINGLE_LIFE_V2 }

    public NonqualifiedAnnuityTerms {
        Objects.requireNonNull(acquisitionDate);
        Objects.requireNonNull(taxAnnuityStartingDate);
        Objects.requireNonNull(basisAsOf);
        Objects.requireNonNull(qualification);
        for (var date : new LocalDate[]{acquisitionDate, taxAnnuityStartingDate, basisAsOf})
            if (date.getYear() < 1000 || date.getYear() > 9999)
                throw new IllegalArgumentException("Annuity date is outside supported bounds.");
        money(excludedPerPayment); money(originalInvestment); money(openingInvestment);
        if (!taxAnnuityStartingDate.isAfter(LocalDate.of(1986, 12, 31))
                || !acquisitionDate.isBefore(basisAsOf) || acquisitionDate.isAfter(taxAnnuityStartingDate)
                || openingInvestment.compareTo(originalInvestment) > 0 || basisAsOf.getDayOfMonth() != 1)
            throw new IllegalArgumentException("Verify the post-1986 tax date, completed acquisition and opening remaining investment.");
    }

    public static void money(BigDecimal value) {
        ConditionalBenefitDefinition.validateNumber(value, ConditionalBenefitDefinition.MAX_AMOUNT, "Annuity amount");
        if (value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("The supported annuity quotation and investment amounts must be exact cents.");
    }
}
