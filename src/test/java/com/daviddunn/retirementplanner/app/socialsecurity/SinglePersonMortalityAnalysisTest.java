package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SinglePersonMortalityAnalysisTest {
    public static RetirementPlan plan() {
        var plan = SinglePersonDeterministicStrategyTest.plan();
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.FEMALE);
        return plan;
    }
    public static IndividualLongevityScenarios mortality(RetirementPlan plan, String factor) {
        return IndividualLongevityScenarios.create(plan.getHousehold(), SocialSecurityMortalityAdjustment.of(new BigDecimal(factor)),
                LocalDate.of(2027, 1, 1), SocialSecurityMortalityTables.ssaPeriod2022());
    }
    public static SinglePersonMortalityAnalysis.Result run(RetirementPlan plan, IndividualLongevityScenarios mortality,
            String discount, SinglePersonMortalityAnalysis.Mode mode) {
        return new SinglePersonMortalityAnalysis().calculate(plan, mortality, LocalDate.of(2027, 1, 1), new BigDecimal(discount),
                mode, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    @Test void individualConservationConditioningBoundsAndFactorSensitivity() {
        var plan = plan();
        var standard = mortality(plan, "1");
        assertEquals(SocialSecurityMortalityCategory.FEMALE, standard.individual().request().mortalityCategory());
        assertEquals(0, BigDecimal.ONE.compareTo(standard.survivalAt(LocalDate.of(2027, 1, 1))));
        assertEquals(0, BigDecimal.ONE.compareTo(standard.scenarios().stream()
                .map(IndividualLongevityScenarios.DeathScenario::probability).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(standard.scenarios(), mortality(plan, "1").scenarios());
    }

    @Test void probabilitiesAreMonotoneAndAdjustmentDirectionIsPreserved() {
        var plan = plan();
        var baseline = mortality(plan, "1");
        var longer = mortality(plan, "0.8");
        var shorter = mortality(plan, "1.2");
        assertEquals(baseline.individual().distribution().probabilities(), mortality(plan, "1").individual().distribution().probabilities());
        var survival = BigDecimal.ONE;
        for (var scenario : baseline.scenarios()) {
            assertTrue(scenario.probability().signum() >= 0 && scenario.probability().compareTo(BigDecimal.ONE) <= 0);
            var next = baseline.survivalAt(scenario.deathDate());
            assertTrue(next.signum() >= 0 && next.compareTo(survival) <= 0);
            assertEquals(0, survival.subtract(scenario.probability()).compareTo(next));
            survival = next;
        }
        assertEquals(0, survival.signum());
        var late = plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(90);
        assertTrue(longer.survivalAt(late).compareTo(baseline.survivalAt(late)) > 0);
        assertTrue(shorter.survivalAt(late).compareTo(baseline.survivalAt(late)) < 0);
        var terminal = IndividualLongevityScenarios.create(plan.getHousehold(), SocialSecurityMortalityAdjustment.standard(),
                plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(119), SocialSecurityMortalityTables.ssaPeriod2022());
        assertEquals(1, terminal.scenarios().size());
        assertEquals(120, terminal.scenarios().getFirst().deathAge());
        assertEquals(BigDecimal.ONE, terminal.scenarios().getFirst().probability());
    }

    @Test void nineExpectedPvStrategiesAreStableSensitiveAndHaveNoSpouse() throws Exception {
        var plan = plan();
        var before = Stage4TestPlans.json(plan);
        var result = run(plan, mortality(plan, "1"), "0.02", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        assertEquals(9, result.entries().size());
        assertEquals(0, result.projectionCount());
        assertEquals(67, result.current().strategy().primaryRetirementAge());
        assertEquals(1, result.rank(result.ranked().getFirst()));
        assertEquals(result.entries(), run(plan, mortality(plan, "1"), "0.02", result.mode()).entries());
        var lowerDiscount = run(plan, mortality(plan, "1"), "0", result.mode());
        var longer = run(plan, mortality(plan, "0.8"), "0.02", result.mode());
        for (int i = 0; i < 9; i++) {
            var entry = result.entries().get(i);
            assertEquals(62 + i, entry.strategy().primaryRetirementAge());
            assertFalse(entry.strategy().hasSpouse());
            assertNull(entry.strategy().primarySurvivorElection());
            assertNull(entry.expectedInvestableAssets());
            assertTrue(entry.expectedPresentValue().signum() > 0);
            assertTrue(lowerDiscount.entries().get(i).expectedPresentValue().compareTo(entry.expectedPresentValue()) > 0);
            assertTrue(longer.entries().get(i).expectedPresentValue().compareTo(entry.expectedPresentValue()) > 0);
        }
        assertEquals(before, Stage4TestPlans.json(plan));
    }

    @Test void expectedPvMatchesIndependentMonthlyReceiptReference() {
        var plan = plan();
        var mortality = mortality(plan, "1");
        var result = run(plan, mortality, "0", SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        // FRA age 67 gives $3000; annual COLA rounds monthly benefits to cents.
        // Zero real discount isolates receipt probability and calendar-year COLA deflation.
        BigDecimal reference = BigDecimal.ZERO;
        var claim = YearMonth.of(2032, 2);
        for (var scenario : mortality.scenarios()) {
            BigDecimal scenarioPv = BigDecimal.ZERO;
            for (var month = claim; month.isBefore(YearMonth.from(scenario.deathDate())); month = month.plusMonths(1)) {
                var growth = BigDecimal.ONE.add(result.colaRate()).pow(month.getYear() - 2027);
                var nominal = new BigDecimal("3000").multiply(growth).setScale(2, RoundingMode.HALF_UP);
                scenarioPv = scenarioPv.add(nominal.divide(growth, MathContext.DECIMAL128));
            }
            reference = reference.add(scenarioPv.multiply(scenario.probability()));
        }
        assertTrue(reference.subtract(result.current().expectedPresentValue()).abs().compareTo(new BigDecimal("1E-20")) < 0);
    }

    @Test void weightedEstateEqualsIndependentScenarioProjectionSumAndNineExecutions() {
        var plan = plan();
        var original = mortality(plan, "1");
        var mortality = new IndividualLongevityScenarios(new SocialSecurityMortalityDistributionResult(
                new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(70, new BigDecimal("0.25")),
                        new SocialSecurityMortalityProbability(75, new BigDecimal("0.75")))),
                original.individual().tableMetadata(), original.individual().request(), 62, 75, original.individual().partialYearConvention()));
        var result = run(plan, mortality, "0", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        assertEquals(9, result.entries().size());
        assertEquals(18, result.projectionCount());
        for (var entry : result.entries()) {
            BigDecimal expected = BigDecimal.ZERO;
            for (var scenario : mortality.scenarios()) {
                var year = scenario.deathDate().getYear();
                var projection = new ProjectionEngine().project(plan,
                        ProjectionEvaluationContext.withSocialSecurityStrategy(entry.strategy(),
                                HouseholdLifetimeScenario.primaryOnly(Optional.of(Year.of(year)))).withExactEndingYear(year - 1));
                expected = expected.add(projection.getLastYear().getAfterTaxEstateValue().multiply(scenario.probability()));
                projection.getYears().forEach(y -> assertNull(y.getSocialSecurityResult().spouseOwnBenefit()));
            }
            assertEquals(0, expected.compareTo(entry.expectedNominal()));
            assertEquals(0, BigDecimal.ONE.compareTo(entry.outcomes().stream().map(SinglePersonMortalityAnalysis.Outcome::probability)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        assertNotEquals(result.entries().getFirst().expectedNominal(), result.entries().getLast().expectedNominal());
    }

    @Test void fullProductionIndividualDistributionUsesNineTimesIndividualScenarios() {
        var plan = plan();
        var mortality = mortality(plan, "1");
        var result = run(plan, mortality, "0.01", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        assertEquals(9 * mortality.scenarios().size(), result.projectionCount());
        assertEquals(67, result.current().strategy().primaryRetirementAge());
        assertEquals(1, result.rank(result.ranked().getFirst()));
        result.entries().forEach(e -> assertEquals(mortality.scenarios().size(), e.outcomes().size()));
    }

    @Test void openingDeathUsesOpeningAssetsWithNoProjectionOrSurvivor() {
        var plan = plan();
        var original = mortality(plan, "1").individual();
        var opening = new IndividualLongevityScenarios(new SocialSecurityMortalityDistributionResult(
                new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(62, BigDecimal.ONE))),
                original.tableMetadata(), original.request(), 61, 62, original.partialYearConvention()));
        var result = run(plan, opening, "0", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        assertEquals(0, result.projectionCount());
        result.entries().forEach(e -> {
            assertEquals(new BigDecimal("1200000"), e.expectedInvestableAssets());
            assertEquals(1, result.rank(e));
            assertEquals(1, e.outcomes().size());
            assertEquals(2027, e.outcomes().getFirst().deathYear());
        });
    }

    @Test void missingCategoryInvalidHorizonAndCancellationFailClearly() {
        assertThrows(IllegalArgumentException.class, () -> mortality(SinglePersonDeterministicStrategyTest.plan(), "1"));
        var plan = plan();
        var mortality = mortality(plan, "1");
        assertThrows(AnalysisCancelledException.class, () -> new SinglePersonMortalityAnalysis().calculate(plan, mortality,
                LocalDate.of(2027, 1, 1), BigDecimal.ZERO, SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY,
                AnalysisProgressListener.none(), () -> true));
        assertThrows(IllegalArgumentException.class, () -> mortality.survivalAt(LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new SinglePersonMortalityAnalysis().calculate(plan, mortality,
                LocalDate.of(2040, 1, 1), BigDecimal.ZERO, SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertThrows(IllegalArgumentException.class, () -> IndividualLongevityScenarios.create(plan.getHousehold(),
                SocialSecurityMortalityAdjustment.standard(), plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(120),
                SocialSecurityMortalityTables.ssaPeriod2022()));
        assertThrows(IllegalArgumentException.class, () -> new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(80, new BigDecimal("0.999")))));
    }
}
