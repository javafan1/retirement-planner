package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator.PensionIncomeResult;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable run-only household tax facts; never owns or edits a plan or person. */
public record TaxHouseholdContext(
        LocalDate distributionDate,
        Map<AccountOwnership, LocalDate> birthDates,
        Set<AccountOwnership> householdOwners,
        Set<AccountOwnership> activeOwners,
        Optional<PensionIncomeResult> pensionIncome) {

    public TaxHouseholdContext {
        Objects.requireNonNull(distributionDate);
        birthDates = Map.copyOf(birthDates);
        householdOwners = Set.copyOf(householdOwners);
        activeOwners = Set.copyOf(activeOwners);
        Objects.requireNonNull(pensionIncome);
        if (householdOwners.contains(AccountOwnership.JOINT)
                || !householdOwners.containsAll(birthDates.keySet())
                || !householdOwners.containsAll(activeOwners)) {
            throw new IllegalArgumentException("Tax person context must reference actual household members.");
        }
    }

    public static TaxHouseholdContext capture(
            Household household, LocalDate date, DeathScenarioAssumptions deaths) {
        Objects.requireNonNull(household);
        var births = new EnumMap<AccountOwnership, LocalDate>(AccountOwnership.class);
        household.peopleByOwner().forEach((owner, person) -> {
            if (person.getBirthDate() != null) {
                births.put(owner, person.getBirthDate());
            }
        });
        Set<AccountOwnership> owners = household.peopleByOwner().keySet();
        var view = deaths == null ? null : EffectiveHouseholdDeathView.resolve(household, deaths, Optional.empty());
        Set<AccountOwnership> active = view == null ? owners : owners.stream()
                .filter(owner -> view.isAlive(owner, date.getYear())).collect(Collectors.toUnmodifiableSet());
        Optional<PensionIncomeResult> pensions = deaths != null && deaths.getDeathScenario() != DeathScenario.BOTH_SURVIVE
                ? Optional.of(new HouseholdPensionIncomeCalculator().calculateWithSources(household, date, view))
                : Optional.empty();
        return new TaxHouseholdContext(date, births, owners, active, pensions);
    }

    public TaxHouseholdContext withActiveOwners(Set<AccountOwnership> owners) {
        return new TaxHouseholdContext(distributionDate, birthDates, householdOwners, owners, pensionIncome);
    }

    public TaxHouseholdContext withPensionIncome(Optional<PensionIncomeResult> income) {
        return new TaxHouseholdContext(distributionDate, birthDates, householdOwners, activeOwners, income);
    }
}
