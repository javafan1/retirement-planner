package com.daviddunn.retirementplanner.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonTest {

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("Jackson loaded successfully!");
    }
}