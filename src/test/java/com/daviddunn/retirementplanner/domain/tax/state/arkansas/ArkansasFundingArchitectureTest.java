package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.HouseholdCashSettlement;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectedWithdrawalAllocator;
import com.daviddunn.retirementplanner.domain.roth.ProjectedPortfolioRothConverter;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

import static com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRateWorksheet.Policy.OFFICIAL_2026_ESTIMATED_TABLE;
import static com.daviddunn.retirementplanner.domain.tax.state.arkansas.ArkansasRateWorksheet.Policy.P_STATUTORY_CENTS;
import static org.junit.jupiter.api.Assertions.*;

/** Independent worksheet transcription. Funding and surplus simulation are test-only. */
class ArkansasFundingArchitectureTest {
    private static final BigDecimal CENT = decimal(".01");
    private static final BigDecimal ZERO = decimal("0");
    private static final BigDecimal UPPER_TAX_SLOPE = decimal(".257");
    private static final String[] BAND_ENDPOINTS = {
            "3134.04/3137.70", "3147.74/3151.40", "3161.44/3165.10", "3175.14/3178.80",
            "3188.84/3192.50", "3202.54/3206.20", "3216.24/3219.90", "3229.94/3233.60",
            "3243.64/3247.30", "3257.34/3261.00", "3271.04/3274.70", "3284.74/3288.40",
            "3298.44/3302.10", "3312.14/3315.80", "3325.84/3329.50", "3339.54/3343.20",
            "3353.24/3356.90", "3366.94/3370.60", "3380.64/3384.30", "3394.34/3398.00",
            "3408.04/3411.70", "3421.74/3425.40", "3435.44/3439.10", "3449.14/3452.80",
            "3462.84/3466.50", "3476.54/3480.20", "3490.24/3493.90", "3503.94/3507.60",
            "3517.64/3521.30"
    };
    private static final int[] ESTIMATED_FIXED = {3136, 3150, 3163, 3177, 3191, 3204, 3218,
            3232, 3245, 3259, 3273, 3287, 3300, 3314, 3328, 3341, 3355, 3369, 3382, 3396,
            3410, 3424, 3437, 3451, 3465, 3478, 3492, 3506, 3519};
    // From, inclusive through, base, rate, excess over. The printed zero-row gap has no entry.
    private static final String[][] ESTIMATED_ROWS = {
            {"5600", "11199.99", "0", ".02", "5599.99"},
            {"11200", "15999.99", "111", ".03", "11199.99"},
            {"16000", "26399.99", "255", ".034", "15999.99"},
            {"26400", "29999.99", "608", ".037", "26399.99"},
            {"30000", "39999.99", "741", ".037", "29999.99"},
            {"40000", "49999.99", "1111", ".037", "39999.99"},
            {"50000", "59999.99", "1481", ".037", "49999.99"},
            {"60000", "69999.99", "1851", ".037", "59999.99"},
            {"70000", "80000.99", "2221", ".037", "69999.99"},
            {"80001", "94700.99", "2591", ".037", "80000.99"},
            {"97601", "99999.99", "3533", ".037", "97600.99"},
            {"100000", null, "3618", ".037", "99999.99"}
    };

    @Test
    void independentPublicationReconstructionCoversEveryAdjustmentEdge() {
        var worksheet = new ArkansasRateWorksheet();
        for (int band = 0; band < 29; band++) {
            var start = BigDecimal.valueOf(94701 + 100L * band);
            var lastDollar = start.add(decimal("99"));
            var lastCent = start.add(decimal("99.99"));
            var points = List.of(start.subtract(CENT), start, start.add(CENT), lastDollar, lastCent,
                    start.add(decimal("100")), start.add(decimal("100.01")));
            String[] endpoints = BAND_ENDPOINTS[band].split("/");
            assertEquals(decimal(endpoints[0]), cents(statute(start)));
            assertEquals(decimal(endpoints[1]), cents(statute(lastDollar)));
            for (var point : points) {
                assertEquals(cents(statute(point)), worksheet.grossTax(2026, point, P_STATUTORY_CENTS), point.toString());
                assertEquals(cents(estimated(point)), worksheet.grossTax(2026, point, OFFICIAL_2026_ESTIMATED_TABLE),
                        point.toString());
            }
        }
        for (var row : ESTIMATED_ROWS) {
            var start = decimal(row[0]);
            if (!row[0].equals("5600")) {
                compareRates(worksheet, start.subtract(CENT));
            }
            compareRates(worksheet, start);
            compareRates(worksheet, start.add(CENT));
            if (row[1] != null) compareRates(worksheet, decimal(row[1]));
        }
        compareRates(worksheet, decimal("5598.99"));
        for (int cent = 559900; cent < 560000; cent++) {
            var gap = BigDecimal.valueOf(cent, 2);
            assertEquals(decimal("0.00"), cents(statute(gap)));
            assertThrows(IllegalArgumentException.class, () -> estimated(gap));
            assertThrows(IllegalArgumentException.class,
                    () -> worksheet.grossTax(2026, gap, OFFICIAL_2026_ESTIMATED_TABLE));
        }
    }

    @ParameterizedTest
    @CsvSource({"11200,112.00,111.00", "50000,1482.80,1481.00", "94700,3136.70,3134.86",
            "94701,3134.04,3136.00", "94801,3147.74,3150.00", "97601,3531.34,3533.00"})
    void directDifferencesAreIndependentlyReproducible(String net, String p, String es) {
        assertEquals(decimal(p), cents(statute(decimal(net))));
        assertEquals(decimal(es), cents(estimated(decimal(net))));
        compareRates(new ArkansasRateWorksheet(), decimal(net));
    }

    @Test
    void independentFundingWorksheetsSeparateDisplayedEqualityFromRawFederalObligation() {
        assertEquals(decimal("16975.09"), obligation(decimal("16972.94"), false, true));
        assertEquals(decimal("16972.94"), obligation(decimal("16975.09"), false, true));
        assertEquals(decimal("16972.6600"), obligation(decimal("16974.00"), false, false));
        assertEquals(decimal("16973.36"), obligation(decimal("16973.36"), true, true));
        assertEquals(decimal("16974.79"), obligation(decimal("16974.79"), true, true));
        assertEquals(decimal("16973.3592"), obligation(decimal("16973.36"), true, false));
        assertEquals(decimal("16974.7938"), obligation(decimal("16974.79"), true, false));
        assertTrue(sufficient(decimal("16973.36"), true));
        assertFalse(sufficient(decimal("16974.00"), true));
        assertFalse(sufficient(decimal("16974.79"), true));
        assertTrue(sufficient(decimal("16974.80"), true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void boundedProofUsesExecutableWithdrawalsAndConservesSurplus(boolean useEstimated) {
        var ira = new TraditionalIRA("Source", AccountOwnership.PRIMARY, decimal("106197"));
        var portfolio = ProjectedPortfolio.from(new AccountPortfolio(List.of(ira)));
        var allocator = new ProjectedWithdrawalAllocator();
        var strategy = new TaxableFirstWithdrawalStrategy();
        var afterSpending = allocator.allocateAdditionalWithdrawal(portfolio, decimal("86197"), strategy).getPortfolio();
        Function<BigDecimal, BigDecimal> exactNeed = w -> {
            var sources = allocator.calculateWithdrawalBreakdown(afterSpending, w, strategy);
            assertEquals(0, w.compareTo(sources.getTotalWithdrawal()));
            assertEquals(0, w.compareTo(sources.getTaxDeferredWithdrawal()));
            assertTrue(sources.getIncomeSources().stream().allMatch(source ->
                    source.owner().orElseThrow() == AccountOwnership.PRIMARY
                            && source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING));
            return obligation(sources.getTaxDeferredWithdrawal(), useEstimated, false);
        };
        var minimum = firstFeasible(decimal("20000"), useEstimated, exactNeed).orElseThrow();
        assertEquals(decimal(useEstimated ? "16973.36" : "16974.00"), minimum);
        assertEquals(minimum, firstFeasible(decimal("20000"), useEstimated, exactNeed).orElseThrow());
        if (useEstimated) {
            // A higher, fully executable cap is insufficient even though the earlier solution is valid.
            assertTrue(decimal("16974.00").compareTo(exactNeed.apply(decimal("16974.00"))) < 0);
            assertEquals(minimum, firstFeasible(decimal("16974.00"), true, exactNeed).orElseThrow());
        }
        assertTrue(firstFeasible(minimum.subtract(CENT), useEstimated, exactNeed).isEmpty());
        assertTrue(firstFeasible(decimal("1000"), useEstimated, exactNeed).isEmpty());
        var obligation = exactNeed.apply(minimum);
        var excess = minimum.subtract(obligation);
        var withdrawn = allocator.applyAdditionalWithdrawal(afterSpending, minimum, strategy);
        var simulatedEnd = withdrawn.withAdditionalRetainedNonQualifiedAssets(excess);
        assertEquals(0, portfolio.getTotalBalance().subtract(decimal("86197")).subtract(obligation)
                .compareTo(simulatedEnd.getTotalBalance()));
        assertEquals(decimal("106197"), ira.getCurrentBalance());
        if (!useEstimated) {
            assertEquals(0, decimal("1.34").compareTo(excess));
            assertThrows(IllegalArgumentException.class, () -> HouseholdCashSettlement.calculate(
                    ZERO, ZERO, decimal("86197"), minimum, decimal("86197"), ZERO, obligation));
        }
    }

    @Test
    void realAllocatorPreservesMixedTaxCharactersAndAccountOrderAtExhaustion() {
        var checking = new CheckingAccount("Cash", AccountOwnership.PRIMARY, decimal("15.001"));
        var brokerage = new BrokerageAccount("Brokerage", AccountOwnership.JOINT, decimal("24.999"));
        var spouse = new TraditionalIRA("Spouse", AccountOwnership.SPOUSE, decimal("10"));
        var primary = new TraditionalIRA("Primary", AccountOwnership.PRIMARY, decimal("100"));
        var portfolio = new ProjectedPortfolio(List.of(
                new ProjectedAccountBalance(checking, checking.getCurrentBalance()),
                new ProjectedAccountBalance(brokerage, brokerage.getCurrentBalance()),
                new ProjectedAccountBalance(spouse, spouse.getCurrentBalance()),
                new ProjectedAccountBalance(primary, primary.getCurrentBalance())));
        var allocator = new ProjectedWithdrawalAllocator();
        var strategy = new TaxableFirstWithdrawalStrategy();
        var trace = allocator.calculateWithdrawalBreakdown(portfolio, decimal("50.01"), strategy);
        assertEquals(List.of(AccountOwnership.PRIMARY, AccountOwnership.JOINT, AccountOwnership.SPOUSE,
                AccountOwnership.PRIMARY), trace.getIncomeSources().stream().map(s -> s.owner().orElseThrow()).toList());
        assertTrue(trace.getIncomeSources().get(1).modeledTaxableAmount().isEmpty());
        assertEquals(0, decimal("10.01").compareTo(trace.getTaxDeferredWithdrawal()));
        assertEquals(0, decimal("0.01").compareTo(trace.getIncomeSources().getLast().grossAmount()));
        var end = allocator.applyAdditionalWithdrawal(portfolio, decimal("50.01"), strategy);
        assertEquals(0, decimal("99.99").compareTo(end.getTotalBalance()));
        assertEquals(decimal("10"), spouse.getCurrentBalance());
    }

    @Test
    void fundingFromThePreConversionPortfolioReusesAnAlreadyConvertedSource() {
        var primary = new TraditionalIRA("Primary traditional", AccountOwnership.PRIMARY, decimal("1000"));
        var spouse = new TraditionalIRA("Spouse traditional", AccountOwnership.SPOUSE, decimal("90"));
        var roth = new RothIRA("Primary Roth", AccountOwnership.PRIMARY, ZERO);
        var portfolio = ProjectedPortfolio.from(new AccountPortfolio(List.of(primary, spouse, roth)));
        var conversion = new ProjectedPortfolioRothConverter().convertHousehold(portfolio, decimal("1100"));
        assertEquals(0, decimal("1000").compareTo(conversion.getTotalConversion()));
        assertEquals(0, decimal("100").compareTo(decimal("1100").subtract(conversion.getTotalConversion())));
        assertEquals(AccountOwnership.PRIMARY, conversion.getIncomeSources(2026).getFirst().owner().orElseThrow());
        var allocator = new ProjectedWithdrawalAllocator();
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var before = allocator.calculateWithdrawalBreakdown(portfolio, decimal("150"), strategy);
        var after = allocator.calculateWithdrawalBreakdown(conversion.getPortfolio(), decimal("150"), strategy);
        assertEquals(List.of(AccountOwnership.PRIMARY),
                before.getIncomeSources().stream().map(s -> s.owner().orElseThrow()).toList());
        assertEquals(List.of(AccountOwnership.SPOUSE, AccountOwnership.PRIMARY),
                after.getIncomeSources().stream().map(s -> s.owner().orElseThrow()).toList());
        assertEquals(0, decimal("90").compareTo(after.getTaxDeferredWithdrawal()));
        assertEquals(0, decimal("60").compareTo(after.getRothWithdrawal()));
        assertEquals(0, decimal("1000").compareTo(primary.getCurrentBalance()));
        assertEquals(ZERO, roth.getCurrentBalance());
    }

    // For this fixture the only interior changes are the printed state rows and the
    // adjustment bands. Federal taxable income stays in the 22% slice up to $20,000.
    // Upper bound on increasing unrounded taxes in each segment is 25.7%.
    private static Optional<BigDecimal> firstFeasible(BigDecimal capacity, boolean useEstimated,
                                                       Function<BigDecimal, BigDecimal> need) {
        var cuts = new TreeSet<>(List.of(ZERO, capacity.add(CENT)));
        if (useEstimated && capacity.compareTo(decimal("2274")) >= 0) cuts.add(decimal("2274"));
        for (int band = 0; band <= 29; band++) {
            var start = BigDecimal.valueOf(16974 + 100L * band);
            if (start.compareTo(capacity) <= 0) cuts.add(start);
        }
        var points = List.copyOf(cuts);
        for (int i = 0; i < points.size() - 1; i++) {
            var left = points.get(i).setScale(2);
            var right = points.get(i + 1).subtract(CENT);
            var deficit = need.apply(left).subtract(left);
            if (deficit.signum() <= 0) return Optional.of(left);

            // State tax rounds by at most half a cent. Difference of two such errors
            // is bounded by one cent. Taxes are nondecreasing before rounding here.
            var slope = useEstimated && left.compareTo(decimal("16974")) >= 0
                    && left.compareTo(decimal("19874")) < 0 ? decimal(".22") : UPPER_TAX_SLOPE;
            var residualSlope = BigDecimal.ONE.subtract(slope);
            var earliest = left.add(deficit.subtract(CENT).max(ZERO).divide(residualSlope,
                    2, RoundingMode.CEILING)).max(left.add(CENT));
            var guaranteed = left.add(deficit.add(CENT).divide(residualSlope, 2, RoundingMode.CEILING));
            var limit = right.min(guaranteed);
            for (var w = earliest; w.compareTo(limit) <= 0; w = w.add(CENT)) {
                if (w.compareTo(need.apply(w)) >= 0) return Optional.of(w);
            }
            assertTrue(right.compareTo(guaranteed) < 0, "Certified upper bound must succeed");
        }
        return Optional.empty();
    }

    private static void compareRates(ArkansasRateWorksheet worksheet, BigDecimal n) {
        assertEquals(cents(statute(n)), worksheet.grossTax(2026, n, P_STATUTORY_CENTS));
        assertEquals(cents(estimated(n)), worksheet.grossTax(2026, n, OFFICIAL_2026_ESTIMATED_TABLE));
    }

    private static BigDecimal statute(BigDecimal n) {
        if (n.compareTo(decimal("94701")) >= 0) {
            var gross = decimal("94").add(n.subtract(decimal("4700")).multiply(decimal(".037")));
            for (int band = ESTIMATED_FIXED.length - 1; band >= 0; band--) {
                var from = BigDecimal.valueOf(94701 + band * 100L);
                if (n.compareTo(from) >= 0 && n.compareTo(from.add(decimal("100"))) < 0) {
                    return gross.subtract(BigDecimal.valueOf(290 - band * 10L));
                }
            }
            return gross;
        }
        // Equivalent continuous-slice formula using the marginal rate increments.
        return over(n, "5600", ".02").add(over(n, "11200", ".01"))
                .add(over(n, "16000", ".004")).add(over(n, "26400", ".003"));
    }

    private static BigDecimal estimated(BigDecimal n) {
        if (n.compareTo(decimal("5599")) < 0) return ZERO;
        for (int band = 0; band < ESTIMATED_FIXED.length; band++) {
            var from = BigDecimal.valueOf(94701 + band * 100L);
            if (n.compareTo(from) >= 0 && n.compareTo(from.add(decimal("99.99"))) <= 0) {
                return BigDecimal.valueOf(ESTIMATED_FIXED[band]);
            }
        }
        for (var row : ESTIMATED_ROWS) {
            if (n.compareTo(decimal(row[0])) >= 0 && (row[1] == null || n.compareTo(decimal(row[1])) <= 0)) {
                return decimal(row[2]).add(n.subtract(decimal(row[4])).multiply(decimal(row[3])));
            }
        }
        throw new IllegalArgumentException("No published ES row");
    }

    private static BigDecimal obligation(BigDecimal w, boolean useEstimated, boolean roundFederal) {
        var taxable = decimal("86197").add(w).subtract(decimal("16100"));
        var federal = decimal("1240").add(decimal("38000").multiply(decimal(".12")))
                .add(taxable.subtract(decimal("50400")).multiply(decimal(".22")));
        var n = decimal("77727").add(w);
        return (roundFederal ? cents(federal) : federal)
                .add(cents(useEstimated ? estimated(n) : statute(n))).subtract(decimal("29"));
    }

    private static boolean sufficient(BigDecimal w, boolean useEstimated) {
        return w.compareTo(obligation(w, useEstimated, false)) >= 0;
    }

    private static BigDecimal over(BigDecimal n, String floor, String rate) {
        return n.subtract(decimal(floor)).max(ZERO).multiply(decimal(rate));
    }

    private static BigDecimal cents(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
