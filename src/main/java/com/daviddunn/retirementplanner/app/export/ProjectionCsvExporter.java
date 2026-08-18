package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProjectionCsvExporter {

    public void export(
            Projection projection,
            Path file)
            throws IOException {

        if (projection == null
                || projection.isEmpty()) {

            throw new IllegalArgumentException(
                    "Projection cannot be empty.");
        }

        try (BufferedWriter writer =
                     Files.newBufferedWriter(file)) {

            writeHeader(writer);

            for (ProjectionYear year :
                    projection.getYears()) {

                writeYear(
                        writer,
                        year);
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
                        + "Annual Expenses,"
                        + "Cash Flow Need,"
                        + "Portfolio Withdrawal,"
                        + "Required Minimum Distribution,"
                        + "Excess RMD,"
                        + "Tax Funding Withdrawal,"
                        + "Roth Conversion,"
                        + "Unallocated Cash,"
                        + "Ending Investable Assets,"
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
            ProjectionYear year)
            throws IOException {

        writeRow(
                writer,

                year.getProjectionYear(),
                year.getCalendarYear(),
                year.getPrimaryPersonAge(),

                year.getBeginningInvestableAssets(),
                year.getInvestmentGrowth(),
                year.getGuaranteedIncome(),
                year.getAnnualExpenses(),
                year.getCashFlowNeed(),
                year.getPortfolioWithdrawal(),
                year.getRequiredMinimumDistribution(),
                year.getExcessRmd(),
                year.getTaxFundingWithdrawal(),
                year.getRothConversion(),
                year.getUnallocatedCash(),
                year.getEndingInvestableAssets(),

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

        /*
         * CSV fields containing commas,
         * quotes, or newlines must be
         * surrounded by quotes.
         */
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