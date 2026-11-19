package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasProductionTaxCalculator.Election;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source evidence from the selected executable annual trial, indexed by its frozen source list. */
public record ArkansasAnnualProvenance(
        LocalDate firstProjectedReceipt, FilingStatus filingStatus, String governmentRulesVersion,
        Map<AccountOwnership, LocalDate> modeledDeaths, List<Receipt> receipts, List<Pension> pensions,
        BigDecimal targetedFederalTaxableIncome, Election election) {

    public enum ReceiptBasis { VALIDATION_INPUT, MODELED_TRANSACTION }

    public record Receipt(int sourceIndex, ReceiptBasis basis, ReceiptWindow window,
            SourceQualification qualification, Form5329Status additionalTax, boolean additionalTaxFromValidation) {
        public Receipt {
            Objects.requireNonNull(basis);
            Objects.requireNonNull(qualification);
            Objects.requireNonNull(additionalTax);
            if (sourceIndex < 0 || (!additionalTaxFromValidation && additionalTax != Form5329Status.UNKNOWN)
                    || (basis == ReceiptBasis.MODELED_TRANSACTION
                    && (window == null || qualification != SourceQualification.UNSPECIFIED))) {
                throw new IllegalArgumentException("Invalid Arkansas receipt evidence reference.");
            }
        }
    }

    public record Pension(int sourceIndex, RetirementIncomeSourceOrigin origin, SourceQualification qualification) {
        public Pension {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(qualification);
            if (sourceIndex < 0) throw new IllegalArgumentException("Invalid Arkansas pension source reference.");
        }
    }

    public ArkansasAnnualProvenance {
        Objects.requireNonNull(firstProjectedReceipt);
        Objects.requireNonNull(filingStatus);
        Objects.requireNonNull(election);
        if (Objects.requireNonNull(governmentRulesVersion).isBlank()
                || (targetedFederalTaxableIncome != null && targetedFederalTaxableIncome.signum() < 0)) {
            throw new IllegalArgumentException("Invalid Arkansas annual rule or target provenance.");
        }
        modeledDeaths = Map.copyOf(modeledDeaths);
        receipts = List.copyOf(receipts);
        pensions = List.copyOf(pensions);
        if (modeledDeaths.containsKey(AccountOwnership.JOINT)
                || modeledDeaths.values().stream().anyMatch(d -> d.getDayOfYear() != 1
                        || d.getYear() > firstProjectedReceipt.getYear())) {
            throw new IllegalArgumentException("Invalid Arkansas modeled death evidence.");
        }
    }

    static ArkansasAnnualProvenance capture(ArkansasAnnualFunding.Candidate candidate,
            ArkansasTaxEvidence supplied, Map<IncomeSourceAttribution.AccountReference, Form5329Status> additionalTaxInputs,
            BigDecimal target) {
        var sources = candidate.incomeSources();
        var receipts = new ArrayList<Receipt>();
        var pensions = new ArrayList<Pension>();
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            var facts = candidate.evidence().receipts().get(source);
            if (facts != null) {
                var basis = source.kind() != IncomeSourceAttribution.Kind.ROTH_CONVERSION
                        && source.purpose() != IncomeSourceAttribution.Purpose.TAX_FUNDING
                        && facts.equals(supplied.receipts().get(source))
                        ? ReceiptBasis.VALIDATION_INPUT : ReceiptBasis.MODELED_TRANSACTION;
                boolean suppliedAdditionalTax = basis == ReceiptBasis.VALIDATION_INPUT
                        || (source.kind() != IncomeSourceAttribution.Kind.ROTH_CONVERSION
                            && source.account().filter(additionalTaxInputs::containsKey).isPresent());
                receipts.add(new Receipt(i, basis, facts.window().orElse(null), facts.qualification(),
                        facts.additionalTax(), suppliedAdditionalTax));
            }
            if (source.pension().isPresent()) {
                var origin = source.pension().orElseThrow().sourceOrigin();
                pensions.add(new Pension(i, origin, qualification(origin, facts == null
                        ? SourceQualification.UNSPECIFIED : facts.qualification())));
            }
        }
        var annual = candidate.annualTaxFacts();
        return new ArkansasAnnualProvenance(annual.household().distributionDate(), annual.filingStatus(),
                annual.projectedRules().getRulesVersion(), candidate.evidence().modeledDeaths(), receipts, pensions,
                target, candidate.stateTax().election().orElseThrow());
    }

    public void validateSources(int year, List<IncomeSourceAttribution> sources) {
        if (year != firstProjectedReceipt.getYear()
                || sources.stream().distinct().count() != sources.size()
                || sources.stream().anyMatch(s -> s.taxYear().orElse(-1) != year)) {
            throw new IllegalArgumentException("Arkansas source evidence year mismatch.");
        }
        var facts = new HashMap<Integer, Receipt>();
        for (var receipt : receipts) {
            var source = source(sources, receipt.sourceIndex());
            if (facts.put(receipt.sourceIndex(), receipt) != null
                    || receipt.qualification() == SourceQualification.OUTSIDE_ORDINARY_SCOPE
                    || receipt.additionalTax() == Form5329Status.APPLICABLE_ADDITIONAL_TAX
                    || (receipt.window() != null && (receipt.window().first().getYear() != year
                            || receipt.window().last().getYear() != year))
                    || (receipt.window() != null && source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                            && !receipt.window().last().isBefore(firstProjectedReceipt))
                    || (receipt.basis() == ReceiptBasis.MODELED_TRANSACTION && !source.isRetirementDistribution())) {
                throw new IllegalArgumentException("Inconsistent Arkansas receipt evidence.");
            }
        }
        var pensionIndices = new java.util.HashSet<Integer>();
        for (var pension : pensions) {
            var source = source(sources, pension.sourceIndex());
            var terms = source.pension().orElseThrow(() -> new IllegalArgumentException("Expected an Arkansas pension receipt."));
            var recipient = source.owner().orElseThrow();
            var receipt = facts.get(pension.sourceIndex());
            var explicit = receipt == null ? SourceQualification.UNSPECIFIED : receipt.qualification();
            if (!pensionIndices.add(pension.sourceIndex()) || pension.origin() != terms.sourceOrigin()
                    || pension.qualification() != qualification(pension.origin(), explicit)
                    || modeledDeaths.containsKey(recipient)
                    || (recipient != terms.originalOwner()
                        && (!modeledDeaths.containsKey(terms.originalOwner())
                            || terms.survivorMonthlyBenefit().filter(amount -> amount.signum() > 0).isEmpty()))) {
                throw new IllegalArgumentException("Inconsistent Arkansas pension origin or recipient evidence.");
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            if (source.pension().isPresent() != pensionIndices.contains(i)
                    || (source.grossAmount().signum() > 0 && source.isRetirementDistribution() && !facts.containsKey(i))) {
                throw new IllegalArgumentException("Missing Arkansas receipt evidence.");
            }
        }
        int expectedFilers = filingStatus == FilingStatus.MARRIED_FILING_JOINTLY ? 2 : 1;
        if (election.recipients().size() != expectedFilers
                || (expectedFilers == 1 && modeledDeaths.containsKey(election.recipients().getFirst().owner()))) {
            throw new IllegalArgumentException("Arkansas election differs from annual filing status.");
        }
    }

    private static IncomeSourceAttribution source(List<IncomeSourceAttribution> sources, int index) {
        if (index < 0 || index >= sources.size()) {
            throw new IllegalArgumentException("Arkansas evidence references an absent annual source.");
        }
        return sources.get(index);
    }

    private static SourceQualification qualification(RetirementIncomeSourceOrigin origin, SourceQualification explicit) {
        if (explicit == SourceQualification.OUTSIDE_ORDINARY_SCOPE
                || (origin != RetirementIncomeSourceOrigin.UNKNOWN && origin != RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN)) {
            throw new IllegalArgumentException("Unadmitted Arkansas pension provenance.");
        }
        return origin == RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN
                ? SourceQualification.ORDINARY_EMPLOYMENT_PENSION : explicit;
    }
}
