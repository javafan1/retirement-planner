package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.fixture;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.policy;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLowIncomeDiagnostic.DeathYear.*;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLowIncomeDiagnostic.Pass.*;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLowIncomeDiagnostic.RothPath.*;
import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.Bound.*;
import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.FederalCash.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasLowIncomeDiagnosticsTest {
    private static final LocalDate START = LocalDate.of(2028, 1, 1);

    @ParameterizedTest
    @CsvSource({"0, true, 0, 0, true", "2028, false, 0, 0, false", "2028, true, 0, 0, false",
            "0, true, 70000, 10000, true", "2028, false, 70000, 40000, true", "2028, true, 70000, 0, false"})
    void matchedDeathAndPolicyCases(int death, boolean policy, String expense, String spendingWithdrawal, boolean completes) {
        var engine = ProjectionEngine.forArkansasProduction();
        var plan = plan(death, policy, "2500", expense);
        var reports = new ArrayList<ArkansasLowIncomeDiagnostic>();
        if (completes) {
            var year = completed(engine, plan, reports).getFirstYear();
            money(spendingWithdrawal, year.getHouseholdCashSettlement().spendingWithdrawal());
            money(death == 0 ? "60000" : "30000", year.getGuaranteedIncome());
            assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, year.arkansasValidationTax().orElseThrow().filingStatus());
            assertTrue(reports.isEmpty());
            if (!expense.equals("0")) {
                assertTrue(year.getIncomeSources().stream().anyMatch(source -> source.owner().filter(SPOUSE::equals).isPresent()
                        && source.purpose() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING));
            }
        } else {
            var report = refuse(engine, plan, reports);
            assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, report.filingStatus());
            assertEquals(FIRST_DEATH_YEAR, report.deathYear());
            assertEquals(policy, report.lifePrincipalRecognized());
            assertEquals(FAIL, report.section301Gross());
            assertEquals(FAIL, report.section501Net());
            assertEquals(INITIAL, report.pass());
            assertEquals(FIXED, report.rothPath());
            assertEquals(ArkansasTaxIssue.Withdrawal.ZERO, report.fundingProbe().withdrawal());
            assertEquals(COVERS_FEDERAL_TAX, report.fundingProbe().cash());
        }
    }

    @Test
    void grossOnlyAndNetOnlyAreSeparateAndAnUnpaidDefinitionIsNotAReceipt() {
        var engine = ProjectionEngine.forArkansasProduction();
        var gross = plan(2028, true, "0", "0");
        gross.getHousehold().getSpouse().setBirthDate(LocalDate.of(1970, 6, 15));
        roth(gross, RothConversionStrategy.FIXED_AMOUNT, "38000");
        var grossReport = refuse(engine, gross, new ArrayList<>());
        assertTrue(grossReport.lifePrincipalRecognized());
        assertEquals(FAIL, grossReport.section301Gross());
        assertEquals(PASS, grossReport.section501Net());

        var net = plan(2028, true, "2500", "0");
        roth(net, RothConversionStrategy.FIXED_AMOUNT, "10000");
        var netReport = refuse(engine, net, new ArrayList<>());
        assertEquals(PASS, netReport.section301Gross());
        assertEquals(FAIL, netReport.section501Net());

        var later = refuse(engine, plan(2027, true, "2500", "0"), new ArrayList<>());
        assertEquals(FilingStatus.SINGLE, later.filingStatus());
        assertEquals(LATER_SURVIVOR_YEAR, later.deathYear());
        assertFalse(later.lifePrincipalRecognized());
        assertEquals(PASS, later.section301Gross());
        assertEquals(FAIL, later.section501Net());

        var future = plan(2029, true, "2500", "0");
        var smaller = new Pension("Synthetic employer pension", PRIMARY, START, null, new BigDecimal("2500"), ZERO);
        smaller.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        future.getHousehold().getPrimaryPerson().setIncomeSources(List.of(smaller));
        assertEquals(BEFORE_FIRST_DEATH, refuse(engine, future, new ArrayList<>()).deathYear());
    }

    @Test
    void reportsTargetInnerProbeWithOutwardReason() {
        var plan = plan(2028, true, "2500", "0");
        plan.getAccountPortfolio().getAccounts().getLast().setCurrentBalance(new BigDecimal("10000"));
        roth(plan, RothConversionStrategy.FILL_12_PERCENT_BRACKET, "0");
        var report = refuse(ProjectionEngine.forArkansasProduction(), plan, new ArrayList<>());
        assertEquals(TARGET, report.rothPath());
        assertEquals(ArkansasLowIncomeDiagnostic.Stage.INNER_EXACT, report.stage());
        assertEquals(ArkansasProjectionUnavailableException.Reason.TARGET_PROOF, report.reason());
        assertEquals(PASS, report.section301Gross());
        assertEquals(FAIL, report.section501Net());
    }

    @Test
    void medicareSpendingMovesAnInitialZeroEstimateIntoTheLowIncomeRegion() {
        var engine = ProjectionEngine.forArkansasProduction();
        var plan = plan(2028, true, "1620", "119440");
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1962, 6, 15));
        var finalPass = refuse(engine, plan, new ArrayList<>());
        assertEquals(MEDICARE_AWARE, finalPass.pass());
        assertEquals(FIRST_DEATH_YEAR, finalPass.deathYear());
        assertTrue(finalPass.lifePrincipalRecognized());
        assertEquals(FAIL, finalPass.section301Gross());
        assertEquals(FAIL, finalPass.section501Net());
        assertEquals(ArkansasTaxIssue.Withdrawal.ZERO, finalPass.fundingProbe().withdrawal());

        var younger = plan(2028, true, "1620", "119440");
        var complete = completed(engine, younger, new ArrayList<>()).getFirstYear();
        money("0", complete.getStateIncomeTax());
        money("0", complete.getHouseholdCashSettlement().spendingWithdrawal());
    }

    @Test
    void unaffordableZeroFundingProbeSurfacesAndOnlySafeFieldsAreEmitted() {
        var plan = plan(2028, true, "2500", "130000");
        roth(plan, RothConversionStrategy.FIXED_AMOUNT, "11000");
        var engine = ProjectionEngine.forArkansasProduction();
        var disabled = assertThrows(ArkansasProjectionUnavailableException.class, () ->
                engine.projectArkansasWithOutcome(plan, AnalysisCancellationToken.none(), count -> fail("No completed year")));
        assertTrue(disabled.issue().lowIncomeEvidence().isEmpty());
        var report = refuse(engine, plan, new ArrayList<>());
        assertEquals(disabled.reason(), report.reason());
        assertEquals("""
                phase=P3D3C2
                status=COMPLETE_REFUSAL
                year=2028
                filing=MARRIED_FILING_JOINTLY
                deathYear=FIRST_DEATH_YEAR
                mode=BASIC
                rothPath=FIXED
                pass=INITIAL
                stage=INNER_EXACT
                probe=EXACT
                candidate=EXPLORATORY
                fundingWithdrawal=ZERO
                pointFederalCash=BELOW_FEDERAL_TAX
                lifePrincipalRecognized=true
                section301Gross=PASS
                section501Net=FAIL
                reason=SOURCE_OR_TAX
                issue=LOW_INCOME
                """, report.safeText());
    }

    @Test
    void optInSuccessLeavesAnnualEvidenceAndFrozenInputsIdentical() {
        var engine = ProjectionEngine.forArkansasProduction();
        var plan = plan(2028, true, "2500", "0");
        roth(plan, RothConversionStrategy.FILL_12_PERCENT_BRACKET, "0");
        var before = ArkansasLifePrincipalProjectionTest.run(plan);
        var reports = new ArrayList<ArkansasLowIncomeDiagnostic>();
        var after = completed(engine, plan, reports);
        var owner = UUID.randomUUID();
        var beforeSnapshot = CompleteProjectionSnapshot.captureArkansas(owner, plan, before, Instant.EPOCH);
        var afterSnapshot = CompleteProjectionSnapshot.captureArkansas(owner, plan, after, Instant.EPOCH);
        assertEquals(beforeSnapshot.inputsDocument(), afterSnapshot.inputsDocument());
        assertEquals(beforeSnapshot.inputDigest(), afterSnapshot.inputDigest());
        assertEquals(beforeSnapshot.annual(), afterSnapshot.annual());
        assertEquals(beforeSnapshot.provenance(), afterSnapshot.provenance());
        assertTrue(reports.isEmpty());
        money("103000", after.getFirstYear().getRothConversion());
        assertEquals(before.getFirstYear().arkansasValidationTax().orElseThrow().targetMetrics(),
                after.getFirstYear().arkansasValidationTax().orElseThrow().targetMetrics());
    }

    @Test
    void concurrentAndCancelledRunsCannotLeakDiagnostics() throws Exception {
        var engine = ProjectionEngine.forArkansasProduction();
        var start = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return refuse(engine, plan(2028, true, "2500", "0"), new ArrayList<>());
            });
            var second = workers.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return refuse(engine, plan(2027, false, "2500", "0"), new ArrayList<>());
            });
            assertEquals(FIRST_DEATH_YEAR, first.get(10, TimeUnit.SECONDS).deathYear());
            assertEquals(LATER_SURVIVOR_YEAR, second.get(10, TimeUnit.SECONDS).deathYear());
        }
        var traces = new ArrayList<ArkansasLowIncomeDiagnostic>();
        var stack = StackWalker.getInstance();
        assertThrows(AnalysisCancelledException.class, () -> engine.projectArkansasWithOutcome(
                plan(2028, true, "2500", "0"), () -> stack.walk(frames -> frames.anyMatch(frame ->
                        frame.getClassName().equals("com.daviddunn.retirementplanner.domain.tax.ArkansasMinimumFundingSolver"))),
                count -> fail("No completed year"), Optional.of(traces::add)));
        assertTrue(traces.isEmpty());
        assertTrue(refuse(engine, plan(2028, true, "2500", "0"), traces).lifePrincipalRecognized());
    }

    private static RetirementPlan plan(int death, boolean benefit, String survivor, String expense) {
        var plan = fixture(START, 1, death == 0 ? DeathScenario.BOTH_SURVIVE : DeathScenario.PRIMARY_DIES, death, "0");
        plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(ZERO);
        var pension = new Pension("Synthetic employer pension", PRIMARY, START.minusYears(10), null,
                new BigDecimal("5000"), ZERO, new BigDecimal(survivor));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        if (benefit) plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "100000", null, null)));
        if (!expense.equals("0")) plan.getHousehold().addExpense(new Expense("Synthetic spending", new BigDecimal(expense)));
        return plan;
    }

    private static void roth(RetirementPlan plan, RothConversionStrategy strategy, String amount) {
        plan.getAccountPortfolio().addAccount(new RothIRA("Synthetic Roth", SPOUSE, ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2028, new BigDecimal(amount),
                RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME));
    }

    private static ArkansasLowIncomeDiagnostic refuse(ProjectionEngine engine, RetirementPlan plan,
            List<ArkansasLowIncomeDiagnostic> reports) {
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> engine.projectArkansasWithOutcome(
                plan, AnalysisCancellationToken.none(), year -> fail("No completed year"), Optional.of(reports::add)));
        assertEquals(ArkansasTaxIssue.Kind.LOW_INCOME, failure.issue().kind(), failure.detail());
        assertEquals(1, reports.size());
        assertEquals(failure.reason(), reports.getFirst().reason());
        return reports.getFirst();
    }

    private static Projection completed(ProjectionEngine engine, RetirementPlan plan, List<ArkansasLowIncomeDiagnostic> reports) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class, engine.projectArkansasWithOutcome(plan,
                AnalysisCancellationToken.none(), count -> { }, Optional.of(reports::add))).projection();
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), actual::toPlainString);
    }
}
