package com.daviddunn.retirementplanner.ui.views;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FutureFederalTaxRateChangePresentationTest {

    @Test
    void bothAssumptionEditorsUseSharedClearablePairSemantics()
            throws Exception {

        assertSharedParserAndBlankDisplay(
                "AssumptionsView.java");
        assertSharedParserAndBlankDisplay(
                "ResultsSummaryView.java");
    }

    private void assertSharedParserAndBlankDisplay(
            String fileName)
            throws Exception {

        String source = Files.readString(Path.of(
                "src/main/java/com/daviddunn/retirementplanner/ui/views/"
                        + fileName));

        assertTrue(source.contains(
                "FutureFederalTaxRateChangeInput.parse("),
                fileName);
        assertTrue(source.contains(
                "futureFederalMarginalRateAdjustment != null"),
                fileName);
    }
}
