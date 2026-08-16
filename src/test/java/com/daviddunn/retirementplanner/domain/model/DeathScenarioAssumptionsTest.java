package com.daviddunn.retirementplanner.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DeathScenarioAssumptionsTest {

    @Test
    void bothSurviveDoesNotRequireDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null,
                        null);

        assertEquals(
                DeathScenario.BOTH_SURVIVE,
                assumptions.getDeathScenario());

        assertNull(
                assumptions.getDeathYear());

        assertNull(
                assumptions.getSurvivorClaimingAge());
    }

    @Test
    void deathScenarioRequiresDeathYear() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                null,
                                67));
    }

    @Test
    void deathScenarioRequiresSurvivorClaimingAge() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                null));
    }

    @Test
    void survivorClaimingAgeMustBeAtLeast62() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                61));
    }

    @Test
    void survivorClaimingAgeMayBe62() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        62);

        assertEquals(
                Integer.valueOf(62),
                assumptions.getSurvivorClaimingAge());
    }

    @Test
    void survivorClaimingAgeMayBe70() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        70);

        assertEquals(
                Integer.valueOf(70),
                assumptions.getSurvivorClaimingAge());
    }

    @Test
    void survivorClaimingAgeMustNotExceed70() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                71));
    }

    @Test
    void postDeathExpenseFactorAccepts75Percent() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67,
                        new BigDecimal("0.75"));

        assertEquals(
                new BigDecimal("0.75"),
                assumptions.getPostDeathExpenseFactor());
    }

    @Test
    void postDeathExpenseFactorAcceptsZero() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67,
                        BigDecimal.ZERO);

        assertEquals(
                BigDecimal.ZERO,
                assumptions.getPostDeathExpenseFactor());
    }

    @Test
    void postDeathExpenseFactorAcceptsOne() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67,
                        BigDecimal.ONE);

        assertEquals(
                BigDecimal.ONE,
                assumptions.getPostDeathExpenseFactor());
    }

    @Test
    void postDeathExpenseFactorRejectsNegativeValue() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67,
                                new BigDecimal("-0.01")));
    }

    @Test
    void postDeathExpenseFactorRejectsValueGreaterThanOne() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67,
                                new BigDecimal("1.01")));
    }

    @Test
    void compatibilityConstructorDefaultsExpenseFactorTo100Percent() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67);

        assertEquals(
                BigDecimal.ONE,
                assumptions.getPostDeathExpenseFactor());
    }

    @Test
    void twoArgumentConstructorDefaultsSurvivorAgeAndExpenseFactor() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertEquals(
                Integer.valueOf(67),
                assumptions.getSurvivorClaimingAge());

        assertEquals(
                BigDecimal.ONE,
                assumptions.getPostDeathExpenseFactor());
    }

    @Test
    void bothSurviveDefaultsExpenseFactorTo100Percent() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null);

        assertEquals(
                BigDecimal.ONE,
                assumptions.getPostDeathExpenseFactor());
    }

    @Test
    void deathScenarioBecomesActiveInDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67);

        assertFalse(
                assumptions.isDeathScenarioActive(2034));

        assertTrue(
                assumptions.isDeathScenarioActive(2035));

        assertTrue(
                assumptions.isDeathScenarioActive(2036));
    }

    @Test
    void bothSurviveNeverActivatesDeathScenario() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null);

        assertFalse(
                assumptions.isDeathScenarioActive(2035));

        assertFalse(
                assumptions.isDeathScenarioActive(2050));
    }

    @Test
    void primaryIncomeStopsWhenPrimaryDies() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2034));

        assertFalse(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2035));

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2035));
    }

    @Test
    void spouseIncomeStopsWhenSpouseDies() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.SPOUSE_DIES,
                        2035,
                        67);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2034));

        assertFalse(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2035));

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2035));
    }
}