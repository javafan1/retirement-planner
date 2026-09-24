package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler.RandomBytes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Independent, run-local streams for NEW dimensions only. MARKET_RETURNS_V1 stays
 * in MonteCarloScenarioGenerator with its original java.util.Random seed formula.
 *
 * Frozen protocol 1 (all integers big-endian, signed two's-complement):
 * key = SHA-256(ASCII "RPMC" || int32(1) || int64(masterSeed) ||
 *              int32(scenarioIndex) || int32(dimensionId) || int32(dimensionVersion)).
 * block(c) = SHA-256(key || int64(c)), c = 0 through Long.MAX_VALUE.
 * Bytes are consumed in digest order, retaining unused bytes across requests.
 * Counter exhaustion fails rather than wrapping. Empty requests consume nothing.
 * Bounded draws use DiscreteProbabilitySampler.nextBelow's frozen rejection protocol.
 * Each create call owns its state; streams are sequential and not thread-safe.
 */
public final class MonteCarloRandomStreams {

    public static final int HOUSEHOLD_MORTALITY = 1;
    public static final int HOUSEHOLD_MORTALITY_V1 = 1;

    // Reserved identifiers only: no sampling or stochastic models implemented here.
    public static final int PRIMARY_MORTALITY = 2;
    public static final int SPOUSE_MORTALITY = 3;
    public static final int GENERAL_INFLATION = 4;
    public static final int HEALTHCARE_INFLATION = 5;

    private MonteCarloRandomStreams() {
    }

    public static RandomBytes create(long masterSeed, int scenarioIndex,
            int dimensionId, int dimensionVersion) {
        if (scenarioIndex < 0 || dimensionId <= 0 || dimensionVersion <= 0) {
            throw new IllegalArgumentException(
                    "Scenario index must be nonnegative; dimension ID and version must be positive.");
        }
        byte[] header = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
                .put(new byte[] {'R', 'P', 'M', 'C'})
                .putInt(1)
                .putLong(masterSeed)
                .putInt(scenarioIndex)
                .putInt(dimensionId)
                .putInt(dimensionVersion)
                .array();
        return new Stream(sha256().digest(header));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required.", exception);
        }
    }

    private static final class Stream implements RandomBytes {
        private final byte[] key;
        private final MessageDigest digest = sha256();
        private byte[] block = new byte[0];
        private int offset;
        private long counter;
        private boolean exhausted;

        private Stream(byte[] key) {
            this.key = key;
        }

        @Override
        public void nextBytes(byte[] destination) {
            Objects.requireNonNull(destination, "Destination is required.");
            int written = 0;
            while (written < destination.length) {
                if (offset == block.length) {
                    if (exhausted) {
                        throw new IllegalStateException("Random stream counter exhausted.");
                    }
                    digest.update(key);
                    block = digest.digest(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN)
                            .putLong(counter).array());
                    offset = 0;
                    if (counter == Long.MAX_VALUE) {
                        exhausted = true;
                    } else {
                        counter++;
                    }
                }
                int count = Math.min(destination.length - written, block.length - offset);
                System.arraycopy(block, offset, destination, written, count);
                offset += count;
                written += count;
            }
        }
    }
}
