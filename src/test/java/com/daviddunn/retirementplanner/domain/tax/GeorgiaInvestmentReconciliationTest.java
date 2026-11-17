package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class GeorgiaInvestmentReconciliationTest {
    @ParameterizedTest
    @EnumSource(value=IncomeSourceAttribution.Kind.class,names={"CAPITAL_GAIN","DIVIDEND","INTEREST"})
    void realCalculatorAndAttachmentReconcileSameDollars(IncomeSourceAttribution.Kind kind) throws Exception {
        var h = new Household(new Person("A","Example",LocalDate.of(1961,6,1)),new Person("B","Example",LocalDate.of(1961,6,1)));
        var base = new TaxIncomeCalculator().calculate(h,LocalDate.of(2026,1,1),b("90000"),b("0"),b("20000"));
        for (var owner : List.of(AccountOwnership.PRIMARY,AccountOwnership.SPOUSE,AccountOwnership.JOINT)) {
            var gain = new IncomeSourceAttribution(kind,IncomeSourceAttribution.Purpose.INCOME,
                    Optional.of(owner),Optional.empty(),Optional.empty(),"Known modeled gain",b("20000"),Optional.of(b("20000")),OptionalInt.of(2026),Optional.empty());
            var attributed = TaxIncomeAttribution.attach(base,List.of(retirement(AccountOwnership.PRIMARY,"90000",2026),gain),2026);
            assertEquals(kind!=IncomeSourceAttribution.Kind.INTEREST,attributed.getIncomeSources().stream()
                    .anyMatch(s -> s.kind()==IncomeSourceAttribution.Kind.INTEREST && s.owner().isEmpty()));
            assertTax(owner==AccountOwnership.SPOUSE ? "0.00" : owner==AccountOwnership.JOINT ? "249.50" : "748.50",
                    request(StateTaxJurisdiction.GA,2026,true,65,65,attributed));
            // VA: 110000 - 17500 - 3460 exemptions = 89040; graduated tax 4862.30.
            assertTax("4862.30",request(StateTaxJurisdiction.VA,2026,true,65,65,attributed));
            var duplicate = attributed.withIncomeSources(new ArrayList<>() {{ addAll(attributed.getIncomeSources()); add(gain); }});
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED,new GeorgiaProductionTaxCalculator().calculate(request(StateTaxJurisdiction.GA,2026,true,65,65,duplicate)).calculationStatus());
        }
        var unallocated = TaxIncomeAttribution.attach(base,List.of(retirement(AccountOwnership.PRIMARY,"90000",2026)),2026);
        assertTax("748.50",request(StateTaxJurisdiction.GA,2026,true,65,65,unallocated));
        assertEquals(b("110000"),request(StateTaxJurisdiction.GA,2026,true,65,65,unallocated).federal().getAdjustedGrossIncome());
    }
}
