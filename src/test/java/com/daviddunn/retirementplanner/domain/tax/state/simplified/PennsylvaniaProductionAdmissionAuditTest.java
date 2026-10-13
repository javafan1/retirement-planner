package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.Income;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.Owner;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.PersonIncome;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.Qualification;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.IncomeType;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.PensionSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit worksheets, NOT a production admission service. Admission labels are
 * reviewed conclusions in PHASE-6D3A-PENNSYLVANIA-PRODUCTION-ADMISSION-AUDIT.md.
 * Prototype results assume the explicitly supplied state amounts/qualifications.
 * Sources: PA 2026 REV-413(I), PIT Bulletin 2008-1 sections 8-10, current PIT Guide,
 * and Revenue FAQ 274 (updated August 14, 2026). Literal expectations are independent
 * worksheets: qualifying income excluded; remaining ordinary investment income *.0307.
 * Tax Forgiveness/local taxes are deliberately outside these gross state-tax fixtures.
 */
class PennsylvaniaProductionAdmissionAuditTest {

    private enum Admission { SUPPORTED, SUPPORTED_WITH_LIMITATIONS, UNSUPPORTED }

    private record Fixture(
            String id,
            Admission admission,
            StateTaxRequest request,
            String expectedTaxableIncome,
            String expectedTax,
            String reason) {
        @Override
        public String toString() {
            return id + " / " + admission;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void independentlyWorkedAuditScenario(Fixture fixture) throws Exception {
        assertFalse(fixture.reason().isBlank());
        StateTaxResult result = new SimplifiedStateTaxCalculator().calculate(fixture.request());
        if (fixture.expectedTax() == null) {
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.status());
            assertTrue(result.estimate().isEmpty(), "Unknown facts must not become a zero estimate");
            assertFalse(result.warnings().isEmpty());
        } else {
            assertEquals(StateTaxCalculationStatus.CALCULATED, result.status());
            var estimate = result.estimate().orElseThrow();
            amount(fixture.expectedTaxableIncome(), estimate.taxableIncome());
            amount(fixture.expectedTax(), estimate.finalEstimatedTax());
            amount("0", estimate.standardDeduction());
            amount("0", estimate.personalExemption());
        }
    }

    private static Stream<Fixture> fixtures() {
        return Stream.of(
                calculated("SS-RETIREMENT", Admission.SUPPORTED, "65", "0", "0",
                        "Gross40000/federally included25000 SS is outside the PA base.",
                        income(IncomeType.SOCIAL_SECURITY, "25000", "40000")),
                calculated("SS-SURVIVOR", Admission.SUPPORTED, "65", "0", "0",
                        "Selected survivor SS is still Social Security, independently exempt.",
                        income(IncomeType.SOCIAL_SECURITY, "25000", "40000")),
                calculated("INTEREST", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Explicit ordinary bank interest, no bond-source exception or Tax Forgiveness assessed.",
                        income(IncomeType.INTEREST, "20000", "20000")),
                calculated("DIVIDENDS", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Explicit cash dividends; currently no production dividend generator.",
                        income(IncomeType.ORDINARY_DIVIDEND, "20000", "20000")),
                calculated("QUALIFIED-DIVIDENDS", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Federal qualification does not supply a lower PA rate.",
                        income(IncomeType.QUALIFIED_DIVIDEND, "20000", "20000")),
                calculated("SHORT-GAINS", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Independently established positive PA securities gain; no basis reconstruction.",
                        income(IncomeType.SHORT_TERM_CAPITAL_GAIN, "20000", "20000")),
                calculated("LONG-GAINS", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Same PA rate as short gains; existing production has no realized-gain ledger.",
                        income(IncomeType.LONG_TERM_CAPITAL_GAIN, "20000", "20000")),
                calculated("IRA-NORMAL", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "0", "0",
                        "Ordinary individual IRA, post-eligibility receipt, not an employer-sponsored IRA.",
                        income(IncomeType.TRADITIONAL_IRA, "80000", "80000")),
                calculated("IRA-RMD", Admission.SUPPORTED_WITH_LIMITATIONS, "75", "0", "0",
                        "Normal IRA RMD retains the underlying eligible IRA treatment.",
                        income(IncomeType.TRADITIONAL_IRA, "40000", "40000")),
                calculated("IRA-WITH-BASIS-AFTER-AGE", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "0", "0",
                        "Known normal eligible receipt is excluded regardless of this federal/state amount difference.",
                        income(IncomeType.TRADITIONAL_IRA, "60000", "80000")),
                calculated("PENSION-EXPLICITLY-QUALIFIED", Admission.UNSUPPORTED, "65", "0", "0",
                        "Prototype can consume eligibility assertion; current Pension cannot establish it.",
                        qualified(IncomeType.PENSION, "40000", "ELIGIBLE_PLAN_AND_RETIRED")),
                calculated("EMPLOYER-EXPLICITLY-QUALIFIED", Admission.UNSUPPORTED, "65", "0", "0",
                        "Legal worksheet assumes eligible plan and retired; current account type alone does not prove these.",
                        qualified(IncomeType.EMPLOYER_RETIREMENT_PLAN, "40000", "ELIGIBLE_PLAN_AND_RETIRED")),
                calculated("CONVERSION-25000", Admission.SUPPORTED_WITH_LIMITATIONS, "40", "0", "0",
                        "Entire executed IRA distribution transferred to same-owner Roth IRA; no cash retained.",
                        qualified(IncomeType.ROTH_CONVERSION, "25000", "COMPLETE_IRA_TO_ROTH_CONVERSION")),
                calculated("CONVERSION-100000", Admission.SUPPORTED_WITH_LIMITATIONS, "40", "0", "0",
                        "Complete IRA-to-Roth IRA transfer, independently qualifying at this amount.",
                        qualified(IncomeType.ROTH_CONVERSION, "100000", "COMPLETE_IRA_TO_ROTH_CONVERSION")),
                calculated("CONVERSION-250000", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "0", "0",
                        "No PA cap on the admitted IRA-to-Roth transfer exclusion.",
                        qualified(IncomeType.ROTH_CONVERSION, "250000", "COMPLETE_IRA_TO_ROTH_CONVERSION")),
                calculated("MIXED-INTEREST", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Eligible IRA cannot shelter ordinary interest.",
                        income(IncomeType.TRADITIONAL_IRA, "80000", "80000"), income(IncomeType.INTEREST, "20000", "20000")),
                calculated("MIXED-DIVIDENDS", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Investment amount is explicitly established, not inferred from portfolio growth.",
                        income(IncomeType.TRADITIONAL_IRA, "80000", "80000"), income(IncomeType.ORDINARY_DIVIDEND, "20000", "20000")),
                calculated("MIXED-GAINS", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "20000", "614.00",
                        "Only the eligible retirement amount is excluded.",
                        income(IncomeType.TRADITIONAL_IRA, "80000", "80000"), income(IncomeType.LONG_TERM_CAPITAL_GAIN, "20000", "20000")),
                rejected("EARLY-IRA", "59.4", "State basis and exception unavailable.",
                        income(IncomeType.TRADITIONAL_IRA, "10000", "10000")),
                calculated("IRA-EXACT-AGE", Admission.SUPPORTED_WITH_LIMITATIONS, "59.5", "0", "0",
                        "Fixture receipts occur after the eligibility birthday, not an end-of-year inference.",
                        income(IncomeType.TRADITIONAL_IRA, "10000", "10000")),
                rejected("EARLY-EMPLOYER", "54", "Retirement/service and previously taxed contribution facts absent.",
                        income(IncomeType.EMPLOYER_RETIREMENT_PLAN, "10000", "10000")),
                rejected("CONVERSION-PARTIAL-ROLLOVER-UNKNOWN", "40", "Part retained: cannot apply complete-transfer assertion or infer basis.",
                        income(IncomeType.ROTH_CONVERSION, "60000", "60000")),
                rejected("CONVERSION-WITHHOLDING", "40", "Withheld dollars require allocation and basis; current engine does not generate withholding.",
                        income(IncomeType.ROTH_CONVERSION, "60000", "60000")),
                rejected("CONVERSION-RETAINED", "40", "Retained distribution amount and PA basis are unavailable.",
                        income(IncomeType.ROTH_CONVERSION, "60000", "60000")),
                rejected("EARLY-IRA-PA-BASIS-UNKNOWN", "40", "Federal included6000 does not establish PA taxable amount.",
                        income(IncomeType.TRADITIONAL_IRA, "6000", "10000")),
                rejected("PENSION-UNKNOWN", "65", "Age and monthly payment do not establish eligible plan/retirement.",
                        income(IncomeType.PENSION, "40000", "40000")),
                rejected("EMPLOYER-RMD-UNKNOWN", "75", "RMD alone cannot prove plan terms or retirement from service.",
                        income(IncomeType.EMPLOYER_RETIREMENT_PLAN, "40000", "40000")),
                calculated("INHERITED-IRA-DEATH-ASSERTED", Admission.SUPPORTED_WITH_LIMITATIONS,
                        request("40", Set.of("DEATH"), income(IncomeType.TRADITIONAL_IRA, "40000", "40000")),
                        "0", "0", "Known beneficiary distribution after original-owner death; not a spouse own-account election."),
                calculated("ROTH-EXPLICITLY-QUALIFIED", Admission.SUPPORTED_WITH_LIMITATIONS, "65", "0", "0",
                        "Caller has established qualification; modeled federal zero alone is not that assessment.",
                        income(IncomeType.QUALIFIED_ROTH_DISTRIBUTION, "0", "40000")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"25000", "100000", "250000", "600000"})
    void executedConversionTransfersItsEntireDistributionEvenWhenTargetIsUnfulfilled(String target) {
        var source = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, bd("500000"));
        var destination = new RothIRA("Roth", AccountOwnership.PRIMARY, bd("0"));
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(source);
        portfolio.addAccount(destination);
        var result = new ProjectedPortfolioRothConverter().convertHousehold(ProjectedPortfolio.from(portfolio), bd(target));
        BigDecimal executed = bd(target).min(bd("500000"));
        amount(executed.toPlainString(), result.getTotalConversion());
        amount(executed.toPlainString(), result.getPortfolio().getBalance(destination));
        amount(bd("500000").subtract(executed).toPlainString(), result.getPortfolio().getBalance(source));
        var attribution = result.getIncomeSources(2026).getFirst();
        assertEquals(AccountType.TRADITIONAL_IRA, attribution.account().orElseThrow().type());
        assertEquals(AccountType.ROTH_IRA, attribution.destination().orElseThrow().type());
        assertEquals(attribution.owner().orElseThrow(), attribution.destination().orElseThrow().owner());
        amount(executed.toPlainString(), attribution.grossAmount());
        amount(executed.toPlainString(), attribution.modeledTaxableAmount().orElseThrow());
        // Persisted balances were not changed. Unmet requested dollars were never distributed.
        amount("500000", source.getCurrentBalance());
        amount("0", destination.getCurrentBalance());
    }

    @Test
    void federalZeroDoesNotSupplyNonqualifiedRothBasisOrAQualifiedDistributionAssertion() {
        var source = new RothIRA("Roth", AccountOwnership.PRIMARY, bd("40000"));
        var attribution = IncomeSourceAttribution.withdrawal(source, bd("10000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        amount("0", attribution.modeledTaxableAmount().orElseThrow());
        assertTrue(attribution.pension().isEmpty());
        // No NONQUALIFIED_ROTH type exists in the research request. Do not mislabel
        // this unassessed early distribution as its QUALIFIED_ROTH_DISTRIBUTION type.
        assertFalse(Arrays.stream(IncomeType.values()).anyMatch(type -> type.name().equals("NONQUALIFIED_ROTH_DISTRIBUTION")));
    }

    @Test
    void jointReturnUsesEachOwnersEligibilityRatherThanTheOlderSpousesAge() throws Exception {
        var primary = new PersonIncome(Owner.PRIMARY, bd("65"), bd("0"), bd("0"), Set.of(),
                List.of(income(IncomeType.TRADITIONAL_IRA, "40000", "40000")));
        var spouse = new PersonIncome(Owner.SPOUSE, bd("59.4"), bd("0"), bd("0"), Set.of(),
                List.of(income(IncomeType.TRADITIONAL_IRA, "10000", "10000")));
        var request = new StateTaxRequest("PA", 2026, FilingStatus.MARRIED_FILING_JOINTLY,
                bd("50000"), bd("0"), List.of(primary, spouse));
        var result = new SimplifiedStateTaxCalculator().calculate(request);
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.status());
        assertTrue(result.estimate().isEmpty());
    }

    @Test
    void qualifiedCoupleRetirementDoesNotExcludeTheirInvestmentIncome() throws Exception {
        var primary = new PersonIncome(Owner.PRIMARY, bd("65"), bd("0"), bd("0"), Set.of(),
                List.of(income(IncomeType.TRADITIONAL_IRA, "40000", "40000"), income(IncomeType.INTEREST, "10000", "10000")));
        var spouse = new PersonIncome(Owner.SPOUSE, bd("65"), bd("0"), bd("0"), Set.of(),
                List.of(income(IncomeType.TRADITIONAL_IRA, "30000", "30000"), income(IncomeType.INTEREST, "10000", "10000")));
        var request = new StateTaxRequest("PA", 2026, FilingStatus.MARRIED_FILING_JOINTLY,
                bd("90000"), bd("0"), List.of(primary, spouse));
        var estimate = new SimplifiedStateTaxCalculator().calculate(request).estimate().orElseThrow();
        amount("20000", estimate.taxableIncome());
        amount("614.00", estimate.finalEstimatedTax());
    }

    @Test
    void independentlyEstablishedStateGainDoesNotBecomeFederalTaxableIncome() throws Exception {
        var request = request("65", Set.of(), income(IncomeType.LONG_TERM_CAPITAL_GAIN, "30000", "20000"));
        var estimate = new SimplifiedStateTaxCalculator().calculate(request).estimate().orElseThrow();
        amount("20000", estimate.startingTaxBase());
        amount("20000", estimate.taxableIncome());
        amount("614.00", estimate.finalEstimatedTax());
    }

    @Test
    void pennsylvaniaRemainsRejectedByProductionJurisdictionAdmission() {
        assertEquals(List.of("MI", "FL", "IL"), Arrays.stream(StateTaxJurisdiction.values()).map(Enum::name).toList());
        assertThrows(IllegalArgumentException.class, () -> StateTaxJurisdiction.fromCode("PA"));
    }

    private static Fixture calculated(String id, Admission admission, String age, String taxable, String tax, String reason, Income... lines) {
        return calculated(id, admission, request(age, Set.of(), lines), taxable, tax, reason);
    }

    private static Fixture calculated(String id, Admission admission, StateTaxRequest request, String taxable, String tax, String reason) {
        return new Fixture(id, admission, request, taxable, tax, reason);
    }

    private static Fixture rejected(String id, String age, String reason, Income... lines) {
        return new Fixture(id, Admission.UNSUPPORTED, request(age, Set.of(), lines), null, null, reason);
    }

    private static StateTaxRequest request(String age, Set<String> events, Income... lines) {
        BigDecimal federal = Arrays.stream(lines).map(Income::federalIncluded).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal includedSs = Arrays.stream(lines).filter(line -> line.type() == IncomeType.SOCIAL_SECURITY)
                .map(Income::federalIncluded).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossSs = Arrays.stream(lines).filter(line -> line.type() == IncomeType.SOCIAL_SECURITY)
                .map(Income::stateDefinedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var person = new PersonIncome(Owner.PRIMARY, bd(age), grossSs, includedSs, events, List.of(lines));
        // This helper only supplies disjoint illustrative FEDERAL fixture components;
        // it is not a production AGI builder or a Pennsylvania-base inference.
        return new StateTaxRequest("PA", 2026, FilingStatus.SINGLE, federal, BigDecimal.ZERO, List.of(person));
    }

    private static Income income(IncomeType type, String federal, String state) {
        return new Income(type, bd(federal), bd(state), PensionSource.UNKNOWN, Map.of());
    }

    private static Income qualified(IncomeType type, String amount, String qualification) {
        return new Income(type, bd(amount), bd(amount), PensionSource.UNKNOWN, Map.of(qualification, Qualification.ELIGIBLE));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void amount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual), "Expected " + expected + ", got " + actual);
    }
}
