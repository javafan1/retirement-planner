package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules;
import com.daviddunn.retirementplanner.persistence.StateTaxDatasetRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Full-year resident planning estimate. Qualification decisions remain in the tax layer. */
public final class IllinoisProductionTaxCalculator implements StateIncomeTaxCalculator {
    private static final StateTaxRules RULES = loadRules();

    private static StateTaxRules loadRules() {
        try {
            var rules = new StateTaxDatasetRepository().load().get("IL");
            if (rules == null || !rules.readiness().equals("READY_FOR_SIMPLIFIED_MODEL")
                    || !rules.taxBase().equals("FEDERAL_AGI_WITH_ADJUSTMENTS")
                    || !rules.system().equals("FLAT")) {
                throw new IllegalStateException("Illinois production rules are not ready.");
            }
            if (rules.adjustments().size() != 1
                    || !rules.adjustments().getFirst().method().equals("UNLIMITED_ELIGIBLE_INCOME")
                    || !rules.adjustments().getFirst().qualification()
                            .equals("QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME")
                    || rules.adjustments().getFirst().minimumAge() != null
                    || !rules.adjustments().getFirst().scope().equals("HOUSEHOLD")
                    || !rules.exemptionType().equals("DEDUCTION")
                    || rules.standardDeduction().values().stream().anyMatch(amount -> amount.signum() != 0)
                    || !rules.incomeTreatment().get(StateTaxRules.IncomeType.SOCIAL_SECURITY).equals("EXEMPT")
                    || !rules.surcharges().isEmpty() || !rules.boundaries().isEmpty()) {
                throw new IllegalStateException("Illinois rule changes require adapter review.");
            }
            return rules;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.IL) {
            throw new IllegalArgumentException("Illinois calculator requires Illinois residency.");
        }
        var status = request.filingStatus();
        if (status != FilingStatus.SINGLE && status != FilingStatus.MARRIED_FILING_JOINTLY) {
            throw new IllegalArgumentException("Illinois filing status is not modeled.");
        }
        var warnings = new LinkedHashSet<String>();
        warnings.add("Full-year Illinois resident; simplified retirement-planning estimate.");
        if (request.taxYear() < RULES.taxYear()) {
            throw new IllegalArgumentException("Historical Illinois rules before the dataset baseline are unavailable.");
        }
        if (request.taxYear() != RULES.taxYear()) {
            warnings.add("Uses the " + RULES.taxYear()
                    + " Illinois baseline in nominal dollars; future indexing and historical rules are not modeled.");
        }
        BigDecimal withdrawal = BigDecimal.ZERO;
        BigDecimal conversion = BigDecimal.ZERO;
        for (var source : request.income().getIncomeSources()) {
            if (!source.isRetirementDistribution()) continue;
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear()) {
                throw new IllegalArgumentException("Illinois attribution belongs to another tax year.");
            }
            if (source.modeledTaxableAmount().isEmpty()) {
                warnings.add("Unknown taxable retirement amount treated conservatively: " + source.description());
                continue;
            }
            var amount = source.modeledTaxableAmount().get();
            if (amount.signum() == 0) continue;
            if (source.account().isEmpty() || source.owner().isEmpty()
                    || !request.household().activeOwners().contains(source.owner().get())) {
                warnings.add("Unknown retirement source or recipient treated conservatively: " + source.description());
                continue;
            }
            AccountType type = source.account().get().type();
            boolean qualifying = switch (type) {
                case TRADITIONAL_IRA, ROLLOVER_IRA, TRADITIONAL_401K, TRADITIONAL_403B -> true;
                default -> false;
            };
            if (!qualifying) {
                warnings.add("Unassessed retirement qualification treated conservatively: " + source.description());
                continue;
            }
            if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                conversion = conversion.add(amount);
            } else {
                withdrawal = withdrawal.add(amount);
            }
        }
        if (withdrawal.compareTo(request.income().getTaxDeferredWithdrawals()) > 0
                || conversion.compareTo(request.income().getRothConversion()) > 0) {
            throw new IllegalArgumentException("Attributed Illinois income exceeds its federally included category.");
        }
        if (withdrawal.compareTo(request.income().getTaxDeferredWithdrawals()) < 0
                || conversion.compareTo(request.income().getRothConversion()) < 0) {
            warnings.add("Unassessed retirement income receives no Illinois subtraction.");
        }
        if (request.income().getPensionIncome().signum() > 0) {
            warnings.add("Pension plan qualification unavailable; pension conservatively taxed.");
        }
        BigDecimal agi = request.federal().getAdjustedGrossIncome();
        BigDecimal adjustments = request.federal().getTaxableSocialSecurity().add(withdrawal).add(conversion);
        if (adjustments.compareTo(agi) > 0) {
            throw new IllegalArgumentException("Illinois subtractions exceed federal AGI.");
        }
        BigDecimal base = agi.subtract(adjustments);
        BigDecimal exemption = BigDecimal.ZERO;
        if (agi.compareTo(RULES.exemptionCeiling().get(status)) <= 0) {
            int filers = Math.min(status == FilingStatus.SINGLE ? 1 : 2,
                    request.household().householdOwners().size());
            exemption = RULES.exemptions().get(status);
            if (filers == 0) {
                exemption = BigDecimal.ZERO;
                warnings.add("Household exemption context unavailable.");
            } else if (status == FilingStatus.MARRIED_FILING_JOINTLY && filers < 2) {
                exemption = RULES.exemptions().get(FilingStatus.SINGLE);
            }
            LocalDate end = LocalDate.of(request.taxYear(), 12, 31);
            Set<AccountOwnership> owners = request.household().householdOwners();
            if (status == FilingStatus.SINGLE) {
                var active = request.household().activeOwners();
                owners = active.contains(AccountOwnership.PRIMARY) ? Set.of(AccountOwnership.PRIMARY)
                        : active.contains(AccountOwnership.SPOUSE) ? Set.of(AccountOwnership.SPOUSE) : Set.of();
            }
            for (var owner : owners) {
                var birth = request.household().birthDates().get(owner);
                if (birth == null) {
                    warnings.add("Age exemption unavailable for " + owner);
                    continue;
                }
                // The modeled death is January 1. A deceased filer cannot attain
                // a later birthday merely because the return remains joint that year.
                var ageDate = request.household().activeOwners().contains(owner)
                        ? end : LocalDate.of(request.taxYear(), 1, 1);
                var age = BigDecimal.valueOf(Period.between(birth, ageDate).getYears());
                for (var senior : RULES.seniors()) {
                    if (age.compareTo(senior.minimumAge()) >= 0) {
                        exemption = exemption.add(senior.amount());
                    }
                }
            }
        }
        BigDecimal tax = base.subtract(exemption).max(BigDecimal.ZERO)
                .multiply(RULES.rate()).setScale(2, RoundingMode.HALF_UP);
        return new ProductionStateTaxResult(StateTaxJurisdiction.IL, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, List.copyOf(warnings), Optional.empty());
    }
}
