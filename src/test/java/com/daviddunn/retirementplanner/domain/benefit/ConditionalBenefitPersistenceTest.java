package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.app.baseline.ProjectionInputs;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitPersistenceTest {
    @TempDir Path directory;

    @Test void absentNullAndEmptyUseExactlyTheLegacyOmittedWireShape() throws Exception {
        var plan = plan(true);
        String legacy = JSON.writeValueAsString(plan);
        String frozen = CompleteProjectionSnapshot.freeze(plan);
        assertFalse(legacy.contains("conditionalBenefits"));
        assertFalse(frozen.contains("conditionalBenefits"));
        for (var value : List.of(JSON.nullNode(), JSON.createArrayNode())) {
            ObjectNode document = JSON.readValue(legacy, ObjectNode.class);
            document.set("conditionalBenefits", value);
            var loaded = JSON.treeToValue(document, RetirementPlan.class);
            assertTrue(loaded.getConditionalBenefits().isEmpty());
            assertEquals(legacy, JSON.writeValueAsString(loaded));
            assertEquals(frozen, CompleteProjectionSnapshot.freeze(loaded));
        }
        assertEquals(frozen, CompleteProjectionSnapshot.freeze(JSON.readValue(legacy, RetirementPlan.class)));
        var snapshot = JSON.readValue(frozen, RetirementPlanSnapshot.class);
        assertTrue(snapshot.getConditionalBenefits().isEmpty());
        ObjectNode node = JSON.readValue(frozen, ObjectNode.class);
        node.putNull("conditionalBenefits");
        assertEquals(frozen, CompleteProjectionSnapshot.freeze(JSON.treeToValue(node, RetirementPlanSnapshot.class)));
    }

    @Test void repositoryAndAllPureCopiesRetainDefinitionsAndIndependentCandidateInputs() throws Exception {
        for (boolean couple : new boolean[]{false, true}) {
            var original = plan(couple);
            original.setConditionalBenefits(List.of(monthly()));
            var baseline = ProjectionBaselineFactory.create(original, "Synthetic baseline");
            original.setBaseline(baseline);
            var changed = change(monthly(), "grossAmount", new BigDecimal("4000.12345678"));
            original.setConditionalBenefits(List.of(changed));
            var path = directory.resolve(couple + ".json");
            var repository = new JsonRetirementPlanRepository();
            repository.save(original, path);
            var loaded = repository.load(path);
            assertEquals(original.getConditionalBenefits(), loaded.getConditionalBenefits());
            assertEquals(List.of(monthly()), loaded.getBaseline().getSnapshot().getConditionalBenefits());
            var copied = new RetirementPlanScenarioCopyService().copy(loaded);
            assertEquals(List.of(changed), copied.getConditionalBenefits());
            assertEquals(List.of(changed), RetirementPlanSnapshot.fromRetirementPlan(copied).getConditionalBenefits());
            var frozen = CompleteProjectionSnapshot.freeze(copied);
            assertEquals(List.of(changed), CompleteProjectionSnapshot.restoreInputs(frozen).getConditionalBenefits());
            assertEquals(ProjectionInputs.capture(copied), ProjectionInputs.capture(CompleteProjectionSnapshot.restoreInputs(frozen)));
            assertThrows(UnsupportedOperationException.class, () -> baseline.getSnapshot().getConditionalBenefits().clear());
            var settings = MonteCarloSettings.forPlan(copied, 2, 417, BigDecimal.ZERO);
            for (var mode : MonteCarloMode.values()) {
                var captured = MonteCarloStrategyComparisonRunService.capture(copied, settings, mode, "1", "1", "67", "67");
                var inputs = MonteCarloFingerprintService.comparison(captured.request());
                assertEquals(List.of(changed), JSON.readValue(inputs.request().planA(), RetirementPlan.class).getConditionalBenefits());
                assertEquals(List.of(monthly()), JSON.readValue(inputs.request().planB(), RetirementPlan.class).getConditionalBenefits());
                assertTrue(new MonteCarloFingerprintService().assess(inputs).valid());
            }
            copied.setConditionalBenefits(List.of());
            assertEquals(List.of(changed), loaded.getConditionalBenefits());
            assertEquals(List.of(monthly()), baseline.getSnapshot().getConditionalBenefits());
        }
    }

    @Test void malformedNonemptyPlanContentIsNeverLoadedAsEmpty() throws Exception {
        var plan = plan(true); plan.setConditionalBenefits(List.of(monthly()));
        ObjectNode source = JSON.valueToTree(plan);
        for (String field : List.of("trigger", "taxCharacter", "recipient", "paymentPattern", "destination", "id")) {
            var node = source.deepCopy();
            ((ObjectNode) node.at("/conditionalBenefits/0")).put(field, "UNKNOWN");
            assertThrows(Exception.class, () -> JSON.treeToValue(node, RetirementPlan.class));
        }
        var node = source.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) node.get("conditionalBenefits")).add(JSON.valueToTree(monthly()));
        assertThrows(Exception.class, () -> JSON.treeToValue(node, RetirementPlan.class));
        for (String invalid : List.of("{}", "[null]", "[{}]", "false", "123", "\"invalid\"")) {
            var malformed = source.deepCopy(); malformed.set("conditionalBenefits", JSON.readTree(invalid));
            var file = directory.resolve("malformed.json"); JSON.writeValue(file.toFile(), malformed);
            assertThrows(Exception.class, () -> new JsonRetirementPlanRepository().load(file));
        }
        var unknown = source.deepCopy(); unknown.put("unknownRoot", true);
        assertThrows(Exception.class, () -> JSON.treeToValue(unknown, RetirementPlan.class));
    }

    @Test void verifiedDeterministicBaselinePreservesAndExecutesItsOwnDefinitions() {
        var plan = plan(false);
        var benefit = change(monthly(),"trigger",new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.Trigger(
                com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.TriggerType.FIXED_DATE,java.time.LocalDate.of(2026,1,1),null,null));
        plan.setConditionalBenefits(List.of(benefit));
        var baseline = ProjectionBaselineFactory.create(plan,"Frozen benefit definition");
        plan.setConditionalBenefits(List.of());
        var result = new BaselineProjectionService(new ProjectionEngine()).projectBaseline(baseline);
        assertEquals(benefit.id(),result.getYearAt(0).getIncomeSources().stream().filter(s->s.conditionalBenefit().isPresent())
                .findFirst().orElseThrow().conditionalBenefit().orElseThrow().benefitId());
    }
}
