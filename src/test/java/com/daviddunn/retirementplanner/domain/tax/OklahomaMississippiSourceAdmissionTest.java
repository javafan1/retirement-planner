package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.util.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaMississippiSourceAdmissionTest {
    private ProductionStateTaxResult result(StateTaxJurisdiction state,TaxIncome income) throws Exception {
        return new ProductionStateTaxService().calculate(request(state,2026,false,62,62,income));
    }
    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void realBrokeragePrincipalQualifiedRothAndKnownGainStayInTheirOwnChannels(StateTaxJurisdiction state) throws Exception {
        var broker = IncomeSourceAttribution.withdrawal(new BrokerageAccount("Broker",AccountOwnership.PRIMARY,b("100000")),b("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL,IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var roth = IncomeSourceAttribution.withdrawal(new RothIRA("Roth",AccountOwnership.PRIMARY,b("100000")),b("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        assertEquals(b("0.00"),result(state,new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("0"),List.of(broker,roth))).incomeTax());
        var gain = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.CAPITAL_GAIN,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),broker.account(),Optional.empty(),"Known gain",b("5000"),Optional.of(b("5000")),OptionalInt.of(2026),Optional.empty());
        var income = new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("5000"),List.of(broker,gain,retirement(AccountOwnership.PRIMARY,"50000",2026)));
        assertEquals(b(state==StateTaxJurisdiction.OK ? "1479.50" : "0.00"),result(state,income).incomeTax());
        for(var sources:List.of(List.of(gain,gain),List.of(gain.inYear(2025)))) {
            var invalid = new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("5000"),sources);
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(state,invalid).calculationStatus());
        }
    }
    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void retirementClassificationAndBasisContradictionsFailSafely(StateTaxJurisdiction state) throws Exception {
        for(var type:List.of(AccountType.TRADITIONAL_IRA,AccountType.ROTH_IRA,AccountType.BROKERAGE)) {
            var account = AccountFactory.create(type,"Source",AccountOwnership.PRIMARY,b("100000"));
            var source = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,IncomeSourceAttribution.Purpose.CASH_FLOW,
                    Optional.of(AccountOwnership.PRIMARY),Optional.of(IncomeSourceAttribution.AccountReference.capture(account)),Optional.empty(),
                    "Contradictory retirement classification",b("5000"),Optional.of(b(type==AccountType.BROKERAGE ? "5000" : "1000")),OptionalInt.of(2026),Optional.empty());
            var income = new TaxIncome(b("0"),b("0"),source.modeledTaxableAmount().orElseThrow(),b("0"),b("0"),List.of(source));
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(state,income).calculationStatus());
        }
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA",AccountOwnership.PRIMARY,b("100000")),b("5000"),
                IncomeSourceAttribution.Kind.CAPITAL_GAIN,IncomeSourceAttribution.Purpose.INCOME).inYear(2026);
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(state,new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("5000"),List.of(source))).calculationStatus());
    }
    @ParameterizedTest @EnumSource(value=AccountType.class,names={"TRADITIONAL_IRA","ROLLOVER_IRA","TRADITIONAL_401K","TRADITIONAL_403B"})
    void ordinarySourcesRmdAndOpeningRmdUseSameTaxableAmount(AccountType type) throws Exception {
        var account=AccountFactory.create(type,"Ordinary source",AccountOwnership.PRIMARY,b("100000"));
        for(var kind:List.of(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,IncomeSourceAttribution.Kind.RMD,IncomeSourceAttribution.Kind.OPENING_RMD)) {
            var source=IncomeSourceAttribution.withdrawal(account,b("50000"),kind,IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
            var income=new TaxIncome(b("0"),b("0"),b("50000"),b("0"),b("0"),List.of(source));
            assertEquals(b("1254.50"),result(StateTaxJurisdiction.OK,income).incomeTax());
            assertEquals(b("0.00"),result(StateTaxJurisdiction.MS,income).incomeTax());
        }
    }
    @Test
    void admittedAnnuityWorksheetTaxesOnlyFiftyThousandOfSixtyThousand() throws Exception {
        var date=LocalDate.of(2026,1,1);
        var definition=com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest.annuity(
                com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference.PRIMARY,date,date,"5000","1000","10000");
        var plan=com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.plan(false);
        var deaths=com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.empty());
        var receipt=new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator().evaluate(List.of(definition),
                com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext.capture(plan.getHousehold(),deaths,date,2026)).receipts().getFirst();
        assertEquals(0,b("60000").compareTo(receipt.gross()));
        assertEquals(0,b("50000").compareTo(receipt.taxable()));
        var income=TaxIncomeAttribution.attach(new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("0")),
                List.of(IncomeSourceAttribution.conditional(receipt)),2026);
        assertEquals(b("1704.50"),result(StateTaxJurisdiction.OK,income).incomeTax());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(StateTaxJurisdiction.MS,income).calculationStatus());
    }
    @Test
    void newStatesDoNotInferEmployerConversionEligibility() throws Exception {
        var source=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ROTH_CONVERSION,IncomeSourceAttribution.Purpose.CONVERSION,
                Optional.of(AccountOwnership.PRIMARY),Optional.of(IncomeSourceAttribution.AccountReference.capture(new Traditional401K("Employer",AccountOwnership.PRIMARY,b("100000")))),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Roth",AccountOwnership.PRIMARY,b("0")))),
                "Employer conversion",b("20000"),Optional.of(b("20000")),OptionalInt.of(2026),Optional.empty());
        var income=new TaxIncome(b("0"),b("0"),b("0"),b("20000"),b("0"),List.of(source));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(StateTaxJurisdiction.OK,income).calculationStatus());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(StateTaxJurisdiction.MS,income).calculationStatus());
    }
    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void overlappingGainAndWithdrawalEvidenceCannotClaimDisjointIncome(StateTaxJurisdiction state) throws Exception {
        var account=Optional.of(IncomeSourceAttribution.AccountReference.capture(new BrokerageAccount("Broker",AccountOwnership.PRIMARY,b("100000"))));
        var proceeds=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL,IncomeSourceAttribution.Purpose.CASH_FLOW,
                Optional.of(AccountOwnership.PRIMARY),account,Optional.empty(),"Proceeds with known taxable portion",b("10000"),Optional.of(b("1000")),OptionalInt.of(2026),Optional.empty());
        var gain=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.CAPITAL_GAIN,IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY),account,Optional.empty(),"Gain on the same account",b("1000"),Optional.of(b("1000")),OptionalInt.of(2026),Optional.empty());
        // Even within a larger modeled bucket, facts do not establish that these are different gains.
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(state,
                new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("5000"),List.of(proceeds,gain))).calculationStatus());
    }
    @ParameterizedTest @EnumSource(value=StateTaxJurisdiction.class,names={"OK","MS"})
    void rothSourceCannotMasqueradeAsQualifiedCashWhenKindIsConversion(StateTaxJurisdiction state) throws Exception {
        var source=new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ROTH_CONVERSION,IncomeSourceAttribution.Purpose.CONVERSION,
                Optional.of(AccountOwnership.PRIMARY),Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Source Roth",AccountOwnership.PRIMARY,b("10000")))),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Destination Roth",AccountOwnership.PRIMARY,b("0")))),
                "Contradictory conversion source",b("5000"),Optional.of(b("0")),OptionalInt.of(2026),Optional.empty());
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED,result(state,new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("0"),List.of(source))).calculationStatus());
    }
}
