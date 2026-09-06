package com.daviddunn.retirementplanner.ui.summary;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionYearDetailsSocialSecurityTest {

    @Test
    void detailsPanePresentsStoredSocialSecurityAuditValues() throws Exception {

        String source = Files.readString(Path.of(
                "src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java"));

        assertTrue(source.contains("Primary Own Benefit / Candidate"));
        assertTrue(source.contains("Spouse Own Benefit / Candidate"));
        assertTrue(source.contains("Primary Survivor Candidate"));
        assertTrue(source.contains("Spouse Survivor Candidate"));
        assertTrue(source.contains("Primary Selected Benefit"));
        assertTrue(source.contains("Spouse Selected Benefit"));
        assertTrue(source.contains("Household Social Security Received"));
        assertTrue(source.contains("getSocialSecurityResult()"));
        assertFalse(source.contains(
                "HouseholdSocialSecurityIncomeCalculator"));
    }
}
