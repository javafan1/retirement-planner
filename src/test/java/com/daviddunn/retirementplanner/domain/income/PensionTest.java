package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PensionTest {

    private final Person person =
            new Person(
                    "John",
                    "Doe",
                    LocalDate.of(1963, 1, 1));

    @Test
    void pensionStartingJulyPaysSixMonthsInFirstYear() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 7, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2026, 1, 1));

        assertEquals(
                new BigDecimal("22800"),
                income);
    }

    @Test
    void pensionPaysTwelveMonthsAfterStartYear() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 7, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2027, 1, 1));

        assertEquals(
                new BigDecimal("45600"),
                income);
    }

    @Test
    void pensionBeforeStartYearReturnsZero() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 7, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2025, 1, 1));

        assertEquals(
                BigDecimal.ZERO,
                income);
    }

    @Test
    void pensionStartingJanuaryPaysTwelveMonths() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2026, 1, 1));

        assertEquals(
                new BigDecimal("45600"),
                income);
    }

    @Test
    void pensionEndingJunePaysSixMonthsInFinalYear() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2026, 1, 1));

        assertEquals(
                new BigDecimal("22800"),
                income);
    }

    @Test
    void pensionAfterEndDateReturnsZero() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2027, 1, 1));

        assertEquals(
                BigDecimal.ZERO,
                income);
    }

    @Test
    void pensionAppliesColaAfterFirstYear() {

        Pension pension =
                new Pension(
                        "COLA Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("3800"),
                        new BigDecimal("0.02"));

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2027, 1, 1));

        assertEquals(
                new BigDecimal("46512.00"),
                income);
    }

    @Test
    void pensionStoresSurvivorMonthlyBenefit() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO,
                        new BigDecimal("1900"));

        assertEquals(
                new BigDecimal("1900"),
                pension.getSurvivorMonthlyBenefit());
    }

    @Test
    void pensionWithoutSurvivorBenefitReturnsNull() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO);

        assertNull(
                pension.getSurvivorMonthlyBenefit());
    }

    @Test
    void survivorBenefitDoesNotChangePensionIncome() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("3800"),
                        BigDecimal.ZERO,
                        new BigDecimal("1900"));

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2026, 1, 1));

        assertEquals(
                new BigDecimal("45600"),
                income);
    }

    @Test
    void pensionWithSurvivorBenefitStillEndsOnPensionEndDate() {

        Pension pension =
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2035, 12, 31),
                        new BigDecimal("3800"),
                        BigDecimal.ZERO,
                        new BigDecimal("1900"));

        BigDecimal income =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2035, 1, 1));

        assertEquals(
                new BigDecimal("45600"),
                income);

        BigDecimal incomeAfterEndDate =
                pension.getAnnualIncome(
                        person,
                        LocalDate.of(2036, 1, 1));

        assertEquals(
                BigDecimal.ZERO,
                incomeAfterEndDate);
    }
}