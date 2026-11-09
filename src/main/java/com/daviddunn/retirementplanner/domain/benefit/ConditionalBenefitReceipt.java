package com.daviddunn.retirementplanner.domain.benefit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Actual completed-year payment facts, not the face value of an untriggered contract. */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = ConditionalBenefitReceiptReader.class)
public record ConditionalBenefitReceipt(UUID benefitId, String label, Category category, Trigger trigger,
        PersonReference recipient, PaymentPattern pattern, LocalDate eventDate, LocalDate firstEligibleDate,
        LocalDate paymentStart, LocalDate paymentEnd, int months, int year, BigDecimal gross,
        BigDecimal taxable, BigDecimal excluded, TaxCharacter taxCharacter, ReceiptDestination destination,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        AnnuityBasisRecovery annuityBasis) {
    public ConditionalBenefitReceipt(UUID benefitId, String label, Category category, Trigger trigger,
            PersonReference recipient, PaymentPattern pattern, LocalDate eventDate, LocalDate firstEligibleDate,
            LocalDate paymentStart, LocalDate paymentEnd, int months, int year, BigDecimal gross,
            BigDecimal taxable, BigDecimal excluded, TaxCharacter taxCharacter, ReceiptDestination destination) {
        this(benefitId, label, category, trigger, recipient, pattern, eventDate, firstEligibleDate, paymentStart,
                paymentEnd, months, year, gross, taxable, excluded, taxCharacter, destination, null);
    }
    public ConditionalBenefitReceipt {
        Objects.requireNonNull(benefitId); Objects.requireNonNull(label); Objects.requireNonNull(category); Objects.requireNonNull(trigger);
        Objects.requireNonNull(recipient); Objects.requireNonNull(pattern); Objects.requireNonNull(eventDate); Objects.requireNonNull(firstEligibleDate);
        Objects.requireNonNull(paymentStart); Objects.requireNonNull(paymentEnd); Objects.requireNonNull(gross); Objects.requireNonNull(taxable);
        Objects.requireNonNull(excluded); Objects.requireNonNull(taxCharacter); Objects.requireNonNull(destination);
        boolean annuity = taxCharacter == TaxCharacter.NONQUALIFIED_GENERAL_RULE_FIXED_EXCLUSION;
        if (annuity) {
            Objects.requireNonNull(annuityBasis, "Annuity receipt requires finite basis evidence.");
            if (category != Category.NONQUALIFIED_INCOME_ANNUITY || trigger.type() != TriggerType.FIXED_DATE
                    || pattern != PaymentPattern.MONTHLY_RECURRING || months != annuityBasis.payments()
                    || gross.signum() < 0 || gross.scale() != 2 || taxable.signum() < 0 || excluded.signum() < 0
                    || gross.compareTo(taxable.add(excluded)) != 0 || excluded.compareTo(annuityBasis.recovery()) != 0
                    || year < 1000 || year > 9999 || paymentStart.getYear() != year || paymentEnd.getYear() != year
                    || paymentEnd.isBefore(paymentStart) || !eventDate.equals(trigger.date()) || !firstEligibleDate.equals(eventDate))
                throw new IllegalArgumentException("Invalid nonqualified annuity receipt.");
        } else {
        if (annuityBasis != null || category == Category.NONQUALIFIED_INCOME_ANNUITY)
            throw new IllegalArgumentException("Annuity basis cannot be attached to another receipt.");
        boolean lifePrincipal = taxCharacter == TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED;
        if (gross.signum() < 0 || gross.scale() != 2 || gross.precision() > 4096
                || taxable.signum() < 0 || excluded.signum() < 0
                || !lifePrincipal && (!gross.equals(taxable) || excluded.signum() != 0)
                || lifePrincipal && (taxable.signum() != 0 || !gross.equals(excluded)
                    || category != Category.DEATH_BENEFIT || pattern != PaymentPattern.ONE_TIME_LUMP_SUM
                    || trigger.type() != TriggerType.DEATH || trigger.person() == recipient || eventDate.getDayOfYear() != 1)
                || year < 1000 || year > 9999 || paymentStart.getYear() != year || paymentEnd.getYear() != year
                || paymentEnd.isBefore(paymentStart) || firstEligibleDate.isBefore(eventDate)
                || paymentStart.isBefore(firstEligibleDate)
                || pattern == PaymentPattern.MONTHLY_RECURRING && (months < 1 || months > 12
                    || months != java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(paymentStart),java.time.YearMonth.from(paymentEnd)) + 1)
                || pattern == PaymentPattern.ONE_TIME_LUMP_SUM && (months != 0 || !paymentStart.equals(eventDate) || !paymentEnd.equals(eventDate)))
            throw new IllegalArgumentException("Invalid conditional payment facts.");
        }
    }
    public void validateDefinition(ConditionalBenefitDefinition definition) {
        if (!benefitId.equals(definition.id()) || !label.equals(definition.label()) || category != definition.category()
                || !trigger.equals(definition.trigger()) || recipient != definition.recipient() || pattern != definition.paymentPattern()
                || taxCharacter != definition.taxCharacter() || destination != definition.destination())
            throw new IllegalArgumentException("Conditional receipt does not match its original definition.");
        if (definition.annuityTerms() != null && (annuityBasis == null || !annuityBasis.terms().equals(definition.annuityTerms())
                || gross.compareTo(definition.grossAmount().multiply(BigDecimal.valueOf(months))) != 0))
            throw new IllegalArgumentException("Annuity receipt differs from its original quotation or investment facts.");
        if (definition.excludesLifePrincipal()
                && (gross.compareTo(definition.grossAmount().setScale(2,java.math.RoundingMode.HALF_UP)) != 0
                    || definition.coverageStart() != null && eventDate.isBefore(definition.coverageStart())
                    || definition.coverageEnd() != null && eventDate.isAfter(definition.coverageEnd())))
            throw new IllegalArgumentException("Excluded principal amount or event coverage differs from its original definition.");
    }
}
