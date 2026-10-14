package com.daviddunn.retirementplanner.domain.tax.state.iowa;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Evaluates represented filers; never awards a deceased person a later birthday. */
public final class IowaPersonalCreditCalculator {

    public record HouseholdAllowance(
            Set<AccountOwnership> filers,
            BigDecimal personalCredit,
            BigDecimal ageCredit,
            boolean seniorReliefEligible,
            LocalDate reliefAgeBoundary,
            LocalDate creditAgeBoundary) {
        public HouseholdAllowance {
            filers = Set.copyOf(filers);
            Objects.requireNonNull(personalCredit);
            Objects.requireNonNull(ageCredit);
            Objects.requireNonNull(reliefAgeBoundary);
            Objects.requireNonNull(creditAgeBoundary);
            if (filers.isEmpty() || filers.contains(AccountOwnership.JOINT)
                    || personalCredit.signum() < 0 || ageCredit.signum() < 0) {
                throw new IllegalArgumentException("Invalid Iowa represented filer/credit.");
            }
        }
    }

    public record Admission(
            Optional<HouseholdAllowance> allowance,
            List<StateTaxUnavailableReason> reasons,
            List<String> diagnostics) {
        public Admission {
            Objects.requireNonNull(allowance);
            reasons = List.copyOf(reasons);
            diagnostics = List.copyOf(diagnostics);
            if (allowance.isPresent() == !reasons.isEmpty()) {
                throw new IllegalArgumentException("Iowa credit admission must be complete or refused.");
            }
        }
    }

    public Admission calculate(int year, FilingStatus status, TaxHouseholdContext household, IowaTaxRules rules) {
        Objects.requireNonNull(household);
        Objects.requireNonNull(rules);
        Objects.requireNonNull(status);
        if (year != household.distributionDate().getYear()) {
            throw new IllegalArgumentException("Iowa credit year must match annual household facts.");
        }
        Set<AccountOwnership> filers;
        if (status == FilingStatus.SINGLE && household.activeOwners().size() == 1) {
            filers = household.activeOwners();
        } else if (status == FilingStatus.MARRIED_FILING_JOINTLY
                && household.householdOwners().equals(Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE))) {
            filers = household.householdOwners();
        } else {
            return refused("Iowa filing status does not establish the represented filer(s).");
        }
        var reliefBoundary = LocalDate.of(year, 12, 31);
        var creditBoundary = LocalDate.of(year + 1, 1, 1);
        var deathBoundary = LocalDate.of(year, 1, 1);
        boolean senior = false;
        int eligibleCredits = 0;
        for (var owner : filers) {
            var birth = household.birthDates().get(owner);
            if (birth == null) {
                return refused("Iowa age-dependent relief and credits require the actual filer's DOB.");
            }
            var reliefBirthday = birth.plusYears(rules.reliefAge());
            var creditBirthday = birth.plusYears(rules.creditAge());
            if (!household.activeOwners().contains(owner)) {
                if ((!reliefBirthday.isAfter(reliefBoundary) && reliefBirthday.isAfter(deathBoundary))
                        || (!creditBirthday.isAfter(creditBoundary) && creditBirthday.isAfter(deathBoundary))) {
                    return refused("A deceased filer's birthday after modeled death has unresolved Iowa relief/credit eligibility.");
                }
                senior |= !reliefBirthday.isAfter(deathBoundary);
                if (!creditBirthday.isAfter(deathBoundary)) {
                    eligibleCredits++;
                }
            } else {
                senior |= !reliefBirthday.isAfter(reliefBoundary);
                if (!creditBirthday.isAfter(creditBoundary)) {
                    eligibleCredits++;
                }
            }
        }
        return new Admission(Optional.of(new HouseholdAllowance(filers,
                rules.personalCredits().get(status), rules.ageCredit().multiply(BigDecimal.valueOf(eligibleCredits)),
                senior, reliefBoundary, creditBoundary)), List.of(), List.of());
    }

    private static Admission refused(String explanation) {
        return new Admission(Optional.empty(), List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), List.of(explanation));
    }
}
