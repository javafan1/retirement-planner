package com.daviddunn.retirementplanner.app;

import com.daviddunn.retirementplanner.data.DemoDataFactory;
import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.console.ConsoleReportPrinter;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import com.daviddunn.retirementplanner.util.Money;
import com.daviddunn.retirementplanner.domain.financial.Account;

import java.io.IOException;
import java.nio.file.Path;

public class RetirementPlannerApplication {

    public void run() {

        RetirementPlan plan =
                DemoDataFactory.createRetirementPlan();

        ProjectionEngine projectionEngine =
                new ProjectionEngine();

        Projection projection =
                projectionEngine.project(plan);

        ConsoleReportPrinter printer = new ConsoleReportPrinter();
        printer.printProjection(projection);
        printer.printRetirementPlan(plan);

        RetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path file = Path.of("plan.json");

        try {
            repository.save(plan, file);

            RetirementPlan loadedPlan =
                    repository.load(file);

            System.out.println(loadedPlan.getHousehold()
                    .getHouseholdName());
        } catch (
                IOException e) {

            e.printStackTrace();
        }

    }


    
}