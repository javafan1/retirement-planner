package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import java.time.LocalDate;
import java.time.Period;

/** Immutable metadata captured with a projection, not read from live UI assumptions. */
public record BreakEvenPlanSummary(PersonSummary primary, PersonSummary spouse) {
    public record PersonSummary(String name, LocalDate birthDate, Integer retirementClaimingAge,
            LocalDate retirementClaimDate,
            com.daviddunn.retirementplanner.domain.model.MortalityCategory mortalityCategory) {
        public PersonSummary(String name, LocalDate birthDate, Integer retirementClaimingAge) {
            this(name, birthDate, retirementClaimingAge, null, null);
        }
        public Integer ageIn(int year) {
            return birthDate == null ? null : Period.between(birthDate, LocalDate.of(year, 12, 31)).getYears();
        }
    }

    public static BreakEvenPlanSummary from(Household household) {
        PersonSummary primary = person(household.getPrimaryPerson(), "Primary");
        if (!household.hasSpouse() && primary.birthDate() != null && primary.retirementClaimingAge() != null) {
            primary = new PersonSummary(primary.name(), primary.birthDate(), primary.retirementClaimingAge(),
                    com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator
                            .calculateRetirementClaimDate(primary.birthDate(), primary.retirementClaimingAge()), primary.mortalityCategory());
        }
        return new BreakEvenPlanSummary(primary,
                household.spouse().map(spouse -> person(spouse, "Spouse")).orElse(null));
    }

    public boolean hasSpouse() { return spouse != null; }

    private static PersonSummary person(Person person, String fallback) {
        if (person == null) return new PersonSummary(fallback, null, null);
        String name = person.getFirstName();
        var elections = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast).toList();
        return new PersonSummary(name == null || name.isBlank() ? fallback : name,
                person.getBirthDate(), elections.size() == 1 ? elections.getFirst().getClaimingAge() : null,
                // The normal projection's authoritative election uses this exact stored start date.
                elections.size() == 1 ? elections.getFirst().getStartDate() : null,
                person.getMortalityCategory());
    }
}
