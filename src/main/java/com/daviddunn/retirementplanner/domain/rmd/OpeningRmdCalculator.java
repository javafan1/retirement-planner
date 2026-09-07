package com.daviddunn.retirementplanner.domain.rmd;

import java.util.Set;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Calculates the opening projection year's annual and remaining RMDs.
 * It delegates statutory RMD math to {@link HouseholdRmdCalculator}.
 */
public final class OpeningRmdCalculator {

    private final HouseholdRmdCalculator householdRmdCalculator;
    private final RmdEligibilityCalculator eligibilityCalculator;

    public OpeningRmdCalculator() {
        this(new HouseholdRmdCalculator(), new RmdEligibilityCalculator());
    }

    OpeningRmdCalculator(
            HouseholdRmdCalculator householdRmdCalculator,
            RmdEligibilityCalculator eligibilityCalculator) {

        this.householdRmdCalculator = Objects.requireNonNull(
                householdRmdCalculator,
                "Household RMD calculator is required.");
        this.eligibilityCalculator = Objects.requireNonNull(
                eligibilityCalculator,
                "RMD eligibility calculator is required.");
    }

    public OpeningRmdCalculation calculate(
            RetirementPlan plan,
            int distributionYear,
            GovernmentRules governmentRules) {

        return calculate(plan, distributionYear, governmentRules,
                Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE));
    }

    public OpeningRmdCalculation calculate(
            RetirementPlan plan,
            int distributionYear,
            GovernmentRules governmentRules,
            Set<AccountOwnership> eligibleOwners) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Objects.requireNonNull(governmentRules, "Government rules are required.");
        eligibleOwners = Set.copyOf(Objects.requireNonNull(eligibleOwners));
        for (Account account : plan.getAccountPortfolio().getAccounts()) {
            OpeningRmdAccountData data = account.getOpeningRmdAccountData();
            if (account.getOwnership() != AccountOwnership.JOINT
                    && !eligibleOwners.contains(account.getOwnership())
                    && account.getType().isSubjectToOwnerRmd()
                    && data != null && data.getDistributionYear() == distributionYear
                    && data.getRmdAlreadyDistributedBeforeProjection().signum() > 0) {
                throw new IllegalStateException("Inconsistent lifetime scenario: deceased owner has an already-distributed RMD for "
                        + account.getName() + " in " + distributionYear + ".");
            }
        }


        validateRequiredOpeningData(plan, distributionYear, governmentRules, eligibleOwners);

        RmdBalanceSnapshot openingSnapshot =
                RmdBalanceSnapshot.fromOpeningRmdData(
                        plan.getAccountPortfolio(), distributionYear);

        HouseholdRmdResult annualRequirement =
                householdRmdCalculator.calculate(
                        plan,
                        openingSnapshot,
                        distributionYear,
                        governmentRules, eligibleOwners);

        OwnerRemainingRmd primary = eligibleOwners.contains(AccountOwnership.PRIMARY) ? calculateRemainingOwnerRmd(
                plan.getAccountPortfolio(),
                AccountOwnership.PRIMARY,
                annualRequirement.getPrimaryRmd()) : new OwnerRemainingRmd(OwnerRmdResult.zero(), BigDecimal.ZERO);

        OwnerRemainingRmd spouse = eligibleOwners.contains(AccountOwnership.SPOUSE) ? calculateRemainingOwnerRmd(
                plan.getAccountPortfolio(),
                AccountOwnership.SPOUSE,
                annualRequirement.getSpouseRmd()) : new OwnerRemainingRmd(OwnerRmdResult.zero(), BigDecimal.ZERO);

        return new OpeningRmdCalculation(
                annualRequirement,
                new HouseholdRmdResult(primary.result(), spouse.result()),
                primary.distributedBeforeProjection()
                        .add(spouse.distributedBeforeProjection()));
    }

    private void validateRequiredOpeningData(
            RetirementPlan plan,
            int distributionYear,
            GovernmentRules governmentRules, Set<AccountOwnership> eligibleOwners) {

        Household household = plan.getHousehold();
        if (eligibleOwners.contains(AccountOwnership.PRIMARY)) validateOwnerOpeningData(
                plan.getAccountPortfolio(),
                household.getPrimaryPerson(),
                AccountOwnership.PRIMARY,
                distributionYear,
                governmentRules);
        if (eligibleOwners.contains(AccountOwnership.SPOUSE)) validateOwnerOpeningData(
                plan.getAccountPortfolio(),
                household.getSpouse(),
                AccountOwnership.SPOUSE,
                distributionYear,
                governmentRules);
    }

    private void validateOwnerOpeningData(
            AccountPortfolio portfolio,
            Person person,
            AccountOwnership ownership,
            int distributionYear,
            GovernmentRules governmentRules) {

        if (person.getBirthDate() == null || !eligibilityCalculator.isRmdRequired(
                person.getBirthDate(), distributionYear, governmentRules)) {
            return;
        }

        for (Account account : portfolio.getAccounts(ownership)) {
            if (!account.getType().isSubjectToOwnerRmd()) {
                continue;
            }

            OpeningRmdAccountData data = account.getOpeningRmdAccountData();

            if (data == null) {
                throw new IllegalStateException(
                        "Opening RMD information is required for "
                                + account.getName() + " in " + distributionYear
                                + ". Enter its prior December 31 balance and RMD already distributed.");
            }

            if (data.getDistributionYear() != distributionYear) {
                throw new IllegalStateException(
                        "Opening RMD information for " + account.getName()
                                + " is for " + data.getDistributionYear()
                                + ", but the projection starts in "
                                + distributionYear + ".");
            }
        }
    }

    private OwnerRemainingRmd calculateRemainingOwnerRmd(
            AccountPortfolio portfolio,
            AccountOwnership ownership,
            OwnerRmdResult annualResult) {

        BigDecimal iraDistributedBeforeProjection = portfolio
                .getAccounts(ownership)
                .stream()
                .filter(account -> account.getType().isSubjectToOwnerRmd())
                .filter(account -> account.getType().getRmdAccountCategory()
                        == RmdAccountCategory.IRA)
                .map(this::getDistributedBeforeProjection)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        validateDoesNotExceedRequirement(
                iraDistributedBeforeProjection,
                annualResult.getIraRmd(),
                ownership + " IRA RMD");

        BigDecimal remainingIraRmd = annualResult.getIraRmd()
                .subtract(iraDistributedBeforeProjection)
                .max(BigDecimal.ZERO);

        AccountRmdRemaining traditional401k = remainingAccountRmds(
                annualResult.getTraditional401kRmds());
        AccountRmdRemaining traditional403b = remainingAccountRmds(
                annualResult.getTraditional403bRmds());

        return new OwnerRemainingRmd(
                new OwnerRmdResult(
                        remainingIraRmd,
                        traditional401k.accountRmds(),
                        traditional403b.accountRmds()),
                iraDistributedBeforeProjection
                        .add(traditional401k.distributedBeforeProjection())
                        .add(traditional403b.distributedBeforeProjection()));
    }

    private AccountRmdRemaining remainingAccountRmds(
            List<AccountRmd> annualAccountRmds) {

        BigDecimal distributedBeforeProjection = BigDecimal.ZERO;

        List<AccountRmd> remaining = annualAccountRmds.stream()
                .map(annualRmd -> {
                    BigDecimal alreadyDistributed = getDistributedBeforeProjection(
                            annualRmd.getAccount());
                    validateDoesNotExceedRequirement(
                            alreadyDistributed,
                            annualRmd.getAmount(),
                            annualRmd.getAccount().getName() + " RMD");
                    return new AccountRmd(
                            annualRmd.getAccount(),
                            annualRmd.getAmount()
                                    .subtract(alreadyDistributed)
                                    .max(BigDecimal.ZERO));
                })
                .toList();

        for (AccountRmd annualRmd : annualAccountRmds) {
            distributedBeforeProjection = distributedBeforeProjection.add(
                    getDistributedBeforeProjection(annualRmd.getAccount()));
        }

        return new AccountRmdRemaining(remaining, distributedBeforeProjection);
    }

    private BigDecimal getDistributedBeforeProjection(Account account) {
        OpeningRmdAccountData data = account.getOpeningRmdAccountData();
        return data != null
                ? data.getRmdAlreadyDistributedBeforeProjection()
                : BigDecimal.ZERO;
    }

    private void validateDoesNotExceedRequirement(
            BigDecimal alreadyDistributed,
            BigDecimal annualRequirement,
            String description) {

        if (alreadyDistributed.compareTo(annualRequirement) > 0) {
            throw new IllegalStateException(
                    description + " already distributed amount cannot exceed its annual RMD requirement.");
        }
    }

    private record OwnerRemainingRmd(
            OwnerRmdResult result,
            BigDecimal distributedBeforeProjection) {
    }

    private record AccountRmdRemaining(
            List<AccountRmd> accountRmds,
            BigDecimal distributedBeforeProjection) {
    }
}
