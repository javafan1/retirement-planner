
package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;

public class ProjectionEngine {


    public Projection project(RetirementPlan plan) {

        Projection projection = new Projection();

        BigDecimal beginningAssets =
                plan.getHousehold().getTotalAssets();

        int startYear = 2027;          // Temporary
        int years = 1;                 // MVP

        for (int i = 0; i < years; i++) {

            ProjectionYear year = calculateYear(
                    plan,
                    startYear + i,
                    beginningAssets);

            projection.addYear(year);

            beginningAssets = year.getEndingInvestableAssets();
        }

        return projection;
    }
//    public Projection project(RetirementPlan plan) {
//
//        Projection projection = new Projection();
//
//        projection.addYear(
//                projectYear(plan, 2027));
//
//        return projection;
 //   }

private ProjectionYear calculateYear(
        RetirementPlan plan,
        int calendarYear,
        BigDecimal beginningAssets) {

    Household household = plan.getHousehold();

    PlanningAssumptions assumptions =
            plan.getPlanningAssumptions();

    //BigDecimal beginningAssets =
     //       household.getTotalAssets();

    BigDecimal investmentGrowth =
            beginningAssets.multiply(
                    assumptions.getExpectedAnnualInvestmentReturn());

    BigDecimal guaranteedIncome =
            household.getGuaranteedIncome();

    BigDecimal expenses =
            household.getTotalAnnualExpenses();

    BigDecimal endingAssets =
            beginningAssets
                    .add(investmentGrowth)
                    .add(guaranteedIncome)
                    .subtract(expenses);

    return new ProjectionYear(
            calendarYear,
            beginningAssets,
            investmentGrowth,
            guaranteedIncome,
            expenses,
            endingAssets);

}

    private ProjectionYear projectYear(
            RetirementPlan plan,
            int calendarYear) {

        Household household = plan.getHousehold();

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        BigDecimal beginningAssets =
                household.getTotalAssets();

        BigDecimal investmentGrowth =
                beginningAssets.multiply(
                        assumptions.getExpectedAnnualInvestmentReturn());

        BigDecimal guaranteedIncome =
                household.getGuaranteedIncome();

        BigDecimal expenses =
                household.getTotalAnnualExpenses();

        BigDecimal endingAssets =
                beginningAssets
                        .add(investmentGrowth)
                        .add(guaranteedIncome)
                        .subtract(expenses);

        return new ProjectionYear(
                calendarYear,
                beginningAssets,
                investmentGrowth,
                guaranteedIncome,
                expenses,
                endingAssets);
    }
}


//package com.daviddunn.retirementplanner.domain.projection;

//import com.daviddunn.retirementplanner.domain.model.Household;
//import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
//
//import java.math.BigDecimal;

//public class ProjectionEngine {
//
//    public ProjectionYear projectYear(
//            RetirementPlan plan,
//            int year) {
//
//        Household household = plan.getHousehold();
//
//        BigDecimal beginningAssets =
//                household.getTotalAssets();
//
//        BigDecimal growthRate =
//                plan.getPlanningAssumptions()
//                        .getExpectedAnnualInvestmentReturn();
//
//        BigDecimal investmentGrowth =
//                beginningAssets.multiply(growthRate);
//
//        BigDecimal guaranteedIncome =
//                household.getGuaranteedIncome();
//
//        BigDecimal expenses =
//                household.getTotalAnnualExpenses();
//
//        BigDecimal endingAssets =
//                beginningAssets
//                        .add(investmentGrowth)
//                        .add(guaranteedIncome)
//                        .subtract(expenses);
//
//        return new ProjectionYear(
//                year,
//                beginningAssets,
//                investmentGrowth,
//                guaranteedIncome,
//                expenses,
//                endingAssets);
//    }
//}


//public class ProjectionEngine {

//    public Projection run(RetirementPlan plan) {
//
//        Projection projection = new Projection();
//
//        ...
//
//        return projection;
//    }
//}