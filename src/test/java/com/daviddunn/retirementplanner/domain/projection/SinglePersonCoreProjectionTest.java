package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonCoreProjectionTest {
    static BigDecimal money(String value) { return new BigDecimal(value); }

    static RetirementPlan plan(boolean couple, FilingStatus filing) {
        Person primary = new Person("Primary", "Fixture", LocalDate.of(1954, 2, 1));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1), null, money("10000"), money("0.02")));
        primary.addIncomeSource(new SocialSecurityIncome("Own SS", AccountOwnership.PRIMARY,
                LocalDate.of(2021, 2, 1), null, money("2600"), 67, BigDecimal.ZERO, 2026));
        Person spouse = null;
        if (couple) {
            spouse = new Person("Spouse", "Fixture", LocalDate.of(1954, 6, 1));
            spouse.addIncomeSource(new SocialSecurityIncome("Spouse SS", AccountOwnership.SPOUSE,
                    LocalDate.of(2021, 6, 1), null, money("1800"), 67, BigDecimal.ZERO, 2026));
        }
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", money("100000")));
        household.addExpense(new Expense("Health", money("8000"), GrowthCategory.HEALTHCARE,
                LocalDate.of(2027, 1, 1), null, ExpenseType.RECURRING));
        household.addExpense(new Expense("Purchase", money("25000"), GrowthCategory.GENERAL,
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31), ExpenseType.ONE_TIME));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY, money("400000")));
        var ira = new TraditionalIRA("Primary IRA", AccountOwnership.PRIMARY, money("600000"));
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, money("650000"), money("15000")));
        portfolio.addAccount(ira);
        portfolio.addAccount(new RothIRA("Primary Roth", AccountOwnership.PRIMARY, money("100000")));
        if (couple) {
            var spouseIra = new TraditionalIRA("Spouse IRA", AccountOwnership.SPOUSE, money("300000"));
            spouseIra.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, money("320000"), money("5000")));
            portfolio.addAccount(spouseIra);
            portfolio.addAccount(new RothIRA("Spouse Roth", AccountOwnership.SPOUSE, money("50000")));
        }
        var assumptions = new PlanningAssumptions(new EconomicAssumptions(money("0.04"), money("0.02"), money("0.04"), money("0.02")),
                new TaxAssumptions(money("0.02"), money("0.02"), BigDecimal.ZERO, BigDecimal.ZERO, filing, money("0.25")),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), 4, LocalDate.of(2027, 7, 1));
        RetirementPlan plan = new RetirementPlan(household, portfolio, assumptions);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, money("30000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        return plan;
    }

    static String financialRows(Projection projection) {
        var rows = new ArrayList<String>();
        for (var y : projection.getYears()) {
            rows.add(String.join("|", Integer.toString(y.getCalendarYear()), y.getGuaranteedIncome().toPlainString(),
                    y.getAnnualExpenses().toPlainString(), y.getRequiredMinimumDistribution().toPlainString(),
                    y.getRothConversion().toPlainString(), y.getFederalIncomeTax().toPlainString(),
                    y.getMichiganIncomeTax().toPlainString(), y.getAnnualMedicarePremium().toPlainString(),
                    y.getModifiedAdjustedGrossIncome().toPlainString(), y.getPortfolioWithdrawal().toPlainString(),
                    y.getEndingInvestableAssets().toPlainString(), y.getAfterTaxEstateValue().toPlainString()));
        }
        return String.join("\n", rows) + "\n";
    }

    @Test
    void representativeCoupleRemainsExactlyUnchanged() throws Exception {
        String actual = financialRows(new ProjectionEngine().project(plan(true, FilingStatus.MARRIED_FILING_JOINTLY)));
        assertEquals(Files.readString(Path.of("src/test/resources/single-person-stage2-couple-golden.txt")).replace("\r\n", "\n"), actual);
    }

    @Test
    void genuineSingleProjectionUsesOnlyPresentOwnerAndSameYearIrmaa() throws Exception {
        var plan = plan(false, FilingStatus.SINGLE);
        var home = new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset("Home", money("300000"), money("0.02"));
        plan.addNonInvestableAsset(home);
        var originalBalances = plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).toList();
        var result = new ProjectionEngine().project(plan);
        assertEquals(4, result.getYears().size());
        var rules = new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository()
                .load("/rules/government-rules-2026.json");
        for (var year : result.getYears()) {
            assertFalse(year.getSocialSecurityResult().hasSpouse());
            assertTrue(year.getSocialSecurityResult().spouseOwnBenefitIfPresent().isEmpty());
            assertNull(year.getSocialSecurityResult().primarySurvivorCandidate());
            assertEquals(year.getSocialSecurityResult().primaryOwnBenefit(), year.getSocialSecurityResult().householdBenefit());
            assertTrue(year.spouseRothConversion().isEmpty());
            assertEquals(0, year.getRothConversion().compareTo(year.getPrimaryRothConversion()));
            assertEquals(0, year.getRothConversion().compareTo(money("30000")));
            assertTrue(year.getRequiredMinimumDistribution().signum() > 0);
            assertTrue(year.getFederalIncomeTax().signum() > 0);
            assertTrue(year.getMichiganIncomeTax().signum() > 0);
            assertTrue(year.getInvestmentGrowth().signum() > 0);
            var premium = year.getMedicarePremiumCalculation();
            assertEquals(1, premium.coveredMedicareParticipants());
            // Existing model: Medicare records same-year modeled AGI, not historical income.
            assertEquals(0, year.getAdjustedGrossIncome().compareTo(premium.modifiedAdjustedGrossIncome()));
            var projectedRules = new com.daviddunn.retirementplanner.domain.tax.GovernmentRuleProjectionService()
                    .project(rules, plan.getPlanningAssumptions(), year.getCalendarYear());
            var bracket = projectedRules.getIrmaaRules().getBracket(FilingStatus.SINGLE, premium.modifiedAdjustedGrossIncome());
            assertEquals(bracket.getMonthlyPartBPremium(), premium.monthlyPartBPremium());
            assertEquals(bracket.getMonthlyPartDPremium(), premium.monthlyPartDPremium());
            assertEquals(0, year.getEndingInvestableAssets().subtract(year.getEstimatedHeirTax()).compareTo(year.getAfterTaxEstateValue()));
            assertEquals(0, year.getBeginningInvestableAssets().add(year.getInvestmentGrowth())
                    .subtract(year.getPortfolioWithdrawal()).add(year.getRetainedHouseholdSurplus())
                    .setScale(2, java.math.RoundingMode.HALF_UP).compareTo(year.getEndingInvestableAssets()));
        }
        assertEquals(0, result.getYearAt(0).getRmdDistributedBeforeProjection().compareTo(money("15000")));
        assertEquals(0, result.getYearAt(0).getRequiredMinimumDistribution().subtract(money("15000"))
                .compareTo(result.getYearAt(0).getRmdDistributedInProjection()));
        assertEquals(originalBalances, plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).toList());
        var metrics = new com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator().calculate(plan, result);
        var homeValue = new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService().projectValue(home, 3);
        assertEquals(0, result.getYears().getLast().getEndingInvestableAssets().add(homeValue).compareTo(metrics.endingNetWorth()));
        assertTrue(metrics.spouseSocialSecurity().isEmpty());
        assertEquals(metrics.lifetimePrimarySocialSecurity(), metrics.lifetimeHouseholdSocialSecurity());
    }

    @Test
    void openingDistributionReducesTargetConversionRoomWithoutAnotherWithdrawal() throws Exception {
        var ordinary = plan(false, FilingStatus.SINGLE);
        var satisfied = plan(false, FilingStatus.SINGLE);
        for (var plan : List.of(ordinary, satisfied)) {
            plan.setRothConversionRequest(new RothConversionRequest(true, 2027, BigDecimal.ZERO,
                    RothConversionStopRule.NEVER, RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,
                    RothConversionFrequency.ANNUAL, money("300000")));
        }
        var ira = satisfied.getAccountPortfolio().getAccounts().get(1);
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, money("3000000"), BigDecimal.ZERO));
        var rules = new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository()
                .load("/rules/government-rules-2026.json");
        BigDecimal annual = new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdCalculator()
                .calculate(satisfied, 2027, rules).getAnnualRequirement().getTotalRmd();
        ira.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, money("3000000"), annual));
        var baseline = new ProjectionEngine().project(ordinary).getYearAt(0);
        var actual = new ProjectionEngine().project(satisfied).getYearAt(0);
        assertEquals(0, actual.getRmdDistributedInProjection().signum());
        assertEquals(0, actual.getRmdDistributedBeforeProjection().compareTo(annual));
        assertTrue(actual.getRothConversion().signum() > 0);
        assertTrue(actual.getRothConversion().compareTo(baseline.getRothConversion()) < 0);
        assertEquals(0, actual.getFederalTaxableIncome().compareTo(money("300000")));
        assertEquals(0, baseline.getFederalTaxableIncome().compareTo(money("300000")));
        assertEquals(0, baseline.getRothConversion().subtract(actual.getRothConversion())
                .compareTo(annual.subtract(baseline.getRequiredMinimumDistribution())));
        assertTrue(actual.spouseRothConversion().isEmpty());
        assertEquals(0, ira.getCurrentBalance().compareTo(money("600000")));
    }

    @Test
    void firstHouseholdRmdStopsPrimaryConversionAndFilingStatusRemainsExplicit() {
        var stop = plan(false, FilingStatus.SINGLE);
        stop.setRothConversionRequest(new RothConversionRequest(true, 2027, money("30000"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        for (var year : new ProjectionEngine().project(stop).getYears()) {
            assertTrue(year.getRequiredMinimumDistribution().signum() > 0);
            assertEquals(0, year.getRothConversion().signum());
        }
        var single = new ProjectionEngine().project(plan(false, FilingStatus.SINGLE)).getYearAt(0);
        var jointStatus = new ProjectionEngine().project(plan(false, FilingStatus.MARRIED_FILING_JOINTLY)).getYearAt(0);
        assertEquals(1, jointStatus.getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertTrue(jointStatus.getFederalStandardDeduction().compareTo(single.getFederalStandardDeduction()) > 0);
        assertTrue(jointStatus.getFederalIncomeTax().compareTo(single.getFederalIncomeTax()) < 0);
        assertNotEquals(single.getMonthlyPartBPremium(), jointStatus.getMonthlyPartBPremium());
    }

    @Test
    void fundingFailureHasNoSpouseAgeAndInvalidJointOrSurvivorRecordsAreRejected() {
        var poor = plan(false, FilingStatus.SINGLE);
        for (var income : List.copyOf(poor.getHousehold().getPrimaryPerson().getIncomeSources())) {
            poor.getHousehold().getPrimaryPerson().removeIncomeSource(income);
        }
        for (var account : poor.getAccountPortfolio().getAccounts()) {
            account.setCurrentBalance(BigDecimal.ZERO);
            account.setOpeningRmdAccountData(null);
        }
        poor.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 2, 1));
        var failure = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, new ProjectionEngine().projectWithOutcome(poor));
        assertTrue(failure.fundingFailure().spouseAge().isEmpty());
        assertTrue(failure.fundingFailure().primaryAge().isPresent());
        assertTrue(failure.fundingFailure().shortfallAmount().signum() > 0);
        var joint = plan(false, FilingStatus.SINGLE);
        joint.getAccountPortfolio().addAccount(new BrokerageAccount("Joint", AccountOwnership.JOINT, BigDecimal.ONE));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(joint)).getMessage().contains("Joint"));
        var survivor = plan(false, FilingStatus.SINGLE);
        survivor.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Survivor", AccountOwnership.PRIMARY,
                LocalDate.of(2027, 1, 1), null, money("1000"), BigDecimal.ZERO, money("500")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(survivor)).getMessage().contains("survivor pension"));
    }

    @Test
    void medicareBeginsForOneEligiblePersonOnly() {
        var plan = plan(false, FilingStatus.SINGLE);
        var person = plan.getHousehold().getPrimaryPerson();
        person.setBirthDate(LocalDate.of(1963, 1, 1));
        for (var income : List.copyOf(person.getIncomeSources())) {
            if (income instanceof SocialSecurityIncome) person.removeIncomeSource(income);
        }
        plan.getAccountPortfolio().getAccounts().get(1).setOpeningRmdAccountData(null);
        var result = new ProjectionEngine().project(plan);
        assertEquals(0, result.getYearAt(0).getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertEquals(0, result.getYearAt(0).getAnnualMedicarePremium().signum());
        assertEquals(1, result.getYearAt(1).getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertTrue(result.getYearAt(1).getAnnualMedicarePremium().signum() > 0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {62, 67, 70})
    void ownSocialSecurityMatchesAuthoritativeOwnComponentWithoutSpouseBenefits(int age) {
        var single = plan(false, FilingStatus.SINGLE);
        var couple = plan(true, FilingStatus.MARRIED_FILING_JOINTLY);
        for (var plan : List.of(single, couple)) {
            var person = plan.getHousehold().getPrimaryPerson();
            person.setBirthDate(LocalDate.of(1960, 2, 1));
            var old = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).findFirst().orElseThrow();
            person.replaceIncomeSource(old, new SocialSecurityIncome("Own", AccountOwnership.PRIMARY,
                    SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(person.getBirthDate(), age),
                    null, money("2600"), age, BigDecimal.ZERO, 2027));
        }
        var provider = new SocialSecurityProjectionIncomeProvider();
        var own = provider.calculate(single, 2027, 2031);
        var reference = provider.calculate(couple, 2027, 2031);
        for (int year = 2027; year <= 2031; year++) {
            assertEquals(0, reference.get(year).primaryOwnBenefit().compareTo(own.get(year).householdBenefit()));
            assertNull(own.get(year).primarySpousalExcessBenefit());
            assertNull(own.get(year).spouseSelection());
        }
        if (age == 70) assertEquals(0, own.get(2027).householdBenefit().signum());
        if (age == 67) assertTrue(own.get(2028).householdBenefit().compareTo(own.get(2027).householdBenefit()) > 0);
    }

    @Test
    void saveReloadAndAdditionalPrimaryAccountTypesPreserveProjection(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var plan = plan(false, FilingStatus.SINGLE);
        var pension = plan.getHousehold().getPrimaryPerson().getIncomeSources().getFirst();
        assertInstanceOf(Pension.class, pension);
        plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.CHECKING, "Cash", AccountOwnership.PRIMARY, money("10000")));
        var account = AccountFactory.create(AccountType.TRADITIONAL_401K, "401k", AccountOwnership.PRIMARY, money("80000"));
        account.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, money("80000"), BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(account);
        String before = financialRows(new ProjectionEngine().project(plan));
        var repository = new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository();
        Path file = directory.resolve("single.json");
        repository.save(plan, file);
        var loaded = repository.load(file);
        assertFalse(loaded.getHousehold().hasSpouse());
        assertEquals(before, financialRows(new ProjectionEngine().project(loaded)));
    }

    @Test
    void monteCarloAndIndividualCsvWorkWithoutSpouse(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var plan = plan(false, FilingStatus.SINGLE);
        var projection = new ProjectionEngine().project(plan);
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(1, 417, BigDecimal.ZERO, BigDecimal.ZERO);
        var monteCarlo = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer().analyze(plan, settings);
        assertEquals(1, monteCarlo.completedCount());
        assertFalse(plan.getHousehold().hasSpouse());
        Path csv = directory.resolve("single.csv");
        new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), csv);
        assertTrue(Files.exists(csv));
        assertFalse(Files.readString(csv).contains("Spouse"));
        Path pdf = directory.resolve("single.pdf");
        assertThrows(NullPointerException.class,
                () -> new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter().export(plan, projection, List.of(), null, pdf));
        assertFalse(Files.exists(pdf));
    }

    @Test
    void singleConversionStopsAtPrimarySourceCapacity() {
        var plan = plan(false, FilingStatus.SINGLE);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, money("5000000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(year.getRothConversion().signum() > 0);
        assertTrue(year.getRothConversion().compareTo(year.getRequestedRothConversion()) < 0);
        assertEquals(0, year.getEndingBalance(plan.getAccountPortfolio().getAccounts().get(1)).signum());
        assertTrue(year.spouseRothConversion().isEmpty());
        assertEquals(0, year.getPrimaryRothConversion().compareTo(year.getRothConversion()));
        assertTrue(year.getAdjustedGrossIncome().compareTo(year.getRothConversion()) > 0);
    }
}
