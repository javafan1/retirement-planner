package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

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

    private static final float MARGIN = 36;

    private static final float CONTENT_WIDTH =
            PAGE_WIDTH - (MARGIN * 2);

    private static final float ROW_HEIGHT = 18;

    private static final float FONT_SIZE = 8;

    private static final float HEADER_FONT_SIZE = 20;

    private static final float SECTION_FONT_SIZE = 12;

    /*
     * Colors chosen to resemble the Results Summary UI.
     */
    private static final PDColor BLUE =
            new PDColor(
                    new float[]{0.18f, 0.39f, 0.67f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor LIGHT_BLUE =
            new PDColor(
                    new float[]{0.91f, 0.95f, 0.98f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor PURPLE =
            new PDColor(
                    new float[]{0.42f, 0.29f, 0.60f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor LIGHT_PURPLE =
            new PDColor(
                    new float[]{0.95f, 0.92f, 0.98f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor ORANGE =
            new PDColor(
                    new float[]{0.90f, 0.52f, 0.12f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor LIGHT_ORANGE =
            new PDColor(
                    new float[]{0.99f, 0.95f, 0.88f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor GREEN =
            new PDColor(
                    new float[]{0.25f, 0.55f, 0.35f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor LIGHT_GREEN =
            new PDColor(
                    new float[]{0.91f, 0.97f, 0.92f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor GRAY =
            new PDColor(
                    new float[]{0.45f, 0.45f, 0.45f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor LIGHT_GRAY =
            new PDColor(
                    new float[]{0.94f, 0.94f, 0.94f},
                    PDDeviceRGB.INSTANCE);

    private static final PDColor WHITE =
            new PDColor(
                    new float[]{1f, 1f, 1f},
                    PDDeviceRGB.INSTANCE);

    private static final PDType1Font FONT_NORMAL =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

    private static final PDType1Font FONT_BOLD =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD);

    private PDDocument document;

    private PDPage page;

    private PDPageContentStream content;

    private float currentY;


    public void export(
            RetirementPlan plan,
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableProjections,
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

        if (nonInvestableProjections == null) {
            nonInvestableProjections =
                    List.of();
        }

        document =
                new PDDocument();

        try {

            startPage();

            writeTitle(
                    plan,
                    projection);

            writeKeyResults(
                    projection,
                    nonInvestableProjections);

            writeKeyAssumptions(
                    plan);

            writeProjectionTable(
                    projection,
                    nonInvestableProjections);

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


    // ============================================================
    // Title
    // ============================================================

    private void writeTitle(
            RetirementPlan plan,
            Projection projection)
            throws IOException {

        writeText(
                "RETIREMENT PROJECTION",
                MARGIN,
                currentY,
                FONT_BOLD,
                HEADER_FONT_SIZE,
                BLUE);

        currentY -= 25;

        String period =
                projection.getYears()
                        .get(0)
                        .getCalendarYear()
                        + " - "
                        + projection.getYears()
                        .get(
                                projection.getYears().size() - 1)
                        .getCalendarYear();

        writeText(
                "Projection Period: "
                        + period,
                MARGIN,
                currentY,
                FONT_NORMAL,
                10,
                GRAY);

        currentY -= 14;

        writeText(
                "Generated: "
                        + LocalDate.now(),
                MARGIN,
                currentY,
                FONT_NORMAL,
                9,
                GRAY);

        currentY -= 24;

        drawHorizontalRule(
                BLUE);
    }


    // ============================================================
    // Key Results
    // ============================================================

    private void writeKeyResults(
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableProjections)
            throws IOException {

        currentY -= 16;

        writeSectionHeading(
                "KEY RESULTS",
                BLUE);

        ProjectionYear last =
                projection.getYears()
                        .get(
                                projection.getYears().size() - 1);

        BigDecimal peakAssets =
                projection.getYears()
                        .stream()
                        .map(
                                ProjectionYear::
                                        getEndingInvestableAssets)
                        .max(
                                BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

        BigDecimal endingNonInvestable =
                getNonInvestableAssetValue(
                        last.getCalendarYear(),
                        nonInvestableProjections);

        BigDecimal netWorth =
                last.getEndingInvestableAssets()
                        .add(endingNonInvestable);

        float gap = 8;

        float cardWidth =
                (CONTENT_WIDTH - gap * 4) / 5;

        float cardHeight = 58;

        writeMetricCard(
                MARGIN,
                currentY - cardHeight,
                cardWidth,
                cardHeight,
                "ENDING INVESTABLE ASSETS",
                compactMoney(
                        last.getEndingInvestableAssets()),
                LIGHT_BLUE,
                BLUE);

        writeMetricCard(
                MARGIN + (cardWidth + gap),
                currentY - cardHeight,
                cardWidth,
                cardHeight,
                "PEAK INVESTABLE ASSETS",
                compactMoney(peakAssets),
                LIGHT_GREEN,
                GREEN);

        writeMetricCard(
                MARGIN + (cardWidth + gap) * 2,
                currentY - cardHeight,
                cardWidth,
                cardHeight,
                "NON-INVESTABLE ASSETS",
                compactMoney(endingNonInvestable),
                LIGHT_ORANGE,
                ORANGE);

        writeMetricCard(
                MARGIN + (cardWidth + gap) * 3,
                currentY - cardHeight,
                cardWidth,
                cardHeight,
                "TOTAL NET WORTH",
                compactMoney(netWorth),
                LIGHT_BLUE,
                BLUE);

        writeMetricCard(
                MARGIN + (cardWidth + gap) * 4,
                currentY - cardHeight,
                cardWidth,
                cardHeight,
                "AFTER-TAX ESTATE",
                compactMoney(
                        last.getAfterTaxEstateValue()),
                LIGHT_PURPLE,
                PURPLE);

        currentY -= cardHeight + 18;

        writeKeyValue(
                "Effective Tax Rate",
                percent(
                        last.getCombinedEffectiveTaxRate()),
                ORANGE);

        currentY -= 8;
    }


    // ============================================================
    // Key Assumptions
    // ============================================================

    private void writeKeyAssumptions(
            RetirementPlan plan)
            throws IOException {

        writeSectionHeading(
                "KEY ASSUMPTIONS",
                BLUE);

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        writeKeyValue(
                "Investment Return",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getExpectedAnnualInvestmentReturn()),
                GRAY);

        writeKeyValue(
                "General Inflation",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getGeneralInflationRate()),
                GRAY);

        writeKeyValue(
                "Healthcare Inflation",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getHealthcareInflationRate()),
                GRAY);

        writeKeyValue(
                "Social Security COLA",
                percent(
                        assumptions
                                .getEconomicAssumptions()
                                .getSocialSecurityColaRate()),
                GRAY);

        DeathScenarioAssumptions death =
                assumptions
                        .getDeathScenarioAssumptions();

        writeKeyValue(
                "Death Scenario",
                death.getDeathScenario()
                        .toString(),
                GRAY);

        if (death.getDeathYear() != null) {

            writeKeyValue(
                    "Death Year",
                    death.getDeathYear()
                            .toString(),
                    GRAY);
        }

        if (death.getSurvivorClaimingAge() != null) {

            writeKeyValue(
                    "Survivor Claiming Age",
                    death.getSurvivorClaimingAge()
                            .toString(),
                    GRAY);
        }

        RothConversionRequest roth =
                plan.getRothConversionRequest();

        if (roth == null) {

            writeKeyValue(
                    "Roth Conversion",
                    "Not configured",
                    GRAY);

        } else {

            writeKeyValue(
                    "Roth Conversion",
                    roth.isEnabled()
                            ? "Enabled"
                            : "Disabled",
                    GRAY);

            if (roth.isEnabled()) {

                writeKeyValue(
                        "Roth Start Year",
                        Integer.toString(
                                roth.getStartYear()),
                        GRAY);

                writeKeyValue(
                        "Roth Annual Amount",
                        money(
                                roth.getAnnualAmount()),
                        GRAY);

                writeKeyValue(
                        "Roth Frequency",
                        roth.getFrequency()
                                .toString(),
                        GRAY);

                writeKeyValue(
                        "Roth Strategy",
                        roth.getStrategy()
                                .toString(),
                        GRAY);
            }
        }

        currentY -= 10;
    }


    // ============================================================
    // Projection Table
    // ============================================================

    private void writeProjectionTable(
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableProjections)
            throws IOException {

        writeSectionHeading(
                "PROJECTION SUMMARY",
                BLUE);

        /*
         * Keep the table compact enough to fit
         * comfortably on a portrait page.
         */
        float[] widths = {
                40,     // Year
                34,     // Age
                66,     // Beginning
                58,     // Growth
                58,     // Income
                58,     // Expenses
                58,     // Tax
                66,     // Ending
                66,     // Non-Investable
                66      // Net Worth
        };

        String[] headers = {
                "Year",
                "Age",
                "Beginning",
                "Growth",
                "Income",
                "Expenses",
                "Tax",
                "Ending",
                "Non-Invest.",
                "Net Worth"
        };

        writeTableHeader(
                headers,
                widths);

        for (ProjectionYear year :
                projection.getYears()) {

            if (currentY <
                    MARGIN + ROW_HEIGHT * 2) {

                finishPage();

                startPage();

                writeText(
                        "RETIREMENT PROJECTION",
                        MARGIN,
                        currentY,
                        FONT_BOLD,
                        14,
                        BLUE);

                currentY -= 22;

                writeTableHeader(
                        headers,
                        widths);
            }

            BigDecimal nonInvestable =
                    getNonInvestableAssetValue(
                            year.getCalendarYear(),
                            nonInvestableProjections);

            BigDecimal netWorth =
                    year.getEndingInvestableAssets()
                            .add(nonInvestable);

            String[] values = {

                    Integer.toString(
                            year.getCalendarYear()),

                    Integer.toString(
                            year.getPrimaryPersonAge()),

                    compactMoney(
                            year.getBeginningInvestableAssets()),

                    compactMoney(
                            year.getInvestmentGrowth()),

                    compactMoney(
                            year.getGuaranteedIncome()),

                    compactMoney(
                            year.getAnnualExpenses()),

                    compactMoney(
                            year.getTotalIncomeTax()),

                    compactMoney(
                            year.getEndingInvestableAssets()),

                    compactMoney(
                            nonInvestable),

                    compactMoney(
                            netWorth)
            };

            writeTableRow(
                    values,
                    widths);
        }
    }


    // ============================================================
    // Table Helpers
    // ============================================================

    private void writeTableHeader(
            String[] headers,
            float[] widths)
            throws IOException {

        float totalWidth = 0;

        for (float width : widths) {
            totalWidth += width;
        }

        float x = MARGIN;

        drawFilledRectangle(
                MARGIN,
                currentY - 14,
                totalWidth,
                20,
                BLUE);

        for (int i = 0;
             i < headers.length;
             i++) {

            writeText(
                    headers[i],
                    x + 3,
                    currentY - 8,
                    FONT_BOLD,
                    FONT_SIZE,
                    WHITE);

            x += widths[i];
        }

        currentY -= 22;
    }


    private void writeTableRow(
            String[] values,
            float[] widths)
            throws IOException {

        float totalWidth = 0;

        for (float width : widths) {
            totalWidth += width;
        }

        /*
         * Alternating row shading.
         */
        boolean shaded =
                ((int)
                        ((PAGE_HEIGHT - currentY)
                                / ROW_HEIGHT))
                        % 2 == 0;

        if (shaded) {

            drawFilledRectangle(
                    MARGIN,
                    currentY - 13,
                    totalWidth,
                    ROW_HEIGHT,
                    LIGHT_GRAY);
        }

        float x = MARGIN;

        for (int i = 0;
             i < values.length;
             i++) {

            writeText(
                    values[i],
                    x + 3,
                    currentY - 9,
                    FONT_NORMAL,
                    FONT_SIZE,
                    GRAY);

            x += widths[i];
        }

        currentY -= ROW_HEIGHT;
    }


    // ============================================================
    // Metric Cards
    // ============================================================

    private void writeMetricCard(
            float x,
            float y,
            float width,
            float height,
            String title,
            String value,
            PDColor background,
            PDColor accent)
            throws IOException {

        drawFilledRectangle(
                x,
                y,
                width,
                height,
                background);

        /*
         * Accent bar on left side.
         */
        drawFilledRectangle(
                x,
                y,
                4,
                height,
                accent);

        writeText(
                title,
                x + 10,
                y + height - 17,
                FONT_BOLD,
                6.5f,
                accent);

        writeText(
                value,
                x + 10,
                y + 17,
                FONT_BOLD,
                13,
                accent);
    }


    // ============================================================
    // General Helpers
    // ============================================================

    private void writeSectionHeading(
            String text,
            PDColor color)
            throws IOException {

        currentY -= 4;

        writeText(
                text,
                MARGIN,
                currentY,
                FONT_BOLD,
                SECTION_FONT_SIZE,
                color);

        currentY -= 5;

        drawHorizontalRule(
                color);

        currentY -= 12;
    }


    private void writeKeyValue(
            String label,
            String value,
            PDColor color)
            throws IOException {

        writeText(
                label,
                MARGIN,
                currentY,
                FONT_NORMAL,
                9,
                GRAY);

        writeText(
                value,
                MARGIN + 180,
                currentY,
                FONT_BOLD,
                9,
                color);

        currentY -= 14;
    }


    private void writeText(
            String text,
            float x,
            float y,
            PDType1Font font,
            float fontSize,
            PDColor color)
            throws IOException {

        content.beginText();

        content.setFont(
                font,
                fontSize);

        content.setNonStrokingColor(
                color);

        content.newLineAtOffset(
                x,
                y);

        content.showText(
                sanitize(text));

        content.endText();
    }


    private void drawFilledRectangle(
            float x,
            float y,
            float width,
            float height,
            PDColor color)
            throws IOException {

        content.setNonStrokingColor(
                color);

        content.addRect(
                x,
                y,
                width,
                height);

        content.fill();
    }


    private void drawHorizontalRule(
            PDColor color)
            throws IOException {

        content.setStrokingColor(
                color);

        content.setLineWidth(
                1.2f);

        content.moveTo(
                MARGIN,
                currentY);

        content.lineTo(
                PAGE_WIDTH - MARGIN,
                currentY);

        content.stroke();
    }


    private BigDecimal getNonInvestableAssetValue(
            int calendarYear,
            List<NonInvestableAssetProjection>
                    projections) {

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(
                        NonInvestableAssetProjection::
                                getTotalValue)
                .orElse(
                        BigDecimal.ZERO);
    }


    private String compactMoney(
            BigDecimal value) {

        if (value == null) {
            return "0";
        }

        BigDecimal absolute =
                value.abs();

        if (absolute.compareTo(
                new BigDecimal("1000000")) >= 0) {

            return value
                    .divide(
                            new BigDecimal("1000000"),
                            1,
                            java.math.RoundingMode.HALF_UP)
                    .toPlainString()
                    + "m";
        }

        if (absolute.compareTo(
                new BigDecimal("1000")) >= 0) {

            return value
                    .divide(
                            new BigDecimal("1000"),
                            0,
                            java.math.RoundingMode.HALF_UP)
                    .toPlainString()
                    + "K";
        }

        return value
                .setScale(
                        0,
                        java.math.RoundingMode.HALF_UP)
                .toPlainString();
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

        return text
                .replace("–", "-")
                .replace("—", "-")
                .replace("’", "'")
                .replace("“", "\"")
                .replace("”", "\"");
    }
}