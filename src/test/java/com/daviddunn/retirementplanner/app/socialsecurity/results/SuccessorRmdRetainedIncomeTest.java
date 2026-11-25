package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.RMD;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition.Treatment.MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1;
import static org.junit.jupiter.api.Assertions.*;

class SuccessorRmdRetainedIncomeTest {
    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void existingRetainedIncomeRowRebindsToTheSameAccountAndVerifiedDebit(AccountOwnership original) throws Exception {
        var survivor = original == PRIMARY ? SPOUSE : PRIMARY;
        var ira = new TraditionalIRA("Synthetic IRA", original, new BigDecimal("3000.00"));
        var other = new TraditionalIRA("Synthetic IRA", original, new BigDecimal("3000.00"));
        var identities = List.<com.daviddunn.retirementplanner.domain.financial.Account>of(ira, other);
        var ref = AccountReference.capture(ira);
        var births = Map.of(PRIMARY, LocalDate.of(1960, 6, 15), SPOUSE, LocalDate.of(1962, 6, 15));
        var household = new Household(new Person("Synthetic", "Primary", births.get(PRIMARY)),
                new Person("Synthetic", "Spouse", births.get(SPOUSE)));
        var deaths = EffectiveHouseholdDeathView.resolve(household, new DeathScenarioAssumptions(
                original == PRIMARY ? DeathScenario.PRIMARY_DIES : DeathScenario.SPOUSE_DIES, 2034), Optional.empty());
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getRmdRules();
        var ownership = RetirementOwnershipResolver.resolveForPlanning(Year.of(2037), LocalDate.of(2033, 1, 1), deaths,
                births, rules, List.of(ref),
                new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER), Set.of(ref));
        var disposition = ownership.accounts().get(ref);
        var debit = IncomeSourceAttribution.withdrawal(ira, new BigDecimal("2000.00"), RMD, CASH_FLOW).inYear(2037);
        var source = new RetainedProjectionSnapshot.Reference(0, ref.participantFacts());
        var row = new RetainedProjectionSnapshot.Income(RMD, CASH_FLOW, survivor, source, null, debit.description(),
                debit.grossAmount(), debit.grossAmount(), 2037, null, null, null, null, MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1);
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var wire = json.writeValueAsString(row);
        var read = json.readValue(wire, RetainedProjectionSnapshot.Income.class);
        assertEquals(wire, json.writeValueAsString(read));
        assertThrows(IllegalArgumentException.class, () -> read.restore(identities));
        var restored = read.restoreSuccessorRmd(identities, disposition, debit);
        assertEquals(survivor, restored.owner().orElseThrow());
        assertEquals(original, restored.account().orElseThrow().owner());
        assertSame(debit.account().orElseThrow(), restored.account().orElseThrow());
        assertEquals(debit.grossAmount(), restored.grossAmount());
        assertThrows(IllegalArgumentException.class,
                () -> read.restoreSuccessorRmd(List.of(other, ira), disposition, debit));
        for (var tampered : List.of(wire.replace("2000.00", "2001.00"),
                wire.replace("\"taxYear\":2037", "\"taxYear\":2038"),
                wire.replace("\"owner\":\"" + survivor.name(), "\"owner\":\"" + original.name()))) {
            var altered = json.readValue(tampered, RetainedProjectionSnapshot.Income.class);
            assertNotEquals(read, altered);
            assertThrows(IllegalArgumentException.class, () -> altered.restoreSuccessorRmd(identities, disposition, debit));
        }
        assertThrows(Exception.class, () -> json.readValue(
                wire.replace("MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1", "MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V2"),
                RetainedProjectionSnapshot.Income.class));

        var ordinary = new RetainedProjectionSnapshot.Income(RMD, CASH_FLOW, original, source, null, debit.description(),
                debit.grossAmount(), debit.grossAmount(), 2037, null);
        assertFalse(json.writeValueAsString(ordinary).contains("successorRmdTreatment"));
        assertEquals(debit, ordinary.restore(identities));
        // The retained row can round-trip, but pending history must never publish it as applied.
        var legacy = RetainedProjectionSnapshot.capture(new ProjectionEngine().project(BaselineDetailsTest.plan(true)));
        var legacyTree = json.valueToTree(legacy);
        ((com.fasterxml.jackson.databind.node.ArrayNode) legacyTree.path("years").get(0).path("sources")).add(json.readTree(wire));
        var refusal = assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> json.treeToValue(legacyTree, RetainedProjectionSnapshot.class));
        assertTrue(refusal.getMessage().contains("Successor RMD rows require an applied projection policy."));
    }
}
