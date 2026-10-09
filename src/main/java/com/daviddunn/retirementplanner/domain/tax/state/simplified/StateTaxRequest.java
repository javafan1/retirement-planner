package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.IncomeType;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.PensionSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Full-year resident, nonnegative income only. Federal bases are supplied results,
 * never reconstructed by adding income lines. Lines are disjoint taxable components:
 * IRA/employer distributions EXCLUDE conversions; qualified dividends EXCLUDE ordinary
 * dividends. SS federalIncluded is the federally taxable part; gross SS is separate.
 * stateDefinedAmount is a caller-established state-basis amount BEFORE exclusions,
 * not an inferred copy of AGI. Qualifications must be explicitly assessed per line.
 */
public record StateTaxRequest(
        String jurisdiction, int taxYear, FilingStatus filingStatus,
        BigDecimal federalAgi, BigDecimal federalTaxableIncome,
        List<PersonIncome> people) {

    public StateTaxRequest {
        Objects.requireNonNull(jurisdiction);
        if (!jurisdiction.matches("[A-Z]{2}") || taxYear < 1900) {
            throw new IllegalArgumentException("Invalid jurisdiction/year");
        }
        Objects.requireNonNull(filingStatus);
        nonnegative(federalAgi);
        nonnegative(federalTaxableIncome);
        if (federalTaxableIncome.compareTo(federalAgi) > 0) {
            throw new IllegalArgumentException("Federal taxable income exceeds AGI");
        }
        people = List.copyOf(people);
        if (people.isEmpty() || people.size() > 2
                || people.getFirst().owner() != Owner.PRIMARY
                || (people.size() == 2 && people.getLast().owner() != Owner.SPOUSE)
                || (filingStatus == FilingStatus.SINGLE && people.size() != 1)
                || (filingStatus == FilingStatus.MARRIED_FILING_JOINTLY && people.size() != 2)) {
            throw new IllegalArgumentException("Return participants must match filing status");
        }
        BigDecimal included = people.stream().flatMap(person -> person.income().stream())
                .map(Income::federalIncluded).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (included.compareTo(federalAgi) > 0) {
            throw new IllegalArgumentException("Classified federal components exceed AGI");
        }
    }

    public enum Owner { PRIMARY, SPOUSE }
    public enum Qualification { ELIGIBLE, INELIGIBLE, UNKNOWN }

    public record PersonIncome(
            Owner owner, BigDecimal age, BigDecimal totalSocialSecurity,
            BigDecimal federallyTaxableSocialSecurity,
            Set<String> qualifyingEvents, List<Income> income) {
        public PersonIncome {
            Objects.requireNonNull(owner);
            nonnegative(age);
            if (age.compareTo(new BigDecimal("130")) > 0) {
                throw new IllegalArgumentException("Invalid age");
            }
            nonnegative(totalSocialSecurity);
            nonnegative(federallyTaxableSocialSecurity);
            qualifyingEvents = Set.copyOf(qualifyingEvents);
            if (!Set.of("DEATH", "DISABILITY").containsAll(qualifyingEvents)) {
                throw new IllegalArgumentException("Unknown qualifying event");
            }
            income = List.copyOf(income);
            BigDecimal taxableSs = income.stream()
                    .filter(line -> line.type() == IncomeType.SOCIAL_SECURITY)
                    .map(Income::federalIncluded).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (taxableSs.compareTo(federallyTaxableSocialSecurity) != 0
                    || taxableSs.compareTo(totalSocialSecurity) > 0) {
                throw new IllegalArgumentException("Social Security components must reconcile and not exceed gross benefits");
            }
        }
    }

    public record Income(
            IncomeType type, BigDecimal federalIncluded, BigDecimal stateDefinedAmount,
            PensionSource pensionSource, Map<String, Qualification> qualifications) {
        public Income {
            Objects.requireNonNull(type);
            nonnegative(federalIncluded);
            nonnegative(stateDefinedAmount);
            Objects.requireNonNull(pensionSource);
            qualifications = Map.copyOf(qualifications);
            if (type == IncomeType.QUALIFIED_ROTH_DISTRIBUTION
                    && federalIncluded.signum() != 0) {
                throw new IllegalArgumentException("Qualified Roth cannot be federally included");
            }
        }
    }

    private static void nonnegative(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount is required; absence is not zero");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Negative income/basis is outside the prototype");
        }
    }
}
