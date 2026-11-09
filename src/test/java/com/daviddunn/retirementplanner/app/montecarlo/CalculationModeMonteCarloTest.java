package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;

class CalculationModeMonteCarloTest {
    @org.junit.jupiter.api.Test void mixedLegacyInputsGetOneCommonBasicPolicyWithIndependentBeforeAfterOracle() {
        var a=NonqualifiedAnnuityFinancialTest.fixture(true);a.setConditionalBenefits(List.of());
        var b=NonqualifiedAnnuityFinancialTest.fixture(true);
        for(var plan:List.of(a,b))plan.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),
                RothConversionStopRule.NEVER,RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ONE_TIME));
        var engine=new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine();
        assertEquals(0,new BigDecimal("48085.60").compareTo(engine.project(a).getYearAt(0).getFederalIncomeTax()));
        assertEquals(0,new BigDecimal("49686.88").compareTo(engine.project(b).getYearAt(0).getFederalIncomeTax()));
        var settings=MonteCarloSettings.forPlan(a,2,417,BigDecimal.ZERO);
        var result=new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison(a,b,settings,false,2032),PROGRESS,NEVER);
        for(var outcome:result.outcomes()) {
            assertEquals(0,new BigDecimal("48085.60").compareTo(outcome.outcomeA().terminal().orElseThrow().lifetimeTaxes()));
            assertEquals(0,new BigDecimal("49468").compareTo(outcome.outcomeB().terminal().orElseThrow().lifetimeTaxes()));
            assertEquals(0,new BigDecimal("-41817.60").compareTo(outcome.outcomeA().terminal().orElseThrow().endingInvestableAssets()
                    .subtract(outcome.outcomeB().terminal().orElseThrow().endingInvestableAssets())));
        }
        assertNull(a.getFinancialCalculationMode());assertNull(b.getFinancialCalculationMode());
    }

    @org.junit.jupiter.api.Test void basicModeAlonePreservesSuppliedWorldExecutionAndAuthenticatedSourceBinding() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setConditionalBenefits(List.of());plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);
        var settings=MonteCarloSettings.forPlan(plan,2,417,BigDecimal.ZERO);
        var request=comparison(plan,new RetirementPlanScenarioCopyService().copy(plan),settings,false,2032);
        var result=new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request,request.worldSource(),PROGRESS,NEVER);
        assertEquals(0,result.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
        var time=Instant.parse("2026-11-08T15:00:00Z");
        var run=new MonteCarloStrategyComparisonRun(result,MonteCarloMode.FIXED_LIFESPAN,"Original",0);
        assertThrows(IllegalArgumentException.class,()->MonteCarloSnapshotPresentationMapper.capture(run,time,time.plusSeconds(1)));
        var payload=MonteCarloSnapshotPresentationMapper.capture(run,time,time.plusSeconds(1),"a".repeat(64));
        var envelope=StandaloneExpenseCapabilityTest.envelope(payload);var codec=new AnalysisSnapshotCodec();
        assertEquals(envelope,codec.decode(codec.encode(envelope)));
    }
    @org.junit.jupiter.api.Test void legacyPairedV11RemainsReadableButCannotClaimNewCommonModeExecution() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);
        var settings=MonteCarloSettings.forPlan(plan,2,417,BigDecimal.ZERO);
        var request=comparison(plan,new RetirementPlanScenarioCopyService().copy(plan),settings,false,2032);
        var result=new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request,PROGRESS,NEVER);
        var time=Instant.parse("2026-11-08T15:00:00Z");
        var written=MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,MonteCarloMode.FIXED_LIFESPAN,"Original",0),time,time.plusSeconds(1));
        // Synthetic historical control: both legacy candidates have identical annuity facts,
        // zero return and taxable income below the standard deduction; old/new NIIT is zero.
        var legacy=ConditionalBenefitAnalysisExecution.captureLegacy(written.kind(),written.request().planA(),written.request().planB());
        var original=new MonteCarloSnapshot(11,written.mode(),written.hasSpouse(),written.settings(),written.request(),written.provenance(),
                written.fixed(),written.mortality(),written.paired(),written.display(),written.startedAt(),written.completedAt(),
                written.expenseExecution(),written.pairedExpenseExecution(),legacy);
        assertEquals(12,original.fingerprintVersion());assertEquals(4,legacy.version());
        var envelope=StandaloneExpenseCapabilityTest.envelope(original);var codec=new AnalysisSnapshotCodec();
        assertEquals(envelope,codec.decode(codec.encode(envelope)));
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED,new MonteCarloFingerprintService().compatibility(envelope,
                new MonteCarloFingerprintService.Inputs(written.kind(),written.settings(),written.request())).state());
    }
    static Stream<Arguments> configurations() {
        var cases=new java.util.ArrayList<Arguments>();
        for(var mode:FinancialCalculationMode.values()) for(boolean couple:new boolean[]{false,true})
            for(boolean longevity:new boolean[]{false,true}) for(boolean paired:new boolean[]{false,true})
                for(boolean expenses:new boolean[]{false,true}) for(boolean annuity:new boolean[]{false,true})
                    cases.add(Arguments.of(mode,couple,longevity,paired,expenses,annuity));
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("configurations")
    void bothPoliciesAllEightGeneratedModesAndExpenseWorlds(FinancialCalculationMode mode, boolean couple,
            boolean longevity, boolean paired, boolean expenses, boolean annuity) throws Exception {
        var a=NonqualifiedAnnuityFinancialTest.fixture(couple);
        a.setFinancialCalculationMode(mode);
        if(!annuity)a.setConditionalBenefits(List.of());
        a.getAccountPortfolio().getAccounts().get(1).setCurrentBalance(new BigDecimal("10000000"));
        a.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),
                RothConversionStopRule.NEVER,RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ONE_TIME));
        if(mode==FinancialCalculationMode.BASIC)
            a.getAccountPortfolio().addAccount(new BrokerageAccount("Positive taxable brokerage",AccountOwnership.PRIMARY,new BigDecimal("500000")));
        if(expenses) a.getHousehold().addExpense(new Expense("Ordinary volatile spending",new BigDecimal("12000"),
                GrowthCategory.GENERAL,null,null,ExpenseType.RECURRING,new BigDecimal(".25")));
        var settings=MonteCarloSettings.forPlan(a,2,417,mode==FinancialCalculationMode.BASIC?new BigDecimal(".12"):BigDecimal.ZERO);
        MonteCarloSnapshot payload;
        var time=Instant.parse("2026-11-08T15:00:00Z");
        if(paired) {
            var b=new RetirementPlanScenarioCopyService().copy(a);
            b.setFinancialCalculationMode(mode==FinancialCalculationMode.BASIC?FinancialCalculationMode.ADVANCED:FinancialCalculationMode.BASIC);
            var request=comparison(a,b,settings,longevity,2032);
            var analyzer=new MonteCarloStrategyComparisonAnalyzer();
            var result=analyzer.analyzeComparison(request,PROGRESS,NEVER);
            assertEquals(result.outcomes(),analyzer.analyzeComparison(request,PROGRESS,NEVER).outcomes());
            assertEquals(0,result.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
            payload=MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                    longevity?MonteCarloMode.LONGEVITY_ADJUSTED:MonteCarloMode.FIXED_LIFESPAN,"Original",0),time,time.plusSeconds(1));
            assertEquals(b.getFinancialCalculationMode(),MonteCarloPlanDocument.read(payload.request().planB()).getFinancialCalculationMode());
            assertEquals(13,payload.version()); assertEquals(14,payload.fingerprintVersion());
        } else {
            var service=new MonteCarloRunService();
            var run=longevity?service.runMortality(a,mortality(a,settings),PROGRESS,NEVER):service.run(a,settings,null,PROGRESS,NEVER);
            payload=MonteCarloSnapshotPresentationMapper.capture(run,a,time,time.plusSeconds(1));
            assertEquals(12,payload.version()); assertEquals(13,payload.fingerprintVersion());
        }
        assertEquals(5,payload.benefitExecution().version()); assertEquals(mode,payload.benefitExecution().calculationMode());
        var original=payload;
        assertThrows(IllegalArgumentException.class,()->new MonteCarloSnapshot(original.paired()==null?10:11,
                original.mode(),original.hasSpouse(),original.settings(),original.request(),original.provenance(),original.fixed(),
                original.mortality(),original.paired(),original.display(),original.startedAt(),original.completedAt(),
                original.expenseExecution(),original.pairedExpenseExecution(),original.benefitExecution()));
        assertThrows(IllegalArgumentException.class,()->new ConditionalBenefitAnalysisExecution(5,
                mode==FinancialCalculationMode.BASIC?ConditionalBenefitAnalysisExecution.ADVANCED_MODEL:ConditionalBenefitAnalysisExecution.BASIC_MODEL,
                original.kind(),original.benefitExecution().scenarioProtocol(),original.benefitExecution().candidates()));
        var snapshot=StandaloneExpenseCapabilityTest.envelope(payload);
        var codec=new AnalysisSnapshotCodec(); assertEquals(snapshot,codec.decode(codec.encode(snapshot)));
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,new MonteCarloFingerprintService().compatibility(snapshot,
                new MonteCarloFingerprintService.Inputs(payload.kind(),payload.settings(),payload.request())).state());
        assertTrue(payload.benefitExecution().disclosure().stream().anyMatch(s -> s.contains(mode.label())));
    }
}
