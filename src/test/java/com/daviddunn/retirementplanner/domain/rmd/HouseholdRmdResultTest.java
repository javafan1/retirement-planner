package com.daviddunn.retirementplanner.domain.rmd;




import org.junit.jupiter.api.Test;

import java.math.BigDecimal;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class HouseholdRmdResultTest {


    @Test
    void createsZeroHouseholdRmdResult() {

        HouseholdRmdResult result =
                HouseholdRmdResult.zero();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getTotalRmd()));
    }
}
