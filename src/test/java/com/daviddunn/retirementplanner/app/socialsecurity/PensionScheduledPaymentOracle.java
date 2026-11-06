package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Independent payment schedule for the Stage 4 fixture; does not select survivor benefits. */
final class PensionScheduledPaymentOracle {
    private PensionScheduledPaymentOracle() { }

    static RetirementPlan plan(StateTaxJurisdiction state) {
        var plan = Stage4TestPlans.plan();
        var person = plan.getHousehold().getPrimaryPerson();
        var pension = (Pension) person.getIncomeSources().stream().filter(Pension.class::isInstance).findFirst().orElseThrow();
        person.removeIncomeSource(pension);
        person.addIncomeSource(new Pension("Owner scheduled 2030-2031", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), LocalDate.of(2031, 12, 31), new BigDecimal("1000"), new BigDecimal(".02")));
        // $500 x (1.02)^2 = $520.2000/month in 2032; later years retain the 2% COLA.
        plan.getHousehold().getSpouse().addIncomeSource(new Pension("Living recipient scheduled from 2032", AccountOwnership.SPOUSE,
                LocalDate.of(2032, 1, 1), null, new BigDecimal("520.2000"), new BigDecimal(".02")));
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
        return plan;
    }
}
