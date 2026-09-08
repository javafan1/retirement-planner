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
        assertEquals("2030:66000.00:5.00000000:10000:1563625.40:1312375.40|2031:42600.00:0:10000:1610699.36:1354411.86|2032:43260.0000:2171.44000000:10000:1657674.10:1396197.98|2033:39993.000000:807.4100000000:10000:1699155.11:1432334.70|2034:36000.00:0:10000:1743694.96:1471369.94", financial);
        assertEquals(0, new BigDecimal("10000").compareTo(projection.getYearAt(2).getRothConversion()));
    }
}
