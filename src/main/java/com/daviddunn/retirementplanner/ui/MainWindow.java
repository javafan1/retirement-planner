package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.ui.charts.PortfolioChartView;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;

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

import java.io.File;

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
        dashboardView = new DashboardView();
        portfolioChartView = new PortfolioChartView();

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
                this::refreshProjectionViews);

        incomeSourcesView.setOnPlanChanged(
                this::refreshProjectionViews);

        accountsView.setOnPlanChanged(
                this::refreshProjectionViews);

        assumptionsView.setOnPlanChanged(
                this::refreshProjectionViews);

        resultsView.setOnYearDoubleClick(
                this::showProjectionYearSummary);
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
        MenuItem exitItem = new MenuItem("Exit");

        saveItem.setOnAction(e -> onSave());
        saveAsItem.setOnAction(e -> onSaveAs());
        openItem.setOnAction(e -> onOpen());
        exitItem.setOnAction(e -> onExit());

        fileMenu.getItems().addAll(
                newItem,
                openItem,
                saveItem,
                saveAsItem,
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
                createTab("Results", resultsView));

        tabPane.getTabs().add(
                createTab("Dashboard", dashboardView));


        tabPane.getTabs().add(
                createTab("Portfolio Chart",
                        portfolioChartView));

        tabPane.getTabs().add(
                createTab("Household", householdView));

        tabPane.getTabs().add(
                createTab("Accounts", accountsView));

        tabPane.getTabs().add(
                createTab("Income", incomeSourcesView));

        tabPane.getTabs().add(
                createTab("Expenses", expensesView));

        tabPane.getTabs().add(
                createTab("Assumptions", assumptionsView));

        tabPane.getTabs().add(
                createTab("Projection", projectionYearView));



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

        refreshProjectionViews();

        statusLabel.setText("Ready");
    }

    private void saveCurrentPlan() {

        RetirementPlan plan = controller.getCurrentPlan();

        householdView.save(plan);
        accountsView.save(plan);
        incomeSourcesView.save(plan);
        expensesView.save(plan);
        assumptionsView.save(plan);

    }

    private void onSave() {


        if (!controller.hasCurrentFile()) {
            onSaveAs();
            return;
        }

        saveCurrentPlan();

        try {
            controller.save();
            statusLabel.setText("Plan saved.");
        } catch (Exception ex) {
            statusLabel.setText("Save failed.");
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
            ProjectionYear projectionYear) {

        ProjectionYearDetailsDialog dialog =
                new ProjectionYearDetailsDialog(
                        projectionYear);

        dialog.show();
    }

    private void refreshProjectionViews() {

        controller.invalidateProjection();

        Projection projection =
                controller.getCurrentProjection();

        ProjectionSummary summary =
                controller.getCurrentProjectionSummary();

        dashboardView.load(summary);
        portfolioChartView.load(projection);
        projectionYearView.load(projection);
        resultsView.load(projection);

        statusLabel.setText("Projection updated.");
    }
}