package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.ReceiptFacts;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.ReceiptWindow;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasJointWorksheet;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRateWorksheet;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRetirementEligibility;
import com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasTaxRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PersonReference;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.*;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.*;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.Form5329Status.*;
import static com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence.SourceQualification.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasDomainCalculationTest {
    private static final LocalDate OPENING = LocalDate.of(2026, 1, 1);
    private static final LocalDate BIRTH = LocalDate.of(1965, 1, 1);
    private static final LocalDate SENIOR = LocalDate.of(1958, 1, 1);
    private static final StateTaxAssumptions CARRIER = StateTaxAssumptions.simplified(StateTaxJurisdiction.FL);
    private static final ArkansasProductionTaxCalculator ARKANSAS = new ArkansasProductionTaxCalculator();
    private static GovernmentRules published;

    @BeforeAll
    static void loadRules() throws Exception {
        published = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
    }

    @Test
    void actualAccountWithdrawalsRmdAndExecutedConversionShareOneCap() {
        var first = ira(PRIMARY);
        var second = ira(PRIMARY);
        var spending = withdrawal(first, "10000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var rmd = withdrawal(second, "17000", RMD, CASH_FLOW);
        var opening = withdrawal(second, "3000", OPENING_RMD, INCOME);
        var conversion = conversion(first, "20000", true);
        var income = income("0", "20000", "30000", "20000", "0", spending, rmd, opening, conversion);
        var result = ARKANSAS.calculateUnregistered(request(income,
                single(OPENING.plusMonths(6), LocalDate.of(1950, 1, 1))), ArkansasTaxEvidence.NONE);
        assertEquals(b("1111.41"), result.incomeTax());
        assertTrue(result.limitations().getLast().contains("retirement exclusion 6000"));
        assertEquals(0, first.getCurrentBalance().compareTo(b("1000000")));
    }

    @Test
    void actualJointCalculatesEachPoolAndAgeCreditWithOneCombinedSchedule() {
        var first = withdrawal(ira(PRIMARY), "50000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var second = withdrawal(ira(SPOUSE), "50000", ORDINARY_RETIREMENT_WITHDRAWAL, TAX_FUNDING);
        var household = joint(BIRTH, SENIOR, Set.of(PRIMARY, SPOUSE), OPENING);
        assertEquals(b("2619.02"), calculate(income("0", "0", "100000", "0", "0", first, second), household).incomeTax());
        var concentrated = withdrawal(ira(PRIMARY), "100000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        assertEquals(b("2812.02"), calculate(income("0", "0", "100000", "0", "0", concentrated), household).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"1966-01-15,2025-07-15", "1966-08-31,2026-02-28", "1964-02-29,2023-08-28",
            "1968-08-31,2028-02-29"})
    void preparatoryHalfBirthdayCalendarIsExplicit(String birth, String expected) {
        assertEquals(LocalDate.parse(expected), ArkansasRetirementEligibility.age59AndHalf(LocalDate.parse(birth)));
    }

    @Test
    void possibleEarlyCashRequiresFormEvidenceAndMaterialTimingRequiresARealWindow() {
        var birth = LocalDate.of(1966, 8, 31);
        var household = single(OPENING, birth);
        var receipt = withdrawal(ira(PRIMARY), "50000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var income = income("0", "0", "50000", "0", "0", receipt);
        var request = request(income, household);
        unsupported(ARKANSAS.calculateUnregistered(request, ArkansasTaxEvidence.NONE), "Form 5329");
        unsupported(ARKANSAS.calculateUnregistered(request, facts(receipt, Optional.empty(), UNSPECIFIED,
                NO_APPLICABLE_ADDITIONAL_TAX)), "timing is material");
        var cutoff = LocalDate.of(2026, 2, 28);
        assertEquals(b("1140.41"), ARKANSAS.calculateUnregistered(request, facts(receipt, date(cutoff),
                UNSPECIFIED, UNKNOWN)).incomeTax());
        assertEquals(b("1362.41"), ARKANSAS.calculateUnregistered(request, facts(receipt, date(cutoff.minusDays(1)),
                UNSPECIFIED, NO_APPLICABLE_ADDITIONAL_TAX)).incomeTax());
        unsupported(ARKANSAS.calculateUnregistered(request, facts(receipt, date(cutoff.minusDays(1)),
                UNSPECIFIED, APPLICABLE_ADDITIONAL_TAX)), "Form 5329");
    }

    @Test
    void saturatedPoolAndTaxStableZeroAdmitReceiptTimingUncertainty() {
        var household = single(OPENING, LocalDate.of(1966, 8, 31));
        var a = withdrawal(ira(PRIMARY), "44000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var c = conversion(ira(PRIMARY), "6000", true);
        var evidence = facts(a, date(OPENING.plusMonths(4)), UNSPECIFIED, UNKNOWN);
        assertEquals(b("1140.41"), ARKANSAS.calculateUnregistered(
                request(income("0", "0", "44000", "6000", "0", a, c), household), evidence).incomeTax());
        var small = conversion(ira(PRIMARY), "100", true);
        assertEquals(b("0.00"), calculate(income("0", "0", "0", "100", "0", small), household).incomeTax());
    }

    @Test
    void actualEarlyRothConversionHasTaxableInclusionWithoutAnAgePenaltyInference() {
        var conversion = conversion(ira(PRIMARY), "50000", true);
        var household = single(OPENING, LocalDate.of(1976, 1, 1));
        assertEquals(b("1362.41"), calculate(income("0", "0", "0", "50000", "0", conversion), household).incomeTax());
        var missingDestination = conversion(ira(PRIMARY), "50000", false);
        unsupported(calculate(income("0", "0", "0", "50000", "0", missingDestination), household), "destination");
        var employer = conversion(new Traditional401K("Employer", PRIMARY, b("50000")), "50000", true);
        unsupported(calculate(income("0", "0", "0", "50000", "0", employer), household), "only covers");
        unsupported(calculate(income("0", "0", "0", "60000", "0", conversion), household), "contradicts");
    }

    @Test
    void ordinaryEmployerCashCanQualifyBeforeTheOwnIraAge() {
        var account = new Traditional401K("Ordinary 401(k)", PRIMARY, b("100000"));
        var source = withdrawal(account, "50000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var request = request(income("0", "0", "50000", "0", "0", source),
                single(OPENING, LocalDate.of(1976, 1, 1)));
        assertEquals(b("1140.41"), ARKANSAS.calculateUnregistered(request,
                facts(source, Optional.empty(), UNSPECIFIED, NO_APPLICABLE_ADDITIONAL_TAX)).incomeTax());
        unsupported(ARKANSAS.calculateUnregistered(request,
                facts(source, Optional.empty(), OUTSIDE_ORDINARY_SCOPE, NO_APPLICABLE_ADDITIONAL_TAX)), "specialized");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ordinaryDbAndDcAndDirectSurvivorPensionsUseTheRecipientPool(boolean definedBenefit) {
        var pension = new Pension("Ordinary plan", PRIMARY, OPENING.minusYears(4), null,
                b("5000"), ZERO, b("3000"));
        pension.setPlanClassification(definedBenefit ? PensionPlanClassification.DEFINED_BENEFIT_414J
                : PensionPlanClassification.DEFINED_CONTRIBUTION);
        var original = IncomeSourceAttribution.pension(pension, PRIMARY, b("50000"), 2026);
        var income = income("50000", "0", "0", "0", "0", original);
        unsupported(calculate(income, single(OPENING, BIRTH)), "employment-related");
        assertEquals(b("1140.41"), ARKANSAS.calculateUnregistered(request(income, single(OPENING, BIRTH)),
                facts(original, Optional.empty(), ORDINARY_EMPLOYMENT_PENSION, UNKNOWN)).incomeTax());
        var survivor = IncomeSourceAttribution.pension(pension, SPOUSE, b("50000"), 2026);
        var ira = withdrawal(ira(SPOUSE), "50000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var survivorIncome = income("50000", "0", "50000", "0", "0", survivor, ira);
        var household = joint(BIRTH, BIRTH, Set.of(SPOUSE), OPENING);
        var receiptFacts = new ReceiptFacts(Optional.empty(), ORDINARY_EMPLOYMENT_PENSION, UNKNOWN);
        var deathYear = new ArkansasTaxEvidence(Map.of(survivor, receiptFacts), Map.of(PRIMARY, OPENING));
        assertEquals(b("2870.02"), ARKANSAS.calculateUnregistered(request(survivorIncome, household), deathYear).incomeTax());
        var later = new ArkansasTaxEvidence(Map.of(survivor, receiptFacts), Map.of(PRIMARY, OPENING.minusYears(1)));
        assertEquals(b("2990.41"), ARKANSAS.calculateUnregistered(
                request(survivorIncome, household, FilingStatus.SINGLE, published), later).incomeTax());
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var established = IncomeSourceAttribution.pension(pension, PRIMARY, b("50000"), 2026);
        assertEquals(b("1140.41"), calculate(income("50000", "0", "0", "0", "0", established),
                single(OPENING, BIRTH)).incomeTax());
        var otherYear = IncomeSourceAttribution.pension(pension, PRIMARY, b("50000"), 2027);
        unsupported(calculate(income("50000", "0", "0", "0", "0", otherYear), single(OPENING, BIRTH)), "same tax year");
        unsupported(ARKANSAS.calculateUnregistered(request(income, single(OPENING, BIRTH)),
                facts(established, Optional.empty(), ORDINARY_EMPLOYMENT_PENSION, UNKNOWN)), "absent payment");
    }

    @Test
    void jointPensionOriginsAreIndependentOfTheOtherRecipientsCompletedPool() {
        var first = new Pension("First primary source", PRIMARY, OPENING, null, b("5000"), ZERO);
        var second = new Pension("Second primary source", PRIMARY, OPENING, null, b("4000"), ZERO);
        var third = new Pension("Spouse source", SPOUSE, OPENING, null, b("4500"), ZERO);
        first.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        second.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var a = IncomeSourceAttribution.pension(first, PRIMARY, b("40000"), 2026);
        var b = IncomeSourceAttribution.pension(second, PRIMARY, b("10000"), 2026);
        var unknown = IncomeSourceAttribution.pension(third, SPOUSE, b("50000"), 2026);
        var joint = joint(BIRTH, BIRTH.plusYears(1), Set.of(PRIMARY, SPOUSE), OPENING);
        unsupported(calculate(income("100000", "0", "0", "0", "0", a, b, unknown), joint), "employment-related");
        third.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var known = IncomeSourceAttribution.pension(third, SPOUSE, b("50000"), 2026);
        assertEquals(b("2648.02"), calculate(income("100000", "0", "0", "0", "0", a, b, known), joint).incomeTax());
    }

    @Test
    void originUncertaintyUsesTheSameRecipientCapAndAdmitsTaxStableZero() {
        var pension = new Pension("Unresolved", PRIMARY, OPENING, null, b("500"), ZERO);
        var fullPool = withdrawal(ira(PRIMARY), "50000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var small = IncomeSourceAttribution.pension(pension, PRIMARY, b("500"), 2026);
        assertEquals(b("0.00"), calculate(income("500", "0", "0", "0", "0", small),
                single(OPENING, BIRTH)).incomeTax());
        assertEquals(b("1158.91"), calculate(income("500", "0", "50000", "0", "0", small, fullPool),
                single(OPENING, BIRTH)).incomeTax());
    }

    @Test
    void employerOriginDoesNotOverrideSpecialTreatmentAndPrivateContractRequiresAnnuityEvidence() {
        var pension = new Pension("Unverified special plan", PRIMARY, OPENING, null, b("5000"), ZERO);
        for (var origin : List.of(
                RetirementIncomeSourceOrigin.INDIVIDUALLY_PURCHASED_CONTRACT,
                RetirementIncomeSourceOrigin.OTHER_OR_SPECIAL_ARRANGEMENT)) {
            pension.setSourceOrigin(origin);
            var receipt = IncomeSourceAttribution.pension(pension, PRIMARY, b("50000"), 2026);
            var input = income("50000", "0", "0", "0", "0", receipt);
            unsupported(calculate(input, single(OPENING, BIRTH)), "outside ordinary");
            unsupported(ARKANSAS.calculateUnregistered(request(input, single(OPENING, BIRTH)),
                    facts(receipt, Optional.empty(), ORDINARY_EMPLOYMENT_PENSION, UNKNOWN)), "contradicts");
        }
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var receipt = IncomeSourceAttribution.pension(pension, PRIMARY, b("50000"), 2026);
        var input = income("50000", "0", "0", "0", "0", receipt);
        unsupported(ARKANSAS.calculateUnregistered(request(input, single(OPENING, BIRTH)),
                facts(receipt, Optional.empty(), OUTSIDE_ORDINARY_SCOPE, UNKNOWN)), "specialized");
        unsupported(calculate(input, single(OPENING, LocalDate.of(1976, 1, 1))), "5329");
        assertEquals(b("1140.41"), ARKANSAS.calculateUnregistered(
                request(input, single(OPENING, LocalDate.of(1976, 1, 1))),
                facts(receipt, Optional.empty(), UNSPECIFIED, NO_APPLICABLE_ADDITIONAL_TAX)).incomeTax());
    }

    @Test
    void januaryFollowingYearCreditAndMaterialDeathBoundary() {
        assertEquals(b("1304.41"), calculate(income("0", "0", "0", "0", "50000"),
                single(OPENING, LocalDate.of(1962, 1, 1))).incomeTax());
        assertEquals(b("1362.41"), calculate(income("0", "0", "0", "0", "50000"),
                single(OPENING, LocalDate.of(1962, 1, 2))).incomeTax());
        var joint = joint(LocalDate.of(1961, 6, 1), BIRTH, Set.of(SPOUSE), OPENING);
        var death = new ArkansasTaxEvidence(Map.of(), Map.of(PRIMARY, OPENING));
        unsupported(ARKANSAS.calculateUnregistered(request(income("0", "0", "0", "0", "50000"), joint), death), "age-65");
        assertEquals(b("0.00"), ARKANSAS.calculateUnregistered(request(income("0", "0", "0", "0", "100"), joint), death).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"94699.9999,3049.70", "94700.9999,3049.74", "94701.0000,3047.04",
            "94801,3060.74", "97601,3444.34"})
    void seniorElectionPreservesFractionalHighScheduleSteps(String net, String expected) {
        var smallIra = withdrawal(ira(PRIMARY), "200", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var taxableInvestments = b(net).add(b("2470")).subtract(b("200")).toPlainString();
        assertEquals(b(expected), calculate(income("0", "0", "200", "0", taxableInvestments, smallIra),
                single(OPENING, SENIOR)).incomeTax());
    }

    @Test
    void directTaxAppliesEveryAdjustmentAtTheTransitionOnBothSides() {
        var household = single(OPENING, BIRTH);
        for (int step = 0; step <= 29; step++) {
            var edge = BigDecimal.valueOf(94701 + 100L * step);
            for (var delta : List.of(b("-.0001"), ZERO, b(".0001"))) {
                var net = edge.add(delta);
                boolean before = delta.signum() < 0;
                long subtraction = Math.max(290 - 10L * (before ? step - 1 : step), 0);
                var expected = net.multiply(b(".037"))
                        .subtract(before && step == 0 ? b("367.20") : b("79.90").add(BigDecimal.valueOf(subtraction)))
                        .subtract(b("29")).setScale(2, RoundingMode.HALF_UP);
                assertEquals(expected, calculate(income("0", "0", "0", "0",
                        net.add(b("2470")).toPlainString()), household).incomeTax(), net.toPlainString());
            }
        }
    }

    @Test
    void equalEndpointTaxesDoNotHideMaterialTimingInsideTheHighScheduleJump() {
        var birth = LocalDate.of(1966, 8, 31);
        var conversion = conversion(ira(PRIMARY), "72.97", true);
        var income = income("0", "0", "0", "72.97", "97170", conversion);
        var request = request(income, single(OPENING, birth));
        // Both $94,700.00 (full exclusion) and $94,772.97 (none) round to $3,107.70 after credit.
        assertEquals(b("3107.70"), ARKANSAS.calculateUnregistered(
                request, facts(conversion, date(OPENING), UNSPECIFIED, UNKNOWN)).incomeTax());
        assertEquals(b("3107.70"), ARKANSAS.calculateUnregistered(
                request, facts(conversion, date(OPENING.plusMonths(3)), UNSPECIFIED, UNKNOWN)).incomeTax());
        unsupported(ARKANSAS.calculateUnregistered(request, ArkansasTaxEvidence.NONE), "timing is material");
    }

    @Test
    void missingLowIncomeTablesAdmitOnlyTaxStableZeroOrAnUnaffectedRegularEstimate() {
        var primary = single(OPENING, BIRTH);
        assertEquals(b("0.00"), calculate(income("0", "50000", "0", "0", "8500"), primary).incomeTax());
        unsupported(calculate(income("0", "0", "0", "0", "18000"), primary), "low-income");
        unsupported(calculate(income("0", "0", "0", "0", "30970"), primary), "low-income");
        assertEquals(b("658.30"), calculate(income("0", "0", "0", "0", "30970.01"), primary).incomeTax());
        var joint = joint(BIRTH, BIRTH, Set.of(PRIMARY, SPOUSE), OPENING);
        unsupported(calculate(income("0", "0", "0", "0", "35000"), joint), "low-income");
        assertEquals(b("874.98"), calculate(income("0", "0", "0", "0", "40080"), joint).incomeTax());

        var senior = single(OPENING, SENIOR);
        var smallIra = withdrawal(ira(PRIMARY), "200", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var worksheet = new ArkansasJointWorksheet();
        var recipient = new ArkansasJointWorksheet.Recipient(PRIMARY, b("200"), true, false);
        for (var boundary : List.of(List.of("5600", "0.00"), List.of("11200", "25.00"),
                List.of("16000", "169.00"), List.of("26400", "522.60"))) {
            for (var delta : List.of(b("-.0001"), ZERO, b(".0001"))) {
                var gross = b(boundary.getFirst()).add(delta).add(b("2470"));
                var regular = worksheet.lowestReviewedChoice(gross, List.of(recipient),
                        ArkansasRateWorksheet.Policy.P_STATUTORY_CENTS);
                assertEquals(b("87"), regular.credits());
                assertEquals(b(boundary.getLast()), regular.tax());
                var result = calculate(income("0", "0", "200", "0",
                        gross.subtract(b("200")).toPlainString(), smallIra), senior);
                if (regular.tax().signum() == 0) assertEquals(b("0.00"), result.incomeTax());
                else unsupported(result, "low-income");
            }
        }
    }

    @Test
    void brokeragePrincipalAndQualifiedRothAreCashAndInvestmentBucketIsCountedOnce() {
        var broker = new BrokerageAccount("Brokerage", PRIMARY, b("100000"));
        var cash = withdrawal(broker, "60000", ORDINARY_WITHDRAWAL, TAX_FUNDING);
        var roth = withdrawal(new RothIRA("Roth", PRIMARY, b("100000")), "20000", ORDINARY_RETIREMENT_WITHDRAWAL, CASH_FLOW);
        var gain = new IncomeSourceAttribution(CAPITAL_GAIN, INCOME, Optional.of(PRIMARY),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(broker)), Optional.empty(), "Generic gain",
                b("20000"), Optional.of(b("20000")), OptionalInt.of(2026), Optional.empty());
        var base = income("0", "25000", "0", "0", "50000", investmentPlaceholder("50000"));
        var attributed = TaxIncomeAttribution.attach(base, List.of(cash, gain, roth), 2026);
        var household = single(OPENING, BIRTH);
        assertEquals(b("1362.41"), calculate(attributed, household).incomeTax());
        assertEquals(b("0.00"), calculate(income("0", "0", "0", "0", "0", cash, roth), household).incomeTax());
        unsupported(calculate(attributed.withIncomeSources(List.of(cash, gain, gain)), household), "Duplicate");
        unsupported(calculate(attributed.withIncomeSources(List.of(gain.inYear(2027))), household), "same tax year");
        var gainsInCash = new IncomeSourceAttribution(ORDINARY_WITHDRAWAL, TAX_FUNDING, cash.owner(),
                cash.account(), Optional.empty(), cash.description(), cash.grossAmount(), Optional.of(b("1000")),
                cash.taxYear(), Optional.empty());
        unsupported(calculate(attributed.withIncomeSources(List.of(gain, gainsInCash)), household), "Overlapping");
        unsupported(calculate(income("0", "0", "0", "0", "1000", gain), household), "investment bucket");
        var advanced = new ArrayList<>(attributed.getIncomeSources());
        advanced.add(IncomeSourceAttribution.calculationPolicy(FinancialCalculationMode.ADVANCED, 2026));
        var invalid = attributed.withIncomeSources(advanced);
        var substituted = new StateTaxCalculationRequest(2026, FilingStatus.SINGLE, household, invalid,
                request(attributed, household).federal(), published, CARRIER);
        unsupported(ARKANSAS.calculateUnregistered(substituted, ArkansasTaxEvidence.NONE), "NIIT");
    }

    @Test
    void commercialAnnuityTaxableFractionAndAdmittedConditionalIncomeEnterOnce() {
        var annuity = com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest.annuity(
                PersonReference.PRIMARY, OPENING, OPENING, "5000", "2500", "50000");
        var context = new BenefitEvaluationContext(BIRTH, Optional.empty(), EffectiveHouseholdDeathView.resolve(
                new Household(new Person("A", "Example", BIRTH), null),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), Optional.empty()), OPENING, 2026);
        var annuitySource = IncomeSourceAttribution.conditional(
                new ConditionalBenefitEvaluator().evaluate(List.of(annuity), context).receipts().getFirst());
        var income = TaxIncomeAttribution.attach(income("0", "0", "0", "0", "20000", investmentPlaceholder("20000")),
                List.of(annuitySource), 2026);
        var result = calculate(income, single(OPENING, BIRTH));
        assertEquals(b("1362.41"), result.incomeTax());
        assertEquals(b("30000.00"), income.getConditionalOrdinaryIncome());
        assertTrue(request(income, single(OPENING, BIRTH)).federal().niit().isPresent());

        var trigger = new ConditionalBenefitDefinition.Trigger(ConditionalBenefitDefinition.TriggerType.FIXED_DATE, OPENING, null, null);
        var payment = new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt(java.util.UUID.randomUUID(),
                "Ordinary conditional receipt", ConditionalBenefitDefinition.Category.CONDITIONAL_INCOME, trigger, PersonReference.PRIMARY,
                ConditionalBenefitDefinition.PaymentPattern.ONE_TIME_LUMP_SUM, OPENING, OPENING, OPENING, OPENING, 0, 2026,
                b("50000.00"), b("50000.00"), ZERO, ConditionalBenefitDefinition.TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION,
                ConditionalBenefitDefinition.ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH);
        var taxable = TaxIncomeAttribution.attach(income("0", "0", "0", "0", "0"),
                List.of(IncomeSourceAttribution.conditional(payment)), 2026);
        assertEquals(b("1362.41"), calculate(taxable, single(OPENING, BIRTH)).incomeTax());
    }

    @Test
    void futureNominalPolicyUsesSameDollarParametersAndExplicitUnregisteredResult() {
        int year = 2040;
        var assumptions = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createSinglePersonPlan()
                .getPlanningAssumptions();
        var rules = new GovernmentRuleProjectionService().project(published, assumptions, year);
        var h = single(LocalDate.of(year, 1, 1), LocalDate.of(1979, 1, 1));
        var result = ARKANSAS.calculateUnregistered(request(income("0", "0", "0", "0", "50000"),
                h, FilingStatus.SINGLE, rules), ArkansasTaxEvidence.NONE);
        assertEquals(b("1362.41"), result.incomeTax());
        assertTrue(result.limitations().getLast().contains("held nominally for 2040"));
        assertEquals(20, StateTaxJurisdiction.alphabeticalChoices().size());
        assertFalse(ArkansasTaxRules.productionAdmitted());
        assertThrows(IllegalArgumentException.class, () -> ArkansasTaxRules.reviewedYearFor(2025));
        assertThrows(IllegalArgumentException.class, () -> new ArkansasRateWorksheet().grossTax(year,
                b("50000"), ArkansasRateWorksheet.Policy.OFFICIAL_2026_ESTIMATED_TABLE));
    }

    @ParameterizedTest
    @CsvSource({"2026,23148,37035,28500", "2027,23843,38147,29300", "2030,26055,41686,32000",
            "2040,35023,56028,43100", "2100,206432,330175,253700", "2426,3160420703,5054773467,3882564700"})
    void conservativeCeilingsUseTheirOwnStatutoryBasesAndRounding(int year, String single, String joint, String credit) {
        assertEquals(0, b(single).compareTo(ArkansasTaxRules.lowIncomeGrossCeiling(year, false)));
        assertEquals(0, b(joint).compareTo(ArkansasTaxRules.lowIncomeGrossCeiling(year, true)));
        assertEquals(0, b(credit).compareTo(ArkansasTaxRules.additionalCreditNetCeiling(year)));
        if (year == 2426) {
            assertThrows(IllegalArgumentException.class, () -> ArkansasTaxRules.additionalCreditNetCeiling(year + 1));
        }
    }

    @Test
    void increasingLegalCeilingsCanRefuseTheSameNominalResultInTheNextYear() {
        var noReliefIn2026 = income("0", "0", "0", "0", "31000");
        assertEquals(b("659.41"), calculate(noReliefIn2026, single(OPENING, BIRTH)).incomeTax());
        var assumptions = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createSinglePersonPlan()
                .getPlanningAssumptions();
        var nextRules = new GovernmentRuleProjectionService().project(published, assumptions, 2027);
        var future = single(OPENING.plusYears(1), BIRTH);
        unsupported(ARKANSAS.calculateUnregistered(request(noReliefIn2026, future, FilingStatus.SINGLE, nextRules),
                ArkansasTaxEvidence.NONE), "low-income");
        assertEquals(b("0.00"), ARKANSAS.calculateUnregistered(request(
                income("0", "0", "0", "0", "100"), future, FilingStatus.SINGLE, nextRules),
                ArkansasTaxEvidence.NONE).incomeTax());
        for (int year = 2027; year <= 2100; year++) {
            assertTrue(ArkansasTaxRules.lowIncomeGrossCeiling(year, false)
                    .compareTo(ArkansasTaxRules.lowIncomeGrossCeiling(year - 1, false)) >= 0);
            assertTrue(ArkansasTaxRules.additionalCreditNetCeiling(year)
                    .compareTo(ArkansasTaxRules.additionalCreditNetCeiling(year - 1)) >= 0);
        }
    }

    @Test
    void productionEnvelopeCompatibilityUsesOnlyAnExplicitTestAdapter() {
        var request = request(income("0", "0", "0", "0", "18000"), single(OPENING, BIRTH));
        var arkansas = ARKANSAS.calculateUnregistered(request, ArkansasTaxEvidence.NONE);
        var testEnvelope = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.FL,
                CARRIER.calculationMethod(), arkansas.unavailableReasons(), arkansas.limitations().getFirst());
        assertThrows(StateTaxUnavailableException.class, () -> StateTaxUnavailableException.requireCalculated(testEnvelope, 2026));
        assertEquals(0, new ProductionStateTaxService().calculate(request).incomeTax().signum());
    }

    @Test
    void inconsistentFederalIncomeAndOutsidePolicyAreDistinguished() {
        var h = single(OPENING, BIRTH);
        var income = income("0", "15000", "0", "0", "50000");
        var original = request(income, h);
        var federal = original.federal();
        var mismatched = new StateTaxCalculationRequest(2026, FilingStatus.SINGLE, h, income,
                new FederalTaxCalculation(federal.getAdjustedGrossIncome(), ZERO, federal.getStandardDeduction(),
                        federal.getTaxableIncome(), federal.getFederalIncomeTax()), published, CARRIER);
        var discrepancy = ARKANSAS.calculateUnregistered(mismatched, ArkansasTaxEvidence.NONE);
        unsupported(discrepancy, "federal AGI");
        assertEquals(Optional.of(ArkansasProductionTaxCalculator.FailureCategory.ATTRIBUTION), discrepancy.failureCategory());
        var old = new StateTaxCalculationRequest(2025, FilingStatus.SINGLE, single(OPENING.minusYears(1), BIRTH),
                income, federal, published, CARRIER);
        var outsidePolicy = ARKANSAS.calculateUnregistered(old, ArkansasTaxEvidence.NONE);
        unsupported(outsidePolicy, "before 2026");
        assertEquals(Optional.of(ArkansasProductionTaxCalculator.FailureCategory.OUTSIDE_REVIEWED_POLICY),
                outsidePolicy.failureCategory());
    }

    private static ArkansasProductionTaxCalculator.Result calculate(TaxIncome income, TaxHouseholdContext household) {
        return ARKANSAS.calculateUnregistered(request(income, household), ArkansasTaxEvidence.NONE);
    }

    private static StateTaxCalculationRequest request(TaxIncome income, TaxHouseholdContext household) {
        return request(income, household, household.householdOwners().size() == 2 ? FilingStatus.MARRIED_FILING_JOINTLY
                : FilingStatus.SINGLE, published);
    }

    private static StateTaxCalculationRequest request(TaxIncome income, TaxHouseholdContext household,
                                                       FilingStatus filingStatus, GovernmentRules rules) {
        return new StateTaxCalculationRequest(household.distributionDate().getYear(), filingStatus, household, income,
                new FederalTaxCalculator().calculate(income, filingStatus, rules), rules, CARRIER);
    }

    private static TaxHouseholdContext single(LocalDate date, LocalDate birth) {
        return new TaxHouseholdContext(date, Map.of(PRIMARY, birth), Set.of(PRIMARY), Set.of(PRIMARY), Optional.empty());
    }

    private static TaxHouseholdContext joint(LocalDate primary, LocalDate spouse, Set<AccountOwnership> active, LocalDate date) {
        return new TaxHouseholdContext(date, Map.of(PRIMARY, primary, SPOUSE, spouse),
                Set.of(PRIMARY, SPOUSE), active, Optional.empty());
    }

    private static TaxIncome income(String pension, String ss, String retirement, String conversion, String investments,
                                    IncomeSourceAttribution... sources) {
        return new TaxIncome(b(pension), b(ss), b(retirement), b(conversion), b(investments), List.of(sources));
    }

    private static TraditionalIRA ira(AccountOwnership owner) {
        return new TraditionalIRA("IRA", owner, b("1000000"));
    }

    private static IncomeSourceAttribution withdrawal(Account account, String amount, IncomeSourceAttribution.Kind kind,
                                                      IncomeSourceAttribution.Purpose purpose) {
        return IncomeSourceAttribution.withdrawal(account, b(amount), kind, purpose).inYear(2026);
    }

    private static IncomeSourceAttribution conversion(Account account, String amount, boolean destination) {
        return new IncomeSourceAttribution(ROTH_CONVERSION, CONVERSION, Optional.of(account.getOwnership()),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(account)),
                destination ? Optional.of(IncomeSourceAttribution.AccountReference.capture(
                        new RothIRA("Destination", account.getOwnership(), ZERO))) : Optional.empty(),
                "Completed transfer", b(amount), Optional.of(b(amount)), OptionalInt.of(2026), Optional.empty());
    }

    private static IncomeSourceAttribution investmentPlaceholder(String amount) {
        return IncomeSourceAttribution.aggregate(INTEREST, INCOME, "Unattributed INTEREST",
                b(amount), Optional.of(b(amount))).inYear(2026);
    }

    private static Optional<ReceiptWindow> date(LocalDate date) {
        return Optional.of(new ReceiptWindow(date, date));
    }

    private static ArkansasTaxEvidence facts(IncomeSourceAttribution source, Optional<ReceiptWindow> window,
            ArkansasTaxEvidence.SourceQualification qualification, ArkansasTaxEvidence.Form5329Status form5329) {
        return new ArkansasTaxEvidence(Map.of(source, new ReceiptFacts(window, qualification, form5329)), Map.of());
    }

    private static void unsupported(ArkansasProductionTaxCalculator.Result result, String message) {
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus(), result.limitations().toString());
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertFalse(result.unavailableReasons().isEmpty());
        assertTrue(result.limitations().getFirst().contains(message), result.limitations().toString());
    }

    private static BigDecimal b(String value) { return new BigDecimal(value); }
}
