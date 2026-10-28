package com.daviddunn.retirementplanner.application.settings;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One application preference authority. Field updates merge only after entering the write boundary. */
public final class ApplicationSettingsService implements AutoCloseable {
    private final ApplicationSettingsRepository repository;
    private volatile ApplicationSettings committed;
    private final Object writeLock = new Object();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                var thread = new Thread(runnable, "application-preferences"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final ThreadPoolExecutor probes = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                var thread = new Thread(runnable, "application-directory-probe"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public ApplicationSettingsService(ApplicationSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository);
        committed = repository.load().copy();
        worker.allowCoreThreadTimeOut(true);
        probes.allowCoreThreadTimeOut(true);
    }

    public ApplicationSettings snapshot() { return committed.copy(); }

    private void update(Consumer<ApplicationSettings> change) {
        synchronized (writeLock) {
            var next = committed.copy();
            change.accept(next);
            repository.save(next);
            committed = next;
        }
    }

    /** Existing successful plan operations retain their independent preference-failure policy. */
    public void recordLastPlan(String path) { update(next -> next.setLastOpenedPlan(path)); }
    public CompletionStage<Void> recordLastPlanAsync(String path) {
        return submit(worker, () -> { recordLastPlan(path); return null; });
    }

    public CompletionStage<Void> savePreferences(ApplicationTheme theme, String directory) {
        Objects.requireNonNull(theme);
        return background(() -> {
            if (directory != null && directory.length() > 4096) throw new IllegalArgumentException("Directory preference is too long.");
            String normalized = directory == null || directory.isBlank() ? null : java.nio.file.Path.of(directory).toAbsolutePath().normalize().toString();
            if (normalized != null && !PlanDirectoryResolver.usable(normalized))
                throw new IllegalArgumentException("Choose an existing readable and writable directory.");
            return normalized;
        }).thenCompose(normalized -> submit(worker, () -> {
            update(next -> { next.setTheme(theme); next.setDefaultPlanDirectory(normalized); });
            return null;
        }));
    }

    public <T> CompletionStage<T> background(Supplier<T> task) {
        return submit(probes, task).orTimeout(8, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> submit(ThreadPoolExecutor executor, Supplier<T> task) {
        var result = new CompletableFuture<T>();
        try { executor.execute(() -> {
            if (result.isDone()) return;
            try { result.complete(task.get()); } catch (Exception failure) { result.completeExceptionally(failure); }
        }); } catch (RejectedExecutionException busy) { result.completeExceptionally(busy); }
        return result;
    }

    @Override public void close() { probes.shutdownNow(); worker.shutdown(); }
}
