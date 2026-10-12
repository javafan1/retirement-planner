package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;



import static org.junit.jupiter.api.Assertions.*;

class SurvivorMechanicsCharacterizationTest {
    @Test
    void deterministicDeathRetainsCompleteFinancialOutput() throws Exception {
        var plan = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2031, 67));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("10000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        var projection = new ProjectionEngine().project(plan);
        String financial = projection.getYears().stream().map(year ->
                year.getCalendarYear() + ":" + year.getGuaranteedIncome() + ":"
                        + year.getTotalIncomeTax() + ":" + year.getRothConversion() + ":"
                        + year.getEndingInvestableAssets() + ":" + year.getAfterTaxEstateValue().setScale(2, java.math.RoundingMode.HALF_UP))
                .collect(java.util.stream.Collectors.joining("|"));
        // Reviewed annual Michigan/source and retained-cash differences are listed in the Phase 6C.3 impact ledger.
        assertEquals("2030:66000.00:13.50000000:10000:1563616.90:1312366.90|2031:42600.00:204.000000:10000:1610486.61:1354199.11|2032:43260.0000:2654.24000000:10000:1656972.17:1395496.05|2033:39993.000000:1151.3625000000:10000:1698088.18:1431267.77|2034:36000.00:174.2500:10000:1742421.78:1470096.76", financial);
        assertEquals(0, new BigDecimal("10000").compareTo(projection.getYearAt(2).getRothConversion()));
    }
}
