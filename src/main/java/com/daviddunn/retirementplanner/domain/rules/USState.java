package com.daviddunn.retirementplanner.domain.rules;

import java.util.Arrays;

public enum USState {

    ALABAMA("Alabama"),
    ALASKA("Alaska"),
    ARIZONA("Arizona"),
    ARKANSAS("Arkansas"),
    CALIFORNIA("California"),
    COLORADO("Colorado"),
    CONNECTICUT("Connecticut"),
    DELAWARE("Delaware"),
    DISTRICT_OF_COLUMBIA("District of Columbia"),
    FLORIDA("Florida"),
    GEORGIA("Georgia"),
    HAWAII("Hawaii"),
    IDAHO("Idaho"),
    ILLINOIS("Illinois"),
    INDIANA("Indiana"),
    IOWA("Iowa"),
    KANSAS("Kansas"),
    KENTUCKY("Kentucky"),
    LOUISIANA("Louisiana"),
    MAINE("Maine"),
    MARYLAND("Maryland"),
    MASSACHUSETTS("Massachusetts"),
    MICHIGAN("Michigan"),
    MINNESOTA("Minnesota"),
    MISSISSIPPI("Mississippi"),
    MISSOURI("Missouri"),
    MONTANA("Montana"),
    NEBRASKA("Nebraska"),
    NEVADA("Nevada"),
    NEW_HAMPSHIRE("New Hampshire"),
    NEW_JERSEY("New Jersey"),
    NEW_MEXICO("New Mexico"),
    NEW_YORK("New York"),
    NORTH_CAROLINA("North Carolina"),
    NORTH_DAKOTA("North Dakota"),
    OHIO("Ohio"),
    OKLAHOMA("Oklahoma"),
    OREGON("Oregon"),
    PENNSYLVANIA("Pennsylvania"),
    RHODE_ISLAND("Rhode Island"),
    SOUTH_CAROLINA("South Carolina"),
    SOUTH_DAKOTA("South Dakota"),
    TENNESSEE("Tennessee"),
    TEXAS("Texas"),
    UTAH("Utah"),
    VERMONT("Vermont"),
    VIRGINIA("Virginia"),
    WASHINGTON("Washington"),
    WEST_VIRGINIA("West Virginia"),
    WISCONSIN("Wisconsin"),
    WYOMING("Wyoming");

    private final String displayName;

    USState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static USState fromDisplayName(String displayName) {

        return Arrays.stream(values())
                .filter(state ->
                        state.displayName.equalsIgnoreCase(displayName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown state: " + displayName));
    }
}