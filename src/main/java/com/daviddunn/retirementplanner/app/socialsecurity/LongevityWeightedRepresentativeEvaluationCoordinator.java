package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * One comparison's bounded execution scope. Only its owner thread prepares tasks,
 * consumes results and checks the external token. Workers receive a sticky token.
 * A task must own its mutable graph and return a compact immutable result.
 * Stage 5C2 remains sequential: tasks must never create nested analysis executors.
 */
final class LongevityWeightedRepresentativeEvaluationCoordinator<T> implements AutoCloseable {
    static final int DEFAULT_LIMIT = 4;
    static final int MAXIMUM_LIMIT = 8;

    private final List<Integer> orders;
    private final BiFunction<Integer, AnalysisCancellationToken, Callable<T>> prepare;
    private final AnalysisCancellationToken externalCancellation;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ThreadPoolExecutor executor;
    private final Map<Integer, Future<T>> pending = new LinkedHashMap<>();
    private final int window;
    private int next;
    private boolean closed;

    LongevityWeightedRepresentativeEvaluationCoordinator(int configuredLimit, List<Integer> orders,
            BiFunction<Integer, AnalysisCancellationToken, Callable<T>> prepare,
            AnalysisCancellationToken cancellation) {
        this(configuredLimit, Runtime.getRuntime().availableProcessors(), orders, prepare, cancellation);
    }

    /** Explicit processor count is a test seam; production always uses the runtime count. */
    LongevityWeightedRepresentativeEvaluationCoordinator(int configuredLimit, int processors, List<Integer> orders,
            BiFunction<Integer, AnalysisCancellationToken, Callable<T>> prepare,
            AnalysisCancellationToken cancellation) {
        this.orders = List.copyOf(Objects.requireNonNull(orders));
        if (new HashSet<>(orders).size() != orders.size()) {
            throw new IllegalArgumentException("Representative positions must be unique.");
        }
        this.prepare = Objects.requireNonNull(prepare);
        this.externalCancellation = Objects.requireNonNull(cancellation);
        int workers = effectiveWorkers(configuredLimit, processors, orders.size());
        window = Math.max(1, workers * 2);
        // Outstanding tasks are limited to 2W. Queue capacity also tolerates the
        // brief gap between Future completion and its worker becoming available.
        executor = workers <= 1 ? null : new ThreadPoolExecutor(workers, workers, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(window),
                Thread.ofPlatform().name("longevity-representative-", 0).factory());
    }

    static int effectiveWorkers(int configuredLimit, int processors, int representatives) {
        if (configuredLimit < 1 || processors < 1 || representatives < 0) {
            throw new IllegalArgumentException("Worker limits must be positive and work count nonnegative.");
        }
        return Math.min(Math.min(configuredLimit, MAXIMUM_LIMIT), Math.min(processors, representatives));
    }

    T representative(int order) {
        checkCancellation();
        if (executor == null) {
            if (next >= orders.size() || orders.get(next) != order) {
                throw new IllegalArgumentException("Representatives must be consumed in original order.");
            }
            next++;
            return direct(order);
        }
        fillWindow();
        Future<T> future = pending.get(order);
        if (future == null) throw new IllegalArgumentException("Representative is not in the current window.");
        T result = await(future);
        pending.remove(order);
        // Refill only on the next request, after the caller's progress/cancellation boundary.
        return result;
    }

    /** Independent failed-representative member retry, never a new equivalence representative. */
    T retry(int originalOrder) {
        checkCancellation();
        if (executor == null) return direct(originalOrder);
        // The caller consumed its representative before retrying, leaving a window slot.
        if (pending.size() >= window) throw new IllegalStateException("No bounded retry slot is available.");
        Future<T> future = submit(originalOrder);
        pending.put(originalOrder, future);
        T result = await(future);
        pending.remove(originalOrder);
        return result;
    }

    private void fillWindow() {
        while (next < orders.size() && pending.size() < window) {
            checkCancellation();
            int order = orders.get(next++);
            pending.put(order, submit(order));
        }
    }

    private Future<T> submit(int order) {
        checkCancellation();
        var task = prepare.apply(order, cancelled::get);
        checkCancellation();
        return executor.submit(() -> {
            if (cancelled.get()) throw new AnalysisCancelledException();
            return task.call();
        });
    }

    private T direct(int order) {
        // On the owner thread a safe boundary may also poll the external token.
        var task = prepare.apply(order, () -> {
            checkCancellation();
            return false;
        });
        checkCancellation();
        try {
            T result = task.call();
            checkCancellation();
            return result;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Representative task failed.", failure);
        }
    }

    private T await(Future<T> future) {
        for (;;) {
            checkCancellation();
            try {
                T result = future.get(25, TimeUnit.MILLISECONDS);
                checkCancellation();
                return result;
            } catch (TimeoutException waiting) {
                // Poll external cancellation on the owner thread, never on workers.
            } catch (InterruptedException interrupted) {
                cancelled.set(true);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Representative coordinator was interrupted.", interrupted);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Representative task failed.", cause);
            }
        }
    }

    void checkCancellation() {
        if (externalCancellation.isCancellationRequested()) cancelled.set(true);
        if (cancelled.get()) throw new AnalysisCancelledException();
        if (closed) throw new IllegalStateException("Representative coordinator is closed.");
    }

    boolean terminated() {
        return executor == null || executor.isTerminated();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelled.set(true);
        if (executor == null) return;
        pending.values().forEach(future -> future.cancel(false));
        executor.getQueue().clear();
        executor.shutdown();
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(25, TimeUnit.MILLISECONDS);
            } catch (InterruptedException signal) {
                interrupted = true;
            }
        }
        pending.clear();
        if (interrupted) Thread.currentThread().interrupt();
    }
}
