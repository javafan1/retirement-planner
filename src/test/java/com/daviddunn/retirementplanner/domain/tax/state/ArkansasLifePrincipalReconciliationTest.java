package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasLifePrincipalReconciliationTest {
    @Test
    void requiresExactFrozenAnnualEvidenceEvenForZeroOrMissingPayments() throws Exception {
        var plan = fixture(START, 1, DeathScenario.PRIMARY_DIES, 2026, "0");
        var definition = policy(PersonReference.PRIMARY, "100000", null, null);
        plan.setConditionalBenefits(List.of(definition));
        var death = EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                plan.getPlanningAssumptions().getDeathScenarioAssumptions(), Optional.empty());
        var context = TaxHouseholdContext.capture(plan.getHousehold(), START,
                plan.getPlanningAssumptions().getDeathScenarioAssumptions());
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var selection = new ConditionalBenefitEvaluator().evaluate(List.of(definition),
                BenefitEvaluationContext.capture(plan.getHousehold(), death, START, 2026));
        var receipt = selection.receipts().getFirst();
        var source = IncomeSourceAttribution.conditional(receipt);
        var evidence = new ArkansasTaxEvidence(Map.of(), Map.of(PRIMARY, START),
                ArkansasTaxEvidence.LifePrincipal.capture(plan, death, 2026));
        var calculator = new ArkansasProductionTaxCalculator();

        java.util.function.Function<List<IncomeSourceAttribution>, AnnualIncomeTaxFacts> annual = sources ->
                ArkansasAnnualFunding.captureIncome(context, FilingStatus.MARRIED_FILING_JOINTLY, rules,
                        plan.getHousehold(), HouseholdSocialSecurityResult.zero(), WithdrawalBreakdown.zero(), ZERO, ZERO, sources);
        var result = calculator.calculateUnregistered(annual.apply(List.of(source)), evidence);
        assertEquals(0, result.incomeTax().signum());
        assertEquals(0, result.election().orElseThrow().retirementExclusion().signum());
        reject(calculator.calculateUnregistered(annual.apply(List.of(source)), new ArkansasTaxEvidence(Map.of(), Map.of(PRIMARY, START))));
        reject(calculator.calculateUnregistered(annual.apply(List.of()), evidence));
        reject(calculator.calculateUnregistered(annual.apply(List.of(source, source)), evidence));
        for (var substituted : List.of(withAmount(receipt, receipt.benefitId(), new BigDecimal("99999.99")),
                withAmount(receipt, UUID.randomUUID(), receipt.gross()), withAmount(receipt, receipt.benefitId(), new BigDecimal("0.00")))) {
            reject(calculator.calculateUnregistered(annual.apply(List.of(IncomeSourceAttribution.conditional(substituted))), evidence));
        }
    }

    @Test
    void illegalLifeShapesCannotBeRepresentedByAnExcludedTaxCharacter() {
        var valid = policy(PersonReference.PRIMARY, "1000", null, null);
        assertThrows(IllegalArgumentException.class,
                () -> definition(valid, valid.recipient(), valid.paymentPattern(), null));
        assertThrows(IllegalArgumentException.class,
                () -> definition(valid, PersonReference.PRIMARY, valid.paymentPattern(), valid.lifeInsuranceQualification()));
        assertThrows(IllegalArgumentException.class,
                () -> definition(valid, valid.recipient(), PaymentPattern.MONTHLY_RECURRING, valid.lifeInsuranceQualification()));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalBenefitReceipt(valid.id(), valid.label(),
                valid.category(), valid.trigger(), valid.recipient(), valid.paymentPattern(), START, START, START, START,
                0, 2026, new BigDecimal("1000.00"), new BigDecimal("4.00"), new BigDecimal("996.00"), valid.taxCharacter(),
                valid.destination()));
    }

    private static void reject(ArkansasProductionTaxCalculator.Result result) {
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertEquals(ArkansasTaxIssue.Kind.LIFE_PRINCIPAL_EVIDENCE, result.issue().orElseThrow().kind());
    }

    private static ConditionalBenefitReceipt withAmount(ConditionalBenefitReceipt receipt, UUID id, BigDecimal amount) {
        return new ConditionalBenefitReceipt(id, receipt.label(), receipt.category(), receipt.trigger(),
                receipt.recipient(), receipt.pattern(), receipt.eventDate(), receipt.firstEligibleDate(),
                receipt.paymentStart(), receipt.paymentEnd(), receipt.months(), receipt.year(), amount, ZERO,
                amount, receipt.taxCharacter(), receipt.destination());
    }

    private static ConditionalBenefitDefinition definition(ConditionalBenefitDefinition original, PersonReference recipient,
            PaymentPattern pattern, LifeInsuranceQualification qualification) {
        return new ConditionalBenefitDefinition(original.id(), original.label(), original.category(), original.trigger(),
                recipient, pattern, original.grossAmount(), null, null, null, null, ZERO, original.escalationOrigin(),
                original.taxCharacter(), original.destination(), qualification);
    }
}
