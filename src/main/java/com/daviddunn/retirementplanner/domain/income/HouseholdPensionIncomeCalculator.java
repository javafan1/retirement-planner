package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** Authoritative annual pension cash and tax sources under the effective household death state. */
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
                var receipt = annualReceipt(pension, person, date, deathView);
                total = total.add(receipt.amount());
                if (receipt.amount().signum() > 0) {
                    sources.add(IncomeSourceAttribution.pension(pension, receipt.recipient(), receipt.amount(), date.getYear()));
                }
            }
        }
        return new PensionIncomeResult(total, sources);
    }

    /** Applies the same owner/survivor selection and payment calendar used by the projection. */
    public static AnnualReceipt annualReceipt(
            Pension pension, Person person, LocalDate date, EffectiveHouseholdDeathView deathView) {
        AccountOwnership owner = pension.getOwnership();
        if (deathView.isAlive(owner, date.getYear())) {
            return new AnnualReceipt(owner, pension.getAnnualIncome(person, date));
        }
        if (owner != AccountOwnership.JOINT) {
            AccountOwnership survivor = owner == AccountOwnership.PRIMARY
                    ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
            if (deathView.isAlive(survivor, date.getYear())) {
                return new AnnualReceipt(survivor, pension.getAnnualSurvivorIncome(date));
            }
        }
        return new AnnualReceipt(owner, BigDecimal.ZERO);
    }

    public record AnnualReceipt(AccountOwnership recipient, BigDecimal amount) { }

    public record PensionIncomeResult(BigDecimal total, List<IncomeSourceAttribution> sources) {
        public PensionIncomeResult {
            Objects.requireNonNull(total);
            sources = List.copyOf(sources);
        }
    }
}
