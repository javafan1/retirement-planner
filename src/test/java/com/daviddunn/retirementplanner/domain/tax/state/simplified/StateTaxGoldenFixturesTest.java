package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent worksheet constants, never generated from the calculator or packaged rules. */
class StateTaxGoldenFixturesTest {

    private final SimplifiedStateTaxCalculator calculator = new SimplifiedStateTaxCalculator();

    StateTaxGoldenFixturesTest() throws Exception { }

    enum Confidence { HIGH, MEDIUM, UNSUPPORTED_AMBIGUOUS }

    record Fixture(String id, StateTaxRequest request, String incomeAdjustment,
                   String retirementExclusion, String taxableIncome, String tax,
                   StateTaxResult.Status status, Confidence confidence, String source, String rationale) {
        @Override public String toString() { return id; }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void independentlyDerivedGoldenFixture(Fixture fixture) {
        assertTrue(fixture.source().startsWith("https://"));
        assertFalse(fixture.rationale().isBlank());
        StateTaxResult result = calculator.calculate(fixture.request());
        assertEquals(fixture.status(), result.status(), result.warnings().toString());
        if (fixture.status() == StateTaxResult.Status.UNSUPPORTED) {
            assertTrue(result.estimate().isEmpty());
            return;
        }
        var estimate = result.estimate().orElseThrow();
        amount(fixture.incomeAdjustment(), estimate.incomeAdjustments().stream()
                .map(StateTaxResult.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        amount(fixture.retirementExclusion(), estimate.retirementExclusions().stream()
                .map(StateTaxResult.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        amount(fixture.taxableIncome(), estimate.taxableIncome());
        amount(fixture.tax(), estimate.finalEstimatedTax());
    }

    static Stream<Fixture> fixtures() {
        List<Fixture> fixtures = new ArrayList<>();
        add(fixtures, "FL-SINGLE", single("FL", "65", "1000000", "800000"), "0", "0", "0", "0", "No state personal income tax.");
        add(fixtures, "FL-MFJ", joint("FL", "2000000", "1600000", person(Owner.PRIMARY,"65"), person(Owner.SPOUSE,"66")), "0", "0", "0", "0", "No state personal income tax at high income.");
        add(fixtures, "CA-SINGLE", single("CA", "60", "60000", "45000", line(IncomeType.TRADITIONAL_IRA,"60000")), "0", "0", "54100", "1573.08", "1056.74 +11239*.06 -158; age below senior-credit eligibility.");
        add(fixtures, "CA-MFJ", joint("CA", "60000", "45000", person(Owner.PRIMARY,"60",line(IncomeType.PENSION,"50000")), person(Owner.SPOUSE,"60",line(IncomeType.INTEREST,"10000"))), "0", "0", "48200", "418.88", "229.12 +(48200-22912)*.02 -316.");
        add(fixtures, "CA-SS-IRA", single("CA", "64", "60000", "45000", line(IncomeType.SOCIAL_SECURITY,"17000"), line(IncomeType.TRADITIONAL_IRA,"43000")), "17000", "0", "37100", "668.30", "Exclude only federal taxable SS, then5900 deduction and158 credit.");
        add(fixtures, "CA-CONVERSION", single("CA", "60", "60000", "45000", line(IncomeType.TRADITIONAL_IRA,"30000"),line(IncomeType.ROTH_CONVERSION,"30000")), "0", "0", "54100", "1573.08", "Conversion is disjoint taxable amount already in AGI.");
        add(fixtures, "CA-INVESTMENTS", single("CA", "60", "33057", "0",line(IncomeType.INTEREST,"10000"),line(IncomeType.ORDINARY_DIVIDEND,"3057"),line(IncomeType.QUALIFIED_DIVIDEND,"10000"),line(IncomeType.LONG_TERM_CAPITAL_GAIN,"10000")), "0", "0", "27157", "270.58", "California investment income has no preferential capital-gain rate; published bracket endpoint.");
        Fixture high = fixture("CA-SURCHARGE", single("CA","60","1105900","0",line(IncomeType.ROTH_CONVERSION,"1105900")),"0","0","1100000","116327.07","Exact progressive slices115485.069, plus1000 surcharge, less158 modeled credit; phaseout omitted.");
        fixtures.add(new Fixture(high.id(), high.request(), high.incomeAdjustment(),high.retirementExclusion(),high.taxableIncome(),high.tax(),high.status(),Confidence.MEDIUM,high.source(),high.rationale()));
        unsupported(fixtures,"CA-EARLY-DISTRIBUTION",single("CA","59.4","60000","0",line(IncomeType.TRADITIONAL_IRA,"60000")),"Additional early-distribution tax/exception cannot be inferred.");
        add(fixtures,"CA-DISTRIBUTION-EXACT-AGE",single("CA","59.5","60000","0",line(IncomeType.TRADITIONAL_IRA,"60000")),"0","0","54100","1573.08","Normal IRA income at exact age59.5.");
        add(fixtures,"MI-INTEREST",single("MI","60","10000","0",line(IncomeType.INTEREST,"10000")),"0","0","4100","174.25","Only personal exemption; interest is not retirement income.");
        add(fixtures,"MI-SINGLE-CAP",single("MI","60","100000","0",qualified(IncomeType.TRADITIONAL_IRA,"100000","QUALIFYING_RETIREMENT_BENEFIT_ONLY")),"0","67610","26490","1125.83","(100000-67610-5900)*.0425.");
        add(fixtures,"MI-MFJ-CAP",joint("MI","200000","0",person(Owner.PRIMARY,"60",qualified(IncomeType.TRADITIONAL_IRA,"100000","QUALIFYING_RETIREMENT_BENEFIT_ONLY")),person(Owner.SPOUSE,"60",qualified(IncomeType.PENSION,"100000","QUALIFYING_RETIREMENT_BENEFIT_ONLY"))),"0","135220","52980","2251.65","Joint cap135220 plus two5900 exemptions.");
        add(fixtures,"MI-CONVERSION-SHARED",single("MI","60","90000","0",qualified(IncomeType.TRADITIONAL_IRA,"50000","QUALIFYING_RETIREMENT_BENEFIT_ONLY"),qualified(IncomeType.ROTH_CONVERSION,"40000","QUALIFYING_RETIREMENT_BENEFIT_ONLY")),"0","67610","16490","700.83","Conversion and IRA share67610, never two caps.");
        add(fixtures,"MI-CONVERSION-BELOW-AGE",single("MI","59.4","10000","0",qualified(IncomeType.ROTH_CONVERSION,"10000","QUALIFYING_RETIREMENT_BENEFIT_ONLY")),"0","0","4100","174.25","Rollover below59.5 receives no modeled retirement subtraction.");
        add(fixtures,"MI-CONVERSION-EXACT-AGE",single("MI","59.5","10000","0",qualified(IncomeType.ROTH_CONVERSION,"10000","QUALIFYING_RETIREMENT_BENEFIT_ONLY")),"0","10000","0","0","Exact qualifying age; limited to included income.");
        unsupported(fixtures,"MI-AGE67-ELECTION",single("MI","67","10000","0",line(IncomeType.INTEREST,"10000")),"Alternative all-income deduction may materially change estimate.");
        unsupported(fixtures,"MI-EMPLOYER-UNKNOWN",single("MI","60","10000","0",line(IncomeType.EMPLOYER_RETIREMENT_PLAN,"10000")),"Voluntary-deferral-only employer amounts cannot be presumed qualifying.");
        add(fixtures,"IL-RETIREMENT",single("IL","65","80000","0",qualified(IncomeType.TRADITIONAL_IRA,"80000","QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME")),"0","80000","0","0","Qualifying federal taxable retirement income excluded, not gross untaxed benefits.");
        add(fixtures,"IL-INVESTMENT",single("IL","65","100000","0",qualified(IncomeType.PENSION,"80000","QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME"),line(IncomeType.INTEREST,"20000")),"0","80000","16075","795.71","20000 less2925 personal and1000 senior, times.0495.");
        add(fixtures,"IL-LARGE-CONVERSION",single("IL","65","310000","0",qualified(IncomeType.ROTH_CONVERSION,"300000","QUALIFYING_FEDERALLY_TAXABLE_RETIREMENT_INCOME"),line(IncomeType.INTEREST,"10000")),"0","300000","10000","495","AGI exceeds250000: exemption denied, qualifying conversion still subtracted.");
        add(fixtures,"IL-CEILING-EXACT",single("IL","65","250000","0",line(IncomeType.INTEREST,"250000")),"0","0","246075","12180.71","Exactly ceiling retains2925+1000 deductions.");
        add(fixtures,"IL-CEILING-ABOVE",single("IL","65","250001","0",line(IncomeType.INTEREST,"250001")),"0","0","250001","12375.05","One dollar above ceiling denies both deductions.");
        add(fixtures,"PA-AGE65-IRA",single("PA","65","90000","0",line(IncomeType.TRADITIONAL_IRA,"80000"),line(IncomeType.INTEREST,"10000")),"0","80000","10000","307","Eligible normal IRA distribution excluded; interest*.0307.");
        add(fixtures,"PA-PENSION",single("PA","65","50000","0",qualified(IncomeType.PENSION,"40000","ELIGIBLE_PLAN_AND_RETIRED"),line(IncomeType.INTEREST,"10000")),"0","40000","10000","307","Explicit eligible plan and retirement assessment.");
        add(fixtures,"PA-COMPLETE-CONVERSION",single("PA","40","60000","0",qualified(IncomeType.ROTH_CONVERSION,"60000","COMPLETE_IRA_TO_ROTH_CONVERSION")),"0","60000","0","0","Complete qualifying trustee transfer; no withholding/retained distribution.");
        add(fixtures,"PA-INVESTMENTS",single("PA","65","20000","0",line(IncomeType.INTEREST,"5000"),line(IncomeType.ORDINARY_DIVIDEND,"5000"),line(IncomeType.LONG_TERM_CAPITAL_GAIN,"10000")),"0","0","20000","614","State-defined classified investment income*.0307.");
        unsupported(fixtures,"PA-EARLY-IRA",single("PA","59.4","10000","0",line(IncomeType.TRADITIONAL_IRA,"10000")),"Cost recovery/basis unavailable.");
        unsupported(fixtures,"PA-CONVERSION-WITHHOLDING",single("PA","65","60000","0",line(IncomeType.ROTH_CONVERSION,"60000")),"Full qualifying conversion not established.");
        add(fixtures,"NY-PER-PERSON",joint("NY","70000","0",person(Owner.PRIMARY,"65",qualified(IncomeType.PENSION,"60000","QUALIFYING_PRIVATE_RETIREMENT")),person(Owner.SPOUSE,"65",qualified(IncomeType.TRADITIONAL_IRA,"10000","QUALIFYING_PRIVATE_RETIREMENT"))),"0","30000","23950","970.68","A20000+B10000, not40000; joint standard16050.");
        add(fixtures,"NY-GOVERNMENT",single("NY","65","80000","0",new Income(IncomeType.PENSION,bd("30000"),bd("30000"),PensionSource.FEDERAL_GOVERNMENT,Map.of()),qualified(IncomeType.TRADITIONAL_IRA,"50000","QUALIFYING_PRIVATE_RETIREMENT")),"0","50000","22000","1023","Unlimited federal pension first, separate private20000 allowance.");
        add(fixtures,"NY-CONVERSION-SHARED",single("NY","60","60000","0",qualified(IncomeType.PENSION,"30000","QUALIFYING_PRIVATE_RETIREMENT"),qualified(IncomeType.ROTH_CONVERSION,"30000","QUALIFYING_PRIVATE_RETIREMENT")),"0","20000","32000","1563.00","Shared owner pension/conversion limit20000; 2200*.0515=113.30.");
        add(fixtures,"NY-RECAPTURE-BELOW",single("NY","60","107649","0",line(IncomeType.INTEREST,"107649")),"0","0","99649","5311.04","NY AGI one below recapture trigger; exact marginal slices.");
        add(fixtures,"NY-RECAPTURE-EXACT",single("NY","60","107650","0",line(IncomeType.INTEREST,"107650")),"0","0","99650","5311.10","NY AGI exact ceiling uses normal schedule; exact marginal slices.");
        unsupported(fixtures,"NY-RECAPTURE-ABOVE",single("NY","60","107651","0",line(IncomeType.INTEREST,"107651")),"Supplemental worksheet required above107650.");
        for (String age : List.of("54","55","64","64.5","65")) {
            String excluded = age.equals("54") ? "0" : age.equals("65") ? "24000" : "20000";
            String taxable = age.equals("54") ? "60000" : age.equals("65") ? "36000" : "40000";
            String tax = age.equals("54") ? "2640" : age.equals("65") ? "1584" : "1760";
            add(fixtures,"CO-AGE-"+age,single("CO",age,"80000","60000",qualified(IncomeType.PENSION,"40000","COLORADO_QUALIFYING_PENSION_ANNUITY")),"0",excluded,taxable,tax,"Completed-year age band, per-person pension allowance; no repeated federal deduction.");
        }
        add(fixtures,"CO-SHARED-LIMIT",single("CO","67","70000","50000",line(IncomeType.SOCIAL_SECURITY,"18000"),qualified(IncomeType.PENSION,"40000","COLORADO_QUALIFYING_PENSION_ANNUITY")),"0","24000","26000","1144","18000 SS consumes18000 of24000; pension subtraction6000, not24000 extra.");
        add(fixtures,"CO-SS-LOW-AGI",single("CO","64","75000","50000",line(IncomeType.SOCIAL_SECURITY,"25000")),"0","25000","25000","1100","55-64 full taxable SS at AGI75000; may exceed ordinary20000 cap.");
        add(fixtures,"CO-SS-ABOVE-AGI",single("CO","64","75001","50000",qualified(IncomeType.SOCIAL_SECURITY,"25000","COLORADO_QUALIFYING_PENSION_ANNUITY")),"0","20000","30000","1320","Above75000 returns to coordinated20000 allowance.");
        add(fixtures,"CO-ADDBACK-EXACT",single("CO","54","300000","280000",line(IncomeType.INTEREST,"300000")),"0","0","280000","12320","No high-income addback trigger at exact300000.");
        unsupported(fixtures,"CO-ADDBACK-ABOVE",single("CO","54","300001","280000",line(IncomeType.INTEREST,"300001")),"Federal deduction components not supplied for2026 addback.");
        unsupported(fixtures,"CO-CONVERSION",single("CO","65","60000","40000",line(IncomeType.ROTH_CONVERSION,"60000")),"Subtraction eligibility needs additional independent verification.");
        for (String[] row : List.of(new String[]{"99999","22999","332.48"},new String[]{"100000","23000","332.50"},new String[]{"100001","60500.625","1850.16"},new String[]{"125000","76125","2722.91"},new String[]{"125001","99563.3125","4215.93"},new String[]{"150000","119875","5509.79"},new String[]{"150001","148001","7301.41"})) {
            // Expected TI constants explicitly include NJ1000 personal and1000 age65 deductions.
            String total=row[0]; String ti=row[1];
            BigDecimal exclusion=bd(total).subtract(bd(ti)).subtract(bd("2000"));
            add(fixtures,"NJ-INCOME-"+total,njSingle("65",total,line(IncomeType.PENSION,total)),"0",exclusion.toPlainString(),ti,row[2],"NJ income upper-inclusive bands, age65; actual SS provides special-exclusion ineligibility evidence.");
        }
        add(fixtures,"NJ-MFJ",njJoint("140000",person(Owner.PRIMARY,"65",line(IncomeType.PENSION,"70000")),person(Owner.SPOUSE,"65",line(IncomeType.PENSION,"70000"))),"0","35000","101000","2805.25","25 percent shared pension exclusion, personal2000+senior2000.");
        add(fixtures,"NJ-EXPLICIT-CONVERSION",njSingle("65","90000",qualified(IncomeType.ROTH_CONVERSION,"90000","STATE_TAXABLE_CONVERSION_AMOUNT_ESTABLISHED")),"0","75000","13000","182","Caller confirms NJ taxable conversion amount; eligible age and standard pension limit.");
        unsupported(fixtures,"NJ-CONVERSION-BASIS",njSingle("65","90000",line(IncomeType.ROTH_CONVERSION,"90000")),"NJ basis cannot be presumed zero.");
        unsupported(fixtures,"NJ-OTHER-RETIREMENT",njSingle("65","110000",line(IncomeType.PENSION,"80000"),line(IncomeType.INTEREST,"30000")),"Additional unused retirement allowance and earned-income eligibility unavailable.");
        unsupported(fixtures,"NJ-SPECIAL-ELIGIBILITY",single("NJ","65","90000","0",line(IncomeType.PENSION,"90000")),"No gross SS: special exclusion eligibility cannot be established.");
        return fixtures.stream();
    }

    private static void add(List<Fixture> fixtures,String id,StateTaxRequest request,String adjustment,String exclusion,String taxable,String tax,String rationale) {
        fixtures.add(fixture(id,request,adjustment,exclusion,taxable,tax,rationale));
    }

    private static Fixture fixture(String id,StateTaxRequest request,String adjustment,String exclusion,String taxable,String tax,String rationale) {
        return new Fixture(id,request,adjustment,exclusion,taxable,tax,StateTaxResult.Status.CALCULATED,Confidence.HIGH,source(request.jurisdiction()),rationale);
    }

    private static void unsupported(List<Fixture> fixtures,String id,StateTaxRequest request,String rationale) {
        fixtures.add(new Fixture(id,request,null,null,null,null,StateTaxResult.Status.UNSUPPORTED,Confidence.UNSUPPORTED_AMBIGUOUS,source(request.jurisdiction()),rationale));
    }

    private static String source(String state) {
        return switch(state) {
            case "FL" -> "https://floridarevenue.com/faq/Pages/FAQDetails.aspx?FAQID=1466";
            case "CA" -> "https://www.ftb.ca.gov/about-ftb/newsroom/tax-news/";
            case "MI" -> "https://www.michigan.gov/ors/public-act-4-of-2023-faq";
            case "IL" -> "https://tax.illinois.gov/research/publications/pubs/retirement-income.html";
            case "PA" -> "https://revenue-pa.custhelp.com/app/answers/detail/a_id/274/kw/retirement";
            case "NY" -> "https://www.tax.ny.gov/pdf/current_forms/it/it2105i.pdf";
            case "CO" -> "https://tax.colorado.gov/sites/tax/files/documents/ITT_Social_Security_Pensions_and_Annuities_Jan_2025.pdf";
            case "NJ" -> "https://www.nj.gov/treasury/taxation/pdf/pubs/tgi-ee/git1&2.pdf";
            default -> throw new IllegalArgumentException(state);
        };
    }

    private static StateTaxRequest single(String state,String age,String agi,String federalTaxable,Income... income) {
        return new StateTaxRequest(state,2026,FilingStatus.SINGLE,bd(agi),bd(federalTaxable),List.of(person(Owner.PRIMARY,age,income)));
    }
    private static StateTaxRequest joint(String state,String agi,String taxable,PersonIncome primary,PersonIncome spouse) {
        return new StateTaxRequest(state,2026,FilingStatus.MARRIED_FILING_JOINTLY,bd(agi),bd(taxable),List.of(primary,spouse));
    }
    private static PersonIncome person(Owner owner,String age,Income... income) {
        BigDecimal taxable=Arrays.stream(income).filter(line->line.type()==IncomeType.SOCIAL_SECURITY)
                .map(Income::federalIncluded).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new PersonIncome(owner,bd(age),taxable.signum()>0?bd("40000"):BigDecimal.ZERO,taxable,Set.of(),List.of(income));
    }
    private static StateTaxRequest njSingle(String age,String stateTotal,Income... income) {
        List<Income> lines=new ArrayList<>(List.of(income));
        lines.add(line(IncomeType.SOCIAL_SECURITY,"34000"));
        return single("NJ",age,bd(stateTotal).add(bd("34000")).toPlainString(),"0",lines.toArray(Income[]::new));
    }
    private static StateTaxRequest njJoint(String total,PersonIncome primary,PersonIncome spouse) {
        return joint("NJ",bd(total).add(bd("68000")).toPlainString(),"0",withSs(primary),withSs(spouse));
    }
    private static PersonIncome withSs(PersonIncome person) {
        List<Income> lines=new ArrayList<>(person.income()); lines.add(line(IncomeType.SOCIAL_SECURITY,"34000"));
        return person(person.owner(),person.age().toPlainString(),lines.toArray(Income[]::new));
    }
    private static Income line(IncomeType type,String value) { return new Income(type,bd(value),bd(value),PensionSource.PRIVATE,Map.of()); }
    private static Income qualified(IncomeType type,String value,String qualification) { return new Income(type,bd(value),bd(value),PensionSource.PRIVATE,Map.of(qualification,Qualification.ELIGIBLE)); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static void amount(String expected,BigDecimal actual) { assertEquals(0,bd(expected).compareTo(actual),"Expected "+expected+" actual "+actual); }
}
