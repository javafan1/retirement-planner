package com.daviddunn.retirementplanner.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class StateTaxDatasetRepositoryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StateTaxDatasetRepository repository = new StateTaxDatasetRepository();

    @Test
    void archivedPhase6bDatasetIsByteIdenticalAndStillParses() throws Exception {
        try (var input = getClass().getResourceAsStream("/tax/state/state-tax-2026-phase6b.json")) {
            assertNotNull(input);
            assertArrayEquals(Files.readAllBytes(Path.of("state-tax-2026-eight-states.json")), input.readAllBytes());
        }
        try (var input = getClass().getResourceAsStream("/tax/state/state-tax-2026-phase6b.json")) {
            assertEquals(8, repository.parse(input).size());
        }
        assertEquals("2026-10-09-phase6c-validated-boundaries", repository.load().get("FL").datasetVersion());
    }

    @Test
    void everyDatasetChangeHasTraceableOldAndNewValuesAndReplaysExactly() throws Exception {
        ObjectNode replay;
        try (var original = getClass().getResourceAsStream("/tax/state/state-tax-2026-phase6b.json")) {
            replay = (ObjectNode) mapper.readTree(original);
        }
        JsonNode changes = mapper.readTree(Path.of("PHASE-6C-STATE-TAX-DATASET-CHANGES.json").toFile());
        assertEquals(44, changes.size());
        for (JsonNode change : changes) {
            String pointer = change.get("path").asText();
            int separator = pointer.lastIndexOf('/');
            JsonNode parent = replay.at(pointer.substring(0, separator));
            String key = pointer.substring(separator + 1).replace("~1", "/").replace("~0", "~");
            if (change.has("oldValue")) {
                assertEquals(change.get("oldValue"), replay.at(pointer), pointer);
            } else {
                assertTrue(replay.at(pointer).isMissingNode(), pointer);
            }
            assertNotEquals("remove", change.get("operation").asText(), "This patch should preserve original fields");
            if (parent.isArray()) {
                var array = (com.fasterxml.jackson.databind.node.ArrayNode) parent;
                int index = Integer.parseInt(key);
                if (index == array.size()) {
                    array.add(change.get("newValue"));
                } else {
                    array.set(index, change.get("newValue"));
                }
            } else {
                ((ObjectNode) parent).set(key, change.get("newValue"));
            }
        }
        try (var resource = getClass().getResourceAsStream("/tax/state/state-tax-2026.json")) {
            assertEquals(mapper.readTree(resource), replay);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"schema", "year", "currency", "count", "status", "base", "system", "rate",
            "negativeDeduction", "missingDeduction", "unknownField", "unknownTreatment", "missingTreatment",
            "unknownMethod", "unknownQualification", "unknownReference", "unknownScope", "duplicateId",
            "duplicateState", "missingState", "bracketGap", "bracketOverlap", "bracketOrder", "bracketIncomplete",
            "missingUpper", "missingStatus", "invalidFilingStatus", "badSurcharge", "badSurchargeBasis",
            "missingSurchargeRate", "ageOverlap", "ageGap", "negativeAge", "ageIncomplete", "incomeOverlap",
            "incomeGap", "badPercentage", "badBandMethod", "badSeniorReference", "badEligibility", "badPensionSource"})
    void rejectsMalformedOrUnsupportedConfiguration(String mutation) throws Exception {
        reject(root -> {
            ObjectNode ca = state(root, 0);
            ObjectNode mi = state(root, 1);
            ObjectNode il = state(root, 3);
            ObjectNode ny = state(root, 5);
            ObjectNode co = state(root, 6);
            ObjectNode nj = state(root, 7);
            ObjectNode bracket = (ObjectNode) ca.at("/taxSystem/brackets/SINGLE/1");
            ObjectNode adjustment = (ObjectNode) mi.at("/retirementAdjustments/0");
            ObjectNode ageBand = (ObjectNode) co.at("/retirementAdjustments/0/ageBands/1");
            ObjectNode incomeBand = (ObjectNode) nj.at("/retirementAdjustments/0/calculation/bands/1");
            switch (mutation) {
                case "schema" -> root.put("schemaVersion", "2");
                case "year" -> root.put("taxYear", 2027);
                case "currency" -> root.put("currency", "EUR");
                case "count" -> ((ObjectNode) root.get("model")).put("jurisdictionCount", 9);
                case "status" -> ca.put("calculationStatus", "APPROVED");
                case "base" -> ca.put("taxBase", "FEDERAL_TAX");
                case "system" -> ((ObjectNode) ca.get("taxSystem")).put("type", "UNKNOWN");
                case "rate" -> ((ObjectNode) mi.get("taxSystem")).put("rate", 1.1);
                case "negativeDeduction" -> ((ObjectNode) ca.at("/deductions/standardDeduction")).put("SINGLE", -1);
                case "missingDeduction" -> ((ObjectNode) ca.get("deductions")).remove("standardDeduction");
                case "unknownField" -> adjustment.put("extraExclusion", 100);
                case "unknownTreatment" -> ((ObjectNode) ca.get("incomeTreatment")).put("SOCIAL_SECURITY", "PARTIAL");
                case "missingTreatment" -> ((ObjectNode) ca.get("incomeTreatment")).remove("INTEREST");
                case "unknownMethod" -> ((ObjectNode) adjustment.get("calculation")).put("method", "MAGIC");
                case "unknownQualification" -> adjustment.put("qualification", "ALL_INCOME");
                case "unknownReference" -> ((com.fasterxml.jackson.databind.node.ArrayNode) adjustment.get("appliesTo")).add("WAGES");
                case "unknownScope" -> adjustment.put("scope", "ALL_PERSONS");
                case "duplicateId" -> ((ObjectNode) ny.at("/retirementAdjustments/1")).put("id", "NY_PRIVATE_PENSION_20000");
                case "duplicateState" -> mi.put("jurisdictionCode", "CA");
                case "missingState" -> ((com.fasterxml.jackson.databind.node.ArrayNode) root.get("jurisdictions")).remove(7);
                case "bracketGap" -> bracket.put("from", 11457);
                case "bracketOverlap" -> bracket.put("from", 11455);
                case "bracketOrder" -> bracket.put("to", 100);
                case "bracketIncomplete" -> ((ObjectNode) ca.at("/taxSystem/brackets/SINGLE/8")).put("to", 2000000);
                case "missingUpper" -> bracket.remove("to");
                case "missingStatus" -> ((ObjectNode) ca.at("/taxSystem/brackets")).remove("SINGLE");
                case "invalidFilingStatus" -> ((ObjectNode) ca.at("/deductions/standardDeduction")).put("HEAD_OF_HOUSEHOLD", 5900);
                case "badSurcharge" -> ((ObjectNode) ca.at("/additionalTaxes/0")).put("type", "AMT");
                case "badSurchargeBasis" -> ((ObjectNode) ca.at("/additionalTaxes/0")).put("basis", "FEDERAL_AGI");
                case "missingSurchargeRate" -> ((ObjectNode) ca.at("/additionalTaxes/0")).remove("rate");
                case "ageOverlap" -> ageBand.put("minimumAge", 64);
                case "ageGap" -> ageBand.put("minimumAge", 66);
                case "negativeAge" -> ageBand.put("minimumAge", -1);
                case "ageIncomplete" -> ageBand.put("maximumAge", 80);
                case "incomeOverlap" -> incomeBand.put("incomeFrom", 99999);
                case "incomeGap" -> incomeBand.put("incomeFrom", 100001);
                case "badPercentage" -> ((ObjectNode) incomeBand.get("percentage")).put("SINGLE", 1.1);
                case "badBandMethod" -> incomeBand.put("method", "UNKNOWN");
                case "badSeniorReference" -> ((ObjectNode) il.at("/deductions/personalExemption")).remove("federalAgiCeiling");
                case "badEligibility" -> ((ObjectNode) nj.at("/retirementAdjustments/0/eligibility")).put("ageTest", "BOTH_SPOUSES");
                case "badPensionSource" -> ((com.fasterxml.jackson.databind.node.ArrayNode) ny.at("/retirementAdjustments/1/pensionSources")).add("UNKNOWN_SOURCE");
                default -> throw new AssertionError(mutation);
            }
        });
    }

    @Test
    void rejectsDuplicateJsonPropertiesAndTrailingData() {
        assertThrows(IOException.class, () -> repository.parse(new ByteArrayInputStream(
                "{\"schemaVersion\":\"1.0-draft\",\"schemaVersion\":\"1.0-draft\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(IOException.class, () -> repository.parse(new ByteArrayInputStream(
                "{} {}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingSharedGroup", "mixedSharedFlags", "sharedScope", "differentSharedCaps",
            "unboundedSharedGroup", "fractionalAgeBand", "badBoundaryStage", "badBoundaryBasis",
            "badBoundaryTrigger", "missingBoundaryMaximum", "negativeBoundaryMaximum", "ignoredBoundaryEligibility",
            "unknownBoundaryQualification", "missingBoundaryTypes", "badBoundarySource", "badIncomeEndpoints",
            "badAboveBandPolicy", "invalidAgiEligibility", "allocationAge", "unconditionalBoundary"})
    void rejectsMalformedHardeningRules(String mutation) throws Exception {
        reject(root -> {
            ObjectNode mi = state(root, 1);
            ObjectNode nyBoundary = (ObjectNode) state(root, 5).at("/scenarioBoundaries/0");
            ObjectNode co = state(root, 6);
            ObjectNode nj = state(root, 7);
            ObjectNode shared = (ObjectNode) mi.at("/retirementAdjustments/1");
            ObjectNode conversion = (ObjectNode) nj.at("/scenarioBoundaries/0");
            switch (mutation) {
                case "missingSharedGroup" -> shared.remove("exclusionGroup");
                case "mixedSharedFlags" -> shared.put("coordinatedLimit", false);
                case "sharedScope" -> shared.put("scope", "PER_PERSON");
                case "differentSharedCaps" -> ((ObjectNode) shared.at("/calculation/maximumAmount")).put("SINGLE", 1);
                case "unboundedSharedGroup" -> {
                    for (JsonNode rule : mi.get("retirementAdjustments")) {
                        ObjectNode calculation = (ObjectNode) rule.get("calculation");
                        calculation.put("method", "UNLIMITED_ELIGIBLE_INCOME");
                        calculation.remove("maximumAmount");
                    }
                }
                case "fractionalAgeBand" -> ((ObjectNode) co.at("/retirementAdjustments/0/ageBands/0")).put("minimumAge", 55.5);
                case "badBoundaryStage" -> nyBoundary.put("stage", "AFTER_TAX");
                case "badBoundaryBasis" -> nyBoundary.put("basis", "FEDERAL_TAX");
                case "badBoundaryTrigger" -> nyBoundary.put("trigger", "IGNORE");
                case "missingBoundaryMaximum" -> nyBoundary.remove("maximumIncome");
                case "negativeBoundaryMaximum" -> ((ObjectNode) nyBoundary.get("maximumIncome")).put("SINGLE", -1);
                case "ignoredBoundaryEligibility" -> nyBoundary.put("minimumAge", 65);
                case "unknownBoundaryQualification" -> conversion.put("qualification", "ASSUME_ZERO_BASIS");
                case "missingBoundaryTypes" -> conversion.remove("incomeTypes");
                case "badBoundarySource" -> ((ObjectNode) mi.at("/scenarioBoundaries/0")).putArray("pensionSources").add("PRIVATE");
                case "badIncomeEndpoints" -> ((ObjectNode) nj.at("/retirementAdjustments/0/calculation")).put("incomeBandEndpoints", "LOWER_INCLUSIVE");
                case "badAboveBandPolicy" -> ((ObjectNode) nj.at("/retirementAdjustments/0/calculation")).put("aboveFinalBand", "UNLIMITED");
                case "invalidAgiEligibility" -> ((ObjectNode) shared.get("eligibility")).put("federalAgiMaximum", 75000);
                case "allocationAge" -> ((ObjectNode) co.at("/scenarioBoundaries/2")).put("minimumAge", 65);
                case "unconditionalBoundary" -> ((ObjectNode) mi.at("/scenarioBoundaries/0")).remove("minimumAge");
                default -> throw new AssertionError(mutation);
            }
        });
    }

    private void reject(Consumer<ObjectNode> mutation) throws Exception {
        try (var input = getClass().getResourceAsStream("/tax/state/state-tax-2026.json")) {
            ObjectNode root = (ObjectNode) mapper.readTree(input);
            mutation.accept(root);
            assertThrows(IOException.class, () -> repository.parse(new ByteArrayInputStream(mapper.writeValueAsBytes(root))));
        }
    }

    private static ObjectNode state(JsonNode root, int index) {
        return (ObjectNode) root.get("jurisdictions").get(index);
    }
}
