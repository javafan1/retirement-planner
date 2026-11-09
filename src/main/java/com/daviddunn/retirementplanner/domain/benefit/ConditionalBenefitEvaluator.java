package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Pure annual selection. Repeated evaluation does not deposit cash or remember paid events. */
public final class ConditionalBenefitEvaluator {
    public AnnualBenefitEvaluation evaluate(List<ConditionalBenefitDefinition> definitions, BenefitEvaluationContext context) {
        Objects.requireNonNull(context);
        definitions = List.copyOf(definitions);
        if (definitions.size() > ConditionalBenefits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many benefits.");
        if (definitions.isEmpty()) return AnnualBenefitEvaluation.EMPTY;
        var identities = new HashSet<UUID>();
        var receipts = new ArrayList<ConditionalBenefitReceipt>();
        for (var definition : definitions) {
            if (!identities.add(definition.id())) throw new IllegalArgumentException("Duplicate benefit ID.");
            if (definition.recipient() == PersonReference.SPOUSE) context.birth(PersonReference.SPOUSE);
            if (definition.trigger().person() == PersonReference.SPOUSE) context.birth(PersonReference.SPOUSE);
            if (definition.annuityTerms() != null) {
                receipts.add(annuity(definition, context));
                continue;
            }
            var recipient = owner(definition.recipient());
            if (!context.deaths().isAlive(recipient, context.year())) continue;
            LocalDate event = switch (definition.trigger().type()) {
                case FIXED_DATE -> definition.trigger().date();
                case AGE -> context.birth(definition.trigger().person()).plusYears(definition.trigger().age());
                case DEATH -> context.deaths().deathDate(owner(definition.trigger().person())).orElse(null);
            };
            if (event == null || definition.coverageStart() != null && event.isBefore(definition.coverageStart())
                    || definition.coverageEnd() != null && event.isAfter(definition.coverageEnd())) continue;
            LocalDate eligible = definition.notBefore() == null ? event : later(event, definition.notBefore());
            LocalDate first;
            LocalDate last;
            int months;
            BigDecimal amount;
            if (definition.paymentPattern() == PaymentPattern.ONE_TIME_LUMP_SUM) {
                if (event.isBefore(context.opening()) || event.getYear() != context.year()) continue;
                first = event; last = event; months = 0; amount = definition.grossAmount();
            } else {
                first = later(later(eligible, context.opening()), LocalDate.of(context.year(),1,1));
                last = LocalDate.of(context.year(),12,31);
                if (definition.endDate() != null && definition.endDate().isBefore(last)) last = definition.endDate();
                if (last.isBefore(first)) continue;
                months = Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(last)) + 1);
                int exponent = context.year() - eligible.getYear();
                amount = definition.grossAmount().multiply(BigDecimal.ONE.add(definition.annualEscalationRate())
                        .pow(exponent, MathContext.DECIMAL128), MathContext.DECIMAL128).multiply(BigDecimal.valueOf(months), MathContext.DECIMAL128);
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);
            receipts.add(new ConditionalBenefitReceipt(definition.id(), definition.label(), definition.category(), definition.trigger(),
                    definition.recipient(), definition.paymentPattern(), event, eligible, first, last, months, context.year(), amount,
                    definition.excludesLifePrincipal() ? BigDecimal.ZERO : amount,
                    definition.excludesLifePrincipal() ? amount : BigDecimal.ZERO, definition.taxCharacter(), definition.destination()));
        }
        return new AnnualBenefitEvaluation(receipts);
    }
    public static AccountOwnership owner(PersonReference person) {
        return person == PersonReference.PRIMARY ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
    }
    private ConditionalBenefitReceipt annuity(ConditionalBenefitDefinition definition, BenefitEvaluationContext context) {
        var terms = definition.annuityTerms();
        if (!terms.basisAsOf().equals(context.opening()) || context.opening().getDayOfMonth() != 1)
            throw new IllegalArgumentException("Verify annuity remaining investment as of the month-aligned projection opening.");
        var first = later(definition.trigger().date(), context.opening());
        var last = context.deaths().deathDate(owner(definition.recipient())).map(d -> d.minusDays(1))
                .orElse(LocalDate.of(9999,12,31));
        int before = count(first, earlier(LocalDate.of(context.year(),1,1).minusDays(1), last));
        var start = later(first, LocalDate.of(context.year(),1,1));
        var end = earlier(LocalDate.of(context.year(),12,31), last);
        int months = count(start, end);
        var opening = terms.openingInvestment().subtract(terms.excludedPerPayment().multiply(BigDecimal.valueOf(before)))
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        var recovery = opening.min(terms.excludedPerPayment().multiply(BigDecimal.valueOf(months))).setScale(2, RoundingMode.HALF_UP);
        var closing = opening.subtract(recovery);
        var gross = definition.grossAmount().multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
        return new ConditionalBenefitReceipt(definition.id(), definition.label(), definition.category(), definition.trigger(),
                definition.recipient(), definition.paymentPattern(), definition.trigger().date(), definition.trigger().date(),
                months == 0 ? later(context.opening(),LocalDate.of(context.year(),1,1)) : start,
                months == 0 ? LocalDate.of(context.year(),12,31) : end, months, context.year(), gross, gross.subtract(recovery), recovery,
                definition.taxCharacter(), definition.destination(), new AnnuityBasisRecovery(terms, before, months, opening, recovery, closing));
    }
    private static int count(LocalDate first, LocalDate last) {
        return last.isBefore(first) ? 0 : Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(last)) + 1);
    }
    private static LocalDate earlier(LocalDate first, LocalDate second) { return first.isBefore(second) ? first : second; }
    private static LocalDate later(LocalDate first, LocalDate second) { return first.isAfter(second) ? first : second; }
}
