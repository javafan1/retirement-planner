package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModePersistenceTest {
    @TempDir Path directory;
    @Test void legacyPreferenceWritesMergeWithoutResettingAConcurrentModeSelection() throws Exception {
        var stored=new java.util.concurrent.atomic.AtomicReference<>(new ApplicationSettings());
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var saves=new java.util.concurrent.atomic.AtomicInteger();
        var repo=new ApplicationSettingsRepository(){
            public ApplicationSettings load(){return stored.get().copy();}
            public void save(ApplicationSettings value){
                if(saves.incrementAndGet()==1){entered.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Timed out");}catch(InterruptedException e){throw new IllegalStateException(e);}}
                stored.set(value.copy());
            }
        };
        try(var service=new ApplicationSettingsService(repo)) {
            var advanced=service.savePreferences(ApplicationTheme.LIGHT,directory.toString(),directory.toString(),false,FinancialCalculationMode.ADVANCED).toCompletableFuture();
            assertTrue(entered.await(20,TimeUnit.SECONDS));
            var legacy=service.savePreferences(ApplicationTheme.DARK,directory.toString(),directory.toString(),false).toCompletableFuture();
            release.countDown();java.util.concurrent.CompletableFuture.allOf(advanced,legacy).get(20,TimeUnit.SECONDS);
            assertEquals(FinancialCalculationMode.ADVANCED,service.snapshot().getDefaultFinancialCalculationMode());
            assertEquals(ApplicationTheme.DARK,service.snapshot().getTheme());
        }finally{release.countDown();}
    }
    @Test void invalidSettingsModeUsesExistingVisibleRecoveryAndPreservesOriginalBytes() throws Exception {
        var path=directory.resolve("invalid-settings.json");var original="{\"defaultFinancialCalculationMode\":\"UNKNOWN\"}";Files.writeString(path,original);
        var repo=new JsonApplicationSettingsRepository(path);repo.load();assertFalse(repo.takeWarnings().isEmpty());
        assertThrows(RuntimeException.class,()->repo.save(new ApplicationSettings()));assertEquals(original,Files.readString(path));
    }
    @Test void legacyOmissionDoesNotConsultApplicationDefaultOrChangeFrozenInputBytes() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);
        var original=CompleteProjectionSnapshot.freeze(plan);
        assertFalse(original.contains("financialCalculationMode"));
        var repo=new JsonRetirementPlanRepository(); var path=directory.resolve("legacy.json");
        repo.save(plan,path); var loaded=repo.load(path);
        assertNull(loaded.getFinancialCalculationMode());
        assertEquals(original,CompleteProjectionSnapshot.freeze(loaded));
        var settings=new ApplicationSettings(); settings.setDefaultFinancialCalculationMode(FinancialCalculationMode.BASIC);
        assertEquals(com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy.resolved(plan),
                com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy.resolved(loaded));
    }
    @Test void explicitModesSurviveSaveAsScenarioAndBaselineWithoutSharingMutableCollections() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true); var repo=new JsonRetirementPlanRepository();
        for(var mode:FinancialCalculationMode.values()) {
            plan.setFinancialCalculationMode(mode);var path=directory.resolve(mode+".json");repo.save(plan,path);
            var loaded=repo.load(path);assertEquals(mode,loaded.getFinancialCalculationMode());
            repo.save(loaded,directory.resolve(mode+"-copy.json"));
            assertEquals(mode,new RetirementPlanScenarioCopyService().copy(loaded).getFinancialCalculationMode());
            assertEquals(mode,CompleteProjectionSnapshot.restoreInputs(CompleteProjectionSnapshot.freeze(loaded)).getFinancialCalculationMode());
            assertEquals(mode,com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot.fromRetirementPlan(loaded).getFinancialCalculationMode());
        }
    }
    @Test void modeChangesFinancialIdentityButNotSocialSecurityOnlyIdentity() {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);var service=new AnalysisFingerprintService();
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        var opening=plan.getPlanningAssumptions().getProjectionStartDate();
        var ssInputs=new AnalysisFingerprintService.Inputs(java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,opening,opening,
                java.math.BigDecimal.ZERO,null,null,java.util.List.of(),java.util.List.of(),null);
        var ss=service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY,plan,ssInputs);
        var basic=service.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none());
        plan.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        assertEquals(ss,service.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY,plan,ssInputs));
        assertNotEquals(basic,service.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none()));
        assertEquals(5,ConditionalBenefitAnalysisExecution.capture(AnalysisKind.COUPLE_EXHAUSTIVE,plan).version());
        assertThrows(IllegalArgumentException.class,()->ConditionalBenefitAnalysisExecution.captureLegacy(
                AnalysisKind.COUPLE_EXHAUSTIVE,com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPlanDocument.capture(plan),null));
    }
    @Test void settingsDefaultIsBasicAndExplicitAdvancedPersistsWithoutChangingExistingPlan() throws Exception {
        var path=directory.resolve("settings.json");var repo=new JsonApplicationSettingsRepository(path);
        assertEquals(FinancialCalculationMode.BASIC,repo.load().getDefaultFinancialCalculationMode());
        Files.writeString(path,"{\"theme\":\"LIGHT\"}");
        assertEquals(FinancialCalculationMode.BASIC,new JsonApplicationSettingsRepository(path).load().getDefaultFinancialCalculationMode());
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
            service.savePreferences(ApplicationTheme.LIGHT,directory.toString(),directory.toString(),false,FinancialCalculationMode.ADVANCED)
                    .toCompletableFuture().get(10,TimeUnit.SECONDS);
            assertEquals(FinancialCalculationMode.ADVANCED,new JsonApplicationSettingsRepository(path).load().getDefaultFinancialCalculationMode());
            assertEquals(FinancialCalculationMode.BASIC,plan.getFinancialCalculationMode());
        }
    }
    @Test void unknownAndExplicitNullPlanModesRejectInsteadOfBecomingBasic() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true); var mapper=new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var tree=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(plan);
        tree.put("financialCalculationMode","UNKNOWN");assertThrows(Exception.class,()->mapper.treeToValue(tree,RetirementPlan.class));
        tree.putNull("financialCalculationMode");assertThrows(Exception.class,()->mapper.treeToValue(tree,RetirementPlan.class));
        tree.put("financialCalculationMode",0);assertThrows(Exception.class,()->mapper.treeToValue(tree,RetirementPlan.class));
    }
}
