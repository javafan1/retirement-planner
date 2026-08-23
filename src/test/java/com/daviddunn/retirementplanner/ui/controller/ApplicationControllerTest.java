package com.daviddunn.retirementplanner.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationControllerTest {

    @Test
    void savesCurrentPlanAsBaseline() {

        ApplicationController controller =
                new ApplicationController();

        controller.saveCurrentAsBaseline(
                "Test Baseline");

        assertNotNull(
                controller
                        .getCurrentPlan()
                        .getBaseline());

        assertEquals(
                "Test Baseline",
                controller
                        .getCurrentPlan()
                        .getBaseline()
                        .getDescription());

        assertNotNull(
                controller
                        .getCurrentPlan()
                        .getBaseline()
                        .getSnapshot());



        assertTrue(
                controller.isModified());
    }
}