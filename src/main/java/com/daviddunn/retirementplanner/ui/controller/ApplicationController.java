package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;

import java.io.IOException;
import java.nio.file.Path;

public class ApplicationController {

    private RetirementPlan currentPlan;
    private Projection currentProjection;
    private Path currentFile;

    private final ProjectionEngine projectionEngine;
    private final RetirementPlanRepository repository;

    public ApplicationController() {

        projectionEngine = new ProjectionEngine();
        repository = new JsonRetirementPlanRepository();

        newPlan();
    }

    public RetirementPlan getCurrentPlan() {
        return currentPlan;
    }

    public Projection getCurrentProjection() {

        if (currentProjection == null) {
            currentProjection =
                    projectionEngine.project(currentPlan);
        }

        return currentProjection;
    }

    public void invalidateProjection() {
        currentProjection = null;
    }

    public RetirementPlan newPlan() {

        currentPlan = RetirementPlanFactory.createEmptyPlan();
        //currentProjection = null;
        currentFile = null;
        projectionChanged();

        return currentPlan;
    }

    public RetirementPlan open(Path file)
            throws IOException {

        currentPlan = repository.load(file);
        //currentProjection = null;
        currentFile = file;

        projectionChanged();
        return currentPlan;
    }

    public void save()
            throws IOException {

        if (currentFile == null) {
            throw new IllegalStateException(
                    "No file selected.");
        }

        repository.save(currentPlan, currentFile);
    }

    public void saveAs(Path file)
            throws IOException {

        repository.save(currentPlan, file);
        currentFile = file;
    }

    public boolean hasCurrentFile() {
        return currentFile != null;
    }

    public Path getCurrentFile() {
        return currentFile;
    }

    private void projectionChanged() {
        currentProjection = null;
    }

}