package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver;
import com.daviddunn.retirementplanner.domain.retirementownership.Spousal401kRollover;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdMembership;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import com.daviddunn.retirementplanner.domain.rules.RmdRules;
import com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy;

import java.time.LocalDate;
import java.time.Year;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable.Reason.*;

/** Admission and annual binding for the explicitly requested Florida Basic deterministic route. */
final class SpousalIraRmdPolicy {
    enum Treatment { OWNER_IRA, OWNER_ROLLOVER, BENEFICIARY_IRA, BENEFICIARY_401K }
    private final LocalDate opening;
    private final LocalDate death;
    private final EffectiveHouseholdDeathView deaths;
    private final AccountOwnership deceased;
    private final AccountOwnership survivor;
    private final Map<AccountOwnership, LocalDate> births;
    private final List<AccountReference> accounts;
    private final Set<AccountReference> cleared;
    private final SpousalInheritanceAssumption selection;
    private final RmdRules rules;
    private final int survivorStartingAge;
    private final int decedentFirstDistributionYear;
    private final boolean employerRollovers;
    private final boolean beneficiaryTreatment;
    private final boolean beneficiary401k;

    static boolean requested(RetirementPlan plan) {
        return requested(plan, AccountType.TRADITIONAL_IRA, SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER);
    }

    static boolean rolloverRequested(RetirementPlan plan) {
        return requested(plan, AccountType.TRADITIONAL_401K, SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER);
    }

    static boolean beneficiaryRequested(RetirementPlan plan) {
        return requested(plan, AccountType.TRADITIONAL_IRA, SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY);
    }

    static boolean beneficiary401kRequested(RetirementPlan plan) {
        return requested(plan, AccountType.TRADITIONAL_401K, SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY);
    }

    private static boolean requested(RetirementPlan plan, AccountType sourceType,
            SpousalInheritanceAssumption.Treatment selected) {
        var assumptions = plan.getPlanningAssumptions();
        var choice = assumptions.getEconomicAssumptions().getSpousalInheritance();
        if (choice == null || choice.treatment() != selected
                || assumptions.getStateTaxAssumptions().jurisdiction() != StateTaxJurisdiction.FL
                || FinancialCalculationPolicy.resolved(plan) != FinancialCalculationMode.BASIC
                || !plan.getHousehold().hasSpouse()) return false;
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), assumptions.getDeathScenarioAssumptions(),
                Optional.empty());
        int lastYear = assumptions.getProjectionStartDate().getYear() + assumptions.getProjectionLengthYears() - 1;
        return deaths.firstDeathYear().filter(y -> y.getValue() < lastYear).isPresent()
                && plan.getAccountPortfolio().getAccounts().stream().anyMatch(account ->
                    account.getType() == sourceType
                            && (sourceType != AccountType.TRADITIONAL_401K
                                || selected == SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY
                                || account.getCurrentBalance().signum() > 0)
                            && !deaths.isAlive(account.getOwnership(), lastYear));
    }

    SpousalIraRmdPolicy(RetirementPlan plan, RmdRules rules) {
        this(plan, rules, false);
    }

    SpousalIraRmdPolicy(RetirementPlan plan, RmdRules rules, boolean employerRollovers) {
        this(plan, rules, employerRollovers ? Treatment.OWNER_ROLLOVER : Treatment.OWNER_IRA);
    }

    SpousalIraRmdPolicy(RetirementPlan plan, RmdRules rules, Treatment treatment) {
        this.rules = rules;
        employerRollovers = treatment == Treatment.OWNER_ROLLOVER;
        beneficiary401k = treatment == Treatment.BENEFICIARY_401K;
        beneficiaryTreatment = treatment == Treatment.BENEFICIARY_IRA || beneficiary401k;
        var assumptions = plan.getPlanningAssumptions();
        opening = assumptions.getProjectionStartDate();
        boolean requested = switch (treatment) {
            case OWNER_IRA -> requested(plan);
            case OWNER_ROLLOVER -> rolloverRequested(plan);
            case BENEFICIARY_IRA -> beneficiaryRequested(plan);
            case BENEFICIARY_401K -> beneficiary401kRequested(plan);
        };
        if (!requested)
            throw new SpousalIraRmdUnavailable(opening.getYear(), UNSUPPORTED_POLICY_OR_ACCOUNT,
                "Applied spousal retirement treatment requires an eligible Florida Basic deterministic request.");
        selection = assumptions.getEconomicAssumptions().getSpousalInheritance();
        deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), assumptions.getDeathScenarioAssumptions(),
                Optional.empty());
        deceased = deaths.deathDate(AccountOwnership.PRIMARY).isPresent() ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        survivor = deceased == AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
        death = deaths.deathDate(deceased).orElseThrow();
        if (death.isBefore(opening)) throw refusal(death.getYear(), INVALID_OWNERSHIP_CHRONOLOGY,
                "First death must be within the modeled period.");
        if (beneficiaryTreatment && death.getYear() < 2025) {
            throw refusal(death.getYear(), INVALID_OWNERSHIP_CHRONOLOGY,
                    "This beneficiary method applies to modeled deaths from 2025 onward.");
        }
        var birthDates = new EnumMap<AccountOwnership, LocalDate>(AccountOwnership.class);
        plan.getHousehold().peopleByOwner().forEach((owner, person) -> {
            var birth = person.getBirthDate();
            if (birth == null || !birth.isBefore(death) || birth.isAfter(opening) || birth.getYear() < 1960) {
                throw refusal(death.getYear(), INVALID_OWNERSHIP_CHRONOLOGY,
                        "This policy requires supported birth dates in the 1960-or-later cohort.");
            }
            birthDates.put(owner, birth);
        });
        births = Map.copyOf(birthDates);
        var decedentBirth = births.get(deceased);
        survivorStartingAge = startingAge(births.get(survivor));
        decedentFirstDistributionYear = decedentBirth.getYear() + startingAge(decedentBirth);
        if (death.getYear() >= decedentFirstDistributionYear) {
            throw refusal(death.getYear(), UNRESOLVED_MANDATORY_DISTRIBUTION,
                    "Death was in or after the deceased spouse's earliest applicable owner distribution year.");
        }
        accounts = plan.getAccountPortfolio().getAccounts().stream().map(AccountReference::capture).toList();
        if (new HashSet<>(accounts).size() != accounts.size()) throw refusal(death.getYear(),
                MISSING_OR_DUPLICATE_SOURCE, "The original portfolio contains a duplicate position.");
        var clearedAccounts = new HashSet<AccountReference>();
        for (var account : plan.getAccountPortfolio().getAccounts()) {
            if ((account.getType() == AccountType.INHERITED_TRADITIONAL_IRA || account.getType() == AccountType.INHERITED_ROTH_IRA)
                    && account.getCurrentBalance().signum() > 0) {
                throw refusal(opening.getYear(), UNSUPPORTED_POLICY_OR_ACCOUNT,
                        "The applied projection has an existing inherited-account obligation.");
            }
            if (account.getOwnership() != deceased) continue;
            boolean ira = account.getType() == AccountType.TRADITIONAL_IRA;
            boolean employer = (employerRollovers || beneficiary401k) && account.getType() == AccountType.TRADITIONAL_401K;
            if (!ira && !employer) continue;
            var openingRmd = account.getOpeningRmdAccountData();
            if (openingRmd != null && (openingRmd.getDistributionYear() != opening.getYear()
                    || openingRmd.getRmdAlreadyDistributedBeforeProjection().signum() != 0)) {
                throw refusal(death.getYear(), INCOMPATIBLE_ACCOUNT_HISTORY,
                        "The decedent's opening RMD data conflict with the pre-RMD death model.");
            }
            if (beneficiary401k && employer) {
                // The pre-first-distribution-year gate precedes even the earliest age-based April 1 RBD.
                var facts = AccountReference.capture(account).participantFacts();
                if ((facts.separationDate() != null && facts.separationDate().isAfter(death))
                        || (facts.retirementConditionsMetDate() != null && facts.retirementConditionsMetDate().isAfter(death))) {
                    throw refusal(death.getYear(), INCOMPATIBLE_ACCOUNT_HISTORY,
                            "Dated employer facts conflict with the participant's death.");
                }
            }
            if (ira || beneficiary401k && employer) clearedAccounts.add(AccountReference.capture(account));
        }
        cleared = Set.copyOf(clearedAccounts);
    }

    boolean inSurvivorPeriod(int year) { return year >= death.getYear(); }

    SpousalIraRmdMembership membership(int year, ProjectedPortfolio portfolio, RmdBalanceSnapshot prior) {
        return membership(year, portfolio, prior, Map.of());
    }

    Map<AccountReference, Spousal401kRollover> rolloversAtOpening(
            int year, ProjectedPortfolio portfolio, RmdBalanceSnapshot prior) {
        if (!employerRollovers || year != death.getYear() + 1) return Map.of();
        if (prior == null || !prior.getSnapshotDate().equals(death.withDayOfYear(death.lengthOfYear()))) {
            throw refusal(year, MISSING_OR_DUPLICATE_SOURCE, "The original 401(k) December 31 balance is required.");
        }
        var priorBySource = new HashMap<AccountReference, java.math.BigDecimal>();
        for (var position : prior.getAccountBalances()) {
            if (priorBySource.putIfAbsent(AccountReference.capture(position.getAccount()), position.getBalance()) != null) {
                throw refusal(year, MISSING_OR_DUPLICATE_SOURCE, "The prior balance contains a duplicate account occurrence.");
            }
        }
        var result = new LinkedHashMap<AccountReference, Spousal401kRollover>();
        for (var position : portfolio.getAccountBalances()) {
            var account = position.getAccount();
            if (account.getOwnership() != deceased || account.getType() != AccountType.TRADITIONAL_401K) continue;
            var source = AccountReference.capture(account);
            var closing = priorBySource.remove(source);
            if (closing == null) throw refusal(year, MISSING_OR_DUPLICATE_SOURCE, "An original 401(k) occurrence is missing or duplicated.");
            if (closing.compareTo(position.getBalance()) != 0) {
                throw refusal(year, INCOMPATIBLE_ACCOUNT_HISTORY, "The 401(k) opening balance differs from the preceding closing balance.");
            }
            if (closing.signum() == 0) continue;
            if (death.getYear() < 2025) {
                throw refusal(year, INVALID_OWNERSHIP_CHRONOLOGY,
                        "The modeled death precedes the applicable final employer RMD rules.");
            }
            // The distribution and receipt are both January 1. The modeled sole spouse uses the default
            // life-expectancy method until then. A beneficiary RMD for the rollover year is ineligible,
            // including when its year-end deadline has not passed.
            if (year >= decedentFirstDistributionYear) {
                throw refusal(year, UNRESOLVED_MANDATORY_DISTRIBUTION,
                        "A 401(k) beneficiary distribution is required in the rollover year and must be excluded first.");
            }
            RetirementParticipantFacts facts = source.participantFacts();
            if (facts.separationDate() != null && facts.separationDate().isAfter(death)
                    || facts.retirementConditionsMetDate() != null && facts.retirementConditionsMetDate().isAfter(death)) {
                throw refusal(year, INCOMPATIBLE_ACCOUNT_HISTORY, "Dated employer facts conflict with the participant's death.");
            }
            result.put(source, new Spousal401kRollover(source, survivor, death.plusYears(1), closing));
        }
        if (priorBySource.keySet().stream().anyMatch(ref -> ref.owner() == deceased
                && ref.type() == AccountType.TRADITIONAL_401K)) {
            throw refusal(year, MISSING_OR_DUPLICATE_SOURCE, "A prior 401(k) balance does not identify an opening position.");
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    SpousalIraRmdMembership membership(int year, ProjectedPortfolio portfolio, RmdBalanceSnapshot prior,
            Map<AccountReference, Spousal401kRollover> rollovers) {
        if (!inSurvivorPeriod(year)) return null;
        for (var position : portfolio.getAccountBalances()) {
            var account = position.getAccount();
            var type = account.getType();
            if (position.getBalance().signum() == 0 || !type.requiresIndividualOwnership()) continue;
            if (type == AccountType.INHERITED_TRADITIONAL_IRA || type == AccountType.INHERITED_ROTH_IRA
                    || account.getOwnership() == survivor && type == AccountType.ROLLOVER_IRA
                    || account.getOwnership() == deceased && type != AccountType.TRADITIONAL_IRA
                        && !(type == AccountType.TRADITIONAL_401K && (beneficiary401k
                            || employerRollovers && (year == death.getYear()
                                || rollovers.containsKey(AccountReference.capture(account)))))) {
                throw refusal(year, UNSUPPORTED_POLICY_OR_ACCOUNT,
                        "An unresolved inherited or non-personal-IRA obligation requires separate treatment.");
            }
        }
        if (year == death.getYear()) return null;
        var survivorBirth = births.get(survivor);
        int age = year - survivorBirth.getYear();
        if (age >= survivorStartingAge) {
            try {
                rules.getLifeExpectancyFactor(age);
            } catch (IllegalArgumentException unsupported) {
                throw refusal(year, UNSUPPORTED_POLICY_OR_ACCOUNT, "The survivor's Uniform Lifetime divisor is unavailable.");
            }
        }
        var ownership = ownership(year, rollovers);
        return new SpousalIraRmdMembership(ownership, survivor, prior, portfolio, rollovers,
                beneficiaryTreatment ? Math.max(death.getYear() + 1, decedentFirstDistributionYear) : null);
    }

    com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership ownership(int year) {
        return ownership(year, Map.of());
    }

    com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership ownership(int year,
            Map<AccountReference, Spousal401kRollover> rollovers) {
        var ownership = beneficiaryTreatment
                ? RetirementOwnershipResolver.resolveForBeneficiaryPlanning(Year.of(year), opening, deaths, births, rules,
                        accounts, selection, cleared)
                : RetirementOwnershipResolver.resolveForPlanning(Year.of(year), opening, deaths, births, rules,
                        accounts, selection, cleared, rollovers);
        if (java.util.stream.Stream.concat(cleared.stream(), rollovers.keySet().stream())
                .anyMatch(ref -> ownership.accounts().get(ref).effectiveRmdRecipient().orElse(null) != survivor)) {
            throw refusal(year, UNRESOLVED_MANDATORY_DISTRIBUTION, "The selected spousal retirement treatment could not be established.");
        }
        return ownership;
    }

    void validateDiscretionarySources(int year, List<IncomeSourceAttribution> sources) {
        if (!inSurvivorPeriod(year)) return;
        for (var source : sources) {
            var account = source.account().orElse(null);
            if (account != null && account.owner() == deceased
                    && account.type().requiresIndividualOwnership()
                    && source.kind() != IncomeSourceAttribution.Kind.RMD && source.grossAmount().signum() > 0) {
                throw refusal(year, UNSUPPORTED_POLICY_OR_ACCOUNT,
                        "The applied policy supports required distributions only from a successor retirement account.");
            }
        }
    }

    private static SpousalIraRmdUnavailable refusal(int year, SpousalIraRmdUnavailable.Reason reason, String detail) {
        return new SpousalIraRmdUnavailable(year, reason, detail);
    }

    private int startingAge(LocalDate birthDate) {
        try {
            return rules.getRmdStartingAge(birthDate);
        } catch (IllegalArgumentException unsupported) {
            throw refusal(death.getYear(), UNRESOLVED_MANDATORY_DISTRIBUTION,
                    "No applicable first distribution year was established.");
        }
    }
}
