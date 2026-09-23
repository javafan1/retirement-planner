package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenControllerTest {
    @Test void twentyFiveYearSeventySixtyTwoVsSeventySeventyContextDoesNotChangeAnyMetric() {
        var controller = new ApplicationController();
        var plan = controller.getCurrentPlan();
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        primary.setFirstName("David"); primary.setBirthDate(LocalDate.of(1963, 6, 4));
        spouse.setFirstName("Lisa"); spouse.setBirthDate(LocalDate.of(1965, 2, 28));
        primary.setMortalityCategory(MortalityCategory.MALE); spouse.setMortalityCategory(MortalityCategory.FEMALE);
        primary.addIncomeSource(new SocialSecurityIncome("David SS", AccountOwnership.PRIMARY,
                LocalDate.of(2033, 6, 4), null, new BigDecimal("3000"), 70, BigDecimal.ZERO, 2027));
        spouse.addIncomeSource(new SocialSecurityIncome("Lisa SS", AccountOwnership.SPOUSE,
                LocalDate.of(2027, 2, 28), null, new BigDecimal("2000"), 62, BigDecimal.ZERO, 2027));
        plan.setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.04"), new BigDecimal("0.02"),
                25, LocalDate.of(2027, 1, 1)));
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Synthetic funding", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        controller.saveCurrentAsBaseline("Synthetic 70/62");
        controller.compareProjections();
        spouse.setIncomeSources(List.of(new SocialSecurityIncome("Lisa SS", AccountOwnership.SPOUSE,
                LocalDate.of(2035, 2, 28), null, new BigDecimal("2000"), 70, BigDecimal.ZERO, 2027)));
        controller.invalidateProjection();
        controller.compareProjections();
        var before = controller.getCachedBreakEvenAnalysis();
        var cachedBaseline = controller.getBaselineProjection();
        var cachedCurrent = controller.getCurrentProjection();
        var context = controller.prepareBreakEvenContext(before);
        assertEquals(3, context.events().size());
        assertEquals(1, context.events().stream().filter(e -> e.name().equals("David")).count());
        assertEquals(2, context.events().stream().filter(e -> e.name().equals("Lisa")).count());
        assertEquals(25, context.survival().size());
        assertEquals(before, controller.getCachedBreakEvenAnalysis());
        assertSame(cachedBaseline, controller.getBaselineProjection());
        assertSame(cachedCurrent, controller.getCurrentProjection());
        assertEquals(4, before.metrics().size());
    }
    @Test void mortalitySessionSettingsDoNotModifyPlanAndResetForDifferentPlan() throws Exception {
        var controller = new ApplicationController();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(controller.getCurrentPlan());
        long revision = controller.getSourcePlanRevision();
        var settings = new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings(
                LocalDate.of(2028, 3, 1),
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment.of(new BigDecimal("0.8")),
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment.standard());
        controller.setLongevitySessionSettings(settings);
        assertEquals(settings, controller.getLongevitySessionSettings());
        assertFalse(controller.isModified());
        assertEquals(revision, controller.getSourcePlanRevision());
        assertEquals(before, mapper.writeValueAsString(controller.getCurrentPlan()));
        controller.newPlan();
        assertEquals(BigDecimal.ONE, controller.getLongevitySessionSettings().primaryAdjustment().factor());
        assertEquals(controller.getCurrentPlan().getPlanningAssumptions().getProjectionStartDate(),
                controller.getLongevitySessionSettings().conditioningDate());
    }
    @Test void normalComparisonWorkflowCapturesIndependentElectionsAndDifferentHorizons() {
        ApplicationController controller = new ApplicationController();
        var plan = controller.getCurrentPlan();
        plan.getHousehold().getPrimaryPerson().setFirstName("David");
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1963, 6, 4));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new SocialSecurityIncome(
                "David SS", AccountOwnership.PRIMARY, LocalDate.of(2033, 6, 4), null,
                new BigDecimal("3000"), 70, BigDecimal.ZERO));
        plan.getHousehold().getSpouse().setFirstName("Lisa");
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1965, 2, 28));
        plan.getHousehold().getSpouse().addIncomeSource(income(62));
        plan.setPlanningAssumptions(new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                4, LocalDate.of(2027, 1, 1)));
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Funding", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        controller.saveCurrentAsBaseline("Lisa 62");
        controller.compareProjections(); // Existing comparison prepares both projections and asset results.
        var baseline = controller.getBaselineProjection();
        plan.getHousehold().getSpouse().setIncomeSources(List.of(income(70)));
        plan.setPlanningAssumptions(new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                10, LocalDate.of(2027, 1, 1)));
        controller.invalidateProjection();
        controller.compareProjections();
        var result = controller.getCachedBreakEvenAnalysis();
        assertEquals(62, result.baselineAssumptions().spouse().retirementClaimingAge());
        assertEquals(70, result.currentAssumptions().spouse().retirementClaimingAge());
        assertEquals(4, result.comparableYearCount());
        assertTrue(result.planningHorizonsDiffer());
        assertSame(baseline, controller.getBaselineProjection());
        assertTrue(result.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).finalDifference().signum() < 0);
    }

    @Test void savedClaimingAssumptionsStayFrozenAfterCurrentEditsAndJsonRoundTrip() throws Exception {
        var plan = RetirementPlanFactory.createEmptyPlan();
        var spouse = plan.getHousehold().getSpouse();
        spouse.setFirstName("Lisa");
        spouse.setBirthDate(LocalDate.of(1965, 2, 28));
        var original = income(62);
        spouse.addIncomeSource(original);
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "62"));
        spouse.replaceIncomeSource(original, income(70));
        spouse.setFirstName("Changed current name");
        assertEquals(62, BreakEvenPlanSummary.from(plan.getBaseline().getSnapshot().getHousehold()).spouse().retirementClaimingAge());
        assertEquals("Lisa", BreakEvenPlanSummary.from(plan.getBaseline().getSnapshot().getHousehold()).spouse().name());
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var loaded = mapper.readValue(mapper.writeValueAsBytes(plan), RetirementPlan.class);
        assertEquals(62, BreakEvenPlanSummary.from(loaded.getBaseline().getSnapshot().getHousehold()).spouse().retirementClaimingAge());
        assertEquals(70, BreakEvenPlanSummary.from(loaded.getHousehold()).spouse().retirementClaimingAge());
    }

    @Test void analysisReadsOnlyCachesAndDoesNotMutateOrProject() throws Exception {
        ApplicationController controller = new ApplicationController();
        set(controller, "projectionEngine", new ProjectionEngine() {
            @Override public Projection project(RetirementPlan plan) { throw new AssertionError("Analysis must not project"); }
        });
        assertNull(controller.getCachedBreakEvenAnalysis());
        RetirementPlan plan = controller.getCurrentPlan();
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1963, 6, 4));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1965, 2, 28));
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.FEMALE);
        plan.getHousehold().getSpouse().addIncomeSource(income(62));
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "Baseline"));
        var baselineSummary = BreakEvenPlanSummary.from(plan.getBaseline().getSnapshot().getHousehold());
        plan.getHousehold().getSpouse().setIncomeSources(List.of(income(70)));
        var currentSummary = BreakEvenPlanSummary.from(plan.getHousehold());
        Projection baseline = new Projection(); baseline.addYear(ProjectionYearBuilder.aProjectionYear().build());
        Projection current = new Projection(); current.addYear(ProjectionYearBuilder.aProjectionYear().withEndingInvestableAssets(100).build());
        set(controller, "baselineProjection", baseline);
        set(controller, "currentProjection", current);
        set(controller, "cachedBaselineIdentity", plan.getBaseline());
        set(controller, "baselineProjectionAssumptions", baselineSummary);
        set(controller, "currentProjectionAssumptions", currentSummary);
        set(controller, "baselineNonInvestableAssetProjections", List.of());
        set(controller, "currentNonInvestableAssetProjections", List.of());
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(plan);
        boolean modified = controller.isModified();
        long revision = controller.getSourcePlanRevision();
        var result = controller.getCachedBreakEvenAnalysis();
        controller.setLongevitySessionSettings(com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings
                .defaults(LocalDate.of(result.comparisonStartYear(), 1, 1)));
        var context = controller.prepareBreakEvenContext(result);
        var insight = controller.prepareBreakEvenInsight(result);
        assertEquals(3, insight.observations().size());
        assertEquals(context, controller.prepareBreakEvenContext(result));
        assertFalse(context.survival().isEmpty());
        assertEquals(62, result.baselineAssumptions().spouse().retirementClaimingAge());
        assertEquals(70, result.currentAssumptions().spouse().retirementClaimingAge());
        assertEquals(before, mapper.writeValueAsString(plan));
        assertEquals(modified, controller.isModified());
        assertEquals(revision, controller.getSourcePlanRevision());
        assertSame(current, get(controller, "currentProjection"));
        assertSame(baseline, get(controller, "baselineProjection"));
        controller.invalidateProjection();
        assertEquals(insight, controller.prepareBreakEvenInsight(result)); // Frozen prepared observations survive cache invalidation.
        assertNull(controller.getCachedBreakEvenAnalysis());
    }

    @Test void changingPlanCannotReusePreviousBaselineCache() throws Exception {
        ApplicationController controller = new ApplicationController();
        var plan = controller.getCurrentPlan();
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "Old baseline"));
        set(controller, "cachedBaselineIdentity", plan.getBaseline());
        set(controller, "baselineProjection", new Projection());
        set(controller, "baselineNonInvestableAssetProjections", List.of());
        controller.newPlan();
        assertNull(controller.getCachedBreakEvenAnalysis());
        assertNull(controller.getBaselineProjection());
        assertNull(get(controller, "baselineProjectionAssumptions"));
    }
    private static SocialSecurityIncome income(int age) {
        return new SocialSecurityIncome("SS", AccountOwnership.SPOUSE, LocalDate.of(1965 + age, 2, 28), null,
                new BigDecimal("2000"), age, BigDecimal.ZERO);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}
