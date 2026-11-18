package com.daviddunn.retirementplanner.domain.tax.state.alabama;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class AlabamaTaxRulesTest {
    @org.junit.jupiter.api.Test
    void everyCompletedStepAndSpecifiedFederalWorksheetContrasts() {
        var r=new AlabamaTaxRules();
        for(int step=1;step<=20;step++) {
            var boundary=BigDecimal.valueOf(25500+500L*step);
            assertEquals(0,BigDecimal.valueOf(3000-25L*step).compareTo(r.standardDeduction(boundary,FilingStatus.SINGLE)));
            assertEquals(0,BigDecimal.valueOf(3000-25L*(step-1)).compareTo(r.standardDeduction(boundary.subtract(new BigDecimal(".01")),FilingStatus.SINGLE)));
            assertEquals(0,BigDecimal.valueOf(8500-175L*step).compareTo(r.standardDeduction(boundary,FilingStatus.MARRIED_FILING_JOINTLY)));
        }
        // AL-04/05 and AL-07 are legal worksheet premises with a specified deduction,
        // not fabricated FederalTaxCalculation amounts on an unexplained federal return.
        String[][] rows={{"25999.99","0","1035.00"},{"26000","0","1036.25"},
                {"50000","0","2260.00"},{"50000","5000","2010.00"},{"50000","50000","0.00"}};
        for(var row:rows) {
            var agi=new BigDecimal(row[0]);
            var base=agi.subtract(r.standardDeduction(agi,FilingStatus.SINGLE)).subtract(new BigDecimal("1500"))
                    .subtract(new BigDecimal(row[1])).max(BigDecimal.ZERO);
            assertEquals(new BigDecimal(row[2]),r.tax(base,FilingStatus.SINGLE));
        }
        assertEquals(new BigDecimal("645.00"),r.tax(new BigDecimal("25999.99").subtract(new BigDecimal("8500")).subtract(new BigDecimal("3000")),FilingStatus.MARRIED_FILING_JOINTLY));
        assertEquals(new BigDecimal("653.75"),r.tax(new BigDecimal("26000").subtract(new BigDecimal("8325")).subtract(new BigDecimal("3000")),FilingStatus.MARRIED_FILING_JOINTLY));
    }

    @ParameterizedTest
    @CsvSource({"false,0,0.00", "false,500,10.00", "false,500.01,10.00",
            "false,3000,110.00", "false,3000.10,110.01", "true,1000,20.00",
            "true,6000,220.00", "true,6000.10,220.01"})
    void auditedMarginalSlices(boolean joint, String income, String expected) {
        assertEquals(new BigDecimal(expected), new AlabamaTaxRules().tax(new BigDecimal(income), status(joint)));
    }

    @ParameterizedTest
    @CsvSource({"false,25500,3000", "false,25999.99,3000", "false,26000,2975",
            "false,35499.99,2525", "false,35500,2500", "false,100000,2500",
            "true,25999.99,8500", "true,26000,8325", "true,35499.99,5175", "true,35500,5000"})
    void completedStepsAreNotContinuousOrCeilingRounded(boolean joint, String agi, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(new AlabamaTaxRules()
                .standardDeduction(new BigDecimal(agi), status(joint))));
    }

    private static FilingStatus status(boolean joint) {
        return joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
    }
}
