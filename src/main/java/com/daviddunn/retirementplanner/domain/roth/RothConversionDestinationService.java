package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Applies an enabled conversion request and any required modeled Roth destinations as one plan mutation. */
public final class RothConversionDestinationService {
    private static final RothConversionEligibilityPolicy POLICY = new RothConversionEligibilityPolicy();

    public record ApplyResult(boolean changed, List<AccountOwnership> createdOwners) {
        public ApplyResult {
            createdOwners = List.copyOf(createdOwners);
        }
    }

    /** Owners are returned in the established PRIMARY-then-SPOUSE order. */
    public List<AccountOwnership> ownersRequiringDestination(
            RetirementPlan plan,
            RothConversionRequest request) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(request);
        RothConversionConfiguration.validateTransition(plan, request);
        if (!request.isEnabled()) {
            return List.of();
        }

        int startYear = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        int endYear = Math.addExact(startYear, plan.getPlanningAssumptions().getProjectionLengthYears() - 1);
        int firstScheduledYear = Math.max(startYear, request.getStartYear());
        if (firstScheduledYear > endYear) {
            return List.of();
        }

        Set<AccountOwnership> presentOwners = plan.getHousehold().peopleByOwner().keySet();
        List<Account> accounts = plan.getAccountPortfolio().getAccounts();
        List<AccountOwnership> missing = new ArrayList<>();
        for (AccountOwnership owner : List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE)) {
            if (!presentOwners.contains(owner) || hasDestination(accounts, owner)) {
                continue;
            }
            boolean sourceUsableDuringSchedule = accounts.stream()
                    .filter(account -> account.getOwnership() == owner)
                    .filter(account -> RothConversionEligibilityPolicy.isSupportedPretaxSource(account.getType()))
                    .anyMatch(account -> usableDuringSchedule(account, firstScheduledYear, endYear));
            if (sourceUsableDuringSchedule) {
                missing.add(owner);
            }
        }
        return List.copyOf(missing);
    }

    /** Mutates only after request validation and all destination accounts have been prepared. */
    public ApplyResult apply(RetirementPlan plan, RothConversionRequest request) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(request);
        RothConversionConfiguration.validateTransition(plan, request);

        RothConversionRequest originalRequest = plan.getRothConversionRequest();
        boolean requestChanged = !RothConversionConfiguration.same(originalRequest, request);
        List<AccountOwnership> missingOwners = ownersRequiringDestination(plan, request);
        AccountPortfolio portfolio = plan.getAccountPortfolio();
        List<Account> prepared = new ArrayList<>();
        Set<String> usedNames = new java.util.HashSet<>();
        portfolio.getAccounts().forEach(account -> usedNames.add(normalizedName(account.getName())));
        for (AccountOwnership owner : missingOwners) {
            prepared.add(new RothIRA(uniqueName(owner, usedNames), owner, BigDecimal.ZERO));
        }

        if (prepared.isEmpty() && !requestChanged) {
            return new ApplyResult(false, List.of());
        }

        List<Account> added = new ArrayList<>();
        try {
            for (Account account : prepared) {
                portfolio.addAccount(account);
                added.add(account);
            }
            plan.setRothConversionRequest(request);
        } catch (RuntimeException failure) {
            for (int index = added.size() - 1; index >= 0; index--) {
                portfolio.removeAccount(added.get(index));
            }
            plan.setRothConversionRequest(originalRequest);
            throw failure;
        }
        return new ApplyResult(true, missingOwners);
    }

    private static boolean usableDuringSchedule(Account account, int firstScheduledYear, int endYear) {
        if (!RothConversionEligibilityPolicy.isEmployerPretaxSource(account.getType())) {
            return true;
        }
        var availability = POLICY.availabilityAt(account, firstScheduledYear);
        if (availability != RothConversionEligibilityPolicy.EmployerDistributionAvailability.UNAVAILABLE) {
            return true;
        }
        var explicit = account.getRothConversionAvailability();
        return explicit != null
                && explicit.firstEligibleYear() != null
                && explicit.firstEligibleYear() <= endYear;
    }

    private static boolean hasDestination(List<Account> accounts, AccountOwnership owner) {
        return accounts.stream().anyMatch(account -> account.getOwnership() == owner
                && RothConversionEligibilityPolicy.isSupportedDestination(account.getType()));
    }

    private static String uniqueName(AccountOwnership owner, Set<String> usedNames) {
        String ownerName = owner == AccountOwnership.PRIMARY ? "Primary" : "Spouse";
        String base = ownerName + " Roth IRA (Modeled)";
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(normalizedName(candidate))) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static String normalizedName(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
