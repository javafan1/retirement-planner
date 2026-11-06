package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import com.daviddunn.retirementplanner.ui.charts.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitReportingTest {
    static final Path FOLDER = Path.of("target","phase22b2","reporting");
    static RetirementPlan fixture() {
        var plan = ConditionalBenefitFinancialIntegrationTest.base(StateTaxJurisdiction.FL);
        var monthly = change(change(ConditionalBenefitFinancialIntegrationTest.payment("2500",LocalDate.of(2030,1,1)),"recipient",PersonReference.SPOUSE),
                "trigger",new Trigger(TriggerType.DEATH,null,PersonReference.PRIMARY,null));
        monthly = change(monthly,"label","Survivor monthly benefit");
        var lump = change(change(change(monthly,"id",new UUID(0,2)),"paymentPattern",PaymentPattern.ONE_TIME_LUMP_SUM),"grossAmount",new BigDecimal("400000"));
        lump = change(lump,"label","Synthetic covered survivor lump-sum payment with an intentionally long description for original-context reporting and wrapping");
        var future = change(change(ConditionalBenefitFinancialIntegrationTest.payment("9000",LocalDate.of(2045,1,1)),"id",new UUID(0,3)),"label","Future untriggered benefit");
        plan.setConditionalBenefits(List.of(monthly,lump,future)); return plan;
    }
    static ProjectionPdfReport report(RetirementPlan plan, Projection projection) {
        var non = new NonInvestableAssetProjectionService().project(plan.getNonInvestableAssets(),projection.getStartYear(),projection.getEndYear());
        var chart = ProjectionChartModel.from(projection.getYears(),non,BreakEvenPlanSummary.from(plan),plan.getAccountPortfolio().getAccounts());
        return new ProjectionPdfReport("Synthetic B2 conditional acceptance",chart,BigDecimal.ZERO,projection.getYearAt(projection.size()-1).getAfterTaxEstateValue(),null);
    }
    @Test void pdfUsesFrozenCompletedReceiptsAndOmitsSectionsForLegacyPlans() throws Exception {
        Files.createDirectories(FOLDER);
        var live = fixture(); var completed = new ProjectionEngine().project(live);
        var frozen = new RetirementPlanScenarioCopyService().copy(live); var retained = RetainedProjectionSnapshot.capture(completed);
        if (!Files.exists(FOLDER.resolve("synthetic-benefits.json")))
            new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository().save(frozen,FOLDER.resolve("synthetic-benefits.json"));
        var report = report(frozen,completed);
        new ProjectionPdfExporter().export(frozen,completed,List.of(),report,FOLDER.resolve("conditional-current.pdf"));
        live.setConditionalBenefits(List.of());
        new ProjectionPdfExporter().export(frozen,retained.restoreProjection(),List.of(),report,FOLDER.resolve("conditional-history.pdf"));
        try (var original = Loader.loadPDF(FOLDER.resolve("conditional-current.pdf").toFile());
             var historical = Loader.loadPDF(FOLDER.resolve("conditional-history.pdf").toFile())) {
            var text = new PDFTextStripper().getText(original);
            assertEquals(text,new PDFTextStripper().getText(historical)); assertEquals(original.getNumberOfPages(),historical.getNumberOfPages());
            assertTrue(text.contains("CONDITIONAL BENEFITS")); assertTrue(text.contains("Lump sum")); assertTrue(text.contains("Monthly income"));
            assertTrue(text.contains("400,000")); assertTrue(text.replaceAll("\\s+"," ").contains("Future triggers are not current income"));
            for (int page=Math.max(0,original.getNumberOfPages()-2);page<original.getNumberOfPages();page++)
                javax.imageio.ImageIO.write(new PDFRenderer(original).renderImageWithDPI(page,96),"png",FOLDER.resolve("conditional-page-"+page+".png").toFile());
        }
        var zero = new ProjectionEngine().project(live);
        new ProjectionPdfExporter().export(live,zero,List.of(),report(live,zero),FOLDER.resolve("legacy-zero.pdf"));
        try (var pdf = Loader.loadPDF(FOLDER.resolve("legacy-zero.pdf").toFile())) { assertFalse(new PDFTextStripper().getText(pdf).contains("CONDITIONAL BENEFITS")); }
    }
    @Test void actualDetailsScenesShowOnlyCompletedReceiptsInBothThemesAndSizes() throws Exception {
        Files.createDirectories(FOLDER);
        var projection = new ProjectionEngine().project(fixture());
        com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.startFx();
        for (var theme : com.daviddunn.retirementplanner.application.settings.ApplicationTheme.values()) for (int width : new int[]{900,1180}) {
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.fx(()-> {
                var pane = new com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane(projection.getYearAt(1),null,BigDecimal.ZERO,null);
                var stage = new javafx.stage.Stage(); var scene = new javafx.scene.Scene(pane,width,width==900?650:820);
                try (var themes = com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager.start(theme)) {
                    stage.setScene(scene); stage.show(); pane.applyCss(); pane.layout();
                    ((javafx.scene.control.ScrollPane)pane.getCenter()).setVvalue(.3); pane.applyCss(); pane.layout();
                    var image = pane.snapshot(null,null);
                    var bitmap = new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y=0;y<bitmap.getHeight();y++) for (int x=0;x<bitmap.getWidth();x++) bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                    javax.imageio.ImageIO.write(bitmap,"png",FOLDER.resolve("details-"+theme+"-"+width+".png").toFile());
                    var labels = new ArrayList<String>(); collect(pane,labels);
                    assertTrue(labels.stream().anyMatch(s->s.contains("Conditional Lump-Sum Receipts")));
                    assertTrue(labels.stream().anyMatch(s->s.contains("Survivor monthly benefit")));
                    assertFalse(labels.stream().anyMatch(s->s.contains("Future untriggered benefit")));
                } finally { stage.close(); }
            });
        }
    }
    static void collect(javafx.scene.Node node,List<String> labels) {
        if (node instanceof javafx.scene.control.Label label) labels.add(label.getText());
        if (node instanceof javafx.scene.Parent parent) parent.getChildrenUnmodifiable().forEach(child->collect(child,labels));
    }
}
