package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.baseline.*;
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

    private RetirementPlan currentPlan;
    private ProjectionSummary currentProjectionSummary;
    private Path currentFile;
    private Projection currentProjection;
    private Projection baselineProjection;

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

        return nonInvestableAssetProjectionService
                .project(
                        plan.getNonInvestableAssets(),
                        firstYear,
                        lastYear);
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
        }

        return currentProjection;
    }

    public boolean isCurrentPlanReadyForProjection() {

        return ProjectionReadiness.isReady(
                currentPlan);
    }

    public Projection getBaselineProjection() {

        if (baselineProjection == null
                && currentPlan != null
                && currentPlan.getBaseline() != null) {

            baselineProjection =
                    baselineProjectionService
                            .projectBaseline(
                                    currentPlan.getBaseline());
        }

        return baselineProjection;
    }

    public List<NonInvestableAssetProjection>
    getBaselineNonInvestableAssetProjections() {

        if (baselineNonInvestableAssetProjections != null) {
            return baselineNonInvestableAssetProjections;
        }

        Projection projection =
                getBaselineProjection();

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
    }

    public boolean isModified() {

        return modified;
    }



}
