package com.daviddunn.retirementplanner.ui.views;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsSummaryBaselineMetricsTest {

    @Test
    void comparisonCardsHaveExpectedOrderAndNoEffectiveTaxRate() throws IOException {

        String source = Files.readString(Path.of(
                "src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java"));
        int start = source.indexOf("private VBox createBaselineComparisonSection()");
        int end = source.indexOf("private VBox createComparisonMetricCard", start);
        String section = source.substring(start, end);

        List<String> titles = List.of(
                "Investment Growth",
                "Total Income",
                "Total Taxes",
                "Peak Annual Tax",
                "Investable Assets",
                "Total Net Worth",
                "Investable After-Tax Estate");

        int previous = -1;
        for (String title : titles) {
            int index = section.indexOf('"' + title + '"');
            assertTrue(index > previous, title);
            previous = index;
        }

        assertEquals(7, count(section, "createComparisonMetricCard("));
        assertFalse(section.contains("Effective Tax Rate"));
        assertFalse(section.contains("Average Effective Tax Rate"));
        assertFalse(section.contains("Today's Dollars"));

        assertTrue(source.contains(
                "comparison.getTotalTaxesChange(), true"));
        assertTrue(source.contains(
                "comparison.getPeakAnnualTaxChange(), true"));
    }

    private int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
