package com.daviddunn.retirementplanner.domain.income;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullRetirementAgeCalculatorTest {

    @Test
    void shouldReturn66ForBirthYears1954AndEarlier() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1954, 12, 31));

        assertEquals(66, fra.years());
        assertEquals(0, fra.months());
    }

    @Test
    void shouldReturn66Years2MonthsFor1955() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1955, 6, 15));

        assertEquals(66, fra.years());
        assertEquals(2, fra.months());
    }

    @Test
    void shouldReturn66Years4MonthsFor1956() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1956, 1, 1));

        assertEquals(66, fra.years());
        assertEquals(4, fra.months());
    }

    @Test
    void shouldReturn66Years6MonthsFor1957() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1957, 8, 20));

        assertEquals(66, fra.years());
        assertEquals(6, fra.months());
    }

    @Test
    void shouldReturn66Years8MonthsFor1958() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1958, 2, 10));

        assertEquals(66, fra.years());
        assertEquals(8, fra.months());
    }

    @Test
    void shouldReturn66Years10MonthsFor1959() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1959, 11, 30));

        assertEquals(66, fra.years());
        assertEquals(10, fra.months());
    }

    @Test
    void shouldReturn67For1960AndLater() {

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        LocalDate.of(1963, 6, 4));

        assertEquals(67, fra.years());
        assertEquals(0, fra.months());
    }
}