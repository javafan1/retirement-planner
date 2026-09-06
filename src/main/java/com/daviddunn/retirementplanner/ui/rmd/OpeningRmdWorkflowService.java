package com.daviddunn.retirementplanner.ui.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdCalculation;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.RmdEligibilityCalculator;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI-facing workflow support for opening-year RMD data.
 * Financial calculations remain delegated to OpeningRmdCalculator.
 */
public final class OpeningRmdWorkflowService {

    private final OpeningRmdCalculator openingRmdCalculator;
    private final RmdEligibilityCalculator eligibilityCalculator;
    private final GovernmentRules governmentRules;

    public OpeningRmdWorkflowService() {
        this(new OpeningRmdCalculator(),
                new RmdEligibilityCalculator(),
                loadGovernmentRules());
    }

    OpeningRmdWorkflowService(
            OpeningRmdCalculator openingRmdCalculator,
            RmdEligibilityCalculator eligibilityCalculator,
            GovernmentRules governmentRules) {

        this.openingRmdCalculator = Objects.requireNonNull(
                openingRmdCalculator,
                "Opening RMD calculator is required.");
        this.eligibilityCalculator = Objects.requireNonNull(
                eligibilityCalculator,
                "RMD eligibility calculator is required.");
        this.governmentRules = Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");
    }

    public int getDistributionYear(
            RetirementPlan plan) {

        return Objects.requireNonNull(plan, "Retirement plan is required.")
                .getPlanningAssumptions()
                .getProjectionStartDate()
                .getYear();
    }

    public boolean isRequired(
            RetirementPlan plan) {

        return !getApplicableAccounts(plan).isEmpty();
    }

    public boolean hasStaleData(
            RetirementPlan plan) {

        int distributionYear = getDistributionYear(plan);

        return getApplicableAccounts(plan).stream()
                .map(OpeningRmdAccountRow::account)
                .map(Account::getOpeningRmdAccountData)
                .filter(Objects::nonNull)
                .anyMatch(data ->
                        data.getDistributionYear() != distributionYear);
    }

    public List<OpeningRmdAccountRow> getApplicableAccounts(
            RetirementPlan plan) {

        Objects.requireNonNull(plan, "Retirement plan is required.");

        int distributionYear = getDistributionYear(plan);

        return List.of(
                getApplicableAccounts(
                        plan,
                        plan.getHousehold().getPrimaryPerson(),
                        AccountOwnership.PRIMARY,
                        distributionYear),
                getApplicableAccounts(
                        plan,
                        plan.getHousehold().getSpouse(),
                        AccountOwnership.SPOUSE,
                        distributionYear))
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    public OpeningRmdCalculation preview(
            RetirementPlan plan,
            Map<Account, OpeningRmdInput> inputs) {

        Map<Account, OpeningRmdAccountData> proposedData =
                createOpeningData(plan, inputs);

        Map<Account, OpeningRmdAccountData> existingData =
                captureExistingData(proposedData.keySet());

        try {
            applyData(proposedData);
            return openingRmdCalculator.calculate(
                    plan,
                    getDistributionYear(plan),
                    governmentRules);
        } finally {
            applyData(existingData);
        }
    }

    public void save(
            RetirementPlan plan,
            Map<Account, OpeningRmdInput> inputs) {

        Map<Account, OpeningRmdAccountData> proposedData =
                createOpeningData(plan, inputs);

        /*
         * Validate the complete proposed state before changing
         * the plan so Cancel and failed Save leave it unchanged.
         */
        preview(plan, inputs);
        applyData(proposedData);
    }

    private List<OpeningRmdAccountRow> getApplicableAccounts(
            RetirementPlan plan,
            Person person,
            AccountOwnership ownership,
            int distributionYear) {

        if (person.getBirthDate() == null
                || !eligibilityCalculator.isRmdRequired(
                person.getBirthDate(), distributionYear, governmentRules)) {
            return List.of();
        }

        return plan.getAccountPortfolio()
                .getAccounts(ownership)
                .stream()
                .filter(account -> account.getType().isSubjectToOwnerRmd())
                .map(account -> new OpeningRmdAccountRow(
                        account,
                        ownership,
                        person.getFullName()))
                .toList();
    }

    private Map<Account, OpeningRmdAccountData> createOpeningData(
            RetirementPlan plan,
            Map<Account, OpeningRmdInput> inputs) {

        Objects.requireNonNull(inputs, "Opening RMD inputs are required.");

        int distributionYear = getDistributionYear(plan);
        Map<Account, OpeningRmdAccountData> data = new LinkedHashMap<>();

        for (OpeningRmdAccountRow row : getApplicableAccounts(plan)) {
            OpeningRmdInput input = inputs.get(row.account());

            if (input == null) {
                throw new IllegalArgumentException(
                        "Opening RMD information is required for "
                                + row.account().getName() + ".");
            }

            data.put(row.account(), new OpeningRmdAccountData(
                    distributionYear,
                    input.priorDecember31Balance(),
                    input.rmdAlreadyDistributedBeforeProjection()));
        }

        return data;
    }

    private static Map<Account, OpeningRmdAccountData> captureExistingData(
            Iterable<Account> accounts) {

        Map<Account, OpeningRmdAccountData> data = new LinkedHashMap<>();

        for (Account account : accounts) {
            data.put(account, account.getOpeningRmdAccountData());
        }

        return data;
    }

    private static void applyData(
            Map<Account, OpeningRmdAccountData> data) {

        data.forEach(Account::setOpeningRmdAccountData);
    }

    private static GovernmentRules loadGovernmentRules() {

        try {
            return new GovernmentRulesRepository().load(
                    "/rules/government-rules-2026.json");
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to load government rules for opening RMD information.",
                    ex);
        }
    }

    public record OpeningRmdAccountRow(
            Account account,
            AccountOwnership ownership,
            String ownerName) {

        public OpeningRmdAccountRow {
            Objects.requireNonNull(account, "Account is required.");
            Objects.requireNonNull(ownership, "Account ownership is required.");
            Objects.requireNonNull(ownerName, "Owner name is required.");
        }
    }

    public record OpeningRmdInput(
            BigDecimal priorDecember31Balance,
            BigDecimal rmdAlreadyDistributedBeforeProjection) {

        public OpeningRmdInput {
            Objects.requireNonNull(
                    priorDecember31Balance,
                    "Prior December 31 balance is required.");
            Objects.requireNonNull(
                    rmdAlreadyDistributedBeforeProjection,
                    "RMD already distributed is required.");
        }
    }
}
