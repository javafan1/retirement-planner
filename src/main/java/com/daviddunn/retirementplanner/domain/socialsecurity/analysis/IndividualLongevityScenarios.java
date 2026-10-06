package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.Household;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/** A conditioned individual distribution, with no joint or survivor state. */
public record IndividualLongevityScenarios(SocialSecurityMortalityDistributionResult individual) {
    public IndividualLongevityScenarios {
        Objects.requireNonNull(individual, "Individual mortality distribution is required.");
        int previous = 0;
        for (var probability : individual.distribution().probabilities()) {
            if (probability.deathAge() <= previous) throw new IllegalArgumentException("Death ages must be ordered and unique.");
            var death = SocialSecurityDeathDateCalculator.calculateDeathDate(individual.request().dateOfBirth(), probability.deathAge());
            if (!death.isAfter(individual.request().mortalityBaseDate())) {
                throw new IllegalArgumentException("Individual death outcomes must follow mortality conditioning.");
            }
            previous = probability.deathAge();
        }
    }

    public static IndividualLongevityScenarios create(Household household,
            SocialSecurityMortalityAdjustment adjustment, LocalDate conditioning,
            SocialSecurityMortalityTable table) {
        Objects.requireNonNull(household);
        if (household.hasSpouse()) throw new IllegalArgumentException("Individual household analysis requires no spouse.");
        var person = household.getPrimaryPerson();
        if (person.getMortalityCategory() == null) {
            throw new IllegalArgumentException("Primary mortality category is required. Set it in Person information.");
        }
        var category = switch (person.getMortalityCategory()) {
            case MALE -> SocialSecurityMortalityCategory.MALE;
            case FEMALE -> SocialSecurityMortalityCategory.FEMALE;
        };
        return new IndividualLongevityScenarios(new SocialSecurityMortalityDistributionProvider(table)
                .createDistribution(new SocialSecurityMortalityDistributionRequest(
                        person.getBirthDate(), conditioning, category, adjustment)));
    }

    public record DeathScenario(int deathAge, LocalDate deathDate, BigDecimal probability) { }

    public List<DeathScenario> scenarios() {
        return individual.distribution().probabilities().stream().map(p -> new DeathScenario(p.deathAge(),
                SocialSecurityDeathDateCalculator.calculateDeathDate(individual.request().dateOfBirth(), p.deathAge()),
                p.probability())).toList();
    }

    /** Birthday death timing; probability conditional on the captured conditioning date. */
    public BigDecimal survivalAt(LocalDate date) {
        if (date.isBefore(individual.request().mortalityBaseDate())) {
            throw new IllegalArgumentException("Survival date precedes mortality conditioning.");
        }
        return scenarios().stream().filter(s -> date.isBefore(s.deathDate()))
                .map(DeathScenario::probability).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** SS excludes the whole death month, matching the existing monthly benefit engine. */
    public BigDecimal probabilityOfReceipt(YearMonth month) {
        return scenarios().stream().filter(s -> month.isBefore(YearMonth.from(s.deathDate())))
                .map(DeathScenario::probability).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
