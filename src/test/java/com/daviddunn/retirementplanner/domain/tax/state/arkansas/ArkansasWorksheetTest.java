package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRateWorksheet.Policy.*;
import static com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRetirementEligibility.*;

class ArkansasWorksheetTest {
    private final ArkansasRateWorksheet worksheet = new ArkansasRateWorksheet();

    @ParameterizedTest
    @CsvSource({"0,0.00", "5600,0.00", "11200,112.00", "16000,256.00", "26400,609.60",
            "94700,3136.70", "94700.99,3136.74", "94701,3134.04", "94800,3137.70",
            "94801,3147.74", "97600,3521.30", "97601,3531.34", "100000,3620.10"})
    void auditStatutoryGoldens(String income, String tax) {
        assertEquals(b(tax), worksheet.grossTax(2026, b(income), P_STATUTORY_CENTS));
    }

    @ParameterizedTest
    @CsvSource({"5600,0.00", "11200,111.00", "16000,255.00", "26400,608.00", "30000,741.00",
            "40000,1111.00", "50000,1481.00", "60000,1851.00", "70000,2221.00", "80001,2591.00",
            "94700,3134.86", "94700.99,3134.90", "94701,3136.00", "94800.99,3136.00", "94801,3150.00",
            "97600.99,3519.00", "97601,3533.00", "100000,3618.00"})
    void datedAgencyEstimatedWorksheetIsAnIndependentAlternative(String income, String tax) {
        assertEquals(b(tax), worksheet.grossTax(2026, b(income), OFFICIAL_2026_ESTIMATED_TABLE));
    }

    @Test
    void allTwentyNineAdjustmentBandsHaveClosedWholeDollarAndExplicitFractionalEdges() {
        for(var edge:List.of(new String[]{"5600","0.00"},new String[]{"11200","112.00"},
                new String[]{"16000","256.00"},new String[]{"26400","609.60"}))
            for(var cent:List.of("-.01","0",".01"))
                assertEquals(b(edge[1]),worksheet.grossTax(2026,b(edge[0]).add(b(cent)),P_STATUTORY_CENTS));
        String[] firstTax={"3134.04","3147.74","3161.44","3175.14","3188.84","3202.54","3216.24",
                "3229.94","3243.64","3257.34","3271.04","3284.74","3298.44","3312.14","3325.84",
                "3339.54","3353.24","3366.94","3380.64","3394.34","3408.04","3421.74","3435.44",
                "3449.14","3462.84","3476.54","3490.24","3503.94","3517.64"};
        int[] agency={3136,3150,3163,3177,3191,3204,3218,3232,3245,3259,3273,3287,3300,3314,3328,
                3341,3355,3369,3382,3396,3410,3424,3437,3451,3465,3478,3492,3506,3519};
        for(int i=0;i<29;i++) {
            var first=BigDecimal.valueOf(94701+100L*i);
            assertEquals(0,BigDecimal.valueOf(290-10L*i).compareTo(worksheet.bracketAdjustment(first)));
            assertEquals(b(firstTax[i]),worksheet.grossTax(2026,first,P_STATUTORY_CENTS));
            assertEquals(b(firstTax[i]).add(b("3.70")),worksheet.grossTax(2026,first.add(b("99.99")),P_STATUTORY_CENTS));
            assertEquals(b(agency[i]+".00"),worksheet.grossTax(2026,first,OFFICIAL_2026_ESTIMATED_TABLE));
            assertEquals(b(agency[i]+".00"),worksheet.grossTax(2026,first.add(b("99.99")),OFFICIAL_2026_ESTIMATED_TABLE));
            assertEquals(worksheet.grossTax(2026,first,P_STATUTORY_CENTS).subtract(b(i==0?"-2.70":"10.00")),
                    worksheet.grossTax(2026,first.subtract(b(".01")),P_STATUTORY_CENTS));
        }
        assertThrows(IllegalArgumentException.class,()->worksheet.grossTax(2026,b("5599.99"),OFFICIAL_2026_ESTIMATED_TABLE));
        assertThrows(IllegalArgumentException.class,()->worksheet.grossTax(2027,b("50000"),P_STATUTORY_CENTS));
        assertFalse(ArkansasTaxRules.productionAdmitted());
        assertTrue(com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.fromCode("AR").productionAdmitted());
    }

    @ParameterizedTest
    @CsvSource({"50000,0,false,1362.41", "55000,5000,false,1362.41", "56000,6000,false,1362.41",
            "57000,7000,false,1399.41", "50000,50000,false,1140.41", "50000,50000,true,1111.41",
            "100000,100000,false,2990.41", "70000,0,false,2102.41", "60000,0,false,1732.41", "0,0,false,0.00"})
    void ordinaryIndependentRecipientWorksheets(String income,String eligible,boolean senior,String tax) {
        var r=new ArkansasJointWorksheet().calculate(b(income),List.of(recipient(AccountOwnership.PRIMARY,eligible,senior,true)),P_STATUTORY_CENTS);
        assertEquals(b(tax),r.tax());
    }

    @Test
    void jointCapsDoNotTransferAndSeniorSpecialIsAlternativeToRetirementRelief() {
        var w=new ArkansasJointWorksheet();
        assertEquals(b("2648.02"),w.calculate(b("100000"),List.of(recipient(AccountOwnership.PRIMARY,"50000",false,true),
                recipient(AccountOwnership.SPOUSE,"50000",false,true)),P_STATUTORY_CENTS).tax());
        assertEquals(b("2870.02"),w.calculate(b("100000"),List.of(recipient(AccountOwnership.PRIMARY,"100000",false,true),
                recipient(AccountOwnership.SPOUSE,"0",false,true)),P_STATUTORY_CENTS).tax());
        assertEquals(b("1304.41"),w.calculate(b("50000"),List.of(recipient(AccountOwnership.PRIMARY,"0",true,false)),P_STATUTORY_CENTS).tax());
        assertEquals(b("1315.51"),w.lowestReviewedChoice(b("50300"),List.of(recipient(AccountOwnership.PRIMARY,"300",true,true)),P_STATUTORY_CENTS).tax());
        assertEquals(b("1111.41"),w.lowestReviewedChoice(b("50000"),List.of(recipient(AccountOwnership.PRIMARY,"50000",true,true)),P_STATUTORY_CENTS).tax());
        // Taxable20,000 of a50,000 commercial annuity: basis is outside income, no retirement pool.
        assertEquals(b("308.02"),worksheet.grossTax(2026,b("17530"),P_STATUTORY_CENTS));
        assertEquals(b("279.02"),w.calculate(b("20000"),List.of(recipient(AccountOwnership.PRIMARY,"0",false,true)),P_STATUTORY_CENTS).tax());
        // Independently qualified employer-plan annuity can instead share the6,000 pool.
        assertEquals(b("92.90"),w.calculate(b("20000"),List.of(recipient(AccountOwnership.PRIMARY,"20000",false,true)),P_STATUTORY_CENTS).tax());
        assertThrows(IllegalArgumentException.class,()->w.calculate(b("1000"),List.of(recipient(AccountOwnership.PRIMARY,"2000",false,true)),P_STATUTORY_CENTS));
        assertEquals(b("5702.05"),w.calculate(b("50000"),List.of(recipient(AccountOwnership.PRIMARY,"50000",false,true)),P_STATUTORY_CENTS).tax().multiply(b("5")));
    }

    @Test
    void laterStatuteOwnIraCoverageIsAgeBoundedAndNotABlanketRegulatoryExclusion() {
        var e=new ArkansasRetirementEligibility();var dob=LocalDate.of(1966,7,1);
        var begin=LocalDate.of(2026,1,1);var end=LocalDate.of(2026,12,31);
        assertEquals(Decision.QUALIFYING_POOL,e.assess(Source.OWN_IRA_CASH,dob,begin,end,false));
        assertEquals(Decision.QUALIFYING_POOL,e.assess(Source.OWN_IRA_CONVERSION_INTERPRETATION,dob,begin,end,false));
        assertEquals(Decision.TAXABLE_WITHOUT_POOL,e.assess(Source.OWN_IRA_CASH,dob.plusYears(10),begin,end,false));
        assertEquals(Decision.UNSUPPORTED,e.assess(Source.OWN_IRA_CASH,dob.plusDays(1),begin,end,false));
        assertEquals(Decision.QUALIFYING_POOL,e.assess(Source.ORDINARY_EMPLOYER_PLAN,dob.plusYears(10),begin,end,true));
        assertEquals(Decision.QUALIFYING_POOL,e.assess(Source.DIRECT_SURVIVOR_PENSION,dob.plusYears(20),begin,end,true));
        assertEquals(Decision.TAXABLE_WITHOUT_POOL,e.assess(Source.COMMERCIAL_ANNUITY,dob,begin,end,true));
        assertEquals(Decision.OUTSIDE_FEDERAL_BASE,e.assess(Source.QUALIFIED_ROTH,dob,begin,end,false));
        assertEquals(Decision.UNSUPPORTED,e.assess(Source.UNSUPPORTED_SPECIAL,dob,begin,end,true));
    }
    private static ArkansasJointWorksheet.Recipient recipient(AccountOwnership owner,String amount,boolean senior,boolean pool) {
        return new ArkansasJointWorksheet.Recipient(owner,b(amount),senior,pool);
    }
    private static BigDecimal b(String v) {return new BigDecimal(v);}
}
