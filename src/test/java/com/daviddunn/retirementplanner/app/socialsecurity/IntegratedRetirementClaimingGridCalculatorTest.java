package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedRetirementClaimingGridCalculatorTest {

    private final IntegratedRetirementClaimingGridCalculator calculator =
            new IntegratedRetirementClaimingGridCalculator();
    private final IntegratedSocialSecurityStrategyEvaluator evaluator =
            new IntegratedSocialSecurityStrategyEvaluator();

    @Test
    void standardGridEvaluatesAllEightyOneCellsInRowMajorOrderAndPreservesPlan()
            throws Exception {
        RetirementPlan plan = plan();
        String before = json(plan);
        IntegratedSocialSecurityStrategyResult baselineBefore =
                evaluator.evaluateCurrentStrategy(plan);

        IntegratedRetirementClaimingGridResult result = calculator.calculate(plan);

        assertEquals(81, result.cells().size());
        assertEquals(81, result.successfulCellCount());
        assertEquals(0, result.failedCellCount());
        assertEquals(List.of(62, 63, 64, 65, 66, 67, 68, 69, 70),
                result.primaryRetirementAges());
        assertEquals(62, result.cells().get(0).primaryRetirementAge());
        assertEquals(62, result.cells().get(0).spouseRetirementAge());
        assertEquals(62, result.cells().get(8).primaryRetirementAge());
        assertEquals(70, result.cells().get(8).spouseRetirementAge());
        assertEquals(70, result.cells().get(72).primaryRetirementAge());
        assertEquals(62, result.cells().get(72).spouseRetirementAge());
        assertEquals(70, result.cells().get(80).primaryRetirementAge());
        assertEquals(70, result.cells().get(80).spouseRetirementAge());

        assertEquals(LocalDate.of(2024, 12, 31),
                result.cellFor(62, 62).orElseThrow().primaryRetirementClaimDate());
        assertEquals(LocalDate.of(2034, 2, 28),
                result.cellFor(62, 70).orElseThrow().spouseRetirementClaimDate());
        assertEquals(LocalDate.of(2032, 12, 31),
                result.cellFor(70, 62).orElseThrow().primaryRetirementClaimDate());
        assertEquals(LocalDate.of(2034, 2, 28),
                result.cellFor(70, 70).orElseThrow().spouseRetirementClaimDate());

        for (IntegratedRetirementClaimingGridCell cell : result.cells()) {
            assertSame(result.survivorPolicy().primaryElection(),
                    cell.strategy().primarySurvivorElection());
            assertSame(result.survivorPolicy().spouseElection(),
                    cell.strategy().spouseSurvivorElection());
            assertTrue(cell.integratedResult().orElseThrow().projection().size() > 0);
        }

        IntegratedRetirementClaimingGridCell early = result.cellFor(62, 62).orElseThrow();
        IntegratedRetirementClaimingGridCell late = result.cellFor(70, 70).orElseThrow();
        ProjectionMetrics earlyMetrics = early.integratedResult().orElseThrow().metrics();
        ProjectionMetrics lateMetrics = late.integratedResult().orElseThrow().metrics();
        assertNotEquals(earlyMetrics.lifetimeHouseholdSocialSecurity(),
                lateMetrics.lifetimeHouseholdSocialSecurity());
        assertNotEquals(earlyMetrics.totalTaxes(), lateMetrics.totalTaxes());
        assertNotEquals(earlyMetrics.lifetimePortfolioWithdrawals(),
                lateMetrics.lifetimePortfolioWithdrawals());
        assertNotEquals(earlyMetrics.afterTaxEstate(), lateMetrics.afterTaxEstate());

        ProjectionMetrics baseline = result.currentPlanBaseline().metrics();
        ProjectionMetrics difference = early.differencesFromCurrentPlan().orElseThrow();
        assertEquals(earlyMetrics.lifetimeHouseholdSocialSecurity()
                        .subtract(baseline.lifetimeHouseholdSocialSecurity()),
                difference.lifetimeHouseholdSocialSecurity());
        assertEquals(earlyMetrics.totalTaxes().subtract(baseline.totalTaxes()),
                difference.totalTaxes());
        assertEquals(earlyMetrics.afterTaxEstate().subtract(baseline.afterTaxEstate()),
                difference.afterTaxEstate());

        IntegratedSocialSecurityStrategyResult direct = evaluator.evaluate(plan, early.strategy());
        assertEquals(direct.metrics(), earlyMetrics);
        assertEquals(direct.projection().size(),
                early.integratedResult().orElseThrow().projection().size());
        for (int index = 0; index < direct.projection().size(); index++) {
            var directYear = direct.projection().getYearAt(index);
            var gridYear = early.integratedResult().orElseThrow().projection().getYearAt(index);
            assertEquals(directYear.getSocialSecurityResult(), gridYear.getSocialSecurityResult());
            assertEquals(directYear.getGuaranteedIncome(), gridYear.getGuaranteedIncome());
            assertEquals(directYear.getTotalIncomeTax(), gridYear.getTotalIncomeTax());
            assertEquals(directYear.getEndingInvestableAssets(),
                    gridYear.getEndingInvestableAssets());
            assertEquals(directYear.getAfterTaxEstateValue(), gridYear.getAfterTaxEstateValue());
        }
        assertEquals(baselineBefore.metrics(), result.currentPlanBaseline().metrics());
        assertEquals(before, json(plan));
    }

    @Test
    void repeatedGridAndUnderlyingAbaEvaluationsAreDeterministic() {
        RetirementPlan plan = plan();
        IntegratedRetirementClaimingGridResult first = calculator.calculate(plan);
        IntegratedRetirementClaimingGridResult second = calculator.calculate(plan);

        assertEquals(first.cells().stream().map(IntegratedRetirementClaimingGridCell::strategy).toList(),
                second.cells().stream().map(IntegratedRetirementClaimingGridCell::strategy).toList());
        assertEquals(first.cells().stream().map(cell ->
                        cell.integratedResult().orElseThrow().metrics()).toList(),
                second.cells().stream().map(cell ->
                        cell.integratedResult().orElseThrow().metrics()).toList());

        SocialSecurityHouseholdClaimingStrategy a =
                first.cellFor(62, 62).orElseThrow().strategy();
        SocialSecurityHouseholdClaimingStrategy b =
                first.cellFor(70, 70).orElseThrow().strategy();
        ProjectionMetrics firstA = evaluator.evaluate(plan, a).metrics();
        evaluator.evaluate(plan, b);
        ProjectionMetrics secondA = evaluator.evaluate(plan, a).metrics();
        assertEquals(firstA, secondA);
    }

    @Test
    void customRequestPreservesCallerRowAndColumnOrder() {
        RetirementPlan plan = plan();
        IntegratedRetirementClaimingGridSurvivorPolicy policy =
                calculator.survivorPolicy(plan);
        IntegratedRetirementClaimingGridRequest request =
                new IntegratedRetirementClaimingGridRequest(
                        plan, List.of(70, 62), List.of(69, 63), policy);

        IntegratedRetirementClaimingGridResult result = calculator.calculate(request);

        assertEquals(List.of("70/69", "70/63", "62/69", "62/63"),
                result.cells().stream().map(cell -> cell.primaryRetirementAge()
                        + "/" + cell.spouseRetirementAge()).toList());
    }

    @Test
    void requestRejectsInvalidOrDuplicateAges() {
        RetirementPlan plan = plan();
        IntegratedRetirementClaimingGridSurvivorPolicy policy =
                calculator.survivorPolicy(plan);
        assertThrows(IllegalArgumentException.class,
                () -> new IntegratedRetirementClaimingGridRequest(
                        plan, List.of(61), List.of(62), policy));
        assertThrows(IllegalArgumentException.class,
                () -> new IntegratedRetirementClaimingGridRequest(
                        plan, List.of(62, 62), List.of(62), policy));
    }

    @Test
    void measuresSequentialBaselineNineCellAndFullGridRuntime() {
        RetirementPlan plan = plan();
        Instant start = Instant.now();
        evaluator.evaluateCurrentStrategy(plan);
        long baselineMillis = Duration.between(start, Instant.now()).toMillis();

        IntegratedRetirementClaimingGridSurvivorPolicy policy = calculator.survivorPolicy(plan);
        start = Instant.now();
        calculator.calculate(new IntegratedRetirementClaimingGridRequest(
                plan, List.of(62), List.of(62, 63, 64, 65, 66, 67, 68, 69, 70), policy));
        long nineCellMillis = Duration.between(start, Instant.now()).toMillis();

        start = Instant.now();
        IntegratedRetirementClaimingGridResult full = calculator.calculate(plan);
        long fullGridMillis = Duration.between(start, Instant.now()).toMillis();

        assertEquals(81, full.cells().size());
        System.out.printf(
                "Integrated retirement grid runtime: baseline=%d ms, baseline+9=%d ms, baseline+81=%d ms%n",
                baselineMillis, nineCellMillis, fullGridMillis);
        for (int primaryAge : List.of(62, 66, 70)) {
            System.out.print("After-tax estate age " + primaryAge + ":");
            for (int spouseAge : List.of(62, 66, 70)) {
                BigDecimal estate = full.cellFor(primaryAge, spouseAge).orElseThrow()
                        .integratedResult().orElseThrow().metrics().afterTaxEstate();
                System.out.print(" " + spouseAge + "=" + estate.setScale(0,
                        java.math.RoundingMode.HALF_UP));
            }
            System.out.println();
        }
    }

    private RetirementPlan plan() {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 1, 1));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1964, 2, 29));
        primary.addIncomeSource(new Pension(
                "Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1),
                null, new BigDecimal("5000"), BigDecimal.ZERO));
        primary.addIncomeSource(source(primary, AccountOwnership.PRIMARY, "3000", 67));
        spouse.addIncomeSource(source(spouse, AccountOwnership.SPOUSE, "1200", 62));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("80000")));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, new BigDecimal("750000")));
        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal("0.03"), new BigDecimal("0.02"),
                        BigDecimal.ZERO, new BigDecimal("0.02")),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                new DeathScenarioAssumptions(DeathScenario.SPOUSE_DIES, 2040, 67),
                25, LocalDate.of(2026, 7, 1));
        return new RetirementPlan(household, portfolio, assumptions);
    }

    private SocialSecurityIncome source(
            Person person,
            AccountOwnership owner,
            String benefit,
            int age) {
        return new SocialSecurityIncome(
                "Social Security", owner,
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                        person.getBirthDate(), age),
                null, new BigDecimal(benefit), age, BigDecimal.ZERO, 2025);
    }

    private String json(RetirementPlan plan) throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(plan);
    }
}
