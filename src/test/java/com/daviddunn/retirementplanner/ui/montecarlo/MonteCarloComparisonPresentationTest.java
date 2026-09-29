package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloStrategyComparisonPresentation.*;

class MonteCarloComparisonPresentationTest {
    @Test void fundingFractionsAndFourStatesAreVisibleWithExplicitUnits() {
        var s = new MonteCarloPairedStateSummary(1000, 970, 14, 9, 7);
        assertTrue(funding(s).contains("98.4%")); assertTrue(funding(s).contains("97.9%"));
        assertTrue(funding(s).contains("+0.5 percentage points"));
        assertEquals("\u22120.5 percentage points", difference(new BigDecimal("-0.005")));
        for (String text : List.of("970 (97.00%)", "14 (1.40%)", "9 (0.90%)", "7 (0.70%)", "1,000 requested")) {
            assertTrue(states(s).contains(text), text);
        }
    }

    @Test void financialMetricDetailsUsePairedValuesAndNeutralTaxDirection() {
        var row = new Metric("Lifetime Modeled Income Taxes", MonteCarloComparisonFixtures.metric(), true);
        assertEquals("+$50", row.median()); assertEquals("+$100", row.mean());
        assertEquals("50.00%", row.greater());
        for (String text : List.of("P10 \u2212$140", "P25 \u2212$50", "P75 +$200", "P90 +$380")) assertTrue(row.quantiles().contains(text), row.quantiles());
        assertTrue(row.relations().contains("paid more / equal / less"));
        assertTrue(row.relations().contains("50.00% / 25.00% / 25.00%"));
        assertTrue(row.direction().contains("paid MORE"));
        assertTrue(NOMINAL.contains("not discounted"));
        assertTrue(percentileHelp("P10").contains("10% of comparable"));
        assertTrue(percentileHelp("P90").contains("90% of comparable"));
        assertTrue(percentileHelp("Median").contains("Half"));
    }

    @Test void missingPopulationIsUnavailableAndAnnualCountsAreAuthoritative() {
        var empty = new MonteCarloPairedMetricSummary(0, Optional.empty(), Optional.empty(), 0, 0, 0);
        var row = new Metric("Investable Assets", empty, false);
        assertEquals("Unavailable", row.median()); assertEquals("Unavailable", row.mean()); assertEquals("Unavailable", row.greater());
        var year = MonteCarloComparisonFixtures.annual().get(2040);
        var text = selectedYear(year);
        assertEquals(2, text.split("\n").length);
        for (String part : List.of("4 comparable of 2,000", "Current-only funded 2", "Baseline-only funded 1", "Both failed 2", "1,991 deceased")) assertTrue(text.contains(part), text);
        assertTrue(warning(year).contains("Only 4"));
        assertTrue(denominator(MonteCarloComparisonFixtures.run().result().summary()).contains("3 of 3"));
    }

    @ParameterizedTest
    @CsvSource({"0,2000,false", "1,2000,true", "100,2000,true", "101,5000,false", "100,1999,false"})
    void warningMatchesExistingInclusiveThresholds(int n, int requested, boolean expected) {
        var p = n == 0 ? Optional.<MonteCarloPercentiles>empty() : Optional.of(new MonteCarloPercentiles(n,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        var m = new MonteCarloPairedMetricSummary(n, p, n == 0 ? Optional.empty() : Optional.of(BigDecimal.ZERO), 0, n, 0);
        assertEquals(expected, smallSample(new MonteCarloPairedAnnualResult(2040, requested, n, requested - n, 0, 0, 0, m)));
    }

    @Test void captureFreezesBothSourcesSettingsAndDistinctSurvivorAges() throws Exception {
        var p = MonteCarloComparisonFixtures.plan();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var before = mapper.writeValueAsString(p);
        var settings = new MonteCarloInputs(3, "4.5", "12", "417").settings(true, "2", "1.75", "-2");
        var prepared = MonteCarloStrategyComparisonRunService.capture(p, settings, MonteCarloMode.LONGEVITY_ADJUSTED, "1", "1", "66", "70");
        assertSame(settings, prepared.request().assumptions().settings());
        assertEquals("Current Plan", prepared.request().strategyA().label()); assertEquals("Saved Baseline", prepared.request().strategyB().label());
        assertTrue(prepared.details().contains("Current Plan 66, Saved Baseline 70"));
        assertEquals(before, mapper.writeValueAsString(p));
        var service = new MonteCarloStrategyComparisonRunService();
        var first = service.run(prepared, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        p.getHousehold().addExpense(new Expense("Later edit", new BigDecimal("999999999")));
        p.getBaseline().getSnapshot().getHousehold().addExpense(new Expense("Later baseline edit", new BigDecimal("888888888")));
        var second = service.run(prepared, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(first.result(), second.result()); assertEquals(first.details(), second.details());
    }

    @Test void unavailableBaselineNeverFallsBackToSelfComparison() {
        var p = MonteCarloUiFixtures.plan("1000", 2);
        assertFalse(MonteCarloStrategyComparisonRunService.available(p));
        var error = assertThrows(IllegalArgumentException.class, () -> MonteCarloStrategyComparisonRunService.capture(p,
                new MonteCarloSettings(1, 417, BigDecimal.ZERO, BigDecimal.ZERO), MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67"));
        assertEquals(MonteCarloStrategyComparisonRunService.MISSING_BASELINE, error.getMessage());
    }

    @Test void captureRejectsIncompatibleStartsWithoutChangingEitherPlan() {
        var p = MonteCarloComparisonFixtures.plan(); var a = p.getPlanningAssumptions();
        p.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(), a.getProjectionLengthYears(), a.getProjectionStartDate().plusYears(1)));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloStrategyComparisonRunService.capture(p,
                new MonteCarloSettings(1, 417, BigDecimal.ZERO, BigDecimal.ZERO), MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67"));
    }
}
