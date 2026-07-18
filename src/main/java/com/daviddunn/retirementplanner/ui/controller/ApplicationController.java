package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;

import java.io.IOException;
import java.nio.file.Path;

public class ApplicationController {

    private RetirementPlan currentPlan;
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

    public void save() throws IOException {

        if (currentFile == null) {
            throw new IllegalStateException("No file selected.");
        }
        repository.save(currentPlan, currentFile);
    }
//    public void saveCurrentPlan() {
//
//        repository.save(currentPlan);
//    }

//    public void setCurrentPlan(RetirementPlan currentPlan) {
//        this.currentPlan = currentPlan;
//    }

    public RetirementPlan newPlan() {

        currentPlan = RetirementPlanFactory.createEmptyPlan();
        currentFile = null;

        return currentPlan;
    }
    public Path getCurrentFile() {
        return currentFile;
    }
}