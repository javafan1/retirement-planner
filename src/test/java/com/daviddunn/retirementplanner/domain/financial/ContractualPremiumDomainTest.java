package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContractualPremiumDomainTest {
    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    @TempDir Path directory;

    public static Expense premium() { return premium("2000", ContractualPremiumPolicy.Insured.PRIMARY, LocalDate.of(2030,1,1),LocalDate.of(2040,12,31)); }
    public static Expense premium(String amount, ContractualPremiumPolicy.Insured insured, LocalDate start, LocalDate end) {
        return new Expense("Term premium",new BigDecimal(amount),GrowthCategory.NONE,start,end,ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,null,
                new ContractualPremiumPolicy(ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,insured));
    }

    @Test void legacyWireAndStrictPremiumRoundTrip() throws Exception {
        var legacy = new Expense("Living",new BigDecimal("123.4500"));
        var legacyNode = JSON.valueToTree(legacy);
        assertFalse(legacyNode.has("contractualPremium"));
        assertEquals(6,legacyNode.size());
        var decoded = JSON.readValue(JSON.writeValueAsBytes(premium()),Expense.class);
        assertEquals(premium().getContractualPremium(),decoded.getContractualPremium());
        assertEquals(premium().getAnnualAmount(),decoded.getAnnualAmount());
        var extra = ((ObjectNode)legacyNode).put("unknownLegacyField",true);
        assertEquals(legacy.getAnnualAmount(),JSON.treeToValue(extra,Expense.class).getAnnualAmount());
    }

    @ParameterizedTest @ValueSource(strings={"contractualPremium","startDate","annualAmount","description"})
    void missingRequiredPremiumFactsFail(String field) {
        ObjectNode node = JSON.valueToTree(premium()); node.remove(field);
        assertThrows(Exception.class,()->JSON.treeToValue(node,Expense.class));
    }

    @ParameterizedTest @ValueSource(strings={"-1","NaN","Infinity","1.000000001","1000000000001","1E-999","1E999","1234567890123456789012345"})
    void unsupportedAmountsFail(String value) {
        assertThrows(IllegalArgumentException.class,()->premium(value,ContractualPremiumPolicy.Insured.PRIMARY,LocalDate.of(2030,1,1),null));
    }

    @Test void amountAndDateBoundsAndPolicyRestrictions() throws Exception {
        premium("1000000000000",ContractualPremiumPolicy.Insured.PRIMARY,LocalDate.of(1000,1,1),LocalDate.of(9999,12,31));
        assertThrows(IllegalArgumentException.class,()->premium("1",ContractualPremiumPolicy.Insured.PRIMARY,LocalDate.of(999,1,1),null));
        assertThrows(IllegalArgumentException.class,()->premium("1",ContractualPremiumPolicy.Insured.PRIMARY,LocalDate.of(2030,2,1),LocalDate.of(2030,1,31)));
        var policy = premium().getContractualPremium();
        assertThrows(IllegalArgumentException.class,()->new Expense("x",BigDecimal.ONE,GrowthCategory.GENERAL,LocalDate.of(2030,1,1),null,ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,null,policy));
        assertThrows(IllegalArgumentException.class,()->new Expense("x",BigDecimal.ONE,GrowthCategory.NONE,LocalDate.of(2030,1,1),null,ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,new BigDecimal(".1"),policy));
        assertThrows(IllegalArgumentException.class,()->new Expense("x",BigDecimal.ONE,GrowthCategory.GENERAL,null,null,ExpenseType.RECURRING,null,policy));
        for (String field : List.of("model","insured")) {
            ObjectNode node = JSON.valueToTree(premium()); ((ObjectNode)node.get("contractualPremium")).put(field,"UNSUPPORTED");
            assertThrows(Exception.class,()->JSON.treeToValue(node,Expense.class));
        }
        ObjectNode node = JSON.valueToTree(premium()); ((ObjectNode)node.get("contractualPremium")).put("ignored",true);
        assertThrows(Exception.class,()->JSON.treeToValue(node,Expense.class));
        String duplicate = JSON.writeValueAsString(premium()).replace("\"insured\":\"PRIMARY\"","\"insured\":\"PRIMARY\",\"insured\":\"SPOUSE\"");
        assertThrows(Exception.class,()->JSON.readValue(duplicate,Expense.class));
    }

    @Test void householdRejectsAbsentSpouseAtMutationAndLoad() throws Exception {
        var plan = BenefitFixtures.plan(false);
        var spouse = premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,LocalDate.of(2030,1,1),null);
        assertThrows(IllegalArgumentException.class,()->plan.getHousehold().addExpense(spouse));
        ObjectNode document = JSON.valueToTree(plan);
        ((com.fasterxml.jackson.databind.node.ArrayNode)document.path("household").path("expenses")).add(JSON.valueToTree(spouse));
        assertThrows(Exception.class,()->JSON.treeToValue(document,RetirementPlan.class));
    }

    @Test void pureSelectionDoesNotTreatAnAbsentSpouseAsDeceased() {
        var p=BenefitFixtures.plan(false);
        var view=EffectiveHouseholdDeathView.resolve(p.getHousehold(),new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.empty());
        var expense=premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,LocalDate.of(2030,1,1),null);
        assertThrows(IllegalArgumentException.class,()->ContractualPremiums.annual(expense,LocalDate.of(2030,1,1),2030,view));
    }

    @Test void calendarMonthBoundariesAndAnnualRounding() {
        var plan = BenefitFixtures.plan(true);
        var view = EffectiveHouseholdDeathView.resolve(plan.getHousehold(),new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.empty());
        var opening = LocalDate.of(2030,7,19);
        assertEquals(new BigDecimal("1000.00"),ContractualPremiums.annual(premium(),opening,2030,view));
        for (var interval : List.of(new String[]{"2030-01-01","2030-12-31","2000.00"},new String[]{"2030-12-15","2030-12-16","166.67"},
                new String[]{"2030-02-28","2030-03-01","333.33"},new String[]{"2031-01-01","2031-01-01","0"})) {
            var expense = premium("2000",ContractualPremiumPolicy.Insured.PRIMARY,LocalDate.parse(interval[0]),LocalDate.parse(interval[1]));
            assertEquals(0,new BigDecimal(interval[2]).compareTo(ContractualPremiums.annual(expense,LocalDate.of(2030,1,1),2030,view)));
        }
    }

    @Test void actualPreChangeReaderRejectsPremiumEvenWhenUnknownPropertiesAreIgnored() throws Exception {
        var sources = new ArrayList<String>();
        for (String name : List.of("Expense","ExpenseDeserializer","ExpenseType","GrowthCategory")) {
            var source = directory.resolve(name+".java");
            try (var in = getClass().getResourceAsStream("/phase23c/legacy-expense/"+name+".java.txt")) { Files.copy(Objects.requireNonNull(in),source); }
            sources.add(source.toString());
        }
        var arguments = new ArrayList<>(List.of("-classpath",System.getProperty("java.class.path"),"-d",directory.toString())); arguments.addAll(sources);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new)));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.daviddunn.retirementplanner.domain.financial.")) {
                    var result=findLoadedClass(name); if (result==null) result=findClass(name); if (resolve) resolveClass(result); return result;
                }
                return super.loadClass(name,resolve);
            }
        }) {
            var oldExpense = loader.loadClass(Expense.class.getName());
            var oldMapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            assertNotNull(oldMapper.readValue(JSON.writeValueAsBytes(new Expense("Ordinary",BigDecimal.ONE)),oldExpense));
            var wire=JSON.writeValueAsBytes(premium());
            var rejected=assertThrows(Exception.class,()->oldMapper.readValue(wire,oldExpense));
            assertTrue(rejected.getMessage().contains("CONTRACTUAL_INSURANCE_PREMIUM"));
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2035,2038,PRIMARY,2034,2000","2035,2038,PRIMARY,2035,0","2035,2038,PRIMARY,2036,0",
            "2035,2038,SPOUSE,2035,2000","2035,2038,SPOUSE,2038,0","2029,2038,PRIMARY,2030,0","2030,2038,PRIMARY,2030,0",
            "2035,2035,PRIMARY,2035,0","2035,2035,SPOUSE,2035,0"})
    void scenarioMortalitySelectsOnlyTheNamedInsured(int primary,int spouse,ContractualPremiumPolicy.Insured insured,int year,String expected) {
        var p=BenefitFixtures.plan(true);
        var view=EffectiveHouseholdDeathView.resolve(p.getHousehold(),new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.of(
                new com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario(Optional.of(java.time.Year.of(primary)),Optional.of(java.time.Year.of(spouse)))));
        var expense=premium("2000",insured,LocalDate.of(2030,1,1),LocalDate.of(2040,12,31));
        assertEquals(0,new BigDecimal(expected).compareTo(ContractualPremiums.annual(expense,LocalDate.of(2030,1,1),year,view)));
        assertEquals(ContractualPremiums.annual(expense,LocalDate.of(2030,1,1),year,view),ContractualPremiums.annual(expense,LocalDate.of(2030,1,1),year,view));
    }

    @Test void occurrenceBoundsIncludeReplacementsAndDoNotCreateAssets() {
        var p=BenefitFixtures.plan(true); p.getHousehold().getExpenses().stream().toList().forEach(p.getHousehold()::removeExpense);
        for(int i=0;i<1000;i++) p.getHousehold().addExpense(premium());
        assertThrows(IllegalArgumentException.class,()->p.getHousehold().addExpense(premium()));
        assertThrows(IllegalArgumentException.class,()->p.getHousehold().addExpense(new Expense("Other",BigDecimal.ONE)));
        p.getHousehold().replaceExpense(p.getHousehold().getExpenses().getFirst(),premium());
        assertEquals(1000,p.getHousehold().getExpenses().size());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"2034-12-31,0","2035-01-15,166.67","2035-12-01,2000"})
    void inclusiveTerminationAndExpiredCoverage(String termination,String expected) {
        var p=BenefitFixtures.plan(true);
        var view=EffectiveHouseholdDeathView.resolve(p.getHousehold(),new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),Optional.empty());
        var expense=premium("2000",ContractualPremiumPolicy.Insured.PRIMARY,LocalDate.of(2030,1,1),LocalDate.parse(termination));
        assertEquals(0,new BigDecimal(expected).compareTo(ContractualPremiums.annual(expense,LocalDate.of(2035,1,1),2035,view)));
    }
}
