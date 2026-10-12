package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

final class MonteCarloMortalityUiFixtures {
    /** Seed-417 preview fingerprint, retained at full aggregate precision. */
    static void assertPreviewTail(MonteCarloRun run, boolean age67) {
        int[] living = {63, 39, 18, 9, 2};
        String[][] quantiles = {
                {"1824113.33", "5997756.4980", "10125940.9400", "16774774.1800", "31504215.1950", "49614391.9120", "84539373.97"},
                {"1773497.48", "4725759.3800", "9552278.0950", "15486382.1700", "28929115.4750", "41488668.4660", "63273558.27"},
                {"3768049.64", "8297089.1540", "10503319.7675", "14258754.3300", "28922129.8950", "42301101.8530", "56875522.03"},
                {"3160579.96", "5790223.7040", "9337683.0900", "10238682.1200", "32984328.2900", "46842825.9620", "49525078.93"},
                {"6300188.05", "6501704.2830", "6803978.6325", "7307769.2150", "7811559.7975", "8113834.1470", "8315350.38"}
        };
        var result = run.mortalityResult();
        for (int year = 2067; year <= 2071; year++) {
            var annual = result.annualResults().get(year);
            org.junit.jupiter.api.Assertions.assertEquals(5000, annual.requestedWorldCount());
            org.junit.jupiter.api.Assertions.assertEquals(living[year - 2067], annual.livingHouseholdCount());
            org.junit.jupiter.api.Assertions.assertEquals(0, annual.livingFundingFailedByYearCount());
            org.junit.jupiter.api.Assertions.assertEquals(0, annual.bothAliveCount());
            org.junit.jupiter.api.Assertions.assertEquals(year <= 2068 ? 1 : 0, annual.primaryOnlyAliveCount());
            org.junit.jupiter.api.Assertions.assertEquals(annual.requestedWorldCount(), annual.livingHouseholdCount() + annual.bothDeceasedCount());
            org.junit.jupiter.api.Assertions.assertEquals(annual.livingHouseholdCount(), annual.completedLivingYearSampleCount() + annual.livingFundingFailedByYearCount());
            org.junit.jupiter.api.Assertions.assertEquals(annual.livingHouseholdCount(), annual.bothAliveCount() + annual.primaryOnlyAliveCount() + annual.spouseOnlyAliveCount());
            final int selected = year;
            var chartYear = run.fan().years().stream().filter(y -> y.calendarYear() == selected).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertSame(annual, chartYear.population().orElseThrow());
            org.junit.jupiter.api.Assertions.assertSame(annual.investableAssets(), chartYear.percentiles());
            var observations = result.outcomes().stream().filter(o -> selected <= o.finalLivingFinancialYear())
                    .map(o -> o.annualInvestableAssets().get(selected)).filter(Objects::nonNull).toList();
            org.junit.jupiter.api.Assertions.assertEquals(MonteCarloPercentiles.of(observations), annual.investableAssets());
            if (age67) {
                org.junit.jupiter.api.Assertions.assertEquals(Arrays.stream(quantiles[year - 2067]).map(BigDecimal::new).toList(),
                        MonteCarloPresentation.quantiles(annual.investableAssets().orElseThrow()));
            }
        }
    }

    static RetirementPlan plan() {
        var plan = MonteCarloFixtures.household();
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.FEMALE);
        survivorPolicy(plan, 67);
        return plan;
    }

    static void survivorPolicy(RetirementPlan plan, Integer age) {
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, age, BigDecimal.ONE),
                a.getProjectionLengthYears(), a.getProjectionStartDate()));
    }

    static MonteCarloRun run(int funded, boolean opening) {
        var plan = plan();
        var request = new MonteCarloMortalityRequest(plan, MonteCarloSettings.forPlan(plan, 4, 417, new BigDecimal("0.12")),
                LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)));
        var outcomes = new ArrayList<MonteCarloMortalityAnalysisResult.WorldOutcome>();
        int[][] deaths = {{2037, 2039}, {2061, 2040}, {2050, 2086}, {2070, 2080}};
        for (int i = 0; i < 4; i++) {
            int primary = opening ? 2027 : deaths[i][0];
            int spouse = opening ? 2027 : deaths[i][1];
            int last = Math.max(primary, spouse) - 1;
            boolean successful = i < funded;
            var annual = new TreeMap<Integer, BigDecimal>();
            int end = successful ? last : 2034;
            for (int y = 2027; y <= end; y++) {
                annual.put(y, BigDecimal.valueOf(1_000_000L + 100_000L * i + 10_000L * (y - 2027)));
            }
            var value = BigDecimal.valueOf(2_000_000L + i * 100_000L);
            var terminal = new MonteCarloMortalityAnalysisResult.TerminalOutcome(
                    opening ? LocalDate.of(2027, 1, 1) : LocalDate.of(last, 12, 31),
                    value, value.add(new BigDecimal("500000")), value.subtract(new BigDecimal("100000")),
                    opening ? BigDecimal.ZERO : new BigDecimal("300000"));
            var failure = new FundingFailure(2035, 8, Optional.empty(), Optional.empty(),
                    FundingFailure.Stage.WITHDRAWAL_ALLOCATION, Optional.empty(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
            outcomes.add(new MonteCarloMortalityAnalysisResult.WorldOutcome(i,
                    new HouseholdLifetimeScenario(Optional.of(Year.of(primary)), Optional.of(Year.of(spouse))), annual,
                    successful ? Optional.of(terminal) : Optional.empty(), successful ? Optional.empty() : Optional.of(failure)));
        }
        var result = new MonteCarloMortalityAnalysisResult(request, "MARKET_RETURNS_V1", "HOUSEHOLD_MORTALITY_V1", outcomes);
        return new MonteCarloRun(new MonteCarloRun.Mortality(result), MonteCarloFanModel.from(result, BreakEvenPlanSummary.from(plan.getHousehold())),
                BreakEvenPlanSummary.from(plan.getHousehold()), 2027, result.lastReportingYear().orElse(2026), false, 0);
    }
}
