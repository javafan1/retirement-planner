package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaMississippiAnnualScheduleTest {
    private RetirementPlan plan(StateTaxJurisdiction state, boolean joint, DeathScenario death) {
        var h = new Household(new Person("Primary","Example",LocalDate.of(1966,6,1)),
                joint ? new Person("Spouse","Example",LocalDate.of(1955,6,1)) : null);
        var p = new AccountPortfolio();
        p.addAccount(new CheckingAccount("Tax cash",AccountOwnership.PRIMARY,new BigDecimal("1000000")));
        p.addAccount(new TraditionalIRA("IRA",AccountOwnership.PRIMARY,new BigDecimal("10000000")));
        p.addAccount(new RothIRA("Roth",AccountOwnership.PRIMARY,BigDecimal.ZERO));
        var a = new PlanningAssumptions(BigDecimal.ZERO,BigDecimal.ZERO,5,LocalDate.of(2026,1,1));
        return new RetirementPlan(h,p,new PlanningAssumptions(a.getEconomicAssumptions(),
                new TaxAssumptions(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                        joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE,new BigDecimal("0.25")),
                a.getWithdrawalAssumptions(),new DeathScenarioAssumptions(death,death==DeathScenario.BOTH_SURVIVE ? null : 2027,joint ? 67 : null),
                5,a.getProjectionStartDate(),StateTaxAssumptions.simplified(state)));
    }

    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void actualCashFundedConversionsMatchIndependentAnnualAndLifetimeWorksheets(StateTaxJurisdiction state) {
        var plan = plan(state,false,DeathScenario.BOTH_SURVIVE);
        var amount = new BigDecimal("50000");
        plan.setRothConversionRequest(new RothConversionRequest(true,2026,amount,RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ANNUAL));
        var result = new ProjectionEngine().project(plan);
        var values = state==StateTaxJurisdiction.OK ? List.of("1254.50","1254.50","1254.50","1254.50","1254.50")
                : List.of("0.00","0.00","0.00","0.00","0.00");
        for(int i=0;i<values.size();i++) {
            var y=result.getYearAt(i);
            assertEquals(new BigDecimal(values.get(i)),y.getStateIncomeTax());
            assertEquals(0,amount.compareTo(y.getRothConversion()));
            assertEquals(0,amount.compareTo(y.getAdjustedGrossIncome()));
            GeorgiaBrokerageProjectionRegressionTest.assertCashConservation(y);
        }
        assertEquals(new BigDecimal(state==StateTaxJurisdiction.OK ? "6272.50" : "0.00"),
                result.getYears().stream().map(ProjectionYear::getStateIncomeTax).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(new BigDecimal("10000000"),plan.getAccountPortfolio().getAccounts().get(1).getCurrentBalance());
    }

    @ParameterizedTest @EnumSource(value=DeathScenario.class,names={"PRIMARY_DIES","SPOUSE_DIES"})
    void bothSurvivorDirectionsUseRecipientAndAnnualFilingStatus(DeathScenario death) {
        for(var state:List.of(StateTaxJurisdiction.OK,StateTaxJurisdiction.MS)) {
            var plan = plan(state,true,death);
            // The independent survivor worksheet specifies pension only, with no RMD/conversion.
            for (var account : List.copyOf(plan.getAccountPortfolio().getAccounts())) {
                if (account.getType() != AccountType.CHECKING) plan.getAccountPortfolio().removeAccount(account);
            }
            plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1955,6,1));
            var owner = death==DeathScenario.PRIMARY_DIES ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
            var survivor = owner==AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
            // Independent K survivor worksheet: 100,000 private pension, one current recipient cap.
            var monthly = new BigDecimal("100000").divide(new BigDecimal("12"),40,RoundingMode.HALF_UP);
            plan.getHousehold().peopleByOwner().get(owner).addIncomeSource(new Pension("Ordinary survivor pension",owner,
                    LocalDate.of(2026,1,1),null,monthly,BigDecimal.ZERO,monthly));
            var result=new ProjectionEngine().project(plan);
            assertEquals(new BigDecimal(state==StateTaxJurisdiction.OK ? "2959.00" : "0.00"),result.getYearAt(1).getStateIncomeTax());
            assertEquals(new BigDecimal(state==StateTaxJurisdiction.OK ? "3504.50" : "0.00"),result.getYearAt(2).getStateIncomeTax());
            for(int i:List.of(1,2)) {
                var source=result.getYearAt(i).getIncomeSources().stream().filter(s->s.pension().isPresent()).findFirst().orElseThrow();
                assertEquals(survivor,source.owner().orElseThrow());
                assertEquals(owner,source.pension().orElseThrow().originalOwner());
            }
        }
    }
}
