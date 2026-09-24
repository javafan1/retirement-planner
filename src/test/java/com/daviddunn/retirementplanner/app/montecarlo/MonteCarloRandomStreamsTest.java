package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler.RandomBytes;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloRandomStreamsTest {

    @Test
    void frozenTwoBlockKnownAnswersFromIndependentDotNetSha256Implementation() {
        vector(417, 0, 1, 1,
                "49bf5432e524b7fa14b606721b9bf0bf04d8d158673139afdced0762c72167ff"
                        + "38ebcfeac0ce94c68d916cec5638a652f0f4275e680414bdbdaebc7a194e7a85");
        vector(417, 1, 1, 1,
                "5e7e0e86981af5ee89dd406b91b508f0a21db92310fee8f63b8bd12d184d47c3e"
                        + "fa15c2e876a6126ff363c1530873af6fbf5cd573a2199639ef2b39c69c1b990");
        vector(417, 224, 1, 1,
                "8cbf793ec258567f4c03cbe41a7a7c58bdf7a14ac413d3bd1ae91056de3262aaa"
                        + "857e95ef3f669910335ee5b6f93214efd15ee1b5b87fc24b37e9bab73c9dfbf");
        vector(-417, 224, 1, 1,
                "94574d6dba062cba11e3eef3e338d48f1a20ecefb6d24084c729bd23bd4ebc987"
                        + "589032704300bc3b85caa1b51d8fc7205409ae7d9c53609f05b477fe41f90dd");
        // 101 is a test-only dimension; no reserved future model is consumed.
        vector(417, 0, 101, 1,
                "46e3e78748f51ea41b4087867a603f439309e6e422cd98b82baf6c610d2b281369"
                        + "be9afc3a2527ced2f3277a3cf2f78b2a613c2ab8265eed9f5354502a3f93c9");
        vector(417, 0, 1, 2,
                "ca512bc4222e43fe6ed758e2a78cdbc426dc7191e4bb412531b3d9c26b11ae579"
                        + "7dd4b68d316af2ee6a0f0e8ab9cebe5ed31fcfd785645d2a21bff39e4842dd8");
    }

    @Test
    void repeatedConstructionAndIndependentRegenerationIgnoreOtherDrawsAndOrder() {
        byte[] expected = read(stream(224, 1), 129);
        read(stream(0, 1), 10000);
        var otherDimension = stream(224, 101);
        read(otherDimension, 12345);
        read(stream(1, 1), 17);
        assertArrayEquals(expected, read(stream(224, 1), 129));
        var uninterrupted = stream(224, 1);
        byte[] prefix = read(uninterrupted, 7);
        read(otherDimension, 1024);
        byte[] suffix = read(uninterrupted, 122);
        assertArrayEquals(java.util.Arrays.copyOfRange(expected, 0, 7), prefix);
        assertArrayEquals(java.util.Arrays.copyOfRange(expected, 7, 129), suffix);
    }

    @Test
    void chunkingAcrossDigestBoundariesAndEmptyRequestsPreserveSequence() {
        var stream = stream(0, 1);
        byte[] actual = new byte[97];
        int offset = 0;
        for (int size : new int[] {0, 1, 30, 0, 2, 32, 32}) {
            byte[] part = read(stream, size);
            System.arraycopy(part, 0, actual, offset, size);
            offset += size;
        }
        assertArrayEquals(read(stream(0, 1), 97), actual);
    }

    @Test
    void scenarioDimensionAndVersionSeparateStreams() {
        String baseline = HexFormat.of().formatHex(read(stream(0, 1), 64));
        assertNotEquals(baseline, HexFormat.of().formatHex(read(stream(1, 1), 64)));
        assertNotEquals(baseline, HexFormat.of().formatHex(read(stream(0, 101), 64)));
        assertNotEquals(baseline, HexFormat.of().formatHex(read(MonteCarloRandomStreams.create(417, 0, 1, 2), 64)));
    }

    @Test
    void identifiersAreExplicitAndInvalidCoordinatesFail() {
        assertEquals(1, MonteCarloRandomStreams.HOUSEHOLD_MORTALITY);
        assertEquals(1, MonteCarloRandomStreams.HOUSEHOLD_MORTALITY_V1);
        assertEquals(2, MonteCarloRandomStreams.PRIMARY_MORTALITY);
        assertEquals(3, MonteCarloRandomStreams.SPOUSE_MORTALITY);
        assertEquals(4, MonteCarloRandomStreams.GENERAL_INFLATION);
        assertEquals(5, MonteCarloRandomStreams.HEALTHCARE_INFLATION);
        assertThrows(IllegalArgumentException.class, () -> MonteCarloRandomStreams.create(417, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloRandomStreams.create(417, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> MonteCarloRandomStreams.create(417, 0, 1, 0));
        assertThrows(NullPointerException.class, () -> stream(0, 1).nextBytes(null));
        assertArrayEquals(read(MonteCarloRandomStreams.create(Long.MIN_VALUE, Integer.MAX_VALUE, 1, 1), 64),
                read(MonteCarloRandomStreams.create(Long.MIN_VALUE, Integer.MAX_VALUE, 1, 1), 64));
    }

    private static RandomBytes stream(int index, int dimension) {
        return MonteCarloRandomStreams.create(417, index, dimension, 1);
    }

    private static byte[] read(RandomBytes stream, int count) {
        byte[] bytes = new byte[count];
        stream.nextBytes(bytes);
        return bytes;
    }

    private static void vector(long seed, int index, int dimension, int version, String expected) {
        assertEquals(expected, HexFormat.of().formatHex(read(
                MonteCarloRandomStreams.create(seed, index, dimension, version), 64)));
    }
}
