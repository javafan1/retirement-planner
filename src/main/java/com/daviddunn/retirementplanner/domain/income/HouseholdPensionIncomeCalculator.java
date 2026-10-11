package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** Annual pension cash received under the run-only household lifetime convention. */
public final class HouseholdPensionIncomeCalculator {
    public BigDecimal calculate(Household household, LocalDate date, EffectiveHouseholdDeathView deathView) {
        return calculateWithSources(household, date, deathView).total();
    }

    public PensionIncomeResult calculateWithSources(
            Household household, LocalDate date, EffectiveHouseholdDeathView deathView) {
        Objects.requireNonNull(household);
        Objects.requireNonNull(date);
        Objects.requireNonNull(deathView);
        if (deathView.isHouseholdDeceased(date.getYear())) {
            return new PensionIncomeResult(BigDecimal.ZERO, List.of());
        }
        BigDecimal total = BigDecimal.ZERO;
        List<IncomeSourceAttribution> sources = new ArrayList<>();
        for (Person person : household.members()) {
            for (IncomeSource source : person.getIncomeSources()) {
                if (!(source instanceof Pension pension)) {
                    continue;
                }
                AccountOwnership owner = pension.getOwnership();
                if (!household.hasSpouse() && (owner != AccountOwnership.PRIMARY
                        || pension.getSurvivorMonthlyBenefit() != null && pension.getSurvivorMonthlyBenefit().signum() > 0)) {
                    throw new IllegalArgumentException("Single-person pension cannot require a spouse.");
                }
                if (deathView.isAlive(owner, date.getYear())) {
                    BigDecimal amount = pension.getAnnualIncome(person, date);
                    total = total.add(amount);
                    if (amount.signum() > 0) {
                        sources.add(IncomeSourceAttribution.pension(pension, owner, amount, date.getYear()));
                    }
                } else if (owner != AccountOwnership.JOINT) {
                    AccountOwnership survivor = owner == AccountOwnership.PRIMARY
                            ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
                    if (deathView.isAlive(survivor, date.getYear())) {
                        BigDecimal amount = pension.getAnnualSurvivorIncome(date);
                        total = total.add(amount);
                        if (amount.signum() > 0) {
                            sources.add(IncomeSourceAttribution.pension(pension, survivor, amount, date.getYear()));
                        }
                    }
                }
            }
        }
        return new PensionIncomeResult(total, sources);
    }
    public record PensionIncomeResult(BigDecimal total, List<IncomeSourceAttribution> sources) {
        public PensionIncomeResult {
            Objects.requireNonNull(total);
            sources = List.copyOf(sources);
        }
    }
}
