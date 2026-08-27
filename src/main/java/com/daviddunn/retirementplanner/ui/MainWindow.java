package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.ui.charts.PortfolioChartView;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.RothConversionView;

import com.daviddunn.retirementplanner.ui.views.*;
import com.daviddunn.retirementplanner.ui.dialogs.*;

import javafx.geometry.Insets;
import javafx.scene.Scene;
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
    private final Label statusLabel;

    public MainWindow() {

        controller = new ApplicationController();

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
        controller.openLastPlan();

        loadCurrentPlan();
    }

    private void wireEvents() {

        expensesView.setOnPlanChanged(
                this::onPlanChanged);

        incomeSourcesView.setOnPlanChanged(
                this::onPlanChanged);

        accountsView.setOnPlanChanged(
                this::onPlanChanged);

        assumptionsView.setOnPlanChanged(
                this::onPlanChanged);

        assumptionsView.setOnOpeningRmdRequested(
                this::showOpeningRmdDialog);

        resultsView.setOnYearDoubleClick(
                this::showProjectionYearSummary);

        rothConversionView.setOnPlanChanged(
                this::onPlanChanged);

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

        Menu helpMenu = new Menu("Help");

        MenuItem aboutItem = new MenuItem("About");

        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, helpMenu);
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



        tabPane.getTabs().add(
                createTab("Charts",
                        portfolioChartView));


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

    private HBox createStatusBar() {

        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(5));

        return statusBar;
    }


    private void loadCurrentPlan() {

        RetirementPlan plan = controller.getCurrentPlan();


        householdView.load(plan);
        accountsView.load(plan);
        incomeSourcesView.load(plan);
        expensesView.load(plan);
        assumptionsView.load(plan);
        rothConversionView.load(plan);
        nonInvestableAssetsView.load(plan);

        updateWindowTitle();

        refreshAllViews();

        statusLabel.setText("Ready");
    }

    private void saveCurrentPlan() {

        RetirementPlan plan = controller.getCurrentPlan();

        householdView.save(plan);
        accountsView.save(plan);
        incomeSourcesView.save(plan);
        expensesView.save(plan);
        assumptionsView.save(plan);
        rothConversionView.save(plan);

    }

    private void onSave() {

        if (!controller.hasCurrentFile()) {
            onSaveAs();
            return;
        }

        saveCurrentPlan();

        try {

            controller.save();

            updateWindowTitle();

            statusLabel.setText(
                    "Plan saved.");

        } catch (Exception ex) {

            statusLabel.setText(
                    "Save failed.");

            ex.printStackTrace();
        }
    }

    private void onSaveAs() {

        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Save Retirement Plan");

        fileChooser.setInitialFileName("RetirementPlan.json");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "JSON Files",
                        "*.json"));

        File file = fileChooser.showSaveDialog(root.getScene().getWindow());

        if (file == null) {
            return;
        }

        saveCurrentPlan();

        try {
            controller.saveAs(file.toPath());

            statusLabel.setText("Plan saved.");

            updateWindowTitle();

        } catch (Exception ex) {

            statusLabel.setText("Save failed.");

            ex.printStackTrace();
        }
    }

    private void onOpen() {

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

            statusLabel.setText("Plan opened.");
        } catch (Exception ex) {

            statusLabel.setText("Open failed.");

            ex.printStackTrace();
        }
    }

    private void onExit() {

        // Later we'll ask to save unsaved changes.

        root.getScene().getWindow().hide();
    }
    private void showProjectionYearSummary(
            ProjectionYear currentYear) {

        ProjectionYear baselineYear =
                null;

        BigDecimal baselineNonInvestableValue =
                null;

        if (controller.getCurrentPlan()
                .getBaseline() != null) {

            Projection baselineProjection =
                    controller.getBaselineProjection();

            if (baselineProjection != null) {

                baselineYear =
                        baselineProjection.getYears()
                                .stream()
                                .filter(year ->
                                        year.getCalendarYear()
                                                == currentYear
                                                .getCalendarYear())
                                .findFirst()
                                .orElse(null);

                if (baselineYear != null) {

                    baselineNonInvestableValue =
                            controller
                                    .getBaselineNonInvestableAssetValue(
                                            currentYear
                                                    .getCalendarYear());
                }
            }
        }

        BigDecimal currentNonInvestableValue =
                controller.getNonInvestableAssetValue(
                        currentYear.getCalendarYear());

        ProjectionYearDetailsDialog dialog =
                new ProjectionYearDetailsDialog(
                        currentYear,
                        baselineYear,
                        currentNonInvestableValue,
                        baselineNonInvestableValue);

        dialog.show();
    }

    private void refreshProjectionViews() {

        controller.invalidateProjection();

        try {
            Projection projection =
                    controller.getCurrentProjection();

            ProjectionSummary summary =
                    controller.getCurrentProjectionSummary();

            dashboardView.load(summary);
            portfolioChartView.load(projection);
            projectionYearView.load(projection);
            resultsView.load(
                    projection,
                    controller.getCurrentNonInvestableAssetProjections());
            assumptionsView.load(
                    controller.getCurrentPlan());

            resultsSummaryView.load(
                    controller.getCurrentPlan(),
                    projection,
                    controller.getCurrentNonInvestableAssetProjections());

            statusLabel.setText("Projection updated.");
        }
        catch (Exception ex) {

            handleProjectionFailure(ex);
        }
    }

    public void setStage(Stage stage) {

        this.stage = stage;

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

        controller.newPlan();

        loadCurrentPlan();

        updateWindowTitle();

        statusLabel.setText("New plan.");
    }

    private void refreshAllViews() {

        RetirementPlan plan =
                controller.getCurrentPlan();

        householdView.load(plan);
        accountsView.load(plan);
        incomeSourcesView.load(plan);
        expensesView.load(plan);
        assumptionsView.load(plan);
        rothConversionView.load(plan);

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

        refreshAllViews();

        updateWindowTitle();

        statusLabel.setText(
                "Current projection saved as baseline.");
    }
}
