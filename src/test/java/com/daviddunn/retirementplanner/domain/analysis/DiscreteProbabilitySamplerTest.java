package com.daviddunn.retirementplanner.domain.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscreteProbabilitySamplerTest {

    @Test
    void onePointAndZeroEntriesDrawNothing() {
        var random = (DiscreteProbabilitySampler.RandomBytes) bytes -> fail("Unexpected draw");
        assertEquals(0, sampler("1.000").sample(random));
        assertEquals(1, sampler("0", "1", "0.000").sample(random));
    }

    @Test
    void twoPointFirstLastAndExactBoundary() {
        var sampler = sampler("0.3", "0.7");
        assertEquals(0, sampler.sample(bytes(0)));
        assertEquals(0, sampler.sample(bytes(2)));
        assertEquals(1, sampler.sample(bytes(3)));
        assertEquals(1, sampler.sample(bytes(9)));
    }

    @Test
    void mixedScalesZeroMassesAndStableUnsortedCallerOrder() {
        var sampler = sampler("0", "0.50", "0.000", "0.125", "0.3750", "0");
        assertEquals(1, sampler.sample(bytes(0, 0)));
        assertEquals(1, sampler.sample(bytes(1, 243))); // 499
        assertEquals(3, sampler.sample(bytes(1, 244))); // 500
        assertEquals(3, sampler.sample(bytes(2, 112))); // 624
        assertEquals(4, sampler.sample(bytes(2, 113))); // 625
        assertEquals(4, sampler.sample(bytes(3, 231))); // 999
    }

    @Test
    void everyIntegerHasExactlyItsRepresentedMassAndZerosAreNeverSelected() {
        var sampler = sampler("0", "0.2", "0", "0.5", "0.3", "0");
        int[] counts = new int[6];
        for (int value = 0; value < 10; value++) {
            counts[sampler.sample(bytes(value))]++;
        }
        assertArrayEquals(new int[] {0, 2, 0, 5, 3, 0}, counts);
    }

    @Test
    void malformedTotalsAndNegativeValuesAreRejectedWithoutNormalization() {
        assertThrows(IllegalArgumentException.class, () -> sampler("0.4", "0.5"));
        assertThrows(IllegalArgumentException.class, () -> sampler("0.5", "0.6"));
        assertThrows(IllegalArgumentException.class, () -> sampler("-0.1", "1.1"));
        assertThrows(IllegalArgumentException.class, () -> sampler("0.999999999999999999999999999999999999"));
        assertThrows(IllegalArgumentException.class, () -> sampler("1.000000000000000000000000000000000001"));
        assertThrows(IllegalArgumentException.class, () -> new DiscreteProbabilitySampler(List.of()));
        assertThrows(NullPointerException.class, () -> new DiscreteProbabilitySampler(null));
        assertThrows(NullPointerException.class, () -> new DiscreteProbabilitySampler(Arrays.asList(BigDecimal.ONE, null)));
    }

    @Test
    void suppliedRandomValuesRepeatAndCallerMutationCannotChangePreparedState() {
        var probabilities = new ArrayList<>(List.of(new BigDecimal("0.3"), new BigDecimal("0.7")));
        var sampler = new DiscreteProbabilitySampler(probabilities);
        probabilities.clear();
        var first = bytes(0, 3, 9, 2);
        var second = bytes(0, 3, 9, 2);
        for (int expected : new int[] {0, 1, 1, 0}) {
            assertEquals(expected, sampler.sample(first));
            assertEquals(expected, sampler.sample(second));
        }
    }

    @Test
    void extremelySmallMassRemainsSelectableWithoutFloatingPoint() {
        BigDecimal tiny = new BigDecimal("1E-1000");
        var sampler = new DiscreteProbabilitySampler(List.of(tiny, BigDecimal.ONE.subtract(tiny)));
        assertEquals(0, sampler.sample(destination -> Arrays.fill(destination, (byte) 0)));
        assertEquals(1, sampler.sample(destination -> {
            Arrays.fill(destination, (byte) 0);
            destination[destination.length - 1] = 1;
        }));
    }

    @Test
    void rejectionConsumesWholeAttemptsAndMasksHighBits() {
        var source = bytes(0xff, 0xfa, 0xf9, 0x03);
        // Mask to four bits: 15 and 10 rejected, 9 accepted; next byte remains 3.
        assertEquals(BigInteger.valueOf(9), DiscreteProbabilitySampler.nextBelow(BigInteger.TEN, source));
        assertEquals(BigInteger.valueOf(3), DiscreteProbabilitySampler.nextBelow(BigInteger.TEN, source));
        assertEquals(BigInteger.valueOf(256), DiscreteProbabilitySampler.nextBelow(
                BigInteger.valueOf(257), bytes(0xff, 0xff, 0x81, 0x00)));
    }

    @Test
    void unsignedBigEndianPowerOfTwoAndInvalidBounds() {
        assertEquals(BigInteger.valueOf(255), DiscreteProbabilitySampler.nextBelow(
                BigInteger.valueOf(256), bytes(255)));
        assertEquals(BigInteger.valueOf(258), DiscreteProbabilitySampler.nextBelow(
                BigInteger.valueOf(65536), bytes(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> DiscreteProbabilitySampler.nextBelow(BigInteger.ZERO, bytes()));
        assertThrows(IllegalArgumentException.class, () -> DiscreteProbabilitySampler.nextBelow(BigInteger.ONE.negate(), bytes()));
        assertThrows(NullPointerException.class, () -> DiscreteProbabilitySampler.nextBelow(BigInteger.ONE, null));
    }

    private static DiscreteProbabilitySampler sampler(String... probabilities) {
        return new DiscreteProbabilitySampler(Arrays.stream(probabilities).map(BigDecimal::new).toList());
    }

    private static DiscreteProbabilitySampler.RandomBytes bytes(int... values) {
        return new DiscreteProbabilitySampler.RandomBytes() {
            private int position;

            @Override
            public void nextBytes(byte[] destination) {
                assertTrue(position + destination.length <= values.length, "Unexpected extra random bytes");
                for (int index = 0; index < destination.length; index++) {
                    destination[index] = (byte) values[position++];
                }
            }
        };
    }
}
