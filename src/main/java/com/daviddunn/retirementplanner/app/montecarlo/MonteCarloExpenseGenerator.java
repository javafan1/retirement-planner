package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.ProjectionExpensePath;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExpensePath.Occurrence;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure, independently keyed annual expense-level shocks. No production financial caller yet.
 * Frozen V1 protocol (big-endian integers, UTF-8 strings preceded by int32 byte length):
 * definition = ASCII RPEX || int32(1) || strings(type, normalized amount, start ISO date
 * or empty, end ISO date or empty, escalation enum, normalized volatility).
 * correspondence = SHA-256(definition), without truncation.
 * key = SHA-256(ASCII RPMC || int32(1) || int64(seed) || int32(scenario) || int32(6)
 *       || int32(1) || int32(32) || correspondence || int32(duplicate ordinal)).
 * year block = SHA-256(key || int32(absolute calendar year)).
 * The first two int64 words supply high-52-bit midpoint uniforms and cosine-only
 * StrictMath Box-Muller, exactly as in the existing inflation normal convention.
 * M = StrictMath.exp(-log1p(v*v)/2 + sqrt(log1p(v*v))*Z); BigDecimal.valueOf(M)
 * is the only floating-point boundary. No tail clipping, cached sine or compounded shocks.
 *
 * Instances are immutable and thread-safe; each generate call owns its digest and buffers.
 * Space is O(E*Y) for ONE scenario, with hard bounds; no scenario history or memoization.
 */
public final class MonteCarloExpenseGenerator {

    public static final String MODEL_VERSION = "INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1";
    public static final int CORRESPONDENCE_VERSION = 1;

    private record Prepared(Occurrence occurrence, byte[] definitionKey, double meanLog, double sigmaLog) { }

    public record PairedPaths(Optional<ProjectionExpensePath> first, Optional<ProjectionExpensePath> second) {
        public PairedPaths {
            Objects.requireNonNull(first);
            Objects.requireNonNull(second);
        }
    }

    private final List<Prepared> enabled;
    private final List<Optional<Occurrence>> occurrencesInInputOrder;

    public MonteCarloExpenseGenerator(List<ExpenseSamplingSpecification> specifications) {
        Objects.requireNonNull(specifications, "Expense specifications are required.");
        if (specifications.size() > ProjectionExpensePath.MAX_OCCURRENCES) {
            throw new IllegalArgumentException("Too many expense sampling specifications.");
        }
        var prepared = new ArrayList<Prepared>();
        var bindings = new ArrayList<Optional<Occurrence>>();
        var counts = new HashMap<String, Integer>();
        for (var specification : specifications) {
            Objects.requireNonNull(specification, "Expense specification is required.");
            if (!specification.enabled()) {
                bindings.add(Optional.empty());
                continue;
            }
            byte[] definitionKey = sha256().digest(definitionBytes(specification));
            String key = HexFormat.of().formatHex(definitionKey);
            int ordinal = counts.getOrDefault(key, 0);
            counts.put(key, ordinal + 1);
            var occurrence = new Occurrence(key, ordinal);
            double v = specification.annualVolatility().doubleValue();
            double varianceLog = StrictMath.log1p(v * v);
            prepared.add(new Prepared(occurrence, definitionKey, -varianceLog / 2, StrictMath.sqrt(varianceLog)));
            bindings.add(Optional.of(occurrence));
        }
        enabled = List.copyOf(prepared);
        occurrencesInInputOrder = List.copyOf(bindings);
    }

    /** Includes empty bindings for disabled entries; never binds by description or plan identity. */
    public List<Optional<Occurrence>> occurrencesInInputOrder() {
        return occurrencesInInputOrder;
    }

    public Optional<ProjectionExpensePath> generate(long masterSeed, int scenarioIndex, int firstYear, int lastYear) {
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        ProjectionExpensePath.validateBounds(firstYear, lastYear, enabled.size());
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        var digest = sha256();
        var header = ByteBuffer.allocate(68).order(ByteOrder.BIG_ENDIAN);
        var yearInput = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN);
        var result = new LinkedHashMap<Occurrence, List<BigDecimal>>();
        for (var expense : enabled) {
            header.clear();
            header.put(new byte[] {'R', 'P', 'M', 'C'}).putInt(1).putLong(masterSeed).putInt(scenarioIndex)
                    .putInt(MonteCarloRandomStreams.RECURRING_EXPENSE)
                    .putInt(MonteCarloRandomStreams.RECURRING_EXPENSE_V1)
                    .putInt(32).put(expense.definitionKey()).putInt(expense.occurrence().duplicateIndex());
            byte[] key = digest.digest(header.array());
            yearInput.clear();
            yearInput.put(key);
            var values = new ArrayList<BigDecimal>(lastYear - firstYear + 1);
            for (int year = firstYear; year <= lastYear; year++) {
                yearInput.putInt(32, year);
                byte[] block = digest.digest(yearInput.array());
                double normal = normalFromBlock(block);
                values.add(convert(expense.meanLog(), expense.sigmaLog(), normal));
            }
            result.put(expense.occurrence(), values);
        }
        return Optional.of(new ProjectionExpensePath(firstYear, lastYear, result));
    }

    /** Same protocol on both sides: matching occurrences share draws, regardless of horizon or order. */
    public PairedPaths generatePaired(MonteCarloExpenseGenerator other, long masterSeed, int scenarioIndex,
            int firstYear, int lastYear, int otherFirstYear, int otherLastYear) {
        Objects.requireNonNull(other, "Other expense generator is required.");
        return new PairedPaths(generate(masterSeed, scenarioIndex, firstYear, lastYear),
                other.generate(masterSeed, scenarioIndex, otherFirstYear, otherLastYear));
    }

    // Package-visible numerical protocol boundaries permit independent known-answer tests.
    static double normalFromBlock(byte[] block) {
        if (Objects.requireNonNull(block).length != 32) {
            throw new IllegalArgumentException("Expense sampling requires a complete SHA-256 block.");
        }
        var words = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN);
        double u = ((words.getLong() >>> 12) + 0.5) * 0x1.0p-52;
        double v = ((words.getLong() >>> 12) + 0.5) * 0x1.0p-52;
        return StrictMath.sqrt(-2 * StrictMath.log(u)) * StrictMath.cos(2 * StrictMath.PI * v);
    }

    static BigDecimal transform(BigDecimal volatility, double normal) {
        var specification = new ExpenseSamplingSpecification(
                com.daviddunn.retirementplanner.domain.financial.ExpenseType.RECURRING, BigDecimal.ONE,
                null, null, com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL, volatility);
        double v = specification.annualVolatility().doubleValue();
        double varianceLog = StrictMath.log1p(v * v);
        return convert(-varianceLog / 2, StrictMath.sqrt(varianceLog), normal);
    }

    private static BigDecimal convert(double meanLog, double sigmaLog, double normal) {
        if (!Double.isFinite(normal)) {
            throw new IllegalArgumentException("Non-finite expense normal draw.");
        }
        double multiplier = StrictMath.exp(meanLog + sigmaLog * normal);
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("Non-finite or nonpositive generated expense multiplier.");
        }
        return BigDecimal.valueOf(multiplier);
    }

    static byte[] definitionBytes(ExpenseSamplingSpecification specification) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.write(new byte[] {'R', 'P', 'E', 'X'});
            output.writeInt(CORRESPONDENCE_VERSION);
            for (String field : List.of(specification.expenseType().name(), specification.annualAmount().toString(),
                    specification.startDate() == null ? "" : specification.startDate().toString(),
                    specification.endDate() == null ? "" : specification.endDate().toString(),
                    specification.escalationPolicy().name(), specification.annualVolatility().toString())) {
                byte[] text = field.getBytes(StandardCharsets.UTF_8);
                output.writeInt(text.length);
                output.write(text);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode in-memory expense definition.", exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required.", exception);
        }
    }
}
