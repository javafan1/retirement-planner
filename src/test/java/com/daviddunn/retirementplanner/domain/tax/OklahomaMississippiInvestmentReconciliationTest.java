package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaMississippiInvestmentReconciliationTest {
    @ParameterizedTest
    @EnumSource(value=IncomeSourceAttribution.Kind.class,names={"CAPITAL_GAIN","DIVIDEND","INTEREST"})
    void realAggregatePlaceholderIsDescriptiveNotAnotherIncomeChannel(IncomeSourceAttribution.Kind kind) throws Exception {
        var h=new Household(new Person("A","Example",LocalDate.of(1966,6,1)),new Person("B","Example",LocalDate.of(1966,6,1)));
        var base=new TaxIncomeCalculator().calculate(h,LocalDate.of(2026,1,1),b("0"),b("0"),b("100000"));
        var sources=new ArrayList<IncomeSourceAttribution>();
        for(var owner:List.of(AccountOwnership.PRIMARY,AccountOwnership.SPOUSE)) sources.add(new IncomeSourceAttribution(kind,
                IncomeSourceAttribution.Purpose.INCOME,Optional.of(owner),Optional.empty(),Optional.empty(),"Known modeled income",
                b("50000"),Optional.of(b("50000")),OptionalInt.of(2026),Optional.empty()));
        var attributed=TaxIncomeAttribution.attach(base,sources,2026);
        for(var state:List.of(StateTaxJurisdiction.OK,StateTaxJurisdiction.MS)) {
            var r=request(state,2026,true,60,60,attributed);
            assertEquals(b("100000"),r.federal().getAdjustedGrossIncome());
            var result=new ProductionStateTaxService().calculate(r);
            assertEquals(b(state==StateTaxJurisdiction.OK?"3409.00":"2536.00"),result.incomeTax());
            var duplicate=new ArrayList<>(attributed.getIncomeSources()); duplicate.add(sources.getFirst());
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new ProductionStateTaxService().calculate(
                    request(state,2026,true,60,60,attributed.withIncomeSources(duplicate))).calculationStatus());
            var tiny=new TaxIncome(b("0"),b("0"),b("0"),b("0"),b("1000"),List.of(sources.getFirst()));
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new ProductionStateTaxService().calculate(
                    request(state,2026,true,60,60,tiny)).calculationStatus());
        }
        var unallocated=TaxIncomeAttribution.attach(base,List.of(),2026);
        assertEquals(b("2936.00"),new ProductionStateTaxService().calculate(request(StateTaxJurisdiction.MS,2026,true,60,60,unallocated)).incomeTax());
    }
}
