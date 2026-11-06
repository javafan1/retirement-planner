package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import java.time.LocalDate;
import java.util.*;

/** Detached calendar inputs. The effective death view is itself immutable. */
public record BenefitEvaluationContext(LocalDate primaryBirthDate, Optional<LocalDate> spouseBirthDate,
        EffectiveHouseholdDeathView deaths, LocalDate opening, int year) {
    public BenefitEvaluationContext {
        Objects.requireNonNull(primaryBirthDate); Objects.requireNonNull(spouseBirthDate);
        Objects.requireNonNull(deaths); Objects.requireNonNull(opening);
        if (year < opening.getYear() || year > 9999 || year < 1000)
            throw new IllegalArgumentException("Benefit evaluation year is outside the modeled interval.");
        if (spouseBirthDate.isPresent() != (deaths.state(AccountOwnership.SPOUSE, year) != EffectiveHouseholdDeathView.PersonState.ABSENT))
            throw new IllegalArgumentException("Benefit household membership is inconsistent.");
    }
    public static BenefitEvaluationContext capture(Household household, EffectiveHouseholdDeathView deaths, LocalDate opening, int year) {
        return new BenefitEvaluationContext(household.getPrimaryPerson().getBirthDate(), household.spouse().map(Person::getBirthDate), deaths, opening, year);
    }
    public LocalDate birth(ConditionalBenefitDefinition.PersonReference person) {
        return person == ConditionalBenefitDefinition.PersonReference.PRIMARY ? primaryBirthDate
                : spouseBirthDate.orElseThrow(() -> new IllegalArgumentException("Benefit references an absent spouse."));
    }
}
