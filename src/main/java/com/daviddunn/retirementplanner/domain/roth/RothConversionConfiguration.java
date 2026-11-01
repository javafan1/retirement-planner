package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.model.*;
import java.math.BigDecimal;
import java.util.*;

/** Shared read-only configuration assessment and transition admission for both UI surfaces. */
public final class RothConversionConfiguration {
    private static final RothConversionEligibilityPolicy POLICY = new RothConversionEligibilityPolicy();

    public record Source(AccountOwnership owner, boolean ownerPresent, BigDecimal openingBalance,
            RothConversionEligibilityPolicy.EmployerDistributionAvailability availability,
            Integer firstEligibleYear, boolean destinationReady) { }
    public record Assessment(boolean hasSupportedSource, boolean canEnable, List<Source> sources, String guidance) {
        public Assessment { sources = List.copyOf(sources); }
    }

    public static Assessment assess(RetirementPlan plan) {
        if (plan == null) return new Assessment(false, false, List.of(), "Open a retirement plan to configure conversions.");
        var owners = plan.getHousehold().peopleByOwner().keySet();
        var accounts = plan.getAccountPortfolio().getAccounts();
        int year = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        var sources = new ArrayList<Source>();
        var messages = new LinkedHashSet<String>();
        boolean canEnable = false;
        for (var source : accounts) {
            if (!RothConversionEligibilityPolicy.isSupportedPretaxSource(source.getType())) continue;
            boolean present = owners.contains(source.getOwnership());
            boolean destination = accounts.stream().anyMatch(a -> a.getOwnership() == source.getOwnership()
                    && RothConversionEligibilityPolicy.isSupportedDestination(a.getType()));
            var availability = POLICY.availabilityAt(source, year);
            var explicit = source.getRothConversionAvailability();
            Integer first = explicit == null ? null : explicit.firstEligibleYear();
            sources.add(new Source(source.getOwnership(), present, source.getCurrentBalance(), availability, first, destination));
            String owner = source.getOwnership() == AccountOwnership.PRIMARY ? "Primary" : "Spouse";
            if (!present) { messages.add("A source account belongs to an owner absent from this household."); continue; }
            canEnable |= availability != RothConversionEligibilityPolicy.EmployerDistributionAvailability.UNAVAILABLE || first != null;
            if (source.getCurrentBalance().signum() <= 0)
                messages.add(owner + " has a supported source with no opening funds; future scheduling remains possible.");
            if (!destination) messages.add(owner + ": no Roth IRA destination is configured. Add a same-owner Roth IRA to execute conversions.");
            if (availability == RothConversionEligibilityPolicy.EmployerDistributionAvailability.UNKNOWN)
                messages.add("Employer distribution availability is unknown. Legacy modeling assumes availability; confirm the employer plan's rules.");
            if (first != null) messages.add(owner + " employer account is modeled as available beginning January 1, " + first + ".");
            else if (availability == RothConversionEligibilityPolicy.EmployerDistributionAvailability.UNAVAILABLE)
                messages.add(owner + " employer account is unavailable throughout the modeled horizon.");
        }
        if (sources.stream().noneMatch(Source::ownerPresent)) messages.add(
                "No eligible pretax retirement account is configured. Add a Traditional IRA, Rollover IRA, or supported traditional employer retirement account to enable Roth conversions.");
        if (!canEnable && plan.getRothConversionRequest() != null && plan.getRothConversionRequest().isEnabled())
            messages.add("The saved schedule is preserved but cannot currently execute. You may explicitly disable it.");
        if (messages.isEmpty()) messages.add("Supported source and same-owner Roth IRA destination configured. Actual conversion depends on available projected funds.");
        return new Assessment(!sources.isEmpty(), canEnable, sources, String.join("\n", messages));
    }

    public static void validateTransition(RetirementPlan plan, RothConversionRequest proposed) {
        if (proposed == null || !proposed.isEnabled() || assess(plan).canEnable()) return;
        if (!same(plan.getRothConversionRequest(), proposed))
            throw new IllegalArgumentException("Roth conversions cannot be newly enabled or edited without a supported present-owner source available now or in a future year. The saved schedule remains unchanged.");
    }

    public static RothConversionRequest disabled(RothConversionRequest saved) {
        return saved == null ? null : new RothConversionRequest(false, saved.getStartYear(), saved.getAnnualAmount(),
                saved.getStopRule(), saved.getStrategy(), saved.getFrequency(), saved.getCustomTargetTaxableIncome());
    }

    public static boolean same(RothConversionRequest a, RothConversionRequest b) {
        if (a == null || b == null) return a == b;
        return a.isEnabled() == b.isEnabled() && a.getStartYear() == b.getStartYear()
                && decimalEquals(a.getAnnualAmount(), b.getAnnualAmount()) && a.getStopRule() == b.getStopRule()
                && a.getStrategy() == b.getStrategy() && a.getFrequency() == b.getFrequency()
                && decimalEquals(a.getCustomTargetTaxableIncome(), b.getCustomTargetTaxableIncome());
    }

    private static boolean decimalEquals(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }
}
