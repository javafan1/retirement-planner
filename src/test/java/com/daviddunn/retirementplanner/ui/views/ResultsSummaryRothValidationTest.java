package com.daviddunn.retirementplanner.ui.views;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsSummaryRothValidationTest {

    @Test
    void disabledRothConversionDoesNotParseBlankFields()
            throws Exception {

        String method = applyRothConversionMethod();

        int disabledCheck =
                method.indexOf("if (!enabled)");
        int clearRequest =
                method.indexOf(
                        "rothConversionHandler.accept(\n" +
                                "                        null)");
        int startYearParse =
                method.indexOf(
                        "Integer.parseInt(\n" +
                                "                            startYearText)");

        assertTrue(disabledCheck >= 0);
        assertTrue(clearRequest > disabledCheck);
        assertTrue(startYearParse > clearRequest);
    }

    @Test
    void expectedRothInputErrorsDoNotPrintStackTraces()
            throws Exception {

        String method = applyRothConversionMethod();

        int numberFormatCatch =
                method.indexOf(
                        "catch (NumberFormatException ex)");
        int validationCatch =
                method.indexOf(
                        "catch (IllegalArgumentException ex)");
        int unexpectedCatch =
                method.indexOf(
                        "catch (RuntimeException ex)");
        int stackTrace =
                method.indexOf("ex.printStackTrace()");

        assertTrue(numberFormatCatch >= 0);
        assertTrue(validationCatch > numberFormatCatch);
        assertTrue(unexpectedCatch > validationCatch);
        assertTrue(stackTrace > unexpectedCatch);
    }

    @Test
    void enabledRothConversionValidatesRequiredInputs()
            throws Exception {

        String method = applyRothConversionMethod();

        assertTrue(method.contains(
                "Roth conversion start year is required."));
        assertTrue(method.contains(
                "Roth conversion strategy is required."));
        assertTrue(method.contains(
                "Roth conversion frequency is required."));
        assertTrue(method.contains(
                "Roth conversion stop rule is required."));
    }

    private String applyRothConversionMethod()
            throws Exception {

        String source = Files.readString(Path.of(
                "src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java"));

        int start = source.indexOf(
                "private void applyRothConversion()");
        int end = source.indexOf(
                "public void setOnRothConversionApply",
                start);

        assertTrue(start >= 0);
        assertTrue(end > start);

        return source.substring(start, end);
    }
}
