package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Permanent characterization of the unchanged financial engine, before continuation reuse. */
class LongevityContinuationPrefixTest {
    @ParameterizedTest
    @CsvSource({"false,false,0", "false,false,1", "false,false,2", "false,true,2",
            "true,false,0", "true,false,1", "true,false,2", "true,true,2"})
    void completeAnnualPrefixesAreIndependentOfLaterSecondDeath(boolean young, boolean partial, int conversion)
            throws Exception {
        var plan = young ? LongevityWeightedStrategyEquivalenceTest.youngPlan() : Stage4TestPlans.plan();
        if (partial) {
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                    a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), 5, LocalDate.of(2030, 7, 1)));
        }
        if (conversion > 0) {
            plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("10000"),
                    conversion == 1 ? RothConversionStopRule.FIRST_HOUSEHOLD_RMD : RothConversionStopRule.NEVER,
                    conversion == 1 ? RothConversionStrategy.FIXED_AMOUNT : RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                    RothConversionFrequency.ANNUAL));
        }
        String before = Stage4TestPlans.json(plan);
        var deterministic = new ProjectionEngine().project(plan);
        boolean rmdObserved = false;
        boolean conversionObserved = false;
        boolean sourceExhausted = false;
        boolean taxFundingObserved = false;
        boolean retainedCashObserved = false;
        for (int[] dates : List.of(new int[]{2041,2045,2041,2055}, new int[]{2045,2041,2055,2041},
                new int[]{2045,2045,2055,2055}, new int[]{2031,2032,2031,2055}, new int[]{2032,2031,2055,2031})) {
            var shorter = project(plan, dates[0], dates[1]);
            var longer = project(plan, dates[2], dates[3]);
            for (var row : longer.getYears()) {
                rmdObserved |= row.getRequiredMinimumDistribution().signum() > 0;
                conversionObserved |= row.getRothConversion().signum() > 0;
                sourceExhausted |= row.getRothConversionShortfall().signum() > 0;
                taxFundingObserved |= row.getTaxFundingWithdrawal().signum() > 0;
                retainedCashObserved |= row.getEndingRetainedNonQualifiedAssets().signum() > 0;
            }
            for (var row : shorter.getYears()) {
                if (row.getCalendarYear() >= Math.max(dates[0], dates[1])) break;
                assertEquals(Stage4TestPlans.json(row), Stage4TestPlans.json(longer.getYearAt(row.getCalendarYear() - 2030)),
                        "Complete prefix row " + row.getCalendarYear() + " deaths " + Arrays.toString(dates));
            }
        }
        assertEquals(before, Stage4TestPlans.json(plan));
        assertEquals(Stage4TestPlans.json(deterministic), Stage4TestPlans.json(new ProjectionEngine().project(plan)));
        if (conversion == 1) assertTrue(rmdObserved && conversionObserved, "Exercise conversion and RMD stop history");
        if (conversion == 2) assertTrue(conversionObserved && sourceExhausted && taxFundingObserved,
                "Exercise bracket fill, capacity exhaustion and tax funding");
        if (!young && conversion == 0) assertTrue(retainedCashObserved, "Exercise retained cash");
    }

    static Projection project(RetirementPlan plan, int primary, int spouse) {
        var isolated = new RetirementPlanScenarioCopyService().copy(plan);
        return new ProjectionEngine().project(isolated, ProjectionEvaluationContext.withSocialSecurityStrategy(
                Stage4TestPlans.strategy(plan), new HouseholdLifetimeScenario(Optional.of(Year.of(primary)),
                        Optional.of(Year.of(spouse)))).withEndingYear(Math.max(primary, spouse) - 1));
    }
}
