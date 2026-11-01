package com.daviddunn.retirementplanner.ui.views;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FutureFederalTaxRateChangePresentationTest {

    @Test
    void federalRateChangeControlsBelongOnlyToAssumptionsScreen()
            throws Exception {

        assertSharedParserAndBlankDisplay(
                "AssumptionsView.java");

        String summary = Files.readString(Path.of(
                "src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java"));
        assertFalse(summary.contains("FutureFederalTaxRateChangeInput"));
        assertFalse(summary.contains("futureFederalMarginalRateChangeField"));
        assertFalse(summary.contains("futureFederalMarginalRateEffectiveYearField"));
        assertFalse(summary.contains("Future Federal Tax Rate Change"));
        assertFalse(summary.contains("Effective Federal Tax Change Year"));
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
