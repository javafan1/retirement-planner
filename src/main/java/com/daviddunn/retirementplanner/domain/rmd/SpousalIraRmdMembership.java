package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition;
import com.daviddunn.retirementplanner.domain.retirementownership.Spousal401kRollover;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Verified prior balances and debit membership for separate owner IRA and spousal beneficiary obligations. */
public final class SpousalIraRmdMembership {
    private final AnnualRetirementOwnership ownership;
    private final AccountOwnership survivor;
    private final Map<AccountReference, BigDecimal> deathYearBalances;
    private final Map<Account, BigDecimal> deathYearEmployerBalances;
    private final Integer postRbdDeathYear;
    private final Map<AccountReference, BigDecimal> priorBalances;
    private final List<Spousal401kRollover> openingRollovers;
    private final Set<AccountReference> beneficiaryAccounts;
    private final BeneficiaryIraRmd beneficiaryObligation;
    private final List<Beneficiary401kRmd> employerObligations;
    private final Map<AccountReference, Beneficiary401kRmd> employerObligationsBySource;
    private final Map<AccountReference, Account> employerPositions;

    public SpousalIraRmdMembership(AnnualRetirementOwnership ownership, AccountOwnership survivor,
                                  RmdBalanceSnapshot prior, ProjectedPortfolio opening) {
        this(ownership, survivor, prior, opening, Map.of());
    }

    public SpousalIraRmdMembership(AnnualRetirementOwnership ownership, AccountOwnership survivor,
                                  RmdBalanceSnapshot prior, ProjectedPortfolio opening,
                                  Map<AccountReference, Spousal401kRollover> rollovers) {
        this(ownership, survivor, prior, opening, rollovers, null);
    }

    public SpousalIraRmdMembership(AnnualRetirementOwnership ownership, AccountOwnership survivor,
                                  RmdBalanceSnapshot prior, ProjectedPortfolio opening,
                                  Map<AccountReference, Spousal401kRollover> rollovers, Integer firstBeneficiaryYear) {
        this(ownership, survivor, prior, opening, rollovers, firstBeneficiaryYear, null);
    }

    public SpousalIraRmdMembership(AnnualRetirementOwnership ownership, AccountOwnership survivor,
            RmdBalanceSnapshot prior, ProjectedPortfolio opening, Map<AccountReference, Spousal401kRollover> rollovers,
            Integer firstBeneficiaryYear, Integer postRbdDeathYear) {
        this.postRbdDeathYear = postRbdDeathYear;
        this.ownership = java.util.Objects.requireNonNull(ownership);
        this.survivor = java.util.Objects.requireNonNull(survivor);
        if (prior == null || !prior.getSnapshotDate().equals(LocalDate.of(year() - 1, 12, 31))) {
            throw new SpousalIraRmdUnavailable(year(), SpousalIraRmdUnavailable.Reason.MISSING_OR_DUPLICATE_SOURCE,
                    "The preceding December 31 IRA balances are required.");
        }
        var balances = new HashMap<AccountReference, BigDecimal>();
        for (var position : prior.getAccountBalances()) {
            var reference = AccountReference.capture(position.getAccount());
            if (balances.putIfAbsent(reference, position.getBalance()) != null) throw invalidSource();
        }
        var members = new HashMap<AccountReference, BigDecimal>();
        var deceasedMembers = new HashMap<AccountReference, BigDecimal>();
        var inherited = new HashMap<AccountReference, BigDecimal>();
        var employer = new LinkedHashMap<AccountReference, BigDecimal>();
        for (var disposition : ownership.accounts().values()) {
            var original = disposition.originalAccount();
            if (disposition.status() == RetirementOwnershipDisposition.Status.DEATH_YEAR_RECIPIENT) {
                var balance = balances.get(original);
                if (balance == null || disposition.effectiveRmdRecipient().orElse(null) != survivor) throw invalidSource();
                deceasedMembers.put(original, balance);
            }
            if (disposition.status() == RetirementOwnershipDisposition.Status.ELIGIBLE_SPOUSAL_BENEFICIARY) {
                var balance = balances.get(original);
                if (balance == null || disposition.effectiveRmdRecipient().orElse(null) != survivor) throw invalidSource();
                if (original.type() == AccountType.TRADITIONAL_401K) {
                    employer.put(original, balance);
                } else if (original.type() == AccountType.TRADITIONAL_IRA) {
                    inherited.put(original, balance);
                } else {
                    throw invalidSource();
                }
                continue;
            }
            boolean rolledOver = disposition.treatment()
                    == RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_401K_DIRECT_ROLLOVER_V1;
            if ((original.type() == AccountType.TRADITIONAL_IRA || rolledOver)
                    && disposition.effectiveRmdCategory() == RmdAccountCategory.IRA
                    && disposition.effectiveRmdOwner().orElse(null) == survivor) {
                var balance = balances.get(original);
                if (balance == null) throw invalidSource();
                if (rolledOver) {
                    var event = rollovers.get(original);
                    if (event == null) throw invalidSource();
                    if (event.effectiveDate().getYear() == year()) {
                        if (postRbdDeathYear == null && balance.compareTo(event.eligibleTransferredBalance()) != 0) {
                            throw new SpousalIraRmdUnavailable(year(), SpousalIraRmdUnavailable.Reason.INCOMPATIBLE_ACCOUNT_HISTORY,
                                    "The eligible rollover amount differs from the original plan's prior closing balance.");
                        }
                        // A same-calendar-year rollover was still held by the distributing plan on December 31.
                        // The receiving IRA can pay an owner IRA RMD this year but adds no prior balance yet.
                        balance = BigDecimal.ZERO;
                    }
                }
                members.put(original, balance);
            }
        }
        var deathYearEmployer = new LinkedHashMap<Account, BigDecimal>();
        var positions = new HashSet<AccountReference>();
        var employerAccounts = new HashMap<AccountReference, Account>();
        var employerRmds = new LinkedHashMap<AccountReference, Beneficiary401kRmd>();
        var divisor = firstBeneficiaryYear == null || year() < firstBeneficiaryYear ? null : beneficiaryDivisor();
        for (var position : opening.getAccountBalances()) {
            var ref = AccountReference.capture(position.getAccount());
            if (!positions.add(ref)) throw invalidSource();
            if (ref.type() == AccountType.TRADITIONAL_401K && deceasedMembers.containsKey(ref)) {
                deathYearEmployer.put(position.getAccount(), deceasedMembers.get(ref));
            }
            var balance = employer.get(ref);
            if (balance != null) {
                if (firstBeneficiaryYear == null || !ref.participantFacts().equals(ownership.accounts().get(ref).originalAccount().participantFacts())) {
                    throw invalidSource();
                }
                employerAccounts.put(ref, position.getAccount());
                var amount = divisor == null ? BigDecimal.ZERO : RmdCalculator.divideByDistributionPeriod(balance, divisor);
                employerRmds.put(ref, new Beneficiary401kRmd(ref, year(), firstBeneficiaryYear, survivor, balance, divisor, amount));
            }
        }
        if (!positions.containsAll(members.keySet()) || !positions.containsAll(inherited.keySet())
                || !positions.containsAll(deceasedMembers.keySet()) || employerRmds.size() != employer.size()) throw invalidSource();
        if ((firstBeneficiaryYear == null) != (inherited.isEmpty() && employer.isEmpty())) throw invalidSource();
        priorBalances = Map.copyOf(members);
        deathYearBalances = Map.copyOf(deceasedMembers);
        deathYearEmployerBalances = java.util.Collections.unmodifiableMap(deathYearEmployer);
        beneficiaryAccounts = Set.copyOf(inherited.keySet());
        beneficiaryObligation = inherited.isEmpty() ? null : beneficiaryObligation(firstBeneficiaryYear, inherited, divisor);
        employerPositions = Map.copyOf(employerAccounts);
        employerObligationsBySource = Map.copyOf(employerRmds);
        employerObligations = List.copyOf(employerRmds.values());
        openingRollovers = rollovers.values().stream().filter(event -> event.effectiveDate().getYear() == year()).toList();
    }

    public int year() { return ownership.year().getValue(); }
    public List<Spousal401kRollover> openingRollovers() { return openingRollovers; }
    public BeneficiaryIraRmd beneficiaryObligation() { return beneficiaryObligation; }

    public List<Beneficiary401kRmd> beneficiaryEmployerObligations() { return employerObligations; }

    public Account employerDebitPosition(Beneficiary401kRmd obligation) {
        if (employerObligationsBySource.get(obligation.source()) != obligation) throw invalidSource();
        return employerPositions.get(obligation.source());
    }

    public boolean includesBeneficiary(Account account) {
        return beneficiaryAccounts.contains(AccountReference.capture(account));
    }
    public boolean includes(Account account, AccountOwnership owner) {
        var reference = AccountReference.capture(account);
        return owner == survivor ? priorBalances.containsKey(reference)
                : owner == reference.owner() && deathYearBalances.containsKey(reference);
    }

    BigDecimal priorIraBalance(AccountOwnership owner) {
        if (owner != survivor) throw new IllegalArgumentException("IRA RMD owner differs from the survivor.");
        return priorBalances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public HouseholdRmdResult addDeathYearRequirement(HouseholdRmdResult living,
            com.daviddunn.retirementplanner.domain.rules.GovernmentRules rules) {
        if (deathYearBalances.isEmpty()) return living;
        var decedent = decedent();
        var birth = ownership.birthDates().get(decedent);
        var balance = deathYearBalances.entrySet().stream()
                .filter(entry -> entry.getKey().type() == AccountType.TRADITIONAL_IRA)
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var amount = new OwnerRmdService().calculateIraRmd(balance, birth, year(), rules);
        var employerService = new EmployerPlanRmdService();
        var employers = deathYearEmployerBalances.entrySet().stream()
                .map(entry -> employerService.calculateAccountRmd(entry.getKey(), entry.getValue(), birth, year(), rules))
                .toList();
        return living.withDeathYearRequirement(decedent, new OwnerRmdResult(amount, employers, List.of()));
    }

    private AccountOwnership decedent() {
        return survivor == AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
    }

    public IncomeSourceAttribution attributeActualDebit(IncomeSourceAttribution debit) {
        if (debit.owner().orElse(null) == survivor) return debit;
        var account = debit.account().orElseThrow(this::invalidSource);
        RetirementOwnershipDisposition disposition = ownership.accounts().get(account);
        if (deathYearBalances.containsKey(account)) return debit.inYear(year()).forSuccessorRmd(disposition);
        if (beneficiaryAccounts.contains(account)) {
            return debit.inYear(year()).forSuccessorRmd(disposition, beneficiaryObligation);
        }
        var employer = employerObligationsBySource.get(account);
        if (employer != null) return debit.inYear(year()).forBeneficiary401kRmd(disposition, employer);
        if (!priorBalances.containsKey(account) || disposition == null) throw invalidSource();
        return debit.inYear(year()).forSuccessorRmd(disposition);
    }

    public IncomeSourceAttribution attributeOrdinaryDebit(IncomeSourceAttribution debit, LocalDate firstPossibleDate) {
        var account = debit.account().orElseThrow(this::invalidSource);
        var disposition = ownership.accounts().get(account);
        if (disposition == null) throw invalidSource();
        if (disposition.effectiveRmdRecipient().orElse(null) != survivor
                || (!priorBalances.containsKey(account) && !beneficiaryAccounts.contains(account)
                    && !employerObligationsBySource.containsKey(account))) {
            throw new SpousalIraRmdUnavailable(year(), SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                    "The selected retirement position lacks effective successor treatment.");
        }
        if (disposition.status() == RetirementOwnershipDisposition.Status.ELIGIBLE_SUCCESSOR_OWNERSHIP) {
            var threshold = ownership.birthDates().get(survivor).plusYears(59).plusMonths(6);
            if (firstPossibleDate.isBefore(threshold)) {
                var detail = threshold.getYear() == year()
                        ? "The annual withdrawal date cannot establish that the survivor has reached age 59½."
                        : "The owner IRA distribution could require the federal early-distribution additional tax.";
                throw new SpousalIraRmdUnavailable(year(), SpousalIraRmdUnavailable.Reason.UNSUPPORTED_EARLY_DISTRIBUTION, detail);
            }
        }
        return debit.inYear(year()).forSuccessorOrdinary(disposition);
    }

    private BigDecimal beneficiaryDivisor() {
        try {
            var spouseFactor = SingleLifeRmdTable.divisor(year() - ownership.birthDates().get(survivor).getYear());
            if (postRbdDeathYear == null) return spouseFactor;
            var decedentFactor = SingleLifeRmdTable.divisor(postRbdDeathYear - ownership.birthDates().get(decedent()).getYear())
                    .subtract(BigDecimal.valueOf(year() - postRbdDeathYear));
            var factor = spouseFactor.max(decedentFactor);
            if (factor.compareTo(BigDecimal.ONE) <= 0) {
                throw new IllegalArgumentException("The continuing beneficiary divisor has reached the depletion boundary.");
            }
            return factor;
        } catch (IllegalArgumentException unsupported) {
            throw new SpousalIraRmdUnavailable(year(), SpousalIraRmdUnavailable.Reason.UNSUPPORTED_POLICY_OR_ACCOUNT,
                    "A verified Single Life Table divisor for the beneficiary is unavailable.");
        }
    }

    private BeneficiaryIraRmd beneficiaryObligation(int firstYear, Map<AccountReference, BigDecimal> inherited,
            BigDecimal divisor) {
        var decedent = survivor == AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
        if (inherited.keySet().stream().anyMatch(account -> account.owner() != decedent)) throw invalidSource();
        var requirement = divisor == null ? BigDecimal.ZERO
                : inherited.values().stream().map(balance -> RmdCalculator.divideByDistributionPeriod(balance, divisor))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        var prior = inherited.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BeneficiaryIraRmd(year(), firstYear, decedent, survivor, prior, divisor, requirement);
    }

    private SpousalIraRmdUnavailable invalidSource() {
        return new SpousalIraRmdUnavailable(year(), SpousalIraRmdUnavailable.Reason.MISSING_OR_DUPLICATE_SOURCE,
                "The retirement source must identify exactly one prior balance and original position.");
    }
}
