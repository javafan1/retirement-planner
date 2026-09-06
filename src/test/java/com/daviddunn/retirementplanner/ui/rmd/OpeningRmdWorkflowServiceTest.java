package com.daviddunn.retirementplanner.ui.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningRmdWorkflowServiceTest {

    private final OpeningRmdWorkflowService workflow =
            new OpeningRmdWorkflowService();

    @Test
    void doesNotRequireWorkflowWhenNoOwnerIsEligibleInOpeningYear() {
        RetirementPlan plan = plan(1965, 1966);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA(
                "Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000")));

        assertFalse(workflow.isRequired(plan));
        assertTrue(workflow.getApplicableAccounts(plan).isEmpty());
    }

    @Test
    void includesOnlyEligibleOwnersAndApplicableAccounts() {
        RetirementPlan plan = plan(1950, 1965);
        TraditionalIRA primaryIra = new TraditionalIRA(
                "Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000"));
        Traditional401K primary401k = new Traditional401K(
                "Primary 401(k)", AccountOwnership.PRIMARY,
                new BigDecimal("100000"));
        TraditionalIRA spouseIra = new TraditionalIRA(
                "Spouse IRA", AccountOwnership.SPOUSE,
                new BigDecimal("100000"));
        BrokerageAccount brokerage = new BrokerageAccount(
                "Joint Brokerage", AccountOwnership.JOINT,
                new BigDecimal("100000"));

        plan.getAccountPortfolio().addAccount(primaryIra);
        plan.getAccountPortfolio().addAccount(primary401k);
        plan.getAccountPortfolio().addAccount(spouseIra);
        plan.getAccountPortfolio().addAccount(brokerage);

        assertTrue(workflow.isRequired(plan));
        assertEquals(2, workflow.getApplicableAccounts(plan).size());
        assertTrue(workflow.getApplicableAccounts(plan).stream()
                .allMatch(row -> row.ownership() == AccountOwnership.PRIMARY));
    }

    @Test
    void includesBothOwnersWhenBothAreOpeningYearEligible() {
        RetirementPlan plan = plan(1950, 1949);
        plan.getAccountPortfolio().addAccount(new TraditionalIRA(
                "Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000")));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA(
                "Spouse IRA", AccountOwnership.SPOUSE,
                new BigDecimal("100000")));

        assertEquals(2, workflow.getApplicableAccounts(plan).size());
        assertTrue(workflow.getApplicableAccounts(plan).stream()
                .anyMatch(row -> row.ownership() == AccountOwnership.PRIMARY));
        assertTrue(workflow.getApplicableAccounts(plan).stream()
                .anyMatch(row -> row.ownership() == AccountOwnership.SPOUSE));
    }

    @Test
    void savesHistoricalFactsAndCalculatesAnnualAndRemainingRmd() {
        RetirementPlan plan = plan(1950, 1965);
        TraditionalIRA ira = new TraditionalIRA(
                "Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("900000"));
        plan.getAccountPortfolio().addAccount(ira);

        Map<com.daviddunn.retirementplanner.domain.financial.Account,
                OpeningRmdWorkflowService.OpeningRmdInput> inputs =
                inputs(ira, "1000000", "15000");

        var preview = workflow.preview(plan, inputs);

        assertNull(ira.getOpeningRmdAccountData());
        assertEquals(0, new BigDecimal("42194.09").compareTo(
                preview.getAnnualRequirement().getPrimaryRmd().getIraRmd()));
        assertEquals(0, new BigDecimal("27194.09").compareTo(
                preview.getRemainingRequirement().getPrimaryRmd().getIraRmd()));

        workflow.save(plan, inputs);

        OpeningRmdAccountData saved = ira.getOpeningRmdAccountData();
        assertEquals(2026, saved.getDistributionYear());
        assertEquals(0, new BigDecimal("1000000").compareTo(
                saved.getPriorDecember31Balance()));
        assertEquals(0, new BigDecimal("15000").compareTo(
                saved.getRmdAlreadyDistributedBeforeProjection()));
        assertEquals(0, new BigDecimal("900000").compareTo(
                ira.getCurrentBalance()));
    }

    @Test
    void rejectsNegativeOrMissingHistoricalFactsWithoutMutatingPlan() {
        RetirementPlan plan = plan(1950, 1965);
        TraditionalIRA ira = new TraditionalIRA(
                "Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("900000"));
        plan.getAccountPortfolio().addAccount(ira);

        assertThrows(IllegalArgumentException.class,
                () -> workflow.save(plan, inputs(ira, "-1", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> workflow.save(plan, Map.of()));
        assertNull(ira.getOpeningRmdAccountData());
    }

    @Test
    void flagsOpeningDataFromAnotherProjectionYearAsStale() {
        RetirementPlan plan = plan(1950, 1965);
        TraditionalIRA ira = new TraditionalIRA(
                "Primary IRA", AccountOwnership.PRIMARY,
                new BigDecimal("900000"));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2025, new BigDecimal("1000000"), BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(ira);

        assertTrue(workflow.hasStaleData(plan));
    }

    private RetirementPlan plan(
            int primaryBirthYear,
            int spouseBirthYear) {

        Household household = new Household(
                new Person("Primary", "Person",
                        LocalDate.of(primaryBirthYear, 1, 1)),
                new Person("Spouse", "Person",
                        LocalDate.of(spouseBirthYear, 1, 1)));

        return new RetirementPlan(household, new AccountPortfolio(),
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 2,
                        LocalDate.of(2026, 7, 1)));
    }

    private Map<com.daviddunn.retirementplanner.domain.financial.Account,
            OpeningRmdWorkflowService.OpeningRmdInput> inputs(
            com.daviddunn.retirementplanner.domain.financial.Account account,
            String priorBalance,
            String distributed) {

        Map<com.daviddunn.retirementplanner.domain.financial.Account,
                OpeningRmdWorkflowService.OpeningRmdInput> inputs =
                new LinkedHashMap<>();
        inputs.put(account, new OpeningRmdWorkflowService.OpeningRmdInput(
                new BigDecimal(priorBalance), new BigDecimal(distributed)));
        return inputs;
    }
}
