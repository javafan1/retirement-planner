package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;
import com.daviddunn.retirementplanner.domain.projection.summary.IncomeSummaryService;
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
    private Projection currentProjection;
    private final ProjectionEngine projectionEngine;
    private final ProjectionSummaryService projectionSummaryService;
    private final RetirementPlanRepository repository;
    private boolean modified;

    private final JsonApplicationSettingsRepository
            applicationSettingsRepository;

    private final ApplicationSettings
            applicationSettings;

    public ApplicationController() {

        projectionEngine =
                new ProjectionEngine();

        projectionSummaryService =
                new ProjectionSummaryService(
                        new ProjectionStatisticsService(),
                        new IncomeSummaryService());

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

        if (currentProjection == null) {

            currentProjection =
                    projectionEngine.project(
                            currentPlan);
        }

        return currentProjection;
    }

    /**
     * New UI should use this.
     */
    public ProjectionSummary getCurrentProjectionSummary() {

        if (currentProjectionSummary == null) {

            currentProjectionSummary =
                    projectionSummaryService.summarize(
                            currentPlan,
                            getCurrentProjection());
        }

        return currentProjectionSummary;
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

    public void markModified() {

        modified = true;
    }

    public boolean isModified() {

        return modified;
    }

}