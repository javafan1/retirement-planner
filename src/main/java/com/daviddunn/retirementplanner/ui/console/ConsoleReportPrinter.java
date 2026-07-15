package com.daviddunn.retirementplanner.ui.console;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import com.daviddunn.retirementplanner.util.Money;

public class ConsoleReportPrinter {

    public void printRetirementPlan(RetirementPlan plan) {

        printHeader();

        printHousehold(plan.getHousehold());

        printPlanningAssumptions(plan.getPlanningAssumptions());

        printFooter();
    }

    public void printProjection(Projection projection) {

        printProjectionHeader();

        for (ProjectionYear year : projection.getYears()) {
            printProjectionYear(year);
        }

        printFooter();
    }

    // ----------------------------------------------------
    // Projection
    // ----------------------------------------------------

    private void printProjectionHeader() {

        System.out.println();
        System.out.println("Projection");
        System.out.println("========================================");
    }

    private void printProjectionYear(
            ProjectionYear year) {

        System.out.println();
        System.out.println(year.getCalendarYear());

        System.out.printf("%-30s %15s%n",
                "Beginning Assets",
                CurrencyFormatter.format(
                        year.getBeginningInvestableAssets()));

        System.out.printf("%-30s %15s%n",
                "Investment Growth",
                CurrencyFormatter.format(
                        year.getInvestmentGrowth()));

        System.out.printf("%-30s %15s%n",
                "Guaranteed Income",
                CurrencyFormatter.format(
                        year.getGuaranteedIncome()));

        System.out.printf("%-30s %15s%n",
                "Expenses",
                CurrencyFormatter.format(
                        year.getExpenses()));

        System.out.println("----------------------------------------");

        System.out.printf("%-30s %15s%n",
                "Ending Assets",
                CurrencyFormatter.format(
                        year.getEndingInvestableAssets()));
    }

    public void print(){
        this.printHeader();

       // this.printPerson();

        //this.printHousehold();

       // this.printHousehold();Summary()

    }
    public void printHeader() {

        System.out.println();
        System.out.println("Retirement Planner");
        System.out.println("==================");
        System.out.println();
    }

    private void printFooter() {

        System.out.println();
        System.out.println("========================================");
    }

    public void printPerson(Person person) {

        System.out.println(person.getFullName());

        System.out.println("-------------------------");

        System.out.println("Age: " + person.getAge());

        System.out.println();

        System.out.println("Accounts");

        for (Account account : person.getAccounts()) {

            System.out.printf(
                    "%-30s %15s%n",
                    account.getAccountName(),
                    CurrencyFormatter.format(account.getCurrentBalance()));
        }

        System.out.println();

        System.out.println("Net Worth");

        System.out.println(
                CurrencyFormatter.format(
                        person.getNetWorth()));

        System.out.println();
    }

    public void printHouseholdSummary(Household household) {

        System.out.println("==============================");

        System.out.println("Household Net Worth");

        System.out.println(
                CurrencyFormatter.format(
                        household.getNetWorth()));

        System.out.println();

        System.out.println("Accounts: "
                + household.getAccountCount());
    }

    private void printHousehold(Household household) {

        System.out.println();
        System.out.println("Retirement Planner");
        System.out.println("==================");
        System.out.println();

        System.out.println("Primary");
        System.out.println("-------");
        System.out.println("Name : " +
                household.getPrimaryPerson().getFullName());

        System.out.println("Age  : " +
                household.getPrimaryPerson().getAge());

        System.out.println();

        System.out.println("Spouse");
        System.out.println("------");
        System.out.println("Name : " +
                household.getSpouse().getFullName());

        System.out.println("Age  : " +
                household.getSpouse().getAge());




    }

    private void printPlanningAssumptions(
            PlanningAssumptions assumptions) {

        System.out.println();
        System.out.println("Planning Assumptions");
        System.out.println("----------------------------------------");

        System.out.printf(
                "%-30s %14.2f%%%n",
                "Investment Return",
                assumptions.getExpectedAnnualInvestmentReturn()
                        .multiply(Money.of("100")));

        System.out.printf(
                "%-30s %14.2f%%%n",
                "Inflation",
                assumptions.getExpectedAnnualInflationRate()
                        .multiply(Money.of("100")));

        System.out.println();
    }

}
