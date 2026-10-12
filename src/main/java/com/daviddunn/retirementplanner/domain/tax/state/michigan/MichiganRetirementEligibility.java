package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** State eligibility assessment is intentionally outside account/withdrawal objects. */
public final class MichiganRetirementEligibility {
    public enum Qualification { QUALIFYING, NONQUALIFYING, UNKNOWN }

    public record Decision(
            IncomeSourceAttribution source,
            Qualification qualification,
            BigDecimal eligibleAmount,
            String reason) {
        public Decision {
            Objects.requireNonNull(source);
            Objects.requireNonNull(qualification);
            Objects.requireNonNull(eligibleAmount);
            Objects.requireNonNull(reason);
        }
    }

    public Decision assess(IncomeSourceAttribution source, MichiganTaxContext context) {
        if (!source.isRetirementDistribution()
                && source.kind() != IncomeSourceAttribution.Kind.PENSION) {
            return denied(source, Qualification.NONQUALIFYING, "NOT_RETIREMENT_DISTRIBUTION");
        }
        if (source.modeledTaxableAmount().isPresent()
                && source.modeledTaxableAmount().get().signum() == 0) {
            return denied(source, Qualification.NONQUALIFYING, "NOT_INCLUDED_IN_MODELED_TAXABLE_INCOME");
        }
        if (source.kind() == IncomeSourceAttribution.Kind.PENSION) {
            return denied(source, Qualification.UNKNOWN, "PENSION_SOURCE_QUALIFICATION_UNAVAILABLE");
        }
        if (source.owner().isEmpty() || source.account().isEmpty()
                || source.modeledTaxableAmount().isEmpty()) {
            return denied(source, Qualification.UNKNOWN, "SOURCE_OWNER_OR_TAXABLE_AMOUNT_UNAVAILABLE");
        }
        var owner = source.owner().get();
        if (!context.activeOwners().contains(owner)) {
            return denied(source, Qualification.UNKNOWN, "DECEASED_OWNER_OR_SUCCESSOR_QUALIFICATION_UNAVAILABLE");
        }
        AccountType type = source.account().get().type();
        if (type == AccountType.TRADITIONAL_401K || type == AccountType.TRADITIONAL_403B) {
            return denied(source, Qualification.UNKNOWN, "EMPLOYER_CONTRIBUTION_QUALIFICATION_UNAVAILABLE");
        }
        if (type == AccountType.ROLLOVER_IRA) {
            return denied(source, Qualification.UNKNOWN, "ROLLOVER_ORIGINAL_PLAN_QUALIFICATION_UNAVAILABLE");
        }
        if (type != AccountType.TRADITIONAL_IRA) {
            return denied(source, Qualification.UNKNOWN, "ACCOUNT_QUALIFICATION_UNAVAILABLE");
        }
        LocalDate birth = context.birthDates().get(owner);
        if (birth == null) {
            return denied(source, Qualification.UNKNOWN, "OWNER_BIRTH_DATE_UNAVAILABLE");
        }
        LocalDate earliestReceipt = source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                ? LocalDate.of(context.distributionDate().getYear(), 1, 1) : context.distributionDate();
        if (birth.plusYears(59).plusMonths(6).isAfter(earliestReceipt)) {
            return denied(source, Qualification.NONQUALIFYING, "BELOW_59_AND_HALF_NO_MODELED_EXCEPTION");
        }
        return new Decision(source, Qualification.QUALIFYING, source.modeledTaxableAmount().orElseThrow(),
                "IRA_AGE_QUALIFIED_SHARED_RETIREMENT_LIMIT");
    }

    private static Decision denied(IncomeSourceAttribution source, Qualification qualification, String reason) {
        return new Decision(source, qualification, BigDecimal.ZERO, reason);
    }
}
