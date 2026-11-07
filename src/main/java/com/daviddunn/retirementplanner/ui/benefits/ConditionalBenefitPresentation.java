package com.daviddunn.retirementplanner.ui.benefits;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

/** Input presentation only; never evaluates a payment or reads a completed historical result. */
public final class ConditionalBenefitPresentation {
    public static final String TAX = "Fully Taxable Ordinary Non-Pension Income";
    public static final String DESTINATION = "Household Retained Nonqualified Cash";
    public static final String TAX_HELP = "This payment is currently modeled as fully taxable ordinary income, not as a pension or Social Security benefit.";
    public static final String DESTINATION_HELP = "Once received, the payment can help fund household expenses and taxes. Any remaining after-tax amount is retained in projected household nonqualified assets.";
    public static final String INSURANCE_HELP = "This payment remains fully taxable unless you explicitly select qualifying personal life insurance death principal and confirm its restricted qualification. Do not include settlement interest in excluded principal.";

    public static String tax(TaxCharacter character) {
        return character == TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION ? TAX
                : "Qualifying Life Insurance Death Proceeds — Federally Excluded";
    }

    private ConditionalBenefitPresentation() { }

    public static String person(PersonReference role, Household household) {
        if (role == null) return "";
        Person person = role == PersonReference.PRIMARY ? household.getPrimaryPerson()
                : household.hasSpouse() ? household.getSpouse() : null;
        String name = person == null ? "" : String.join(" ",
                person.getFirstName() == null ? "" : person.getFirstName(),
                person.getLastName() == null ? "" : person.getLastName()).strip();
        String label = role == PersonReference.PRIMARY ? "Primary" : "Spouse";
        return name.isBlank() ? label : label + " (" + name + ")";
    }

    public static String money(BigDecimal amount) {
        var format = new DecimalFormat("#,##0.00######", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + format.format(amount);
    }

    public static String payment(ConditionalBenefitDefinition benefit) {
        return money(benefit.grossAmount()) + (benefit.paymentPattern() == PaymentPattern.MONTHLY_RECURRING
                ? " per month" : " one-time receipt");
    }

    public static String category(Category value) {
        return value == Category.DEATH_BENEFIT ? "Death Benefit" : "Conditional Income";
    }

    public static String trigger(Trigger value, Household household) {
        return switch (value.type()) {
            case FIXED_DATE -> "On " + value.date();
            case AGE -> person(value.person(), household) + " reaches age " + value.age();
            case DEATH -> person(value.person(), household) + " dies";
        };
    }

    public static String timing(ConditionalBenefitDefinition benefit) {
        var parts = new ArrayList<String>();
        if (benefit.notBefore() != null) parts.add("Not before " + benefit.notBefore());
        if (benefit.endDate() != null) parts.add("Payments end " + benefit.endDate());
        if (benefit.coverageStart() != null) parts.add("Event coverage from " + benefit.coverageStart());
        if (benefit.coverageEnd() != null) parts.add("Event coverage through " + benefit.coverageEnd());
        return parts.isEmpty() ? "No additional date limits" : String.join("; ", parts);
    }

    public static List<String> details(ConditionalBenefitDefinition benefit, Household household) {
        var lines = new ArrayList<String>();
        lines.add(benefit.label());
        lines.add("Category: " + category(benefit.category()));
        lines.add("Payment: " + payment(benefit));
        lines.add("Trigger: " + trigger(benefit.trigger(), household));
        lines.add("Living recipient: " + person(benefit.recipient(), household));
        lines.add(timing(benefit));
        if (benefit.paymentPattern() == PaymentPattern.MONTHLY_RECURRING) {
            lines.add("Annual escalation: " + benefit.annualEscalationRate().movePointRight(2)
                    .stripTrailingZeros().toPlainString() + "% from the first eligible payment year.");
            lines.add("Partial eligible calendar months count as whole payment months. Payments stop when the recipient dies.");
        } else {
            lines.add("Paid once in its eligible trigger year; pre-projection events are not replayed.");
        }
        lines.add("Coverage qualifies the trigger event; it does not terminate an already-active recurring stream.");
        lines.add("Tax treatment: " + tax(benefit.taxCharacter()));
        if (benefit.excludesLifePrincipal()) {
            lines.add(com.daviddunn.retirementplanner.app.projection.ConditionalBenefitDisclosures.LIFE_PRINCIPAL_SCOPE);
            lines.add("Original qualification: " + com.daviddunn.retirementplanner.app.projection.ConditionalBenefitDisclosures.LIFE_QUALIFICATION);
        }
        lines.add("Destination: " + DESTINATION);
        lines.add(DESTINATION_HELP);
        if (benefit.trigger().type() == TriggerType.DEATH) {
            lines.add("Modeled death occurs January 1 of the selected death year; a living recipient is required.");
            if (benefit.paymentPattern() == PaymentPattern.ONE_TIME_LUMP_SUM && !benefit.excludesLifePrincipal()) lines.add(INSURANCE_HELP);
        }
        lines.add("Configured future benefits are not existing account balances or guaranteed current income.");
        return List.copyOf(lines);
    }
}
