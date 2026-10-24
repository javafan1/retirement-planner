package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonService;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyResult;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchCalculator;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchRequest;


import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration;

/** Resizable, read-only Social Security strategy analysis window. */
public final class SocialSecurityStrategyAnalyzerDialog {
    private final Button deterministicExportPdf = new Button("Export PDF");
    private IntegratedReportContext deterministicReportContext;
    private IntegratedReportContext weightedReportContext;
    private IntegratedSocialSecurityCompleteStrategySearchEntry deterministicSelectedEntry;
    private boolean exportBusy;
    java.util.function.Function<com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonRequest,
            com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonResult> weightedCalculation =
            request -> new com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonService().compare(request);
    private WeightedResultPersistence weightedPersistence;
    java.util.function.Function<SocialSecuritySurvivorClaimingOptimizationRequest,
            SocialSecurityAnalyzerJobController.Work<SocialSecuritySurvivorClaimingOptimizationResult>> socialSecurityCalculation =
            request -> (progress, cancellation) -> new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(request, progress, cancellation);
    private WeightedResultPersistence socialSecurityPersistence;
    private WeightedResultPersistence exhaustivePersistence;
    java.util.function.Function<SocialSecurityAnalyzerInputs.Quick, SocialSecurityAnalyzerJobController.Work<IntegratedSocialSecurityComparisonPresentation>> quickCalculation =
            input -> (progress, cancellation) -> IntegratedSocialSecurityComparisonPresentation.from(
                    new IntegratedSocialSecurityStrategyComparisonService().compareAnalyzerCandidates(input.plan(),
                            input.selected().stream().map(SocialSecurityStrategyAnalyzerPresentation.RankedStrategy::cell).toList(), progress, cancellation), input.selected());
    private ExhaustiveSnapshot.CrossReference historicalExhaustiveReference;
    java.util.function.Function<IntegratedSocialSecurityCompleteStrategySearchRequest,
            SocialSecurityAnalyzerJobController.Work<com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchResult>> exhaustiveCalculation =
            request -> (progress, cancellation) -> new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request, progress, cancellation);
    private VBox socialSecurityWorkspace;
    private VBox exhaustiveWorkspace;
    private com.daviddunn.retirementplanner.ui.controller.ApplicationController analysisSource;
    private List<IntegratedAnalysisComparisonPresentation.Row> historicalWeightedComparison;
    private java.util.function.Function<SocialSecurityHouseholdClaimingStrategy, java.util.OptionalInt> historicalWeightedRanks;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu");

    private RetirementPlan plan;
    private final Stage stage = new Stage();
    private final TextField primaryMortalityAdjustment = new TextField("1.00");
    private final TextField spouseMortalityAdjustment = new TextField("1.00");
    private final TextField discountRate = new TextField("1.0");
    private final DatePicker pvDate;
    private final DatePicker mortalityDate;
    private final SocialSecurityAnalyzerInputView inputSummary = new SocialSecurityAnalyzerInputView();
    private final TitledPane inputSection = new TitledPane("Analysis Inputs and Plan Assumptions", inputSummary);
    private final VBox header = new VBox(8);
    private final VBox sharedConfiguration = new VBox(6);
    private final Label inheritedPlan = AnalyzerConfigurationLayout.note("");
    private SocialSecurityAnalyzerInputSummary currentInputSummary;
    private AnalyzerNavigationView navigation;
    private final Label quickPrerequisite = new Label();
    private final Button goToSocialSecurity = new Button("Go to Social Security Only");
    private final Button runButton = new Button("Run Social Security Analysis");
    private final AnalysisProgressView socialSecurityProgress = new AnalysisProgressView();
    private final Label status = new Label("Mortality categories come from Person information.");
    private final Label stale = new Label();
    private final VBox recommendation = new VBox(8);
    private final GridPane claimingGrid = new GridPane();
    private final Label gridDetails = new Label("Select a retirement-grid cell.");
    private final TableView<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> strategies =
            new TableView<>();
    private final Label strategyDetails = new Label("Select a complete strategy.");
    private final TextArea assumptions = new TextArea();
    private final Spinner<Integer> integratedCandidateCount = new Spinner<>(1, 20, 10);
    private final Button integratedRunButton = new Button("Run Quick Comparison");
    private final AnalysisProgressView integratedProgress = new AnalysisProgressView();
    private final Label integratedStatus = new Label(
            "Run Social Security analysis first, then run the integrated comparison.");
    private final Label integratedStale = new Label();
    private final VBox integratedBaseline = new VBox(5);
    private final TableView<IntegratedSocialSecurityComparisonPresentation.Row> integratedTable =
            new TableView<>();
    private final TextArea integratedDetails = new TextArea();
    private final TextArea integratedMethodology = new TextArea();
    private final Button exhaustiveRunButton = new Button("Run Deterministic Exhaustive Search");
    private final LongevityWeightedIntegratedView weightedView = new LongevityWeightedIntegratedView();
    private final CurrentStrategyBaselineView baselineInputs;
    private final AnalysisProgressView sharedProgress = new AnalysisProgressView();
    private final Label sharedStatus = new Label();
    private final Button sharedCancel = new Button("Cancel");
    private LongevityWeightedIntegratedPresentation weightedPresentation;
    private long planRevision;
    private long assumptionsRevision;
    private long exhaustiveRevision = -1;
    private boolean weightedCurrent;
    private final Button exhaustiveCancelButton = new Button("Cancel");
    private final AnalysisProgressView exhaustiveProgress = new AnalysisProgressView();
    private final Label exhaustiveStatus = new Label(
            "Run exhaustive search to evaluate the complete tested strategy universe.");
    private final Label exhaustiveStale = new Label();
    private final VBox exhaustiveSummary = new VBox(5);
    private final Label exhaustiveSocialSecurityReference = new Label();
    private final TableView<ExhaustiveIntegratedSearchPresentation.Group> exhaustiveTable =
            new TableView<>();
    private final TextArea exhaustiveDetails = new TextArea();
    private final VBox deterministicRankedContent = new VBox(10);
    private final ClaimingStrategyHeatMapView deterministicHeatMap = new ClaimingStrategyHeatMapView(
            ClaimingStrategyHeatMapProfile.deterministic(), this::openDeterministicHeatMapStrategy);
    private final Tab deterministicRankedTab = new Tab("Ranked Strategies", deterministicRankedContent);
    private final Tab deterministicHeatMapTab = new Tab("Claiming-Age Heat Map", deterministicHeatMap);
    private final TabPane deterministicResultTabs = new IntegratedAnalysisResultTabs(deterministicRankedTab, deterministicHeatMapTab);
    private ExhaustiveIntegratedSearchPresentation deterministicHeatMapSource;
    private final SocialSecurityAnalyzerJobController jobs;
    private Runnable detachPlanListener = () -> { };
    private final java.util.List<Runnable> detachInputListeners = new java.util.ArrayList<>();
    private final Button socialSecurityCancelButton = new Button("Cancel");
    private final Button integratedCancelButton = new Button("Cancel");
    private SocialSecurityStrategyAnalyzerPresentation presentation;
    private IntegratedSocialSecurityComparisonPresentation integratedPresentation;
    private ExhaustiveIntegratedSearchPresentation exhaustivePresentation;
    private boolean socialSecurityResultCurrent;
    private SinglePersonIntegratedView singlePersonView;
    private final java.util.EnumMap<AnalysisKind, SocialSecurityParameterBinding> parameterBindings = new java.util.EnumMap<>(AnalysisKind.class);
    private int acceptedSsTop = SocialSecuritySurvivorOptimizationSettings.DEFAULT_TOP_RETIREMENT_CANDIDATES;
    private AnalysisParameterConfiguration.Search acceptedExhaustiveProfile;
    private AnalysisParameterConfiguration.Mortality coupleParameterDefaults;

    public SocialSecurityStrategyAnalyzerDialog(Window owner, RetirementPlan plan) {
        this(owner, plan, new SocialSecurityAnalyzerJobController(
                com.daviddunn.retirementplanner.ui.MainApplication.analysisJobs()));
    }

    SocialSecurityStrategyAnalyzerDialog(Window owner, RetirementPlan plan,
            SocialSecurityAnalyzerJobController jobs) {
        this.plan = plan;
        this.jobs = jobs;
        baselineInputs = plan.getHousehold().hasSpouse() ? new CurrentStrategyBaselineView(plan) : null;
        if (baselineInputs != null) baselineInputs.onChanged(this::baselineChanged);
        pvDate = new DatePicker(plan.getPlanningAssumptions().getProjectionStartDate());
        mortalityDate = new DatePicker(pvDate.getValue());
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Social Security Strategy Analyzer");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setWidth(1180);
        stage.setHeight(820);
        if (plan.getHousehold().hasSpouse()) {
            stage.setScene(new Scene(content()));
        } else {
            singlePersonView = new SinglePersonIntegratedView(plan, jobs);
            stage.setScene(new Scene(singlePersonView));
        }
        stage.setOnCloseRequest(event -> close());
        stage.setOnHidden(event -> close());
        if (singlePersonView == null) {
            jobs.onChanged(this::refreshJobState);
            refreshJobState();
        }
    }

    public SocialSecurityStrategyAnalyzerDialog(Window owner,
            com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        this(owner, source, new SocialSecurityAnalyzerJobController(com.daviddunn.retirementplanner.ui.MainApplication.analysisJobs()));
    }
    SocialSecurityStrategyAnalyzerDialog(Window owner,
            com.daviddunn.retirementplanner.ui.controller.ApplicationController source, SocialSecurityAnalyzerJobController jobs) {
        this(owner, source.getCurrentPlan(), jobs);
        analysisSource = source;
        if (singlePersonView != null) {
            singlePersonView.attachParameters(source);
            IndividualCompletionPersistence.attach(singlePersonView.weightedView, source);
            singlePersonView.navigation.onSelected(mode -> {
                if (mode == SocialSecurityAnalyzerJobController.Mode.WEIGHTED) singlePersonView.weightedView.persistence.load();
            });
            if (!source.canPersistAnalysisResults()) {
                var notice = new Label("This plan is not saved. Analyses remain runnable in memory; save the plan before running a longevity-weighted analysis intended for persistence.");
                notice.setWrapText(true);
                singlePersonView.getChildren().addFirst(notice);
                singlePersonView.ssView.persistenceNotice.setText("Social Security Only results remain in memory; persistent restoration is not enabled for this analysis.");
                singlePersonView.ssView.persistenceNotice.setWrapText(true);
            }
            detachPlanListener = source.addSourcePlanRevisionListener(() -> singlePersonView.weightedView.persistence.sourceChanged(() -> {
                if (source.getCurrentPlan().getHousehold().hasSpouse()) { close(); stage.close(); return; }
                plan = source.getCurrentPlan();
                singlePersonView.invalidate(plan);
            }));
            return;
        }
        if (!source.canPersistAnalysisResults()) {
            status.setText("Save the retirement plan before running a longevity-weighted analysis intended for persistence. Analyses remain runnable in memory; no plan is autosaved.");
        }
        var sharedLongevity = source.getLongevitySessionSettings();
        mortalityDate.setValue(sharedLongevity.conditioningDate());
        primaryMortalityAdjustment.setText(sharedLongevity.primaryAdjustment().factor().toPlainString());
        spouseMortalityAdjustment.setText(sharedLongevity.spouseAdjustment().factor().toPlainString());
        Runnable shareLongevity = () -> {
            try {
                source.setLongevitySessionSettings(new LongevitySessionSettings(mortalityDate.getValue(),
                        adjustment(primaryMortalityAdjustment.getText(), "Primary"), adjustment(spouseMortalityAdjustment.getText(), "Spouse")));
            } catch (IllegalArgumentException | NullPointerException incompleteInput) {
                // Keep the last valid session selection while a field is being edited.
            }
        };
        observe(mortalityDate.valueProperty(), shareLongevity);
        observe(primaryMortalityAdjustment.textProperty(), shareLongevity);
        observe(spouseMortalityAdjustment.textProperty(), shareLongevity);
        detachPlanListener = source.addSourcePlanRevisionListener(() -> exhaustivePersistence.sourceChanged(() -> socialSecurityPersistence.sourceChanged(() -> weightedPersistence.sourceChanged(() -> {
            if (!source.getCurrentPlan().getHousehold().hasSpouse()) { close(); stage.close(); return; }
            boolean differentPlan = plan != source.getCurrentPlan();
            plan = source.getCurrentPlan();
            if (differentPlan) {
                var defaults = source.getLongevitySessionSettings();
                mortalityDate.setValue(defaults.conditioningDate());
                primaryMortalityAdjustment.setText(defaults.primaryAdjustment().factor().toPlainString());
                spouseMortalityAdjustment.setText(defaults.spouseAdjustment().factor().toPlainString());
                baselineInputs.load(plan);
                if (plan.getHousehold().hasSpouse()) coupleParameterDefaults = defaultCoupleParameters(source);
                parameterBindings.values().forEach(SocialSecurityParameterBinding::planChanged);
            }
            baselineInputs.load(plan);
            if (weightedPresentation == null) weightedView.initialCurrent(baselineInputs.snapshot());
            planRevision++;
            jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
            markStale();
            if (exhaustivePresentation != null) {
                exhaustiveStale.setText("Plan changed - run exhaustive search again.");
            }
        }))));
        weightedPersistence = new WeightedResultPersistence(source, AnalysisKind.COUPLE_WEIGHTED,
                this::weightedInputs, saved -> {
                    var model = new LongevityWeightedIntegratedPresentation(saved.payload().couple().restore(), 0, 0);
                    var context = saved.context();
                    var b = context.baseline();
                    var baseline = new CurrentStrategyBaseline(b.elections().stream()
                            .map(e -> new CurrentStrategyBaseline.Election(e.name(), e.value(), e.source())).toList(),
                            java.util.Optional.ofNullable(b.strategy()).map(WeightedSnapshots.Strategy::restore), b.problem());
                    var report = new IntegratedReportContext(context.household(), context.primary(), context.spouse(), context.startedAt(),
                            context.assumptions().stream().map(s -> new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section(s.title(), s.lines())).toList(),
                            baseline.summary() + "\n" + baseline.problem());
                    var comparisons = context.historicalComparisons().stream().filter(c -> !c.explanation().equals("Frozen strategy-detail ranks"))
                            .flatMap(c -> c.rows().stream())
                            .map(AnalysisSnapshotPresentationMapper::restore).toList();
                    var ranks = AnalysisSnapshotPresentationMapper.ranks(context.historicalComparisons().stream()
                            .filter(c -> c.explanation().equals("Frozen strategy-detail ranks")).flatMap(c -> c.rows().stream()).toList());
                    return () -> {
                        weightedPresentation = model;
                        weightedReportContext = report;
                        weightedCurrent = false;
                        historicalWeightedComparison = comparisons;
                        historicalWeightedRanks = ranks;
                        weightedView.render(model, baseline);
                        weightedView.comparisons(comparisons);
                        weightedView.deterministicReference(null);
                        weightedView.historicalRanks(historicalWeightedRanks);
                        weightedView.resultTabs.setVisible(true);
                        weightedView.resultTabs.setManaged(true);
                        var preferences = context.preferences();
                        if (preferences.containsKey("metric")) {
                            var metric = ClaimingStrategyHeatMapMetric.valueOf(preferences.get("metric"));
                            if (weightedView.heatMap.metric.getItems().contains(metric)) weightedView.heatMap.metric.setValue(metric);
                        }
                        if (preferences.containsKey("occurrence")) {
                            int occurrence = Integer.parseInt(preferences.get("occurrence"));
                            model.result().orderedEntries().stream().filter(e -> e.inputOrder() == occurrence).findFirst()
                                    .ifPresent(entry -> {
                                        weightedView.table.getSelectionModel().select(entry);
                                        weightedView.heatMap.restoreStrategy(entry.strategy());
                                    });
                        }
                        if ("heat-map".equals(preferences.get("tab"))) weightedView.resultTabs.getSelectionModel().select(weightedView.heatMapTab);
                    };
                }, this::refreshWeightedResultStatus, () -> {
                    weightedPresentation = null;
                    weightedReportContext = null;
                    historicalWeightedComparison = null;
                    historicalWeightedRanks = null;
                    weightedView.table.getItems().clear();
                    weightedView.resultTabs.setVisible(false);
                    weightedView.resultTabs.setManaged(false);
                    weightedView.exportPdf.setDisable(true);
                    weightedView.status.setText("No saved result loaded for this plan.");
                    weightedView.stale.setText("");
                });
        weightedView.getChildren().add(5, weightedPersistence.controls);
        socialSecurityPersistence = new WeightedResultPersistence(source, AnalysisKind.COUPLE_SOCIAL_SECURITY,
                this::socialSecurityInputs, saved -> {
                    var payload = (CoupleSocialSecuritySnapshot) saved.payload();
                    var context = CoupleSocialSecuritySnapshotMapper.context(payload);
                    var model = SocialSecurityStrategyAnalyzerPresentation.from(payload.restore());
                    return () -> {
                        presentation = model;
                        render(context, model);
                        var occurrence = saved.context().preferences().get("occurrence");
                        if (occurrence != null) {
                            int index = Integer.parseInt(occurrence) - 1;
                            if (index < model.result().evaluatedStrategies().size()) {
                                var cell = model.result().evaluatedStrategies().get(index);
                                model.rankedStrategies().stream().filter(row -> row.cell().equals(cell)).findFirst()
                                        .ifPresent(row -> strategies.getSelectionModel().select(row));
                            }
                        }
                        status.setText("Previous Social Security analysis restored without recalculation.");
                    };
                }, this::refreshSocialSecurityResultStatus, () -> {
                    presentation = null;
                    socialSecurityResultCurrent = false;
                    recommendation.getChildren().setAll(new Label("Run analysis to identify the highest expected-PV tested strategy."));
                    claimingGrid.getChildren().clear(); strategies.getItems().clear();
                    assumptions.clear(); gridDetails.setText("Select a retirement-grid cell.");
                    strategyDetails.setText("Select a complete strategy."); stale.setText("");
                });
        socialSecurityWorkspace.getChildren().add(5, socialSecurityPersistence.controls);
        exhaustivePersistence = new WeightedResultPersistence(source, AnalysisKind.COUPLE_EXHAUSTIVE,
                AnalysisFingerprintService.Inputs::none, saved -> {
                    var restored = ExhaustiveResultSnapshots.restore((ExhaustiveSnapshot) saved.payload());
                    return () -> {
                        exhaustivePresentation = restored.model();
                        deterministicReportContext = restored.report();
                        historicalExhaustiveReference = restored.reference();
                        // Passive historical loading is not a new source revision for cross-analysis comparisons.
                        exhaustiveRevision = -1;
                        renderExhaustive(restored.model());
                        var selection = restored.selection();
                        if (selection != null) {
                            deterministicHeatMap.metric.setValue(ClaimingStrategyHeatMapMetric.valueOf(selection.metric()));
                            if (selection.occurrence() != null) {
                                var entry = restored.model().result().entries().stream()
                                        .filter(e -> e.generationOrder() == selection.occurrence()).findFirst().orElseThrow();
                                deterministicHeatMap.restoreStrategy(entry.strategy());
                                exhaustiveTable.getItems().stream().filter(g -> g.representative().generationOrder() == entry.generationOrder())
                                        .findFirst().ifPresent(g -> exhaustiveTable.getSelectionModel().select(g));
                                deterministicSelectedEntry = entry;
                                exhaustiveDetails.setText(exhaustiveDetail(entry, "\nOriginal strategy occurrence: " + entry.generationOrder()));
                            }
                            if ("heat-map".equals(selection.tab())) deterministicResultTabs.getSelectionModel().select(deterministicHeatMapTab);
                        }
                        exhaustiveStatus.setText("Previous exhaustive analysis restored without recalculation.");
                    };
                }, this::refreshExhaustiveResultStatus, () -> {
                    exhaustivePresentation = null; deterministicReportContext = null;
                    historicalExhaustiveReference = null; deterministicHeatMapSource = null;
                    deterministicSelectedEntry = null; exhaustiveRevision = -1;
                    exhaustiveTable.getItems().clear(); exhaustiveSummary.getChildren().clear();
                    deterministicResultTabs.setVisible(false); deterministicResultTabs.setManaged(false);
                    deterministicExportPdf.setDisable(true); exhaustiveDetails.clear(); exhaustiveStale.setText("");
                });
        exhaustiveWorkspace.getChildren().add(5, exhaustivePersistence.controls);
        observe(mortalityDate.getEditor().textProperty(), () -> { WeightedResultPersistence.dateEdited(mortalityDate); weightedPersistence.reassess(); socialSecurityPersistence.reassess(); });
        observe(pvDate.getEditor().textProperty(), () -> { WeightedResultPersistence.dateEdited(pvDate); weightedPersistence.reassess(); socialSecurityPersistence.reassess(); });
        attachCoupleParameters(source);
        socialSecurityPersistence.load();
    }

    private boolean applyingParameters() { return parameterBindings.values().stream().anyMatch(SocialSecurityParameterBinding::applying); }
    private SocialSecurityParameterBinding.Run parameterRun(AnalysisKind kind, AnalysisParameterConfiguration.Parameters values) {
        return parameterBindings.containsKey(kind) ? parameterBindings.get(kind).begin(values) : null;
    }
    private void acceptParameters(AnalysisKind kind, SocialSecurityParameterBinding.Run run) {
        if (run != null) parameterBindings.get(kind).accept(run);
    }
    private void rollbackParameters(AnalysisKind kind, SocialSecurityParameterBinding.Run run) {
        if (run != null) parameterBindings.get(kind).rollback(run);
    }
    private void refreshParameterInputs() {
        if (analysisSource != null && plan.getHousehold().hasSpouse()) {
            try { analysisSource.setLongevitySessionSettings(new LongevitySessionSettings(mortalityDate.getValue(),
                    adjustment(primaryMortalityAdjustment.getText(), "Primary"), adjustment(spouseMortalityAdjustment.getText(), "Spouse"))); }
            catch (IllegalArgumentException | NullPointerException incomplete) { /* Only valid values propagate. */ }
        }
        refreshInputSummary();
        if (socialSecurityPersistence != null) socialSecurityPersistence.reassess();
        if (weightedPersistence != null) weightedPersistence.reassess();
        refreshQuickPrerequisite();
    }
    private void attachCoupleParameters(com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        coupleParameterDefaults = defaultCoupleParameters(source);
        for (var kind : List.of(AnalysisKind.COUPLE_SOCIAL_SECURITY, AnalysisKind.COUPLE_QUICK,
                AnalysisKind.COUPLE_EXHAUSTIVE, AnalysisKind.COUPLE_WEIGHTED)) {
            var binding = new SocialSecurityParameterBinding(source, kind, values -> {
                if (values instanceof AnalysisParameterConfiguration.Mortality m) {
                    primaryMortalityAdjustment.setText(m.primaryFactor().toPlainString());
                    spouseMortalityAdjustment.setText(m.spouseFactor().toPlainString());
                    SocialSecurityParameterMapping.date(mortalityDate, m.conditioningDate()); SocialSecurityParameterMapping.date(pvDate, m.valuationDate());
                    discountRate.setText(SocialSecurityParameterMapping.percent(m.realDiscountRate()));
                    if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY) {
                        if (!m.optimizerProfile().startsWith("couple-ss-v1:top=")) throw new IllegalArgumentException("Unsupported optimization profile");
                        acceptedSsTop = new SocialSecuritySurvivorOptimizationSettings(Integer.parseInt(m.optimizerProfile().substring("couple-ss-v1:top=".length()))).topRetirementCandidates();
                    } else {
                        if (!SocialSecurityParameterMapping.WEIGHTED.equals(m.optimizerProfile())) throw new IllegalArgumentException("Unsupported optimization profile");
                        baselineInputs.restoreParameters(m.baseline());
                    }
                    source.setLongevitySessionSettings(new LongevitySessionSettings(m.conditioningDate(),
                            adjustment(primaryMortalityAdjustment.getText(), "Primary"), adjustment(spouseMortalityAdjustment.getText(), "Spouse")));
                } else if (values instanceof AnalysisParameterConfiguration.Quick q) integratedCandidateCount.getValueFactory().setValue(q.candidateCount());
                else {
                    var s = (AnalysisParameterConfiguration.Search) values;
                    if (!"couple-full-v1".equals(s.profile())) throw new IllegalArgumentException("Unsupported search profile");
                    acceptedExhaustiveProfile = s;
                }
            }, () -> {
                if (kind == AnalysisKind.COUPLE_QUICK) integratedCandidateCount.getValueFactory().setValue(10);
                else if (kind == AnalysisKind.COUPLE_EXHAUSTIVE) acceptedExhaustiveProfile = null;
                else {
                    var settings = coupleParameterDefaults;
                    primaryMortalityAdjustment.setText(settings.primaryFactor().toPlainString());
                    spouseMortalityAdjustment.setText(settings.spouseFactor().toPlainString());
                    SocialSecurityParameterMapping.date(mortalityDate, settings.conditioningDate()); SocialSecurityParameterMapping.date(pvDate, settings.valuationDate());
                    discountRate.setText(SocialSecurityParameterMapping.percent(settings.realDiscountRate()));
                    acceptedSsTop = SocialSecuritySurvivorOptimizationSettings.DEFAULT_TOP_RETIREMENT_CANDIDATES;
                    if (kind == AnalysisKind.COUPLE_WEIGHTED) baselineInputs.restoreParameters(null);
                }
            }, this::refreshParameterInputs, () -> navigation.selected().mode() == switch (kind) {
                case COUPLE_SOCIAL_SECURITY -> SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY;
                case COUPLE_QUICK -> SocialSecurityAnalyzerJobController.Mode.QUICK;
                case COUPLE_EXHAUSTIVE -> SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE;
                default -> SocialSecurityAnalyzerJobController.Mode.WEIGHTED;
            });
            parameterBindings.put(kind, binding);
            if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY) socialSecurityWorkspace.getChildren().add(5, binding.controls);
            else if (kind == AnalysisKind.COUPLE_WEIGHTED) weightedView.getChildren().add(5, binding.controls);
            else if (kind == AnalysisKind.COUPLE_EXHAUSTIVE) exhaustiveWorkspace.getChildren().add(5, binding.controls);
        }
        // Quick's workspace has its own notice; no new result persistence is introduced.
        var quick = (VBox) navigation.choices().stream().filter(c -> c.mode() == SocialSecurityAnalyzerJobController.Mode.QUICK).findFirst().orElseThrow().workspace();
        quick.getChildren().add(3, parameterBindings.get(AnalysisKind.COUPLE_QUICK).controls);
        navigation.onSelected(mode -> {
            refreshContextualInputs();
            var kind = switch (mode) {
                case SOCIAL_SECURITY -> AnalysisKind.COUPLE_SOCIAL_SECURITY;
                case QUICK -> AnalysisKind.COUPLE_QUICK;
                case EXHAUSTIVE -> AnalysisKind.COUPLE_EXHAUSTIVE;
                case WEIGHTED -> AnalysisKind.COUPLE_WEIGHTED;
            };
            parameterBindings.get(kind).restore();
        });
    }
    private AnalysisParameterConfiguration.Mortality defaultCoupleParameters(com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        var settings = source.getLongevitySessionSettings();
        return new AnalysisParameterConfiguration.Mortality(settings.primaryAdjustment().factor(), settings.spouseAdjustment().factor(),
                settings.conditioningDate(), plan.getPlanningAssumptions().getProjectionStartDate(), new BigDecimal("0.010"),
                "couple-ss-v1:top=5", null);
    }
    private void refreshExhaustiveResultStatus() {
        if (exhaustivePresentation != null && exhaustivePersistence != null)
            exhaustiveStale.setText(exhaustivePersistence.current() ? "" : WeightedResultPersistence.OUTDATED);
        refreshExportActions();
    }
    private AnalysisFingerprintService.Inputs socialSecurityInputs() {
        return new AnalysisFingerprintService.Inputs(adjustment(primaryMortalityAdjustment.getText(), "Primary").factor(),
                adjustment(spouseMortalityAdjustment.getText(), "Spouse").factor(), WeightedResultPersistence.date(mortalityDate),
                WeightedResultPersistence.date(pvDate), WeightedResultPersistence.rate(discountRate),
                null, null, List.of(), List.of(), null);
    }
    private void refreshSocialSecurityResultStatus() {
        socialSecurityResultCurrent = presentation != null && socialSecurityPersistence != null && socialSecurityPersistence.current();
        if (presentation != null) stale.setText(socialSecurityResultCurrent ? "" : WeightedResultPersistence.OUTDATED);
        refreshQuickPrerequisite();
        integratedRunButton.setDisable(jobs.state() != SocialSecurityAnalyzerJobController.State.IDLE || !socialSecurityResultCurrent);
    }
    private AnalysisFingerprintService.Inputs weightedInputs() {
        var baseline = baselineInputs.snapshot();
        var rows = baseline.elections().stream().map(e -> new SnapshotContext.Section(e.name(), List.of(e.value(), e.source()))).toList();
        return new AnalysisFingerprintService.Inputs(adjustment(primaryMortalityAdjustment.getText(), "Primary").factor(),
                adjustment(spouseMortalityAdjustment.getText(), "Spouse").factor(), WeightedResultPersistence.date(mortalityDate),
                WeightedResultPersistence.date(pvDate), WeightedResultPersistence.rate(discountRate),
                null, null, List.of(), rows, null);
    }
    private void refreshWeightedResultStatus() {
        if (weightedPersistence != null && weightedPersistence.exportable()) {
            weightedView.stale.setText(weightedPersistence.current() ? "" : WeightedResultPersistence.OUTDATED);
        }
        refreshExportActions();
    }
    public void show() {
        stage.showAndWait();
    }

    private BorderPane content() {
        stale.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
        integratedStale.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
        exhaustiveStale.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
        integratedRunButton.setDisable(true);
        exhaustiveCancelButton.setVisible(false);
        exhaustiveCancelButton.setManaged(false);
        runButton.setOnAction(event -> runAnalysis());
        integratedRunButton.setOnAction(event -> runIntegratedAnalysis());
        exhaustiveRunButton.setOnAction(event -> runExhaustiveSearch());
        weightedView.run.setOnAction(event -> runWeightedAnalysis());
        deterministicExportPdf.setDisable(true);
        deterministicExportPdf.setOnAction(event -> exportPdf(false));
        weightedView.exportPdf.setOnAction(event -> exportPdf(true));
        exhaustiveStale.textProperty().addListener((o, before, after) -> refreshExportActions());
        weightedView.stale.textProperty().addListener((o, before, after) -> refreshExportActions());
        sharedCancel.setOnAction(event -> jobs.cancel());
        exhaustiveCancelButton.setOnAction(event -> jobs.cancel());
        socialSecurityCancelButton.setOnAction(event -> jobs.cancel());
        integratedCancelButton.setOnAction(event -> jobs.cancel());
        observe(primaryMortalityAdjustment.textProperty(), this::markStale);
        observe(spouseMortalityAdjustment.textProperty(), this::markStale);
        observe(discountRate.textProperty(), this::markStale);
        observe(mortalityDate.valueProperty(), () -> markStale(SocialSecurityAnalyzerJobController.Change.MORTALITY_CONDITIONING_DATE));
        observe(pvDate.valueProperty(), () -> markStale(SocialSecurityAnalyzerJobController.Change.VALUATION_DATE));
        observe(integratedCandidateCount.valueProperty(), () -> {
            jobs.invalidate(SocialSecurityAnalyzerJobController.Change.QUICK_CANDIDATES);
            markIntegratedStale("Candidate count changed - run integrated analysis again.");
        });

        inputSection.setExpanded(false);
        inputSection.setAnimated(false);
        inputSection.setMinHeight(Region.USE_PREF_SIZE);
        inputSection.setMaxHeight(Region.USE_PREF_SIZE);
        refreshInputSummary();
        header.getChildren().setAll(
                AnalyzerConfigurationLayout.heading("From Retirement Plan"), inheritedPlan, inputSection, sharedConfiguration);
        inputSection.setText("Input Reference — Complete Dependencies and Plan Assumptions");
        sharedConfiguration.getChildren().setAll(AnalyzerConfigurationLayout.heading("Inputs for Next Run"),
                wrappedLabel("Required temporary analyzer settings. Mortality conditioning and valuation are independent dates; neither changes the plan's projection start."),
                inputs());
        header.setPadding(new Insets(12));

        prepareWeightedBaseline();
        navigation = new AnalyzerNavigationView(header, List.of(
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY,
                        "Social Security Only", "Compare claiming choices by expected Social Security present value.", socialSecurityContent()),
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.QUICK,
                        "Deterministic Quick Comparison", "Evaluate top Social Security-only strategies through your retirement plan.", quickComparisonContent()),
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE,
                        "Deterministic Exhaustive Search", "Test all supported strategies using configured plan assumptions.", exhaustiveSearchContent()),
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.WEIGHTED,
                        "Longevity-Weighted Search", "Rank expected PV after-tax investable estate across modeled lifetimes.", weightedView)));
        navigation.onSelected(mode -> refreshContextualInputs());
        VBox activeExecution = new VBox(4, AnalyzerConfigurationLayout.flow(sharedCancel, sharedStatus), sharedProgress);
        activeExecution.managedProperty().bind(sharedCancel.managedProperty());
        activeExecution.visibleProperty().bind(activeExecution.managedProperty());
        navigation.addExecutionStatus(activeExecution);
        BorderPane root = navigation;
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> { close(); stage.close(); });
        Label scope = new Label(
                "Read-only analysis. No strategy is applied to or saved in the active retirement plan.");
        HBox footer = new HBox(12, scope, closeButton);
        HBox.setHgrow(scope, Priority.ALWAYS);
        footer.setPadding(new Insets(10));
        root.setBottom(footer);
        return root;
    }

    private VBox socialSecurityContent() {
        recommendation.setPadding(new Insets(16));
        recommendation.getChildren().add(new Label(
                "Run analysis to identify the highest expected-PV tested strategy."));
        configureStrategyTable();
        assumptions.setEditable(false);
        assumptions.setWrapText(true);

        ScrollPane gridScroll = new ScrollPane(new VBox(10, claimingGrid, gridDetails));
        gridScroll.setFitToWidth(true);
        VBox strategyBox = new VBox(10, strategies, strategyDetails);
        VBox.setVgrow(strategies, Priority.ALWAYS);
        TabPane socialSecurityTabs = new TabPane(
                tab("Recommendation", new ScrollPane(recommendation)),
                tab("Claiming Grid", gridScroll),
                tab("Top Strategies", strategyBox),
                tab("Assumptions", assumptions));
        VBox content = new VBox(8,
                AnalyzerConfigurationLayout.heading("Run / Cancel / Progress"),
                AnalyzerConfigurationLayout.flow(runButton, socialSecurityCancelButton), status, socialSecurityProgress, stale,
                AnalyzerConfigurationLayout.heading("Completed Results"),
                wrappedLabel("Completed Result Assumptions are retained in the result-time Assumptions tab; next-run edits do not rewrite them."), socialSecurityTabs);
        status.setWrapText(true);
        content.setPadding(new Insets(12));
        VBox.setVgrow(socialSecurityTabs, Priority.ALWAYS);
        socialSecurityWorkspace = content;
        return content;
    }

    private void prepareWeightedBaseline() {
        var baselineSection = new TitledPane("Current Strategy Baseline — Optional Comparison Settings", baselineInputs);
        baselineSection.setAnimated(false);
        baselineSection.setMinHeight(Region.USE_PREF_SIZE);
        baselineSection.setMaxHeight(Region.USE_PREF_SIZE);
        weightedView.getChildren().addFirst(baselineSection);
        weightedView.initialCurrent(baselineInputs.snapshot());
    }

    private VBox quickComparisonContent() {
        quickPrerequisite.setWrapText(true);
        quickPrerequisite.setMinHeight(Region.USE_PREF_SIZE);
        quickPrerequisite.setAccessibleText("Quick Comparison prerequisite");
        goToSocialSecurity.setAccessibleText("Go to Social Security Only");
        goToSocialSecurity.setAccessibleHelp("Navigate to Social Security Only without running an analysis.");
        goToSocialSecurity.setOnAction(event -> {
            navigation.select(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY);
            navigation.focusSelector();
        });
        configureIntegratedTable();
        integratedDetails.setEditable(false);
        integratedDetails.setWrapText(true);
        integratedDetails.setPrefRowCount(12);
        integratedMethodology.setEditable(false);
        integratedMethodology.setWrapText(true);
        integratedMethodology.setPrefRowCount(7);
        integratedMethodology.setText(
                "Social Security Only ranks complete strategies by mortality-weighted expected "
                        + "Social Security present value. Integrated Retirement Plan evaluates a "
                        + "small analyzer-ordered set through the current plan's deterministic death, "
                        + "tax, withdrawal, Roth, RMD, Medicare, portfolio, and estate assumptions.\n\n"
                        + "Mortality categories, mortality adjustments, and the real discount rate affect "
                        + "strategy generation and SS Expected PV. They do not make the full-plan projection "
                        + "mortality-weighted and do not discount its future-dollar metrics.\n\n"
                        + "If the active plan changes while this window remains open, rerun the "
                        + "analyzer before relying on the comparison.");
        integratedBaseline.getChildren().setAll(new Label(
                "The current-plan baseline will appear after integrated analysis."));

        Label explanation = new Label(
                "Compares top Social Security-only strategies using deterministic full-plan outcomes. "
                        + "Rows remain in Social Security analyzer order; no integrated winner is selected.");
        explanation.setWrapText(true);
        InputHelp.install(integratedCandidateCount, "Number of top Social Security-only strategies to evaluate through the deterministic retirement plan, from 1 to 20. This affects Quick Comparison only; it does not limit Exhaustive Search or change the saved plan.");
        VBox candidates = AnalyzerConfigurationLayout.field(new Label("Integrated candidates (1-20):"), integratedCandidateCount);
        FlowPane actions = AnalyzerConfigurationLayout.flow(integratedRunButton, integratedCancelButton);
        integratedStatus.setWrapText(true);
        VBox tableBox = new VBox(8, heading("Candidate Comparison"), integratedTable);
        VBox.setVgrow(integratedTable, Priority.ALWAYS);
        SplitPane details = new SplitPane(
                new VBox(8, heading("Current Plan Baseline"), integratedBaseline),
                new VBox(8, heading("Selected Candidate"), integratedDetails));
        details.setDividerPositions(0.45);
        VBox content = new VBox(10,
                AnalyzerConfigurationLayout.heading("Inputs for Next Run"),
                quickPrerequisite, goToSocialSecurity,
                wrappedLabel("Candidate selection uses the completed Social Security Only ranking, including its mortality and valuation assumptions. "
                        + "To change those inputs, go to Social Security Only and rerun it. Full-plan outcomes remain deterministic under the configured plan death scenario."),
                candidates, AnalyzerConfigurationLayout.heading("Run / Cancel / Progress"),
                actions, integratedStatus, integratedProgress, integratedStale,
                AnalyzerConfigurationLayout.heading("Completed Results"),
                explanation, details, tableBox, heading("Methodology and Assumptions"), integratedMethodology);
        content.setPadding(new Insets(12));
        VBox.setVgrow(tableBox, Priority.ALWAYS);
        return content;
    }

    private VBox exhaustiveSearchContent() {
        configureExhaustiveTable();
        exhaustiveDetails.setEditable(false);
        exhaustiveDetails.setWrapText(true);
        exhaustiveDetails.setPrefRowCount(12);
        exhaustiveSummary.getChildren().setAll(new Label(
                "Current-plan and exhaustive-search summary will appear after a successful run."));
        Label explanation = new Label(
                "Exhaustive Search evaluates all tested claiming strategies against the "
                        + "deterministic full retirement plan. Analyzer longevity and valuation inputs are not "
                        + "used in this search. The ranking "
                        + "shown is deterministic After-Tax Estate Ranking under current projection "
                        + "assumptions. It is not a universal recommendation.");
        explanation.setWrapText(true);
        Label methodology = new Label(
                "This deterministic future-dollar projection uses the plan's configured death, tax, "
                        + "Roth, RMD, Medicare, withdrawal, portfolio, and estate assumptions. It is "
                        + "independent of SS-only mortality-weighted ranking and does not apply a strategy.");
        methodology.setWrapText(true);
        FlowPane actions = AnalyzerConfigurationLayout.flow(exhaustiveRunButton, exhaustiveCancelButton);
        exhaustiveStatus.setWrapText(true);
        VBox tableBox = new VBox(8, heading("Grouped Top Tested Outcomes"), exhaustiveTable);
        tableBox.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(exhaustiveTable, Priority.ALWAYS);
        SplitPane details = new SplitPane(
                new VBox(8, heading("Search Summary and Current Plan"), exhaustiveSummary),
                new VBox(8, heading("Selected Outcome"), exhaustiveDetails));
        details.setDividerPositions(0.45);
        deterministicRankedContent.getChildren().setAll(details, tableBox, heading("Methodology"), methodology);
        deterministicHeatMap.resultNotice.textProperty().bind(exhaustiveStale.textProperty());
        VBox content = new VBox(10, explanation, AnalyzerConfigurationLayout.heading("Run / Cancel / Progress"),
                actions, exhaustiveStatus, exhaustiveProgress, exhaustiveStale,
                AnalyzerConfigurationLayout.heading("Completed Results"),
                new HBox(10, heading("Completed Analysis"), deterministicExportPdf), deterministicResultTabs);
        content.setPadding(new Insets(12));
        VBox.setVgrow(tableBox, Priority.NEVER);
        exhaustiveWorkspace = content;
        return content;
    }

    private VBox inputs() {
        String adjustmentHelp = "Mortality risk multiplier: 1.00 means standard table mortality; 0.75 means 75% "
                + "and 1.25 means 125% of the table's underlying mortality hazard. Annual death probabilities "
                + "are adjusted through survival probabilities, not multiplied directly. This does not shorten "
                + "or lengthen remaining life by that percentage. Above 1.00 increases modeled mortality; below 1.00 decreases it.";
        primaryMortalityAdjustment.setTooltip(HelpIcon.createTooltip(adjustmentHelp));
        spouseMortalityAdjustment.setTooltip(HelpIcon.createTooltip(adjustmentHelp));
        mortalityDate.setTooltip(HelpIcon.createTooltip(
                "Future mortality probabilities assume both people are alive as of this date. Death probabilities "
                        + "before this date are excluded; the model uses complete birthday intervals after this date. "
                        + "This does not change the retirement-plan projection start date."));
        pvDate.setTooltip(HelpIcon.createTooltip(
                "The common financial date used to express present-value results. Future benefits or estate values "
                        + "are translated to this date for comparison. Changing this date does not change the mortality "
                        + "conditioning date or the retirement projection start date."));
        discountRate.setTooltip(HelpIcon.createTooltip(
                "Annual real discount percentage (enter 1 for 1%) used to discount inflation-adjusted future values back to the valuation date. "
                        + "It represents time preference after removing general inflation. Higher values place less "
                        + "present value on more distant future amounts."));
        Label help = new Label(
                "1.00x uses standard SSA mortality; above 1.00x increases modeled mortality and below 1.00x decreases it. "
                        + "Planning assumption only, not a medical or actuarial assessment.");
        help.setWrapText(true);
        help.setMinHeight(Region.USE_PREF_SIZE);
        return new VBox(6, AnalyzerConfigurationLayout.flow(
                AnalyzerConfigurationLayout.field(new Label("Primary mortality adjustment:"), primaryMortalityAdjustment),
                AnalyzerConfigurationLayout.field(new Label("Spouse mortality adjustment:"), spouseMortalityAdjustment),
                AnalyzerConfigurationLayout.field(new Label("Mortality conditioning date:"), mortalityDate),
                AnalyzerConfigurationLayout.field(new Label("Valuation date:"), pvDate),
                AnalyzerConfigurationLayout.field(new Label("Real discount rate (%):"), discountRate)), help);
    }

    private void runAnalysis() {
        try {
            BigDecimal rate = new BigDecimal(discountRate.getText().trim())
                    .movePointLeft(2);
            SocialSecurityMortalityAdjustment primaryAdjustment = adjustment(
                    primaryMortalityAdjustment.getText(), "Primary");
            SocialSecurityMortalityAdjustment spouseAdjustment = adjustment(
                    spouseMortalityAdjustment.getText(), "Spouse");
            SocialSecurityStrategyAnalysisContext preparedContext =
                    new SocialSecurityStrategyAnalysisRequestFactory().create(
                            plan,
                            primaryAdjustment,
                            spouseAdjustment,
                            rate,
                            mortalityDate.getValue(),
                            pvDate.getValue());
            var originalRequest = preparedContext.request();
            var context = acceptedSsTop == originalRequest.settings().topRetirementCandidates() ? preparedContext
                    : withOptimizationSettings(preparedContext, acceptedSsTop);
            var parameterRun = parameterRun(AnalysisKind.COUPLE_SOCIAL_SECURITY, SocialSecurityParameterMapping.socialSecurity(context));
            var completedPlanId = analysisSource == null ? null : analysisSource.getAnalysisSessionId();
            var completedFile = analysisSource != null && analysisSource.canPersistAnalysisResults() ? analysisSource.getCurrentFile() : null;
            var started = java.time.Instant.now();
            var fingerprints = new AnalysisFingerprintService();
            var inputFingerprint = socialSecurityPersistence == null ? null : fingerprints.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, socialSecurityInputs());
            var contextFingerprint = socialSecurityPersistence == null ? null : fingerprints.effectiveContextFingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan);
            var calculation = socialSecurityCalculation.apply(context.request());
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY,
                    (progress, cancellation) -> {
                        var result = calculation.run(progress, cancellation);
                        return new RunResult(context, result);
                    }, run -> {
                presentation = SocialSecurityStrategyAnalyzerPresentation.from(run.result());
                render(run.context(), presentation);
                socialSecurityResultCurrent = true;
                jobs.invalidate(SocialSecurityAnalyzerJobController.Change.SOCIAL_SECURITY_RESULT);
                refreshExhaustiveSocialSecurityReference();
                stale.setText("");
                markIntegratedStale("Social Security analysis changed - run integrated analysis again.");
                setAnalysisBusy(false);
                status.setText("Analysis complete.");
                if (AnalysisSuccessPolicy.accepted(run.result())) acceptParameters(AnalysisKind.COUPLE_SOCIAL_SECURITY, parameterRun);
                if (socialSecurityPersistence != null && AnalysisSuccessPolicy.accepted(run.result())) {
                    var preferences = new java.util.HashMap<String, String>();
                    var selected = strategies.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        int occurrence = run.result().evaluatedStrategies().indexOf(selected.cell());
                        if (occurrence >= 0) preferences.put("occurrence", Integer.toString(occurrence + 1));
                    }
                    var frozen = new SnapshotContext("Couple", context.primaryName(), context.spouseName(), started,
                            List.of(new SnapshotContext.Section("Original analysis assumptions", List.of(assumptionText(context, run.result())))),
                            List.of(), List.of(), preferences);
                    socialSecurityPersistence.accept(new AcceptedAnalysisCompletion(completedPlanId, completedFile,
                            AnalysisKind.COUPLE_SOCIAL_SECURITY, java.util.UUID.randomUUID(), java.time.Instant.now(),
                            inputFingerprint, contextFingerprint, AnalysisVersions.algorithms(AnalysisKind.COUPLE_SOCIAL_SECURITY), java.util.Map.of(),
                            CoupleSocialSecuritySnapshotMapper.capture(context, run.result()), frozen));
                }
            }, this::showError, () -> rollbackParameters(AnalysisKind.COUPLE_SOCIAL_SECURITY, parameterRun));
            if (!accepted) {
                status.setText("Another Social Security analysis is running or cleaning up.");
            }
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private static SocialSecurityStrategyAnalysisContext withOptimizationSettings(SocialSecurityStrategyAnalysisContext c, int top) {
        return new SocialSecurityStrategyAnalysisContext(new SocialSecuritySurvivorClaimingOptimizationRequest(
                c.request().retirementGridRequest(), new SocialSecuritySurvivorOptimizationSettings(top)),
                c.primaryName(), c.spouseName(), c.primaryFraMonthlyBenefit(), c.spouseFraMonthlyBenefit(),
                c.primaryBenefitValuationYear(), c.spouseBenefitValuationYear(), c.primaryCurrentClaimDate(), c.spouseCurrentClaimDate(),
                c.primaryMortalityCategory(), c.spouseMortalityCategory(), c.primaryMortalityAdjustment(), c.spouseMortalityAdjustment(),
                c.mortalityMetadata(), c.longevityAssumptions());
    }

    private void render(
            SocialSecurityStrategyAnalysisContext context,
            SocialSecurityStrategyAnalyzerPresentation model) {
        SocialSecuritySurvivorClaimingOptimizationCell top =
                model.rankedStrategies().getFirst().cell();
        recommendation.getChildren().setAll(
                heading("Highest Expected-PV Tested Strategy"),
                new Label("Based on tested claiming ages, survivor choices, SSA 2022 period mortality, and the displayed assumptions."),
                new Label(strategyText(top)),
                new Label("Expected PV: " + money(top.expectedPresentValue())),
                new Label("Expected real lifetime benefits: " + money(top.expectedRealBenefits())),
                new Label("Expected nominal lifetime benefits: " + money(top.expectedNominalBenefits())));
        strategies.getItems().setAll(model.rankedStrategies());
        renderGrid(model);
        assumptions.setText(assumptionText(context, model.result()));
    }

    private void renderGrid(SocialSecurityStrategyAnalyzerPresentation model) {
        claimingGrid.getChildren().clear();
        claimingGrid.setHgap(6);
        claimingGrid.setVgap(6);
        claimingGrid.add(new Label("Primary \\ Spouse"), 0, 0);
        List<Integer> spouseAges = model.result().retirementGrid().spouseClaimAges();
        for (int column = 0; column < spouseAges.size(); column++) {
            claimingGrid.add(new Label("Age " + spouseAges.get(column)), column + 1, 0);
        }
        List<Integer> primaryAges = model.result().retirementGrid().primaryClaimAges();
        for (int row = 0; row < primaryAges.size(); row++) {
            int primaryAge = primaryAges.get(row);
            claimingGrid.add(new Label("Age " + primaryAge), 0, row + 1);
            for (int column = 0; column < spouseAges.size(); column++) {
                int spouseAge = spouseAges.get(column);
                SocialSecurityMortalityWeightedClaimingGridCell cell = model.result()
                        .retirementGrid().cellFor(primaryAge, spouseAge).orElseThrow();
                Button button = new Button(money(cell.expectedPresentValue()));
                boolean retained = model.advancedToStageTwo(primaryAge, spouseAge);
                boolean highest = model.result().retirementGrid()
                        .highestExpectedPresentValue().highestCells().contains(cell);
                button.setText((highest ? "★ " : retained ? "▶ " : "") + button.getText());
                button.setTooltip(new Tooltip(
                        "Expected PV; ★ highest Stage 1, ▶ advanced to Stage 2"));
                button.setOnAction(event -> gridDetails.setText(
                        gridDetail(model, cell, retained)));
                claimingGrid.add(button, column + 1, row + 1);
            }
        }
    }

    private String gridDetail(
            SocialSecurityStrategyAnalyzerPresentation model,
            SocialSecurityMortalityWeightedClaimingGridCell cell,
            boolean retained) {
        String detail = "Primary age " + cell.primaryClaimAge()
                + ", spouse age " + cell.spouseClaimAge()
                + " | Expected PV " + money(cell.expectedPresentValue())
                + " | Real " + money(cell.expectedRealBenefits())
                + " | Nominal " + money(cell.expectedNominalBenefits())
                + " | " + (retained ? "Advanced to Stage 2" : "Not retained for Stage 2");
        return model.bestForRetirement(cell.primaryClaimAge(), cell.spouseClaimAge())
                .map(best -> detail + " | Best expanded PV " + money(best.expectedPresentValue()))
                .orElse(detail);
    }

    private void runIntegratedAnalysis() {
        try {
            if (presentation == null || !socialSecurityResultCurrent) {
                throw new IllegalStateException(
                        "Run Social Security analysis with the current inputs before integrated analysis.");
            }
            List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected =
                    new IntegratedSocialSecurityCandidateSelector().select(
                            presentation.rankedStrategies(), integratedCandidateCount.getValue());
            if (selected.isEmpty()) {
                throw new IllegalStateException(
                        "No complete Social Security strategies are available for integrated analysis.");
            }
            var input = SocialSecurityAnalyzerInputs.quick(plan, selected);
            var prerequisite = new AnalysisFingerprintService().fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, socialSecurityInputs());
            var quickParameters = new AnalysisParameterConfiguration.Quick(integratedCandidateCount.getValue(), prerequisite);
            var candidateInputs = new AnalysisFingerprintService.Inputs(null, null, null, null, null, quickParameters.candidateCount(), prerequisite,
                    selected.stream().map(row -> new AnalysisFingerprintService.Candidate(WeightedSnapshots.Strategy.from(row.cell().strategy()),
                            row.rank(), row.cell().expectedValue())).toList(), List.of(), null);
            var parameterRun = parameterBindings.containsKey(AnalysisKind.COUPLE_QUICK)
                    ? parameterBindings.get(AnalysisKind.COUPLE_QUICK).begin(quickParameters, java.util.Map.of("candidateBasisFingerprint",
                            new AnalysisFingerprintService().fingerprint(AnalysisKind.COUPLE_QUICK, input.plan(), candidateInputs))) : null;
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.QUICK,
                    quickCalculation.apply(input), model -> {
                integratedPresentation = model;
                renderIntegrated(model);
                integratedStale.setText("");
                integratedStatus.setText(integratedCompletionText(model));
                if (AnalysisSuccessPolicy.accepted(model.comparison())) acceptParameters(AnalysisKind.COUPLE_QUICK, parameterRun);
                else rollbackParameters(AnalysisKind.COUPLE_QUICK, parameterRun);
            }, this::showError, () -> rollbackParameters(AnalysisKind.COUPLE_QUICK, parameterRun));
            if (!accepted) {
                integratedStatus.setText("Another Social Security analysis is running or cleaning up.");
            }
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void runWeightedAnalysis() {
        try {
            var factory = new LongevityWeightedAnalysisRequestFactory();
            var snapshot = factory.capture(plan,
                    adjustment(primaryMortalityAdjustment.getText(), "Primary"),
                    adjustment(spouseMortalityAdjustment.getText(), "Spouse"), mortalityDate.getValue(), pvDate.getValue(),
                    new BigDecimal(discountRate.getText().trim()).movePointLeft(2), planRevision, assumptionsRevision,
                    baselineInputs.snapshot());
            var parameterRun = parameterRun(AnalysisKind.COUPLE_WEIGHTED,
                    SocialSecurityParameterMapping.weighted(snapshot, baselineInputs.parameters(snapshot.baseline)));
            var reportContext = captureReportContext(true);
            var completedPlanId = analysisSource == null ? null : analysisSource.getAnalysisSessionId();
            var completedFile = analysisSource != null && analysisSource.canPersistAnalysisResults() ? analysisSource.getCurrentFile() : null;
            var fingerprints = new AnalysisFingerprintService();
            // Fingerprint the effective accepted request, without adding validation to the existing Run path.
            var capturedInputs = new AnalysisFingerprintService.Inputs(snapshot.longevity.primaryAdjustment().factor(),
                    snapshot.longevity.spouseAdjustment().factor(), snapshot.longevity.mortalityBaseDate(), snapshot.valuationDate,
                    snapshot.discountRate, null, null, List.of(), snapshot.baseline.elections().stream()
                    .map(e -> new SnapshotContext.Section(e.name(), List.of(e.value(), e.source()))).toList(), null);
            var inputFingerprint = weightedPersistence == null ? null : fingerprints.fingerprint(AnalysisKind.COUPLE_WEIGHTED, plan, capturedInputs);
            var contextFingerprint = weightedPersistence == null ? null : fingerprints.effectiveContextFingerprint(AnalysisKind.COUPLE_WEIGHTED, plan);
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.WEIGHTED,
                    (progress, cancellation) -> weightedCalculation.apply(factory.create(snapshot, progress, cancellation)), result -> {
                        if (!AnalysisSuccessPolicy.accepted(result)) {
                            rollbackParameters(AnalysisKind.COUPLE_WEIGHTED, parameterRun);
                            weightedView.status.setText("No candidate completed successfully. Previous successful results were retained.");
                            return;
                        }
                        weightedPresentation = new LongevityWeightedIntegratedPresentation(result,
                                snapshot.planRevision, snapshot.assumptionsRevision);
                        acceptParameters(AnalysisKind.COUPLE_WEIGHTED, parameterRun);
                        weightedCurrent = true;
                        weightedReportContext = reportContext;
                        historicalWeightedComparison = null;
                        historicalWeightedRanks = null;
                        weightedView.render(weightedPresentation, snapshot.baseline);
                        weightedView.resultTabs.setVisible(true);
                        weightedView.resultTabs.setManaged(true);
                        refreshWeightedComparison();
                        if (weightedPersistence != null) {
                            var b = snapshot.baseline;
                            var baseline = new SnapshotContext.Baseline(b.elections().stream()
                                    .map(e -> new SnapshotContext.Election(e.name(), e.value(), e.source())).toList(),
                                    b.strategy().map(WeightedSnapshots.Strategy::from).orElse(null), b.problem());
                            var rows = IntegratedAnalysisComparisonPresentation.create(weightedPresentation,
                                    exhaustivePresentation == null ? null : exhaustivePresentation.result(), exhaustiveRevision, planRevision, weightedCurrent);
                            var deterministic = exhaustivePresentation == null ? null : exhaustivePresentation.result();
                            boolean compatible = IntegratedAnalysisComparisonPresentation.compatible(weightedPresentation, deterministic,
                                    exhaustiveRevision, planRevision, weightedCurrent);
                            var deterministicRanks = new java.util.HashMap<List<Object>, java.util.OptionalInt>();
                            if (compatible) deterministic.entries().forEach(e -> deterministicRanks.putIfAbsent(
                                    LongevityWeightedIntegratedPresentation.identity(e.strategy()), e.afterTaxEstateRank()));
                            var detailRanks = result.orderedEntries().stream().map(e -> {
                                var rank = deterministicRanks.getOrDefault(LongevityWeightedIntegratedPresentation.identity(e.strategy()), java.util.OptionalInt.empty());
                                return new SnapshotContext.ComparisonRow("Strategy detail", WeightedSnapshots.Strategy.from(e.strategy()),
                                        rank.isPresent() ? rank.getAsInt() : null, null, null, null, null, null, null);
                            }).toList();
                            var context = new SnapshotContext(reportContext.household(), reportContext.primary(), reportContext.spouse(),
                                    reportContext.runStarted(), reportContext.assumptions().stream()
                                    .map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(), List.of(), List.of(), java.util.Map.of(), baseline,
                                    List.of(new SnapshotContext.HistoricalComparison(AnalysisKind.COUPLE_EXHAUSTIVE, null, null,
                                            "Comparison captured at original weighted completion.", rows.stream().map(AnalysisSnapshotPresentationMapper::capture).toList()),
                                            new SnapshotContext.HistoricalComparison(AnalysisKind.COUPLE_EXHAUSTIVE, null, null,
                                                    "Frozen strategy-detail ranks", detailRanks)));
                            historicalWeightedComparison = rows;
                            historicalWeightedRanks = AnalysisSnapshotPresentationMapper.ranks(detailRanks);
                            weightedView.comparisons(rows);
                            weightedView.historicalRanks(historicalWeightedRanks);
                            try {
                                weightedPersistence.accept(new AcceptedAnalysisCompletion(completedPlanId, completedFile, AnalysisKind.COUPLE_WEIGHTED,
                                        java.util.UUID.randomUUID(), java.time.Instant.now(), inputFingerprint, contextFingerprint,
                                        AnalysisVersions.algorithms(AnalysisKind.COUPLE_WEIGHTED), java.util.Map.of(),
                                        new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(result), null), context));
                            } catch (RuntimeException saving) {
                                weightedView.status.setText("Analysis completed, but result saving could not start. Previous saved results were retained.");
                            }
                        }
                        refreshExportActions();
                    }, failure -> weightedView.status.setText(SocialSecurityAnalysisFailurePresentation.message(failure)),
                    () -> rollbackParameters(AnalysisKind.COUPLE_WEIGHTED, parameterRun));
            if (!accepted) {
                weightedView.status.setText("Another Social Security analysis is running or cleaning up.");
            } else if (weightedPresentation != null) {
                weightedView.status.setText("Rerunning — previous successful result shown");
            }
        } catch (RuntimeException failure) {
            weightedView.status.setText(SocialSecurityAnalysisFailurePresentation.message(failure));
        }
    }

    private void refreshWeightedComparison() {
        if (weightedPresentation != null) {
            if (historicalWeightedComparison != null) {
                weightedView.comparisons(historicalWeightedComparison);
                return;
            }
            var deterministic = exhaustivePresentation == null ? null : exhaustivePresentation.result();
            weightedView.comparisons(IntegratedAnalysisComparisonPresentation.create(weightedPresentation,
                    deterministic,
                    exhaustiveRevision, planRevision, weightedCurrent));
            weightedView.deterministicReference(IntegratedAnalysisComparisonPresentation.compatible(weightedPresentation,
                    deterministic, exhaustiveRevision, planRevision, weightedCurrent) ? deterministic : null);
        }
    }

    private void runExhaustiveSearch() {
        try {
            var standard = SocialSecurityAnalyzerInputs.exhaustive(plan);
            var request = acceptedExhaustiveProfile == null ? standard : new IntegratedSocialSecurityCompleteStrategySearchRequest(
                    standard.plan(), standard.primaryRetirementAges(), standard.spouseRetirementAges(), standard.primarySurvivorCandidates(),
                    standard.spouseSurvivorCandidates(), com.daviddunn.retirementplanner.app.socialsecurity.IntegratedStrategyRankingMeasure.valueOf(acceptedExhaustiveProfile.rankingMeasure()),
                    acceptedExhaustiveProfile.retainedDetails());
            var parameterRun = parameterRun(AnalysisKind.COUPLE_EXHAUSTIVE, SocialSecurityParameterMapping.search(request));
            long capturedRevision = planRevision;
            var reportContext = captureReportContext(false);
            var planId = analysisSource == null ? null : analysisSource.getAnalysisSessionId();
            var file = analysisSource != null && analysisSource.canPersistAnalysisResults() ? analysisSource.getCurrentFile() : null;
            var ssMetadata = socialSecurityPersistence == null ? null : socialSecurityPersistence.resultMetadata();
            var basis = presentation != null && socialSecurityResultCurrent && !presentation.rankedStrategies().isEmpty()
                    ? new ExhaustiveResultSnapshots.SocialSecurityBasis(
                            WeightedSnapshots.Strategy.from(presentation.rankedStrategies().getFirst().cell().strategy()),
                            presentation.rankedStrategies().getFirst().cell().expectedPresentValue(), planRevision,
                            ssMetadata == null ? null : ssMetadata.runId(), ssMetadata == null ? null : ssMetadata.inputFingerprint()) : null;
            String metric = deterministicHeatMap.metric.getValue().name();
            String tab = deterministicResultTabs.getSelectionModel().getSelectedItem() == deterministicHeatMapTab ? "heat-map" : "ranked";
            var calculation = exhaustiveCalculation.apply(request);
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE,
                    (progress, cancellation) -> {
                        long started = System.nanoTime();
                        var result = calculation.run(progress, cancellation);
                        var model = ExhaustiveIntegratedSearchPresentation.from(result,
                                Duration.ofNanos(System.nanoTime() - started), 20);
                        AcceptedAnalysisCompletion completion = null;
                        ExhaustiveSnapshot payload = null;
                        if (planId != null && AnalysisSuccessPolicy.accepted(result)) {
                            var completed = java.time.Instant.now();
                            payload = ExhaustiveResultSnapshots.capture(model, reportContext, basis, completed, metric, tab);
                            var context = new SnapshotContext(reportContext.household(), reportContext.primary(), reportContext.spouse(),
                                    reportContext.runStarted(), reportContext.assumptions().stream()
                                            .map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(), List.of(), List.of(), java.util.Map.of());
                            var fingerprints = new AnalysisFingerprintService();
                            completion = new AcceptedAnalysisCompletion(planId, file, AnalysisKind.COUPLE_EXHAUSTIVE,
                                    java.util.UUID.randomUUID(), completed,
                                    fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, request.plan(), AnalysisFingerprintService.Inputs.none()),
                                    fingerprints.effectiveContextFingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, request.plan()),
                                    AnalysisVersions.algorithms(AnalysisKind.COUPLE_EXHAUSTIVE), AnalysisVersions.references(AnalysisKind.COUPLE_EXHAUSTIVE),
                                    payload, context);
                        }
                        return new ExhaustiveRun(model, payload, completion);
                    }, run -> {
                if (planId != null && !planId.equals(analysisSource.getAnalysisSessionId())) return;
                var model = run.model();
                if (!AnalysisSuccessPolicy.accepted(model.result())) {
                    rollbackParameters(AnalysisKind.COUPLE_EXHAUSTIVE, parameterRun);
                    exhaustiveStatus.setText("No successful candidates. Previous results retained.");
                    return;
                }
                exhaustivePresentation = model;
                acceptParameters(AnalysisKind.COUPLE_EXHAUSTIVE, parameterRun);
                exhaustiveRevision = capturedRevision;
                deterministicReportContext = reportContext;
                historicalExhaustiveReference = run.payload() == null ? null : run.payload().socialSecurityReference();
                renderExhaustive(model);
                if (run.completion() != null) exhaustivePersistence.accept(run.completion());
                refreshWeightedComparison();
                exhaustiveStale.setText("");
                exhaustiveStatus.setText("Exhaustive integrated search complete - "
                        + model.result().totalStrategyCount() + " strategies evaluated; " + model.result().failedStrategyCount() + " unavailable.");
                refreshExportActions();
            }, this::showError, () -> rollbackParameters(AnalysisKind.COUPLE_EXHAUSTIVE, parameterRun));
            if (accepted && exhaustivePersistence != null) exhaustivePersistence.beginReplacement();
            if (!accepted) {
                exhaustiveStatus.setText("Another Social Security analysis is running or cleaning up.");
            }
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }
    private record ExhaustiveRun(ExhaustiveIntegratedSearchPresentation model,
            ExhaustiveSnapshot payload, AcceptedAnalysisCompletion completion) { }
    private void configureExhaustiveTable() {
        exhaustiveTable.getColumns().add(exhaustiveColumn("Estate Rank", group ->
                Integer.toString(group.representative().afterTaxEstateRank().orElseThrow()), 85));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Primary Retirement", group ->
                retirementElection(group.representative().strategy().primaryRetirementAge(),
                        group.representative().strategy().primaryRetirementClaimDate()), 165));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Spouse Retirement", group ->
                retirementElection(group.representative().strategy().spouseRetirementAge(),
                        group.representative().strategy().spouseRetirementClaimDate()), 165));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Primary Survivor Benefit Claiming Age", group ->
                survivorElection(group.representative().strategy().primarySurvivorElection()), 180));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Spouse Survivor Benefit Claiming Age", group ->
                survivorElection(group.representative().strategy().spouseSurvivorElection()), 180));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Lifetime SS", group ->
                money(group.representative().metrics().orElseThrow()
                        .lifetimeHouseholdSocialSecurity()), 125));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Total Taxes", group ->
                money(group.representative().metrics().orElseThrow().totalTaxes()), 120));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Portfolio Withdrawals", group ->
                money(group.representative().metrics().orElseThrow()
                        .lifetimePortfolioWithdrawals()), 155));
        exhaustiveTable.getColumns().add(moneyColumn("Ending Investable Assets", (ExhaustiveIntegratedSearchPresentation.Group group) ->
                group.representative().metrics().orElseThrow()
                        .endingInvestableAssets(), 165));
        exhaustiveTable.getColumns().add(moneyColumn("After-Tax Heir Value", (ExhaustiveIntegratedSearchPresentation.Group group) ->
                group.representative().metrics().orElseThrow().afterTaxEstate(), 150));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Delta Estate vs Current", group ->
                signedMoney(group.representative().differencesFromCurrentPlan().orElseThrow()
                        .afterTaxEstate()), 155));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Equivalent Strategies", group ->
                Integer.toString(group.equivalentStrategyCount()), 135));
        exhaustiveTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        exhaustiveTable.setPlaceholder(new Label("Run exhaustive search to display tested outcomes."));
        exhaustiveTable.getSelectionModel().selectedItemProperty().addListener((o, a, group) -> {
            if (group != null) {
                deterministicSelectedEntry = group.representative();
                exhaustiveDetails.setText(exhaustiveDetail(group));
            }
        });
    }

    private TableColumn<ExhaustiveIntegratedSearchPresentation.Group, String> exhaustiveColumn(
            String title,
            java.util.function.Function<ExhaustiveIntegratedSearchPresentation.Group, String> value,
            double width) {
        TableColumn<ExhaustiveIntegratedSearchPresentation.Group, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    private <T> TableColumn<T, BigDecimal> moneyColumn(
            String title, java.util.function.Function<T, BigDecimal> value, double width) {
        TableColumn<T, BigDecimal> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        column.setComparator(java.util.Comparator.nullsLast(BigDecimal::compareTo));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty ? null : amount == null ? "Unavailable" : money(amount));
            }
        });
        column.setPrefWidth(width);
        return column;
    }

    private void renderExhaustive(ExhaustiveIntegratedSearchPresentation model) {
        deterministicResultTabs.setVisible(true);
        deterministicResultTabs.setManaged(true);
        deterministicSelectedEntry = null;
        deterministicHeatMapSource = model;
        deterministicHeatMap.render(DeterministicHeatMapAdapter.from(model));
        var result = model.result();
        ProjectionMetrics current = result.currentPlanBaseline().metrics();
        var highest = result.rankedSuccessfulEntries().isEmpty()
                ? null : result.rankedSuccessfulEntries().getFirst();
        BigDecimal gap = highest == null ? BigDecimal.ZERO
                : highest.metrics().orElseThrow().afterTaxEstate().subtract(current.afterTaxEstate());
        String gapPercent = current.afterTaxEstate().signum() == 0
                ? "not available"
                : gap.multiply(new BigDecimal("100"))
                        .divide(current.afterTaxEstate(), 2, java.math.RoundingMode.HALF_UP)
                        .toPlainString() + "%";
        String currentRank = result.currentPlanRank().isPresent()
                ? Integer.toString(result.currentPlanRank().getAsInt()) : "Unavailable";
        exhaustiveSummary.getChildren().setAll(
                new Label(strategySummary(result.currentPlanBaseline().evaluatedStrategy())),
                new Label("Current survivor policy: persisted deterministic projection policy"),
                new Label("Future dollars at configured projection horizon"),
                new Label("Current Ending Investable Assets: " + money(current.endingInvestableAssets())),
                new Label("Highest tested Ending Investable Assets: " + (highest == null ? "Unavailable"
                        : money(highest.metrics().orElseThrow().endingInvestableAssets()))),
                new Label("Current After-Tax Heir Value: " + money(current.afterTaxEstate())),
                new Label("Deterministic after-tax-estate metric position: " + currentRank),
                new Label("Highest tested After-Tax Heir Value: "
                        + (highest == null ? "Unavailable"
                        : money(highest.metrics().orElseThrow().afterTaxEstate()))),
                new Label("Gap from highest tested: " + signedMoney(gap)
                        + " (" + gapPercent + ")"),
                new Label("Successful: " + result.successfulStrategyCount()
                        + " | Failed: " + result.failedStrategyCount()
                        + " | Runtime: " + formatDuration(model.runtime())),
                exhaustiveSocialSecurityReference);
        refreshExhaustiveSocialSecurityReference();
        exhaustiveTable.getItems().setAll(model.groups());
        if (model.groups().isEmpty()) {
            exhaustiveDetails.setText("No successful exhaustive-search outcomes were returned.");
        } else {
            exhaustiveTable.getSelectionModel().selectFirst();
            exhaustiveDetails.setText(exhaustiveDetail(model.groups().getFirst()));
        }
    }

    private void openDeterministicHeatMapStrategy(int generationOrder) {
        if (deterministicHeatMapSource == null) return;
        deterministicHeatMapSource.result().entries().stream()
                .filter(entry -> entry.generationOrder() == generationOrder && entry.successful()).findFirst().ifPresent(entry -> {
                    deterministicResultTabs.getSelectionModel().select(deterministicRankedTab);
                    var displayed = exhaustiveTable.getItems().stream()
                            .filter(group -> group.representative().generationOrder() == generationOrder).findFirst();
                    if (displayed.isPresent()) {
                        exhaustiveTable.getSelectionModel().select(displayed.orElseThrow());
                        exhaustiveTable.scrollTo(displayed.orElseThrow());
                    } else {
                        exhaustiveTable.getSelectionModel().clearSelection();
                    }
                    exhaustiveDetails.setText(exhaustiveDetail(entry, "\nOriginal strategy occurrence: " + generationOrder));
                    deterministicSelectedEntry = entry;
                    exhaustiveDetails.requestFocus();
                    javafx.application.Platform.runLater(() -> IntegratedAnalysisResultTabs.reveal(exhaustiveDetails));
                });
    }

    private String socialSecurityCrossReference(ExhaustiveIntegratedSearchPresentation model) {
        if (historicalExhaustiveReference != null) {
            var reference = historicalExhaustiveReference;
            if (reference.status() != ExhaustiveSnapshot.ReferenceStatus.AVAILABLE)
                return "Original SS-only recommendation cross-reference: " + reference.explanation();
            return "Original SS-only recommendation cross-reference: Expected PV " + money(reference.expectedPresentValue())
                    + ", deterministic after-tax estate " + money(reference.estate()) + ", estate rank " + reference.rank()
                    + ", gap from highest tested " + signedMoney(reference.gapFromHighest()) + ".";
        }
        if (presentation == null || !socialSecurityResultCurrent
                || presentation.rankedStrategies().isEmpty()) {
            return "SS-only recommendation cross-reference: run current SS-only analysis first.";
        }
        var recommended = presentation.rankedStrategies().getFirst().cell();
        var match = model.result().entryFor(recommended.strategy());
        if (match.isEmpty() || !match.orElseThrow().successful()) {
            return "SS-only recommendation is outside the current exhaustive integrated search universe.";
        }
        var entry = match.orElseThrow();
        BigDecimal highestEstate = model.result().rankedSuccessfulEntries().getFirst()
                .metrics().orElseThrow().afterTaxEstate();
        BigDecimal estate = entry.metrics().orElseThrow().afterTaxEstate();
        return "SS-only recommendation cross-reference: Expected PV "
                + money(recommended.expectedPresentValue())
                + ", deterministic after-tax estate " + money(estate)
                + ", estate rank " + entry.afterTaxEstateRank().orElseThrow()
                + ", gap from highest tested " + signedMoney(estate.subtract(highestEstate)) + ".";
    }

    private String exhaustiveDetail(ExhaustiveIntegratedSearchPresentation.Group group) {
        return exhaustiveDetail(group.representative(), "\nEquivalent tested strategies: " + group.equivalentStrategyCount());
    }

    private String exhaustiveDetail(IntegratedSocialSecurityCompleteStrategySearchEntry entry, String identity) {
        ProjectionMetrics current = exhaustivePresentation.result().currentPlanBaseline().metrics();
        ProjectionMetrics candidate = entry.metrics().orElseThrow();
        ProjectionMetrics delta = entry.differencesFromCurrentPlan().orElseThrow();
        return "Deterministic After-Tax Estate Rank: "
                + entry.afterTaxEstateRank().orElseThrow()
                + identity
                + "\n" + strategySummary(entry.strategy())
                + "\n\nIntegrated financial result (future-dollar deterministic projection)"
                + metricLine("Investment growth", current.totalInvestmentGrowth(),
                        candidate.totalInvestmentGrowth(), delta.totalInvestmentGrowth())
                + metricLine("Total guaranteed income", current.totalIncome(),
                        candidate.totalIncome(), delta.totalIncome())
                + metricLine("Total taxes", current.totalTaxes(), candidate.totalTaxes(), delta.totalTaxes())
                + metricLine("Peak annual tax", current.peakAnnualTax(), candidate.peakAnnualTax(), delta.peakAnnualTax())
                + metricLine("Ending investable assets", current.endingInvestableAssets(), candidate.endingInvestableAssets(), delta.endingInvestableAssets())
                + metricLine("Ending net worth", current.endingNetWorth(), candidate.endingNetWorth(), delta.endingNetWorth())
                + metricLine("After-Tax Heir Value", current.afterTaxEstate(), candidate.afterTaxEstate(), delta.afterTaxEstate())
                + metricLine("Portfolio withdrawals", current.lifetimePortfolioWithdrawals(), candidate.lifetimePortfolioWithdrawals(), delta.lifetimePortfolioWithdrawals())
                + metricLine("Roth conversions", current.lifetimeRothConversions(), candidate.lifetimeRothConversions(), delta.lifetimeRothConversions())
                + metricLine("RMDs", current.lifetimeRequiredMinimumDistributions(), candidate.lifetimeRequiredMinimumDistributions(), delta.lifetimeRequiredMinimumDistributions())
                + metricLine("Medicare premiums", current.lifetimeMedicarePremiums(), candidate.lifetimeMedicarePremiums(), delta.lifetimeMedicarePremiums())
                + metricLine("Household Social Security", current.lifetimeHouseholdSocialSecurity(), candidate.lifetimeHouseholdSocialSecurity(), delta.lifetimeHouseholdSocialSecurity())
                + "\nPrimary Social Security: " + money(candidate.lifetimePrimarySocialSecurity())
                + "\nSpouse Social Security: " + money(candidate.lifetimeSpouseSocialSecurity())
                + "\nProjection detail retained: " + entry.retainedDetail().isPresent();
    }

    private static String formatDuration(Duration duration) {
        return String.format(java.util.Locale.US, "%.2f seconds", duration.toMillis() / 1000.0d);
    }

    private void configureIntegratedTable() {
        integratedTable.getColumns().add(integratedColumn("SS Rank", row ->
                Integer.toString(row.socialSecurityRank()), 65));
        integratedTable.getColumns().add(integratedColumn("Primary Retirement", row ->
                retirementElection(row.entry().strategy().primaryRetirementAge(),
                        row.entry().strategy().primaryRetirementClaimDate()), 165));
        integratedTable.getColumns().add(integratedColumn("Spouse Retirement", row ->
                retirementElection(row.entry().strategy().spouseRetirementAge(),
                        row.entry().strategy().spouseRetirementClaimDate()), 165));
        integratedTable.getColumns().add(integratedColumn("Primary Survivor Benefit Claiming Age", row ->
                survivorElection(row.entry().strategy().primarySurvivorElection()), 180));
        integratedTable.getColumns().add(integratedColumn("Spouse Survivor Benefit Claiming Age", row ->
                survivorElection(row.entry().strategy().spouseSurvivorElection()), 180));
        integratedTable.getColumns().add(integratedColumn("SS Expected PV", row ->
                row.entry().socialSecurityOnlyExpectedValue()
                        .map(value -> money(value.expectedPresentValue())).orElse("Unavailable"), 130));
        integratedTable.getColumns().add(integratedColumn("Lifetime SS", row ->
                metric(row.entry(), ProjectionMetrics::lifetimeHouseholdSocialSecurity), 125));
        integratedTable.getColumns().add(integratedColumn("Total Taxes", row ->
                metric(row.entry(), ProjectionMetrics::totalTaxes), 120));
        integratedTable.getColumns().add(integratedColumn("Portfolio Withdrawals", row ->
                metric(row.entry(), ProjectionMetrics::lifetimePortfolioWithdrawals), 155));
        integratedTable.getColumns().add(moneyColumn("Ending Investable Assets", (IntegratedSocialSecurityComparisonPresentation.Row row) ->
                row.entry().integratedResult().map(value -> value.metrics().endingInvestableAssets()).orElse(null), 165));
        integratedTable.getColumns().add(moneyColumn("After-Tax Heir Value", (IntegratedSocialSecurityComparisonPresentation.Row row) ->
                row.entry().integratedResult().map(value -> value.metrics().afterTaxEstate()).orElse(null), 150));
        integratedTable.getColumns().add(integratedColumn("Delta Estate vs Current", row ->
                difference(row.entry(), ProjectionMetrics::afterTaxEstate), 155));
        integratedTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        integratedTable.setPlaceholder(new Label(
                "Run integrated analysis to compare complete Social Security strategies."));
        integratedTable.getSelectionModel().selectedItemProperty().addListener((o, a, row) -> {
            if (row != null) {
                integratedDetails.setText(integratedDetail(row));
            }
        });
    }

    private TableColumn<IntegratedSocialSecurityComparisonPresentation.Row, String> integratedColumn(
            String title,
            java.util.function.Function<IntegratedSocialSecurityComparisonPresentation.Row, String> value,
            double width) {
        TableColumn<IntegratedSocialSecurityComparisonPresentation.Row, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    private void renderIntegrated(IntegratedSocialSecurityComparisonPresentation model) {
        IntegratedSocialSecurityStrategyResult baseline = model.comparison().currentPlanBaseline();
        ProjectionMetrics metrics = baseline.metrics();
        integratedBaseline.getChildren().setAll(
                new Label(strategySummary(baseline.evaluatedStrategy())),
                new Label("Survivor policy: current persisted projection policy"),
                new Label("Ending investable assets: " + money(metrics.endingInvestableAssets())),
                new Label("Ending net worth: " + money(metrics.endingNetWorth())),
                new Label("After-Tax Heir Value: " + money(metrics.afterTaxEstate())),
                new Label("Total taxes: " + money(metrics.totalTaxes())),
                new Label("Portfolio withdrawals: " + money(metrics.lifetimePortfolioWithdrawals())),
                new Label("Lifetime household Social Security: "
                        + money(metrics.lifetimeHouseholdSocialSecurity())));
        integratedTable.getItems().setAll(model.rows());
        integratedDetails.setText(model.rows().isEmpty()
                ? "No unique candidates were returned."
                : integratedDetail(model.rows().getFirst()));
        if (!model.rows().isEmpty()) {
            integratedTable.getSelectionModel().selectFirst();
        }
    }

    private String integratedDetail(IntegratedSocialSecurityComparisonPresentation.Row row) {
        IntegratedSocialSecurityStrategyComparisonEntry entry = row.entry();
        String elections = "Social Security rank: " + row.socialSecurityRank()
                + "\n" + strategySummary(entry.strategy());
        if (!entry.successful()) {
            return elections + "\n\nUnavailable: " + entry.failure().orElseThrow().message();
        }
        ProjectionMetrics current = integratedPresentation.comparison()
                .currentPlanBaseline().metrics();
        ProjectionMetrics candidate = entry.integratedResult().orElseThrow().metrics();
        ProjectionMetrics delta = entry.differencesFromCurrentPlan().orElseThrow();
        return elections
                + "\n\nSS-only analysis"
                + "\nExpected PV: " + entry.socialSecurityOnlyExpectedValue()
                        .map(value -> money(value.expectedPresentValue())).orElse("Unavailable")
                + "\n\nIntegrated financial result (future-dollar deterministic projection)"
                + metricLine("Investment growth", current.totalInvestmentGrowth(),
                        candidate.totalInvestmentGrowth(), delta.totalInvestmentGrowth())
                + metricLine("Total guaranteed income", current.totalIncome(),
                        candidate.totalIncome(), delta.totalIncome())
                + metricLine("Total taxes", current.totalTaxes(), candidate.totalTaxes(), delta.totalTaxes())
                + metricLine("Peak annual tax", current.peakAnnualTax(),
                        candidate.peakAnnualTax(), delta.peakAnnualTax())
                + metricLine("Ending investable assets", current.endingInvestableAssets(),
                        candidate.endingInvestableAssets(), delta.endingInvestableAssets())
                + metricLine("Ending net worth", current.endingNetWorth(),
                        candidate.endingNetWorth(), delta.endingNetWorth())
                + metricLine("After-Tax Heir Value", current.afterTaxEstate(),
                        candidate.afterTaxEstate(), delta.afterTaxEstate())
                + metricLine("Portfolio withdrawals", current.lifetimePortfolioWithdrawals(),
                        candidate.lifetimePortfolioWithdrawals(), delta.lifetimePortfolioWithdrawals())
                + metricLine("Roth conversions", current.lifetimeRothConversions(),
                        candidate.lifetimeRothConversions(), delta.lifetimeRothConversions())
                + metricLine("RMDs", current.lifetimeRequiredMinimumDistributions(),
                        candidate.lifetimeRequiredMinimumDistributions(),
                        delta.lifetimeRequiredMinimumDistributions())
                + metricLine("Medicare premiums", current.lifetimeMedicarePremiums(),
                        candidate.lifetimeMedicarePremiums(), delta.lifetimeMedicarePremiums())
                + metricLine("Household Social Security", current.lifetimeHouseholdSocialSecurity(),
                        candidate.lifetimeHouseholdSocialSecurity(),
                        delta.lifetimeHouseholdSocialSecurity())
                + "\nPrimary Social Security: " + money(candidate.lifetimePrimarySocialSecurity())
                + "\nSpouse Social Security: " + money(candidate.lifetimeSpouseSocialSecurity());
    }

    private void configureStrategyTable() {
        strategies.getColumns().add(column("Rank", row -> Integer.toString(row.rank())));
        strategies.getColumns().add(column("Primary Ret.", row ->
                "Age " + row.cell().strategy().primaryRetirementAge()));
        strategies.getColumns().add(column("Spouse Ret.", row ->
                "Age " + row.cell().strategy().spouseRetirementAge()));
        strategies.getColumns().add(column("Primary Survivor Benefit Claiming Age", row ->
                row.cell().strategy().primarySurvivorElection().label()));
        strategies.getColumns().add(column("Spouse Survivor Benefit Claiming Age", row ->
                row.cell().strategy().spouseSurvivorElection().label()));
        strategies.getColumns().add(column("Expected PV", row ->
                money(row.cell().expectedPresentValue())));
        strategies.getColumns().add(column("Expected Real", row ->
                money(row.cell().expectedRealBenefits())));
        strategies.getColumns().add(column("Expected Nominal", row ->
                money(row.cell().expectedNominalBenefits())));
        strategies.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        strategies.getSelectionModel().selectedItemProperty().addListener((o, a, row) -> {
            if (row != null) {
                strategyDetails.setText(strategyText(row.cell())
                        + "\nExpected primary selected: "
                        + money(row.cell().expectedPrimarySelectedBenefits())
                        + " | Expected spouse selected: "
                        + money(row.cell().expectedSpouseSelectedBenefits()));
            }
        });
    }

    private TableColumn<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy, String> column(
            String title,
            java.util.function.Function<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy, String> value) {
        TableColumn<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        return column;
    }

    private String strategyText(SocialSecuritySurvivorClaimingOptimizationCell cell) {
        SocialSecurityHouseholdClaimingStrategy strategy = cell.strategy();
        return "Primary retirement: Age " + strategy.primaryRetirementAge()
                + " — " + DATE.format(strategy.primaryRetirementClaimDate())
                + "\nPrimary Survivor Benefit Claiming Age: " + strategy.primarySurvivorElection().label()
                + " — " + DATE.format(strategy.primarySurvivorElection().claimDate())
                + "\nSpouse retirement: Age " + strategy.spouseRetirementAge()
                + " — " + DATE.format(strategy.spouseRetirementClaimDate())
                + "\nSpouse Survivor Benefit Claiming Age: " + strategy.spouseSurvivorElection().label()
                + " — " + DATE.format(strategy.spouseSurvivorElection().claimDate());
    }

    private static String retirementElection(int age, LocalDate date) {
        return "Age " + age + " - " + DATE.format(date);
    }

    private static String survivorElection(SocialSecuritySurvivorClaimingCandidate candidate) {
        return candidate.label() + " - " + DATE.format(candidate.claimDate());
    }

    private static String strategySummary(SocialSecurityHouseholdClaimingStrategy strategy) {
        return "Primary retirement: "
                + retirementElection(strategy.primaryRetirementAge(),
                        strategy.primaryRetirementClaimDate())
                + "\nSpouse retirement: "
                + retirementElection(strategy.spouseRetirementAge(),
                        strategy.spouseRetirementClaimDate())
                + "\nPrimary Survivor Benefit Claiming Age: "
                + survivorElection(strategy.primarySurvivorElection())
                + "\nSpouse Survivor Benefit Claiming Age: "
                + survivorElection(strategy.spouseSurvivorElection());
    }

    private static String metric(
            IntegratedSocialSecurityStrategyComparisonEntry entry,
            java.util.function.Function<ProjectionMetrics, BigDecimal> value) {
        return entry.integratedResult()
                .map(result -> money(value.apply(result.metrics())))
                .orElse("Unavailable");
    }

    private static String difference(
            IntegratedSocialSecurityStrategyComparisonEntry entry,
            java.util.function.Function<ProjectionMetrics, BigDecimal> value) {
        return entry.differencesFromCurrentPlan()
                .map(metrics -> signedMoney(value.apply(metrics)))
                .orElse("Unavailable");
    }

    private static String metricLine(
            String label,
            BigDecimal current,
            BigDecimal candidate,
            BigDecimal difference) {
        return "\n" + label + ": Current " + money(current)
                + " | Candidate " + money(candidate)
                + " | Difference " + signedMoney(difference);
    }

    private static String integratedCompletionText(
            IntegratedSocialSecurityComparisonPresentation model) {
        String text = model.successfulCount() + " strategies evaluated";
        if (model.failureCount() > 0) {
            text += ", " + model.failureCount() + " unavailable";
        }
        return text + ".";
    }

    private String assumptionText(
            SocialSecurityStrategyAnalysisContext context,
            SocialSecuritySurvivorClaimingOptimizationResult result) {
        SocialSecurityMortalityTableMetadata metadata = context.mortalityMetadata();
        return "Mortality table: " + metadata.displayName()
                + "\nSource: " + metadata.sourceName() + " — " + metadata.sourceVersion()
                + "\nType: " + metadata.tableType()
                + "\nPrimary category: " + context.primaryMortalityCategory()
                + " x " + factor(context.primaryMortalityAdjustment())
                + "\nSpouse category: " + context.spouseMortalityCategory()
                + " x " + factor(context.spouseMortalityAdjustment())
                + "\nIndependence assumption: primary and spouse mortality are independent"
                + "\nSocial Security COLA: " + percent(
                        context.request().retirementGridRequest().baseStrategy().socialSecurityColaRate())
                + "\nReal discount rate: " + percent(
                        context.request().retirementGridRequest().realDiscountRate())
                + "\nMortality conditioning date: " + DATE.format(context.longevityAssumptions().mortalityBaseDate())
                + "\nSS remaining-benefit analysis start: " + DATE.format(context.request().retirementGridRequest().baseStrategy().analysisDate())
                + "\nValuation date: " + DATE.format(
                        context.request().retirementGridRequest().presentValueBaseDate())
                + "\nRetirement claims tested: whole ages 62–70"
                + "\nSurvivor claims tested: age 60, whole years, plus exact survivor FRA"
                + "\nStage-1 cutoff: top " + context.request().settings().topRetirementCandidates()
                + " expected-PV cells plus cutoff ties"
                + "\nRanking objective: mortality-weighted expected present value"
                + "\nStage-1 strategies: " + result.retirementGrid().cells().size()
                + "\nStage-2 strategies: " + result.stageTwoStrategyCount()
                + "\nMortality scenarios: " + result.retirementGrid().jointMortalityScenarios().size()
                + "\nStage-2 deterministic evaluations: "
                + result.stageTwoDeterministicEvaluationCount();
    }

    private void markStale() {
        markStale(SocialSecurityAnalyzerJobController.Change.ASSUMPTIONS);
    }

    private void baselineChanged() {
        if (applyingParameters()) return;
        parameterBindings.values().forEach(SocialSecurityParameterBinding::edited);
        jobs.invalidate(SocialSecurityAnalyzerJobController.Change.WEIGHTED_SETTINGS);
        refreshInputSummary();
        if (weightedPresentation != null) {
            weightedCurrent = false;
            weightedView.stale.setText("Current Strategy baseline changed — rerun weighted analysis.");
            refreshWeightedComparison();
        } else {
            weightedView.initialCurrent(baselineInputs.snapshot());
        }
        if (weightedPersistence != null) weightedPersistence.reassess();
    }

    private void markStale(SocialSecurityAnalyzerJobController.Change change) {
        if (applyingParameters()) return;
        assumptionsRevision++;
        jobs.invalidate(change);
        refreshInputSummary();
        setAnalysisBusy(jobs.state() != SocialSecurityAnalyzerJobController.State.IDLE);
        if (weightedPresentation != null) {
            weightedCurrent = false;
            weightedView.stale.setText("Inputs changed — rerun");
            refreshWeightedComparison();
        }
        if (presentation != null) {
            socialSecurityResultCurrent = false;
            refreshExhaustiveSocialSecurityReference();
            stale.setText("Inputs changed - run Social Security analysis again.");
            integratedRunButton.setDisable(true);
            markIntegratedStale(
                    "Social Security inputs changed - rerun Social Security analysis and Quick Comparison.");
        }
        refreshQuickPrerequisite();
        if (weightedPersistence != null) weightedPersistence.reassess();
        if (socialSecurityPersistence != null) socialSecurityPersistence.reassess();
    }

    private void markIntegratedStale(String message) {
        if (integratedPresentation != null) {
            integratedStale.setText(message);
        }
    }

    private void refreshExhaustiveSocialSecurityReference() {
        if (exhaustivePresentation != null) {
            exhaustiveSocialSecurityReference.setText(socialSecurityCrossReference(exhaustivePresentation));
        }
    }

    private void refreshQuickPrerequisite() {
        quickPrerequisite.setText(socialSecurityResultCurrent
                ? "Social Security Only result current — prerequisite satisfied. This completed ranking supplies candidates for the next Quick Comparison."
                : "Social Security Only result required — Run Social Security Only with the current inputs before running Quick Comparison.");
        if (socialSecurityResultCurrent && presentation != null) {
            var basis = presentation.result().request().retirementGridRequest();
            quickPrerequisite.setText(quickPrerequisite.getText() + "\nCompleted SS candidate basis: conditioned "
                    + basis.mortalityBaseDate() + "; valued " + basis.presentValueBaseDate()
                    + "; real discount " + basis.realDiscountRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%.");
        }
    }

    private void setAnalysisBusy(boolean disabled) {
        refreshQuickPrerequisite();
        refreshExportActions();
        if (disabled) {
            deterministicExportPdf.setDisable(true);
            weightedView.exportPdf.setDisable(true);
        }
        runButton.setDisable(disabled);
        integratedRunButton.setDisable(disabled || !socialSecurityResultCurrent);
        exhaustiveRunButton.setDisable(disabled);
        weightedView.run.setDisable(disabled || !weightedInputsValid());
        weightedView.heatMap.setAnalysisBusy(disabled);
        deterministicHeatMap.setAnalysisBusy(disabled);
        integratedCandidateCount.setDisable(disabled);
        primaryMortalityAdjustment.setDisable(disabled);
        spouseMortalityAdjustment.setDisable(disabled);
        discountRate.setDisable(disabled);
        pvDate.setDisable(disabled);
        mortalityDate.setDisable(disabled);
    }

    private boolean weightedInputsValid() {
        if (pvDate.getValue() == null || mortalityDate.getValue() == null) return false;
        try {
            adjustment(primaryMortalityAdjustment.getText(), "Primary");
            adjustment(spouseMortalityAdjustment.getText(), "Spouse");
            com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator.validateRate(
                    new BigDecimal(discountRate.getText().trim()).movePointLeft(2));
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private IntegratedReportContext captureReportContext(boolean weighted) {
        return IntegratedReportContext.capture(plan, weighted,
                new SocialSecurityAnalyzerInputSummary.AnalyzerValues(
                        validAdjustment(primaryMortalityAdjustment.getText()), validAdjustment(spouseMortalityAdjustment.getText()),
                        mortalityDate.getValue(), pvDate.getValue(), validDiscountRate(discountRate.getText())), baselineInputs.snapshot());
    }

    private boolean canExportPdf(boolean weighted) {
        if (exportBusy || jobs.state() != SocialSecurityAnalyzerJobController.State.IDLE) return false;
        if (!weighted && exhaustivePersistence != null)
            return exhaustivePersistence.exportable() && exhaustivePresentation != null && deterministicReportContext != null;
        if (weighted && weightedPersistence != null)
            return weightedPersistence.exportable() && weightedPresentation != null && weightedReportContext != null;
        return weighted ? weightedCurrent && weightedPresentation != null && weightedReportContext != null
                && weightedPresentation.planRevision() == planRevision && weightedView.stale.getText().isEmpty()
                && weightedPresentation.assumptionsRevision() == assumptionsRevision
                && !weightedPresentation.result().rankedSuccessfulEntries().isEmpty()
                : exhaustivePresentation != null && deterministicReportContext != null
                && exhaustiveRevision == planRevision && exhaustiveStale.getText().isEmpty()
                && !exhaustivePresentation.result().rankedSuccessfulEntries().isEmpty();
    }

    private void refreshExportActions() {
        deterministicExportPdf.setDisable(!canExportPdf(false));
        weightedView.exportPdf.setDisable(!canExportPdf(true));
    }

    private com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport capturePdfReport(boolean weighted) {
        if (!canExportPdf(weighted)) throw new IllegalStateException("Run a current, successful analysis before exporting PDF.");
        if (weighted) {
            var heatMap = weightedView.heatMap;
            var selectedCell = heatMap.reportSelection();
            var selected = weightedView.resultTabs.getSelectionModel().getSelectedItem() == weightedView.heatMapTab
                    ? selectedCell.strategy().flatMap(cell -> weightedPresentation.result().orderedEntries().stream()
                            .filter(entry -> entry.inputOrder() == cell.inputOrder()).findFirst()).orElse(null)
                    : weightedView.reportSelection();
            var deterministic = exhaustivePresentation == null ? null : exhaustivePresentation.result();
            boolean compatible = IntegratedAnalysisComparisonPresentation.compatible(weightedPresentation,
                    deterministic, exhaustiveRevision, planRevision, weightedCurrent);
            var report = IntegratedAnalyzerReportAdapter.weighted(weightedReportContext, weightedPresentation,
                    heatMap.reportModel(), heatMap.metric.getValue(), selectedCell, selected,
                    List.copyOf(weightedView.table.getItems()), historicalWeightedComparison != null ? historicalWeightedComparison : IntegratedAnalysisComparisonPresentation.create(weightedPresentation,
                            deterministic, exhaustiveRevision, planRevision, weightedCurrent), compatible ? deterministic : null, historicalWeightedRanks);
            return weightedPersistence != null && weightedPersistence.exportable() ? weightedPersistence.report(report) : report;
        }
        var selectedCell = deterministicHeatMap.reportSelection();
        var selected = deterministicResultTabs.getSelectionModel().getSelectedItem() == deterministicHeatMapTab
                ? selectedCell.strategy().flatMap(cell -> exhaustivePresentation.result().entries().stream()
                        .filter(entry -> entry.generationOrder() == cell.inputOrder()).findFirst()).orElse(null)
                : deterministicSelectedEntry;
        var report = IntegratedAnalyzerReportAdapter.deterministic(deterministicReportContext, exhaustivePresentation,
                deterministicHeatMap.reportModel(), deterministicHeatMap.metric.getValue(), selectedCell, selected,
                List.copyOf(exhaustiveTable.getItems()), socialSecurityCrossReference(exhaustivePresentation));
        return exhaustivePersistence != null ? exhaustivePersistence.report(report) : report;
    }

    private void exportPdf(boolean weighted) {
        if (!canExportPdf(weighted)) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Integrated Analysis PDF");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        chooser.setInitialFileName(weighted ? "Social-Security-Integrated-Longevity-Weighted-Analysis.pdf"
                : "Social-Security-Integrated-Deterministic-Analysis.pdf");
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null || !canExportPdf(weighted)) return;
        try {
            var report = capturePdfReport(weighted);
            exportBusy = true;
            refreshExportActions();
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override protected Void call() throws Exception {
                    new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerPdfExporter().export(report, file.toPath());
                    return null;
                }
            };
            task.setOnSucceeded(event -> {
                exportBusy = false;
                refreshExportActions();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Analysis report saved to " + file.getAbsolutePath());
                alert.initOwner(stage);
                alert.setTitle("Export PDF");
                alert.setHeaderText("PDF export complete");
                alert.show();
            });
            task.setOnFailed(event -> {
                exportBusy = false;
                refreshExportActions();
                showPdfError(task.getException());
            });
            Thread.ofVirtual().name("integrated-analysis-pdf").start(task);
        } catch (RuntimeException failure) {
            exportBusy = false;
            refreshExportActions();
            showPdfError(failure);
        }
    }

    private void showPdfError(Throwable failure) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Export PDF");
        alert.setHeaderText("Unable to export analysis report");
        alert.setContentText(SocialSecurityAnalysisFailurePresentation.message(failure));
        alert.show();
    }

    private void refreshInputSummary() {
        currentInputSummary = SocialSecurityAnalyzerInputSummary.from(plan,
                new SocialSecurityAnalyzerInputSummary.AnalyzerValues(
                        validAdjustment(primaryMortalityAdjustment.getText()),
                        validAdjustment(spouseMortalityAdjustment.getText()), mortalityDate.getValue(), pvDate.getValue(),
                        validDiscountRate(discountRate.getText())), baselineInputs.snapshot());
        inputSummary.show(currentInputSummary);
        if (navigation != null) refreshContextualInputs();
    }

    private void refreshContextualInputs() {
        var mode = navigation.selected().mode();
        if (exhaustivePersistence != null && mode == SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE) exhaustivePersistence.load();
        if (weightedPersistence != null && mode == SocialSecurityAnalyzerJobController.Mode.WEIGHTED) weightedPersistence.load();
        if (socialSecurityPersistence != null && (mode == SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY
                || mode == SocialSecurityAnalyzerJobController.Mode.QUICK)) socialSecurityPersistence.load();
        boolean longevity = mode == SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY
                || mode == SocialSecurityAnalyzerJobController.Mode.WEIGHTED;
        sharedConfiguration.setVisible(longevity);
        sharedConfiguration.setManaged(longevity);
        var rows = switch (mode) {
            case SOCIAL_SECURITY -> List.of("Social Security COLA");
            case QUICK, EXHAUSTIVE -> List.of("Projection start date", "Planning Horizon - calendar years",
                    "Configured plan death scenario", "Investment return assumptions", "General inflation", "State / local tax jurisdiction");
            case WEIGHTED -> List.of("Projection start date", "Weighted scenario horizon", "Social Security COLA");
        };
        inheritedPlan.setText(AnalyzerConfigurationLayout.household(plan, longevity)
                + "\n" + currentInputSummary.rows().stream().filter(row -> rows.contains(row.input()))
                        .map(row -> row.input() + ": " + (row.input().equals("Weighted scenario horizon")
                                ? "Household second death; configured horizon is reference only. See Input Reference for the snapshot convention."
                                : row.value())).collect(java.util.stream.Collectors.joining("\n")));
    }

    private static BigDecimal validAdjustment(String text) {
        try { return adjustment(text, "Mortality").factor(); }
        catch (RuntimeException invalid) { return null; }
    }

    private static BigDecimal validDiscountRate(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim()).movePointLeft(2);
            com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator.validateRate(value);
            return value;
        } catch (RuntimeException invalid) { return null; }
    }

    private void showError(Throwable throwable) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Social Security Analysis");
        alert.setHeaderText("Unable to run Social Security strategy analysis");
        alert.setContentText(SocialSecurityAnalysisFailurePresentation.message(throwable));
        alert.showAndWait();
    }

    private void refreshJobState() {
        var state = jobs.state();
        if (exhaustivePersistence != null && jobs.mode() == SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE
                && state == SocialSecurityAnalyzerJobController.State.IDLE)
            javafx.application.Platform.runLater(exhaustivePersistence::finishReplacement);
        boolean busy = state != SocialSecurityAnalyzerJobController.State.IDLE;
        navigation.showActivity(busy ? jobs.mode() : null, jobs.status());
        setAnalysisBusy(busy);
        sharedCancel.setVisible(busy);
        sharedCancel.setManaged(busy);
        sharedCancel.setDisable(state != SocialSecurityAnalyzerJobController.State.RUNNING);
        sharedStatus.setText(jobs.status());
        if (busy) {
            sharedProgress.show(jobs.progress());
        } else {
            sharedProgress.hide();
        }
        for (Button button : List.of(socialSecurityCancelButton, integratedCancelButton, exhaustiveCancelButton)) {
            button.setVisible(busy);
            button.setManaged(busy);
            button.setDisable(state != SocialSecurityAnalyzerJobController.State.RUNNING);
        }
        socialSecurityProgress.hide();
        integratedProgress.hide();
        exhaustiveProgress.hide();
        if (jobs.mode() == null) {
            return;
        }
        Label label = switch (jobs.mode()) {
            case SOCIAL_SECURITY -> status;
            case QUICK -> integratedStatus;
            case EXHAUSTIVE -> exhaustiveStatus;
            case WEIGHTED -> weightedView.status;
        };
        label.setText(jobs.status());
        if (busy) {
            if (jobs.mode() == SocialSecurityAnalyzerJobController.Mode.WEIGHTED && weightedPresentation != null) {
                weightedView.status.setText(jobs.status() + " — previous successful result shown");
            }
        }
    }

    private <T> void observe(javafx.beans.value.ObservableValue<T> value, Runnable action) {
        javafx.beans.value.ChangeListener<T> listener = (observable, oldValue, newValue) -> {
            if (applyingParameters()) return;
            parameterBindings.values().forEach(SocialSecurityParameterBinding::edited);
            action.run();
        };
        value.addListener(listener);
        detachInputListeners.add(() -> value.removeListener(listener));
    }

    private void close() {
        parameterBindings.values().forEach(SocialSecurityParameterBinding::close);
        if (singlePersonView != null) singlePersonView.closeParameters();
        if (exhaustivePersistence != null) exhaustivePersistence.close();
        if (weightedPersistence != null) weightedPersistence.close();
        if (socialSecurityPersistence != null) socialSecurityPersistence.close();
        if (singlePersonView != null && singlePersonView.weightedView.persistence != null) singlePersonView.weightedView.persistence.close();
        jobs.close();
        detachPlanListener.run();
        detachPlanListener = () -> { };
        detachInputListeners.forEach(Runnable::run);
        detachInputListeners.clear();
        socialSecurityProgress.hide();
        integratedProgress.hide();
        exhaustiveProgress.hide();
        sharedProgress.hide();
    }
    private static Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        return label;
    }

    private static Label wrappedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static String money(BigDecimal value) {
        return UIFormatters.money(value);
    }

    private static String signedMoney(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + money(value);
    }

    private static String percent(BigDecimal value) {
        return value.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    static SocialSecurityMortalityAdjustment adjustment(
            String text,
            String owner) {
        final BigDecimal factor;
        try {
            factor = new BigDecimal(text.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    owner + " mortality adjustment must be a number from 0.50 to 3.00.");
        }
        if (factor.compareTo(new BigDecimal("0.50")) < 0
                || factor.compareTo(new BigDecimal("3.00")) > 0) {
            throw new IllegalArgumentException(
                    owner + " mortality adjustment must be from 0.50 to 3.00.");
        }
        return SocialSecurityMortalityAdjustment.of(factor);
    }

    private static String factor(SocialSecurityMortalityAdjustment adjustment) {
        return adjustment.factor().setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString() + "x";
    }

    private record RunResult(
            SocialSecurityStrategyAnalysisContext context,
            SocialSecuritySurvivorClaimingOptimizationResult result) {
    }
}
