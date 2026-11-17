package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaMississippiAuditCompletionTest {
    @Test
    void conversionCanRaiseTaxableSocialSecurityEvenWhenRemovedFromSeniorAgiTest() throws Exception {
        var income=new TaxIncome(b("0"),b("20000"),b("0"),b("40000"),b("0"),
                List.of(conversion(AccountOwnership.PRIMARY,"40000",2026)));
        var r=request(StateTaxJurisdiction.OK,2026,false,65,65,income);
        // Provisional income 50000 gives taxable SS 17000. Senior eligibility uses
        // 57000 AGI minus 40000 conversion = 17000, above 15000; no extra exemption.
        assertEquals(0,b("17000").compareTo(r.federal().getTaxableSocialSecurity()));
        OklahomaProductionTaxCalculatorTest.check("804.50",r);
        MississippiProductionTaxCalculatorTest.check("0.00",request(StateTaxJurisdiction.MS,2026,false,65,65,income));
    }

    @Test
    void jointStandardAndPersonalFloorsAndSocialOnly() throws Exception {
        for(var entry:Map.of("22200","0.00","22201","0.03").entrySet())
            OklahomaProductionTaxCalculatorTest.check(entry.getValue(),request(StateTaxJurisdiction.OK,2026,true,60,60,
                    new TaxIncome(b("0"),b("0"),b("0"),b("0"),b(entry.getKey()))));
        for(var state:List.of(StateTaxJurisdiction.OK,StateTaxJurisdiction.MS))
            assertEquals(b("0.00"),new ProductionStateTaxService().calculate(request(state,2026,false,60,60,
                    new TaxIncome(b("0"),b("50000"),b("0"),b("0"),b("0")))).incomeTax());
    }

    @Test
    void pensionAndOtherSpousesConversionUseIndependentPoolsAndRmdUsesSamePool() throws Exception {
        var pension=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.PENSION,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),Optional.empty(),Optional.empty(),"Ordinary private pension",
                b("50000"),Optional.of(b("50000")),OptionalInt.of(2026),Optional.empty());
        var income=new TaxIncome(b("50000"),b("0"),b("0"),b("50000"),b("0"),List.of(pension,conversion(AccountOwnership.SPOUSE,"50000",2026)));
        OklahomaProductionTaxCalculatorTest.check("2509.00",request(StateTaxJurisdiction.OK,2026,true,60,75,income));
        var rmd=IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA",AccountOwnership.PRIMARY,b("1000000")),
                b("30000"),IncomeSourceAttribution.Kind.RMD,IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var mixed=new TaxIncome(b("0"),b("0"),b("30000"),b("20000"),b("0"),List.of(rmd,conversion(AccountOwnership.PRIMARY,"20000",2026)));
        OklahomaProductionTaxCalculatorTest.check("1254.50",request(StateTaxJurisdiction.OK,2026,false,75,75,mixed));
        MississippiProductionTaxCalculatorTest.check("0.00",request(StateTaxJurisdiction.MS,2026,false,75,75,mixed));
    }

    @Test
    void actualCommercialAnnuityTwentyThousandTaxableOfFiftyThousandIsNotRetirementPlan() throws Exception {
        var date=LocalDate.of(2026,1,1);
        var definition=com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest.annuity(
                com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference.PRIMARY,
                date,LocalDate.of(2026,3,1),"5000","3000","30000");
        var plan=com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.plan(false);
        var view=com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.empty());
        var receipt=new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator().evaluate(List.of(definition),
                com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext.capture(plan.getHousehold(),view,date,2026)).receipts().getFirst();
        assertEquals(0,b("50000").compareTo(receipt.gross()));
        assertEquals(0,b("20000").compareTo(receipt.taxable()));
        // Exercise the actual package-private attribution boundary through a test-only bridge.
        var income=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("0"));
        var attached=new com.daviddunn.retirementplanner.domain.tax.OklahomaMississippiConditionalFixture().attach(income,receipt);
        OklahomaProductionTaxCalculatorTest.check("354.50",request(StateTaxJurisdiction.OK,2026,false,60,60,attached));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new MississippiProductionTaxCalculator().calculate(
                request(StateTaxJurisdiction.MS,2026,false,60,60,attached)).calculationStatus());
    }

    @Test
    void mississippiDeathYearAndLaterSingleInvestmentWorksheetUsesActualCalendarRate() throws Exception {
        var joint=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("100000"),List.of(
                MississippiProductionTaxCalculatorTest.investment(AccountOwnership.SPOUSE,"100000").inYear(2027)));
        // Both already 65 at Jan1 death: June 1961 DOB, not June 1962 turning 65 later.
        var deathYear=request(StateTaxJurisdiction.MS,2027,true,66,66,joint);
        var deathContext=deathYear.household().withActiveOwners(Set.of(AccountOwnership.SPOUSE));
        MississippiProductionTaxCalculatorTest.check("2640.00",new StateTaxCalculationRequest(2027,deathYear.filingStatus(),
                deathContext,joint,deathYear.federal(),deathYear.projectedRules(),deathYear.assumptions()));
        var single=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("100000"));
        var later=request(StateTaxJurisdiction.MS,2028,false,67,67,single);
        var survivorContext=new TaxHouseholdContext(LocalDate.of(2028,1,1),deathContext.birthDates(),
                deathContext.householdOwners(),Set.of(AccountOwnership.SPOUSE),Optional.empty());
        MississippiProductionTaxCalculatorTest.check("2807.00",new StateTaxCalculationRequest(2028,later.filingStatus(),
                survivorContext,single,later.federal(),later.projectedRules(),later.assumptions()));
        var beforeBirthday=request(StateTaxJurisdiction.MS,2026,true,65,71,
                joint.withIncomeSources(joint.getIncomeSources().stream().map(s->s.inYear(2026)).toList()));
        var earlyDeath=beforeBirthday.household().withActiveOwners(Set.of(AccountOwnership.SPOUSE));
        // Deceased June-1961 filer was 64 at Jan1 death; only survivor gets extra 1500.
        MississippiProductionTaxCalculatorTest.check("2876.00",new StateTaxCalculationRequest(2026,beforeBirthday.filingStatus(),
                earlyDeath,beforeBirthday.income(),beforeBirthday.federal(),beforeBirthday.projectedRules(),beforeBirthday.assumptions()));
    }
}
