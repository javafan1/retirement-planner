package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.io.IOException;
import java.nio.file.Path;

public interface RetirementPlanRepository {

    void save(RetirementPlan plan, Path file) throws IOException;

    RetirementPlan load(Path file) throws IOException;
}