package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.tax.state.colorado.ColoradoFederalBridgeCompletionTest.*;

/** Phase 6E-E official worksheet fixtures. No production routing or conditional future-law constants. */
class ColoradoWorksheetCompletionTest {
    @ParameterizedTest
    @CsvSource({"60,50000,0,611.60", "65,50000,0,81.40", "60,50000,5000,831.60",
            "60,0,300000,12491.60", "60,0,300001,13156.04", "60,0,16200.125,4.41",
            "60,0,0,0.00", "60,19999.99,0,0.00", "65,23999.99,0,0.00"})
    void independentSingleWorksheet(int age, String ira, String investment, String expected) throws Exception {
        var income = new TaxIncome(n("0"), n("0"), n(ira), n("0"), n(investment), List.of(ira(AccountOwnership.PRIMARY, ira)));
        assertEquals(n(expected), calculate(context(age, 60, false), false, income).tax());
    }

    @ParameterizedTest
    @CsvSource({"50000,50000,710.60", "100000,0,1766.60", "0,100000,1590.60"})
    void separateSpouseCapsCannotTransfer(String a, String b, String expected) throws Exception {
        var income = new TaxIncome(n("0"), n("0"), n(a).add(n(b)), n("0"), n("0"),
                List.of(ira(AccountOwnership.PRIMARY, a), ira(AccountOwnership.SPOUSE, b)));
        assertEquals(n(expected), calculate(context(60, 66, true), true, income).tax());
    }

    @Test
    void unequalSocialSecurityAllocationAndPriorityChangeOwnershipResult() throws Exception {
        var ss = List.of(social(AccountOwnership.PRIMARY, "30000"), social(AccountOwnership.SPOUSE, "10000"));
        var sources = new ArrayList<>(ss);
        sources.add(ira(AccountOwnership.PRIMARY, "10000"));
        sources.add(ira(AccountOwnership.SPOUSE, "10000"));
        var income = new TaxIncome(n("0"), n("40000"), n("20000"), n("0"), n("80000"), sources);
        var result = calculate(context(66, 62, true), true, income);
        assertEquals(n("2206.60"), result.tax());
        same("25500", result.retirement().taxableSocialSecurityByRecipient().get(AccountOwnership.PRIMARY));
        same("8500", result.retirement().taxableSocialSecurityByRecipient().get(AccountOwnership.SPOUSE));
        same("44000", result.retirement().totalSubtraction());
        sources = new ArrayList<>(ss);
        sources.add(ira(AccountOwnership.PRIMARY, "20000"));
        assertEquals(n("2646.60"), calculate(context(66, 62, true), true, income.withIncomeSources(sources)).tax());
    }

    @ParameterizedTest
    @CsvSource({"44540,1251.36", "44541,1711.64"})
    void socialSecurityAgiCliffUsesActualFederalTaxableShare(String investment, String expected) throws Exception {
        var income = new TaxIncome(n("0"), n("40000"), n("0"), n("0"), n(investment),
                List.of(social(AccountOwnership.PRIMARY, "40000")));
        assertEquals(n(expected), calculate(context(62, 60, false), false, income).tax());
    }

    @Test
    void realBrokeragePrincipalAndKnownGainAreReconciledOnce() throws Exception {
        var account = new BrokerageAccount("Ordinary brokerage", AccountOwnership.PRIMARY, n("100000"));
        var proceeds = IncomeSourceAttribution.withdrawal(account, n("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var principal = new TaxIncome(n("0"), n("0"), n("0"), n("0"), n("0"), List.of(proceeds));
        assertEquals(n("0.00"), calculate(context(60, 60, false), false, principal).tax());
        var gain = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.CAPITAL_GAIN,
                IncomeSourceAttribution.Purpose.INCOME, "Already modeled gain", n("5000"), Optional.of(n("5000"))).inYear(2026);
        var mixed = new TaxIncome(n("0"), n("0"), n("50000"), n("0"), n("5000"),
                List.of(proceeds, gain, ira(AccountOwnership.PRIMARY, "50000")));
        var result = calculate(context(60, 60, false), false, mixed);
        assertEquals(n("831.60"), result.tax());
        assertTrue(result.limitations().stream().anyMatch(note -> note.contains("Unmodeled realized gains")));
        assertThrows(IllegalArgumentException.class, () -> calculate(context(60, 60, false), false,
                principal.withIncomeSources(List.of(proceeds, gain))));
        assertThrows(IllegalArgumentException.class, () -> calculate(context(60, 60, false), false,
                mixed.withIncomeSources(List.of(proceeds, gain, gain, ira(AccountOwnership.PRIMARY, "50000")))));
        same("100000", account.getCurrentBalance());
    }

    @Test
    void qualifiedRothIsNotIncomeOrAnotherSubtraction() throws Exception {
        var source = IncomeSourceAttribution.withdrawal(new RothIRA("Roth", AccountOwnership.PRIMARY, n("100000")),
                n("50000"), IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var result = calculate(context(65, 60, false), false,
                new TaxIncome(n("0"), n("0"), n("0"), n("0"), n("0"), List.of(source)));
        assertEquals(n("0.00"), result.tax());
        same("0", result.retirement().totalSubtraction());
    }

    @Test
    void openingAndProjectionRmdsShareOnePool() throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, n("100000"));
        var sources = List.of(IncomeSourceAttribution.withdrawal(account, n("20000"), IncomeSourceAttribution.Kind.OPENING_RMD,
                IncomeSourceAttribution.Purpose.INCOME).inYear(2026),
                IncomeSourceAttribution.withdrawal(account, n("30000"), IncomeSourceAttribution.Kind.RMD,
                        IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026));
        assertEquals(n("81.40"), calculate(context(75, 60, false), false,
                new TaxIncome(n("0"), n("0"), n("50000"), n("0"), n("0"), sources)).tax());
        same("100000", account.getCurrentBalance());
    }

    @Test
    void ordinaryPensionAndSurvivorUseActualRecipient() throws Exception {
        var pension = new Pension("Names do not prove qualification", AccountOwnership.PRIMARY,
                LocalDate.of(2020, 1, 1), null, n("5000"), n("0"), n("5000"));
        var single = context(65, 60, false);
        var own = new TaxIncome(n("50000"), n("0"), n("0"), n("0"), n("0"),
                List.of(IncomeSourceAttribution.pension(pension, AccountOwnership.PRIMARY, n("50000"), 2026)));
        assertEquals(n("81.40"), calculate(single, false, own).tax());
        var couple = context(66, 60, true);
        var survivorContext = couple.withActiveOwners(Set.of(AccountOwnership.SPOUSE));
        var survivor = own.withIncomeSources(List.of(IncomeSourceAttribution.pension(pension, AccountOwnership.SPOUSE, n("50000"), 2026)));
        // Death year: joint basic 32,200 + deceased age 1,650 + enhanced 6,000;
        // surviving spouse's own 20,000 pension pool leaves no taxable income.
        assertEquals(n("0.00"), calculate(survivorContext, true, survivor).tax());
        assertEquals(n("611.60"), calculate(survivorContext, false, survivor).tax());
        var under55 = context(66, 54, true).withActiveOwners(Set.of(AccountOwnership.SPOUSE));
        assertThrows(IllegalArgumentException.class, () -> calculate(under55, true, survivor));
    }

    @Test
    void earlyAndMidyear59HalfReceiptsNeedTimingRatherThanYearEndAge() throws Exception {
        var income = new TaxIncome(n("0"), n("0"), n("50000"), n("0"), n("0"), List.of(ira(AccountOwnership.PRIMARY, "50000")));
        var owners = Set.of(AccountOwnership.PRIMARY);
        var midyear = new TaxHouseholdContext(LocalDate.of(2026, 1, 1),
                Map.of(AccountOwnership.PRIMARY, LocalDate.of(1967, 1, 2)), owners, owners, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> calculate(midyear, false, income));
        assertThrows(IllegalArgumentException.class, () -> calculate(context(55, 60, false), false, income));
        // Exactly 59.5 at the beginning of the annual receipt interval is sufficiently known.
        var openingEligible = new TaxHouseholdContext(LocalDate.of(2026, 1, 1),
                Map.of(AccountOwnership.PRIMARY, LocalDate.of(1966, 7, 1)), owners, owners, Optional.empty());
        assertEquals(n("611.60"), calculate(openingEligible, false, income).tax());
    }

    @Test
    void incompleteContradictoryAndUnverifiedSourcesAreExplicitlyRefused() throws Exception {
        var context = context(60, 60, false);
        var income = new TaxIncome(n("0"), n("0"), n("50000"), n("0"), n("0"));
        assertThrows(IllegalArgumentException.class, () -> calculate(context, false, income));
        assertThrows(IllegalArgumentException.class, () -> calculate(context, false,
                income.withIncomeSources(List.of(ira(AccountOwnership.PRIMARY, "50000").inYear(2027)))));
        var retirement = new TraditionalIRA("Misclassified IRA", AccountOwnership.PRIMARY, n("100000"));
        var mislabeled = IncomeSourceAttribution.withdrawal(retirement, n("5000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        assertThrows(IllegalArgumentException.class, () -> calculate(context, false,
                new TaxIncome(n("0"), n("0"), n("0"), n("0"), n("5000"), List.of(mislabeled))));
        var conversion = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                IncomeSourceAttribution.Purpose.CONVERSION, "Unverified conversion", n("20000"), Optional.of(n("20000"))).inYear(2026);
        var exception = assertThrows(IllegalArgumentException.class, () -> calculate(context, false,
                new TaxIncome(n("0"), n("0"), n("0"), n("20000"), n("0"), List.of(conversion))));
        assertTrue(exception.getMessage().contains("current official rule"));
        var employer = IncomeSourceAttribution.withdrawal(new Traditional401K("Employer", AccountOwnership.PRIMARY, n("100000")),
                n("50000"), IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        assertThrows(IllegalArgumentException.class, () -> calculate(context, false, income.withIncomeSources(List.of(employer))));
    }

    @Test
    void representedPensionNonretirementFactsOverrideOrdinaryPlanAssumption() throws Exception {
        var pension = new Pension("An ordinary-looking name", AccountOwnership.PRIMARY,
                LocalDate.of(2020, 1, 1), null, n("5000"), n("0"), n("0"));
        pension.setRetirementParticipantFacts(new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.NOT_SEPARATED, null,
                RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET, null));
        var income = new TaxIncome(n("50000"), n("0"), n("0"), n("0"), n("0"),
                List.of(IncomeSourceAttribution.pension(pension, AccountOwnership.PRIMARY, n("50000"), 2026)));
        assertThrows(IllegalArgumentException.class, () -> calculate(context(65, 60, false), false, income));
    }

    @Test
    void validFiniteAnnuityBasisDoesNotProveColoradoFullMaturity() throws Exception {
        var start = LocalDate.of(2026, 1, 1);
        var terms = new NonqualifiedAnnuityTerms(LocalDate.of(2020, 1, 1), start,
                n("100.00"), n("10000.00"), n("10000.00"), start,
                NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1);
        var recovery = new AnnuityBasisRecovery(terms, 0, 12, n("10000.00"), n("1200.00"), n("8800.00"));
        var receipt = new ConditionalBenefitReceipt(UUID.randomUUID(), "Already annuitized is not a maturity certificate",
                ConditionalBenefitDefinition.Category.NONQUALIFIED_INCOME_ANNUITY,
                new ConditionalBenefitDefinition.Trigger(ConditionalBenefitDefinition.TriggerType.FIXED_DATE, start, null, null),
                ConditionalBenefitDefinition.PersonReference.PRIMARY, ConditionalBenefitDefinition.PaymentPattern.MONTHLY_RECURRING,
                start, start, start, LocalDate.of(2026, 12, 31), 12, 2026, n("12000.00"), n("10800.00"), n("1200.00"),
                ConditionalBenefitDefinition.TaxCharacter.NONQUALIFIED_GENERAL_RULE_FIXED_EXCLUSION,
                ConditionalBenefitDefinition.ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH, recovery);
        var income = new TaxIncome(n("0"), n("0"), n("0"), n("0"), n("0"), List.of(IncomeSourceAttribution.conditional(receipt)));
        // The existing federal NIIT guard remains independent and must not be bypassed.
        assertThrows(NiitUnsupportedInputException.class, () -> calculate(context(65, 60, false), false, income));
        // Exercise the isolated state-source gate separately; this is not a funded/NIIT-admitted projection.
        var exception = assertThrows(IllegalArgumentException.class, () -> new ColoradoIncomeReconciliation().reconcile(
                2026, FilingStatus.SINGLE, context(65, 60, false), income,
                new FederalTaxCalculation(n("0"), n("0"), n("16100"), n("0"), n("0"))));
        assertTrue(exception.getMessage().contains("fully matured"));
        same("1200", receipt.excluded());
        same("10800", receipt.taxable());
    }

    @Test
    void noProductionAdmissionOrFutureTaxFallbackAndFrozenNotesAreImmutable() throws Exception {
        assertEquals(21, StateTaxJurisdiction.productionChoices().size());
        assertThrows(IllegalArgumentException.class, () -> StateTaxJurisdiction.fromCode("CO"));
        assertFalse(new ColoradoTaxRules().productionAdmitted());
        var result = calculate(context(60, 60, false), false, new TaxIncome(n("0"), n("0"), n("0"), n("0"), n("0")));
        assertThrows(UnsupportedOperationException.class, () -> result.limitations().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.retirement().socialSecuritySubtractions().clear());
        for (int year : List.of(2027, 2028, 2029, 2033, 2040)) {
            assertThrows(IllegalArgumentException.class, () -> new ColoradoTaxRules().tax(year, n("100000")));
        }
    }

    private static ColoradoTaxWorksheet.Result calculate(TaxHouseholdContext household, boolean joint, TaxIncome income) throws Exception {
        var status = joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var federal = new FederalTaxCalculator().calculate(income, status, rules);
        return new ColoradoTaxWorksheet().calculate(2026, status, household, income, federal);
    }

    private static IncomeSourceAttribution ira(AccountOwnership owner, String amount) {
        return IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", owner, n("1000000")), n(amount),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
    }

    private static IncomeSourceAttribution social(AccountOwnership owner, String amount) {
        return new IncomeSourceAttribution(IncomeSourceAttribution.Kind.SOCIAL_SECURITY, IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(owner), Optional.empty(), Optional.empty(), "Selected benefit", n(amount), Optional.empty(),
                OptionalInt.of(2026), Optional.empty());
    }

    private static TaxHouseholdContext context(int a, int b, boolean joint) {
        var births = joint ? Map.of(AccountOwnership.PRIMARY, LocalDate.of(2026 - a, 6, 1),
                AccountOwnership.SPOUSE, LocalDate.of(2026 - b, 8, 1))
                : Map.of(AccountOwnership.PRIMARY, LocalDate.of(2026 - a, 6, 1));
        return new TaxHouseholdContext(LocalDate.of(2026, 1, 1), births, births.keySet(), births.keySet(), Optional.empty());
    }
}
