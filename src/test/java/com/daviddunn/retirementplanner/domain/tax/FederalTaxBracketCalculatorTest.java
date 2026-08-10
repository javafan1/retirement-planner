package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederalTaxBracketCalculatorTest {

    @Test
    void finds22PercentBracket() {

        FederalTaxRules rules =
                new FederalTaxRules(
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        new BigDecimal("30000"),
                        List.of(
                                new FederalTaxBracket(
                                        new BigDecimal("0"),
                                        new BigDecimal("23200"),
                                        new BigDecimal("0.10")),

                                new FederalTaxBracket(
                                        new BigDecimal("23200"),
                                        new BigDecimal("94300"),
                                        new BigDecimal("0.12")),

                                new FederalTaxBracket(
                                        new BigDecimal("94300"),
                                        new BigDecimal("201050"),
                                        new BigDecimal("0.22")),

                                new FederalTaxBracket(
                                        new BigDecimal("201050"),
                                        new BigDecimal("383900"),
                                        new BigDecimal("0.24"))
                        ));

        FederalTaxBracketCalculator calculator =
                new FederalTaxBracketCalculator();

        FederalTaxBracket bracket =
                calculator.findBracket(
                        rules,
                        new BigDecimal("0.22"));

        assertEquals(
                0,
                new BigDecimal("94300")
                        .compareTo(
                                bracket.getLowerBound()));

        assertEquals(
                0,
                new BigDecimal("201050")
                        .compareTo(
                                bracket.getUpperBound()));

        assertEquals(
                0,
                new BigDecimal("0.22")
                        .compareTo(
                                bracket.getTaxRate()));
    }
}