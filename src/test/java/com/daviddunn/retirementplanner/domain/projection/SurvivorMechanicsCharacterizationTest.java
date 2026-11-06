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
        // Independent oracle: split the old pension into scheduled living-recipient payments.
        // The 2031 amount is $500 x 1.10 = $550/month, followed by the original COLA/end date.
        var oracle = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        PensionCashTaxConsistencyTest.removePensions(oracle);
        oracle.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension(
                "Owner scheduled payments", AccountOwnership.PRIMARY, java.time.LocalDate.of(2030, 1, 1),
                java.time.LocalDate.of(2030, 12, 31), new BigDecimal("1000"), BigDecimal.ZERO));
        oracle.getHousehold().getSpouse().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension(
                "Living recipient scheduled payments", AccountOwnership.SPOUSE, java.time.LocalDate.of(2031, 1, 1),
                java.time.LocalDate.of(2033, 6, 30), new BigDecimal("550"), new BigDecimal(".10")));
        var independentlyScheduled = new ProjectionEngine().project(oracle);
        var pensionAmounts = java.util.List.of("12000", "6600", "7260", "3993", "0");
        for (int index = 0; index < projection.size(); index++) {
            var year = projection.getYearAt(index);
            var attributed = year.getIncomeSources().stream()
                    .filter(source -> source.kind() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.PENSION)
                    .map(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution::grossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            PensionCashTaxConsistencyTest.money(new BigDecimal(pensionAmounts.get(index)), attributed);
            for (var getter : ProjectionYear.class.getMethods()) {
                if (getter.getName().startsWith("get") && getter.getParameterCount() == 0 && getter.getReturnType() == BigDecimal.class)
                    PensionCashTaxConsistencyTest.money((BigDecimal) getter.invoke(independentlyScheduled.getYearAt(index)),
                            (BigDecimal) getter.invoke(year));
            }
        }
        String financial = projection.getYears().stream().map(year ->
                year.getCalendarYear() + ":" + year.getGuaranteedIncome() + ":"
                        + year.getTotalIncomeTax() + ":" + year.getRothConversion() + ":"
                        + year.getEndingInvestableAssets() + ":" + year.getAfterTaxEstateValue().setScale(2, java.math.RoundingMode.HALF_UP))
                .collect(java.util.stream.Collectors.joining("|"));
        // B0 corrects the ordinary pension tax selection; the independent scheduled-payment oracle above
        // verifies the changed tax, funding and estate values. Pre-death output is unchanged.
        assertEquals("2030:66000.00:13.50000000:10000:1563616.90:1312366.90|2031:42600.00:204.000000:10000:1610486.61:1354199.11|2032:43260.0000:1155.90000000:10000:1658470.51:1396994.39|2033:39993.000000:482.9025000000:10000:1700299.93:1433479.52|2034:36000.00:174.2500:10000:1744699.88:1472374.86", financial);
        assertEquals(0, new BigDecimal("10000").compareTo(projection.getYearAt(2).getRothConversion()));
    }
}
