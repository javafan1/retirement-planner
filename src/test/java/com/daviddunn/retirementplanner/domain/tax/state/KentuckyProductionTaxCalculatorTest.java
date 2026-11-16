package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

public class KentuckyProductionTaxCalculatorTest {
    // Independent Schedule M per-person worksheet / actual joint Form 740.
    @ParameterizedTest
    @CsvSource({"2026,50000,0,0,543.55", "2026,50000,20000,0,1243.55",
            "2026,0,20000,0,0.00", "2026,34470,0,0,0.00", "2026,34470.15,0,0,0.01",
            "2026,50000,0,5000,718.55", "2027,50000,0,0,539.70", "2033,50000,0,0,539.70"})
    void singleIndependentWorksheet(int year, String ira, String conversion, String investment, String expected) throws Exception {
        var sources = new ArrayList<IncomeSourceAttribution>();
        sources.add(retirement(AccountOwnership.PRIMARY, ira, year));
        sources.add(conversion(AccountOwnership.PRIMARY, conversion, year));
        var income = new TaxIncome(b("0"), b("0"), b(ira), b(conversion), b(investment), sources);
        assertEquals(b(expected), calculate(request(year, false, 60, 62, income)).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"50000,50000,1204.70", "100000,0,2293.55", "0,100000,2293.55"})
    void jointHasOneDeductionAndIndividualCaps(String primary, String spouse, String expected) throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b(primary).add(b(spouse)), b("0"), b("0"),
                List.of(retirement(AccountOwnership.PRIMARY, primary, 2026), retirement(AccountOwnership.SPOUSE, spouse, 2026)));
        assertEquals(b(expected), calculate(request(2026, true, 60, 62, income)).incomeTax());
    }

    @Test
    void pensionAndConversionUseActualRecipientsAndSamePool() throws Exception {
        var pension = owned(IncomeSourceAttribution.Kind.PENSION, AccountOwnership.PRIMARY, "30000", 2026);
        var income = new TaxIncome(b("30000"), b("0"), b("0"), b("40000"), b("0"),
                List.of(pension, conversion(AccountOwnership.SPOUSE, "40000", 2026)));
        assertEquals(b("193.55"), calculate(request(2026, true, 60, 62, income)).incomeTax());
        assertEquals(b("1243.55"), calculate(request(2026, true, 60, 62, income.withIncomeSources(
                List.of(pension, conversion(AccountOwnership.PRIMARY, "40000", 2026))))).incomeTax());
    }

    @Test
    void socialSecurityAndBrokeragePrincipalAreNotAddedOrSubtractedTwice() throws Exception {
        var broker = new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY, b("100000"));
        var proceeds = IncomeSourceAttribution.withdrawal(broker, b("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        var income = new TaxIncome(b("0"), b("30000"), b("50000"), b("0"), b("0"),
                List.of(proceeds, retirement(AccountOwnership.PRIMARY, "50000", 2026)));
        assertEquals(b("543.55"), calculate(request(2026, false, 60, 62, income)).incomeTax());
        assertEquals(b("0.00"), calculate(request(2026, false, 60, 62,
                new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(proceeds)))).incomeTax());
        var gain = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.CAPITAL_GAIN,
                IncomeSourceAttribution.Purpose.INCOME, "Known gain", b("5000"), Optional.of(b("5000"))).inYear(2026);
        var known = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("5000"),
                List.of(proceeds, gain, retirement(AccountOwnership.PRIMARY, "50000", 2026)));
        assertEquals(b("718.55"), calculate(request(2026, false, 60, 62, known)).incomeTax());
        unsupported(known.withIncomeSources(List.of(gain, gain, retirement(AccountOwnership.PRIMARY, "50000", 2026))));
        unsupported(new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(gain)));
    }

    @Test
    void seniorCreditFloorsAtZeroAndDoesNotAgeDeceasedFilerThroughBirthday() throws Exception {
        var income = new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"),
                List.of(retirement(AccountOwnership.PRIMARY, "50000", 2026)));
        assertEquals(b("503.55"), calculate(request(2026, false, 65, 62, income)).incomeTax());
        var joint = request(2026, true, 65, 65, income);
        var beforeBirthday = new TaxHouseholdContext(LocalDate.of(2026, 1, 1),
                Map.of(AccountOwnership.PRIMARY, LocalDate.of(1961, 6, 1), AccountOwnership.SPOUSE, LocalDate.of(1961, 6, 1)),
                Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE), Set.of(AccountOwnership.PRIMARY), Optional.empty());
        var result = calculate(new StateTaxCalculationRequest(2026, joint.filingStatus(), beforeBirthday,
                income, joint.federal(), joint.projectedRules(), joint.assumptions()));
        assertEquals(b("503.55"), result.incomeTax());
    }

    @Test
    void unownedPartialWrongYearAndEmployerConversionsFailWithoutGuessing() throws Exception {
        unsupported(new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0")));
        var source = owned(IncomeSourceAttribution.Kind.PENSION, AccountOwnership.PRIMARY, "5000", 2027);
        unsupported(new TaxIncome(b("5000"), b("0"), b("0"), b("0"), b("0"), List.of(source)));
        var employer = new Traditional401K("Employer", AccountOwnership.PRIMARY, b("50000"));
        var conversion = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                IncomeSourceAttribution.Purpose.CONVERSION, Optional.of(AccountOwnership.PRIMARY),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(employer)),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Roth", AccountOwnership.PRIMARY, b("0")))),
                "Employer conversion", b("20000"), Optional.of(b("20000")), OptionalInt.of(2026), Optional.empty());
        unsupported(new TaxIncome(b("0"), b("0"), b("0"), b("20000"), b("0"), List.of(conversion)));
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"TRADITIONAL_IRA", "ROLLOVER_IRA", "TRADITIONAL_401K", "TRADITIONAL_403B"})
    void verifiedOrdinaryRetirementSourcesShareOnePool(AccountType type) throws Exception {
        var account = AccountFactory.create(type, "Ordinary account", AccountOwnership.PRIMARY, b("100000"));
        for (var kind : List.of(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Kind.OPENING_RMD)) {
            var source = IncomeSourceAttribution.withdrawal(account, b("50000"), kind, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
            assertEquals(b("543.55"), calculate(request(2026, false, 60, 62,
                    new TaxIncome(b("0"), b("0"), b("50000"), b("0"), b("0"), List.of(source)))).incomeTax());
        }
    }

    @Test
    void partialBasisTaxableRothAndUnclassifiedEvidenceAreRefused() throws Exception {
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, b("100000"));
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, b("100000"));
        for (var account : List.of(ira, roth)) {
            var partial = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                    IncomeSourceAttribution.Purpose.CASH_FLOW, Optional.of(AccountOwnership.PRIMARY),
                    Optional.of(IncomeSourceAttribution.AccountReference.capture(account)), Optional.empty(), "Explicit partial",
                    b("5000"), Optional.of(b("1000")), OptionalInt.of(2026), Optional.empty());
            unsupported(new TaxIncome(b("0"), b("0"), b("1000"), b("0"), b("0"), List.of(partial)));
        }
        var unknown = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.OTHER,
                IncomeSourceAttribution.Purpose.INCOME, "Represented unsupported classification", b("5000"), Optional.of(b("5000")));
        unsupported(new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("5000"), List.of(unknown)));
        var qualified = IncomeSourceAttribution.withdrawal(roth, b("50000"), IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        assertEquals(b("0.00"), calculate(request(2026, false, 65, 62,
                new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("0"), List.of(qualified)))).incomeTax());
    }

    @Test
    void representedTraditionalDistributionCannotMasqueradeAsTaxableInvestmentProceeds() throws Exception {
        var incorrect = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, b("100000")),
                b("5000"), IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(2026);
        unsupported(new TaxIncome(b("0"), b("0"), b("0"), b("0"), b("5000"), List.of(incorrect)));
    }

    private void unsupported(TaxIncome income) throws Exception {
        var result = calculate(request(2026, false, 60, 62, income));
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertTrue(result.estimatedIncomeTax().isEmpty());
    }

    public static StateTaxCalculationRequest request(int year, boolean joint, int ageA, int ageB, TaxIncome income) throws Exception {
        var household = new Household(new Person("Primary", "Example", LocalDate.of(year - ageA, 1, 1)),
                joint ? new Person("Spouse", "Example", LocalDate.of(year - ageB, 1, 1)) : null);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var status = joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        return new StateTaxCalculationRequest(year, status, TaxHouseholdContext.capture(household, LocalDate.of(year, 1, 1), null),
                income, new FederalTaxCalculator().calculate(income, status, rules), rules,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.KY));
    }

    public static IncomeSourceAttribution retirement(AccountOwnership owner, String amount, int year) {
        return IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", owner, b("1000000")), b(amount),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(year);
    }

    public static IncomeSourceAttribution conversion(AccountOwnership owner, String amount, int year) {
        return new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ROTH_CONVERSION, IncomeSourceAttribution.Purpose.CONVERSION,
                Optional.of(owner), Optional.of(IncomeSourceAttribution.AccountReference.capture(new TraditionalIRA("IRA", owner, b("1000000")))),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Roth", owner, b("0")))),
                "Complete IRA conversion", b(amount), Optional.of(b(amount)), OptionalInt.of(year), Optional.empty());
    }

    private static IncomeSourceAttribution owned(IncomeSourceAttribution.Kind kind, AccountOwnership owner, String amount, int year) {
        return new IncomeSourceAttribution(kind, IncomeSourceAttribution.Purpose.INCOME, Optional.of(owner), Optional.empty(),
                Optional.empty(), "Ordinary private pension", b(amount), Optional.of(b(amount)), OptionalInt.of(year), Optional.empty());
    }
    private static ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        return new KentuckyProductionTaxCalculator().calculate(request);
    }
    private static BigDecimal b(String value) { return new BigDecimal(value); }
}
