package com.daviddunn.retirementplanner.app.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenContextFactoryTest {
    private static final LocalDate DOB = LocalDate.of(1960, 6, 15);

    @Test void exactAnnualConventionAndAllProbabilityBoundaries() {
        var table = table("1", "0.5");
        var result = result(people(62), people(70), 2020, 2025);
        var context = new BreakEvenContextFactory(table).create(result, settings(BigDecimal.ONE));
        // Condition at age 60 birthday. First interval dies at birthday 61, mapped to January 1, 2021.
        assertEquals(0, BigDecimal.ONE.compareTo(context.survival().get(2020).householdAtLeastOneAliveProbability()));
        var next = context.survival().get(2021);
        assertEquals(0, next.primarySurvivalProbability().signum());
        assertEquals(0, new BigDecimal("0.5").compareTo(next.spouseSurvivalProbability()));
        assertEquals(0, next.spouseSurvivalProbability().compareTo(next.householdAtLeastOneAliveProbability()));
        assertEquals(61, next.primaryAge());
        assertEquals(0, context.survival().get(2025).householdAtLeastOneAliveProbability().signum());
        context.survival().values().forEach(point -> {
            for (var value : List.of(point.primarySurvivalProbability(), point.spouseSurvivalProbability(), point.householdAtLeastOneAliveProbability())) {
                assertTrue(value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0);
            }
        });
        assertTrue(context.survivalExplanation().contains("January 1"));
    }

    @Test void agreesExactlyWithWeightedJointScenarioMassAndLongevityFactors() {
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        var people = people(70);
        var result = result(people, people, 2020, 2051);
        for (var factor : List.of(new BigDecimal("0.8"), BigDecimal.ONE, new BigDecimal("1.2"))) {
            var settings = settings(factor);
            var assumptions = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                    settings.primaryAdjustment(), SocialSecurityMortalityCategory.FEMALE, settings.spouseAdjustment(),
                    settings.conditioningDate(), table.metadata(), SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
            var prepared = new HouseholdLongevityScenarioFactory(table).create(DOB, DOB, assumptions);
            var context = new BreakEvenContextFactory(table).create(result, settings);
            for (var point : context.survival().values()) {
                var expected = prepared.scenarios().stream().filter(scenario ->
                        scenario.primaryDeathDate().getYear() > point.year() || scenario.spouseDeathDate().getYear() > point.year())
                        .map(SocialSecurityJointMortalityScenario::jointProbability).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(0, expected.compareTo(point.householdAtLeastOneAliveProbability()));
                var complement = BigDecimal.ONE.subtract(BigDecimal.ONE.subtract(point.primarySurvivalProbability())
                        .multiply(BigDecimal.ONE.subtract(point.spouseSurvivalProbability())));
                assertEquals(0, complement.compareTo(point.householdAtLeastOneAliveProbability()));
            }
        }
        var lowerHazard = new BreakEvenContextFactory(table).create(result, settings(new BigDecimal("0.8")));
        var higherHazard = new BreakEvenContextFactory(table).create(result, settings(new BigDecimal("1.2")));
        assertTrue(lowerHazard.survival().get(2050).householdAtLeastOneAliveProbability()
                .compareTo(higherHazard.survival().get(2050).householdAtLeastOneAliveProbability()) > 0);
    }

    @Test void nextCompleteBirthdayIntervalAndPreconditioningYearsArePreserved() {
        var settings = new LongevitySessionSettings(LocalDate.of(2020, 6, 16),
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard());
        var context = new BreakEvenContextFactory(table("1", "1")).create(result(people(62), people(70), 2019, 2025), settings);
        assertFalse(context.survival().containsKey(2019));
        assertEquals(0, BigDecimal.ONE.compareTo(context.survival().get(2021).householdAtLeastOneAliveProbability()));
        assertEquals(0, context.survival().get(2022).householdAtLeastOneAliveProbability().signum());
    }

    @Test void eventsUseStoredDatesCombineSamePersonAndExcludeOutsideHorizon() {
        var baseline = people(62);
        var current = people(70);
        var result = result(baseline, current, 2020, 2035);
        var events = BreakEvenContextFactory.events(result);
        assertEquals(3, events.size());
        var primary = events.stream().filter(e -> e.person() == AccountOwnership.PRIMARY).findFirst().orElseThrow();
        assertEquals(2, primary.elections().size());
        assertEquals(2030, primary.year());
        assertEquals(DOB.plusYears(70), primary.elections().getFirst().claimDate());
        assertEquals(List.of(2022, 2030), events.stream().filter(e -> e.person() == AccountOwnership.SPOUSE).map(BreakEvenEvent::year).toList());
        assertEquals(2, BreakEvenContextFactory.events(result(baseline, current, 2027, 2051)).size());
        // Persisted exact date is authoritative, even when it differs from birthday + saved age.
        var exact = new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Primary", LocalDate.of(1960, 1, 1), 70,
                LocalDate.of(2029, 12, 31), MortalityCategory.MALE), current.spouse());
        assertEquals(2029, BreakEvenContextFactory.events(result(exact, exact, 2027, 2035)).getFirst().year());
    }

    @Test void mismatchedOrMissingMortalityDoesNotHideEventsOrChangeDeterministicResults() {
        var baseline = people(62);
        var mismatched = new BreakEvenPlanSummary(baseline.primary(), new BreakEvenPlanSummary.PersonSummary("Spouse", DOB, 70,
                DOB.plusYears(70), MortalityCategory.MALE));
        var result = result(baseline, mismatched, 2020, 2051);
        var before = result.metrics();
        var context = new BreakEvenContextFactory().create(result, settings(BigDecimal.ONE));
        assertTrue(context.survival().isEmpty());
        assertTrue(context.survivalExplanation().contains("different mortality assumptions"));
        assertFalse(context.events().isEmpty());
        assertSame(before, result.metrics());
        var missing = new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Primary", DOB, 70),
                new BreakEvenPlanSummary.PersonSummary("Spouse", DOB, 70));
        assertTrue(new BreakEvenContextFactory().create(result(missing, missing, 2020, 2051), settings(BigDecimal.ONE))
                .survivalExplanation().contains("both people need"));
    }

    @Test void explanatoryContextLeavesAllFourPhaseOneResultsExactlyUnchanged() {
        var baseline = snapshot(people(62), 2027, 2051, 1000);
        var current = snapshot(people(70), 2027, 2051, 1100);
        var analyzer = new BreakEvenAnalyzer();
        var before = analyzer.analyze(baseline, current);
        new BreakEvenContextFactory().create(before, settings(BigDecimal.ONE));
        assertEquals(before, analyzer.analyze(baseline, current));
        assertEquals(4, before.metrics().size());
    }

    private static LongevitySessionSettings settings(BigDecimal factor) {
        return new LongevitySessionSettings(DOB.plusYears(60), SocialSecurityMortalityAdjustment.of(factor), SocialSecurityMortalityAdjustment.of(factor));
    }
    private static BreakEvenPlanSummary people(int spouseAge) {
        return new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Primary", DOB, 70, DOB.plusYears(70), MortalityCategory.MALE),
                new BreakEvenPlanSummary.PersonSummary("Spouse", DOB, spouseAge, DOB.plusYears(spouseAge), MortalityCategory.FEMALE));
    }
    private static BreakEvenAnalysisResult result(BreakEvenPlanSummary baseline, BreakEvenPlanSummary current, int start, int end) {
        return new BreakEvenAnalyzer().analyze(snapshot(baseline, start, end, 1000), snapshot(current, start, end, 1100));
    }
    private static BreakEvenProjectionSnapshot snapshot(BreakEvenPlanSummary people, int start, int end, long assets) {
        return new BreakEvenProjectionSnapshot(IntStream.rangeClosed(start, end).mapToObj(year -> ProjectionYearBuilder.aProjectionYear()
                .withCalendarYear(year).withEndingInvestableAssets(assets).build()).toList(), List.of(), people);
    }
    private static SocialSecurityMortalityTable table(String male, String female) {
        return new SocialSecurityMortalityTable(new SocialSecurityMortalityTableMetadata("test", "Test", "Test", "1",
                SocialSecurityMortalityTableType.TEST_FIXTURE, 60, 65, "Synthetic boundary fixture"),
                IntStream.range(60, 65).mapToObj(age -> new SocialSecurityMortalityTableEntry(age, new BigDecimal(male), new BigDecimal(female))).toList());
    }
}
