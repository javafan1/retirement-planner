package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class ProjectionPdfExporter {

    private static final float PAGE_WIDTH =
            PDRectangle.LETTER.getWidth();

    private static final float PAGE_HEIGHT =
            PDRectangle.LETTER.getHeight();

    private static final float MARGIN = 40;

    private static final float CONTENT_WIDTH =
            PAGE_WIDTH - (MARGIN * 2);

    private static final float ROW_HEIGHT = 18;

    private static final float FONT_SIZE = 8;

    private static final float HEADER_FONT_SIZE = 18;

    private static final float SECTION_FONT_SIZE = 12;

    private PDDocument document;

    private PDPage page;

    private PDPageContentStream content;

    private float currentY;

    private static final PDType1Font FONT_NORMAL =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

    private static final PDType1Font FONT_BOLD =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD);


    public void export(
            RetirementPlan plan,
            Projection projection,
            Path file)
            throws IOException {

        if (plan == null) {
            throw new IllegalArgumentException(
                    "Retirement plan is required.");
        }

        if (projection == null
                || projection.isEmpty()) {

            throw new IllegalArgumentException(
                    "Projection cannot be empty.");
        }

        document =
                new PDDocument();

        try {

            startPage();

            writeTitle(
                    plan,
                    projection);

            writeKeyResults(
                    projection);

            writeKeyAssumptions(
                    plan);

            writeProjectionTable(
                    projection);

            finishPage();

            document.save(
                    file.toFile());

        } finally {

            document.close();
        }
    }


    private void startPage()
            throws IOException {

        page =
                new PDPage(
                        PDRectangle.LETTER);

        document.addPage(page);

        content =
                new PDPageContentStream(
                        document,
                        page);

        currentY =
                PAGE_HEIGHT - MARGIN;
    }


    private void finishPage()
            throws IOException {

        if (content != null) {
            content.close();
            content = null;
        }
    }


    private void writeTitle(
            RetirementPlan plan,
            Projection projection)
            throws IOException {

        writeText(
                "RETIREMENT PROJECTION REPORT",
                MARGIN,
                currentY,
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                HEADER_FONT_SIZE);

        currentY -= 24;

        String period =
                projection.getYears()
                        .get(0)
                        .getCalendarYear()
                        + " - "
                        + projection.getYears()
                        .get(projection.getYears().size() - 1)
                        .getCalendarYear();

        writeText(
                "Projection Period: "
                        + period,
                MARGIN,
                currentY,
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                10);

        currentY -= 14;

        writeText(
                "Generated: "
                        + LocalDate.now(),
                MARGIN,
                currentY,
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                9);

        currentY -= 28;
    }


    private void writeKeyResults(
            Projection projection)
            throws IOException {

        writeSectionHeading(
                "KEY RESULTS");

        ProjectionYear first =
                projection.getYears().get(0);

        ProjectionYear last =
                projection.getYears()
                        .get(projection.getYears().size() - 1);

        BigDecimal peakAssets =
                projection.getYears()
                        .stream()
                        .map(
                                ProjectionYear
                                        ::getEndingInvestableAssets)
                        .max(
                                BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

        writeKeyValue(
                "Ending Investable Assets",
                money(
                        last.getEndingInvestableAssets()));

        writeKeyValue(
                "Peak Investable Assets",
                money(peakAssets));

        writeKeyValue(
                "After-Tax Estate",
                money(
                        last.getAfterTaxEstateValue()));

        writeKeyValue(
                "Effective Tax Rate",
                percent(
                        last.getCombinedEffectiveTaxRate()));

        currentY -= 12;
    }


    private void writeKeyAssumptions(
            RetirementPlan plan)
            throws IOException {

        writeSectionHeading(
                "KEY ASSUMPTIONS");

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        writeKeyValue(
                "Investment Return",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getExpectedAnnualInvestmentReturn()));

        writeKeyValue(
                "General Inflation",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getGeneralInflationRate()));

        writeKeyValue(
                "Healthcare Inflation",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getHealthcareInflationRate()));

        writeKeyValue(
                "Social Security COLA",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getSocialSecurityColaRate()));

        DeathScenarioAssumptions death =
                assumptions
                        .getDeathScenarioAssumptions();

        writeKeyValue(
                "Death Scenario",
                death
                        .getDeathScenario()
                        .toString());

        if (death.getDeathYear() != null) {

            writeKeyValue(
                    "Death Year",
                    death.getDeathYear()
                            .toString());
        }

        if (death.getSurvivorClaimingAge() != null) {

            writeKeyValue(
                    "Survivor Claiming Age",
                    death.getSurvivorClaimingAge()
                            .toString());
        }

        RothConversionRequest roth =
                plan.getRothConversionRequest();

        if (roth == null) {

            writeKeyValue(
                    "Roth Conversion",
                    "Not configured");

        } else {

            writeKeyValue(
                    "Roth Conversion",
                    roth.isEnabled()
                            ? "Enabled"
                            : "Disabled");

            if (roth.isEnabled()) {

                writeKeyValue(
                        "Roth Start Year",
                        Integer.toString(
                                roth.getStartYear()));

                writeKeyValue(
                        "Roth Annual Amount",
                        money(
                                roth.getAnnualAmount()));

                writeKeyValue(
                        "Roth Frequency",
                        roth.getFrequency()
                                .toString());

                writeKeyValue(
                        "Roth Strategy",
                        roth.getStrategy()
                                .toString());
            }
        }

        currentY -= 12;
    }


    private void writeProjectionTable(
            Projection projection)
            throws IOException {

        writeSectionHeading(
                "PROJECTION SUMMARY");

        /*
         * The projection contains more columns than
         * can comfortably fit on a portrait page.
         *
         * The first PDF version therefore focuses
         * on the most useful high-level results.
         */
        float[] widths = {
                42, 35, 62, 62, 58, 58, 62, 65
        };

        String[] headers = {
                "Year",
                "Age",
                "Beginning",
                "Growth",
                "Income",
                "Expenses",
                "Tax",
                "Ending"
        };

        writeTableHeader(
                headers,
                widths);

        for (ProjectionYear year :
                projection.getYears()) {

            if (currentY < MARGIN + ROW_HEIGHT * 2) {

                finishPage();
                startPage();

                writeText(
                        "RETIREMENT PROJECTION REPORT",
                        MARGIN,
                        currentY,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                        12);

                currentY -= 20;

                writeTableHeader(
                        headers,
                        widths);
            }

            String[] values = {

                    Integer.toString(
                            year.getCalendarYear()),

                    Integer.toString(
                            year.getPrimaryPersonAge()),

                    money(
                            year.getBeginningInvestableAssets()),

                    money(
                            year.getInvestmentGrowth()),

                    money(
                            year.getGuaranteedIncome()),

                    money(
                            year.getAnnualExpenses()),

                    money(
                            year.getTotalIncomeTax()),

                    money(
                            year.getEndingInvestableAssets())
            };

            writeTableRow(
                    values,
                    widths);
        }
    }


    private void writeTableHeader(
            String[] headers,
            float[] widths)
            throws IOException {

        float x = MARGIN;

        for (int i = 0;
             i < headers.length;
             i++) {

            writeText(
                    headers[i],
                    x,
                    currentY,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                    FONT_SIZE);

            x += widths[i];
        }

        currentY -= ROW_HEIGHT;
    }


    private void writeTableRow(
            String[] values,
            float[] widths)
            throws IOException {

        float x = MARGIN;

        for (int i = 0;
             i < values.length;
             i++) {

            writeText(
                    values[i],
                    x,
                    currentY,
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    FONT_SIZE);

            x += widths[i];
        }

        currentY -= ROW_HEIGHT;
    }


    private void writeSectionHeading(
            String text)
            throws IOException {

        currentY -= 4;

        writeText(
                text,
                MARGIN,
                currentY,
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                SECTION_FONT_SIZE);

        currentY -= 18;
    }


    private void writeKeyValue(
            String label,
            String value)
            throws IOException {

        writeText(
                label + ": " + value,
                MARGIN,
                currentY,
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                9);

        currentY -= 14;
    }


    private void writeText(
            String text,
            float x,
            float y,
            PDType1Font font,
            float fontSize)
            throws IOException {

        content.beginText();

        content.setFont(
                font,
                fontSize);

        content.newLineAtOffset(
                x,
                y);

        content.showText(
                sanitize(text));

        content.endText();
    }


    private String money(
            BigDecimal value) {

        if (value == null) {
            return "$0.00";
        }

        return String.format(
                "$%,.2f",
                value.doubleValue());
    }


    private String percent(
            BigDecimal value) {

        if (value == null) {
            return "0.00%";
        }

        return String.format(
                "%.2f%%",
                value.doubleValue() * 100);
    }


    private String sanitize(
            String text) {

        /*
         * PDFBox Standard 14 fonts do not support
         * arbitrary Unicode characters.
         */
        return text
                .replace("–", "-")
                .replace("—", "-")
                .replace("’", "'")
                .replace("“", "\"")
                .replace("”", "\"");
    }
}