package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium;
import static org.junit.jupiter.api.Assertions.*;

class ContractualPremiumIntegratedTest {
    @Test void quickGridAndCompleteSearchRetainOriginalPremiumsAndAuthoritativeExpenseFacts() throws Exception {
        var p=LifeInsuranceIntegratedTest.plan();
        p.getHousehold().addExpense(premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,LocalDate.of(2027,1,1),null));
        var evaluator=new IntegratedSocialSecurityStrategyEvaluator(); var current=evaluator.extractCurrentStrategy(p);
        var request=new IntegratedSocialSecurityCompleteStrategySearchRequest(p,List.of(67,68),List.of(67),
                List.of(current.primarySurvivorElection()),List.of(current.spouseSurvivorElection()),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2);
        var search=new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        var quick=new IntegratedSocialSecurityStrategyComparisonService().compare(p,request.strategies());
        var grid=new IntegratedRetirementClaimingGridCalculator();
        assertEquals(2,search.rankedSuccessfulEntries().size()); assertEquals(2,quick.entries().size());
        assertEquals(2,grid.calculate(new IntegratedRetirementClaimingGridRequest(p,List.of(67,68),List.of(67),grid.survivorPolicy(p))).cells().size());
        var projection=evaluator.evaluateCurrentStrategy(p).projection();
        var direct=new ProjectionEngine().project(p);
        assertEquals(direct.getYears().stream().map(y->y.getAnnualExpenses()).toList(),projection.getYears().stream().map(y->y.getAnnualExpenses()).toList());
        var capability=ConditionalBenefitAnalysisExecution.capture(AnalysisKind.COUPLE_QUICK,p);
        assertEquals(3,capability.version()); assertTrue(capability.excludesLifePrincipal());
        assertEquals("analyzer-contractual-premiums-v3",capability.algorithm());
        assertTrue(capability.disclosure().stream().anyMatch(s->s.contains("insured SPOUSE")));
    }

    @ParameterizedTest @CsvSource({"2027,2029,4000","2026,2029,4000","2028,2029,4000","2027,2027,0","2029,2027,0"})
    void weightedReferenceAndContinuationRespectLivingInsuredAndTerminalBoundary(int primary,int spouse,String expected) throws Exception {
        var p=LifeInsuranceIntegratedTest.plan(); p.setConditionalBenefits(List.of());
        p.getHousehold().getExpenses().stream().toList().forEach(p.getHousehold()::removeExpense);
        p.getHousehold().addExpense(premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,LocalDate.of(2027,1,1),null));
        var strategy=IntegratedSocialSecurityCompleteStrategySearchRequest.standard(p).strategies().getFirst();
        var opening=p.getPlanningAssumptions().getProjectionStartDate();
        var request=new LongevityWeightedIntegratedStrategyRequest(p,strategy,ConditionalBenefitIntegratedExecutionTest.mortality(p,primary,spouse),opening,BigDecimal.ZERO);
        var weighted=new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request);
        assertEquals(BigDecimal.ONE,weighted.totalEvaluatedProbability());
        assertEquals(weighted.scenarioOutcomes(),new LongevityWeightedContinuationEvaluator().evaluate(request).scenarioOutcomes());
        var explicit=new ProjectionEngine().project(p,ProjectionEvaluationContext.withSocialSecurityStrategy(strategy,new HouseholdLifetimeScenario(
                Optional.of(Year.of(primary)),Optional.of(Year.of(spouse)))).withExactEndingYear(Math.max(primary,spouse)-1));
        assertEquals(0,new BigDecimal(expected).compareTo(explicit.getYears().stream().map(y->y.getAnnualExpenses()).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertEquals(explicit.getYears().getLast().getAfterTaxEstateValue(),weighted.expectedNominalEstateAtSecondDeath());
    }
}
