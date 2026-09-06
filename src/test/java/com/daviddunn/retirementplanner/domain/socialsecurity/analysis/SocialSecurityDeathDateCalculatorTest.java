package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecurityDeathDateCalculatorTest {

    @Test
    void deathAgeMeansActualChronologicalBirthday() {
        assertEquals(LocalDate.of(2048, 1, 1),
                SocialSecurityDeathDateCalculator.calculateDeathDate(
                        LocalDate.of(1963, 1, 1),
                        85));
    }

    @Test
    void leapDayUsesStandardLocalDatePlusYearsBehavior() {
        assertEquals(LocalDate.of(2025, 2, 28),
                SocialSecurityDeathDateCalculator.calculateDeathDate(
                        LocalDate.of(1964, 2, 29),
                        61));
    }

    @Test
    void invalidAgesAreRejected() {
        LocalDate birthDate = LocalDate.of(1963, 1, 2);
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityDeathDateCalculator.calculateDeathDate(
                        birthDate, -1));
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityDeathDateCalculator.calculateDeathDate(
                        birthDate, 0));
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityDeathDateCalculator.calculateDeathDate(
                        birthDate, 121));
    }
}
