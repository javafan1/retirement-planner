package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModeIntegratedTest {
    @ParameterizedTest @EnumSource(FinancialCalculationMode.class)
    void quickGridCompleteAndWeightedUseTheSameAnnualPolicy(FinancialCalculationMode mode) {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(mode);
        var evaluator=new IntegratedSocialSecurityStrategyEvaluator();var current=evaluator.extractCurrentStrategy(plan);
        var request=new IntegratedSocialSecurityCompleteStrategySearchRequest(plan,List.of(67,68),List.of(67),
                List.of(current.primarySurvivorElection()),List.of(current.spouseSurvivorElection()),IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,2);
        assertEquals(2,new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request).rankedSuccessfulEntries().size());
        assertEquals(2,new IntegratedSocialSecurityStrategyComparisonService().compare(plan,request.strategies()).entries().size());
        var grid=new IntegratedRetirementClaimingGridCalculator();
        assertEquals(2,grid.calculate(new IntegratedRetirementClaimingGridRequest(plan,List.of(67,68),List.of(67),grid.survivorPolicy(plan))).cells().size());
        var opening=plan.getPlanningAssumptions().getProjectionStartDate();
        var weighted=new LongevityWeightedIntegratedStrategyRequest(plan,current,ConditionalBenefitIntegratedExecutionTest.mortality(plan,2031,2033),opening,BigDecimal.ZERO);
        var result=new LongevityWeightedIntegratedStrategyEvaluator().evaluate(weighted);
        assertEquals(0,new BigDecimal("1254660.80").compareTo(result.expectedNominalEstateAtSecondDeath()));
        assertEquals(result.scenarioOutcomes(),new LongevityWeightedContinuationEvaluator().evaluate(weighted).scenarioOutcomes());
    }
}
