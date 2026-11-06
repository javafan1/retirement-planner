package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.util.*;

/** Pure original-input descriptions. Configured definitions are not evidence of payment or execution. */
public final class ConditionalBenefitDisclosures {
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
            text.append("; fully taxable ordinary non-pension income; household retained nonqualified cash.");
            lines.add(text.toString());
        }
        if (!plan.getConditionalBenefits().isEmpty())
            lines.add("Configured benefits are conditional, not current guaranteed income. Payments require a living modeled recipient. "
                    + "No external beneficiary, estate settlement, tax-excluded insurance proceeds or post-last-death payment is modeled.");
        return List.copyOf(lines);
    }
}
