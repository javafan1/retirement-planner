package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Detached export facts captured from an Arkansas execution or from its restored annual history. */
public record ArkansasValidationReport(List<Year> years) {
    public record Year(int year, ArkansasValidationTax state, BigDecimal federalTax, BigDecimal funding,
                       BigDecimal conversion, BigDecimal surplus, BigDecimal retained, BigDecimal assets, BigDecimal estate) { }

    public ArkansasValidationReport {
        years = List.copyOf(years);
        if (years.isEmpty()) throw new IllegalArgumentException("Arkansas validation export requires annual results.");
    }

    public static ArkansasValidationReport capture(Projection projection) {
        return new ArkansasValidationReport(projection.getYears().stream().map(y -> new Year(y.getCalendarYear(),
                y.arkansasValidationTax().orElseThrow(() -> new IllegalArgumentException("Expected Arkansas validation years.")),
                y.getFederalIncomeTax(), y.getTaxFundingWithdrawal(), y.getRothConversion(),
                y.getRetainedFromTaxFundingSurplus(), y.getEndingRetainedNonQualifiedAssets(),
                y.getEndingInvestableAssets(), y.getAfterTaxEstateValue())).toList());
    }

    public void writeCsv(Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("Year,Result scope,Filing status,Federal income tax,Arkansas income tax,Gross tax funding,Taxable funding income,"
                    + "Executed Roth conversion,Retained from tax-funding surplus,Retained assets,Ending assets,After-tax estate,Versions,Planning limitations,"
                    + "Jurisdiction,Report schema,Arkansas retirement exclusion,Senior elections");
            writer.newLine();
            for (var y : years) {
                writer.write(String.join(",", Integer.toString(y.year()), scope(y), y.state().filingStatus().name(),
                        y.federalTax().toPlainString(), y.state().calculation().incomeTax().toPlainString(), y.funding().toPlainString(),
                        y.state().taxableFundingIncome().toPlainString(), y.conversion().toPlainString(), y.surplus().toPlainString(),
                        y.retained().toPlainString(), y.assets().toPlainString(), y.estate().toPlainString(),
                        csv(versions(y)), csv(String.join(" ", y.state().calculation().limitations())), "AR", "1",
                        y.state().provenance().election().retirementExclusion().toPlainString(), csv(elections(y))));
                writer.newLine();
            }
        }
    }

    public void writePdf(Path path) throws IOException {
        try (var document = new PDDocument()) {
            for (var y : years) {
                var lines = new ArrayList<>(List.of(
                        scope(y) + " - " + y.year() + " " + y.state().filingStatus(),
                        "Federal income tax: " + money(y.federalTax()),
                        "Arkansas income tax: " + money(y.state().calculation().incomeTax()),
                        "Gross tax funding: " + money(y.funding()),
                        "Taxable funding income: " + money(y.state().taxableFundingIncome()),
                        "Executed Roth conversion: " + money(y.conversion()),
                        "Arkansas retirement exclusion: " + money(y.state().provenance().election().retirementExclusion()),
                        "Senior elections: " + elections(y),
                        "Retained nonqualified assets: " + money(y.retained()),
                        "Ending investment assets: " + money(y.assets()),
                        "After-tax estate: " + money(y.estate())));
                if (y.surplus().signum() > 0) {
                    lines.add("Retained from tax-funding surplus: " + money(y.surplus()));
                }
                lines.add(versions(y));
                lines.addAll(y.state().calculation().limitations());
                var wrapped = new ArrayList<String>();
                for (var line : lines) wrapped.addAll(PdfReportSupport.wrap(line, 520, 10));
                for (int start = 0; start < wrapped.size(); start += 47) {
                    var page = new PDPage();
                    document.addPage(page);
                    try (var content = new PDPageContentStream(document, page)) {
                        float top = 738;
                        for (var line : wrapped.subList(start, Math.min(start + 47, wrapped.size()))) {
                            PdfReportSupport.text(content, line, 46, top, 10, false, PdfReportSupport.INK);
                            top -= 14;
                        }
                    }
                }
            }
            PdfReportSupport.pageNumbers(document);
            document.save(path.toFile());
        }
    }

    private static String scope(Year year) {
        return year.state().restoredHistory() ? "Arkansas retained history" : "Arkansas validation only";
    }

    private static String elections(Year year) {
        return year.state().provenance().election().recipients().stream().filter(r -> r.senior())
                .map(r -> r.owner().name() + (r.claimRetirementPool() ? " retirement pool" : " 65 Special"))
                .reduce((a, b) -> a + "; " + b).orElse("None");
    }

    private static String versions(Year year) {
        var versions = new TreeMap<>(year.state().versions());
        versions.put("government-rules", year.state().provenance().governmentRulesVersion());
        return versions.toString();
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private static String money(BigDecimal value) {
        return "$" + value.setScale(Math.max(2, value.stripTrailingZeros().scale())).toPlainString();
    }
}
