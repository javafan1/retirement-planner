package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Characterizes the old engine before changing its run-only horizon policy. */
class Stage5GPrefixCharacterizationTest {
    @ParameterizedTest
    @CsvSource({"2035,2040,15,false,0", "2040,2035,15,false,1",
            "2040,2040,15,false,2", "2035,2044,15,false,2",
            "2035,2045,15,false,1", "2035,2046,15,false,0",
            "2035,2040,5,false,2", "2035,2040,15,true,2"})
    void sameDeathsHaveExactlyEqualFinancialAndSocialSecurityPrefixes(
            int primaryDeath, int spouseDeath, int configuredLength, boolean partial, int conversion) throws Exception {
        var source = Stage4TestPlans.plan();
        configure(source, configuredLength, partial);
        if (conversion > 0) {
            source.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("10000"),
                    conversion == 1 ? RothConversionStopRule.FIRST_HOUSEHOLD_RMD : RothConversionStopRule.NEVER,
                    conversion == 1 ? RothConversionStrategy.FIXED_AMOUNT : RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                    RothConversionFrequency.ANNUAL));
        }
        String before = Stage4TestPlans.json(source);
        var engine = new ProjectionEngine();
        String ordinary = Stage4TestPlans.json(engine.project(source));
        int end = Math.max(primaryDeath, spouseDeath) - 1;
        var context = ProjectionEvaluationContext.withSocialSecurityStrategy(Stage4TestPlans.strategy(source),
                new HouseholdLifetimeScenario(Optional.of(Year.of(primaryDeath)), Optional.of(Year.of(spouseDeath))));
        var longer = engine.project(source, context.withEndingYear(end));
        // Test-only isolated horizon supplies the exact-stop oracle before an exact context API exists.
        var shortened = new RetirementPlanScenarioCopyService().copy(source);
        configure(shortened, end - 2030 + 1, partial);
        var exact = engine.project(shortened, context);
        var exactContext = engine.project(source, context.withExactEndingYear(end));
        assertEquals(Stage4TestPlans.json(exact), Stage4TestPlans.json(exactContext));
        assertEquals(end, exact.getEndYear());
        assertEquals(end - 2030 + 1, exact.size());
        for (int index = 0; index < exact.size(); index++) {
            var expected = longer.getYearAt(index);
            var actual = exact.getYearAt(index);
            assertEquals(Stage4TestPlans.json(expected), Stage4TestPlans.json(actual),
                    "Complete financial row, including exact decimal scales: " + actual.getCalendarYear());
            assertEquals(expected.getSocialSecurityResult(), actual.getSocialSecurityResult());
            // Raw monetary accessors, not display strings; BigDecimal.equals retains scale.
            for (var method : ProjectionYear.class.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == BigDecimal.class) {
                    assertEquals(method.invoke(expected), method.invoke(actual), method.getName());
                }
            }
            assertEquals(expected.getEndingAccountSnapshots().size(), actual.getEndingAccountSnapshots().size());
            for (int account = 0; account < expected.getEndingAccountSnapshots().size(); account++) {
                assertEquals(expected.getEndingAccountSnapshots().get(account).getEndingBalance(),
                        actual.getEndingAccountSnapshots().get(account).getEndingBalance());
            }
        }
        var provider = new SocialSecurityProjectionIncomeProvider();
        var longBenefits = provider.calculate(source, 2030, longer.getEndYear(), context);
        var exactBenefits = provider.calculate(shortened, 2030, end, context);
        assertEquals(new TreeSet<>(exact.getYears().stream().map(ProjectionYear::getCalendarYear).toList()),
                new TreeSet<>(exactBenefits.keySet()));
        exactBenefits.forEach((year, value) -> assertEquals(longBenefits.get(year), value));
        var death = LocalDate.of(end + 1, 1, 1);
        var calculator = new EstateAtSecondDeathCalculator();
        var a = calculator.calculate(source, longer, death);
        var b = calculator.calculate(shortened, exact, death);
        assertEquals(a, b);
        var factor = new EstatePresentValueCalculator().discountFactor(LocalDate.of(2029, 7, 1), death,
                source.getPlanningAssumptions().getGeneralInflationRate(), new BigDecimal("0.03"));
        assertEquals(a.nominalAfterTaxEstate().multiply(factor), b.nominalAfterTaxEstate().multiply(factor));
        assertEquals(before, Stage4TestPlans.json(source));
        assertEquals(ordinary, Stage4TestPlans.json(engine.project(source)));
    }

    static void configure(RetirementPlan plan, int length, boolean partial) {
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), length,
                LocalDate.of(2030, partial ? 7 : 1, 1)));
    }
}
