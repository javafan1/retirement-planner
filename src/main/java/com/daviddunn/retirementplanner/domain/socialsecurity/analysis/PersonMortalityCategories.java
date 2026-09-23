package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.util.Objects;

/** Validates Person inputs before analysis and adapts them to existing table requests. */
public record PersonMortalityCategories(
        SocialSecurityMortalityCategory primary,
        SocialSecurityMortalityCategory spouse) {

    public static PersonMortalityCategories from(Household household) {
        Objects.requireNonNull(household, "Household is required.");
        boolean primaryMissing = missing(household.getPrimaryPerson());
        boolean spouseMissing = missing(household.getSpouse());
        if (primaryMissing || spouseMissing) {
            String people = primaryMissing && spouseMissing ? "Primary and Spouse"
                    : primaryMissing ? "Primary" : "Spouse";
            throw new IllegalArgumentException("Mortality category is required for " + people
                    + " before running longevity analysis. Set the values in Person information.");
        }
        return new PersonMortalityCategories(
                adapt(household.getPrimaryPerson().getMortalityCategory()),
                adapt(household.getSpouse().getMortalityCategory()));
    }

    private static boolean missing(Person person) {
        return person == null || person.getMortalityCategory() == null;
    }

    private static SocialSecurityMortalityCategory adapt(MortalityCategory category) {
        return switch (category) {
            case MALE -> SocialSecurityMortalityCategory.MALE;
            case FEMALE -> SocialSecurityMortalityCategory.FEMALE;
        };
    }
}
