package com.daviddunn.retirementplanner.ui.components;

import com.daviddunn.retirementplanner.domain.model.Person;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class PersonCard extends GridPane {

    private final TextField firstNameField = new TextField();
    private final TextField lastNameField = new TextField();
    private final DatePicker birthDatePicker = new DatePicker();

    public PersonCard() {

        setPadding(new Insets(15));
        setHgap(10);
        setVgap(10);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHalignment(HPos.RIGHT);

        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);

        getColumnConstraints().addAll(labelColumn, fieldColumn);

        firstNameField.setPrefColumnCount(20);
        lastNameField.setPrefColumnCount(20);

        add(new Label("First Name:"), 0, 0);
        add(firstNameField, 1, 0);

        add(new Label("Last Name:"), 0, 1);
        add(lastNameField, 1, 1);

        add(new Label("Birth Date:"), 0, 2);
        add(birthDatePicker, 1, 2);
    }

    public void load(Person person) {

        if (person == null) {
            clear();
            return;
        }

        firstNameField.setText(person.getFirstName());
        lastNameField.setText(person.getLastName());
        birthDatePicker.setValue(person.getBirthDate());
    }

    public void save(Person person) {

        if (person == null) {
            return;
        }

        person.setFirstName(firstNameField.getText());
        person.setLastName(lastNameField.getText());
        person.setBirthDate(birthDatePicker.getValue());
    }

    public void clear() {

        firstNameField.clear();
        lastNameField.clear();
        birthDatePicker.setValue(null);
    }
}