package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.AnnualIncomeTaxFacts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.fixture;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.policy;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.DEFERRED_FIRST;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.money;
import static com.daviddunn.retirementplanner.domain.tax.ArkansasAccountFundingTest.portfolio;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasLowIncomeExactFundingDiagnosticsTest {
    @Test
    void anUnaffordableExploratoryRefusalKeepsTheSolverStoppedBeforeTheKnownFundedCent() throws Exception {
        var start = LocalDate.of(2028, 1, 1);
        var plan = fixture(start, 1, DeathScenario.PRIMARY_DIES, 2028, "0");
        plan.setConditionalBenefits(List.of(policy(PersonReference.PRIMARY, "100000", null, null)));
        var household = plan.getHousehold();
        var pension = new Pension("Synthetic employer pension", PRIMARY, start, null,
                new BigDecimal("5000"), ZERO, new BigDecimal("2500"));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        household.getPrimaryPerson().addIncomeSource(pension);
        var assumptions = plan.getPlanningAssumptions();
        var death = EffectiveHouseholdDeathView.resolve(household, assumptions.getDeathScenarioAssumptions(), Optional.empty());
        var selected = new HouseholdPensionIncomeCalculator().calculateWithSources(household, start, death);
        var receipts = new ConditionalBenefitEvaluator().evaluate(plan.getConditionalBenefits(),
                BenefitEvaluationContext.capture(household, death, start, 2028)).receipts();
        var sources = new ArrayList<>(selected.sources());
        receipts.forEach(receipt -> sources.add(IncomeSourceAttribution.conditional(receipt)));
        var context = new TaxHouseholdContext(start, Map.of(PRIMARY, household.getPrimaryPerson().getBirthDate(),
                SPOUSE, household.getSpouse().getBirthDate()), Set.of(PRIMARY, SPOUSE), Set.of(SPOUSE), Optional.of(selected));
        var published = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var rules = new GovernmentRuleProjectionService().project(published, assumptions, 2028);
        var filing = FilingStatus.MARRIED_FILING_JOINTLY;
        var income = new TaxIncome(selected.total(), ZERO, ZERO, ZERO, ZERO, sources);
        var seed = new AnnualIncomeTaxFacts(2028, filing, context, income,
                new FederalTaxCalculator().calculate(income, filing, rules), rules);
        var evidence = new ArkansasTaxEvidence(Map.of(), Map.of(PRIMARY, start),
                ArkansasTaxEvidence.LifePrincipal.capture(plan, death, 2028));
        var cash = new ArkansasAnnualFunding.Cash(new BigDecimal("130000"), ZERO, ZERO, new BigDecimal("130000"), ZERO);
        var facts = new ArkansasAnnualFunding.Facts(seed, evidence, Optional.empty(), Map.of(), cash, true);
        var base = portfolio(new TraditionalIRA("Synthetic IRA", SPOUSE, new BigDecimal("250000")),
                new RothIRA("Synthetic Roth", SPOUSE, ZERO));

        var trial = new ArkansasRothFunding().fixed(facts, base, DEFERRED_FIRST, new BigDecimal("11000"));
        var funding = trial.funding();
        assertEquals(ArkansasMinimumFundingSolver.Status.UNSUPPORTED, funding.status());
        assertEquals(1, funding.metrics().candidates());
        assertEquals(0, funding.metrics().intervalTaxBounds());
        assertTrue(funding.candidate().isEmpty());
        var zero = funding.issue().orElseThrow().lowIncomeEvidence().orElseThrow().exactFundingProbe().orElseThrow();
        assertEquals(ArkansasTaxIssue.Withdrawal.ZERO, zero.withdrawal());
        assertEquals(ArkansasTaxIssue.FederalCash.BELOW_FEDERAL_TAX, zero.cash());

        // Independent exact probes after the production solver has already refused; these cannot publish a projection.
        var evaluator = new ArkansasAnnualFunding.Evaluator(facts, trial.conversion(), DEFERRED_FIRST);
        var unresolved = assertThrows(ArkansasAnnualFunding.Unavailable.class,
                () -> evaluator.evaluate(new BigDecimal("140.00"))).issue().lowIncomeEvidence().orElseThrow();
        assertEquals(ArkansasTaxIssue.Bound.PASS, unresolved.section301Gross());
        assertEquals(ArkansasTaxIssue.Bound.FAIL, unresolved.section501Net());
        var positive = unresolved.exactFundingProbe().orElseThrow();
        assertEquals(ArkansasTaxIssue.Withdrawal.POSITIVE, positive.withdrawal());
        assertEquals(ArkansasTaxIssue.FederalCash.BELOW_FEDERAL_TAX, positive.cash());
        var justOutside = evaluator.evaluate(new BigDecimal("140.01")).candidate();
        money("894.001", justOutside.federalTax());
        money("692.20", justOutside.stateTax().incomeTax());
        money("0.007", evaluator.evaluate(new BigDecimal("1815.77")).candidate().deficit());
        var sufficient = evaluator.evaluate(new BigDecimal("1815.78")).candidate();
        money("1061.578", sufficient.federalTax());
        money("754.20", sufficient.stateTax().incomeTax());
        money("0", sufficient.deficit());
        money("0.002", sufficient.surplus());
    }
}
