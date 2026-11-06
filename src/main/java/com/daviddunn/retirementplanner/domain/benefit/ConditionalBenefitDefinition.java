package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stored payment definition only. No annual benefit evaluation or financial execution. */
@JsonDeserialize(using = ConditionalBenefitDefinitionDeserializer.class)
public record ConditionalBenefitDefinition(
        UUID id, String label, Category category, Trigger trigger, PersonReference recipient,
        PaymentPattern paymentPattern, BigDecimal grossAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate notBefore,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate endDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate coverageStart,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate coverageEnd,
        BigDecimal annualEscalationRate, EscalationOrigin escalationOrigin,
        TaxCharacter taxCharacter, ReceiptDestination destination) {

    public static final int MAX_LABEL_LENGTH = 128;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    public enum PersonReference { PRIMARY, SPOUSE }
    public enum Category { CONDITIONAL_INCOME, DEATH_BENEFIT }
    public enum PaymentPattern { MONTHLY_RECURRING, ONE_TIME_LUMP_SUM }
    public enum TriggerType { FIXED_DATE, AGE, DEATH }
    public enum EscalationOrigin { FIRST_ELIGIBLE_PAYMENT_YEAR }
    public enum TaxCharacter { FULLY_TAXABLE_ORDINARY_NON_PENSION }
    public enum ReceiptDestination { HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH }

    public record Trigger(TriggerType type,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date,
            PersonReference person, Integer age) {
        public Trigger {
            Objects.requireNonNull(type, "Trigger type is required.");
            validateDate(date);
            switch (type) {
                case FIXED_DATE -> {
                    if (date == null || person != null || age != null)
                        throw new IllegalArgumentException("Fixed trigger requires only a date.");
                }
                case AGE -> {
                    if (date != null || person == null || age == null || age < 0 || age > 120)
                        throw new IllegalArgumentException("Age trigger requires a named person and age 0 through 120.");
                }
                case DEATH -> {
                    if (date != null || person == null || age != null)
                        throw new IllegalArgumentException("Death trigger requires only a named person.");
                }
            }
        }

        /** Birthday arithmetic only; February 29 maps to February 28 in a non-leap target year. */
        public Optional<LocalDate> fixedOrAgeDate(Household household) {
            validatePerson(household, person);
            return switch (type) {
                case FIXED_DATE -> Optional.of(date);
                case AGE -> {
                    var owner = person == PersonReference.PRIMARY ? household.getPrimaryPerson() : household.getSpouse();
                    var resolved = Objects.requireNonNull(owner.getBirthDate(), "Age trigger requires a birth date.").plusYears(age);
                    validateDate(resolved);
                    yield Optional.of(resolved);
                }
                case DEATH -> Optional.empty();
            };
        }

        /** Existing annual mortality convention, not a new mortality calculation. */
        public static LocalDate deathEventDate(Year year) {
            var date = Objects.requireNonNull(year).atDay(1);
            validateDate(date);
            return date;
        }
    }

    public ConditionalBenefitDefinition {
        Objects.requireNonNull(id, "Benefit ID is required.");
        if (id.equals(new UUID(0, 0))) throw new IllegalArgumentException("Benefit ID cannot be empty.");
        Objects.requireNonNull(label, "Benefit label is required.");
        if (label.isBlank() || label.length() > MAX_LABEL_LENGTH || label.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Benefit label must be nonblank, bounded and free of control characters.");
        Objects.requireNonNull(category, "Benefit category is required.");
        Objects.requireNonNull(trigger, "Benefit trigger is required.");
        Objects.requireNonNull(recipient, "Living recipient is required.");
        Objects.requireNonNull(paymentPattern, "Payment pattern is required.");
        validateNumber(grossAmount, MAX_AMOUNT, "Gross amount");
        validateNumber(annualEscalationRate, BigDecimal.ONE, "Escalation rate");
        Objects.requireNonNull(escalationOrigin, "Escalation origin is required.");
        Objects.requireNonNull(taxCharacter, "Tax character is required.");
        Objects.requireNonNull(destination, "Receipt destination is required.");
        validateDate(notBefore); validateDate(endDate); validateDate(coverageStart); validateDate(coverageEnd);
        if (category == Category.DEATH_BENEFIT && trigger.type() != TriggerType.DEATH)
            throw new IllegalArgumentException("Death benefit requires a death trigger.");
        if (trigger.type() == TriggerType.DEATH && trigger.person() == recipient)
            throw new IllegalArgumentException("A death-triggered benefit requires a different living recipient.");
        if (coverageStart != null && coverageEnd != null && coverageEnd.isBefore(coverageStart))
            throw new IllegalArgumentException("Coverage end precedes coverage start.");
        if (notBefore != null && endDate != null && endDate.isBefore(notBefore))
            throw new IllegalArgumentException("Benefit end precedes not-before date.");
        if (paymentPattern == PaymentPattern.ONE_TIME_LUMP_SUM
                && (notBefore != null || endDate != null || annualEscalationRate.signum() != 0))
            throw new IllegalArgumentException("Lump sums do not have monthly timing or escalation fields.");
        if (trigger.type() == TriggerType.DEATH) {
            // Validate that the bounds can contain an event; do not select a scenario death.
            var firstJanuary = LocalDate.of(coverageStart == null ? 1000 : coverageStart.getYear(), 1, 1);
            if (coverageStart != null && firstJanuary.isBefore(coverageStart)) firstJanuary = firstJanuary.plusYears(1);
            var latestEvent = coverageEnd == null ? LocalDate.of(9999, 12, 31) : coverageEnd;
            if (endDate != null && endDate.isBefore(latestEvent)) latestEvent = endDate;
            if (firstJanuary.isAfter(latestEvent))
                throw new IllegalArgumentException("Death coverage and payment bounds must include a January 1 event.");
        }
        if (trigger.type() == TriggerType.FIXED_DATE) validateTiming(trigger.date(), notBefore, endDate, coverageStart, coverageEnd);
    }

    public void validateFor(Household household) {
        validatePerson(household, recipient);
        validatePerson(household, trigger.person());
        trigger.fixedOrAgeDate(household).ifPresent(date -> validateTiming(date, notBefore, endDate, coverageStart, coverageEnd));
    }

    public boolean referencesSpouse() {
        return recipient == PersonReference.SPOUSE || trigger.person() == PersonReference.SPOUSE;
    }

    private static void validatePerson(Household household, PersonReference person) {
        Objects.requireNonNull(household, "Household is required.");
        if (person == PersonReference.SPOUSE && !household.hasSpouse())
            throw new IllegalArgumentException("Conditional benefit references an absent spouse.");
    }

    private static void validateTiming(LocalDate event, LocalDate threshold, LocalDate end, LocalDate from, LocalDate through) {
        if (from != null && event.isBefore(from) || through != null && event.isAfter(through))
            throw new IllegalArgumentException("Trigger falls outside inclusive coverage.");
        LocalDate commencement = threshold != null && threshold.isAfter(event) ? threshold : event;
        if (end != null && end.isBefore(commencement)) throw new IllegalArgumentException("Benefit ends before eligibility.");
    }

    static void validateNumber(BigDecimal value, BigDecimal max, String field) {
        Objects.requireNonNull(value, field + " is required.");
        if (value.precision() > 24 || value.scale() < -12 || value.scale() > 8
                || value.signum() < 0 || value.compareTo(max) > 0)
            throw new IllegalArgumentException(field + " exceeds the supported numeric bounds.");
    }

    static void validateDate(LocalDate date) {
        if (date != null && (date.getYear() < 1000 || date.getYear() > 9999))
            throw new IllegalArgumentException("Benefit dates require years 1000 through 9999.");
    }
}
