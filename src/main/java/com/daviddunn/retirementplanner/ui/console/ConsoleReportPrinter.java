package com.daviddunn.retirementplanner.ui.console;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;

public class ConsoleReportPrinter {

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

}
