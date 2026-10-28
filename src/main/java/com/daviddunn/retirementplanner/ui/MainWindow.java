package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsRequest;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.ui.charts.PortfolioChartView;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.RothConversionView;
import com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityStrategyAnalyzerDialog;

import com.daviddunn.retirementplanner.ui.views.*;
import com.daviddunn.retirementplanner.ui.dialogs.*;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.List;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;


public class MainWindow {

    private final ApplicationController controller;

    private final BorderPane root;

    private final HouseholdView householdView;
    private final AccountsView accountsView;
    private final IncomeSourcesView incomeSourcesView;
    private final ProjectionYearView projectionYearView;
    private final ExpensesView expensesView;
    private final AssumptionsView assumptionsView;
    private final ResultsView resultsView;
    private final DashboardView dashboardView;
    private final PortfolioChartView portfolioChartView;
    private final RothConversionView rothConversionView;
    private final ResultsSummaryView resultsSummaryView;
    private final NonInvestableAssetsView nonInvestableAssetsView;

    private Stage stage;
    private boolean collectingViewEdits;
    private final Label statusLabel;
    private final Label projectionFundingNotice = new Label();

    public MainWindow() {
        this(new ApplicationController(), true);
    }

    public MainWindow(ApplicationController controller) {
        this(controller, false);
    }

    private MainWindow(ApplicationController controller, boolean reopenLastPlan) {
        this.controller = java.util.Objects.requireNonNull(controller);

        householdView = new HouseholdView();
        accountsView = new AccountsView();
        incomeSourcesView = new IncomeSourcesView();
        projectionYearView = new ProjectionYearView();
        expensesView = new ExpensesView();
        assumptionsView = new AssumptionsView();
        resultsView = new ResultsView();
        resultsSummaryView =
                new ResultsSummaryView(controller);
        dashboardView = new DashboardView();
        portfolioChartView = new PortfolioChartView();
        rothConversionView =
                new RothConversionView();
        nonInvestableAssetsView =
                new NonInvestableAssetsView();

        statusLabel = new Label("Ready");

        root = new BorderPane();

        wireEvents();

        root.setTop(createMenuBar());
        root.setCenter(createTabPane());
        root.setBottom(createStatusBar());

        //loadCurrentPlan();
        if (reopenLastPlan) controller.openLastPlan();
        loadCurrentPlan(reopenLastPlan);
    }

    private void wireEvents() {

        householdView.setOnPlanChanged(() -> {
            if (collectingViewEdits) {
                controller.markModified();
                controller.invalidateProjection();
                updateWindowTitle();
            }
            else {
                onPlanChanged();
            }
        });

        expensesView.setOnPlanChanged(
                this::onPlanChanged);

        incomeSourcesView.setOnPlanChanged(
                this::onPlanChanged);

        accountsView.setOnPlanChanged(
                this::onPlanChanged);

        assumptionsView.setOnPlanChanged(() -> {
            if (collectingViewEdits) {
                controller.markModified();
                controller.invalidateProjection();
                updateWindowTitle();
            }
            else {
                onPlanChanged();
            }
        });

        assumptionsView.setOnOpeningRmdRequested(
                this::showOpeningRmdDialog);

        resultsView.setOnYearDoubleClick(
                this::showProjectionYearSummary);
        resultsView.setOnBreakEvenAnalysis(this::showBreakEvenAnalysis);
        resultsSummaryView.setOnBreakEvenAnalysis(this::showBreakEvenAnalysis);
        resultsSummaryView.setOnBaselineDetails(this::showBaselineDetails);
        resultsSummaryView.setOnSwapProjections(this::swapCurrentAndBaseline);

        rothConversionView.setOnPlanChanged(() -> {
            if (collectingViewEdits) {
                controller.markModified();
                controller.invalidateProjection();
                updateWindowTitle();
            }
            else {
                onPlanChanged();
            }
        });

        resultsSummaryView.setOnYearDoubleClick(
                this::showProjectionYearSummary);

        resultsSummaryView.setOnEconomicAssumptionsApply(
                updatedAssumptions -> {

                    controller
                            .getCurrentPlan()
                            .setPlanningAssumptions(
                                    updatedAssumptions);

                    controller.markModified();

                    //refreshAllViews();
                    onPlanChanged();

                    //updateWindowTitle();
                });

        resultsSummaryView.setOnDeathScenarioApply(
                updatedAssumptions -> {

                    controller
                            .getCurrentPlan()
                            .setPlanningAssumptions(
                                    updatedAssumptions);

                    controller.markModified();


                    refreshAllViews();

                    //updateWindowTitle();
                });

        resultsSummaryView.setOnRothConversionApply(
                request -> {

                    controller
                            .getCurrentPlan()
                            .setRothConversionRequest(
                                    request);

                    controller.markModified();

                    refreshAllViews();

                    //updateWindowTitle();
                });

        resultsSummaryView.setOnSocialSecurityApply(
                updates -> {

                    for (ResultsSummaryView.SocialSecurityUpdate update :
                            updates) {

                        Person person =
                                update.getPerson();

                        SocialSecurityIncome oldSource =
                                person.getIncomeSources()
                                        .stream()
                                        .filter(
                                                SocialSecurityIncome.class::isInstance)
                                        .map(
                                                SocialSecurityIncome.class::cast)
                                        .findFirst()
                                        .orElse(null);

                        if (oldSource != null) {

                            person.replaceIncomeSource(
                                    oldSource,
                                    update.getSource());
                        }
                    }

                    controller.markModified();

                    refreshAllViews();
                });

        nonInvestableAssetsView.setOnAssetsChanged(
                () -> {

                    controller.markModified();

                    refreshProjectionViews();

                    updateWindowTitle();

                    statusLabel.setText(
                            "Non-investable assets updated.");
                });

    }

    public Scene createScene() {
        return new Scene(root, 1200, 800);
    }

    private MenuBar createMenuBar() {

        Menu fileMenu = new Menu("File");

        MenuItem newItem = new MenuItem("New");
        MenuItem openItem = new MenuItem("Open...");
        MenuItem saveItem = new MenuItem("Save");
        MenuItem saveAsItem = new MenuItem("Save As...");
        MenuItem saveBaselineItem =
                new MenuItem("Save Current as Baseline");
        MenuItem exitItem = new MenuItem("Exit");

        saveItem.setOnAction(e -> onSave());
        saveAsItem.setOnAction(e -> onSaveAs());
        openItem.setOnAction(e -> onOpen());
        exitItem.setOnAction(e -> onExit());

        newItem.setOnAction(e -> onNew());
        saveBaselineItem.setOnAction(
                e -> onSaveCurrentAsBaseline());

        fileMenu.getItems().addAll(
                newItem,
                openItem,
                saveItem,
                saveAsItem,
                new SeparatorMenuItem(),
                saveBaselineItem,
                new SeparatorMenuItem(),
                exitItem);

        Menu analysisMenu = new Menu("Analysis");
        MenuItem socialSecurityAnalyzerItem =
                new MenuItem("Social Security Strategy Analyzer...");
        socialSecurityAnalyzerItem.setOnAction(event ->
                new SocialSecurityStrategyAnalyzerDialog(
                        stage,
                        controller).show());
        analysisMenu.getItems().add(socialSecurityAnalyzerItem);
        MenuItem monteCarloItem = new MenuItem("Monte Carlo Retirement Analysis...");
        monteCarloItem.setId("monte-carlo-analysis-menu");
        monteCarloItem.setOnAction(event ->
                new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloAnalysisDialog(
                        stage, controller).show());
        analysisMenu.getItems().add(monteCarloItem);
        MenuItem comparisonItem = new MenuItem("Monte Carlo Strategy Comparison...");
        comparisonItem.setId("monte-carlo-comparison-menu");
        MenuItem comparisonNotice = new MenuItem(
                com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloStrategyComparisonRunService.MISSING_BASELINE);
        comparisonNotice.setDisable(true);
        MenuItem singlePersonNotice = new MenuItem("Monte Carlo comparison requires matching household composition.");
        singlePersonNotice.setId("single-person-monte-carlo-notice");
        singlePersonNotice.setDisable(true);
        analysisMenu.getItems().add(singlePersonNotice);
        Runnable updateComparison = () -> {
            var plan = controller.getCurrentPlan();
            monteCarloItem.setDisable(plan == null);
            boolean available = com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloStrategyComparisonRunService.available(plan);
            boolean sameComposition = available && plan.getHousehold().hasSpouse()
                    == plan.getBaseline().getSnapshot().getHousehold().hasSpouse();
            comparisonItem.setDisable(!available || !sameComposition);
            singlePersonNotice.setText("Monte Carlo comparison requires the same household composition in Current Plan and Saved Baseline.");
            singlePersonNotice.setVisible(available && !sameComposition);
            comparisonNotice.setVisible(!available);
        };
        updateComparison.run();
        analysisMenu.setOnShowing(event -> updateComparison.run());
        comparisonItem.setOnAction(event -> new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloStrategyComparisonDialog(
                stage, controller).show());
        analysisMenu.getItems().addAll(comparisonItem, comparisonNotice);

        Menu helpMenu = new Menu("Help");

        MenuItem aboutItem = new MenuItem("About");

        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, analysisMenu, helpMenu);
    }

    private TabPane createTabPane() {

        TabPane tabPane = new TabPane();


        tabPane.getTabs().add(
                createTab(
                        "Summary",
                        resultsSummaryView));


        tabPane.getTabs().add(
                createTab("Yearly Detail", resultsView));

       // tabPane.getTabs().add(
       //         createTab("Dashboard", dashboardView));



//        tabPane.getTabs().add(
//                createTab("Charts",
//                        portfolioChartView));


        tabPane.getTabs().add(
                createTab("Assumptions", assumptionsView));


        tabPane.getTabs().add(
                createTab(
                        "Roth Conversion",
                        rothConversionView));




        tabPane.getTabs().add(
                createTab("Accounts", accountsView));

        tabPane.getTabs().add(
                createTab("Income", incomeSourcesView));


        tabPane.getTabs().add(
                createTab("Expenses", expensesView));


        tabPane.getTabs().add(
                createTab("Household", householdView));

        tabPane.getTabs().add(
                createTab(
                        "Non-Investable Assets",
                        nonInvestableAssetsView));


        //tabPane.getTabs().add(
        //        createTab("Projection", projectionYearView));



//        tabPane.getTabs().add(createTab("Accounts", accountsView));
//        tabPane.getTabs().add(createTab("Income", incomeView));
//        tabPane.getTabs().add(createTab("Expenses", expensesView));
//        tabPane.getTabs().add(createTab("Assumptions", assumptionsView));
//        tabPane.getTabs().add(createTab("Results", resultsView));

        return tabPane;
    }

    private Tab createTab(String title, javafx.scene.Node content) {

        Tab tab = new Tab(title, content);
        tab.setClosable(false);

        return tab;
    }

    private javafx.scene.layout.VBox createStatusBar() {

        projectionFundingNotice.setId("projection-funding-notice");
        projectionFundingNotice.setWrapText(true);
        projectionFundingNotice.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        projectionFundingNotice.setFocusTraversable(true);
        projectionFundingNotice.accessibleTextProperty().bind(projectionFundingNotice.textProperty());
        projectionFundingNotice.visibleProperty().bind(projectionFundingNotice.textProperty().isNotEmpty());
        projectionFundingNotice.managedProperty().bind(projectionFundingNotice.visibleProperty());
        projectionFundingNotice.setStyle("-fx-background-color: #fff6dc; -fx-text-fill: #493000; -fx-padding: 8;");
        var statusBar = new javafx.scene.layout.VBox(5, projectionFundingNotice, statusLabel);
        statusBar.setPadding(new Insets(5));

        return statusBar;
    }


    private void loadCurrentPlan() {

        loadCurrentPlan(true);
    }

    private void loadCurrentPlan(boolean runProjection) {

        RetirementPlan plan = controller.getCurrentPlan();


        householdView.load(plan);
        accountsView.load(plan);
        incomeSourcesView.load(plan);
        expensesView.load(plan);
        assumptionsView.load(plan);
        rothConversionView.load(plan);
        nonInvestableAssetsView.load(plan);

        updateWindowTitle();

        if (controller.isRestoredProjectionMode()) {
            refreshProjectionViews(false, true);
            observeProjectionRestoration(plan);
        }
        else if (runProjection) {
            refreshAllViews();
        }
        else {
            refreshProjectionViews(false);
        }
    }

    private void observeProjectionRestoration(RetirementPlan plan) {
        var restoring = controller.getProjectionRestoration();
        restoring.whenComplete((ignored, failure) -> javafx.application.Platform.runLater(() -> {
            if (controller.getCurrentPlan() != plan || !controller.isRestoredProjectionMode()) return;
            if (controller.getProjectionRestoration() != restoring) observeProjectionRestoration(plan);
            else refreshProjectionViews(false, true);
        }));
    }

    private boolean saveCurrentPlan() {

        RetirementPlan plan = controller.getCurrentPlan();

        // Validate Assumptions and Roth before Household's atomic validation/apply.
        if (!assumptionsView.validateChanges()) {
            statusLabel.setText("Please correct the Assumptions edits before saving.");
            return false;
        }
        if (!rothConversionView.validateChanges()) {
            statusLabel.setText("Please correct the Roth Conversion edits before saving.");
            return false;
        }
        boolean viewsHadEdits = householdView.isDirty() || assumptionsView.isDirty()
                || rothConversionView.isDirty();
        boolean householdApplied;
        collectingViewEdits = true;
        try {
            householdApplied = householdView.save(plan);
            if (householdApplied && !assumptionsView.save(plan)) {
                statusLabel.setText("Please correct the Assumptions edits before saving.");
                return false;
            }
            if (householdApplied && !rothConversionView.save(plan)) {
                statusLabel.setText("Please correct the Roth Conversion edits before saving.");
                return false;
            }
        }
        finally {
            collectingViewEdits = false;
        }
        if (!householdApplied) {
            statusLabel.setText("Please correct the Household edits before saving.");
            return false;
        }
        accountsView.save(plan);
        incomeSourcesView.save(plan);
        expensesView.save(plan);
        if (viewsHadEdits) {
            refreshProjectionViews();
        }
        return true;

    }

    private boolean onSave() {

        if (!controller.hasCurrentFile()) {
            return onSaveAs();
        }

        if (!saveCurrentPlan()) {
            return false;
        }

        try {

            controller.save();

            updateWindowTitle();

            statusLabel.setText(
                    "Plan saved.");
            showPersistenceWarnings();

        } catch (Exception ex) {

            statusLabel.setText(
                    "Save failed.");

            showSaveFailure();
            return false;
        }
        return true;
    }

    private boolean onSaveAs() {

        // Invalid drafts must also block Save before an untitled plan opens the file chooser.
        if (!assumptionsView.validateChanges()) {
            statusLabel.setText("Please correct the Assumptions edits before saving.");
            return false;
        }

        if (!rothConversionView.validateChanges()) {
            statusLabel.setText("Please correct the Roth Conversion edits before saving.");
            return false;
        }

        File file = choosePlanSaveFile();

        if (file == null) {
            return false;
        }

        if (!saveCurrentPlan()) {
            return false;
        }

        try {
            controller.saveAs(file.toPath());
            controller.getSnapshotCopyCompletion().thenAccept(warnings -> {
                if (!warnings.isEmpty()) javafx.application.Platform.runLater(() -> {
                    if (stage != null && stage.isShowing()) showPersistenceWarnings();
                });
            });

            statusLabel.setText("Plan saved.");

            updateWindowTitle();
            showPersistenceWarnings();

        } catch (Exception ex) {

            statusLabel.setText("Save failed.");

            showSaveFailure();
            return false;
        }
        return true;
    }

    private void onOpen() {

        if (!confirmPlanDeparture()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Open Retirement Plan");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "JSON Files",
                        "*.json"));

        File file = fileChooser.showOpenDialog(root.getScene().getWindow());

        if (file == null) {
            return;
        }

        try {

            controller.open(file.toPath());

            loadCurrentPlan();

            updateWindowTitle();

            statusLabel.setText(controller.getRecoveredBackupPath() == null
                    ? "Plan opened." : "Recovered backup — unsaved. Review, then Save As to a different filename.");
            showPersistenceWarnings();
        } catch (Exception ex) {

            statusLabel.setText("Open failed.");

            offerPlanRecovery(file.toPath());
        }
    }

    /** Native chooser retains platform overwrite confirmation; overridable for cancellation tests. */
    protected File choosePlanSaveFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Retirement Plan");
        chooser.setInitialFileName("RetirementPlan.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        return chooser.showSaveDialog(root.getScene().getWindow());
    }

    private void showSaveFailure() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Unable to save plan");
        alert.setHeaderText("The plan was not saved.");
        alert.setContentText("Existing plan data was preserved. Check folder permissions, read-only or locked files, available space and adjacent backup ownership. The location must support atomic replacement. If the existing file is damaged, use Open to review its backup, or Save As to a different filename.");
        alert.showAndWait();
    }

    public void showPersistenceWarnings() {
        var warnings = controller.takePersistenceWarnings();
        if (!warnings.isEmpty()) {
            statusLabel.setText(statusLabel.getText() + " Local storage notice (details in tooltip).");
            var details = new javafx.scene.control.Tooltip(String.join("\n\n", warnings));
            details.setWrapText(true);
            details.setMaxWidth(520);
            statusLabel.setTooltip(details);
        }
    }

    /** Called once after the stage is visible; no automatic backup activation. */
    public void showStartupPersistenceNotices() {
        showPersistenceWarnings();
        if (controller.getFailedOpenPath() != null) {
            offerPlanRecovery(controller.getFailedOpenPath());
        }
    }

    private void offerPlanRecovery(java.nio.file.Path original) {
        boolean available = controller.hasValidBackup(original);
        Alert alert = new Alert(available ? Alert.AlertType.CONFIRMATION : Alert.AlertType.WARNING);
        alert.setTitle("Unable to open plan");
        alert.setHeaderText("The original file was preserved.");
        ButtonType recover = new ButtonType("Review backup");
        alert.setContentText(available
                ? "A validated adjacent backup is available. Open it as an unsaved plan for review? Save As will be required; the damaged original will not be overwritten automatically."
                : "No valid adjacent backup is available. Use Open to select another plan. No plan file was changed.");
        if (available) alert.getButtonTypes().setAll(recover, ButtonType.CANCEL);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == recover) {
            try {
                controller.openRecoveredPlan(original);
                loadCurrentPlan();
                updateWindowTitle();
                statusLabel.setText("Recovered backup — unsaved. Review, then Save As to a different filename.");
            } catch (Exception failure) {
                statusLabel.setText("Backup could not be opened. Original files were preserved.");
            }
        }
    }

    private boolean confirmPlanDeparture() {

        if (!controller.isModified() && !householdView.isDirty()
                && !assumptionsView.isDirty() && !rothConversionView.isDirty()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Save changes before continuing?");
        alert.setContentText("The plan has unsaved changes or unapplied Household, Assumptions, or Roth Conversion edits.");
        ButtonType save = new ButtonType("Save");
        ButtonType discard = new ButtonType("Discard");
        alert.getButtonTypes().setAll(save, discard, ButtonType.CANCEL);
        ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == discard) {
            householdView.cancelChanges();
            assumptionsView.cancelChanges();
            rothConversionView.cancelChanges();
            return true;
        }
        if (choice == save) {
            return onSave();
        }
        return false;
    }
    private void onExit() {

        if (!confirmPlanDeparture()) {
            return;
        }

        finishExitAfterResultWrites(() -> root.getScene().getWindow().hide());
    }

    private boolean drainingAnalysisResults;

    /** Preserve the existing plan-departure decision, then drain accepted result I/O off FX. */
    private void finishExitAfterResultWrites(Runnable closeWindow) {
        if (drainingAnalysisResults) return;
        var store = controller.getAnalysisResults();
        var parameters = controller.getAnalysisParameters();
        if (!store.needsDrain() && !parameters.needsDrain()) { parameters.shutdown(); closeWindow.run(); return; }
        drainingAnalysisResults = true;
        parameters.beginExit();
        var notice = new Alert(Alert.AlertType.INFORMATION, "Saving completed analysis results before exit…", ButtonType.CANCEL);
        notice.setHeaderText("Finishing result saves");
        if (stage != null) notice.initOwner(stage);
        notice.show();
        notice.setOnHidden(event -> {
            if (notice.getResult() == ButtonType.CANCEL) { store.resumeAfterCancelledExit(); parameters.resumeAfterCancelledExit(); }
        });
        Thread.ofVirtual().name("analysis-result-exit-drain").start(() -> {
            var outcome = store.drain(java.time.Duration.ofSeconds(30));
            com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator.Drain parameterOutcome;
            try { parameterOutcome = parameters.drain(java.time.Duration.ofSeconds(30)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            boolean parametersSaved = parameterOutcome.complete() && parameterOutcome.failed() == 0;
            javafx.application.Platform.runLater(() -> {
                boolean cancelled = notice.getResult() == ButtonType.CANCEL;
                notice.setOnHidden(null); notice.close(); drainingAnalysisResults = false;
                if (outcome.safeToExit() && parametersSaved && !cancelled) { parameters.shutdown(); closeWindow.run(); return; }
                store.resumeAfterCancelledExit();
                parameters.resumeAfterCancelledExit();
                if (cancelled) return;
                var retry = new ButtonType("Retry result saves");
                var warning = new Alert(Alert.AlertType.WARNING,
                        "The application remains open because completed results or accepted parameters are not durably saved. Previous saved data was preserved.", retry, ButtonType.CANCEL);
                warning.setHeaderText("Analysis results could not be saved");
                if (stage != null) warning.initOwner(stage);
                if (warning.showAndWait().orElse(ButtonType.CANCEL) == retry) {
                    store.retryFailed(); parameters.retryFailed(); finishExitAfterResultWrites(closeWindow);
                }
            });
        });
    }
    private void showProjectionYearSummary(
            ProjectionYearDetailsRequest request) {

        Projection baseline = controller.getCurrentPlan().getBaseline() == null
                ? null : controller.getBaselineProjection();

        ProjectionYearDetailsDialog dialog = new ProjectionYearDetailsDialog(
                request,
                baseline == null ? List.of() : baseline.getYears(),
                baseline == null ? List.of()
                        : controller.getBaselineNonInvestableAssetProjections());
        dialog.show();
    }

    private void showBreakEvenAnalysis(com.daviddunn.retirementplanner.domain.breakeven.BreakEvenAnalysisResult result) {
        var context = controller.prepareBreakEvenContext(result);
        var dialog = new com.daviddunn.retirementplanner.ui.breakeven.BreakEvenAnalysisDialog(
                result, context, controller.prepareBreakEvenInsight(result));
        if (stage != null) dialog.initOwner(stage);
        dialog.showAndWait();
    }

    private void showBaselineDetails(com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession session) {
        var owner = controller.getCurrentPlan();
        var baseline = owner.getBaseline();
        var dialog = new com.daviddunn.retirementplanner.ui.baseline.BaselineDetailsDialog(session, controller);
        if (stage != null) dialog.initOwner(stage);
        var remove = controller.addSourcePlanRevisionListener(() -> {
            if (controller.getCurrentPlan() != owner || owner.getBaseline() != baseline) dialog.close();
        });
        try { dialog.showAndWait(); }
        finally { remove.run(); }
    }

    private boolean swappingProjections;
    private long swapUiEpoch;

    private void swapCurrentAndBaseline() {
        if (swappingProjections) return;
        ++swapUiEpoch;
        var owner = controller.getCurrentPlan();
        String reason = controller.projectionSwapUnavailableReason();
        // Saving drafts currently applies inputs and can project. Never invoke that route from swapping.
        if (householdView.isDirty() || assumptionsView.isDirty() || rothConversionView.isDirty())
            reason = "Apply or cancel pending input edits, then run and accept a matching projection before swapping. No calculation was started.";
        if (!reason.isEmpty()) { showSwapNotice(reason); return; }
        var roles = owner.getProjectionState();
        if (roles.current().id().equals(roles.baseline().id())) {
            showSwapNotice("Current and baseline already refer to the same original projection. Nothing was changed."); return;
        }
        if (controller.isModified()) {
            if (!controller.hasCurrentFile()) { showSwapNotice("Save this plan explicitly before swapping. Save As is not performed by the swap command."); return; }
            var save = new ButtonType("Save and Continue", ButtonBar.ButtonData.OK_DONE);
            var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "The former baseline becomes the editable current plan; the former current becomes the baseline. No calculation runs. Save the current plan before continuing?",
                    save, ButtonType.CANCEL);
            confirmation.setTitle("Save Before Swapping Projections?");
            confirmation.setHeaderText("Save Before Swapping Projections?");
            if (stage != null) confirmation.initOwner(stage);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != save) return;
            if (controller.getCurrentPlan() != owner || owner.getProjectionState() != roles
                    || !controller.projectionSwapUnavailableReason().isEmpty()) {
                showSwapNotice("The plan or projection changed while confirmation was open. Nothing was swapped."); return;
            }
            swappingProjections = true;
            runSwapBackground(controller.saveBeforeProjectionSwap(), owner, saved -> {
                controller.finishProjectionSwapSave(saved);
                prepareProjectionSwap(owner);
            });
        } else {
            swappingProjections = true;
            prepareProjectionSwap(owner);
        }
    }

    private void prepareProjectionSwap(RetirementPlan owner) {
        runSwapBackground(controller.prepareProjectionSwap(), owner, prepared -> {
            boolean changed = controller.commitProjectionSwap(prepared);
            if (changed) loadCurrentPlan(false); // Accepted caches only; no invalidate/reproject path.
            statusLabel.setText(changed ? controller.getProjectionSnapshotNotice() : "Both roles already identify the same projection. Nothing changed.");
            updateWindowTitle();
            swappingProjections = false;
        });
    }

    private <T> void runSwapBackground(java.util.concurrent.CompletionStage<T> work, RetirementPlan owner,
            java.util.function.Consumer<T> success) {
        long operation = swapUiEpoch;
        var progress = new Dialog<Void>();
        progress.setTitle("Preparing Projection Swap");
        progress.setHeaderText("Verifying original projection results. No financial calculation runs.");
        progress.getDialogPane().setContent(new javafx.scene.control.ProgressIndicator());
        progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        if (stage != null) progress.initOwner(stage);
        var finished = new java.util.concurrent.atomic.AtomicBoolean();
        progress.setOnHidden(event -> {
            if (!finished.get() && operation == swapUiEpoch) { ++swapUiEpoch; controller.cancelProjectionSwap(); swappingProjections = false; }
        });
        progress.show();
        work.whenComplete((value, failure) -> javafx.application.Platform.runLater(() -> {
            if (operation != swapUiEpoch) { finished.set(true); progress.close(); return; }
            if (!progress.isShowing() || controller.getCurrentPlan() != owner || stage != null && !stage.isShowing()) {
                finished.set(true); progress.close(); controller.cancelProjectionSwap(); swappingProjections = false; return;
            }
            finished.set(true); progress.close();
            try {
                if (failure != null) {
                    controller.cancelProjectionSwap(); swappingProjections = false;
                    showSwapNotice("Projection swap could not be completed. Original roles were retained. "
                            + (failure.getCause() instanceof IllegalStateException cause ? cause.getMessage() : "Review persistence and projection readiness before retrying."));
                } else success.accept(value);
            } catch (IllegalStateException rejected) {
                controller.cancelProjectionSwap(); swappingProjections = false; showSwapNotice(rejected.getMessage());
            }
        }));
    }

    private void showSwapNotice(String text) {
        var notice = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        notice.setTitle("Swap Current and Baseline"); notice.setHeaderText("Projection swap");
        if (stage != null) notice.initOwner(stage);
        notice.showAndWait();
    }
    private void refreshProjectionViews() {

        refreshProjectionViews(true);
    }

    private void refreshProjectionViews(boolean runProjection) {
        refreshProjectionViews(runProjection, false);
    }

    private void refreshProjectionViews(boolean runProjection, boolean restored) {

        resultsView.setBreakEvenAnalysis(null);
        resultsSummaryView.setBreakEvenAnalysis(null);

        if (!restored) controller.invalidateProjection();
        projectionFundingNotice.setText("");

        try {
            Projection projection =
                    restored ? controller.peekCurrentProjection() : runProjection ? controller.getCurrentProjection() : null;

            ProjectionSummary summary =
                    (runProjection || restored && projection != null) ? controller.getCurrentProjectionSummary() : null;

            dashboardView.load(summary);
            portfolioChartView.load(projection);
            projectionYearView.load(projection);
            resultsView.load(
                    projection,
                    (runProjection || restored && projection != null) ? controller.getCurrentNonInvestableAssetProjections() : List.of());
            assumptionsView.refresh(
                    controller.getCurrentPlan());

            resultsSummaryView.load(
                    controller.getCurrentPlan(),
                    projection,
                    (runProjection || restored && projection != null) ? controller.getCurrentNonInvestableAssetProjections() : List.of());

            var breakEven = controller.getCachedBreakEvenAnalysis();
            resultsView.setBreakEvenAnalysis(breakEven);
            resultsSummaryView.setBreakEvenAnalysis(breakEven);

            var funding = controller.getCurrentFundingFailure();
            funding.ifPresent(failure -> projectionFundingNotice.setText(
                    ProjectionFailureMessages.insufficientFundingMessage(failure)));
            statusLabel.setText(funding.isPresent() ? "Projection not completed: insufficient eligible funds."
                    : projection == null
                            ? "Complete the required plan information to run a projection."
                            : "Projection updated.");
            if (restored) statusLabel.setText(controller.getProjectionSnapshotNotice());
        }
        catch (Exception ex) {

            handleProjectionFailure(ex);
        }
    }

    public void setStage(Stage stage) {

        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            if (!confirmPlanDeparture()) {
                event.consume();
            } else if (controller.getAnalysisResults().needsDrain()) {
                event.consume();
                finishExitAfterResultWrites(stage::hide);
            }
        });

        updateWindowTitle();
    }


    private void updateWindowTitle() {

        if (stage == null) {
            return;
        }

        StringBuilder title =
                new StringBuilder("Retirement Planner");

        title.append(" - ");

        if (controller.hasCurrentFile()) {

            title.append(
                    controller.getCurrentFile()
                            .getFileName());
        }
        else {

            title.append("Untitled");
        }

        if (controller.isModified()) {

            title.append(" *");
        }

        stage.setTitle(
                title.toString());
    }

    private void onNew() {

        new com.daviddunn.retirementplanner.ui.wizard.NewRetirementPlanWizard(stage)
                .showAndWait().ifPresent(plan -> {
                    // Opening/canceling the wizard must preserve even unapplied tab edits.
                    if (!confirmPlanDeparture()) {
                        return;
                    }
                    controller.newPlan(plan);
                    // Legal empty plans retain Phase 5A's deferred projection; populated plans use the normal engine.
                    loadCurrentPlan(false);
                    if (!plan.getAccountPortfolio().getAccounts().isEmpty()
                            || plan.getHousehold().members().stream().anyMatch(person -> !person.getIncomeSources().isEmpty())
                            || !plan.getHousehold().getExpenses().isEmpty()) {
                        refreshProjectionViews();
                    }
                    TabPane tabs = (TabPane) root.getCenter();
                    tabs.getSelectionModel().select(tabs.getTabs().stream()
                            .filter(tab -> tab.getContent() == resultsSummaryView)
                            .findFirst().orElseThrow());
                    if (controller.getCurrentFundingFailure().isPresent()) {
                        projectionFundingNotice.setText("Plan created successfully. " + projectionFundingNotice.getText());
                        statusLabel.setText("New plan created; projection not completed. Continue editing using the tabs.");
                    }
                    else {
                        statusLabel.setText("New plan. Continue editing using the tabs.");
                    }
                });
    }

    private void refreshAllViews() {

        RetirementPlan plan =
                controller.getCurrentPlan();

        householdView.refresh(plan);
        accountsView.load(plan);
        incomeSourcesView.load(plan);
        expensesView.load(plan);
        assumptionsView.refresh(plan);
        rothConversionView.refresh(plan);

        refreshProjectionViews();

        updateWindowTitle();
    }

    private void onPlanChanged() {

        controller.markModified();

        refreshProjectionViews();

        updateWindowTitle();
    }

    private void showOpeningRmdDialog() {

        OpeningRmdDialog dialog =
                new OpeningRmdDialog(
                        controller.getCurrentPlan());

        dialog.showAndWait().ifPresent(saved -> {
            controller.markModified();
            refreshAllViews();
            statusLabel.setText("Opening RMD information updated.");
        });
    }

    private void handleProjectionFailure(
            Exception exception) {

        var unavailableMessage = ProjectionFailureMessages.stateTaxUnavailableMessage(exception);
        if (unavailableMessage.isPresent()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("State Tax Unavailable");
            alert.setHeaderText("State income tax could not be estimated.");
            alert.setContentText(unavailableMessage.orElseThrow());
            alert.showAndWait();
            statusLabel.setText("Projection unavailable: state income tax could not be estimated.");
            return;
        }

        if (ProjectionFailureMessages.isOpeningRmdValidation(exception)) {

            String message = exception.getMessage();

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Opening RMD Information Required");
            alert.setHeaderText("Opening RMD information is required.");
            alert.setContentText(message);

            ButtonType enterOpeningRmdInformation = new ButtonType(
                    "Enter Opening RMD Information");
            alert.getButtonTypes().setAll(
                    ButtonType.CANCEL,
                    enterOpeningRmdInformation);

            alert.showAndWait();

            if (alert.getResult() == enterOpeningRmdInformation) {
                showOpeningRmdDialog();
            }

            statusLabel.setText("Opening RMD information is required.");
            return;
        }

        /*
         * There is no application logging facility yet. Preserve the
         * existing developer-observable stack trace while keeping the
         * user-facing failure concise and non-technical.
         */
        exception.printStackTrace();

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Projection Unavailable");
        alert.setHeaderText("The projection could not be completed.");
        alert.setContentText(
                ProjectionFailureMessages.unexpectedProjectionFailureMessage());
        alert.showAndWait();

        statusLabel.setText("Projection could not be completed.");
    }
    private void onSaveCurrentAsBaseline() {

        if (!controller.currentProjectionInputsMatch()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Run and accept a projection matching the current inputs before capturing a baseline. No calculation was started.").showAndWait();
            return;
        }

        RetirementPlan plan =
                controller.getCurrentPlan();

        if (plan.getBaseline() != null) {

            Alert alert =
                    new Alert(
                            Alert.AlertType.CONFIRMATION);

            alert.setTitle(
                    "Replace Existing Baseline");

            alert.setHeaderText(
                    "Replace Existing Baseline?");

            alert.setContentText(
                    "An existing baseline is already "
                            + "saved for this plan.\n\n"
                            + "Saving the current projection "
                            + "will replace it. "
                            + "The existing baseline cannot "
                            + "be recovered.");

            ButtonType replaceButton =
                    new ButtonType(
                            "Replace Baseline");

            alert.getButtonTypes().setAll(
                    ButtonType.CANCEL,
                    replaceButton);

            alert.showAndWait();

            if (alert.getResult()
                    != replaceButton) {

                return;
            }
        }

        controller.saveCurrentAsBaseline(
                "Baseline");

        refreshProjectionViews(false, true);
        statusLabel.setText("Original accepted projection captured as baseline. Save the plan to retain it across restarts.");

        updateWindowTitle();

        statusLabel.setText(
                "Current projection saved as baseline.");
    }
}
