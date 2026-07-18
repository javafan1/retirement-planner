package com.daviddunn.retirementplanner.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {

        MainWindow window = new MainWindow();

        stage.setTitle("Retirement Planner");
        stage.setScene(window.createScene());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}