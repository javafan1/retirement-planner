package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRateWorksheet.Policy.*;

/** Test-local experiment only: never installs a production Arkansas route. */
class ArkansasFundingCounterexampleTest {
    private final ArkansasRateWorksheet worksheet=new ArkansasRateWorksheet();
    private BigDecimal need(BigDecimal withdrawal,ArkansasRateWorksheet.Policy policy) {
        // Independent 2026 federal worksheet: taxable=70097+W, in the 22% slice.
        var federal=b("10133.34").add(withdrawal.multiply(b(".22"))).setScale(2,RoundingMode.HALF_UP);
        var state=worksheet.grossTax(2026,b("77727").add(withdrawal),policy).subtract(b("29"));
        return federal.add(state);
    }

    @Test
    void exactAuditedTwoCycleAndNoFixedPointInEitherApplicableSegment() {
        assertEquals(b("16975.09"),need(b("16972.94"),P_STATUTORY_CENTS));
        assertEquals(b("16972.94"),need(b("16975.09"),P_STATUTORY_CENTS));
        // Continuous roots (before final-cent rounding) fall outside both their own segments.
        var lowRoot=b("12613.039").divide(b(".743"),12,RoundingMode.HALF_UP);
        var highRoot=b("12610.339").divide(b(".743"),12,RoundingMode.HALF_UP);
        assertTrue(lowRoot.compareTo(b("16974"))>0);
        assertTrue(highRoot.compareTo(b("16974"))<0);
        for(var w=b("16972");w.compareTo(b("16977"))<=0;w=w.add(b(".01")))
            assertNotEquals(0,w.compareTo(need(w,P_STATUTORY_CENTS)));
    }

    @Test
    void minimumExecutableCentAndCurrentSettlementCannotAttributeItsGenuineSurplus() {
        assertTrue(b("16973.99").compareTo(need(b("16973.99"),P_STATUTORY_CENTS))<0);
        assertEquals(b("16972.66"),need(b("16974.00"),P_STATUTORY_CENTS));
        assertEquals(b("1.34"),b("16974.00").subtract(need(b("16974.00"),P_STATUTORY_CENTS)));
        // On the entire lower branch need-W is decreasing and remains positive up to its last cent.
        assertTrue(need(b("0"),P_STATUTORY_CENTS).signum()>0);
        var ira=new TraditionalIRA("Source",AccountOwnership.PRIMARY,b("1000000"));
        var p=new ProjectedPortfolio(List.of(new ProjectedAccountBalance(ira,b("1000000"))));
        var a=new ProjectedWithdrawalAllocator();var strategy=new TaxDeferredFirstWithdrawalStrategy();
        var spending=a.allocateAdditionalWithdrawal(p,b("86197"),strategy);
        var funded=a.allocateAdditionalWithdrawal(spending.getPortfolio(),b("16974.00"),strategy);
        assertEquals(0,b("16974.00").compareTo(funded.getWithdrawalBreakdown().getTaxDeferredWithdrawal()));
        var hypotheticalRetained=funded.getPortfolio().withAdditionalRetainedNonQualifiedAssets(b("1.34"));
        assertEquals(0,b("896830.34").compareTo(hypotheticalRetained.getTotalBalance()));
        assertEquals(b("1000000"),ira.getCurrentBalance());
        assertThrows(IllegalArgumentException.class,()->HouseholdCashSettlement.calculate(b("0"),b("0"),b("86197"),
                b("16974"),b("86197"),b("0"),b("16972.66")));
        assertThrows(IllegalArgumentException.class,()->new HouseholdCashSettlement(b("0"),b("0"),b("86197"),
                b("16974"),b("86197"),b("0"),b("16972.66"),b("1.34"),b("0"),b("0")));
        // Honest test-local ledger proves feasibility, not compatibility with current settlement attribution.
        assertEquals(0,b("1000000").subtract(hypotheticalRetained.getTotalBalance()).subtract(b("86197"))
                .subtract(b("16972.66")).signum());
    }

    @Test
    void agencyAlternativeHasAConvergentOrdinaryFixedPointForTheOriginalFixture() {
        var w=b("0");
        for(int i=0;i<100;i++) w=need(w,OFFICIAL_2026_ESTIMATED_TABLE);
        assertEquals(0,w.compareTo(need(w,OFFICIAL_2026_ESTIMATED_TABLE)));
        assertTrue(w.compareTo(b("16974"))<0);
        // 10133.34+.22*16973.36 => 13867.48; ES gross3134.88-29=3105.88.
        assertEquals(b("16973.36"), need(b("16973.36"),OFFICIAL_2026_ESTIMATED_TABLE));
        assertEquals(b("16974.79"), need(b("16974.79"),OFFICIAL_2026_ESTIMATED_TABLE));
        // Its upward tax jump makes sufficient funding nonmonotone and permits two roots.
        var boundary=b("16974");
        var leftState=worksheet.grossTax(2026,b("94700.99"),OFFICIAL_2026_ESTIMATED_TABLE);
        var rightState=worksheet.grossTax(2026,b("94701"),OFFICIAL_2026_ESTIMATED_TABLE);
        assertEquals(b("1.10"),rightState.subtract(leftState));
        assertTrue(boundary.signum()>0);
    }

    @Test
    void boundedSegmentCandidatePrototypeFindsMinimumWithoutEnumeratingEveryWithdrawalCent() {
        assertEquals(b("16974.00"),minimumSufficient(P_STATUTORY_CENTS,b("20000")).orElseThrow());
        assertEquals(b("16973.36"),minimumSufficient(OFFICIAL_2026_ESTIMATED_TABLE,b("20000")).orElseThrow());
        assertTrue(minimumSufficient(P_STATUTORY_CENTS,b("1000")).isEmpty());
    }

    // Restricted proof domain: this fixture, 0<=W<=20000, fully taxable same-owner IRA,
    // 22% federal segment, no SS/NIIT/credits beyond the stated AR29. NOT a production solver.
    private Optional<BigDecimal> minimumSufficient(ArkansasRateWorksheet.Policy policy,BigDecimal capacity) {
        if(capacity.signum()<0 || capacity.compareTo(b("20000"))>0) throw new IllegalArgumentException();
        var boundaries=new TreeSet<BigDecimal>();boundaries.add(b("0"));boundaries.add(b("2274"));
        for(int i=0;i<=29;i++) boundaries.add(b("16974").add(BigDecimal.valueOf(100L*i)));
        boundaries.add(capacity.add(b(".01")));
        var points=boundaries.stream().filter(x->x.compareTo(capacity.add(b(".01")))<=0).toList();
        var candidates=new TreeSet<BigDecimal>();
        for(int i=0;i<points.size()-1;i++) {
            var lower=points.get(i);var upper=points.get(i+1).subtract(b(".01"));
            var slope=policy==OFFICIAL_2026_ESTIMATED_TABLE && lower.compareTo(b("16974"))>=0
                    && lower.compareTo(b("19874"))<0 ? b(".22") : b(".257");
            var root=need(lower,policy).subtract(lower.multiply(slope)).divide(BigDecimal.ONE.subtract(slope),2,RoundingMode.FLOOR);
            candidates.add(lower.setScale(2));candidates.add(upper.setScale(2));
            // Two rounded taxes introduce <=.01 error; +/-3 cents bounds its root effect here.
            for(int j=-3;j<=3;j++) {
                var candidate=root.add(BigDecimal.valueOf(j,2));
                if(candidate.compareTo(lower)>=0 && candidate.compareTo(upper)<=0) candidates.add(candidate);
            }
        }
        return candidates.stream().filter(w->w.compareTo(need(w,policy))>=0).findFirst();
    }

    @Test
    void unmodifiedRealSolverReproducesNonconvergenceWithAnExplicitTestLocalAdapter() throws Exception {
        var service=new ProductionStateTaxService(Map.of(StateTaxJurisdiction.FL,request -> {
            var net=request.federal().getAdjustedGrossIncome().subtract(b("6000")).subtract(b("2470"));
            var tax=worksheet.grossTax(2026,net,P_STATUTORY_CENTS).subtract(b("29"));
            return new ProductionStateTaxResult(StateTaxJurisdiction.FL,request.assumptions().calculationMethod(),
                    StateTaxCalculationStatus.CALCULATED,tax,List.of("TEST LOCAL Arkansas P; not Florida production law"),Optional.empty());
        }));
        var ira=new TraditionalIRA("Source",AccountOwnership.PRIMARY,b("1000000"));
        var p=new ProjectedPortfolio(List.of(new ProjectedAccountBalance(ira,b("1000000"))));
        var strategy=new TaxDeferredFirstWithdrawalStrategy();
        var spending=new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(p,b("86197"),strategy);
        var household=new Household(new Person("Primary","Example",LocalDate.of(1966,6,1)));
        var failure=assertThrows(IllegalStateException.class,()->new TaxFundingCalculator(service).calculate(household,
                LocalDate.of(2026,1,1),spending.getPortfolio(),spending.getWithdrawalBreakdown(),strategy,FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),b("0"),b("0"),null,null,b("0"),null,b("0"),
                Optional.empty(),List.of(),TaxHouseholdContext.capture(household,LocalDate.of(2026,1,1),null),
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        assertTrue(failure.getMessage().contains("did not converge"));
        assertEquals(b("1000000"),ira.getCurrentBalance());
    }
    private static BigDecimal b(String value){return new BigDecimal(value);}
}
