package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaMississippiQualificationBoundaryTest {
    @Test
    void clearlyUnqualifiedEarlyIraAndAlreadyQualifiedEmployerAreNotAmbiguous() throws Exception {
        var ira=new TraditionalIRA("Early IRA",AccountOwnership.PRIMARY,b("100000"));
        // The model intentionally permits participant facts only on employer plans.
        // Ordinary own-IRA early qualification uses DOB/receipt bounds, not invented IRA facts.
        var early=IncomeSourceAttribution.withdrawal(ira,b("50000"),IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var earlyIncome=new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),List.of(early));
        MississippiProductionTaxCalculatorTest.check("1268.00",request(StateTaxJurisdiction.MS,2026,false,50,50,earlyIncome));
        var employer=new Traditional401K("Qualified plan",AccountOwnership.PRIMARY,b("100000"));
        employer.setRetirementParticipantFacts(new RetirementParticipantFacts(null,null,
                RetirementParticipantFacts.RetirementConditionsStatus.MET,LocalDate.of(2025,1,1)));
        var payment=IncomeSourceAttribution.withdrawal(employer,b("50000"),IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var qualified=new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),List.of(payment));
        MississippiProductionTaxCalculatorTest.check("0.00",request(StateTaxJurisdiction.MS,2026,false,59,59,qualified));
    }

    private ProductionStateTaxResult calculate(StateTaxJurisdiction state,TaxIncome income) throws Exception {
        return new ProductionStateTaxService().calculate(request(state,2026,false,60,60,income));
    }

    @Test
    void governmentCoordinationLegalWorksheetsAreNotProductionPromises() {
        var rules=new OklahomaTaxRules();
        var status=com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE;
        // K OK-G1..G3: independently specified statutory A3/A5/A6 coordination.
        assertEquals(b("624.50"),rules.tax(2026,status,b("36000").subtract(b("10000")).subtract(b("7350"))));
        assertEquals(b("804.50"),rules.tax(2026,status,b("80000").subtract(b("50000")).subtract(b("7350"))));
        assertEquals(b("2604.50"),rules.tax(2026,status,b("80000").subtract(b("10000")).subtract(b("7350"))));
        // K OK-22/MS-21: a verified qualified-plan taxable fraction has these worksheet bases;
        // the current commercial-annuity representation does not establish that qualification.
        assertEquals(b("0.00"),rules.tax(2026,status,b("20000").subtract(b("10000")).subtract(b("7350")).max(b("0"))));
        assertEquals(b("468.00"),new com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiMarriedWorksheet()
                .tax(2026,status,Map.of(AccountOwnership.PRIMARY,b("30000")),b("0"),b("6000")));
    }

    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void unclassifiedSpecialBenefitsAndPartialPensionBasisDoNotBecomeOrdinaryByName(StateTaxJurisdiction state) throws Exception {
        var special=IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.OTHER,IncomeSourceAttribution.Purpose.INCOME,
                "Represented unclassified special retirement benefit",b("50000"),Optional.of(b("50000"))).inYear(2026);
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,calculate(state,new TaxIncome(b("50000"),b("0"),b("0"),b("0"),b("0"),List.of(special))).calculationStatus());
        var partial=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.PENSION,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),Optional.empty(),Optional.empty(),"Private annuity/pension with unmodeled basis",
                b("40000"),Optional.of(b("20000")),OptionalInt.of(2026),Optional.empty());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,calculate(state,new TaxIncome(b("20000"),b("0"),b("0"),b("0"),b("0"),List.of(partial))).calculationStatus());
    }

    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void affirmativeUnmetOrFuturePensionQualificationOverridesOrdinaryAssumption(StateTaxJurisdiction state) throws Exception {
        for(var facts:List.of(new RetirementParticipantFacts(null,null,RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET,null),
                new RetirementParticipantFacts(null,null,RetirementParticipantFacts.RetirementConditionsStatus.MET,LocalDate.of(2027,1,1)))) {
            var pension=new Pension("Private pension",AccountOwnership.PRIMARY,LocalDate.of(2026,1,1),null,b("1000"),b("0"));
            pension.setRetirementParticipantFacts(facts);
            var source=IncomeSourceAttribution.pension(pension,AccountOwnership.PRIMARY,b("12000"),2026);
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,calculate(state,new TaxIncome(b("12000"),b("0"),b("0"),b("0"),b("0"),List.of(source))).calculationStatus());
        }
    }

    @Test
    void partialOpeningAfterNormalBirthdayIsNotTheWholeYearCrossingCase() throws Exception {
        var income=OklahomaProductionTaxCalculatorTest.income("50000","0","0");
        var r=request(StateTaxJurisdiction.MS,2026,false,59,59,income);
        var h=new TaxHouseholdContext(LocalDate.of(2026,10,1),Map.of(AccountOwnership.PRIMARY,LocalDate.of(1967,1,1)),
                Set.of(AccountOwnership.PRIMARY),Set.of(AccountOwnership.PRIMARY),Optional.empty());
        MississippiProductionTaxCalculatorTest.check("0.00",new StateTaxCalculationRequest(2026,r.filingStatus(),h,income,r.federal(),r.projectedRules(),r.assumptions()));
    }

    @Test
    void materialJanuaryFirstSeniorAmbiguityIsNarrowlyRefused() throws Exception {
        for(String amount:List.of("0","50000")) {
            var income=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b(amount));
            var r=request(StateTaxJurisdiction.MS,2026,false,64,64,income);
            var h=new TaxHouseholdContext(LocalDate.of(2026,1,1),Map.of(AccountOwnership.PRIMARY,LocalDate.of(1962,1,1)),
                    Set.of(AccountOwnership.PRIMARY),Set.of(AccountOwnership.PRIMARY),Optional.empty());
            var result=new MississippiProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026,r.filingStatus(),h,income,r.federal(),r.projectedRules(),r.assumptions()));
            assertEquals(amount.equals("0")?StateTaxCalculationStatus.CALCULATED:StateTaxCalculationStatus.UNSUPPORTED,result.calculationStatus());
            if(!amount.equals("0")) assertTrue(String.join(" ",result.limitations()).contains("January 1"));
        }
    }

    @Test
    void dayBeforeConventionAtModeledJanuaryFirstDeathIsAlsoMaterialOnly() throws Exception {
        for(String amount:List.of("0","100000")) {
            var income=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b(amount));
            var r=request(StateTaxJurisdiction.MS,2026,true,65,71,income);
            var h=new TaxHouseholdContext(LocalDate.of(2026,1,1),Map.of(
                    AccountOwnership.PRIMARY,LocalDate.of(1961,1,2),AccountOwnership.SPOUSE,LocalDate.of(1955,6,1)),
                    Set.of(AccountOwnership.PRIMARY,AccountOwnership.SPOUSE),Set.of(AccountOwnership.SPOUSE),Optional.empty());
            var result=new MississippiProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026,r.filingStatus(),h,income,r.federal(),r.projectedRules(),r.assumptions()));
            assertEquals(amount.equals("0")?StateTaxCalculationStatus.CALCULATED:StateTaxCalculationStatus.UNSUPPORTED,result.calculationStatus());
        }
    }
}
