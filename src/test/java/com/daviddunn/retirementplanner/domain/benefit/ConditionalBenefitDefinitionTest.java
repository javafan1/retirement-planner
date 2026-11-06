package com.daviddunn.retirementplanner.domain.benefit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitDefinitionTest {
    @Test void listAndDefinitionsAreImmutableAndIdentityIsNeverGeneratedByCopy() throws Exception {
        var benefit = monthly();
        var plan = plan(true);
        var supplied = new ArrayList<>(List.of(benefit));
        plan.setConditionalBenefits(supplied); supplied.clear();
        assertEquals(List.of(benefit), plan.getConditionalBenefits());
        assertThrows(UnsupportedOperationException.class, () -> plan.getConditionalBenefits().clear());
        var copy = JSON.readValue(JSON.writeValueAsBytes(benefit), ConditionalBenefitDefinition.class);
        assertEquals(benefit, copy);
        assertEquals(new BigDecimal("2500.12345678"), copy.grossAmount());
        assertEquals(new BigDecimal("0.0200"), copy.annualEscalationRate());
        assertTrue(JSON.valueToTree(benefit).at("/trigger/date").isTextual());
    }
    @Test void explicitAgeAndDeathConventionsDoNotPersistDerivedDates() throws Exception {
        var plan = plan(false);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 2, 29));
        var age = new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, 65);
        assertEquals(LocalDate.of(2025, 2, 28), age.fixedOrAgeDate(plan.getHousehold()).orElseThrow());
        assertEquals(LocalDate.of(2024, 2, 29), new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, 64)
                .fixedOrAgeDate(plan.getHousehold()).orElseThrow());
        assertEquals(LocalDate.of(2032, 1, 1), Trigger.deathEventDate(Year.of(2032)));
        var definition = change(monthly(), "trigger", age);
        plan.setConditionalBenefits(List.of(definition));
        assertNull(JSON.valueToTree(definition).at("/trigger/date").textValue());
        assertEquals(definition, JSON.readValue(JSON.writeValueAsBytes(definition), ConditionalBenefitDefinition.class));
        for (int boundary : new int[]{0, 120}) assertDoesNotThrow(() -> new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, boundary));
        for (int invalid : new int[]{-1, 121}) assertThrows(IllegalArgumentException.class, () -> new Trigger(TriggerType.AGE, null, PersonReference.PRIMARY, invalid));
        assertThrows(IllegalArgumentException.class, () -> new Trigger(TriggerType.DEATH, LocalDate.of(2030, 1, 1), PersonReference.PRIMARY, null));
    }
    @Test void roleValidationDistinguishesAbsentSpouseFromDeadPerson() {
        var death = new Trigger(TriggerType.DEATH, null, PersonReference.PRIMARY, null);
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "trigger", death));
        var survivor = change(change(monthly(), "recipient", PersonReference.SPOUSE), "trigger", death);
        survivor = change(survivor, "category", Category.DEATH_BENEFIT);
        var valid = survivor;
        assertDoesNotThrow(() -> plan(true).setConditionalBenefits(List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> plan(false).setConditionalBenefits(List.of(valid)));
        var spouseTrigger = change(monthly(), "trigger", new Trigger(TriggerType.AGE, null, PersonReference.SPOUSE, 75));
        assertThrows(IllegalArgumentException.class, () -> plan(false).setConditionalBenefits(List.of(spouseTrigger)));
        var couple = plan(true); couple.setConditionalBenefits(List.of(valid));
        assertThrows(IllegalArgumentException.class, () -> couple.setSpouse(null));
        assertTrue(couple.getHousehold().hasSpouse());
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "category", Category.DEATH_BENEFIT));
    }
    @Test void coverageAndMonthlyBoundsAreInclusiveAndLumpSumsHaveNoMonthlyFields() {
        var date = LocalDate.of(2032, 1, 1);
        var inclusive = change(change(monthly(), "coverageStart", date), "coverageEnd", date);
        assertDoesNotThrow(() -> plan(false).setConditionalBenefits(List.of(inclusive)));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "coverageStart", date.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "coverageEnd", date.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "endDate", date.minusDays(1)));
        var recurring = change(change(monthly(), "notBefore", date.plusMonths(1)), "endDate", date.plusMonths(1));
        assertDoesNotThrow(() -> plan(false).setConditionalBenefits(List.of(recurring)));
        assertThrows(IllegalArgumentException.class, () -> change(recurring, "endDate", date));
        var lump = change(change(monthly(), "annualEscalationRate", BigDecimal.ZERO), "paymentPattern", PaymentPattern.ONE_TIME_LUMP_SUM);
        assertDoesNotThrow(() -> plan(false).setConditionalBenefits(List.of(lump)));
        assertThrows(IllegalArgumentException.class, () -> change(lump, "notBefore", date));
        assertThrows(IllegalArgumentException.class, () -> change(lump, "endDate", date));
        assertThrows(IllegalArgumentException.class, () -> change(lump, "annualEscalationRate", new BigDecimal(".01")));
        var survivor = change(change(monthly(), "recipient", PersonReference.SPOUSE), "trigger", new Trigger(TriggerType.DEATH, null, PersonReference.PRIMARY, null));
        var covered = change(survivor, "coverageStart", date);
        assertDoesNotThrow(() -> change(covered, "coverageEnd", date));
        var impossible = change(survivor, "coverageStart", date.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> change(impossible, "coverageEnd", date.plusMonths(1)));
    }
    @ParameterizedTest @ValueSource(strings={"0", "0.1", "1000000000000", "0.12345678", "1E+12"})
    void acceptsBoundedAmounts(String value) { assertDoesNotThrow(() -> change(monthly(), "grossAmount", new BigDecimal(value))); }

    @Test void deathCoverageMustContainAPossibleBoundedEventBeforeRecurringTermination() {
        var survivor = change(change(monthly(), "recipient", PersonReference.SPOUSE), "trigger",
                new Trigger(TriggerType.DEATH, null, PersonReference.PRIMARY, null));
        assertDoesNotThrow(() -> change(survivor, "coverageStart", LocalDate.of(9999, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> change(survivor, "coverageStart", LocalDate.of(9999, 1, 2)));
        var futureCoverage = change(survivor, "coverageStart", LocalDate.of(2032, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> change(futureCoverage, "endDate", LocalDate.of(2032, 12, 31)));
        assertDoesNotThrow(() -> change(futureCoverage, "endDate", LocalDate.of(2033, 1, 1)));
        var lump = change(change(survivor, "annualEscalationRate", BigDecimal.ZERO), "paymentPattern", PaymentPattern.ONE_TIME_LUMP_SUM);
        lump = change(change(lump, "category", Category.DEATH_BENEFIT), "grossAmount", new BigDecimal("400000"));
        var finalLump = change(change(lump, "coverageStart", LocalDate.of(2032, 1, 1)), "coverageEnd", LocalDate.of(2032, 1, 1));
        assertDoesNotThrow(() -> plan(true).setConditionalBenefits(List.of(finalLump)));
        assertThrows(IllegalArgumentException.class, () -> plan(false).setConditionalBenefits(List.of(finalLump)));
    }
    @ParameterizedTest @ValueSource(strings={"-0.1", "1000000000001", "0.123456789", "1E+13", "1E-100", "1234567890123456789012345"})
    void rejectsUnsupportedAmounts(String value) { assertThrows(IllegalArgumentException.class, () -> change(monthly(), "grossAmount", new BigDecimal(value))); }
    @ParameterizedTest @ValueSource(strings={"-0.1", "1.00000001", "0.123456789", "1E-99"})
    void rejectsUnsupportedRates(String value) { assertThrows(IllegalArgumentException.class, () -> change(monthly(), "annualEscalationRate", new BigDecimal(value))); }
    @ParameterizedTest @ValueSource(strings={"0", "0.1", "1"})
    void acceptsBoundedRates(String value) { assertDoesNotThrow(() -> change(monthly(), "annualEscalationRate", new BigDecimal(value))); }
    @ParameterizedTest @ValueSource(strings={"id", "label", "category", "trigger", "recipient", "paymentPattern", "grossAmount", "annualEscalationRate", "escalationOrigin", "taxCharacter", "destination"})
    void missingAndNullCorePropertiesAreRejected(String field) {
        com.fasterxml.jackson.databind.node.ObjectNode node = JSON.valueToTree(monthly());
        node.remove(field);
        assertThrows(Exception.class, () -> JSON.treeToValue(node, ConditionalBenefitDefinition.class));
        node.putNull(field);
        assertThrows(Exception.class, () -> JSON.treeToValue(node, ConditionalBenefitDefinition.class));
    }
    @Test void closedSchemaRejectsCoercionsUnknownFieldsAndUnsupportedRolesOrTaxPromises() throws Exception {
        for (String field : List.of("trigger", "paymentPattern", "taxCharacter", "recipient", "destination", "category", "escalationOrigin"))
            assertThrows(IllegalArgumentException.class, () -> change(monthly(), field, "UNSUPPORTED"));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "grossAmount", "2500"));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "recipient", "JOINT"));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "taxCharacter", "TAX_FREE_INSURANCE"));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "unexpected", true));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "id", "1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class, () -> change(monthly(), "id", new UUID(0, 0)));
        var wire = JSON.writeValueAsString(monthly());
        assertThrows(Exception.class, () -> JSON.readValue(wire.replace("\"grossAmount\":", "\"grossAmount\":1,\"grossAmount\":"), ConditionalBenefitDefinition.class));
    }
    @Test void collectionLabelAndDateBudgetsHaveExplicitBoundaries() {
        assertDoesNotThrow(() -> change(monthly(), "label", "a".repeat(128)));
        for (String label : List.of(" ", "a".repeat(129), "line\nbreak")) assertThrows(IllegalArgumentException.class, () -> change(monthly(), "label", label));
        for (int year : new int[]{1000, 9999}) assertDoesNotThrow(() -> change(monthly(), "trigger", new Trigger(TriggerType.FIXED_DATE, LocalDate.of(year, 1, 1), null, null)));
        for (int year : new int[]{999, 10000}) assertThrows(IllegalArgumentException.class, () -> new Trigger(TriggerType.FIXED_DATE, LocalDate.of(year, 1, 1), null, null));
        var definitions = new ArrayList<ConditionalBenefitDefinition>();
        for (int i=1; i<=100; i++) definitions.add(change(monthly(), "id", new UUID(0, i)));
        var plan = plan(false); plan.setConditionalBenefits(definitions);
        definitions.add(change(monthly(), "id", new UUID(0, 101)));
        assertThrows(IllegalArgumentException.class, () -> plan.setConditionalBenefits(definitions));
        assertEquals(100, plan.getConditionalBenefits().size());
        assertThrows(IllegalArgumentException.class, () -> plan.setConditionalBenefits(List.of(monthly(), monthly())));
        assertThrows(NullPointerException.class, () -> plan.setConditionalBenefits(Arrays.asList(monthly(), null)));
    }
}
