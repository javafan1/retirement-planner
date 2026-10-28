package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot;
import com.daviddunn.retirementplanner.app.projection.AnalyzerAssociation;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

/** Read-only, projection-owned history. No analysis controls, jobs or calculation factories. */
public final class ProjectionAnalyzerHistoryDialog extends Dialog<Void> {
    private record Role(String name, UUID id) { @Override public String toString() { return name; } }
    record Content(Supplier<Node> view, Function<Path, Runnable> export) { }
    private final ApplicationController source;
    private final Object owner;
    private final UUID ownerId;
    private final ComboBox<Role> roles = new ComboBox<>();
    private final ComboBox<AnalysisKind> kinds = new ComboBox<>();
    private final Label notice = label("");
    private final VBox body = new VBox(10);
    private final Button export = new Button("Export PDF");
    private long generation;
    private boolean closed;
    private boolean exporting;
    private Content selected;
    private AnalyzerAssociation selectedReference;
    private Runnable detach;

    public ProjectionAnalyzerHistoryDialog(ApplicationController source, UUID preferredProjection) {
        this(source, preferredProjection, null);
    }

    public ProjectionAnalyzerHistoryDialog(ApplicationController source, UUID preferredProjection, AnalysisKind preferredKind) {
        this.source = Objects.requireNonNull(source); owner = source.getCurrentPlan(); ownerId = source.getAnalysisSessionId();
        setTitle("Projection Analyzer History"); setHeaderText("Original saved analyzer results — read only"); setResizable(true);
        var state = source.getCurrentPlan().getProjectionState();
        if (state != null) {
            if (state.current() != null) roles.getItems().add(new Role("Current accepted projection", state.current().id()));
            if (state.baseline() != null) roles.getItems().add(new Role("Saved baseline projection", state.baseline().id()));
            for (var pin : state.pinnedProjections()) if (roles.getItems().stream().noneMatch(r -> r.id().equals(pin.id())))
                roles.getItems().add(new Role("Earlier accepted projection " + pin.id(), pin.id()));
        }
        kinds.getItems().setAll(Arrays.stream(AnalysisKind.values()).filter(k ->
                (k.shape() == AnalysisKind.Shape.COUPLE) == source.getCurrentPlan().getHousehold().hasSpouse()).toList());
        roles.setAccessibleText("Projection role"); kinds.setAccessibleText("Saved analyzer identity");
        roles.setMaxWidth(Double.MAX_VALUE); kinds.setMaxWidth(Double.MAX_VALUE);
        var selectors = new FlowPane(10, 8, new Label("Projection:"), roles, new Label("Analyzer:"), kinds, export);
        export.setDisable(true); export.setOnAction(e -> export());
        var contents = new VBox(12, selectors, notice, body); contents.setMinWidth(0);
        contents.setPadding(new javafx.geometry.Insets(14));
        var scroll = new ScrollPane(contents); scroll.setFitToWidth(true);
        getDialogPane().setContent(scroll); getDialogPane().setPrefSize(1180, 820);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        roles.valueProperty().addListener((o, a, b) -> { selectRoleKinds(); load(); }); kinds.valueProperty().addListener((o, a, b) -> load());
        roles.setValue(roles.getItems().stream().filter(r -> r.id().equals(preferredProjection)).findFirst()
                .orElse(roles.getItems().isEmpty() ? null : roles.getItems().getFirst()));
        if (preferredKind != null && kinds.getItems().contains(preferredKind)) kinds.setValue(preferredKind);
        else kinds.getSelectionModel().selectFirst();
        detach = source.addSourcePlanRevisionListener(() -> { if (!owned()) close(); });
        setOnHidden(e -> { closed = true; generation++; selected = null; body.getChildren().clear(); if (detach != null) detach.run(); });
    }

    /** A role exchange may promote an earlier household shape. Use its recorded metadata, not live inputs. */
    private void selectRoleKinds() {
        if (!owned() || roles.getValue() == null) return;
        var id = roles.getValue().id();
        var component = source.getCurrentPlan().getProjectionComponents().get(id);
        if (component == null) component = source.getCurrentPlan().getPendingPinnedProjections().get(id);
        boolean couple = component != null ? component.people().hasSpouse()
                : source.getAnalyzerAssociations(id).stream().findFirst().map(a -> a.kind().shape() == AnalysisKind.Shape.COUPLE)
                    .orElse(source.getCurrentPlan().getHousehold().hasSpouse());
        var previous = kinds.getValue();
        kinds.getItems().setAll(Arrays.stream(AnalysisKind.values()).filter(k -> (k.shape() == AnalysisKind.Shape.COUPLE) == couple).toList());
        if (kinds.getItems().contains(previous)) kinds.setValue(previous);
        else kinds.getSelectionModel().selectFirst();
    }

    private boolean owned() { return !closed && owner == source.getCurrentPlan() && ownerId.equals(source.getAnalysisSessionId()); }
    private boolean attached(AnalyzerAssociation ref) {
        return owned() && roles.getValue() != null && roles.getValue().id().equals(ref.projectionId())
                && source.getAnalyzerAssociations(ref.projectionId()).contains(ref);
    }
    private void load() {
        long ticket = ++generation; selected = null; selectedReference = null; export.setDisable(true); body.getChildren().clear();
        if (!owned() || roles.getValue() == null || kinds.getValue() == null) { notice.setText("No complete projection is available."); return; }
        var refs = source.getAnalyzerAssociations(roles.getValue().id());
        var ref = refs.stream().filter(r -> r.kind() == kinds.getValue()).findFirst().orElse(null);
        if (ref == null) {
            notice.setText("Analyzer result not captured for this projection. Last-success and legacy results have no recorded association here; they are not inferred or recalculated.");
            return;
        }
        notice.setText("Loading original saved analyzer result…");
        String durability = source.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(ref.runId())
                ? "Accepted in memory. Historical association is pending ordinary plan Save; no durable pin is claimed."
                : "Verified committed immutable historical analyzer component.";
        source.loadAssociatedAnalyzer(ref).thenCompose(saved -> source.getAnalysisResults().background(() -> {
            var assessment = new AnalysisCompatibilityPolicy().assess(saved,
                    new AnalysisFingerprintService.Assessment(saved.inputFingerprint(), saved.contextFingerprint(), null),
                    AnalysisVersions.algorithms(saved.kind()), AnalysisVersions.references(saved.kind()));
            if (assessment.state() != AnalysisCompatibilityPolicy.State.CURRENT)
                throw new IllegalArgumentException("Saved analyzer algorithm/reference context is incompatible.");
            return prepare(saved, durability);
        })).whenComplete((content, failure) -> Platform.runLater(() -> {
            if (ticket != generation || !attached(ref)) return;
            if (failure != null) { notice.setText("Original analyzer result unavailable or incompatible. The saved reference has been preserved."); return; }
            selected = content; selectedReference = ref; body.getChildren().setAll(content.view().get());
            notice.setText("Original saved analyzer result | Projection " + ref.projectionId() + " | Completed " + ref.acceptedAt()
                    + "\nOriginal assumptions are shown. Editable inputs and last-success slots do not change this association."
                    + (source.getCurrentPlan().getPendingAnalyzerAssociations().containsKey(ref.runId())
                    ? "\nHistorical pin pending ordinary plan Save; the last-success slot has its own save status." : "\nCommitted immutable historical component."));
            export.setDisable(exporting);
        }));
    }

    private void export() {
        if (!owned() || selected == null || selectedReference == null || !attached(selectedReference) || exporting) return;
        var chooser = new FileChooser(); chooser.setTitle("Export Original Analyzer PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf")); chooser.setInitialFileName("historical-analyzer.pdf");
        var file = chooser.showSaveDialog(getDialogPane().getScene().getWindow()); if (file == null) return;
        var work = selected.export().apply(file.toPath()); // capture selection on FX before background file work
        exporting = true; export.setDisable(true); long ticket = generation;
        source.getAnalysisResults().background(() -> { work.run(); return null; }).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            exporting = false; if (!owned()) return;
            export.setDisable(selected == null || selectedReference == null || !attached(selectedReference));
            if (ticket == generation && failure != null) notice.setText("PDF export failed. Original saved results were not changed.");
        }));
    }

    static Content prepare(AnalysisSnapshot saved) {
        return prepare(saved, "Original saved analyzer result; immutable historical component.");
    }
    private static Content prepare(AnalysisSnapshot saved, String durability) {
        if (saved.payload() instanceof MonteCarloSnapshot mc) {
            if (mc.mode() == MonteCarloSnapshot.Mode.PAIRED_FIXED || mc.mode() == MonteCarloSnapshot.Mode.PAIRED_LONGEVITY) {
                var run = MonteCarloSnapshotPresentationMapper.restoreComparison(mc);
                return monte(saved, durability, () -> MonteCarloPdfReportAdapter.from(run), () -> {
                    var chart = new MonteCarloDifferenceChart(); chart.setPrefHeight(430); chart.load(run.result().summary().annualResults());
                    var detail = label(""); detail.textProperty().bind(chart.selectedYearProperty());
                    var warning = label(""); warning.textProperty().bind(chart.warningProperty());
                    return new VBox(8, chart, detail, warning);
                });
            }
            var run = MonteCarloSnapshotPresentationMapper.restoreRun(mc);
            return monte(saved, durability, () -> MonteCarloPdfReportAdapter.from(run), () -> {
                var chart = new MonteCarloFanChart(); chart.setPrefHeight(430); chart.load(run.fan());
                var summary = label(""); summary.textProperty().bind(chart.selectedSummaryProperty());
                var detail = label(""); detail.textProperty().bind(chart.selectedDetailProperty());
                return new VBox(8, chart, summary, detail);
            });
        }
        var prepared = HistoricalAnalyzerPresentation.prepare(saved);
        var holder = new java.util.concurrent.atomic.AtomicReference<SocialView>();
        return new Content(() -> { var view = new SocialView(prepared, saved, durability); view.build(); holder.set(view); return view; }, path -> {
            var report = holder.get().report();
            return () -> { try { new IntegratedAnalyzerPdfExporter().export(report, path); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } };
        });
    }

    private static Content monte(AnalysisSnapshot saved, String durability, Supplier<MonteCarloPdfReport> reports, Supplier<Node> chart) {
        return new Content(() -> {
            var report = reports.get(); var box = new VBox(10, label(report.chart().title()), chart.get(), label(String.join("\n", report.context())));
            box.getStyleClass().add("mc-view");
            for (var css : List.of("results-summary.css", "chart-timeline.css", "projection-chart.css", "monte-carlo.css"))
                box.getStylesheets().add(Objects.requireNonNull(ProjectionAnalyzerHistoryDialog.class.getResource("/css/" + css)).toExternalForm());
            for (var section : report.sections()) {
                var content = new VBox(8, label(String.join("\n", section.paragraphs())));
                for (var table : section.tables()) content.getChildren().add(table(table.headings(), table.rows()));
                box.getChildren().add(new TitledPane(section.title(), content));
            }
            return box;
        }, path -> {
            var r = reports.get(); var context = new ArrayList<>(r.context());
            context.add(durability);
            context.add("Original saved result | Started " + saved.context().startedAt() + " | Completed " + saved.completedAt());
            var report = new MonteCarloPdfReport(r.title(), r.type(), r.filename(), r.exportedAt(), context, r.chart(), r.sections());
            return () -> { try { new MonteCarloPdfExporter().export(report, path); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } };
        });
    }

    private static final class SocialView extends VBox {
        private final HistoricalAnalyzerPresentation.Prepared prepared;
        private final AnalysisSnapshot saved;
        private final String durability;
        private final VBox text = new VBox(10);
        private ClaimingStrategyHeatMapView integrated;
        private ClaimingAgeGridView.Pair pair;
        private int age = 67;
        SocialView(HistoricalAnalyzerPresentation.Prepared prepared, AnalysisSnapshot saved, String durability) { super(10); this.prepared = prepared; this.saved = saved; this.durability = durability; }
        void build() {
            if (prepared.integrated() != null) {
                integrated = new ClaimingStrategyHeatMapView(saved.kind() == AnalysisKind.COUPLE_EXHAUSTIVE
                        ? ClaimingStrategyHeatMapProfile.deterministic() : ClaimingStrategyHeatMapProfile.weighted(), occurrence -> refresh());
                integrated.render(prepared.integrated());
                integrated.metric.valueProperty().addListener((o, a, b) -> refresh()); getChildren().add(integrated);
            } else if (prepared.socialSecurity() != null) {
                var model = prepared.socialSecurity(); var grid = new ClaimingAgeGridView();
                var gridBox = new VBox(8, label("Primary claiming age →"), grid, ClaimingAgeGridView.legend("★ Stage 1 optimum; ▲ retained for Stage 2"));
                gridBox.getStyleClass().add("claiming-heat-map");
                gridBox.setStyle(com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette.cssVariables());
                gridBox.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/claiming-strategy-heat-map.css")).toExternalForm());
                grid.render(model.primaryAges(), model.spouseAges(), model.cells().stream().map(SocialSecurityClaimingGridAdapter.Cell::display).toList(),
                        "historical-ss-", value -> { pair = value; refresh(); });
                getChildren().add(gridBox);
                model.cells().stream().filter(SocialSecurityClaimingGridAdapter.Cell::optimal).findFirst().ifPresent(c -> grid.select(c.pair()));
            } else if (saved.shape() == AnalysisKind.Shape.SINGLE) {
                var ages = new ComboBox<Integer>(); ages.getItems().setAll(java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList());
                ages.setValue(age); ages.setAccessibleText("Original claiming strategy details");
                ages.valueProperty().addListener((o, a, b) -> { age = b; refresh(); }); getChildren().add(new HBox(8, new Label("Claiming age:"), ages));
            }
            getChildren().add(text); refresh();
            retained();
        }
        private void retained() {
            var values = new LinkedHashMap<String, RetainedProjectionSnapshot>();
            if (saved.payload() instanceof QuickComparisonSnapshot q) {
                values.put("Original baseline", q.baseline());
                q.entries().stream().filter(e -> e.detail() != null).forEach(e -> values.put("Original candidate " + e.order(), e.detail()));
            } else if (saved.payload() instanceof ExhaustiveSnapshot e) {
                values.put("Original baseline", e.baseline());
                e.entries().stream().filter(v -> v.detail() != null).forEach(v -> values.put("Retained strategy " + v.order(), v.detail()));
            } else if (saved.payload() instanceof IndividualDeterministicSnapshot d) {
                values.put("Original baseline", d.baseline());
                d.entries().stream().filter(v -> v.detail() != null).forEach(v -> values.put("Retained strategy " + v.order(), v.detail()));
            }
            if (values.isEmpty()) return;
            var choices = new ComboBox<String>(); choices.getItems().setAll(values.keySet()); choices.setAccessibleText("Original retained annual projection");
            var annual = new VBox(8); var content = new VBox(10, choices, annual);
            choices.valueProperty().addListener((o, a, b) -> {
                var original = values.get(b);
                annual.getChildren().setAll(label("Original saved annual results. Unretained candidate projections are unavailable; no new projection is run."),
                        table(List.of("Year", "Investible assets", "Income", "Expenses", "Growth", "Withdrawals"), original.years().stream().map(y -> List.of(
                                "" + y.year(), money(y.ending()), money(y.income()), money(y.expenses()), money(y.growth()), money(y.withdrawal()))).toList()),
                        table(List.of("Year", "Federal tax", "State tax", "RMD", "Roth conversion", "After-tax estate"), original.years().stream().map(y -> List.of(
                                "" + y.year(), money(y.federalTax()), money(y.stateTax()), money(y.rmd()), money(y.roth()), money(y.estate()))).toList()),
                        label(String.join("\n", original.warnings())));
            });
            choices.getSelectionModel().selectFirst();
            var pane = new TitledPane("Original retained annual projections", content); pane.setExpanded(false); getChildren().add(pane);
        }
        IntegratedAnalyzerReport report() {
            var r = prepared.reports().capture(age, integrated == null ? ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL : integrated.metric.getValue(),
                    integrated == null ? null : integrated.reportSelection(), pair);
            var context = new ArrayList<>(r.context()); context.add("Original saved analyzer result | Projection association | Completed " + saved.completedAt());
            context.add(durability);
            var assumptions = new ArrayList<>(r.assumptions());
            saved.context().request().forEach(s -> assumptions.add(new IntegratedAnalyzerReport.Section("Original request — " + s.title(), s.lines())));
            saved.context().comparisons().forEach(s -> assumptions.add(new IntegratedAnalyzerReport.Section("Original comparison context — " + s.title(), s.lines())));
            return new IntegratedAnalyzerReport(r.analysisType(), r.household(), r.runStarted(), r.exportedAt(), context,
                    r.selectedSummary(), r.visualization(), assumptions, r.rankedTables(), r.results());
        }
        void refresh() {
            var r = report(); text.getChildren().setAll(label(r.analysisType() + " — " + r.household()), label(String.join("\n", r.selectedSummary())));
            if (r.visualization() instanceof IntegratedAnalyzerReport.Individual individual) {
                var tiles = new FlowPane(6, 6);
                for (var a : individual.ages()) {
                    var button = new Button(a.age() + "\n" + a.value() + "\n" + (a.optimal() ? "★ Optimal " : "") + (a.current() ? "Original current" : ""));
                    button.setWrapText(true); button.setPrefWidth(132); button.setMinHeight(70);
                    button.setStyle("-fx-background-color:" + a.tier().color + ";-fx-text-fill:#142536;-fx-border-width:2;-fx-border-color:"
                            + (a.selected() ? com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette.SELECTED_BORDER
                            : a.optimal() ? com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette.OPTIMAL_BORDER : "#79889a") + ";");
                    button.setAccessibleText(a.age() + " " + a.value() + " " + a.percentOfOptimal() + (a.optimal() ? " Optimal" : "") + (a.selected() ? " Selected" : ""));
                    button.setTooltip(new Tooltip(button.getAccessibleText())); button.setOnAction(e -> { age = a.age(); refresh(); }); tiles.getChildren().add(button);
                }
                text.getChildren().add(tiles);
                text.getChildren().add(table(List.of("Age", individual.metric(), "% optimum", "Markers"), individual.ages().stream()
                        .map(a -> List.of("" + a.age(), a.value(), a.percentOfOptimal(), (a.optimal() ? "Optimal " : "") + (a.current() ? "Original current " : "") + (a.selected() ? "Selected" : ""))).toList()));
            }
            for (var table : r.rankedTables()) text.getChildren().add(new TitledPane(table.title(), table(table.columns().stream().map(IntegratedAnalyzerReport.Column::title).toList(), table.rows())));
            for (var section : java.util.stream.Stream.concat(r.assumptions().stream(), r.results().stream()).toList())
                text.getChildren().add(new TitledPane(section.title(), label(String.join("\n", section.lines()))));
        }
    }

    private static TableView<List<String>> table(List<String> headings, List<List<String>> rows) {
        var table = new TableView<List<String>>(); table.getItems().setAll(rows); table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setPrefHeight(Math.min(420, 65 + rows.size() * 27));
        for (int i = 0; i < headings.size(); i++) {
            int index = i; var col = new TableColumn<List<String>, String>(headings.get(i));
            col.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().get(index))); col.setSortable(false); col.setReorderable(false);
            col.setCellFactory(v -> new TableCell<>() {
                @Override protected void updateItem(String value, boolean empty) { super.updateItem(value, empty);
                    setText(empty ? null : value); setTooltip(empty ? null : new Tooltip(value)); }
            }); table.getColumns().add(col);
        }
        return table;
    }
    private static Label label(String value) { var label = new Label(value); label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE); return label; }
    private static String money(java.math.BigDecimal value) { return value == null ? "Unavailable" : com.daviddunn.retirementplanner.ui.util.UIFormatters.money(value); }
}
