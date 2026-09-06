package com.daviddunn.retirementplanner.ui.summary;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionYearDetailsOrganizationTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java");

    @Test
    void presentsSectionsAndSubsectionsInAnnualFlowOrder()
            throws Exception {

        String source = Files.readString(SOURCE);

        assertTokensInOrder(
                methodBody(source, "public ProjectionYearDetailsPane", "private GridPane createComparisonGrid"),
                List.of(
                        "createCashFlowRows",
                        "createIncomeSourceRows",
                        "createRetirementActivityRows",
                        "createTaxRows",
                        "createMedicareRows",
                        "createPortfolioRows",
                        "createEstateRows"));

        assertInOrder(
                methodBody(source, "private int createRetirementActivityRows", "private int createTaxRows"),
                List.of(
                        "Roth Conversions",
                        "Required Minimum Distributions"));

        assertTrue(methodBody(
                source,
                "private int createIncomeSourceRows",
                "private int createRetirementActivityRows")
                .contains(quoted("Social Security")));

        String taxRows = methodBody(
                source,
                "private int createTaxRows",
                "private int createFederalTaxRows");
        assertTokensInOrder(
                taxRows,
                List.of(
                        "createFederalTaxRows",
                        "createMichiganTaxRows",
                        quoted("Tax Summary")));
        assertTrue(methodBody(
                source,
                "private int createFederalTaxRows",
                "private int createMichiganTaxRows")
                .contains(quoted("Federal")));
        assertTrue(methodBody(
                source,
                "private int createMichiganTaxRows",
                "private int createMedicareRows")
                .contains(quoted("Michigan")));

        assertInOrder(
                methodBody(source, "private int createMedicareRows", "private int createPortfolioRows"),
                List.of(
                        "Part B",
                        "Part D"));
        assertInOrder(
                methodBody(source, "private int createPortfolioRows", "private int createEstateRows"),
                List.of(
                        "Portfolio Activity",
                        "Ending Portfolio Composition",
                        "Retained Non-Qualified Assets"));
    }

    @Test
    void placesRepresentativeRowsInTheirIntendedGroups()
            throws Exception {

        String source = Files.readString(SOURCE);

        assertMethodContains(source, "createCashFlowRows", "createIncomeSourceRows", "Portfolio Withdrawal");
        assertMethodContains(source, "createIncomeSourceRows", "createRetirementActivityRows", "Primary Survivor Candidate");
        assertMethodContains(source, "createRetirementActivityRows", "createTaxRows", "Roth Conversion");
        assertMethodContains(source, "createRetirementActivityRows", "createTaxRows", "Required Minimum Distribution");
        assertMethodContains(source, "createFederalTaxRows", "createMichiganTaxRows", "Federal Income Tax");
        assertMethodContains(source, "createMichiganTaxRows", "createMedicareRows", "Michigan Income Tax");
        assertMethodContains(source, "createTaxRows", "createFederalTaxRows", "Combined Effective Tax Rate");
        assertMethodContains(source, "createMedicareRows", "createPortfolioRows", "IRMAA Bracket");
        assertMethodContains(source, "createPortfolioRows", "createEstateRows", "Tax-Deferred Accounts");
        assertMethodContains(source, "createPortfolioRows", "createEstateRows", "Ending Retained Non-Qualified Assets");

        assertTrue(sectionFrom(source, "Estate & Net Worth")
                .contains(quoted("Projected After-Tax Estate")));
    }

    @Test
    void retainsMajorMetricsWithoutDuplicateGrossEstateRow()
            throws Exception {

        String source = Files.readString(SOURCE);

        List<String> labels = List.of(
                "Guaranteed Income",
                "Annual Expenses",
                "Cash Flow Need",
                "Tax Funding Withdrawal",
                "Household Social Security Received",
                "Requested Roth Conversion",
                "Roth Conversion Shortfall",
                "RMD Distributed Before Projection",
                "RMD Distributed in Projection",
                "Excess RMD",
                "Adjusted Gross Income",
                "Taxable Social Security",
                "Standard Deduction",
                "Federal Taxable Income",
                "Modified Adjusted Gross Income",
                "Total Annual Medicare Premium",
                "Beginning Assets",
                "Investment Growth",
                "Ending Assets",
                "Roth Accounts",
                "Taxable Accounts",
                "Cash Accounts",
                "Non-Investable Assets",
                "Total Net Worth",
                "Estimated Heir Tax");

        labels.forEach(label ->
                assertTrue(source.contains(quoted(label)), label));

        assertFalse(source.contains(quoted("Total Gross Estate")));
    }

    private void assertInOrder(
            String source,
            List<String> labels) {

        int previous = -1;

        for (String label : labels) {
            int current = source.indexOf(quoted(label));
            assertTrue(current > previous, label);
            previous = current;
        }
    }

    private void assertTokensInOrder(
            String source,
            List<String> tokens) {

        int previous = -1;

        for (String token : tokens) {
            int current = source.indexOf(token);
            assertTrue(current > previous, token);
            previous = current;
        }
    }

    private void assertMethodContains(
            String source,
            String start,
            String end,
            String expected) {

        assertTrue(
                methodBody(
                        source,
                        "private int " + start,
                        "private int " + end)
                        .contains(quoted(expected)),
                expected);
    }

    private String methodBody(
            String source,
            String start,
            String end) {

        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + 1);

        assertTrue(startIndex >= 0, start);
        assertTrue(endIndex > startIndex, end);

        return source.substring(startIndex, endIndex);
    }

    private String sectionFrom(
            String source,
            String start) {

        return source.substring(source.indexOf(quoted(start)));
    }

    private String quoted(String value) {
        return "\"" + value + "\"";
    }
}
