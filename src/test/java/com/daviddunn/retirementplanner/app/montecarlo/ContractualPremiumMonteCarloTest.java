package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium;
import static org.junit.jupiter.api.Assertions.*;

class ContractualPremiumMonteCarloTest {
    static RetirementPlan prepared(boolean couple, boolean stochastic) {
        var plan=couple ? LifeInsuranceMonteCarloTest.prepared(stochastic) : plan(false);
        if (!couple && stochastic) StandaloneExpenseCapabilityTest.enable(plan,".25");
        plan.getHousehold().addExpense(premium("2000",ContractualPremiumPolicy.Insured.PRIMARY,
                plan.getPlanningAssumptions().getProjectionStartDate(),null));
        return plan;
    }

    @ParameterizedTest @CsvSource({"true,false,false","true,true,false","false,false,false","false,true,false",
            "true,false,true","true,true,true","false,false,true","false,true,true"})
    void standaloneHouseholdMortalityAndExpenseModes(boolean couple,boolean longevity,boolean stochastic) throws Exception {
        var plan=prepared(couple,stochastic); var settings=MonteCarloSettings.forPlan(plan,2,417,new BigDecimal(".12"));
        var service=new MonteCarloRunService();
        var run=longevity?service.runMortality(plan,mortality(plan,settings),PROGRESS,NEVER):service.run(plan,settings,null,PROGRESS,NEVER);
        var again=longevity?service.runMortality(plan,mortality(plan,settings),PROGRESS,NEVER):service.run(plan,settings,null,PROGRESS,NEVER);
        if (longevity) assertEquals(run.mortalityResult().outcomes(),again.mortalityResult().outcomes());
        else assertEquals(run.result(),again.result());
        roundTrip(MonteCarloSnapshotPresentationMapper.capture(run,plan,LifeInsuranceMonteCarloTest.START,LifeInsuranceMonteCarloTest.START.plusSeconds(1)));
        int first=plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        for(int index=0;index<2;index++) {
            var world=longevity?new MonteCarloWorldGenerator(mortality(plan,settings)).generate(index):null;
            int last=longevity?world.lifetime().terminalDeathYear()-1:first+plan.getPlanningAssumptions().getProjectionLengthYears()-1;
            if(last<first) continue;
            var economic=longevity?world.economicPath():new MonteCarloScenarioGenerator().generate(first,last,settings,index);
            var context=longevity?com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(last)
                    :com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext.empty();
            if(longevity && couple) context=context.withSurvivorClaimingAge(mortality(plan,settings).survivorClaimingAge().orElseThrow());
            var inflation=longevity?world.inflationPath():new MonteCarloInflationGenerator().generate(first,last,settings,index);
            if(inflation.isPresent()) context=context.withInflationPath(inflation.orElseThrow());
            if(stochastic) context=InternalExpenseRun.configured(plan).apply(plan,context,settings.seed(),index,first,last);
            var direct=new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine().projectWithOutcome(plan,context,economic);
            var annual=new TreeMap<Integer,BigDecimal>(); direct.completedYears().forEach(y->annual.put(y.getCalendarYear(),y.getEndingInvestableAssets()));
            if(longevity) assertEquals(annual,run.mortalityResult().outcomes().get(index).annualInvestableAssets());
            else if(direct instanceof com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult.Completed complete)
                assertEquals(new com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator().calculate(plan,complete.projection()),run.result().outcomes().get(index).metrics().orElseThrow());
        }
    }

    @ParameterizedTest @CsvSource({"true,false,false","true,true,false","false,false,false","false,true,false",
            "true,false,true","true,true,true","false,false,true","false,true,true"})
    void pairedAllHouseholdMortalityAndExpenseModes(boolean couple,boolean longevity,boolean stochastic) throws Exception {
        var a=prepared(couple,stochastic); var b=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(a);
        var settings=MonteCarloSettings.forPlan(a,2,417,new BigDecimal(".12"));
        int last=a.getPlanningAssumptions().getProjectionStartDate().getYear()+a.getPlanningAssumptions().getProjectionLengthYears()-1;
        var request=longevity || !couple ? comparison(a,b,settings,longevity,last) : new MonteCarloStrategyComparisonRequest(
                new MonteCarloStrategyCandidate("A",a),new MonteCarloStrategyCandidate("B",b),
                new MonteCarloStrategyComparisonRequest.Fixed(settings,a.getPlanningAssumptions().getProjectionStartDate(),last,
                        MonteCarloLifetime.couple(new com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario(
                                Optional.of(Year.of(a.getPlanningAssumptions().getProjectionStartDate().getYear()+1)),Optional.empty()))));
        var analyzer=new MonteCarloStrategyComparisonAnalyzer();
        var result=analyzer.analyzeComparison(request,PROGRESS,NEVER);
        assertEquals(result.outcomes(),analyzer.analyzeComparison(request,PROGRESS,NEVER).outcomes());
        assertEquals(0,result.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
        var payload=MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                longevity?MonteCarloMode.LONGEVITY_ADJUSTED:MonteCarloMode.FIXED_LIFESPAN,"Original",0),LifeInsuranceMonteCarloTest.START,LifeInsuranceMonteCarloTest.START.plusSeconds(1));
        roundTrip(payload);
    }

    @ParameterizedTest @ValueSource(strings={"CURRENT","BASELINE","AMOUNT","INSURED","DATES","LABEL","BENEFIT"})
    void candidatesPreserveTheirOwnOriginalPolicies(String configuration) throws Exception {
        var a=prepared(true,true); var b=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(a);
        var premiumA=a.getHousehold().getExpenses().getLast(); var premiumB=b.getHousehold().getExpenses().getLast();
        switch(configuration) {
            case "CURRENT" -> b.getHousehold().removeExpense(premiumB);
            case "BASELINE" -> a.getHousehold().removeExpense(premiumA);
            case "AMOUNT" -> b.getHousehold().replaceExpense(premiumB,premium("3000",ContractualPremiumPolicy.Insured.PRIMARY,premiumB.getStartDate(),null));
            case "INSURED" -> b.getHousehold().replaceExpense(premiumB,premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,premiumB.getStartDate(),null));
            case "DATES" -> b.getHousehold().replaceExpense(premiumB,premium("2000",ContractualPremiumPolicy.Insured.PRIMARY,premiumB.getStartDate().plusYears(1),null));
            case "LABEL" -> b.getHousehold().replaceExpense(premiumB,new Expense("Different label",new BigDecimal("2000.00"),GrowthCategory.NONE,premiumB.getStartDate(),null,
                    ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,null,premiumB.getContractualPremium()));
            case "BENEFIT" -> b.setConditionalBenefits(List.of());
        }
        if(configuration.equals("LABEL")) b.assignPlanId(UUID.randomUUID());
        var settings=MonteCarloSettings.forPlan(a,2,417,new BigDecimal(".12"));
        int last=a.getPlanningAssumptions().getProjectionStartDate().getYear()+a.getPlanningAssumptions().getProjectionLengthYears()-1;
        var request=comparison(a,b,settings,true,last);
        var result=new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request,PROGRESS,NEVER);
        if(configuration.equals("LABEL")) assertEquals(0,result.summary().terminalAfterTaxEstateDifference().meanDifference().orElseThrow().signum());
        var payload=MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,MonteCarloMode.LONGEVITY_ADJUSTED,"Original",0),
                LifeInsuranceMonteCarloTest.START,LifeInsuranceMonteCarloTest.START.plusSeconds(1));
        roundTrip(payload);
        assertEquals(premiumPolicies(a),premiumPolicies(MonteCarloPlanDocument.read(payload.request().planA())));
        assertEquals(premiumPolicies(b),premiumPolicies(MonteCarloPlanDocument.read(payload.request().planB())));
        if(!configuration.equals("LABEL")) assertThrows(IllegalArgumentException.class,()->payload.benefitExecution().validate(payload.kind(),payload.request().planB(),payload.request().planA()));
    }

    static List<ContractualPremiumPolicy> premiumPolicies(RetirementPlan plan) {
        return plan.getHousehold().getExpenses().stream().filter(Expense::isContractualInsurancePremium).map(Expense::getContractualPremium).toList();
    }

    static void roundTrip(MonteCarloSnapshot payload) throws Exception {
        assertEquals(payload.paired()==null?8:9,payload.version());
        assertEquals(payload.paired()==null?9:10,payload.fingerprintVersion());
        assertEquals(3,payload.benefitExecution().version());
        assertTrue(payload.benefitExecution().disclosure().stream().anyMatch(s->s.contains("Contractual insurance premium")));
        var saved=StandaloneExpenseCapabilityTest.envelope(payload); var codec=new AnalysisSnapshotCodec();
        assertEquals(saved,codec.decode(codec.encode(saved)));
        var policy=new MonteCarloFingerprintService();
        assertEquals(AnalysisCompatibilityPolicy.State.CURRENT,policy.compatibility(saved,new MonteCarloFingerprintService.Inputs(payload.kind(),payload.settings(),payload.request())).state());
        assertThrows(IllegalArgumentException.class,()->new ConditionalBenefitAnalysisExecution(2,ConditionalBenefitAnalysisExecution.LIFE_PRINCIPAL_MODEL,
                payload.kind(),payload.benefitExecution().scenarioProtocol(),payload.benefitExecution().candidates()));
    }

    @Test void premiumConsumesNoExpenseDrawAndCannotChangeExistingVectors() {
        var plan=plan(true); StandaloneExpenseCapabilityTest.enable(plan,".25");
        var specs=plan.getHousehold().getExpenses().stream().map(e->ExpenseSamplingSpecification.from(e,e.getEffectiveAnnualExpenseVolatility())).toList();
        var before=new MonteCarloExpenseGenerator(specs);
        var afterSpecs=new ArrayList<>(specs); afterSpecs.addFirst(ExpenseSamplingSpecification.from(premium(),BigDecimal.ZERO));
        var after=new MonteCarloExpenseGenerator(afterSpecs);
        assertTrue(after.occurrencesInInputOrder().getFirst().isEmpty());
        assertEquals(before.occurrencesInInputOrder(),after.occurrencesInInputOrder().subList(1,afterSpecs.size()));
        for(int scenario=0;scenario<3;scenario++) {
            var pathA=before.generate(417,scenario,2030,2033).orElseThrow(); var pathB=after.generate(417,scenario,2030,2033).orElseThrow();
            for(var occurrence:before.occurrencesInInputOrder()) if(occurrence.isPresent()) for(int year=2030;year<=2033;year++)
                assertEquals(pathA.multiplier(occurrence.orElseThrow(),year),pathB.multiplier(occurrence.orElseThrow(),year));
        }
        var settings=MonteCarloSettings.forPlan(plan,2,417,new BigDecimal(".12"));
        var worldBefore=new MonteCarloWorldGenerator(mortality(plan,settings)); plan.getHousehold().addExpense(premium());
        var worldAfter=new MonteCarloWorldGenerator(mortality(plan,settings));
        for(int i=0;i<2;i++) {
            assertEquals(worldBefore.generate(i).lifetime(),worldAfter.generate(i).lifetime());
            assertEquals(worldBefore.generate(i).economicPath().annualReturns(),worldAfter.generate(i).economicPath().annualReturns());
            assertEquals(worldBefore.generate(i).inflationPath(),worldAfter.generate(i).inflationPath());
        }
    }

    @Test void boundedRepresentativeRunRecordsPerformanceAndPreservesCancellation() throws Exception {
        var plan=prepared(true,true); var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),
                10,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        var settings=MonteCarloSettings.forPlan(plan,64,417,new BigDecimal(".12"));
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        var allocation=bean instanceof com.sun.management.ThreadMXBean measured && measured.isThreadAllocatedMemorySupported()?measured:null;
        long before=allocation==null?0:allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
        long start=System.nanoTime();
        var result=new MonteCarloRunService().run(plan,settings,null,PROGRESS,NEVER);
        long elapsed=System.nanoTime()-start;
        long bytes=allocation==null?-1:allocation.getThreadAllocatedBytes(Thread.currentThread().threadId())-before;
        assertEquals(64,result.result().outcomes().size());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/phase23c/bounded-performance.json"),
                "{\"trials\":64,\"years\":10,\"elapsedNanos\":"+elapsed+",\"currentThreadAllocatedBytes\":"+bytes+"}");
        assertThrows(com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException.class,()->new MonteCarloRunService().run(plan,settings,null,PROGRESS,()->true));
    }
}
