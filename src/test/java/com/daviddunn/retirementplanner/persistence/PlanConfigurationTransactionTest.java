package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanConfigurationTransactionTest {
    @TempDir Path directory;
    final JsonRetirementPlanRepository repository = new JsonRetirementPlanRepository();
    final AnalysisKind kind = AnalysisKind.COUPLE_QUICK;
    Path file() { return directory.resolve("plan.json"); }
    RetirementPlan save() throws Exception {
        var plan = RetirementPlanFactory.createEmptyPlan(); repository.save(plan, file()); return plan;
    }
    void update(JsonRetirementPlanRepository repo, RetirementPlan plan, AnalysisParameterConfiguration configuration) throws Exception {
        repo.registerParameters(file(), configuration);
        repo.saveParameters(file(), UUID.fromString(plan.getPlanId()), configuration);
    }
    @Test void exactUnrelatedTokensDirtyFinancialIsolationAndIndependentSlots() throws Exception {
        var plan = save();
        byte[] before = Files.readAllBytes(file());
        plan.getHousehold().getPrimaryPerson().setFirstName("UNSAVED");
        var configuration = ParameterFixtures.configuration(kind); update(repository, plan, configuration);
        byte[] after = Files.readAllBytes(file());
        assertArrayEquals(PlanConfigurationTransaction.replace(before, "analyzerConfigurations", null),
                PlanConfigurationTransaction.replace(after, "analyzerConfigurations", null));
        assertArrayEquals(before, Files.readAllBytes(repository.backupPath(file())));
        var loaded = repository.load(file());
        assertEquals("", loaded.getHousehold().getPrimaryPerson().getFirstName());
        assertEquals("UNSAVED", plan.getHousehold().getPrimaryPerson().getFirstName());
        assertEquals(configuration, loaded.getAnalyzerConfigurations().get(kind).orElseThrow());
        var second = ParameterFixtures.configuration(AnalysisKind.COUPLE_WEIGHTED); update(repository, plan, second);
        loaded = repository.load(file());
        assertEquals(configuration, loaded.getAnalyzerConfigurations().get(kind).orElseThrow());
        assertEquals(second, loaded.getAnalyzerConfigurations().get(second.kind()).orElseThrow());
    }
    @Test void exactNumericLexemesIncludingExponentAndTrailingZerosAreCopied() throws Exception {
        byte[] source = "{\"money\":123.450000,\"e\":1.00E+8,\"nested\":[-0.000,1e-9]}".getBytes();
        byte[] patched = PlanConfigurationTransaction.replace(source, "analyzerConfigurations", "{}".getBytes());
        String text = new String(patched);
        for (String token : List.of("123.450000", "1.00E+8", "-0.000", "1e-9")) assertTrue(text.contains(token));
        assertArrayEquals(source, PlanConfigurationTransaction.replace(patched, "analyzerConfigurations", null));
    }
    @Test void externalChangesAreRejectedByAutomaticAndOrdinarySave() throws Exception {
        var plan = save();
        Files.writeString(file(), Files.readString(file()) + "\n");
        byte[] external = Files.readAllBytes(file());
        assertThrows(IOException.class, () -> update(repository, plan, ParameterFixtures.configuration(kind)));
        assertThrows(IOException.class, () -> repository.save(plan, file()));
        assertArrayEquals(external, Files.readAllBytes(file()));
    }
    @Test void wrongIdentityUnboundPathAndHouseholdAreRejected() throws Exception {
        var plan = save(); var configuration = ParameterFixtures.configuration(kind);
        repository.registerParameters(file(), configuration);
        assertThrows(IOException.class, () -> repository.saveParameters(file(), UUID.randomUUID(), configuration));
        assertThrows(IOException.class, () -> update(new JsonRetirementPlanRepository(), plan, configuration));
        var individual = ParameterFixtures.configuration(AnalysisKind.INDIVIDUAL_WEIGHTED);
        assertThrows(IOException.class, () -> update(repository, plan, individual));
    }
    @Test void cooperativeExternalLockPreventsReplacement() throws Exception {
        var plan = save();
        try (var channel = FileChannel.open(directory.resolve("plan.json.rp-lock"), StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertThrows(IOException.class, () -> update(repository, plan, ParameterFixtures.configuration(kind)));
            assertThrows(IOException.class, () -> repository.save(plan, file()));
        }
    }
    @Test void newerRegisteredAcceptanceCannotBeOverwrittenByQueuedOldRun() throws Exception {
        var plan = save(); var old = ParameterFixtures.configuration(kind); var newer = ParameterFixtures.configuration(kind);
        repository.registerParameters(file(), old); repository.registerParameters(file(), newer);
        assertThrows(IOException.class, () -> repository.saveParameters(file(), UUID.fromString(plan.getPlanId()), old));
        repository.saveParameters(file(), UUID.fromString(plan.getPlanId()), newer);
        assertEquals(newer, repository.load(file()).getAnalyzerConfigurations().get(kind).orElseThrow());
    }
    @ParameterizedTest @EnumSource(value = AtomicFileWriter.Stage.class, names = {"CREATE", "WRITE", "FORCE", "CLOSE", "VERIFY", "BACKUP", "REPLACE"})
    void atomicFailurePreservesDurablePlanAndBackup(AtomicFileWriter.Stage failing) throws Exception {
        var plan = save(); update(repository, plan, ParameterFixtures.configuration(kind));
        byte[] original = Files.readAllBytes(file()); byte[] backup = Files.readAllBytes(repository.backupPath(file()));
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((stage, path) -> { if (stage == failing) throw new IOException("Injected transaction failure"); }), new ObjectMapper());
        faulty.load(file());
        assertThrows(IOException.class, () -> update(faulty, plan, ParameterFixtures.configuration(kind)));
        assertArrayEquals(original, Files.readAllBytes(file())); assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file())));
        assertNotNull(repository.loadBackup(file()));
    }
    @Test void modificationAtFinalReplaceGuardIsDetected() throws Exception {
        var plan = save();
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.REPLACE) Files.writeString(path, Files.readString(path) + "\n");
        }), new ObjectMapper());
        faulty.load(file());
        assertThrows(IOException.class, () -> update(faulty, plan, ParameterFixtures.configuration(kind)));
        assertNull(repository.load(file()).getAnalyzerConfigurations());
    }
    @Test void futureSchemaSurvivesManualSaveButCannotBeAutomaticallyOverwritten() throws Exception {
        var plan = save(); plan.setAnalyzerConfigurations(AnalysisParameterConfigurations.parse("{\"schemaVersion\":2,\"future\":1.2300}"));
        repository.save(plan, file()); byte[] before = Files.readAllBytes(file());
        assertThrows(IllegalStateException.class, () -> update(repository, plan, ParameterFixtures.configuration(kind)));
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertTrue(repository.load(file()).getAnalyzerConfigurations().json().contains("1.2300"));
    }
    @Test void unknownConfigurationMetadataIsNotTreatedAsFinancialStateConfiguration() throws Exception {
        var plan = save();
        plan.setAnalyzerConfigurations(com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations.parse(
                "{\"schemaVersion\":1,\"configurations\":{\"FUTURE_KIND\":{\"configurationVersion\":2,\"stateTaxAssumptions\":null,\"futureNumber\":1.2300}}}"));
        repository.save(plan, file());
        update(repository, plan, ParameterFixtures.configuration(kind));
        String restored = new JsonRetirementPlanRepository().load(file()).getAnalyzerConfigurations().json();
        assertTrue(restored.contains("\"stateTaxAssumptions\":null")); assertTrue(restored.contains("1.2300"));
    }
}
