package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.function.*;

/** FX-confined binding and status; decoding and durable storage belong to the application store. */
final class WeightedResultPersistence implements AutoCloseable {
    static final String OUTDATED = "These results were generated using previous plan or analysis assumptions and may not reflect the current retirement plan.";
    final Label notice;
    final Button retry = new Button("Retry Save");
    final VBox controls;
    final AssociatedAnalyzerAction associated;
    private final ApplicationController source;
    private final AnalysisKind kind;
    private final Supplier<AnalysisFingerprintService.Inputs> inputs;
    private final Function<AnalysisSnapshot, Runnable> reconstruct;
    private final Runnable changed;
    private final Runnable clear;
    private AnalysisResultStore.Slot slot;
    private java.nio.file.Path boundFile;
    private AnalysisSnapshot snapshot;
    private AnalysisCompatibilityPolicy.Assessment assessment;
    private Map<String, String> references = Map.of();
    private long generation;
    private boolean closed;
    private boolean loaded;
    private boolean loading;
    private boolean metadataPending;
    private boolean changingSource;
    private boolean replacing;
    private Runnable loadStarted = () -> { };

    void onLoadStarted(Runnable action) { loadStarted = Objects.requireNonNull(action); }

    WeightedResultPersistence(ApplicationController source, AnalysisKind kind,
            Supplier<AnalysisFingerprintService.Inputs> inputs, Function<AnalysisSnapshot, Runnable> reconstruct,
            Runnable changed, Runnable clear) {
        this(source, kind, inputs, reconstruct, changed, clear, null);
    }
    WeightedResultPersistence(ApplicationController source, AnalysisKind kind,
            Supplier<AnalysisFingerprintService.Inputs> inputs, Function<AnalysisSnapshot, Runnable> reconstruct,
            Runnable changed, Runnable clear, Label existingNotice) {
        notice = existingNotice == null ? AnalyzerConfigurationLayout.note("") : existingNotice;
        notice.setWrapText(true);
        notice.setMinWidth(0);
        notice.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        associated = new AssociatedAnalyzerAction(source, () -> kind);
        controls = new VBox(5, notice, retry, associated);
        this.source = source;
        this.kind = kind;
        this.inputs = inputs;
        this.reconstruct = reconstruct;
        this.changed = changed;
        this.clear = clear;
        notice.accessibleTextProperty().bind(notice.textProperty());
        notice.setAccessibleHelp("Status and original completion time for the displayed completed analysis.");
        retry.setAccessibleText("Retry saving the completed analysis without recalculation");
        retry.visibleProperty().bind(retry.managedProperty());
        retry.setManaged(false);
        retry.setOnAction(event -> {
            source.getAnalysisResults().retry(slot);
            observeSave(generation);
        });
        binding();
        show();
    }

    private void binding() {
        var next = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind);
        var file = currentFile();
        if (!next.equals(slot) || !Objects.equals(file, boundFile)) {
            generation++;
            slot = next;
            boundFile = file;
            snapshot = null;
            assessment = null;
            loaded = false;
            loading = false;
            metadataPending = false;
            replacing = false;
            clear.run();
        }
    }

    void load() {
        if (closed || changingSource) return;
        binding();
        if (replacing) { reassess(); return; }
        if (loaded || loading) {
            if (loaded && snapshot == null) source.getAnalysisResults().inMemory(slot).ifPresent(c -> metadata(c, generation));
            reassess(); return;
        }
        var store = source.getAnalysisResults();
        var memory = store.inMemory(slot);
        if (!source.canPersistAnalysisResults() && memory.isEmpty()) { show(); return; }
        long expected = generation;
        loadStarted.run();
        loading = true;
        notice.setText(kind == AnalysisKind.COUPLE_EXHAUSTIVE ? "Loading previous exhaustive results…"
                : kind == AnalysisKind.COUPLE_QUICK ? "Loading previous Quick Comparison results…"
                : kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC ? "Loading previous individual deterministic results…"
                : kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                ? "Loading previous Social Security results…" : "Loading previous longevity-weighted results…");
        var file = source.getCurrentFile();
        var read = source.canPersistAnalysisResults() ? store.load(slot, file)
                : store.background(() -> Optional.of(memory.orElseThrow().presentationSnapshot(new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec())));
        read.thenCompose(found -> store.background(() -> {
            var saved = found.orElse(null);
            if (saved == null) return new Restored(null, null, Map.of());
            var refs = AnalysisVersions.references(kind);
            var compatibility = new AnalysisCompatibilityPolicy().assess(saved,
                    new AnalysisFingerprintService.Assessment(saved.inputFingerprint(), saved.contextFingerprint(), null),
                    AnalysisVersions.algorithms(kind), refs);
            if (compatibility.state() == AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE)
                throw new IllegalArgumentException("Unsupported saved financial meaning.");
            return new Restored(saved, reconstruct.apply(saved), refs);
        })).whenComplete((restored, failure) -> Platform.runLater(() -> {
            if (!valid(expected)) return;
            loading = false;
            loaded = true;
            if (failure != null) {
                assessment = new AnalysisCompatibilityPolicy().unavailable("Saved results are incompatible or unavailable. The historical file was preserved; you can run a new analysis.");
            } else if (restored.snapshot != null) {
                snapshot = restored.snapshot;
                references = restored.references;
                restored.attach.run();
            }
            reassess();
        }));
    }

    private record Restored(AnalysisSnapshot snapshot, Runnable attach, Map<String, String> references) { }

    /** Exhaustive replacement guard: a pending older load cannot attach during a replacement. */
    void beginReplacement() {
        if (loading) { generation++; loading = false; loaded = false; }
        replacing = true;
    }
    void finishReplacement() {
        if (!replacing || closed) return;
        replacing = false;
        load();
    }

    /** Called only inside the existing eligible, accepted financial completion callback. */
    void accept(AcceptedAnalysisCompletion completion) {
        replacing = false;
        generation++;
        loading = false;
        loaded = true;
        snapshot = null;
        assessment = null;
        metadataPending = false;
        source.getAnalysisResults().accept(completion);
        source.associateAnalyzer(completion.association(), completion);
        observeSave(generation);
        // Build integrity metadata off FX, including for a memory-only accepted result.
        metadata(completion, generation);
    }
    private void metadata(AcceptedAnalysisCompletion completion, long expected) {
        if (metadataPending || snapshot != null) return;
        metadataPending = true;
        source.getAnalysisResults().background(() -> completion.presentationSnapshot(new com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec()))
                .whenComplete((saved, failure) -> Platform.runLater(() -> {
                    if (!valid(expected)) return;
                    metadataPending = false;
                    if (failure == null) {
                        snapshot = saved;
                        references = saved.references();
                    }
                    reassess();
                }));
    }

    private void observeSave(long expected) {
        show();
        source.getAnalysisResults().whenSettled(slot).whenComplete((status, failure) -> Platform.runLater(() -> {
            if (valid(expected)) {
                if (snapshot == null) source.getAnalysisResults().inMemory(slot).ifPresent(c -> metadata(c, expected));
                show();
            }
        }));
    }

    void reassess() {
        if (closed || changingSource) return;
        binding();
        if (snapshot != null) {
            try {
                var next = inputs.get();
                assessment = new AnalysisCompatibilityPolicy().assess(snapshot,
                        new AnalysisFingerprintService().assess(kind, source.getCurrentPlan(), next, null),
                        AnalysisVersions.algorithms(kind), references);
            } catch (RuntimeException invalid) {
                assessment = new AnalysisCompatibilityPolicy.Assessment(AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION,
                        "Inputs need attention. Previous results remain available; correct the pending analysis inputs.");
            }
        }
        show();
        changed.run();
    }

    private boolean valid(long expected) {
        return !closed && generation == expected && sameBinding();
    }
    private boolean sameBinding() {
        return slot != null && slot.planId().equals(source.getAnalysisSessionId())
                && Objects.equals(boundFile, currentFile());
    }
    private java.nio.file.Path currentFile() {
        var file = source.getCurrentFile();
        return file == null ? null : file.toAbsolutePath().normalize();
    }
    void sourceChanged(Runnable update) {
        changingSource = true;
        try { update.run(); }
        finally {
            changingSource = false;
            // Controller open/new notifications precede its file-path assignment.
            Platform.runLater(this::load);
        }
    }
    boolean exportable() { return sameBinding() && snapshot != null && assessment != null
            && assessment.state() != AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE; }
    boolean current() { return sameBinding() && assessment != null && assessment.state() == AnalysisCompatibilityPolicy.State.CURRENT; }
    AnalysisResultFreshness freshness() {
        return new AnalysisResultFreshness(reportAssessment(), source.analysisAssociation(resultMetadata()));
    }
    String warning() { return freshness().warning(); }
    /** FX-thread capture of presentation only; compatibility remains the authoritative assessment. */
    List<String> provenance() {
        var saved = resultMetadata();
        if (saved == null) return List.of();
        var lines = new ArrayList<String>();
        lines.add("Displayed completed result: last-success analysis (run " + saved.runId() + ").");
        if (freshness().mismatch()) {
            lines.add("This last-success analysis does not match Current's effective inputs or required analysis compatibility. Specific recorded input differences, when available, follow.");
            if (saved.payload() instanceof CoupleSocialSecuritySnapshot ss && source.currentProjectionInputsMatch()) {
                var household = source.getCurrentPlan().getHousehold();
                claimingDifference(lines, "Primary", ss.request().base().primaryElection(), ss.frozen().primaryClaimDate(), household.getPrimaryPerson());
                claimingDifference(lines, "Spouse", ss.request().base().spouseElection(), ss.frozen().spouseClaimDate(), household.getSpouse());
            }
        }
        if (associated.available()) lines.add("Current has a separately recorded associated analysis. View Current Projection's Associated Result opens it read-only without recalculation; it does not replace this last-success analysis. Integrity is verified on opening.");
        else lines.add("No recorded association for this analyzer is available for Current. Legacy associations are not inferred.");
        return List.copyOf(lines);
    }

    private static void claimingDifference(List<String> lines, String owner,
            com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityClaimingElection original,
            java.time.LocalDate frozenDate, com.daviddunn.retirementplanner.domain.model.Person current) {
        if (current == null || !original.retirementClaimDate().equals(frozenDate)) return;
        var records = current.getIncomeSources().stream()
                .filter(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::isInstance)
                .map(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::cast).toList();
        if (records.size() != 1) return;
        var now = records.getFirst();
        if (!original.birthDate().equals(current.getBirthDate()) || frozenDate.equals(now.getStartDate())) return;
        for (int age = 62; age <= 70; age++) {
            if (original.birthDate().plusYears(age).equals(frozenDate)
                    && current.getBirthDate().plusYears(now.getClaimingAge()).equals(now.getStartDate())) {
                lines.add(owner + " claiming age: last-success " + age + "; Current " + now.getClaimingAge() + ".");
                return;
            }
        }
        lines.add(owner + " claiming date: last-success " + frozenDate + "; Current " + now.getStartDate() + ".");
    }
    AnalysisSnapshot resultMetadata() { return sameBinding() ? snapshot : null; }
    AnalysisCompatibilityPolicy.Assessment reportAssessment() { return sameBinding() ? assessment : null; }
    static java.time.LocalDate date(DatePicker picker) {
        var value = Objects.requireNonNull(picker.getValue());
        var text = picker.getEditor().getText();
        // An unskinned, untouched picker may not yet have formatted its initial value.
        if (picker.getSkin() == null && (text == null || text.isBlank())
                && !Boolean.TRUE.equals(picker.getProperties().get("weighted-date-edited"))) return value;
        var converter = picker.getConverter();
        if (converter == null) converter = new javafx.util.converter.LocalDateStringConverter();
        if (!Objects.equals(value, converter.fromString(text)))
            throw new IllegalArgumentException("Date entry needs attention.");
        return value;
    }
    static void dateEdited(DatePicker picker) { picker.getProperties().put("weighted-date-edited", true); }
    static java.math.BigDecimal rate(TextField field) {
        var rate = new java.math.BigDecimal(field.getText().trim()).movePointLeft(2);
        com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator.validateRate(rate);
        return rate;
    }

    private void show() {
        String text = snapshot == null && !loading && !metadataPending && assessment == null
                ? "No Saved Results" : freshness().status();
        if (freshness().mismatch()) text = "Previous Results — Outdated\n" + text;
        associated.refresh();
        if (snapshot != null) {
            text += "\nAnalysis completed: " + snapshot.completedAt();
            if (snapshot.originPlanId() != null) text += "\nHistorical copy from another plan; original completion and financial assumptions retained.";
        }
        var status = source.getAnalysisResults().status(slot).orElse(null);
        if (assessment == null && status != null) text = "Completed Results — preparing result metadata";
        if (status != null) text += "\n" + status.explanation();
        else if (snapshot != null) text += "\nResults saved.";
        if (!source.canPersistAnalysisResults()) text += "\nSave the retirement plan first to enable persistent results. No plan is autosaved.";
        var association = source.getAnalyzerAssociationNotice(kind);
        if (!association.isEmpty()) text += "\n" + association;
        for (var line : provenance()) text += "\n" + line;
        retry.setManaged(status != null && status.durability() == AnalysisResultStore.Durability.SAVE_FAILED);
        notice.setText(text);
    }

    IntegratedAnalyzerReport report(IntegratedAnalyzerReport original) {
        reassess();
        if (!exportable()) throw new IllegalStateException("No safely restored result is available.");
        var context = new ArrayList<String>();
        context.add(freshness().status());
        context.addAll(provenance());
        context.add("Original analysis completed: " + snapshot.completedAt());
        context.addAll(original.context());
        return new IntegratedAnalyzerReport(freshness().mismatch() ? "Historical Analysis — Outdated | " + original.analysisType() : original.analysisType(), original.household(), original.runStarted(),
                original.exportedAt(), context, original.selectedSummary(), original.visualization(), original.assumptions(),
                original.rankedTables(), original.results());
    }
    @Override public void close() { closed = true; generation++; }
}

