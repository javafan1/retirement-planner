package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentRulesRepositoryTest {

    @Test
    void loads2026GovernmentRules() throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        assertNotNull(rules);

        assertEquals(
                "2026.1",
                rules.getRulesVersion());

        assertEquals(
                2026,
                rules.getTaxYear());

        assertEquals(
                5,
                rules.getFederalTaxRules().size());
    }

    @Test
    void loadsMarriedFilingJointlyRules()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        FederalTaxRules marriedRules =
                rules.getFederalTaxRules(
                        TaxFilingStatus
                                .MARRIED_FILING_JOINTLY);

        assertEquals(
                new BigDecimal("32200"),
                marriedRules.getStandardDeduction());

        assertEquals(
                7,
                marriedRules.getTaxBrackets().size());

        assertEquals(
                new BigDecimal("0.10"),
                marriedRules
                        .getTaxBrackets()
                        .get(0)
                        .getTaxRate());

        assertEquals(
                new BigDecimal("24800"),
                marriedRules
                        .getTaxBrackets()
                        .get(0)
                        .getUpperBound());

        assertNull(
                marriedRules
                        .getTaxBrackets()
                        .get(6)
                        .getUpperBound());
    }

    @Test
    void loadsSingleRules()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        FederalTaxRules singleRules =
                rules.getFederalTaxRules(
                        TaxFilingStatus.SINGLE);

        assertEquals(
                new BigDecimal("16100"),
                singleRules.getStandardDeduction());

        assertEquals(
                new BigDecimal("12400"),
                singleRules
                        .getTaxBrackets()
                        .get(0)
                        .getUpperBound());
    }
}