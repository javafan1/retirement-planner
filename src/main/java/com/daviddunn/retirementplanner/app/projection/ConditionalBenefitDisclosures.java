package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.util.*;

/** Pure original-input descriptions. Configured definitions are not evidence of payment or execution. */
public final class ConditionalBenefitDisclosures {
    public static final String LIFE_PRINCIPAL_SCOPE = "Qualifying life insurance death proceeds are excluded from federal income tax. "
            + "The proceeds increase available household cash but do not directly increase federal AGI or Medicare IRMAA. "
            + "Future investment earnings may be taxable. Special policy ownership and transfer rules may change the actual tax treatment.";
    public static final String LIFE_QUALIFICATION = "I confirm this is ordinary qualifying personal life insurance principal payable because of the insured's death, "
            + "to the different living modeled beneficiary. No settlement interest is included. There is no known transfer-for-value limitation, "
            + "reportable policy sale, employer-owned contract, or unresolved special ownership, assignment or business-purpose exception. "
            + "This is my planning assertion, not a legal determination by Aledrix.";
    private ConditionalBenefitDisclosures() { }

    public static List<String> lines(RetirementPlan plan, String side) {
        var lines = new ArrayList<String>();
        lines.add(side + ": " + (plan.getConditionalBenefits().isEmpty()
                ? "no conditional benefits configured." : "conditional benefits configured."));
        for (var b : plan.getConditionalBenefits()) {
            var t = b.trigger();
            String trigger = switch (t.type()) {
                case FIXED_DATE -> "date " + t.date();
                case AGE -> t.person() + " age " + t.age();
                case DEATH -> t.person() + " death (January 1 of the scenario death year)";
            };
            var text = new StringBuilder(side).append(" — ").append(b.label()).append(": ")
                    .append(b.category()).append("; ").append(b.grossAmount().stripTrailingZeros().toPlainString())
                    .append(b.paymentPattern() == ConditionalBenefitDefinition.PaymentPattern.MONTHLY_RECURRING
                            ? " monthly" : " one-time lump sum")
                    .append("; trigger ").append(trigger).append("; living recipient ").append(b.recipient());
            if (b.notBefore() != null) text.append("; not before ").append(b.notBefore());
            if (b.endDate() != null) text.append("; payments end ").append(b.endDate());
            if (b.coverageStart() != null) text.append("; coverage starts ").append(b.coverageStart());
            if (b.coverageEnd() != null) text.append("; coverage ends ").append(b.coverageEnd());
            if (b.paymentPattern() == ConditionalBenefitDefinition.PaymentPattern.MONTHLY_RECURRING)
                text.append("; annual escalation ").append(b.annualEscalationRate().movePointRight(2)
                        .stripTrailingZeros().toPlainString()).append("% from first eligible payment year");
            text.append(b.excludesLifePrincipal()
                    ? "; qualifying personal life insurance death principal; federally excluded; household retained nonqualified cash; qualification PERSONAL_LIFE_PRINCIPAL_ONLY_V1."
                    : "; fully taxable ordinary non-pension income; household retained nonqualified cash.");
            lines.add(text.toString());
            if (b.excludesLifePrincipal()) {
                lines.add(side + " — " + LIFE_PRINCIPAL_SCOPE);
                lines.add(side + " — Original qualification assertion: " + LIFE_QUALIFICATION);
            }
        }
        if (!plan.getConditionalBenefits().isEmpty())
            lines.add("Configured benefits are conditional, not current guaranteed income. Payments require a living modeled recipient. "
                    + (com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefits.hasExcludedPrincipal(plan.getConditionalBenefits())
                    ? "No external beneficiary, estate settlement or post-last-death payment is modeled. Settlement interest is outside the principal-only feature."
                    : "No external beneficiary, estate settlement, tax-excluded insurance proceeds or post-last-death payment is modeled."));
        return List.copyOf(lines);
    }
}
