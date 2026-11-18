package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent Phase6E-M AL-08..22 fixtures. Rules-only bracket/deduction fixtures are separate. */
public class AlabamaProductionTaxCalculatorTest {
    @ParameterizedTest
    @CsvSource({"60,50000,0,0,2069.00", "60,50000,20000,0,2931.50", "65,50000,0,0,1817.30",
            "65,50000,0,20000,1715.30", "60,0,0,20000,0.00"})
    void independentIraConversionAndSocialWorksheets(int age, String ira, String conversion, String social, String expected) throws Exception {
        var income = new TaxIncome(b("0"), b(social), b(ira), b(conversion), b("0"),
                List.of(retirement(AccountOwnership.PRIMARY, ira, 2026), conversion(AccountOwnership.PRIMARY, conversion, 2026)));
        check(expected, request(StateTaxJurisdiction.AL, 2026, false, age, age, income));
    }

    @Test
    void individualSeniorCapsAndFederalSeniorCorrection() throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b("100000"), b("0"), b("0"), List.of(
                retirement(AccountOwnership.PRIMARY,"50000",2026), retirement(AccountOwnership.SPOUSE,"50000",2026)));
        check("3629.80", request(StateTaxJurisdiction.AL,2026,true,65,65,income));
        check("3929.80", request(StateTaxJurisdiction.AL,2026,true,65,65,income.withIncomeSources(
                List.of(retirement(AccountOwnership.PRIMARY,"100000",2026)))));
    }

    @Test
    void explicitDbExemptionNeverConsumesSeniorPoolAndUnknownMustBeMaterial() throws Exception {
        var db = pension(PensionPlanClassification.DEFINED_BENEFIT_414J,"30000");
        var income = new TaxIncome(b("30000"),b("0"),b("20000"),b("20000"),b("0"),List.of(db,
                retirement(AccountOwnership.PRIMARY,"20000",2026), conversion(AccountOwnership.PRIMARY,"20000",2026)));
        check("1193.55",request(StateTaxJurisdiction.AL,2026,false,70,70,income));
        check("0.00",request(StateTaxJurisdiction.AL,2026,false,60,60,
                new TaxIncome(b("50000"),b("0"),b("0"),b("0"),b("0"),List.of(pension(PensionPlanClassification.DEFINED_BENEFIT_414J,"50000")))));
        var unknown = new TaxIncome(b("50000"),b("0"),b("0"),b("0"),b("0"),List.of(pension(PensionPlanClassification.UNKNOWN,"50000")));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, new AlabamaProductionTaxCalculator()
                .calculate(request(StateTaxJurisdiction.AL,2026,false,65,65,unknown)).calculationStatus());
        check("0.00",request(StateTaxJurisdiction.AL,2026,false,60,60,
                new TaxIncome(b("1000"),b("0"),b("0"),b("0"),b("0"),List.of(pension(PensionPlanClassification.UNKNOWN,"1000")))));
        check("1817.30",request(StateTaxJurisdiction.AL,2026,false,65,65,
                unknown.withIncomeSources(List.of(pension(PensionPlanClassification.DEFINED_CONTRIBUTION,"50000")))));
    }

    @Test
    void unknownBasisBrokeragePrincipalDoesNotCreateIncome() throws Exception {
        var account=new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Brokerage",AccountOwnership.PRIMARY,b("100000"));
        var income=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("0"),List.of(IncomeSourceAttribution.withdrawal(account,b("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL,IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026)));
        check("0.00",request(StateTaxJurisdiction.AL,2026,false,65,65,income));
    }

    @Test
    void admittedAnnuityTaxablePortionUsesOnlyOwnSeniorPool() throws Exception {
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
        check("715.50",request(StateTaxJurisdiction.AL,2026,false,60,60,attached));
        check("435.00",request(StateTaxJurisdiction.AL,2026,false,65,65,attached));
    }
    public static IncomeSourceAttribution pension(PensionPlanClassification type,String amount) {
        var p=new Pension("Display name is not classification",AccountOwnership.PRIMARY,LocalDate.of(2026,1,1),null,
                b(amount).divide(b("12"),20,java.math.RoundingMode.HALF_UP),b("0"));
        p.setPlanClassification(type);
        return IncomeSourceAttribution.pension(p,AccountOwnership.PRIMARY,b(amount),2026);
    }

    public static void check(String expected,StateTaxCalculationRequest request) {
        var result=new AlabamaProductionTaxCalculator().calculate(request);
        assertEquals(StateTaxCalculationStatus.CALCULATED,result.calculationStatus(),()->result.limitations().toString());
        assertEquals(b(expected),result.incomeTax());
    }
}
