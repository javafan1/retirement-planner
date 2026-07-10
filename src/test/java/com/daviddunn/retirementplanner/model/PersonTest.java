package com.daviddunn.retirementplanner.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonTest {

    @Test
    void fullNameShouldBeCorrect() {

        Person david =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        assertEquals("David Dunn", david.getFullName());
    }
}