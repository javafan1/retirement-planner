package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public final class HouseholdSocialSecurityIncomeCalculator {

    public BigDecimal calculateAnnualIncome(
            Household household,
            LocalDate projectionDate,
            DeathScenarioAssumptions deathAssumptions,
            BigDecimal socialSecurityColaRate) {

        return calculate(
                household,
                projectionDate,
                deathAssumptions,
                socialSecurityColaRate)
                .householdBenefit();
    }

    public HouseholdSocialSecurityResult calculate(
            Household household,
            LocalDate projectionDate,
            DeathScenarioAssumptions deathAssumptions,
            BigDecimal socialSecurityColaRate) {

        Objects.requireNonNull(household);
        Objects.requireNonNull(projectionDate);
        Objects.requireNonNull(deathAssumptions);
        Objects.requireNonNull(socialSecurityColaRate);

        if (deathAssumptions.getDeathScenario()
                == DeathScenario.BOTH_SURVIVE
                || !deathAssumptions.isDeathScenarioActive(
                projectionDate.getYear())) {

            BigDecimal primaryOwn = activeAnnualIncome(
                    household.getPrimaryPerson(),
                    projectionDate,
                    socialSecurityColaRate);

            BigDecimal spouseOwn = activeAnnualIncome(
                            household.getSpouse(),
                            projectionDate,
                            socialSecurityColaRate);

            return new HouseholdSocialSecurityResult(
                    primaryOwn,
                    spouseOwn,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    selectionForOwn(primaryOwn),
                    selectionForOwn(spouseOwn),
                    primaryOwn.add(spouseOwn));
        }

        Person deceased = deathAssumptions.getDeathScenario()
                == DeathScenario.PRIMARY_DIES
                ? household.getPrimaryPerson()
                : household.getSpouse();

        Person survivor = deceased == household.getPrimaryPerson()
                ? household.getSpouse()
                : household.getPrimaryPerson();

        BigDecimal ownAnnualBenefit = activeAnnualIncome(
                survivor,
                projectionDate,
                socialSecurityColaRate);

        BigDecimal survivorAnnualBenefit = survivorAnnualBenefit(
                deceased,
                survivor,
                projectionDate,
                deathAssumptions,
                socialSecurityColaRate);

        BigDecimal householdBenefit = ownAnnualBenefit.max(
                survivorAnnualBenefit);

        SocialSecurityBenefitSelection survivorSelection =
                selectionForSurvivor(
                        ownAnnualBenefit,
                        survivorAnnualBenefit);

        if (survivor == household.getPrimaryPerson()) {
            return new HouseholdSocialSecurityResult(
                    ownAnnualBenefit,
                    BigDecimal.ZERO,
                    survivorAnnualBenefit,
                    BigDecimal.ZERO,
                    survivorSelection,
                    SocialSecurityBenefitSelection.NONE,
                    householdBenefit);
        }

        return new HouseholdSocialSecurityResult(
                BigDecimal.ZERO,
                ownAnnualBenefit,
                BigDecimal.ZERO,
                survivorAnnualBenefit,
                SocialSecurityBenefitSelection.NONE,
                survivorSelection,
                householdBenefit);
    }

    private SocialSecurityBenefitSelection selectionForOwn(
            BigDecimal ownBenefit) {

        return ownBenefit.signum() > 0
                ? SocialSecurityBenefitSelection.OWN
                : SocialSecurityBenefitSelection.NONE;
    }

    private SocialSecurityBenefitSelection selectionForSurvivor(
            BigDecimal ownBenefit,
            BigDecimal survivorBenefit) {

        if (ownBenefit.signum() == 0
                && survivorBenefit.signum() == 0) {
            return SocialSecurityBenefitSelection.NONE;
        }

        return survivorBenefit.compareTo(ownBenefit) > 0
                ? SocialSecurityBenefitSelection.SURVIVOR
                : SocialSecurityBenefitSelection.OWN;
    }

    private LocalDate modeledDeathDate(
            DeathScenarioAssumptions deathAssumptions) {

        return LocalDate.of(
                deathAssumptions.getDeathYear(),
                1,
                1);
    }

    private BigDecimal activeAnnualIncome(
            Person person,
            LocalDate projectionDate,
            BigDecimal socialSecurityColaRate) {

        return person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .map(income -> income.getAnnualIncome(
                        person,
                        projectionDate,
                        socialSecurityColaRate))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal survivorAnnualBenefit(
            Person deceased,
            Person survivor,
            LocalDate projectionDate,
            DeathScenarioAssumptions deathAssumptions,
            BigDecimal socialSecurityColaRate) {

        if (survivor.getAge(projectionDate)
                < deathAssumptions.getSurvivorClaimingAge()) {
            return BigDecimal.ZERO;
        }

        BigDecimal monthlyBenefit = deceased.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .map(income -> SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                income.getProjectedSurvivorBenefitBase(
                                        deceased,
                                        modeledDeathDate(
                                                deathAssumptions),
                                        projectionDate,
                                        socialSecurityColaRate),
                                survivor.getBirthDate(),
                                deathAssumptions
                                        .getSurvivorClaimingAge()))
                .reduce(BigDecimal.ZERO, BigDecimal::max);

        return monthlyBenefit
                .multiply(BigDecimal.valueOf(12))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
