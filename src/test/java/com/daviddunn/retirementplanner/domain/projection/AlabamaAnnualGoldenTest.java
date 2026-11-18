package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlabamaAnnualGoldenTest {
    @Test
    void actualFiveAnnualExecutedConversionsUseIndependentConstantPremisesAndAccumulate() {
        var h=new Household(new Person("Primary","Example",LocalDate.of(1966,6,1)));
        var p=new AccountPortfolio();
        p.addAccount(new CheckingAccount("Cash taxes",AccountOwnership.PRIMARY,new BigDecimal("1000000")));
        p.addAccount(new TraditionalIRA("IRA",AccountOwnership.PRIMARY,new BigDecimal("10000000")));
        p.addAccount(new RothIRA("Roth",AccountOwnership.PRIMARY,BigDecimal.ZERO));
        var a=new PlanningAssumptions(BigDecimal.ZERO,BigDecimal.ZERO,5,LocalDate.of(2026,1,1));
        var plan=new RetirementPlan(h,p,new PlanningAssumptions(a.getEconomicAssumptions(),new TaxAssumptions(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,FilingStatus.SINGLE,new BigDecimal(".25")),
                a.getWithdrawalAssumptions(),new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),5,a.getProjectionStartDate(),
                StateTaxAssumptions.simplified(StateTaxJurisdiction.AL)));
        plan.setRothConversionRequest(new RothConversionRequest(true,2026,new BigDecimal("50000"),RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ANNUAL));
        var projection=new ProjectionEngine().project(plan);
        for(var y:projection.getYears()) {
            assertEquals(new BigDecimal("2069.00"),y.getStateIncomeTax());
            assertEquals(0,new BigDecimal("3820").compareTo(y.getFederalIncomeTax()));
            assertEquals(0,new BigDecimal("50000").compareTo(y.getRothConversion()));
            AlabamaBrokerageProjectionRegressionTest.assertCashConservation(y);
        }
        assertEquals(new BigDecimal("10345.00"),projection.getYears().stream().map(ProjectionYear::getStateIncomeTax).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(new BigDecimal("10000000"),p.getAccounts().get(1).getCurrentBalance());
    }
}
