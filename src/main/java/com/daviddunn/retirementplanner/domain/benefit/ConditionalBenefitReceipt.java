package com.daviddunn.retirementplanner.domain.benefit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Actual completed-year payment facts, not the face value of an untriggered contract. */
public record ConditionalBenefitReceipt(UUID benefitId, String label, Category category, Trigger trigger,
        PersonReference recipient, PaymentPattern pattern, LocalDate eventDate, LocalDate firstEligibleDate,
        LocalDate paymentStart, LocalDate paymentEnd, int months, int year, BigDecimal gross,
        BigDecimal taxable, BigDecimal excluded, TaxCharacter taxCharacter, ReceiptDestination destination) {
    public ConditionalBenefitReceipt {
        Objects.requireNonNull(benefitId); Objects.requireNonNull(label); Objects.requireNonNull(category); Objects.requireNonNull(trigger);
        Objects.requireNonNull(recipient); Objects.requireNonNull(pattern); Objects.requireNonNull(eventDate); Objects.requireNonNull(firstEligibleDate);
        Objects.requireNonNull(paymentStart); Objects.requireNonNull(paymentEnd); Objects.requireNonNull(gross); Objects.requireNonNull(taxable);
        Objects.requireNonNull(excluded); Objects.requireNonNull(taxCharacter); Objects.requireNonNull(destination);
        if (gross.signum() < 0 || gross.scale() != 2 || gross.precision() > 4096 || !gross.equals(taxable) || excluded.signum() != 0
                || year < 1000 || year > 9999 || paymentStart.getYear() != year || paymentEnd.getYear() != year
                || paymentEnd.isBefore(paymentStart) || firstEligibleDate.isBefore(eventDate)
                || paymentStart.isBefore(firstEligibleDate)
                || pattern == PaymentPattern.MONTHLY_RECURRING && (months < 1 || months > 12
                    || months != java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(paymentStart),java.time.YearMonth.from(paymentEnd)) + 1)
                || pattern == PaymentPattern.ONE_TIME_LUMP_SUM && (months != 0 || !paymentStart.equals(eventDate) || !paymentEnd.equals(eventDate)))
            throw new IllegalArgumentException("Invalid conditional payment facts.");
    }
    public void validateDefinition(ConditionalBenefitDefinition definition) {
        if (!benefitId.equals(definition.id()) || !label.equals(definition.label()) || category != definition.category()
                || !trigger.equals(definition.trigger()) || recipient != definition.recipient() || pattern != definition.paymentPattern()
                || taxCharacter != definition.taxCharacter() || destination != definition.destination())
            throw new IllegalArgumentException("Conditional receipt does not match its original definition.");
    }
}
