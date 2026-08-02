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

import java.math.BigDecimal;

public class ConsoleReportPrinter {

    public void printRetirementPlan(RetirementPlan plan) {

        printHeader();

        printHousehold(plan.getHousehold());

        printPlanningAssumptions(plan.getPlanningAssumptions());

        printFooter();
    }

    public void printProjection(Projection projection) {

        printProjectionHeader();

        for (int i = 0; i < projection.size(); i++) {
            printProjectionYear(projection.getYearAt(i));
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
        System.out.printf("Age: %d%n",
                year.getPrimaryPersonAge());

        printSection("Portfolio");

        printMoney(
                "Beginning Assets",
                year.getBeginningInvestableAssets());

        printMoney(
                "Investment Growth",
                year.getInvestmentGrowth());

        printMoney(
                "Ending Assets",
                year.getEndingInvestableAssets());

        printSection("Cash Flow");

        printMoney(
                "Guaranteed Income",
                year.getGuaranteedIncome());

        printMoney(
                "Portfolio Withdrawal",
                year.getPortfolioWithdrawal());

        printMoney(
                "Tax Funding Withdrawal",
                year.getTaxFundingWithdrawal());

        printMoney(
                "Required Minimum Distribution",
                year.getRequiredMinimumDistribution());

        printMoney(
                "Excess RMD",
                year.getExcessRmd());

        printMoney(
                "Cash Flow Need",
                year.getCashFlowNeed());

        printMoney(
                "Annual Expenses",
                year.getAnnualExpenses());

        printSection("Federal Tax");

        printMoney(
                "Adjusted Gross Income",
                year.getAdjustedGrossIncome());

        printMoney(
                "Taxable Social Security",
                year.getTaxableSocialSecurity());

        printMoney(
                "Federal Taxable Income",
                year.getFederalTaxableIncome());

        printMoney(
                "Federal Income Tax",
                year.getFederalIncomeTax());

        printSection("Michigan Tax");

        printMoney(
                "Michigan Income Tax",
                year.getMichiganIncomeTax());

        printSection("Total Taxes");

        printMoney(
                "Total Income Tax",
                year.getTotalIncomeTax());
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
                    account.getName(),
                    CurrencyFormatter.format(account.getCurrentBalance()));
        }

        System.out.println();

        System.out.println("Net Worth");

        System.out.println(
                CurrencyFormatter.format(
                        person.getNetWorth()));

        System.out.println();
    }

    public void printHouseholdSummary(RetirementPlan plan) {

        System.out.println("==============================");

//        System.out.println("Household Net Worth");

//        System.out.println(
//                CurrencyFormatter.format(
//                        household.getNetWorth()));

        System.out.println();

        System.out.println("Accounts: "
                + plan.getAccountPortfolio().getTotalBalance());
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
    private void printSection(String title) {

        System.out.println();
        System.out.println(title);
        System.out.println("----------------------------------------");
    }

    private void printMoney(
            String label,
            BigDecimal amount) {

        System.out.printf(
                "%-35s %15s%n",
                label,
                CurrencyFormatter.format(amount));
    }

}
