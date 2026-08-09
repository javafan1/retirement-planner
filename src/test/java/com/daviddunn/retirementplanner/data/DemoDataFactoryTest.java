package com.daviddunn.retirementplanner.data;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DemoDataFactoryTest {

    @Test
    void createRetirementPlanWorks() {

        RetirementPlan plan =
                DemoDataFactory.createRetirementPlan();

        assertNotNull(plan);
        assertNotNull(plan.getHousehold());
        assertNotNull(plan.getAccountPortfolio());
        assertNotNull(plan.getPlanningAssumptions());

        System.out.println(
                "Demo plan created successfully.");

        System.out.println(
                "Accounts = "
                        + plan.getAccountPortfolio()
                        .getAccounts()
                        .size());

        System.out.println(
                "Projection years = "
                        + plan.getPlanningAssumptions()
                        .getProjectionLengthYears());
    }

//    @Test
//    void createDemoPlanProjectsSuccessfully() {
//
//        RetirementPlan plan =
//                DemoDataFactory.createRetirementPlan();
//
//        assertNotNull(plan);
//
//        ProjectionEngine engine =
//                new ProjectionEngine();
//
//        Projection projection =
//                engine.project(plan);
//
//        assertNotNull(projection);
//        assertFalse(
//                projection.getYears().isEmpty());
//    }
}