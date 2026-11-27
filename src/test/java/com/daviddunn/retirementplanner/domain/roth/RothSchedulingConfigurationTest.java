package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class RothSchedulingConfigurationTest {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    @Test
    void legacyWireShapeAndNullableRuleSurvive() throws Exception {
        for (var rule : new RothConversionStopRule[]{null, RothConversionStopRule.NEVER, RothConversionStopRule.FIRST_HOUSEHOLD_RMD}) {
            var legacy = new RothConversionRequest(true, 2030, new BigDecimal("5000.00"), rule,
                    RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL);
            String json = mapper.writeValueAsString(legacy);
            assertFalse(json.contains("scheduling"));
            var restored = mapper.readValue(json, RothConversionRequest.class);
            assertEquals(rule, restored.getStopRule());
            assertEquals(legacy.getAnnualAmount(), restored.getAnnualAmount());
            assertEquals(json, mapper.writeValueAsString(restored));
        }
    }

    @Test
    void explicitScheduleAndDisabledCopyRoundTrip() throws Exception {
        var original = request(true, 2040, RothRmdSchedulingPolicy.SKIP_POSITIVE_RMD_YEARS);
        var restored = mapper.readValue(mapper.writeValueAsString(original), RothConversionRequest.class);
        assertTrue(RothConversionConfiguration.same(original, restored));
        var disabled = RothConversionConfiguration.disabled(restored);
        assertFalse(disabled.isEnabled());
        assertEquals(original.getScheduling(), disabled.getScheduling());
        assertNull(disabled.getStopRule());
        assertFalse(RothConversionConfiguration.same(disabled,
                RothConversionConfiguration.disabled(request(true, 2041, RothRmdSchedulingPolicy.SKIP_POSITIVE_RMD_YEARS))));
        assertDoesNotThrow(() -> new RothConversionRequest(true, 2030, BigDecimal.ZERO, null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME, null,
                new RothConversionRequest.Scheduling(1, 2030, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR)));
        assertThrows(IllegalArgumentException.class, () -> new RothConversionRequest(true, 2030, BigDecimal.ZERO, null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME, null, original.getScheduling()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"schedulingVersion\":2,\"conversionEndYear\":2040,\"rmdSchedulingPolicy\":\"CONTINUE_THROUGH_END_YEAR\"}",
            "{\"schedulingVersion\":1,\"rmdSchedulingPolicy\":\"CONTINUE_THROUGH_END_YEAR\"}",
            "{\"schedulingVersion\":1,\"conversionEndYear\":2040,\"rmdSchedulingPolicy\":\"UNKNOWN\"}",
            "{\"schedulingVersion\":1,\"conversionEndYear\":2029,\"rmdSchedulingPolicy\":\"CONTINUE_THROUGH_END_YEAR\"}"})
    void malformedScheduleIsRejected(String scheduling) throws Exception {
        var legacy = mapper.writeValueAsString(new RothConversionRequest(false, 2030, BigDecimal.ZERO, null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> mapper.readValue(
                legacy.substring(0, legacy.length() - 1) + ",\"scheduling\":" + scheduling + "}", RothConversionRequest.class));
    }

    @Test
    void enabledVersionCannotExecuteOrReplaceLastAcceptedProjection() {
        var plan = com.daviddunn.retirementplanner.domain.projection.SpousalPostRbd401kOwnerExecutionTest.fixture(true, true);
        var controller = new ApplicationController();
        controller.newPlan(plan);
        controller.getCurrentProjection();
        var accepted = controller.getAcceptedProjectionSnapshot();
        var versioned = request(true, 2040, RothRmdSchedulingPolicy.PERMANENTLY_STOP_AT_FIRST_RMD);
        plan.setRothConversionRequest(versioned);
        controller.invalidateProjection();
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(assumptions.withStateTaxAssumptions(
                com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.simplified(
                        com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MI)));
        assertThrows(RothConversionRequest.SchedulingUnavailable.class, controller::getCurrentProjection);
        assertThrows(RothConversionRequest.SchedulingUnavailable.class, () -> new ProjectionEngine().projectWithOutcome(plan));
        assertThrows(RothConversionRequest.SchedulingUnavailable.class,
                () -> new ScheduledRothConversionPolicy().shouldExecuteConvert(versioned, 2041, false));
        assertSame(versioned, plan.getRothConversionRequest());
        assertSame(accepted, controller.getAcceptedProjectionSnapshot());
    }

    @Test
    void pendingCreationDefaultsHaveNoFingerprintAndExplicitYearsDo() {
        var plan = com.daviddunn.retirementplanner.domain.projection.SpousalPostRbd401kOwnerExecutionTest.fixture(true, true);
        String initial = CompleteProjectionSnapshot.freeze(plan);
        plan.initializeNewPlanRothSchedulingDraft();
        var draft = plan.getNewPlanRothSchedulingDraft();
        assertFalse(draft.isEnabled());
        assertEquals(2042, draft.getStartYear());
        assertEquals(2044, draft.getScheduling().conversionEndYear());
        assertEquals(RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR, draft.getScheduling().rmdSchedulingPolicy());
        assertEquals(initial, CompleteProjectionSnapshot.freeze(plan));
        plan.setRothConversionRequest(request(false, 2040, RothRmdSchedulingPolicy.SKIP_POSITIVE_RMD_YEARS));
        String explicit = CompleteProjectionSnapshot.freeze(plan);
        plan.setRothConversionRequest(request(false, 2041, RothRmdSchedulingPolicy.SKIP_POSITIVE_RMD_YEARS));
        assertNotEquals(explicit, CompleteProjectionSnapshot.freeze(plan));
    }

    @Test
    void inclusiveWindowAndPositiveToZeroPolicies() {
        var scheduler = new ScheduledRothConversionPolicy();
        for (var policy : RothRmdSchedulingPolicy.values()) {
            var config = request(true, 2040, policy);
            assertFalse(scheduler.shouldExecuteVersioned(config, 2029, false, false));
            assertTrue(scheduler.shouldExecuteVersioned(config, 2030, false, false));
            assertTrue(scheduler.shouldExecuteVersioned(config, 2040, false, false));
            assertFalse(scheduler.shouldExecuteVersioned(config, 2041, false, false));
            assertEquals(policy == RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR,
                    scheduler.shouldExecuteVersioned(config, 2031, true, false));
            assertEquals(policy != RothRmdSchedulingPolicy.PERMANENTLY_STOP_AT_FIRST_RMD,
                    scheduler.shouldExecuteVersioned(config, 2032, false, true));
        }
    }

    @Test
    void versionedContinuePaysRolloverYearMinimumAndUsesOnlyLivingOriginalIra() {
        var plan = com.daviddunn.retirementplanner.domain.projection.SpousalPostRbd401kOwnerExecutionTest.fixture(true, true);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2043, new BigDecimal("20000"), null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME, null,
                new RothConversionRequest.Scheduling(1, 2043, RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR)));
        var controller = new ApplicationController();
        controller.newPlan(plan);
        var projection = controller.getCurrentProjection();
        var year = projection.getYearAt(1);
        assertEquals(0, new BigDecimal("45496.77").compareTo(year.getRequiredMinimumDistribution()));
        assertEquals(0, new BigDecimal("20000").compareTo(year.getRothConversion()));
        assertEquals(0, new BigDecimal("392503.23").compareTo(year.getEndingAccountSnapshots().getFirst().getEndingBalance()));
        assertEquals(0, new BigDecimal("155000").compareTo(year.getEndingAccountSnapshots().get(1).getEndingBalance()));
        var frozen = controller.getAcceptedProjectionSnapshot();
        assertTrue(frozen.provenance().contains("FL-BASIC-ROTH-SCHEDULING-V1/CONTINUE_THROUGH_END_YEAR"));
        assertEquals(year.getRothConversion(), frozen.restoreProjection().getYearAt(1).getRothConversion());
    }

    private static RothConversionRequest request(boolean enabled, int end, RothRmdSchedulingPolicy policy) {
        return new RothConversionRequest(enabled, 2030, BigDecimal.ZERO, null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL, null,
                new RothConversionRequest.Scheduling(1, end, policy));
    }
}
