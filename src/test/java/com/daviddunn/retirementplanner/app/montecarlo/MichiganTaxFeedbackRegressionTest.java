package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Independent annual Michigan worksheets and unchanged RMD divisors for seeded worlds. */
class MichiganTaxFeedbackRegressionTest {
    @Test
    void fixedAndLongevityWorldsPreserveFederalFactsAndRmdFormula() throws Exception {
        var base = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var checked = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                var result = super.projectWithOutcome(plan, context, path);
                var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                        plan.getPlanningAssumptions().getDeathScenarioAssumptions(), context.householdLifetimeScenario());
                ProjectionYear previous = null;
                for (var year : result.completedYears()) {
                    int calendar = year.getCalendarYear();
                    var rules = new GovernmentRuleProjectionService().project(base, plan.getPlanningAssumptions(), calendar);
                    LocalDate receipt = plan.getPlanningAssumptions().getProjectionStartDate().withYear(calendar);
                    BigDecimal retirement = BigDecimal.ZERO;
                    BigDecimal conversion = BigDecimal.ZERO;
                    BigDecimal pension = BigDecimal.ZERO;
                    BigDecimal socialSecurity = BigDecimal.ZERO;
                    BigDecimal interest = BigDecimal.ZERO;
                    BigDecimal eligible = BigDecimal.ZERO;
                    for (var source : year.getIncomeSources()) {
                        BigDecimal taxable = source.modeledTaxableAmount().orElse(BigDecimal.ZERO);
                        if (source.isRetirementDistribution()) {
                            if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) conversion = conversion.add(taxable);
                            else retirement = retirement.add(taxable);
                            if (source.account().isPresent() && source.account().get().type() == AccountType.TRADITIONAL_IRA
                                    && source.owner().isPresent()) {
                                var owner = source.owner().get();
                                var birth = plan.getHousehold().peopleByOwner().get(owner).getBirthDate();
                                var actualReceipt = source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD
                                        ? LocalDate.of(calendar, 1, 1) : receipt;
                                if (deaths.isAlive(owner, calendar) && !birth.plusYears(59).plusMonths(6).isAfter(actualReceipt)) {
                                    eligible = eligible.add(taxable);
                                }
                            }
                        } else if (source.kind() == IncomeSourceAttribution.Kind.PENSION) pension = pension.add(taxable);
                        else if (source.kind() == IncomeSourceAttribution.Kind.SOCIAL_SECURITY) socialSecurity = socialSecurity.add(source.grossAmount());
                        else if (source.kind() == IncomeSourceAttribution.Kind.INTEREST) interest = interest.add(taxable);
                    }
                    FilingStatus filing = deaths.firstDeathYear().map(death -> calendar > death.getValue()).orElse(false)
                            ? FilingStatus.SINGLE : plan.getPlanningAssumptions().getTaxAssumptions().getFilingStatus();
                    BigDecimal limit = filing == FilingStatus.SINGLE ? rules.getMichiganTaxRules().getRetirementDeductionSingle()
                            : rules.getMichiganTaxRules().getRetirementDeductionMarried();
                    BigDecimal exemptions = rules.getMichiganTaxRules().getPersonalExemption()
                            .multiply(BigDecimal.valueOf(filing == FilingStatus.SINGLE ? 1 : 2));
                    BigDecimal ordinary = pension.add(retirement).add(conversion).add(interest);
                    BigDecimal expected = ordinary.subtract(eligible.min(limit)).subtract(exemptions)
                            .max(BigDecimal.ZERO).multiply(new BigDecimal("0.0425"));
                    assertEquals(0, expected.compareTo(year.getMichiganIncomeTax()), "Michigan worksheet " + calendar);
                    var federal = new FederalTaxCalculator().calculate(new TaxIncome(pension, socialSecurity,
                            retirement, conversion, interest), filing, rules);
                    assertEquals(0, federal.getFederalIncomeTax().compareTo(year.getFederalIncomeTax()), "Federal fixed-input calculation " + calendar);
                    if (previous != null) {
                        BigDecimal expectedRmd = BigDecimal.ZERO;
                        for (var owner : plan.getHousehold().peopleByOwner().keySet()) {
                            var birth = plan.getHousehold().peopleByOwner().get(owner).getBirthDate();
                            int age = calendar - birth.getYear();
                            if (context.householdLifetimeScenario().isPresent() && !deaths.isAlive(owner, calendar)) continue;
                            if (age < base.getRmdRules().getRmdStartingAge(birth)) continue;
                            BigDecimal balance = previous.getEndingAccountSnapshots().stream()
                                    .filter(snapshot -> snapshot.getAccount().getOwnership() == owner
                                            && snapshot.getAccount().getType() == AccountType.TRADITIONAL_IRA)
                                    .map(ProjectedAccountSnapshot::getEndingBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
                            expectedRmd = expectedRmd.add(balance.divide(base.getRmdRules().getLifeExpectancyFactor(age)
                                    .getDistributionPeriod(), 2, RoundingMode.HALF_UP));
                        }
                        assertEquals(0, expectedRmd.compareTo(year.getRequiredMinimumDistribution()), "Balance feedback / same divisor " + calendar);
                    }
                    previous = year;
                    checked.incrementAndGet();
                }
                return result;
            }
        };
        var plan = MonteCarloMortalityExecutionTest.plan();
        var settings = MonteCarloSettings.forPlan(plan, 25, 417, new BigDecimal("0.12"));
        var analyzer = new MonteCarloAnalyzer(engine);
        analyzer.analyze(plan, settings);
        analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, settings));
        assertTrue(checked.get() > 1000);
    }
}
