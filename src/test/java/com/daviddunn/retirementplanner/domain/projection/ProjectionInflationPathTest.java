package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFixtures;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionInflationPathTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    static ProjectionInflationPath constant(int first, int last, String rate) {
        var years = new ArrayList<ProjectionInflationPath.AnnualRate>();
        for (int year = first; year <= last; year++) {
            years.add(new ProjectionInflationPath.AnnualRate(year, new BigDecimal(rate)));
        }
        return new ProjectionInflationPath(years);
    }

    @Test
    void constantPathMatchesEveryAnnualFieldWithoutPlanMutation() throws Exception {
        var plan = MonteCarloFixtures.household();
        String before = JSON.writeValueAsString(plan);
        var engine = new ProjectionEngine();
        var actual = engine.project(plan, ProjectionEvaluationContext.empty()
                .withInflationPath(constant(2027, 2056, "0.02")));
        assertEquals(JSON.valueToTree(engine.project(plan).getYears()), JSON.valueToTree(actual.getYears()));
        assertEquals(before, JSON.writeValueAsString(plan));
    }

    @Test
    void varyingRatesCompoundFromOpeningIncludingDelayedAndOneTimeButNotHealthcare() {
        var plan = MonteCarloFixtures.household();
        for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
        plan.setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.045"), new BigDecimal("0.02"),
                3, LocalDate.of(2027, 7, 1)));
        plan.getHousehold().addExpense(new Expense("General", new BigDecimal("10000")));
        plan.getHousehold().addExpense(new Expense("Healthcare", new BigDecimal("1000"), GrowthCategory.HEALTHCARE));
        plan.getHousehold().addExpense(new Expense("Delayed", new BigDecimal("2000"), GrowthCategory.GENERAL,
                LocalDate.of(2029, 1, 1), null, ExpenseType.RECURRING));
        plan.getHousehold().addExpense(new Expense("One time", new BigDecimal("3000"), GrowthCategory.GENERAL,
                LocalDate.of(2029, 1, 1), null, ExpenseType.ONE_TIME));
        var path = new ProjectionInflationPath(List.of(
                new ProjectionInflationPath.AnnualRate(2027, new BigDecimal("0.90")),
                new ProjectionInflationPath.AnnualRate(2028, new BigDecimal("0.10")),
                new ProjectionInflationPath.AnnualRate(2029, new BigDecimal("-0.02"))));
        var engine = new ProjectionEngine();
        var actual = engine.project(plan, ProjectionEvaluationContext.empty().withInflationPath(path));
        assertEquals(new BigDecimal("5500.00"), actual.getYearAt(0).getAnnualExpenses());
        assertEquals(new BigDecimal("12020.00"), actual.getYearAt(1).getAnnualExpenses());
        assertEquals(new BigDecimal("17210.40"), actual.getYearAt(2).getAnnualExpenses());
        var deterministic = engine.project(plan);
        for (int i = 0; i < 3; i++) {
            assertEquals(deterministic.getYearAt(i).getSocialSecurityResult(), actual.getYearAt(i).getSocialSecurityResult());
        }
        assertEquals(2029, actual.getEndYear());
    }

    @Test
    void contextCompositionPreservesPathSurvivorAgeAndExactHorizon() {
        var path = constant(2027, 2030, "0.02");
        var lifetime = new HouseholdLifetimeScenario(Optional.of(Year.of(2029)), Optional.of(Year.of(2031)));
        var context = ProjectionEvaluationContext.withLifetimeScenario(lifetime).withInflationPath(path)
                .withSurvivorClaimingAge(67).withEndingYear(2060).withExactEndingYear(2030);
        assertSame(path, context.inflationPath().orElseThrow());
        assertEquals(67, context.survivorClaimingAge().orElseThrow());
        var result = new ProjectionEngine().project(MonteCarloFixtures.household(), context);
        assertEquals(2030, result.getEndYear());
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(
                MonteCarloFixtures.household(), context.withExactEndingYear(2031)));
    }

    @Test
    void rejectsDuplicatesNullsInvalidRatesAndMissingYearsAndIsImmutable() {
        var rate = new ProjectionInflationPath.AnnualRate(2027, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new ProjectionInflationPath(List.of(rate, rate)));
        assertThrows(NullPointerException.class, () -> new ProjectionInflationPath(null));
        assertThrows(NullPointerException.class, () -> new ProjectionInflationPath(Arrays.asList(rate, null)));
        assertThrows(NullPointerException.class, () -> new ProjectionInflationPath.AnnualRate(2027, null));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionInflationPath.AnnualRate(2027, new BigDecimal("-1")));
        var list = new ArrayList<>(List.of(rate));
        var path = new ProjectionInflationPath(list);
        list.clear();
        assertEquals(BigDecimal.ZERO, path.inflationForYear(2027));
        assertThrows(UnsupportedOperationException.class, () -> path.annualRates().clear());
        assertThrows(IllegalArgumentException.class, () -> path.inflationForYear(2026));
        assertThrows(IllegalArgumentException.class, () -> path.inflationForYear(2028));
        assertThrows(IllegalArgumentException.class, () -> path.requireCoverage(2027, 2028));
    }
}

