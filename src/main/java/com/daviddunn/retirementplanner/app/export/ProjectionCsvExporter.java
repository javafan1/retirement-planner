package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProjectionCsvExporter {

    public void export(
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableProjections,
            Path file)
            throws IOException {

        if (projection == null
                || projection.isEmpty()) {

            throw new IllegalArgumentException(
                    "Projection cannot be empty.");
        }

        if (nonInvestableProjections == null) {
            nonInvestableProjections =
                    List.of();
        }

        try (BufferedWriter writer =
                     Files.newBufferedWriter(file)) {

            writeHeader(writer);

            for (ProjectionYear year :
                    projection.getYears()) {

                writeYear(
                        writer,
                        year,
                        nonInvestableProjections);
            }
        }
    }


    private void writeHeader(
            BufferedWriter writer)
            throws IOException {

        writer.write(
                "Projection Year,"
                        + "Calendar Year,"
                        + "Primary Age,"
                        + "Beginning Investable Assets,"
                        + "Investment Growth,"
                        + "Guaranteed Income,"
                        + "Primary Own Social Security,"
                        + "Spouse Own Social Security,"
                        + "Primary Survivor Candidate,"
                        + "Spouse Survivor Candidate,"
                        + "Primary Selected Social Security Benefit,"
                        + "Spouse Selected Social Security Benefit,"
                        + "Household Social Security,"
                        + "Annual Expenses,"
                        + "Cash Flow Need,"
                        + "Portfolio Withdrawal,"
                        + "Required Minimum Distribution,"
                        + "RMD Distributed Before Projection,"
                        + "RMD Distributed In Projection,"
                        + "Excess RMD,"
                        + "Beginning Retained Non-Qualified Assets,"
                        + "Retained Non-Qualified Asset Growth,"
                        + "Retained Household Surplus,"
                        + "Retained from Excess RMD,"
                        + "Retained from Guaranteed Income,"
                        + "Tax Funding Withdrawal,"
                        + "Requested Roth Conversion,"
                        + "Primary Roth Conversion,"
                        + "Spouse Roth Conversion,"
                        + "Roth Conversion,"
                        + "Roth Conversion Shortfall,"
                        + "Ending Retained Non-Qualified Assets,"
                        + "Ending Investable Assets,"
                        + "Non-Investable Assets,"
                        + "Net Worth,"
                        + "Adjusted Gross Income,"
                        + "Taxable Social Security,"
                        + "Federal Taxable Income,"
                        + "Federal Standard Deduction,"
                        + "Federal Income Tax,"
                        + "Michigan Retirement Income,"
                        + "Michigan Retirement Deduction,"
                        + "Michigan Taxable Income,"
                        + "Michigan Income Tax,"
                        + "Total Income Tax,"
                        + "Combined Effective Tax Rate,"
                        + "Annual Medicare Premium,"
                        + "Monthly Part B Premium,"
                        + "Monthly Part D Premium,"
                        + "IRMAA Bracket,"
                        + "Estimated Heir Tax,"
                        + "After-Tax Estate Value");

        writer.newLine();
    }


    private void writeYear(
            BufferedWriter writer,
            ProjectionYear year,
            List<NonInvestableAssetProjection>
                    nonInvestableProjections)
            throws IOException {

        BigDecimal nonInvestableValue =
                getNonInvestableAssetValue(
                        year.getCalendarYear(),
                        nonInvestableProjections);

        BigDecimal netWorth =
                year.getEndingInvestableAssets()
                        .add(nonInvestableValue);

        writeRow(
                writer,

                year.getProjectionYear(),
                year.getCalendarYear(),
                year.getPrimaryPersonAge(),

                year.getBeginningInvestableAssets(),
                year.getInvestmentGrowth(),
                year.getGuaranteedIncome(),
                year.getSocialSecurityResult()
                        .primaryOwnBenefit(),
                year.getSocialSecurityResult()
                        .spouseOwnBenefit(),
                year.getSocialSecurityResult()
                        .primarySurvivorCandidate(),
                year.getSocialSecurityResult()
                        .spouseSurvivorCandidate(),
                year.getSocialSecurityResult()
                        .primarySelection()
                        .getDisplayName(),
                year.getSocialSecurityResult()
                        .spouseSelection()
                        .getDisplayName(),
                year.getSocialSecurityResult()
                        .householdBenefit(),
                year.getAnnualExpenses(),
                year.getCashFlowNeed(),
                year.getPortfolioWithdrawal(),
                year.getRequiredMinimumDistribution(),
                year.getRmdDistributedBeforeProjection(),
                year.getRmdDistributedInProjection(),
                year.getExcessRmd(),
                year.getBeginningRetainedNonQualifiedAssets(),
                year.getRetainedNonQualifiedAssetGrowth(),
                year.getRetainedHouseholdSurplus(),
                year.getRetainedFromExcessRmd(),
                year.getRetainedFromGuaranteedIncome(),
                year.getTaxFundingWithdrawal(),
                year.getRequestedRothConversion(),
                year.getPrimaryRothConversion(),
                year.getSpouseRothConversion(),
                year.getRothConversion(),
                year.getRothConversionShortfall(),
                year.getEndingRetainedNonQualifiedAssets(),
                year.getEndingInvestableAssets(),

                nonInvestableValue,
                netWorth,

                year.getAdjustedGrossIncome(),
                year.getTaxableSocialSecurity(),
                year.getFederalTaxableIncome(),
                year.getFederalStandardDeduction(),
                year.getFederalIncomeTax(),

                year.getMichiganRetirementIncome(),
                year.getMichiganRetirementDeduction(),
                year.getMichiganTaxableIncome(),
                year.getMichiganIncomeTax(),
                year.getTotalIncomeTax(),
                year.getCombinedEffectiveTaxRate(),

                year.getAnnualMedicarePremium(),
                year.getMonthlyPartBPremium(),
                year.getMonthlyPartDPremium(),
                year.getIrmaaBracketDisplay(),

                year.getEstimatedHeirTax(),
                year.getAfterTaxEstateValue());
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
                .map(NonInvestableAssetProjection::
                        getTotalValue)
                .orElse(BigDecimal.ZERO);
    }


    private void writeRow(
            BufferedWriter writer,
            Object... values)
            throws IOException {

        for (int i = 0;
             i < values.length;
             i++) {

            if (i > 0) {
                writer.write(",");
            }

            writer.write(
                    escape(
                            values[i]));
        }

        writer.newLine();
    }


    private String escape(
            Object value) {

        if (value == null) {
            return "";
        }

        String text =
                value.toString();

        if (text.contains(",")
                || text.contains("\"")
                || text.contains("\n")
                || text.contains("\r")) {

            return "\""
                    + text.replace(
                    "\"",
                    "\"\"")
                    + "\"";
        }

        return text;
    }
}
