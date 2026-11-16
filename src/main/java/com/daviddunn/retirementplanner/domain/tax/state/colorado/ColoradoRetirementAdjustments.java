package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.*;
import java.util.*;

/**
 * Nonproduction DOR joint-SS worksheet / per-person cap arithmetic only.
 * Caller must independently establish qualifying other receipts. This component
 * does not qualify conversions, early IRA receipts, annuity maturity or death benefits.
 */
public final class ColoradoRetirementAdjustments {
    public record Adjustments(Map<AccountOwnership, BigDecimal> taxableSocialSecurityByRecipient,
            Map<AccountOwnership, BigDecimal> socialSecuritySubtractions,
            Map<AccountOwnership, BigDecimal> otherRetirementSubtractions, BigDecimal totalSubtraction) {
        public Adjustments {
            taxableSocialSecurityByRecipient = Map.copyOf(taxableSocialSecurityByRecipient);
            socialSecuritySubtractions = Map.copyOf(socialSecuritySubtractions);
            otherRetirementSubtractions = Map.copyOf(otherRetirementSubtractions);
        }
    }

    public Adjustments calculate(int year, FilingStatus status, TaxHouseholdContext household, BigDecimal agi,
            BigDecimal taxableSs, Map<AccountOwnership, BigDecimal> grossSs, Map<AccountOwnership, BigDecimal> eligibleOther) {
        ColoradoTaxRules.requireVerifiedYear(year);
        var filers = ColoradoFederalTaxBaseBridge.filers(year, status, household);
        requireMoney(agi); requireMoney(taxableSs);
        for (var map : List.of(grossSs, eligibleOther)) {
            Objects.requireNonNull(map);
            if (!filers.containsAll(map.keySet())) throw new IllegalArgumentException("Income belongs to a nonfiler.");
            for (var entry : map.entrySet()) {
                requireMoney(entry.getValue());
                if (entry.getValue().signum() > 0 && !household.activeOwners().contains(entry.getKey()))
                    throw new IllegalArgumentException("Positive receipts require the active recipient.");
            }
        }
        var gross = grossSs.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (taxableSs.compareTo(gross.multiply(new BigDecimal("0.85"))) > 0 || taxableSs.compareTo(agi) > 0)
            throw new IllegalArgumentException("Taxable Social Security does not reconcile with gross benefits/AGI.");
        var allocated = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var ssSub = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var otherSub = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var remaining = taxableSs;
        var ordered = filers.stream().sorted().toList();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < ordered.size(); i++) {
            var person = ordered.get(i);
            BigDecimal share = gross.signum() == 0 ? BigDecimal.ZERO : i == ordered.size() - 1 ? remaining
                    : taxableSs.multiply(grossSs.getOrDefault(person, BigDecimal.ZERO)).divide(gross, 40, RoundingMode.HALF_UP);
            remaining = remaining.subtract(share);
            allocated.put(person, share);
            int age = ColoradoFederalTaxBaseBridge.ageAtTaxClose(year, household, person);
            BigDecimal cap = age >= 65 ? new BigDecimal("24000") : age >= 55 ? new BigDecimal("20000") : BigDecimal.ZERO;
            boolean fullSs = age >= 65 || age >= 55 && agi.compareTo(new BigDecimal(status == FilingStatus.SINGLE ? "75000" : "95000")) <= 0;
            BigDecimal ss = fullSs ? share : share.min(cap);
            BigDecimal other = eligibleOther.getOrDefault(person, BigDecimal.ZERO).min(cap.subtract(ss).max(BigDecimal.ZERO));
            ssSub.put(person, ss); otherSub.put(person, other);
            total = total.add(ss).add(other);
        }
        return new Adjustments(allocated, ssSub, otherSub, total);
    }

    private static void requireMoney(BigDecimal amount) {
        if (Objects.requireNonNull(amount).signum() < 0) throw new IllegalArgumentException("Negative Colorado worksheet income.");
    }
}
