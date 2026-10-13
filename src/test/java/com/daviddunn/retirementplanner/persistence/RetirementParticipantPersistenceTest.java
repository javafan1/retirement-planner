package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class RetirementParticipantPersistenceTest {
    @TempDir Path directory;
    private static final RetirementParticipantFacts FACTS = new RetirementParticipantFacts(
            RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2029, 1, 1),
            RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2030, 1, 1));

    @ParameterizedTest @EnumSource(StateTaxJurisdiction.class)
    void planScenarioCopyAndLegacyRoundTripsPreserveSafeFacts(StateTaxJurisdiction state) throws Exception {
        var plan = RetirementPlanFactory.createSinglePersonPlan();
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 1, 1));
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
        var account = new Traditional401K("Employer", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        account.setRetirementParticipantFacts(FACTS);
        plan.getAccountPortfolio().addAccount(account);
        var pension = new Pension("Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("1000"), BigDecimal.ZERO);
        pension.setRetirementParticipantFacts(FACTS);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var repository = new JsonRetirementPlanRepository();
        var path = directory.resolve("plan.json");
        repository.save(plan, path);
        var loaded = repository.load(path);
        var copied = new RetirementPlanScenarioCopyService().copy(plan);
        for (var result : java.util.List.of(loaded, copied)) {
            assertEquals(FACTS, result.getAccountPortfolio().getAccounts().getFirst().getRetirementParticipantFacts());
            assertEquals(FACTS, ((Pension) result.getHousehold().getPrimaryPerson().getIncomeSources().getFirst()).getRetirementParticipantFacts());
            assertEquals(state, result.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            assertNotSame(account, result.getAccountPortfolio().getAccounts().getFirst());
        }
        var mapper = new ObjectMapper();
        var tree = mapper.readTree(Files.readAllBytes(path));
        removeFacts(tree);
        Files.write(path, mapper.writeValueAsBytes(tree));
        var legacy = repository.load(path);
        assertEquals(RetirementParticipantFacts.UNKNOWN, legacy.getAccountPortfolio().getAccounts().getFirst().getRetirementParticipantFacts());
        assertEquals(RetirementParticipantFacts.UNKNOWN,
                ((Pension) legacy.getHousehold().getPrimaryPerson().getIncomeSources().getFirst()).getRetirementParticipantFacts());
        repository.save(legacy, path);
        assertEquals(RetirementParticipantFacts.UNKNOWN,
                repository.load(path).getAccountPortfolio().getAccounts().getFirst().getRetirementParticipantFacts());
    }

    private static void removeFacts(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof ObjectNode object) object.remove("retirementParticipantFacts");
        node.forEach(RetirementParticipantPersistenceTest::removeFacts);
    }

    @Test void factsDoNotAttachToIndividualIraAndMissingAccountFieldsAreUnknown() throws Exception {
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> ira.setRetirementParticipantFacts(FACTS));
        var mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ira);
        assertFalse(json.contains("retirementParticipantFacts"));
        assertEquals(RetirementParticipantFacts.UNKNOWN, mapper.readValue(json, Account.class).getRetirementParticipantFacts());
    }
}
