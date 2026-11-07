package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium;
import static org.junit.jupiter.api.Assertions.*;

public class ContractualPremiumHistoryTest {
    @TempDir Path directory;
    static final Path OUTPUT=Path.of("target/phase23c/reporting");
    ApplicationController controller() { return new ApplicationController(new JsonRetirementPlanRepository(),
            new JsonApplicationSettingsRepository(directory.resolve("settings-"+UUID.randomUUID())),new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses")))); }

    /** Separate JVM read oracle: no controller, evaluator or financial execution on restoration. */
    public static void main(String[] args) throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var snapshot=json.readValue(Files.readString(Path.of(args[0])),CompleteProjectionSnapshot.class);
        if(!snapshot.calculationSemanticsCurrent()) throw new AssertionError("Saved premium semantics must remain current across JVMs");
        System.out.println("RESTORED "+snapshot.id());
    }

    @Test void premiumEvidenceHasStableCanonicalBindingAcrossIndependentJvms() throws Exception {
        var p=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.MI); p.getHousehold().addExpense(premium());
        var c=controller();
        try {
            c.newPlan(p); c.getCurrentProjection(); var snapshot=c.getAcceptedProjectionSnapshot();
            var saved=directory.resolve("cross-jvm.json");
            var json=new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            Files.writeString(saved,json.writeValueAsString(snapshot));
            for(int i=0;i<2;i++) {
                var output=directory.resolve("child-"+i+".txt");
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx4g",
                        "-cp",System.getProperty("java.class.path"),getClass().getName(),saved.toString())
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
                try {
                    assertTrue(process.waitFor(25,TimeUnit.SECONDS),"Read-only child JVM did not finish");
                    assertEquals(0,process.exitValue(),Files.readString(output));
                    assertTrue(Files.readString(output).contains("RESTORED "+snapshot.id()));
                } finally { if(process.isAlive()) process.destroyForcibly(); }
            }
        } finally { LifeInsuranceReportingHistoryTest.stop(c); }
    }

    @Test void completeHistorySaveAsRestartAndBothSwapsPreserveOriginalPremiumsWithoutProjection() throws Exception {
        Files.createDirectories(OUTPUT);
        var p=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL); p.getHousehold().addExpense(premium());
        var originalPlan=new RetirementPlanScenarioCopyService().copy(p);
        var c=controller(); ApplicationController restarted=null;
        try {
            c.newPlan(p); c.getCurrentProjection(); var old=c.getAcceptedProjectionSnapshot();
            assertTrue(old.provenance().contains("contractual-premiums=insured-alive-months-v1"));
            assertTrue(old.provenance().startsWith("conditional-benefits=deterministic-life-principal-v2"));
            assertTrue(old.calculationSemanticsCurrent());
            assertThrows(IllegalArgumentException.class,()->ContractualPremiumExecutionSemantics.validate(old.inputsDocument(),old.annual(),
                    old.provenance().substring(0,old.provenance().indexOf(ContractualPremiumExecutionSemantics.KEY))));
            c.saveCurrentAsBaseline("Original $2,000 contractual premium");
            var initial=p.getHousehold().getExpenses().stream().filter(Expense::isContractualInsurancePremium).findFirst().orElseThrow();
            p.getHousehold().replaceExpense(initial,premium("3000",ContractualPremiumPolicy.Insured.PRIMARY,initial.getStartDate(),initial.getEndDate()));
            c.markModified(); c.invalidateProjection(); assertFalse(old.matches(p));
            c.getCurrentProjection(); var current=c.getAcceptedProjectionSnapshot();
            assertNotEquals(old.inputDigest(),current.inputDigest()); assertNotEquals(old.annual(),current.annual());
            c.saveAs(directory.resolve("original.json")); c.saveAs(directory.resolve("save-as.json"));
            restarted=controller();
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(restarted,"projectionEngine",new ProjectionEngine() {
                @Override public Projection project(RetirementPlan ignored) { throw new AssertionError("History must not project"); }
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan ignored) { throw new AssertionError("History must not execute"); }
            });
            restarted.open(directory.resolve("save-as.json")); restarted.getProjectionRestoration().toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertEquals(current.id(),restarted.getAcceptedProjectionSnapshot().id()); assertEquals(current.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            var swap=restarted.prepareProjectionSwap().toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap)); assertEquals(old.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            assertEquals(new BigDecimal("2000"),restarted.getCurrentPlan().getHousehold().getExpenses().stream().filter(Expense::isContractualInsurancePremium).findFirst().orElseThrow().getAnnualAmount());
            restarted.save(); swap=restarted.prepareProjectionSwap().toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertTrue(restarted.commitProjectionSwap(swap)); assertEquals(current.annual(),restarted.getAcceptedProjectionSnapshot().annual());
            var frozenProjection=old.annual().restoreProjection();
            for(String name:List.of("premium-original","premium-history")) new ProjectionPdfExporter().export(originalPlan,frozenProjection,List.of(),
                    ConditionalBenefitReportingTest.report(originalPlan,frozenProjection),OUTPUT.resolve(name+".pdf"));
            try(var a=Loader.loadPDF(OUTPUT.resolve("premium-original.pdf").toFile()); var b=Loader.loadPDF(OUTPUT.resolve("premium-history.pdf").toFile())) {
                var text=new PDFTextStripper().getText(a); assertEquals(text,new PDFTextStripper().getText(b));
                assertTrue(text.contains("CONTRACTUAL INSURANCE PREMIUMS"));
                assertTrue(text.replaceAll("\\s+"," ").contains("not reduced by household survivor-spending assumptions"));
                for(int page=0;page<a.getNumberOfPages();page++) javax.imageio.ImageIO.write(new PDFRenderer(a).renderImageWithDPI(page,84),"png",OUTPUT.resolve("premium-page-"+page+".png").toFile());
            }
        } finally { if(restarted!=null) LifeInsuranceReportingHistoryTest.stop(restarted); LifeInsuranceReportingHistoryTest.stop(c); }
    }

    @Test void scenarioBaselineFrozenInputsAndFinancialIdentityRetainPoliciesAndIgnoreLabels() throws Exception {
        var p=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL); p.setConditionalBenefits(List.of()); p.getHousehold().addExpense(premium());
        var service=new AnalysisFingerprintService(); var kind=AnalysisKind.COUPLE_EXHAUSTIVE;
        String before=service.fingerprint(kind,p,AnalysisFingerprintService.Inputs.none());
        var expense=p.getHousehold().getExpenses().getLast();
        p.getHousehold().replaceExpense(expense,new Expense("Renamed",new BigDecimal("2000.00"),GrowthCategory.NONE,expense.getStartDate(),expense.getEndDate(),
                ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,null,expense.getContractualPremium()));
        assertEquals(before,service.fingerprint(kind,p,AnalysisFingerprintService.Inputs.none()));
        var frozen=CompleteProjectionSnapshot.freeze(p); var snapshot=CompleteProjectionSnapshot.restoreInputs(frozen);
        assertEquals(expense.getContractualPremium(),snapshot.getHousehold().getExpenses().getLast().getContractualPremium());
        var copy=new RetirementPlanScenarioCopyService().copy(p);
        assertEquals(frozen,CompleteProjectionSnapshot.freeze(copy));
        var repository=new JsonRetirementPlanRepository(); repository.save(p,directory.resolve("plan.json"));
        var loaded=repository.load(directory.resolve("plan.json"));
        assertEquals(frozen,CompleteProjectionSnapshot.freeze(loaded));
        var replacement=premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,expense.getStartDate(),expense.getEndDate());
        p.getHousehold().replaceExpense(p.getHousehold().getExpenses().getLast(),replacement);
        assertNotEquals(before,service.fingerprint(kind,p,AnalysisFingerprintService.Inputs.none()));
    }

    @Test void produceAndVerifySafeSyntheticNativeAcceptancePlansWithoutOverwriting() throws Exception {
        var folder=Path.of("target/phase23c"); Files.createDirectories(folder);
        for(String variant:List.of("couple-alive","spouse-dies","insured-dies","premium-and-death-principal","single-primary","ordinary-control")) {
            boolean single=variant.equals("single-primary");
            var p=single?com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.plan(false):LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
            if(!variant.equals("premium-and-death-principal")) p.setConditionalBenefits(List.of());
            p.getHousehold().getExpenses().stream().toList().forEach(p.getHousehold()::removeExpense);
            p.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
            p.getHousehold().spouse().ifPresent(person->person.setMortalityCategory(MortalityCategory.FEMALE));
            var a=p.getPlanningAssumptions();
            var death=variant.equals("spouse-dies")||variant.equals("ordinary-control")?DeathScenario.SPOUSE_DIES:
                    variant.equals("insured-dies")||variant.equals("premium-and-death-principal")?DeathScenario.PRIMARY_DIES:DeathScenario.BOTH_SURVIVE;
            p.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                    new DeathScenarioAssumptions(death,2035,67,new BigDecimal(".75")),8,LocalDate.of(2030,1,1),a.getStateTaxAssumptions()));
            p.getHousehold().addExpense(variant.equals("ordinary-control")?new Expense("Ordinary lifestyle control",new BigDecimal("2000"),GrowthCategory.NONE):premium());
            var path=folder.resolve(variant+".json"); var repository=new JsonRetirementPlanRepository();
            if(!Files.exists(path)) repository.save(p,path);
            var loaded=repository.load(path); var projection=new ProjectionEngine().project(loaded);
            for(var year:projection.getYears()) {
                String expected=year.getCalendarYear()>=2035 && (variant.equals("insured-dies")||variant.equals("premium-and-death-principal"))?"0":
                        year.getCalendarYear()>=2035 && variant.equals("ordinary-control")?"1500":"2000";
                assertEquals(0,new BigDecimal(expected).compareTo(year.getAnnualExpenses()),variant+" "+year.getCalendarYear());
            }
            assertEquals(0,loaded.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance().compareTo(new BigDecimal("500000")));
        }
    }
}
