package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionReadiness;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;
import com.daviddunn.retirementplanner.domain.projection.summary.IncomeSummaryService;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummaryService;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;


public class ApplicationController {

    private long sourcePlanRevision;
    private final java.util.List<Runnable> sourcePlanListeners = new java.util.ArrayList<>();

    public long getSourcePlanRevision() {
        return sourcePlanRevision;
    }

    /** UI-thread notification; the returned removal action belongs to the subscriber. */
    public Runnable addSourcePlanRevisionListener(Runnable listener) {
        sourcePlanListeners.add(java.util.Objects.requireNonNull(listener));
        return () -> sourcePlanListeners.remove(listener);
    }

    private void sourcePlanChanged() {
        sourcePlanRevision++;
        java.util.List.copyOf(sourcePlanListeners).forEach(Runnable::run);
    }

    private RetirementPlan currentPlan;
    private ProjectionSummary currentProjectionSummary;
    private Path currentFile;
    private Projection currentProjection;
    private Projection baselineProjection;
    private ProjectionBaseline cachedBaselineIdentity;
    private BreakEvenPlanSummary baselineProjectionAssumptions;
    private BreakEvenPlanSummary currentProjectionAssumptions;
    private List<NonInvestableAssetProjection> currentNonInvestableAssetProjections;
    private RetirementPlan longevitySettingsOwner;
    private com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings longevitySettings;

    public com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings getLongevitySessionSettings() {
        if (longevitySettingsOwner != currentPlan || longevitySettings == null) {
            longevitySettingsOwner = currentPlan;
            longevitySettings = com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings
                    .defaults(currentPlan.getPlanningAssumptions().getProjectionStartDate());
        }
        return longevitySettings;
    }

    public void setLongevitySessionSettings(com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings settings) {
        longevitySettingsOwner = currentPlan;
        longevitySettings = java.util.Objects.requireNonNull(settings);
    }

    public BreakEvenContext prepareBreakEvenContext(BreakEvenAnalysisResult result) {
        return new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory()
                .create(result, getLongevitySessionSettings());
    }

    private List<NonInvestableAssetProjection>
            baselineNonInvestableAssetProjections;

    private final BaselineProjectionService
            baselineProjectionService;

    private final ProjectionComparisonService
            projectionComparisonService;

    private final ProjectionEngine projectionEngine;
    private final ProjectionSummaryService projectionSummaryService;
    private final RetirementPlanRepository repository;
    private boolean modified;

    private final JsonApplicationSettingsRepository
            applicationSettingsRepository;

    private final ApplicationSettings
            applicationSettings;

    private final NonInvestableAssetProjectionService
            nonInvestableAssetProjectionService;

    public ApplicationController() {

        projectionEngine =
                new ProjectionEngine();

        projectionSummaryService =
                new ProjectionSummaryService(
                        new ProjectionStatisticsService(),
                        new IncomeSummaryService());

        nonInvestableAssetProjectionService =
                new NonInvestableAssetProjectionService();

        baselineProjectionService =
                new BaselineProjectionService(
                        projectionEngine);

        projectionComparisonService =
                new ProjectionComparisonService();

        repository =
                new JsonRetirementPlanRepository();

        applicationSettingsRepository =
                new JsonApplicationSettingsRepository();

        applicationSettings =
                applicationSettingsRepository.load();

        newPlan();
    }

    public List<NonInvestableAssetProjection>
    getCurrentNonInvestableAssetProjections() {

        if (currentNonInvestableAssetProjections != null) {
            return currentNonInvestableAssetProjections;
        }

        RetirementPlan plan =
                getCurrentPlan();

        Projection projection =
                getCurrentProjection();

        if (plan == null
                || projection == null) {

            return List.of();
        }

        List<ProjectionYear> years =
                projection.getYears();

        if (years.isEmpty()) {
            return List.of();
        }

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        currentNonInvestableAssetProjections = nonInvestableAssetProjectionService
                .project(
                        plan.getNonInvestableAssets(),
                        firstYear,
                        lastYear);
        return currentNonInvestableAssetProjections;
    }

    public RetirementPlan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * Existing UI can continue using this.
     */
    public Projection getCurrentProjection() {

        if (!isCurrentPlanReadyForProjection()) {
            return null;
        }

        if (currentProjection == null) {

            currentProjection =
                    projectionEngine.project(
                            currentPlan);
            currentProjectionAssumptions = BreakEvenPlanSummary.from(currentPlan.getHousehold());
        }

        return currentProjection;
    }

    /** Read-only cache lookup; never starts financial work on the JavaFX thread. */
    public Projection peekCurrentProjection() {
        return currentProjection;
    }

    public boolean isCurrentPlanReadyForProjection() {

        return ProjectionReadiness.isReady(
                currentPlan);
    }

    public Projection getBaselineProjection() {

        ProjectionBaseline selectedBaseline = currentPlan == null ? null : currentPlan.getBaseline();
        if (cachedBaselineIdentity != selectedBaseline) {
            baselineProjection = null;
            baselineNonInvestableAssetProjections = null;
            baselineProjectionAssumptions = null;
            cachedBaselineIdentity = selectedBaseline;
        }

        if (baselineProjection == null
                && currentPlan != null
                && currentPlan.getBaseline() != null) {

            baselineProjection =
                    baselineProjectionService
                            .projectBaseline(
                                    currentPlan.getBaseline());
            baselineProjectionAssumptions = BreakEvenPlanSummary.from(
                    selectedBaseline.getSnapshot().getHousehold());
        }

        return baselineProjection;
    }

    public List<NonInvestableAssetProjection>
    getBaselineNonInvestableAssetProjections() {

        Projection projection = getBaselineProjection();

        if (baselineNonInvestableAssetProjections != null) {
            return baselineNonInvestableAssetProjections;
        }

        if (projection == null) {
            return List.of();
        }

        List<ProjectionYear> years =
                projection.getYears();

        if (years.isEmpty()) {
            return List.of();
        }

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        baselineNonInvestableAssetProjections =
                nonInvestableAssetProjectionService
                        .project(
                                currentPlan
                                        .getBaseline()
                                        .getSnapshot()
                                        .getNonInvestableAssets(),
                                firstYear,
                                lastYear);

        return baselineNonInvestableAssetProjections;
    }

    private BreakEvenAnalysisResult insightSourceResult;
    private com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight cachedBreakEvenInsight;

    /** Reads only populated result caches. Opening an analysis never projects a plan. */
    public BreakEvenAnalysisResult getCachedBreakEvenAnalysis() {
        if (currentPlan == null || currentPlan.getBaseline() == null
                || cachedBaselineIdentity != currentPlan.getBaseline()
                || baselineProjection == null || currentProjection == null
                || baselineProjectionAssumptions == null || currentProjectionAssumptions == null
                || baselineNonInvestableAssetProjections == null || currentNonInvestableAssetProjections == null) {
            return null;
        }
        var result = new BreakEvenAnalyzer().analyze(
                new BreakEvenProjectionSnapshot(baselineProjection.getYears(),
                        baselineNonInvestableAssetProjections, baselineProjectionAssumptions),
                new BreakEvenProjectionSnapshot(currentProjection.getYears(),
                        currentNonInvestableAssetProjections, currentProjectionAssumptions));
        insightSourceResult = result;
        cachedBreakEvenInsight = new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService()
                .prepare(result, baselineProjection.getYears(), currentProjection.getYears());
        return result;
    }

    /** Observes the same populated projection caches; never invokes lazy projection getters. */
    public com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight prepareBreakEvenInsight(BreakEvenAnalysisResult result) {
        return result == insightSourceResult ? cachedBreakEvenInsight
                : new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(result);
    }

    public ProjectionComparison compareProjections() {

        if (currentPlan == null) {
            throw new IllegalStateException(
                    "No current plan.");
        }

        if (currentPlan.getBaseline() == null) {
            throw new IllegalStateException(
                    "No baseline projection exists.");
        }

        Projection baseline =
                getBaselineProjection();

        Projection current =
                getCurrentProjection();

        return projectionComparisonService.compare(
                baseline,
                getBaselineNonInvestableAssetProjections(),
                current,
                getCurrentNonInvestableAssetProjections());
    }

    public void saveCurrentAsBaseline(
            String description) {

        if (currentPlan == null) {
            throw new IllegalStateException(
                    "No current plan.");
        }

        ProjectionBaseline baseline =
                ProjectionBaselineFactory.create(
                        currentPlan,
                        description);

        currentPlan.setBaseline(
                baseline);

        baselineProjection = null;
        baselineNonInvestableAssetProjections = null;

        modified = true;
        sourcePlanChanged();
    }

    /**
     * New UI should use this.
     */
    public ProjectionSummary getCurrentProjectionSummary() {

        if (currentProjectionSummary == null) {

            Projection projection =
                    getCurrentProjection();

            if (projection == null) {
                return null;
            }

            currentProjectionSummary =
                    projectionSummaryService.summarize(
                            currentPlan,
                            projection);
        }

        return currentProjectionSummary;
    }


    public BigDecimal getNonInvestableAssetValue(
            int calendarYear) {

        List<NonInvestableAssetProjection> projections =
                getCurrentNonInvestableAssetProjections();

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(NonInvestableAssetProjection::getTotalValue)
                .orElse(BigDecimal.ZERO);
    }


    public void invalidateProjection() {
        projectionChanged();
    }

    public RetirementPlan newPlan() {

        currentPlan =
                RetirementPlanFactory.createEmptyPlan();
        sourcePlanChanged();

        currentFile = null;

        modified = false;

        projectionChanged();

        return currentPlan;
    }

    public RetirementPlan open(
            Path file)
            throws IOException {

        currentPlan =
                repository.load(file);
        sourcePlanChanged();
//
//        System.out.println(
//                "Primary Birth Date = "
//                        + currentPlan.getHousehold()
//                        .getPrimaryPerson()
//                        .getBirthDate());
//
//        System.out.println(
//                "Spouse Birth Date = "
//                        + currentPlan.getHousehold()
//                        .getSpouse()
//                        .getBirthDate());

        currentFile =
                file;

        modified = false;

        applicationSettings.setLastOpenedPlan(
                file.toString());

        applicationSettingsRepository.save(
                applicationSettings);

        projectionChanged();

        return currentPlan;
    }

    public boolean openLastPlan() {

//        System.out.println(
//                "Opening plan: "
//                        + applicationSettings.getLastOpenedPlan());

        if (!applicationSettings.isAutomaticallyOpenLastPlan()) {
            return false;
        }

        if (!applicationSettings.hasLastOpenedPlan()) {
            return false;
        }
//
//        System.out.println(
//                "Opening plan: "
//                        + applicationSettings.getLastOpenedPlan());

        try {

            open(Path.of(
                    applicationSettings.getLastOpenedPlan()));

//            System.out.println(
//                    "Primary Birth Date = "
//                            + currentPlan.getHousehold()
//                            .getPrimaryPerson()
//                            .getBirthDate());
//
//            System.out.println(
//                    "Spouse Birth Date = "
//                            + currentPlan.getHousehold()
//                            .getSpouse()
//                            .getBirthDate());

            return true;

        } catch (IOException ex) {

            applicationSettings.setLastOpenedPlan(null);

            applicationSettingsRepository.save(
                    applicationSettings);

            return false;
        }
    }

    public void save()
            throws IOException {

        if (currentFile == null) {

            throw new IllegalStateException(
                    "No file selected.");
        }

        repository.save(
                currentPlan,
                currentFile);

        modified = false;

        applicationSettings.setLastOpenedPlan(
                currentFile.toString());

        applicationSettingsRepository.save(
                applicationSettings);
    }

    public void saveAs(
            Path file)
            throws IOException {

        repository.save(
                currentPlan,
                file);

        currentFile =
                file;

        modified = false;

        applicationSettings.setLastOpenedPlan(
                file.toString());

        applicationSettingsRepository.save(
                applicationSettings);
    }

    public boolean hasCurrentFile() {
        return currentFile != null;
    }

    public Path getCurrentFile() {
        return currentFile;
    }

    private void projectionChanged() {

        currentProjection = null;
        currentProjectionSummary = null;
        currentProjectionAssumptions = null;
        currentNonInvestableAssetProjections = null;
    }

    public String getCurrentPlanName() {

        if (currentFile == null) {
            return "Untitled";
        }

        return currentFile
                .getFileName()
                .toString();
    }

    public String getCurrentPlanDisplayName() {

        if (currentFile == null) {
            return "Untitled";
        }

        return currentFile.getFileName().toString();
    }


    public BigDecimal getBaselineNonInvestableAssetValue(
            int calendarYear) {

        if (currentPlan == null
                || currentPlan.getBaseline() == null) {

            return null;
        }

        List<NonInvestableAssetProjection> projections =
                getBaselineNonInvestableAssetProjections();

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(
                        NonInvestableAssetProjection::
                                getTotalValue)
                .orElse(null);
    }


    public void markModified() {

        modified = true;
        sourcePlanChanged();
    }

    public boolean isModified() {

        return modified;
    }



}
