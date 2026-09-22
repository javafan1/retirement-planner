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
            PDRectangle.LETTER.getHeight();

    private static final float PAGE_HEIGHT =
            PDRectangle.LETTER.getWidth();

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

    private ProjectionPdfReport report;
    private PDDocument document;

    private PDPage page;

    private PDPageContentStream content;

    private float currentY;


    public void export(
            RetirementPlan plan,
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableProjections,
            ProjectionPdfReport report,
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

        this.report = java.util.Objects.requireNonNull(report);
        if (report.charts().years().isEmpty()) throw new IllegalArgumentException("Chart data is required.");
        document = new PDDocument();

        try {

            startPage();

            writeTitle(
                    plan,
                    projection);

            writeKeyResults(
                    projection,
                    nonInvestableProjections);

            writeBaselineComparison();
            finishPage();
            new ProjectionPdfCharts(report).appendTo(document);
            startPage();
            writeProjectionTable(projection, nonInvestableProjections);
            finishPage();
            startPage();
            writeKeyAssumptions(plan);
            finishPage();
            addPageNumbers();
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
                        new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));

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


    private void ensureSpace(float height) throws IOException {
        if (currentY < MARGIN + height) { finishPage(); startPage(); }
    }

    private void addPageNumbers() throws IOException { PdfReportSupport.pageNumbers(document); }

    private void writeBaselineComparison() throws IOException {
        var baseline = report.baselineComparison();
        if (baseline == null) return;
        writeSectionHeading("BASELINE COMPARISON - " + baseline.getCalendarYear(), BLUE);
        String[] titles = {"Investment Growth", "Total Income", "Total Taxes", "Peak Annual Tax",
                "Investable Assets", "Total Net Worth", "Investable After-Tax Estate"};
        BigDecimal[] old = {baseline.getBaselineInvestmentGrowth(), baseline.getBaselineTotalIncome(),
                baseline.getBaselineTotalTaxes(), baseline.getBaselinePeakAnnualTax(), baseline.getBaselineEndingInvestableAssets(),
                baseline.getBaselineNetWorth(), baseline.getBaselineAfterTaxEstate()};
        BigDecimal[] current = {baseline.getCurrentInvestmentGrowth(), baseline.getCurrentTotalIncome(),
                baseline.getCurrentTotalTaxes(), baseline.getCurrentPeakAnnualTax(), baseline.getCurrentEndingInvestableAssets(),
                baseline.getCurrentNetWorth(), baseline.getCurrentAfterTaxEstate()};
        BigDecimal[] changes = {baseline.getInvestmentGrowthChange(), baseline.getTotalIncomeChange(), baseline.getTotalTaxesChange(),
                baseline.getPeakAnnualTaxChange(), baseline.getEndingInvestableAssetsChange(), baseline.getNetWorthChange(), baseline.getAfterTaxEstateChange()};
        float[] widths = {240, 160, 160, 160};
        writeTableHeader(new String[]{"Metric", "Baseline", "Current", "Change"}, widths);
        for (int i = 0; i < titles.length; i++) writeTableRow(new String[]{titles[i], money(old[i]), money(current[i]), money(changes[i])}, widths);
    }
    // ============================================================
    // Title
    // ============================================================

    private void writeTitle(
            RetirementPlan plan,
            Projection projection)
            throws IOException {

        writeText(
                "RETIREMENT PLAN PROJECTION REPORT",
                MARGIN,
                currentY,
                FONT_BOLD,
                HEADER_FONT_SIZE,
                BLUE);

        currentY -= 25;
        writeText("Plan: " + report.planName(), MARGIN, currentY, FONT_NORMAL, 10, GRAY);
        currentY -= 16;

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
                "EXECUTIVE RESULTS SUMMARY",
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

        var endingPoint = report.charts().years().getLast();
        BigDecimal endingNonInvestable = endingPoint.values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.NON_INVESTABLE_ASSETS);
        BigDecimal netWorth = endingPoint.values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.TOTAL_NET_WORTH);
        float gap = 12;
        float cardWidth = (CONTENT_WIDTH - gap * 2) / 3;
        float cardHeight = 80;
        String[] titles = {"AVERAGE EFFECTIVE TAX RATE", "ENDING INVESTABLE ASSETS", "HOME EQUITY / OTHER ASSETS",
                "TOTAL NET WORTH", "AFTER-TAX ESTATE HEIR VALUE", "PEAK INVESTABLE ASSETS"};
        String[] values = {percent(report.averageEffectiveTaxRate()), compactMoney(last.getEndingInvestableAssets()),
                compactMoney(endingNonInvestable), compactMoney(netWorth), compactMoney(report.afterTaxEstateHeirValue()), compactMoney(peakAssets)};
        PDColor[] backgrounds = {LIGHT_ORANGE, LIGHT_BLUE, LIGHT_ORANGE, LIGHT_BLUE, LIGHT_PURPLE, LIGHT_GREEN};
        PDColor[] accents = {ORANGE, BLUE, ORANGE, BLUE, PURPLE, GREEN};
        for (int i = 0; i < titles.length; i++) writeMetricCard(MARGIN + (i % 3) * (cardWidth + gap),
                currentY - cardHeight - (i / 3) * (cardHeight + gap), cardWidth, cardHeight,
                titles[i], values[i], backgrounds[i], accents[i]);
        currentY -= cardHeight * 2 + gap + 20;
        writeKeyValue("Final-Year Effective Tax Rate", percent(last.getCombinedEffectiveTaxRate()), ORANGE);
        writeKeyValue("After-Tax Estate Heir Value", "Includes home equity / other assets; chart estate values are investable only.", GRAY);        currentY -= 8;
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

        if (death.getDeathScenario() != com.daviddunn.retirementplanner.domain.model.DeathScenario.BOTH_SURVIVE
                && death.getSurvivorClaimingAge() != null) {

            var choices = com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy.choices(
                    plan.getHousehold(), death.getDeathScenario(), death.getDeathYear());

            writeKeyValue(
                    "Survivor Benefit Claiming Age",
                    choices.immediateAtDeath() ? "Immediate at death (Age " + choices.ages().getFirst() + ")"
                            : death.getSurvivorClaimingAge().toString(),
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

        var tax = assumptions.getTaxAssumptions();
        writeKeyValue("Filing Status", tax.getFilingStatus().toString(), GRAY);
        writeKeyValue("Federal Bracket Growth", percent(tax.getFederalTaxBracketGrowthRate()), GRAY);
        writeKeyValue("Standard Deduction Growth", percent(tax.getStandardDeductionGrowthRate()), GRAY);
        writeKeyValue("State / Local Income Tax Rates", percent(tax.getStateIncomeTaxRate()) + " / " + percent(tax.getLocalIncomeTaxRate()), GRAY);
        writeKeyValue("Estimated Heir Tax Rate", percent(tax.getEstimatedHeirTaxRateOnTaxDeferredAssets()), GRAY);
        writeKeyValue("Future Federal Marginal Adjustment", percent(tax.getFutureFederalMarginalRateAdjustment())
                + (tax.getFutureFederalMarginalRateEffectiveYear() == null ? " / not scheduled" : " / effective " + tax.getFutureFederalMarginalRateEffectiveYear()), GRAY);
        if (roth != null) writeKeyValue("Roth Stop Rule", roth.getStopRule().toString(), GRAY);
        for (var person : java.util.stream.Stream.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse()).filter(java.util.Objects::nonNull).toList()) {
            writeKeyValue("Household Member", person.getFullName() + " / born " + person.getBirthDate(), GRAY);
            for (var income : person.getIncomeSources()) {
                if (income instanceof com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome ss) {
                    writeKeyValue(person.getFirstName() + " - Social Security Claiming Age", Integer.toString(ss.getClaimingAge()), GRAY);
                    writeKeyValue("Full Retirement Monthly Benefit", money(ss.getFullRetirementMonthlyBenefit()), GRAY);
                    writeKeyValue("Social Security Start / Benefit Valuation Year", ss.getStartDate() + " / " + ss.getBenefitValuationYear(), GRAY);
                }
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

        float tableWidth = 0;
        for (float width : widths) tableWidth += width;
        for (int i = 0; i < widths.length; i++) widths[i] *= CONTENT_WIDTH / tableWidth;
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
                        "RETIREMENT PLAN PROJECTION REPORT",
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

            var point = report.charts().years().stream().filter(p -> p.year() == year.getCalendarYear()).findFirst().orElseThrow();
            BigDecimal nonInvestable = point.values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.NON_INVESTABLE_ASSETS);
            BigDecimal netWorth = point.values().get(com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric.TOTAL_NET_WORTH);
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
                9,
                accent);

        writeText(
                value,
                x + 10,
                y + 17,
                FONT_BOLD,
                20,
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

        var labels = PdfReportSupport.wrap(label, 268, 9);
        var values = PdfReportSupport.wrap(value, CONTENT_WIDTH - 280, 9);
        int lines = Math.max(labels.size(), values.size());
        ensureSpace(lines * 12 + 6);
        for (int i = 0; i < labels.size(); i++) writeText(labels.get(i), MARGIN, currentY - 12 * i, FONT_NORMAL, 9, GRAY);
        for (int i = 0; i < values.size(); i++) writeText(values.get(i), MARGIN + 280, currentY - 12 * i, FONT_BOLD, 9, color);
        currentY -= lines * 12 + 4;
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

        return PdfReportSupport.safe(text)
                .replace("–", "-")
                .replace("—", "-")
                .replace("’", "'")
                .replace("“", "\"")
                .replace("”", "\"");
    }
}
