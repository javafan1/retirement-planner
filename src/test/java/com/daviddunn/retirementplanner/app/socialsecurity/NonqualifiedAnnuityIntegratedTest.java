package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class NonqualifiedAnnuityIntegratedTest {
    @Test void quickGridAndCompleteSearchRetainTheSameQuotedSource() {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);var evaluator=new IntegratedSocialSecurityStrategyEvaluator();
        var current=evaluator.extractCurrentStrategy(plan);
        var request=new IntegratedSocialSecurityCompleteStrategySearchRequest(plan,List.of(67,68),List.of(67),
                List.of(current.primarySurvivorElection()),List.of(current.spouseSurvivorElection()),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2);
        var search=new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        var quick=new IntegratedSocialSecurityStrategyComparisonService().compare(plan,request.strategies());
        var grid=new IntegratedRetirementClaimingGridCalculator();
        assertEquals(2,search.rankedSuccessfulEntries().size());assertEquals(2,quick.entries().size());
        assertEquals(2,grid.calculate(new IntegratedRetirementClaimingGridRequest(plan,List.of(67,68),List.of(67),grid.survivorPolicy(plan))).cells().size());
        var projection=evaluator.evaluateCurrentStrategy(plan).projection();
        assertEquals(3,projection.getYears().stream().flatMap(y->y.getIncomeSources().stream()).flatMap(s->s.conditionalBenefit().stream()).count());
        var capability=ConditionalBenefitAnalysisExecution.capture(AnalysisKind.COUPLE_QUICK,plan);
        assertEquals(4,capability.version());assertTrue(capability.disclosure().stream().anyMatch(s->s.contains("opening remaining investment")));
    }
    @ParameterizedTest @CsvSource({"2031,2033,1254660.80","2033,2031,1283460.80"})
    void weightedDeathsAndReferenceContinuationMatchLiteralEstateControls(int primary,int spouse,String estate) {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);
        var strategy=new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        var opening=plan.getPlanningAssumptions().getProjectionStartDate();
        var request=new LongevityWeightedIntegratedStrategyRequest(plan,strategy,
                ConditionalBenefitIntegratedExecutionTest.mortality(plan,primary,spouse),opening,BigDecimal.ZERO);
        var result=new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request);
        assertEquals(BigDecimal.ONE,result.totalEvaluatedProbability());
        assertEquals(0,new BigDecimal(estate).compareTo(result.expectedNominalEstateAtSecondDeath()));
        assertEquals(result.scenarioOutcomes(),new LongevityWeightedContinuationEvaluator().evaluate(request).scenarioOutcomes());
        var projection=new ProjectionEngine().project(plan,ProjectionEvaluationContext.withSocialSecurityStrategy(strategy,
                new HouseholdLifetimeScenario(Optional.of(Year.of(primary)),Optional.of(Year.of(spouse)))).withExactEndingYear(Math.max(primary,spouse)-1));
        assertEquals(projection.getYears().getLast().getAfterTaxEstateValue(),result.expectedNominalEstateAtSecondDeath());
    }
    @Test void singlePersonIntegratedMortalityDoesNotAlterSocialSecurityOnlyObjective() {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(false);var opening=plan.getPlanningAssumptions().getProjectionStartDate();
        var mortality=IndividualLongevityScenarios.create(plan.getHousehold(),SocialSecurityMortalityAdjustment.standard(),opening,SocialSecurityMortalityTables.ssaPeriod2022());
        var analysis=new SinglePersonMortalityAnalysis();
        var ss=analysis.calculate(plan,mortality,opening,BigDecimal.ZERO,SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY,AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        var integrated=analysis.calculate(plan,mortality,opening,BigDecimal.ZERO,SinglePersonMortalityAnalysis.Mode.INTEGRATED,AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        assertEquals(9,integrated.entries().size());assertTrue(integrated.projectionCount()>0);
        var copy=new RetirementPlanScenarioCopyService().copy(plan);copy.setConditionalBenefits(List.of());
        var ssControl=analysis.calculate(copy,mortality,opening,BigDecimal.ZERO,SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY,AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        assertEquals(ss.entries(),ssControl.entries());
    }
}
