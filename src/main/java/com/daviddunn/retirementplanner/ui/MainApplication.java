package com.daviddunn.retirementplanner.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {

        MainWindow window = new MainWindow();

        // Give MainWindow access to the Stage so it can
        // update the window title after Open, Save As, New, etc.
        window.setStage(stage);

        Scene scene = window.createScene();

        stage.setScene(scene);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}