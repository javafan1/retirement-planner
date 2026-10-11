package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Replaces unallocated metadata, never financial totals or tax conclusions. */
final class TaxIncomeAttribution {
    private TaxIncomeAttribution() {
    }

    static TaxIncome attach(TaxIncome income, List<IncomeSourceAttribution> supplied, int year) {
        requireWithinTotal(supplied, Kind.PENSION, income.getPensionIncome());
        requireWithinTotal(supplied, Kind.ROTH_CONVERSION, income.getRothConversion());
        requireWithinTotal(supplied, Kind.SOCIAL_SECURITY, income.getSocialSecurityIncome());
        List<IncomeSourceAttribution> sources = new ArrayList<>();
        for (IncomeSourceAttribution existing : income.getIncomeSources()) {
            List<IncomeSourceAttribution> replacements = supplied.stream()
                    .filter(source -> replaces(existing.kind(), source)).toList();
            if (replacements.isEmpty()) {
                sources.add(existing);
                continue;
            }
            // Only legacy aggregate records may be replaced. Pension authoritative
            // records arrive as one aggregate; calculated per-pension records remain.
            if (existing.owner().isPresent() || existing.account().isPresent()) {
                throw new IllegalArgumentException("Cannot replace an already attributed " + existing.kind() + " source.");
            }
            BigDecimal attributed = replacements.stream().map(IncomeSourceAttribution::grossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainder = existing.grossAmount().subtract(attributed);
            if (remainder.signum() < 0) {
                throw new IllegalArgumentException("Source amounts exceed " + existing.kind() + " total.");
            }
            if (remainder.signum() > 0) {
                sources.add(IncomeSourceAttribution.aggregate(existing.kind(), existing.purpose(),
                        "Unattributed " + existing.kind(), remainder,
                        existing.modeledTaxableAmount().isPresent()
                                ? Optional.of(remainder) : Optional.empty()).inYear(year));
            }
        }
        supplied.forEach(source -> sources.add(source.inYear(year)));
        return income.withIncomeSources(sources);
    }

    private static void requireWithinTotal(List<IncomeSourceAttribution> supplied, Kind kind, BigDecimal total) {
        BigDecimal amount = supplied.stream().filter(source -> source.kind() == kind)
                .map(IncomeSourceAttribution::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(total) > 0) {
            throw new IllegalArgumentException("Source amounts exceed " + kind + " total.");
        }
    }

    private static boolean replaces(Kind aggregate, IncomeSourceAttribution source) {
        if (aggregate == Kind.ORDINARY_RETIREMENT_WITHDRAWAL) {
            return (source.kind() == Kind.RMD || source.kind() == Kind.ORDINARY_RETIREMENT_WITHDRAWAL)
                    && source.account().isPresent()
                    && source.account().get().type().getTaxTreatment() == TaxTreatment.TAX_DEFERRED;
        }
        return source.kind() == aggregate;
    }

    static void aggregate(List<IncomeSourceAttribution> sources, Kind kind,
            BigDecimal amount, boolean knownTaxable, int year) {
        if (amount.signum() > 0) {
            sources.add(IncomeSourceAttribution.aggregate(kind,
                    kind == Kind.ROTH_CONVERSION ? Purpose.CONVERSION : Purpose.INCOME,
                    "Unattributed " + kind, amount,
                    knownTaxable ? Optional.of(amount) : Optional.empty()).inYear(year));
        }
    }
}
