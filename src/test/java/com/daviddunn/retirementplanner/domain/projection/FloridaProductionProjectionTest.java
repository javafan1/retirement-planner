package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FloridaProductionProjectionTest {
    static RetirementPlan plan(boolean couple, StateTaxJurisdiction state) {
        var plan = SinglePersonCoreProjectionTest.plan(couple,
                couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
        return plan;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void floridaProjectionPreservesOwnersOpeningRmdAndFixedConversions(boolean couple) {
        var mi = new ProjectionEngine().project(plan(couple, StateTaxJurisdiction.MI));
        var fl = new ProjectionEngine().project(plan(couple, StateTaxJurisdiction.FL));
        for (int i = 0; i < fl.getYears().size(); i++) {
            var a = mi.getYearAt(i);
            var b = fl.getYearAt(i);
            assertEquals(StateTaxJurisdiction.FL, b.getStateTaxJurisdiction());
            assertEquals(0, b.getStateIncomeTax().signum());
            assertEquals(b.getFederalIncomeTax(), b.getTotalIncomeTax());
            assertTrue(b.michiganTaxCalculation().isEmpty());
            assertEquals(a.getFederalIncomeTax(), b.getFederalIncomeTax()); // ample taxable/guaranteed cash
            assertEquals(a.getRequiredMinimumDistribution(), b.getRequiredMinimumDistribution());
            assertEquals(a.getRmdDistributedBeforeProjection(), b.getRmdDistributedBeforeProjection());
            assertEquals(a.getRothConversion(), b.getRothConversion());
            assertEquals(a.getSocialSecurityResult(), b.getSocialSecurityResult());
            assertTrue(b.getEndingInvestableAssets().compareTo(a.getEndingInvestableAssets()) > 0);
            assertTrue(b.getIncomeSources().stream().anyMatch(s -> s.kind() == IncomeSourceAttribution.Kind.RMD));
            assertTrue(b.getIncomeSources().stream().anyMatch(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION));
            if (couple) assertTrue(b.getIncomeSources().stream().anyMatch(s -> s.owner().orElse(null) == AccountOwnership.SPOUSE));
            else assertTrue(b.getIncomeSources().stream().noneMatch(s -> s.owner().orElse(null) == AccountOwnership.SPOUSE));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void saveReloadCopyAndLegacyAbsentFieldPreserveTheSelectedState(boolean couple, @TempDir Path directory) throws Exception {
        var repository = new JsonRetirementPlanRepository();
        for (var state : StateTaxJurisdiction.values()) {
            var original = plan(couple, state);
            Path file = directory.resolve(state.name() + ".json");
            repository.save(original, file);
            var loaded = repository.load(file);
            assertEquals(state, loaded.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            assertEquals(state, new RetirementPlanScenarioCopyService().copy(loaded)
                    .getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            assertEquals(financialRows(new ProjectionEngine().project(original)), financialRows(new ProjectionEngine().project(loaded)));
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var json = mapper.readTree(Files.readString(file));
            ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("planningAssumptions")).remove("stateTaxAssumptions");
            Files.writeString(file, mapper.writeValueAsString(json));
            var legacy = repository.load(file);
            assertEquals(StateTaxJurisdiction.MI, legacy.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            assertEquals(financialRows(new ProjectionEngine().project(plan(couple, StateTaxJurisdiction.MI))),
                    financialRows(new ProjectionEngine().project(legacy)));
        }
    }

    @Test
    void explicitInvalidStateFailsLoadRatherThanBecomingZero(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("invalid.json");
        var repository = new JsonRetirementPlanRepository();
        repository.save(plan(false, StateTaxJurisdiction.FL), file);
        String json = Files.readString(file);
        for (String invalid : List.of("CA", "UNKNOWN")) {
            Files.writeString(file, json.replace("\"FL\"", "\"" + invalid + "\""));
            assertThrows(java.io.IOException.class, () -> repository.load(file));
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("planningAssumptions")).putNull("stateTaxAssumptions");
        Files.writeString(file, mapper.writeValueAsString(tree));
        assertThrows(java.io.IOException.class, () -> repository.load(file));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = StateTaxJurisdiction.class, names = {"FL", "IL"})
    void savedBaselineKeepsItsStateWhenCurrentPlanChangesState(StateTaxJurisdiction state) {
        var plan = plan(true, state);
        var expected = new ProjectionEngine().project(plan);
        var baseline = com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory.create(plan, state + " baseline");
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.michigan()));
        var result = new com.daviddunn.retirementplanner.domain.baseline.BaselineProjectionService(new ProjectionEngine()).projectBaseline(baseline);
        assertEquals(financialRows(expected), financialRows(result));
        assertTrue(result.getYears().stream().allMatch(y -> y.getStateTaxJurisdiction() == state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void floridaBracketFillUsesFederalTargetAndSelectedStateFunding(boolean couple) throws Exception {
        var plan = plan(couple, StateTaxJurisdiction.FL);
        var person = plan.getHousehold().getPrimaryPerson();
        for (var income : List.copyOf(person.getIncomeSources())) {
            if (income instanceof com.daviddunn.retirementplanner.domain.income.Pension) person.removeIncomeSource(income);
        }
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(year.getRothConversion().signum() > 0);
        assertEquals(0, year.getStateIncomeTax().signum());
        assertTrue(year.getFederalIncomeTax().signum() > 0);
        var rules = new com.daviddunn.retirementplanner.domain.tax.GovernmentRuleProjectionService().project(
                new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                plan.getPlanningAssumptions(), 2027);
        var target = new RothConversionTargetBracketResolver().resolve(RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                rules.getFederalTaxRules(couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE),
                rules.getFederalTaxRules(couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE)).getUpperBound();
        assertTrue(year.getFederalTaxableIncome().subtract(target).abs().compareTo(new BigDecimal("0.01")) <= 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void largePartialAndMultiOwnerConversionRemainsCapacityLimited(boolean couple) {
        var plan = plan(couple, StateTaxJurisdiction.FL);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("5000000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var first = new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(first.getRothConversionShortfall().signum() > 0);
        assertEquals(0, first.getStateIncomeTax().signum());
        assertTrue(first.getFederalIncomeTax().signum() > 0);
        if (couple) assertTrue(first.getSpouseRothConversion().signum() > 0);
        else assertTrue(first.spouseRothConversion().isEmpty());
        var kinds = first.getIncomeSources().stream().map(IncomeSourceAttribution::kind).toList();
        assertTrue(kinds.indexOf(IncomeSourceAttribution.Kind.RMD) < kinds.indexOf(IncomeSourceAttribution.Kind.ROTH_CONVERSION));
    }

    @Test
    void csvNamesFloridaAndReportsCalculatedZero(@TempDir Path directory) throws Exception {
        var projection = new ProjectionEngine().project(plan(false, StateTaxJurisdiction.FL));
        Path file = directory.resolve("florida.csv");
        new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), file);
        var lines = Files.readAllLines(file);
        var header = Arrays.asList(lines.getFirst().split(","));
        for (String line : lines.subList(1, lines.size())) {
            var row = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            assertEquals(header.size(), row.length);
            assertEquals("FL", row[header.indexOf("State of Residence")]);
            assertEquals(0, new BigDecimal(row[header.indexOf("State Income Tax")]).signum());
            assertEquals("", row[header.indexOf("Michigan Retirement Deduction")]);
        }
    }

    @Test
    void pdfIncludesSelectedStateMethodAndAnnualZero(@TempDir Path directory) throws Exception {
        var plan = plan(false, StateTaxJurisdiction.FL);
        var projection = new ProjectionEngine().project(plan);
        var charts = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(projection.getYears(), List.of(),
                com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()),
                plan.getAccountPortfolio().getAccounts());
        var report = new com.daviddunn.retirementplanner.app.export.ProjectionPdfReport("Florida fixture", charts,
                BigDecimal.ZERO, projection.getYears().getLast().getAfterTaxEstateValue(), null);
        Path file = directory.resolve("florida.pdf");
        new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter().export(plan, projection, List.of(), report, file);
        try (var document = org.apache.pdfbox.Loader.loadPDF(file.toFile())) {
            var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("State of Residence"));
            assertTrue(text.contains("Florida State Income Tax"));
            assertTrue(text.contains("$0 (no individual income tax)"));
            assertTrue(text.contains("STATE INCOME TAX - FULL-YEAR RESIDENCY"));
            assertTrue(text.contains("Simplified estimate"));
            assertFalse(text.contains("Michigan Income Tax"));
        }
    }

    static String financialRows(Projection projection) {
        return projection.getYears().stream().map(y -> y.getCalendarYear() + "|" + y.getFederalIncomeTax() + "|"
                + y.getStateIncomeTax() + "|" + y.getRothConversion() + "|" + y.getRequiredMinimumDistribution() + "|"
                + y.getPortfolioWithdrawal() + "|" + y.getEndingInvestableAssets() + "|" + y.getAfterTaxEstateValue())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
