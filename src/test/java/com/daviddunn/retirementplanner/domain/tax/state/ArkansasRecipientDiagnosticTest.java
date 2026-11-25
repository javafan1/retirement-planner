package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.*;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.*;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest.fixture;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasRecipientDiagnosticTest {
    private static final LocalDate YEAR_OPEN = LocalDate.of(2026, 1, 1);
    private static final BigDecimal AMOUNT = new BigDecimal("43210.98");
    private static final String LABEL = "synthetic-private-label";
    private static final ArkansasTaxEvidence EVIDENCE = new ArkansasTaxEvidence(Map.of(), Map.of(PRIMARY, YEAR_OPEN));

    enum Shape { CONDITIONAL, SPENDING, FUNDING, INHERITED, PENSION, JOINT, RMD, OPENING, ROTH_CASH, CONVERSION }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void emitsOnlyAllowlistedFieldsWhenTheExactMembershipCheckFails(Shape shape) throws Exception {
        var source = source(shape);
        var annual = annual(source);
        var calculator = new ArkansasProductionTaxCalculator();
        var disabled = calculator.calculateUnregistered(annual, EVIDENCE);
        var records = new ArrayList<String>();
        var enabled = ArkansasRecipientDiagnostic.capture(AnalysisCancellationToken.none(), records::add,
                () -> calculator.calculateUnregistered(annual, EVIDENCE));
        assertEquals(disabled, enabled);
        assertEquals(ArkansasProductionTaxCalculator.FailureCategory.ATTRIBUTION, enabled.failureCategory().orElseThrow());
        assertEquals(List.of("Arkansas taxable receipt requires an active individual recipient."), enabled.limitations());
        assertEquals(ArkansasTaxIssue.Kind.SOURCE_OR_TAX, enabled.issue().orElseThrow().kind());
        assertEquals(1, records.size());
        var safe = records.getFirst();
        assertTrue(safe.contains("receiptKind=" + source.kind().name() + "\n"));
        assertTrue(safe.contains("receiptPurpose=" + source.purpose().name() + "\n"));
        assertTrue(safe.contains("receiptOwner=" + source.owner().orElseThrow().name() + "\n"));
        if (shape == Shape.CONDITIONAL) assertTrue(safe.contains("conditionalTrigger=FIXED_DATE\n"));
        assertTrue(safe.contains("stage=EXACT_PRICING\ncandidate=UNKNOWN\n"));
        assertFalse(safe.contains(LABEL));
        assertFalse(safe.contains(AMOUNT.toPlainString()));
        assertFalse(safe.contains(YEAR_OPEN.toString()));
    }

    @Test
    void capturesDistinctProofStagesAndCapsRepeatedFailures() throws Exception {
        var exactInputs = new ArrayList<AnnualIncomeTaxFacts>();
        for (var shape : Shape.values()) exactInputs.add(annual(source(shape)));
        var calculator = new ArkansasProductionTaxCalculator();
        var records = new ArrayList<String>();
        ArkansasRecipientDiagnostic.capture(AnalysisCancellationToken.none(), records::add, () -> {
            var first = exactInputs.getFirst();
            for (int i = 0; i < 8; i++) calculator.prepareTargetTaxFloor(first, EVIDENCE);
            calculator.fundingTaxFloor(first, EVIDENCE, ZERO);
            exactInputs.forEach(facts -> calculator.calculateUnregistered(facts, EVIDENCE));
            return null;
        });
        assertEquals(4, records.size());
        assertTrue(records.get(0).contains("stage=TARGET_BOUND\ncandidate=UNKNOWN\n"));
        assertTrue(records.get(1).contains("stage=FUNDING_INTERVAL\ncandidate=UNKNOWN\n"));
        assertTrue(records.get(2).contains("stage=EXACT_PRICING\ncandidate=UNKNOWN\n"));
        assertFalse(ArkansasRecipientDiagnostic.isEnabled());
    }

    @Test
    void concurrentAndCancelledCapturesStayIsolatedAndSinkFailureCannotReplaceRejection() throws Exception {
        var pension = annual(source(Shape.PENSION));
        var spending = annual(source(Shape.SPENDING));
        var calculator = new ArkansasProductionTaxCalculator();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var income = workers.submit(() -> capture(calculator, pension));
            var cash = workers.submit(() -> capture(calculator, spending));
            assertEquals(1, income.get().size());
            assertTrue(income.get().getFirst().contains("receiptKind=PENSION\n"));
            assertEquals(1, cash.get().size());
            assertTrue(cash.get().getFirst().contains("receiptKind=ORDINARY_RETIREMENT_WITHDRAWAL\n"));
        }
        var cancelled = ArkansasRecipientDiagnostic.capture(() -> true,
                ignored -> fail("Cancelled capture should emit no records"),
                () -> calculator.calculateUnregistered(pension, EVIDENCE));
        var unwritable = ArkansasRecipientDiagnostic.capture(AnalysisCancellationToken.none(),
                ignored -> { throw new IllegalStateException(); },
                () -> calculator.calculateUnregistered(pension, EVIDENCE));
        assertEquals(cancelled, unwritable);
        assertFalse(ArkansasRecipientDiagnostic.isEnabled());
    }

    private static List<String> capture(ArkansasProductionTaxCalculator calculator, AnnualIncomeTaxFacts annual) {
        var records = new ArrayList<String>();
        ArkansasRecipientDiagnostic.capture(AnalysisCancellationToken.none(), records::add,
                () -> calculator.calculateUnregistered(annual, EVIDENCE));
        return records;
    }

    private static IncomeSourceAttribution source(Shape shape) {
        var ira = new TraditionalIRA(LABEL, PRIMARY, AMOUNT);
        var roth = new RothIRA(LABEL, PRIMARY, AMOUNT);
        var inherited = new InheritedTraditionalIRA(LABEL, PRIMARY, AMOUNT, new InheritedAccountInformation(
                YEAR_OPEN.minusYears(85), YEAR_OPEN.minusYears(1), BeneficiaryRelationship.CHILD));
        return switch (shape) {
            case CONDITIONAL -> IncomeSourceAttribution.conditional(new ConditionalBenefitReceipt(UUID.randomUUID(), LABEL,
                    Category.CONDITIONAL_INCOME, new Trigger(TriggerType.FIXED_DATE, YEAR_OPEN, null, null),
                    PersonReference.PRIMARY, PaymentPattern.ONE_TIME_LUMP_SUM, YEAR_OPEN, YEAR_OPEN, YEAR_OPEN, YEAR_OPEN,
                    0, 2026, AMOUNT, AMOUNT, ZERO, TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION,
                    ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH));
            case SPENDING -> IncomeSourceAttribution.withdrawal(ira, AMOUNT, ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW).inYear(2026);
            case FUNDING -> IncomeSourceAttribution.withdrawal(ira, AMOUNT, ORDINARY_RETIREMENT_WITHDRAWAL, TAX_FUNDING).inYear(2026);
            case INHERITED -> IncomeSourceAttribution.withdrawal(inherited, AMOUNT, ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW).inYear(2026);
            case PENSION -> pension(PRIMARY);
            case JOINT -> pension(JOINT);
            case RMD -> IncomeSourceAttribution.withdrawal(ira, AMOUNT, RMD, CASH_FLOW).inYear(2026);
            case OPENING -> IncomeSourceAttribution.withdrawal(ira, AMOUNT, OPENING_RMD, INCOME).inYear(2026);
            case ROTH_CASH -> IncomeSourceAttribution.withdrawal(roth, AMOUNT, ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW).inYear(2026);
            case CONVERSION -> IncomeSourceAttribution.withdrawal(ira, AMOUNT, ROTH_CONVERSION, CONVERSION).inYear(2026);
        };
    }

    private static IncomeSourceAttribution pension(AccountOwnership owner) {
        return IncomeSourceAttribution.pension(new Pension(LABEL, owner, YEAR_OPEN, null, AMOUNT, ZERO), owner, AMOUNT, 2026);
    }

    private static AnnualIncomeTaxFacts annual(IncomeSourceAttribution source) throws Exception {
        var plan = fixture(YEAR_OPEN, 1, DeathScenario.PRIMARY_DIES, 2026, "0");
        var household = TaxHouseholdContext.capture(plan.getHousehold(), YEAR_OPEN,
                plan.getPlanningAssumptions().getDeathScenarioAssumptions());
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var income = new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO, List.of(source));
        return new AnnualIncomeTaxFacts(2026, FilingStatus.MARRIED_FILING_JOINTLY, household, income,
                new FederalTaxCalculator().calculate(income, FilingStatus.MARRIED_FILING_JOINTLY, rules), rules);
    }
}
