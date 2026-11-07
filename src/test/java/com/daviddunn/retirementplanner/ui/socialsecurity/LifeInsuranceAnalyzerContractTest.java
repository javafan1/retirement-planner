package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeInsuranceAnalyzerContractTest {
    @Test void calculatedIntegratedCapabilityRoundTripsAndCannotBeDowngradedToTaxableV1() throws Exception {
        var plan=ConditionalBenefitIntegratedExecutionTest.plan();
        var node=LifeInsurancePrincipalTest.document(); node.put("coverageStart","2027-01-01"); node.put("coverageEnd","2027-01-01");
        plan.setConditionalBenefits(List.of(BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class)));
        var universe=IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request=new IntegratedSocialSecurityCompleteStrategySearchRequest(plan,List.of(67,68),List.of(67),
                universe.primarySurvivorCandidates().subList(0,1),universe.spouseSurvivorCandidates().subList(0,1),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2);
        var calculated=new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        var context=ConditionalBenefitAnalyzerReportingTest.context(plan,false);
        var display=ExhaustiveIntegratedSearchPresentation.from(calculated,Duration.ofSeconds(1),20);
        var payload=ExhaustiveResultSnapshots.capture(display,context,null,ConditionalBenefitAnalyzerReportingTest.DONE,"DIFFERENCE_FROM_CURRENT","ranked");
        var saved=ConditionalBenefitAnalyzerReportingTest.saved(plan,AnalysisKind.COUPLE_EXHAUSTIVE,payload,
                ConditionalBenefitAnalyzerReportingTest.snapshotContext(context,null));
        assertEquals(2,saved.benefitExecution().version());
        assertEquals("analyzer-life-principal-v2",saved.algorithms().get("conditional-benefits"));
        var codec=new AnalysisSnapshotCodec();
        assertEquals(saved,codec.decode(codec.encode(saved)));
        var assessment=new AnalysisFingerprintService().assess(saved.kind(),plan,
                ConditionalBenefitAnalyzerValidation.inputs(payload,saved.context()),null);
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,new AnalysisCompatibilityPolicy().assess(saved,assessment,
                AnalysisVersions.algorithms(saved.kind()),saved.references()).state());
        var capability=saved.benefitExecution();
        assertThrows(IllegalArgumentException.class,()->new ConditionalBenefitAnalysisExecution(1,ConditionalBenefitAnalysisExecution.MODEL,
                capability.family(),capability.scenarioProtocol(),capability.candidates()));
        plan.setConditionalBenefits(List.of());
        var restored=ExhaustiveResultSnapshots.restore((ExhaustiveSnapshot)saved.payload());
        var map=DeterministicHeatMapAdapter.from(restored.model());
        var report=IntegratedAnalyzerReportAdapter.deterministic(restored.report(),restored.model(),map,
                ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT,map.cells().getFirst(),calculated.rankedSuccessfulEntries().getFirst(),
                restored.model().groups(),"No SS-only prerequisite required");
        var folder=Path.of("target/phase23b/reporting"); Files.createDirectories(folder);
        var path=folder.resolve("integrated-excluded-history.pdf"); new IntegratedAnalyzerPdfExporter().export(report,path);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).replaceAll("\\s+"," ");
            assertTrue(text.contains("federally excluded")); assertTrue(text.contains("Future investment earnings may be taxable"));
            assertTrue(text.contains("PERSONAL_LIFE_PRINCIPAL_ONLY_V1"));
        }
    }
}
