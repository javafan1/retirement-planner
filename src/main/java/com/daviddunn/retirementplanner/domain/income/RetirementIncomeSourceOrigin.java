package com.daviddunn.retirementplanner.domain.income;

import com.fasterxml.jackson.annotation.JsonCreator;

/** How the original arrangement was established, independently of its legal DB/DC plan type. */
public enum RetirementIncomeSourceOrigin {
    UNKNOWN,
    /** Ordinary retirement benefit established by an employer and directly tied to the original owner's employment. */
    EMPLOYER_RETIREMENT_PLAN,
    /** Insurance or income contract purchased privately, independent of the owner's employment. */
    INDIVIDUALLY_PURCHASED_CONTRACT,
    /** Known arrangement outside the ordinary model, such as military, railroad, disability, or QDRO benefits. */
    OTHER_OR_SPECIAL_ARRANGEMENT;

    @JsonCreator
    public static RetirementIncomeSourceOrigin fromJson(String value) {
        return valueOf(value);
    }

    /** Keep the legacy input document and its digest unchanged when origin has not been established. */
    public static final class UnknownFilter {
        @Override
        public boolean equals(Object value) {
            return value == UNKNOWN;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
