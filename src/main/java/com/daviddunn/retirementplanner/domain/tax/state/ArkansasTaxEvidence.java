package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Run-only evidence supplied to the isolated Arkansas calculation. */
public record ArkansasTaxEvidence(
        Map<IncomeSourceAttribution, ReceiptFacts> receipts,
        Map<AccountOwnership, LocalDate> modeledDeaths) {

    public static final ArkansasTaxEvidence NONE = new ArkansasTaxEvidence(Map.of(), Map.of());

    public ArkansasTaxEvidence {
        receipts = Map.copyOf(receipts);
        modeledDeaths = Map.copyOf(modeledDeaths);
    }

    public record ReceiptWindow(LocalDate first, LocalDate last) {
        public ReceiptWindow {
            Objects.requireNonNull(first);
            Objects.requireNonNull(last);
            if (last.isBefore(first)) throw new IllegalArgumentException("Reversed Arkansas receipt window.");
        }
    }

    public enum SourceQualification {
        UNSPECIFIED, ORDINARY_EMPLOYMENT_PENSION, OUTSIDE_ORDINARY_SCOPE
    }

    public enum Form5329Status {
        UNKNOWN, NO_APPLICABLE_ADDITIONAL_TAX, APPLICABLE_ADDITIONAL_TAX
    }

    public record ReceiptFacts(
            Optional<ReceiptWindow> window,
            SourceQualification qualification,
            Form5329Status additionalTax) {
        public ReceiptFacts {
            Objects.requireNonNull(window);
            Objects.requireNonNull(qualification);
            Objects.requireNonNull(additionalTax);
        }
    }

    private static final ReceiptFacts UNKNOWN = new ReceiptFacts(
            Optional.empty(), SourceQualification.UNSPECIFIED, Form5329Status.UNKNOWN);

    ReceiptFacts factsFor(IncomeSourceAttribution source) {
        return receipts.getOrDefault(source, UNKNOWN);
    }

    static SourceQualification pensionQualification(RetirementIncomeSourceOrigin origin, ReceiptFacts facts) {
        if (facts.qualification() == SourceQualification.ORDINARY_EMPLOYMENT_PENSION
                && origin != RetirementIncomeSourceOrigin.UNKNOWN
                && origin != RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN) {
            throw new ArkansasIncomeReconciliation.Rejected(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                    ArkansasProductionTaxCalculator.FailureCategory.ATTRIBUTION,
                    "Arkansas pension source origin contradicts the supplied employment-plan evidence.");
        }
        return switch (origin) {
            case EMPLOYER_RETIREMENT_PLAN -> SourceQualification.ORDINARY_EMPLOYMENT_PENSION;
            case UNKNOWN -> facts.qualification();
            case INDIVIDUALLY_PURCHASED_CONTRACT, OTHER_OR_SPECIAL_ARRANGEMENT -> SourceQualification.OUTSIDE_ORDINARY_SCOPE;
        };
    }
}
