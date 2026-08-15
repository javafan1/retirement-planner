package com.daviddunn.retirementplanner.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeathScenarioAssumptionsTest {

    @Test
    void bothSurviveDoesNotRequireDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null);

        assertEquals(
                DeathScenario.BOTH_SURVIVE,
                assumptions.getDeathScenario());

        assertNull(
                assumptions.getDeathYear());
    }

    @Test
    void primaryDiesRequiresDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertEquals(
                DeathScenario.PRIMARY_DIES,
                assumptions.getDeathScenario());

        assertEquals(
                2035,
                assumptions.getDeathYear());
    }

    @Test
    void spouseDiesRequiresDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.SPOUSE_DIES,
                        2035);

        assertEquals(
                DeathScenario.SPOUSE_DIES,
                assumptions.getDeathScenario());

        assertEquals(
                2035,
                assumptions.getDeathYear());
    }

    @Test
    void deathScenarioIsActiveBeginningInDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertFalse(
                assumptions.isDeathScenarioActive(2034));

        assertTrue(
                assumptions.isDeathScenarioActive(2035));

        assertTrue(
                assumptions.isDeathScenarioActive(2036));
    }

    @Test
    void bothSurviveIsNeverActive() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null);

        assertFalse(
                assumptions.isDeathScenarioActive(2034));

        assertFalse(
                assumptions.isDeathScenarioActive(2035));

        assertFalse(
                assumptions.isDeathScenarioActive(2040));
    }

    @Test
    void deathScenarioWithoutDeathYearIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                null));
    }

    @Test
    void zeroDeathYearIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                0));
    }

    @Test
    void negativeDeathYearIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                -1));
    }

    @Test
    void nullScenarioIsRejected() {

        assertThrows(
                NullPointerException.class,
                () ->
                        new DeathScenarioAssumptions(
                                null,
                                null));
    }

    @Test
    void primaryIncomeContinuesBeforePrimaryDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2034));
    }

    @Test
    void primaryIncomeStopsBeginningInDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertFalse(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2035));

        assertFalse(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2036));
    }

    @Test
    void spouseIncomeContinuesWhenPrimaryDies() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2035));
    }



    @Test
    void jointIncomeContinuesWhenPrimaryDies() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.JOINT,
                        2035));
    }

    @Test
    void spouseIncomeStopsBeginningInSpouseDeathYear() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.SPOUSE_DIES,
                        2035);

        assertFalse(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2035));

        assertFalse(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2036));
    }

    @Test
    void primaryIncomeContinuesWhenSpouseDies() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.SPOUSE_DIES,
                        2035);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2035));
    }

    @Test
    void jointIncomeContinuesWhenSpouseDies() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.SPOUSE_DIES,
                        2035);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.JOINT,
                        2035));
    }

    @Test
    void allIncomeContinuesWhenBothSurvive() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null);

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.PRIMARY,
                        2040));

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.SPOUSE,
                        2040));

        assertTrue(
                assumptions.isIncomeActive(
                        AccountOwnership.JOINT,
                        2040));
    }

    @Test
    void survivorClaimingAgeCanBeSpecified() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        65);

        assertEquals(
                65,
                assumptions.getSurvivorClaimingAge());
    }


    @Test
    void survivorClaimingAge62IsValid() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        62);

        assertEquals(
                62,
                assumptions.getSurvivorClaimingAge());
    }

    @Test
    void survivorClaimingAge70IsValid() {

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        70);

        assertEquals(
                70,
                assumptions.getSurvivorClaimingAge());
    }

    @Test
    void survivorClaimingAgeBelow62IsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                61));
    }


    @Test
    void survivorClaimingAgeAbove70IsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                71));
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


}