package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummaryService;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;

import java.io.IOException;
import java.nio.file.Path;

public class ApplicationController {

    private RetirementPlan currentPlan;
    private ProjectionSummary currentProjectionSummary;
    private Path currentFile;

    private final ProjectionEngine projectionEngine;
    private final ProjectionSummaryService projectionSummaryService;
    private final RetirementPlanRepository repository;

    private final JsonApplicationSettingsRepository
            applicationSettingsRepository;

    private final ApplicationSettings
            applicationSettings;

    public ApplicationController() {

        projectionEngine =
                new ProjectionEngine();

        projectionSummaryService =
                new ProjectionSummaryService(
                        new ProjectionStatisticsService());

        repository =
                new JsonRetirementPlanRepository();

        applicationSettingsRepository =
                new JsonApplicationSettingsRepository();

        applicationSettings =
                applicationSettingsRepository.load();

        newPlan();
    }

    public RetirementPlan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * Existing UI can continue using this.
     */
    public Projection getCurrentProjection() {

        return getCurrentProjectionSummary()
                .getProjection();
    }

    /**
     * New UI should use this.
     */
    public ProjectionSummary getCurrentProjectionSummary() {

        if (currentProjectionSummary == null) {

            Projection projection =
                    projectionEngine.project(currentPlan);

            currentProjectionSummary =
                    projectionSummaryService.summarize(
                            projection);
        }

        return currentProjectionSummary;
    }

    public void invalidateProjection() {
        currentProjectionSummary = null;
    }

    public RetirementPlan newPlan() {

        currentPlan =
                RetirementPlanFactory.createEmptyPlan();

        currentFile = null;

        projectionChanged();

        return currentPlan;
    }

    public RetirementPlan open(
            Path file)
            throws IOException {

        currentPlan =
                repository.load(file);

        currentFile =
                file;

        applicationSettings.setLastOpenedPlan(
                file.toString());

        applicationSettingsRepository.save(
                applicationSettings);

        projectionChanged();

        return currentPlan;
    }

    public boolean openLastPlan() {

        if (!applicationSettings.isAutomaticallyOpenLastPlan()) {
            return false;
        }

        if (!applicationSettings.hasLastOpenedPlan()) {
            return false;
        }

        try {

            open(Path.of(
                    applicationSettings.getLastOpenedPlan()));

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
        currentProjectionSummary = null;
    }
}