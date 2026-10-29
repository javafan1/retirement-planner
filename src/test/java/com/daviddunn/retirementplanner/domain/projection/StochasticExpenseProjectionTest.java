package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.junit.jupiter.api.Assertions.*;

class StochasticExpenseProjectionTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    static RetirementPlan plan(int birthYear, boolean ira, LocalDate start, int years) {
        var household = new Household(new Person("Primary", "Test", LocalDate.of(birthYear, 1, 1)),
                new Person("Spouse", "Test", LocalDate.of(1990, 1, 1)));
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(ira ? new TraditionalIRA("Assets", AccountOwnership.PRIMARY, new BigDecimal("1000000"))
                : new BrokerageAccount("Assets", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        return new RetirementPlan(household, portfolio, new PlanningAssumptions(BigDecimal.ZERO,
                BigDecimal.ZERO, years, start));
    }

    static ProjectionExpensePath path(RetirementPlan plan, int first, int last, String... multipliers) {
        var occurrence = new ProjectionExpensePath.Occurrence("a".repeat(64), 0);
        return new ProjectionExpensePath(first, last, Map.of(occurrence,
                Arrays.stream(multipliers).map(BigDecimal::new).toList()))
                .bind(plan.getHousehold().getExpenses(), List.of(Optional.of(occurrence)));
    }

    @ParameterizedTest @EnumSource(GrowthCategory.class)
    void escalationShockThenPartialYearKeepsOriginalRounding(GrowthCategory category) {
        var plan = plan(1980, false, LocalDate.of(2027, 7, 1), 3);
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(BigDecimal.ZERO,
                new BigDecimal(".10"), new BigDecimal(".20"), BigDecimal.ZERO), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), 3, a.getProjectionStartDate()));
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("1000.0000"), category));
        var context = ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2029, "2", "3", ".5"));
        var result = new ProjectionEngine().project(plan, context);
        money("1000", result.getYearAt(0).getAnnualExpenses());
        money(category == GrowthCategory.GENERAL ? "3300" : category == GrowthCategory.HEALTHCARE ? "3600" : "3000",
                result.getYearAt(1).getAnnualExpenses());
        money(category == GrowthCategory.GENERAL ? "605" : category == GrowthCategory.HEALTHCARE ? "720" : "500",
                result.getYearAt(2).getAnnualExpenses());
    }

    @Test void shockedSpendingPropagatesToWithdrawalsBalancesNetWorthAndEstateWithoutTaxDeduction() {
        var plan = plan(1980, false, LocalDate.of(2027, 1, 1), 2);
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("10000"), GrowthCategory.NONE));
        var engine = new ProjectionEngine();
        var base = engine.project(plan);
        var shock = engine.project(plan, ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2028, "2", "1")));
        var first = shock.getFirstYear();
        money("20000", first.getAnnualExpenses()); money("20000", first.getCashFlowNeed());
        money("20000", first.getPortfolioWithdrawal()); money("0", first.getTotalIncomeTax());
        money("980000", first.getEndingInvestableAssets());
        money("970000", new com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator()
                .calculate(plan, shock).endingNetWorth());
        money("980000", first.getAfterTaxEstateValue());
        money("970000", shock.getYearAt(1).getEndingInvestableAssets());
        money("10000", base.getLastYear().getEndingInvestableAssets().subtract(shock.getLastYear().getEndingInvestableAssets()));
        money("1000000", plan.getAccountPortfolio().getTotalBalance());
    }

    @Test void coverageAndFrozenBindingAreValidatedBeforeFinancialExecution() {
        var plan = plan(1980, false, LocalDate.of(2027, 1, 1), 2);
        var expense = new Expense("Spending", new BigDecimal("1000")); plan.getHousehold().addExpense(expense);
        var shortPath = path(plan, 2027, 2027, "2");
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(plan,
                ProjectionEvaluationContext.empty().withExpensePath(shortPath)));
        var valid = path(plan, 2027, 2028, "2", "2");
        plan.getHousehold().replaceExpense(expense, new Expense("Spending", new BigDecimal("2000")));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(plan,
                ProjectionEvaluationContext.empty().withExpensePath(valid)));
    }

    @Test void datesDeathZeroAndOneTimeRulesRemainIndependentOfSampling() {
        var plan = plan(1980, false, LocalDate.of(2027, 7, 1), 3);
        for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse())) {
            var owner = person == plan.getHousehold().getPrimaryPerson() ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
            person.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome("SS", owner,
                    person.getBirthDate().plusYears(67), null, BigDecimal.ZERO, 67, BigDecimal.ZERO, 2027));
        }
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 67,
                new BigDecimal(".75")), 3, a.getProjectionStartDate()));
        plan.getHousehold().addExpense(new Expense("Future", new BigDecimal("1000"), GrowthCategory.NONE,
                LocalDate.of(2028, 7, 1), LocalDate.of(2028, 12, 1), ExpenseType.RECURRING));
        plan.getHousehold().addExpense(new Expense("Zero", new BigDecimal("0.0000"), GrowthCategory.NONE));
        plan.getHousehold().addExpense(new Expense("Purchase", new BigDecimal("500"), GrowthCategory.GENERAL,
                LocalDate.of(2028, 7, 1), LocalDate.of(2028, 7, 1), ExpenseType.ONE_TIME));
        var x = new ProjectionExpensePath.Occurrence("a".repeat(64), 0);
        var z = new ProjectionExpensePath.Occurrence("b".repeat(64), 0);
        var path = new ProjectionExpensePath(2027, 2029, Map.of(x, List.of(new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("2")),
                z, List.of(new BigDecimal("1E+1000"), new BigDecimal("1E+1000"), new BigDecimal("1E+1000"))))
                .bind(plan.getHousehold().getExpenses(), List.of(Optional.of(x), Optional.of(z), Optional.empty()));
        var context = ProjectionEvaluationContext.withLifetimeScenario(new HouseholdLifetimeScenario(
                Optional.of(Year.of(2028)), Optional.of(Year.of(2029)))).withExpensePath(path);
        var result = new ProjectionEngine().project(plan, context);
        money("0", result.getYearAt(0).getAnnualExpenses());
        money("2000", result.getYearAt(1).getAnnualExpenses()); // 1000*2*.75 + 500 purchase.
        money("0", result.getYearAt(2).getAnnualExpenses());
    }

    @Test void medicareRetryAndRepeatedExecutionReuseTheOriginalFixedShock() throws Exception {
        var plan = plan(1950, false, LocalDate.of(2027, 1, 1), 2);
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("1000"), GrowthCategory.NONE));
        var context = ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2028, "2", "2"));
        var engine = new ProjectionEngine();
        var result = engine.project(plan, context);
        assertTrue(result.getFirstYear().getAnnualMedicarePremium().signum() > 0);
        for (var row : result.getYears()) {
            money("2000", row.getAnnualExpenses());
            assertEquals(0, row.getCashFlowNeed().compareTo(new BigDecimal("2000").add(row.getAnnualMedicarePremium())));
        }
        assertEquals(JSON.valueToTree(result.getYears()), JSON.valueToTree(engine.project(plan, context).getYears()));
        assertEquivalentFixedBudget(plan, result, "2000");
    }

    @Test void taxFundingAndBracketFillRespondToActualSpendingWithoutChangingTheirFormulas() throws Exception {
        var plan = plan(1964, true, LocalDate.of(2027, 1, 1), 2);
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ANNUAL));
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("60000"), GrowthCategory.NONE));
        var engine = new ProjectionEngine();
        var base = engine.project(plan);
        var result = engine.project(plan, ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2028, "2", "2")));
        var row = result.getFirstYear();
        money("120000", row.getAnnualExpenses()); money("120000", row.getCashFlowNeed());
        assertTrue(row.getTaxFundingWithdrawal().signum() > 0);
        // Existing tax-funding iteration/settlement contract permits up to two cents of residual.
        assertTrue(row.getTotalIncomeTax().subtract(row.getTaxFundingWithdrawal()).abs().compareTo(new BigDecimal(".02")) <= 0);
        assertEquals(0, row.getPortfolioWithdrawal().compareTo(row.getCashFlowNeed().add(row.getTaxFundingWithdrawal())));
        assertTrue(row.getRothConversion().compareTo(base.getFirstYear().getRothConversion()) < 0);
        assertEquals(row.getBeginningInvestableAssets().subtract(row.getPortfolioWithdrawal())
                .add(row.getRetainedHouseholdSurplus()).setScale(2, java.math.RoundingMode.HALF_UP), row.getEndingInvestableAssets());
        assertEquivalentFixedBudget(plan, result, "120000");
    }

    @Test void laterRmdUsesShockedPriorDecemberBalancesWhileCurrentStatutoryRmdIsUnchanged() {
        var plan = plan(1952, true, LocalDate.of(2027, 1, 1), 2);
        var account = plan.getAccountPortfolio().getAccounts().getFirst();
        account.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, new BigDecimal("1000000"), BigDecimal.ZERO));
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("100000"), GrowthCategory.NONE));
        var engine = new ProjectionEngine();
        var base = engine.project(plan);
        var result = engine.project(plan, ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2028, "2", "1")));
        assertEquals(base.getFirstYear().getRequiredMinimumDistribution(), result.getFirstYear().getRequiredMinimumDistribution());
        assertTrue(result.getYearAt(1).getRequiredMinimumDistribution().compareTo(base.getYearAt(1).getRequiredMinimumDistribution()) < 0);
        assertEquals(result.getFirstYear().getEndingInvestableAssets(),
                result.getYearAt(1).getBeginningInvestableAssets().setScale(2, java.math.RoundingMode.HALF_UP));
        var exactEnding = result.getFirstYear().getEndingAccountSnapshots().stream()
                .map(ProjectedAccountSnapshot::getEndingBalance).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(result.getFirstYear().getEndingRetainedNonQualifiedAssets());
        assertEquals(exactEnding, result.getYearAt(1).getBeginningInvestableAssets());
        assertTrue(result.getFirstYear().getAfterTaxEstateValue().compareTo(base.getFirstYear().getAfterTaxEstateValue()) < 0);
        money("1000000", account.getCurrentBalance());
    }

    @Test void shockedFundingFailureRemainsStructuredAndZeroExpenseKeepsItsExactFinancialRepresentation() {
        var plan = plan(1980, false, LocalDate.of(2027, 1, 1), 1);
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("600000"), GrowthCategory.NONE));
        var outcome = new ProjectionEngine().projectWithOutcome(plan,
                ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2027, "2")), ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var failed = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, outcome);
        money("1200000", failed.fundingFailure().requiredAmount());
        money("1000000", failed.fundingFailure().availableAmount());
        money("200000", failed.fundingFailure().shortfallAmount());
        assertTrue(failed.completedYears().isEmpty());
        var expense = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(expense, new Expense("Zero", new BigDecimal("0.0000"), GrowthCategory.NONE));
        var engine = new ProjectionEngine();
        assertEquals(JSON.valueToTree(engine.project(plan).getYears()), JSON.valueToTree(engine.project(plan,
                ProjectionEvaluationContext.empty().withExpensePath(path(plan, 2027, 2027, "1E+1000"))).getYears()));
    }

    @Test void everyContextBuilderOrderingPreservesExpensePathAndExistingOverrides() {
        var plan = plan(1980, false, LocalDate.of(2027, 1, 1), 2);
        plan.getHousehold().addExpense(new Expense("Spending", BigDecimal.ONE));
        var path = path(plan, 2027, 2028, "2", "2");
        var inflation = new ProjectionInflationPath(List.of(new ProjectionInflationPath.AnnualRate(2027, BigDecimal.ZERO),
                new ProjectionInflationPath.AnnualRate(2028, BigDecimal.ZERO)));
        var actions = List.<java.util.function.UnaryOperator<ProjectionEvaluationContext>>of(
                c -> c.withInflationPath(inflation), c -> c.withEndingYear(2028), c -> c.withExactEndingYear(2028),
                c -> c.withSurvivorClaimingAge(67));
        checkBuilders(ProjectionEvaluationContext.withLifetimeScenario(HouseholdLifetimeScenario.bothSurvive()).withExpensePath(path), actions, path);
        assertTrue(ProjectionEvaluationContext.empty().expensePath().isEmpty());
        assertTrue(new ProjectionEvaluationContext(Optional.empty(), Optional.empty(), Optional.empty(),
                ProjectionEvaluationContext.HorizonPolicy.CONFIGURED, Optional.empty(), Optional.empty()).expensePath().isEmpty());
    }

    private static void checkBuilders(ProjectionEvaluationContext context,
            List<java.util.function.UnaryOperator<ProjectionEvaluationContext>> remaining, ProjectionExpensePath path) {
        assertSame(path, context.expensePath().orElseThrow());
        for (int index = 0; index < remaining.size(); index++) {
            var next = new ArrayList<>(remaining); var action = next.remove(index);
            checkBuilders(action.apply(context), next, path);
        }
    }

    private static void assertEquivalentFixedBudget(RetirementPlan plan, Projection result, String amount) throws Exception {
        var copy = JSON.readValue(JSON.writeValueAsBytes(plan), RetirementPlan.class);
        var original = copy.getHousehold().getExpenses().getFirst();
        copy.getHousehold().replaceExpense(original, new Expense(original.getDescription(), new BigDecimal(amount), GrowthCategory.NONE));
        assertEquals(JSON.valueToTree(new ProjectionEngine().project(copy).getYears()), JSON.valueToTree(result.getYears()));
    }

    @Test void unboundPathsWrongBindingsAndOneTimeBindingsFailExplicitly() {
        var plan = plan(1980, false, LocalDate.of(2027, 1, 1), 1);
        plan.getHousehold().addExpense(new Expense("Spending", BigDecimal.ONE));
        var occurrence = new ProjectionExpensePath.Occurrence("a".repeat(64), 0);
        var path = new ProjectionExpensePath(2027, 2027, Map.of(occurrence, List.of(BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(plan,
                ProjectionEvaluationContext.empty().withExpensePath(path)));
        assertThrows(IllegalArgumentException.class, () -> path.bind(plan.getHousehold().getExpenses(), List.of(Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> path.bind(plan.getHousehold().getExpenses(), List.of()));
        var oneTime = new Expense("Purchase", BigDecimal.ONE, GrowthCategory.GENERAL,
                LocalDate.of(2027, 1, 1), null, ExpenseType.ONE_TIME);
        assertThrows(IllegalArgumentException.class, () -> path.bind(List.of(oneTime), List.of(Optional.of(occurrence))));
    }

    static void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual), actual.toPlainString()); }
}
