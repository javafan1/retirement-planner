package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.rules.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentRulesRepositoryTest {

    //IrmaaRules irmaaRules = mew IrmaaRules();
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
                        FilingStatus
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
                        FilingStatus.SINGLE);

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

    @Test
    void birthYear1959StartsRmdAt73()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        int startingAge =
                rules.getRmdRules()
                        .getRmdStartingAge(1959);

        assertEquals(
                73,
                startingAge);
    }

    @Test
    void birthYear1960StartsRmdAt75()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        int startingAge =
                rules.getRmdRules()
                        .getRmdStartingAge(1960);

        assertEquals(
                75,
                startingAge);
    }

    @Test
    void age75HasDistributionPeriod24Point6()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        RmdLifeExpectancyFactor factor =
                rules.getRmdRules()
                        .getLifeExpectancyFactor(75);

        assertEquals(
                0,
                new BigDecimal("24.6")
                        .compareTo(
                                factor.getDistributionPeriod()));
    }

    @Test
    void rmdStartingAgeChangesAt1960BirthYear()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        assertEquals(
                73,
                rules.getRmdRules()
                        .getRmdStartingAge(1959));

        assertEquals(
                75,
                rules.getRmdRules()
                        .getRmdStartingAge(1960));
    }

    @Test
    void singleFilingStatusSelectsCorrectIrmaaBracket()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        IrmaaBracket bracket =
                rules.getIrmaaRules()
                        .getBracket(
                                FilingStatus.SINGLE,
                                new BigDecimal("150000"));

        assertEquals(
                FilingStatus.SINGLE,
                bracket.getFilingStatus());

        assertEquals(
                new BigDecimal("405.80"),
                bracket.getMonthlyPartBPremium());

        assertEquals(
                new BigDecimal("37.50"),
                bracket.getMonthlyPartDPremium());
    }

    @Test
    void singleFilingStatusSelectsTopIrmaaBracket()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        IrmaaBracket bracket =
                rules.getIrmaaRules()
                        .getBracket(
                                FilingStatus.SINGLE,
                                new BigDecimal("600000"));

        assertEquals(
                FilingStatus.SINGLE,
                bracket.getFilingStatus());

        assertEquals(
                new BigDecimal("689.90"),
                bracket.getMonthlyPartBPremium());

        assertEquals(
                new BigDecimal("91.00"),
                bracket.getMonthlyPartDPremium());
    }

    @Test
    void marriedFilingJointlyStillSelectsCorrectIrmaaBracket()
            throws Exception {

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        GovernmentRules rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        IrmaaBracket bracket =
                rules.getIrmaaRules()
                        .getBracket(
                                FilingStatus.MARRIED_FILING_JOINTLY,
                                new BigDecimal("300000"));

        assertEquals(
                FilingStatus.MARRIED_FILING_JOINTLY,
                bracket.getFilingStatus());

        assertEquals(
                new BigDecimal("405.80"),
                bracket.getMonthlyPartBPremium());

        assertEquals(
                new BigDecimal("37.50"),
                bracket.getMonthlyPartDPremium());
    }
}