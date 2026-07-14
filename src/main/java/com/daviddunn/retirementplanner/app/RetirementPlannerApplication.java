package com.daviddunn.retirementplanner.app;

import com.daviddunn.retirementplanner.data.DemoDataFactory;
import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import com.daviddunn.retirementplanner.util.Money;
import com.daviddunn.retirementplanner.domain.financial.Account;

public class RetirementPlannerApplication {

    public void run() {

        RetirementPlan plan =
                DemoDataFactory.createRetirementPlan();

        Household household =
                plan.getHousehold();

        printHeader();

        printPerson(household.getPrimaryPerson());

        printPerson(household.getSpouse());

        printHouseholdSummary(household);

        printPlanningAssumptions(plan);

//        ProjectionEngine engine =
//                new ProjectionEngine();
//
//        ProjectionYear year =
//                engine.projectYear(plan, 2027);
//
//        printProjection(year);

       // ProjectionEngine engine = new ProjectionEngine();

        //Projection projection = engine.project(plan);

        //printer.print(projection);
    }

    private void printHeader() {

        System.out.println();
        System.out.println("==============================================");
        System.out.println("       Retirement Planner MVP");
        System.out.println("==============================================");
        System.out.println();
    }

    private void printPlanningAssumptions(
            RetirementPlan plan) {

        System.out.println();
        System.out.println("Planning Assumptions");
        System.out.println("------------------------------");

        System.out.printf(
                "Expected Inflation:        %s%%%n",
                plan.getPlanningAssumptions()
                        .getExpectedAnnualInflationRate()
                        .multiply(Money.of("100")));

        System.out.printf(
                "Expected Investment Return: %s%%%n",
                plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn()
                        .multiply(Money.of("100")));
    }

    // Existing methods

    private void printPerson(Person person) {

        System.out.println();
        System.out.println("========================================");
        System.out.println(person.getFullName());
        System.out.println("========================================");

        System.out.println("Birth Date: " + person.getBirthDate());

        System.out.println();

        System.out.println("Accounts");
        System.out.println("----------------------------------------");

        for (Account account : person.getAccounts()) {

            System.out.printf(
                    "%-30s %15s%n",
                    account.getAccountName(),
                    CurrencyFormatter.format(
                            account.getCurrentBalance()));
        }

        System.out.println();

        System.out.println("Income Sources");
        System.out.println("----------------------------------------");

        for (IncomeSource income : person.getIncomeSources()) {

            System.out.printf(
                    "%-30s %15s%n",
                    income.getName(),
                    CurrencyFormatter.format(
                            income.getAnnualIncome()));
        }

        System.out.println();

        System.out.printf("%-30s %15s%n",
                "Total Assets",
                CurrencyFormatter.format(
                        person.getTotalAssets()));

        System.out.printf("%-30s %15s%n",
                "Guaranteed Income",
                CurrencyFormatter.format(
                        person.getGuaranteedIncome()));
    }
//
private void printHouseholdSummary(Household household) {

    System.out.println();
    System.out.println("========================================");
    System.out.println("Household Summary");
    System.out.println("========================================");

    System.out.printf("%-30s %15s%n",
            "Total Assets",
            CurrencyFormatter.format(
                    household.getTotalAssets()));

    System.out.printf("%-30s %15s%n",
            "Total Liabilities",
            CurrencyFormatter.format(
                    household.getTotalLiabilities()));

    System.out.printf("%-30s %15s%n",
            "Net Worth",
            CurrencyFormatter.format(
                    household.getNetWorth()));

    System.out.printf("%-30s %15s%n",
            "Guaranteed Income",
            CurrencyFormatter.format(
                    household.getGuaranteedIncome()));
}

    private void printProjection(
            ProjectionYear projection) {

        System.out.println();
        System.out.println("========================================");
        System.out.println("Projection " + projection.getCalendarYear());
        System.out.println("========================================");

        System.out.printf("%-30s %15s%n",
                "Beginning Assets",
                CurrencyFormatter.format(
                        projection.getBeginningAssets()));

        System.out.printf("%-30s %15s%n",
                "Investment Growth",
                CurrencyFormatter.format(
                        projection.getInvestmentGrowth()));

        System.out.printf("%-30s %15s%n",
                "Guaranteed Income",
                CurrencyFormatter.format(
                        projection.getGuaranteedIncome()));

        System.out.printf("%-30s %15s%n",
                "Expenses",
                CurrencyFormatter.format(
                        projection.getExpenses()));

        System.out.println("----------------------------------------");

        System.out.printf("%-30s %15s%n",
                "Ending Assets",
                CurrencyFormatter.format(
                        projection.getEndingAssets()));
    }
}