package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FederalTaxBracketLookupServiceTest {

    private FederalTaxBracketLookupService service;

    private FederalTaxRules federalTaxRules;

    @BeforeEach
    void setUp() {

        service = new FederalTaxBracketLookupService();

        federalTaxRules =
                new FederalTaxRules(
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        BigDecimal.ZERO,
                        List.of(

                                new FederalTaxBracket(
                                        new BigDecimal("0"),
                                        new BigDecimal("23850"),
                                        new BigDecimal("0.10")),

                                new FederalTaxBracket(
                                        new BigDecimal("23850"),
                                        new BigDecimal("96950"),
                                        new BigDecimal("0.12")),

                                new FederalTaxBracket(
                                        new BigDecimal("96950"),
                                        new BigDecimal("206700"),
                                        new BigDecimal("0.22")),

                                new FederalTaxBracket(
                                        new BigDecimal("206700"),
                                        new BigDecimal("394600"),
                                        new BigDecimal("0.24")),

                                new FederalTaxBracket(
                                        new BigDecimal("394600"),
                                        null,
                                        new BigDecimal("0.32"))
                        ));
    }

    @Test
    void shouldFindBracketByRate() {

        FederalTaxBracket bracket =
                service.findBracket(
                        federalTaxRules,
                        new BigDecimal("0.22"));

        assertEquals(
                new BigDecimal("96950"),
                bracket.getLowerBound());

        assertEquals(
                new BigDecimal("206700"),
                bracket.getUpperBound());

        assertEquals(
                new BigDecimal("0.22"),
                bracket.getTaxRate());
    }

    @Test
    void shouldCalculateRemainingCapacity() {

        BigDecimal remainingCapacity =
                service.calculateRemainingCapacity(
                        federalTaxRules,
                        new BigDecimal("165000"),
                        new BigDecimal("0.22"));

        assertEquals(
                new BigDecimal("41700"),
                remainingCapacity);
    }

    @Test
    void shouldReturnZeroWhenAlreadyAboveTargetBracket() {

        BigDecimal remainingCapacity =
                service.calculateRemainingCapacity(
                        federalTaxRules,
                        new BigDecimal("250000"),
                        new BigDecimal("0.22"));

        assertEquals(
                BigDecimal.ZERO,
                remainingCapacity);
    }

    @Test
    void shouldThrowWhenTaxRateNotFound() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.findBracket(
                                federalTaxRules,
                                new BigDecimal("0.15")));

        assertTrue(
                exception.getMessage()
                        .contains("Federal tax bracket not found"));
    }

    @Test
    void shouldThrowWhenTargetBracketHasNoUpperBound() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateRemainingCapacity(
                        federalTaxRules,
                        new BigDecimal("500000"),
                        new BigDecimal("0.32")));
    }
}