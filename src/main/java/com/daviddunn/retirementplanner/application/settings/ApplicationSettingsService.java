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
        if (committed.getDefaultExportDirectory() == null) {
            committed.setDefaultExportDirectory(deriveExportDirectory(committed.getDefaultPlanDirectory()));
        }
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
        return savePreferences(theme, directory, false);
    }

    public CompletionStage<Void> suppressIntroduction(AnalyzerIntroduction kind) {
        Objects.requireNonNull(kind);
        return submit(worker, () -> {
            update(next -> {
                var values = new java.util.HashSet<>(next.getSuppressedAnalyzerIntroductions());
                values.add(kind);
                next.setSuppressedAnalyzerIntroductions(values);
            });
            return null;
        });
    }

    public CompletionStage<Void> savePreferences(ApplicationTheme theme, String directory, boolean resetIntroductions) {
        return savePreferences(theme, directory, null, resetIntroductions);
    }

    public CompletionStage<Void> savePreferences(ApplicationTheme theme, String directory,
            String exportDirectory, boolean resetIntroductions) {
        Objects.requireNonNull(theme);
        var previous = snapshot();
        return background(() -> {
            if (directory != null && directory.length() > 4096) throw new IllegalArgumentException("Directory preference is too long.");
            String normalized = directory == null || directory.isBlank() ? null : java.nio.file.Path.of(directory).toAbsolutePath().normalize().toString();
            if (normalized != null && !PlanDirectoryResolver.usable(normalized))
                throw new IllegalArgumentException("Choose an existing readable and writable directory.");
            String export = exportDirectory == null || exportDirectory.isBlank()
                    ? (Objects.equals(normalized, previous.getDefaultPlanDirectory())
                        ? previous.getDefaultExportDirectory() : deriveExportDirectory(normalized))
                    : java.nio.file.Path.of(exportDirectory).toAbsolutePath().normalize().toString();
            if (export.length() > 4096) throw new IllegalArgumentException("Directory preference is too long.");
            return new String[] { normalized, export };
        }).thenCompose(paths -> submit(worker, () -> {
            update(next -> {
                next.setTheme(theme);
                next.setDefaultPlanDirectory(paths[0]);
                next.setDefaultExportDirectory(paths[1]);
                if (resetIntroductions) next.setSuppressedAnalyzerIntroductions(java.util.Set.of());
            });
            return null;
        }));
    }

    private static String deriveExportDirectory(String planDirectory) {
        java.nio.file.Path base;
        try {
            base = planDirectory == null || planDirectory.isBlank()
                    ? java.nio.file.Path.of(System.getProperty("user.home"))
                    : java.nio.file.Path.of(planDirectory);
        } catch (RuntimeException invalidPlanPath) {
            base = java.nio.file.Path.of(System.getProperty("user.home"));
        }
        return base.toAbsolutePath().normalize().resolve("export").toString();
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
