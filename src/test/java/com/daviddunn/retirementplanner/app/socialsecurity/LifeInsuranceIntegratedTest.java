package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeInsuranceIntegratedTest {
    static RetirementPlan plan() throws Exception {
        var plan=ConditionalBenefitIntegratedExecutionTest.plan();
        var node=LifeInsurancePrincipalTest.document(); node.put("coverageStart","2027-01-01"); node.put("coverageEnd","2027-01-01");
        plan.setConditionalBenefits(List.of(BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class)));
        return plan;
    }
    @Test void quickGridAndCompleteSearchUseOnlyAuthoritativeFullPlanReceipts() throws Exception {
        var p=plan(); var evaluator=new IntegratedSocialSecurityStrategyEvaluator(); var current=evaluator.extractCurrentStrategy(p);
        var request=new IntegratedSocialSecurityCompleteStrategySearchRequest(p,List.of(67,68),List.of(67),
                List.of(current.primarySurvivorElection()),List.of(current.spouseSurvivorElection()),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2);
        var search=new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        var quick=new IntegratedSocialSecurityStrategyComparisonService().compare(p,request.strategies());
        var grid=new IntegratedRetirementClaimingGridCalculator();
        assertEquals(2,search.rankedSuccessfulEntries().size()); assertEquals(2,quick.entries().size());
        assertEquals(2,grid.calculate(new IntegratedRetirementClaimingGridRequest(p,List.of(67,68),List.of(67),grid.survivorPolicy(p))).cells().size());
        var projection=evaluator.evaluateCurrentStrategy(p).projection();
        var receipts=projection.getYears().stream().flatMap(y->y.getIncomeSources().stream()).flatMap(s->s.conditionalBenefit().stream()).toList();
        assertEquals(1,receipts.size()); assertEquals(new BigDecimal("400000.00"),receipts.getFirst().excluded());
        assertEquals(0,receipts.getFirst().taxable().signum());
        var capability=ConditionalBenefitAnalysisExecution.capture(AnalysisKind.COUPLE_QUICK,p);
        assertEquals("analyzer-life-principal-v2",capability.algorithm());
        assertTrue(capability.disclosure().stream().anyMatch(s->s.contains("Future investment earnings may be taxable")));
    }

    @ParameterizedTest @CsvSource({"2027,2029,400000", "2026,2029,0", "2028,2029,0", "2027,2027,0", "2029,2027,0"})
    void weightedMortalityAndContinuationMatchExplicitScenarioWithoutNewDeathDraws(int primary,int spouse,String expected) throws Exception {
        var p=plan(); var strategy=IntegratedSocialSecurityCompleteStrategySearchRequest.standard(p).strategies().getFirst();
        var opening=p.getPlanningAssumptions().getProjectionStartDate();
        var request=new LongevityWeightedIntegratedStrategyRequest(p,strategy,
                ConditionalBenefitIntegratedExecutionTest.mortality(p,primary,spouse),opening,BigDecimal.ZERO);
        var weighted=new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request);
        assertEquals(BigDecimal.ONE,weighted.totalEvaluatedProbability());
        assertEquals(weighted.scenarioOutcomes(),new LongevityWeightedContinuationEvaluator().evaluate(request).scenarioOutcomes());
        var explicit=new ProjectionEngine().project(p,ProjectionEvaluationContext.withSocialSecurityStrategy(strategy,
                new HouseholdLifetimeScenario(Optional.of(Year.of(primary)),Optional.of(Year.of(spouse)))).withExactEndingYear(Math.max(primary,spouse)-1));
        var proceeds=explicit.getYears().stream().flatMap(y->y.getIncomeSources().stream()).flatMap(s->s.conditionalBenefit().stream())
                .map(ConditionalBenefitReceipt::excluded).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(0,new BigDecimal(expected).compareTo(proceeds));
        assertEquals(explicit.getYears().getLast().getAfterTaxEstateValue(),weighted.expectedNominalEstateAtSecondDeath());
    }
}
