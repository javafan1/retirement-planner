package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class ProjectionChartFixtures {
    static ProjectionChartModel model(List<ProjectionYear> years,
            List<com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection> non,
            BreakEvenPlanSummary people) {
        return ProjectionChartModel.from(years, non, people, years.stream()
                .flatMap(y -> y.getEndingAccountSnapshots().stream())
                .map(ProjectedAccountSnapshot::getAccount).distinct().toList());
    }
    static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Account BROKERAGE = new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY, ZERO);
    private static final Account CASH = new CheckingAccount("Cash", AccountOwnership.PRIMARY, ZERO);
    private static final Account IRA = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, ZERO);
    private static final Account ROTH = new RothIRA("Roth", AccountOwnership.PRIMARY, ZERO);
    static ProjectionYear year(int year, long roth, long rmd) {
        var federal = new FederalTaxCalculation(ZERO, ZERO, ZERO, ZERO, BigDecimal.valueOf(9));
        var state = new MichiganTaxCalculation(ZERO, ZERO, ZERO, BigDecimal.valueOf(3));
        var ss = new HouseholdSocialSecurityResult(BigDecimal.valueOf(60), ZERO, ZERO, ZERO,
                SocialSecurityBenefitSelection.OWN, SocialSecurityBenefitSelection.NONE, BigDecimal.valueOf(60));
        var base = ProjectionYearBuilder.aProjectionYear().withCalendarYear(year).build();
        return new ProjectionYear(0, year, BigDecimal.valueOf(900), BigDecimal.valueOf(40), BigDecimal.valueOf(100),
                ss, BigDecimal.valueOf(80), BigDecimal.valueOf(10), BigDecimal.valueOf(20),
                BigDecimal.valueOf(999), BigDecimal.valueOf(100), BigDecimal.valueOf(rmd), ZERO,
                ZERO, ZERO, BigDecimal.valueOf(50), HouseholdCashSettlement.zero(), BigDecimal.valueOf(1000),
                List.of(snapshot(BROKERAGE, 200),
                        snapshot(CASH, 50),
                        snapshot(IRA, 400),
                        snapshot(ROTH, 300)),
                federal, state, base.getMedicarePremiumCalculation(), BigDecimal.valueOf(5),
                BigDecimal.valueOf(75000), BigDecimal.valueOf(roth), BigDecimal.valueOf(roth), ZERO,
                BigDecimal.valueOf(100), BigDecimal.valueOf(900), 80);
    }
    private static ProjectedAccountSnapshot snapshot(Account account, long value) {
        return new ProjectedAccountSnapshot(account, BigDecimal.valueOf(value));
    }
    static BreakEvenPlanSummary people() {
        return new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Alex", LocalDate.of(1963, 6, 4),
                70, LocalDate.of(2033, 6, 4), null), new BreakEvenPlanSummary.PersonSummary("Sam", LocalDate.of(1965, 2, 28),
                62, LocalDate.of(2029, 3, 7), null)); // Saved start date deliberately differs from DOB + age.
    }
}
