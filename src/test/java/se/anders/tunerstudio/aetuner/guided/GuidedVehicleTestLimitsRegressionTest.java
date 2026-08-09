package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class GuidedVehicleTestLimitsRegressionTest {
    private GuidedVehicleTestLimitsRegressionTest() { }

    public static void main(String[] args) {
        defaultsAreCandidateValuesAndOverridesAreOff();
        activeSessionSnapshotCannotChangeMidRun();
        restoreReturnsToCandidateDefaults();
        System.out.println("GuidedVehicleTestLimitsRegressionTest passed");
    }

    private static void defaultsAreCandidateValuesAndOverridesAreOff() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        GuidedVehicleTestLimits.Snapshot defaults =
                GuidedVehicleTestLimits.current();
        require(!defaults.enabled, "test overrides must default OFF");
        close(defaults.detectorConfirmSeconds, 0.55, "detector default");
        close(defaults.targetAcquisitionSeconds, 1.00, "target-acquisition default");
        close(defaults.mapCatchupSeconds, 1.20, "MAP-catch-up default");
        close(defaults.tpsTolerance, 3.00, "TPS-tolerance default");
        close(defaults.tpsBoundaryEpsilon, 0.05, "boundary-epsilon default");
        close(defaults.localTpsOnsetRise, 2.00,
                "Archive 9 local-onset default must reject tiny TPS drift");
    }

    private static void activeSessionSnapshotCannotChangeMidRun() {
        GuidedVehicleTestLimits.configurePending(true,
                0.80, 1.40, 1.70, 4.00, 0.08, 2.50);
        GuidedVehicleTestLimits.Snapshot active =
                GuidedVehicleTestLimits.beginSession();
        require(active.enabled, "enabled test overrides did not activate");

        GuidedVehicleTestLimits.configurePending(true,
                1.10, 1.90, 2.20, 5.00, 0.12, 3.00);
        GuidedVehicleTestLimits.Snapshot stillActive =
                GuidedVehicleTestLimits.current();
        close(stillActive.detectorConfirmSeconds, 0.80,
                "mid-session pending change altered active detector limit");
        close(stillActive.tpsTolerance, 4.00,
                "mid-session pending change altered active TPS tolerance");
        require(stillActive.summary().contains("TEST OVERRIDES ACTIVE"),
                "active override identity was not visible");

        GuidedVehicleTestLimits.endSession();
        GuidedVehicleTestLimits.Snapshot next =
                GuidedVehicleTestLimits.current();
        close(next.detectorConfirmSeconds, 1.10,
                "next-session pending detector value was lost");
        close(next.tpsTolerance, 5.00,
                "next-session pending TPS tolerance was lost");
    }

    private static void restoreReturnsToCandidateDefaults() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        GuidedVehicleTestLimits.Snapshot restored =
                GuidedVehicleTestLimits.current();
        require(!restored.enabled, "restore did not disable overrides");
        require(!restored.hasNonDefaultValues(),
                "restore left non-default values behind");
    }

    private static void close(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
