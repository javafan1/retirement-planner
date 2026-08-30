package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationControllerTest {

    @Test
    void incompleteNewPlanDoesNotAttemptProjection() {

        ApplicationController controller =
                new ApplicationController();

        assertFalse(
                controller.isCurrentPlanReadyForProjection());

        assertDoesNotThrow(
                () -> assertNull(
                        controller.getCurrentProjection()));

        assertNull(
                controller.getCurrentProjectionSummary());
    }

    @Test
    void completingBirthDatesAllowsProjectionAndNewPlanClearsIt() {

        ApplicationController controller =
                new ApplicationController();

        controller.getCurrentPlan()
                .getHousehold()
                .getPrimaryPerson()
                .setBirthDate(LocalDate.of(1960, 1, 1));

        controller.getCurrentPlan()
                .getHousehold()
                .getSpouse()
                .setBirthDate(LocalDate.of(1962, 1, 1));

        assertTrue(
                controller.isCurrentPlanReadyForProjection());

        Projection completedPlanProjection =
                assertDoesNotThrow(
                        controller::getCurrentProjection);

        assertNotNull(completedPlanProjection);

        controller.newPlan();

        assertFalse(
                controller.isCurrentPlanReadyForProjection());

        assertNull(
                controller.getCurrentProjection());
    }

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
