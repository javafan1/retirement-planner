package com.daviddunn.retirementplanner.app.montecarlo;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** MC-only resource validation, including short exponent tokens that could expand during formatting. */
final class MonteCarloSnapshotNumbers {
    private static final Map<Class<?>, List<Method>> RECORDS = new ConcurrentHashMap<>();
    private MonteCarloSnapshotNumbers() { }
    static BigDecimal check(BigDecimal value) {
        if (value.precision() > 4096 || Math.abs((long) value.scale()) > 4096
                || (long) value.precision() - value.scale() > 4096)
            throw MonteCarloSnapshot.invalid("Monte Carlo decimal expansion exceeds the snapshot numeric budget.");
        return value;
    }
    static void validate(Object value) {
        if (value == null) return;
        if (value instanceof String text && text.length() > 256000)
            throw MonteCarloSnapshot.invalid("Monte Carlo string exceeds its snapshot scalar budget.");
        if (value instanceof BigDecimal decimal) { check(decimal); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(MonteCarloSnapshotNumbers::validate); return; }
        if (value instanceof Iterable<?> list) { list.forEach(MonteCarloSnapshotNumbers::validate); return; }
        // Objects are already created through explicit DTO/leaf types. Never instantiate a JSON-named class.
        if (value.getClass().isRecord()) {
            var methods = RECORDS.computeIfAbsent(value.getClass(), type -> Arrays.stream(type.getRecordComponents()).map(c -> c.getAccessor()).toList());
            for (var method : methods) {
                try { validate(method.invoke(value)); }
                catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot validate snapshot scalar bounds.", e); }
            }
        }
    }
}
