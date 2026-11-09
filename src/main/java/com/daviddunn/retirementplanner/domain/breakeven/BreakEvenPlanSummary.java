package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.socialsecurity.SocialSecurityBenefitReductionPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.Period;

/** Immutable metadata captured with a projection, not read from live UI assumptions. */
public final class BreakEvenPlanSummary {
    private final PersonSummary primary;
    private final PersonSummary spouse;
    @JsonIgnore
    private final SocialSecurityBenefitReductionPolicy benefitReductionPolicy;
    @JsonIgnore
    private final com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode calculationMode;

    /** Original persisted two-property contract; older strict snapshot decoders remain compatible. */
    @JsonCreator
    public BreakEvenPlanSummary(@JsonProperty("primary") PersonSummary primary,
            @JsonProperty("spouse") PersonSummary spouse) {
        this(primary, spouse, null);
    }

    /** In-memory comparison metadata; policy provenance is persisted in the frozen plan inputs. */
    public BreakEvenPlanSummary(PersonSummary primary, PersonSummary spouse,
            SocialSecurityBenefitReductionPolicy benefitReductionPolicy) {
        this(primary, spouse, benefitReductionPolicy, null);
    }

    private BreakEvenPlanSummary(PersonSummary primary, PersonSummary spouse,
            SocialSecurityBenefitReductionPolicy benefitReductionPolicy,
            com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode calculationMode) {
        this.primary = java.util.Objects.requireNonNull(primary);
        this.spouse = spouse;
        this.benefitReductionPolicy = benefitReductionPolicy;
        this.calculationMode = calculationMode;
    }

    @JsonProperty("primary")
    public PersonSummary primary() { return primary; }

    @JsonProperty("spouse")
    public PersonSummary spouse() { return spouse; }

    @JsonIgnore
    public SocialSecurityBenefitReductionPolicy benefitReductionPolicy() { return benefitReductionPolicy; }

    /** Presentation derived from completed annual policy evidence, never serialized as old summary facts. */
    @JsonIgnore
    public com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode calculationMode() { return calculationMode; }

    public BreakEvenPlanSummary withCalculationMode(com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode mode) {
        return new BreakEvenPlanSummary(primary, spouse, benefitReductionPolicy, java.util.Objects.requireNonNull(mode));
    }

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
        return from(household, null);
    }

    public static BreakEvenPlanSummary from(RetirementPlan plan) {
        java.util.Objects.requireNonNull(plan);
        return from(plan.getHousehold(), plan.getPlanningAssumptions().getEconomicAssumptions());
    }

    public static BreakEvenPlanSummary from(RetirementPlanSnapshot plan) {
        java.util.Objects.requireNonNull(plan);
        return from(plan.getHousehold(), plan.getPlanningAssumptions().getEconomicAssumptions());
    }

    public static BreakEvenPlanSummary from(Household household, EconomicAssumptions economicAssumptions) {
        PersonSummary primary = person(household.getPrimaryPerson(), "Primary");
        if (!household.hasSpouse() && primary.birthDate() != null && primary.retirementClaimingAge() != null) {
            primary = new PersonSummary(primary.name(), primary.birthDate(), primary.retirementClaimingAge(),
                    com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator
                            .calculateRetirementClaimDate(primary.birthDate(), primary.retirementClaimingAge()), primary.mortalityCategory());
        }
        return new BreakEvenPlanSummary(primary,
                household.spouse().map(spouse -> person(spouse, "Spouse")).orElse(null),
                economicAssumptions == null ? null : new SocialSecurityBenefitReductionPolicy(
                        economicAssumptions.getSocialSecurityBenefitReductionPercent(),
                        economicAssumptions.getSocialSecurityBenefitReductionEffectiveYear()));
    }

    public BreakEvenPlanSummary withBenefitReductionPolicy(SocialSecurityBenefitReductionPolicy policy) {
        return new BreakEvenPlanSummary(primary, spouse, java.util.Objects.requireNonNull(policy), calculationMode);
    }

    public boolean hasSpouse() { return spouse != null; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BreakEvenPlanSummary that)) return false;
        return java.util.Objects.equals(primary, that.primary)
                && java.util.Objects.equals(spouse, that.spouse);
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(primary, spouse); }

    @Override
    public String toString() {
        return "BreakEvenPlanSummary[primary=" + primary + ", spouse=" + spouse
                + ", benefitReductionPolicy=" + benefitReductionPolicy + "]";
    }

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
