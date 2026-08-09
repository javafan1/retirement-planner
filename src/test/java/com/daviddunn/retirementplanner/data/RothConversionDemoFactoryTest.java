package com.daviddunn.retirementplanner.data;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RothConversionDemoFactoryTest {

    @Test
    void createsRetirementPlan() {

        RetirementPlan plan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        assertNotNull(plan);
        assertNotNull(
                plan.getHousehold());
        assertNotNull(
                plan.getAccountPortfolio());
        assertNotNull(
                plan.getPlanningAssumptions());
        assertNotNull(
                plan.getRothConversionRequest());
    }

    @Test
    void projectsOneTimeRothConversion() {

        RetirementPlan plan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        ProjectionEngine engine =
                new ProjectionEngine();

        Projection projection =
                engine.project(plan);

        ProjectionYear firstYear =
                projection.getYearAt(0);

        ProjectionYear secondYear =
                projection.getYearAt(1);

        /*
         * 2026:
         *
         * The $50,000 Roth conversion should
         * execute in the first projection year.
         */
        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                firstYear
                                        .getRothConversion()));

        /*
         * 2027:
         *
         * The conversion is one-time, so it
         * should not execute again.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        secondYear
                                .getRothConversion()));
    }
}