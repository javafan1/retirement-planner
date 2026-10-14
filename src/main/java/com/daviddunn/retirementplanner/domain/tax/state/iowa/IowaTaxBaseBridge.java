package com.daviddunn.retirementplanner.domain.tax.state.iowa;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxBaseBreakdown;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationRequest;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Signed modeled-base reconciliation and narrow own-IRA admission; metadata is not income twice. */
public final class IowaTaxBaseBridge {

    public record BaseAmounts(
            BigDecimal modeledAgi,
            BigDecimal modeledDeduction,
            BigDecimal derivedRawFederalBase,
            BigDecimal taxableSocialSecurityExcluded,
            BigDecimal iraWithdrawalsExcluded,
            BigDecimal iraConversionsExcluded,
            BigDecimal signedIowaBase,
            BigDecimal nonnegativeIowaTaxableIncome,
            BigDecimal reliefIncome) {
        public BaseAmounts {
            for (var amount : List.of(modeledAgi, modeledDeduction, taxableSocialSecurityExcluded,
                    iraWithdrawalsExcluded, iraConversionsExcluded, nonnegativeIowaTaxableIncome)) {
                if (amount.signum() < 0) throw new IllegalArgumentException("Iowa included/base amount cannot be negative.");
            }
            Objects.requireNonNull(derivedRawFederalBase);
            Objects.requireNonNull(signedIowaBase);
            Objects.requireNonNull(reliefIncome);
            if (derivedRawFederalBase.compareTo(modeledAgi.subtract(modeledDeduction)) != 0
                    || signedIowaBase.compareTo(derivedRawFederalBase.subtract(taxableSocialSecurityExcluded)
                            .subtract(iraWithdrawalsExcluded).subtract(iraConversionsExcluded)) != 0
                    || nonnegativeIowaTaxableIncome.compareTo(signedIowaBase.max(BigDecimal.ZERO)) != 0
                    || reliefIncome.compareTo(signedIowaBase.add(modeledDeduction)) != 0) {
                throw new IllegalArgumentException("Inconsistent Iowa signed tax base.");
            }
        }
    }

    public record Admission(
            Optional<BaseAmounts> amounts,
            List<StateTaxUnavailableReason> reasons,
            List<String> diagnostics) {
        public Admission {
            Objects.requireNonNull(amounts);
            reasons = List.copyOf(reasons);
            diagnostics = List.copyOf(diagnostics);
            if (amounts.isPresent() == !reasons.isEmpty()) {
                throw new IllegalArgumentException("Iowa base admission must be complete or refused.");
            }
        }
    }

    public Admission reconcile(StateTaxCalculationRequest request, FederalTaxBaseBreakdown base, IowaTaxRules rules) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(base);
        Objects.requireNonNull(rules);
        var reasons = new LinkedHashSet<StateTaxUnavailableReason>();
        var explanations = new LinkedHashSet<String>();
        var income = request.income();
        var socialSecurity = request.federal().getTaxableSocialSecurity();
        if (base.coverage() != rules.basePolicy()
                || base.modeledAdjustedGrossIncome().compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(socialSecurity)) != 0
                || base.modeledStandardDeduction().compareTo(request.projectedRules()
                        .getFederalTaxRules(request.filingStatus()).getStandardDeduction()) != 0
                || socialSecurity.compareTo(income.getSocialSecurityIncome().multiply(new BigDecimal("0.85"))) > 0) {
            refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Federal modeled base/coverage does not reconcile with current annual income and projected deduction.");
        }
        if (income.getPensionIncome().signum() > 0) {
            refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                    "Pension legal-plan qualification is unavailable, including survivor qualification.");
        }
        BigDecimal withdrawal = BigDecimal.ZERO;
        BigDecimal conversion = BigDecimal.ZERO;
        BigDecimal investments = BigDecimal.ZERO;
        BigDecimal attributedSocialSecurity = BigDecimal.ZERO;
        var seen = new HashSet<SourceKey>();
        for (var source : income.getIncomeSources()) {
            if (source.grossAmount().signum() == 0) {
                continue;
            }
            if (!seen.add(new SourceKey(source.kind(), source.purpose(), source.account(), source.destination(),
                    source.owner(), source.grossAmount().stripTrailingZeros(), source.taxYear()))) {
                throw new IllegalArgumentException("Duplicate Iowa source attribution.");
            }
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear()) {
                throw new IllegalArgumentException("Iowa source attribution belongs to another tax year.");
            }
            if (source.isRetirementDistribution() && source.modeledTaxableAmount().isPresent()) {
                if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                    conversion = conversion.add(source.modeledTaxableAmount().orElseThrow());
                } else {
                    withdrawal = withdrawal.add(source.modeledTaxableAmount().orElseThrow());
                }
            }
            switch (source.kind()) {
                case SOCIAL_SECURITY -> attributedSocialSecurity = attributedSocialSecurity.add(source.grossAmount());
                case PENSION -> refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                        "Pension legal-plan qualification is unavailable, including survivor qualification.");
                case INTEREST, DIVIDEND, CAPITAL_GAIN -> {
                    if (source.modeledTaxableAmount().isEmpty()
                            || source.modeledTaxableAmount().orElseThrow().compareTo(source.grossAmount()) != 0) {
                        refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                                "Investment source cannot establish an assessed ordinary included amount.");
                    } else {
                        investments = investments.add(source.grossAmount());
                    }
                }
                case ORDINARY_WITHDRAWAL -> {
                    if (source.account().isPresent() && source.account().orElseThrow().type().requiresIndividualOwnership()) {
                        assessRetirement(request, source, rules, reasons, explanations);
                    } else if (source.modeledTaxableAmount().isEmpty()
                            || source.modeledTaxableAmount().orElseThrow().signum() != 0) {
                        refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                                "Brokerage/unknown proceeds do not establish gains or basis; cash principal is not income.");
                    }
                }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION ->
                        assessRetirement(request, source, rules, reasons, explanations);
                case OTHER -> refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                        "Unclassified income or special adjustments are outside the Iowa model.");
            }
        }
        if (withdrawal.compareTo(income.getTaxDeferredWithdrawals()) > 0
                || conversion.compareTo(income.getRothConversion()) > 0
                || investments.compareTo(income.getTaxableInterestIncome()) > 0
                || attributedSocialSecurity.compareTo(income.getSocialSecurityIncome()) > 0) {
            throw new IllegalArgumentException("Attributed Iowa income exceeds its modeled annual category.");
        }
        if (withdrawal.compareTo(income.getTaxDeferredWithdrawals()) < 0
                || conversion.compareTo(income.getRothConversion()) < 0) {
            refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                    "Retirement income, including conversion trials, lacks complete executed-source attribution.");
        }
        if (!reasons.isEmpty()) {
            return new Admission(Optional.empty(), List.copyOf(reasons), List.copyOf(explanations));
        }
        var signed = base.derivedRawTaxableIncome().subtract(socialSecurity).subtract(withdrawal).subtract(conversion);
        var relief = signed.add(base.modeledStandardDeduction());
        if (relief.compareTo(base.modeledAdjustedGrossIncome().subtract(socialSecurity).subtract(withdrawal).subtract(conversion)) != 0) {
            throw new IllegalStateException("Iowa signed relief reconciliation failed.");
        }
        return new Admission(Optional.of(new BaseAmounts(base.modeledAdjustedGrossIncome(), base.modeledStandardDeduction(),
                base.derivedRawTaxableIncome(), socialSecurity, withdrawal, conversion, signed,
                signed.max(BigDecimal.ZERO), relief)), List.of(), List.of());
    }

    private static void assessRetirement(StateTaxCalculationRequest request, IncomeSourceAttribution source,
            IowaTaxRules rules, LinkedHashSet<StateTaxUnavailableReason> reasons, LinkedHashSet<String> explanations) {
        if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION
                && source.account().isEmpty()) {
            refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                    "A positive conversion remainder/external transfer lacks executed source/destination attribution.");
            if (source.modeledTaxableAmount().isEmpty()) {
                refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                        "The federally included conversion amount is unknown.");
            }
            return;
        }
        if (source.account().isEmpty() || source.owner().isEmpty()
                || !request.household().activeOwners().contains(source.owner().orElseThrow())) {
            refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                    "Retirement account, actual owner or active recipient is unknown.");
            return;
        }
        AccountType type = source.account().orElseThrow().type();
        switch (type) {
            case INHERITED_TRADITIONAL_IRA, INHERITED_ROTH_IRA -> refuse(reasons, explanations,
                    StateTaxUnavailableReason.INHERITED_DISTRIBUTION_HISTORY_REQUIRED,
                    "Inherited/successor retirement history and elections are unavailable.");
            case TRADITIONAL_401K, TRADITIONAL_403B, ROTH_401K -> refuse(reasons, explanations,
                    StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                    "Employer legal-plan qualification is unavailable; participant assertions are insufficient.");
            case ROTH_IRA -> refuse(reasons, explanations, StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED,
                    "The domain cannot establish qualified Roth treatment or nonqualified distribution history.");
            case TRADITIONAL_IRA, ROLLOVER_IRA -> {
                if (source.taxYear().isEmpty()) {
                    refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                            "Retirement attribution requires its actual source year.");
                }
                if (source.modeledTaxableAmount().isEmpty()
                        || source.modeledTaxableAmount().orElseThrow().compareTo(source.grossAmount()) != 0) {
                    refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                            "Only fully attributed pre-tax IRA receipts/conversions are admitted; basis is unavailable.");
                }
                if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                    if (source.purpose() != IncomeSourceAttribution.Purpose.CONVERSION
                            || source.destination().isEmpty()
                            || source.destination().orElseThrow().type() != AccountType.ROTH_IRA) {
                        refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                                "A generated same-owner IRA-to-Roth IRA executed transfer must be established.");
                    }
                } else if ((source.kind() != IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL
                        && source.kind() != IncomeSourceAttribution.Kind.RMD && source.kind() != IncomeSourceAttribution.Kind.OPENING_RMD)
                        || (source.purpose() != IncomeSourceAttribution.Purpose.CASH_FLOW
                        && source.purpose() != IncomeSourceAttribution.Purpose.TAX_FUNDING
                        && !(source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                        && source.purpose() == IncomeSourceAttribution.Purpose.INCOME))) {
                    refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                            "IRA transaction purpose/classification is unsupported.");
                }
                var birth = request.household().birthDates().get(source.owner().orElseThrow());
                if (birth == null || birth.plusYears(rules.retirementAge()).isAfter(LocalDate.of(request.taxYear(), 12, 31))) {
                    refuse(reasons, explanations, StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION,
                            "Actual IRA recipient must be 55 by December 31; disability/survivor exceptions are unassessed.");
                }
            }
            default -> refuse(reasons, explanations, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                    "Retirement transaction is inconsistent with its source account type.");
        }
    }

    private static void refuse(LinkedHashSet<StateTaxUnavailableReason> reasons, LinkedHashSet<String> explanations,
            StateTaxUnavailableReason reason, String explanation) {
        reasons.add(reason);
        explanations.add(explanation);
    }

    private record SourceKey(
            IncomeSourceAttribution.Kind kind,
            IncomeSourceAttribution.Purpose purpose,
            Optional<IncomeSourceAttribution.AccountReference> account,
            Optional<IncomeSourceAttribution.AccountReference> destination,
            Optional<com.daviddunn.retirementplanner.domain.model.AccountOwnership> owner,
            BigDecimal gross,
            java.util.OptionalInt year) {}
}
