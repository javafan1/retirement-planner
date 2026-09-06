package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generates age 60, whole-year, and exact survivor-FRA filing candidates. */
public final class SocialSecuritySurvivorClaimingCandidateGenerator {

    public List<SocialSecuritySurvivorClaimingCandidate> generate(
            LocalDate birthDate) {
        Objects.requireNonNull(birthDate, "Birth date is required.");
        LocalDate effectiveBirthDate =
                SocialSecurityRetirementDateCalculator.effectiveBirthDate(birthDate);
        LocalDate survivorFra = SocialSecuritySurvivorBenefitCalculator
                .calculateSurvivorFullRetirementDate(birthDate);
        List<LocalDate> dates = new ArrayList<>();
        for (int age = 60; ; age++) {
            LocalDate date = effectiveBirthDate.plusYears(age);
            if (!date.isBefore(survivorFra)) {
                break;
            }
            dates.add(date);
        }
        if (dates.isEmpty() || !dates.getLast().equals(survivorFra)) {
            dates.add(survivorFra);
        }
        return dates.stream()
                .distinct()
                .map(date -> SocialSecuritySurvivorClaimingCandidate.from(
                        effectiveBirthDate, date))
                .toList();
    }
}
