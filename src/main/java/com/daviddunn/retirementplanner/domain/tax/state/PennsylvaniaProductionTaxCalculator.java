package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules;
import com.daviddunn.retirementplanner.persistence.StateTaxDatasetRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Bounded full-year resident estimate. Missing state basis or legal plan facts never mean zero tax. */
public final class PennsylvaniaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private static final StateTaxRules RULES = loadRules();
    private static final int IRA_ELIGIBILITY_MONTHS = RULES.adjustments().stream()
            .filter(rule -> rule.appliesTo().equals(List.of(StateTaxRules.IncomeType.TRADITIONAL_IRA)))
            .findFirst().orElseThrow().minimumAge().multiply(BigDecimal.valueOf(12)).intValueExact();

    private static StateTaxRules loadRules() {
        try {
            var rules = new StateTaxDatasetRepository().load().get("PA");
            if (rules == null || !rules.readiness().equals("READY_WITH_LIMITATIONS")
                    || !rules.taxBase().equals("STATE_DEFINED_INCOME") || !rules.system().equals("FLAT")
                    || !rules.exemptionType().equals("NONE")
                    || rules.standardDeduction().values().stream().anyMatch(value -> value.signum() != 0)
                    || rules.exemptions().values().stream().anyMatch(value -> value.signum() != 0)
                    || !rules.seniors().isEmpty() || !rules.surcharges().isEmpty()
                    || rules.adjustments().size() != 3
                    || rules.adjustments().stream().anyMatch(rule ->
                            !rule.method().equals("UNLIMITED_ELIGIBLE_INCOME") || !rule.scope().equals("PER_PERSON")
                                    || !rule.maximum().isEmpty() || !rule.ageBands().isEmpty() || !rule.incomeBands().isEmpty())
                    || !"EXEMPT".equals(rules.incomeTreatment().get(StateTaxRules.IncomeType.SOCIAL_SECURITY))) {
                throw new IllegalStateException("Pennsylvania rule changes require production adapter review.");
            }
            var ira = rules.adjustments().stream().filter(rule ->
                    rule.appliesTo().equals(List.of(StateTaxRules.IncomeType.TRADITIONAL_IRA))).findFirst().orElseThrow();
            var conversion = rules.adjustments().stream().filter(rule ->
                    rule.appliesTo().equals(List.of(StateTaxRules.IncomeType.ROTH_CONVERSION))).findFirst().orElseThrow();
            var pension = rules.adjustments().stream().filter(rule ->
                    rule.appliesTo().equals(List.of(StateTaxRules.IncomeType.PENSION, StateTaxRules.IncomeType.EMPLOYER_RETIREMENT_PLAN)))
                    .findFirst().orElseThrow();
            if (ira.minimumAge() == null || ira.qualification() != null
                    || !"COMPLETE_IRA_TO_ROTH_CONVERSION".equals(conversion.qualification()) || conversion.minimumAge() != null
                    || !"ELIGIBLE_PLAN_AND_RETIRED".equals(pension.qualification()) || pension.minimumAge() != null
                    || rules.boundaries().size() != 1
                    || !"UNASSESSED_QUALIFICATION".equals(rules.boundaries().getFirst().trigger())
                    || !"COMPLETE_IRA_TO_ROTH_CONVERSION".equals(rules.boundaries().getFirst().qualification())
                    || rules.incomeTreatment().entrySet().stream().anyMatch(entry -> !entry.getValue().equals(
                            entry.getKey() == StateTaxRules.IncomeType.SOCIAL_SECURITY
                                    || entry.getKey() == StateTaxRules.IncomeType.QUALIFIED_ROTH_DISTRIBUTION ? "EXEMPT" : "TAXABLE"))) {
                throw new IllegalStateException("Pennsylvania qualification changes require production adapter review.");
            }
            return rules;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.PA) {
            throw new IllegalArgumentException("Pennsylvania calculator requires Pennsylvania residency.");
        }
        var reasons = new LinkedHashSet<StateTaxUnavailableReason>();
        var explanations = new LinkedHashSet<String>();
        if (request.taxYear() < RULES.taxYear()) {
            reasons.add(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE);
            explanations.add("Historical Pennsylvania rules before the validated baseline are unavailable.");
        }
        BigDecimal withdrawal = BigDecimal.ZERO;
        BigDecimal conversion = BigDecimal.ZERO;
        BigDecimal investments = BigDecimal.ZERO;
        for (var source : request.income().getIncomeSources()) {
            if (source.grossAmount().signum() == 0) continue;
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear()) {
                throw new IllegalArgumentException("Pennsylvania attribution belongs to another tax year.");
            }
            if (source.isRetirementDistribution() && source.modeledTaxableAmount().isPresent()) {
                if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                    conversion = conversion.add(source.modeledTaxableAmount().get());
                } else {
                    withdrawal = withdrawal.add(source.modeledTaxableAmount().get());
                }
            }
            switch (source.kind()) {
                case SOCIAL_SECURITY -> { /* PA excludes retirement and survivor benefits independently of federal tax. */ }
                case INTEREST, DIVIDEND, CAPITAL_GAIN -> {
                    // These are caller-established ordinary income amounts, not brokerage proceeds.
                    if (source.modeledTaxableAmount().isEmpty()
                            || source.modeledTaxableAmount().get().compareTo(source.grossAmount()) != 0) {
                        reasons.add(StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
                        explanations.add("Investment source/basis cannot establish an ordinary Pennsylvania taxable amount.");
                    } else {
                        investments = investments.add(source.grossAmount());
                    }
                }
                case PENSION -> {
                    reasons.add(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION);
                    explanations.add("Pension legal-plan qualification is unavailable, including survivor-plan qualification.");
                }
                case ORDINARY_WITHDRAWAL -> {
                    if (source.account().isPresent()
                            && source.account().get().type().requiresIndividualOwnership()) {
                        assessRetirement(request, source, reasons, explanations);
                    } else if (source.modeledTaxableAmount().isEmpty()
                            || source.modeledTaxableAmount().get().signum() != 0) {
                        reasons.add(StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
                        explanations.add("Taxable-account proceeds do not establish Pennsylvania gains/basis.");
                    }
                    // Known cash/retained post-tax principal is not an income category.
                }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION ->
                        assessRetirement(request, source, reasons, explanations);
                case OTHER -> {
                    reasons.add(StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE);
                    explanations.add("Unclassified income cannot establish a Pennsylvania taxable category.");
                }
            }
        }
        if (withdrawal.compareTo(request.income().getTaxDeferredWithdrawals()) > 0
                || conversion.compareTo(request.income().getRothConversion()) > 0
                || investments.compareTo(request.income().getTaxableInterestIncome()) > 0) {
            throw new IllegalArgumentException("Attributed income exceeds its modeled income category.");
        }
        if (withdrawal.compareTo(request.income().getTaxDeferredWithdrawals()) < 0
                || conversion.compareTo(request.income().getRothConversion()) < 0) {
            reasons.add(StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE);
            explanations.add("Retirement income lacks complete owner/account/transaction attribution.");
        }
        if (request.income().getPensionIncome().signum() > 0) {
            reasons.add(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION);
            explanations.add("Pension legal-plan qualification is unavailable, including survivor-plan qualification.");
        }
        if (!reasons.isEmpty()) {
            return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.PA,
                    request.assumptions().calculationMethod(), List.copyOf(reasons), String.join(" ", explanations));
        }
        // The existing scalar also represents explicit ordinary investment amounts at the tax boundary.
        // Source records classify that amount; they are not added a second time. Never use federal AGI here.
        BigDecimal taxable = request.income().getTaxableInterestIncome();
        var limitations = new java.util.ArrayList<String>();
        limitations.add("Full-year Pennsylvania resident; state personal income tax only. No local taxes or Tax Forgiveness.");
        limitations.add("Ordinary investment amounts only; no state basis, special interest sourcing or loss reconstruction.");
        limitations.add("Pennsylvania taxable investment income: " + taxable.toPlainString()
                + "; admitted IRA receipts, complete IRA conversions and Social Security excluded.");
        limitations.add("Validated " + RULES.taxYear() + " rules: " + RULES.datasetVersion() + ".");
        if (request.taxYear() != RULES.taxYear()) {
            limitations.add("Uses the " + RULES.taxYear() + " Pennsylvania baseline in nominal dollars; future law is not predicted.");
        }
        return new ProductionStateTaxResult(StateTaxJurisdiction.PA, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED,
                taxable.multiply(RULES.rate()).setScale(2, RoundingMode.HALF_UP), limitations, Optional.empty());
    }

    private static void assessRetirement(StateTaxCalculationRequest request, IncomeSourceAttribution source,
            LinkedHashSet<StateTaxUnavailableReason> reasons, LinkedHashSet<String> explanations) {
        if (source.account().isEmpty() || source.owner().isEmpty()
                || !request.household().activeOwners().contains(source.owner().get())) {
            reasons.add(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION);
            explanations.add("Retirement source or active recipient is unknown.");
            return;
        }
        AccountType type = source.account().get().type();
        switch (type) {
            case INHERITED_TRADITIONAL_IRA, INHERITED_ROTH_IRA -> {
                reasons.add(StateTaxUnavailableReason.INHERITED_DISTRIBUTION_HISTORY_REQUIRED);
                explanations.add("Inherited retirement history/elections are not modeled.");
            }
            case TRADITIONAL_401K, TRADITIONAL_403B, ROTH_401K -> {
                reasons.add(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION);
                explanations.add("Employer legal-plan qualification is unavailable; participant facts alone are insufficient.");
            }
            case TRADITIONAL_IRA, ROLLOVER_IRA, ROTH_IRA -> {
                if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                    if (type == AccountType.ROTH_IRA || source.purpose() != IncomeSourceAttribution.Purpose.CONVERSION
                            || source.destination().isEmpty() || source.destination().get().type() != AccountType.ROTH_IRA
                            || source.modeledTaxableAmount().isEmpty()
                            || source.modeledTaxableAmount().get().compareTo(source.grossAmount()) != 0) {
                        reasons.add(StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE);
                        explanations.add("A complete generated individual IRA-to-Roth IRA transfer must be established; external splits are unavailable.");
                    }
                    return;
                }
                var birth = request.household().birthDates().get(source.owner().get());
                LocalDate receipt = source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                        ? LocalDate.of(request.taxYear(), 1, 1) : request.household().distributionDate();
                if (birth == null || receipt.isBefore(birth.plusMonths(IRA_ELIGIBILITY_MONTHS))) {
                    reasons.add(type == AccountType.ROTH_IRA
                            ? StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED
                            : StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
                    explanations.add("Early or undated IRA receipts require unavailable state contribution/history information.");
                }
            }
            default -> {
                reasons.add(StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE);
                explanations.add("Retirement classification is inconsistent with its source account.");
            }
        }
    }
}
