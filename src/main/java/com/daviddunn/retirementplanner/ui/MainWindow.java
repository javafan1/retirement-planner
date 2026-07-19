package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;

import com.daviddunn.retirementplanner.ui.views.AccountsView;
import com.daviddunn.retirementplanner.ui.views.HouseholdView;
//import com.daviddunn.retirementplanner.ui.views.AccountsView;
//import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
//import com.daviddunn.retirementplanner.ui.views.ExpensesView;
//
//import com.daviddunn.retirementplanner.ui.views.IncomeView;
//import com.daviddunn.retirementplanner.ui.views.ResultsView;
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
import java.io.IOException;

public class MainWindow {

    private final ApplicationController controller;

    private final BorderPane root;

    private final HouseholdView householdView;
    private final AccountsView accountsView;

//    private final AccountsView accountsView;
//    private final IncomeView incomeView;
//    private final ExpensesView expensesView;
//    private final AssumptionsView assumptionsView;
//    private final ResultsView resultsView;

    private final Label statusLabel;

    public MainWindow() {

        controller = new ApplicationController();

        householdView = new HouseholdView();
        accountsView = new AccountsView();

//        accountsView = new AccountsView();
//        incomeView = new IncomeView();
//        expensesView = new ExpensesView();
//        assumptionsView = new AssumptionsView();
//        resultsView = new ResultsView();

        statusLabel = new Label("Ready");

        root = new BorderPane();

        root.setTop(createMenuBar());
        root.setCenter(createTabPane());
        root.setBottom(createStatusBar());

        loadCurrentPlan();
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

        tabPane.getTabs().add(createTab("Household", householdView));

        tabPane.getTabs().add(createTab("Accounts", accountsView));
        tabPane.getTabs().add(createTab("Income", new Label("Coming Soon")));
        tabPane.getTabs().add(createTab("Expenses", new Label("Coming Soon")));
        tabPane.getTabs().add(createTab("Assumptions", new Label("Coming Soon")));
        tabPane.getTabs().add(createTab("Results", new Label("Coming Soon")));

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

        statusLabel.setText("Ready");
    }
//    private void refreshViews() {
//
//        System.out.println(controller);
//        System.out.println(controller.getCurrentPlan());
//
//        householdView.load(controller.getCurrentPlan());
//
//        statusLabel.setText("Ready");
//    }
//    private void refreshViews() {
//
//        householdView.load(controller.getCurrentPlan());
//
//        statusLabel.setText("Ready");
//    }
    //private void refreshViews() {

        // We'll implement this once each view has a load() method.

        // householdView.load(controller.getCurrentPlan());
        // accountsView.load(controller.getCurrentPlan());
        // incomeView.load(controller.getCurrentPlan());
        // expensesView.load(controller.getCurrentPlan());
        // assumptionsView.load(controller.getCurrentPlan());
        // resultsView.clear();
    //}

    private void saveCurrentPlan() {

        RetirementPlan plan = controller.getCurrentPlan();

        householdView.save(plan);
        accountsView.save(plan);

        // Future
        // accountsView.save(plan);
        // incomeView.save(plan);
        // assumptionsView.save(plan);
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
        }
        catch (Exception ex) {
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
        }
        catch (Exception ex) {

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
        }
        catch (Exception ex) {

            statusLabel.setText("Open failed.");

            ex.printStackTrace();
        }
    }

private void onExit() {

    // Later we'll ask to save unsaved changes.

    root.getScene().getWindow().hide();
}
}