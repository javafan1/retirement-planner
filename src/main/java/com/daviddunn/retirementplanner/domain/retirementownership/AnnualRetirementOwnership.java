package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;

import java.time.LocalDate;
import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Membership facts for one projection year, in the original account order. */
public final class AnnualRetirementOwnership {

    private final Year year;
    private final LocalDate projectionOpening;
    private final EffectiveHouseholdDeathView deathView;
    private final Set<AccountOwnership> livingOwners;
    private final Map<AccountOwnership, LocalDate> birthDates;
    private final Map<AccountReference, RetirementOwnershipDisposition> accounts;

    AnnualRetirementOwnership(
            Year year,
            LocalDate projectionOpening,
            EffectiveHouseholdDeathView deathView,
            Set<AccountOwnership> livingOwners,
            Map<AccountOwnership, LocalDate> birthDates,
            Map<AccountReference, RetirementOwnershipDisposition> accounts) {
        this.year = year;
        this.projectionOpening = projectionOpening;
        this.deathView = deathView;
        this.livingOwners = Set.copyOf(livingOwners);
        this.birthDates = Map.copyOf(birthDates);
        this.accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
    }

    public Year year() {
        return year;
    }

    public LocalDate projectionOpening() {
        return projectionOpening;
    }

    public EffectiveHouseholdDeathView deathView() {
        return deathView;
    }

    public Set<AccountOwnership> livingOwners() {
        return livingOwners;
    }

    public Map<AccountOwnership, LocalDate> birthDates() {
        return birthDates;
    }

    public Map<AccountReference, RetirementOwnershipDisposition> accounts() {
        return accounts;
    }
}
