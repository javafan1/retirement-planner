package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFixtures;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseEscalationTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private static RetirementPlan plan() {
        var plan = MonteCarloFixtures.household();
        for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
        plan.setRothConversionRequest(null);
        plan.setPlanningAssumptions(new PlanningAssumptions(
                new EconomicAssumptions(BigDecimal.ZERO, new BigDecimal("0.10"), new BigDecimal("0.20"), BigDecimal.ZERO),
                plan.getPlanningAssumptions().getTaxAssumptions(), plan.getPlanningAssumptions().getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, 67, new BigDecimal("0.75")),
                3, LocalDate.of(2027, 7, 1)));
        return plan;
    }

    private static Expense expense(String name, String category, LocalDate start, LocalDate end, ExpenseType type) {
        return new Expense(name, new BigDecimal("1000.0000"), GrowthCategory.valueOf(category), start, end, type);
    }

    @Test void generalHealthcareAndNoneUseOneRateAndRetainOpeningYearProration() throws Exception {
        var plan = plan();
        for (var category : List.of("GENERAL", "HEALTHCARE", "NONE")) {
            plan.getHousehold().addExpense(expense(category, category, null, null, ExpenseType.RECURRING));
        }
        String before = JSON.writeValueAsString(plan);
        var result = new ProjectionEngine().project(plan);
        assertEquals(List.of(new BigDecimal("1500.00"), new BigDecimal("3300.00"), new BigDecimal("3650.00")),
                result.getYears().stream().map(ProjectionYear::getAnnualExpenses).toList());
        assertEquals(before, JSON.writeValueAsString(plan));
        assertEquals(JSON.valueToTree(result.getYears()), JSON.valueToTree(new ProjectionEngine().project(plan).getYears()));
    }

    @Test void delayedRecurringAmountsStayInOpeningYearDollarsAndOverlapIsNotNewProration() {
        var plan = plan();
        var start = LocalDate.of(2029, 7, 20);
        var end = LocalDate.of(2029, 8, 1);
        plan.getHousehold().addExpense(expense("Flat", "NONE", start, end, ExpenseType.RECURRING));
        plan.getHousehold().addExpense(expense("General", "GENERAL", start, end, ExpenseType.RECURRING));
        plan.getHousehold().addExpense(expense("Purchase", "GENERAL", start, start, ExpenseType.ONE_TIME));
        var result = new ProjectionEngine().project(plan);
        assertEquals(new BigDecimal("0.00"), result.getYearAt(0).getAnnualExpenses());
        assertEquals(new BigDecimal("0.00"), result.getYearAt(1).getAnnualExpenses());
        // 1000 flat + 1000 * 1.10^2 recurring + 1000 * 1.10^2 one-time.
        assertEquals(new BigDecimal("3420.00"), result.getYearAt(2).getAnnualExpenses());
    }

    @Test void noInflationDoesNotDisableSurvivorAdjustmentOrHouseholdDeathOmission() {
        var plan = plan();
        plan.getHousehold().addExpense(expense("Flat", "NONE", null, null, ExpenseType.RECURRING));
        var context = ProjectionEvaluationContext.withLifetimeScenario(new HouseholdLifetimeScenario(
                Optional.of(Year.of(2028)), Optional.of(Year.of(2029))));
        var result = new ProjectionEngine().project(plan, context);
        assertEquals(new BigDecimal("500.00"), result.getYearAt(0).getAnnualExpenses());
        assertEquals(new BigDecimal("750.00"), result.getYearAt(1).getAnnualExpenses());
        assertEquals(new BigDecimal("0.00"), result.getYearAt(2).getAnnualExpenses());
    }

    @Test void noneIgnoresExistingSpendingInflationPathWhileOtherModesRetainTheirSource() {
        var plan = plan();
        for (var category : List.of("GENERAL", "HEALTHCARE", "NONE")) {
            plan.getHousehold().addExpense(expense(category, category, null, null, ExpenseType.RECURRING));
        }
        var path = new ProjectionInflationPath(List.of(
                new ProjectionInflationPath.AnnualRate(2027, new BigDecimal("0.90")),
                new ProjectionInflationPath.AnnualRate(2028, new BigDecimal("0.50")),
                new ProjectionInflationPath.AnnualRate(2029, BigDecimal.ZERO)));
        var result = new ProjectionEngine().project(plan, ProjectionEvaluationContext.empty().withInflationPath(path));
        assertEquals(new BigDecimal("1500.00"), result.getYearAt(0).getAnnualExpenses());
        assertEquals(new BigDecimal("3700.00"), result.getYearAt(1).getAnnualExpenses());
        assertEquals(new BigDecimal("3940.00"), result.getYearAt(2).getAnnualExpenses());
    }

    @Test void noneIsRecurringOnlyAndLegacyNullFieldsKeepTheirDefaults() throws Exception {
        var legacy = JSON.readValue("{\"description\":\"Legacy\",\"annualAmount\":123.4500}", Expense.class);
        assertEquals(GrowthCategory.GENERAL, legacy.getGrowthCategory());
        assertEquals(ExpenseType.RECURRING, legacy.getExpenseType());
        assertEquals(new BigDecimal("123.4500"), legacy.getAnnualAmount());
        assertThrows(IllegalArgumentException.class, () -> expense("Purchase", "NONE", null, null, ExpenseType.ONE_TIME));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void flatRecurringSpendingWorksForBothHouseholdShapes(boolean couple) {
        var plan = com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.plan(couple);
        for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
        plan.getHousehold().addExpense(expense("Flat", "NONE", null, null, ExpenseType.RECURRING));
        var result = new ProjectionEngine().project(plan);
        assertTrue(result.size() > 1);
        for (int i = 1; i < result.size(); i++) assertEquals(new BigDecimal("1000.00"), result.getYearAt(i).getAnnualExpenses());
    }
}
