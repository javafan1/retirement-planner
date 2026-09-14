package com.daviddunn.retirementplanner.ui.socialsecurity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerInputMatrix.Use.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityAnalyzerInputPresentationTest {
    @Test
    void configuredFinancialValuesRefreshWithoutMutatingPreviousSummary() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var account = new com.daviddunn.retirementplanner.domain.financial.SavingsAccount("Savings",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.JOINT, new BigDecimal("12345.67"));
        plan.getAccountPortfolio().addAccount(account);
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Living", new BigDecimal("42000")));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension(
                "Pension", com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("2100"), new BigDecimal("0.025"), new BigDecimal("1000")));
        var before = SocialSecurityAnalyzerInputSummary.from(plan, values());
        assertTrue(before.rows().stream().anyMatch(row -> row.input().equals("Account balances / ownership") && row.value().contains("joint $12345.67")));
        assertTrue(before.rows().stream().anyMatch(row -> row.input().equals("Expenses") && row.value().contains("Living: $42000.00")));
        assertTrue(before.rows().stream().anyMatch(row -> row.input().equals("Pension assumptions")
                && row.value().contains("$2100.00/month; survivor $1000.00/month; COLA 2.5%")));
        account.setCurrentBalance(new BigDecimal("999"));
        var after = SocialSecurityAnalyzerInputSummary.from(plan, values());
        assertTrue(after.rows().stream().anyMatch(row -> row.input().equals("Account balances / ownership") && row.value().contains("joint $999.00")));
        assertTrue(before.rows().stream().anyMatch(row -> row.input().equals("Account balances / ownership") && row.value().contains("joint $12345.67")));
    }
    @Test
    void completeMatrixClassifiesEveryFinancialDependency() {
        var rows = SocialSecurityAnalyzerInputMatrix.rows();
        assertEquals(35, rows.size());
        assertEquals(35, rows.stream().map(SocialSecurityAnalyzerInputMatrix.Row::input).distinct().count());
        var common = Set.of("Primary DOB", "Spouse DOB", "Primary FRA benefit", "Spouse FRA benefit",
                "Primary retirement claim age", "Spouse retirement claim age", "Primary survivor claim age",
                "Spouse survivor claim age", "Social Security COLA", "Ranking objective");
        var longevity = Set.of("Primary mortality category", "Spouse mortality category", "Primary mortality adjustment",
                "Spouse mortality adjustment", "Mortality conditioning date", "Valuation date", "Real discount rate",
                "Mortality probability distribution");
        for (var row : rows) {
            assertEquals(common.contains(row.input()) || longevity.contains(row.input()) ? USED : NOT_USED, row.socialSecurity(), row.input());
            var deterministic = switch (row.input()) {
                case "Primary survivor claim age" -> PRIMARY_SURVIVES;
                case "Spouse survivor claim age" -> SPOUSE_SURVIVES;
                case "Current Strategy Baseline (analyzer survivor inputs)", "Estate at household second death" -> NOT_USED;
                default -> longevity.contains(row.input()) ? NOT_USED : USED;
            };
            assertEquals(deterministic,
                    row.deterministic(), row.input());
            var expected = switch (row.input()) {
                case "Configured projection end date", "Configured projection length" -> REFERENCE_ONLY;
                case "Configured plan death scenario", "Configured-horizon estate" -> NOT_USED;
                default -> USED;
            };
            assertEquals(expected, row.weighted(), row.input());
        }
        assertEquals("Reference only", REFERENCE_ONLY.text());
        assertTrue(SocialSecurityAnalyzerInputMatrix.DETERMINISTIC_OBJECTIVE.contains("Quick Comparison retains SS candidate order"));
    }

    @Test
    void currentValuesUseCalendarHorizonAndIndependentDates() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var rows = SocialSecurityAnalyzerInputSummary.from(plan, values()).rows().stream()
                .collect(Collectors.toMap(SocialSecurityAnalyzerInputSummary.Row::input, row -> row));
        assertEquals("2026-07-01", rows.get("Projection start date").value());
        assertEquals("2027-12-31", rows.get("Configured projection end date").value());
        assertEquals("2 calendar years (2026-2027, inclusive; opening year may be partial)", rows.get("Configured projection length").value());
        assertEquals("2026-01-01", rows.get("Mortality conditioning date").value());
        assertEquals("2025-01-01", rows.get("Valuation date").value());
        assertEquals("1%", rows.get("Real discount rate").value());
        assertEquals("3% annual portfolio return", rows.get("Investment return assumptions").value());
        assertEquals("2%", rows.get("General inflation").value());
        assertTrue(rows.get("Weighted scenario horizon").value().contains("secondDeathYear - 1"));
        assertTrue(rows.get("Weighted scenario horizon").value().contains("opening snapshot"));
        assertTrue(rows.get("Configured plan death scenario").value().contains("Deterministic only"));
        assertTrue(rows.get("State / local tax jurisdiction").value().contains("No named jurisdiction stored"));
        assertEquals("Analyzer", rows.get("Valuation date").source());
        assertEquals("Person information", rows.get("Primary mortality category").source());
        assertEquals("Person information", rows.get("Spouse mortality category").source());
        assertEquals("male", rows.get("Primary mortality category").value());
        assertEquals("female", rows.get("Spouse mortality category").value());
        assertEquals("None configured", rows.get("Expenses").value());
        assertEquals("None configured", rows.get("Pension assumptions").value());
        assertEquals("Disabled", rows.get("Roth conversion strategy").value());
    }

    @Test
    void snapshotIsImmutableAndMissingInputsAreNotInvented() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var summary = SocialSecurityAnalyzerInputSummary.from(plan, values());
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1970, 1, 1));
        assertTrue(summary.rows().stream().anyMatch(row -> row.input().equals("Primary DOB") && row.value().equals("1963-06-04")));
        assertThrows(UnsupportedOperationException.class, () -> summary.rows().clear());
        var missing = SocialSecurityAnalyzerInputSummary.from(plan,
                new SocialSecurityAnalyzerInputSummary.AnalyzerValues(null, null, null, null, null));
        assertTrue(missing.rows().stream().anyMatch(row -> row.input().equals("Valuation date") && row.value().equals("Missing / invalid")));
    }

    private static SocialSecurityAnalyzerInputSummary.AnalyzerValues values() {
        return new SocialSecurityAnalyzerInputSummary.AnalyzerValues(
                new BigDecimal("0.8"),
                BigDecimal.ONE, LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1), new BigDecimal("0.01"));
    }
}
