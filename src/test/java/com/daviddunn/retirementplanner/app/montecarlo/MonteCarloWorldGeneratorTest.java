package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.HouseholdLifetimeScenarioMapper;
import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloWorldGeneratorTest {

    static RetirementPlan plan() {
        var plan = MonteCarloFixtures.household();
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.FEMALE);
        return plan;
    }

    static MonteCarloSettings settings(int count, long seed, String volatility) {
        return new MonteCarloSettings(count, seed, new BigDecimal("0.045"), new BigDecimal(volatility));
    }

    static MonteCarloMortalityRequest request(RetirementPlan plan, MonteCarloSettings settings) {
        return new MonteCarloMortalityRequest(plan, settings,
                LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()));
    }

    @Test
    void indexedWorldsAreRepeatableAcrossDirectSequentialReverseAndLargerCountGeneration() {
        var request = request(plan(), settings(225, 417, "0.15"));
        var generator = new MonteCarloWorldGenerator(request);
        var direct = generator.generate(224);
        var worlds = new ArrayList<MonteCarloWorld>();
        for (int i = 0; i < 225; i++) {
            worlds.add(generator.generate(i));
        }
        assertWorldEquals(direct, worlds.get(224));
        var independent = new MonteCarloWorldGenerator(request);
        var larger = new MonteCarloWorldGenerator(request(plan(), settings(5000, 417, "0.15")));
        for (int i = 224; i >= 0; i--) {
            assertWorldEquals(worlds.get(i), generator.generate(i));
            assertWorldEquals(worlds.get(i), independent.generate(i));
            assertWorldEquals(worlds.get(i), larger.generate(i));
        }
        // Index validity is independent of the requested analysis count.
        assertWorldEquals(direct, new MonteCarloWorldGenerator(
                request(plan(), settings(1, 417, "0.15"))).generate(224));
    }

    @Test
    void differentSeedsChangeMortalityAcrossABatch() {
        var first = new MonteCarloWorldGenerator(request(plan(), settings(100, 417, "0")));
        var second = new MonteCarloWorldGenerator(request(plan(), settings(100, 418, "0")));
        int differences = 0;
        for (int i = 0; i < 100; i++) {
            if (!first.generate(i).lifetimeScenario().equals(second.generate(i).lifetimeScenario())) {
                differences++;
            }
        }
        assertTrue(differences > 0);
    }

    @Test
    void seed417WorldPathsExactlyMatchLegacyForScenariosZeroOneAnd224() {
        var settings = settings(225, 417, "0.15");
        var generator = new MonteCarloWorldGenerator(request(plan(), settings));
        for (int index : List.of(0, 1, 224)) {
            var world = generator.generate(index);
            var legacy = new MonteCarloScenarioGenerator().generate(2027, lastYear(world), settings, index);
            assertEquals(legacy.annualReturns(), world.economicPath().annualReturns());
        }
    }

    @Test
    void longLifetimeAppendsMarketDrawsWithoutChangingAnyShorterPrefix() {
        var plan = plan();
        var settings = settings(225, 417, "0.15");
        var generator = new MonteCarloWorldGenerator(new MonteCarloMortalityRequest(plan, settings,
                LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)), deathTable(120, 120)));
        for (int index : List.of(0, 1, 224)) {
            var world = generator.generate(index);
            assertEquals(2084, lastYear(world));
            for (int end : List.of(2050, 2056, 2070, 2084, 2100)) {
                var legacy = new MonteCarloScenarioGenerator().generate(2027, end, settings, index);
                for (int year = 2027; year <= Math.min(end, lastYear(world)); year++) {
                    assertEquals(legacy.investmentReturnForYear(year), world.economicPath().investmentReturnForYear(year));
                }
            }
        }
    }

    @Test
    void zeroVolatilityAndIndependentMortalityByteConsumptionPreserveReturns() {
        var settings = settings(10, 417, "0");
        var generator = new MonteCarloWorldGenerator(request(plan(), settings));
        for (int i = 0; i < 10; i++) {
            var before = generator.generate(i);
            var random = MonteCarloRandomStreams.create(417, i,
                    MonteCarloRandomStreams.HOUSEHOLD_MORTALITY, MonteCarloRandomStreams.HOUSEHOLD_MORTALITY_V1);
            random.nextBytes(new byte[10000]);
            assertWorldEquals(before, generator.generate(i));
            before.economicPath().annualReturns().values().forEach(rate -> assertEquals(settings.expectedReturn(), rate));
        }
        var stochastic = settings(10, 417, "0.15");
        var standard = new MonteCarloWorldGenerator(request(plan(), stochastic));
        var terminal = new MonteCarloWorldGenerator(new MonteCarloMortalityRequest(plan(), stochastic,
                LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)), deathTable(120, 120)));
        for (int i = 0; i < 10; i++) {
            var shortWorld = standard.generate(i);
            var longWorld = terminal.generate(i);
            shortWorld.economicPath().annualReturns().forEach((year, rate) ->
                    assertEquals(rate, longWorld.economicPath().investmentReturnForYear(year)));
        }
    }

    @Test
    void orderedExactJointSamplerSelectsTheSameMappedOutcome() {
        var generator = new MonteCarloWorldGenerator(request(plan(), settings(500, 417, "0")));
        var prepared = generator.mortalityScenarios();
        var ordered = prepared.scenarios();
        var sampler = new DiscreteProbabilitySampler(ordered.stream()
                .map(SocialSecurityJointMortalityScenario::jointProbability).toList());
        var mapper = new HouseholdLifetimeScenarioMapper();
        for (int i = 0; i < 500; i++) {
            var random = MonteCarloRandomStreams.create(417, i,
                    MonteCarloRandomStreams.HOUSEHOLD_MORTALITY, MonteCarloRandomStreams.HOUSEHOLD_MORTALITY_V1);
            assertEquals(mapper.map(ordered.get(sampler.sample(random))), generator.generate(i).lifetimeScenario());
            assertSame(prepared, generator.mortalityScenarios());
        }
        assertThrows(UnsupportedOperationException.class, () -> ordered.clear());
    }

    @Test
    void birthdayDeathsMapToJanuaryFirstForBothDeathOrdersSameYearAndTerminalTail() {
        // Primary DOB 1963-06-04, spouse DOB 1965-02-28.
        assertDeaths(85, 86, 2048, 2051);
        assertDeaths(90, 83, 2053, 2048);
        assertDeaths(85, 83, 2048, 2048);
        assertDeaths(120, 120, 2083, 2085);
    }

    private void assertDeaths(int primaryAge, int spouseAge, int primaryYear, int spouseYear) {
        var generator = new MonteCarloWorldGenerator(new MonteCarloMortalityRequest(plan(), settings(1, 417, "0"),
                LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)), deathTable(primaryAge, spouseAge)));
        var world = generator.generate(0);
        assertEquals(Optional.of(Year.of(primaryYear)), world.lifetimeScenario().primaryDeathYear());
        assertEquals(Optional.of(Year.of(spouseYear)), world.lifetimeScenario().spouseDeathYear());
        assertEquals(LocalDate.of(primaryYear, 1, 1), world.lifetimeScenario().primaryDeathDate().orElseThrow());
        assertEquals(LocalDate.of(spouseYear, 1, 1), world.lifetimeScenario().spouseDeathDate().orElseThrow());
        var selected = generator.mortalityScenarios().scenarios().stream()
                .filter(scenario -> scenario.jointProbability().signum() > 0).toList();
        assertEquals(1, selected.size());
        assertEquals(LocalDate.of(primaryYear, 6, 4), selected.getFirst().primaryDeathDate());
        assertEquals(LocalDate.of(spouseYear, 2, 28), selected.getFirst().spouseDeathDate());
        assertEquals(Math.max(primaryYear, spouseYear) - 1, lastYear(world));
        world.economicPath().requireCoverage(2027, lastYear(world));
        assertThrows(IllegalArgumentException.class, () -> world.economicPath().investmentReturnForYear(lastYear(world) + 1));
    }

    @Test
    void capturesAuthoritativePersonsAndAdjustmentsWithProjectionStartConditioning() {
        var plan = plan();
        var session = new LongevitySessionSettings(LocalDate.of(2040, 1, 1),
                SocialSecurityMortalityAdjustment.of(new BigDecimal("0.8")),
                SocialSecurityMortalityAdjustment.of(new BigDecimal("1.5")));
        var request = new MonteCarloMortalityRequest(plan, settings(1, 417, "0"), session);
        var generator = new MonteCarloWorldGenerator(request);
        var prepared = generator.mortalityScenarios();
        assertEquals(LocalDate.of(2027, 1, 1), prepared.assumptions().mortalityBaseDate());
        assertEquals(SocialSecurityMortalityCategory.MALE, prepared.assumptions().primaryCategory());
        assertEquals(SocialSecurityMortalityCategory.FEMALE, prepared.assumptions().spouseCategory());
        assertEquals(session.primaryAdjustment(), prepared.assumptions().primaryAdjustment());
        assertEquals(session.spouseAdjustment(), prepared.assumptions().spouseAdjustment());
        assertEquals(64, prepared.primary().firstModeledAttainedAge());
        assertEquals(62, prepared.spouse().firstModeledAttainedAge());
        var before = generator.generate(0);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(2000, 1, 1));
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.FEMALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.MALE);
        assertWorldEquals(before, new MonteCarloWorldGenerator(request).generate(0));
        var changed = request(plan, settings(1, 417, "0"));
        assertEquals(SocialSecurityMortalityCategory.FEMALE, changed.longevityAssumptions().primaryCategory());
        assertEquals(SocialSecurityMortalityCategory.MALE, changed.longevityAssumptions().spouseCategory());
        assertEquals(LocalDate.of(1963, 6, 4), request.primaryBirthDate());
    }

    @Test
    void invalidInputsFailBeforeWorldGeneration() {
        var settings = settings(1, 417, "0");
        var session = LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1));
        assertThrows(NullPointerException.class, () -> new MonteCarloMortalityRequest(null, settings, session));
        assertThrows(NullPointerException.class, () -> new MonteCarloMortalityRequest(plan(), null, session));
        assertThrows(NullPointerException.class, () -> new MonteCarloMortalityRequest(plan(), settings, null));
        assertThrows(NullPointerException.class, () -> new MonteCarloMortalityRequest(plan(), settings, session, null));
        assertThrows(IllegalArgumentException.class, () -> request(MonteCarloFixtures.household(), settings));
        for (boolean primary : List.of(true, false)) {
            var plan = plan();
            (primary ? plan.getHousehold().getPrimaryPerson() : plan.getHousehold().getSpouse()).setBirthDate(null);
            assertThrows(NullPointerException.class, () -> request(plan, settings));
        }
        for (LocalDate invalidBirth : List.of(LocalDate.of(2030, 1, 1), LocalDate.of(1907, 1, 1))) {
            var plan = plan();
            plan.getHousehold().getPrimaryPerson().setBirthDate(invalidBirth);
            assertThrows(IllegalArgumentException.class, () -> new MonteCarloWorldGenerator(request(plan, settings)));
        }
        assertThrows(IllegalArgumentException.class, () -> settings(0, 417, "0"));
        assertThrows(IllegalArgumentException.class, () -> settings(1, 417, "-0.1"));
        assertThrows(IllegalArgumentException.class, () -> SocialSecurityMortalityAdjustment.of(BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new LongevitySessionSettings(null,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard()));
        assertThrows(NullPointerException.class, () -> new MonteCarloWorldGenerator(null));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloWorldGenerator(request(plan(), settings)).generate(-1));
    }

    @Test
    void worldRejectsInvalidIndexMissingPathAndIncompleteLifetimes() {
        var world = new MonteCarloWorldGenerator(request(plan(), settings(1, 417, "0"))).generate(0);
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloWorld(-1, world.economicPath(), world.lifetimeScenario()));
        assertThrows(NullPointerException.class, () -> new MonteCarloWorld(0, null, world.lifetimeScenario()));
        assertThrows(NullPointerException.class, () -> new MonteCarloWorld(0, world.economicPath(), null));
        for (var lifetime : List.of(HouseholdLifetimeScenario.bothSurvive(),
                new HouseholdLifetimeScenario(Optional.of(Year.of(2048)), Optional.empty()),
                new HouseholdLifetimeScenario(Optional.empty(), Optional.of(Year.of(2048))))) {
            assertThrows(IllegalArgumentException.class, () -> new MonteCarloWorld(0, world.economicPath(), lifetime));
        }
        assertThrows(UnsupportedOperationException.class, () -> world.economicPath().annualReturns().clear());
    }

    static SocialSecurityMortalityTable deathTable(int primaryAge, int spouseAge) {
        var metadata = new SocialSecurityMortalityTableMetadata("TEST_POINT_MASS_V1", "Fixture", "Test", "1",
                SocialSecurityMortalityTableType.PERIOD, 0, 120, "Deterministic death ages");
        var entries = new ArrayList<SocialSecurityMortalityTableEntry>();
        for (int age = 0; age < 120; age++) {
            entries.add(new SocialSecurityMortalityTableEntry(age,
                    age == primaryAge - 1 ? BigDecimal.ONE : BigDecimal.ZERO,
                    age == spouseAge - 1 ? BigDecimal.ONE : BigDecimal.ZERO));
        }
        return new SocialSecurityMortalityTable(metadata, entries);
    }

    static int lastYear(MonteCarloWorld world) {
        return Collections.max(world.economicPath().annualReturns().keySet());
    }

    static void assertWorldEquals(MonteCarloWorld expected, MonteCarloWorld actual) {
        assertEquals(expected.scenarioIndex(), actual.scenarioIndex());
        assertEquals(expected.lifetimeScenario(), actual.lifetimeScenario());
        // ProjectionEconomicPath is an existing immutable class with identity equality.
        assertEquals(expected.economicPath().annualReturns(), actual.economicPath().annualReturns());
    }
}
