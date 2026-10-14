package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Independent audit worksheets, not calculators or production tax-law certification.
 * Numeric rows check explicitly supplied, independently worked state bases only.
 * Unsupported rows preserve missing facts/reasons; they do not execute an IA/MS adapter.
 * Pending legal conclusions are skipped, never promoted into passing law tests.
 * Source IDs resolve in PHASE-6D5A-IOWA-MISSISSIPPI-PRODUCTION-ADMISSION-AUDIT.md.
 */
class IowaMississippiAdmissionWorksheetTest {
    private record Worksheet(JsonNode row) {
        @Override
        public String toString() {
            return row.path("id").asText() + " / " + row.path("category").asText();
        }
    }

    private static List<Worksheet> worksheets() throws Exception {
        try (var input = IowaMississippiAdmissionWorksheetTest.class.getResourceAsStream(
                "/tax/state/phase6d5a-admission-worksheets.json")) {
            assertNotNull(input);
            var rows = new ArrayList<Worksheet>();
            new ObjectMapper().readTree(input).forEach(row -> rows.add(new Worksheet(row)));
            return List.copyOf(rows);
        }
    }

    static Stream<Worksheet> iowa() throws Exception {
        return worksheets().stream().filter(sheet -> sheet.row().path("state").asText().equals("IA"));
    }

    static Stream<Worksheet> mississippi() throws Exception {
        return worksheets().stream().filter(sheet -> sheet.row().path("state").asText().equals("MS"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("iowa")
    void independentIowaWorksheet(Worksheet worksheet) {
        checkWorksheet(worksheet);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mississippi")
    void independentMississippiWorksheet(Worksheet worksheet) {
        checkWorksheet(worksheet);
    }

    private static void checkWorksheet(Worksheet worksheet) {
        var row = worksheet.row();
        assertFalse(row.path("inputs").asText().isBlank());
        assertFalse(row.path("source").asText().isBlank());
        assertTrue(row.path("year").asInt() >= 2026);
        String outcome = row.path("outcome").asText();
        if (outcome.equals("PENDING")) {
            assertFalse(row.has("expectedTax"));
            assumeTrue(false, "Pending authoritative guidance: " + worksheet);
        } else if (outcome.equals("EXPECTED_UNSUPPORTED")) {
            assertFalse(row.has("expectedTax"), "Unknown facts must not acquire a numeric golden");
            assertNotNull(StateTaxUnavailableReason.valueOf(row.path("reason").asText()));
        } else {
            assertEquals("VERIFIED_CALCULATION", outcome);
            BigDecimal taxable = decimal(row, "taxableBeforeBand")
                    .subtract(decimal(row, "zeroBand")).max(BigDecimal.ZERO);
            BigDecimal worked = taxable.multiply(decimal(row, "rate"))
                    .subtract(decimal(row, "credit")).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, decimal(row, "expectedTax").compareTo(worked));
        }
    }

    private static BigDecimal decimal(JsonNode row, String field) {
        assertTrue(row.has(field));
        return new BigDecimal(row.path(field).asText());
    }

    @Test
    void independentInventoryHasUniqueIdsAndAtLeastTwentyRowsPerState() throws Exception {
        var sheets = worksheets();
        var ids = new HashSet<String>();
        for (var sheet : sheets) {
            assertTrue(ids.add(sheet.row().path("id").asText()));
        }
        assertEquals(26, iowa().count());
        assertEquals(29, mississippi().count());
    }

    @Test
    void iowaAdmissionLeavesMississippiDisabledAndResearchCatalogUnchanged() throws Exception {
        assertEquals(12, StateTaxJurisdiction.values().length);
        assertEquals(StateTaxJurisdiction.IA, StateTaxJurisdiction.fromCode("IA"));
        assertThrows(IllegalArgumentException.class, () -> StateTaxJurisdiction.fromCode("MS"));
        var catalog = new com.daviddunn.retirementplanner.persistence.StateTaxDatasetRepository().load();
        assertFalse(catalog.containsKey("IA"));
        assertFalse(catalog.containsKey("MS"));
    }

    @Test
    void iowaUsesYearEndAgeRatherThanTheAnnualProjectionReceiptBoundary() {
        var end = LocalDate.of(2026, 12, 31);
        assertFalse(end.isBefore(LocalDate.of(1971, 12, 31).plusYears(55)));
        assertTrue(end.isBefore(LocalDate.of(1972, 1, 1).plusYears(55)));
        assertTrue(LocalDate.of(2026, 1, 1).isBefore(LocalDate.of(1971, 12, 31).plusYears(55)));
    }

    @Test
    void participantFactsCannotBeAppliedRetroactivelyAndUndatedFactsRemainUnknown() {
        var event = LocalDate.of(2030, 6, 1);
        var facts = new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.SEPARATED, event,
                RetirementParticipantFacts.RetirementConditionsStatus.MET, event);
        assertEquals(RetirementParticipantFacts.SeparationStatus.NOT_SEPARATED,
                facts.separationStatusOn(LocalDate.of(2026, 12, 31)));
        assertEquals(RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET,
                facts.retirementConditionsStatusOn(event.minusDays(1)));
        assertEquals(RetirementParticipantFacts.RetirementConditionsStatus.MET,
                facts.retirementConditionsStatusOn(event));
        var undated = new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.SEPARATED, null,
                RetirementParticipantFacts.RetirementConditionsStatus.MET, null);
        assertEquals(RetirementParticipantFacts.RetirementConditionsStatus.UNKNOWN,
                undated.retirementConditionsStatusOn(event));
    }
}
