package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExpensePath;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExpensePath.Occurrence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloExpenseGeneratorTest {

    static ExpenseSamplingSpecification specification(String amount, String volatility) {
        return new ExpenseSamplingSpecification(ExpenseType.RECURRING, new BigDecimal(amount),
                LocalDate.of(2027, 1, 1), null, GrowthCategory.GENERAL,
                volatility == null ? null : new BigDecimal(volatility));
    }

    private static MonteCarloExpenseGenerator generator(ExpenseSamplingSpecification... specifications) {
        return new MonteCarloExpenseGenerator(List.of(specifications));
    }

    private static Occurrence occurrence(MonteCarloExpenseGenerator generator, int index) {
        return generator.occurrencesInInputOrder().get(index).orElseThrow();
    }

    @Test
    void frozenDefinitionEncodingAndFullDigestMatchIndependentDotNetReference() throws Exception {
        var specification = specification("1000.0000", "0.2500");
        String encoded = HexFormat.of().formatHex(MonteCarloExpenseGenerator.definitionBytes(specification));
        assertEquals("525045580000000100000009524543555252494e470000000431452b330000000a323032372d30312d3031000000000000000747454e4552414c00000004302e3235", encoded);
        String key = "ae24c2ab949dbd6df768173d5b096a41cb009beefbdb9f6b8a13eeaaf5b88935";
        assertEquals(key, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(MonteCarloExpenseGenerator.definitionBytes(specification))));
        assertEquals(new Occurrence(key, 0), occurrence(generator(specification), 0));
        assertEquals(6, MonteCarloRandomStreams.RECURRING_EXPENSE);
        assertEquals(1, MonteCarloRandomStreams.RECURRING_EXPENSE_V1);
        assertEquals(1, MonteCarloExpenseGenerator.CORRESPONDENCE_VERSION);
    }

    @Test
    void seededUniformNormalAndMultiplierVectorsFreezeProtocol() {
        // Independently encoded/hashed with .NET SHA256 and Math functions, not the sampler.
        var vectors = List.of(
                new Vector(417, 0, 0, 2027, "1bd8194881c7030b5766f08bf3d4adbfbec8aa125a34977e55988c144c0e2400", -1.1444514649585678, 0.7319083882747449),
                new Vector(417, 0, 0, 2056, "8532a5ad1e95acbddaeda475c356fcf28f34749c197043f5e79463344f97a856", 0.7016857549672143, 1.1531037625422851),
                new Vector(417, 0, 1, 2027, "66fce637a5beb4e4652ea774241e51223d58fd60d14f952e73641e92a3e25277", -1.0675711873540545, 0.7458950207931494),
                new Vector(417, 7, 0, 2027, "b714870c61e2f126f72b84f0e094de29a376515af3d78f14a3816d4e4bc9317a", 0.79968999686143194, 1.181267366556483),
                new Vector(-417, 0, 0, 2027, "4b776189dc8aad0daaafa6a425735027e4ee21719b4751bebb27eb7c5f2fa82b", -0.78085509890904348, 0.80045498474062315),
                new Vector(-417, 7, 1, 2056, "b9ee48121a60be4e16b77843bcb4c1d93a5ba1de9bdda19f5efc61bbf37b1086", 0.67863363831442891, 1.1465773909992676));
        var sampler = generator(specification("1000", "0.25"), specification("1000.00", "0.250"));
        for (var vector : vectors) {
            double normal = MonteCarloExpenseGenerator.normalFromBlock(HexFormat.of().parseHex(vector.block()));
            assertEquals(vector.normal(), normal, 2e-15);
            var path = sampler.generate(vector.seed(), vector.scenario(), 2027, 2056).orElseThrow();
            assertEquals(vector.multiplier(), path.multiplier(occurrence(sampler, vector.duplicate()), vector.year()).doubleValue(), 4e-16);
        }
        // Exact Java StrictMath / BigDecimal.valueOf reference, separate from the independent tolerance checks.
        assertEquals(new BigDecimal("0.7319083882747449"), sampler.generate(417, 0, 2027, 2027).orElseThrow()
                .multiplier(occurrence(sampler, 0), 2027));
        // The documented first uniform is independently decoded, fixing high-bit midpoint semantics.
        long firstWord = ByteBuffer.wrap(HexFormat.of().parseHex(vectors.getFirst().block())).getLong();
        assertEquals(0.10876615543545765, ((firstWord >>> 12) + 0.5) * 0x1.0p-52, 0);
    }

    private record Vector(long seed, int scenario, int duplicate, int year, String block, double normal, double multiplier) { }

    @Test
    void exactStrictMathAndBigDecimalConversionVectorsRemainFrozen() {
        // New sampling protocol fixtures, cross-checked with independent .NET known answers above.
        // Differences at the last bit from .NET Math are expected; this fixes Java StrictMath output and scale.
        var expected = List.of("0.7319083882747449", "1.153103762542285", "0.7458950207931494", "1.188382935464238",
                "1.181267366556483", "0.7299399035940793", "0.7900043297929266", "0.5818707134078651",
                "0.8004549847406232", "0.9211853954254114", "0.9137002315713875", "1.0711113206514076",
                "1.1379372178769283", "0.9995487411742241", "0.7753394310880257", "1.1465773909992676");
        var sampler = generator(specification("1000", ".25"), specification("1000", ".25"));
        int index = 0;
        for (long seed : new long[] {417, -417}) {
            for (int scenario : new int[] {0, 7}) {
                var path = sampler.generate(seed, scenario, 2027, 2056).orElseThrow();
                for (int duplicate : new int[] {0, 1}) {
                    for (int year : new int[] {2027, 2056}) {
                        assertEquals(new BigDecimal(expected.get(index++)), path.multiplier(occurrence(sampler, duplicate), year));
                    }
                }
            }
        }
    }

    @Test
    void independentlyCalculatedTransformsPreserveMeanOneModelAndDoNotClipTails() {
        // At Z=0, M=1/sqrt(1+v^2); exact algebra, not an implementation-derived expectation.
        assertEquals(0.7071067811865475244,
                MonteCarloExpenseGenerator.transform(BigDecimal.ONE, 0).doubleValue(), 2e-16);
        assertEquals(0.8944271909999158786,
                MonteCarloExpenseGenerator.transform(new BigDecimal("0.5"), 0).doubleValue(), 2e-16);
        assertEquals(0, BigDecimal.ONE.compareTo(MonteCarloExpenseGenerator.transform(BigDecimal.ZERO, 8)));
        assertTrue(MonteCarloExpenseGenerator.transform(BigDecimal.ONE, 8).compareTo(new BigDecimal("500")) > 0);
        assertTrue(MonteCarloExpenseGenerator.transform(BigDecimal.ONE, -8).signum() > 0);
        double extreme = MonteCarloExpenseGenerator.normalFromBlock(new byte[32]);
        assertEquals(StrictMath.sqrt(106 * StrictMath.log(2)), extreme, 2e-15);
        assertTrue(MonteCarloExpenseGenerator.transform(BigDecimal.ONE, extreme).compareTo(new BigDecimal("800")) > 0);
        byte[] upper = new byte[32]; Arrays.fill(upper, (byte) 0xff);
        assertTrue(Double.isFinite(MonteCarloExpenseGenerator.normalFromBlock(upper)));
    }

    @Test
    void validatesVolatilityAndNumericalBoundaryWithoutChangingLegacyDisabledAmounts() {
        assertFalse(specification("-100", null).enabled());
        assertFalse(specification("100", "0.000").enabled());
        assertTrue(specification("0", "1").enabled());
        assertThrows(IllegalArgumentException.class, () -> specification("100", "-0.01"));
        assertThrows(IllegalArgumentException.class, () -> specification("100", "1.00000001"));
        assertThrows(IllegalArgumentException.class, () -> specification("-1", "0.1"));
        assertThrows(IllegalArgumentException.class, () -> new ExpenseSamplingSpecification(ExpenseType.ONE_TIME,
                BigDecimal.ONE, null, null, GrowthCategory.GENERAL, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ExpenseSamplingSpecification(ExpenseType.RECURRING,
                BigDecimal.ONE, LocalDate.of(2030, 1, 1), LocalDate.of(2029, 1, 1), GrowthCategory.NONE, null));
        assertThrows(IllegalArgumentException.class, () -> specification("1".repeat(4097), "0.1"));
        for (double normal : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1000, -1000}) {
            assertThrows(IllegalArgumentException.class, () -> MonteCarloExpenseGenerator.transform(BigDecimal.ONE, normal));
        }
        // BigDecimal cannot represent NaN or Infinity at the typed input boundary.
        assertThrows(NumberFormatException.class, () -> specification("100", "NaN"));
        assertThrows(NumberFormatException.class, () -> specification("100", "Infinity"));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloExpenseGenerator.normalFromBlock(new byte[16]));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.25", "1"})
    void fixedSeedMeanVariancePositivityAndTailAreWithinSixStandardErrors(String volatility) {
        int samples = 100_000;
        double v = Double.parseDouble(volatility);
        var sampler = generator(specification("1000", volatility));
        var occurrence = occurrence(sampler, 0);
        double mean = 0;
        double squared = 0;
        double maximum = 0;
        for (int scenario = 0; scenario < samples; scenario++) {
            var multiplier = sampler.generate(417, scenario, 2027, 2027).orElseThrow().multiplier(occurrence, 2027);
            assertTrue(multiplier.signum() > 0);
            double value = multiplier.doubleValue();
            double delta = value - mean;
            mean += delta / (scenario + 1);
            squared += delta * (value - mean);
            maximum = Math.max(maximum, value);
        }
        double variance = squared / (samples - 1);
        // E[M^k]=(1+v^2)^(k(k-1)/2), hence fourth central moment below.
        double a = 1 + v * v;
        double fourthCentral = Math.pow(a, 6) - 4 * Math.pow(a, 3) + 6 * a - 3;
        assertEquals(1, mean, 6 * v / Math.sqrt(samples));
        assertEquals(v * v, variance, 6 * Math.sqrt((fourthCentral - Math.pow(v, 4)) / samples));
        if (v == 1) assertTrue(maximum > 10);
        System.out.printf(java.util.Locale.ROOT,
                "EXPENSE_MOMENTS v=%s samples=%d mean=%.12f variance=%.12f maximum=%.12f%n",
                volatility, samples, mean, variance, maximum);
    }

    @Test
    void missingAndZeroVolatilityOmitPathAndDoNotBindDisabledEntries() {
        var empty = generator();
        var disabled = generator(specification("1000.0000", null), specification("-500.00", "0"));
        assertTrue(empty.generate(417, 0, 2027, 2086).isEmpty());
        assertTrue(disabled.generate(417, 0, 2027, 2086).isEmpty());
        assertEquals(List.of(Optional.empty(), Optional.empty()), disabled.occurrencesInInputOrder());
        assertThrows(UnsupportedOperationException.class, () -> disabled.occurrencesInInputOrder().clear());
        assertThrows(IllegalArgumentException.class, () -> disabled.generate(1, -1, 2027, 2027));
    }

    @Test
    void detachedRequestIgnoresSubsequentListEditsAndDisabledEntriesDoNotConsumeOrdinals() {
        var x = specification("1000", ".25");
        var specifications = new ArrayList<>(List.of(x, specification("1000", "0"), x));
        var sampler = new MonteCarloExpenseGenerator(specifications);
        specifications.clear();
        assertEquals(0, occurrence(sampler, 0).duplicateIndex());
        assertEquals(Optional.empty(), sampler.occurrencesInInputOrder().get(1));
        assertEquals(1, occurrence(sampler, 2).duplicateIndex());
        assertEquals(generator(x, x).generate(417, 0, 2027, 2027).orElseThrow().annualMultipliers(),
                sampler.generate(417, 0, 2027, 2027).orElseThrow().annualMultipliers());
        assertThrows(IllegalArgumentException.class, () -> sampler.generate(417, 0, 2028, 2027));
        assertThrows(IllegalArgumentException.class, () -> sampler.generate(417, 0, 2027, 3027));
        var maximum = sampler.generate(Long.MIN_VALUE, Integer.MAX_VALUE, -999_999_999, -999_999_999).orElseThrow();
        assertTrue(maximum.multiplier(occurrence(sampler, 0), -999_999_999).signum() > 0);
    }

    @Test
    void matchingDefinitionsIgnoreDescriptionsOwnersAndNumericScaleButNotFinancialChanges() {
        var a = new Expense("Original", new BigDecimal("1000.0000"), GrowthCategory.GENERAL,
                LocalDate.of(2027, 1, 1), null, ExpenseType.RECURRING);
        var b = new Expense("Renamed", new BigDecimal("1E+3"), GrowthCategory.GENERAL,
                LocalDate.of(2027, 1, 1), null, ExpenseType.RECURRING);
        var original = ExpenseSamplingSpecification.from(a, new BigDecimal("0.2500"));
        assertEquals(original, ExpenseSamplingSpecification.from(b, new BigDecimal(".25")));
        var key = occurrence(generator(original), 0);
        for (var changed : List.of(specification("1001", ".25"), specification("1000", ".26"),
                new ExpenseSamplingSpecification(ExpenseType.RECURRING, new BigDecimal("1000"), null, null, GrowthCategory.GENERAL, new BigDecimal(".25")),
                new ExpenseSamplingSpecification(ExpenseType.RECURRING, new BigDecimal("1000"), a.getStartDate(), LocalDate.of(2040, 1, 1), GrowthCategory.GENERAL, new BigDecimal(".25")),
                new ExpenseSamplingSpecification(ExpenseType.RECURRING, new BigDecimal("1000"), a.getStartDate(), null, GrowthCategory.HEALTHCARE, new BigDecimal(".25")),
                new ExpenseSamplingSpecification(ExpenseType.RECURRING, new BigDecimal("1000"), a.getStartDate(), null, GrowthCategory.NONE, new BigDecimal(".25")))) {
            assertNotEquals(key, occurrence(generator(changed), 0));
        }
        assertEquals(new BigDecimal("1000.0000"), a.getAnnualAmount());
    }

    @Test
    void pairedDuplicatesMatchFirstSeenOccurrencesAndUnrelatedOrderDoesNotPerturbDraws() {
        var x = specification("1000", ".25");
        var y = specification("2000", ".50");
        var left = generator(x, y, x);
        var right = generator(y, x, specification("7777", "1"), x, x);
        var pair = left.generatePaired(right, 417, 7, 2027, 2056, 2025, 2086);
        var a = pair.first().orElseThrow();
        var b = pair.second().orElseThrow();
        assertEquals(occurrence(left, 0), occurrence(right, 1));
        assertEquals(occurrence(left, 2), occurrence(right, 3));
        assertNotEquals(occurrence(left, 0), occurrence(left, 2));
        for (int year = 2027; year <= 2056; year++) {
            assertEquals(a.multiplier(occurrence(left, 0), year), b.multiplier(occurrence(right, 1), year));
            assertEquals(a.multiplier(occurrence(left, 2), year), b.multiplier(occurrence(right, 3), year));
            assertEquals(a.multiplier(occurrence(left, 1), year), b.multiplier(occurrence(right, 0), year));
        }
        assertNotEquals(a.multiplier(occurrence(left, 0), 2027), a.multiplier(occurrence(left, 2), 2027));
        assertNotEquals(a.multiplier(occurrence(left, 0), 2027), b.multiplier(occurrence(right, 4), 2027));
        var identical = left.generatePaired(generator(x, y, x), 417, 7, 2027, 2056, 2027, 2056);
        assertEquals(identical.first().orElseThrow().annualMultipliers(), identical.second().orElseThrow().annualMultipliers());
    }

    @Test
    void scenarioYearHorizonAndExecutionOrderAreIndependentAndInstancesAreThreadSafe() throws Exception {
        var x = specification("1000", ".25");
        var sampler = generator(x);
        var occurrence = occurrence(sampler, 0);
        var expected = sampler.generate(-417, 7, 2027, 2056).orElseThrow();
        sampler.generate(5000, 88, 2027, 2086);
        assertEquals(expected.annualMultipliers(), generator(x).generate(-417, 7, 2027, 2056).orElseThrow().annualMultipliers());
        assertEquals(expected.multiplier(occurrence, 2035), sampler.generate(-417, 7, 2035, 2035).orElseThrow().multiplier(occurrence, 2035));
        assertNotEquals(expected.multiplier(occurrence, 2027), sampler.generate(-417, 8, 2027, 2027).orElseThrow().multiplier(occurrence, 2027));
        assertNotEquals(expected.multiplier(occurrence, 2027), expected.multiplier(occurrence, 2028));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Callable<Map<Occurrence, List<BigDecimal>>>>();
            for (int index = 0; index < 20; index++) {
                jobs.add(() -> sampler.generate(-417, 7, 2027, 2056).orElseThrow().annualMultipliers());
            }
            for (var future : pool.invokeAll(jobs)) assertEquals(expected.annualMultipliers(), future.get());
        }
    }

    @Test
    void independentDuplicateAndAnnualCoordinatesHaveSmallFixedSeedCorrelations() {
        var sampler = generator(specification("1000", "1"), specification("1000", "1"));
        int count = 20_000;
        double[] sums = new double[7];
        for (int scenario = 0; scenario < count; scenario++) {
            var path = sampler.generate(891, scenario, 2027, 2028).orElseThrow();
            double a = path.multiplier(occurrence(sampler, 0), 2027).doubleValue();
            double b = path.multiplier(occurrence(sampler, 1), 2027).doubleValue();
            double c = path.multiplier(occurrence(sampler, 0), 2028).doubleValue();
            sums[0] += a; sums[1] += b; sums[2] += c;
            sums[3] += a * b; sums[4] += a * c; sums[5] += a * a; sums[6] += b * b;
        }
        double duplicateCovariance = sums[3] / count - sums[0] * sums[1] / count / count;
        double annualCovariance = sums[4] / count - sums[0] * sums[2] / count / count;
        assertEquals(0, duplicateCovariance, .06);
        assertEquals(0, annualCovariance, .06);
    }

    @Test
    void pathIsDetachedImmutableBoundedAndRejectsMissingInvalidEntries() {
        var occurrence = new Occurrence("a".repeat(64), 0);
        var source = new ArrayList<>(List.of(new BigDecimal("1.234500"), new BigDecimal("0.5")));
        var map = new LinkedHashMap<Occurrence, List<BigDecimal>>(); map.put(occurrence, source);
        var path = new ProjectionExpensePath(2027, 2028, map);
        source.clear(); map.clear();
        assertEquals(new BigDecimal("1.234500"), path.multiplier(occurrence, 2027));
        assertSame(path.multiplier(occurrence, 2027), path.multiplier(occurrence, 2027));
        path.requireCoverage(occurrence, 2027, 2028);
        assertThrows(UnsupportedOperationException.class, () -> path.annualMultipliers().clear());
        assertThrows(UnsupportedOperationException.class, () -> path.annualMultipliers().get(occurrence).clear());
        assertThrows(IllegalArgumentException.class, () -> path.multiplier(occurrence, 2029));
        assertThrows(IllegalArgumentException.class, () -> path.multiplier(new Occurrence("b".repeat(64), 0), 2027));
        assertThrows(IllegalArgumentException.class, () -> path.requireCoverage(occurrence, 2028, 2027));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionExpensePath(2027, 2027, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionExpensePath(2027, 2028, Map.of(occurrence, List.of(BigDecimal.ONE))));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionExpensePath(2027, 2027, Map.of(occurrence, List.of(BigDecimal.ZERO))));
        assertThrows(IllegalArgumentException.class, () -> new Occurrence("truncated", 0));
        assertThrows(IllegalArgumentException.class, () -> new Occurrence("a".repeat(64), -1));
        assertThrows(IllegalArgumentException.class, () -> ProjectionExpensePath.validateBounds(2027, 3027, 1));
        assertThrows(IllegalArgumentException.class, () -> ProjectionExpensePath.validateBounds(2027, 2226, 1000));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloExpenseGenerator(
                java.util.Collections.nCopies(1001, specification("1000", ".25"))));
    }

    @Test
    void samplingHasNoFinancialPlanSerializationOrExistingRandomStreamEffects() throws Exception {
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var plan = MonteCarloFixtures.household();
        String before = json.writeValueAsString(plan);
        var original = json.valueToTree(new ProjectionEngine().project(plan).getYears());
        var sampler = generator(ExpenseSamplingSpecification.from(plan.getHousehold().getExpenses().getFirst(), BigDecimal.ONE));
        var oldStream = MonteCarloRandomStreams.create(417, 7, MonteCarloRandomStreams.GENERAL_INFLATION, 1);
        byte[] prefix = new byte[13]; oldStream.nextBytes(prefix);
        sampler.generate(417, 7, 2027, 2086).orElseThrow();
        byte[] suffix = new byte[51]; oldStream.nextBytes(suffix);
        byte[] reference = new byte[64];
        MonteCarloRandomStreams.create(417, 7, MonteCarloRandomStreams.GENERAL_INFLATION, 1).nextBytes(reference);
        assertArrayEquals(Arrays.copyOfRange(reference, 0, 13), prefix);
        assertArrayEquals(Arrays.copyOfRange(reference, 13, 64), suffix);
        assertEquals(before, json.writeValueAsString(plan));
        assertEquals(original, json.valueToTree(new ProjectionEngine().project(plan).getYears()));
    }
}
