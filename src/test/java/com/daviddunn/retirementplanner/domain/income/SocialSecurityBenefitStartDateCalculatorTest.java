package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Person;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityBenefitStartDateCalculatorTest {

    @Test
    void derivesBenefitStartDateFromBirthDateAndClaimingAge() {

        Person person = new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));

        assertEquals(
                LocalDate.of(2030, 6, 4),
                SocialSecurityBenefitStartDateCalculator
                        .calculate(person, 67)
                        .orElseThrow());
    }

    @Test
    void changingClaimingAgeChangesDerivedBenefitStartDate() {

        Person person = new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));

        assertEquals(
                LocalDate.of(2033, 6, 4),
                SocialSecurityBenefitStartDateCalculator
                        .calculate(person, 70)
                        .orElseThrow());
    }

    @Test
    void februaryTwentyNinthUsesLocalDateAdjustment() {

        Person person = new Person(
                "Leap",
                "Year",
                LocalDate.of(1964, 2, 29));

        assertEquals(
                LocalDate.of(2031, 2, 28),
                SocialSecurityBenefitStartDateCalculator
                        .calculate(person, 67)
                        .orElseThrow());
    }

    @Test
    void missingBirthDateReturnsUnavailableWithoutThrowing() {

        Person person = new Person();

        assertTrue(
                SocialSecurityBenefitStartDateCalculator
                        .calculate(person, 67)
                        .isEmpty());
    }

    @Test
    void derivingNormalElectionDoesNotChangeSurvivorClaimingAge() {

        Person person = new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));

        DeathScenarioAssumptions assumptions =
                new DeathScenarioAssumptions(
                        DeathScenario.SPOUSE_DIES,
                        2030,
                        62);

        SocialSecurityBenefitStartDateCalculator
                .calculate(person, 70);

        assertEquals(
                62,
                assumptions.getSurvivorClaimingAge());
    }

    @Test
    void existingInconsistentRecordRemainsLoadableUntilEdited() {

        SocialSecurityIncome existing =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2032, 6, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO,
                        2026);

        assertEquals(
                LocalDate.of(2032, 6, 1),
                existing.getStartDate());

        Person person = new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));

        assertEquals(
                LocalDate.of(2030, 6, 4),
                SocialSecurityBenefitStartDateCalculator
                        .calculate(
                                person,
                                existing.getClaimingAge())
                        .orElseThrow());
    }
}
