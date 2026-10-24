package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisParameterControllerTest {
    @TempDir Path directory;
    @Test void autoWritePreservesDirtyFlagFinancialRevisionAndRestartedIndependentSaveAs() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var controller = new ApplicationController(repository, new JsonApplicationSettingsRepository(directory.resolve("settings.json")));
        controller.newPlan(RetirementPlanFactory.createEmptyPlan());
        Path original = directory.resolve("original.json"); controller.saveAs(original);
        var plan = controller.getCurrentPlan();
        var coordinator = controller.getAnalysisParameters();
        try {
            plan.getHousehold().getPrimaryPerson().setFirstName("Unrelated unsaved edit"); controller.markModified();
            long revision = controller.getSourcePlanRevision();
            var config = ParameterFixtures.configuration(AnalysisKind.COUPLE_QUICK);
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(plan, original), 1, config)));
            assertTrue(coordinator.drain(Duration.ofSeconds(5)).complete());
            assertTrue(controller.isModified()); assertEquals(revision, controller.getSourcePlanRevision());
            assertEquals("", repository.load(original).getHousehold().getPrimaryPerson().getFirstName());
            assertEquals(config, repository.load(original).getAnalyzerConfigurations().get(config.kind()).orElseThrow());
            controller.save(); assertFalse(controller.isModified());
            byte[] prior = Files.readAllBytes(original); String id = plan.getPlanId();
            Path copy = directory.resolve("copy.json"); controller.saveAs(copy);
            assertNotEquals(id, plan.getPlanId()); assertArrayEquals(prior, Files.readAllBytes(original));
            var reopened = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(directory.resolve("restart-settings.json")));
            try { assertEquals(config, reopened.open(copy).getAnalyzerConfigurations().get(config.kind()).orElseThrow()); }
            finally { reopened.getAnalysisParameters().shutdown(); }
        } finally { coordinator.shutdown(); }
    }
    @Test void failedSaveAsKeepsOriginalBindingAndAcceptedConfiguration() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var controller = new ApplicationController(repository, new JsonApplicationSettingsRepository(directory.resolve("settings.json")));
        Path file = directory.resolve("original.json"); controller.saveAs(file);
        var coordinator = controller.getAnalysisParameters();
        try {
            var plan = controller.getCurrentPlan(); var binding = coordinator.bind(plan, file);
            var config = ParameterFixtures.configuration(AnalysisKind.INDIVIDUAL_DETERMINISTIC);
            coordinator.accept(new AcceptedAnalysisParameters(binding, 1, config)); coordinator.drain(Duration.ofSeconds(5));
            assertThrows(java.io.IOException.class, () -> controller.saveAs(directory.resolve("missing/failure.json")));
            assertEquals(file, controller.getCurrentFile()); assertEquals(binding.planId().toString(), plan.getPlanId());
            assertEquals(binding, coordinator.bind(plan, file));
            assertEquals(config, plan.getAnalyzerConfigurations().get(config.kind()).orElseThrow());
        } finally { coordinator.shutdown(); }
    }
}
