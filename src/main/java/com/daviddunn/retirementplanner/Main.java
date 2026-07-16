package com.daviddunn.retirementplanner;

import com.daviddunn.retirementplanner.app.RetirementPlannerApplication;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {



        RetirementPlannerApplication app =
                new RetirementPlannerApplication();

        app.run();
    }
}
