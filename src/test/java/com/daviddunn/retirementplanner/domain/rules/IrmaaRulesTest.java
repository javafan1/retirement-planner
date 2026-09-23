package com.daviddunn.retirementplanner.domain.rules;

import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IrmaaRulesTest {

    @ParameterizedTest
    @EnumSource(value = FilingStatus.class, names = {"SINGLE", "MARRIED_FILING_JOINTLY"})
    void publishedThresholdsHaveExactlyOneCorrectTier(FilingStatus status) throws Exception {
        IrmaaRules rules = published();
        List<IrmaaBracket> tiers = tiers(rules, status);
        long[] thresholds = status == FilingStatus.SINGLE
                ? new long[]{109000, 137000, 171000, 205000, 500000}
                : new long[]{218000, 274000, 342000, 410000, 750000};

        assertTier(rules, status, BigDecimal.ZERO, tiers.getFirst());
        for (int i = 0; i < thresholds.length; i++) {
            BigDecimal threshold = BigDecimal.valueOf(thresholds[i]);
            assertEquals(0, threshold.compareTo(tiers.get(i).getMaximumModifiedAdjustedGrossIncome()));
            assertEquals(0, threshold.compareTo(tiers.get(i + 1).getMinimumModifiedAdjustedGrossIncome()));
            for (String offset : List.of("0.01", "0.000000000000000001")) {
                BigDecimal epsilon = new BigDecimal(offset);
                assertTier(rules, status, threshold.subtract(epsilon), tiers.get(i));
                assertTier(rules, status, threshold.add(epsilon), tiers.get(i + 1));
            }
            assertTier(rules, status, threshold, tiers.get(i == 4 ? i + 1 : i));
        }
        for (String income : List.of("10000000", "1000000000", "1E100")) {
            assertTier(rules, status, new BigDecimal(income), tiers.getLast());
        }
        assertNull(tiers.getLast().getMaximumModifiedAdjustedGrossIncome());
    }

    @Test
    void rejectsMalformedConfiguredCoverage() {
        assertInvalid("zero", List.of(bracket("1", null, true, false)));
        assertInvalid("zero", List.of(bracket("0", null, false, false)));
        assertInvalid("unbounded", List.of(bracket("0", "100", true, true)));
        assertInvalid("gap", List.of(bracket("0", "100", true, true), bracket("101", null, true, false)));
        assertInvalid("overlap", List.of(bracket("0", "100", true, true), bracket("99", null, false, false)));
        assertInvalid("boundary", List.of(bracket("0", "100", true, true), bracket("100", null, true, false)));
        assertInvalid("boundary", List.of(bracket("0", "100", true, false), bracket("100", null, false, false)));
        assertInvalid("ordered", List.of(bracket("0", "100", true, true), bracket("0", null, false, false)));
        assertInvalid("unbounded", List.of(bracket("0", null, true, false), bracket("100", null, true, false)));
    }

    @Test
    void allowsOnlyConfiguredStatusesAndComparesThresholdsNumerically() {
        IrmaaRules rules = new IrmaaRules(List.of(
                bracket("0", "100.0", true, true),
                bracket("100.00", null, false, false)));
        assertTier(rules, FilingStatus.SINGLE, new BigDecimal("100.000"), rules.getBrackets().getFirst());
        assertThrows(IllegalArgumentException.class,
                () -> rules.getBracket(FilingStatus.HEAD_OF_HOUSEHOLD, BigDecimal.ZERO));
    }

    @Test
    void rejectsEmptyIntervalsAndMissingEndpointSemantics() {
        assertThrows(IllegalArgumentException.class, () -> bracket("100", "100", false, true));
        assertThrows(IllegalArgumentException.class, () -> bracket("100", "99", true, true));
        assertThrows(NullPointerException.class, () -> new IrmaaBracket(
                FilingStatus.SINGLE, BigDecimal.ZERO, null, null, false, BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> bracket("0", null, true, true));
        assertThrows(IllegalArgumentException.class, () -> bracket("-1", null, true, false));
    }

    @Test
    void endpointSemanticsAndLookupSurviveJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        IrmaaRules original = published();
        String json = mapper.writeValueAsString(original);
        assertTrue(json.contains("\"minimumIncomeInclusive\""));
        assertTrue(json.contains("\"maximumIncomeInclusive\""));
        IrmaaRules restored = mapper.readValue(json, IrmaaRules.class);
        assertEquals(original.getBrackets().size(), restored.getBrackets().size());
        for (int i = 0; i < original.getBrackets().size(); i++) {
            IrmaaBracket before = original.getBrackets().get(i);
            IrmaaBracket after = restored.getBrackets().get(i);
            assertEquals(before.isMinimumIncomeInclusive(), after.isMinimumIncomeInclusive());
            assertEquals(before.isMaximumIncomeInclusive(), after.isMaximumIncomeInclusive());
            assertEquals(before.getMinimumModifiedAdjustedGrossIncome(), after.getMinimumModifiedAdjustedGrossIncome());
            assertEquals(before.getMaximumModifiedAdjustedGrossIncome(), after.getMaximumModifiedAdjustedGrossIncome());
            assertEquals(before.getMonthlyPartBPremium(), after.getMonthlyPartBPremium());
            assertEquals(before.getMonthlyPartDPremium(), after.getMonthlyPartDPremium());
        }
        assertSame(tiers(restored, FilingStatus.SINGLE).getLast(),
                restored.getBracket(FilingStatus.SINGLE, new BigDecimal("500000")));
        ObjectNode missingInclusion = (ObjectNode) mapper.readTree(json);
        ((ObjectNode) missingInclusion.withArray("brackets").get(0)).remove("minimumIncomeInclusive");
        ValueInstantiationException error = assertThrows(ValueInstantiationException.class,
                () -> mapper.treeToValue(missingInclusion, IrmaaRules.class));
        assertTrue(error.getMessage().contains("Minimum income inclusion is required"));
    }

    @Test
    void displayDistinguishesInclusiveAndExclusiveEndpoints() throws Exception {
        List<IrmaaBracket> tiers = tiers(published(), FilingStatus.SINGLE);
        assertTrue(tiers.getFirst().getDisplayRange().startsWith("Up to "));
        assertTrue(tiers.get(1).getDisplayRange().startsWith("Over "));
        assertTrue(tiers.get(1).getDisplayRange().contains(" through "));
        assertTrue(tiers.get(4).getDisplayRange().contains(" and under "));
        assertTrue(tiers.getLast().getDisplayRange().startsWith("At least "));
    }

    private static IrmaaRules published() throws Exception {
        return new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getIrmaaRules();
    }

    private static List<IrmaaBracket> tiers(IrmaaRules rules, FilingStatus status) {
        return rules.getBrackets().stream().filter(bracket -> bracket.getFilingStatus() == status).toList();
    }

    private static void assertTier(IrmaaRules rules, FilingStatus status, BigDecimal income, IrmaaBracket expected) {
        assertEquals(1, tiers(rules, status).stream().filter(bracket -> bracket.contains(income)).count(), income.toString());
        assertSame(expected, rules.getBracket(status, income), income.toString());
    }

    private static IrmaaBracket bracket(String minimum, String maximum, boolean minimumInclusive, boolean maximumInclusive) {
        return new IrmaaBracket(
                FilingStatus.SINGLE, new BigDecimal(minimum), maximum == null ? null : new BigDecimal(maximum),
                minimumInclusive, maximumInclusive, BigDecimal.ONE, BigDecimal.ZERO);
    }

    private static void assertInvalid(String reason, List<IrmaaBracket> brackets) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new IrmaaRules(brackets));
        assertTrue(error.getMessage().contains("Single"), error.getMessage());
        assertTrue(error.getMessage().contains(reason), error.getMessage());
    }
}
