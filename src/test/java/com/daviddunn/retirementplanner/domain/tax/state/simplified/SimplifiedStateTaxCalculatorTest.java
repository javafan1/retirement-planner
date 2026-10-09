package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import com.daviddunn.retirementplanner.persistence.StateTaxDatasetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SimplifiedStateTaxCalculatorTest {

    private final SimplifiedStateTaxCalculator calculator = new SimplifiedStateTaxCalculator();

    SimplifiedStateTaxCalculatorTest() throws Exception { }

    @ParameterizedTest
    @ValueSource(strings = {"CA", "MI", "FL", "IL", "PA", "NY", "CO", "NJ"})
    void eachStateParsesAndPreservesReadiness(String code) throws Exception {
        StateTaxRules rules = new StateTaxDatasetRepository().load().get(code);
        assertEquals(2026, rules.taxYear());
        assertEquals(Set.of("FL", "IL").contains(code) ? "READY_FOR_SIMPLIFIED_MODEL" : "READY_WITH_LIMITATIONS", rules.readiness());
        StateTaxResult result = calculator.calculate(single(code, "0", "0", "70"));
        assertEquals(StateTaxResult.Status.CALCULATED, result.status());
        tax(result, "0.00");
    }

    @ParameterizedTest
    @CsvSource({"CA,418.88", "MI,2048.50", "FL,0.00", "IL,2680.43", "PA,1842.00",
            "NY,2040.80", "CO,1980.00", "NJ,1001.00"})
    void eachStateJointNonretirementArithmetic(String code, String expected) {
        tax(calculator.calculate(joint(code, "60000", "45000",
                person(Owner.PRIMARY, "40", line(IncomeType.INTEREST, "50000")),
                person(Owner.SPOUSE, "40", line(IncomeType.INTEREST, "10000")))), expected);
    }

    @Test
    void classifiedInvestmentIncomeIsDisjointAndStateDefinedBasisIsExplicit() {
        Income interest = new Income(IncomeType.INTEREST, bd("10000"), bd("20000"), PensionSource.PRIVATE, Map.of());
        Income ordinary = line(IncomeType.ORDINARY_DIVIDEND, "1000");
        Income qualified = line(IncomeType.QUALIFIED_DIVIDEND, "2000");
        Income shortGain = line(IncomeType.SHORT_TERM_CAPITAL_GAIN, "3000");
        Income longGain = line(IncomeType.LONG_TERM_CAPITAL_GAIN, "4000");
        StateTaxResult result = calculator.calculate(single("PA", "20000", "0", "40",
                interest, ordinary, qualified, shortGain, longGain));
        amount(result.estimate().orElseThrow().startingTaxBase(), "30000");
        tax(result, "921.00");
    }

    @Test
    void explicitIneligibleRetirementCannotReceiveSubtraction() {
        Income income = new Income(IncomeType.TRADITIONAL_IRA, bd("80000"), bd("80000"), PensionSource.PRIVATE,
                Map.of("QUALIFYING_RETIREMENT_BENEFIT_ONLY", Qualification.INELIGIBLE));
        tax(calculator.calculate(single("MI", "100000", "0", "65", income)), "3999.25");
    }

    @Test
    void pennsylvaniaDisabilityEventAllowsUnderAgeExclusion() {
        PersonIncome primary = new PersonIncome(Owner.PRIMARY, bd("40"), BigDecimal.ZERO, BigDecimal.ZERO,
                Set.of("DISABILITY"), List.of(line(IncomeType.TRADITIONAL_IRA, "10000")));
        tax(calculator.calculate(new StateTaxRequest("PA", 2026, FilingStatus.SINGLE,
                bd("10000"), BigDecimal.ZERO, List.of(primary))), "0.00");
    }

    @Test
    void newJerseyUpperBandPercentageAndCoupleSeniorDeductions() {
        StateTaxResult result = calculator.calculate(joint("NJ", "140000", "0",
                person(Owner.PRIMARY, "65", line(IncomeType.PENSION, "70000")),
                person(Owner.SPOUSE, "65", line(IncomeType.PENSION, "70000"))));
        amount(result.estimate().orElseThrow().retirementExclusions().getFirst().amount(), "35000");
        amount(result.estimate().orElseThrow().taxableIncome(), "101000");
        // 280 +525 +490 +350 +21,000*.05525 = 2,805.25.
        tax(result, "2805.25");
    }

    @ParameterizedTest
    @CsvSource({"0,false", "50000,false", "2000000,false", "0,true", "50000,true", "2000000,true"})
    void floridaOfficialNoPersonalIncomeTax(String amount, boolean joint) {
        // D: Florida DOR FAQ 1466 states no personal income tax; applies independently of these incomes/statuses.
        StateTaxRequest request = joint
                ? joint("FL", amount, amount, person(Owner.PRIMARY, "65"), person(Owner.SPOUSE, "70"))
                : single("FL", amount, amount, "65");
        tax(calculator.calculate(request), "0.00");
    }

    @Test
    void californiaAgiSocialSecurityDeductionAndCredit() {
        StateTaxResult result = calculator.calculate(single("CA", "60000", "45000", "65",
                line(IncomeType.SOCIAL_SECURITY, "17000"), line(IncomeType.TRADITIONAL_IRA, "30000")));
        tax(result, "668.30");
        amount(result.estimate().orElseThrow().taxableIncome(), "37100");
        amount(result.estimate().orElseThrow().incomeAdjustments().getFirst().amount(), "17000");
    }

    @Test
    void californiaRetirementAndConversionNotAddedToAgiAgain() {
        StateTaxResult result = calculator.calculate(single("CA", "60000", "45000", "65",
                line(IncomeType.TRADITIONAL_IRA, "30000"), line(IncomeType.ROTH_CONVERSION, "30000")));
        // TI=54,100; 1,056.74 + (54,100-42,861)*6% -158.
        tax(result, "1573.08");
    }

    @Test
    void californiaIndependentPublishedScheduleCell() {
        // D (partial): FTB October 2026 indexing Schedule X: 27,157 => regular tax 428.58.
        StateTaxResult result = calculator.calculate(single("CA", "33057", "0", "40"));
        amount(result.estimate().orElseThrow().taxBeforeCredits(), "428.58");
        tax(result, "270.58");
    }

    @Test
    void californiaSurchargeOnlyOnExcessAndJointCredit() {
        StateTaxResult single = calculator.calculate(single("CA", "1105900", "0", "70"));
        amount(single.estimate().orElseThrow().additionalTaxes(), "1000");
        StateTaxResult joint = calculator.calculate(joint("CA", "1111800", "0",
                person(Owner.PRIMARY, "70"), person(Owner.SPOUSE, "68")));
        amount(joint.estimate().orElseThrow().additionalTaxes(), "1000");
        amount(joint.estimate().orElseThrow().credits(), "316");
        assertTrue(joint.warnings().stream().anyMatch(warning -> warning.contains("phaseout")));
    }

    @Test
    void michiganCappedEligibleIncomeAndPersonalExemption() {
        StateTaxResult result = calculator.calculate(single("MI", "100000", "0", "65",
                qualified(IncomeType.TRADITIONAL_IRA, "80000", "QUALIFYING_RETIREMENT_BENEFIT_ONLY"),
                line(IncomeType.INTEREST, "20000")));
        tax(result, "1125.83");
        amount(result.estimate().orElseThrow().retirementExclusions().getFirst().amount(), "67610");
    }

    @Test
    void michiganJointCapAndIncomeLimitedExclusion() {
        StateTaxResult joint = calculator.calculate(joint("MI", "200000", "0",
                person(Owner.PRIMARY, "65", qualified(IncomeType.TRADITIONAL_IRA, "100000", "QUALIFYING_RETIREMENT_BENEFIT_ONLY")),
                person(Owner.SPOUSE, "65", qualified(IncomeType.PENSION, "100000", "QUALIFYING_RETIREMENT_BENEFIT_ONLY"))));
        tax(joint, "2251.65");
        StateTaxResult small = calculator.calculate(single("MI", "10000", "0", "65",
                qualified(IncomeType.TRADITIONAL_IRA, "10000", "QUALIFYING_RETIREMENT_BENEFIT_ONLY")));
        amount(small.estimate().orElseThrow().retirementExclusions().getFirst().amount(), "10000");
        tax(small, "0.00");
    }

    @Test
    void michiganUnassessedRetirementAndConversionEligibilityAreUnsupported() {
        unsupported(calculator.calculate(single("MI", "10000", "0", "65", line(IncomeType.TRADITIONAL_IRA, "10000"))));
        unsupported(calculator.calculate(single("MI", "10000", "0", "65", line(IncomeType.ROTH_CONVERSION, "10000"))));
    }

    @Test
    void illinoisQualifiedRetirementAndPerPersonSeniorDeduction() {
        tax(calculator.calculate(single("IL", "100000", "0", "65",
                qualified(IncomeType.TRADITIONAL_IRA, "80000", "QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME"),
                line(IncomeType.INTEREST, "20000"))), "795.71");
        tax(calculator.calculate(joint("IL", "100000", "0",
                person(Owner.PRIMARY, "65", qualified(IncomeType.PENSION, "30000", "QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME")),
                person(Owner.SPOUSE, "65", qualified(IncomeType.PENSION, "30000", "QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME")))), "1591.43");
    }

    @Test
    void illinoisCeilingDisablesPersonalAndSeniorDeductions() {
        StateTaxResult result = calculator.calculate(single("IL", "250001", "0", "65"));
        amount(result.estimate().orElseThrow().personalExemption(), "0");
        assertTrue(result.estimate().orElseThrow().seniorAdjustments().isEmpty());
        tax(result, "12375.05");
    }

    @Test
    void pennsylvaniaStateBaseNotFederalAgiAndQualifiedRetirement() {
        tax(calculator.calculate(single("PA", "100000", "90000", "65",
                new Income(IncomeType.TRADITIONAL_IRA, bd("90000"), bd("70000"), PensionSource.PRIVATE, Map.of()),
                line(IncomeType.INTEREST, "10000"))), "307.00");
        tax(calculator.calculate(single("PA", "50000", "0", "65",
                qualified(IncomeType.PENSION, "40000", "ELIGIBLE_PLAN_AND_RETIRED"),
                line(IncomeType.INTEREST, "10000"))), "307.00");
    }

    @Test
    void pennsylvaniaEarlyDistributionsAndConversionsUnsupported() {
        unsupported(calculator.calculate(single("PA", "10000", "0", "59.4", line(IncomeType.TRADITIONAL_IRA, "10000"))));
        tax(calculator.calculate(single("PA", "10000", "0", "59.5", line(IncomeType.TRADITIONAL_IRA, "10000"))), "0.00");
        unsupported(calculator.calculate(single("PA", "10000", "0", "65", line(IncomeType.ROTH_CONVERSION, "10000"))));
    }

    @Test
    void newYorkPrivateExclusionIsPerPersonNotTransferable() {
        tax(calculator.calculate(joint("NY", "55000", "0",
                person(Owner.PRIMARY, "65", qualified(IncomeType.TRADITIONAL_IRA, "50000", "QUALIFYING_PRIVATE_RETIREMENT")),
                person(Owner.SPOUSE, "65", qualified(IncomeType.PENSION, "5000", "QUALIFYING_PRIVATE_RETIREMENT")))), "544.05");
    }

    @Test
    void newYorkGovernmentPriorityPreventsDoubleExclusion() {
        Income government = new Income(IncomeType.PENSION, bd("30000"), bd("30000"),
                PensionSource.FEDERAL_GOVERNMENT, Map.of());
        StateTaxResult result = calculator.calculate(single("NY", "80000", "0", "65", government,
                qualified(IncomeType.TRADITIONAL_IRA, "50000", "QUALIFYING_PRIVATE_RETIREMENT")));
        tax(result, "1023.00");
        amount(result.estimate().orElseThrow().retirementExclusions().stream().map(StateTaxResult.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), "50000");
        unsupported(calculator.calculate(single("NY", "10000", "0", "65",
                new Income(IncomeType.PENSION, bd("10000"), bd("10000"), PensionSource.UNKNOWN, Map.of()))));
    }

    @Test
    void coloradoFederalTaxableBaseAndAgeCaps() {
        tax(calculator.calculate(single("CO", "80000", "60000", "65",
                qualified(IncomeType.TRADITIONAL_IRA, "40000", "COLORADO_QUALIFYING_PENSION_ANNUITY"))), "1584.00");
        tax(calculator.calculate(single("CO", "80000", "60000", "55",
                qualified(IncomeType.TRADITIONAL_IRA, "40000", "COLORADO_QUALIFYING_PENSION_ANNUITY"))), "1760.00");
        tax(calculator.calculate(single("CO", "80000", "60000", "64.5",
                qualified(IncomeType.TRADITIONAL_IRA, "40000", "COLORADO_QUALIFYING_PENSION_ANNUITY"))), "1760.00");
    }

    @Test
    void coloradoFullSocialSecurityConsumesSharedCap() {
        tax(calculator.calculate(single("CO", "50000", "30000", "65", line(IncomeType.SOCIAL_SECURITY, "17000"))), "572.00");
        tax(calculator.calculate(single("CO", "70000", "50000", "65",
                line(IncomeType.SOCIAL_SECURITY, "17000"),
                qualified(IncomeType.PENSION, "30000", "COLORADO_QUALIFYING_PENSION_ANNUITY"))), "1144.00");
    }

    @Test
    void newJerseySteppedSingleExclusion() {
        // Prior estimate omitted a potentially material Other Retirement Income Exclusion.
        unsupported(calculator.calculate(single("NJ", "110000", "0", "65",
                line(IncomeType.PENSION, "80000"), line(IncomeType.INTEREST, "30000"))));
        StateTaxResult low = calculator.calculate(single("NJ", "90000", "0", "62",
                line(IncomeType.PENSION, "90000")));
        amount(low.estimate().orElseThrow().retirementExclusions().getFirst().amount(), "75000");
        tax(low, "196.00");
    }

    @Test
    void newJerseyOnlyQualifiedSpouseIncomeGetsHouseholdExclusion() {
        StateTaxResult result = calculator.calculate(joint("NJ", "90000", "0",
                person(Owner.PRIMARY, "61", line(IncomeType.PENSION, "60000")),
                person(Owner.SPOUSE, "62", line(IncomeType.PENSION, "30000"))));
        amount(result.estimate().orElseThrow().retirementExclusions().getFirst().amount(), "30000");
        tax(result, "1001.00");
    }

    @ParameterizedTest
    @CsvSource({"100000,332.50", "125000,2722.91", "150000,5509.79", "150001,7301.41"})
    void newJerseyVerifiedInclusiveBandEndpointsAndNoExclusionAboveCeiling(String income, String expected) {
        tax(calculator.calculate(single("NJ", income, "0", "65", line(IncomeType.PENSION, income))), expected);
    }

    @Test
    void newJerseyExemptSocialSecurityNotCountedInTotalIncomeOrExcludedTwice() {
        StateTaxResult result = calculator.calculate(single("NJ", "110000", "0", "65",
                line(IncomeType.PENSION, "80000"), line(IncomeType.SOCIAL_SECURITY, "30000")));
        amount(result.estimate().orElseThrow().startingTaxBase(), "80000");
        tax(result, "42.00");
    }

    @Test
    void newJerseyFilingThresholdUsesTotalBeforeDeductions() {
        tax(calculator.calculate(single("NJ", "10000", "0", "40", line(IncomeType.INTEREST, "10000"))), "0.00");
        tax(calculator.calculate(single("NJ", "10001", "0", "40", line(IncomeType.INTEREST, "10001"))), "126.01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CA", "MI", "FL", "IL", "PA", "NY", "CO", "NJ"})
    void qualifiedRothIsNeverAddedToFederalBaseOrStateTaxableIncome(String code) {
        tax(calculator.calculate(single(code, "0", "0", "65",
                new Income(IncomeType.QUALIFIED_ROTH_DISTRIBUTION, BigDecimal.ZERO, bd("10000"), PensionSource.PRIVATE, Map.of()))), "0.00");
    }

    @Test
    void unsupportedJurisdictionYearAndStatusHaveNoEstimate() {
        unsupported(calculator.calculate(single("TX", "0", "0", "65")));
        unsupported(calculator.calculate(new StateTaxRequest("CA", 2027, FilingStatus.SINGLE,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(person(Owner.PRIMARY, "65")))));
        unsupported(calculator.calculate(new StateTaxRequest("CA", 2026, FilingStatus.HEAD_OF_HOUSEHOLD,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(person(Owner.PRIMARY, "65")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PA", "NJ"})
    void unclassifiedStateDefinedIncomeCannotBecomeSuccessfulZero(String code) {
        unsupported(calculator.calculate(single(code, "50000", "0", "65")));
        unsupported(calculator.calculate(single(code, "50000", "0", "65", line(IncomeType.INTEREST, "10000"))));
    }

    @Test
    void requestRejectsMissingAmountsInconsistentTotalsAndPlaceholderSpouse() {
        assertThrows(NullPointerException.class, () -> single("CA", null, "0", "65"));
        assertThrows(IllegalArgumentException.class, () -> single("CA", "100", "101", "65"));
        assertThrows(IllegalArgumentException.class, () -> single("CA", "100", "0", "65", line(IncomeType.ROTH_CONVERSION, "200")));
        assertThrows(IllegalArgumentException.class, () -> new StateTaxRequest("CA", 2026, FilingStatus.MARRIED_FILING_JOINTLY,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(person(Owner.PRIMARY, "65"))));
    }

    @Test
    void cachedRulesAndConcurrentCalculationsAreImmutableAndDeterministic() throws Exception {
        Map<String, StateTaxRules> first = new StateTaxDatasetRepository().load();
        assertSame(first, new StateTaxDatasetRepository().load());
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        assertThrows(UnsupportedOperationException.class, () -> first.get("CA").brackets().get(FilingStatus.SINGLE).clear());
        StateTaxRequest request = single("CA", "33057", "0", "65");
        StateTaxResult expected = calculator.calculate(request);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Callable<StateTaxResult>> calls = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                calls.add(() -> calculator.calculate(request));
            }
            for (var result : executor.invokeAll(calls)) {
                assertEquals(expected, result.get());
            }
        }
    }

    private static StateTaxRequest single(String code, String agi, String taxable, String age, Income... income) {
        return new StateTaxRequest(code, 2026, FilingStatus.SINGLE, bd(agi), bd(taxable),
                List.of(person(Owner.PRIMARY, age, income)));
    }

    private static StateTaxRequest joint(String code, String agi, String taxable, PersonIncome primary, PersonIncome spouse) {
        return new StateTaxRequest(code, 2026, FilingStatus.MARRIED_FILING_JOINTLY, bd(agi), bd(taxable), List.of(primary, spouse));
    }

    private static PersonIncome person(Owner owner, String age, Income... income) {
        BigDecimal taxableSs = Arrays.stream(income).filter(line -> line.type() == IncomeType.SOCIAL_SECURITY)
                .map(Income::federalIncluded).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PersonIncome(owner, bd(age), bd("40000"), taxableSs, Set.of(), List.of(income));
    }

    private static Income line(IncomeType type, String amount) {
        return new Income(type, bd(amount), bd(amount), PensionSource.PRIVATE, Map.of());
    }

    private static Income qualified(IncomeType type, String amount, String qualification) {
        return new Income(type, bd(amount), bd(amount), PensionSource.PRIVATE, Map.of(qualification, Qualification.ELIGIBLE));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static void amount(BigDecimal actual, String expected) {
        assertEquals(0, actual.compareTo(bd(expected)), "Expected " + expected + "; actual " + actual);
    }

    private static void tax(StateTaxResult result, String expected) {
        assertNotEquals(StateTaxResult.Status.UNSUPPORTED, result.status(), result.warnings().toString());
        amount(result.estimate().orElseThrow().finalEstimatedTax(), expected);
    }

    private static void unsupported(StateTaxResult result) {
        assertEquals(StateTaxResult.Status.UNSUPPORTED, result.status());
        assertTrue(result.estimate().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }
}
