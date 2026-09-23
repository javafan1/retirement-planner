package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** The persisted whole-year survivor election, independent of either retirement election. */
public final class SurvivorBenefitClaimingPolicy {
    private SurvivorBenefitClaimingPolicy() {
    }

    public record Choices(Person survivor, List<Integer> ages, boolean immediateAtDeath,
                          LocalDate survivorFullRetirementDate) {
        public Choices {
            ages = List.copyOf(ages);
        }
    }

    public static Choices choices(Household household, DeathScenario scenario, Integer deathYear) {
        Objects.requireNonNull(household);
        Person survivor = scenario == DeathScenario.PRIMARY_DIES ? household.getSpouse()
                : scenario == DeathScenario.SPOUSE_DIES ? household.getPrimaryPerson() : null;
        if (survivor == null || survivor.getBirthDate() == null || deathYear == null || deathYear <= 0) {
            return new Choices(survivor, List.of(), false, null);
        }
        LocalDate deathDate = LocalDate.of(deathYear, 1, 1);
        LocalDate fra = survivorFullRetirementDate(survivor.getBirthDate());
        int ageAtDeath = survivor.getAge(deathDate);
        if (!deathDate.isBefore(fra)) {
            return new Choices(survivor, List.of(ageAtDeath), true, fra);
        }
        int first = Math.max(60, ageAtDeath);
        // The persisted election uses exact birthdays and whole years. Do not invent
        // a fractional election or round a fractional FRA up beyond the benefit ceiling.
        int last = SurvivorFullRetirementAgeCalculator.determine(
                SocialSecurityRetirementDateCalculator.effectiveBirthDate(survivor.getBirthDate())).years();
        return new Choices(survivor, IntStream.rangeClosed(first, last).boxed().toList(), false, fra);
    }

    public static LocalDate claimDate(LocalDate birthDate, LocalDate deathDate, int claimingAge) {
        Objects.requireNonNull(birthDate);
        Objects.requireNonNull(deathDate);
        if (claimingAge < 60) {
            throw new IllegalArgumentException("Survivor Benefit Claiming Age must be at least 60.");
        }
        return claimDate(birthDate, deathDate, birthDate.plusYears(claimingAge));
    }

    /** Exact analyzer elections retain their date precision and remain conditional on death. */
    public static LocalDate claimDate(LocalDate birthDate, LocalDate deathDate, LocalDate election) {
        Objects.requireNonNull(birthDate);
        Objects.requireNonNull(deathDate);
        Objects.requireNonNull(election);
        if (election.isBefore(com.daviddunn.retirementplanner.domain.socialsecurity.analysis
                .SocialSecuritySurvivorBenefitCalculator.calculateEarliestSurvivorClaimDate(birthDate))) {
            throw new IllegalArgumentException("Survivor claim date cannot be before age 60.");
        }
        if (!deathDate.isBefore(survivorFullRetirementDate(birthDate))) {
            return deathDate;
        }
        return election.isAfter(deathDate) ? election : deathDate;
    }

    private static LocalDate survivorFullRetirementDate(LocalDate birthDate) {
        return com.daviddunn.retirementplanner.domain.socialsecurity.analysis
                .SocialSecuritySurvivorBenefitCalculator.calculateSurvivorFullRetirementDate(birthDate);
    }
}
