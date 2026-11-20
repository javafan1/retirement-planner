package com.daviddunn.retirementplanner.ui.socialsecurity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ArkansasExhaustiveLaunchTest {
    @BeforeAll static void startFx() throws Exception {
        ArkansasSocialSecurityLaunchTest.startFx();
    }

    @Test void realExhaustiveLauncherRetainsEveryCandidateAndSelectedAnnualDetails(@TempDir Path folder) throws Exception {
        ArkansasSocialSecurityLaunchTest.runQuickAndOptionallyExhaustive(folder, true);
    }
}
