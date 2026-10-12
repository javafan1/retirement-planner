package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.roth.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IllinoisProductionProjectionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void threeStateProjectionKeepsFederalBenefitsRmdOrderingAndReportsIllinois(boolean couple) throws Exception {
        var results = new EnumMap<StateTaxJurisdiction, Projection>(StateTaxJurisdiction.class);
        for (var state : StateTaxJurisdiction.values()) {
            var plan = FloridaProductionProjectionTest.plan(couple, state);
            var before = plan.getAccountPortfolio().getAccounts().stream().map(a -> a.getCurrentBalance()).toList();
            var projection = new ProjectionEngine().project(plan);
            assertEquals(before, plan.getAccountPortfolio().getAccounts().stream().map(a -> a.getCurrentBalance()).toList());
            results.put(state, projection);
        }
        var mi = results.get(StateTaxJurisdiction.MI);
        var fl = results.get(StateTaxJurisdiction.FL);
        var il = results.get(StateTaxJurisdiction.IL);
        for (int index = 0; index < il.getYears().size(); index++) {
            var year = il.getYearAt(index);
            assertEquals(StateTaxJurisdiction.IL, year.getStateTaxJurisdiction());
            assertEquals(mi.getYearAt(index).getFederalIncomeTax(), year.getFederalIncomeTax());
            assertEquals(fl.getYearAt(index).getRequiredMinimumDistribution(), year.getRequiredMinimumDistribution());
            assertEquals(mi.getYearAt(index).getRmdDistributedBeforeProjection(), year.getRmdDistributedBeforeProjection());
            assertEquals(mi.getYearAt(index).getRothConversion(), year.getRothConversion());
            assertEquals(mi.getYearAt(index).getSocialSecurityResult(), year.getSocialSecurityResult());
            // Pension qualification is unknown in the existing model. The IRA, RMD,
            // conversion and federally included SS are excluded, leaving pension only.
            BigDecimal pension = year.getIncomeSources().stream()
                    .filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION)
                    .map(s -> s.modeledTaxableAmount().orElseThrow()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal exemption = new BigDecimal(couple ? "7850" : "3925");
            assertEquals(pension.subtract(exemption).max(BigDecimal.ZERO).multiply(new BigDecimal("0.0495"))
                    .setScale(2, RoundingMode.HALF_UP), year.getStateIncomeTax());
            assertEquals(year.getFederalIncomeTax().add(year.getStateIncomeTax()), year.getTotalIncomeTax());
            assertTrue(year.stateTaxCalculation().orElseThrow().limitations().stream()
                    .anyMatch(s -> s.contains("Pension plan qualification unavailable")));
        }
        var rows = new ArrayList<String>();
        rows.add("Metric|Michigan|Florida|Illinois");
        for (var method : List.of("getFederalIncomeTax", "getStateIncomeTax", "getTotalIncomeTax",
                "getTaxFundingWithdrawal", "getRothConversion", "getEndingInvestableAssets",
                "getAfterTaxEstateValue")) {
            var values = new ArrayList<String>();
            values.add(method);
            for (var state : StateTaxJurisdiction.values()) {
                var year = results.get(state).getYears().getLast();
                values.add(((BigDecimal) ProjectionYear.class.getMethod(method).invoke(year))
                        .setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
            rows.add(String.join("|", values));
        }
        Files.write(Path.of("target/phase6d2-" + (couple ? "couple" : "single") + "-abc.txt"), rows);
        Path csv = Path.of("target/phase6d2-" + (couple ? "couple" : "single") + ".csv");
        new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(il, List.of(), csv);
        assertTrue(Files.readString(csv).contains("IL,SIMPLIFIED_STATE_ESTIMATE"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bracketFillUsesUnchangedFederalAlgorithmWithIllinoisRetirementSubtraction(boolean couple) {
        var plan = FloridaProductionProjectionTest.plan(couple, StateTaxJurisdiction.IL);
        for (var person : plan.getHousehold().peopleByOwner().values()) {
            for (var income : List.copyOf(person.getIncomeSources())) {
                if (income instanceof com.daviddunn.retirementplanner.domain.income.Pension) person.removeIncomeSource(income);
            }
        }
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                RothConversionFrequency.ONE_TIME));
        var il = new ProjectionEngine().project(plan);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var fl = new ProjectionEngine().project(plan);
        for (int index = 0; index < il.getYears().size(); index++) {
            assertEquals(0, il.getYearAt(index).getStateIncomeTax().signum());
            assertEquals(fl.getYearAt(index).getRothConversion(), il.getYearAt(index).getRothConversion());
            assertEquals(fl.getYearAt(index).getEndingInvestableAssets(), il.getYearAt(index).getEndingInvestableAssets());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void largeRequestedConversionIsLimitedToActualSameOwnerCapacity(boolean couple) {
        var plan = FloridaProductionProjectionTest.plan(couple, StateTaxJurisdiction.IL);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("5000000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(year.getRothConversion().signum() > 0);
        assertTrue(year.getRothConversion().compareTo(year.getRequestedRothConversion()) < 0);
        var actual = year.getIncomeSources().stream().filter(source ->
                source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION).toList();
        assertEquals(0, actual.stream().map(IncomeSourceAttribution::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(year.getRothConversion()));
        assertTrue(actual.stream().allMatch(source -> source.owner().equals(
                Optional.of(source.destination().orElseThrow().owner()))));
        if (couple) assertTrue(actual.stream().anyMatch(source -> source.owner().orElseThrow() == AccountOwnership.SPOUSE));
        assertTrue(year.getRmdDistributedInProjection().signum() > 0);
        // AGI exceeds the exemption ceiling; conservative pension is the only IL base.
        var pension = year.getIncomeSources().stream().filter(source -> source.kind() == IncomeSourceAttribution.Kind.PENSION)
                .map(source -> source.modeledTaxableAmount().orElseThrow()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(pension.multiply(new BigDecimal("0.0495")).setScale(2, RoundingMode.HALF_UP), year.getStateIncomeTax());
    }
}
