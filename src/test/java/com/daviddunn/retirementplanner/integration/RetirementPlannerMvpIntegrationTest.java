package com.daviddunn.retirementplanner.integration;

import com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetirementPlannerMvpIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void saveReloadReprojectAndCsvExportPreserveTheMvpFinancialWorkflow()
            throws Exception {

        RetirementPlan original = createPlan();
        ProjectionEngine engine = new ProjectionEngine();
        Projection originalProjection = engine.project(original);
        Projection repeatedProjection = engine.project(original);

        assertEquivalentProjections(originalProjection, repeatedProjection);

        Path planFile = temporaryDirectory.resolve("mvp-plan.json");
        JsonRetirementPlanRepository repository = new JsonRetirementPlanRepository();
        repository.save(original, planFile);

        RetirementPlan reloaded = repository.load(planFile);
        Projection reloadedProjection = engine.project(reloaded);

        assertPersistedInputs(original, reloaded);
        assertEquivalentProjections(originalProjection, reloadedProjection);

        ProjectionYear firstYear = originalProjection.getFirstYear();
        ProjectionYear secondYear = originalProjection.getYearAt(1);

        assertTrue(firstYear.getRequiredMinimumDistribution().signum() > 0);
        assertTrue(firstYear.getRmdDistributedBeforeProjection().signum() > 0);
        assertTrue(firstYear.getRmdDistributedInProjection().signum() >= 0);
        assertMoney(firstYear.getRothConversion(), firstYear.getPrimaryRothConversion()
                .add(firstYear.getSpouseRothConversion()));
        assertTrue(firstYear.getRothConversion().compareTo(
                firstYear.getRequestedRothConversion()) <= 0);
        assertMoney(firstYear.getRequestedRothConversion().subtract(
                firstYear.getRothConversion()), firstYear.getRothConversionShortfall());
        assertTrue(firstYear.getFederalIncomeTax().signum() >= 0);
        assertTrue(firstYear.getMichiganIncomeTax().signum() >= 0);
        assertTrue(firstYear.getSocialSecurityResult()
                .householdBenefit().signum() > 0);
        assertMoney(BigDecimal.ZERO, secondYear.getRmdDistributedBeforeProjection());

        Path csvFile = temporaryDirectory.resolve("mvp-projection.csv");
        new ProjectionCsvExporter().export(
                originalProjection,
                List.of(),
                csvFile);

        assertCsvMatchesFirstProjectionYear(csvFile, firstYear);
    }

    private RetirementPlan createPlan() {

        Person primary = new Person(
                "Primary", "Planner", LocalDate.of(1950, 6, 15));
        Person spouse = new Person(
                "Spouse", "Planner", LocalDate.of(1955, 9, 10));

        primary.addIncomeSource(new Pension(
                "Primary Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2020, 1, 1), null,
                new BigDecimal("2500"), new BigDecimal("0.02")));
        spouse.addIncomeSource(new Pension(
                "Spouse Pension", AccountOwnership.SPOUSE,
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("1200"), BigDecimal.ZERO));
        primary.addIncomeSource(new SocialSecurityIncome(
                "Primary Social Security", AccountOwnership.PRIMARY,
                LocalDate.of(2020, 7, 1), null,
                new BigDecimal("3000"), 70, new BigDecimal("0.025"), 2026));
        spouse.addIncomeSource(new SocialSecurityIncome(
                "Spouse Social Security", AccountOwnership.SPOUSE,
                LocalDate.of(2026, 3, 1), null,
                new BigDecimal("1800"), 67, new BigDecimal("0.025"), 2026));

        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living expenses", new BigDecimal("85000")));

        TraditionalIRA primaryTraditional = new TraditionalIRA(
                "Primary Traditional IRA", AccountOwnership.PRIMARY,
                new BigDecimal("35000"));
        primaryTraditional.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("36000"), new BigDecimal("1000")));

        TraditionalIRA spouseTraditional = new TraditionalIRA(
                "Spouse Traditional IRA", AccountOwnership.SPOUSE,
                new BigDecimal("450000"));
        spouseTraditional.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("460000"), BigDecimal.ZERO));

        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(primaryTraditional);
        portfolio.addAccount(new RothIRA("Primary Roth IRA", AccountOwnership.PRIMARY,
                new BigDecimal("100000")));
        portfolio.addAccount(spouseTraditional);
        portfolio.addAccount(new RothIRA("Spouse Roth IRA", AccountOwnership.SPOUSE,
                new BigDecimal("50000")));
        portfolio.addAccount(new BrokerageAccount("Joint Brokerage", AccountOwnership.JOINT,
                new BigDecimal("150000")));
        portfolio.addAccount(new CheckingAccount("Joint Checking", AccountOwnership.JOINT,
                new BigDecimal("25000")));

        RetirementPlan plan = new RetirementPlan(
                household,
                portfolio,
                new PlanningAssumptions(
                        new BigDecimal("0.05"), new BigDecimal("0.025"),
                        4, LocalDate.of(2026, 7, 1)));

        plan.setRothConversionRequest(new RothConversionRequest(
                true, 2026, new BigDecimal("50000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT,
                RothConversionFrequency.ANNUAL));
        return plan;
    }

    private void assertPersistedInputs(RetirementPlan original, RetirementPlan reloaded) {

        assertEquals(original.getHousehold().getPrimaryPerson().getBirthDate(),
                reloaded.getHousehold().getPrimaryPerson().getBirthDate());
        assertEquals(original.getHousehold().getSpouse().getBirthDate(),
                reloaded.getHousehold().getSpouse().getBirthDate());
        assertEquals(original.getPlanningAssumptions().getProjectionStartDate(),
                reloaded.getPlanningAssumptions().getProjectionStartDate());
        assertEquals(original.getRothConversionRequest().getStrategy(),
                reloaded.getRothConversionRequest().getStrategy());
        assertEquals(original.getRothConversionRequest().getFrequency(),
                reloaded.getRothConversionRequest().getFrequency());
        assertEquals(6, reloaded.getAccountPortfolio().getAccounts().size());

        TraditionalIRA loadedPrimaryTraditional = (TraditionalIRA) reloaded
                .getAccountPortfolio().getAccounts().getFirst();
        assertEquals(AccountOwnership.PRIMARY, loadedPrimaryTraditional.getOwnership());
        assertMoney(new BigDecimal("36000"), loadedPrimaryTraditional
                .getOpeningRmdAccountData().getPriorDecember31Balance());
        assertMoney(new BigDecimal("1000"), loadedPrimaryTraditional
                .getOpeningRmdAccountData().getRmdAlreadyDistributedBeforeProjection());

        SocialSecurityIncome loadedPrimarySocialSecurity = (SocialSecurityIncome) reloaded
                .getHousehold().getPrimaryPerson().getIncomeSources().get(1);
        assertEquals(2026, loadedPrimarySocialSecurity.getBenefitValuationYear());
    }

    private void assertEquivalentProjections(Projection expected, Projection actual) {

        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            ProjectionYear expectedYear = expected.getYearAt(index);
            ProjectionYear actualYear = actual.getYearAt(index);
            assertEquals(expectedYear.getCalendarYear(), actualYear.getCalendarYear());
            assertMoney(expectedYear.getBeginningInvestableAssets(), actualYear.getBeginningInvestableAssets());
            assertMoney(expectedYear.getEndingInvestableAssets(), actualYear.getEndingInvestableAssets());
            assertMoney(expectedYear.getGuaranteedIncome(), actualYear.getGuaranteedIncome());
            assertMoney(expectedYear.getAnnualExpenses(), actualYear.getAnnualExpenses());
            assertMoney(expectedYear.getRequiredMinimumDistribution(), actualYear.getRequiredMinimumDistribution());
            assertMoney(expectedYear.getRmdDistributedBeforeProjection(), actualYear.getRmdDistributedBeforeProjection());
            assertMoney(expectedYear.getRothConversion(), actualYear.getRothConversion());
            assertMoney(expectedYear.getPrimaryRothConversion(), actualYear.getPrimaryRothConversion());
            assertMoney(expectedYear.getSpouseRothConversion(), actualYear.getSpouseRothConversion());
            assertMoney(expectedYear.getFederalIncomeTax(), actualYear.getFederalIncomeTax());
            assertMoney(expectedYear.getMichiganIncomeTax(), actualYear.getMichiganIncomeTax());
        }
    }

    private void assertCsvMatchesFirstProjectionYear(Path csvFile, ProjectionYear year)
            throws Exception {

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(5, lines.size());
        List<String> headers = List.of(lines.getFirst().split(","));
        String[] values = lines.get(1).split(",", -1);

        assertMoney(year.getRothConversion(), value(headers, values, "Roth Conversion"));
        assertMoney(year.getPrimaryRothConversion(), value(headers, values, "Primary Roth Conversion"));
        assertMoney(year.getSpouseRothConversion(), value(headers, values, "Spouse Roth Conversion"));
        assertMoney(year.getRequestedRothConversion(), value(headers, values, "Requested Roth Conversion"));
        assertMoney(year.getRothConversionShortfall(), value(headers, values, "Roth Conversion Shortfall"));
        assertMoney(year.getRequiredMinimumDistribution(), value(headers, values,
                "Required Minimum Distribution"));
        assertMoney(year.getRmdDistributedBeforeProjection(), value(headers, values,
                "RMD Distributed Before Projection"));
        assertMoney(year.getEndingInvestableAssets(), value(headers, values,
                "Ending Investable Assets"));
        assertMoney(year.getSocialSecurityResult().primaryOwnBenefit(),
                value(headers, values, "Primary Own Social Security"));
        assertMoney(year.getSocialSecurityResult().spouseOwnBenefit(),
                value(headers, values, "Spouse Own Social Security"));
        assertMoney(year.getSocialSecurityResult().primarySurvivorCandidate(),
                value(headers, values, "Primary Survivor Candidate"));
        assertMoney(year.getSocialSecurityResult().spouseSurvivorCandidate(),
                value(headers, values, "Spouse Survivor Candidate"));
        assertMoney(year.getSocialSecurityResult().householdBenefit(),
                value(headers, values, "Household Social Security"));

        assertEquals(
                year.getSocialSecurityResult()
                        .primarySelection()
                        .getDisplayName(),
                textValue(
                        headers,
                        values,
                        "Primary Selected Social Security Benefit"));
    }

    private BigDecimal value(List<String> headers, String[] values, String header) {
        int index = headers.indexOf(header);
        assertTrue(index >= 0, "Missing CSV header: " + header);
        return new BigDecimal(values[index]);
    }

    private String textValue(
            List<String> headers,
            String[] values,
            String header) {

        int index = headers.indexOf(header);
        assertTrue(index >= 0, "Missing CSV header: " + header);
        return values[index];
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
