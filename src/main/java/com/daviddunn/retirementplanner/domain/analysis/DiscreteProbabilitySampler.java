package com.daviddunn.retirementplanner.domain.analysis;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable prepared distribution returning indices in the caller's original order.
 * Decimal masses are exact; no normalization or floating-point conversion occurs.
 */
public final class DiscreteProbabilitySampler {

    /** Fills every requested byte with uniform random bits; state belongs to the caller. */
    @FunctionalInterface
    public interface RandomBytes {
        void nextBytes(byte[] destination);
    }

    private final List<BigInteger> cumulativeWeights;
    private final BigInteger totalWeight;

    public DiscreteProbabilitySampler(List<BigDecimal> probabilities) {
        Objects.requireNonNull(probabilities, "Probabilities are required.");
        if (probabilities.isEmpty()) {
            throw new IllegalArgumentException("A distribution must contain at least one entry.");
        }
        List<BigDecimal> masses = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int scale = 0;
        for (BigDecimal probability : probabilities) {
            Objects.requireNonNull(probability, "Probability is required.");
            if (probability.signum() < 0) {
                throw new IllegalArgumentException("Probabilities cannot be negative.");
            }
            BigDecimal mass = probability.stripTrailingZeros();
            masses.add(mass);
            total = total.add(mass);
            scale = Math.max(scale, mass.scale());
        }
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Probabilities must sum exactly to 1.");
        }
        totalWeight = BigInteger.TEN.pow(scale);
        List<BigInteger> cumulative = new ArrayList<>();
        BigInteger sum = BigInteger.ZERO;
        for (BigDecimal mass : masses) {
            sum = sum.add(mass.setScale(scale).unscaledValue());
            cumulative.add(sum);
        }
        if (!sum.equals(totalWeight)) {
            throw new IllegalArgumentException("Integer probability weights must sum exactly to 10^scale.");
        }
        cumulativeWeights = List.copyOf(cumulative);
    }

    public int sample(RandomBytes random) {
        BigInteger value = nextBelow(totalWeight, random);
        int low = 0;
        int high = cumulativeWeights.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (cumulativeWeights.get(middle).compareTo(value) <= 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /**
     * Frozen rejection protocol: b = bitLength(bound - 1), read ceil(b/8) fresh
     * bytes per attempt, clear unused HIGH bits of the FIRST byte, interpret as
     * unsigned big-endian, and reject values >= bound. Rejected attempts are
     * consumed in full; no modulo reduction or bit recycling. Bound 1 draws nothing.
     */
    public static BigInteger nextBelow(BigInteger bound, RandomBytes random) {
        Objects.requireNonNull(bound, "Bound is required.");
        Objects.requireNonNull(random, "Random bytes are required.");
        if (bound.signum() <= 0) {
            throw new IllegalArgumentException("Bound must be positive.");
        }
        int bits = bound.subtract(BigInteger.ONE).bitLength();
        if (bits == 0) {
            return BigInteger.ZERO;
        }
        byte[] bytes = new byte[(bits - 1) / 8 + 1];
        int mask = 0xff >>> ((8 - bits % 8) % 8);
        BigInteger value;
        do {
            random.nextBytes(bytes);
            bytes[0] = (byte) (bytes[0] & mask);
            value = new BigInteger(1, bytes);
        } while (value.compareTo(bound) >= 0);
        return value;
    }
}
