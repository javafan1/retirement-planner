package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;

class NonqualifiedAnnuityMonteCarloTest {
    static final Instant START=Instant.parse("2026-11-07T19:00:00Z");
    static com.daviddunn.retirementplanner.domain.model.RetirementPlan prepared(boolean couple) {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(couple);
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Base spending",new BigDecimal("60000")));
        return plan;
    }
    static void verify(MonteCarloSnapshot payload) throws Exception {
        // Standalone legacy requests retain V10. New paired writes use one common Phase 23E mode.
        assertEquals(payload.paired()==null?10:13,payload.version());
        assertEquals(payload.paired()==null?11:14,payload.fingerprintVersion());
        assertEquals(payload.paired()==null?4:5,payload.benefitExecution().version());
        var envelope=StandaloneExpenseCapabilityTest.envelope(payload);
        var codec=new AnalysisSnapshotCodec(); assertEquals(envelope,codec.decode(codec.encode(envelope)));
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,new MonteCarloFingerprintService().compatibility(envelope,
                new MonteCarloFingerprintService.Inputs(payload.kind(),payload.settings(),payload.request())).state());
        assertThrows(IllegalArgumentException.class,()->new ConditionalBenefitAnalysisExecution(3,ConditionalBenefitAnalysisExecution.PREMIUM_MODEL,
                payload.kind(),payload.benefitExecution().scenarioProtocol(),payload.benefitExecution().candidates()));
    }
    @ParameterizedTest @CsvSource({"true,false,false","true,false,true","true,true,false","true,true,true",
            "false,false,false","false,false,true","false,true,false","false,true,true"})
    void generatedStandaloneBothHouseholdsAndHorizonsWithExpenseVolatility(boolean couple,boolean longevity,boolean volatileExpense) throws Exception {
        var plan=prepared(couple);
        if(longevity) plan.getAccountPortfolio().getAccounts().get(1).setCurrentBalance(new BigDecimal("10000000"));
        if(volatileExpense) {
            plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Variable spending",new BigDecimal("12000"),
                    com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL,null,null,
                    com.daviddunn.retirementplanner.domain.financial.ExpenseType.RECURRING,new BigDecimal(".25")));
        }
        var settings=MonteCarloSettings.forPlan(plan,2,417,longevity?BigDecimal.ZERO:new BigDecimal(".12"));
        var service=new MonteCarloRunService();
        var run=longevity?service.runMortality(plan,mortality(plan,settings),PROGRESS,NEVER):service.run(plan,settings,null,PROGRESS,NEVER);
        var repeated=longevity?service.runMortality(plan,mortality(plan,settings),PROGRESS,NEVER):service.run(plan,settings,null,PROGRESS,NEVER);
        if(longevity) assertEquals(run.mortalityResult().outcomes(),repeated.mortalityResult().outcomes());
        else assertEquals(run.result(),repeated.result());
        verify(MonteCarloSnapshotPresentationMapper.capture(run,plan,START,START.plusSeconds(1)));
        var before=new MonteCarloWorldGenerator(mortality(plan,settings));
        var copy=new RetirementPlanScenarioCopyService().copy(plan); copy.setConditionalBenefits(List.of());
        var after=new MonteCarloWorldGenerator(mortality(copy,settings));
        assertEquals(before.generate(0).economicPath().annualReturns(),after.generate(0).economicPath().annualReturns());
        assertEquals(before.generate(0).lifetime(),after.generate(0).lifetime());
    }
    @ParameterizedTest @CsvSource({"true,false,IDENTICAL","true,true,IDENTICAL","false,false,IDENTICAL","false,true,IDENTICAL",
            "true,false,CURRENT","true,true,BASELINE","false,false,DIFFERENT","false,true,EXHAUSTED",
            "true,false,COEXIST","true,true,COEXIST","true,true,OTHER_ANNUITANT","true,false,DEFERRED"})
    void pairedCandidatesRetainIndependentAnnuityFactsAndSharedWorlds(boolean couple,boolean longevity,String configuration) throws Exception {
        var a=prepared(couple);
        if(longevity) a.getAccountPortfolio().getAccounts().get(1).setCurrentBalance(new BigDecimal("10000000"));
        var b=new RetirementPlanScenarioCopyService().copy(a);
        if(configuration.equals("CURRENT")) b.setConditionalBenefits(List.of());
        if(configuration.equals("BASELINE")) a.setConditionalBenefits(List.of());
        if(configuration.equals("DIFFERENT")) b.setConditionalBenefits(List.of(BenefitFixtures.change(b.getConditionalBenefits().getFirst(),"grossAmount",new BigDecimal("1500"))));
        if(configuration.equals("EXHAUSTED")) {
            var definition=b.getConditionalBenefits().getFirst();
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)BenefitFixtures.JSON.valueToTree(definition);
            ((com.fasterxml.jackson.databind.node.ObjectNode)node.get("annuityTerms")).put("openingInvestment",BigDecimal.ZERO);
            b.setConditionalBenefits(List.of(BenefitFixtures.JSON.treeToValue(node,com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.class)));
        }
        if(configuration.equals("OTHER_ANNUITANT")) b.setConditionalBenefits(List.of(BenefitFixtures.change(b.getConditionalBenefits().getFirst(),"recipient",
                com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference.SPOUSE)));
        if(configuration.equals("DEFERRED")) b.setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest.annuity(
                com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference.PRIMARY,
                java.time.LocalDate.of(2030,1,1),java.time.LocalDate.of(2030,7,1),"1200","720","10000")));
        if(configuration.equals("COEXIST")) for(var candidate:List.of(a,b)) {
            candidate.setConditionalBenefits(List.of(candidate.getConditionalBenefits().getFirst(),BenefitFixtures.change(
                    com.daviddunn.retirementplanner.domain.benefit.LifeInsurancePrincipalTest.principal(),"coverageEnd",null)));
            candidate.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Fixed premium",new BigDecimal("2000"),
                    com.daviddunn.retirementplanner.domain.financial.GrowthCategory.NONE,java.time.LocalDate.of(2030,1,1),java.time.LocalDate.of(2040,12,31),
                    com.daviddunn.retirementplanner.domain.financial.ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,BigDecimal.ZERO,
                    new com.daviddunn.retirementplanner.domain.financial.ContractualPremiumPolicy(
                        com.daviddunn.retirementplanner.domain.financial.ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,
                        com.daviddunn.retirementplanner.domain.financial.ContractualPremiumPolicy.Insured.PRIMARY)));
        }
        for(boolean volatileExpense:new boolean[]{false,true}) {
        if(volatileExpense) for(var candidate:List.of(a,b)) candidate.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense(
                "Ordinary spending",new BigDecimal("12000"),com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL,null,null,
                com.daviddunn.retirementplanner.domain.financial.ExpenseType.RECURRING,new BigDecimal(".25")));
        var settings=MonteCarloSettings.forPlan(a,2,417,longevity?BigDecimal.ZERO:new BigDecimal(".12"));
        var request=comparison(a,b,settings,longevity,2032);
        var analyzer=new MonteCarloStrategyComparisonAnalyzer(); var result=analyzer.analyzeComparison(request,PROGRESS,NEVER);
        assertEquals(result.outcomes(),analyzer.analyzeComparison(request,PROGRESS,NEVER).outcomes());
        if(configuration.equals("IDENTICAL")||configuration.equals("COEXIST")) assertEquals(0,result.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
        var payload=MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                longevity?MonteCarloMode.LONGEVITY_ADJUSTED:MonteCarloMode.FIXED_LIFESPAN,"Original",0),START,START.plusSeconds(1));
        verify(payload);
        assertEquals(a.getConditionalBenefits(),MonteCarloPlanDocument.read(payload.request().planA()).getConditionalBenefits());
        assertEquals(b.getConditionalBenefits(),MonteCarloPlanDocument.read(payload.request().planB()).getConditionalBenefits());
        }
    }
}
