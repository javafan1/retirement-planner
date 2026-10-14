package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IowaProductionProjectionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void admittedSingleCoupleRmdConversionsFundingAndPersistenceRepeatExactly(boolean couple, @TempDir Path directory) throws Exception {
        var plan = PennsylvaniaProductionProjectionTest.plan(couple, StateTaxJurisdiction.IA);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("100000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        var openingBalances = plan.getAccountPortfolio().getAccounts().stream().map(a -> a.getCurrentBalance()).toList();
        var first = new ProjectionEngine().project(plan);
        for (var year : first.getYears()) {
            assertEquals(StateTaxJurisdiction.IA, year.getStateTaxJurisdiction());
            assertEquals(0, year.getStateIncomeTax().signum());
            assertTrue(year.stateTaxCalculation().orElseThrow().limitations().getFirst().contains("not assumed to be zero"));
            assertTrue(year.getIncomeSources().stream().anyMatch(s -> s.kind() == IncomeSourceAttribution.Kind.RMD));
        }
        assertEquals(openingBalances, plan.getAccountPortfolio().getAccounts().stream().map(a -> a.getCurrentBalance()).toList());
        assertEquals(FloridaProductionProjectionTest.financialRows(first), FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(plan)));
        var repository = new JsonRetirementPlanRepository();
        var json = directory.resolve("iowa.json"); repository.save(plan, json);
        var loaded = repository.load(json);
        assertEquals(StateTaxJurisdiction.IA, loaded.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        assertEquals(FloridaProductionProjectionTest.financialRows(first), FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(loaded)));
        loaded.setPlanningAssumptions(loaded.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        PennsylvaniaProductionProjectionTest.assertSameFinancialValues(new ProjectionEngine().project(loaded), first);
    }

    @Test
    void fixedPartialExecutionUsesActualAmountWhileCapacityLimitedBracketTrialRefuses() {
        var plan = PennsylvaniaProductionProjectionTest.plan(true, StateTaxJurisdiction.IA);
        for (var account : plan.getAccountPortfolio().getAccounts()) {
            if (account.getType() == AccountType.CHECKING) account.setCurrentBalance(new BigDecimal("20000000"));
        }
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("5000000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var result = new ProjectionEngine().project(plan).getYears().getFirst();
        assertTrue(result.getRothConversionShortfall().signum() > 0);
        assertTrue(result.getPrimaryRothConversion().signum() > 0);
        assertTrue(result.getSpouseRothConversion().signum() > 0);
        assertEquals(0, result.getStateIncomeTax().signum());
        var total = result.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION)
                .map(IncomeSourceAttribution::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(result.getRothConversion()));
        // Genuine capacity-limited hypothetical trial: optimizer remains unchanged and refuses its remainder.
        for (var account : plan.getAccountPortfolio().getAccounts()) {
            account.setOpeningRmdAccountData(null);
            if (account.getType() == AccountType.TRADITIONAL_IRA || account.getType() == AccountType.ROLLOVER_IRA) {
                account.setCurrentBalance(new BigDecimal("1000"));
                account.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                        2027, new BigDecimal("1000"), BigDecimal.ZERO));
            }
        }
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        var failure = assertThrows(com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException.class,
                () -> new ProjectionEngine().project(plan));
        assertTrue(failure.result().unavailableReasons().contains(com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE));
    }
}
