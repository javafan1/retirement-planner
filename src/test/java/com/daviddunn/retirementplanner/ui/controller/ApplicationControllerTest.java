package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationControllerTest {

    @Test
    void sourceRevisionCoversModificationReplacementAndListenerRemoval() {
        var controller = new ApplicationController();
        long initial = controller.getSourcePlanRevision();
        var notifications = new java.util.ArrayList<Long>();
        Runnable detach = controller.addSourcePlanRevisionListener(
                () -> notifications.add(controller.getSourcePlanRevision()));
        controller.markModified();
        controller.newPlan();
        assertEquals(java.util.List.of(initial + 1, initial + 2), notifications);
        detach.run();
        controller.markModified();
        assertEquals(2, notifications.size());
    }
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

        controller.getCurrentPlan().getAccountPortfolio().addAccount(
                new BrokerageAccount(
                        "Projection funding account",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000")));

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
