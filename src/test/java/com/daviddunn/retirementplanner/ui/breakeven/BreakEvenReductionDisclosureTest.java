package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.app.export.BreakEvenPdfExporter;
import com.daviddunn.retirementplanner.app.export.BreakEvenPdfReport;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.socialsecurity.SocialSecurityBenefitReductionPolicy;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BreakEvenReductionDisclosureTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @BeforeAll
    static void startFx() throws Exception {
        BreakEvenCompactLayoutTest.startFx();
    }

    @Test
    void currentAndBaselineDisclosuresStayIndependentInUiAndPdfWithoutChangingResults() throws Exception {
        var years = List.of(
                ProjectionYearBuilder.aProjectionYear().withCalendarYear(2032)
                        .withEndingInvestableAssets(100000).build(),
                ProjectionYearBuilder.aProjectionYear().withCalendarYear(2033)
                        .withEndingInvestableAssets(120000).build());
        var baseline = snapshot(years, policy("17.00", 2034));
        var current = snapshot(years, policy("22.0", 2032));
        var compared = new BreakEvenAnalyzer().analyze(baseline, current);
        var zeroOnly = new BreakEvenAnalyzer().analyze(snapshot(years, policy("0", 2020)),
                snapshot(years, policy("0.00", 2040)));
        var currentOnly = new BreakEvenAnalyzer().analyze(snapshot(years, policy("0", 2032)),
                snapshot(years, policy("22", 2032)));
        var baselineOnly = new BreakEvenAnalyzer().analyze(snapshot(years, policy("22", 2032)),
                snapshot(years, policy("0", 2032)));
        var unknown = new BreakEvenAnalyzer().analyze(snapshot(years, null), snapshot(years, null));

        assertEquals(zeroOnly.metrics(), compared.metrics(), "Assumption disclosure must not change computed metrics.");
        assertEquals("Social Security benefit reduction: 17% beginning 2034",
                BreakEvenPresentation.reductionDisclosure(compared.baselineAssumptions()));
        assertEquals("Social Security benefit reduction: 22% beginning 2032",
                BreakEvenPresentation.reductionDisclosure(compared.currentAssumptions()));
        assertNull(BreakEvenPresentation.reductionDisclosure(zeroOnly.baselineAssumptions()));
        assertNull(BreakEvenPresentation.reductionDisclosure(zeroOnly.currentAssumptions()));
        assertEquals("Social Security benefit reduction: 22% beginning 2032",
                BreakEvenPresentation.reductionDisclosure(currentOnly.currentAssumptions()));
        assertNull(BreakEvenPresentation.reductionDisclosure(currentOnly.baselineAssumptions()));
        assertEquals("Social Security benefit reduction: 22% beginning 2032",
                BreakEvenPresentation.reductionDisclosure(baselineOnly.baselineAssumptions()));
        assertNull(BreakEvenPresentation.reductionDisclosure(baselineOnly.currentAssumptions()));
        assertTrue(BreakEvenPresentation.reductionDisclosure(unknown.baselineAssumptions()).contains("Unavailable"));

        Path folder = Path.of("target/break-even-reduction-disclosure");
        Files.createDirectories(folder);
        FutureTask<Void> task = new FutureTask<>(() -> {
            var view = new BreakEvenAnalysisView(compared);
            view.applyCss();
            Label baselineDisclosure = (Label) view.lookup("#break-even-baseline-reduction");
            Label currentDisclosure = (Label) view.lookup("#break-even-current-reduction");
            assertNotNull(baselineDisclosure);
            assertNotNull(currentDisclosure);
            assertEquals("Social Security benefit reduction: 17% beginning 2034", baselineDisclosure.getText());
            assertEquals("Social Security benefit reduction: 22% beginning 2032", currentDisclosure.getText());
            assertNotNull(view.lookup("#break-even-cards"));
            for (var metric : BreakEvenMetric.values()) assertNotNull(view.lookup("#break-even-card-" + metric.name()));

            var zeroView = new BreakEvenAnalysisView(zeroOnly);
            assertNull(zeroView.lookup("#break-even-baseline-reduction"));
            assertNull(zeroView.lookup("#break-even-current-reduction"));

            var beforeExportMetrics = compared.metrics();
            export(compared, folder.resolve("different.pdf"));
            export(zeroOnly, folder.resolve("zero.pdf"));
            export(currentOnly, folder.resolve("current-only.pdf"));
            export(baselineOnly, folder.resolve("baseline-only.pdf"));
            for (var pdfName : List.of("different", "zero", "current-only", "baseline-only")) {
                try (var pdf = Loader.loadPDF(folder.resolve(pdfName + ".pdf").toFile())) {
                    var page = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0, 110);
                    javax.imageio.ImageIO.write(page, "png", folder.resolve(pdfName + "-page-1.png").toFile());
                }
            }
            assertSame(beforeExportMetrics, compared.metrics(), "PDF export must only render the accepted result.");
            try (var pdf = Loader.loadPDF(folder.resolve("different.pdf").toFile())) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("Social Security benefit reduction: 17% beginning 2034"));
                assertTrue(text.contains("Social Security benefit reduction: 22% beginning 2032"));
                assertTrue(text.contains("BASELINE PLAN"));
                assertTrue(text.contains("CURRENT PLAN"));
                assertTrue(text.contains("Cumulative Social Security Benefits Difference"));
            }
            try (var pdf = Loader.loadPDF(folder.resolve("zero.pdf").toFile())) {
                String text = new PDFTextStripper().getText(pdf);
                assertFalse(text.contains("Social Security benefit reduction:"));
            }
            for (var scenario : List.of("current-only", "baseline-only")) {
                try (var pdf = Loader.loadPDF(folder.resolve(scenario + ".pdf").toFile())) {
                    String text = new PDFTextStripper().getText(pdf);
                    assertEquals(1, text.split("Social Security benefit reduction:", -1).length - 1);
                    assertTrue(text.contains("22% beginning 2032"));
                }
            }
            captureLightDarkPreviews(compared, folder);
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @Test
    void frozenProjectionInputsSupplyEachRestoredProjectionPolicyAndLegacyDefault() throws Exception {
        var legacyPeople = new BreakEvenPlanSummary(
                new BreakEvenPlanSummary.PersonSummary("Primary", null, null),
                new BreakEvenPlanSummary.PersonSummary("Spouse", null, null));
        var strictMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        assertEquals(legacyPeople, strictMapper.readValue(strictMapper.writeValueAsBytes(legacyPeople),
                BreakEvenPlanSummary.class), "Transient disclosure metadata must not change persisted result shapes.");

        var current = acceptedSnapshot(new BigDecimal("22"), 2032);
        var baseline = acceptedSnapshot(new BigDecimal("17"), 2034);
        assertEquals(policy("22", 2032), current.restoreBreakEvenAssumptions().benefitReductionPolicy());
        assertEquals(policy("17", 2034), baseline.restoreBreakEvenAssumptions().benefitReductionPolicy());
        // Swapping the compared roles changes their presentation position, not their frozen values.
        var swapped = List.of(baseline, current);
        assertEquals(policy("17", 2034), swapped.getFirst().restoreBreakEvenAssumptions().benefitReductionPolicy());
        assertEquals(policy("22", 2032), swapped.get(1).restoreBreakEvenAssumptions().benefitReductionPolicy());

        var legacy = acceptedSnapshot(ZERO, 2032);
        var restoredLegacy = legacy.restoreBreakEvenAssumptions();
        assertEquals(policy("0", 2032), restoredLegacy.benefitReductionPolicy());
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var reloadedLegacy = mapper.readValue(mapper.writeValueAsBytes(legacy), CompleteProjectionSnapshot.class);
        assertEquals(policy("0", 2032), reloadedLegacy.restoreBreakEvenAssumptions().benefitReductionPolicy(),
                "A pre-feature frozen plan input omitting these fields establishes the compatibility default.");

        var malformed = corruptFrozenPolicy(current, new BigDecimal("101"), 2032);
        assertThrows(IllegalArgumentException.class, malformed::restoreBreakEvenAssumptions,
                "Invalid frozen provenance must not be silently treated as zero or current settings.");
    }

    private static void export(BreakEvenAnalysisResult result, Path target) throws Exception {
        var report = new BreakEvenPdfReport(result, BreakEvenContext.unavailable(),
                new BreakEvenInsightService().prepare(result));
        new BreakEvenPdfExporter().export(report, target);
    }

    private static void captureLightDarkPreviews(BreakEvenAnalysisResult result, Path folder) throws Exception {
        var themes = com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager.start(
                com.daviddunn.retirementplanner.application.settings.ApplicationTheme.LIGHT);
        try {
            for (int width : new int[]{900, 1180}) {
                for (var theme : com.daviddunn.retirementplanner.application.settings.ApplicationTheme.values()) {
                    themes.apply(theme);
                    var view = new BreakEvenAnalysisView(result);
                    var scroll = new ScrollPane(view);
                    scroll.setFitToWidth(true);
                    scroll.getStylesheets().add(BreakEvenReductionDisclosureTest.class
                            .getResource("/css/break-even.css").toExternalForm());
                    var stage = new Stage();
                    stage.setScene(new Scene(scroll, width, width == 900 ? 650 : 820));
                    stage.show();
                    try {
                        scroll.applyCss();
                        scroll.layout();
                        var image = stage.getScene().snapshot(null);
                        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < output.getHeight(); y++) {
                            for (int x = 0; x < output.getWidth(); x++) {
                                output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                            }
                        }
                        javax.imageio.ImageIO.write(output, "png", folder.resolve("break-even-"
                                + theme.name().toLowerCase() + "-" + width + ".png").toFile());
                    } finally {
                        stage.close();
                    }
                }
            }
        } finally {
            themes.close();
        }
    }

    private static BreakEvenProjectionSnapshot snapshot(List<com.daviddunn.retirementplanner.domain.projection.ProjectionYear> years,
            SocialSecurityBenefitReductionPolicy policy) {
        var summary = new BreakEvenPlanSummary(
                new BreakEvenPlanSummary.PersonSummary("Primary", null, null),
                new BreakEvenPlanSummary.PersonSummary("Spouse", null, null), policy);
        return new BreakEvenProjectionSnapshot(years, List.of(), summary);
    }

    private static SocialSecurityBenefitReductionPolicy policy(String percent, int year) {
        return new SocialSecurityBenefitReductionPolicy(new BigDecimal(percent), year);
    }

    private static CompleteProjectionSnapshot acceptedSnapshot(BigDecimal percent, int year) {
        var plan = RetirementPlanFactory.createEmptyPlan();
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Synthetic test account", com.daviddunn.retirementplanner.domain.model.AccountOwnership.JOINT,
                new BigDecimal("10000000")));
        plan.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1963, 6, 4));
        plan.getHousehold().spouse().ifPresent(spouse -> spouse.setBirthDate(java.time.LocalDate.of(1965, 2, 28)));
        var existing = plan.getPlanningAssumptions();
        var economic = existing.getEconomicAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(
                new EconomicAssumptions(economic.getExpectedAnnualInvestmentReturn(), economic.getGeneralInflationRate(),
                        economic.getHealthcareInflationRate(), economic.getSocialSecurityColaRate(), percent, year),
                existing.getTaxAssumptions(), existing.getWithdrawalAssumptions(), existing.getDeathScenarioAssumptions(),
                existing.getProjectionLengthYears(), existing.getProjectionStartDate(), existing.getStateTaxAssumptions()));
        var projection = new ProjectionEngine().project(plan);
        String inputs = CompleteProjectionSnapshot.freeze(plan);
        var nonInvestable = projection.getYears().stream().map(projectedYear ->
                new CompleteProjectionSnapshot.NonInvestableYear(projectedYear.getCalendarYear(), List.of(), ZERO)).toList();
        return new CompleteProjectionSnapshot(CompleteProjectionSnapshot.VERSION, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.EPOCH, inputs, CompleteProjectionSnapshot.digest(inputs), "synthetic frozen result",
                com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot.capture(projection),
                nonInvestable, new CompleteProjectionSnapshot.Summary(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO),
                new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Primary", null, null),
                        new BreakEvenPlanSummary.PersonSummary("Spouse", null, null)));
    }

    private static CompleteProjectionSnapshot corruptFrozenPolicy(CompleteProjectionSnapshot source,
            BigDecimal percent, int year) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var planInputs = mapper.readTree(source.inputsDocument());
        var economic = (com.fasterxml.jackson.databind.node.ObjectNode) planInputs.path("planningAssumptions")
                .path("economicAssumptions");
        economic.put("socialSecurityBenefitReductionPercent", percent);
        economic.put("socialSecurityBenefitReductionEffectiveYear", year);
        String document = mapper.writeValueAsString(planInputs);
        return new CompleteProjectionSnapshot(source.version(), source.id(), source.owner(), source.sourcePlanId(),
                source.acceptedAt(), document, CompleteProjectionSnapshot.digest(document), source.provenance(),
                source.annual(), source.nonInvestable(), source.summary(), source.people());
    }
}
