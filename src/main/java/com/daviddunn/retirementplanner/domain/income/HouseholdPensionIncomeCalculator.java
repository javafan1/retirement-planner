package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Annual pension cash received under the run-only household lifetime convention. */
public final class HouseholdPensionIncomeCalculator {
    public BigDecimal calculate(Household household, LocalDate date, EffectiveHouseholdDeathView deathView) {
        Objects.requireNonNull(household);
        Objects.requireNonNull(date);
        Objects.requireNonNull(deathView);
        if (deathView.areBothDeceased(date.getYear())) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Person person : List.of(household.getPrimaryPerson(), household.getSpouse())) {
            for (IncomeSource source : person.getIncomeSources()) {
                if (!(source instanceof Pension pension)) {
                    continue;
                }
                AccountOwnership owner = pension.getOwnership();
                if (deathView.isAlive(owner, date.getYear())) {
                    total = total.add(pension.getAnnualIncome(person, date));
                } else if (owner != AccountOwnership.JOINT) {
                    AccountOwnership survivor = owner == AccountOwnership.PRIMARY
                            ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
                    if (deathView.isAlive(survivor, date.getYear())) {
                        total = total.add(pension.getAnnualSurvivorIncome(date));
                    }
                }
            }
        }
        return total;
    }
}
