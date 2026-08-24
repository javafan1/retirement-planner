package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RothConversionTargetBracketResolverTest {

    private final RothConversionTargetBracketResolver resolver =
            new RothConversionTargetBracketResolver();

    @Test
    void resolves12PercentBracket() {

        FederalTaxRules rules =
                createRules();

        FederalTaxBracket bracket =
                resolver.resolve(
                        RothConversionStrategy
                                .FILL_12_PERCENT_BRACKET,
                        rules);

        assertEquals(
                0,
                new BigDecimal("0.12")
                        .compareTo(
                                bracket.getTaxRate()));
    }


    @Test
    void resolves22PercentBracket() {

        FederalTaxRules rules =
                createRules();

        FederalTaxBracket bracket =
                resolver.resolve(
                        RothConversionStrategy
                                .FILL_22_PERCENT_BRACKET,
                        rules);

        assertEquals(
                0,
                new BigDecimal("0.22")
                        .compareTo(
                                bracket.getTaxRate()));
    }


    @Test
    void resolves24PercentBracket() {

        FederalTaxRules rules =
                createRules();

        FederalTaxBracket bracket =
                resolver.resolve(
                        RothConversionStrategy
                                .FILL_24_PERCENT_BRACKET,
                        rules);

        assertEquals(
                0,
                new BigDecimal("0.24")
                        .compareTo(
                                bracket.getTaxRate()));
    }

    @Test
    void rejectsStrategiesWithoutStatutoryTargetBrackets() {

        FederalTaxRules rules =
                createRules();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        resolver.resolve(
                                RothConversionStrategy.FIXED_AMOUNT,
                                rules));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        resolver.resolve(
                                RothConversionStrategy
                                        .CUSTOM_TAXABLE_INCOME_TARGET,
                                rules));
    }


    private FederalTaxRules createRules() {

        return new FederalTaxRules(
                com.daviddunn.retirementplanner.domain.rules.FilingStatus
                        .MARRIED_FILING_JOINTLY,
                new BigDecimal("32200"),
                List.of(
                        new FederalTaxBracket(
                                new BigDecimal("0"),
                                new BigDecimal("24800"),
                                new BigDecimal("0.10")),

                        new FederalTaxBracket(
                                new BigDecimal("24800"),
                                new BigDecimal("100800"),
                                new BigDecimal("0.12")),

                        new FederalTaxBracket(
                                new BigDecimal("100800"),
                                new BigDecimal("211400"),
                                new BigDecimal("0.22")),

                        new FederalTaxBracket(
                                new BigDecimal("211400"),
                                new BigDecimal("403550"),
                                new BigDecimal("0.24"))
                ));
    }
}
