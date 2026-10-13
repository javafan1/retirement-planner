package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class NoIncomeTaxProductionProjectionTest {
    static final List<StateTaxJurisdiction> STATES = List.of(StateTaxJurisdiction.AK, StateTaxJurisdiction.NV,
            StateTaxJurisdiction.NH, StateTaxJurisdiction.SD, StateTaxJurisdiction.TN, StateTaxJurisdiction.TX, StateTaxJurisdiction.WY);
    static Stream<Arguments> households() {
        return STATES.stream().flatMap(state -> Stream.of(false, true).map(couple -> Arguments.of(state, couple)));
    }

    @Test void recordRepresentativeFloridaEquivalenceWithoutRegeneratingExpectations() throws Exception {
        var expected = new ProjectionEngine().project(FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.FL));
        var lines = new ArrayList<String>();
        lines.add("State,Year,FederalTax,StateTax,TotalTax,TaxFunding,RothConversion,RMD,EndingAssets,NetWorth,AfterTaxEstate");
        var states = new ArrayList<StateTaxJurisdiction>(); states.add(StateTaxJurisdiction.FL); states.addAll(STATES);
        for (var state : states) {
            var result = new ProjectionEngine().project(FloridaProductionProjectionTest.plan(true, state));
            sameFinancialFacts(expected, result, state);
            var y = result.getYears().getLast();
            lines.add(state.name() + "," + y.getCalendarYear() + "," + y.getFederalIncomeTax() + "," + y.getStateIncomeTax()
                    + "," + y.getTotalIncomeTax() + "," + y.getTaxFundingWithdrawal() + "," + y.getRothConversion()
                    + "," + y.getRequiredMinimumDistribution() + "," + y.getEndingInvestableAssets() + "," + y.getEndingInvestableAssets()
                    + "," + y.getAfterTaxEstateValue());
        }
        // Review artifact only. Independent/frozen expectations elsewhere are never replaced.
        Files.write(Path.of("target/phase6d4-florida-equivalence.csv"), lines);
    }

    /** Compare every monetary year getter, not just a selected balance or tax total. */
    static void sameFinancialFacts(Projection expected, Projection actual, StateTaxJurisdiction state) throws Exception {
        assertEquals(expected.getYears().size(), actual.getYears().size());
        var getters = Arrays.stream(ProjectionYear.class.getMethods()).filter(method -> method.getParameterCount() == 0
                && method.getReturnType() == BigDecimal.class).toList();
        for (int i = 0; i < expected.getYears().size(); i++) {
            var a = expected.getYearAt(i); var b = actual.getYearAt(i);
            assertEquals(state, b.getStateTaxJurisdiction());
            assertEquals(StateTaxCalculationStatus.CALCULATED, b.stateTaxCalculation().orElseThrow().calculationStatus());
            for (var getter : getters) {
                try { assertEquals(getter.invoke(a), getter.invoke(b), state + ": " + a.getCalendarYear() + " " + getter.getName()); }
                catch (java.lang.reflect.InvocationTargetException unavailable) {
                    assertInstanceOf(IllegalStateException.class, unavailable.getCause());
                    var same = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> getter.invoke(b));
                    assertInstanceOf(IllegalStateException.class, same.getCause());
                }
            }
            assertEquals(a.getSocialSecurityResult(), b.getSocialSecurityResult());
            assertEquals(a.getIncomeSources().stream().map(s -> s.kind() + "/" + s.owner() + "/" + s.grossAmount()).toList(),
                    b.getIncomeSources().stream().map(s -> s.kind() + "/" + s.owner() + "/" + s.grossAmount()).toList());
        }
    }

    @ParameterizedTest(name = "{0}, couple={1}: FL-equivalent opening RMD / ordinary / Roth flows")
    @MethodSource("households")
    void allSevenMatchFloridaWithNoFixedBracketAndMultiOwnerConversions(StateTaxJurisdiction state, boolean couple) throws Exception {
        for (var strategy : List.of("NONE", "FIXED", "BRACKET", "PARTIAL")) {
            var reference = FloridaProductionProjectionTest.plan(couple, StateTaxJurisdiction.FL);
            reference.setRothConversionRequest(new RothConversionRequest(!strategy.equals("NONE"), 2027,
                    new BigDecimal(strategy.equals("PARTIAL") ? "5000000" : "30000"), RothConversionStopRule.NEVER,
                    strategy.equals("BRACKET") ? RothConversionStrategy.FILL_22_PERCENT_BRACKET : RothConversionStrategy.FIXED_AMOUNT,
                    RothConversionFrequency.ONE_TIME));
            var candidate = new RetirementPlanScenarioCopyService().copy(reference);
            candidate.setPlanningAssumptions(candidate.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
            var baseline = new ProjectionEngine().project(reference);
            var result = new ProjectionEngine().project(candidate);
            sameFinancialFacts(baseline, result, state);
            assertTrue(result.getYears().getFirst().getIncomeSources().stream().anyMatch(s -> s.kind() == IncomeSourceAttribution.Kind.RMD));
            if (strategy.equals("PARTIAL") && couple) assertTrue(result.getYearAt(0).getSpouseRothConversion().signum() > 0);
        }
    }

    @ParameterizedTest @MethodSource("households")
    void saveReloadScenarioCopyLegacyAndExportsKeepStateIdentity(StateTaxJurisdiction state, boolean couple,
            @TempDir Path directory) throws Exception {
        var plan = FloridaProductionProjectionTest.plan(couple, state);
        var repository = new JsonRetirementPlanRepository();
        var file = directory.resolve("plan.json"); repository.save(plan, file);
        var loaded = repository.load(file);
        var copied = new RetirementPlanScenarioCopyService().copy(loaded);
        assertEquals(state, copied.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        var projection = new ProjectionEngine().project(plan);
        sameFinancialFacts(projection, new ProjectionEngine().project(copied), state);
        var csv = directory.resolve("projection.csv");
        new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), csv);
        var lines = Files.readAllLines(csv); var header = Arrays.asList(lines.getFirst().split(","));
        for (var line : lines.subList(1, lines.size())) {
            var row = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            assertEquals(state.name(), row[header.indexOf("State of Residence")]);
            assertEquals(0, new BigDecimal(row[header.indexOf("State Income Tax")]).signum());
        }
        var charts = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(projection.getYears(), List.of(),
                com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()), plan.getAccountPortfolio().getAccounts());
        var report = new com.daviddunn.retirementplanner.app.export.ProjectionPdfReport("State identity fixture", charts,
                BigDecimal.ZERO, projection.getYears().getLast().getAfterTaxEstateValue(), null);
        var pdf = directory.resolve("projection.pdf");
        new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter().export(plan, projection, List.of(), report, pdf);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains(state.displayName() + " State Income Tax"));
            assertTrue(text.contains("$0 (no individual income tax)"));
            assertFalse(text.contains("Florida State Income Tax"));
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = mapper.readTree(Files.readString(file));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("planningAssumptions")).remove("stateTaxAssumptions");
        Files.writeString(file, mapper.writeValueAsString(json));
        assertEquals(StateTaxAssumptions.michigan(), repository.load(file).getPlanningAssumptions().getStateTaxAssumptions());
    }

    @ParameterizedTest @EnumSource(value = StateTaxJurisdiction.class, names = {"AK", "NV", "NH", "SD", "TN", "TX", "WY"})
    void mixedInvestmentIncomeAndIraTaxFundingMatchFloridaExactly(StateTaxJurisdiction state) throws Exception {
        var household = new Household(new Person("Primary", "", LocalDate.of(1950, 1, 1)));
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var spending = new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(new ProjectedPortfolio(List.of(
                new ProjectedAccountBalance(account, account.getCurrentBalance()))), new BigDecimal("80000"), strategy);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var context = TaxHouseholdContext.capture(household, LocalDate.of(2026, 1, 1), null);
        var sources = Stream.of(IncomeSourceAttribution.Kind.INTEREST, IncomeSourceAttribution.Kind.DIVIDEND,
                IncomeSourceAttribution.Kind.CAPITAL_GAIN).map(kind -> IncomeSourceAttribution.aggregate(kind,
                        IncomeSourceAttribution.Purpose.INCOME, "Ordinary investment", new BigDecimal("10000"),
                        Optional.of(new BigDecimal("10000")))).toList();
        var outcomes = new ArrayList<TaxFundingResult>();
        var calls = new ArrayList<Integer>();
        var service = new ProductionStateTaxService();
        for (var selected : List.of(StateTaxJurisdiction.FL, state)) {
            var counted = new java.util.concurrent.atomic.AtomicInteger();
            var routed = new ProductionStateTaxService(Map.of(selected, request -> {
                counted.incrementAndGet();
                return service.calculate(request);
            }));
            outcomes.add(new TaxFundingCalculator(routed).calculate(household, context.distributionDate(), spending.getPortfolio(),
                    spending.getWithdrawalBreakdown(), strategy, com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE,
                    rules, BigDecimal.ZERO, new BigDecimal("30000"), null, null, BigDecimal.ZERO, null,
                    BigDecimal.ZERO, Optional.empty(), sources, context, StateTaxAssumptions.simplified(selected)));
            calls.add(counted.get());
        }
        var fl = outcomes.getFirst(); var candidate = outcomes.getLast();
        assertEquals(fl.getAdditionalWithdrawal(), candidate.getAdditionalWithdrawal());
        assertEquals(calls.getFirst(), calls.getLast(), "No extra state-tax funding iteration relative to Florida");
        assertTrue(candidate.getAdditionalWithdrawal().signum() > 0);
        assertEquals(fl.getFederalTaxCalculation().getFederalIncomeTax(), candidate.getFederalTaxCalculation().getFederalIncomeTax());
        assertEquals(fl.getTotalIncomeTax(), candidate.getTotalIncomeTax());
        assertEquals(BigDecimal.ZERO, candidate.getStateTaxCalculation().incomeTax());
        assertEquals(fl.getIncomeSources().stream().map(s -> s.kind() + "/" + s.grossAmount()).toList(),
                candidate.getIncomeSources().stream().map(s -> s.kind() + "/" + s.grossAmount()).toList());
        assertEquals(1, candidate.getIncomeSources().stream().filter(s -> s.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).count());
    }
}
