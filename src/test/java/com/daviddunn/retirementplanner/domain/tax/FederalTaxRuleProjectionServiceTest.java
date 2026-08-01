package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FederalTaxRuleProjectionServiceTest {

    private FederalTaxRuleProjectionService service;
    private PlanningAssumptions planningAssumptions;
    private GovernmentRules governmentRules;
    private TaxParameterProjectionService projectionService;

    @BeforeEach
    void setUp() throws Exception {

        service =
                new FederalTaxRuleProjectionService();

        projectionService =
                new TaxParameterProjectionService();

        governmentRules =
                new GovernmentRulesRepository()
                        .load("/rules/government-rules-2026.json");

        planningAssumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.070"),
                        new BigDecimal("0.025"),
                        30,
                        LocalDate.of(2026, 1, 1));
    }

    @Test
    void projectsFederalTaxRules() {

        FederalTaxRules publishedRules =
                governmentRules.getFederalTaxRules(
                        FilingStatus.MARRIED_FILING_JOINTLY);

        FederalTaxRules projectedRules =
                service.project(
                        publishedRules,
                        governmentRules.getTaxYear(),
                        2027,
                        planningAssumptions);

        /*
         * Filing status should not change.
         */
        assertEquals(
                publishedRules.getFilingStatus(),
                projectedRules.getFilingStatus());

        /*
         * Standard deduction should be projected.
         */
        assertEquals(
                projected(
                        publishedRules.getStandardDeduction()),
                projectedRules.getStandardDeduction());

        List<FederalTaxBracket> publishedBrackets =
                publishedRules.getTaxBrackets();

        List<FederalTaxBracket> projectedBrackets =
                projectedRules.getTaxBrackets();

        assertEquals(
                publishedBrackets.size(),
                projectedBrackets.size());

        for (int i = 0;
             i < publishedBrackets.size();
             i++) {

            FederalTaxBracket published =
                    publishedBrackets.get(i);

            FederalTaxBracket projected =
                    projectedBrackets.get(i);

            /*
             * Lower bound should be projected.
             */
            assertEquals(
                    projected(
                            published.getLowerBound()),
                    projected.getLowerBound());

            /*
             * Upper bound should be projected
             * unless this is the highest bracket.
             */
            if (published.hasUpperBound()) {

                assertEquals(
                        projected(
                                published.getUpperBound()),
                        projected.getUpperBound());

            } else {

                assertNull(
                        projected.getUpperBound());
            }

            /*
             * Tax rates never change due to inflation.
             */
            assertEquals(
                    published.getTaxRate(),
                    projected.getTaxRate());
        }
    }

    private BigDecimal projected(
            BigDecimal value) {

        return projectionService.project(
                value,
                governmentRules.getTaxYear(),
                2027,
                planningAssumptions);
    }
}