package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.views.HouseholdView;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.geometry.Insets;

import java.math.BigDecimal;
import java.util.ArrayList;


public class MainWindow {

    private final BorderPane root;
    private RetirementPlan currentPlan;


    public MainWindow() {

        currentPlan = RetirementPlanFactory.createEmptyPlan();
        //currentPlan = createEmptyPlan();

        root = new BorderPane();

        root.setTop(createMenuBar());

        root.setCenter(createTabPane());

        root.setBottom(createStatusBar());
    }
//    public MainWindow() {
//
//        root = new BorderPane();
//
//        MenuBar menuBar = createMenuBar();
//
//        root.setTop(menuBar);
//
//        //root.setCenter(new Label("Retirement Planner"));
//        root.setCenter(createTabPane());
//    }

    public Scene createScene() {
        return new Scene(root, 1000, 700);
    }

    private MenuBar createMenuBar() {

        Menu fileMenu = new Menu("File");

        fileMenu.getItems().addAll(
                new MenuItem("New"),
                new MenuItem("Open..."),
                new MenuItem("Save"),
                new MenuItem("Save As..."),
                new MenuItem("Exit")
        );

        Menu helpMenu = new Menu("Help");

        helpMenu.getItems().add(
                new MenuItem("About")
        );

        MenuBar menuBar = new MenuBar();

        menuBar.getMenus().addAll(fileMenu, helpMenu);

        return menuBar;
    }

    private TabPane createTabPane() {

        TabPane tabPane = new TabPane();

        tabPane.getTabs().addAll(
                createTab("Household"),
                createTab("Accounts"),
                createTab("Income"),
                createTab("Expenses"),
                createTab("Assumptions"),
                createTab("Results")
        );

        return tabPane;
    }

    private Tab createTab(String title) {

        Tab tab = new Tab(title);

        tab.setClosable(false);

        //tab.setContent(new Label(title));
        if (title.equals("Household")) {
            tab.setContent(new HouseholdView());
        } else {
            tab.setContent(new Label(title));
        }

        return tab;
    }

    private Label createStatusBar() {

        Label status = new Label("Ready");

        status.setPadding(new Insets(5));

        return status;
    }

//    private RetirementPlan createEmptyPlan() {
//
//        Person primaryPerson = new Person(
//                "",
//                "",
//                null);
//
//        Person spouse = new Person(
//                "",
//                "",
//                null);
//
//        Household household = new Household(
//                primaryPerson,
//                spouse);
//
//        PlanningAssumptions assumptions =
//                new PlanningAssumptions(
//                        new BigDecimal("0.03"),
//                        new BigDecimal("0.08"));
//
//        return new RetirementPlan(
//                household,
//                assumptions);
//    }
}
